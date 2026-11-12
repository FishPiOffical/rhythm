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
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ChatAppearanceService;
import org.b3log.symphony.service.ApiV3AppearanceShopService;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

import java.util.Set;

@Singleton
public class ApiV3ChatAppearanceProcessor {
    @Inject
    private ApiV3ChatAppearanceService appearance;

    @Inject
    private ApiV3AppearanceShopService shop;

    public static void register() {
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3ChatAppearanceProcessor.class);
        registerRoutes(p, beans.getReference(ApiV3AuthMidware.class), beans.getReference(ApiV3WriteMidware.class));
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        final var anonymous = BeanManager.getInstance().getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/appearance-shop", p::catalog, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/me/appearance-items", p::inventory, auth::profileRead);
        Dispatcher.post("/api/v3/me/appearance-purchases", p::buy, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/appearance-mount", p::mount, auth::settingsWrite, writes::handle);
    }

    public static void registerRoutes(ApiV3ChatAppearanceProcessor p, ApiV3AuthMidware auth, ApiV3WriteMidware writes) {
        Dispatcher.get("/api/v3/me/chat-appearance", p::read, auth::profileRead);
        Dispatcher.put("/api/v3/me/chat-appearance", p::save, auth::settingsWrite, writes::handle);
    }

    public void catalog(RequestContext c) {
        ApiV3Requests.read(c, shop::catalog);
    }

    public void inventory(RequestContext c) {
        ApiV3Requests.read(c, () -> shop.inventory(identity(c)));
    }

    public void buy(RequestContext c) {
        ApiV3Requests.respond(c, () -> shop.buy(identity(c),
                ApiV3WriteInputs.body(c, Set.of("productId", "days", "revision"), false, 1024), ApiV3WriteInputs.idempotencyKey(c)));
    }

    public void mount(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final var body = ApiV3WriteInputs.body(c, Set.of("productId"), false, 1024);
            if (!(body.opt("productId") instanceof String productId)) throw ApiV3WriteInputs.invalid();
            return shop.equipMount(identity(c), productId, ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    public void read(RequestContext c) {
        ApiV3Requests.read(c, () -> appearance.read(identity(c)));
    }

    public void save(RequestContext c) {
        ApiV3Requests.respond(c, () -> appearance.update(identity(c),
                ApiV3WriteInputs.body(c, Set.of("bubbleId", "frameId"), false, 1024),
                ApiV3WriteInputs.idempotencyKey(c)));
    }

    private static JSONObject identity(RequestContext c) {
        return (JSONObject) c.attr(ApiV3Processor.IDENTITY);
    }
}
