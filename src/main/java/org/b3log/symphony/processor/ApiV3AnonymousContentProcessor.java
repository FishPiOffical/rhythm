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

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3AnonymousContentQueryService;
import org.b3log.symphony.util.ApiV3Requests;

/** 仅登录用户本人可查看自己的匿名创作，其他用户的资料入口不提供这些内容。 */
@Singleton
public class ApiV3AnonymousContentProcessor {
    @Inject
    private ApiV3AnonymousContentQueryService query;
    public static void register() {
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3AnonymousContentProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        Dispatcher.get("/api/v3/me/anonymous-articles", p::articles, auth::contentRead);
        Dispatcher.get("/api/v3/me/anonymous-comments", p::comments, auth::contentRead);
    }
    public void articles(final RequestContext context) {
        ApiV3Requests.read(context, () -> query.articles(ApiV3Requests.viewer(context).optString(Keys.OBJECT_ID),
                ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }
    public void comments(final RequestContext context) {
        ApiV3Requests.read(context, () -> query.comments(ApiV3Requests.viewer(context).optString(Keys.OBJECT_ID),
                ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }
}
