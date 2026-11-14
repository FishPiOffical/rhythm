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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.*;

import java.util.*;

/** 使用内存仓库验证真实服务的事务顺序，不连接数据库或审核供应商。 */
public class RepeaterStationHardeningTest {
    private final Map<String, Object> previous = new HashMap<>();

    @BeforeClass
    public void setup() throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        props.setProperty("serverScheme", "http");
        props.setProperty("serverHost", "localhost");
        props.setProperty("serverPort", "8081");
        for (final String name : List.of("latkeProps", "localProps")) {
            final var f = Latkes.class.getDeclaredField(name);
            f.setAccessible(true);
            previous.put(name, f.get(null));
            f.set(null, props);
        }
    }

    @AfterClass(alwaysRun = true)
    public void restore() throws Exception {
        for (final var entry : previous.entrySet()) {
            final var f = Latkes.class.getDeclaredField(entry.getKey());
            f.setAccessible(true);
            f.set(null, entry.getValue());
        }
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        final var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static JSONObject body(String text) {
        return new JSONObject().put("type", "joke").put("content", text);
    }

    @Test(timeOut = 60000)
    public void v3ApprovalPublicationAndReplay() throws Exception {
        final Fixture f = new Fixture();
        final JSONObject result = f.v3.publish("1", body("first\nsecond &amp; third"), "publish-1");
        Assert.assertEquals(f.row.getString(RepeaterContent.CONTENT), "first\nsecond & third");
        Assert.assertEquals(f.events, 1);
        Assert.assertEquals(f.adds, 1);
        Assert.assertFalse(f.active);
        f.reject = true;
        Assert.assertTrue(result.similar(f.v3.publish("1", body("first\nsecond &amp; third"), "publish-1")));
        Assert.assertEquals(f.adds, 1);
        Assert.assertEquals(f.events, 1);
        Assert.assertEquals(f.commits, 2);
        Assert.assertEquals(Assert.expectThrows(ApiV3Exception.class,
                () -> f.v3.publish("1", body("first second &amp; third"), "publish-1")).status, 409);
        Assert.assertEquals(f.adds, 1);
    }

    @Test(timeOut = 60000)
    public void legacyReceiptStillReplaysWhenModerationUnavailable() throws Exception {
        final Fixture f = new Fixture();
        final JSONObject input = body("first\nsecond");
        f.receipt = new JSONObject().put("requestHash", ApiV3TokenUtil.hash("request-body\0"
                + ApiV3Json.canonical(ApiV3RepeaterPublicationInputs.legacyReceipt(input))))
                .put("responseJson", new JSONObject().put("id", "123").put("type", "joke").put("createdTime", 1).toString());
        f.reject = true;
        Assert.assertEquals(f.v3.publish("1", input, "legacy").getString("id"), "123");
        Assert.assertEquals(f.adds, 0);
        Assert.assertEquals(f.commits, 1);
    }

    @Test(timeOut = 60000)
    public void rejectionAndBusinessFailureRollback() throws Exception {
        final Fixture rejected = new Fixture();
        rejected.reject = true;
        Assert.assertEquals(Assert.expectThrows(ApiV3Exception.class,
                () -> rejected.v3.publish("1", body("plain text"), "reject")).status, 503);
        Assert.assertEquals(rejected.adds, 0);
        Assert.assertNull(rejected.receipt);
        Assert.assertEquals(rejected.rollbacks, 1);
        final Fixture failed = new Fixture();
        failed.failCapture = true;
        Assert.expectThrows(ApiV3Exception.class, () -> failed.v3.publish("1", body("plain text"), "fail"));
        Assert.assertNull(failed.row);
        Assert.assertNull(failed.receipt);
        Assert.assertEquals(failed.commits, 0);
        Assert.assertFalse(failed.active);
    }

    @Test(timeOut = 60000)
    public void classicApprovalRunsBeforeTransaction() throws Exception {
        final Fixture f = new Fixture();
        final JSONObject request = new JSONObject().put(RepeaterContent.TYPE, "joke")
                .put(RepeaterContent.CONTENT, "first\r\nsecond");
        Assert.assertEquals(f.classic.createUserContent("1", request).getString(RepeaterContent.CONTENT), "first\nsecond");
        Assert.assertEquals(f.events, 1);
        Assert.assertEquals(f.commits, 1);
        final Fixture blocked = new Fixture();
        blocked.reject = true;
        Assert.expectThrows(ServiceException.class, () -> blocked.classic.createUserContent("1", request));
        Assert.assertEquals(blocked.begins, 0);
        Assert.assertEquals(blocked.adds, 0);
    }

    @Test(timeOut = 60000)
    public void deletionAuditAndReversalAreAtomic() throws Exception {
        for (final boolean failAudit : List.of(false, true)) {
            final Fixture f = new Fixture();
            f.row = new JSONObject().put("oId", "123").put(RepeaterContent.STATUS, 0);
            f.failAudit = failAudit;
            if (failAudit) Assert.expectThrows(ServiceException.class,
                    () -> f.classic.removeContent("123", new JSONObject()));
            else f.classic.removeContent("123", new JSONObject());
            Assert.assertEquals(f.row.getInt(RepeaterContent.STATUS), failAudit ? 0 : 1);
            Assert.assertEquals(f.reversals, failAudit ? 0 : 1);
            Assert.assertEquals(f.commits, failAudit ? 0 : 1);
            Assert.assertEquals(f.rollbacks, failAudit ? 1 : 0);
            Assert.assertFalse(f.active);
        }
    }

    @Test(timeOut = 60000)
    public void discoveryUsesBoundParametersAndFullResponseProjection() throws Exception {
        final RepeaterQueryService query = new RepeaterQueryService();
        final var statements = new ArrayList<String>();
        inject(query, "repeaterContentRepository", new RepeaterContentRepository() {
            public List<JSONObject> select(String sql, Object... args) {
                statements.add(sql);
                Assert.assertFalse(sql.contains("101"));
                Assert.assertFalse(sql.contains("SELECT *"));
                Assert.assertFalse(sql.contains("RAND()"));
                Assert.assertTrue(Arrays.asList(args).contains("101"));
                if (sql.contains("COUNT(*)")) return List.of(new JSONObject().put("total", 75));
                Assert.assertTrue(((Number) args[args.length - 1]).longValue() < 75);
                for (String key : List.of("repeaterContentSource", "repeaterContentStatus", "repeaterContentUpdatedTime"))
                    Assert.assertTrue(sql.contains(key));
                return List.of(new JSONObject().put("oId", "175").put(RepeaterContent.TYPE, "joke")
                        .put(RepeaterContent.CONTENT, "text").put(RepeaterContent.AUTHOR_ID, ""));
            }
        });
        Assert.assertEquals(query.next("joke", "", List.of("101")).getString("oId"), "175");
        Assert.assertEquals(statements.size(), 2);
        Assert.expectThrows(ServiceException.class, () -> query.next("joke", "", List.of("1 OR 1=1")));
    }

    @Test(timeOut = 60000)
    public void pageWritesRejectCrossOriginAndMissingIdentity() {
        final var raw = new io.netty.handler.codec.http.DefaultFullHttpRequest(
                io.netty.handler.codec.http.HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.POST, "/api/repeater");
        try {
            final var request = new org.b3log.latke.http.Request(null, raw);
            request.setParams(new HashMap<>());
            final var context = new org.b3log.latke.http.RequestContext(request, new org.b3log.latke.http.Response(null,
                    new io.netty.handler.codec.http.DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                            io.netty.handler.codec.http.HttpResponseStatus.OK)));
            Assert.assertFalse(RepeaterWriteSecurity.allowed(context));
            context.attr("user", new JSONObject().put("oId", "1"));
            raw.headers().set("Origin", "http://localhost:8081");
            Assert.assertTrue(RepeaterWriteSecurity.allowed(context));
            raw.headers().set("Origin", "http://localhost:8081.attacker.test");
            Assert.assertFalse(RepeaterWriteSecurity.allowed(context));
            raw.headers().set("Origin", "null");
            Assert.assertFalse(RepeaterWriteSecurity.allowed(context));
            raw.headers().remove("Origin");
            raw.headers().set("Referer", "http://localhost:8081/");
            Assert.assertTrue(RepeaterWriteSecurity.allowed(context));
            raw.headers().set("Sec-Fetch-Site", "cross-site");
            Assert.assertFalse(RepeaterWriteSecurity.allowed(context));
        } finally { raw.release(); }
    }

    static class Fixture {
        final ApiV3RepeaterPublicationService v3 = new ApiV3RepeaterPublicationService();
        final RepeaterMgmtService classic = new RepeaterMgmtService();
        boolean active, reject, failCapture, failAudit;
        int begins, commits, rollbacks, adds, events, reversals;
        JSONObject row, receipt;

        Fixture() throws Exception {
            final RepeaterContentRepository contents = new RepeaterContentRepository() {
                public boolean hasTransactionBegun() { return active; }
                public Transaction beginTransaction() { return begin(); }
                public String add(JSONObject value) { Assert.assertTrue(active); adds++; row = copy(value); return value.getString("oId"); }
                public List<JSONObject> select(String sql, Object... args) {
                    Assert.assertTrue(active);
                    Assert.assertTrue(sql.endsWith("FOR UPDATE"));
                    return row == null ? List.of() : List.of(copy(row));
                }
                public void update(String id, JSONObject changes, String... fields) {
                    Assert.assertTrue(active);
                    for (String field : fields) row.put(field, changes.get(field));
                }
            };
            final ApiV3RequestRepository requests = new ApiV3RequestRepository() {
                public boolean hasTransactionBegun() { return active; }
                public Transaction beginTransaction() { return begin(); }
                public JSONObject getForUpdate(String id) { Assert.assertTrue(active); return copy(receipt); }
                public String add(JSONObject value) { receipt = copy(value); return value.getString("oId"); }
                public void update(String id, JSONObject value, String... fields) { receipt = copy(value); }
            };
            final RepeaterContentModerationService moderation = new RepeaterContentModerationService() {
                public void requireApproved(String text) {
                    Assert.assertFalse(active, "审核不可持有业务事务");
                    if (reject) throw new ApiV3Exception(503, "temporarily_unavailable", "审核不可用");
                }
            };
            final ProfessionSourceEventCaptureService capture = new ProfessionSourceEventCaptureService() {
                public void repeaterPublished(JSONObject value) throws RepositoryException {
                    Assert.assertTrue(active);
                    if (failCapture) throw new RepositoryException("capture failed");
                    events++;
                }
                public void targetRemoved(String type, String id) {
                    Assert.assertTrue(active);
                    Assert.assertEquals(type, "repeater");
                    reversals++;
                }
            };
            inject(v3, "contents", contents);
            inject(v3, "requests", requests);
            inject(v3, "moderationService", moderation);
            inject(v3, "capture", capture);
            inject(v3, "users", new UserRepository() {
                public List<JSONObject> select(String sql, Object... args) {
                    Assert.assertTrue(active);
                    return List.of(new JSONObject().put("oId", "1").put("userStatus", 0));
                }
            });
            inject(classic, "repeaterContentRepository", contents);
            inject(classic, "moderationService", moderation);
            inject(classic, "professionSourceEventCaptureService", capture);
            inject(classic, "repeaterQueryService", new RepeaterQueryService() {
                public void fillViewFields(JSONObject item, String user) { }
            });
            inject(classic, "operationRepository", new OperationRepository() {
                public String add(JSONObject operation) throws RepositoryException {
                    Assert.assertTrue(active);
                    if (failAudit) throw new RepositoryException("audit failed");
                    return "456";
                }
            });
        }

        Transaction begin() {
            Assert.assertFalse(active);
            active = true;
            begins++;
            final JSONObject savedRow = copy(row), savedReceipt = copy(receipt);
            final int savedEvents = events, savedReversals = reversals;
            return new Transaction() {
                public String getId() { return "memory"; }
                public boolean isActive() { return active; }
                public void commit() { Assert.assertTrue(active); active = false; commits++; }
                public void rollback() {
                    Assert.assertTrue(active);
                    active = false;
                    rollbacks++;
                    row = copy(savedRow);
                    receipt = copy(savedReceipt);
                    events = savedEvents;
                    reversals = savedReversals;
                }
            };
        }

        private static JSONObject copy(JSONObject value) { return value == null ? null : new JSONObject(value.toString()); }
    }
}
