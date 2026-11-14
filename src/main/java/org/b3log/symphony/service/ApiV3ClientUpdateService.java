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
import org.b3log.symphony.model.ApiV3AuthorizationRequest;
import org.b3log.symphony.model.ApiV3ClientRedirect;
import org.b3log.symphony.model.ApiV3Token;
import org.b3log.symphony.model.ApiV3Client;
import org.b3log.symphony.repository.ApiV3AppLoginRequestRepository;
import org.b3log.symphony.repository.ApiV3AuthorizationCodeRepository;
import org.b3log.symphony.repository.ApiV3AuthorizationRequestRepository;
import org.b3log.symphony.repository.ApiV3ClientRedirectRepository;
import org.b3log.symphony.repository.ApiV3ClientRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.ApiV3TokenRepository;
import org.b3log.symphony.util.ApiV3ClientManagementDto;
import org.b3log.symphony.util.ApiV3ClientManagementInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 完整替换登记元数据。授权变化时在同一个事务中撤销原有授权和凭据。
 */
@Service
public class ApiV3ClientUpdateService {
    @Inject
    private ApiV3ClientManagementService management;
    @Inject
    private ApiV3ClientRegistrationService registration;
    @Inject
    private ApiV3ClientRepository clients;
    @Inject
    private ApiV3ClientRedirectRepository redirects;
    @Inject
    private ApiV3TokenRepository tokens;
    @Inject
    private ApiV3AuthorizationCodeRepository codes;
    @Inject
    private ApiV3AuthorizationRequestRepository consents;
    @Inject
    private ApiV3RequestRepository requests;
    @Inject
    private ApiV3AppLoginRequestRepository appLogins;

