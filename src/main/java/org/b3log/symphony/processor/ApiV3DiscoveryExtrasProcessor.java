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
import org.b3log.symphony.service.ApiV3DiscoveryExtrasQueryService;
import org.b3log.symphony.util.ApiV3Requests;

@Singleton
public class ApiV3DiscoveryExtrasProcessor {
    @Inject private ApiV3DiscoveryExtrasQueryService extras;
    public static void register() {
        final var beans = BeanManager.getInstance();
        final var processor = beans.getReference(ApiV3DiscoveryExtrasProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/recent-members", processor::recent, auth::optionalProfile, anonymous::handle);
        Dispatcher.get("/api/v3/articles/random", processor::random, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/logs", processor::logs, auth::activityRead);
    }
    public void recent(final RequestContext context) { ApiV3Requests.read(context, extras::recent); }
    public void random(final RequestContext context) {
        ApiV3Requests.read(context, () -> extras.random(ApiV3Requests.viewer(context),
                ApiV3Requests.number(context, "size", 8, 20), ApiV3Requests.text(context.param("excludeId"), 32, false)));
    }
    public void logs(final RequestContext context) {
        ApiV3Requests.read(context, () -> extras.logs(ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }
}
