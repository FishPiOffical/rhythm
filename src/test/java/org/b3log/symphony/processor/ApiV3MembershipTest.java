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

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.http.handler.RouteHandler;
import org.b3log.latke.http.handler.RouteResolution;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.b3log.symphony.service.ApiV3MembershipQueryService;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ApiV3MembershipDto;
import org.json.*;
import io.netty.handler.codec.http.*;

import java.sql.*;
import java.util.*;

/**
 * 在隔离 SQLite 上执行实际路由、控制器、权限范围中间件、DTO 和本人数据查询。
 */
public final class ApiV3MembershipTest {
    static final String ME = "/api/v3/me/membership", LEVELS = "/api/v3/membership/levels";

    static final class Fixture implements AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3MembershipQueryService query = new ApiV3MembershipQueryService();
        final ApiV3MembershipProcessor processor = new ApiV3MembershipProcessor();
        final ApiV3AuthMidware auth = new ApiV3AuthMidware();
        final List<String> sql = new ArrayList<>();
        String failTable = "";

        Fixture() throws Exception {
            exec("CREATE TABLE user(oId TEXT PRIMARY KEY,userStatus INTEGER,userPassword TEXT)");
            exec("CREATE TABLE membership(oId TEXT,userId TEXT,lvCode TEXT,state INTEGER,expiresAt,configJson TEXT,couponCode TEXT)");
            exec("CREATE TABLE membership_level(oId TEXT PRIMARY KEY,lvCode TEXT,lvName TEXT,price INTEGER,durationType TEXT,durationValue INTEGER,benefits TEXT,internalToken TEXT)");
            exec("INSERT INTO user VALUES('1',0,'PRIVATE-password'),('2',0,'PRIVATE-other')");
            inject(query, "users", new UserRepository() {
                @Override
                public String getName() {
                    return "user";
                }

                @Override
                public List<JSONObject> select(String text, Object... values) throws RepositoryException {
                    return read(text, values);
                }
            });
            inject(query, "memberships", new MembershipRepository() {
                @Override
                public String getName() {
                    return "membership";
                }

                @Override
                public List<JSONObject> select(String text, Object... values) throws RepositoryException {
                    return read(text, values);
                }
            });
            inject(query, "levels", new MembershipLevelRepository() {
                @Override
                public String getName() {
                    return "membership_level";
                }

                @Override
                public List<JSONObject> select(String text, Object... values) throws RepositoryException {
                    return read(text, values);
                }
            });
            inject(processor, "query", query);
            inject(auth, "apiV3TokenMgmtService", new ApiV3TokenMgmtService() {
                @Override
                public JSONObject authenticate(String token) {
                    if (!Set.of("profile", "content", "both", "other").contains(token)) return null;
                    return new JSONObject().put("user", new JSONObject().put("oId", token.equals("other") ? "2" : "1"))
                            .put("scope", token.equals("content") ? "content.read" : token.equals("profile") ? "profile.read" : "profile.read content.read");
                }
            });
            ApiV3MembershipProcessor.registerRoutes(processor, auth);
            Dispatcher.mapping();
        }

        void exec(String text, Object... values) throws Exception {
            try (var s = db.prepareStatement(text)) {
                for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
                s.executeUpdate();
            }
        }

        List<JSONObject> read(String text, Object... values) throws RepositoryException {
            sql.add(text);
            check(text.startsWith("SELECT ") && !text.contains("SELECT *") && !text.contains("userPassword") && !text.contains("couponCode") && !text.contains("internalToken"), "closed read projection");
            if (!failTable.isEmpty() && text.contains("`" + failTable + "`"))
                throw new RepositoryException("PRIVATE-internal DB failure");
            try (var s = db.prepareStatement(text)) {
                for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
                try (var r = s.executeQuery()) {
                    var result = new ArrayList<JSONObject>();
                    var meta = r.getMetaData();
                    while (r.next()) {
                        var row = new JSONObject();
                        for (int i = 1; i <= meta.getColumnCount(); i++)
                            row.put(meta.getColumnLabel(i), r.getObject(i));
                        result.add(row);
                    }
                    return result;
                }
            } catch (SQLException e) {
                throw new RepositoryException(e);
            }
        }

        void membership(String owner, int state, long expiry, String config) throws Exception {
            exec("INSERT INTO membership VALUES(?,?,?,?,?,?,?)", "100" + owner, owner, "VIP4", state, expiry, config, "PRIVATE-coupon");
        }