    public JSONObject update(final JSONObject identity, final String clientId,
                             final ApiV3ClientManagementInputs.Registration input, final String key) throws Exception {
        ApiV3ClientManagementInputs.clientId(clientId);
        if (ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(clientId)) throw ApiV3Requests.notFound();
        final JSONObject viewer = identity == null ? null : identity.optJSONObject("user");
        final String actor = viewer == null ? "" : viewer.optString("oId");
        if (!actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.forbidden();
        try {
            final var shape = ApiV3ClientManagementInputs.registration(input.normalized());
            final var definition = registration.normalizeDefinition(shape.name(), shape.scope(), shape.redirectUris());
            // 回调顺序以及 scope 的重复或重排不改变有效请求内容。
            final List<String> callbacks = List.copyOf(new TreeSet<>(definition.redirectUris()));
            final JSONObject normalized = new JSONObject().put("clientId", clientId).put("name", definition.name())
                    .put("scope", definition.scope()).put("redirectUris", new JSONArray(callbacks));
            final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                public Transaction beginTransaction() {
                    return ApiV3Transactions.strict(requests.beginTransaction());
                }

                public boolean hasTransactionBegun() {
                    return requests.hasTransactionBegun();
                }

                public JSONObject getForUpdate(final String id) throws RepositoryException {
                    management.requireAdministrator(identity, true);
                    return requests.getForUpdate(id);
                }

                public String add(final JSONObject row) throws RepositoryException {
                    return requests.add(row);
                }

                public void update(final String id, final JSONObject row, final String... names) throws RepositoryException {
                    requests.update(id, row, names);
                }
            };
            return ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "admin.oauth.clients.update", key, normalized,
                    () -> updateInCurrentTransaction(clientId, definition, callbacks));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final IllegalArgumentException e) {
            throw ApiV3Requests.invalid();
        } catch (final Exception e) {
            throw unavailable();
        }
    }

    /**
     * 仅在根事务按固定顺序锁定客户端命名空间和当前管理身份、用户、令牌、请求后调用。
     */
    private JSONObject updateInCurrentTransaction(final String clientId,
                                                  final ApiV3ClientRegistrationService.Definition desired,
                                                  final List<String> callbacks) throws Exception {
        if (!requests.hasTransactionBegun()) throw new IllegalStateException("client update transaction required");
        return ApiV3TransactionSavepoint.run(() -> {
            final var rows = clients.select("SELECT oId,clientId,name,scope,status FROM `" + clients.getName()
                    + "` WHERE clientId=? LIMIT 2 FOR UPDATE", clientId);
            if (rows.isEmpty() || !clientId.equals(rows.getFirst().optString("clientId")))
                throw ApiV3Requests.notFound();
            if (rows.size() != 1) throw unavailable();
            final JSONObject current = rows.getFirst();
            ApiV3ClientManagementDto.client(current); // 已存元数据或状态格式异常时拒绝更新。
            // 历史内部登记早于 HTTP 上限。读到 21 条不同的有效回调地址即能确认
            // 它和最多 20 条的新设置不同，无须读取全部历史记录。
            final var active = redirects.select("SELECT oId,clientId,redirectUri,status FROM `" + redirects.getName()
                    + "` WHERE clientId=? AND status=? ORDER BY oId ASC LIMIT 21 FOR UPDATE", clientId, ApiV3ClientRedirect.ACTIVE);
            final Set<String> previousUris = new TreeSet<>();
            for (final JSONObject row : active) {
                final JSONObject value = ApiV3ClientManagementDto.redirect(row);
                final String uri = value.getString("uri");
                if (!clientId.equals(row.optString("clientId"))
                        || !ApiV3ClientRedirectRepository.identity(clientId, uri).equals(row.optString("oId"))
                        || !previousUris.add(uri)) throw unavailable();
            }
            final Set<String> previousScope = new TreeSet<>(Arrays.asList(current.getString("scope").split(" ")));
            final Set<String> nextScope = new TreeSet<>(Arrays.asList(desired.scope().split(" ")));
            final boolean changed = !previousScope.equals(nextScope) || !previousUris.equals(new TreeSet<>(callbacks));
            if (!current.getString("name").equals(desired.name()) || !current.getString("scope").equals(desired.scope())) {
                final int count = ApiV3TransactionStatements.execute("UPDATE `" + clients.getName()
                        + "` SET name=?,scope=? WHERE oId=? AND clientId=?", desired.name(), desired.scope(), current.getString("oId"), clientId);
                if (count != 1) throw unavailable();
            }
            // 完整替换回调定义，并删除已停用的回调记录。
            ApiV3TransactionStatements.execute("DELETE FROM `" + redirects.getName() + "` WHERE clientId=?", clientId);
            for (final String uri : callbacks) {
                final int added = ApiV3TransactionStatements.execute("INSERT INTO `" + redirects.getName()
                                + "` (oId,clientId,redirectUri,status) VALUES (?,?,?,?)",
                        ApiV3ClientRedirectRepository.identity(clientId, uri), clientId, uri, ApiV3ClientRedirect.ACTIVE);
                if (added != 1) throw unavailable();
            }
            if (changed) {
                if (ApiV3Client.APP_CLIENT.equals(clientId)) appLogins.invalidatePendingInCurrentTransaction();
                final int revoked = ApiV3TransactionStatements.execute("UPDATE `" + tokens.getName()
                                + "` SET state=?,updatedAt=? WHERE clientId=? AND (state<>? OR state IS NULL)",
                        ApiV3Token.STATE_REVOKED, System.currentTimeMillis(), clientId, ApiV3Token.STATE_REVOKED);
                if (revoked < 0) throw unavailable();
                // 清除所有历史授权码，保留消费时间与令牌家族绑定的原有含义。
                ApiV3TransactionStatements.execute("DELETE FROM `" + codes.getName() + "` WHERE clientId=?", clientId);
                // 管理撤销与用户拒绝授权是独立事件，已完成的决策历史保留原样。
                ApiV3TransactionStatements.execute("DELETE FROM `" + consents.getName() + "` WHERE clientId=? AND decision=?",
                        clientId, ApiV3AuthorizationRequest.PENDING);
            }
            return new JSONObject().put("clientId", clientId).put("name", desired.name()).put("scope", desired.scope())
                    .put("status", current.getInt("status")).put("redirectUris", new JSONArray(callbacks));
        });
    }

    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "客户端修改暂不可用");
    }
}
