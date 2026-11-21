/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.UserChatTailRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

/** 本人设置与发送快照均按用户行串行化，不影响旧站的整包系统设置。 */
@Service
public class ApiV3ChatTailService {
    @Inject private UserRepository users;
    @Inject private UserChatTailRepository tails;
    @Inject private ApiV3RequestRepository requests;
    @Inject private ApiV3SecurityProofService proofs;

    public JSONObject read(final JSONObject identity) {
        Transaction transaction = null;
        try {
            transaction = begin();
            authorize(identity, "profile.read");
            final JSONObject result = choice(actor(identity));
            transaction.commit();
            return result;
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (transaction != null && transaction.isActive()) transaction.rollback();
        }
    }

    public JSONObject update(final JSONObject identity, final JSONObject body, final String key) {
        final JSONObject input = ApiV3ChatTail.input(body);
        final String actor = actor(identity);
        try {
            final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                public Transaction beginTransaction() { return begin(); }
                public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
                public JSONObject getForUpdate(final String id) throws RepositoryException {
                    authorize(identity, "settings.write");
                    return requests.getForUpdate(id);
                }
                public String add(final JSONObject row) throws RepositoryException { return requests.add(row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
                    requests.update(id, row, fields);
                }
            };
            return ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "settings.chat-tail", key, input, () -> {
                checkContent(input.getString("text"));
                final var rows = tails.select("SELECT oId FROM `" + tails.getName() + "` WHERE oId=? FOR UPDATE", actor);
                final JSONObject row = new JSONObject().put("enabled", input.getBoolean("enabled") ? 1 : 0)
                        .put("text", input.getString("text")).put("updatedAt", System.currentTimeMillis());
                if (rows.isEmpty()) tails.add(row.put("oId", actor));
                else tails.update(actor, row, "enabled", "text", "updatedAt");
                return new JSONObject(input.toString());
            });
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }

    /** 仅供新版普通发送的已加锁根事务调用，重放回执不会再次读取当前设置。 */
    public JSONObject snapshotInCurrentTransaction(final String actor, final String contentHtml) throws RepositoryException {
        if (!users.hasTransactionBegun() || !tails.hasTransactionBegun())
            throw new IllegalStateException("chat tail snapshot transaction required");
        if (!ApiV3ChatTail.eligibleHtml(contentHtml)) return null;
        final JSONObject owner = users.getForUpdate(actor);
        if (owner == null || owner.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID)
            throw ApiV3Requests.notFound();
        final JSONObject current = choice(actor);
        if (!current.getBoolean("enabled")) return null;
        // 发送时重新审核，避免审核规则更新后继续发送已保存的不适宜文案。
        checkContent(current.getString("text"));
        return new JSONObject().put("text", current.getString("text"));
    }

    private static void checkContent(final String text) {
        if (!text.isEmpty() && CensorFactory.getTextCensor().censor(text).isBlocked())
            throw new ApiV3Exception(422, "content_rejected", "小尾巴中含有不适宜的内容");
    }

    private JSONObject choice(final String actor) throws RepositoryException {
        final var rows = tails.select("SELECT enabled,text FROM `" + tails.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.isEmpty()) return new JSONObject().put("enabled", false).put("text", "");
        final JSONObject row = rows.getFirst();
        if (!(row.opt("enabled") instanceof Number enabled) || enabled.intValue() < 0 || enabled.intValue() > 1
                || !(row.opt("text") instanceof String text)) throw unavailable();
        try {
            return ApiV3ChatTail.input(new JSONObject().put("enabled", enabled.intValue() == 1).put("text", text));
        } catch (ApiV3Exception invalid) {
            throw unavailable();
        }
    }

    private void authorize(final JSONObject identity, final String scope) throws RepositoryException {
        if (!actor(identity).equals(proofs.authorizeFirstPartyInCurrentTransaction(identity, scope).optString("oId")))
            throw ApiV3Requests.forbidden();
    }

    private Transaction begin() {
        if (requests.hasTransactionBegun()) throw new IllegalStateException("chat tail root required");
        return ApiV3Transactions.strict(requests.beginTransaction());
    }

    private static String actor(final JSONObject identity) {
        final JSONObject user = identity == null ? null : identity.optJSONObject("user");
        final String actor = user == null ? "" : user.optString("oId");
        if (!actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.forbidden();
        return actor;
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "小尾巴设置暂不可用");
    }
}
