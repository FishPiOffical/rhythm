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

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3RepeaterQueryService;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3RepeaterProcessor {
    @Inject
    private ApiV3RepeaterQueryService query;

    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3RepeaterProcessor.class), beans.getReference(ApiV3AuthMidware.class),
                beans.getReference(AnonymousViewCheckMidware.class));
    }

    public static void registerRoutes(final ApiV3RepeaterProcessor p, final ApiV3AuthMidware auth,
                                      final AnonymousViewCheckMidware anonymous) {
        Dispatcher.get("/api/v3/repeater/items",p::items,auth::optionalContent,anonymous::handle);
        Dispatcher.get("/api/v3/repeater/next",p::next,auth::optionalContent,anonymous::handle);
    }

    public void items(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.items(actor(c), type(c), ApiV3Requests.number(c, "size", 12, 50)));
    }

    public void next(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.next(actor(c), type(c), exclude(c)));
    }

    private static String type(final RequestContext c) {
        return ApiV3Requests.choice(c, "type", "", Set.of("joke", "kfc", "fish"));
    }

    private static String exclude(final RequestContext c) {
        final String value = ApiV3Requests.text(c.param("excludeId"), 32, false);
        if (!value.isEmpty() && !value.matches("[0-9]{1,32}")) throw ApiV3Requests.invalid();
        return value;
    }

    private static String actor(final RequestContext c) {
        final JSONObject viewer = ApiV3Requests.viewer(c);
        return viewer == null ? "" : viewer.optString("oId");
    }
}
