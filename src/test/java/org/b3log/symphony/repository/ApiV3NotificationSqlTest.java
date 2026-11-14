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
package org.b3log.symphony.repository;

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.service.ApiV3IdempotencyMgmtService;
import org.b3log.symphony.service.ApiV3NotificationMgmtService;
import org.b3log.symphony.service.NotificationStateMgmtService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3NotificationDto;
import org.b3log.symphony.util.ApiV3NotificationTypes;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 在隔离的 SQLite 上执行生产 SQL。适配时移除 FOR UPDATE，MySQL 锁行为需另行验证。
 */
public class ApiV3NotificationSqlTest {
    @Test
    public void categoryMapIsDisjointAndAllCurrentTypesAreRepresented() throws Exception {
        final List<Integer> mapped = ApiV3NotificationTypes.CATEGORIES.values().stream().flatMap(List::stream).toList();
        Assert.assertEquals(Set.copyOf(mapped).size(), mapped.size());
        for (final var field : Notification.class.getFields()) {
            if (field.getName().startsWith("DATA_TYPE_C_"))
                Assert.assertNotEquals(ApiV3NotificationTypes.category(field.getInt(null)), "other");
        }
    }

    @Test
    public void actualSqlScopesOwnerStatusCountPaginationAndUnknownTypes() throws Exception {
        try (final Fixture f = fixture()) {
            f.seed("01", "a", 3, false);
            f.seed("02", "a", 6, true);
            f.seed("03", "a", 42, false);
            f.seed("04", "a", 999, false);
            f.seed("05", "b", 6, false);
            f.seed("06", "a", 38, false);
            Assert.assertEquals(f.notifications.countV3("a", "all", "all"), 5L);
            Assert.assertEquals(f.notifications.countV3("a", "point", "unread"), 1L);
            Assert.assertEquals(f.notifications.countV3("a", "point", "read"), 1L);
            Assert.assertEquals(f.notifications.countV3("a", "other", "all"), 1L);
            Assert.assertEquals(f.notifications.getV3Page("a", "all", "all", 1, 2).get(0).getString("oId"), "06");
            Assert.assertEquals(f.notifications.getV3Page("a", "all", "all", 3, 2).get(0).getString("oId"), "02");
            Assert.assertTrue(f.notifications.getV3Page("a", "all", "all", 4, 2).isEmpty());
            Assert.assertNull(f.notifications.getOwned("a", "05"));
            Assert.assertEquals(f.notifications.countV3("a' OR 1=1 --", "all", "all"), 0L);
            final long unread = f.notifications.countV3Unread("a").stream().mapToLong(row -> row.optLong("total")).sum();
            Assert.assertEquals(unread, 4L);
        }
    }

    @Test
    public void ownedMarkDeleteAndReplayCannotTouchAnotherUser() throws Exception {
        try (final Fixture f = fixture()) {
            f.seed("01", "a", 3, false);
            f.seed("02", "b", 3, false);
            Assert.assertEquals(f.api.mark("a", "01", "read").getLong("changed"), 1L);
            Assert.assertEquals(f.api.mark("a", "01", "read").getLong("changed"), 1L);
            Assert.assertEquals(f.api.mark("a", "01", "another-read").getLong("changed"), 0L);
            failure(404, () -> f.api.mark("a", "02", "other-read"));
            failure(404, () -> f.api.remove("a", "02", "other-delete"));
            Assert.assertFalse(f.notifications.getOwned("b", "02").getBoolean("hasRead"));
            f.api.remove("a", "01", "delete");
            f.api.remove("a", "01", "delete");
            Assert.assertNull(f.notifications.getOwned("a", "01"));
        }
    }

