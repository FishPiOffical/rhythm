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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.processor.bot.ChatRoomBot;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.PetActivityRepository;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3AppearanceCatalog;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.ChatMessageRestrictions;
import org.b3log.symphony.util.ChatMessageText;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 使用隔离 SQLite 和真实幂等事务；执法副作用及广播端口只计调用，不操作业务账号。 */
public class ApiV3ChatLawTest {
    @BeforeClass
    public void configure() {
        final var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
    }

    static class Fixture extends F {
        int executions, deliveries, restrictionsRead, saveAttempts, failures, petReads;
        String replacement;
        final DefaultFullHttpRequest raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "/api/v3/chat/messages");
        final Request request = new Request(null, raw);
        final ApiV3ChatPublicationService publication = new ApiV3ChatPublicationService() {
            protected long now() { return NOW; }

            @Override
            protected String renderChatHtml(final String content) { return content; }

            @Override
            protected void executeLawCommand(final Request incoming, final JSONObject actor, final String content) {
                Assert.assertFalse(active());
                Assert.assertFalse(ArticlePublicationCaches.active());
                Assert.assertSame(incoming, request);
                Assert.assertEquals(actor.getString("oId"), "1");
                Assert.assertTrue(DataModelService.hasPermission(actor.getString("userRole"), 3));
                Assert.assertTrue(ChatMessageText.isLawCommand(content));
                executions++;
            }
        };

        Fixture() throws Exception {
            raw.headers().set("X-Real-IP", "127.0.0.1");
            raw.headers().set("User-Agent", "chat-law-test");
            sql(db, "ALTER TABLE u ADD COLUMN userRole TEXT DEFAULT 'defaultRole'");
            sql(db, "ALTER TABLE u ADD COLUMN userName TEXT DEFAULT 'tester'");
            sql(db, "ALTER TABLE u ADD COLUMN userPhone TEXT DEFAULT 'test-phone'");
            sql(db, "ALTER TABLE u ADD COLUMN userLatestCmtTime BIGINT DEFAULT 0");
            sql(db, "CREATE TABLE receipt(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            sql(db, "CREATE TABLE chat(oId TEXT PRIMARY KEY,content TEXT)");
            final var requests = new ApiV3RequestRepository() {
                public Transaction beginTransaction() { return begin(); }
                public boolean hasTransactionBegun() { return active(); }
                public JSONObject getForUpdate(final String id) throws RepositoryException {
                    final var found = selectSQL("SELECT * FROM receipt WHERE oId=? FOR UPDATE", id);
                    return found.isEmpty() ? null : found.getFirst();
                }
                public String add(final JSONObject row) throws RepositoryException {
                    sql(writer, "INSERT INTO receipt VALUES(?,?,?,?,?,?)", row.getString("oId"), row.getString("userId"),
                            row.getString("operation"), row.getString("requestHash"), row.getString("responseJson"), row.getLong("createdAt"));
                    return row.getString("oId");
                }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
                    sql(writer, "UPDATE receipt SET responseJson=? WHERE oId=?", row.getString("responseJson"), id);
                }
            };
            final var idempotency = new ApiV3IdempotencyMgmtService();
            inject(idempotency, "requestRepository", requests);
            inject(publication, "idempotency", idempotency);
            inject(publication, "users", new UserRepository() {
                public boolean hasTransactionBegun() { return active(); }
                public JSONObject getForUpdate(final String id) throws RepositoryException {
                    final var found = selectSQL("SELECT * FROM u WHERE oId=? FOR UPDATE", id);
                    return found.isEmpty() ? null : found.getFirst();
                }
                public void updateColumnsInCurrentTransaction(final String id, final JSONObject changes) throws RepositoryException {
                    Assert.assertTrue(active());
                    sql(writer, "UPDATE u SET userLatestCmtTime=? WHERE oId=?", changes.getLong("userLatestCmtTime"), id);
                }
                public void refreshSettingsCache(final String id) { Assert.assertFalse(active()); }
            });
            inject(publication, "points", new PointtransferMgmtService() {
                public <T> T executeWithTransferLocks(final String actor, final List<String> destinations,
                                                     final Callable<T> call) throws Exception { return call.call(); }
            });
            inject(publication, "validation", new ChatMessageValidationService() {
                public String prepare(final JSONObject actor, final String content) {
                    return replacement == null ? content.trim() : replacement;
                }
            });
            inject(publication, "restrictions", new ChatMessageRestrictionQueryService() {
                public ChatMessageRestrictions.State readInCurrentTransaction(final String actor) {
                    restrictionsRead++;
                    return new ChatMessageRestrictions.State(0, 0, 0);
                }
            });
            inject(publication, "cloud", new CloudService() {
                public String getEnabledMedal(final String actor) { return "[]"; }
            });
            inject(publication, "tails", new ApiV3ChatTailService() {
                public JSONObject snapshotInCurrentTransaction(final String actor, final String html) { return null; }
            });
            inject(publication, "messages", new ChatMessageMgmtService() {
                public JSONObject capturePetStatus(final String actor) {
                    Assert.assertFalse(active());
                    Assert.assertFalse(ArticlePublicationCaches.active());
                    petReads++;
                    return new JSONObject().put("petId", "80").put("name", "荷小豚").put("state", "idle");
                }

                public JSONObject saveInCurrentTransaction(final JSONObject envelope, final String actor) throws RepositoryException {
                    Assert.assertTrue(active());
                    saveAttempts++;
                    final var saved = new JSONObject(envelope.toString()).put("oId", "100");
                    sql(writer, "INSERT INTO chat VALUES(?,?)", "100", saved.toString());
                    if (failures-- > 0) throw new RepositoryException("injected storage failure");
                    return saved;
                }
            });
            inject(publication, "delivery", new ChatroomProcessor() {
                public void afterApiMessagePublished(final JSONObject message) {
                    Assert.assertFalse(active());
                    deliveries++;
                }
            });
        }

