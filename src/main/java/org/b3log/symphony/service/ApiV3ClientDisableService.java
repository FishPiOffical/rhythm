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
import org.b3log.symphony.model.ApiV3Token;
import org.b3log.symphony.model.ApiV3Client;
import org.b3log.symphony.repository.ApiV3AppLoginRequestRepository;
import org.b3log.symphony.repository.ApiV3ClientRepository;
import org.b3log.symphony.repository.ApiV3TokenRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.util.ApiV3ClientManagementInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.json.JSONObject;

/** 客户端逻辑停用、撤销其全部令牌以及回执在同一个严格事务中提交。 */
@Service
public class ApiV3ClientDisableService {
    private static final int DISABLED = 1;
    @Inject private ApiV3ClientManagementService management;
    @Inject private ApiV3ClientRepository clients;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private ApiV3RequestRepository requests;
    @Inject private ApiV3AppLoginRequestRepository appLogins;

    public JSONObject disable(final JSONObject identity, final String clientId, final String key) throws Exception {
        ApiV3ClientManagementInputs.clientId(clientId);
        if (ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(clientId)) throw ApiV3Requests.notFound();
        final JSONObject user = identity == null ? null : identity.optJSONObject("user");
        final String actor = user == null ? "" : user.optString("oId");
        if (!actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.forbidden();
        final ApiV3MutationStore guarded = new ApiV3MutationStore() {
            public Transaction beginTransaction() { return ApiV3Transactions.strict(requests.beginTransaction()); }
            public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
            public JSONObject getForUpdate(final String id) throws RepositoryException {
                // 先按固定顺序锁定客户端命名空间，再锁定用户、令牌和请求，重放也遵循此顺序。
                management.requireAdministrator(identity, true);
                return requests.getForUpdate(id);
            }
            public String add(final JSONObject row) throws RepositoryException { return requests.add(row); }
            public void update(final String id, final JSONObject row, final String... names) throws RepositoryException {
                requests.update(id, row, names);
            }
        };
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "admin.oauth.clients.disable", key,
                    new JSONObject().put("clientId", clientId), () -> disableInCurrentTransaction(clientId));
        } catch (final ApiV3Exception e) { throw e; }
        catch (final Exception e) { throw unavailable(); }
    }

    /** 仅在调用方根事务复核当前管理员后调用，由调用方统一提交。 */
    private JSONObject disableInCurrentTransaction(final String clientId) throws Exception {
        if (!requests.hasTransactionBegun()) throw new IllegalStateException("client disable transaction required");
        return ApiV3TransactionSavepoint.run(() -> {
            final var rows = clients.select("SELECT oId,clientId,status FROM `" + clients.getName()
                    + "` WHERE clientId=? LIMIT 2 FOR UPDATE", clientId);
            if (rows.isEmpty()) throw ApiV3Requests.notFound();
            if (rows.size() != 1 || !clientId.equals(rows.getFirst().optString("clientId"))) throw unavailable();
            final JSONObject current = rows.getFirst();
            final Object state = current.opt("status");
            if (!(state instanceof Number) || !("0".equals(state.toString()) || "1".equals(state.toString()))) throw unavailable();
            if ("0".equals(state.toString())) {
                final int updated = ApiV3TransactionStatements.execute("UPDATE `" + clients.getName()
                        + "` SET status=? WHERE oId=? AND clientId=?", DISABLED, current.getString("oId"), clientId);
                if (updated != 1) throw unavailable();
            }
            // 按客户端一次撤销所有用户、家族、类型和状态的令牌，避免分页遗漏。
            final int changed = ApiV3TransactionStatements.execute("UPDATE `" + tokens.getName()
                    + "` SET state=?,updatedAt=? WHERE clientId=? AND (state<>? OR state IS NULL)",
                    ApiV3Token.STATE_REVOKED, System.currentTimeMillis(), clientId, ApiV3Token.STATE_REVOKED);
            if (changed < 0) throw unavailable();
            if (ApiV3Client.APP_CLIENT.equals(clientId)) appLogins.invalidatePendingInCurrentTransaction();
            return new JSONObject().put("clientId", clientId).put("status", DISABLED);
        });
    }

    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "客户端停用暂不可用");
    }
}