    @Test
    public void bulkTraversesMoreThanOneBatchAndReplaysDoNotAffectNewNotifications() throws Exception {
        try (final Fixture f = fixture()) {
            for (int i = 1; i <= 605; i++) f.seed(String.format("%05d", i), "a", 3, false);
            f.seed("99990", "b", 3, false);
            f.seed("99991", "a", 6, false);
            Assert.assertEquals(f.api.bulk("a", "commented", false, "read-all").getLong("changed"), 605L);
            Assert.assertEquals(f.notifications.countV3("a", "commented", "unread"), 0L);
            f.seed("99992", "a", 3, false);
            Assert.assertEquals(f.api.bulk("a", "commented", false, "read-all").getLong("changed"), 605L);
            Assert.assertEquals(f.notifications.countV3("a", "commented", "unread"), 1L);
            Assert.assertEquals(f.api.bulk("a", "commented", true, "delete-category").getLong("changed"), 606L);
            Assert.assertEquals(f.notifications.countV3("a", "point", "unread"), 1L);
            Assert.assertEquals(f.notifications.countV3("b", "commented", "unread"), 1L);
        }
    }

    @Test
    public void resultFailureRollsBackStateAndReplayRowInTheSameDatabaseTransaction() throws Exception {
        try (final Fixture f = fixture()) {
            f.seed("01", "a", 3, false);
            f.failResults = true;
            try {
                f.api.mark("a", "01", "key");
                Assert.fail("Expected persistence failure");
            } catch (final RepositoryException expected) {
            }
            Assert.assertFalse(f.notifications.getOwned("a", "01").getBoolean("hasRead"));
            Assert.assertEquals(f.select("SELECT COUNT(1) AS total FROM requests").get(0).getLong("total"), 0L);
            f.failResults = false;
            Assert.assertEquals(f.api.mark("a", "01", "key").getLong("changed"), 1L);
        }
    }

    @Test
    public void unknownGroupAndCutoffUseTheSameSelectionAsTheList() throws Exception {
        try (final Fixture f = fixture()) {
            f.seed("01", "a", 999, false);
            f.seed("02", "a", 3, false);
            final Transaction transaction = f.begin();
            final String cutoff = f.notifications.getV3Cutoff("a", "other", true);
            f.seed("03", "a", 998, false);
            Assert.assertEquals(f.notifications.getV3BatchForUpdate("a", "other", true, cutoff).size(), 1);
            transaction.rollback();
            Assert.assertEquals(f.api.bulk("a", "other", false, "other").getLong("changed"), 1L);
            failure(422, () -> f.api.bulk("a", "all", true, "unsupported-delete-all"));
        }
    }

    @Test
    public void rawDtoDoesNotLeakOwnerReferenceBodyOrKeysAndUnavailableClearsTargets() {
        final JSONObject raw = new JSONObject().put("oId", "123").put("userId", "private-owner").put("dataId", "private-reference")
                .put("content", "private-content").put("apiKey", "private-key").put("dataType", 41);
        final JSONObject dto = ApiV3NotificationDto.base(raw);
        Assert.assertFalse(dto.toString().contains("private-"));
        dto.put("article", new JSONObject().put("title", "secret")).put("comment", "secret").put("actor", "secret").put("url", "secret");
        ApiV3NotificationDto.unavailable(dto);
        Assert.assertFalse(dto.toString().contains("secret"));
        Assert.assertFalse(dto.getBoolean("contentAvailable"));
    }

    @Test
    public void replyDataReadIsOwnedBoundAndRollsBackWithCaller() throws Exception {
        try (final Fixture f = fixture()) {
            f.seed("01", "a", 3, false);
            f.seed("02", "b", 3, false);
            f.seed("03", "a", 3, true);
            f.seed("04", "a", 3, false);
            f.execute("UPDATE notification SET dataId='other' WHERE oId='04'");
            try {
                f.state.markDataReadInCurrentTransaction("a", "target");
                Assert.fail();
            } catch (final IllegalStateException expected) {
            }
            final var rollback = f.begin();
            Assert.assertEquals(f.state.markDataReadInCurrentTransaction("a", "target"), 1L);
            Assert.assertFalse(f.notifications.getOwned("b", "02").getBoolean("hasRead"));
            Assert.assertFalse(f.notifications.getOwned("a", "04").getBoolean("hasRead"));
            rollback.rollback();
            Assert.assertFalse(f.notifications.getOwned("a", "01").getBoolean("hasRead"));
            final var commit = f.begin();
            Assert.assertEquals(f.state.markDataReadInCurrentTransaction("a", "target"), 1L);
            Assert.assertEquals(f.state.markDataReadInCurrentTransaction("a", "target"), 0L);
            Assert.assertEquals(f.state.markDataReadInCurrentTransaction("a' OR 1=1 --", "target"), 0L);
            Assert.assertEquals(f.state.markDataReadInCurrentTransaction("a", "target' OR 1=1 --"), 0L);
            commit.commit();
            Assert.assertEquals(f.notifications.getOwned("a", "01").getString("content"), "private-content");
        }
    }

