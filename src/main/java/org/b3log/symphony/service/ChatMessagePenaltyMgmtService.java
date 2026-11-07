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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.AccountPairLocks;
import org.json.JSONArray;
import org.json.JSONObject;

/** Existing repetition penalty joined to a v3 publication transaction, with no independent commit or broadcast. */
@Service
public class ChatMessagePenaltyMgmtService {
    private static final int REPEAT_POINTS = 30;
    private static final long REPEAT_MUTE_MILLIS = 15 * 60 * 1000L;
    @Inject private CloudRepository cloud;
    @Inject private UserRepository users;
    @Inject private PointtransferMgmtService points;
    @Inject private NotificationMgmtService notifications;
    public record Result(String transferId, long mutedUntil) { }
    public Result applyInCurrentTransaction(final String actor, final long now) throws RepositoryException {
        if (!cloud.hasTransactionBegun() || !AccountPairLocks.lock(actor, Pointtransfer.ID_C_SYS).isHeldByCurrentThread()) {
            throw new IllegalStateException("chat penalty requires owner transaction and account-pair lock");
        }
        final JSONObject user = users.getForUpdate(actor);
        if (user == null) throw new RepositoryException("chat penalty owner missing");
        final long until = Math.addExact(now, REPEAT_MUTE_MILLIS);
        final var existing = cloud.select("SELECT oId FROM `" + cloud.getName() + "` WHERE userId=? AND gameId=? FOR UPDATE", actor, "sys-mute");
        for (final var row : existing) cloud.remove(row.getString(Keys.OBJECT_ID));
        cloud.add(new JSONObject().put("userId", actor).put("gameId", "sys-mute").put("data", Long.toString(until)));
        recordMute(user.getString(User.USER_NAME), until, now);
        // Legacy transfers cannot overdraw: a poor account is still muted, with no fictitious deduction notice.
        String transfer = "";
        if (user.optInt(UserExt.USER_POINT) >= REPEAT_POINTS) {
            transfer = points.transferInCurrentTransaction(actor, Pointtransfer.ID_C_SYS, Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT,
                    REPEAT_POINTS, "机器人罚单-聊天室复读频率过高", now, "", PointtransferSource.EMPTY, false);
            if (transfer == null || transfer.isBlank()) throw new RepositoryException("chat repetition deduction failed");
            notifications.addAbusePointDeductNotificationInCurrentTransaction(actor, transfer);
        }
        return new Result(transfer, until);
    }
    private void recordMute(final String name, final long until, final long now) throws RepositoryException {
        final var rows = cloud.select("SELECT oId,data FROM `" + cloud.getName() + "` WHERE userId=? AND gameId=? LIMIT 2 FOR UPDATE", "si:guo", "record");
        if (rows.size() > 1) throw new RepositoryException("duplicate chat mute registry");
        final JSONArray previous;
        try { previous = rows.isEmpty() ? new JSONArray() : new JSONArray(rows.getFirst().getString("data")); }
        catch (final RuntimeException e) { throw new RepositoryException(e); }
        final JSONArray next = new JSONArray();
        for (final var value : previous) {
            if (!(value instanceof JSONObject row)) throw new RepositoryException("invalid chat mute registry");
            if (row.optLong("time") > now && !name.equals(row.optString("userName"))) {
                next.put(new JSONObject().put("userName", row.optString("userName")).put("time", row.getLong("time")));
            }
        }
        next.put(new JSONObject().put("userName", name).put("time", until));
        if (rows.isEmpty()) cloud.add(new JSONObject().put("userId", "si:guo").put("gameId", "record").put("data", next.toString()));
        else cloud.update(rows.getFirst().getString(Keys.OBJECT_ID), new JSONObject().put("data", next.toString()), "data");
    }
}
