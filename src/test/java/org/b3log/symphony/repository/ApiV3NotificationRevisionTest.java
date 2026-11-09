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

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.*;
import org.b3log.symphony.model.ApiV3NotificationRevision;
import org.json.JSONObject;
import org.testng.Assert;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * 用独立的 SQLite 读写连接验证实际的通知写入钩子和版本 SQL。MySQL 锁及 HTTP 需另行验证。
 */
public class ApiV3NotificationRevisionTest {
    static class Fixture implements AutoCloseable {
        final Path file = Files.createTempFile("rhythm-notification-revision-", ".sqlite");
        final String url = "jdbc:sqlite:" + file;
        final Connection writer = DriverManager.getConnection(url), reader = DriverManager.getConnection(url), secondReader = DriverManager.getConnection(url);
        boolean failMarker, failData;
        int snapshotQueries;
        final Writer revisions = new Writer();
        final Reader first = new Reader(reader), second = new Reader(secondReader);
        final Notifications notifications = new Notifications();

        Fixture() throws Exception {
            execute(writer, "CREATE TABLE notification(oId TEXT PRIMARY KEY,userId TEXT,hasRead INT,dataType INT,dataId TEXT)");
            execute(writer, "CREATE TABLE symphony_api_v3_notification_revision(oId TEXT PRIMARY KEY,revision TEXT,updatedAt BIGINT)");
            var injection = NotificationRepository.class.getDeclaredField("revisions");
            injection.setAccessible(true);
            injection.set(notifications, revisions);
        }

