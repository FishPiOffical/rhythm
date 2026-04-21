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
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.processor.channel.ArticleChannel;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.service.ArticleQueryService;
import org.b3log.symphony.service.CommentQueryService;
import org.b3log.symphony.service.ReactionMgmtService;
import org.b3log.symphony.service.ReactionQueryService;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;

/**
 * Reaction processor.
 */
@Singleton
public class ReactionProcessor {

    @Inject
    private ReactionMgmtService reactionMgmtService;

    @Inject
    private ReactionQueryService reactionQueryService;

    @Inject
    private CommentQueryService commentQueryService;

    @Inject
    private ChatRoomRepository chatRoomRepository;

    @Inject
    private ArticleQueryService articleQueryService;

    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final ReactionProcessor reactionProcessor = beanManager.getReference(ReactionProcessor.class);

        Dispatcher.post("/article/reaction", reactionProcessor::updateArticleReaction, loginCheck::handle);
        Dispatcher.post("/comment/reaction", reactionProcessor::updateCommentReaction, loginCheck::handle);
        Dispatcher.post("/chat-room/reaction", reactionProcessor::updateChatReaction, loginCheck::handle);
    }

    public void updateArticleReaction(final RequestContext context) {
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            final String articleId = requestJSONObject.optString("articleId");
            final JSONObject article = articleQueryService.getArticleById(articleId);
            if (StringUtils.isBlank(articleId) || article == null) {
                renderError(context, "帖子不存在");
                return;
            }

            final String userId = getCurrentUser(context).optString(Keys.OBJECT_ID);
            reactionMgmtService.toggleReaction(userId, Reaction.TARGET_TYPE_ARTICLE, articleId,
                    requestJSONObject.optString("groupType"), requestJSONObject.optString("value"));
            final JSONObject result = reactionQueryService.buildOperationResult(
                    Reaction.TARGET_TYPE_ARTICLE, articleId, userId);

            final JSONObject push = new JSONObject();
            push.put(Article.ARTICLE_T_ID, articleId);
            push.put(Reaction.RESPONSE_TARGET_ID, articleId);
            push.put(Reaction.RESPONSE_TARGET_TYPE, Reaction.TARGET_TYPE_ARTICLE);
            push.put(Reaction.RESPONSE_GROUP_TYPE, Reaction.GROUP_EMOJI);
            push.put(Reaction.RESPONSE_SUMMARY, reactionQueryService.getSummary(
                    Reaction.TARGET_TYPE_ARTICLE, articleId, ""));
            push.put(Reaction.RESPONSE_ACTOR_USER_ID, userId);
            push.put(Reaction.RESPONSE_ACTOR_REACTION, result.optString(Reaction.FIELD_CURRENT_USER_REACTION));
            ArticleChannel.notifyArticleReaction(push);

            renderSuccess(context, result);
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void updateCommentReaction(final RequestContext context) {
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            final String commentId = requestJSONObject.optString("commentId");
            final JSONObject comment = commentQueryService.getCommentById(commentId);
            if (StringUtils.isBlank(commentId) || comment == null) {
                renderError(context, "评论不存在");
                return;
            }

            final String userId = getCurrentUser(context).optString(Keys.OBJECT_ID);
            reactionMgmtService.toggleReaction(userId, Reaction.TARGET_TYPE_COMMENT, commentId,
                    requestJSONObject.optString("groupType"), requestJSONObject.optString("value"));
            final JSONObject result = reactionQueryService.buildOperationResult(
                    Reaction.TARGET_TYPE_COMMENT, commentId, userId);

            final JSONObject push = new JSONObject();
            push.put(Article.ARTICLE_T_ID, comment.optString(Comment.COMMENT_ON_ARTICLE_ID));
            push.put(Comment.COMMENT_T_ID, commentId);
            push.put(Reaction.RESPONSE_TARGET_ID, commentId);
            push.put(Reaction.RESPONSE_TARGET_TYPE, Reaction.TARGET_TYPE_COMMENT);
            push.put(Reaction.RESPONSE_GROUP_TYPE, Reaction.GROUP_EMOJI);
            push.put(Reaction.RESPONSE_SUMMARY, reactionQueryService.getSummary(
                    Reaction.TARGET_TYPE_COMMENT, commentId, ""));
            push.put(Reaction.RESPONSE_ACTOR_USER_ID, userId);
            push.put(Reaction.RESPONSE_ACTOR_REACTION, result.optString(Reaction.FIELD_CURRENT_USER_REACTION));
            ArticleChannel.notifyCommentReaction(push);

            renderSuccess(context, result);
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void updateChatReaction(final RequestContext context) {
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            final String oId = requestJSONObject.optString("oId");
            if (StringUtils.isBlank(oId) || chatRoomRepository.get(oId) == null) {
                renderError(context, "聊天消息不存在");
                return;
            }

            final String userId = getCurrentUser(context).optString(Keys.OBJECT_ID);
            reactionMgmtService.toggleReaction(userId, Reaction.TARGET_TYPE_CHAT, oId,
                    requestJSONObject.optString("groupType"), requestJSONObject.optString("value"));

            final JSONObject result = reactionQueryService.buildOperationResult(
                    Reaction.TARGET_TYPE_CHAT, oId, userId);
            final JSONObject push = new JSONObject();
            push.put(Common.TYPE, "chatReaction");
            push.put("oId", oId);
            push.put(Reaction.RESPONSE_TARGET_TYPE, Reaction.TARGET_TYPE_CHAT);
            push.put(Reaction.RESPONSE_GROUP_TYPE, Reaction.GROUP_EMOJI);
            push.put(Reaction.RESPONSE_SUMMARY, reactionQueryService.getSummary(
                    Reaction.TARGET_TYPE_CHAT, oId, ""));
            push.put(Reaction.RESPONSE_ACTOR_USER_ID, userId);
            push.put(Reaction.RESPONSE_ACTOR_REACTION, result.optString(Reaction.FIELD_CURRENT_USER_REACTION));
            ChatroomChannel.notifyChat(push);

            renderSuccess(context, result);
        } catch (final Exception e) {
            renderError(context, e instanceof ServiceException ? e.getMessage() : "更新 reaction 失败");
        }
    }

    private JSONObject getCurrentUser(final RequestContext context) {
        final Object userObj = context.attr(User.USER);
        if (userObj instanceof JSONObject) {
            return (JSONObject) userObj;
        }
        final JSONObject currentUser = Sessions.getUser();
        return currentUser == null ? new JSONObject() : currentUser;
    }

    private void renderSuccess(final RequestContext context, final JSONObject data) {
        final JSONObject result = new JSONObject();
        result.put(Keys.CODE, StatusCodes.SUCC);
        result.put(Keys.MSG, "");
        result.put(Keys.DATA, data);
        context.renderJSON(result);
    }

    private void renderError(final RequestContext context, final String msg) {
        final JSONObject result = new JSONObject();
        result.put(Keys.CODE, StatusCodes.ERR);
        result.put(Keys.MSG, msg);
        context.renderJSON(result);
    }
}
