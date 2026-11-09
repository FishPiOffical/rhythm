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
import org.b3log.symphony.service.ApiV3PersonalQueryService;
import org.b3log.symphony.util.ApiV3Requests;

/** 个人资源的用户身份来自当前认证，查询参数、路径及正文均不接收目标用户。 */
@Singleton
public class ApiV3PersonalProcessor {
    @Inject private ApiV3PersonalQueryService personal;

    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3PersonalProcessor processor = beans.getReference(ApiV3PersonalProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        Dispatcher.get("/api/v3/me/settings", processor::settings, auth::profileRead);
        Dispatcher.get("/api/v3/me/checkin", processor::checkin, auth::activityRead);
        Dispatcher.get("/api/v3/me/bag", processor::bag, auth::activityRead);
        Dispatcher.get("/api/v3/me/points", processor::points, auth::activityRead);
        Dispatcher.get("/api/v3/me/drafts", processor::drafts, auth::contentRead);
        Dispatcher.get("/api/v3/me/drafts/{draftId}", processor::draft, auth::contentRead);
        Dispatcher.get("/api/v3/me/columns", processor::columns, auth::contentRead);
        Dispatcher.get("/api/v3/me/articles/{articleId}/source", processor::articleSource, auth::contentRead);
    }

    public void settings(final RequestContext context) {
        ApiV3Requests.read(context, () -> personal.settings(ApiV3Requests.viewer(context)));
    }
    public void checkin(final RequestContext context) {
        ApiV3Requests.read(context, () -> personal.checkin(ApiV3Requests.viewer(context)));
    }
    public void bag(final RequestContext context) {
        ApiV3Requests.read(context, () -> personal.bag(userId(context)));
    }
    public void points(final RequestContext context) {
        ApiV3Requests.read(context, () -> personal.points(userId(context), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }
    public void drafts(final RequestContext context) {
        ApiV3Requests.read(context, () -> personal.drafts(userId(context), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }
    public void draft(final RequestContext context) {
        ApiV3Requests.read(context, () -> personal.draft(userId(context), ApiV3Requests.id(context, "draftId")));
    }
    public void columns(final RequestContext context) {
        ApiV3Requests.read(context, () -> personal.columns(userId(context), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }
    public void articleSource(final RequestContext context) {
        ApiV3Requests.read(context, () -> personal.articleSource(userId(context), ApiV3Requests.id(context, "articleId")));
    }
    private static String userId(final RequestContext context) {
        return ApiV3Requests.viewer(context).optString(Keys.OBJECT_ID);
    }
}
