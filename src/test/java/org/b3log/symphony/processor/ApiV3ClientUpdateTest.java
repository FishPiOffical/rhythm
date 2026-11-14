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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import static org.b3log.symphony.processor.ApiV3ClientManagementTest.*;

/**
 * 执行实际全量替换 SQL 和严格根事务，并检查仍有效的授权同意、授权码和 SocketTicket 是否失效。
 */
public final class ApiV3ClientUpdateTest {
    static final String BASE = "/api/v3/admin/oauth/clients/", TARGET = "external_app";
    static final String FIRST = "https://App.example:8443/Callback?exact=%2B", SECOND = "com.example.client:/oauth2redirect", VERIFIER = "v".repeat(43);

    static final class UpdateFixture implements AutoCloseable {
        final Fixture f = new Fixture();
        final ApiV3ClientUpdateService service = new ApiV3ClientUpdateService();
        final ApiV3ClientUpdateProcessor processor = new ApiV3ClientUpdateProcessor();
        final ApiV3AuthorizationCodeService codeService = new ApiV3AuthorizationCodeService();
        final ApiV3AuthorizationRequestService consentService = new ApiV3AuthorizationRequestService();
        ApiV3AuthorizationCodeService.Issued oldCode;
        ApiV3AuthorizationRequestService.Prepared oldConsent;
        String before;
        final ApiV3AuthorizationCodeRepository codes = new ApiV3AuthorizationCodeRepository() {
            @Override
            public String getName() {
                return "authcodes";
            }

            @Override
            public boolean hasTransactionBegun() {
                return f.active();
            }

            @Override
            public Transaction beginTransaction() {
                return f.receipts.beginTransaction();
            }

            @Override
            public List<JSONObject> select(String sql, Object... args) throws RepositoryException {
                return f.read("code", sql, args);
            }

            @Override
            public String add(JSONObject row) throws RepositoryException {
                f.insert("authcodes", row);
                return row.getString("oId");
            }
        };
        final ApiV3AuthorizationRequestRepository consents = new ApiV3AuthorizationRequestRepository() {
            @Override
            public String getName() {
                return "consents";
            }

            @Override
            public boolean hasTransactionBegun() {
                return f.active();
            }

            @Override
            public Transaction beginTransaction() {
                return f.receipts.beginTransaction();
            }

            @Override
            public List<JSONObject> select(String sql, Object... args) throws RepositoryException {
                return f.read("consent", sql, args);
            }

            @Override
            public String add(JSONObject row) throws RepositoryException {
                f.insert("consents", row);
                return row.getString("oId");
            }

            @Override
            public void update(String id, JSONObject row, String... names) throws RepositoryException {
                exec(f.writer, "UPDATE consents SET decision=?,decidedAt=? WHERE oId=?", row.getString("decision"), row.getLong("decidedAt"), id);
            }
        };
        final ApiV3RequestRepository receipts = new ApiV3RequestRepository() {
            @Override
            public boolean hasTransactionBegun() {
                return f.active();
            }

            @Override
            public Transaction beginTransaction() {
                final var root = f.receipts.beginTransaction();
                try {
                    final Connection actual = f.tx.getConnection();
                    final Connection observed = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
                        try {
                            final var result = m.invoke(actual, a);
                            if (!m.getName().equals("prepareStatement")) return result;
                            final String sql = (String) a[0];
                            final var statement = (PreparedStatement) result;
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (q, n, b) -> {
                                try {
                                    final var value = n.invoke(statement, b);
                                    if (n.getName().equals("executeUpdate")) {
                                        if (f.faults.contains("observer")) equal(snapshot(), before);
                                        if (sql.startsWith("UPDATE `clients`")) f.fault("metadata.after");
                                        if (sql.startsWith("DELETE FROM `redirects`")) f.fault("redirect.delete.after");
                                        if (sql.startsWith("INSERT INTO `redirects`")) f.fault("redirect.insert.after");
                                        if (sql.startsWith("UPDATE `tokens`")) f.fault("token.after");
                                        if (sql.startsWith("DELETE FROM `authcodes`")) f.fault("code.after");
                                        if (sql.startsWith("DELETE FROM `consents`")) f.fault("consent.after");
                                    }
                                    return value;
                                } catch (InvocationTargetException error) {
                                    throw error.getCause();
                                }
                            });
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
                    inject(f.tx, f.tx.getClass(), "connection", observed);
                    return root;
                } catch (Exception error) {
                    if (root.isActive()) root.rollback();
                    throw new IllegalStateException(error);
                }
            }

            @Override
            public JSONObject getForUpdate(String id) throws RepositoryException {
                return f.receipts.getForUpdate(id);
            }

            @Override
            public String add(JSONObject row) throws RepositoryException {
                return f.receipts.add(row);
            }

            @Override
            public void update(String id, JSONObject row, String... names) throws RepositoryException {
                f.receipts.update(id, row, names);
            }
        };

