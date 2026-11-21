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
import org.b3log.latke.model.User;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.AccountPairLocks;
import org.b3log.symphony.util.CommentRemoval;
import org.b3log.symphony.util.CommentRemovalQueries;
import org.b3log.symphony.util.ContentNotificationReferences;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Shared owner deletion core; administrative forced deletion remains separate. */
@Service
public class CommentRemovalMgmtService {
    @Inject private CommentRepository comments;
    @Inject private ArticleRepository articles;
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private RewardRepository rewards;
    @Inject private VoteRepository votes;
    @Inject private ReactionRepository reactions;
    @Inject private RevisionRepository revisions;
    @Inject private NotificationRepository notifications;
    @Inject private TagRepository tags;
    @Inject private TagArticleRepository relations;
    @Inject private PointtransferMgmtService points;
    @Inject private ProfessionSourceEventCaptureService professions;
    @Inject private NotificationMgmtService notificationEvents;
    @Inject private ArticleMgmtService articleWrites;

    @FunctionalInterface public interface TargetCheck {
        void check(JSONObject actor, JSONObject article, JSONObject comment) throws Exception;
    }

    /** Legacy caller owns one complete transaction; v3 supplies its idempotency transaction instead. */
    public CommentRemoval.Result remove(final String actor, final String id, final TargetCheck check) throws Exception {
        return articleWrites.withPublicationLock(actor, () -> {
            final Transaction transaction = comments.beginTransaction();
            try {
                final var result = removeInCurrentTransaction(actor, id, check);
                transaction.commit();
                return result;
            } finally {
                if (transaction.isActive()) transaction.rollback();
            }
        }, this::afterRemoved);
    }

    /** No nested transaction, commit or external event is allowed here. */
    public CommentRemoval.Result removeInCurrentTransaction(final String actor, final String id, final TargetCheck check) throws Exception {
        Objects.requireNonNull(check, "explicit actor/target authorization required");
        if (!AccountPairLocks.lock(actor, Pointtransfer.ID_C_SYS).isHeldByCurrentThread()) {
            throw new IllegalStateException("comment removal account-pair lock required before transaction");
        }
        return CommentRemoval.apply(new CommentRemoval.Store() {
            public boolean active() { return comments.hasTransactionBegun(); }
            public CommentRemoval.State lockAndLoad(final String owner, final String targetId) throws Exception {
                if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
                final JSONObject target = comments.getOwnedEditTarget(targetId, owner);
                if (null == target) return null;
                final String articleAuthor = articles.getCommentPublicationAuthor(target.optString(Comment.COMMENT_ON_ARTICLE_ID));
                if (articleAuthor.isBlank()) return null;
                for (final String participant : new java.util.TreeSet<>(List.of(owner, articleAuthor))) users.getForUpdate(participant);
                final JSONObject author = users.getForUpdate(owner);
                if (null == author || author.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) throw new CommentRemoval.Rejected("forbidden");
                final JSONObject article = articles.getForUpdate(target.optString(Comment.COMMENT_ON_ARTICLE_ID));
                if (null == article || !articleAuthor.equals(article.optString(Article.ARTICLE_AUTHOR_ID))) return null;
                final JSONObject comment = comments.getOwnedForUpdate(targetId, owner);
                if (null == comment) return null;
                check.check(author, article, comment);
                return new CommentRemoval.State(comment, article);
            }
            public boolean hasReplies(final String targetId) throws Exception {
                return !comments.select(CommentRemovalQueries.replies(comments.getName()), targetId).isEmpty();
            }
            public boolean hasVotes(final String targetId) throws Exception {
                return !votes.select(CommentRemovalQueries.votes(votes.getName()), targetId, Vote.DATA_TYPE_C_COMMENT).isEmpty();
            }
            public boolean hasThanks(final String targetId) throws Exception { return rewards.existsForUpdate(targetId, Reward.TYPE_C_COMMENT); }
            public int balance() throws Exception { return users.getForUpdate(actor).optInt(UserExt.USER_POINT); }
            public String debit(final String targetId, final int fee, final long now) {
                return points.transferInCurrentTransaction(actor, Pointtransfer.ID_C_SYS, Pointtransfer.TRANSFER_TYPE_C_DEL_COMMENT,
                        fee, "", now, "", PointtransferSource.EMPTY, false);
            }
            public List<String> erase(final CommentRemoval.State state) throws Exception { return eraseInCurrentTransaction(state); }
        }, actor, id, Symphonys.POINT_DELETE_COMMENT, System.currentTimeMillis());
    }

