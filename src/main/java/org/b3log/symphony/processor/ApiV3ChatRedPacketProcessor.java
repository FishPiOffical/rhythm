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
import org.b3log.latke.ioc.*;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.service.ApiV3ChatRedPacketService;
import org.b3log.symphony.util.*;
import java.util.Set;

@Singleton
public class ApiV3ChatRedPacketProcessor {
    @Inject
    private ApiV3ChatRedPacketService packets;

    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ChatRedPacketProcessor.class),
                beans.getReference(ApiV3AuthMidware.class), beans.getReference(ApiV3WriteMidware.class));
    }

    public static void registerRoutes(ApiV3ChatRedPacketProcessor processor,ApiV3AuthMidware auth,ApiV3WriteMidware writes){
        Dispatcher.post("/api/v3/chat/red-packets",processor::create,auth::contentWrite,auth::activityWrite,writes::handle);
        Dispatcher.get("/api/v3/chat/red-packets/{id}",processor::detail,auth::contentRead);
        Dispatcher.post("/api/v3/chat/red-packets/{id}/claims",processor::claim,auth::activityWrite,writes::handle);
    }

    public void create(RequestContext c) {
        ApiV3Requests.respond(c, () -> packets.create(ApiV3Requests.viewer(c),
                ApiV3WriteInputs.body(c, Set.of("type", "money", "count", "message", "recipientIds", "riskAccepted", "gesture"), false),
                ApiV3WriteInputs.idempotencyKey(c)));
    }

    public void detail(RequestContext c) {
        ApiV3Requests.read(c, () -> packets.detail(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "id")));
    }

    public void claim(RequestContext c) {
        // 普通红包领取允许空正文，风险确认或猜拳手势由请求正文传入。
        ApiV3Requests.respond(c, () -> packets.claim(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "id"),
                ApiV3WriteInputs.body(c, Set.of("riskAccepted", "gesture"), true),
                ApiV3WriteInputs.idempotencyKey(c)));
    }
}
