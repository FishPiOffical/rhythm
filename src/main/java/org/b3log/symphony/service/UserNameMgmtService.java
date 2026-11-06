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

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.BagMutation;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.UserNameRules;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicReference;

/** Namespace lock precedes owner/bag locks; no independent commit or cache publication. */
@Service
public class UserNameMgmtService {
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private BagMgmtService bags;
    public static final class Rejected extends RuntimeException {
        public final String code;
        public Rejected(final String code) { super(code); this.code = code; }
    }
    public record Rename(String oldName, String newName) { }
    public record CardResult(Rename rename, JSONObject bag) { }
    public void lockNamespaceInCurrentTransaction() throws RepositoryException {
        if (!users.hasTransactionBegun()) throw new IllegalStateException("username transaction required");
        if (null == options.getForUpdate(Option.ID_C_STATISTIC_MEMBER_COUNT)) throw new IllegalStateException("member statistic option missing");
    }
    /** Registration deliberately has its own unverified-account policy; this only provides a current lookup. */
    public JSONObject registrationTargetInCurrentTransaction(final String name) throws RepositoryException {
        lockNamespaceInCurrentTransaction();
        final var rows = users.select("SELECT * FROM `" + users.getName()
                + "` WHERE userName=? ORDER BY CASE WHEN userStatus IN (?,?,?,?) THEN 0 ELSE 1 END,oId LIMIT 1 FOR UPDATE", name,
                UserExt.USER_STATUS_C_VALID, UserExt.USER_STATUS_C_INVALID, UserExt.USER_STATUS_C_INVALID_LOGIN, UserExt.USER_STATUS_C_DEACTIVATED);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private JSONObject owner(final String actor) throws RepositoryException {
        final JSONObject owner = users.getForUpdate(actor);
        if (null == owner) throw new Rejected("not_found");
        if (owner.optInt(UserExt.USER_STATUS) == UserExt.USER_STATUS_C_DEACTIVATED) throw new Rejected("deactivated");
        return owner;
    }
    private static void validate(final String name) { if (UserNameRules.invalid(name)) throw new Rejected("invalid_name"); }
    private Rename renameLocked(final String actor, final JSONObject owner, final String name) throws RepositoryException {
        if (!users.select("SELECT oId FROM `" + users.getName() + "` WHERE userName=? LIMIT 1 FOR UPDATE", name).isEmpty()) throw new Rejected("name_taken");
        users.updateColumnsInCurrentTransaction(actor, new JSONObject().put(User.USER_NAME, name));
        return new Rename(owner.getString(User.USER_NAME), name);
    }
    public Rename renameInCurrentTransaction(final String actor, final String name) throws RepositoryException {
        validate(name); lockNamespaceInCurrentTransaction();
        return renameLocked(actor, owner(actor), name);
    }
    public CardResult useCardInCurrentTransaction(final String actor, final String name) throws RepositoryException {
        return useCardInCurrentTransaction(actor, name, true);
    }
    /** Legacy callers retain their existing status policy; v3 requires an active owner. */
    private CardResult useCardInCurrentTransaction(final String actor, final String name, final boolean requireActive) throws RepositoryException {
        validate(name); lockNamespaceInCurrentTransaction();
        final JSONObject owner = owner(actor);
        if (requireActive && owner.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) throw new Rejected("not_found");
        final var result = new AtomicReference<Rename>();
        final JSONObject bag = bags.mutateInCurrentTransaction(actor, source -> {
            final var adjusted = BagMutation.adjust(source, "nameCard", -1, Integer.MAX_VALUE);
            if (adjusted.status() != 0) throw new Rejected("item_unavailable");
            try { result.set(renameLocked(actor, owner, name)); }
            catch (final RepositoryException e) { throw new WriteFailure(e); }
            return adjusted.bag();
        });
        return new CardResult(result.get(), bag);
    }
    private static final class WriteFailure extends RuntimeException { WriteFailure(final RepositoryException cause) { super(cause); } }
    /** Legacy card boundary: username and card commit together. */
    public CardResult useCardLegacy(final String actor, final String name) throws RepositoryException {
        if (users.hasTransactionBegun()) throw new IllegalStateException("use current-transaction username primitive");
        final Transaction tx = ApiV3Transactions.strict(users.beginTransaction());
        Rename rename = null;
        try {
            final var result = useCardInCurrentTransaction(actor, name, false);
            rename = result.rename(); tx.commit(); return result;
        } finally {
            if (tx.isActive()) tx.rollback();
            afterTransaction(actor, rename);
        }
    }
    /** Remove the old lookup before loading the committed row; never publish an uncommitted name. */
    public void afterTransaction(final String actor, final Rename rename) {
        if (users.hasTransactionBegun()) throw new IllegalStateException("username cache refresh before transaction end");
        try {
            if (null != rename) users.evictUserNameCaches(actor, rename.oldName());
            users.refreshSettingsCache(actor);
        } catch (final Exception e) {
            try { users.evictSettingsCaches(actor); }
            catch (final Exception ignored) { LogManager.getLogger(UserNameMgmtService.class).error("Evict username cache failed", ignored); }
            LogManager.getLogger(UserNameMgmtService.class).error("Refresh durable username failed", e);
        }
    }
}