        JSONObject request(String uri, String token, Map<String, String> params) throws Exception {
            var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.GET, uri);
            var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            try {
                raw.headers().set("Cookie", "legacy-owner-2");
                if (token != null) raw.headers().set("Authorization", "Bearer " + token);
                var request = new Request(null, raw);
                request.setParams(new HashMap<>(params));
                var c = new RequestContext(request, new Response(null, response));
                c.attr("user", new JSONObject().put("oId", "2"));
                c.addHandler(new RouteHandler());
                c.addHandler(context -> {
                    var resolution = (RouteResolution) context.attr("MATCH_RESULT");
                    try {
                        resolution.getContextHandlerMeta().getInvokeHolder().invoke(processor, context);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                });
                c.handle();
                var out = new JSONObject().put("status", c.attr("test.status"));
                if (c.attr("test.data") != null) out.put("data", c.attr("test.data"));
                if (c.attr("test.code") != null) out.put("code", c.attr("test.code"));
                if (out.getInt("status") == 200) {
                    check("no-store".equals(response.headers().get("Cache-Control")), "no-store");
                    check("Authorization".equals(response.headers().get("Vary")), "vary authorization");
                    check(!out.toString().contains("PRIVATE"), "no sensitive raw fields");
                }
                return out;
            } finally {
                raw.release();
            }
        }

        JSONObject get(String uri, String token) throws Exception {
            return request(uri, token, Map.of());
        }