        JSONObject send(final String content, final String key) throws Exception {
            // 来自调用者的角色不能替代锁定行上的当前角色。
            return publication.publish(new JSONObject().put("oId", "1").put("userRole", Role.ROLE_ID_C_ADMIN),
                    content, "", key, request);
        }

        void role(final String role) throws Exception { sql(db, "UPDATE u SET userRole=? WHERE oId='1'", role); }
        int count(final String table) throws Exception { return rows(db, "SELECT COUNT(*) n FROM " + table).getFirst().getInt("n"); }

        @Override
        public void close() throws Exception {
            raw.release();
            super.close();
        }
    }

    @Test
    public void petSnapshotIsCapturedBeforeChatTransaction() throws Exception {
        try (final var f = new Fixture()) {
            f.send("宠物事务专项消息", "pet-chat-snapshot-test");
            Assert.assertEquals(f.petReads, 1);
            final var row = rows(f.db, "SELECT content FROM chat").getFirst();
            final var message = new JSONObject(row.getString("content"));
            Assert.assertEquals(message.getJSONObject("petStatus").getString("petId"), "80");
        }
    }

    @Test(timeOut = 5000)
    public void slowPetSnapshotDoesNotBlockTheCallerUntilTheQueryCompletes() throws Exception {
        final var service = new ChatMessageMgmtService();
        final var entered = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var caller = Thread.currentThread();
        inject(service, "pets", new UserPetRepository() {
            public List<JSONObject> select(final String query, final Object... args) throws RepositoryException {
                Assert.assertNotSame(Thread.currentThread(), caller);
                entered.countDown();
                try {
                    release.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RepositoryException(e);
                }
                return List.of();
            }
        });
        inject(service, "petActivities", new PetActivityRepository());
        try {
            Assert.assertNull(service.capturePetStatus("1"));
            Assert.assertTrue(entered.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test(timeOut = 60000)
    public void currentRoleControlsLawCommands() throws Exception {
        for (final String content : List.of("zf 帮助", "执法 帮助")) {
            try (final var f = new Fixture()) {
                error(403, () -> f.send(content, "denied"));
                Assert.assertEquals(f.executions, 0);
                Assert.assertEquals(f.deliveries, 0);
                Assert.assertEquals(f.count("chat"), 0);
                Assert.assertEquals(f.count("receipt"), 0);
                Assert.assertFalse(f.active());
            }
        }
        for (final String role : List.of(Role.ROLE_ID_C_ADMIN, "1630552921050", "1630631382235")) {
            try (final var f = new Fixture()) {
                f.role(role);
                f.send("zf 帮助", "allowed");
                Assert.assertEquals(f.executions, 1);
                Assert.assertEquals(f.restrictionsRead, 0);
            }
        }
    }

    @Test(timeOut = 60000)
    public void commitAndReplayExecuteOnlyOnce() throws Exception {
        try (final var f = new Fixture()) {
            f.role(Role.ROLE_ID_C_ADMIN);
            final var first = f.send("  执法 帮助  ", "same");
            Assert.assertTrue(first.similar(f.send("  执法 帮助  ", "same")));
            error(409, () -> f.send("zf 帮助", "same"));
            Assert.assertEquals(f.executions, 1);
            Assert.assertEquals(f.deliveries, 1);
            Assert.assertEquals(f.count("chat"), 1);
            Assert.assertEquals(f.count("receipt"), 1);
        }
    }

    @Test(timeOut = 60000)
    public void retriesAndRollbackNeverExecuteUncommittedCommands() throws Exception {
        try (final var f = new Fixture()) {
            f.role(Role.ROLE_ID_C_ADMIN);
            f.failures = 1;
            f.send("zf 帮助", "retry");
            Assert.assertEquals(f.saveAttempts, 2);
            Assert.assertEquals(f.executions, 1);
            Assert.assertEquals(f.count("chat"), 1);
        }
        try (final var f = new Fixture()) {
            f.role(Role.ROLE_ID_C_ADMIN);
            f.failures = 3;
            Assert.expectThrows(RepositoryException.class, () -> f.send("zf 帮助", "rollback"));
            Assert.assertEquals(f.executions, 0);
            Assert.assertEquals(f.deliveries, 0);
            Assert.assertEquals(f.count("chat"), 0);
            Assert.assertEquals(f.count("receipt"), 0);
            Assert.assertFalse(f.active());
        }
    }

    @Test(timeOut = 60000)
    public void ordinaryMessagesAndReservedEnvelopesKeepTheirBoundaries() throws Exception {
        try (final var f = new Fixture()) {
            f.send("普通正文 zf 帮助", "text");
            Assert.assertEquals(f.executions, 0);
            Assert.assertEquals(f.deliveries, 1);
            Assert.assertEquals(f.restrictionsRead, 1);
        }
        try (final var f = new Fixture()) {
            f.role(Role.ROLE_ID_C_ADMIN);
            for (final String content : List.of("[redpacket]{}[/redpacket]", "[music]x[/music]", "[weather]x[/weather]",
                    "[setdiscuss]x[/setdiscuss]", "[barrager]x[/barrager]", "{\"msgType\":\"redPacket\"}")) {
                error(422, () -> f.send(content, "reserved"));
            }
            f.replacement = "zf 帮助";
            error(422, () -> f.send("ordinary", "moderated"));
            Assert.assertEquals(f.executions, 0);
            Assert.assertEquals(f.count("chat"), 0);
        }
        Assert.assertFalse(ChatRoomBot.handleLawCommand(null, new JSONObject().put("userRole", "defaultRole"), "zf 帮助"));
        Assert.assertFalse(ChatRoomBot.handleLawCommand(null, new JSONObject().put("userRole", Role.ROLE_ID_C_ADMIN), "普通消息"));
    }

    @Test(timeOut = 60000)
    public void auditUsesAuthenticatedActorWithoutSession() throws Exception {
        try (final var f = new Fixture()) {
            final var operation = Operation.newOperation(f.request, Operation.OPERATION_CODE_C_DEDUCT_POINT, "transfer-id",
                    new JSONObject().put("oId", "1"));
            Assert.assertEquals(operation.getString(Operation.OPERATION_USER_ID), "1");
            Assert.assertEquals(operation.getString(Operation.OPERATION_DATA_ID), "transfer-id");
            Assert.assertEquals(operation.getString(Operation.OPERATION_UA), "chat-law-test");
        }
    }

    @Test(timeOut = 60000)
    public void everyMountUsesTheSameArrivalSuffix() {
        final var user = new JSONObject().put("oId", "1").put("userName", "tester").put("userNickname", "昵称").put("userStatus", 0);
        for (final var product : ApiV3AppearanceCatalog.PRODUCTS) {
            if (!"mount".equals(product.kind())) continue;
            final var notice = ApiV3RealtimeService.chatMountNotice(user, product, NOW);
            Assert.assertEquals(notice.getString("text"), "昵称" + product.verb() + product.name() + "来了");
            Assert.assertFalse(notice.getString("text").contains("进入了聊天室"));
        }
    }
}
