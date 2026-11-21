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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Daily checkin and patch share the same owner row and bag transaction, with caches deferred. */
@Service
public class CheckinMgmtService {
    @Inject private UserRepository users;
    @Inject private PointtransferRepository transfers;
    @Inject private BagMgmtService bags;
    @Inject private PointtransferMgmtService points;
    public static final class Unavailable extends RuntimeException { }
    public record DailyResult(int amount, List<String> transferIds) { }
    protected long now() { return System.currentTimeMillis(); }
    protected int dailyAmount() {
        return new Random().nextInt(Pointtransfer.TRANSFER_SUM_C_ACTIVITY_CHECKIN_MAX)
                % (Pointtransfer.TRANSFER_SUM_C_ACTIVITY_CHECKIN_MAX - Pointtransfer.TRANSFER_SUM_C_ACTIVITY_CHECKIN_MIN + 1)
                + Pointtransfer.TRANSFER_SUM_C_ACTIVITY_CHECKIN_MIN;
    }
    public <T> T withLock(final String actor, final Callable<T> operation) throws Exception {
        return points.executeWithTransferLock(Pointtransfer.ID_C_SYS, actor, () -> {
            try (final var scope = ArticlePublicationCaches.open(users::hasTransactionBegun)) { return operation.call(); }
        });
    }
    private JSONObject owner(final String actor) throws RepositoryException {
        if (!users.hasTransactionBegun() || !ArticlePublicationCaches.active()
                || !AccountPairLocks.lock(Pointtransfer.ID_C_SYS, actor).isHeldByCurrentThread()) throw new IllegalStateException("checkin transaction, transfer lock and cache boundary required");
        final JSONObject user = users.getForUpdate(actor);
        if (null == user || user.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) throw new Unavailable();
        return user;
    }
    public JSONObject patchInCurrentTransaction(final String actor) throws RepositoryException {
        final JSONObject user = owner(actor);
        final var result = new AtomicReference<CheckinPatchMutation.Result>();
        final JSONObject bag = bags.mutateInCurrentTransaction(actor, source -> {
            final var mutation = CheckinPatchMutation.apply(user, source, Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()).toLocalDate());
            result.set(mutation); return mutation.bag();
        });
        final JSONObject changes = result.get().changes();
        users.updateColumnsInCurrentTransaction(actor, changes);
        return new JSONObject().put("itemCode", CheckinPatchMutation.CARD).put("streak", changes.getInt(UserExt.USER_CURRENT_CHECKIN_STREAK))
                .put("streakStart", changes.getInt(UserExt.USER_CURRENT_CHECKIN_STREAK_START))
                .put("streakEnd", user.getInt(UserExt.USER_CURRENT_CHECKIN_STREAK_END))
                .put("longestStreak", changes.getInt(UserExt.USER_LONGEST_CHECKIN_STREAK)).put("bag", BagMutation.dto(bag));
    }
    public DailyResult dailyInCurrentTransaction(final String actor) throws Exception {
        final JSONObject user = owner(actor);
        final long now = now(); final ZoneId zone = ZoneId.systemDefault();
        final LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        final long start = today.atStartOfDay(zone).toInstant().toEpochMilli(), end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        final long checkedAt = user.optLong(UserExt.USER_CHECKIN_TIME);
        if (checkedAt >= start && checkedAt < end) throw new Unavailable();
        if (!transfers.select("SELECT oId FROM `" + transfers.getName()
                + "` WHERE fromId=? AND toId=? AND type=? AND time>=? AND time<? ORDER BY time DESC,oId DESC LIMIT 1 FOR UPDATE",
                Pointtransfer.ID_C_SYS, actor, Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_CHECKIN, start, end).isEmpty()) throw new Unavailable();
        final var mutation = DailyCheckinMutation.apply(user, today, now);
        final int amount = dailyAmount();
        if (amount < Pointtransfer.TRANSFER_SUM_C_ACTIVITY_CHECKIN_MIN || amount > Pointtransfer.TRANSFER_SUM_C_ACTIVITY_CHECKIN_MAX) throw new IllegalStateException("invalid daily checkin amount");
        final var ids = new ArrayList<String>();
        ids.add(transfer(actor, Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_CHECKIN, amount, now));
        if (mutation.streakReward()) ids.add(transfer(actor, Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_CHECKIN_STREAK, Pointtransfer.TRANSFER_SUM_C_ACTIVITY_CHECKINT_STREAK, now));
        if (null != mutation.patchStart()) bags.mutateInCurrentTransaction(actor, bag -> bag.put(CheckinPatchMutation.START, mutation.patchStart()));
        users.updateColumnsInCurrentTransaction(actor, mutation.changes());
        return new DailyResult(amount, List.copyOf(ids));
    }
    private String transfer(final String actor, final int type, final int amount, final long now) {
        final String id = points.transferInCurrentTransaction(Pointtransfer.ID_C_SYS, actor, type, amount, actor, now, "", PointtransferSource.EMPTY, false);
        if (null == id || id.isBlank()) throw new IllegalStateException("daily checkin transfer failed");
        return id;
    }
    public int dailyLegacy(final String actor) {
        try {
            final var result = withLock(actor, () -> {
                final Transaction tx = users.beginTransaction();
                try { final var value = dailyInCurrentTransaction(actor); tx.commit(); return value; }
                finally { if (tx.isActive()) tx.rollback(); }
            });
            for (final String id : result.transferIds()) {
                try { points.indexTransferAsync(Pointtransfer.ID_C_SYS, actor, id); }
                catch (final Exception e) { LogManager.getLogger(CheckinMgmtService.class).error("Index committed checkin failed", e); }
            }
            return result.amount();
        } catch (final Unavailable e) { return -1; }
        catch (final Exception e) { LogManager.getLogger(CheckinMgmtService.class).error("Daily checkin failed", e); return -1; }
        finally { refresh(actor); }
    }
    public int patchLegacy(final String actor) {
        try {
            withLock(actor, () -> {
                final Transaction tx = users.beginTransaction();
                try { patchInCurrentTransaction(actor); tx.commit(); return null; }
                finally { if (tx.isActive()) tx.rollback(); }
            });
            return 0;
        } catch (final CheckinPatchMutation.Rejected e) { return e.reason.legacyCode; }
        catch (final Exception e) { LogManager.getLogger(CheckinMgmtService.class).error("Patch checkin failed", e); return -4; }
        finally { refresh(actor); }
    }
    public void refresh(final String actor) {
        if (users.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("checkin refresh before transaction end");
        try { users.refreshSettingsCache(actor); }
        catch (final Exception e) { users.evictSettingsCaches(actor); LogManager.getLogger(CheckinMgmtService.class).error("Refresh committed checkin failed", e); }
    }
}
