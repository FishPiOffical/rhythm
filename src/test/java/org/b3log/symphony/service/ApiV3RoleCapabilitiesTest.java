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
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.ChatRedPacketRepository;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.repository.ReactionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3UserCapabilities;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;
import org.testng.Assert;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 使用隔离 SQLite 验证真实撤回、保存点和幂等；不连接站点账号或发送线上消息。 */
public class ApiV3RoleCapabilitiesTest {
    static class Fixture extends F {
        int delivered;
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
            sql(db, "CREATE TABLE receipt(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            sql(db, "CREATE TABLE chat(oId TEXT PRIMARY KEY,content TEXT)");
            sql(db, "CREATE TABLE cloud(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");
            sql(db, "CREATE TABLE ledger(oId TEXT PRIMARY KEY,amount INT)");
            sql(db, "CREATE TABLE events(oId TEXT PRIMARY KEY)");
            sql(db, "CREATE TABLE reaction(oId TEXT PRIMARY KEY)");
            sql(db, "CREATE TABLE reversals(oId TEXT PRIMARY KEY)");
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
                }
            });
            inject(withdrawals, "messages", messages);
        }

        void role(final String role) throws Exception { sql(db, "UPDATE u SET userRole=? WHERE oId='1'", role); }
        void message(final String id, final String author, final Object content) throws Exception {
            sql(db, "INSERT INTO chat VALUES(?,?)", id, new JSONObject().put("userOId", author).put("content", content).toString());
            sql(db, "INSERT INTO reaction VALUES(?)", id);
        }
        JSONObject identity(final String client) {
            // 故意携带过时的管理员角色，验证数据库降权后不会被旧令牌快照覆盖。
            return new JSONObject().put("clientId", client).put("user", new JSONObject().put("oId", "1").put("userRole", Role.ROLE_ID_C_ADMIN));
        }
        JSONObject withdraw(final String client, final String id, final String key) throws Exception {
            return withdrawals.withdrawAuthenticated(identity(client), id, key);
        }
        int count(final String table) throws Exception { return rows(db, "SELECT COUNT(*) n FROM " + table).getFirst().getInt("n"); }
        int points() throws Exception { return rows(db, "SELECT userPoint FROM u WHERE oId='1'").getFirst().getInt("userPoint"); }
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
                Assert.assertEquals(f.delivered, 1);
                // 同账号同键只恢复已提交元数据，不因换客户端或随后降权重做管理操作。
                f.role(Role.ROLE_ID_C_DEFAULT);
                Assert.assertEquals(f.withdraw("third", "100", "moderate-original").toString(), receipt.toString());
                Assert.assertEquals(f.delivered, 1);
                Assert.assertEquals(f.count("events"), 1);
                f.message("101", "2", "未撤回的正文");
                error(404, () -> f.withdraw("website_v3", "101", "after-demotion"));
                Assert.assertEquals(f.count("chat"), 1);
            }
        }
        for (final String role : List.of(Role.ROLE_ID_C_ADMIN, Role.ROLE_ID_C_OP, Role.ROLE_ID_C_DISCIPLINE_COMMITTEE)) {
            try (final var f = new Fixture()) {
                f.role(role);
                f.message("100", "2", "其他用户的正文");
                error(404, () -> f.withdraw("third", "100", "third-new"));
                Assert.assertEquals(f.count("chat"), 1);
                Assert.assertEquals(f.count("receipt"), 0);
                Assert.assertEquals(f.delivered, 0);
                f.message("101", "1", "自己的正文");
                Assert.assertEquals(f.withdraw("third", "101", "third-own").getInt("cost"), 0);
            }
        }
        try (final var f = new Fixture()) {
            f.message("100", "2", "不可撤回他人");
            error(404, () -> f.withdraw("website_v3", "100", "normal-others"));
            f.message("101", "1", "第一次本人撤回");
            f.message("102", "1", "第二次本人撤回");
            Assert.assertEquals(f.withdraw("website_v3", "101", "first-own").getInt("cost"), 0);
            Assert.assertEquals(f.withdraw("third", "102", "second-own").getInt("cost"), 32);
            Assert.assertEquals(f.points(), 968);
            Assert.assertEquals(f.count("ledger"), 2);
            Assert.assertEquals(f.count("cloud"), 1);
        }
        try (final var f = new Fixture()) {
            f.role(Role.ROLE_ID_C_SUPER_MEMBER);
            f.message("100", "2", "超级会员没有管理撤回权限");
            error(404, () -> f.withdraw("website_v3", "100", "super-member"));
        }
        try (final var f = new Fixture()) {
            f.role(Role.ROLE_ID_C_ADMIN);
            f.message("100", "2", new JSONObject().put("msgType", "redPacket"));
            error(409, () -> f.withdraw("website_v3", "100", "red-packet"));
            Assert.assertEquals(f.count("chat"), 1);
            Assert.assertEquals(f.count("reaction"), 1);
            Assert.assertEquals(f.count("receipt"), 0);
            Assert.assertEquals(f.delivered, 0);
        }
        try (final var f = new Fixture()) {
            f.message("100", "1", "提交失败不可部分撤回");
            f.fault = "commit";
            try { f.withdraw("website_v3", "100", "rollback"); Assert.fail("应返回提交失败"); }
            catch (final RuntimeException expected) { /* 数据库提交故障是本测试注入的预期结果。 */ }
            f.fault = "";
            Assert.assertEquals(f.count("chat"), 1);
            Assert.assertEquals(f.count("reaction"), 1);
            for (final String table : List.of("receipt", "ledger", "cloud", "events", "reversals")) Assert.assertEquals(f.count(table), 0);
            Assert.assertEquals(f.points(), 1000);
            Assert.assertEquals(f.delivered, 0);
        }
        System.out.println("PASS：角色能力、第一方管理撤回、第三方隔离、降权、原键恢复、费用、红包与事务回滚");
    }
}
