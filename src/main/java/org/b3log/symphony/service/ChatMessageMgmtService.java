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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.repository.ChatRedPacketRepository;
import org.b3log.symphony.repository.ReactionRepository;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.repository.PetActivityRepository;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.util.ApiV3ChatMessageDto;
import org.b3log.symphony.util.ApiV3PetRules;
import org.b3log.symphony.util.ApiV3ChatTransaction;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Persistence only: callers must finish authentication, validation and admission before entering. */
@Service
public class ChatMessageMgmtService {
    private static final Semaphore PET_SNAPSHOT_READS = new Semaphore(4);
    private static final long PET_SNAPSHOT_WAIT_MS = 100;
    @Inject private ChatRoomRepository messages;
    @Inject private ApiV3ChatAppearanceService appearances;
    @Inject private ChatRedPacketRepository redPackets;
    @Inject private ProfessionSourceEventCaptureService professions;
    @Inject private ReactionRepository reactions;
    @Inject private UserPetRepository pets;
    @Inject private PetActivityRepository petActivities;

    @FunctionalInterface public interface RemovalCheck { void check(JSONObject message) throws RepositoryException; }

    /** Lock the current target before policy, then bind deletion, emoji cleanup and reversals to the owner. */
    public boolean removeInCurrentTransaction(final String id, final RemovalCheck check) throws RepositoryException {
        if (!messages.hasTransactionBegun()) throw new IllegalStateException("chat removal transaction required");
        if (id == null || !id.matches("[0-9]{1,32}")) throw new IllegalArgumentException("chat removal id");
        return ApiV3ChatTransaction.write(() -> {
            final var rows = messages.select("SELECT oId,content FROM `" + messages.getName() + "` WHERE oId=? FOR UPDATE", id);
            if (rows.isEmpty()) return false;
            redPackets.requireSettledForRemoval(id);
            if (check != null) check.check(new JSONObject(rows.getFirst().toString()));
            messages.remove(id);
            reactions.removeTargetInCurrentTransaction(Reaction.TARGET_TYPE_CHAT, id);
            professions.targetRemoved("chatroom_message", id);
            return true;
        });
    }

    /** Existing callers retain their admission/fee policy; this boundary only owns durable removal. */
    public boolean removeLegacy(final String id) throws RepositoryException {
        if (messages.hasTransactionBegun()) throw new IllegalStateException("use current-transaction chat removal primitive");
        final Transaction transaction = ApiV3Transactions.strict(messages.beginTransaction());
        try {
            final boolean removed = removeInCurrentTransaction(id, null);
            transaction.commit();
            return removed;
        } finally { if (transaction.isActive()) transaction.rollback(); }
    }

    /** Message, committed-change marker and profession fact join the owner's transaction. */
    public JSONObject saveInCurrentTransaction(final JSONObject envelope, final String actor) throws RepositoryException {
        if (!messages.hasTransactionBegun()) throw new IllegalStateException("chat publication transaction required");
        if (actor == null || actor.isEmpty() || !actor.equals(envelope.optString("userOId"))) {
            throw new IllegalArgumentException("chat publication actor mismatch");
        }
        return ApiV3ChatTransaction.write(() -> {
            final JSONObject saved = new JSONObject(envelope.toString());
            saved.put("appearance", appearances.snapshotInCurrentTransaction(actor));
            saved.remove("petStatus");
            final JSONObject petStatus = ApiV3ChatMessageDto.petStatus(envelope.opt("petStatus"));
            if (petStatus != null) saved.put("petStatus", petStatus);
            saved.put(Keys.OBJECT_ID, messages.add(new JSONObject().put("content", saved.toString())));
            professions.chatroomMessagePublished(saved, actor);
            return saved;
        });
    }

    /** 发送前限时读取附加资料。慢查询占用的并发有上限，超时仍可提交正文。 */
    public JSONObject capturePetStatus(final String actor) {
        if (pets == null || petActivities == null || !PET_SNAPSHOT_READS.tryAcquire()) return null;
        final long now = System.currentTimeMillis();
        final var result = new CompletableFuture<JSONObject>();
        Thread.startVirtualThread(() -> {
            try {
                result.complete(readPetStatus(actor, now));
            } catch (final RepositoryException | RuntimeException e) {
                result.completeExceptionally(e);
            } finally {
                // 独立线程没有 HTTP 请求结束钩子，必须主动归还 JDBC 连接。
                try {
                    JdbcRepository.dispose();
                } finally {
                    PET_SNAPSHOT_READS.release();
                }
            }
        });
        try {
            return result.get(PET_SNAPSHOT_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (final TimeoutException e) {
            // 查询可能仍在执行，名额由查询线程在结束后释放。
            return null;
        } catch (final ExecutionException e) {
            LogManager.getLogger(ChatMessageMgmtService.class).debug("聊天室宠物快照读取失败", e.getCause());
            return null;
        }
    }

    private JSONObject readPetStatus(final String actor, final long now) throws RepositoryException {
        final var rows = pets.select("SELECT p.oId AS petId,p.name,p.speciesId,p.experience,p.activityKind,p.activityEndsAt,a.kind,a.endsAt"
                + " FROM `" + pets.getName() + "` p LEFT JOIN `" + petActivities.getName()
                + "` a ON a.slotKey=CONCAT('active:',p.userId) AND a.userId=p.userId AND a.petId=p.oId"
                + " WHERE p.userId=?", actor);
        if (rows.size() != 1) return null;
        final JSONObject row = rows.getFirst();
        final String state;
        if (!row.optString("kind", "").isEmpty()) {
            state = row.optLong("endsAt") <= now ? "claimable" : row.optString("kind");
        } else {
            state = "rest".equals(row.optString("activityKind")) && row.optLong("activityEndsAt") > now ? "rest" : "idle";
        }
        return ApiV3ChatMessageDto.petStatus(new JSONObject()
                .put("petId", row.optString("petId"))
                .put("name", row.optString("name"))
                .put("speciesId", row.optString("speciesId"))
                .put("state", state)
                .put("level", ApiV3PetRules.level(row.getInt("experience"))));
    }

    /** Legacy root boundary; never call this from an idempotency callback. */
    public JSONObject saveLegacy(final JSONObject envelope, final String actor) throws RepositoryException {
        if (messages.hasTransactionBegun()) throw new IllegalStateException("use current-transaction chat primitive");
        final JSONObject prepared = new JSONObject(envelope.toString());
        prepared.put("petStatus", capturePetStatus(actor));
        final Transaction transaction = ApiV3Transactions.strict(messages.beginTransaction());
        try {
            final JSONObject saved = saveInCurrentTransaction(prepared, actor);
            transaction.commit();
            return saved;
        } finally {
            if (transaction.isActive()) transaction.rollback();
        }
    }
}
