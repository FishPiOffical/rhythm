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
import org.b3log.symphony.service.ApiV3ChatTailService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

import java.util.Set;

@Singleton
public class ApiV3ChatTailProcessor {
    @Inject private ApiV3ChatTailService tails;

    public static void register() {
        final var beans = BeanManager.getInstance();
        final var processor = beans.getReference(ApiV3ChatTailProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.get("/api/v3/me/settings/chat-tail", processor::read, auth::profileRead);
        Dispatcher.put("/api/v3/me/settings/chat-tail", processor::save, auth::settingsWrite, writes::handle);
    }

    public void read(final RequestContext context) {
        ApiV3Requests.read(context, () -> tails.read(identity(context)));
    }

    public void save(final RequestContext context) {
        ApiV3Requests.respond(context, () -> tails.update(identity(context),
                ApiV3WriteInputs.body(context, Set.of("enabled", "text"), false, 8192),
                ApiV3WriteInputs.idempotencyKey(context)));
    }

    private static JSONObject identity(final RequestContext context) {
        return (JSONObject) context.attr(ApiV3Processor.IDENTITY);
    }
}
