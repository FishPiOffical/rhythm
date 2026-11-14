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
import org.b3log.symphony.util.FollowMutation;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 在 SQLite 上执行实际的 FollowRepository SQL。适配时移除 FOR UPDATE，MySQL 锁行为需另行验证。
 */
public class ApiV3FollowSqlTest {
    public static void main(final String[] args) throws Exception {
        final var propsField = Latkes.class.getDeclaredField("localProps");
        propsField.setAccessible(true);
        final Object original = propsField.get(null);
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        final Rows rows;
        try {
            propsField.set(null, props);
            rows = new Rows();
        } finally {
            propsField.set(null, original);
        }
        try (final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            rows.db = db;
            db.createStatement().execute("CREATE TABLE follow(oId TEXT PRIMARY KEY,followerId TEXT,followingId TEXT,followingType INTEGER)");
            db.createStatement().execute("CREATE TABLE users(oId TEXT PRIMARY KEY)");
            db.createStatement().execute("INSERT INTO users VALUES('owner'),('target')");
            db.createStatement().execute("INSERT INTO follow VALUES('1','other','target',2),('2','owner','target',3)");
            try {
                rows.lockEntity("users", "owner");
                Assert.fail();
            } catch (final IllegalStateException expected) {
            }
            db.setAutoCommit(false);
            Assert.assertNotNull(rows.lockEntity("users", "owner"));
            Assert.assertNull(rows.lockEntity("users", "owner' OR 1=1 --"));
            Assert.assertTrue(rows.getRelationsForUpdate("owner", "target", 2).isEmpty());
            Assert.assertEquals(rows.getRelationsForUpdate("owner", "target", 3).size(), 1);
            Assert.assertTrue(rows.getRelationsForUpdate("owner' OR 1=1 --", "target", 3).isEmpty());
            db.rollback();
            System.out.println("PASS real owner/type/target SQL binding and transaction guard");
            final FollowMutation.Store store = new FollowMutation.Store() {
                public boolean hasTransaction() {
                    return rows.hasTransactionBegun();
                }

                public boolean lockActor(final String actor) throws RepositoryException {
                    return null != rows.lockEntity("users", actor);
                }

                public JSONObject lockTarget(final String target, final int type) throws RepositoryException {
                    return rows.lockEntity("users", target);
                }

                public List<JSONObject> relations(final String actor, final String target, final int type) throws RepositoryException {
                    return rows.getRelationsForUpdate(actor, target, type);
                }

                public void add(final JSONObject row) throws RepositoryException {
                    try (final var sql = db.prepareStatement("INSERT INTO follow VALUES('created',?,?,?)")) {
                        sql.setString(1, row.getString("followerId"));
                        sql.setString(2, row.getString("followingId"));
                        sql.setInt(3, row.getInt("followingType"));
                        sql.executeUpdate();
                    } catch (final Exception e) {
                        throw new RepositoryException(e);
                    }
                }

                public void remove(final String id) throws RepositoryException {
                    throw new RepositoryException("unused");
                }

                public void adjust(final JSONObject row, final String id, final int type, final int delta) throws RepositoryException {
                    throw new RepositoryException("simulated counter write failure");
                }
            };
            try {
                FollowMutation.set(store, "owner", "target", 2, true, null);
                Assert.fail();
            } catch (final RepositoryException expected) {
                db.rollback();
            }
            Assert.assertTrue(rows.getRelationsForUpdate("owner", "target", 2).isEmpty());
            Assert.assertEquals(rows.getRelationsForUpdate("other", "target", 2).size(), 1);
            db.rollback();
            System.out.println("PASS actual DB rollback removes partial relationship and preserves other owner");
        }
    }

    private static class Rows extends FollowRepository {
        Connection db;

        @Override
        public String getName() {
            return "follow";
        }

        @Override
        public boolean hasTransactionBegun() {
            try {
                return null != db && !db.getAutoCommit();
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
            try (final var statement = db.prepareStatement(sql.replace(" FOR UPDATE", ""))) {
                for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                final List<JSONObject> rows = new ArrayList<>();
                try (final var result = statement.executeQuery()) {
                    while (result.next()) {
                        final JSONObject row = new JSONObject();
                        for (int i = 1; i <= result.getMetaData().getColumnCount(); i++)
                            row.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                        rows.add(row);
                    }
                }
                return rows;
            } catch (final Exception e) {
                throw new RepositoryException(e);
            }
        }
    }
}
