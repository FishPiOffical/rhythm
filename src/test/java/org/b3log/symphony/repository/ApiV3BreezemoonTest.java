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
import org.b3log.symphony.util.ApiV3BreezemoonInputs;
import org.b3log.symphony.util.ApiV3BreezemoonEdit;
import org.b3log.symphony.model.Breezemoon;
import org.b3log.symphony.model.Follow;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** 验证实际的入参规则和仓库 SQL。应用数据库、内容审核和职业执行器需另行验证。 */
public class ApiV3BreezemoonTest {
    public static void main(final String[] args) throws Exception {
        Assert.assertEquals(ApiV3BreezemoonInputs.content(new JSONObject().put("content", "  测试\n[链接](https://example.test)  ")), "测试\n[链接](https://example.test)");
        Assert.assertEquals(ApiV3BreezemoonInputs.content(new JSONObject().put("content", "x".repeat(512))).length(), 512);
        for (final Object value : new Object[]{"", " ", "x".repeat(513), 123, JSONObject.NULL, "a\u0001b"}) {
            try { ApiV3BreezemoonInputs.content(new JSONObject().put("content", value)); Assert.fail("Invalid content accepted"); }
            catch (final ApiV3Exception expected) { Assert.assertEquals(expected.status, 422); }
        }
        Assert.assertEquals(ApiV3BreezemoonInputs.publishable("<b>正文</b>"), "正文");
        try { ApiV3BreezemoonInputs.publishable("<script>alert(1)</script>"); Assert.fail(); } catch (final ApiV3Exception expected) { }
        System.out.println("PASS content type/length/control checks and legacy publish HTML stripping");
        Assert.assertEquals(ApiV3BreezemoonInputs.editable("  <b>编辑</b>  "), "<b>编辑</b>");
        final JSONObject old = new JSONObject().put("oId", "id").put(Breezemoon.BREEZEMOON_AUTHOR_ID, "owner")
                .put(Breezemoon.BREEZEMOON_CREATED, 1).put(Breezemoon.BREEZEMOON_UPDATED, 2).put(Breezemoon.BREEZEMOON_STATUS, 1)
                .put(Breezemoon.BREEZEMOON_CITY, "city").put(Breezemoon.BREEZEMOON_CONTENT, "old");
        final JSONObject edit = ApiV3BreezemoonEdit.apply(old, "new", "test-ip", "a".repeat(300), 3, 255);
        Assert.assertEquals(edit.getString(Breezemoon.BREEZEMOON_AUTHOR_ID), "owner");
        Assert.assertEquals(edit.getLong(Breezemoon.BREEZEMOON_CREATED), 1); Assert.assertEquals(edit.getInt(Breezemoon.BREEZEMOON_STATUS), 1);
        Assert.assertEquals(edit.getString(Breezemoon.BREEZEMOON_CITY), "city"); Assert.assertEquals(edit.getString(Breezemoon.BREEZEMOON_UA).length(), 255);
        Assert.assertEquals(edit.getString(Breezemoon.BREEZEMOON_CONTENT), "new"); Assert.assertEquals(edit.getLong(Breezemoon.BREEZEMOON_UPDATED), 3);
        Assert.assertEquals(old.getString(Breezemoon.BREEZEMOON_CONTENT), "old"); Assert.assertEquals(old.getLong(Breezemoon.BREEZEMOON_UPDATED), 2);
        System.out.println("PASS edit preserves author/creation/city/status and leaves the original untouched");
        final var field = Latkes.class.getDeclaredField("localProps"); field.setAccessible(true); final Object original = field.get(null);
        final Properties props = new Properties(); props.setProperty("runtimeDatabase", "NONE");
        final Rows repo;
        try { field.set(null, props); repo = new Rows(); } finally { field.set(null, original); }
        try (final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            repo.db = db;
            db.createStatement().execute("CREATE TABLE users(oId TEXT PRIMARY KEY,userStatus INTEGER,userBreezemoonStatus INTEGER)");
            db.createStatement().execute("CREATE TABLE breezemoon(oId TEXT PRIMARY KEY,breezemoonAuthorId TEXT,breezemoonStatus INTEGER)");
            db.createStatement().execute("INSERT INTO users VALUES('public',0,0),('private',0,1),('blocked',1,0),('deactivated',4,0)");
            db.createStatement().execute("INSERT INTO breezemoon VALUES('1','public',0),('2','public',0),('3','private',0),('4','blocked',0),('5','deactivated',0),('6','public',1)");
            final var first = repo.getV3Page("users", null, 1, 1, 1, 4, 0);
            Assert.assertEquals(first.total(), 2L); Assert.assertEquals(first.rows().getFirst().getString("oId"), "2");
            Assert.assertEquals(repo.getV3Page("users", null, 2, 1, 1, 4, 0).rows().getFirst().getString("oId"), "1");
            Assert.assertTrue(repo.getV3Page("users", null, 3, 1, 1, 4, 0).rows().isEmpty());
            Assert.assertEquals(repo.getV3Page("users", "private", 1, 20, 1, 4, 0).total(), 1L);
            Assert.assertEquals(repo.getV3Page("users", "private' OR 1=1 --", 1, 20, 1, 4, 0).total(), 0L);
            System.out.println("PASS public privacy/status filtering before count/pagination and bound owner query");
            db.createStatement().execute("CREATE TABLE follows(followerId TEXT,followingId TEXT,followingType INTEGER)");
            db.createStatement().execute("INSERT INTO follows VALUES('reader','public',0),('reader','public',0),('reader','private',0),('reader','blocked',0),('reader','deactivated',0),('reader','unused-tag',1),('other','private',0)");
            final var following = repo.getV3FollowingPage("users", "follows", "reader", 1, 1, 1, 4, 0);
            Assert.assertEquals(following.total(), 2L); Assert.assertEquals(following.rows().getFirst().getString("oId"), "2");
            Assert.assertEquals(repo.getV3FollowingPage("users", "follows", "reader", 2, 1, 1, 4, 0).rows().getFirst().getString("oId"), "1");
            Assert.assertEquals(repo.getV3FollowingPage("users", "follows", "no-follows", 1, 20, 1, 4, 0).total(), 0L);
            Assert.assertEquals(repo.getV3FollowingPage("users", "follows", "reader' OR 1=1 --", 1, 20, 1, 4, 0).total(), 0L);
            System.out.println("PASS following feed privacy/status/type, deduplication, paging, owner binding and no fallback");
            try { repo.getOwnedForUpdate("public", "1"); Assert.fail(); } catch (final IllegalStateException expected) { }
            db.setAutoCommit(false);
            Assert.assertNotNull(repo.getForUpdate("1"));
            Assert.assertNotNull(repo.getOwnedForUpdate("public", "1"));
            Assert.assertNull(repo.getOwnedForUpdate("private", "1"));
            Assert.assertNull(repo.getOwnedForUpdate("public' OR 1=1 --", "1"));
            db.rollback(); System.out.println("PASS owner+ID transaction guard and binding; SQLite does not test FOR UPDATE");
        }
    }
    private static class Rows extends BreezemoonRepository {
        Connection db;
        @Override public String getName() { return "breezemoon"; }
        @Override public boolean hasTransactionBegun() { try { return null != db && !db.getAutoCommit(); } catch (Exception e) { throw new IllegalStateException(e); } }
        @Override public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
            try (final var statement = db.prepareStatement(sql.replace(" FOR UPDATE", ""))) {
                for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                final List<JSONObject> rows = new ArrayList<>();
                try (final var rs = statement.executeQuery()) { while (rs.next()) { final JSONObject row = new JSONObject();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i)); rows.add(row); } }
                return rows;
            } catch (final Exception e) { throw new RepositoryException(e); }
        }
    }
}
