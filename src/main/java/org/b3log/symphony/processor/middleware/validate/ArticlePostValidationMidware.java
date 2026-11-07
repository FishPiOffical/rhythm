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

import org.b3log.latke.Keys;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.service.ArticleValidationService;
import org.b3log.symphony.service.LogsService;
import org.b3log.symphony.util.ArticleValidation;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;

/** Legacy response adapter for the shared article validator. */
@Singleton
public class ArticlePostValidationMidware {
    public static final int MAX_ARTICLE_TITLE_LENGTH = ArticleValidation.MAX_ARTICLE_TITLE_LENGTH;
    public static final int MAX_ARTICLE_CONTENT_LENGTH = ArticleValidation.MAX_ARTICLE_CONTENT_LENGTH;
    public static final int MIN_ARTICLE_CONTENT_LENGTH = ArticleValidation.MIN_ARTICLE_CONTENT_LENGTH;
    public static final int MAX_ARTICLE_REWARD_CONTENT_LENGTH = ArticleValidation.MAX_ARTICLE_REWARD_CONTENT_LENGTH;
    public static final int MIN_ARTICLE_REWARD_CONTENT_LENGTH = ArticleValidation.MIN_ARTICLE_REWARD_CONTENT_LENGTH;
    public void handle(final RequestContext context) {
        final JSONObject currentUser = (JSONObject) context.attr(User.USER);
        if (null == currentUser) { context.sendError(401); context.abort(); return; }
        try {
            BeanManager.getInstance().getReference(ArticleValidationService.class).validate(context.requestJSON(), currentUser,
                    "POST".equalsIgnoreCase(context.method()), message -> {
                        LogsService.censorLog(context, currentUser.optString(Keys.OBJECT_ID), message);
                        System.out.println(message);
                    });
        } catch (final ArticleValidation.Rejected e) {
            context.renderJSON(new JSONObject().put(Keys.CODE, StatusCodes.ERR).put(Keys.MSG, e.getMessage()));
            context.abort();
            return;
        }
        context.handle();
    }
}
