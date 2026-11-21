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
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.service.ApiV3MedalQueryService;
import org.b3log.symphony.util.ApiV3Requests;

/**
 * 勋章只读投影，独立于会清理过期授予记录的旧读取方法。
 */
@Singleton
public class ApiV3MedalProcessor {
    @Inject
    private ApiV3MedalQueryService query;

    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3MedalProcessor p = beans.getReference(ApiV3MedalProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final AnonymousViewCheckMidware anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/me/medals", p::mine, auth::profileRead);
        Dispatcher.get("/api/v3/users/{userName}/medals", p::displayed, auth::optionalProfile, anonymous::handle);
    }

    public void mine(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.mine(ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID), ApiV3Requests.page(c), ApiV3Requests.size(c)));
    }

    public void displayed(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.displayed(ApiV3Requests.text(c.pathVar("userName"), 64, true), ApiV3Requests.page(c), ApiV3Requests.size(c)));
    }
}
