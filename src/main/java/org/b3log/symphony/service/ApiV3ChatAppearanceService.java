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
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.UserChatAppearanceRepository;
import org.b3log.symphony.repository.MembershipRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

/** 通过锁定当前账号行串行处理个人外观设置与发送消息时的外观快照。 */
@Service
public class ApiV3ChatAppearanceService {
    @Inject
    private UserRepository users;
    @Inject
    private UserChatAppearanceRepository appearances;
    @Inject
    private MembershipRepository memberships;
    @Inject
    private ApiV3RequestRepository requests;
    @Inject
    private ApiV3SecurityProofService proofs;

    protected long now() {
        return System.currentTimeMillis();
    }

    public JSONObject read(JSONObject identity) {
        Transaction tx = null;
        try {
            tx = begin();
            final String actor = actor(identity);
            authorize(identity, "profile.read");
            final JSONObject value = choice(actor);
            final JSONObject result = ApiV3ChatAppearance.effective(value, !ApiV3ChatAppearance.premium(value) || vip(actor));
            tx.commit();
            return result;
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            throw unavailable();
        } finally {
            if (tx != null && tx.isActive())
                tx.rollback();
        }
    }

    public JSONObject update(JSONObject identity, JSONObject body, String key) {
        final JSONObject input = ApiV3ChatAppearance.input(body);
        final String actor = actor(identity);
        try {
            final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                public Transaction beginTransaction() {
                    return begin();
                }

                public boolean hasTransactionBegun() {
                    return requests.hasTransactionBegun();
                }

                public JSONObject getForUpdate(String id) throws RepositoryException {
                    authorize(identity, "settings.write");
                    return requests.getForUpdate(id);
                }

                public String add(JSONObject row) throws RepositoryException {
                    return requests.add(row);
                }

                public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                    requests.update(id, row, fields);
                }
            };
            return ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "chat.appearance", key, input, () -> {
                // 新设置使用当前会员状态校验，幂等回放走已保存的回执。
                if (ApiV3ChatAppearance.premium(input) && !vip(actor))
                    throw new ApiV3Exception(403, "membership_required", "该外观需要有效会员");
                final var existing = appearances.select("SELECT oId FROM `" + appearances.getName() + "` WHERE oId=? FOR UPDATE", actor);
                final JSONObject value = new JSONObject(input.toString()).put("version", 1).put("updatedAt", now());
                if (existing.size() > 1)
                    throw unavailable();
                if (existing.isEmpty())
                    appearances.add(new JSONObject(value.toString()).put("oId", actor));
                else
                    appearances.update(actor, value, "bubbleId", "frameId", "version", "updatedAt");
                return ApiV3ChatAppearance.publicSnapshot(value);
            });
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            throw unavailable();
        }
    }

    /** 仅在消息发送方的根事务中调用，以数据库中的设置生成外观快照。 */
    public JSONObject snapshotInCurrentTransaction(String actor)throws RepositoryException{
        if(!users.hasTransactionBegun()||!appearances.hasTransactionBegun())throw new IllegalStateException("chat appearance snapshot transaction required");
        final JSONObject owner=users.getForUpdate(actor);
        if(owner==null||owner.optInt(UserExt.USER_STATUS,-1)!=UserExt.USER_STATUS_C_VALID)throw ApiV3Requests.notFound();
        final JSONObject value=choice(actor);
        return ApiV3ChatAppearance.effective(value,!ApiV3ChatAppearance.premium(value)||vip(actor));
    }
    private JSONObject choice(String actor) throws RepositoryException {
        final var rows = appearances.select("SELECT bubbleId,frameId,version FROM `" + appearances.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.size() > 1)
            throw unavailable();
        return rows.isEmpty() ? ApiV3ChatAppearance.defaults() : ApiV3ChatAppearance.publicSnapshot(rows.getFirst());
    }

    private boolean vip(String actor) throws RepositoryException {
        final var rows = memberships.select("SELECT state,expiresAt FROM `" + memberships.getName() + "` WHERE userId=? LIMIT 2 FOR UPDATE", actor);
        if (rows.size() > 1)
            throw unavailable();
        if (rows.isEmpty())
            return false;
        final var value = rows.getFirst();
        if (!(value.opt("state") instanceof Number state) || !(value.opt("expiresAt") instanceof Number expiry) || expiry.longValue() < 0)
            throw unavailable();
        return state.intValue() == 1 && (expiry.longValue() == 0 || expiry.longValue() > now());
    }

    private void authorize(JSONObject identity, String scope) throws RepositoryException {
        if (!actor(identity).equals(proofs.authorizeFirstPartyInCurrentTransaction(identity, scope).optString("oId")))
            throw ApiV3Requests.forbidden();
    }

    private Transaction begin() {
        if (requests.hasTransactionBegun())
            throw new IllegalStateException("chat appearance root required");
        return ApiV3Transactions.strict(requests.beginTransaction());
    }

    private static String actor(JSONObject identity) {
        final JSONObject user = identity == null ? null : identity.optJSONObject("user");
        final String actor = user == null ? "" : user.optString("oId");
        if (!actor.matches("[1-9][0-9]{0,18}"))
            throw ApiV3Requests.forbidden();
        return actor;
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "聊天外观暂不可用");
    }
}
