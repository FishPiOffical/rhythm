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
import org.b3log.symphony.util.CommentRemovalQueries;
import org.json.JSONObject;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Shared article-vote transition. Comment votes have their own target-locking service. */
@Service
public class VoteMgmtService {
    @Inject private VoteRepository voteRepository;
    @Inject private ArticleRepository articleRepository;
    @Inject private TagArticleRepository tagArticleRepository;
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private ArticleMgmtService articleWrites;
    @Inject private NotificationMgmtService notifications;
    @Inject private LivenessMgmtService livenessMgmtService;
    public record Result(int previous, int current, boolean changed, String recipient, boolean notified, int upCount, int downCount) { }
    public static final class Rejected extends RuntimeException {
        public final String code;
        public Rejected(final String code) { super(code); this.code = code; }
    }
    @FunctionalInterface public interface TargetCheck { void check(JSONObject actor, JSONObject article) throws Exception; }

    public Result toggle(final String actor, final String id, final int choice, final boolean notifyUp) throws Exception {
        return run(actor, id, choice, true, notifyUp);
    }
    private Result run(final String actor, final String id, final int choice, final boolean toggle, final boolean notifyUp) throws Exception {
        final String author = articleRepository.getCommentPublicationAuthor(id);
        if (author.isBlank()) throw new Rejected("not_found");
        return articleWrites.withPublicationLock(actor, () -> {
            final Transaction transaction = articleRepository.beginTransaction();
            try {
                final Result result = setInCurrentTransaction(actor, id, author, choice, toggle, notifyUp, (a,b) -> { });
                transaction.commit(); return result;
            } finally { if (transaction.isActive()) transaction.rollback(); }
        }, result -> afterVote(actor, result));
    }
    // Keep the Java entry signatures, but do not open a transaction before the shared lock boundary.
    public void voteUp(final String actor, final String id, final int dataType) { compatibility(actor, id, dataType, Vote.TYPE_C_UP); }
    public void voteDown(final String actor, final String id, final int dataType) { compatibility(actor, id, dataType, Vote.TYPE_C_DOWN); }
    public void voteCancel(final String actor, final String id, final int dataType) { compatibility(actor, id, dataType, -1); }
    private void compatibility(final String actor, final String id, final int dataType, final int choice) {
        if (dataType != Vote.DATA_TYPE_C_ARTICLE) throw new IllegalArgumentException("Use CommentVoteMgmtService for comment votes");
        try { run(actor, id, choice, false, false); }
        catch (final RuntimeException e) { throw e; }
        catch (final Exception e) { throw new IllegalStateException("Article vote failed", e); }
    }

    public Result setInCurrentTransaction(final String actor, final String id, final String expectedAuthor, final int choice,
                                          final boolean toggle, final boolean notifyUp, final TargetCheck check) throws Exception {
        Objects.requireNonNull(check);
        if (choice != -1 && choice != Vote.TYPE_C_UP && choice != Vote.TYPE_C_DOWN) throw new IllegalArgumentException("invalid article vote");
        if (!articleRepository.hasTransactionBegun() || !ArticlePublicationCaches.active()
                || !AccountPairLocks.lock(actor, Pointtransfer.ID_C_SYS).isHeldByCurrentThread()) throw new IllegalStateException("article vote transaction and account lock required");
        if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
        for (final String participant : new TreeSet<>(List.of(actor, expectedAuthor))) users.getForUpdate(participant);
        final var voter = users.getForUpdate(actor);
        if (null == voter || voter.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) throw new Rejected("forbidden");
        if (null == users.getForUpdate(expectedAuthor)) throw new Rejected("not_found");
        final var article = articleRepository.getForUpdate(id);
        if (null == article) throw new Rejected("not_found");
        if (!expectedAuthor.equals(article.optString(Article.ARTICLE_AUTHOR_ID))) throw new Rejected("target_changed");
        check.check(voter, article);
        if (actor.equals(expectedAuthor) && !Role.ROLE_ID_C_ADMIN.equals(voter.optString(User.USER_ROLE))) throw new Rejected("self");
        final var previous = voteRepository.select("SELECT oId,`type` FROM `" + voteRepository.getName() + "` WHERE userId=? AND dataId=? AND dataType=? FOR UPDATE", actor, id, Vote.DATA_TYPE_C_ARTICLE);
        if (previous.size() > 1) throw new IllegalStateException("duplicate stored article votes");
        final int old = previous.isEmpty() ? -1 : previous.getFirst().getInt(Vote.TYPE);
        if (old != -1 && old != Vote.TYPE_C_UP && old != Vote.TYPE_C_DOWN) throw new IllegalStateException("invalid stored article vote");
        final int next = toggle && old == choice ? -1 : choice;
        int ups = article.optInt(Article.ARTICLE_GOOD_CNT), downs = article.optInt(Article.ARTICLE_BAD_CNT);
        if (ups < 0 || downs < 0) throw new IllegalStateException("negative stored article vote count");
        if (old == next) return new Result(old, next, false, expectedAuthor, false, ups, downs);
        if (old == Vote.TYPE_C_UP) ups = Math.max(0, ups - 1);
        if (old == Vote.TYPE_C_DOWN) downs = Math.max(0, downs - 1);
        if (next == Vote.TYPE_C_UP) ups = Math.addExact(ups, 1);
        if (next == Vote.TYPE_C_DOWN) downs = Math.addExact(downs, 1);
        for (final var row : previous) voteRepository.remove(row.getString(Keys.OBJECT_ID));
        if (next != -1) voteRepository.add(new JSONObject().put(Vote.USER_ID, actor).put(Vote.DATA_ID, id).put(Vote.DATA_TYPE, Vote.DATA_TYPE_C_ARTICLE).put(Vote.TYPE, next));
        article.put(Article.ARTICLE_GOOD_CNT, ups).put(Article.ARTICLE_BAD_CNT, downs).put(Article.REDDIT_SCORE, score(ups, downs, article.optLong(Keys.OBJECT_ID)/1000));
        articleRepository.update(id, article, Article.ARTICLE_GOOD_CNT, Article.ARTICLE_BAD_CNT, Article.REDDIT_SCORE);
        for (final var relation : tagArticleRepository.select(CommentRemovalQueries.relations(tagArticleRepository.getName()), id)) {
            relation.put(Article.REDDIT_SCORE, article.getDouble(Article.REDDIT_SCORE));
            tagArticleRepository.update(relation.getString(Keys.OBJECT_ID), relation, Article.REDDIT_SCORE);
        }
        final boolean notified = notifyUp && next == Vote.TYPE_C_UP && !actor.equals(expectedAuthor)
                && notifications.addArticleVoteNotificationInCurrentTransaction(expectedAuthor, id, actor);
        return new Result(old, next, true, expectedAuthor, notified, ups, downs);
    }
    private static double score(final int ups, final int downs, final long time) {
        final long difference = (long) ups - downs;
        // Preserve the existing integral time component rather than changing ranking behavior.
        return Math.log10(Math.max(Math.abs(difference), 1L)) + Long.compare(difference, 0) * ((time-1353745196L)/45000L);
    }
    public void afterVote(final String actor, final Result result) {
        if (articleRepository.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("article vote effects before commit");
        if (result.notified()) {
            try { notifications.publishReadStateRefresh(result.recipient()); }
            catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Refresh committed article vote failed", e); }
        }
        if (result.changed() && result.current() != -1) {
            try { livenessMgmtService.incLiveness(actor, Liveness.LIVENESS_VOTE); }
            catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Record committed article vote activity failed", e); }
        }
    }
}
