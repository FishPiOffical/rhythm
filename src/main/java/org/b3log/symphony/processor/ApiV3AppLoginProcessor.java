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
import org.b3log.latke.util.Requests;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.service.ApiV3AppLoginService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

import java.util.Set;

/**
 * 通过 JSON 传递应用登录凭据。只有发起请求的网站使用 Bearer 身份，原生应用凭据不放入 URL。
 */
@Singleton
public class ApiV3AppLoginProcessor {
    @Inject
    private ApiV3AppLoginService service;

    public static void register() {
        ApiV3OAuthTransportLogFilter.requireInstalled();
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3AppLoginProcessor.class), beans.getReference(ApiV3AuthMidware.class),
                beans.getReference(ApiV3WriteMidware.class), beans.getReference(AnonymousViewCheckMidware.class));
    }

    public static void registerRoutes(ApiV3AppLoginProcessor p, ApiV3AuthMidware auth, ApiV3WriteMidware writes,
                                      AnonymousViewCheckMidware anonymous) {
        Dispatcher.post("/api/v3/auth/app-login/requests", p::create, p::privacy, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/auth/app-login/status", p::webStatus, p::privacy, auth::profileRead, writes::handle);
        Dispatcher.post("/api/v3/auth/app-login/decision", p::webDecision, p::privacy, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/auth/app-login/claim", p::claim, p::privacy, p::budget, anonymous::handle);
        Dispatcher.post("/api/v3/auth/app-login/app-status", p::appStatus, p::privacy, p::budget, anonymous::handle);
        Dispatcher.post("/api/v3/auth/app-login/app-decision", p::appDecision, p::privacy, p::budget, anonymous::handle);
        Dispatcher.post("/api/v3/auth/app-login/exchange", p::exchange, p::privacy, p::budget, anonymous::handle);
    }

    public void privacy(RequestContext c) {
        c.setHeader("Cache-Control", "no-store");
        c.setHeader("Pragma", "no-cache");
        c.setHeader("Referrer-Policy", "no-referrer");
        c.setHeader("X-Content-Type-Options", "nosniff");
        c.handle();
    }

    public void budget(RequestContext c) {
        if (!ApiV3OAuthRateLimit.ip(Requests.getRemoteAddr(c.getRequest()))) {
            c.setHeader("Retry-After", "60");
            ApiV3Processor.error(c, 429, "too_many_requests", "请求过于频繁，请稍后再试");
            c.abort();
            return;
        }
        c.handle();
    }

    public void create(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            body(c, Set.of(), true);
            return service.create(identity(c));
        });
    }

    public void webStatus(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            var b = body(c, Set.of("request"), false);
            return service.webStatus(identity(c), value(b, "request"));
        });
    }

    public void webDecision(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            var b = body(c, Set.of("request", "decision"), false);
            return service.webDecision(identity(c), value(b, "request"), value(b, "decision"));
        });
    }

    public void claim(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            var b = body(c, Set.of("request", "codeChallenge"), false);
            return service.claim(value(b, "request"), value(b, "codeChallenge"));
        });
    }

    public void appStatus(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            var b = body(c, Set.of("request", "codeVerifier"), false);
            return service.appStatus(value(b, "request"), value(b, "codeVerifier"));
        });
    }

    public void appDecision(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            var b = body(c, Set.of("request", "codeVerifier", "decision"), false);
            return service.appDecision(value(b, "request"), value(b, "codeVerifier"), value(b, "decision"));
        });
    }

    public void exchange(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            var b = body(c, Set.of("request", "codeVerifier"), false);
            return service.exchange(value(b, "request"), value(b, "codeVerifier"));
        });
    }

    private static JSONObject identity(RequestContext c) {
        return (JSONObject) c.attr(ApiV3Processor.IDENTITY);
    }

    private static String value(JSONObject body, String field) {
        return ApiV3WriteInputs.string(body, field, 43, false);
    }

    private static JSONObject body(RequestContext c, Set<String> allowed, boolean empty) {
        final byte[] bytes = c.getRequest().getBytes();
        if (bytes != null && bytes.length > 4096)
            throw new ApiV3Exception(413, "body_too_large", "请求体过大");
        return ApiV3WriteInputs.body(c, allowed, empty);
    }
}
