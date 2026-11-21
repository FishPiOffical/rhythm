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

import org.b3log.latke.http.*;
import org.b3log.latke.ioc.*;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.service.ApiV3MfaService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

import java.util.Set;

@Singleton
public class ApiV3MfaProcessor {
    @Inject
    private ApiV3MfaService service;

    public static void register() {
        final var b = BeanManager.getInstance();
        registerRoutes(b.getReference(ApiV3MfaProcessor.class),
                b.getReference(ApiV3AuthMidware.class), b.getReference(ApiV3WriteMidware.class));
    }

    public static void registerRoutes(ApiV3MfaProcessor p, ApiV3AuthMidware auth, ApiV3WriteMidware writes) {
        Dispatcher.post("/api/v3/me/security/mfa/preparations", p::prepare, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/security/mfa", p::enable, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/me/security/mfa", p::disable, auth::settingsWrite, writes::handle);
    }

    public void prepare(RequestContext c) {
        ApiV3Requests.respond(c, () -> service.prepare(identity(c), body(c, false), Requests.getRemoteAddr(c.getRequest())));
    }

    public void enable(RequestContext c) {
        ApiV3Requests.respond(c, () -> service.enable(identity(c), body(c, true),
                ApiV3WriteInputs.idempotencyKey(c), Requests.getRemoteAddr(c.getRequest())));
    }

    public void disable(RequestContext c) {
        ApiV3Requests.respond(c, () -> service.disable(identity(c), body(c, false),
                ApiV3WriteInputs.idempotencyKey(c), Requests.getRemoteAddr(c.getRequest())));
    }

    private static JSONObject identity(RequestContext c) {
        return (JSONObject) c.attr(ApiV3Processor.IDENTITY);
    }

    private static JSONObject body(RequestContext c, boolean enable) {
        // 准备响应可携带当次密钥，因此在读取正文前统一设置响应禁止缓存。
        c.getResponse().setHeader("Cache-Control", "no-store");
        c.getResponse().setHeader("Pragma", "no-cache");
        final byte[] bytes = c.getRequest().getBytes();
        if (bytes != null && bytes.length > 4096)
            throw new ApiV3Exception(413, "body_too_large", "请求体过大");
        return ApiV3WriteInputs.body(c, enable ? Set.of("currentPassword", "currentTotp", "preparationId", "newTotp")
                : Set.of("currentPassword", "currentTotp"), false);
    }
}
