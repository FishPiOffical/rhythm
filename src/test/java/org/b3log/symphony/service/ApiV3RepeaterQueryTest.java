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
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;

import java.sql.*;
import java.util.*;

/**
 * 执行实际查询 SQL、控制器、鉴权和 Dispatcher，仅适配数据库连接。
 */
public class ApiV3RepeaterQueryTest {
    static void inject(Object target, String key, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(key);
        f.setAccessible(true);
        f.set(target, value);
    }

    static class Fixture implements AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3RepeaterQueryService query = new ApiV3RepeaterQueryService();
        final ApiV3RepeaterProcessor controller = new ApiV3RepeaterProcessor();
        final ApiV3AuthMidware auth = new ApiV3AuthMidware();
        final AnonymousViewCheckMidware anonymous = new AnonymousViewCheckMidware();
        int reads, failAt = -1, disableAt = -1, removeAt = -1;

        Fixture() throws Exception {
            sql("CREATE TABLE u(oId TEXT,userStatus INT,userName TEXT,userPassword TEXT)");
            sql("CREATE TABLE c(oId TEXT,repeaterContentType TEXT,repeaterContent TEXT,repeaterContentLikeCount INT,repeaterContentCreatedTime INT,repeaterContentAuthorId TEXT,repeaterContentStatus INT,repeaterContentSource TEXT)");
            sql("CREATE TABLE k(repeaterLikeContentId TEXT,repeaterLikeUserId TEXT)");
            sql("INSERT INTO u VALUES('1',0,'Alice','PRIVATE'),('2',0,'Bob','PRIVATE'),('3',4,'Disabled','PRIVATE')");
            for (int i = 1; i <= 55; i++)
                sql("INSERT INTO c VALUES(?,?,?,?,?,?,0,'PRIVATE_SOURCE')", String.valueOf(100 + i), i % 2 == 0 ? "joke" : "fish", "text " + i, 56 - i, 1000 + i, i == 1 ? "3" : i == 2 ? "" : "2");
            sql("INSERT INTO c VALUES('999','joke','REMOVED',999,999,'2',1,'PRIVATE')");
            sql("INSERT INTO k VALUES('101','1'),('101','1'),('102','2')");
            inject(query, "users", new UserRepository() {
                public String getName() {
                    return "u";
                }

                public List<JSONObject> select(String s, Object... a) throws RepositoryException {
                    return selectSQL(s, a);
                }
            });
            inject(query, "contents", new RepeaterContentRepository() {
                public String getName() {
                    return "c";
                }

                public List<JSONObject> select(String s, Object... a) throws RepositoryException {
                    return selectSQL(s, a);
                }
            });
            inject(query, "likes", new RepeaterLikeRepository() {
                public String getName() {
                    return "k";
                }
            });
            inject(controller, "query", query);
            inject(anonymous, "optionQueryService", new OptionQueryService());
            AnonymousCaptchaShield.setEnabled(false);
            resetShield();
            inject(auth, "apiV3TokenMgmtService", new ApiV3TokenMgmtService() {
                public JSONObject authenticate(String t) {
                    return Set.of("valid", "wrong", "disabled").contains(t) ? new JSONObject().put("scope", t.equals("wrong") ? "profile.read" : "content.read").put("user", new JSONObject().put("oId", t.equals("disabled") ? "3" : "1")) : null;
                }
            });
        }

        void sql(String q, Object... a) throws Exception {
            try (var p = db.prepareStatement(q)) {
                for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
                p.executeUpdate();
            }
        }

        List<JSONObject> selectSQL(String q, Object... a) throws RepositoryException {
            reads++;
            Assert.assertTrue(q.startsWith("SELECT "));
            Assert.assertFalse(q.contains("SELECT *"));
            Assert.assertFalse(q.contains("userPassword"));
            if (reads == failAt) throw new RepositoryException("PRIVATE_FAILURE");
            try {
                // 在指定轮次查询前改变账号或内容状态，模拟多次读取之间的变化。
                if (reads == disableAt) sql("UPDATE u SET userStatus=4 WHERE oId='1'");
                if (reads == removeAt) sql("UPDATE c SET repeaterContentStatus=1 WHERE oId='101'");
                try (var p = db.prepareStatement(q)) {
                    for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
                    try (var r = p.executeQuery()) {
                        var list = new ArrayList<JSONObject>();
                        while (r.next()) {
                            var row = new JSONObject();
                            for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
                                row.put(r.getMetaData().getColumnLabel(i), r.getObject(i));
                            list.add(row);
                        }
                        return list;
                    }
                }
            } catch (Exception e) {
                throw new RepositoryException(e);
            }
        }

