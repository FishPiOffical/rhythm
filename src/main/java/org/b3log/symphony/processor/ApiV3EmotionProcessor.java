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
import org.b3log.symphony.service.ApiV3EmotionQueryService;
import org.b3log.symphony.util.ApiV3EmotionOptions;
import org.b3log.symphony.util.ApiV3Requests;

/**
 * 仅本人可读取的表情设置。
 */
@Singleton
public class ApiV3EmotionProcessor {
    @Inject
    private ApiV3EmotionQueryService query;

    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3EmotionProcessor p = beans.getReference(ApiV3EmotionProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        Dispatcher.get("/api/v3/me/settings/emotions", p::emotions, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/emotion-options", p::options, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/emoji-groups", p::groups, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/emoji-groups/{groupId}/emojis", p::items, auth::profileRead);
    }

    public void emotions(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.emotions(actor(c)));
    }

    public void options(final RequestContext c) {
        ApiV3Requests.read(c, ApiV3EmotionOptions::options);
    }

    public void groups(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.groups(actor(c), ApiV3Requests.page(c), ApiV3Requests.size(c)));
    }

    public void items(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.items(actor(c), ApiV3Requests.id(c, "groupId"),
                ApiV3Requests.page(c), ApiV3Requests.size(c), c.param("order")));
    }

    private static String actor(final RequestContext c) {
        return ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID);
    }
}