        UpdateFixture() throws Exception {
            exec(f.writer, "CREATE TABLE app_logins(oId TEXT PRIMARY KEY,state TEXT)");
            exec(f.writer, "INSERT INTO app_logins VALUES('pending','APP_CONFIRMED'),('done','CONSUMED'),('denied','DENIED')");
            final var appLogins = new ApiV3AppLoginRequestRepository() {
                @Override
                public String getName() {
                    return "app_logins";
                }

                @Override
                public boolean hasTransactionBegun() {
                    return f.active();
                }
            };
            inject(service, ApiV3ClientUpdateService.class, "appLogins", appLogins);

            exec(f.writer, "ALTER TABLE tokens ADD COLUMN updatedAt BIGINT DEFAULT 0");
            exec(f.writer, "CREATE TABLE authcodes(oId TEXT PRIMARY KEY,clientId TEXT,userId TEXT,redirectUri TEXT,scope TEXT,codeChallenge TEXT,createdAt BIGINT,expiresAt BIGINT,consumedAt BIGINT,familyId TEXT)");
            exec(f.writer, "CREATE TABLE consents(oId TEXT PRIMARY KEY,userId TEXT,clientId TEXT,redirectUri TEXT,scope TEXT,state TEXT,codeChallenge TEXT,consentHash TEXT,createdAt BIGINT,expiresAt BIGINT,decidedAt BIGINT,decision TEXT)");
            exec(f.writer, "UPDATE clients SET scope='profile.read content.read' WHERE clientId=?", TARGET);
            exec(f.writer, "INSERT INTO clients VALUES('3','other_app','Other','content.read',0,'PRIVATE')");
            exec(f.writer, "DELETE FROM redirects");
            callback(FIRST, 0);
            callback(SECOND, 0);
            final var userRows = new UserRepository() {
                @Override
                public String getName() {
                    return "user";
                }

                @Override
                public List<JSONObject> select(String sql, Object... args) throws RepositoryException {
                    return f.read("user", sql, args);
                }
            };
            inject(service, ApiV3ClientUpdateService.class, "management", f.service);
            inject(service, ApiV3ClientUpdateService.class, "registration", f.registration);
            inject(service, ApiV3ClientUpdateService.class, "clients", f.clients);
            inject(service, ApiV3ClientUpdateService.class, "redirects", f.redirects);
            inject(service, ApiV3ClientUpdateService.class, "tokens", f.tokens);
            inject(service, ApiV3ClientUpdateService.class, "codes", codes);
            inject(service, ApiV3ClientUpdateService.class, "consents", consents);
            inject(service, ApiV3ClientUpdateService.class, "requests", receipts);
            inject(processor, ApiV3ClientUpdateProcessor.class, "management", f.service);
            inject(processor, ApiV3ClientUpdateProcessor.class, "service", service);
            inject(codeService, ApiV3AuthorizationCodeService.class, "clients", f.clients);
            inject(codeService, ApiV3AuthorizationCodeService.class, "redirects", f.redirects);
            inject(codeService, ApiV3AuthorizationCodeService.class, "codes", codes);
            inject(codeService, ApiV3AuthorizationCodeService.class, "users", userRows);
            inject(codeService, ApiV3AuthorizationCodeService.class, "tokenRows", f.tokens);
            inject(codeService, ApiV3AuthorizationCodeService.class, "tokens", new ApiV3TokenMgmtService());
            inject(consentService, ApiV3AuthorizationRequestService.class, "requests", consents);
            inject(consentService, ApiV3AuthorizationRequestService.class, "codes", codeService);
            ApiV3ClientUpdateProcessor.registerRoutes(processor, f.auth, new org.b3log.symphony.processor.middleware.ApiV3WriteMidware());
            Dispatcher.mapping();
            oldConsent = consentService.start("1", TARGET, FIRST, "content.read", "state", ApiV3Pkce.challenge(VERIFIER), "S256");
            var root = f.receipts.beginTransaction();
            try {
                oldCode = codeService.issueInCurrentTransaction("1", TARGET, FIRST, "content.read", ApiV3Pkce.challenge(VERIFIER), "S256");
                root.commit();
            } finally {
                if (root.isActive()) root.rollback();
            }
            final var code = query(f.reader, "SELECT * FROM authcodes").getFirst();
            final var pending = query(f.reader, "SELECT * FROM consents").getFirst();
            for (int i = 0; i < 75; i++) {
                var copy = new JSONObject(code.toString()).put("oId", "old-code-" + i).put("userId", String.valueOf(i % 2 + 1));
                if (i % 2 == 0) copy.put("consumedAt", System.currentTimeMillis()).put("familyId", "a".repeat(32));
                f.insert("authcodes", copy);
                f.insert("consents", new JSONObject(pending.toString()).put("oId", "old-pending-" + i).put("userId", String.valueOf(i % 2 + 1)));
                exec(f.writer, "INSERT INTO tokens VALUES(?,?,?,?,?,?,?,?,?)", "20" + i, String.valueOf(i % 2 + 1), TARGET, i % 2 == 0 ? "access" : "refresh", i % 4, 9999999999999L, "content.read", "PRIVATE", 0);
            }
            f.insert("authcodes", new JSONObject(code.toString()).put("oId", "other-code").put("clientId", "other_app"));
            f.insert("consents", new JSONObject(pending.toString()).put("oId", "other-pending").put("clientId", "other_app"));
            for (String decision : List.of("approved", "denied"))
                f.insert("consents", new JSONObject(pending.toString()).put("oId", "history-" + decision).put("decision", decision).put("decidedAt", System.currentTimeMillis()));
            exec(f.writer, "INSERT INTO tokens VALUES('5000','1','other_app','access',0,9999999999999,'content.read','PRIVATE',7)");
            before = snapshot();
        }

