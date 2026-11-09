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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.ApiV3ChatAppearanceProcessor;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;

import java.util.*;

import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/**
 * 覆盖实际 SQL、本人凭据校验、幂等与 DTO，行锁语法由隔离 SQLite 夹具适配。
 */
public class ApiV3ChatAppearanceTest {
    static class Fixture extends ApiV3IdentityApplicationTest.Fixture {
        final ApiV3ChatAppearanceService appearance = new ApiV3ChatAppearanceService() {
            protected long now() {
                return NOW;
            }
        };
        final ApiV3ChatAppearanceProcessor endpointAppearance = new ApiV3ChatAppearanceProcessor();

        Fixture() throws Exception {
            sql(db, "CREATE TABLE chat_appearance(oId TEXT PRIMARY KEY,bubbleId TEXT,frameId TEXT,version INT,updatedAt BIGINT)");
            sql(db, "CREATE TABLE member_data(userId TEXT,state INT,expiresAt BIGINT)");
            sql(db, "CREATE TABLE chat_data(oId TEXT PRIMARY KEY,content TEXT)");
            final var choice = new UserChatAppearanceRepository() {
                public String getName() {
                    return "chat_appearance";
                }

                public boolean hasTransactionBegun() {
                    return active();
                }

                public List<JSONObject> select(String q, Object... a) throws RepositoryException {
                    return selectSQL(q, a);
                }

                public String add(JSONObject row) throws RepositoryException {
                    Assert.assertTrue(active());
                    sql(writer, "INSERT INTO chat_appearance VALUES(?,?,?,?,?)", row.getString("oId"),
                            row.getString("bubbleId"), row.getString("frameId"), row.getInt("version"), row.getLong("updatedAt"));
                    return row.getString("oId");
                }

                public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                    Assert.assertTrue(active());
                    sql(writer, "UPDATE chat_appearance SET bubbleId=?,frameId=?,version=?,updatedAt=? WHERE oId=?",
                            row.getString("bubbleId"), row.getString("frameId"), row.getInt("version"), row.getLong("updatedAt"), id);
                }
            };
            final var users = new UserRepository() {
                public String getName() {
                    return "u";
                }

                public boolean hasTransactionBegun() {
                    return active();
                }

                public JSONObject getForUpdate(String id) throws RepositoryException {
                    final var found = selectSQL("SELECT * FROM u WHERE oId=? FOR UPDATE", id);
                    return found.isEmpty() ? null : found.getFirst();
                }
            };
            final var members = new MembershipRepository() {
                public String getName() {
                    return "member_data";
                }

                public List<JSONObject> select(String q, Object... a) throws RepositoryException {
                    return selectSQL(q, a);
                }
            };
            inject(appearance, "users", users);
            inject(appearance, "appearances", choice);
            inject(appearance, "memberships", members);
            inject(appearance, "requests", ApiV3QqSettingsTest.field(service, ApiV3IdentityApplicationService.class, "requests"));
            inject(appearance, "proofs", proof);
            inject(endpointAppearance, "appearance", appearance);
        }

        JSONObject snapshot(String message) throws Exception {
            // 发送时将样式写入消息快照，提交后从存储重新读取并执行 DTO 投影。
            final var tx = begin();
            try {
                final var envelope = new JSONObject().put("userOId", "1").put("userName", "owner")
                        .put("content", "text").put("time", NOW)
                        .put("appearance", appearance.snapshotInCurrentTransaction("1"));
                sql(writer, "INSERT INTO chat_data VALUES(?,?)", message, envelope.toString());
                tx.commit();
                return projected(message);
            } finally {
                if (tx.isActive()) tx.rollback();
            }
        }

        JSONObject projected(String id) throws Exception {
            return ApiV3ChatMessageDto.from(rows(db, "SELECT * FROM chat_data WHERE oId=?", id).getFirst(), v -> "<p>" + v + "</p>");
        }

