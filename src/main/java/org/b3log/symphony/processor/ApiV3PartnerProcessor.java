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
import org.b3log.symphony.service.PartnerService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

@Singleton
public class ApiV3PartnerProcessor {
    @Inject private PartnerService partners;

    public static void register() {
        final var beans = BeanManager.getInstance();
        final var processor = beans.getReference(ApiV3PartnerProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        Dispatcher.get("/api/v3/me/partners", processor::state, auth::partnerRead);
        Dispatcher.get("/api/v3/me/partners/card/{id}", processor::card, auth::partnerRead);
        Dispatcher.get("/api/v3/me/partners/activity", processor::activity, auth::partnerRead);
        Dispatcher.post("/api/v3/me/partners/{action}", processor::write, auth::partnerWrite);
    }

    public void state(final RequestContext context) {
        ApiV3Requests.read(context, () -> partners.state((JSONObject) context.attr(ApiV3Processor.IDENTITY), context.param("after"),
                context.param("sort"), context.param("peer")));
    }

    public void card(final RequestContext context) {
        ApiV3Requests.read(context, () -> partners.card((JSONObject) context.attr(ApiV3Processor.IDENTITY), context.pathVar("id")));
    }

    public void activity(final RequestContext context) {
        ApiV3Requests.read(context, () -> partners.activity((JSONObject) context.attr(ApiV3Processor.IDENTITY),
                context.param("kind"), context.param("before")));
    }

    public void write(final RequestContext context) {
        ApiV3Requests.respond(context, () -> partners.command((JSONObject) context.attr(ApiV3Processor.IDENTITY),
                context.pathVar("action"), ApiV3WriteInputs.idempotencyKey(context),
                ApiV3WriteInputs.objectBody(context, false, 512)));
    }
}