        void callback(String uri, int state) throws Exception {
            exec(f.writer, "INSERT INTO redirects VALUES(?,?,?,?,'PRIVATE')", ApiV3ClientRedirectRepository.identity(TARGET, uri), TARGET, uri, state);
        }

        String proofs() throws Exception {
            return query(f.reader, "SELECT * FROM authcodes ORDER BY oId") + "|" + query(f.reader, "SELECT * FROM consents ORDER BY oId") + "|" + query(f.reader, "SELECT oId,state,updatedAt FROM tokens ORDER BY oId");
        }

        String snapshot() throws Exception {
            return query(f.reader, "SELECT clientId,name,scope,status FROM clients ORDER BY clientId") + "|" + query(f.reader, "SELECT oId,clientId,redirectUri,status FROM redirects ORDER BY oId") + "|" + proofs() + "|" + query(f.reader, "SELECT * FROM requests ORDER BY oId");
        }

        JSONObject update(String key, String name, String scope, List<String> callbacks) throws Exception {
            return service.update(f.identity("11"), TARGET, new ApiV3ClientManagementInputs.Registration(name, scope, callbacks), key);
        }

        JSONObject http(String target, String token, String body, String key) throws Exception {
            var bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.PUT, BASE + target, Unpooled.wrappedBuffer(bytes));
            var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            try {
                raw.headers().set("Content-Type", "application/json").set("Cookie", "PRIVATE-admin");
                if (token != null) raw.headers().set("Authorization", "Bearer " + token);
                if (key != null) raw.headers().set("Idempotency-Key", key);
                var request = new Request(null, raw) {
                    @Override
                    public byte[] getBytes() {
                        return bytes;
                    }

                    @Override
                    public String getRemoteAddr() {
                        return "192.0.2.19";
                    }
                };
                request.setParams(Map.of("userId", "2", "clientId", "website_v3", "apiKey", "PRIVATE"));
                var c = new RequestContext(request, new Response(null, response));
                c.attr("user", new JSONObject().put("oId", "1").put("userRole", "adminRole"));
                c.addHandler(new RouteHandler());
                c.addHandler(context -> {
                    var route = (RouteResolution) context.attr("MATCH_RESULT");
                    try {
                        route.getContextHandlerMeta().getInvokeHolder().invoke(processor, context);
                    } catch (ReflectiveOperationException error) {
                        throw new AssertionError(error);
                    }
                });
                c.handle();
                var result = new JSONObject().put("status", c.attr("test.status"));
                if (c.attr("test.data") != null) result.put("data", c.attr("test.data"));
                if (result.getInt("status") != 401 && !"14".equals(token)) {
                    equal(response.headers().get("Cache-Control"), "no-store");
                    equal(response.headers().get("Vary"), "Authorization");
                }
                check(!result.toString().contains("PRIVATE"), "no confidential response");
                return result;
            } finally {
                raw.release();
            }
        }