    @Test
    public void replyDataReadBatchesHaveStableCutoff() throws Exception {
        try (final Fixture f = fixture()) {
            for (int i = 0; i < 401; i++) f.seed(String.format("%04d", i), "a", 3, false);
            f.seed("0500", "b", 3, false);
            f.insertAfterDataCutoff = true;
            final var tx = f.begin();
            Assert.assertEquals(f.state.markDataReadInCurrentTransaction("a", "target"), 401L);
            Assert.assertFalse(f.notifications.getOwned("a", "9999").getBoolean("hasRead"));
            Assert.assertFalse(f.notifications.getOwned("b", "0500").getBoolean("hasRead"));
            tx.commit();
        }
    }

    private Fixture fixture() throws Exception {
        final var field = Latkes.class.getDeclaredField("localProps");
        field.setAccessible(true);
        final Properties previous = (Properties) field.get(null);
        final Properties properties = new Properties();
        if (null != previous) properties.putAll(previous);
        properties.setProperty("runtimeDatabase", "NONE");
        Latkes.setLocalProps(properties);
        try {
            return new Fixture();
        } finally {
            Latkes.setLocalProps(previous);
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private void failure(final int status, final Action action) throws Exception {
        try {
            action.run();
            Assert.fail("Expected protocol failure");
        } catch (final ApiV3Exception e) {
            Assert.assertEquals(e.status, status);
        }
    }

    private static void inject(final Object target, final String name, final Object value) throws Exception {
        final var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Fixture implements AutoCloseable {
        private final Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        private final Notifications notifications = new Notifications();
        private final Requests requests = new Requests();
        private final ApiV3NotificationMgmtService api = new ApiV3NotificationMgmtService();
        private boolean failResults;
        private boolean insertAfterDataCutoff;
        private final NotificationStateMgmtService state = new NotificationStateMgmtService();

        private Fixture() throws Exception {
            execute("CREATE TABLE notification (oId TEXT PRIMARY KEY,userId TEXT,dataType INTEGER,hasRead INTEGER,dataId TEXT,content TEXT)");
            execute("CREATE TABLE requests (oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt INTEGER)");
            inject(state, "notificationRepository", notifications);
            final ApiV3IdempotencyMgmtService replay = new ApiV3IdempotencyMgmtService();
            inject(replay, "requestRepository", requests);
            inject(api, "stateMgmtService", state);
            inject(api, "idempotencyMgmtService", replay);
        }

        private void seed(final String id, final String user, final int type, final boolean read) throws RepositoryException {
            execute("INSERT INTO notification VALUES (?,?,?,?,?,?)", id, user, type, read, "target", "private-content");
        }

        private PreparedStatement statement(final String sql, final Object... args) throws SQLException {
            final PreparedStatement statement = connection.prepareStatement(sql.replace(" FOR UPDATE", ""));
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }

        private List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
            try (final var statement = statement(sql, args); final var result = statement.executeQuery()) {
                final List<JSONObject> rows = new ArrayList<>();
                final var metadata = result.getMetaData();
                while (result.next()) {
                    final JSONObject row = new JSONObject();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        final String name = metadata.getColumnLabel(i);
                        row.put(name, "hasRead".equals(name) ? result.getBoolean(i) : result.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            } catch (final SQLException e) {
                throw new RepositoryException(e);
            }
        }

        private void execute(final String sql, final Object... args) throws RepositoryException {
            try (final var statement = statement(sql, args)) {
                statement.executeUpdate();
            } catch (final SQLException e) {
                throw new RepositoryException(e);
            }
        }

        private Transaction begin() {
            try {
                Assert.assertTrue(connection.getAutoCommit());
                connection.setAutoCommit(false);
            } catch (final SQLException e) {
                throw new IllegalStateException(e);
            }
            return new Transaction() {
                private boolean active = true;

                @Override
                public void commit() {
                    finish(true);
                }

                @Override
                public void rollback() {
                    finish(false);
                }

                @Override
                public boolean isActive() {
                    return active;
                }

                private void finish(final boolean commit) {
                    try {
                        if (commit) connection.commit();
                        else connection.rollback();
                        connection.setAutoCommit(true);
                        active = false;
                    } catch (final SQLException e) {
                        throw new IllegalStateException(e);
                    }
                }
            };
        }

        private boolean active() {
            try {
                return !connection.getAutoCommit();
            } catch (final SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() throws SQLException {
            connection.close();
        }

        private final class Notifications extends NotificationRepository {
            @Override
            public String getName() {
                return "notification";
            }

            @Override
            public String unreadDataCutoffForUpdate(final String userId, final String dataId) throws RepositoryException {
                final String cutoff = super.unreadDataCutoffForUpdate(userId, dataId);
                if (insertAfterDataCutoff) {
                    insertAfterDataCutoff = false;
                    seed("9999", "a", 3, false);
                }
                return cutoff;
            }

            @Override
            public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
                return Fixture.this.select(sql, args);
            }

            @Override
            public boolean hasTransactionBegun() {
                return active();
            }

            @Override
            public void update(final String id, final JSONObject row, final String... properties) throws RepositoryException {
                execute("UPDATE notification SET hasRead=? WHERE oId=?", row.getBoolean("hasRead"), id);
            }

            @Override
            public void remove(final String id) throws RepositoryException {
                execute("DELETE FROM notification WHERE oId=?", id);
            }
        }

        private final class Requests extends ApiV3RequestRepository {
            @Override
            public String getName() {
                return "requests";
            }

            @Override
            public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
                return Fixture.this.select(sql, args);
            }

            @Override
            public boolean hasTransactionBegun() {
                return active();
            }

            @Override
            public Transaction beginTransaction() {
                return begin();
            }

            @Override
            public String add(final JSONObject row) throws RepositoryException {
                execute("INSERT INTO requests VALUES (?,?,?,?,?,?)", row.getString("oId"), row.getString("userId"), row.getString("operation"),
                        row.getString("requestHash"), row.getString("responseJson"), row.getLong("createdAt"));
                return row.getString("oId");
            }

            @Override
            public void update(final String id, final JSONObject row, final String... properties) throws RepositoryException {
                if (failResults) throw new RepositoryException("simulated result storage failure");
                execute("UPDATE requests SET responseJson=? WHERE oId=?", row.getString("responseJson"), id);
            }
        }
    }

    public static void main(final String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        final var test = new ApiV3NotificationSqlTest();
        test.categoryMapIsDisjointAndAllCurrentTypesAreRepresented();
        test.actualSqlScopesOwnerStatusCountPaginationAndUnknownTypes();
        test.ownedMarkDeleteAndReplayCannotTouchAnotherUser();
        test.bulkTraversesMoreThanOneBatchAndReplaysDoNotAffectNewNotifications();
        test.resultFailureRollsBackStateAndReplayRowInTheSameDatabaseTransaction();
        test.unknownGroupAndCutoffUseTheSameSelectionAsTheList();
        test.rawDtoDoesNotLeakOwnerReferenceBodyOrKeysAndUnavailableClearsTargets();
        test.replyDataReadIsOwnedBoundAndRollsBackWithCaller();
        test.replyDataReadBatchesHaveStableCutoff();
        System.out.println("PASS: 9 notification SQL/state/DTO tests on SQLite; MySQL FOR UPDATE locking not covered");
    }
}
