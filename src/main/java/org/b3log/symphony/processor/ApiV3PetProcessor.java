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
import org.b3log.symphony.service.ApiV3PetService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

import java.util.Set;

/** 第一批入口仅第一方本人可用，不放开匿名盾、Cookie 或旧 apiKey。 */
@Singleton
public class ApiV3PetProcessor {
    @Inject private ApiV3PetService pets;

    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3PetProcessor.class), beans.getReference(ApiV3AuthMidware.class),
                beans.getReference(ApiV3WriteMidware.class));
    }

    public static void registerRoutes(final ApiV3PetProcessor processor, final ApiV3AuthMidware auth,
                                      final ApiV3WriteMidware writes) {
        Dispatcher.get("/api/v3/pets/catalog", processor::catalog, auth::profileRead);
        Dispatcher.get("/api/v3/me/pet", processor::read, auth::profileRead);
        Dispatcher.post("/api/v3/me/pet/adoption", processor::adopt, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/pet/name", processor::rename, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/me/pet/actions", processor::action, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/me/pet/supplies", processor::supplies, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/me/pet/purchases", processor::purchase, auth::settingsWrite, writes::handle);
    }

    public void catalog(final RequestContext context) {
        ApiV3Requests.read(context, () -> pets.catalog(identity(context)));
    }

    public void read(final RequestContext context) {
        ApiV3Requests.read(context, () -> pets.read(identity(context)));
    }

    public void adopt(final RequestContext context) {
        ApiV3Requests.respond(context, () -> pets.adopt(identity(context),
                body(context, Set.of("speciesId", "name")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void rename(final RequestContext context) {
        ApiV3Requests.respond(context, () -> pets.rename(identity(context),
                body(context, Set.of("petId", "name")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void action(final RequestContext context) {
        ApiV3Requests.respond(context, () -> pets.action(identity(context),
                body(context, Set.of("petId", "action", "itemId")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void supplies(final RequestContext context) {
        ApiV3Requests.respond(context, () -> pets.supplies(identity(context),
                body(context, Set.of("expectedDate")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void purchase(final RequestContext context) {
        ApiV3Requests.respond(context, () -> pets.purchase(identity(context),
                body(context, Set.of("petId", "itemId", "quantity", "catalogVersion")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    private static JSONObject body(final RequestContext context, final Set<String> fields) {
        return ApiV3WriteInputs.body(context, fields, false, 2048);
    }

    private static JSONObject identity(final RequestContext context) {
        return (JSONObject) context.attr(ApiV3Processor.IDENTITY);
    }
}