        public void close() throws Exception {
            db.close();
        }
    }

    static void inject(Object bean, String name, Object value) throws Exception {
        var field = bean.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(bean, value);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void equal(Object actual, Object expected) {
        check(Objects.equals(actual, expected), "expected=" + expected + " actual=" + actual);
    }

    static JSONObject data(JSONObject response) {
        equal(response.getInt("status"), 200);
        return response.getJSONObject("data");
    }

    public static void main(String[] args) throws Exception {
        var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        try (var f = new Fixture()) {
            for (String route : List.of(ME, LEVELS))
                for (String token : Arrays.asList(null, "invalid")) equal(f.get(route, token).getInt("status"), 401);
            equal(f.get(ME, "content").getInt("status"), 403);
            equal(f.get(LEVELS, "profile").getInt("status"), 403);
            check(f.sql.isEmpty(), "denied routes never query repositories");
            check(RouteHandler.doMatch(ME, "POST") == null && RouteHandler.doMatch(LEVELS, "PUT") == null, "no accidental write registration");
            System.out.println("PASS 1 actual route mapping/auth rejects missing/invalid Bearer and wrong scopes before SQL; no write route");

            var config = new JSONObject().put("bold", true).put("underline", false).put("color", "galaxy").put("checkinCard", 3)
                    .put("internalToken", "PRIVATE-token").put("userEmail", "PRIVATE-email").put("nested", new JSONObject().put("password", "PRIVATE"));
            f.membership("1", 1, 0, config.toString());
            f.membership("2", 1, 0, "{\"bold\":false}");
            var own = data(f.request(ME, "profile", Map.of("userId", "2", "apiKey", "legacy", "lvCode", "VIP1")));
            equal(own.keySet(), Set.of("active", "levelCode", "expiresAt", "config"));
            check(own.getBoolean("active"), "permanent active");
            equal(own.getJSONObject("config").keySet(), Set.of("bold", "underline", "color", "checkinCard"));
            check(own.getJSONObject("config").getBoolean("bold"), "Bearer owner selected instead of Cookie/query");
            check(!data(f.get(ME, "other")).getJSONObject("config").getBoolean("bold"), "other Bearer selects its own record");
            f.exec("UPDATE membership SET configJson=? WHERE userId='1'", "{\"color\":\"#123456\"}");
            equal(data(f.get(ME, "profile")).getJSONObject("config").getString("color"), "#123456");
            System.out.println("PASS 2 actual controller/owner SQL ignores caller userId and Cookie/apiKey, re-reads current config, whitelists nested response and returns private headers");

            f.exec("UPDATE membership SET expiresAt=? WHERE userId='1'", System.currentTimeMillis() - 1);
            check(!data(f.get(ME, "profile")).getBoolean("active"), "expired inactive");
            f.exec("UPDATE membership SET expiresAt=0,state=0 WHERE userId='1'");
            check(!data(f.get(ME, "profile")).getBoolean("active"), "disabled inactive");
            f.exec("DELETE FROM membership WHERE userId='1'");
            check(!data(f.get(ME, "profile")).getBoolean("active"), "missing inactive");
            f.exec("UPDATE user SET userStatus=4 WHERE oId='1'");
            equal(f.get(ME, "profile").getInt("status"), 404);

            // 恢复有效账号，分别注入重复会员记录和损坏的有效期。
            f.exec("UPDATE user SET userStatus=0 WHERE oId='1'");
            f.membership("1", 1, 0, "{}");
            f.membership("1", 1, 0, "{}");
            equal(f.get(ME, "profile").getInt("status"), 503);
            f.exec("DELETE FROM membership WHERE userId='1'");
            f.membership("1", 1, 0, "{}");
            f.exec("UPDATE membership SET expiresAt='broken' WHERE userId='1'");
            equal(f.get(ME, "profile").getInt("status"), 503);
            f.exec("UPDATE membership SET expiresAt=0 WHERE userId='1'");
            System.out.println("PASS 3 expiry/permanence/disabled/missing membership, current deactivated owner, duplicate membership and damaged expiry fail safely");

            for (String raw : List.of("broken", "[]", "{\"bold\":true,\"bold\":false}", "x".repeat(102401), "{\"x\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}")) {
                f.exec("UPDATE membership SET configJson=? WHERE userId='1'", raw);
                check(data(f.get(ME, "profile")).getJSONObject("config").isEmpty(), "malformed optional config omitted");
            }
            equal(ApiV3MembershipDto.configuration("{\"bold\":\"true\",\"color\":\"url(https://PRIVATE)\",\"checkinCard\":-1,\"metal\":{\"token\":\"PRIVATE\"}}").length(), 0);
            equal(ApiV3MembershipDto.configuration("{\"checkinCard\":1.5,\"jointVip\":[\"PRIVATE\"]}").length(), 0);
            System.out.println("PASS 4 malformed/duplicate/oversized/deep JSON, wrong types, nested values and unsafe colors never expose raw configuration");

            for (int i = 1; i <= 53; i++)
                f.exec("INSERT INTO membership_level VALUES(?,?,?,?,?,?,?,?)", String.format("%03d", i), "VIP1", "<b>会员</b>", 100, "月卡", 30, config.toString(), "PRIVATE-ops");
            var first = data(f.request(LEVELS, "content", Map.of("size", "50")));
            equal(first.getJSONArray("list").length(), 50);
            equal(first.getJSONObject("pagination").getLong("total"), 53L);
            var level = first.getJSONArray("list").getJSONObject(0);
            equal(level.keySet(), Set.of("id", "code", "name", "price", "durationType", "durationValue", "benefits"));
            equal(level.getString("name"), "会员");
            equal(level.getString("id"), "001");
            var second = data(f.request(LEVELS, "content", Map.of("p", "2", "size", "50")));
            equal(second.getJSONArray("list").length(), 3);
            equal(second.getJSONArray("list").getJSONObject(0).getString("id"), "051");
            equal(data(f.request(LEVELS, "content", Map.of("p", "3", "size", "50"))).getJSONArray("list").length(), 0);
            f.exec("UPDATE membership_level SET benefits='invalid' WHERE oId='001'");
            check(data(f.get(LEVELS, "content")).getJSONArray("list").getJSONObject(0).getJSONObject("benefits").isEmpty(), "bad optional benefits omitted");
            System.out.println("PASS 5 actual bounded level SQL sorts/paginates by price/id, returns precise counts and strips ops fields/HTML and malformed benefits");

            for (var params : List.of(Map.of("size", "51"), Map.of("p", "0"), Map.of("p", "2147483647"), Map.of("p", "1 OR 1=1"))) {
                int before = f.sql.size();
                equal(f.request(LEVELS, "content", params).getInt("status"), 422);
                equal(f.sql.size(), before);
            }
            for (String table : List.of("user", "membership", "membership_level")) {
                f.failTable = table;
                var response = f.get(table.equals("membership_level") ? LEVELS : ME, "both");
                equal(response.getInt("status"), 503);
                equal(response.getString("code"), "temporarily_unavailable");
                check(!response.toString().contains("PRIVATE"), "DB error sanitized");
            }
            f.failTable = "";
            f.exec("DELETE FROM membership_level");
            check(data(f.get(LEVELS, "content")).getJSONArray("list").isEmpty(), "empty catalog");
            System.out.println("PASS 6 invalid pagination rejects before DB; each repository failure is controlled 503 and never an inactive/empty success");

            f.exec("INSERT INTO membership_level VALUES('001','VIP1','会员',0,'月卡',1,'{}','PRIVATE-ops')");
            equal(data(f.get(LEVELS, "content")).getJSONArray("list").getJSONObject(0).getLong("price"), 0L);
            f.exec("UPDATE membership_level SET price=2147483647,durationValue=2147483647");
            equal(data(f.get(LEVELS, "content")).getJSONArray("list").getJSONObject(0).getLong("durationValue"), 2147483647L);
            for (Object bad : List.of(-1, 1.5, "broken", "9223372036854775808", 2147483648L)) {
                f.exec("UPDATE membership_level SET price=?", bad);
                equal(f.get(LEVELS, "content").getInt("status"), 503);
            }
            f.exec("UPDATE membership_level SET price=100");
            for (Object bad : List.of(0, -1, 1.5, "broken", 2147483648L)) {
                f.exec("UPDATE membership_level SET durationValue=?", bad);
                equal(f.get(LEVELS, "content").getInt("status"), 503);
            }
            for (Object bad : List.of(-1, 1.5, "broken", "9223372036854775808")) {
                f.exec("UPDATE membership SET expiresAt=? WHERE userId='1'", bad);
                equal(f.get(ME, "profile").getInt("status"), 503);
            }
            f.exec("UPDATE membership SET expiresAt=? WHERE userId='1'", Long.MAX_VALUE);
            equal(data(f.get(ME, "profile")).getLong("expiresAt"), Long.MAX_VALUE);
            System.out.println("PASS 7 price/duration integer bounds, free catalog plans, positive durations and nonnegative long expiry reject invalid/overflow/fractional storage");
        }
        System.out.println("Membership isolated tests passed: 7 groups");
    }
}
