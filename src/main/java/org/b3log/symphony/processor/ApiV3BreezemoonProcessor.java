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
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.service.ApiV3BreezemoonMgmtService;
import org.b3log.symphony.service.ApiV3DiscoveryQueryService;
import org.b3log.symphony.util.ApiV3BreezemoonInputs;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.symphony.util.Geos;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3BreezemoonProcessor {
    @Inject
    private ApiV3BreezemoonMgmtService management;
    @Inject
    private ApiV3DiscoveryQueryService discovery;
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3BreezemoonProcessor p = beans.getReference(ApiV3BreezemoonProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);
        final AnonymousViewCheckMidware anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/breezemoons/{breezemoonId}", p::detail, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/me/watching/breezemoons", p::following, auth::contentRead);
        Dispatcher.put("/api/v3/breezemoons/{breezemoonId}", p::update, auth::contentWrite, writes::handle);
        Dispatcher.post("/api/v3/breezemoons", p::create, auth::contentWrite, writes::handle);
        Dispatcher.delete("/api/v3/breezemoons/{breezemoonId}", p::remove, auth::contentWrite, writes::handle);
    }
    public void detail(final RequestContext c) {
        ApiV3Requests.read(c, () -> discovery.breezemoon(ApiV3Requests.id(c, "breezemoonId"), ApiV3Requests.viewer(c)));
    }
    public void create(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final String content = ApiV3BreezemoonInputs.content(ApiV3WriteInputs.body(c, Set.of("content"), false));
            final String key = ApiV3WriteInputs.idempotencyKey(c);
            final String ip = Requests.getRemoteAddr(c.getRequest());
            final JSONObject address = Geos.getAddressByGeoIP(ip);
            final String ua = c.header("User-Agent");
            return management.create(ApiV3Requests.viewer(c), content, key, ip, null == ua ? "" : ua,
                    null == address ? "" : address.optString(Common.CITY));
        });
    }
    public void following(final RequestContext c) {
        ApiV3Requests.read(c, () -> discovery.followingBreezemoons(ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID), ApiV3Requests.page(c), ApiV3Requests.size(c)));
    }
    public void update(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final String content = ApiV3BreezemoonInputs.content(ApiV3WriteInputs.body(c, Set.of("content"), false));
            final String ua = c.header("User-Agent");
            return management.update(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "breezemoonId"), content,
                    ApiV3WriteInputs.idempotencyKey(c), Requests.getRemoteAddr(c.getRequest()), null == ua ? "" : ua);
        });
    }
    public void remove(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            ApiV3WriteInputs.body(c, Set.of(), true);
            return management.remove(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "breezemoonId"), ApiV3WriteInputs.idempotencyKey(c));
        });
    }
}
