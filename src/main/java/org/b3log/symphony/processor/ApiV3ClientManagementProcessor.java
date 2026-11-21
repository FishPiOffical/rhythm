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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ClientManagementService;
import org.b3log.symphony.util.ApiV3ClientManagementInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

import java.util.Set;

@Singleton
public class ApiV3ClientManagementProcessor {
    @Inject
    private ApiV3ClientManagementService management;

    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ClientManagementProcessor.class), beans.getReference(ApiV3AuthMidware.class),
                beans.getReference(ApiV3WriteMidware.class));
    }

    static void registerRoutes(final ApiV3ClientManagementProcessor p, final ApiV3AuthMidware auth, final ApiV3WriteMidware writes) {
        Dispatcher.get("/api/v3/admin/oauth/clients", p::list, auth::settingsWrite, p::administrator);
        Dispatcher.get("/api/v3/admin/oauth/clients/{clientId}", p::detail, auth::settingsWrite, p::administrator);
        Dispatcher.post("/api/v3/admin/oauth/clients", p::create, auth::settingsWrite, p::administrator, writes::handle);
    }

    private static JSONObject identity(final RequestContext c) {
        return (JSONObject) c.attr(ApiV3Processor.IDENTITY);
    }

    public void administrator(final RequestContext c) {
        c.getResponse().setHeader("Cache-Control", "no-store");
        c.getResponse().setHeader("Vary", "Authorization");
        try {
            management.requireAdministrator(identity(c), false);
            c.handle();
        } catch (ApiV3Exception e) {
            ApiV3Processor.error(c, e.status, e.code, e.getMessage());
            c.abort();
        } catch (RepositoryException e) {
            final var error = ApiV3ClientManagementService.unavailable();
            ApiV3Processor.error(c, error.status, error.code, error.getMessage());
            c.abort();
        }
    }

    public void list(final RequestContext c) {
        ApiV3Requests.read(c, () -> read(() -> management.list(identity(c), ApiV3Requests.page(c), ApiV3Requests.size(c))));
    }

    public void detail(final RequestContext c) {
        ApiV3Requests.read(c, () -> read(() -> management.detail(identity(c), ApiV3ClientManagementInputs.clientId(c.pathVar("clientId")),
                ApiV3Requests.page(c), ApiV3Requests.size(c))));
    }

    public void create(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final var body = ApiV3WriteInputs.body(c, Set.of("name", "scope", "redirectUris"), false);
            return management.create(identity(c), ApiV3ClientManagementInputs.registration(body), ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    private static Object read(final ApiV3Requests.ReadOperation operation) throws Exception {
        try {
            return operation.run();
        } catch (RepositoryException e) {
            throw ApiV3ClientManagementService.unavailable();
        }
    }
}
