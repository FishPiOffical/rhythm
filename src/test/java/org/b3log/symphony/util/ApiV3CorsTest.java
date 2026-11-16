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
package org.b3log.symphony.util;

import io.netty.handler.codec.http.*;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.testng.Assert;
import org.b3log.symphony.processor.ApiV3OAuthProcessor;
import org.b3log.symphony.service.ApiV3OAuthCorsService;
import org.b3log.symphony.repository.ApiV3ClientRepository;
import org.b3log.symphony.repository.ApiV3ClientRedirectRepository;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/** 使用真实请求和响应校验跨域头，不建立用户会话。 */
public class ApiV3CorsTest {
    private static void check(final String path, final String method, final String origin,
                              final String allowed, final boolean credentials) {
        final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), path);
        try {
            raw.headers().set("Origin", origin);
            final var response = new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            final var context = new RequestContext(new Request(null, raw), response);
            Assert.assertTrue(ApiV3Cors.apply(context));
            Assert.assertEquals(response.getHeader("Access-Control-Allow-Origin"), allowed);
            Assert.assertEquals(response.getHeader("Access-Control-Allow-Credentials"), credentials ? "true" : null);
            Assert.assertEquals(response.getHeader("Vary"), "Origin");
        } finally {
            raw.release();
        }
    }

    private static void oauth(final String origin, final boolean allowed) throws Exception {
        final var processor = new ApiV3OAuthProcessor();
        final var policy = new ApiV3OAuthCorsService();
        final var clients = ApiV3OAuthCorsService.class.getDeclaredField("clients"); clients.setAccessible(true);
        clients.set(policy, new ApiV3ClientRepository() {
            public JSONObject getByClientId(String id) { return new JSONObject().put("clientId", id).put("status", 0); }
        });
        final var redirects = ApiV3OAuthCorsService.class.getDeclaredField("redirects"); redirects.setAccessible(true);
        redirects.set(policy, new ApiV3ClientRedirectRepository() {
            public List<JSONObject> select(String sql, Object... args) {
                return List.of(new JSONObject().put("clientId", "third_client").put("redirectUri", "https://third.example/callback"));
            }
        });
        final var field = ApiV3OAuthProcessor.class.getDeclaredField("cors"); field.setAccessible(true); field.set(processor, policy);
        final var method = ApiV3OAuthProcessor.class.getDeclaredMethod("cors", RequestContext.class, String.class, Map.class);
        method.setAccessible(true);
        final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/v3/oauth/token");
        try {
            raw.headers().set("Origin", origin);
            final var response = new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            final var context = new RequestContext(new Request(null, raw), response);
            ApiV3Cors.apply(context);
            try {
                method.invoke(processor, context, "third_client", Map.of());
                Assert.assertTrue(allowed);
                Assert.assertEquals(response.getHeader("Access-Control-Allow-Origin"), origin);
                Assert.assertNull(response.getHeader("Access-Control-Allow-Credentials"));
            } catch (java.lang.reflect.InvocationTargetException denied) {
                Assert.assertFalse(allowed);
                Assert.assertTrue(denied.getCause() instanceof ApiV3Exception);
                Assert.assertEquals(((ApiV3Exception) denied.getCause()).status, 403);
                Assert.assertNull(response.getHeader("Access-Control-Allow-Origin"));
            }
        } finally { raw.release(); }
    }

    public static void main(final String[] args) throws Exception {
        final var properties = new Properties();
        properties.setProperty("runtimeDatabase", "NONE");
        properties.setProperty("runtimeMode", "PRODUCTION");
        Latkes.setLatkeProps(properties);
        Latkes.setLocalProps(properties);
        Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);
        Latkes.setServerScheme("https");
        Latkes.setServerHost("fishpi.cn");
        Latkes.setServerPort("443");
        Latkes.setContextPath("");
        for (final String origin : new String[]{"https://new.fishpi.cn", "https://fishpi.cn"}) {
            for (final String route : new String[]{"/api/v3/me", "/api/v3/auth/refresh", "/api/v3/oauth/token", "/validateCaptcha"})
                check(route, "POST", origin, origin, true);
        }
        for (final String origin : new String[]{"https://third.example", "https://unused.fishpi.cn", "http://new.fishpi.cn",
                "https://new.fishpi.cn:444", "https://new.fishpi.cn.third.example"}) {
            check("/api/v3/me", "GET", origin, "*", false);
            check("/validateCaptcha", "POST", origin, "*", false);
            for (final String route : new String[]{"login", "refresh", "logout", "session"})
                check("/api/v3/auth/" + route, "POST", origin, null, false);
            check("/api/v3/oauth/consent", "POST", origin, null, false);
            check("/api/v3/oauth/token", "POST", origin, null, false);
            check("/api/v3/oauth/revoke", "POST", origin, null, false);
            check("/api/v3/oauth/token", "OPTIONS", origin, "*", false);
        }
        for (final String origin : new String[]{"null", "https://new.fishpi.cn/path", "https://new.fishpi.cn?x=1",
                "https://new.fishpi.cn#x", "https://new.fishpi.cn@third.example/path"})
            check("/api/v3/me", "GET", origin, null, false);
        oauth("https://third.example", true);
        oauth("https://unregistered.example", false);
        Latkes.setContextPath("/community");
        check("/community/api/v3/me", "GET", "https://third.example", "*", false);
        check("/community/api/v3/auth/refresh", "POST", "https://third.example", null, false);
        Latkes.setContextPath("");
        Latkes.setServerScheme("http");
        Latkes.setServerHost("localhost");
        Latkes.setServerPort("8080");
        Latkes.setRuntimeMode(Latkes.RuntimeMode.DEVELOPMENT);
        check("/api/v3/auth/refresh", "POST", "http://localhost:5173", "http://localhost:5173", true);
        System.setProperty("rhythm.dev.websiteOrigin", "http://127.0.0.1:5174");
        try {
            check("/api/v3/auth/refresh", "POST", "http://127.0.0.1:5174", "http://127.0.0.1:5174", true);
            Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);
            check("/api/v3/auth/refresh", "POST", "http://127.0.0.1:5174", null, false);
        } finally { System.clearProperty("rhythm.dev.websiteOrigin"); }
        System.out.println("PASS trusted website credentials, third-party Bearer, OAuth isolation, invalid origins and context path");
    }
}
