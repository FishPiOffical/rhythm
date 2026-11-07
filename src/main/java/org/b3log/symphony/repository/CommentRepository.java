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
package org.b3log.symphony.repository;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.cache.CommentCache;
import org.b3log.symphony.model.*;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.CommentEditing;
import org.json.JSONObject;

import java.util.List;

/**
 * Comment repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.1.1.3, Jun 6, 2019
 * @since 0.2.0
 */
@Repository
public class CommentRepository extends AbstractRepository {

    /**
     * Comment cache.
     */
    @Inject
    private CommentCache commentCache;

    /**
     * Article repository.
     */
    @Inject
    private ArticleRepository articleRepository;

    /**
     * User repository.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * Revision repository.
     */
    @Inject
    private RevisionRepository revisionRepository;

    /**
     * Option repository.
     */
    @Inject
    private OptionRepository optionRepository;

    /**
     * Notification repository.
     */
    @Inject
    private NotificationRepository notificationRepository;

    @Inject
    private RewardRepository rewardRepository;

    @Inject
    private VoteRepository voteRepository;

    @Inject
    private ReactionRepository reactionRepository;

    @Inject private ApiV3ArticleRevisionRepository realtimeRevisions;

    /**
     * Public constructor.
     */
    public CommentRepository() {
        super(Comment.COMMENT);
    }

    @Override public String add(final JSONObject comment) throws RepositoryException {
        requireWriteTransaction();
        final String id = insertComment(comment);
        realtimeRevisions.touchInCurrentTransaction(comment.optString(Comment.COMMENT_ON_ARTICLE_ID));
        return id;
    }
    protected String insertComment(final JSONObject row) throws RepositoryException { return super.add(row); }
    protected void updateComment(final String id, final JSONObject row, final String... fields) throws RepositoryException { super.update(id,row,fields); }
    protected void removeCommentRow(final String id) throws RepositoryException { super.remove(id); }
    private void requireWriteTransaction() { if (!hasTransactionBegun()) throw new IllegalStateException("comment transaction required"); }
    private String currentArticle(final String id) throws RepositoryException {
        requireWriteTransaction();
        final var rows = select("SELECT commentOnArticleId FROM `" + getName() + "` WHERE oId=? FOR UPDATE",id);
        return rows.isEmpty() ? "" : rows.getFirst().optString(Comment.COMMENT_ON_ARTICLE_ID);
    }
    private void changed(final String before, final JSONObject row, final String... fields) throws RepositoryException {
        realtimeRevisions.touchInCurrentTransaction(before);
        if (fields.length == 0 || java.util.Arrays.asList(fields).contains(Comment.COMMENT_ON_ARTICLE_ID)) {
            final String after = row.optString(Comment.COMMENT_ON_ARTICLE_ID,before);
            if (!before.equals(after)) realtimeRevisions.touchInCurrentTransaction(after);
        }
    }

    /**
     * Removes a comment specified with the given comment id. Calls this method will remove all existed data related
     * with the specified comment forcibly.
     *
     * @param commentId the given comment id
     * @throws RepositoryException repository exception
     */
    public void removeComment(final String commentId) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("comment removal requires transaction");
        final JSONObject comment = get(commentId);
        if (null == comment) {
            return;
        }

        remove(comment.optString(Keys.OBJECT_ID));

        final String commentAuthorId = comment.optString(Comment.COMMENT_AUTHOR_ID);
        final JSONObject commenter = userRepository.get(commentAuthorId);
        int commentCount = commenter.optInt(UserExt.USER_COMMENT_COUNT) - 1;
        if (0 > commentCount) {
            commentCount = 0;
        }
        commenter.put(UserExt.USER_COMMENT_COUNT, commentCount);
        userRepository.update(commentAuthorId, commenter);

