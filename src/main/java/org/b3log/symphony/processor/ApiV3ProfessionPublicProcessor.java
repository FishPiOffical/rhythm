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
import org.b3log.symphony.service.ApiV3ProfessionPublicQueryService;
import org.b3log.symphony.util.ApiV3Requests;

/** Viewer-explicit public profession metadata; never derives identity from page credentials. */
@Singleton
public class ApiV3ProfessionPublicProcessor {
    @Inject private ApiV3ProfessionPublicQueryService query;
    public static void register() {
        final BeanManager beans=BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ProfessionPublicProcessor.class), beans.getReference(ApiV3AuthMidware.class),
                beans.getReference(AnonymousViewCheckMidware.class));
    }
    public static void registerRoutes(final ApiV3ProfessionPublicProcessor p, final ApiV3AuthMidware auth,
                                      final AnonymousViewCheckMidware anonymous) {
        Dispatcher.get("/api/v3/users/{userName}/professions",p::profile,auth::optionalProfile,anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}/professions/summary",p::summary,auth::optionalProfile,anonymous::handle);
    }
    public void summary(final RequestContext c) {
        ApiV3Requests.read(c,()->query.summary(ApiV3Requests.text(c.pathVar("userName"),64,true),
                ApiV3Requests.viewer(c)==null?"":ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID)));
    }
    public void profile(final RequestContext c) {
        ApiV3Requests.read(c,()->query.profile(ApiV3Requests.text(c.pathVar("userName"),64,true),
                ApiV3Requests.viewer(c)==null?"":ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID)));
    }
}
