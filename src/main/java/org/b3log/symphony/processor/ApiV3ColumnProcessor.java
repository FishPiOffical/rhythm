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
import org.b3log.symphony.service.ApiV3ColumnMgmtService;
import org.b3log.symphony.util.ApiV3ColumnInputs;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3ColumnProcessor {
    @Inject
    private ApiV3ColumnMgmtService management;
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3ColumnProcessor p = beans.getReference(ApiV3ColumnProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.put("/api/v3/columns/{columnId}/title", p::title, auth::contentWrite, writes::handle);
        Dispatcher.put("/api/v3/columns/{columnId}/cover", p::cover, auth::contentWrite, writes::handle);
        Dispatcher.put("/api/v3/me/columns/order", p::order, auth::contentWrite, writes::handle);
        Dispatcher.delete("/api/v3/columns/{columnId}", p::remove, auth::contentWrite, writes::handle);
    }
    public void title(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final JSONObject body = ApiV3WriteInputs.body(c, Set.of("title"), false);
            return management.title(actor(c), ApiV3Requests.id(c, "columnId"), ApiV3ColumnInputs.text(body, "title", 64, false), ApiV3WriteInputs.idempotencyKey(c));
        });
    }
    public void cover(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final JSONObject body = ApiV3WriteInputs.body(c, Set.of("coverURL"), false);
            return management.cover(actor(c), ApiV3Requests.id(c, "columnId"), ApiV3ColumnInputs.text(body, "coverURL", 1024, true), ApiV3WriteInputs.idempotencyKey(c));
        });
    }
    public void order(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final JSONObject body = ApiV3WriteInputs.body(c, Set.of("columnIds"), false);
            return management.order(actor(c), ApiV3ColumnInputs.order(body), ApiV3WriteInputs.idempotencyKey(c));
        });
    }
    public void remove(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            ApiV3WriteInputs.body(c, Set.of(), true);
            return management.remove(actor(c), ApiV3Requests.id(c, "columnId"), ApiV3WriteInputs.idempotencyKey(c));
        });
    }
    private static String actor(final RequestContext c) {
        return ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID);
    }
}
