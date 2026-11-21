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
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.service.ArticleQueryService;
import org.b3log.symphony.service.CommentQueryService;
import org.b3log.symphony.service.CommentValidationService;
import org.b3log.symphony.service.LogsService;
import org.b3log.symphony.util.CommentValidation;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;

@Singleton
public class CommentAddValidationMidware {
    @Inject private CommentValidationService validation;
    public void handle(final RequestContext context) {
        final JSONObject requestJSONObject = context.requestJSON();
        context.getRequest().setAttribute(Keys.REQUEST, requestJSONObject);
        final JSONObject currentUser = (JSONObject) context.attr(User.USER);
        if (null == currentUser) {
            context.sendError(401);
            context.abort();
            return;
        }
        final JSONObject exception = new JSONObject().put(Keys.CODE, StatusCodes.ERR);
        try {
            validation.prepare(requestJSONObject, currentUser, true);
            final var beans = BeanManager.getInstance();
            final var langPropsService = beans.getReference(LangPropsService.class);
            final var articleQueryService = beans.getReference(ArticleQueryService.class);
            final var commentQueryService = beans.getReference(CommentQueryService.class);
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
            validation.moderate(requestJSONObject, result -> {
                final String content = StringUtils.trim(requestJSONObject.optString(Comment.COMMENT_CONTENT));
                LogsService.censorLog(context, currentUser.optString(Keys.OBJECT_ID), "用户：" + currentUser.optString(User.USER_NAME)
                        + " 违规评论：" + content + " 违规判定：" + result);
                System.out.println("用户：" + currentUser.optString(User.USER_NAME) + " 违规评论：" + content + " 违规判定：" + result);
            });
        } catch (final CommentValidation.Rejected e) {
            context.renderJSON(exception.put(Keys.MSG, e.getMessage()));
            context.abort();
            return;
        }
        context.handle();
    }
}
