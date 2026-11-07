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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.apache.logging.log4j.LogManager;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Shared legacy/v3 withdrawal: account locks, durable daily count and one complete transaction. */
@Service
public class ChatMessageWithdrawalMgmtService {
    public static final String COUNTER_GAME_ID = "sys-chat-revoke";
    @Inject private UserRepository users;
    @Inject private CloudRepository cloud;
    @Inject private ChatMessageMgmtService messages;
    @Inject private PointtransferMgmtService points;
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    public record Result(String actor, String id, int cost, String transferId, boolean privileged) { }
    protected long now() { return System.currentTimeMillis(); }

    /** Legacy root preserves the operator moderation branch. */
    public Result withdrawLegacy(final String actor, final String id) throws Exception {
        final Result result = boundary(actor, () -> {
            final Transaction transaction = ApiV3Transactions.strict(users.beginTransaction());
            try {
                final Result removed = withdrawInCurrentTransaction(actor, id, true);
                transaction.commit();
                return removed;
            } finally { if (transaction.isActive()) transaction.rollback(); }
        });
        afterWithdrawn(result);
        return result;
    }

    /** Bearer owner-only adapter; a replay returns its receipt without charging or broadcasting again. */
    public JSONObject withdraw(final JSONObject viewer, final String id, final String key) throws Exception {
        final String actor = viewer == null ? "" : viewer.optString(Keys.OBJECT_ID);
        if (!validActor(actor)) throw new ApiV3Exception(401, "unauthorized", "登录凭据无效");
        if (id == null || !id.matches("[0-9]{1,32}")) throw new ApiV3Exception(422, "invalid_request", "消息 ID 格式不合法");
        final var removed = new AtomicReference<Result>();
        final var outcome = boundary(actor, () -> idempotency.executeWithOutcome(actor, "chat.messages.withdraw:" + id, key,
                new JSONObject(), () -> {
                    final Result result = withdrawInCurrentTransaction(actor, id, false);
                    removed.set(result);
                    return new JSONObject().put("id", result.id()).put("removed", true).put("cost", result.cost());
                }));
        if (outcome.applied()) afterWithdrawn(removed.get());
        return outcome.result();
    }

    private <T> T boundary(final String actor, final Callable<T> work) throws Exception {
        if (!validActor(actor)) throw new IllegalArgumentException("withdrawal actor");
        if (users.hasTransactionBegun()) throw new IllegalStateException("withdrawal root transaction already active");
        return AccountPairLocks.execute(List.of(new AccountPairLocks.Pair(actor, Pointtransfer.ID_C_SYS)), () -> {
            try (final var caches = ArticlePublicationCaches.open(users::hasTransactionBegun)) {
                ArticlePublicationCaches.afterTransaction("chat-withdrawal:" + actor, () -> {
                    try { users.refreshSettingsCache(actor); }
                    catch (final Exception e) { users.evictSettingsCaches(actor); }
                });
                return work.call();
            }
        });
    }

