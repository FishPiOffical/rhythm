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
package org.b3log.symphony.util;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.RewardRepository;
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Real repository SQL with SQLite; the adapter removes FOR UPDATE, so does not prove MySQL locks. */
public class ArticleRemovalSqlTest {
    public static void main(final String[] args) throws Exception {
        final var field = Latkes.class.getDeclaredField("localProps"); field.setAccessible(true);
        final Object previous = field.get(null);
        final var props = new Properties(); props.setProperty("runtimeDatabase", "NONE");
        final Rows rows;
        try { field.set(null, props); rows = new Rows(); } finally { field.set(null, previous); }
        try (final var db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            rows.db = db;
            try (final var stmt = db.createStatement()) {
                stmt.execute("CREATE TABLE reward(oId TEXT PRIMARY KEY,dataId TEXT,type INT)");
                stmt.execute("INSERT INTO reward VALUES('1','article',0),('2','other',1)");
            }
            try { rows.existsForUpdate("article", 0); Assert.fail(); } catch (final IllegalStateException expected) { }
            db.setAutoCommit(false);
            Assert.assertTrue(rows.existsForUpdate("article", 0));
            Assert.assertFalse(rows.existsForUpdate("article", 1));
            Assert.assertFalse(rows.existsForUpdate("article' OR 1=1 --", 0));
            Assert.assertFalse(rows.existsForUpdate("absent", 0));
            db.rollback();
            System.out.println("PASS real reward existence SQL binds article/type, rejects injection and requires transaction");
            try (final var stmt = db.createStatement()) { stmt.execute("DROP TABLE reward"); }
            try { rows.existsForUpdate("article", 0); Assert.fail(); }
            catch (final RepositoryException expected) { db.rollback(); }
            System.out.println("PASS actual SQL failure propagates instead of reporting no rewards");
        }
    }
    private static final class Rows extends RewardRepository {
        Connection db;
        @Override public String getName() { return "reward"; }
        @Override public boolean hasTransactionBegun() {
            try { return null != db && !db.getAutoCommit(); } catch (final Exception e) { throw new IllegalStateException(e); }
        }
        @Override public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
            Assert.assertTrue(sql.endsWith(" LIMIT 1 FOR UPDATE"));
            try (final var stmt = db.prepareStatement(sql.replace(" FOR UPDATE", ""))) {
                for (int i = 0; i < args.length; i++) stmt.setObject(i + 1, args[i]);
                final var found = new ArrayList<JSONObject>();
                try (final var result = stmt.executeQuery()) { while (result.next()) found.add(new JSONObject().put("oId", result.getString(1))); }
                return found;
            } catch (final Exception e) { throw new RepositoryException(e); }
        }
    }
}
