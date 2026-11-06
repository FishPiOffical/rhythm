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
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.model.ApiV3Token;
import org.b3log.symphony.model.ApiV3Client;
import org.b3log.symphony.repository.ApiV3ClientRepository;
import org.b3log.symphony.repository.ApiV3ClientRedirectRepository;
import org.b3log.symphony.repository.ApiV3TokenRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3ClientManagementInputs;
import org.b3log.symphony.util.ApiV3ClientManagementDto;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Administrator-approved public-client registration; there are no credential or self-service operations. */
@Service
public class ApiV3ClientManagementService {
    @Inject private ApiV3ClientRepository clients;
    @Inject private ApiV3ClientRedirectRepository redirects;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private UserRepository users;
    @Inject private ApiV3RequestRepository requests;
    @Inject private ApiV3ClientRegistrationService registration;

    /** Never trust an identity/client supplied in JSON; authenticate() exposes only a trustworthy token row ID. */
    public String requireAdministrator(final JSONObject identity, final boolean lock) throws RepositoryException {
        final JSONObject viewer = identity == null ? null : identity.optJSONObject("user");
        final String actor = viewer == null ? "" : viewer.optString("oId");
        final String tokenId = identity == null ? "" : identity.optString("tokenId");
        if (!actor.matches("[1-9][0-9]{0,18}") || !tokenId.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.forbidden();
        if (lock && !requests.hasTransactionBegun()) throw new IllegalStateException("admin root transaction required");
        if (lock) clients.lockForAccountResetInCurrentTransaction();
        final String suffix = lock ? " FOR UPDATE" : "";
        // Reserve the client namespace in the same sorted order as account reset before inserting a new client.
        // Then current website client, user, token and receipt locks retain their common direction.
        final var website = clients.select("SELECT clientId,status,scope FROM `" + clients.getName() + "` WHERE clientId=?" + suffix,
                ApiV3TokenMgmtService.WEBSITE_CLIENT);
        final var current = users.select("SELECT oId,userRole,userStatus FROM `" + users.getName() + "` WHERE oId=?" + suffix, actor);
        final var access = tokens.select("SELECT userId,clientId,tokenType,state,expiresAt,scope FROM `" + tokens.getName()
                + "` WHERE oId=?" + suffix, tokenId);
        if (website.size() != 1 || current.size() != 1 || access.size() != 1) throw ApiV3Requests.forbidden();
        final JSONObject site = website.getFirst(), user = current.getFirst(), token = access.getFirst();
        if (!ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(site.optString("clientId"))
                || site.optInt("status", -1) != ApiV3Client.STATUS_ACTIVE
                || !ApiV3TokenMgmtService.hasScope(site.optString("scope"), "settings.write")
                || user.optInt("userStatus", -1) != UserExt.USER_STATUS_C_VALID
                || !Role.ROLE_ID_C_ADMIN.equals(user.optString("userRole"))
                || !actor.equals(token.optString("userId"))
                || !ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(token.optString("clientId"))
                || !ApiV3Token.TYPE_ACCESS.equals(token.optString("tokenType"))
                || token.optInt("state", -1) != ApiV3Token.STATE_ACTIVE
                || token.optLong("expiresAt", 0) <= System.currentTimeMillis()
                || !ApiV3TokenMgmtService.hasScope(token.optString("scope"), "settings.write")) throw ApiV3Requests.forbidden();
        return actor;
    }

    public JSONObject list(final JSONObject identity, final int page, final int size) throws RepositoryException {
        requireAdministrator(identity, false); page(page, size);
        final var count = clients.select("SELECT COUNT(*) AS total FROM `" + clients.getName() + "` WHERE clientId<>?",
                ApiV3TokenMgmtService.WEBSITE_CLIENT);
        final var rows = clients.select("SELECT clientId,name,scope,status FROM `" + clients.getName()
                + "` WHERE clientId<>? ORDER BY clientId ASC LIMIT ? OFFSET ?", ApiV3TokenMgmtService.WEBSITE_CLIENT, size, (page-1)*size);
        final var list = new JSONArray(); for (final var row : rows) list.put(ApiV3ClientManagementDto.client(row));
        return ApiV3Requests.pagination(list, page, size, total(count));
    }

    public JSONObject detail(final JSONObject identity, final String id, final int page, final int size) throws RepositoryException {
        requireAdministrator(identity, false); ApiV3ClientManagementInputs.clientId(id); page(page, size);
        if (ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(id)) throw ApiV3Requests.notFound();
        final var rows = clients.select("SELECT clientId,name,scope,status FROM `" + clients.getName() + "` WHERE clientId=?", id);
        if (rows.isEmpty()) throw ApiV3Requests.notFound();
        if (rows.size() != 1 || !id.equals(rows.getFirst().optString("clientId"))) throw unavailable();
        final var count = redirects.select("SELECT COUNT(*) AS total FROM `" + redirects.getName() + "` WHERE clientId=?", id);
        final var callbacks = redirects.select("SELECT redirectUri,status FROM `" + redirects.getName()
                + "` WHERE clientId=? ORDER BY oId ASC LIMIT ? OFFSET ?", id, size, (page-1)*size);
        final var list = new JSONArray();
        for (final var row : callbacks) list.put(ApiV3ClientManagementDto.redirect(row));
        return ApiV3ClientManagementDto.client(rows.getFirst()).put("redirects", ApiV3Requests.pagination(list, page, size, total(count)));
    }

    public JSONObject create(final JSONObject identity, final ApiV3ClientManagementInputs.Registration input, final String key) throws Exception {
        final JSONObject viewer = identity == null ? null : identity.optJSONObject("user");
        final String actor = viewer == null ? "" : viewer.optString("oId");
        if (!actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.forbidden();
        final ApiV3ClientManagementInputs.Registration validated = ApiV3ClientManagementInputs.registration(input.normalized());
        // Every attempt, including replay, authenticates under the root before locking/reading its receipt.
        final ApiV3MutationStore guarded = new ApiV3MutationStore() {
            public Transaction beginTransaction() { return ApiV3Transactions.strict(requests.beginTransaction()); }
            public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
            public JSONObject getForUpdate(String id) throws RepositoryException {
                requireAdministrator(identity, true); return requests.getForUpdate(id);
            }
            public String add(JSONObject row) throws RepositoryException { return requests.add(row); }
            public void update(String id, JSONObject row, String... names) throws RepositoryException { requests.update(id,row,names); }
        };
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "admin.oauth.clients.create", key, validated.normalized(), () -> {
                final var value = registration.createApprovedInCurrentTransaction(validated.name(), validated.scope(), validated.redirectUris());
                return new JSONObject().put("clientId", value.clientId()).put("name", value.name()).put("scope", value.scope())
                        .put("status", ApiV3Client.STATUS_ACTIVE).put("redirectUris", new JSONArray(value.redirectUris()));
            });
        } catch (ApiV3Exception e) { throw e; }
        catch (IllegalArgumentException e) { throw ApiV3Requests.invalid(); }
        catch (Exception e) { throw unavailable(); }
    }

    private static long total(final List<JSONObject> rows) { if(rows.size()!=1)throw unavailable();return rows.getFirst().getLong("total"); }
    private static void page(final int p, final int size) { if(p<1||p>Integer.MAX_VALUE/50||size<1||size>50)throw ApiV3Requests.invalid(); }
    public static ApiV3Exception unavailable() { return new ApiV3Exception(503,"temporarily_unavailable","客户端管理暂不可用"); }
}
