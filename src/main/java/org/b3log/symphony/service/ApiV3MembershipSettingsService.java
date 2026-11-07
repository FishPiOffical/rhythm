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
import org.b3log.symphony.cache.MembershipCache;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.MembershipLevelRepository;
import org.b3log.symphony.repository.MembershipRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MembershipConfiguration;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;
import java.math.BigDecimal;

/** Display-only membership updates; never changes entitlement fields, expiry, state or tier. */
@Service
public class ApiV3MembershipSettingsService {
    @Inject private UserRepository users;
    @Inject private MembershipRepository memberships;
    @Inject private MembershipLevelRepository levels;
    @Inject private ApiV3RequestRepository requests;
    @Inject private MembershipCache cache;

    public JSONObject update(final String actor, final JSONObject body, final String key) throws Exception {
        id(actor);
        final JSONObject changes = ApiV3MembershipConfiguration.validateChanges(body);
        try {
            final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                public Transaction beginTransaction() { return ApiV3Transactions.strict(requests.beginTransaction()); }
                public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
                public JSONObject getForUpdate(final String receipt) throws RepositoryException { owner(actor); return requests.getForUpdate(receipt); }
                public String add(final JSONObject row) throws RepositoryException { return requests.add(row); }
                public void update(final String receipt, final JSONObject row, final String... fields) throws RepositoryException { requests.update(receipt,row,fields); }
            };
            return ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "membership.config", key, changes,
                    () -> updateInCurrentTransaction(actor, changes));
        } catch (final ApiV3Exception e) { throw e; }
        catch (final Exception e) { throw ApiV3MembershipConfiguration.unavailable(); }
        finally {
            // Invalidate on rollback/uncertain acknowledgement/replay too; never publish a transaction snapshot.
            if (!requests.hasTransactionBegun()) { try { cache.remove(actor); } catch (final RuntimeException ignored) { } }
        }
    }
    public JSONObject updateInCurrentTransaction(final String actor, final JSONObject body) throws Exception {
        id(actor);
        final JSONObject changes = ApiV3MembershipConfiguration.validateChanges(body);
        if (!requests.hasTransactionBegun()) throw new IllegalStateException("membership config transaction required");
        return ApiV3TransactionSavepoint.run(() -> {
            owner(actor);
            final var hint = memberships.select("SELECT lvCode FROM `"+memberships.getName()+"` WHERE userId=? LIMIT 2",actor);
            if (hint.isEmpty()) throw ApiV3Requests.notFound();
            if (hint.size()!=1 || !(hint.getFirst().opt("lvCode") instanceof String code) || code.isBlank() || code.length()>32)
                throw ApiV3MembershipConfiguration.unavailable();
            final var definitions = levels.select("SELECT oId,lvCode,benefits FROM `"+levels.getName()+"` WHERE lvCode=? ORDER BY oId ASC LIMIT 65 FOR UPDATE",code);
            if (definitions.isEmpty() || definitions.size()>64) throw ApiV3MembershipConfiguration.unavailable();
            JSONObject definition = null, display = null;
            for (final JSONObject row : definitions) {
                if (!code.equals(row.optString("lvCode"))) throw ApiV3MembershipConfiguration.unavailable();
                final JSONObject candidate = ApiV3MembershipConfiguration.displayDefinition(row);
                if (display != null && !display.similar(candidate)) throw ApiV3MembershipConfiguration.unavailable();
                if (definition == null) { definition = row; display = candidate; }
            }
            final var rows = memberships.select("SELECT oId,userId,lvCode,state,expiresAt,configJson FROM `"+memberships.getName()+"` WHERE userId=? LIMIT 2 FOR UPDATE",actor);
            if (rows.isEmpty()) throw ApiV3Requests.notFound();
            if (rows.size()!=1) throw ApiV3MembershipConfiguration.unavailable();
            final JSONObject current = rows.getFirst();
            if (!actor.equals(current.optString("userId")) || !code.equals(current.optString("lvCode"))) throw ApiV3MembershipConfiguration.unavailable();
            final long state = integer(current.opt("state")), expiry = integer(current.opt("expiresAt"));
            if (state != 1 || expiry != 0 && expiry <= now()) throw ApiV3Requests.notFound();
            if (!(current.opt("configJson") instanceof String config)) throw ApiV3MembershipConfiguration.unavailable();
            final JSONObject merged = ApiV3MembershipConfiguration.merge(definition,config,changes);
            final int count=ApiV3TransactionStatements.execute("UPDATE `"+memberships.getName()+"` SET configJson=?,updatedAt=? WHERE oId=? AND userId=?",merged.toString(),now(),current.getString("oId"),actor);
            if(count!=1) throw ApiV3MembershipConfiguration.unavailable();
            return new JSONObject().put("config",ApiV3MembershipConfiguration.publicConfig(merged));
        });
    }
    protected long now() { return System.currentTimeMillis(); }
    private void owner(final String actor) throws RepositoryException {
        final var rows=users.select("SELECT oId,userStatus FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",actor);
        if(rows.size()!=1 || !actor.equals(rows.getFirst().optString("oId")) || integer(rows.getFirst().opt("userStatus"))!=UserExt.USER_STATUS_C_VALID)
            throw ApiV3Requests.notFound();
    }
    private static long integer(final Object value) {
        if(!(value instanceof Number number)) throw ApiV3MembershipConfiguration.unavailable();
        try { final long n=new BigDecimal(number.toString()).longValueExact();if(n<0)throw ApiV3MembershipConfiguration.unavailable();return n; }
        catch(final ArithmeticException|NumberFormatException e){throw ApiV3MembershipConfiguration.unavailable();}
    }
    private static void id(final String actor) { if(actor==null || !actor.matches("[1-9][0-9]{0,18}"))throw ApiV3Requests.invalid(); }
}
