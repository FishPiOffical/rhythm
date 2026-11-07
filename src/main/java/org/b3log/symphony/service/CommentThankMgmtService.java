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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.AccountPairLocks;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Comment thanks, including payment and notification, join one caller-owned transaction. */
@Service
public class CommentThankMgmtService {
    @Inject private CommentRepository comments;
    @Inject private ArticleRepository articles;
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private RewardRepository rewards;
    @Inject private PointtransferMgmtService points;
    @Inject private NotificationMgmtService notifications;
    @Inject private LivenessMgmtService liveness;
    @Inject private ArticleMgmtService articleWrites;

    public record Target(String id, String author, String article) { }
    public record Result(String actor, String recipient, String transferId, boolean applied) { }
    public static final class Rejected extends RuntimeException {
        public final String code;
        public Rejected(final String code) { super(code); this.code = code; }
    }
    @FunctionalInterface public interface TargetCheck {
        void check(JSONObject actor, JSONObject article, JSONObject comment) throws Exception;
    }

    /** Projection only: used before acquiring the full account-pair set, never as authorization. */
    public Target target(final String id) throws Exception {
        final var rows = comments.select("SELECT oId,commentAuthorId,commentOnArticleId FROM `" + comments.getName() + "` WHERE oId=? LIMIT 1", id);
        if (rows.isEmpty()) return null;
        final var row = rows.getFirst();
        return new Target(row.getString(Keys.OBJECT_ID), row.optString(Comment.COMMENT_AUTHOR_ID), row.optString(Comment.COMMENT_ON_ARTICLE_ID));
    }

    /** Legacy missing/invalid targets remain no-ops; caller preserves its existing response. */
    public void thank(final String actor, final String id) throws Exception {
        final Target expected = target(id);
        if (null == expected) return;
        articleWrites.withPublicationLocks(actor, List.of(Pointtransfer.ID_C_SYS, expected.author()), () -> {
            final Transaction transaction = comments.beginTransaction();
            try {
                final Result result = thankInCurrentTransaction(actor, expected, (sender, article, comment) -> { });
                transaction.commit();
                return result;
            } finally { if (transaction.isActive()) transaction.rollback(); }
        }, this::afterThanked);
    }

    public Result thankInCurrentTransaction(final String actor, final Target expected, final TargetCheck check) throws Exception {
        Objects.requireNonNull(expected); Objects.requireNonNull(check);
        if (!comments.hasTransactionBegun() || !ArticlePublicationCaches.active()) throw new IllegalStateException("comment thanks transaction required");
        if (!AccountPairLocks.lock(actor, Pointtransfer.ID_C_SYS).isHeldByCurrentThread()
                || !AccountPairLocks.lock(actor, expected.author()).isHeldByCurrentThread()) throw new IllegalStateException("comment thanks account-pair locks required before transaction");
        if (Symphonys.POINT_THANK_COMMENT < 0) throw new IllegalStateException("negative comment thank fee");
        if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
        for (final String participant : new TreeSet<>(List.of(actor, expected.author()))) users.getForUpdate(participant);
        final JSONObject sender = users.getForUpdate(actor), receiver = users.getForUpdate(expected.author());
        if (null == sender || null == receiver || sender.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID
                || receiver.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) return null;
        final JSONObject article = articles.getForUpdate(expected.article());
        if (null == article) return null;
        // get() bypasses caches and locks the target inside the publication boundary.
        final JSONObject comment = comments.get(expected.id());
        if (null == comment || comment.optInt(Comment.COMMENT_STATUS) == Comment.COMMENT_STATUS_C_INVALID) return null;
        if (!expected.author().equals(comment.optString(Comment.COMMENT_AUTHOR_ID))
                || !expected.article().equals(comment.optString(Comment.COMMENT_ON_ARTICLE_ID))) throw new Rejected("target_changed");
        check.check(sender, article, comment);
        if (actor.equals(expected.author())) throw new Rejected("self");
        // Current read under the target lock: old query helpers suppress errors and are unsuitable here.
        if (!rewards.select("SELECT oId FROM `" + rewards.getName() + "` WHERE senderId=? AND dataId=? AND `type`=? LIMIT 1 FOR UPDATE",
                actor, expected.id(), Reward.TYPE_C_COMMENT).isEmpty()) return new Result(actor, expected.author(), null, false);
        final String rewardId = Ids.genTimeMillisId();
        String transferId = null;
        if (comment.optInt(Comment.COMMENT_ANONYMOUS) == Comment.COMMENT_ANONYMOUS_C_PUBLIC) {
            transferId = points.transferInCurrentTransaction(actor, expected.author(), Pointtransfer.TRANSFER_TYPE_C_COMMENT_REWARD,
                    Symphonys.POINT_THANK_COMMENT, rewardId + "-" + expected.article(), System.currentTimeMillis(), "", PointtransferSource.EMPTY, false);
            if (null == transferId || transferId.isBlank()) throw new Rejected("transfer_failed");
        }
        comment.put(Comment.COMMENT_THANK_CNT, Math.addExact(comment.optInt(Comment.COMMENT_THANK_CNT), 1));
        comments.update(expected.id(), comment, Comment.COMMENT_THANK_CNT);
        rewards.add(new JSONObject().put(Keys.OBJECT_ID, rewardId).put(Reward.SENDER_ID, actor)
                .put(Reward.DATA_ID, expected.id()).put(Reward.TYPE, Reward.TYPE_C_COMMENT));
        notifications.addCommentRewardNotificationInCurrentTransaction(expected.author(), rewardId, Notification.DATA_TYPE_C_POINT_COMMENT_THANK);
        return new Result(actor, expected.author(), transferId, true);
    }

    public void afterThanked(final Result result) {
        if (comments.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("comment thanks effects before commit");
        if (null == result || !result.applied()) return;
        if (null != result.transferId()) {
            try { points.indexTransferAsync(result.actor(), result.recipient(), result.transferId()); }
            catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Index committed comment thanks failed", e); }
        }
        try { notifications.publishReadStateRefresh(result.recipient()); }
        catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Refresh committed comment thanks failed", e); }
        try { liveness.incLiveness(result.actor(), Liveness.LIVENESS_THANK); }
        catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Record committed comment thanks activity failed", e); }
    }
}
