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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.event.Event;
import org.b3log.latke.event.EventManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Transactional;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.event.EventTypes;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.CommentEditing;
import org.b3log.symphony.util.CommentPublishPoints;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.Runes;
import org.b3log.symphony.util.ShuiTieDetector;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * Comment management service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.15.0.5, Apr 1, 2019
 * @since 0.2.0
 */
@Service
public class CommentMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(CommentMgmtService.class);

    /**
     * Revision repository.
     */
    @Inject
    private RevisionRepository revisionRepository;

    /**
     * Comment repository.
     */
    @Inject
    private CommentRepository commentRepository;

    /**
     * Article repository.
     */
    @Inject
    private ArticleRepository articleRepository;

    @Inject private ArticleMgmtService articleMgmtService;
    @Inject private NotificationStateMgmtService notificationStateMgmtService;
    @Inject private FollowMgmtService followMgmtService;

    /**
     * Option repository.
     */
    @Inject
    private OptionRepository optionRepository;

    /**
     * Tag repository.
     */
    @Inject
    private TagRepository tagRepository;

    /**
     * Tag-Article repository.
     */
    @Inject
    private TagArticleRepository tagArticleRepository;

    /**
     * User repository.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * Notification repository.
     */
    @Inject
    private NotificationRepository notificationRepository;

    /**
     * Event manager.
     */
    @Inject
    private EventManager eventManager;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * Reward query service.
     */
    @Inject
    private RewardQueryService rewardQueryService;

    /**
     * Notification management service.
     */
    @Inject
    private NotificationMgmtService notificationMgmtService;

    /**
     * Liveness management service.
     */
    @Inject
    private LivenessMgmtService livenessMgmtService;

    @Inject
    private ProfessionSourceEventCaptureService professionSourceEventCaptureService;

    @Inject
    private CommentThankMgmtService commentThanks;

    @Inject
    private CommentAcceptMgmtService commentAcceptance;

    /**
     * Accepts a comment specified with the given comment id.
     *
     * @param commentId target comment
     * @param actor authenticated article author
     * @throws ServiceException service exception
     */
    public void acceptComment(final String commentId, final String actor) throws ServiceException {
        try {
            commentAcceptance.accept(actor, commentId);
        } catch (final CommentAcceptMgmtService.Rejected e) {
            final String message = switch (e.code) {
                case "self" -> langPropsService.get("thankSelfLabel");
                case "forbidden" -> langPropsService.get("sc403Label");
                case "not_found" -> "Not found comment to accept";
                default -> langPropsService.get("transferFailLabel");
            };
            throw new ServiceException(message);
        } catch (final Exception e) {
            LOGGER.error("Accepts a comment failed [id={}]", commentId, e);
            throw new ServiceException(langPropsService.get("systemErrLabel"));
        }
    }

    /**
     * Removes a comment specified with the given comment id. A comment is removable if:
     * <ul>
     * <li>No replies</li>
     * <li>No ups, downs</li>
     * <li>No thanks</li>
     * </ul>
     * Sees https://github.com/b3log/symphony/issues/451 for more details.
     *
     * @param commentId the given commentId id
     * @throws ServiceException service exception
     */
    @Transactional
    public void removeComment(final String commentId) throws ServiceException {
        JSONObject comment = null;

        try {
            comment = commentRepository.get(commentId);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Gets a comment [id=" + commentId + "] failed", e);
        }

        if (null == comment) {
            return;
        }

        final int replyCnt = comment.optInt(Comment.COMMENT_REPLY_CNT);
        if (replyCnt > 0) {
            throw new ServiceException(langPropsService.get("removeCommentFoundReplyLabel"));
        }

        final int ups = comment.optInt(Comment.COMMENT_GOOD_CNT);
        final int downs = comment.optInt(Comment.COMMENT_BAD_CNT);
        if (ups > 0 || downs > 0) {
            throw new ServiceException(langPropsService.get("removeCommentFoundWatchEtcLabel"));
        }

        final int thankCnt = (int) rewardQueryService.rewardedCount(commentId, Reward.TYPE_C_COMMENT);
        if (thankCnt > 0) {
            throw new ServiceException(langPropsService.get("removeCommentFoundThankLabel"));
        }

        if (Comment.COMMENT_QNA_OFFERED_C_YES == comment.optInt(Comment.COMMENT_QNA_OFFERED)) {
            throw new ServiceException(langPropsService.get("removeCommentFoundOfferedLabel"));
        }

        // Perform removal
        removeCommentByAdmin(commentId);
    }

    /**
     * Removes a comment specified with the given comment id. Calls this method will remove all existed data related
     * with the specified comment forcibly.
     *
     * @param commentId the given comment id
     */
    @Transactional
    public void removeCommentByAdmin(final String commentId) {
        try {
            final JSONObject comment = commentRepository.get(commentId);
            if (null == comment) {
                return;
            }
            professionSourceEventCaptureService.targetRemoved("comment", commentId);
            commentRepository.removeComment(commentId);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Removes a comment error [id=" + commentId + "]", e);
            throw new IllegalStateException("删除评论时撤销职业来源失败", e);
        }
    }

    /**
     * A user specified by the given sender id thanks the author of a comment specified by the given comment id.
     *
     * @param commentId the given comment id
     * @param senderId  the given sender id
     * @throws ServiceException service exception
     */
    public void thankComment(final String commentId, final String senderId) throws ServiceException {
        try {
            commentThanks.thank(senderId, commentId);
        } catch (final CommentThankMgmtService.Rejected e) {
            final String label = "self".equals(e.code) ? "thankSelfLabel" : "transferFailLabel";
            throw new ServiceException(langPropsService.get(label));
        } catch (final Exception e) {
            LOGGER.error("Thanks a comment failed [id={}]", commentId, e);
            throw new ServiceException(langPropsService.get("systemErrLabel"));
        }
    }

    /**
     * Adds a comment with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "commentContent": "",
     *                          "commentAuthorId": "",
     *                          "commentOnArticleId": "",
     *                          "commentOriginalCommentId": "", // optional
     *                          "commentAuthorName": "" // If from client
     *                          "commentIP": "", // optional, default to ""
     *                          "commentUA": "", // optional, default to ""
     *                          "commentAnonymous": int, // optional, default to 0 (public)
     *                          "commentVisible": int, // optional, default to 0 (all)
     *                          "userCommentViewMode": int
     *                          , see {@link Comment} for more details
     * @return generated comment id
     * @throws ServiceException service exception
     */
    public String addComment(final JSONObject request) throws ServiceException {
        try {
            final String articleId = request.optString(Comment.COMMENT_ON_ARTICLE_ID);
            final JSONObject target = articleRepository.get(articleId);
            if (null == target) throw new ServiceException(langPropsService.get("commentArticleErrorLabel"));
            final String actor = request.optString(Comment.COMMENT_AUTHOR_ID);
            final String expectedAuthor = target.optString(Article.ARTICLE_AUTHOR_ID);
            final var publication = articleMgmtService.withPublicationLocks(actor, List.of(Pointtransfer.ID_C_SYS, expectedAuthor), () -> {
                final Transaction tx = commentRepository.beginTransaction();
                try {
                    final var result = addCommentInCurrentTransaction(request, expectedAuthor);
                    tx.commit();
                    return result;
                } finally { if (tx.isActive()) tx.rollback(); }
            }, this::afterCommentPublished);
            return publication.comment().optString(Keys.OBJECT_ID);
        } catch (final ServiceException e) { throw e; }
        catch (final Exception e) { LOGGER.error("Adds a comment failed", e); throw new ServiceException(langPropsService.get("systemErrLabel")); }
    }

    /** Internal committed-state snapshots; never serialize these persistence objects to a client. */
    public record Publication(JSONObject comment, JSONObject article, int viewMode,
                              List<CommentPublishPoints.Payment> transfers, boolean refreshRead, boolean publicBoth) {
        public Publication { transfers = List.copyOf(transfers); }
    }

    /** The caller must hold actor/sys and actor/expectedAuthor pair locks before starting its transaction. */
    public Publication addCommentInCurrentTransaction(final JSONObject input, final String expectedAuthor) throws Exception {
        return addCommentInCurrentTransaction(input, expectedAuthor, false);
    }

    /** v3 does not copy historical paragraph snapshots into new replies or legacy broadcasts. */
    public Publication addCommentInCurrentTransaction(final JSONObject input, final String expectedAuthor,
                                                     final boolean suppressReplySnapshot) throws Exception {
        if (!commentRepository.hasTransactionBegun() || !ArticlePublicationCaches.active()) {
            throw new IllegalStateException("comment publication transaction and cache boundary required");
        }
        final JSONObject request = new JSONObject(input.toString());
        final String actor = request.optString(Comment.COMMENT_AUTHOR_ID);
        final String articleId = request.optString(Comment.COMMENT_ON_ARTICLE_ID);
        if (null == optionRepository.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
        for (final String participant : List.of(actor, expectedAuthor).stream().distinct().sorted().toList()) {
            if (null == userRepository.getForUpdate(participant)) throw new ServiceException(langPropsService.get("systemErrLabel"));
        }
        final JSONObject commenter = userRepository.getForUpdate(actor);
        if (UserExt.USER_STATUS_C_VALID != commenter.optInt(UserExt.USER_STATUS)) throw new ServiceException(langPropsService.get("userStatusInvalidLabel"));
        final JSONObject article = articleRepository.getForUpdate(articleId);
        if (null == article) throw new ServiceException(langPropsService.get("commentArticleErrorLabel"));
        if (!expectedAuthor.equals(article.optString(Article.ARTICLE_AUTHOR_ID))) throw new ServiceException("文章作者已变化，请重试");
        final long now = System.currentTimeMillis();
        if (now - commenter.optLong(UserExt.USER_LATEST_CMT_TIME) < Symphonys.MIN_STEP_CMT_TIME
                && !Role.ROLE_ID_C_ADMIN.equals(commenter.optString(User.USER_ROLE))
                && !UserExt.COM_BOT_NAME.equals(commenter.optString(User.USER_NAME))) {
            throw new ServiceException(langPropsService.get("tooFrequentCmtLabel"));
        }
        final JSONObject allow = optionRepository.get(Option.ID_C_MISC_ALLOW_ADD_COMMENT);
        if (!"0".equals(allow.optString(Option.OPTION_VALUE))) throw new ServiceException(langPropsService.get("notAllowAddCommentLabel"));
        final int anonymous = request.optInt(Comment.COMMENT_ANONYMOUS);
        final int visible = request.optInt(Comment.COMMENT_VISIBLE);
        final boolean anonymousComment = anonymous == Comment.COMMENT_ANONYMOUS_C_ANONYMOUS;
        final boolean anonymousArticle = article.optInt(Article.ARTICLE_ANONYMOUS) == Article.ARTICLE_ANONYMOUS_C_ANONYMOUS;
        final int balance = commenter.optInt(UserExt.USER_POINT);
        if (anonymousComment && balance < Symphonys.ANONYMOUS_POST_POINT) {
            throw new ServiceException(langPropsService.get("anonymousEnabelPointLabel").replace("${point}", String.valueOf(Symphonys.ANONYMOUS_POST_POINT)));
        }
        final var pointPlan = CommentPublishPoints.plan(new CommentPublishPoints.Rates(
                        Pointtransfer.TRANSFER_SUM_C_ADD_COMMENT, Pointtransfer.TRANSFER_SUM_C_ADD_SELF_ARTICLE_COMMENT,
                        Pointtransfer.TRANSFER_SUM_C_ADD_ONLY_AUTHOR_VISIBLE_ARTICLE_COMMENT, Symphonys.ANONYMOUS_POST_POINT,
                        Pointtransfer.TRANSFER_TYPE_C_ADD_COMMENT, Pointtransfer.TRANSFER_TYPE_C_ONLY_AUTHOR_VISIBLE_COMMENT),
                actor, expectedAuthor, Pointtransfer.ID_C_SYS, anonymousComment, anonymousArticle, visible == Comment.COMMENT_VISIBLE_C_AUTHOR);
        if (balance < pointPlan.requiredBalance()) throw new ServiceException(langPropsService.get("insufficientBalanceLabel"));
        final String id = Ids.genTimeMillisId();
        final String originalId = request.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID);
        boolean refreshRead = false;
        if (StringUtils.isNotBlank(originalId)) {
            final JSONObject original = commentRepository.get(originalId);
            if (null == original) throw new ServiceException(langPropsService.get("commentArticleErrorLabel"));
            original.put(Comment.COMMENT_REPLY_CNT, Math.addExact(original.optInt(Comment.COMMENT_REPLY_CNT), 1));
            commentRepository.update(originalId, original, Comment.COMMENT_REPLY_CNT);
            if (original.optInt(Comment.COMMENT_TYPE) == Comment.COMMENT_TYPE_C_PARAGRAPH) {
                request.put(Comment.COMMENT_TYPE, Comment.COMMENT_TYPE_C_PARAGRAPH)
                        .put(Comment.COMMENT_PARAGRAPH_ID, original.optString(Comment.COMMENT_PARAGRAPH_ID))
                        .put(Comment.COMMENT_PARAGRAPH_KIND, original.optString(Comment.COMMENT_PARAGRAPH_KIND))
                        .put(Comment.COMMENT_PARAGRAPH_INDEX, original.optInt(Comment.COMMENT_PARAGRAPH_INDEX, -1))
                        .put(Comment.COMMENT_PARAGRAPH_SNAPSHOT, suppressReplySnapshot ? "" : original.optString(Comment.COMMENT_PARAGRAPH_SNAPSHOT))
                        .put(Comment.COMMENT_PARAGRAPH_STATUS, original.optInt(Comment.COMMENT_PARAGRAPH_STATUS));
            }
            refreshRead = notificationStateMgmtService.markDataReadInCurrentTransaction(actor, originalId) > 0;
        }
        String content = Emotions.toAliases(request.optString(Comment.COMMENT_CONTENT)).replaceAll("\\s+$", "") + " ";
        content = content.replace(langPropsService.get("uploadingLabel", Locale.SIMPLIFIED_CHINESE), "");
        content = content.replace(langPropsService.get("uploadingLabel", Locale.US), "");
        final String ua = request.optString(Comment.COMMENT_UA);
        final JSONObject comment = new JSONObject().put(Keys.OBJECT_ID, id).put(Comment.COMMENT_AUTHOR_ID, actor)
                .put(Comment.COMMENT_ON_ARTICLE_ID, articleId).put(Comment.COMMENT_ORIGINAL_COMMENT_ID, originalId)
                .put(Comment.COMMENT_CONTENT, content).put(Comment.COMMENT_CREATE_TIME, System.currentTimeMillis())
                .put(Comment.COMMENT_SHARP_URL, "/article/" + articleId + "#" + id).put(Comment.COMMENT_STATUS, Comment.COMMENT_STATUS_C_VALID)
                .put(Comment.COMMENT_IP, request.optString(Comment.COMMENT_IP)).put(Comment.COMMENT_UA, ua.substring(0, Math.min(ua.length(), Common.MAX_LENGTH_UA)))
                .put(Comment.COMMENT_ANONYMOUS, anonymous).put(Comment.COMMENT_VISIBLE, visible)
                .put(Comment.COMMENT_THANK_CNT, 0).put(Comment.COMMENT_GOOD_CNT, 0).put(Comment.COMMENT_BAD_CNT, 0)
                .put(Comment.COMMENT_SCORE, 0D).put(Comment.COMMENT_REPLY_CNT, 0).put(Comment.COMMENT_AUDIO_URL, "")
                .put(Comment.COMMENT_QNA_OFFERED, Comment.COMMENT_QNA_OFFERED_C_NOT)
                .put(Comment.COMMENT_TYPE, request.optInt(Comment.COMMENT_TYPE, Comment.COMMENT_TYPE_C_ARTICLE))
                .put(Comment.COMMENT_PARAGRAPH_ID, request.optString(Comment.COMMENT_PARAGRAPH_ID))
                .put(Comment.COMMENT_PARAGRAPH_KIND, request.optString(Comment.COMMENT_PARAGRAPH_KIND))
                .put(Comment.COMMENT_PARAGRAPH_INDEX, request.optInt(Comment.COMMENT_PARAGRAPH_INDEX, -1))
                .put(Comment.COMMENT_PARAGRAPH_SNAPSHOT, request.optString(Comment.COMMENT_PARAGRAPH_SNAPSHOT))
                .put(Comment.COMMENT_PARAGRAPH_STATUS, request.optInt(Comment.COMMENT_PARAGRAPH_STATUS, Comment.COMMENT_PARAGRAPH_STATUS_C_ACTIVE));
        if (comment.optInt(Comment.COMMENT_TYPE) == Comment.COMMENT_TYPE_C_PARAGRAPH && !comment.optString(Comment.COMMENT_PARAGRAPH_ID).isBlank()) {
            comment.put(Comment.COMMENT_SHARP_URL, "/article/" + articleId + "?paragraph=" + comment.optString(Comment.COMMENT_PARAGRAPH_ID) + "#" + id);
        }
        article.put(Article.ARTICLE_COMMENT_CNT, Math.addExact(article.optInt(Article.ARTICLE_COMMENT_CNT), 1));
        article.put(Article.ARTICLE_LATEST_CMTER_NAME, anonymousComment ? UserExt.ANONYMOUS_USER_NAME : commenter.optString(User.USER_NAME));
        article.put(Article.ARTICLE_LATEST_CMT_TIME, now);
        articleRepository.update(articleId, article, Article.ARTICLE_COMMENT_CNT, Article.ARTICLE_LATEST_CMTER_NAME, Article.ARTICLE_LATEST_CMT_TIME);
        final var total = optionRepository.get(Option.ID_C_STATISTIC_CMT_COUNT);
        total.put(Option.OPTION_VALUE, String.valueOf(Math.addExact(total.optInt(Option.OPTION_VALUE), 1)));
        optionRepository.update(Option.ID_C_STATISTIC_CMT_COUNT, total, Option.OPTION_VALUE);
        for (final String tagTitle : article.optString(Article.ARTICLE_TAGS).split(",")) {
            final JSONObject tag = tagRepository.getByTitle(tagTitle.trim());
            tag.put(Tag.TAG_COMMENT_CNT, Math.addExact(tag.optInt(Tag.TAG_COMMENT_CNT), 1)).put(Tag.TAG_RANDOM_DOUBLE, Math.random());
            tagRepository.update(tag.optString(Keys.OBJECT_ID), tag, Tag.TAG_COMMENT_CNT, Tag.TAG_RANDOM_DOUBLE);
        }
        commenter.put(UserExt.USER_COMMENT_COUNT, Math.addExact(commenter.optInt(UserExt.USER_COMMENT_COUNT), 1));
        commenter.put(UserExt.USER_LATEST_CMT_TIME, now);
        userRepository.update(actor, commenter, UserExt.USER_COMMENT_COUNT, UserExt.USER_LATEST_CMT_TIME);
        commentRepository.add(comment);
        final var relations = tagArticleRepository.select("SELECT * FROM `" + tagArticleRepository.getName() + "` WHERE article_oId=? FOR UPDATE", articleId);
        for (final var relation : relations) {
            relation.put(Article.ARTICLE_LATEST_CMT_TIME, now).put(Article.ARTICLE_COMMENT_CNT, article.optInt(Article.ARTICLE_COMMENT_CNT));
            tagArticleRepository.update(relation.optString(Keys.OBJECT_ID), relation, Article.ARTICLE_LATEST_CMT_TIME, Article.ARTICLE_COMMENT_CNT);
        }
        revisionRepository.add(new JSONObject().put(Revision.REVISION_AUTHOR_ID, actor)
                .put(Revision.REVISION_DATA, new JSONObject().put(Comment.COMMENT_CONTENT, content).toString())
                .put(Revision.REVISION_DATA_ID, id).put(Revision.REVISION_DATA_TYPE, Revision.DATA_TYPE_C_COMMENT));
        professionSourceEventCaptureService.commentPublished(comment, article);
        if ((!actor.equals(expectedAuthor) && commenter.optInt(UserExt.USER_REPLY_WATCH_ARTICLE_STATUS) == UserExt.USER_XXX_STATUS_C_ENABLED)
                || article.optInt(Article.ARTICLE_TYPE) == Article.ARTICLE_TYPE_C_DISCUSSION) {
            followMgmtService.setInCurrentTransaction(actor, articleId, Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH, true, null);
        }
        final var transfers = CommentPublishPoints.charge(new CommentPublishPoints.Store() {
            public boolean inTransaction() { return commentRepository.hasTransactionBegun(); }
            public int lockedBalance() throws Exception { return userRepository.getForUpdate(actor).optInt(UserExt.USER_POINT); }
            public String debit(final CommentPublishPoints.Debit debit) {
                return pointtransferMgmtService.transferInCurrentTransaction(actor, debit.recipient(), debit.type(), debit.amount(),
                        id, System.currentTimeMillis(), "", PointtransferSource.EMPTY, false);
            }
        }, pointPlan);
        return new Publication(comment, articleRepository.getForUpdate(articleId), request.optInt(UserExt.USER_COMMENT_VIEW_MODE),
                transfers, refreshRead, !anonymousComment && !anonymousArticle);
    }

    public void afterCommentPublished(final Publication publication) {
        if (commentRepository.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("comment publication effects before transaction end");
        final String actor = publication.comment().optString(Comment.COMMENT_AUTHOR_ID);
        for (final var transfer : publication.transfers()) {
            try { pointtransferMgmtService.indexTransferAsync(actor, transfer.recipient(), transfer.id()); }
            catch (final RuntimeException e) { LOGGER.error("Index committed comment publication failed", e); }
        }
        if (publication.refreshRead()) {
            try { notificationMgmtService.publishReadStateRefresh(actor); }
            catch (final RuntimeException e) { LOGGER.error("Refresh committed comment reply read state failed", e); }
        }
        if (publication.publicBoth()) {
            try {
                if (ShuiTieDetector.isShuiTie(publication.comment().optString(Comment.COMMENT_CONTENT))) notificationMgmtService.addSysAnnounceShuiTieNotification(actor);
                else livenessMgmtService.incLiveness(actor, Liveness.LIVENESS_COMMENT);
            } catch (final Exception e) { LOGGER.error("Apply committed comment liveness failed", e); }
        }
        final var event = new JSONObject().put(Comment.COMMENT, publication.comment()).put(Article.ARTICLE, publication.article())
                .put(UserExt.USER_COMMENT_VIEW_MODE, publication.viewMode());
        try { eventManager.fireEventAsynchronously(new Event<>(EventTypes.ADD_COMMENT_TO_ARTICLE, event)); }
        catch (final Exception e) { LOGGER.error("Dispatch committed comment publication failed", e); }
    }

    /**
     * Updates the specified comment by the given comment id.
     *
     * @param commentId the given comment id
     * @param comment   the specified comment
     * @throws ServiceException service exception
     */
    public void updateComment(final String commentId, final JSONObject comment) throws ServiceException {
        try {
            final var result = articleMgmtService.withPublicationLock(comment.optString(Comment.COMMENT_AUTHOR_ID), () -> {
                final Transaction tx = commentRepository.beginTransaction();
                try {
                    final var edit = updateCommentInCurrentTransaction(commentId, comment);
                    tx.commit();
                    return edit;
                } finally { if (tx.isActive()) tx.rollback(); }
            }, this::afterCommentEdited);
            comment.put(Comment.COMMENT_CONTENT, result.comment().optString(Comment.COMMENT_CONTENT));
        } catch (final ServiceException e) { throw e; }
        catch (final Exception e) { LOGGER.error("Updates a comment failed [id={}]", commentId, e); throw new ServiceException(e); }
    }

    /** Join the caller's transaction. Authentication/visibility/moderation remain at the entry boundary. */
    public CommentEditing.Result updateCommentInCurrentTransaction(final String commentId, final JSONObject request) throws Exception {
        final String actor = request.optString(Comment.COMMENT_AUTHOR_ID);
        try {
            return CommentEditing.apply(new CommentEditing.Store() {
                public boolean inTransaction() { return commentRepository.hasTransactionBegun(); }
                public CommentEditing.State lockAndLoad(final String owner, final String id) throws Exception {
                    if (null == optionRepository.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
                    final var author = userRepository.getForUpdate(owner);
                    if (null == author || author.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) {
                        throw new ServiceException(langPropsService.get("userStatusInvalidLabel"));
                    }
                    final var target = commentRepository.getOwnedEditTarget(id, owner);
                    if (null == target) return null;
                    final var article = articleRepository.getForUpdate(target.optString(Comment.COMMENT_ON_ARTICLE_ID));
                    return new CommentEditing.State(commentRepository.getOwnedForUpdate(id, owner), article);
                }
                public String normalize(final String raw) {
                    String content = Emotions.toAliases(raw).replaceAll("\\s+$", "") + " ";
                    content = content.replace(langPropsService.get("uploadingLabel", Locale.SIMPLIFIED_CHINESE), "");
                    content = content.replace(langPropsService.get("uploadingLabel", Locale.US), "");
                    return Runes.removeControlChars(content);
                }
                public int balance() throws Exception { return userRepository.getForUpdate(actor).optInt(UserExt.USER_POINT); }
                public void save(final String id, final JSONObject comment) throws Exception {
                    commentRepository.update(id, comment, Comment.COMMENT_CONTENT, Comment.COMMENT_VISIBLE, Comment.COMMENT_IP, Comment.COMMENT_UA);
                }
                public void revision(final String id, final String owner, final String content) throws Exception {
                    revisionRepository.add(new JSONObject().put(Revision.REVISION_AUTHOR_ID, owner)
                            .put(Revision.REVISION_DATA, new JSONObject().put(Comment.COMMENT_CONTENT, content).toString())
                            .put(Revision.REVISION_DATA_ID, id).put(Revision.REVISION_DATA_TYPE, Revision.DATA_TYPE_C_COMMENT));
                }
                public String charge(final String id, final int fee, final long now) {
                    return pointtransferMgmtService.transferInCurrentTransaction(actor, Pointtransfer.ID_C_SYS,
                            Pointtransfer.TRANSFER_TYPE_C_UPDATE_COMMENT, fee, id, now, "", PointtransferSource.EMPTY, false);
                }
            }, actor, commentId, request, System.currentTimeMillis(), Pointtransfer.TRANSFER_SUM_C_UPDATE_COMMENT);
        } catch (final CommentEditing.Rejected e) {
            throw new ServiceException("insufficient_balance".equals(e.code) ? langPropsService.get("insufficientBalanceLabel") : "评论不存在或不可编辑");
        }
    }

    public void afterCommentEdited(final CommentEditing.Result edit) {
        if (commentRepository.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("comment edit effects before transaction end");
        final String actor = edit.comment().optString(Comment.COMMENT_AUTHOR_ID);
        for (final String transfer : edit.transfers()) {
            try { pointtransferMgmtService.indexTransferAsync(actor, Pointtransfer.ID_C_SYS, transfer); }
            catch (final RuntimeException e) { LOGGER.error("Index committed comment edit transfer failed", e); }
        }
        final var event = new JSONObject().put(Article.ARTICLE, edit.article()).put(Comment.COMMENT, edit.comment());
        try { eventManager.fireEventAsynchronously(new Event<>(EventTypes.UPDATE_COMMENT, event)); }
        catch (final Exception e) { LOGGER.error("Dispatch committed comment edit failed", e); }
    }

    /**
     * Updates the specified comment by the given comment id.
     * <p>
     * <b>Note</b>: This method just for admin console.
     * </p>
     *
     * @param commentId the given comment id
     * @param comment   the specified comment
     */
    public void updateCommentByAdmin(final String commentId, final JSONObject comment) {
        final Transaction transaction = commentRepository.beginTransaction();

        try {
            final String commentAuthorId = comment.optString(Comment.COMMENT_AUTHOR_ID);
            final JSONObject author = userRepository.get(commentAuthorId);
            if (UserExt.USER_STATUS_C_VALID != author.optInt(UserExt.USER_STATUS)) {
                return;
            }

            String content = comment.optString(Comment.COMMENT_CONTENT);
            content = Emotions.toAliases(content);
            content = content.replaceAll("\\s+$", ""); // https://github.com/b3log/symphony/issues/389
            content += " "; // in case of tailing @user
            content = content.replace(langPropsService.get("uploadingLabel", Locale.SIMPLIFIED_CHINESE), "");
            content = content.replace(langPropsService.get("uploadingLabel", Locale.US), "");
            content = Runes.removeControlChars(content);
            comment.put(Comment.COMMENT_CONTENT, content);

            commentRepository.update(commentId, comment);

            transaction.commit();
        } catch (final RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.ERROR, "Updates a comment [id=" + commentId + "] failed", e);
        }
    }
}
