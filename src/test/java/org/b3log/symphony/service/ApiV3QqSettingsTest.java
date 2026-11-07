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
package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.symphony.processor.ApiV3ProfileSettingsProcessor;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.util.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** Actual first-party SQL proof, strict writes/receipts and HTTP gates; SQLite strips FOR UPDATE. */
public class ApiV3QqSettingsTest {
    static Object field(Object target, Class<?> type, String name) throws Exception {
        final var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    static class Fixture extends ApiV3IdentityApplicationTest.Fixture {
        final ApiV3QqSettingsService qq = new ApiV3QqSettingsService() { protected long now() { return NOW; } };
        final ApiV3ProfileSettingsProcessor endpointQq = new ApiV3ProfileSettingsProcessor();
        Fixture() throws Exception {
            sql(db, "ALTER TABLE u ADD userQQ TEXT DEFAULT ''"); sql(db, "ALTER TABLE u ADD userUpdateTime BIGINT DEFAULT 0");
            sql(db, "UPDATE u SET userQQ='00123' WHERE oId='1'"); sql(db, "UPDATE u SET userQQ='00999' WHERE oId='2'");
            inject(qq, "users", field(proof, ApiV3SecurityProofService.class, "users"));
            inject(qq, "requests", field(service, ApiV3IdentityApplicationService.class, "requests"));
            inject(qq, "proofs", proof); inject(endpointQq, "qq", qq);
        }
        String stored() throws Exception { return rows(db,"SELECT userQQ FROM u WHERE oId='1'").getFirst().getString("userQQ"); }
        RequestContext requestQq(String method, String token, String key, String body) throws Exception {
            final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.valueOf(method), "/api/v3/me/settings/qq");
            try {
                if (token != null) raw.headers().set("Authorization", "Bearer " + token);
                if (key != null) raw.headers().set("Idempotency-Key", key);
                raw.headers().set("Content-Type", "application/json"); raw.headers().set("Cookie", "legacy"); raw.headers().set("X-Real-IP", "127.0.0.1");
                final var request = new Request(null, raw); raw.content().clear().writeBytes(body == null ? new byte[0] : body.getBytes(java.nio.charset.StandardCharsets.UTF_8));request.setBytes(body == null ? new byte[0] : body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                request.setParams(new HashMap<>(Map.of("apiKey", "legacy")));
                final var context = new RequestContext(request, new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
                context.addHandler(new org.b3log.latke.http.handler.RouteHandler());
                context.addHandler(c -> { if (method.equals("GET")) endpointQq.qq(c); else endpointQq.updateQq(c); }); context.handle(); return context;
            } finally { raw.release(); }
        }
    }
    public static void main(String[] args) throws Exception {
        final var props = new Properties(); props.setProperty("runtimeDatabase", "NONE"); props.setProperty("contextPath", ""); Latkes.setLatkeProps(props); Latkes.setLocalProps(props);
        for (String value : List.of("", "0", "00123", "123456789012")) Assert.assertEquals(ApiV3QqSettingsService.normalize(new JSONObject().put("qq", " " + value + " ")).getString("qq"), value);
        for (String value : List.of("NaN", "Infinity", "1e3", "+123", "-123", "12.3", "１２３", "١٢٣", "1234567890123", "1 23", "<b>123</b>")) error(422, () -> ApiV3QqSettingsService.normalize(new JSONObject().put("qq", value)));
        error(422, () -> ApiV3QqSettingsService.normalize(new JSONObject().put("qq", 123))); error(422, () -> ApiV3QqSettingsService.normalize(new JSONObject().put("qq", "123").put("userId", "2")));
        System.out.println("PASS qq 1 exact ASCII 1..12 digits, strip/clear/leading-zero semantics; floats and hidden owner inputs rejected");
        try (var f = new Fixture()) {
            Assert.assertEquals(f.qq.read(identity()).toString(), new JSONObject().put("qq", "00123").toString());
            final var first = f.qq.update(identity(), new JSONObject().put("qq", " 000456 "), "same"); Assert.assertEquals(first.keySet(), Set.of("qq")); Assert.assertEquals(f.stored(), "000456"); Assert.assertEquals(f.count("receipt"), 1);
            f.qq.update(identity(), new JSONObject().put("qq", "789"), "later"); Assert.assertEquals(f.qq.update(identity(), new JSONObject().put("qq", "000456"), "same").getString("qq"), "000456"); Assert.assertEquals(f.stored(), "789");
            error(409, () -> f.qq.update(identity(), new JSONObject().put("qq", "1"), "same"));
            Assert.assertEquals(rows(f.db, "SELECT userQQ FROM u WHERE oId='2'").getFirst().getString("userQQ"), "00999");
            f.qq.update(identity(), new JSONObject().put("qq", ""), "clear"); Assert.assertEquals(f.stored(), "");
        }
        System.out.println("PASS qq 2 owner-only projection and atomic setting/receipt, normalized replay, no later-value overwrite, conflict and clear");
        try (var f = new Fixture()) {
            sql(f.db, "UPDATE tokens SET tokenType='access',scope='profile.read settings.write' WHERE oId='101'");
            final var third = new JSONObject().put("tokenId", "101").put("user", new JSONObject().put("oId", "1"));
            error(403, () -> f.qq.read(third)); error(403, () -> f.qq.update(third, new JSONObject().put("qq", "456"), "third"));
            f.qq.update(identity(), new JSONObject().put("qq", "456"), "valid"); sql(f.db, "UPDATE tokens SET state=1 WHERE oId='100'");
            error(403, () -> f.qq.read(identity())); error(403, () -> f.qq.update(identity(), new JSONObject().put("qq", "456"), "valid")); Assert.assertEquals(f.count("receipt"), 1);
        }
        try (var f = new Fixture()) { sql(f.db, "UPDATE u SET userQQ='NaN' WHERE oId='1'"); Assert.assertEquals(f.qq.read(identity()).getString("qq"), "NaN"); error(422, () -> f.qq.update(identity(), new JSONObject().put("qq", "NaN"), "legacy")); }
        System.out.println("PASS qq 3 real SQL first-party/current-grant checks apply to reads and replays; legacy invalid stored value is not falsely cleared");
        for (String failure : List.of("UPDATE `u`", "receipt", "commit")) try (var f = new Fixture()) {
            if (failure.startsWith("UPDATE")) f.writeFault = failure; else f.fault = failure;
            error(503, () -> f.qq.update(identity(), new JSONObject().put("qq", "456"), "fault")); Assert.assertEquals(f.stored(), "00123"); Assert.assertEquals(f.count("receipt"), 0);
        }
        try (var f = new Fixture()) { f.fault = "ack"; error(503, () -> f.qq.update(identity(), new JSONObject().put("qq", "456"), "ack")); Assert.assertEquals(f.stored(), "456"); f.fault = ""; Assert.assertEquals(f.qq.update(identity(), new JSONObject().put("qq", "456"), "ack").getString("qq"), "456"); Assert.assertEquals(f.count("receipt"), 1); }
        System.out.println("PASS qq 4 setting/receipt/commit failures roll back; lost commit ACK replays a single committed result");
        try (var f = new Fixture()) {
            ApiV3ProfileSettingsProcessor.registerRoutes(f.endpointQq, f.auth, f.writes); Dispatcher.mapping();
            Assert.assertEquals(f.requestQq("GET", null, null, null).attr("test.status"), 401); Assert.assertEquals(f.requestQq("GET", "scope", null, null).attr("test.status"), 403);
            final var read = f.requestQq("GET", "owner", null, null); Assert.assertEquals(read.attr("test.status"), 200); Assert.assertEquals(read.getResponse().getHeader("Cache-Control"), "no-store");
            Assert.assertEquals(f.requestQq("PUT", "owner", null, "{\"qq\":\"456\"}").attr("test.status"), 422);
            Assert.assertEquals(f.requestQq("PUT", "owner", "http", "{\"qq\":\"000456\"}").attr("test.status"), 200);
            Assert.assertEquals(f.requestQq("PUT", "owner", "unknown", "{\"qq\":\"1\",\"userId\":\"2\"}").attr("test.status"), 422);
        }
        for (String path : List.of("/api/v3/me/settings/qq", "%2Fapi%2Fv3%2Fme%2Fsettings%2Fqq")) {
            final var event = org.apache.logging.log4j.core.impl.Log4jLogEvent.newBuilder().setLoggerName("org.b3log.latke.http.Request").setMessage(new org.apache.logging.log4j.message.SimpleMessage(path + " PRIVATE_QQ")).build();
            Assert.assertEquals(new ApiV3OAuthTransportLogFilter().filter(event), org.apache.logging.log4j.core.Filter.Result.DENY);
        }
        System.out.println("PASS qq 5 actual HTTP Bearer/scope/key/body gates, private no-store projection and encoded-path transport log protection");
    }
}
