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
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.ApiV3SecurityProcessor;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import sun.misc.Unsafe;

import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class ApiV3SecurityTest {
    static final String OLD = "a".repeat(32), NEW = "b".repeat(32), SECRET = "JBSWY3DPEHPK3PXP";
    static final long NOW = 1700000000000L;

    static void inject(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    static List<JSONObject> rows(Connection c, String q, Object... a) throws RepositoryException {
        try (var p = c.prepareStatement(q.replace(" FOR UPDATE", ""))) {
            for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
            try (var r = p.executeQuery()) {
                var result = new ArrayList<JSONObject>();
                while (r.next()) {
                    var row = new JSONObject();
                    for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
                        row.put(r.getMetaData().getColumnLabel(i), r.getObject(i));
                    result.add(row);
                }
                return result;
            }
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    static void sql(Connection c, String q, Object... a) throws RepositoryException {
        try (var p = c.prepareStatement(q)) {
            for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    static JSONObject identity() {
        return new JSONObject().put("tokenId", "100").put("user", new JSONObject().put("oId", "1"));
    }

    static JSONObject body() {
        return new JSONObject().put("currentPassword", OLD).put("newPassword", NEW);
    }

    static String totp() throws Exception {
        var m = MultiFactorAuthenticator.class.getDeclaredMethod("verifyCode", byte[].class, long.class);
        m.setAccessible(true);
        long value = ((Number) m.invoke(null, new org.apache.commons.codec.binary.Base32().decode(SECRET), NOW / 30000)).longValue();
        return String.format("%06d", value);
    }

    static class F implements AutoCloseable {
        Path file = Files.createTempFile("security-", ".sqlite");
        String url = "jdbc:sqlite:" + file;
        Connection db = DriverManager.getConnection(url), writer;
        JdbcTransaction tx;
        String fault = "";
        boolean allowed = true;
        int roots, cache;
        List<String> locks = new ArrayList<>();
        ApiV3SecurityProofService proof = new ApiV3SecurityProofService() {
            protected long now() {
                return NOW;
            }

            public void checkAttempts(JSONObject i, String ip) {
                if (!allowed) throw new ApiV3Exception(429, "too_many_requests", "busy");
            }
        };
        ApiV3SecuritySettingsService settings = new ApiV3SecuritySettingsService();
        AccountCredentialMgmtService credentials = new AccountCredentialMgmtService();
        ApiV3SecurityProcessor controller = new ApiV3SecurityProcessor();
        ApiV3AuthMidware auth = new ApiV3AuthMidware();
        ApiV3WriteMidware writes = new ApiV3WriteMidware();

        F() throws Exception {
            sql(db, "CREATE TABLE u(oId TEXT PRIMARY KEY,userStatus INT,userPassword TEXT,secret2fa TEXT)");
            sql(db, "INSERT INTO u VALUES('1',0,?,''),('2',0,?,'')", OLD, OLD);
            sql(db, "CREATE TABLE clients(clientId TEXT PRIMARY KEY,status INT,scope TEXT)");
            sql(db, "INSERT INTO clients VALUES('website_v3',0,'profile.read settings.write'),('third',0,'profile.read settings.write')");
            sql(db, "CREATE TABLE tokens(oId TEXT PRIMARY KEY,userId TEXT,clientId TEXT,tokenType TEXT,state INT,expiresAt INT,scope TEXT,updatedAt INT)");
            sql(db, "INSERT INTO tokens VALUES('100','1','website_v3','access',0,?,'profile.read settings.write',0),('101','1','third','refresh',0,?,'settings.write',0),('102','2','website_v3','access',0,?,'profile.read',0)", NOW + 100000, NOW + 100000, NOW + 100000);
            sql(db, "CREATE TABLE states(oId TEXT PRIMARY KEY,credentialEpoch INT,updatedAt INT)");
            sql(db, "CREATE TABLE openid(oId TEXT,userId TEXT,state INT,updatedAt INT)");
            sql(db, "INSERT INTO openid VALUES('1','1',0,0),('2','2',0,0)");
            sql(db, "CREATE TABLE requests(oId TEXT,userId TEXT,decision TEXT)");
            sql(db, "INSERT INTO requests VALUES('1','1','pending'),('2','1','approved'),('3','2','pending')");
            sql(db, "CREATE TABLE codes(oId TEXT,userId TEXT,consumedAt INT)");
            sql(db, "INSERT INTO codes VALUES('1','1',0),('2','1',1),('3','2',0)");
            var users = new UserRepository() {
                public String getName() {
                    return "u";
                }

                public boolean hasTransactionBegun() {
                    return active();
                }

                public List<JSONObject> select(String q, Object... a) throws RepositoryException {
                    return selectSQL(q, a);
                }

                public void evictSettingsCaches(String a) {
                    cache++;
                }
            };
            var clients = new ApiV3ClientRepository() {
                public String getName() {
                    return "clients";
                }

                public boolean hasTransactionBegun() {
                    return active();
                }

                public List<JSONObject> select(String q, Object... a) throws RepositoryException {
                    return selectSQL(q, a);
                }
            };
            var tokens = new ApiV3TokenRepository() {
                public String getName() {
                    return "tokens";
                }

                public boolean hasTransactionBegun() {
                    return active();
                }

                public Transaction beginTransaction() {
                    return begin();
                }

                public List<JSONObject> select(String q, Object... a) throws RepositoryException {
                    return selectSQL(q, a);
                }
            };
            var states = new AccountCredentialStateRepository() {
                public String getName() {
                    return "states";
                }

                public boolean hasTransactionBegun() {
                    return active();
                }

                public List<JSONObject> select(String q, Object... a) throws RepositoryException {
                    return selectSQL(q, a);
                }
            };
            inject(states, "users", users);
            var openid = new OpenIdRefreshTokenRepository() {
                public String getName() {
                    return "openid";
                }
            };
            var requests = new ApiV3AuthorizationRequestRepository() {
                public String getName() {
                    return "requests";
                }

                public boolean hasTransactionBegun() {
                    return active();
                }
            };
            var codes = new ApiV3AuthorizationCodeRepository() {
                public String getName() {
                    return "codes";
                }

                public boolean hasTransactionBegun() {
                    return active();
                }
            };
            for (var service : List.of(proof, credentials)) {
                inject(service, "users", users);
                inject(service, "tokens", tokens);
                inject(service, "states", states);
            }
            inject(proof, "clients", clients);
            inject(credentials, "openid", openid);
            inject(credentials, "requests", requests);
            inject(credentials, "codes", codes);
            inject(settings, "proof", proof);
            inject(settings, "credentials", credentials);
            inject(settings, "tokens", tokens);
            inject(settings, "users", users);
            inject(controller, "settings", settings);
            inject(auth, "apiV3TokenMgmtService", new ApiV3TokenMgmtService() {
                public JSONObject authenticate(String token) {
                    if (!Set.of("owner", "wrong").contains(token)) return null;
                    try {
                        if (rows(active() ? writer : db, "SELECT state FROM tokens WHERE oId='100'").getFirst().getInt("state") != 0)
                            return null;
                    } catch (Exception e) {
                        return null;
                    }
                    return identity().put("scope", token.equals("wrong") ? "content.read" : "profile.read settings.write");
                }
            });
        }

        boolean active() {
            return tx != null && tx.isActive();
        }

        List<JSONObject> selectSQL(String q, Object... a) throws RepositoryException {
            if (q.endsWith(" FOR UPDATE")) {
                Assert.assertTrue(active());
                locks.add(q.contains("ORDER BY clientId") ? "namespace" : q.contains("`states`") ? "epoch" : q.contains("`u`") ? "user" : q.contains("`tokens`") ? "token" : "client");
            }
            if (fault.equals("read")) throw new RepositoryException("PRIVATE");
            return rows(active() ? writer : db, q, a);
        }

        Transaction begin() {
            try {
                roots++;
                var real = DriverManager.getConnection(url);
                real.setAutoCommit(false);
                writer = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, (p, m, a) -> {
                    try {
                        if (m.getName().equals("commit")) {
                            if (fault.equals("commit")) throw new SQLException("PRIVATE");
                            real.commit();
                            if (fault.equals("ack")) throw new SQLException("PRIVATE");
                            return null;
                        }
                        if (m.getName().equals("prepareStatement")) {
                            String q = (String) a[0];
                            var ps = real.prepareStatement(q.replace(" FOR UPDATE", ""));
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PreparedStatement.class}, (x, n, b) -> {
                                try {
                                    var out = n.invoke(ps, b);
                                    if (n.getName().equals("executeUpdate") && ((fault.equals("password") && q.startsWith("UPDATE `u`")) || (fault.equals("revoke") && q.startsWith("UPDATE `tokens`"))))
                                        throw new SQLException("PRIVATE");
                                    return out;
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
                        }
                        return m.invoke(real, a);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
                var f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                tx = (JdbcTransaction) ((Unsafe) f.get(null)).allocateInstance(JdbcTransaction.class);
                f = JdbcTransaction.class.getDeclaredField("connection");
                f.setAccessible(true);
                f.set(tx, writer);
                f = JdbcTransaction.class.getDeclaredField("isActive");
                f.setAccessible(true);
                f.setBoolean(tx, true);
                JdbcRepository.TX.set(tx);
                return tx;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        RequestContext http(String token, String path, String body) throws Exception {
            var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, body == null ? io.netty.handler.codec.http.HttpMethod.GET : io.netty.handler.codec.http.HttpMethod.PUT, path, io.netty.buffer.Unpooled.copiedBuffer(body == null ? "" : body, StandardCharsets.UTF_8));
            try {
                if (token != null) raw.headers().set("Authorization", "Bearer " + token);
                raw.headers().set("Cookie", "legacy");
                raw.headers().set("Content-Type", "application/json");
                raw.headers().set("X-Real-IP", "127.0.0.1");
                var req = new Request(null, raw);
                req.setBytes(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
                req.setParams(new HashMap<>(Map.of("apiKey", "legacy")));
                var ctx = new RequestContext(req, new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
                ctx.attr("user", new JSONObject().put("oId", "2"));
                ctx.addHandler(new org.b3log.latke.http.handler.RouteHandler());
                ctx.addHandler(c -> {
                    if (body == null) controller.mfa(c);
                    else controller.password(c);
                });
                ctx.handle();
                return ctx;
            } finally {
                raw.release();
            }
        }

        public void close() throws Exception {
            if (active()) tx.rollback();
            JdbcRepository.TX.remove();
            db.close();
            Files.deleteIfExists(file);
        }
    }

    interface A {
        void run() throws Exception;
    }

    static void error(int status, A a) throws Exception {
        try {
            a.run();
            Assert.fail("Expected " + status);
        } catch (ApiV3Exception e) {
            Assert.assertEquals(e.status, status);
        }
    }

    public static void main(String[] a) throws Exception {
        var p = new Properties();
        p.setProperty("runtimeDatabase", "NONE");
        p.setProperty("contextPath", "");
        Latkes.setLatkeProps(p);
        Latkes.setLocalProps(p);
        try (var f = new F()) {
            Assert.assertEquals(f.settings.mfa(identity()).keySet(), Set.of("enabled"));
            Assert.assertFalse(f.settings.mfa(identity()).getBoolean("enabled"));
            var result = f.settings.password(identity(), body(), "ip");
            Assert.assertEquals(result.keySet(), Set.of("changed", "reauthenticationRequired"));
            Assert.assertTrue(result.getBoolean("changed"));
            Assert.assertEquals(rows(f.db, "SELECT userPassword FROM u WHERE oId='1'").getFirst().getString("userPassword"), NEW);

            // 密码与凭据版本一并更新，只有当前账号的旧授权被撤销。
            Assert.assertEquals(rows(f.db, "SELECT credentialEpoch FROM states WHERE oId='1'").getFirst().getLong("credentialEpoch"), 1L);
            Assert.assertEquals(rows(f.db, "SELECT COUNT(*) AS n FROM tokens WHERE userId='1' AND state=0").getFirst().getInt("n"), 0);
            Assert.assertEquals(rows(f.db, "SELECT state FROM openid WHERE userId='1'").getFirst().getInt("state"), 2);
            Assert.assertEquals(rows(f.db, "SELECT COUNT(*) AS n FROM requests WHERE userId='1'").getFirst().getInt("n"), 1);
            Assert.assertEquals(rows(f.db, "SELECT COUNT(*) AS n FROM codes WHERE userId='1'").getFirst().getInt("n"), 1);
            Assert.assertEquals(rows(f.db, "SELECT state FROM tokens WHERE userId='2'").getFirst().getInt("state"), 0);
            Assert.assertEquals(f.locks.subList(0, 5), List.of("namespace", "client", "user", "token", "epoch"));
            error(403, () -> f.settings.mfa(identity()));
        }
        System.out.println("PASS security 1 actual first-party SQL/status projection and strict password+epoch/all-client v3/OpenID/pending/code atomic revocation, lock order and other-owner isolation");
        try (var f = new F()) {
            error(422, () -> f.settings.password(identity(), body().put("currentPassword", NEW), "ip"));
            sql(f.db, "UPDATE u SET secret2fa=? WHERE oId='1'", SECRET);
            Assert.assertTrue(f.settings.mfa(identity()).getBoolean("enabled"));
            error(422, () -> f.settings.password(identity(), body(), "ip"));
            error(422, () -> f.settings.password(identity(), body().put("currentTotp", "000000"), "ip"));
            f.settings.password(identity(), body().put("currentTotp", totp()), "ip");
        }
        System.out.println("PASS security 2 current database password and real TOTP algorithm, missing/wrong factor rejected, enabled state is bool only");
        for (String mutation : List.of(
                "UPDATE tokens SET clientId='third' WHERE oId='100'",
                "UPDATE tokens SET state=2 WHERE oId='100'",
                "UPDATE tokens SET expiresAt=1 WHERE oId='100'",
                "UPDATE clients SET status=1 WHERE clientId='website_v3'",
                "UPDATE u SET userStatus=4 WHERE oId='1'",
                "UPDATE tokens SET scope='profile.read' WHERE oId='100'"))
            try (var f = new F()) {
                sql(f.db, mutation);
                error(403, () -> f.settings.password(identity(), body(), "ip"));
                Assert.assertEquals(rows(f.db, "SELECT userPassword FROM u WHERE oId='1'").getFirst().getString("userPassword"), OLD);
            }
        System.out.println("PASS security 3 current first-party access/client/owner/scope validity; forged cached principal never upgrades a third-party or expired grant");
        for (String fault : List.of("read", "password", "revoke", "commit"))
            try (var f = new F()) {
                f.fault = fault;
                error(503, () -> f.settings.password(identity(), body(), "ip"));
                Assert.assertEquals(rows(f.db, "SELECT userPassword FROM u WHERE oId='1'").getFirst().getString("userPassword"), OLD);
                Assert.assertEquals(rows(f.db, "SELECT COUNT(*) AS n FROM states").getFirst().getInt("n"), 0);
                Assert.assertEquals(rows(f.db, "SELECT state FROM tokens WHERE oId='100'").getFirst().getInt("state"), 0);
                Assert.assertEquals(rows(f.db, "SELECT COUNT(*) AS n FROM requests WHERE userId='1'").getFirst().getInt("n"), 2);
            }
        // 提交回执丢失时，已提交的新密码仍生效，旧访问凭据也仍被拒绝。
        try (var f = new F()) {
            f.fault = "ack";
            error(503, () -> f.settings.password(identity(), body(), "ip"));
            Assert.assertEquals(rows(f.db, "SELECT userPassword FROM u WHERE oId='1'").getFirst().getString("userPassword"), NEW);
            f.fault = "";
            error(403, () -> f.settings.password(identity(), body(), "ip"));
        }
        System.out.println("PASS security 4 actual JDBC read/password/revocation/commit rollback, lost-ack remains committed and revoked access cannot obtain ordinary replay");
        try (var f = new F()) {
            f.allowed = false;
            error(429, () -> f.settings.password(identity(), body(), "ip"));
            Assert.assertEquals(f.roots, 0);
            for (var bad : List.of(new JSONObject(), body().put("currentPassword", OLD.toUpperCase()),
                    body().put("newPassword", "x"), body().put("currentTotp", 123456), body().put("userId", "2")))
                error(422, () -> f.settings.password(identity(), bad, "ip"));
            ApiV3SecurityProcessor.registerRoutes(f.controller, f.auth, f.writes);
            Dispatcher.mapping();
            for (String token : Arrays.asList(null, "bad", "wrong"))
                Assert.assertEquals(f.http(token, "/api/v3/me/security/mfa", null).attr("test.status"), "wrong".equals(token) ? 403 : 401);
            var state = f.http("owner", "/api/v3/me/security/mfa", null);
            Assert.assertEquals(state.attr("test.status"), 200);
            Assert.assertEquals(state.getResponse().getHeader("Cache-Control"), "no-store");
            f.allowed = true;
            Assert.assertEquals(f.http("owner", "/api/v3/me/settings/password", body().toString()).attr("test.status"), 200);
            Assert.assertEquals(f.http("owner", "/api/v3/me/security/mfa", null).attr("test.status"), 401);
        }
        System.out.println("PASS security 5 input/attempt gate before roots, real GET+PUT Dispatcher/auth/write middleware, no Cookie/apiKey identity, no-store and old access immediately rejected");
        try (var f = new F()) {
            f.settings.password(identity(), body().put("newPassword", OLD), "ip");
            Assert.assertEquals(rows(f.db, "SELECT credentialEpoch FROM states WHERE oId='1'").getFirst().getLong("credentialEpoch"), 1L);
        }
        try (var f = new F()) {
            sql(f.db, "UPDATE u SET secret2fa='INVALID_SECRET' WHERE oId='1'");
            error(503, () -> f.settings.password(identity(), body().put("currentTotp", "123456"), "ip"));
        }
        var actualBudget = new ApiV3SecurityProofService();
        final var limited = new JSONObject().put("user", new JSONObject().put("oId", "99991"));
        for (int i = 0; i < 12; i++)
            actualBudget.checkAttempts(limited, "budget-only-ip");
        error(429, () -> actualBudget.checkAttempts(limited, "budget-only-ip"));
        System.out.println("PASS security 6 actual bounded shared proof attempts, corrupt MFA secret fails closed without algorithm logging, same-password success still revokes credentials without relying on affected-row mode");

    }
}