    private List<String> eraseInCurrentTransaction(final CommentRemoval.State state) throws Exception {
        final JSONObject comment = state.comment(), article = state.article();
        final String id = comment.getString(Keys.OBJECT_ID), articleId = article.getString(Keys.OBJECT_ID);
        final String actor = comment.getString(Comment.COMMENT_AUTHOR_ID);
        professions.targetRemoved("comment", id);
        comments.remove(id);

        final JSONObject author = users.getForUpdate(actor);
        author.put(UserExt.USER_COMMENT_COUNT, decrement(author.optInt(UserExt.USER_COMMENT_COUNT)));
        users.update(actor, author, UserExt.USER_COMMENT_COUNT);
        article.put(Article.ARTICLE_COMMENT_CNT, decrement(article.optInt(Article.ARTICLE_COMMENT_CNT)));
        final var newest = comments.select(CommentRemovalQueries.latest(comments.getName()), articleId);
        String latestName = "";
        long latestTime = article.optLong(Keys.OBJECT_ID);
        if (!newest.isEmpty()) {
            final JSONObject latest = newest.getFirst(); latestTime = latest.optLong(Keys.OBJECT_ID);
            if (latest.optInt(Comment.COMMENT_ANONYMOUS) == Comment.COMMENT_ANONYMOUS_C_ANONYMOUS) latestName = UserExt.ANONYMOUS_USER_NAME;
            else {
                final var names = users.select(CommentRemovalQueries.userName(users.getName()), latest.optString(Comment.COMMENT_AUTHOR_ID));
                latestName = names.isEmpty() ? UserExt.ANONYMOUS_USER_NAME : names.getFirst().optString(User.USER_NAME);
            }
        }
        article.put(Article.ARTICLE_LATEST_CMT_TIME, latestTime).put(Article.ARTICLE_LATEST_CMTER_NAME, latestName);
        articles.update(articleId, article, Article.ARTICLE_COMMENT_CNT, Article.ARTICLE_LATEST_CMT_TIME, Article.ARTICLE_LATEST_CMTER_NAME);
        final JSONObject total = options.getForUpdate(Option.ID_C_STATISTIC_CMT_COUNT);
        if (null == total) throw new IllegalStateException("comment statistic option missing");
        total.put(Option.OPTION_VALUE, decrement(total.optInt(Option.OPTION_VALUE)));
        options.update(Option.ID_C_STATISTIC_CMT_COUNT, total, Option.OPTION_VALUE);
        final String parentId = comment.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID);
        if (!parentId.isBlank()) {
            final JSONObject parent = comments.get(parentId);
            if (null != parent) {
                parent.put(Comment.COMMENT_REPLY_CNT, decrement(parent.optInt(Comment.COMMENT_REPLY_CNT)));
                comments.update(parentId, parent, Comment.COMMENT_REPLY_CNT);
            }
        }
        for (final String title : article.optString(Article.ARTICLE_TAGS).split(",")) {
            if (title.isBlank()) continue;
            final JSONObject tag = tags.getByTitle(title);
            if (null == tag) continue;
            tag.put(Tag.TAG_COMMENT_CNT, decrement(tag.optInt(Tag.TAG_COMMENT_CNT))).put(Tag.TAG_RANDOM_DOUBLE, Math.random());
            tags.update(tag.getString(Keys.OBJECT_ID), tag, Tag.TAG_COMMENT_CNT, Tag.TAG_RANDOM_DOUBLE);
        }
        for (final JSONObject relation : relations.select(CommentRemovalQueries.relations(relations.getName()), articleId)) {
            relation.put(Article.ARTICLE_COMMENT_CNT, article.getInt(Article.ARTICLE_COMMENT_CNT)).put(Article.ARTICLE_LATEST_CMT_TIME, latestTime);
            relations.update(relation.getString(Keys.OBJECT_ID), relation, Article.ARTICLE_COMMENT_CNT, Article.ARTICLE_LATEST_CMT_TIME);
        }
        for (final JSONObject revision : revisions.select(CommentRemovalQueries.revisions(revisions.getName()), id, Revision.DATA_TYPE_C_COMMENT)) {
            revisions.remove(revision.getString(Keys.OBJECT_ID));
        }
        reactions.removeTargetInCurrentTransaction(Reaction.TARGET_TYPE_COMMENT, id);
        final var recipients = new LinkedHashSet<String>();
        final boolean removeMentions = articles.select(ContentNotificationReferences.oppositeTarget(articles.getName()), id).isEmpty();
        final var selection = ContentNotificationReferences.target(notifications.getName(), id, false, removeMentions);
        for (final JSONObject notification : notifications.select(selection.sql(), selection.args().toArray())) {
            final String owner = notification.optString(Notification.NOTIFICATION_USER_ID);
            if (!owner.isBlank()) recipients.add(owner);
            notifications.remove(notification.getString(Keys.OBJECT_ID));
        }
        return new ArrayList<>(recipients);
    }

    private static int decrement(final int value) { return value <= 0 ? 0 : value - 1; }

    public void afterRemoved(final CommentRemoval.Result result) {
        if (comments.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("comment removal effects before commit");
        try { points.indexTransferAsync(result.actor(), Pointtransfer.ID_C_SYS, result.transferId()); }
        catch (final RuntimeException e) { LogManager.getLogger(CommentRemovalMgmtService.class).error("Index committed comment removal failed", e); }
        for (final String recipient : result.notificationUsers()) {
            try { notificationEvents.publishReadStateRefresh(recipient); }
            catch (final RuntimeException e) { LogManager.getLogger(CommentRemovalMgmtService.class).error("Refresh committed comment removal failed", e); }
        }
        LogManager.getLogger(CommentRemovalMgmtService.class).info("comment_removed actor={} id={} article={}", result.actor(), result.id(), result.articleId());
    }
}
