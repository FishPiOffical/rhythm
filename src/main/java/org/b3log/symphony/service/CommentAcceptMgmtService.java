package org.b3log.symphony.service;

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.AccountPairLocks;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** One article can accept one answer; article lock, payment, flag, reward and notification are atomic. */
@Service
public class CommentAcceptMgmtService {
    @Inject private CommentRepository comments;
    @Inject private ArticleRepository articles;
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private RewardRepository rewards;
    @Inject private PointtransferMgmtService points;
    @Inject private NotificationMgmtService notifications;
    @Inject private LivenessMgmtService liveness;
    @Inject private ArticleMgmtService articleWrites;
    @Inject private CommentThankMgmtService targets;

    public record Result(String actor, String recipient, String acceptedId, String transferId, boolean applied) { }

    /** V3-only eligibility, evaluated against the locked article inside the mutation callback. */
    static void requireV3Question(final JSONObject article) {
        if (article.optInt(Article.ARTICLE_TYPE) != Article.ARTICLE_TYPE_C_QNA
                || article.optInt(Article.ARTICLE_QNA_OFFER_POINT) < 20) {
            throw new ApiV3Exception(422, "invalid_question", "仅支持采纳悬赏至少20积分的问答回答");
        }
    }
    public static final class Rejected extends RuntimeException {
        public final String code;
        public Rejected(final String code) { super(code); this.code = code; }
    }

    public void accept(final String actor, final String id) throws Exception {
        final var expected = targets.target(id);
        if (null == expected) throw new Rejected("not_found");
        articleWrites.withPublicationLocks(actor, List.of(Pointtransfer.ID_C_SYS, expected.author()), () -> {
            final Transaction transaction = comments.beginTransaction();
            try {
                final Result result = acceptInCurrentTransaction(actor, expected, (sender, article, comment) -> { });
                transaction.commit();
                return result;
            } finally { if (transaction.isActive()) transaction.rollback(); }
        }, this::afterAccepted);
    }

    public Result acceptInCurrentTransaction(final String actor, final CommentThankMgmtService.Target expected,
                                              final CommentThankMgmtService.TargetCheck check) throws Exception {
        Objects.requireNonNull(expected); Objects.requireNonNull(check);
        if (!comments.hasTransactionBegun() || !ArticlePublicationCaches.active()) throw new IllegalStateException("comment acceptance transaction required");
        if (!AccountPairLocks.lock(actor, Pointtransfer.ID_C_SYS).isHeldByCurrentThread()
                || !AccountPairLocks.lock(actor, expected.author()).isHeldByCurrentThread()) throw new IllegalStateException("comment acceptance account-pair locks required before transaction");
        if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
        for (final String participant : new TreeSet<>(List.of(actor, expected.author()))) users.getForUpdate(participant);
        final var sender = users.getForUpdate(actor);
        if (null == sender || sender.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) throw new Rejected("forbidden");
        final var article = articles.getForUpdate(expected.article());
        if (null == article) throw new Rejected("not_found");
        final var comment = comments.get(expected.id());
        if (null == comment) throw new Rejected("not_found");
        if (!expected.author().equals(comment.optString(Comment.COMMENT_AUTHOR_ID))
                || !expected.article().equals(comment.optString(Comment.COMMENT_ON_ARTICLE_ID))) throw new Rejected("target_changed");
        check.check(sender, article, comment);
        if (!actor.equals(article.optString(Article.ARTICLE_AUTHOR_ID))) throw new Rejected("forbidden");
        if (actor.equals(expected.author())) throw new Rejected("self");
        // The article row serializes all candidates, even when there is no existing acceptance row.
        final var accepted = comments.select("SELECT oId FROM `" + comments.getName()
                + "` WHERE commentOnArticleId=? AND commentQnAOffered=? ORDER BY oId LIMIT 1 FOR UPDATE",
                expected.article(), Comment.COMMENT_QNA_OFFERED_C_YES);
        if (!accepted.isEmpty()) return new Result(actor, expected.author(), accepted.getFirst().getString(Keys.OBJECT_ID), null, false);
        final int offer = article.optInt(Article.ARTICLE_QNA_OFFER_POINT);
        if (offer < 0) throw new Rejected("invalid_offer");
        final String rewardId = Ids.genTimeMillisId();
        String transferId = null;
        if (comment.optInt(Comment.COMMENT_ANONYMOUS) == Comment.COMMENT_ANONYMOUS_C_PUBLIC) {
            transferId = points.transferInCurrentTransaction(actor, expected.author(), Pointtransfer.TRANSFER_TYPE_C_QNA_OFFER,
                    offer, rewardId, System.currentTimeMillis(), "", PointtransferSource.EMPTY, false);
            if (null == transferId || transferId.isBlank()) throw new Rejected("transfer_failed");
        }
        comment.put(Comment.COMMENT_QNA_OFFERED, Comment.COMMENT_QNA_OFFERED_C_YES);
        comments.update(expected.id(), comment, Comment.COMMENT_QNA_OFFERED);
        rewards.add(new JSONObject().put(Keys.OBJECT_ID, rewardId).put(Reward.SENDER_ID, actor)
                .put(Reward.DATA_ID, expected.article()).put(Reward.TYPE, Reward.TYPE_C_ACCEPT_COMMENT));
        notifications.addCommentRewardNotificationInCurrentTransaction(expected.author(), rewardId, Notification.DATA_TYPE_C_POINT_COMMENT_ACCEPT);
        return new Result(actor, expected.author(), expected.id(), transferId, true);
    }

    public void afterAccepted(final Result result) {
        if (comments.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("comment acceptance effects before commit");
        if (!result.applied()) return;
        if (null != result.transferId()) {
            try { points.indexTransferAsync(result.actor(), result.recipient(), result.transferId()); }
            catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Index committed comment acceptance failed", e); }
        }
        try { notifications.publishReadStateRefresh(result.recipient()); }
        catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Refresh committed comment acceptance failed", e); }
        try { liveness.incLiveness(result.actor(), Liveness.LIVENESS_ACCEPT_ANSWER); }
        catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Record committed comment acceptance activity failed", e); }
    }
}
