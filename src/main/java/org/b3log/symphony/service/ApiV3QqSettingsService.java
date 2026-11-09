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
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

import java.util.Set;

/**
 * 私有 QQ 设置，第三方客户端的现有 profile.read 投影保持独立。
 */
@Service
public class ApiV3QqSettingsService {
    @Inject
    private ApiV3RequestRepository requests;
    @Inject
    private UserRepository users;
    @Inject
    private ApiV3SecurityProofService proofs;

    protected long now() {
        return System.currentTimeMillis();
    }

    public static JSONObject normalize(JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("qq")) || !(body.opt("qq") instanceof String raw))
            throw ApiV3WriteInputs.invalid();
        final String value = raw.strip();
        if (!value.isEmpty() && !value.matches("[0-9]{1,12}")) throw ApiV3WriteInputs.invalid();
        return new JSONObject().put("qq", value);
    }

    public JSONObject read(JSONObject identity) {
        Transaction transaction = null;
        try {
            transaction = begin();
            authorize(identity, "profile.read");
            final JSONObject result = current(actor(identity));
            transaction.commit();
            return result;
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (transaction != null && transaction.isActive())
                transaction.rollback();
        }
    }

    public JSONObject update(JSONObject identity, JSONObject body, String key) {
        final JSONObject value = normalize(body);
        final String actor = actor(identity);
        final boolean[] authorized = {false};
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
                    authorized[0] = true;
                    return requests.getForUpdate(id);
                }

                public String add(JSONObject row) throws RepositoryException {
                    return requests.add(row);
                }

                public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                    requests.update(id, row, fields);
                }
            };
            final JSONObject result = ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "settings.qq", key, value, () -> {
                final String qq = value.getString("qq");
                if (!current(actor).getString("qq").equals(qq)) {
                    final long time = now();
                    if (time <= 0)
                        throw unavailable();
                    if (ApiV3TransactionStatements.execute("UPDATE `" + users.getName() + "` SET userQQ=?,userUpdateTime=? WHERE oId=? AND userStatus=0", qq, time, actor) != 1)
                        throw unavailable();
                }
                return new JSONObject().put("qq", qq);
            });
            return normalize(result);
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (authorized[0])
                try {
                    users.refreshSettingsCache(actor);
                } catch (Exception ignored) {
                    try {
                        users.evictSettingsCaches(actor);
                    } catch (Exception ignoredEviction) {
                        // 仍以数据库中已提交的私有值为准。
                    }
                }
        }
    }

    private JSONObject current(String actor) throws RepositoryException {
        final var rows = users.select("SELECT userQQ FROM `" + users.getName() + "` WHERE oId=? AND userStatus=0 FOR UPDATE", actor);
        if (rows.size() != 1 || !(rows.getFirst().opt("userQQ") instanceof String value) || value.length() > 12)
            throw unavailable();
        // 旧值可能不符合新写入规则，读取时按实际存储值返回。
        return new JSONObject().put("qq", value);
    }

    private void authorize(JSONObject identity, String scope) throws RepositoryException {
        if (!actor(identity).equals(proofs.authorizeFirstPartyInCurrentTransaction(identity, scope).optString("oId")))
            throw ApiV3Requests.forbidden();
    }

    private Transaction begin() {
        if (requests.hasTransactionBegun()) throw new IllegalStateException("private QQ root required");
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
        return new ApiV3Exception(503, "temporarily_unavailable", "QQ设置暂不可用");
    }
}
