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
package org.b3log.symphony.processor;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Paginator;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.PermissionMidware;
import org.b3log.symphony.processor.middleware.validate.CommentAddValidationMidware;
import org.b3log.symphony.processor.middleware.validate.CommentUpdateValidationMidware;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Comment processor.
 * <ul>
 * <li>Adds a comment (/comment) <em>locally</em>, POST</li>
 * <li>Updates a comment (/comment/{id}) <em>locally</em>, PUT</li>
 * <li>Gets a comment's content (/comment/{id}/content), GET</li>
 * <li>Thanks a comment (/comment/thank), POST</li>
 * <li>Gets a comment's replies (/comment/replies), GET </li>
 * <li>Gets a comment's revisions (/commment/{id}/revisions), GET</li>
 * <li>Removes a comment (/comment/{id}/remove), POST</li>
 * <li>Accepts a comment (/comment/accept), POST</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 0.2.0
 */
@Singleton
public class CommentProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(CommentProcessor.class);

    private static final String COMMENT_SORT_HOT = "hot";

    private static final String COMMENT_AUTHOR_FILTER = "1";

    private static final String COMMENT_THREAD_PARENTS = "commentThreadParents";

    private static final String COMMENT_THREAD_REPLIES = "commentThreadReplies";

    private static final String COMMENT_THREAD_REPLY_COUNT = "commentThreadReplyCount";

    private static final String COMMENT_THREAD_ROOT_ID = "commentThreadRootId";

    private static final String ANCHOR_COMMENT_ID = "anchorCommentId";

    private static final String THREAD_REPLY_PAGE = "page";

    private static final int COMMENT_THREAD_REPLY_PAGE_SIZE = 20;

    private static final int COMMENT_THREAD_REPLY_WINDOW_SIZE = 5;

    private static final int API_KEY_LENGTH = 192;

    /**
     * Revision query service.
     */
    @Inject
    private RevisionQueryService revisionQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Comment management service.
     */
    @Inject
    private CommentMgmtService commentMgmtService;

    /**
     * Comment query service.
     */
    @Inject
    private CommentQueryService commentQueryService;

    @Inject
    private ReactionQueryService reactionQueryService;

    /**
     * Article query service.
     */
    @Inject
    private ArticleQueryService articleQueryService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Reward query service.
     */
    @Inject
    private RewardQueryService rewardQueryService;

    @Inject
    private VoteQueryService voteQueryService;

    /**
     * Short link query service.
     */
    @Inject
    private ShortLinkQueryService shortLinkQueryService;

    /**
     * Follow management service.
     */
    @Inject
    private FollowMgmtService followMgmtService;

    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final PermissionMidware permissionMidware = beanManager.getReference(PermissionMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);
        final AnonymousViewCheckMidware anonymousViewCheckMidware = beanManager.getReference(AnonymousViewCheckMidware.class);
        final CommentUpdateValidationMidware commentUpdateValidationMidware = beanManager.getReference(CommentUpdateValidationMidware.class);
        final CommentAddValidationMidware commentAddValidationMidware = beanManager.getReference(CommentAddValidationMidware.class);

        final CommentProcessor commentProcessor = beanManager.getReference(CommentProcessor.class);
        Dispatcher.post("/comment/accept", commentProcessor::acceptComment, loginCheck::handle, csrfMidware::check, permissionMidware::check);
        Dispatcher.post("/comment/{id}/remove", commentProcessor::removeComment, loginCheck::handle, permissionMidware::check);
        Dispatcher.get("/comment/{id}/revisions", commentProcessor::getCommentRevisions, loginCheck::handle, permissionMidware::check);
        Dispatcher.get("/comment/{id}/content", commentProcessor::getCommentContent, loginCheck::handle);
        Dispatcher.put("/comment/{id}", commentProcessor::updateComment, loginCheck::handle, permissionMidware::check, commentUpdateValidationMidware::handle);
        Dispatcher.post("/comment/original", commentProcessor::getOriginalComment);
        Dispatcher.post("/comment/replies", commentProcessor::getReplies);
        Dispatcher.post("/comment/thread/parents", commentProcessor::getThreadParentComments, anonymousViewCheckMidware::handle);
        Dispatcher.post("/comment/thread/replies", commentProcessor::getThreadReplies, anonymousViewCheckMidware::handle);
        Dispatcher.post("/comment", commentProcessor::addComment, loginCheck::handle, permissionMidware::check, commentAddValidationMidware::handle);
        Dispatcher.post("/comment/thank", commentProcessor::thankComment, loginCheck::handle, permissionMidware::check);
    }

    /**
     * Accepts a comment.
     *
     * @param context the specified context
     */
    public void acceptComment(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final JSONObject requestJSONObject = context.requestJSON();
        final JSONObject currentUser = Sessions.getUser();
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        final String commentId = requestJSONObject.optString(Comment.COMMENT_T_ID);

        try {
            final JSONObject comment = commentQueryService.getComment(commentId);
            if (null == comment) {
                context.renderMsg("Not found comment to accept");
                return;
            }
            final String commentAuthorId = comment.optString(Comment.COMMENT_AUTHOR_ID);
            if (StringUtils.equals(userId, commentAuthorId)) {
                context.renderMsg(langPropsService.get("thankSelfLabel"));
                return;
            }

            final String articleId = comment.optString(Comment.COMMENT_ON_ARTICLE_ID);
            final JSONObject article = articleQueryService.getArticle(articleId);
            if (!StringUtils.equals(userId, article.optString(Article.ARTICLE_AUTHOR_ID))) {
                context.renderMsg(langPropsService.get("sc403Label"));
                return;
            }

            commentMgmtService.acceptComment(commentId);

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }

    /**
     * Removes a comment.
     *
     * @param context the specified context
     */
    public void removeComment(final RequestContext context) {
        final String id = context.pathVar("id");
        if (StringUtils.isBlank(id)) {
            context.sendError(404);
            return;
        }

        JSONObject currentUser = Sessions.getUser();
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }

        final String currentUserId = currentUser.optString(Keys.OBJECT_ID);
        final JSONObject comment = commentQueryService.getComment(id);
        if (null == comment) {
            context.sendError(404);
            return;
        }

        final String authorId = comment.optString(Comment.COMMENT_AUTHOR_ID);
        if (!authorId.equals(currentUserId)) {
            context.sendError(403);
            return;
        }

        context.renderJSON(StatusCodes.ERR);
        try {
            // 评论扣除100积分
            final boolean succ = null != pointtransferMgmtService.transfer(currentUserId, Pointtransfer.ID_C_SYS,
                    Pointtransfer.TRANSFER_TYPE_C_DEL_COMMENT,
                    Symphonys.POINT_DELETE_COMMENT, "", System.currentTimeMillis(), "");
            if (!succ) {
                context.renderJSON(StatusCodes.ERR).renderMsg("少年，你的积分不足！");
                return;
            }

            // 日志记录
            LogsService.commentLog(context, currentUser.optString(User.USER_NAME), comment);

            commentMgmtService.removeComment(id);

            context.renderJSONValue(Keys.CODE, StatusCodes.SUCC);
            context.renderJSONValue(Comment.COMMENT_T_ID, id);
        } catch (final ServiceException e) {
            final String msg = e.getMessage();

            context.renderMsg(msg);
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
        }
    }

    /**
     * Gets a comment's revisions.
     *
     * @param context the specified context
     */
    public void getCommentRevisions(final RequestContext context) {
        final String id = context.pathVar("id");
        final JSONObject viewer = (JSONObject) context.attr(User.USER);
        final JSONObject comment = commentQueryService.getComment(id);
        if (null == comment || null == getViewableArticle(
                comment.optString(Comment.COMMENT_ON_ARTICLE_ID), viewer)) {
            context.renderJSON(StatusCodes.ERR).renderMsg("评论不存在或不可查看");
            return;
        }
        final List<JSONObject> revisions = revisionQueryService.getCommentRevisions(viewer, id);
        final JSONObject ret = new JSONObject();
        ret.put(Keys.CODE, StatusCodes.SUCC);
        ret.put(Revision.REVISIONS, (Object) revisions);
        context.renderJSON(ret);
    }

    /**
     * Gets a comment's content.
     *
     * @param context the specified context
     */
    public void getCommentContent(final RequestContext context) {
        final String id = context.pathVar("id");
        context.renderJSON(StatusCodes.ERR);

        final JSONObject comment = commentQueryService.getComment(id);
        if (null == comment) {
            LOGGER.warn("Not found comment [id=" + id + "] to update");
            return;
        }

        final JSONObject currentUser = Sessions.getUser();
        if (!currentUser.optString(Keys.OBJECT_ID).equals(comment.optString(Comment.COMMENT_AUTHOR_ID))) {
            context.sendError(403);
            return;
        }

        context.renderJSONValue(Comment.COMMENT_CONTENT, comment.optString(Comment.COMMENT_CONTENT));
        context.renderJSONValue(Comment.COMMENT_VISIBLE, comment.optInt(Comment.COMMENT_VISIBLE));
        context.renderJSONValue(Keys.CODE, StatusCodes.SUCC);
    }

    /**
     * Updates a comment locally.
     * <p>
     * The request json object:
     * <pre>
     * {
     *     "commentContent": "",
     *     "commentVisible": boolean
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    public void updateComment(final RequestContext context) {
        final String id = context.pathVar("id");
        context.renderJSON(StatusCodes.ERR);

        final Request request = context.getRequest();

        try {
            final JSONObject comment = commentQueryService.getComment(id);
            if (null == comment) {
                LOGGER.warn("Not found comment [id=" + id + "] to update");
                return;
            }

            final JSONObject requestJSONObject = (JSONObject) context.attr(Keys.REQUEST);
            JSONObject currentUser = Sessions.getUser();
            try {
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            if (!currentUser.optString(Keys.OBJECT_ID).equals(comment.optString(Comment.COMMENT_AUTHOR_ID))) {
                context.sendError(403);
                return;
            }


            String commentContent = requestJSONObject.optString(Comment.COMMENT_CONTENT);
            final boolean isOnlyAuthorVisible = requestJSONObject.optBoolean(Comment.COMMENT_VISIBLE);
            final String ip = Requests.getRemoteAddr(request);
            final String ua = Headers.getHeader(request, Common.USER_AGENT, "");

            comment.put(Comment.COMMENT_CONTENT, commentContent);
            comment.put(Comment.COMMENT_IP, "");
            if (StringUtils.isNotBlank(ip)) {
                comment.put(Comment.COMMENT_IP, ip);
            }
            comment.put(Comment.COMMENT_UA, "");
            if (StringUtils.isNotBlank(ua)) {
                comment.put(Comment.COMMENT_UA, ua);
            }
            comment.put(Comment.COMMENT_VISIBLE, isOnlyAuthorVisible
                    ? Comment.COMMENT_VISIBLE_C_AUTHOR : Comment.COMMENT_VISIBLE_C_ALL);

            commentMgmtService.updateComment(comment.optString(Keys.OBJECT_ID), comment);

            commentContent = comment.optString(Comment.COMMENT_CONTENT);
            commentContent = shortLinkQueryService.linkArticle(commentContent);
            commentContent = Emotions.toAliases(commentContent);
            commentContent = Emotions.convert(commentContent);
            commentContent = Markdowns.toHTML(commentContent);
            commentContent = Markdowns.clean(commentContent, "");
            commentContent = MediaPlayers.renderAudio(commentContent);
            commentContent = MediaPlayers.renderVideo(commentContent);

            context.renderJSONValue(Keys.CODE, StatusCodes.SUCC);
            context.renderJSONValue(Comment.COMMENT_CONTENT, commentContent);
            context.renderJSONValue(Comment.COMMENT_REVISION_COUNT,
                    revisionQueryService.count(id, Revision.DATA_TYPE_C_COMMENT));
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }

    /**
     * Gets a comment's original comment.
     *
     * @param context the specified context
     */
    public void getOriginalComment(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        final JSONObject requestJSONObject = context.requestJSON();
        final String commentId = requestJSONObject.optString(Comment.COMMENT_T_ID);
        int commentViewMode = requestJSONObject.optInt(UserExt.USER_COMMENT_VIEW_MODE);
        final JSONObject currentUser = getCurrentUser(context, requestJSONObject);
        final String currentUserId = getCurrentUserId(currentUser);

        final JSONObject comment = commentQueryService.getComment(commentId);
        if (null == comment || null == getViewableArticle(comment.optString(Comment.COMMENT_ON_ARTICLE_ID), currentUser)) {
            context.renderMsg("评论不存在或不可查看");
            return;
        }

        final JSONObject originalCmt = commentQueryService.getOriginalComment(currentUserId, commentViewMode, commentId);
        if (null == originalCmt) {
            context.renderMsg("评论不存在或不可查看");
            return;
        }

        // Fill thank
        final String originalCmtId = originalCmt.optString(Keys.OBJECT_ID);

        if (null != currentUser) {
            originalCmt.put(Common.REWARDED,
                    rewardQueryService.isRewarded(currentUser.optString(Keys.OBJECT_ID),
                            originalCmtId, Reward.TYPE_C_COMMENT));
        }

        reactionQueryService.fillCommentReaction(originalCmt, currentUserId);

        context.renderJSON(StatusCodes.SUCC).renderJSONValue(Comment.COMMENT_T_REPLIES, originalCmt);
    }

    /**
     * Gets a comment's replies.
     *
     * @param context the specified context
     */
    public void getReplies(final RequestContext context) {
        final JSONObject requestJSONObject = context.requestJSON();
        final String commentId = requestJSONObject.optString(Comment.COMMENT_T_ID);
        int commentViewMode = requestJSONObject.optInt(UserExt.USER_COMMENT_VIEW_MODE);
        final JSONObject currentUser = getCurrentUser(context, requestJSONObject);
        final String currentUserId = getCurrentUserId(currentUser);

        if (StringUtils.isBlank(commentId)) {
            context.renderJSON(StatusCodes.SUCC).renderJSONValue(Comment.COMMENT_T_REPLIES, Collections.emptyList());
            return;
        }

        final JSONObject comment = commentQueryService.getComment(commentId);
        if (null == comment || null == getViewableArticle(comment.optString(Comment.COMMENT_ON_ARTICLE_ID), currentUser)) {
            context.renderJSON(StatusCodes.ERR).renderMsg("评论不存在或不可查看");
            return;
        }

        final List<JSONObject> replies = commentQueryService.getReplies(currentUserId, commentViewMode, commentId);

        // Fill reply thank
        for (final JSONObject reply : replies) {
            final String replyId = reply.optString(Keys.OBJECT_ID);

            if (null != currentUser) {
                reply.put(Common.REWARDED,
                        rewardQueryService.isRewarded(currentUser.optString(Keys.OBJECT_ID),
                                replyId, Reward.TYPE_C_COMMENT));
            }

            final int rewardCount = reply.optInt(Comment.COMMENT_THANK_CNT);
            reply.put(Common.REWARED_COUNT, rewardCount);
        }

        reactionQueryService.fillCommentReactions(replies, currentUserId);

        context.renderJSON(StatusCodes.SUCC).renderJSONValue(Comment.COMMENT_T_REPLIES, replies);
    }

    public void getThreadParentComments(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        final JSONObject requestJSONObject = context.requestJSON();
        final String articleId = requestJSONObject.optString(Article.ARTICLE_T_ID);
        final JSONObject currentUser = getCurrentUser(context, requestJSONObject);
        final JSONObject article = getViewableArticle(articleId, currentUser);
        if (null == article) {
            context.renderMsg("文章不存在或不可查看");
            return;
        }

        final int pageNum = requestJSONObject.optInt(Pagination.PAGINATION_CURRENT_PAGE_NUM, 1);
        if (pageNum < 1) {
            context.renderMsg("页码无效");
            return;
        }

        final int commentViewMode = normalizeCommentViewMode(requestJSONObject);
        final String currentUserId = getCurrentUserId(currentUser);
        final JSONObject options = buildThreadParentOptions(
                requestJSONObject, article, pageNum, commentViewMode, currentUserId);
        final int commentCnt = commentQueryService.countArticleThreadParentComments(options);
        final int pageCount = (int) Math.ceil((double) commentCnt / Symphonys.ARTICLE_COMMENTS_CNT);
        final int currentPage = normalizeThreadPage(pageNum, pageCount, commentViewMode);
        options.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, currentPage);

        final List<JSONObject> comments = commentQueryService.getArticleThreadParentComments(options);
        fillThreadParentViewState(comments, article, currentUser, commentViewMode);
        context.renderJSON(StatusCodes.SUCC).
                renderJSONValue(COMMENT_THREAD_PARENTS, comments).
                renderJSONValue(Pagination.PAGINATION, buildThreadPagination(currentPage, pageCount, commentCnt));
    }

    public void getThreadReplies(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        final JSONObject requestJSONObject = context.requestJSON();
        final String commentId = requestJSONObject.optString(Comment.COMMENT_T_ID);
        if (StringUtils.isBlank(commentId)) {
            context.renderMsg("评论不存在");
            return;
        }

        final String rootCommentId = commentQueryService.getCommentThreadRootId(commentId);
        final JSONObject rootComment = commentQueryService.getComment(rootCommentId);
        final JSONObject currentUser = getCurrentUser(context, requestJSONObject);
        if (null == rootComment || null == getViewableArticle(rootComment.optString(Comment.COMMENT_ON_ARTICLE_ID), currentUser)) {
            context.renderMsg("评论不存在或不可查看");
            return;
        }

        final String currentUserId = getCurrentUserId(currentUser);
        final List<JSONObject> allReplies;
        try {
            allReplies = commentQueryService.getCommentThreadReplies(currentUserId, rootCommentId);
        } catch (final CommentQueryService.CommentThreadTooLargeException e) {
            context.renderMsg(e.getMessage());
            return;
        }
        final int replyCount = allReplies.size();
        final int pageCount = (int) Math.ceil((double) replyCount / COMMENT_THREAD_REPLY_PAGE_SIZE);
        final int requestedPage = getRequestedThreadReplyPage(requestJSONObject);
        final int anchorPage = getAnchorThreadReplyPage(
                allReplies, requestJSONObject.optString(ANCHOR_COMMENT_ID), requestedPage);
        final int currentPage = normalizeThreadReplyPage(anchorPage, pageCount);
        final List<JSONObject> replies = getThreadReplyPageReplies(allReplies, currentPage);
        commentQueryService.fillCommentRevisionCounts(replies);
        reactionQueryService.fillCommentReactions(replies, currentUserId);
        context.renderJSON(StatusCodes.SUCC).
                renderJSONValue(COMMENT_THREAD_REPLIES, replies).
                renderJSONValue(COMMENT_THREAD_REPLY_COUNT, replyCount).
                renderJSONValue(COMMENT_THREAD_ROOT_ID, rootCommentId).
                renderJSONValue(Pagination.PAGINATION, buildThreadReplyPagination(currentPage, pageCount, replyCount));
    }

    private int getRequestedThreadReplyPage(final JSONObject requestJSONObject) {
        final int currentPageNum = requestJSONObject.optInt(Pagination.PAGINATION_CURRENT_PAGE_NUM, 0);
        if (currentPageNum > 0) {
            return currentPageNum;
        }
        return requestJSONObject.optInt(THREAD_REPLY_PAGE, 1);
    }

    private int getAnchorThreadReplyPage(final List<JSONObject> replies, final String anchorCommentId,
                                         final int requestedPage) {
        if (StringUtils.isBlank(anchorCommentId)) {
            return requestedPage;
        }
        for (int i = 0; i < replies.size(); i++) {
            if (StringUtils.equals(anchorCommentId, replies.get(i).optString(Keys.OBJECT_ID))) {
                return (i / COMMENT_THREAD_REPLY_PAGE_SIZE) + 1;
            }
        }
        return requestedPage;
    }

    private int normalizeThreadReplyPage(final int pageNum, final int pageCount) {
        if (pageCount < 1) {
            return 1;
        }
        if (pageNum < 1) {
            return 1;
        }
        return Math.min(pageNum, pageCount);
    }

    private List<JSONObject> getThreadReplyPageReplies(final List<JSONObject> replies, final int currentPage) {
        final int fromIndex = Math.max((currentPage - 1) * COMMENT_THREAD_REPLY_PAGE_SIZE, 0);
        if (fromIndex >= replies.size()) {
            return Collections.emptyList();
        }
        final int toIndex = Math.min(fromIndex + COMMENT_THREAD_REPLY_PAGE_SIZE, replies.size());
        return new java.util.ArrayList<>(replies.subList(fromIndex, toIndex));
    }

    private JSONObject buildThreadReplyPagination(final int pageNum, final int pageCount, final int replyCount) {
        final List<Integer> pageNums = Paginator.paginate(
                pageNum, COMMENT_THREAD_REPLY_PAGE_SIZE, pageCount, COMMENT_THREAD_REPLY_WINDOW_SIZE);
        final JSONObject pagination = new JSONObject();
        pagination.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        pagination.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        pagination.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        pagination.put(Pagination.PAGINATION_PAGE_SIZE, COMMENT_THREAD_REPLY_PAGE_SIZE);
        pagination.put(Pagination.PAGINATION_RECORD_COUNT, replyCount);
        if (!pageNums.isEmpty()) {
            pagination.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            pagination.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }
        return pagination;
    }

    private JSONObject buildThreadParentOptions(final JSONObject requestJSONObject, final JSONObject article,
                                                final int pageNum, final int commentViewMode,
                                                final String currentUserId) {
        final JSONObject options = new JSONObject();
        options.put(Article.ARTICLE_T_ID, article.optString(Keys.OBJECT_ID));
        options.put(Article.ARTICLE_AUTHOR_ID, article.optString(Article.ARTICLE_AUTHOR_ID));
        options.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        options.put(Pagination.PAGINATION_PAGE_SIZE, Symphonys.ARTICLE_COMMENTS_CNT);
        options.put(UserExt.USER_COMMENT_VIEW_MODE, commentViewMode);
        options.put(Comment.COMMENT_AUTHOR_ID, getThreadAuthorFilter(requestJSONObject, article));
        options.put("commentSort", normalizeCommentSort(requestJSONObject.optString("sort")));
        options.put(Keys.OBJECT_ID, currentUserId);
        return options;
    }

    private String getThreadAuthorFilter(final JSONObject requestJSONObject, final JSONObject article) {
        if (COMMENT_AUTHOR_FILTER.equals(requestJSONObject.optString("author"))) {
            return article.optString(Article.ARTICLE_AUTHOR_ID);
        }
        return "";
    }

    private int normalizeThreadPage(final int pageNum, final int pageCount, final int commentViewMode) {
        if (pageCount < 1) {
            return 1;
        }
        if (pageNum <= pageCount) {
            return pageNum;
        }
        if (UserExt.USER_COMMENT_VIEW_MODE_C_TRADITIONAL == commentViewMode) {
            return pageCount;
        }
        return 1;
    }

    private JSONObject buildThreadPagination(final int pageNum, final int pageCount, final int commentCnt) {
        final List<Integer> pageNums = Paginator.paginate(
                pageNum, Symphonys.ARTICLE_COMMENTS_CNT, pageCount, Symphonys.ARTICLE_COMMENTS_WIN_SIZE);
        final JSONObject pagination = new JSONObject();
        pagination.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        pagination.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        pagination.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        pagination.put(Pagination.PAGINATION_PAGE_SIZE, Symphonys.ARTICLE_COMMENTS_CNT);
        pagination.put(Pagination.PAGINATION_RECORD_COUNT, commentCnt);
        if (!pageNums.isEmpty()) {
            pagination.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            pagination.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }
        return pagination;
    }

    private void fillThreadParentViewState(final List<JSONObject> comments, final JSONObject article,
                                           final JSONObject currentUser, final int commentViewMode) {
        final String currentUserId = null == currentUser ? "" : currentUser.optString(Keys.OBJECT_ID);
        final double niceScore = getThreadNiceScore(article.optString(Keys.OBJECT_ID), commentViewMode);
        final String thankTemplate = langPropsService.get("thankConfirmLabel");
        for (final JSONObject comment : comments) {
            fillSingleThreadParentState(comment, article, currentUserId, niceScore, thankTemplate);
        }
        reactionQueryService.fillCommentReactions(comments, currentUserId);
    }

    private void fillSingleThreadParentState(final JSONObject comment, final JSONObject article,
                                             final String currentUserId, final double niceScore,
                                             final String thankTemplate) {
        final String commentId = comment.optString(Keys.OBJECT_ID);
        final JSONObject commenter = comment.optJSONObject(Comment.COMMENT_T_COMMENTER);
        final String commenterName = null == commenter
                ? comment.optString(Comment.COMMENT_T_AUTHOR_NAME) : commenter.optString(User.USER_NAME);
        comment.put(Comment.COMMENT_T_NICE, comment.optDouble(Comment.COMMENT_SCORE, 0D) >= niceScore);
        comment.put(Comment.COMMENT_T_THANK_LABEL, thankTemplate.
                replace("{point}", String.valueOf(Symphonys.POINT_THANK_COMMENT)).
                replace("{user}", commenterName));
        comment.put(Common.REWARED_COUNT, comment.optInt(Comment.COMMENT_THANK_CNT));
        if (StringUtils.isNotBlank(currentUserId)) {
            comment.put(Common.REWARDED, rewardQueryService.isRewarded(currentUserId, commentId, Reward.TYPE_C_COMMENT));
            comment.put(Comment.COMMENT_T_VOTE, voteQueryService.isVoted(currentUserId, commentId));
        }
        filterOnlyAuthorVisibleContent(comment, article, currentUserId);
    }

    private void filterOnlyAuthorVisibleContent(final JSONObject comment, final JSONObject article,
                                                final String currentUserId) {
        if (Comment.COMMENT_VISIBLE_C_AUTHOR != comment.optInt(Comment.COMMENT_VISIBLE)) {
            return;
        }
        final String articleAuthorId = article.optString(Article.ARTICLE_AUTHOR_ID);
        if (StringUtils.isBlank(currentUserId) ||
                (!comment.optBoolean(Comment.COMMENT_T_IS_CURRENT_USER)
                        && !StringUtils.equals(currentUserId, articleAuthorId))) {
            comment.put(Comment.COMMENT_CONTENT, langPropsService.get("onlySelfAndArticleAuthorVisibleLabel"));
        }
    }

    private double getThreadNiceScore(final String articleId, final int commentViewMode) {
        final List<JSONObject> niceComments = commentQueryService.getNiceComments(commentViewMode, articleId, 3);
        if (niceComments.isEmpty()) {
            return Double.MAX_VALUE;
        }
        return niceComments.get(niceComments.size() - 1).optDouble(Comment.COMMENT_SCORE, 0D);
    }

    private JSONObject getViewableArticle(final String articleId, final JSONObject currentUser) {
        if (StringUtils.isBlank(articleId)) {
            return null;
        }
        final JSONObject article = articleQueryService.getArticleById(articleId);
        if (null == article || Article.ARTICLE_STATUS_C_INVALID == article.optInt(Article.ARTICLE_STATUS)) {
            return null;
        }
        if (null == currentUser
                && Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW == article.optInt(Article.ARTICLE_ANONYMOUS_VIEW)) {
            return null;
        }
        return isDiscussionViewable(article, currentUser) ? article : null;
    }

    private boolean isDiscussionViewable(final JSONObject article, final JSONObject currentUser) {
        if (Article.ARTICLE_TYPE_C_DISCUSSION != article.optInt(Article.ARTICLE_TYPE)) {
            return true;
        }
        if (null == currentUser) {
            return false;
        }
        if (StringUtils.equals(currentUser.optString(Keys.OBJECT_ID), article.optString(Article.ARTICLE_AUTHOR_ID))) {
            return true;
        }
        if (Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE))) {
            return true;
        }
        final String currentUserName = currentUser.optString(User.USER_NAME);
        if (StringUtils.isBlank(currentUserName)) {
            return false;
        }
        final Set<String> userNames = userQueryService.getUserNames(article.optString(Article.ARTICLE_CONTENT));
        return userNames.contains(currentUserName);
    }

    private JSONObject getCurrentUser(final RequestContext context, final JSONObject requestJSONObject) {
        final JSONObject currentUser = Sessions.getUser();
        if (null != currentUser) {
            return currentUser;
        }
        final JSONObject queryUser = getApiKeyUser(context.param("apiKey"));
        if (null != queryUser) {
            return queryUser;
        }
        return getApiKeyUser(requestJSONObject.optString("apiKey"));
    }

    private JSONObject getApiKeyUser(final String apiKey) {
        if (StringUtils.isBlank(apiKey) || API_KEY_LENGTH != apiKey.length()) {
            return null;
        }
        try {
            return ApiProcessor.getUserByKey(apiKey);
        } catch (final NullPointerException e) {
            return null;
        }
    }

    private String getCurrentUserId(final JSONObject currentUser) {
        return null == currentUser ? "" : currentUser.optString(Keys.OBJECT_ID);
    }

    private String normalizeCommentSort(final String sort) {
        if (COMMENT_SORT_HOT.equals(sort)) {
            return COMMENT_SORT_HOT;
        }
        return "";
    }

    private int normalizeCommentViewMode(final JSONObject requestJSONObject) {
        final int mode = requestJSONObject.optInt(UserExt.USER_COMMENT_VIEW_MODE);
        if (UserExt.USER_COMMENT_VIEW_MODE_C_REALTIME == mode) {
            return UserExt.USER_COMMENT_VIEW_MODE_C_REALTIME;
        }
        return UserExt.USER_COMMENT_VIEW_MODE_C_TRADITIONAL;
    }

    /**
     * Adds a comment locally.
     * <p>
     * The request json object (a comment):
     * <pre>
     * {
     *     "articleId": "",
     *     "commentContent": "",
     *     "commentAnonymous": boolean,
     *     "commentVisible": boolean,
     *     "commentOriginalCommentId": "", // optional
     *     "userCommentViewMode": int
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    public void addComment(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final Request request = context.getRequest();
        final JSONObject requestJSONObject = (JSONObject) context.attr(Keys.REQUEST);

        final String articleId = requestJSONObject.optString(Article.ARTICLE_T_ID);
        final String commentContent = requestJSONObject.optString(Comment.COMMENT_CONTENT);
        final String commentOriginalCommentId = requestJSONObject.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID);
        final int commentViewMode = requestJSONObject.optInt(UserExt.USER_COMMENT_VIEW_MODE);
        final String ip = Requests.getRemoteAddr(request);
        final String ua = Headers.getHeader(request, Common.USER_AGENT, "");

        final boolean isAnonymous = requestJSONObject.optBoolean(Comment.COMMENT_ANONYMOUS);
        final boolean isOnlyAuthorVisible = requestJSONObject.optBoolean(Comment.COMMENT_VISIBLE);

        final JSONObject comment = new JSONObject();
        comment.put(Comment.COMMENT_CONTENT, commentContent);
        comment.put(Comment.COMMENT_ON_ARTICLE_ID, articleId);
        comment.put(UserExt.USER_COMMENT_VIEW_MODE, commentViewMode);
        comment.put(Comment.COMMENT_IP, "");
        if (StringUtils.isNotBlank(ip)) {
            comment.put(Comment.COMMENT_IP, ip);
        }
        comment.put(Comment.COMMENT_UA, "");
        if (StringUtils.isNotBlank(ua)) {
            comment.put(Comment.COMMENT_UA, ua);
        }
        comment.put(Comment.COMMENT_ORIGINAL_COMMENT_ID, commentOriginalCommentId);

        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final String userPhone = currentUser.optString("userPhone");
            if (userPhone.isEmpty()) {
                context.renderJSON(StatusCodes.ERR).renderMsg("未绑定手机号码，无法使用此功能。请至设置-账户中绑定手机号码。");
                return;
            }
            final String currentUserName = currentUser.optString(User.USER_NAME);
            final JSONObject article = articleQueryService.getArticle(articleId);
            final String articleContent = article.optString(Article.ARTICLE_CONTENT);
            final String articleAuthorId = article.optString(Article.ARTICLE_AUTHOR_ID);
            final JSONObject articleAuthor = userQueryService.getUser(articleAuthorId);
            final String articleAuthorName = articleAuthor.optString(User.USER_NAME);

            final Set<String> userNames = userQueryService.getUserNames(articleContent);
            if (Article.ARTICLE_TYPE_C_DISCUSSION == article.optInt(Article.ARTICLE_TYPE)
                    && !articleAuthorName.equals(currentUserName)) {
                boolean invited = false;
                for (final String userName : userNames) {
                    if (userName.equals(currentUserName)) {
                        invited = true;
                        break;
                    }
                }

                if (!invited) {
                    context.sendError(403);
                    return;
                }
            }

            final String commentAuthorId = currentUser.optString(Keys.OBJECT_ID);
            comment.put(Comment.COMMENT_AUTHOR_ID, commentAuthorId);
            comment.put(Comment.COMMENT_ANONYMOUS, isAnonymous
                    ? Comment.COMMENT_ANONYMOUS_C_ANONYMOUS : Comment.COMMENT_ANONYMOUS_C_PUBLIC);
            comment.put(Comment.COMMENT_VISIBLE, isOnlyAuthorVisible
                    ? Comment.COMMENT_VISIBLE_C_AUTHOR : Comment.COMMENT_VISIBLE_C_ALL);

            commentMgmtService.addComment(comment);

            if ((!commentAuthorId.equals(articleAuthorId) &&
                    UserExt.USER_XXX_STATUS_C_ENABLED == currentUser.optInt(UserExt.USER_REPLY_WATCH_ARTICLE_STATUS))
                    || Article.ARTICLE_TYPE_C_DISCUSSION == article.optInt(Article.ARTICLE_TYPE)) {
                followMgmtService.watchArticle(commentAuthorId, articleId);
            }

            context.renderJSONValue(Keys.CODE, StatusCodes.SUCC);
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }

    /**
     * Thanks a comment.
     *
     * @param context the specified context
     */
    public void thankComment(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final JSONObject requestJSONObject = context.requestJSON();
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        final String commentId = requestJSONObject.optString(Comment.COMMENT_T_ID);

        try {
            commentMgmtService.thankComment(commentId, currentUser.optString(Keys.OBJECT_ID));

            context.renderJSON(StatusCodes.SUCC).renderMsg(langPropsService.get("thankSentLabel"));
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }
}