        RequestContext requestAppearance(String method, String token, String key, String body) throws Exception {
            final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.valueOf(method), "/api/v3/me/chat-appearance");
            try {
                if (token != null) raw.headers().set("Authorization", "Bearer " + token);
                if (key != null) raw.headers().set("Idempotency-Key", key);
                raw.headers().set("Content-Type", "application/json");
                raw.headers().set("Cookie", "legacy");
                raw.headers().set("X-Real-IP", "127.0.0.1");
                final byte[] bytes = (body == null ? "" : body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                raw.content().clear().writeBytes(bytes);
                final var req = new Request(null, raw);
                req.setBytes(bytes);
                req.setParams(new HashMap<>(Map.of("apiKey", "legacy")));
                final var c = new RequestContext(req, new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());
                c.addHandler(x -> {
                    if (method.equals("GET")) endpointAppearance.read(x);
                    else endpointAppearance.save(x);
                });
                c.handle();
                return c;
            } finally {
                raw.release();
            }
        }
    }

    static JSONObject input(String bubble, String frame) {
        return new JSONObject().put("bubbleId", bubble).put("frameId", frame);
    }

    public static void main(String[] args) throws Exception {
        final var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        for (JSONObject invalid : List.of(input("../../evil", "none"), input("classic", "url(https://bad)"), input("classic", "none").put("userId", "2"), input("classic", "none").put("version", 1)))
            error(422, () -> ApiV3ChatAppearance.input(invalid));
        try (var f = new Fixture()) {
            Assert.assertTrue(f.appearance.read(identity()).similar(ApiV3ChatAppearance.defaults()));
            f.appearance.update(identity(), input("paipai-orange", "paipai-orange"), "orange");
            final JSONObject one = f.snapshot("10"), two = f.snapshot("11");
            f.appearance.update(identity(), input("fishpond-blue", "paipai-orange"), "blue");
            final JSONObject three = f.snapshot("12"), four = f.snapshot("13");
            for (JSONObject message : List.of(one, two, f.projected("10"), f.projected("11")))
                Assert.assertEquals(message.getJSONObject("appearance").getString("bubbleId"), "paipai-orange");
            for (JSONObject message : List.of(three, four))
                Assert.assertEquals(message.getJSONObject("appearance").getString("bubbleId"), "fishpond-blue");
            Assert.assertEquals(three.getJSONObject("appearance").getString("frameId"), "paipai-orange");
            f.appearance.update(identity(), input("paipai-orange", "paipai-orange"), "orange");
            Assert.assertEquals(f.appearance.read(identity()).getString("bubbleId"), "fishpond-blue");
            error(409, () -> f.appearance.update(identity(), input("classic", "none"), "orange"));
            Assert.assertEquals(rows(f.db, "SELECT COUNT(*) n FROM chat_appearance WHERE oId='2'").getFirst().getInt("n"), 0);
        }
        System.out.println("PASS appearance 1 exact catalog, independent sender snapshot for four messages, later choice/replay never rewrites history");
        try (var f = new Fixture()) {
            error(403, () -> f.appearance.update(identity(), input("candy", "gold"), "vip"));
            Assert.assertEquals(f.count("receipt"), 0);
            sql(f.db, "INSERT INTO member_data VALUES('1',1,?)", NOW + 10000);
            f.appearance.update(identity(), input("candy", "gold"), "vip");
            final JSONObject old = f.snapshot("20");
            sql(f.db, "UPDATE member_data SET expiresAt=1");
            Assert.assertEquals(f.appearance.read(identity()).getString("bubbleId"), "classic");
            Assert.assertEquals(f.snapshot("21").getJSONObject("appearance").getString("frameId"), "none");
            Assert.assertTrue(old.getJSONObject("appearance").similar(f.projected("20").getJSONObject("appearance")));
            Assert.assertEquals(f.projected("20").getJSONObject("appearance").getString("frameId"), "gold");
        }
        System.out.println("PASS appearance 2 current VIP checked on selection/send; expired entitlement never strips a valid historical snapshot");
        try (var f = new Fixture()) {
            f.fault = "receipt";
            error(503, () -> f.appearance.update(identity(), input("paipai-orange", "none"), "rollback"));
            Assert.assertEquals(f.count("receipt"), 0);
            Assert.assertEquals(rows(f.db, "SELECT COUNT(*) n FROM chat_appearance").getFirst().getInt("n"), 0);
            f.fault = "";
            f.appearance.update(identity(), input("paipai-orange", "none"), "rollback");
            Assert.assertEquals(f.count("receipt"), 1);
            sql(f.db, "UPDATE tokens SET state=1 WHERE oId='100'");
            error(403, () -> f.appearance.read(identity()));
            error(403, () -> f.appearance.update(identity(), input("paipai-orange", "none"), "rollback"));
        }
        System.out.println("PASS appearance 3 rollback keeps choice/receipt atomic and revoked identity cannot recover a prior command");
        try (var f = new Fixture()) {
            ApiV3ChatAppearanceProcessor.registerRoutes(f.endpointAppearance, f.auth, f.writes);
            Dispatcher.mapping();
            Assert.assertEquals(f.requestAppearance("GET", null, null, null).attr("test.status"), 401);
            Assert.assertEquals(f.requestAppearance("PUT", "scope", "scope", input("classic", "none").toString()).attr("test.status"), 403);
            Assert.assertEquals(f.requestAppearance("PUT", "owner", "http", input("fishpond-blue", "none").toString()).attr("test.status"), 200);
            Assert.assertEquals(f.appearance.read(identity()).getString("bubbleId"), "fishpond-blue");
        }
        System.out.println("PASS appearance 4 Bearer/scope/write gate and real HTTP normalizer; no legacy Cookie/apiKey authorization");
        final JSONObject legacy = new JSONObject().put("oId", "30").put("content", new JSONObject().put("userOId", "1").put("content", "legacy").toString());
        Assert.assertTrue(ApiV3ChatMessageDto.from(legacy, v -> v).getJSONObject("appearance").similar(ApiV3ChatAppearance.defaults()));
        Assert.assertTrue(ApiV3ChatAppearance.publicSnapshot(new JSONObject().put("version", 99).put("bubbleId", "candy")).similar(ApiV3ChatAppearance.defaults()));
        System.out.println("PASS appearance 5 legacy/unknown version defaults are independent of current author and viewer settings");
    }
}