        RequestContext http(String token, String route, Map<String, String> params) throws Exception {
            var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.GET, "/api/v3/repeater/" + route + "?apiKey=legacy");
            try {
                raw.headers().set("Cookie", "legacy");
                if (token != null) raw.headers().set("Authorization", "Bearer " + token);
                var req = new Request(null, raw) {
                    public String getRemoteAddr() {
                        return "198.51.100.7";
                    }
                };
                var all = new HashMap<>(params);
                all.put("apiKey", "legacy");
                req.setParams(all);
                final int[] httpStatus = {200};
                var c = new RequestContext(req, new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)) {
                    public void setStatus(int value) {
                        httpStatus[0] = value;
                        super.setStatus(value);
                    }

                    public void sendError(int value) {
                        setStatus(value);
                    }

                    public void sendRedirect(String uri) {
                        setHeader("Location", uri);
                        setStatus(302);
                    }
                });
                c.attr("user", new JSONObject().put("oId", "1"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());
                c.addHandler(route.equals("next") ? controller::next : controller::items);
                c.handle();
                if (c.attr("test.status") == null) c.attr("test.status", httpStatus[0]);
                return c;
            } finally {
                raw.release();
            }
        }

        void resetShield() throws Exception {
            for (String key : List.of("ipVisitCountCache", "ipFirstVisitTimeCache", "ipLastCaptchaPassTimeCache", "ipBlacklistCache")) {
                var field = AnonymousCaptchaShield.class.getDeclaredField(key);
                field.setAccessible(true);
                ((com.github.benmanes.caffeine.cache.Cache<?, ?>) field.get(null)).invalidateAll();
            }
            var field = AnonymousCaptchaShield.class.getDeclaredField("whiteList");
            field.setAccessible(true);
            ((Set<?>) field.get(null)).clear();
            AnonymousCaptchaShield.setFirstVisitCaptchaEnabled(true);
        }

        Integer shieldCount() throws Exception {
            var field = AnonymousCaptchaShield.class.getDeclaredField("ipVisitCountCache");
            field.setAccessible(true);
            return (Integer) ((com.github.benmanes.caffeine.cache.Cache<?, ?>) field.get(null)).asMap().get("198.51.100.7");
        }

        public void close() throws Exception {
            db.close();
            resetShield();
            AnonymousCaptchaShield.setEnabled(true);
        }
    }

    interface Action {
        void run() throws Exception;
    }

    static void error(int status, Action a) throws Exception {
        try {
            a.run();
            Assert.fail("Expected " + status);
        } catch (ApiV3Exception e) {
            Assert.assertEquals(e.status, status);
            Assert.assertFalse(e.getMessage().contains("PRIVATE"));
        }
    }

    static void safe(JSONObject d) {
        for (String k : List.of("PRIVATE", "repeaterContentSource", "userPassword", "repeaterContentAuthorId", "userStatus"))
            Assert.assertFalse(d.toString().contains(k));
    }

    public static void main(String[] a) throws Exception {
        var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        try (var f = new Fixture()) {
            var d = f.query.items("1", "", 12);
            safe(d);
            var rows = d.getJSONArray("items");
            Assert.assertEquals(rows.length(), 12);
            Assert.assertEquals(rows.getJSONObject(0).getString("id"), "101");
            Assert.assertTrue(rows.getJSONObject(0).getBoolean("liked"));
            Assert.assertFalse(rows.getJSONObject(1).getBoolean("liked"));
            Assert.assertTrue(rows.getJSONObject(0).isNull("author"));
            Assert.assertTrue(rows.getJSONObject(1).isNull("author"));
            Assert.assertEquals(rows.getJSONObject(2).getJSONObject("author").getString("name"), "Bob");
            Assert.assertEquals(rows.getJSONObject(2).keySet(), Set.of("id", "type", "content", "likeCount", "createdTime", "liked", "author"));
            Assert.assertFalse(f.query.items("", "", 12).getJSONArray("items").getJSONObject(0).getBoolean("liked"));
            var jokes = f.query.items("", "joke", 50).getJSONArray("items");
            Assert.assertEquals(jokes.length(), 27);
            for (int i = 0; i < jokes.length(); i++)
                Assert.assertEquals(jokes.getJSONObject(i).getString("type"), "joke");
            f.sql("UPDATE c SET repeaterContent='' WHERE oId='101'");
            Assert.assertEquals(f.query.items("", "", 1).getJSONArray("items").getJSONObject(0).getString("content"), "");
        }
        System.out.println("PASS 1 actual SQL pool order/type/size, valid content with removed authors and seed author null, owner-bound duplicate-safe likes, strict whitelist and legacy short content");
        try (var f = new Fixture()) {
            for (int i = 0; i < 200; i++) {
                var item = f.query.next("", "", "101").getJSONObject("item");
                int id = Integer.parseInt(item.getString("id"));
                Assert.assertTrue(id >= 102 && id <= 155);
            }
            f.sql("DELETE FROM c WHERE oId<>'101'");
            Assert.assertTrue(f.query.next("", "", "101").isNull("item"));
            Assert.assertEquals(f.query.next("", "", "").getJSONObject("item").getString("id"), "101");
            f.sql("DELETE FROM c");
            Assert.assertTrue(f.query.next("", "", "").isNull("item"));
        }
        System.out.println("PASS 2 next uses all valid contents before exclusion; excluded only item and empty pool return explicit null");
        try (var f = new Fixture()) {
            f.sql("UPDATE c SET repeaterContentLikeCount=100,repeaterContentCreatedTime=5 WHERE oId IN ('101','102')");
            Assert.assertEquals(f.query.items("", "", 2).getJSONArray("items").getJSONObject(0).getString("id"), "102");
            for (String t : List.of("unknown", "JOKE", "joke OR 1=1", " ")) error(422, () -> f.query.items("", t, 12));
            for (int n : new int[]{0, 51, Integer.MAX_VALUE}) error(422, () -> f.query.items("", "", n));
            error(422, () -> f.query.next("", "", "1 OR 1=1"));
            error(404, () -> f.query.items("3", "", 12));
        }
        System.out.println("PASS 3 deterministic tie ordering and strict type/size/exclude/active actor validation");
        for (String field : List.of("repeaterContent", "repeaterContentType"))
            try (var f = new Fixture()) {
                f.sql("UPDATE c SET " + field + "=? WHERE oId='101'", "x".repeat(501));
                error(503, () -> f.query.items("", "", 12));
            }
        try (var f = new Fixture()) {
            f.sql("UPDATE c SET repeaterContentLikeCount=-1 WHERE oId='101'");
            // 负数记录排在末尾，只保留该行以确保查询读到它。
            f.sql("DELETE FROM c WHERE oId<>'101'");
            error(503, () -> f.query.items("", "", 12));
        }
        try (var f = new Fixture()) {
            f.sql("INSERT INTO u SELECT * FROM u WHERE oId='2'");
            error(503, () -> f.query.items("", "", 12));
        }
        JSONObject numeric = new JSONObject().put("id", "1").put("type", "joke").put("content", "").put("authorId", "")
                .put("likeCount", new java.math.BigDecimal("1.000")).put("createdTime", new java.math.BigInteger("10")).put("liked", 0);
        Assert.assertEquals(ApiV3RepeaterDto.item(numeric).getLong("likeCount"), 1L);
        error(503, () -> ApiV3RepeaterDto.item(new JSONObject(numeric.toString()).put("likeCount", new java.math.BigDecimal("1.5"))));
        error(503, () -> ApiV3RepeaterDto.item(new JSONObject(numeric.toString()).put("likeCount", new java.math.BigInteger("9223372036854775808"))));
        System.out.println("PASS 4 oversized/corrupt stored projections and duplicate joins fail closed without truncation or leaking raw persistence");
        for (int stage = 1; stage <= 5; stage++)
            try (var f = new Fixture()) {
                f.failAt = stage;
                error(503, () -> f.query.items("1", "", 12));
            }
        try (var f = new Fixture()) {
            f.removeAt = 2;
            error(503, () -> f.query.items("", "", 12));
        }
        try (var f = new Fixture()) {
            f.disableAt = 5;
            error(404, () -> f.query.items("1", "", 12));
        }
        System.out.println("PASS 5 sanitized failures, current pool and actor final checks, no writes");
        try (var f = new Fixture()) {
            ApiV3RepeaterProcessor.registerRoutes(f.controller, f.auth, f.anonymous);
            Dispatcher.mapping();
            var anon = f.http(null, "items", Map.of());
            Assert.assertEquals(anon.attr("test.status"), 200);
            Assert.assertEquals(((JSONObject) anon.attr("test.data")).getJSONArray("items").length(), 12);
            Assert.assertFalse(((JSONObject) anon.attr("test.data")).getJSONArray("items").getJSONObject(0).getBoolean("liked"));
            Assert.assertEquals(anon.getResponse().getHeader("Cache-Control"), "no-store");
            var valid = f.http("valid", "items", Map.of());
            Assert.assertTrue(((JSONObject) valid.attr("test.data")).getJSONArray("items").getJSONObject(0).getBoolean("liked"));
            Assert.assertEquals(f.http("bad", "items", Map.of()).attr("test.status"), 401);
            Assert.assertEquals(f.http("wrong", "items", Map.of()).attr("test.status"), 403);
            Assert.assertEquals(f.http("disabled", "items", Map.of()).attr("test.status"), 404);
            Assert.assertEquals(f.http(null, "items", Map.of("type", "other")).attr("test.status"), 422);
            Assert.assertEquals(f.http(null, "next", Map.of("excludeId", "x")).attr("test.status"), 422);
            Assert.assertEquals(f.http(null, "items", Map.of("size", "51")).attr("test.status"), 422);
            Assert.assertEquals(f.http(null, "next", Map.of()).attr("test.status"), 200);
            f.resetShield();
            AnonymousCaptchaShield.setEnabled(true);
            int before = f.reads;
            var blocked = f.http(null, "items", Map.of());
            Assert.assertEquals(blocked.attr("test.status"), 401);
            Assert.assertNull(blocked.getResponse().getHeader("Location"));
            Assert.assertEquals(f.reads, before);
            Assert.assertEquals(f.shieldCount(), Integer.valueOf(1));
            Assert.assertEquals(f.http("bad", "items", Map.of()).attr("test.status"), 401);
            Assert.assertEquals(f.shieldCount(), Integer.valueOf(1));
            Assert.assertEquals(f.http("valid", "items", Map.of()).attr("test.status"), 200);
            f.resetShield();
            AnonymousCaptchaShield.setFirstVisitCaptchaEnabled(false);
            for (int visit = 1; visit <= 4; visit++)
                Assert.assertEquals(f.http(null, "items", Map.of()).attr("test.status"), 200);
            Assert.assertEquals(f.http(null, "items", Map.of()).attr("test.status"), 401);
        }
        System.out.println("PASS 6 actual Dispatcher/controller optional auth and anonymous middleware wiring, Cookie/apiKey never personalize, valid/wrong/expired Bearer and input failures");
        try (var f = new Fixture()) {
            f.sql("ALTER TABLE c ADD COLUMN repeaterContentUpdatedTime INT DEFAULT 0");
            final RepeaterQueryService legacy = new RepeaterQueryService();
            inject(legacy, "repeaterContentRepository", new RepeaterContentRepository() {
                public String getName() { return "c"; }
                public List<JSONObject> select(String sql, Object... args) throws RepositoryException { return f.selectSQL(sql, args); }
            });
            inject(legacy, "userRepository", new UserRepository() {
                public String getName() { return "u"; }
                public List<JSONObject> select(String sql, Object... args) throws RepositoryException { return f.selectSQL(sql, args); }
            });
            final JSONObject board = legacy.leaderboard();
            Assert.assertEquals(board.getJSONArray("authors").length(), 1);
            Assert.assertEquals(board.getJSONArray("authors").getJSONObject(0).getLong("likeCount"), 1431L);
            Assert.assertEquals(board.getJSONArray("items").getJSONObject(0).getString("authorName"), "");
            Assert.assertEquals(board.getJSONArray("items").length(), 20);
            f.sql("DELETE FROM c WHERE oId<>'155'");
            Assert.assertEquals(f.query.next("", "", "").getJSONObject("item").getString("id"), "155");
            final JSONObject item = legacy.next("fish", "", List.of());
            Assert.assertEquals(item.getString("oId"), "155");
            Assert.assertTrue(item.has("repeaterContentSource"));
            Assert.assertTrue(item.has("repeaterContentUpdatedTime"));
            Assert.assertEquals(item.getString("repeaterContentAuthorName"), "Bob");
            Assert.assertNull(legacy.next("fish", "", List.of("155")));
            f.sql("UPDATE c SET repeaterContentStatus=1");
            Assert.assertTrue(legacy.leaderboard().getJSONArray("items").isEmpty());
            Assert.assertTrue(legacy.leaderboard().getJSONArray("authors").isEmpty());
        }
        System.out.println("PASS 7 classic discovery full projection and exclusion, low-rank candidate, author totals, deleted exclusion and disabled author privacy");
        System.out.println("Repeater isolated tests passed: 7 groups");

    }
}
