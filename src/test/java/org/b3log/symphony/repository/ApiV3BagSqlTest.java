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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.util.BagMutation;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

/**
 * 用 SQLite 验证实际的 CloudRepository SQL 和背包状态变更。MySQL 锁及完整服务需另行验证。
 */
public class ApiV3BagSqlTest {
    public static void main(final String[] args) throws Exception {
        final JSONObject original = new JSONObject().put("checkin1day", 2).put("checkin2days", 1).put(BagMutation.REMAIN, 3).put("other", 9).put("opaque", "private");
        final JSONObject one = BagMutation.useExemption(original, "checkin1day");
        Assert.assertEquals(one.getInt(BagMutation.REMAIN), 4);
        Assert.assertEquals(one.getInt("checkin1day"), 1);
        final JSONObject two = BagMutation.useExemption(one, "checkin2days");
        Assert.assertEquals(two.getInt(BagMutation.REMAIN), 6);
        Assert.assertEquals(BagMutation.useExemption(original, "checkin2days").getInt(BagMutation.REMAIN), 5);
        Assert.assertEquals(two.getInt("checkin2days"), 0);
        Assert.assertEquals(two.getInt("other"), 9);
        Assert.assertEquals(original.getInt(BagMutation.REMAIN), 3);
        for (final JSONObject bag : List.of(new JSONObject(), new JSONObject().put("checkin1day", 1).put(BagMutation.REMAIN, Integer.MAX_VALUE), new JSONObject().put("checkin1day", 1).put(BagMutation.REMAIN, -1))) {
            try {
                BagMutation.useExemption(bag, "checkin1day");
                Assert.fail();
            } catch (final BagMutation.Unavailable expected) {
            }
        }
        Assert.assertEquals(BagMutation.adjust(new JSONObject().put(BagMutation.REMAIN, 3), BagMutation.REMAIN, 4, 4).bag().getInt(BagMutation.REMAIN), 4);
        Assert.assertEquals(BagMutation.adjust(new JSONObject().put("card", Integer.MAX_VALUE), "card", 1, Integer.MAX_VALUE).bag().getInt("card"), Integer.MAX_VALUE);
        Assert.assertEquals(BagMutation.adjust(original, "checkin1day", -3, Integer.MAX_VALUE).status(), -1);
        Assert.assertEquals(BagMutation.adjust(original, "checkin1day", 0, Integer.MAX_VALUE).status(), -1);
        Assert.assertFalse(BagMutation.dto(original).toString().contains("private"));
        Assert.assertFalse(BagMutation.dto(original).toString().contains("opaque"));
        for (final Object value : BagMutation.dto(original).getJSONArray("list"))
            Assert.assertEquals(((JSONObject) value).keySet(), Set.of("code", "count"));
        System.out.println("PASS existing card accumulation, no-card/overflow checks, capped adjustments and DTO whitelist");
        final var prop = Latkes.class.getDeclaredField("localProps");
        prop.setAccessible(true);
        final Object prior = prop.get(null);
        final Properties p = new Properties();
        p.setProperty("runtimeDatabase", "NONE");
        prop.set(null, p);
        try (final Fixture f = new Fixture()) {
            try {
                f.repo.getBagRows("owner", true);
                Assert.fail();
            } catch (final IllegalStateException expected) {
            }
            Assert.assertEquals(f.repo.getBagRows("owner", false).size(), 1);
            Assert.assertTrue(f.repo.getBagRows("owner' OR '1'='1", false).isEmpty());
            Assert.assertTrue(f.repo.getBagRows("missing", false).isEmpty());
            Assert.assertEquals(f.repo.getBagRows("owner", false).getFirst().getString("gameId"), "sys-bag");
            f.db.setAutoCommit(false);
            final var rows = f.repo.getBagRows("owner", true);
            final JSONObject bag = BagMutation.useExemption(new JSONObject(rows.getFirst().getString("data")), "checkin1day");
            f.exec("UPDATE cloud SET data=? WHERE oId=?", bag.toString(), rows.getFirst().getString("oId"));
            f.db.rollback();
            f.db.setAutoCommit(true);
            Assert.assertEquals(new JSONObject(f.repo.getBagRows("owner", false).getFirst().getString("data")).getInt("checkin1day"), 2);
            f.db.setAutoCommit(false);
            f.exec("UPDATE cloud SET data=? WHERE oId='1'", bag.toString());
            f.db.commit();
            f.db.setAutoCommit(true);
            final JSONObject saved = new JSONObject(f.repo.getBagRows("owner", false).getFirst().getString("data"));
            Assert.assertEquals(saved.getInt("checkin1day"), 1);
            Assert.assertEquals(saved.getInt("sysCheckinRemain"), 4);
            Assert.assertEquals(f.repo.getBagRows("other", false).getFirst().getString("data"), "{}");
            System.out.println("PASS actual bag SQL owner/game isolation, transaction guard and use-card rollback/commit");
        } finally {
            prop.set(null, prior);
        }
    }

    private static class Fixture implements AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        final CloudRepository repo = new CloudRepository() {
            public String getName() {
                return "cloud";
            }

            public boolean hasTransactionBegun() {
                try {
                    return !db.getAutoCommit();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
                try (final var statement = db.prepareStatement(sql.replace(" FOR UPDATE", ""))) {
                    for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                    final List<JSONObject> rows = new ArrayList<>();
                    try (final var rs = statement.executeQuery()) {
                        while (rs.next()) {
                            final JSONObject row = new JSONObject();
                            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                                row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                            rows.add(row);
                        }
                    }
                    return rows;
                } catch (final Exception e) {
                    throw new RepositoryException(e);
                }
            }
        };

        Fixture() throws Exception {
            exec("CREATE TABLE cloud(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");
            exec("INSERT INTO cloud VALUES ('1','owner','sys-bag','{\"checkin1day\":2,\"sysCheckinRemain\":3}'),('2','owner','game','{}'),('3','other','sys-bag','{}')");
        }

        void exec(final String sql, final Object... args) throws Exception {
            try (final var statement = db.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                statement.executeUpdate();
            }
        }

        public void close() throws Exception {
            db.close();
        }
    }
}
