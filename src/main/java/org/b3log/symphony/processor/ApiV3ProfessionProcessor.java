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
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3ProfessionQueryService;
import org.b3log.symphony.util.ApiV3Requests;

/** Read-only profession directory and authenticated owner's private state. */
@Singleton
public class ApiV3ProfessionProcessor {
    @Inject private ApiV3ProfessionQueryService query;
    public static void register() {
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3ProfessionProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/professions", p::catalog, auth::optionalProfile, anonymous::handle);
        Dispatcher.get("/api/v3/me/professions", p::mine, auth::profileRead);
    }
    public void catalog(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.catalog(ApiV3Requests.page(c), ApiV3Requests.size(c)));
    }
    public void mine(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.mine(ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID),
                ApiV3Requests.page(c), ApiV3Requests.size(c)));
    }
}
