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
import org.b3log.latke.repository.jdbc.mapping.StringMapping;
import org.b3log.latke.repository.jdbc.util.FieldDefinition;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.ChatRedPacketRepository;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.repository.LogsRepository;
import org.b3log.symphony.repository.ReactionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3UserCapabilities;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;
import org.testng.Assert;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 使用隔离 SQLite 验证真实撤回、保存点和幂等；不连接站点账号或发送线上消息。 */
public class ApiV3RoleCapabilitiesTest {
    static class Fixture extends F {
        int delivered;
        int auditAttempts;
        int lastTransferAmount;
        final ChatMessageWithdrawalMgmtService withdrawals = new ChatMessageWithdrawalMgmtService() {
            protected long now() { return NOW; }
            protected void afterWithdrawn(final Result result) {
                Assert.assertFalse(active());
                Assert.assertFalse(ArticlePublicationCaches.active());
                delivered++;
            }
        };

        Fixture() throws Exception {
            sql(db, "ALTER TABLE u ADD COLUMN userRole TEXT DEFAULT 'defaultRole'");
            sql(db, "ALTER TABLE u ADD COLUMN userPoint INT DEFAULT 1000");
            sql(db, "ALTER TABLE u ADD COLUMN userName TEXT DEFAULT 'current-operator'");
            sql(db, "CREATE TABLE receipt(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            sql(db, "CREATE TABLE chat(oId TEXT PRIMARY KEY,content TEXT)");
            sql(db, "CREATE TABLE cloud(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");
            sql(db, "CREATE TABLE ledger(oId TEXT PRIMARY KEY,amount INT)");
            sql(db, "CREATE TABLE events(oId TEXT PRIMARY KEY)");
            sql(db, "CREATE TABLE reaction(oId TEXT PRIMARY KEY)");
            sql(db, "CREATE TABLE reversals(oId TEXT PRIMARY KEY)");
            sql(db, "CREATE TABLE logs(type TEXT,key1 TEXT,key2 TEXT,key3 TEXT,data TEXT,public INT)");
            final var logs = new LogsService();
            inject(logs, "logsRepository", new LogsRepository() {
                public boolean hasTransactionBegun() { return active(); }
                public String add(final JSONObject row) throws RepositoryException {
                    Assert.assertTrue(active());
                    Assert.assertTrue(ArticlePublicationCaches.active());
                    Assert.assertEquals(row.keySet(), Set.of("type", "key1", "key2", "key3", "data", "public"));
                    // 保留生产六参数组装，并确认写审计时锁定消息还未删除。
                    final var message = selectSQL("SELECT content FROM chat WHERE oId=?", row.getString("key3"));
                    Assert.assertEquals(message.size(), 1);
                    Assert.assertEquals(new JSONObject(row.getString("data")).getString("content"),
                            message.getFirst().getString("content"));
                    auditAttempts++;
                    sql(writer, "INSERT INTO logs VALUES(?,?,?,?,?,?)", row.getString("type"), row.getString("key1"),
                            row.getString("key2"), row.getString("key3"), row.getString("data"), row.getBoolean("public"));
                    if (fault.equals("audit")) throw new RepositoryException("测试注入审计写入失败");
                    return row.getString("key3");
                }
            });
            inject(withdrawals, "logs", logs);
            final var requests = new ApiV3RequestRepository() {
                public Transaction beginTransaction() { return ApiV3Transactions.strict(begin()); }
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
            inject(withdrawals, "idempotency", idempotency);
            inject(withdrawals, "users", new UserRepository() {
                public Transaction beginTransaction() { return ApiV3Transactions.strict(begin()); }
                public boolean hasTransactionBegun() { return active(); }
                public JSONObject getForUpdate(final String id) throws RepositoryException {
                    final var found = selectSQL("SELECT * FROM u WHERE oId=? FOR UPDATE", id);
                    return found.isEmpty() ? null : found.getFirst();
                }
                public void refreshSettingsCache(final String id) { Assert.assertFalse(active()); }
            });
            inject(withdrawals, "cloud", new CloudRepository() {
                public String getName() { return "cloud"; }
                public List<JSONObject> select(final String query, final Object... args) throws RepositoryException {
                    return selectSQL(query, args);
                }
                public String add(final JSONObject row) throws RepositoryException {
                    sql(writer, "INSERT INTO cloud VALUES('counter',?,?,?)", row.getString("userId"), row.getString("gameId"), row.getString("data"));
                    return "counter";
                }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
                    sql(writer, "UPDATE cloud SET data=? WHERE oId=?", row.getString("data"), id);
                }
            });
            inject(withdrawals, "points", new PointtransferMgmtService() {
                public String transferInCurrentTransaction(final String from, final String to, final int type,
                        final int sum, final String dataId, final long time, final String memo,
                        final PointtransferSource source, final boolean indexAsync) {
                    try {
                        Assert.assertTrue(active());
                        final String id = Integer.toString(selectSQL("SELECT * FROM ledger").size() + 1);
                        sql(writer, "UPDATE u SET userPoint=userPoint-? WHERE oId=?", sum, from);
                        sql(writer, "INSERT INTO ledger VALUES(?,?)", id, sum);
                        lastTransferAmount = sum;
                        return id;
                    } catch (final RepositoryException error) { throw new IllegalStateException(error); }
                }
            });
            // 保留真实消息删除核心，数据库端口以本次测试事务记录删除、表态清理和职业撤销。
            final var messages = new ChatMessageMgmtService();
            inject(messages, "messages", new ChatRoomRepository() {
                public String getName() { return "chat"; }
                public boolean hasTransactionBegun() { return active(); }
                public List<JSONObject> select(final String query, final Object... args) throws RepositoryException {
                    return selectSQL(query, args);
                }
                public void remove(final String id) throws RepositoryException {
                    sql(writer, "DELETE FROM chat WHERE oId=?", id);
                    sql(writer, "INSERT INTO events VALUES(?)", id);
                }
            });
            inject(messages, "redPackets", new ChatRedPacketRepository() {
                public void requireSettledForRemoval(final String id) { Assert.assertTrue(active()); }
            });
            inject(messages, "reactions", new ReactionRepository() {
                public void removeTargetInCurrentTransaction(final String type, final String id) throws RepositoryException {
                    sql(writer, "DELETE FROM reaction WHERE oId=?", id);
                }
            });
            inject(messages, "professions", new ProfessionSourceEventCaptureService() {
                public void targetRemoved(final String type, final String id) throws RepositoryException {
                    sql(writer, "INSERT INTO reversals VALUES(?)", id);
                    if (fault.equals("removal")) throw new RepositoryException("测试注入后续删除链失败");
                }
            });
            inject(withdrawals, "messages", messages);
        }

        void role(final String role) throws Exception { sql(db, "UPDATE u SET userRole=? WHERE oId='1'", role); }
        void message(final String id, final String author, final Object content) throws Exception {
            rawMessage(id, new JSONObject().put("userOId", author).put("content", content).toString());
        }
        void rawMessage(final String id, final String content) throws Exception {
            sql(db, "INSERT INTO chat VALUES(?,?)", id, content);
            sql(db, "INSERT INTO reaction VALUES(?)", id);
        }
        JSONObject identity(final String client) {
            // 故意携带过时的管理员角色，验证数据库降权后不会被旧令牌快照覆盖。
            return new JSONObject().put("clientId", client).put("user", new JSONObject().put("oId", "1")
                    .put("userRole", Role.ROLE_ID_C_ADMIN).put("userName", "stale-operator"));
        }
        JSONObject withdraw(final String client, final String id, final String key) throws Exception {
            return withdrawals.withdrawAuthenticated(identity(client), id, key);
        }
        int count(final String table) throws Exception { return rows(db, "SELECT COUNT(*) n FROM " + table).getFirst().getInt("n"); }
        int points() throws Exception { return rows(db, "SELECT userPoint FROM u WHERE oId='1'").getFirst().getInt("userPoint"); }
        void audit(final String id, final String raw, final String address) throws Exception {
            final var found = rows(db, "SELECT * FROM logs WHERE type='cr-revoke' AND key3=?", id);
            Assert.assertEquals(found.size(), 1);
            final var row = found.getFirst();
            Assert.assertEquals(row.getString("key2"), address);
            Assert.assertTrue(row.getString("key1").matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"));
            Assert.assertEquals(row.getInt("public"), 0);
            final var data = new JSONObject(row.getString("data"));
            Assert.assertEquals(data.keySet(), Set.of("oId", "content", "actorId", "actorName"));
            Assert.assertEquals(data.getString("oId"), id);
            Assert.assertEquals(data.getString("content"), raw);
            Assert.assertEquals(data.getString("actorId"), "1");
            Assert.assertEquals(data.getString("actorName"), "current-operator");
        }
        void rolledBack() throws Exception {
            Assert.assertEquals(count("chat"), 1);
            Assert.assertEquals(count("reaction"), 1);
            for (final String table : List.of("receipt", "ledger", "cloud", "events", "reversals", "logs")) {
                Assert.assertEquals(count(table), 0, table + " 必须随撤回整体回滚");
            }
            Assert.assertEquals(points(), 1000);
            Assert.assertEquals(delivered, 0);
        }
    }

    private static void capabilities() {
        final Set<String> permissions = Set.of(Permission.PERMISSION_ID_C_COMMON_VIEW_ARTICLE_HISTORY,
                Permission.PERMISSION_ID_C_COMMON_VIEW_COMMENT_HISTORY, Permission.PERMISSION_ID_C_MENU_ADMIN);
        final var website = new JSONObject().put("clientId", ApiV3TokenMgmtService.WEBSITE_CLIENT);
        for (final String role : List.of(Role.ROLE_ID_C_ADMIN, Role.ROLE_ID_C_OP, Role.ROLE_ID_C_DISCIPLINE_COMMITTEE,
                Role.ROLE_ID_C_DEFAULT, Role.ROLE_ID_C_MEMBER, Role.ROLE_ID_C_INTRODUCTION_MEMBER,
                Role.ROLE_ID_C_SUPER_MEMBER, Role.ROLE_ID_C_VISITOR)) {
            final var user = new JSONObject().put("userRole", role);
            final var result = ApiV3UserCapabilities.project(user, website, permissions);
            Assert.assertEquals(result.keySet(), Set.of("chatRevokeOthers", "viewArticleHistory", "viewCommentHistory", "canAccessAdmin"));
            Assert.assertEquals(result.getBoolean("chatRevokeOthers"),
                    Set.of(Role.ROLE_ID_C_ADMIN, Role.ROLE_ID_C_OP, Role.ROLE_ID_C_DISCIPLINE_COMMITTEE).contains(role));
            Assert.assertTrue(result.getBoolean("viewArticleHistory"));
            Assert.assertTrue(result.getBoolean("viewCommentHistory"));
            Assert.assertTrue(result.getBoolean("canAccessAdmin"));
            final var revoked = ApiV3UserCapabilities.project(user, website, Set.of());
            Assert.assertFalse(revoked.getBoolean("viewArticleHistory"));
            Assert.assertFalse(revoked.getBoolean("viewCommentHistory"));
            Assert.assertFalse(revoked.getBoolean("canAccessAdmin"));
            final var thirdParty = ApiV3UserCapabilities.project(user, new JSONObject().put("clientId", "third"), permissions);
            for (final String key : thirdParty.keySet()) Assert.assertFalse(thirdParty.getBoolean(key));
        }
        final var anonymous = ApiV3UserCapabilities.project(null, null, Set.of());
        for (final String key : anonymous.keySet()) Assert.assertFalse(anonymous.getBoolean(key));
    }

    public static void main(final String[] args) throws Exception {
        final var properties = new Properties();
        properties.setProperty("runtimeDatabase", "NONE");
        properties.setProperty("contextPath", "");
        Latkes.setLatkeProps(properties);
        Latkes.setLocalProps(properties);
        capabilities();
        for (final String role : List.of(Role.ROLE_ID_C_ADMIN, Role.ROLE_ID_C_OP, Role.ROLE_ID_C_DISCIPLINE_COMMITTEE)) {
            try (final var f = new Fixture()) {
                f.role(role);
                f.message("100", "2", "其他用户的正文");
                final var receipt = f.withdraw("website_v3", "100", "moderate-original");
                Assert.assertEquals(receipt.keySet(), Set.of("id", "removed", "cost"));
                Assert.assertEquals(receipt.getInt("cost"), 0);
                Assert.assertEquals(f.count("chat"), 0);
                Assert.assertEquals(f.count("reaction"), 0);
                Assert.assertEquals(f.count("events"), 1);
                Assert.assertEquals(f.count("reversals"), 1);
                Assert.assertEquals(f.count("cloud"), 0);
                Assert.assertEquals(f.count("ledger"), 0);
                Assert.assertEquals(f.count("logs"), 1);
                f.audit("100", new JSONObject().put("userOId", "2").put("content", "其他用户的正文").toString(), "");
                Assert.assertEquals(f.delivered, 1);
                // 同账号同键只恢复已提交元数据，不因换客户端或随后降权重做管理操作。
                f.role(Role.ROLE_ID_C_DEFAULT);
                Assert.assertEquals(f.withdraw("third", "100", "moderate-original").toString(), receipt.toString());
                Assert.assertEquals(f.delivered, 1);
                Assert.assertEquals(f.count("events"), 1);
                Assert.assertEquals(f.count("logs"), 1);
                Assert.assertEquals(f.auditAttempts, 1);
                f.message("101", "2", "未撤回的正文");
                error(404, () -> f.withdraw("website_v3", "101", "after-demotion"));
                Assert.assertEquals(f.count("chat"), 1);
                Assert.assertEquals(f.count("logs"), 1);
            }
        }
        for (final String role : List.of(Role.ROLE_ID_C_ADMIN, Role.ROLE_ID_C_OP, Role.ROLE_ID_C_DISCIPLINE_COMMITTEE)) {
            try (final var f = new Fixture()) {
                f.role(role);
                f.message("100", "2", "其他用户的正文");
                error(404, () -> f.withdraw("third", "100", "third-new"));
                Assert.assertEquals(f.count("chat"), 1);
                Assert.assertEquals(f.count("receipt"), 0);
                Assert.assertEquals(f.count("logs"), 0);
                Assert.assertEquals(f.auditAttempts, 0);
                Assert.assertEquals(f.delivered, 0);
                f.message("101", "1", "自己的正文");
                Assert.assertEquals(f.withdraw("third", "101", "third-own").getInt("cost"), 0);
            }
        }
        try (final var f = new Fixture()) {
            f.message("100", "2", "不可撤回他人");
            error(404, () -> f.withdraw("website_v3", "100", "normal-others"));
            Assert.assertEquals(f.count("logs"), 0);
            Assert.assertEquals(f.auditAttempts, 0);
            f.message("101", "1", "第一次本人撤回");
            f.message("102", "1", "第二次本人撤回");
            Assert.assertEquals(f.withdraw("website_v3", "101", "first-own").getInt("cost"), 0);
            Assert.assertEquals(f.withdraw("third", "102", "second-own").getInt("cost"), 32);
            Assert.assertEquals(f.points(), 968);
            Assert.assertEquals(f.count("ledger"), 2);
            Assert.assertEquals(f.count("cloud"), 1);
            Assert.assertEquals(f.count("logs"), 2);
        }
        try (final var f = new Fixture()) {
            f.role(Role.ROLE_ID_C_SUPER_MEMBER);
            f.message("100", "2", "超级会员没有管理撤回权限");
            error(404, () -> f.withdraw("website_v3", "100", "super-member"));
            Assert.assertEquals(f.count("logs"), 0);
            Assert.assertEquals(f.auditAttempts, 0);
        }
        try (final var f = new Fixture()) {
            f.role(Role.ROLE_ID_C_ADMIN);
            f.message("100", "2", new JSONObject().put("msgType", "redPacket"));
            error(409, () -> f.withdraw("website_v3", "100", "red-packet"));
            Assert.assertEquals(f.count("chat"), 1);
            Assert.assertEquals(f.count("reaction"), 1);
            Assert.assertEquals(f.count("receipt"), 0);
            Assert.assertEquals(f.count("logs"), 0);
            Assert.assertEquals(f.auditAttempts, 0);
            Assert.assertEquals(f.delivered, 0);
        }
        auditSnapshots();
        auditCapacity();
        auditRejections();
        chargedAuditRollback();
        for (final boolean legacy : List.of(false, true)) {
            for (final String fault : List.of("audit", "removal", "commit")) {
                try (final var f = new Fixture()) {
                    f.message("100", "1", "失败不可部分撤回");
                    f.fault = fault;
                    try {
                        if (legacy) f.withdrawals.withdrawLegacy("1", "100", "192.0.2.*");
                        else f.withdrawals.withdrawAuthenticated(f.identity("website_v3"), "100", "rollback", "192.0.2.*");
                        Assert.fail("应返回注入故障：" + fault);
                    } catch (final RepositoryException | RuntimeException expected) {
                        // 审计、删除链及数据库提交故障均须回滚整个撤回事务。
                    }
                    f.fault = "";
                    Assert.assertTrue(f.auditAttempts > 0);
                    f.rolledBack();
                    if (legacy) f.withdrawals.withdrawLegacy("1", "100", "192.0.2.*");
                    else f.withdrawals.withdrawAuthenticated(f.identity("website_v3"), "100", "rollback", "192.0.2.*");
                    Assert.assertEquals(f.count("chat"), 0);
                    Assert.assertEquals(f.count("logs"), 1);
                    Assert.assertEquals(f.count("events"), 1);
                    Assert.assertEquals(f.delivered, 1);
                }
            }
        }
        System.out.println("PASS：角色能力、第一方管理撤回、第三方隔离、降权、原键恢复、费用、红包、精确私有审计与事务回滚");
    }

    private static void auditSnapshots() throws Exception {
        // 外壳的空白、转义和作者快照必须原样保留，不能解析后重新序列化或改写为操作者。
        final String raw = " \n{ \"content\" : \"  **原正文**\\n<script>文本</script>\\t尾部  \","
                + " \"userName\":\"message-author\", \"userOId\" : \"2\" }\r\n";
        for (final boolean legacy : List.of(false, true)) {
            try (final var f = new Fixture()) {
                f.role(Role.ROLE_ID_C_OP);
                f.rawMessage("100", raw);
                if (legacy) f.withdrawals.withdrawLegacy("1", "100", "192.0.2.*");
                else {
                    final var receipt = f.withdrawals.withdrawAuthenticated(f.identity("website_v3"), "100", "snapshot", "192.0.2.*");
                    Assert.assertEquals(receipt.keySet(), Set.of("id", "removed", "cost"));
                    final var replay = f.withdrawals.withdrawAuthenticated(f.identity("website_v3"), "100", "snapshot", "198.51.100.*");
                    Assert.assertEquals(replay.toString(), receipt.toString());
                }
                Assert.assertEquals(f.count("chat"), 0);
                Assert.assertEquals(f.count("logs"), 1);
                Assert.assertEquals(f.auditAttempts, 1);
                Assert.assertEquals(f.delivered, 1);
                f.audit("100", raw, "192.0.2.*");
            }
        }
        try (final var f = new Fixture()) {
            f.role(Role.ROLE_ID_C_ADMIN);
            f.rawMessage("100", raw);
            f.withdrawals.withdrawLegacy("1", "100");
            f.audit("100", raw, "");
        }
    }

    private static void auditRejections() throws Exception {
        for (final boolean legacy : List.of(false, true)) {
            try (final var f = new Fixture()) {
                f.message("100", "2", "不能越权记录其他用户的正文");
                if (legacy) error(404, () -> f.withdrawals.withdrawLegacy("1", "100", "192.0.2.*"));
                else error(401, () -> f.withdrawals.withdrawAuthenticated(null, "100", "missing-identity", "192.0.2.*"));
                Assert.assertEquals(f.auditAttempts, 0);
                f.rolledBack();
            }
            try (final var f = new Fixture()) {
                f.role(Role.ROLE_ID_C_ADMIN);
                f.message("100", "2", new JSONObject().put("msgType", "redPacket"));
                if (legacy) error(409, () -> f.withdrawals.withdrawLegacy("1", "100", "192.0.2.*"));
                else error(409, () -> f.withdrawals.withdrawAuthenticated(f.identity("website_v3"), "100", "packet-audit", "192.0.2.*"));
                Assert.assertEquals(f.auditAttempts, 0);
                f.rolledBack();
            }
        }
    }

    private static void auditCapacity() throws Exception {
        // 使用项目实际声明与捆绑 Latke 映射，避免 SQLite 的 TEXT 掩盖 MySQL 列容量差异。
        JSONObject dataDefinition = null;
        try (final var resource = ApiV3RoleCapabilitiesTest.class.getResourceAsStream("/repository.json")) {
            Assert.assertNotNull(resource);
            final var schema = new JSONObject(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            for (final Object repository : schema.getJSONArray("repositories")) {
                final var definition = (JSONObject) repository;
                if (!"logs".equals(definition.getString("name"))) continue;
                for (final Object key : definition.getJSONArray("keys")) {
                    final var field = (JSONObject) key;
                    if ("data".equals(field.getString("name"))) dataDefinition = field;
                }
            }
        }
        Assert.assertNotNull(dataDefinition);
        final var field = new FieldDefinition();
        field.setName(dataDefinition.getString("name"));
        field.setLength(dataDefinition.getInt("length"));
        field.setNullable(dataDefinition.optBoolean("nullable"));
        final String column = new StringMapping().toDataBaseString(field);
        Assert.assertTrue(column.contains(" MEDIUMTEXT"));
        Assert.assertTrue(column.endsWith(" NOT NULL"));
        try (final var f = new Fixture()) {
            final String raw = new JSONObject().put("userOId", "1").put("content", "中文".repeat(12000)).toString();
            Assert.assertTrue(raw.length() < 65535);
            Assert.assertTrue(raw.getBytes(StandardCharsets.UTF_8).length > 65535);
            f.rawMessage("100", raw);
            Assert.assertEquals(f.withdraw("website_v3", "100", "large-audit").getInt("cost"), 0);
            Assert.assertEquals(f.count("chat"), 0);
            Assert.assertEquals(f.count("logs"), 1);
            f.audit("100", raw, "");
            final String stored = rows(f.db, "SELECT data FROM logs").getFirst().getString("data");
            Assert.assertTrue(stored.getBytes(StandardCharsets.UTF_8).length > 65535);
        }
    }

    private static void chargedAuditRollback() throws Exception {
        for (final boolean legacy : List.of(false, true)) {
            for (final String fault : List.of("audit", "removal", "commit")) {
                try (final var f = new Fixture()) {
                    f.message("100", "1", "已成功撤回的第一条消息");
                    Assert.assertEquals(f.withdraw("website_v3", "100", "charged-first").getInt("cost"), 0);
                    f.message("101", "1", "第二次撤回需要扣除32积分");
                    // 保留首笔审计、计次和回执的精确快照，故障不能污染已提交数据。
                    final var baseline = new JSONObject();
                    for (final String table : List.of("receipt", "ledger", "cloud", "events", "reversals", "logs", "chat", "reaction")) {
                        baseline.put(table, rows(f.db, "SELECT * FROM " + table).toString());
                    }
                    f.fault = fault;
                    try {
                        if (legacy) f.withdrawals.withdrawLegacy("1", "101", "192.0.2.*");
                        else f.withdrawals.withdrawAuthenticated(f.identity("website_v3"), "101", "charged-second", "192.0.2.*");
                        Assert.fail("收费撤回应返回注入故障：" + fault);
                    } catch (final RepositoryException | RuntimeException expected) {
                        /* 数据库提交故障是本测试注入的预期结果。 */
                        // 审计及后续删除链故障同样必须撤销本次已经执行的32积分扣款。
                    }
                    f.fault = "";
                    Assert.assertEquals(f.lastTransferAmount, 32);
                    Assert.assertTrue(f.auditAttempts > 1);
                    Assert.assertEquals(f.points(), 1000);
                    Assert.assertEquals(f.delivered, 1);
                    for (final String table : baseline.keySet()) {
                        Assert.assertEquals(rows(f.db, "SELECT * FROM " + table).toString(), baseline.getString(table),
                                table + " 必须保留首笔结果且无第二笔残留");
                    }
                    if (legacy) Assert.assertEquals(f.withdrawals.withdrawLegacy("1", "101", "192.0.2.*").cost(), 32);
                    else {
                        final var receipt = f.withdrawals.withdrawAuthenticated(f.identity("website_v3"), "101", "charged-second", "192.0.2.*");
                        Assert.assertEquals(receipt.getInt("cost"), 32);
                        final int auditAttempts = f.auditAttempts;
                        Assert.assertEquals(f.withdraw("website_v3", "101", "charged-second").toString(), receipt.toString());
                        Assert.assertEquals(f.auditAttempts, auditAttempts);
                    }
                    Assert.assertEquals(f.points(), 968);
                    Assert.assertEquals(f.count("chat"), 0);
                    Assert.assertEquals(f.count("reaction"), 0);
                    Assert.assertEquals(f.count("receipt"), legacy ? 1 : 2);
                    for (final String table : List.of("logs", "ledger", "events", "reversals")) Assert.assertEquals(f.count(table), 2);
                    Assert.assertEquals(rows(f.db, "SELECT SUM(amount) amount FROM ledger").getFirst().getInt("amount"), 32);
                    Assert.assertEquals(new JSONObject(rows(f.db, "SELECT data FROM cloud").getFirst().getString("data")).getInt("count"), 2);
                    Assert.assertEquals(f.delivered, 2);
                }
            }
        }
    }
}
