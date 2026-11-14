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
import org.b3log.symphony.service.ApiV3ProfessionDetailQueryService;
import org.b3log.symphony.util.ApiV3Requests;

@Singleton
public class ApiV3ProfessionDetailProcessor {
    @Inject
    private ApiV3ProfessionDetailQueryService query;

    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ProfessionDetailProcessor.class), beans.getReference(ApiV3AuthMidware.class));
    }

    public static void registerRoutes(final ApiV3ProfessionDetailProcessor p, final ApiV3AuthMidware auth) {
        Dispatcher.get("/api/v3/me/professions/{professionId}", p::detail, auth::profileRead);
        Dispatcher.get("/api/v3/me/professions/{professionId}/records", p::records, auth::profileRead);
    }

    public void detail(final RequestContext context) {
        ApiV3Requests.read(context, () -> query.detail(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID), context.pathVar("professionId")));
    }

    public void records(final RequestContext context) {
        ApiV3Requests.read(context, () -> query.records(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID), context.pathVar("professionId"),
                ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }
}
