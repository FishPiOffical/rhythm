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
import org.b3log.symphony.service.ApiV3MilestoneQueryService;
import org.b3log.symphony.service.ApiV3MilestoneMgmtService;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.util.ApiV3MilestoneInputs;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.symphony.util.ApiV3Requests;

import java.util.Set;

@Singleton
public class ApiV3MilestoneProcessor {
    @Inject
    private ApiV3MilestoneQueryService query;
    @Inject
    private ApiV3MilestoneMgmtService management;

    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3MilestoneProcessor p = beans.getReference(ApiV3MilestoneProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/milestones", p::create, auth::contentWrite, writes::handle);
        Dispatcher.get("/api/v3/me/milestones", p::list, auth::contentRead);
        Dispatcher.get("/api/v3/me/milestones/{milestoneId}", p::detail, auth::contentRead);
    }

    public void create(final RequestContext c) {
        ApiV3Requests.respond(c, () -> management.create(actor(c), ApiV3MilestoneInputs.normalize(
                ApiV3WriteInputs.body(c, ApiV3MilestoneInputs.FIELDS, false)), ApiV3WriteInputs.idempotencyKey(c)));
    }

    public void list(final RequestContext c) {
        ApiV3Requests.read(c, () -> {
            final String status = ApiV3Requests.choice(c, "status", "all", Set.of("all", "pending", "approved", "rejected"));
            final int code = switch (status) {
                case "pending" -> 1;
                case "approved" -> 2;
                case "rejected" -> 3;
                default -> 0;
            };
            return query.list(actor(c), code, ApiV3Requests.page(c), ApiV3Requests.size(c));
        });
    }

    public void detail(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.detail(actor(c), ApiV3Requests.id(c, "milestoneId")));
    }

    private static String actor(final RequestContext c) {
        return ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID);
    }
}
