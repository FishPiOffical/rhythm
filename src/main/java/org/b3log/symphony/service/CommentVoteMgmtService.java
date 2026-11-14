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
import org.b3log.symphony.util.AccountPairLocks;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;
import java.util.Objects;
import java.util.List;
import java.util.TreeSet;

/** Locks the current target before changing a vote; controllers retain legacy toggle semantics. */
@Service
public class CommentVoteMgmtService {
    @Inject private CommentRepository comments;
    @Inject private ArticleRepository articles;
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private VoteRepository votes;
    @Inject private ArticleMgmtService articleWrites;
    @Inject private CommentThankMgmtService targets;
    @Inject private NotificationMgmtService notifications;
    @Inject private LivenessMgmtService liveness;
    public record Result(int previous, int current, boolean changed, String recipient, boolean notified, int upCount, int downCount) { }
    public static final class Rejected extends RuntimeException {
        public final String code;
        public Rejected(final String code) { super(code); this.code = code; }
    }

    public Result toggle(final String actor, final String id, final int choice, final boolean notifyUp) throws Exception {
        final var target = targets.target(id);
        if (null == target) throw new Rejected("not_found");
        return articleWrites.withPublicationLock(actor, () -> {
            final Transaction transaction = comments.beginTransaction();
            try {
                final Result result = setInCurrentTransaction(actor, target, choice, true, notifyUp, (a,b,c) -> { });
                transaction.commit(); return result;
            } finally { if (transaction.isActive()) transaction.rollback(); }
        }, result -> afterVote(actor, result));
    }

    /** choice=-1 clears; toggle is only for the old page/API operation. */
    public Result setInCurrentTransaction(final String actor, final CommentThankMgmtService.Target target, final int choice,
                                          final boolean toggle, final boolean notifyUp, final CommentThankMgmtService.TargetCheck check) throws Exception {
        Objects.requireNonNull(target); Objects.requireNonNull(check);
        if (choice != -1 && choice != Vote.TYPE_C_UP && choice != Vote.TYPE_C_DOWN) throw new IllegalArgumentException("invalid comment vote");
        if (!comments.hasTransactionBegun() || !ArticlePublicationCaches.active()
                || !AccountPairLocks.lock(actor, Pointtransfer.ID_C_SYS).isHeldByCurrentThread()) throw new IllegalStateException("comment vote transaction and account lock required");
        if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
        final String articleAuthor = articles.getCommentPublicationAuthor(target.article());
        if (articleAuthor.isBlank()) throw new Rejected("not_found");
        for (final String participant : new TreeSet<>(List.of(actor, target.author(), articleAuthor))) users.getForUpdate(participant);
        final JSONObject voter = users.getForUpdate(actor);
        if (null == voter || voter.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) throw new Rejected("forbidden");
        final var article = articles.getForUpdate(target.article());
        final var comment = comments.get(target.id());
        if (null == article || null == comment) throw new Rejected("not_found");
        if (!articleAuthor.equals(article.optString(Article.ARTICLE_AUTHOR_ID))) throw new Rejected("target_changed");
        if (!target.article().equals(comment.optString(Comment.COMMENT_ON_ARTICLE_ID))
                || !target.author().equals(comment.optString(Comment.COMMENT_AUTHOR_ID))) throw new Rejected("target_changed");
        check.check(voter, article, comment);
        if (actor.equals(target.author()) && !Role.ROLE_ID_C_ADMIN.equals(voter.optString(User.USER_ROLE))) throw new Rejected("self");
        final var previous = votes.select("SELECT oId,`type` FROM `" + votes.getName() + "` WHERE userId=? AND dataId=? AND dataType=? FOR UPDATE",
                actor, target.id(), Vote.DATA_TYPE_C_COMMENT);
        if (previous.size() > 1) throw new IllegalStateException("duplicate stored comment votes");
        final int old = previous.isEmpty() ? -1 : previous.getFirst().getInt(Vote.TYPE);
        if (old != -1 && old != Vote.TYPE_C_UP && old != Vote.TYPE_C_DOWN) throw new IllegalStateException("invalid stored comment vote");
        final int next = toggle && old == choice ? -1 : choice;
        int ups = comment.optInt(Comment.COMMENT_GOOD_CNT), downs = comment.optInt(Comment.COMMENT_BAD_CNT);
        if (ups < 0 || downs < 0) throw new IllegalStateException("negative stored comment vote count");
        if (old == next) return new Result(old, next, false, target.author(), false, ups, downs);
        if (old == Vote.TYPE_C_UP) ups = Math.max(0, ups - 1);
        if (old == Vote.TYPE_C_DOWN) downs = Math.max(0, downs - 1);
        if (next == Vote.TYPE_C_UP) ups = Math.addExact(ups, 1);
        if (next == Vote.TYPE_C_DOWN) downs = Math.addExact(downs, 1);
        for (final var row : previous) votes.remove(row.getString(Keys.OBJECT_ID));
        if (next != -1) votes.add(new JSONObject().put(Vote.USER_ID, actor).put(Vote.DATA_ID, target.id())
                .put(Vote.DATA_TYPE, Vote.DATA_TYPE_C_COMMENT).put(Vote.TYPE, next));
        comment.put(Comment.COMMENT_GOOD_CNT, ups).put(Comment.COMMENT_BAD_CNT, downs).put(Comment.COMMENT_SCORE, score(ups, downs));
        comments.update(target.id(), comment, Comment.COMMENT_GOOD_CNT, Comment.COMMENT_BAD_CNT, Comment.COMMENT_SCORE);
        final boolean notified = notifyUp && next == Vote.TYPE_C_UP && !actor.equals(target.author())
                && notifications.addCommentVoteNotificationInCurrentTransaction(target.author(), target.id(), actor);
        return new Result(old, next, true, target.author(), notified, ups, downs);
    }

    private static double score(final int ups, final int downs) {
        final double n = (double) ups + downs;
        if (n == 0) return 0;
        final double z = 1.281551565545, p = ups / n;
        return (p + z*z/(2*n) - z*Math.sqrt((p*(1-p)+z*z/(4*n))/n))/(1+z*z/n);
    }
    public void afterVote(final String actor, final Result result) {
        if (comments.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("comment vote effects before commit");
        if (result.notified()) {
            try { notifications.publishReadStateRefresh(result.recipient()); }
            catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Refresh committed comment vote failed", e); }
        }
        if (result.changed() && result.current() != -1) {
            try { liveness.incLiveness(actor, Liveness.LIVENESS_VOTE); }
            catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Record committed comment vote activity failed", e); }
        }
    }
}
