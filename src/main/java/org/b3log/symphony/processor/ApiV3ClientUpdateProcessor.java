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
import org.b3log.symphony.service.ApiV3ClientUpdateService;
import org.b3log.symphony.util.ApiV3ClientManagementInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3ClientUpdateProcessor {
    @Inject
    private ApiV3ClientManagementService management;
    @Inject
    private ApiV3ClientUpdateService service;

    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ClientUpdateProcessor.class), beans.getReference(ApiV3AuthMidware.class),
                beans.getReference(ApiV3WriteMidware.class));
    }
    static void registerRoutes(final ApiV3ClientUpdateProcessor p, final ApiV3AuthMidware auth, final ApiV3WriteMidware writes) {
        Dispatcher.put("/api/v3/admin/oauth/clients/{clientId}", p::update, auth::settingsWrite, p::administrator, writes::handle);
    }
    public void administrator(final RequestContext context) {
        context.getResponse().setHeader("Cache-Control", "no-store");
        context.getResponse().setHeader("Vary", "Authorization");
        try {
            management.requireAdministrator(identity(context), false);
            context.handle();
        } catch (final ApiV3Exception e) {
            ApiV3Processor.error(context, e.status, e.code, e.getMessage());
            context.abort();
        } catch (final RepositoryException e) {
            final var error = ApiV3ClientUpdateService.unavailable();
            ApiV3Processor.error(context, error.status, error.code, error.getMessage());
            context.abort();
        }
    }
    public void update(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, Set.of("name", "scope", "redirectUris"), false);
            return service.update(identity(context), ApiV3ClientManagementInputs.clientId(context.pathVar("clientId")),
                    ApiV3ClientManagementInputs.registration(body), ApiV3WriteInputs.idempotencyKey(context));
        });
    }
    private static JSONObject identity(final RequestContext context) {
        return (JSONObject) context.attr(ApiV3Processor.IDENTITY);
    }
}