        boolean active() {
            try {
                return !writer.getAutoCommit();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        void begin() throws Exception {
            writer.setAutoCommit(false);
        }

        void commit() throws Exception {
            writer.commit();
            writer.setAutoCommit(true);
        }

        void rollback() throws Exception {
            writer.rollback();
            writer.setAutoCommit(true);
        }

        String revision(String actor) throws Exception {
            return first.snapshot(List.of(actor)).get(actor);
        }

        void add(String id, String actor, int type) throws Exception {
            notifications.add(new JSONObject().put("oId", id).put("userId", actor).put("hasRead", false).put("dataType", type).put("dataId", "PRIVATE-ref"));
        }

        long count(String actor) throws Exception {
            return query(reader, "SELECT COUNT(*) AS n FROM notification WHERE userId=?", actor).getFirst().getLong("n");
        }

        class Writer extends ApiV3NotificationRevisionRepository {
            @Override
            public String getName() {
                return "symphony_api_v3_notification_revision";
            }

            @Override
            public boolean hasTransactionBegun() {
                return active();
            }

            @Override
            protected Connection currentConnection() {
                return writer;
            }

            @Override
            protected String upsertSql() {
                return failMarker ? "INSERT INTO missing_table VALUES(?)" : super.upsertSql().replace("ON DUPLICATE KEY UPDATE revision=VALUES(revision),updatedAt=VALUES(updatedAt)", "ON CONFLICT(oId) DO UPDATE SET revision=excluded.revision,updatedAt=excluded.updatedAt");
            }
        }

        class Reader extends ApiV3NotificationRevisionRepository {
            final Connection connection;

            Reader(Connection c) {
                connection = c;
            }

            @Override
            public String getName() {
                return "symphony_api_v3_notification_revision";
            }

            @Override
            public boolean hasTransactionBegun() {
                return false;
            }

            @Override
            public List<JSONObject> select(String sql, Object... args) throws RepositoryException {
                snapshotQueries++;
                return query(connection, sql, args);
            }
        }

        class Notifications extends NotificationRepository {
            @Override
            public String getName() {
                return "notification";
            }

            @Override
            public boolean hasTransactionBegun() {
                return active();
            }

            @Override
            public List<JSONObject> select(String sql, Object... args) throws RepositoryException {
                return query(writer, sql.replace(" FOR UPDATE", ""), args);
            }

            @Override
            protected String insertNotification(JSONObject row) throws RepositoryException {
                Assert.assertTrue(active());
                if (failData) throw new RepositoryException("injected data failure");
                execute(writer, "INSERT INTO notification VALUES(?,?,?,?,?)", row.getString("oId"), row.getString("userId"), row.getBoolean("hasRead"), row.getInt("dataType"), row.getString("dataId"));
                return row.getString("oId");
            }

            @Override
            protected void updateNotification(String id, JSONObject row, String... fields) throws RepositoryException {
                Assert.assertTrue(active());
                if (failData) throw new RepositoryException("injected data failure");
                if (Arrays.equals(fields, new String[]{"hasRead"}))
                    execute(writer, "UPDATE notification SET hasRead=? WHERE oId=?", row.getBoolean("hasRead"), id);
                else {
                    Assert.assertEquals(fields, new String[]{"userId"});
                    execute(writer, "UPDATE notification SET userId=? WHERE oId=?", row.getString("userId"), id);
                }
            }

            @Override
            protected void removeNotification(String id) throws RepositoryException {
                Assert.assertTrue(active());
                if (failData) throw new RepositoryException("injected data failure");
                execute(writer, "DELETE FROM notification WHERE oId=?", id);
            }

            @Override
            protected void removeNotifications(Query q) throws RepositoryException {
                Assert.assertTrue(active());
                if (failData) throw new RepositoryException("injected data failure");
                final List<Filter> filters = q.getFilter() instanceof PropertyFilter ? List.of(q.getFilter()) : ((CompositeFilter) q.getFilter()).getSubFilters();
                var values = new HashMap<String, Object>();
                for (Filter filter : filters) {
                    var property = (PropertyFilter) filter;
                    Assert.assertEquals(property.getOperator(), FilterOperator.EQUAL);
                    values.put(property.getKey(), property.getValue());
                }
                Assert.assertTrue(values.containsKey("userId"));
                if (values.containsKey("dataType")) {
                    Assert.assertEquals(values.size(), 2);
                    execute(writer, "DELETE FROM notification WHERE userId=? AND dataType=?", values.get("userId"), values.get("dataType"));
                } else {
                    Assert.assertEquals(values.size(), 1);
                    execute(writer, "DELETE FROM notification WHERE userId=?", values.get("userId"));
                }
            }
        }

        public void close() throws Exception {
            if (active()) rollback();
            writer.close();
            reader.close();
            secondReader.close();
            Files.deleteIfExists(file);
        }
    }

    static List<JSONObject> query(Connection connection, String sql, Object... args) throws RepositoryException {
        try (var s = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            try (var r = s.executeQuery()) {
                var out = new ArrayList<JSONObject>();
                while (r.next()) {
                    var row = new JSONObject();
                    for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
                        row.put(r.getMetaData().getColumnLabel(i), r.getObject(i));
                    out.add(row);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    static void execute(Connection connection, String sql, Object... args) throws RepositoryException {
        try (var s = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        for (String key : List.of("latkeProps", "localProps")) {
            var field = Latkes.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, props);
        }
        try (var f = new Fixture()) {
            // 在事务提交前后分别用独立读连接观察通知和版本。
            f.begin();
            f.add("1", "123", 3);
            Assert.assertNull(f.revision("123"));
            Assert.assertEquals(f.count("123"), 0L);
            f.commit();
            String one = f.revision("123");
            Assert.assertNotNull(one);
            Assert.assertTrue(one.matches("[0-9a-f]{32}"));
            Assert.assertEquals(f.count("123"), 1L);
            Assert.assertEquals(f.second.snapshot(List.of("123")).get("123"), one);

            f.begin();
            f.notifications.update("1", new JSONObject().put("hasRead", true), "hasRead");
            Assert.assertEquals(f.revision("123"), one);
            try {
                f.revisions.snapshot(List.of("123"));
                Assert.fail();
            } catch (IllegalStateException expected) {
            }
            f.rollback();
            Assert.assertEquals(f.revision("123"), one);
            Assert.assertEquals(query(f.reader, "SELECT hasRead FROM notification").getFirst().getInt("hasRead"), 0);

            f.begin();
            f.notifications.update("1", new JSONObject().put("hasRead", true), "hasRead");
            f.commit();
            String two = f.revision("123");
            Assert.assertNotEquals(two, one);
            Assert.assertEquals(query(f.reader, "SELECT hasRead FROM notification").getFirst().getInt("hasRead"), 1);

            f.begin();
            f.notifications.remove("1");
            Assert.assertEquals(f.revision("123"), two);
            f.commit();
            Assert.assertNotEquals(f.revision("123"), two);
            Assert.assertEquals(f.count("123"), 0L);
        }
        System.out.println("PASS actual add/read/delete hooks and revision writes share one transaction; independent readers see only committed versions and rollback emits no new marker");
        try (var f = new Fixture()) {
            f.begin();
            f.add("1", "123", 3);
            f.commit();
            String before = f.revision("123");
            f.failMarker = true;
            f.begin();
            try {
                f.add("2", "123", 3);
                Assert.fail();
            } catch (RepositoryException expected) {
                f.rollback();
            }
            f.failMarker = false;
            Assert.assertEquals(f.count("123"), 1L);
            Assert.assertEquals(f.revision("123"), before);

            f.failData = true;
            f.begin();
            try {
                f.notifications.remove("1");
                Assert.fail();
            } catch (RepositoryException expected) {
                f.rollback();
            }
            f.failData = false;
            Assert.assertEquals(f.count("123"), 1L);
            Assert.assertEquals(f.revision("123"), before);
            try {
                f.notifications.remove("1");
                Assert.fail();
            } catch (IllegalStateException expected) {
            }
        }
        System.out.println("PASS marker/data failures roll notification and marker back in the owning transaction; missing-transaction writes fail closed");
        try (var f = new Fixture()) {
            f.begin();
            f.add("1", "123", 3);
            f.add("2", "123", 6);
            f.add("3", "321", 3);
            f.commit();
            String other = f.revision("321"), before = f.revision("123");
            f.begin();
            f.notifications.removeByUserIdAndType("123", 3);
            f.commit();
            Assert.assertEquals(f.count("123"), 1L);
            Assert.assertEquals(f.count("321"), 1L);
            Assert.assertNotEquals(f.revision("123"), before);
            Assert.assertEquals(f.revision("321"), other);
            f.begin();
            f.notifications.removeByUserId("123");
            f.commit();
            Assert.assertEquals(f.count("123"), 0L);
            Assert.assertEquals(f.count("321"), 1L);
            Assert.assertEquals(f.revision("321"), other);
        }
        System.out.println("PASS legacy owned/type bulk deletion updates only its owner's marker and preserves other notification types/owners");
        try (var f = new Fixture()) {
            f.begin();
            f.add("1", "123", 3);
            f.add("2", "321", 3);
            f.commit();
            String other = f.revision("321");
            f.begin();
            f.notifications.update("1", new JSONObject().put("hasRead", true).put("userId", "321"), "hasRead");
            f.commit();
            Assert.assertEquals(f.count("123"), 1L);
            Assert.assertEquals(f.revision("321"), other);
            String before = f.revision("123");
            f.begin();
            f.notifications.update("1", new JSONObject().put("userId", "321"), "userId");
            f.commit();
            Assert.assertEquals(f.count("123"), 0L);
            Assert.assertEquals(f.count("321"), 2L);
            Assert.assertNotEquals(f.revision("123"), before);
            Assert.assertNotEquals(f.revision("321"), other);
        }
        System.out.println("PASS exact-column updates do not signal an unapplied owner field; an internal ownership move invalidates both actual affected owners");
        try (var f = new Fixture()) {
            f.begin();
            f.add("1", "123", 3);
            f.commit();
            String before = f.revision("123");
            f.begin();
            f.notifications.remove("1");
            f.add("2", "123", 3);
            execute(f.writer, "UPDATE symphony_api_v3_notification_revision SET updatedAt=1 WHERE oId='123'");
            f.commit();
            Assert.assertEquals(f.count("123"), 1L);
            Assert.assertNotEquals(f.revision("123"), before);
        }
        System.out.println("PASS replacement with unchanged notification counts and a backwards timestamp still produces a distinct committed marker");
        try (var f = new Fixture()) {
            var owners = new ArrayList<String>();
            f.begin();
            for (int i = 0; i < 205; i++) {
                owners.add("" + i);
                f.revisions.touchInCurrentTransaction("" + i);
            }
            f.commit();
            f.snapshotQueries = 0;
            Assert.assertEquals(f.first.snapshot(owners).size(), 205);
            Assert.assertEquals(f.snapshotQueries, 2);
            Assert.assertTrue(f.first.snapshot(List.of("0' OR 1=1 --")).isEmpty());
            Assert.assertFalse(ApiV3NotificationRevision.REPOSITORY.startsWith("symphony_"));
        }
        System.out.println("PASS marker snapshots use bounded parameterized batches, preserve owner isolation and do not order by timestamps or notification counts");
    }
}