        public void close() throws Exception {
            f.close();
        }
    }

    static String body(String name, String scope, List<String> callbacks) {
        return new JSONObject().put("name", name).put("scope", scope).put("redirectUris", new JSONArray(callbacks)).toString();
    }

    static void invalidated(UpdateFixture x) throws Exception {
        equal(query(x.f.reader, "SELECT * FROM tokens WHERE clientId=? AND (state<>2 OR state IS NULL)", TARGET).size(), 0);
        equal(query(x.f.reader, "SELECT * FROM authcodes WHERE clientId=?", TARGET).size(), 0);
        equal(query(x.f.reader, "SELECT * FROM consents WHERE clientId=? AND decision='pending'", TARGET).size(), 0);
        equal(query(x.f.reader, "SELECT * FROM consents WHERE clientId=? AND decision IN ('approved','denied')", TARGET).size(), 2);
        equal(query(x.f.reader, "SELECT * FROM authcodes WHERE clientId='other_app'").size(), 1);
        equal(query(x.f.reader, "SELECT * FROM consents WHERE clientId='other_app'").size(), 1);
        equal(query(x.f.reader, "SELECT state FROM tokens WHERE oId='5000'").getFirst().getInt("state"), 0);
        equal(query(x.f.reader, "SELECT state FROM tokens WHERE oId='11'").getFirst().getInt("state"), 0);
    }

    public static void main(String[] args) throws Exception {
        var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);
        try (var x = new UpdateFixture()) {
            String proofs = x.proofs();
            for (String token : Arrays.asList(null, "invalid"))
                equal(x.http(TARGET, token, body("New", "content.read", List.of(FIRST)), "denied").getInt("status"), 401);
            for (String token : List.of("12", "13", "14"))
                equal(x.http(TARGET, token, body("New", "content.read", List.of(FIRST)), "denied").getInt("status"), 403);
            equal(x.snapshot(), x.before);
            var result = data(x.http(TARGET, "11", body(" New name ", "profile.read content.read profile.read", List.of(SECOND, FIRST)), "name"));
            equal(result.keySet(), Set.of("clientId", "name", "scope", "status", "redirectUris"));
            equal(result.getString("name"), "New name");
            equal(result.getString("scope"), "content.read profile.read");
            equal(result.getInt("status"), 0);
            equal(x.proofs(), proofs);
            check(x.f.clientNamespaceLocked, "sorted namespace lock acquired");
            var decision = x.consentService.decide("1", x.oldConsent.requestId(), x.oldConsent.consentToken(), false);
            check(!decision.approved(), "name-only preserves actual outstanding consent usability");
        }
        System.out.println("PASS 1 actual PUT/current-admin gates and pure name/scoped-word/URI-order normalization; name-only keeps tokens/codes/consent proofs, actual old consent can still complete");
        for (boolean uriChange : List.of(false, true))
            try (var x = new UpdateFixture()) {
                var result = x.update("change", "Updated", uriChange ? "content.read profile.read" : "content.read", uriChange ? List.of("https://new.example/cb") : List.of(FIRST, SECOND));
                equal(result.getString("clientId"), TARGET);
                invalidated(x);
                equal(query(x.f.reader, "SELECT redirectUri FROM redirects WHERE clientId=?", TARGET).size(), uriChange ? 1 : 2);
                check(!x.f.active() && JdbcRepository.TX.get() == null, "committed strict root disposed");
            }
        System.out.println("PASS 2 either effective scope or exact callback-set change revokes every target token and all old codes/pending requests beyond fifty, retaining completed user decisions and unrelated clients");
        try (var x = new UpdateFixture()) {
            x.update("change", "Changed", "content.read", List.of(FIRST, SECOND));
            invalidated(x);
            x.update("restore", "Original", "profile.read content.read", List.of(SECOND, FIRST));
            denied(400, () -> x.codeService.exchange(TARGET, FIRST, x.oldCode.code(), VERIFIER));
            denied(400, () -> x.consentService.decide("1", x.oldConsent.requestId(), x.oldConsent.consentToken(), true));
            equal(query(x.f.reader, "SELECT * FROM authcodes WHERE clientId=?", TARGET).size(), 0);
        }
        System.out.println("PASS 3 actual code-exchange and consent-decision cores reject deleted old proof even after callback/scope definitions are restored; no manufactured denied or consumed/family state");
        try (var x = new UpdateFixture()) {
            var first = x.update("key", "Updated", "content.read", List.of(SECOND, FIRST));
            var replay = x.update("key", " Updated ", "content.read content.read", List.of(FIRST, SECOND));
            equal(ApiV3Json.canonical(first), ApiV3Json.canonical(replay));
            x.update("later", "Later", "content.read profile.read", List.of("https://later.example/cb"));
            String after = x.snapshot();
            // 老键重放返回原回执，数据库仍保留之后的更新。
            equal(ApiV3Json.canonical(x.update("key", "Updated", "content.read", List.of(FIRST, SECOND))), ApiV3Json.canonical(first));
            equal(x.snapshot(), after);
            denied(409, () -> x.update("key", "Other", "content.read", List.of(FIRST, SECOND)));
            exec(x.f.writer, "UPDATE clients SET status=1 WHERE clientId=?", TARGET);
            equal(x.update("disabled", "Disabled metadata", "content.read", List.of(FIRST)).getInt("status"), 1);
            equal(query(x.f.reader, "SELECT status FROM clients WHERE clientId=?", TARGET).getFirst().getInt("status"), 1);
        }
        System.out.println("PASS 4 semantic request reordering replays same receipt, changed content conflicts, old replay cannot overwrite newer metadata/disable, editing inactive clients never reactivates");
        try (var x = new UpdateFixture()) {
            String good = body("App", "content.read", List.of(FIRST));
            for (String target : List.of("website_v3", "missing"))
                equal(x.http(target, "11", good, "bad-target").getInt("status"), 404);
            for (String bad : List.of("{}", "{\"name\":\"Only\"}", new JSONObject(good).put("status", 0).toString(), new JSONObject(good).put("userId", "2").toString(), new JSONObject(good).put("scope", 3).toString(), new JSONObject(good).put("redirectUris", new JSONArray().put(FIRST).put(FIRST)).toString()))
                equal(x.http(TARGET, "11", bad, "invalid").getInt("status"), 422);
            equal(x.http(TARGET, "11", "{\"name\":\"one\",\"name\":\"two\"}", "json").getInt("status"), 400);
            equal(x.http(TARGET, "11", good, null).getInt("status"), 422);
            var tooMany = new JSONArray();
            for (int i = 0; i < 21; i++) tooMany.put("https://app.example/" + i);
            equal(x.http(TARGET, "11", new JSONObject(good).put("redirectUris", tooMany).toString(), "many").getInt("status"), 422);
            equal(x.http(TARGET, "11", new JSONObject(good).put("redirectUris", new JSONArray().put("https://app.example/" + "x".repeat(2048))).toString(), "long").getInt("status"), 422);
            equal(x.snapshot(), x.before);
        }
        System.out.println("PASS 5 full-replacement required fields, strict JSON/key/path/20-callback/2048 URI boundaries and protected website reject without metadata/proof/receipt changes");
        try (var x = new UpdateFixture()) {
            x.update("first", "One", "content.read", List.of(FIRST));
            String before = x.snapshot();
            exec(x.f.writer, "UPDATE user SET userRole='defaultRole' WHERE oId='1'");
            denied(403, () -> x.update("first", "One", "content.read", List.of(FIRST)));
            equal(x.snapshot(), before);
        }
        try (var x = new UpdateFixture()) {
            x.f.beforeBegin = () -> exec(x.f.writer, "UPDATE tokens SET state=2 WHERE oId='11'");
            equal(x.http(TARGET, "11", body("Changed", "content.read", List.of(FIRST)), "inflight").getInt("status"), 403);
            equal(query(x.f.reader, "SELECT name FROM clients WHERE clientId=?", TARGET).getFirst().getString("name"), "External");
            equal(query(x.f.reader, "SELECT * FROM requests").size(), 0);
        }
        System.out.println("PASS 6 replay and preflight race recheck current first-party administrator/token within root, preventing post-revocation metadata or privilege changes");
        for (String fault : List.of("metadata.after", "redirect.delete.after", "redirect.insert.after", "token.after", "code.after", "consent.after", "receipt.add.after", "receipt.update.after", "release.after", "commit"))
            try (var x = new UpdateFixture()) {
                x.f.faults.add(fault);
                denied(503, () -> x.update("fault", "Changed", "content.read", List.of("https://new.example/cb")));
                equal(x.snapshot(), x.before);
                check(!x.f.active() && JdbcRepository.TX.get() == null, "failed root clean " + fault);
                x.f.faults.clear();
                x.update("fault", "Changed", "content.read", List.of("https://new.example/cb"));
                invalidated(x);
            }
        System.out.println("PASS 7 every metadata/callback/token/code/pending-consent/receipt write and actual strict JDBC savepoint/commit fault rolls back all effects; same-key recovery is safe");
        try (var x = new UpdateFixture()) {
            x.f.faults.add("observer");
            x.update("observe", "Changed", "content.read", List.of(FIRST));
        }
        try (var x = new UpdateFixture()) {
            x.f.faults.add("commit.after");
            denied(503, () -> x.update("ack", "Changed", "content.read", List.of(FIRST)));
            invalidated(x);
            equal(query(x.f.reader, "SELECT * FROM requests").size(), 1);
            String after = x.snapshot();
            equal(x.update("ack", "Changed", "content.read", List.of(FIRST)).getString("name"), "Changed");
            equal(x.snapshot(), after);
        }
        System.out.println("PASS 8 separate observer never sees partial replacement; actual committed-but-lost JDBC acknowledgement preserves all invalidations and original receipt for non-overwriting retry");
        for (String sql : List.of("UPDATE clients SET status=9 WHERE clientId='external_app'", "UPDATE clients SET scope='unknown.scope' WHERE clientId='external_app'", "UPDATE redirects SET redirectUri='https://user:PRIVATE@app.example/cb' WHERE clientId='external_app'"))
            try (var x = new UpdateFixture()) {
                exec(x.f.writer, sql);
                String before = x.snapshot();
                denied(503, () -> x.update("broken", "Changed", "content.read", List.of(FIRST)));
                equal(x.snapshot(), before);
            }
        try (var x = new UpdateFixture()) {
            x.callback("https://retired.example/cb", 1);
            String proofs = x.proofs();
            x.update("retired-cleanup", "Changed name", "profile.read content.read", List.of(FIRST, SECOND));
            equal(x.proofs(), proofs);
            equal(query(x.f.reader, "SELECT * FROM redirects WHERE clientId=?", TARGET).size(), 2);
        }
        System.out.println("PASS 9 malformed current metadata/registered URI fails closed; removing retired-only callback rows does not revoke unchanged effective authorization");
        try (var x = new UpdateFixture()) {
            for (int i = 0; i < 55; i++) x.callback("https://old.example/" + i, 0);
            x.update("historic-many", "Bounded replacement", "content.read profile.read", List.of(FIRST, SECOND));
            invalidated(x);
            equal(query(x.f.reader, "SELECT * FROM redirects WHERE clientId=?", TARGET).size(), 2);
        }
        System.out.println("PASS 10 twenty-one unique current active callbacks prove a <=20 replacement changed; complete bound DELETE removes all historical callbacks and proofs without truncated comparison");
        try (var x = new UpdateFixture()) {
            exec(x.f.writer, "CREATE TABLE tickets(oId TEXT PRIMARY KEY,userId TEXT,parentTokenId TEXT,channel TEXT,targetId TEXT,createdAt BIGINT,expiresAt BIGINT,consumedAt BIGINT)");
            exec(x.f.writer, "UPDATE tokens SET scope='content.read' WHERE oId='12'");
            final var socket = new org.b3log.symphony.service.ApiV3SocketTicketService();
            final var tickets = new org.b3log.symphony.repository.ApiV3SocketTicketRepository() {
                @Override
                public String getName() {
                    return "tickets";
                }

                @Override
                public boolean hasTransactionBegun() {
                    return x.f.active();
                }

                @Override
                public Transaction beginTransaction() {
                    return x.f.receipts.beginTransaction();
                }

                @Override
                public java.util.List<JSONObject> select(String sql, Object... args) throws RepositoryException {
                    return x.f.read("ticket", sql, args);
                }

                @Override
                public String add(JSONObject row) throws RepositoryException {
                    x.f.insert("tickets", row);
                    return row.getString("oId");
                }

                @Override
                public void update(String id, JSONObject row, String... names) throws RepositoryException {
                    exec(x.f.writer, "UPDATE tickets SET consumedAt=? WHERE oId=?", row.getLong("consumedAt"), id);
                }
            };
            inject(socket, socket.getClass(), "tickets", tickets);
            inject(socket, socket.getClass(), "clients", x.f.clients);
            inject(socket, socket.getClass(), "tokens", x.f.tokens);
            inject(socket, socket.getClass(), "users", new org.b3log.symphony.repository.UserRepository() {
                @Override
                public String getName() {
                    return "user";
                }
            });
            var issued = socket.issueChat("1", "12");
            var live = socket.consume(issued.ticket());
            check(socket.authorized(live), "issued grant currently authorized");
            var pending = socket.issueChat("1", "12");
            x.update("socket", "Changed", "content.read", List.of(FIRST, SECOND));
            check(!socket.authorized(live), "existing current-authority grant denied after authorization change");
            denied(401, () -> socket.consume(pending.ticket()));
            denied(401, () -> socket.issueChat("1", "12"));
            check(!x.f.active() && JdbcRepository.TX.get() == null, "failed ticket root disposed");
        }
        System.out.println("PASS 11 actual SocketTicket current parent/client SQL rejects existing grants, unconsumed old tickets and new issuance after committed callback/scope change; no real socket/network claimed");

        try (var x = new UpdateFixture()) {
            exec(x.f.writer, "INSERT INTO clients VALUES('native','fishpi_app_v3','Native','profile.read content.read',0,'PRIVATE')");
            x.service.update(x.f.identity("11"), "fishpi_app_v3", new ApiV3ClientManagementInputs.Registration("Native", "profile.read", List.of(FIRST)), "native-change");
            equal(query(x.f.reader, "SELECT oId FROM app_logins ORDER BY oId").size(), 2);
            equal(query(x.f.reader, "SELECT oId FROM app_logins WHERE oId='pending'").size(), 0);
            equal(query(x.f.reader, "SELECT scope FROM clients WHERE clientId='fishpi_app_v3'").getFirst().getString("scope"), "profile.read");
        }
        System.out.println("PASS 12 native APP authorization change invalidates pending QR requests in the same administrator transaction and preserves terminal history");

        System.out.println("Client update isolated tests passed: 12 groups");
    }
}