    protected void afterWithdrawn(final Result result) {
        if (users.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("withdrawal delivery before commit");
        if (!result.transferId().isEmpty()) {
            try { points.indexTransferAsync(result.actor(), Pointtransfer.ID_C_SYS, result.transferId()); }
            catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Index committed chat withdrawal failed", e); }
        }
        try { ChatroomChannel.notifyChat(new JSONObject().put("type", "revoke").put("oId", result.id())); }
        catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Deliver committed chat withdrawal failed", e); }
        LogManager.getLogger(getClass()).info("chat_message_withdrawn actor={} id={} cost={}", result.actor(), result.id(), result.cost());
    }
    private static boolean validActor(final String actor) { return actor != null && actor.matches("[1-9][0-9]{0,31}"); }

    /** allowModeration is for the legacy operator entry; owner-only v3 passes false. */
    public Result withdrawInCurrentTransaction(final String actor, final String id, final boolean allowModeration) throws RepositoryException {
        if (!validActor(actor) || id == null || !id.matches("[0-9]{1,32}")) throw new IllegalArgumentException("withdrawal target");
        if (!users.hasTransactionBegun() || !ArticlePublicationCaches.active()
                || !AccountPairLocks.lock(actor, Pointtransfer.ID_C_SYS).isHeldByCurrentThread()) {
            throw new IllegalStateException("withdrawal requires owner transaction, cache boundary and prior account-pair lock");
        }
        return ApiV3ChatTransaction.write(() -> {
            final JSONObject current = users.getForUpdate(actor);
            if (current == null || current.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID) {
                throw new ApiV3Exception(404, "not_found", "用户不存在或不可用");
            }
            final boolean privileged = DataModelService.hasPermission(current.optString(User.USER_ROLE), 3);
            // Locking the owner also serializes creation when no counter row exists yet, across instances.
            final List<JSONObject> rows = privileged ? List.of() : cloud.select("SELECT oId,data FROM `" + cloud.getName()
                    + "` WHERE userId=? AND gameId=? ORDER BY oId FOR UPDATE", actor, COUNTER_GAME_ID);
            if (rows.size() > 1) throw new IllegalStateException("duplicate chat withdrawal counter");
            final long time = now();
            final LocalDate today = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
            final ChatMessageRevokeFees.Quote quote = quote(rows, today, privileged);
            final Result[] result = new Result[1];
            final boolean removed = messages.removeInCurrentTransaction(id, row -> {
                ChatMessageRemovalPolicy.requireRemovable(row, actor, allowModeration && privileged);
                String transfer = "";
                if (quote.counted()) {
                    // Preserve the zero-cost first ledger entry and the existing balance gate.
                    if (current.optInt(UserExt.USER_POINT) < quote.cost()) throw insufficient();
                    transfer = points.transferInCurrentTransaction(actor, Pointtransfer.ID_C_SYS,
                            Pointtransfer.TRANSFER_TYPE_C_CHAT_ROOM_REVOKE, quote.cost(), "", time, "", PointtransferSource.EMPTY, false);
                    if (transfer == null) throw insufficient();
                    final JSONObject record = new JSONObject().put("userId", actor).put("gameId", COUNTER_GAME_ID)
                            .put("data", new JSONObject().put("day", quote.day().toString()).put("count", quote.nextCount()).toString());
                    if (rows.isEmpty()) cloud.add(record);
                    else cloud.update(rows.getFirst().getString(Keys.OBJECT_ID), record);
                }
                result[0] = new Result(actor, id, quote.cost(), transfer, privileged);
            });
            if (!removed) throw new ApiV3Exception(404, "not_found", "消息不存在或已撤回");
            return result[0];
        });
    }

    private static ChatMessageRevokeFees.Quote quote(final List<JSONObject> rows, final LocalDate today, final boolean privileged) {
        if (rows.isEmpty()) return ChatMessageRevokeFees.quote(null, 0, today, privileged);
        try {
            final String raw = rows.getFirst().getString("data");
            if (raw.length() > 256) throw new IllegalArgumentException("counter length");
            final JSONObject value = ApiV3Json.parse(raw.getBytes(StandardCharsets.UTF_8));
            if (!value.keySet().equals(Set.of("day", "count")) || !(value.get("day") instanceof String)
                    || !(value.get("count") instanceof Integer)) throw new IllegalArgumentException("counter shape");
            final LocalDate day = LocalDate.parse(value.getString("day"));
            if (day.isAfter(today)) throw new IllegalArgumentException("future counter day");
            return ChatMessageRevokeFees.quote(day, value.getInt("count"), today, false);
        } catch (final ApiV3Exception e) { throw e; }
        catch (final RuntimeException e) { throw new IllegalStateException("invalid chat withdrawal counter", e); }
    }
    private static ApiV3Exception insufficient() { return new ApiV3Exception(409, "insufficient_points", "积分不足，无法撤回消息"); }
}
