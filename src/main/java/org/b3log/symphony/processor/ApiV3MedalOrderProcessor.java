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
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3MedalOrderService;
import org.b3log.symphony.util.*;
import java.util.Set;

@Singleton
public class ApiV3MedalOrderProcessor {
    @Inject private ApiV3MedalOrderService management;
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3MedalOrderProcessor p = beans.getReference(ApiV3MedalOrderProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/me/medals/{medalId}/move", p::move, auth::settingsWrite, writes::handle);
    }
    public void move(final RequestContext c) {
        ApiV3Requests.respond(c, () -> management.move(ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID),
                ApiV3MedalDisplay.medalId(c.pathVar("medalId")),
                ApiV3MedalOrderInputs.direction(ApiV3WriteInputs.body(c, Set.of("direction"), false)), ApiV3WriteInputs.idempotencyKey(c)));
    }
}
