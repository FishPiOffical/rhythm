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

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.repository.LongArticleColumnRepository;
import org.b3log.symphony.repository.LongArticleChapterRepository;
import org.b3log.symphony.util.ApiV3ColumnInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.json.JSONArray;
import org.testng.Assert;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.stream.Collectors;

/** 使用临时 SQLite 事务执行实际服务和 SQL，覆盖范围限于 SQLite。 */
public class ApiV3ColumnMgmtServiceTest {
    public static void main(final String[] args) throws Exception {
        Assert.assertEquals(ApiV3ColumnInputs.text(new JSONObject().put("title", " title "), "title", 64, false), "title");
        for (final Object value : List.of("", "x".repeat(65), "x\ny", 1)) {
            try { ApiV3ColumnInputs.text(new JSONObject().put("title", value), "title", 64, false); Assert.fail(); } catch (final ApiV3Exception expected) { }
        }
        for (final JSONArray ids : List.of(new JSONArray(), new JSONArray().put("1").put("1"), new JSONArray().put(1), new JSONArray().put("x"), new JSONArray().put("1".repeat(20)))) {
            try { ApiV3ColumnInputs.order(new JSONObject().put("columnIds", ids)); Assert.fail(); } catch (final ApiV3Exception expected) { }
        }
        Assert.assertEquals(ApiV3ColumnInputs.order(new JSONObject().put("columnIds", new JSONArray().put("2").put("1"))), List.of("2", "1"));
        for (final String url : List.of("javascript:alert(1)", "//example.test/x", "https://example.test/x\"", "https://example.test/" + "a".repeat(1024))) {
            try { ColumnCoverMgmtService.normalizeCoverURL(url); Assert.fail(); } catch (final ServiceException expected) { }
        }
        Assert.assertEquals(ColumnCoverMgmtService.normalizeCoverURL(" /image.png "), "/image.png");
        Assert.assertEquals(ColumnCoverMgmtService.normalizeCoverURL(""), "");
        System.out.println("PASS strict title/order inputs and existing cover validation");
        final var props = Latkes.class.getDeclaredField("localProps"); props.setAccessible(true); final Object original = props.get(null);
        final Properties local = new Properties(); local.setProperty("runtimeDatabase", "NONE"); props.set(null, local);
        try (final Fixture f = new Fixture()) {
            try { f.columns.getForUpdate("1"); Assert.fail(); } catch (final IllegalStateException expected) { }
            f.service.renameColumn("owner", "1", " renamed ");
            Assert.assertEquals(f.row("1").getString("columnTitle"), "renamed");
            try { f.service.renameColumn("other", "1", "stolen"); Assert.fail(); } catch (final ServiceException expected) { }
            try { f.service.renameColumn("owner", "1", "two"); Assert.fail(); } catch (final ServiceException expected) { }
            Assert.assertEquals(f.row("1").getString("columnTitle"), "renamed");
            f.covers.updateCoverURL("owner", "1", "https://example.test/cover.png");
            Assert.assertEquals(f.row("1").getString("columnCoverURL"), "https://example.test/cover.png");
            Assert.assertEquals(f.row("1").getInt("columnArticleCount"), 0);
            System.out.println("PASS owner checks, duplicate title, cover update and transaction guard");
            f.service.updateColumnOrder("owner", List.of("2", "1", "2"));
            Assert.assertEquals(f.row("2").getInt("columnOrder"), 0);
            Assert.assertEquals(f.row("1").getInt("columnOrder"), 1);
            Assert.assertEquals(f.row("3").getInt("columnOrder"), 7);
            try { f.service.updateColumnOrder("owner", List.of("1", "4")); Assert.fail(); } catch (final ServiceException expected) { }
            Assert.assertEquals(f.row("1").getInt("columnOrder"), 1);
            f.failId = "2";
            try { f.service.updateColumnOrder("owner", List.of("1", "2")); Assert.fail(); } catch (final ServiceException expected) { }
            Assert.assertEquals(f.row("1").getInt("columnOrder"), 1); f.failId = "";
            System.out.println("PASS partial order, legacy deduplication, all-owner validation and rollback");
            final Transaction chapterTx = f.begin();
            final JSONObject article = new JSONObject().put("oId", "10").put(Article.ARTICLE_AUTHOR_ID, "owner").put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_LONG);
            final JSONObject request = new JSONObject().put("columnId", "1");
            f.service.syncChapterInCurrentTransaction(article, request); chapterTx.commit();
            Assert.assertEquals(f.row("1").getInt("columnArticleCount"), 1);
            try { f.service.removeColumn("owner", "1"); Assert.fail(); } catch (final ServiceException expected) { }
            final Transaction removeTx = f.begin(); f.service.removeChapterInCurrentTransaction("10"); removeTx.commit();
            Assert.assertEquals(f.row("1").getInt("columnArticleCount"), 0);
            f.service.removeColumn("owner", "1");
            Assert.assertEquals(f.row("1").getInt("columnStatus"), 1);
            final Transaction invalidTx = f.begin();
            try { f.service.syncChapterInCurrentTransaction(article, request); Assert.fail(); } catch (final ServiceException expected) { invalidTx.rollback(); }
            Assert.assertTrue(f.select("SELECT * FROM long_article_column_chapter").isEmpty());
            try { f.service.renameColumn("owner", "2", "renamed"); Assert.fail(); } catch (final ServiceException expected) { }
            System.out.println("PASS chapter count, nonempty delete conflict, removal and deleted-parent rejection");
        } finally { props.set(null, original); }
    }
    private static class Fixture implements AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        final Columns columns = new Columns(); final Chapters chapters = new Chapters();
        final LongArticleColumnMgmtService service = new LongArticleColumnMgmtService();
        final ColumnCoverMgmtService covers = new ColumnCoverMgmtService();
        String failId = "";
        Fixture() throws Exception {
            exec("CREATE TABLE long_article_column(oId TEXT PRIMARY KEY,columnAuthorId TEXT,columnTitle TEXT,columnCoverURL TEXT DEFAULT '',columnStatus INT DEFAULT 0,columnOrder INT DEFAULT 7,columnArticleCount INT DEFAULT 0,columnCreateTime BIGINT DEFAULT 1,columnUpdateTime BIGINT DEFAULT 1,UNIQUE(columnAuthorId,columnTitle))");
            exec("CREATE TABLE long_article_column_chapter(oId TEXT PRIMARY KEY,articleId TEXT UNIQUE,columnId TEXT,chapterNo INT,chapterCreateTime BIGINT,chapterUpdateTime BIGINT,UNIQUE(columnId,chapterNo))");
            exec("INSERT INTO long_article_column(oId,columnAuthorId,columnTitle) VALUES ('1','owner','one'),('2','owner','two'),('3','owner','three'),('4','other','four')");
            inject(service, "longArticleColumnRepository", columns); inject(service, "longArticleChapterRepository", chapters); inject(covers, "longArticleColumnRepository", columns);
        }
        void inject(final Object target, final String name, final Object value) throws Exception { final var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        JSONObject row(final String id) throws RepositoryException { return select("SELECT * FROM long_article_column WHERE oId=?", id).getFirst(); }
        Transaction begin() {
            try { if (!db.getAutoCommit()) throw new IllegalStateException("nested transaction"); db.setAutoCommit(false); } catch (final java.sql.SQLException e) { throw new IllegalStateException(e); }
            return new Transaction() {
                boolean active = true;
                public void commit() { try { db.commit(); db.setAutoCommit(true); active = false; } catch (Exception e) { throw new IllegalStateException(e); } }
                public void rollback() { try { db.rollback(); db.setAutoCommit(true); active = false; } catch (Exception e) { throw new IllegalStateException(e); } }
                public boolean isActive() { return active; }
            };
        }
        boolean active() { try { return !db.getAutoCommit(); } catch (Exception e) { throw new IllegalStateException(e); } }
        List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
            try (final var statement = db.prepareStatement(sql.replace(" FOR UPDATE", ""))) {
                for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); final List<JSONObject> rows = new ArrayList<>();
                try (final var rs = statement.executeQuery()) { while (rs.next()) { final JSONObject row = new JSONObject(); for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i)); rows.add(row); } } return rows;
            } catch (final Exception e) { throw new RepositoryException(e); }
        }
        void exec(final String sql, final Object... args) throws RepositoryException {
            try (final var statement = db.prepareStatement(sql)) { for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); statement.executeUpdate(); } catch (Exception e) { throw new RepositoryException(e); }
        }
        String add(final String table, final JSONObject row) throws RepositoryException {
            final List<String> keys = new ArrayList<>(row.keySet());
            exec("INSERT INTO " + table + " (" + String.join(",", keys) + ") VALUES (" + keys.stream().map(k -> "?").collect(Collectors.joining(",")) + ")", keys.stream().map(row::get).toArray());
            return row.getString(Keys.OBJECT_ID);
        }
        void update(final String table, final String id, final JSONObject row, final String... properties) throws RepositoryException {
            final List<String> keys = properties.length == 0 ? row.keySet().stream().filter(k -> !k.equals(Keys.OBJECT_ID)).toList() : Arrays.asList(properties);
            final List<Object> args = new ArrayList<>(); for (final String key : keys) args.add(row.opt(key)); args.add(id);
            exec("UPDATE " + table + " SET " + keys.stream().map(k -> k + "=?").collect(Collectors.joining(",")) + " WHERE oId=?", args.toArray());
        }
        class Columns extends LongArticleColumnRepository {
            public String getName() { return "long_article_column"; }
            public Transaction beginTransaction() { return begin(); }
            public boolean hasTransactionBegun() { return active(); }
            public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException { return Fixture.this.select(sql, args); }
            public String add(final JSONObject row) throws RepositoryException { return Fixture.this.add(getName(), row); }
            public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
                Fixture.this.update(getName(), id, row, fields); if (id.equals(failId)) throw new RepositoryException("simulated failure");
            }
        }
        class Chapters extends LongArticleChapterRepository {
            public String getName() { return "long_article_column_chapter"; }
            public boolean hasTransactionBegun() { return active(); }
            public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException { return Fixture.this.select(sql, args); }
            public String add(final JSONObject row) throws RepositoryException { return Fixture.this.add(getName(), row); }
            public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { Fixture.this.update(getName(), id, row, fields); }
            public void remove(final String id) throws RepositoryException { exec("DELETE FROM long_article_column_chapter WHERE oId=?", id); }
        }
        public void close() throws Exception { db.close(); }
    }
}
