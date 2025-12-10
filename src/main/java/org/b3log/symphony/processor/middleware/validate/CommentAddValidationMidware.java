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
package org.b3log.symphony.processor.middleware.validate;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.processor.bot.ChatRoomBot;
import org.b3log.symphony.processor.channel.ChatChannel;
import org.b3log.symphony.service.ArticleQueryService;
import org.b3log.symphony.service.CommentQueryService;
import org.b3log.symphony.service.LogsService;
import org.b3log.symphony.service.OptionQueryService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

/**
 * Validates for comment adding locally.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 0.2.0
 */
@Singleton
public class CommentAddValidationMidware {

    final private static SimpleCurrentLimiter addCommentLimiter = new SimpleCurrentLimiter(60 * 10, 5);

    public void handle(final RequestContext context) {
        final Request request = context.getRequest();
        final JSONObject requestJSONObject = context.requestJSON();
        request.setAttribute(Keys.REQUEST, requestJSONObject);
        final BeanManager beanManager = BeanManager.getInstance();
        final LangPropsService langPropsService = beanManager.getReference(LangPropsService.class);
        final OptionQueryService optionQueryService = beanManager.getReference(OptionQueryService.class);
        final ArticleQueryService articleQueryService = beanManager.getReference(ArticleQueryService.class);
        final CommentQueryService commentQueryService = beanManager.getReference(CommentQueryService.class);

        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        try {
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        if (null == currentUser) {
            context.sendError(401);
            context.abort();
            return;
        }

        final JSONObject exception = new JSONObject();
        exception.put(Keys.CODE, StatusCodes.ERR);

        // 频率检测
        if (!Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE)) && !"1630552921050".equals(currentUser.optString(User.USER_ROLE))) {
            if (!addCommentLimiter.access(currentUser.optString(Keys.OBJECT_ID))) {
                context.renderJSON(exception.put(Keys.MSG, "操作过于频繁，请稍候重试。"));
                context.abort();
                return;
            }
        }

        requestJSONObject.put(Comment.COMMENT_CONTENT, ReservedWords.processReservedWord(requestJSONObject.optString(Comment.COMMENT_CONTENT)));
        final String commentContent = StringUtils.trim(requestJSONObject.optString(Comment.COMMENT_CONTENT));
        if (StringUtils.isBlank(commentContent) || commentContent.length() > Comment.MAX_COMMENT_CONTENT_LENGTH) {
            context.renderJSON(exception.put(Keys.MSG, langPropsService.get("commentErrorLabel")));
            context.abort();
            return;
        }

        final String articleId = requestJSONObject.optString(Article.ARTICLE_T_ID);
        if (StringUtils.isBlank(articleId)) {
            context.renderJSON(exception.put(Keys.MSG, langPropsService.get("commentArticleErrorLabel")));
            context.abort();
            return;
        }

        final JSONObject article = articleQueryService.getArticleById(articleId);
        if (null == article) {
            context.renderJSON(exception.put(Keys.MSG, langPropsService.get("commentArticleErrorLabel")));
            context.abort();
            return;
        }

        if (!article.optBoolean(Article.ARTICLE_COMMENTABLE)) {
            context.renderJSON(exception.put(Keys.MSG, langPropsService.get("notAllowCmtLabel")));
            context.abort();
            return;
        }

        final String originalCommentId = requestJSONObject.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID);
        if (StringUtils.isNotBlank(originalCommentId)) {
            final JSONObject originalCmt = commentQueryService.getComment(originalCommentId);
            if (null == originalCmt) {
                context.renderJSON(exception.put(Keys.MSG, langPropsService.get("commentArticleErrorLabel")));
                context.abort();
                return;
            }
        }

        // 敏感词检测
        JSONObject censorResult = QiniuTextCensor.censor(commentContent);
        if (censorResult.optString("do").equals("block")) {
            // 违规内容，不予显示
            String bannedWords = "内容" + QiniuTextCensor.showBannedWords(censorResult);
            context.renderJSON(exception.put(Keys.MSG, "您的评论存在违规内容，内容已被记录，管理员将会复审，请修改内容后重试。" + bannedWords));
            ChatChannel.sendAdminMsg(currentUser.optString(User.USER_NAME), "【AI审查】您由于上传违规评论，被处以 1 积分的处罚，请引以为戒。\n如误报请在此处回复我，审核后找回积分并获得补偿！");
            ChatRoomBot.abusePoint(currentUser.optString(Keys.OBJECT_ID), 1, "[AI审查] [如有误报请联系管理员追回积分] 机器人罚单-上传违规内容（评论）");
            // 记录日志
            LogsService.censorLog(context, currentUser.optString(Keys.OBJECT_ID), "用户：" + currentUser.optString(User.USER_NAME) + " 违规评论：" + commentContent + " 违规判定：" + censorResult);
            System.out.println("用户：" + currentUser.optString(User.USER_NAME) + " 违规评论：" + commentContent + " 违规判定：" + censorResult);
            context.abort();
            return;
        }

        context.handle();
    }
}
