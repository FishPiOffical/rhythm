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
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ClientDownloadService;
import org.b3log.symphony.service.ApiV3RepeaterPublicationService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

import java.util.Set;

@Singleton
public class ApiV3CommunityDirectoryProcessor {
    @Inject private ApiV3ClientDownloadService clients;
    @Inject private ApiV3RepeaterPublicationService repeater;

    public static void register() {
        final var beans = BeanManager.getInstance();
        final var directory = beans.getReference(ApiV3CommunityDirectoryProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.get("/api/v3/client-downloads", directory::publicClients, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/admin/client-downloads", directory::adminClients, auth::profileRead);
        Dispatcher.put("/api/v3/admin/client-downloads/{id}", directory::saveClient, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/repeater/items", directory::adminRepeater, auth::profileRead);
        Dispatcher.post("/api/v3/admin/repeater/items", directory::addRepeater, auth::settingsWrite, writes::handle);
    }

    public void publicClients(RequestContext context) {
        ApiV3Requests.read(context, clients::directory);
    }

    public void adminClients(RequestContext context) {
        ApiV3Requests.read(context, () -> clients.adminList(identity(context), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void saveClient(RequestContext context) {
        ApiV3Requests.respond(context, () -> clients.save(identity(context), context.pathVar("id"),
                ApiV3WriteInputs.body(context, Set.of("revision", "client"), false, 16384),
                ApiV3WriteInputs.idempotencyKey(context), context.getRequest()));
    }

    public void adminRepeater(RequestContext context) {
        ApiV3Requests.read(context, () -> repeater.adminList(identity(context), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void addRepeater(RequestContext context) {
        ApiV3Requests.respond(context, () -> repeater.publishByAdmin(identity(context),
                ApiV3WriteInputs.body(context, Set.of("type", "content"), false, 8192),
                ApiV3WriteInputs.idempotencyKey(context), context.getRequest()));
    }

    private static JSONObject identity(RequestContext context) {
        return (JSONObject) context.attr(ApiV3Processor.IDENTITY);
    }
}