        final String articleId = comment.optString(Comment.COMMENT_ON_ARTICLE_ID);
        final JSONObject article = articleRepository.get(articleId);
        article.put(Article.ARTICLE_COMMENT_CNT, article.optInt(Article.ARTICLE_COMMENT_CNT) - 1);
        if (0 < article.optInt(Article.ARTICLE_COMMENT_CNT)) {
            final Query latestCmtQuery = new Query().
                    setFilter(new PropertyFilter(Comment.COMMENT_ON_ARTICLE_ID, FilterOperator.EQUAL, articleId)).
                    addSort(Keys.OBJECT_ID, SortDirection.DESCENDING).
                    setPage(1, 1);
            final JSONObject latestCmt = getFirst(latestCmtQuery);
            article.put(Article.ARTICLE_LATEST_CMT_TIME, latestCmt.optLong(Keys.OBJECT_ID));
            final JSONObject latestCmtAuthor = userRepository.get(latestCmt.optString(Comment.COMMENT_AUTHOR_ID));
            article.put(Article.ARTICLE_LATEST_CMTER_NAME, latestCmtAuthor.optString(User.USER_NAME));
        } else {
            article.put(Article.ARTICLE_LATEST_CMT_TIME, articleId);
            article.put(Article.ARTICLE_LATEST_CMTER_NAME, "");
        }
        articleRepository.update(articleId, article);

        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(Revision.REVISION_DATA_ID, FilterOperator.EQUAL, commentId),
                new PropertyFilter(Revision.REVISION_DATA_TYPE, FilterOperator.EQUAL, Revision.DATA_TYPE_C_COMMENT)));
        final List<JSONObject> commentRevisions = revisionRepository.getList(query);
        for (final JSONObject articleRevision : commentRevisions) {
            revisionRepository.remove(articleRevision.optString(Keys.OBJECT_ID));
        }

        final JSONObject commentCntOption = optionRepository.get(Option.ID_C_STATISTIC_CMT_COUNT);
        commentCntOption.put(Option.OPTION_VALUE, commentCntOption.optInt(Option.OPTION_VALUE) - 1);
        optionRepository.update(Option.ID_C_STATISTIC_CMT_COUNT, commentCntOption);

        final String originalCommentId = comment.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID);
        if (StringUtils.isNotBlank(originalCommentId)) {
            final JSONObject originalComment = get(originalCommentId);
            if (null != originalComment) {
                originalComment.put(Comment.COMMENT_REPLY_CNT, originalComment.optInt(Comment.COMMENT_REPLY_CNT) - 1);

                update(originalCommentId, originalComment);
            }
        }

        notificationRepository.removeContentReferences(commentId, false, articleRepository.getName());
        for (final String rewardId : rewardRepository.removeByDataId(commentId, Reward.TYPE_C_COMMENT)) {
            notificationRepository.removeRewardReferences(rewardId, Reward.TYPE_C_COMMENT);
        }
        voteRepository.removeByDataId(commentId, Vote.DATA_TYPE_C_COMMENT);
        reactionRepository.removeTargetInCurrentTransaction(Reaction.TARGET_TYPE_COMMENT, commentId);
    }

    @Override
    public void remove(final String id) throws RepositoryException {
        final String articleId = currentArticle(id);
        if (ArticlePublicationCaches.active()) {
            if (!hasTransactionBegun()) throw new IllegalStateException("removal transaction required");
            ArticlePublicationCaches.afterTransaction("comment:" + id, () -> commentCache.removeComment(id));
            removeCommentRow(id);
            realtimeRevisions.touchInCurrentTransaction(articleId);
            return;
        }
        removeCommentRow(id);
        realtimeRevisions.touchInCurrentTransaction(articleId);
        commentCache.removeComment(id);
    }

    @Override
    public JSONObject get(final String id) throws RepositoryException {
        if (ArticlePublicationCaches.active()) {
            if (!hasTransactionBegun()) throw new IllegalStateException("comment transaction required");
            final var rows = select("SELECT * FROM `" + getName() + "` WHERE oId=? FOR UPDATE", id);
            return rows.isEmpty() ? null : rows.getFirst();
        }
        JSONObject ret = commentCache.getComment(id);
        if (null != ret) {
            return ret;
        }

        ret = super.get(id);
        if (null == ret) {
            return null;
        }

        commentCache.putComment(ret);

        return ret;
    }

    public JSONObject getOwnedEditTarget(final String id, final String actor) throws RepositoryException {
        final var rows = select(CommentEditing.ownedSql(getName(), false), id, actor);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public JSONObject getOwnedForUpdate(final String id, final String actor) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("comment transaction required");
        final var rows = select(CommentEditing.ownedSql(getName(), true), id, actor);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public void update(final String id, final JSONObject comment, final String... propertyNames) throws RepositoryException {
        final String articleId = currentArticle(id);
        if (ArticlePublicationCaches.active()) {
            if (!hasTransactionBegun()) throw new IllegalStateException("comment transaction required");
            ArticlePublicationCaches.afterTransaction("comment:" + id, () -> {
                commentCache.removeComment(id);
                try { get(id); } catch (final RepositoryException e) { /* Refill on a later read. */ }
            });
            updateComment(id, comment, propertyNames);
            changed(articleId,comment,propertyNames);
            return;
        }
        updateComment(id, comment, propertyNames);
        changed(articleId,comment,propertyNames);

        comment.put(Keys.OBJECT_ID, id);
        commentCache.putComment(comment);
    }
}
