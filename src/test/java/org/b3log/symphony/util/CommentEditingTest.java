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

import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Common;
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.Connection;
import java.sql.DriverManager;

/** Actual edit coordinator against SQLite adapters; not the real service or MySQL locks. */
public class CommentEditingTest {
    private static final String ID = "1800000000000";
    private static final long CREATED = Long.parseLong(ID);
    public static void main(final String[] args) throws Exception {
        try (final var store = new Store()) {
            try { CommentEditing.apply(store, "owner", ID, request(), CREATED, 2); Assert.fail(); } catch (final IllegalStateException expected) { }
            store.begin();
            try { CommentEditing.apply(store, "owner", ID, request(), CREATED, 2); Assert.fail(); } catch (final IllegalStateException expected) { store.rollback(); }
        }
        for (final long elapsed : new long[]{0, 300000, 300001}) {
            try (final var store = new Store()) {
                store.begin();
                try (final var scope = ArticlePublicationCaches.open(store::inTransaction)) {
                    final var result = CommentEditing.apply(store, "owner", ID, request(), CREATED + elapsed, 2);
                    Assert.assertTrue(store.inTransaction());
                    Assert.assertEquals(result.transfers().size(), elapsed > 300000 ? 1 : 0);
                    store.commit();
                }
                Assert.assertEquals(store.scalar("SELECT balance FROM account"), elapsed > 300000 ? 98 : 100);
                Assert.assertEquals(store.scalar("SELECT COUNT(*) FROM revision"), elapsed > 300000 ? 1 : 0);
            }
        }
        System.out.println("PASS required caller transaction/cache boundary and exact five-minute charge/revision boundary");
        try (final var store = new Store()) {
            final var original = store.current.toString();
            final var input = request().put(Comment.COMMENT_AUTHOR_ID, "other").put(Comment.COMMENT_ON_ARTICLE_ID, "other")
                    .put(Comment.COMMENT_REPLY_CNT, -100).put(Comment.COMMENT_ANONYMOUS, 0).put(Comment.COMMENT_UA, "u".repeat(Common.MAX_LENGTH_UA + 2));
            store.begin();
            try (final var scope = ArticlePublicationCaches.open(store::inTransaction)) {
                final var result = CommentEditing.apply(store, "owner", ID, input, CREATED + 1, 2);
                Assert.assertEquals(result.comment().getString(Comment.COMMENT_AUTHOR_ID), "owner");
                Assert.assertEquals(result.comment().getString(Comment.COMMENT_ON_ARTICLE_ID), "article");
                Assert.assertEquals(result.comment().getInt(Comment.COMMENT_REPLY_CNT), 7);
                Assert.assertEquals(result.comment().getInt(Comment.COMMENT_VISIBLE), 1);
                Assert.assertEquals(result.comment().getString(Comment.COMMENT_PARAGRAPH_SNAPSHOT), "paragraph snapshot");
                Assert.assertEquals(result.comment().getString(Comment.COMMENT_UA).length(), Common.MAX_LENGTH_UA);
                Assert.assertEquals(store.current.toString(), original); store.commit();
            }
        }
        System.out.println("PASS author/target/reply/paragraph metadata immutable, omitted private visibility preserved and UA bounded");
        for (final int anonymousTarget : new int[]{0, 1}) {
            try (final var store = new Store()) {
                if (anonymousTarget == 0) store.current.put(Comment.COMMENT_ANONYMOUS, 1);
                else store.article.put(Article.ARTICLE_ANONYMOUS, 1);
                store.exec("UPDATE account SET balance=-1"); store.begin();
                try (final var scope = ArticlePublicationCaches.open(store::inTransaction)) {
                    Assert.assertTrue(CommentEditing.apply(store, "owner", ID, request(), CREATED + 300001, 2).transfers().isEmpty());
                    store.commit();
                }
                Assert.assertEquals(store.scalar("SELECT balance FROM account"), -1);
                Assert.assertEquals(store.scalar("SELECT COUNT(*) FROM revision"), 1);
            }
        }
        System.out.println("PASS anonymous comment or article retains free edits and content revision even with negative balance");
        for (final int failure : new int[]{0, 1, 2}) {
            try (final var store = new Store()) {
                if (failure == 0) store.exec("UPDATE account SET balance=1");
                if (failure == 1) store.nullTransfer = true;
                if (failure == 2) store.failRevision = true;
                store.begin();
                try (final var scope = ArticlePublicationCaches.open(store::inTransaction)) {
                    try { CommentEditing.apply(store, "owner", ID, request(), CREATED + 300001, 2); Assert.fail(); }
                    catch (final CommentEditing.Rejected e) { Assert.assertEquals(e.code, "insufficient_balance"); store.rollback(); }
                    catch (final IllegalStateException expected) { store.rollback(); }
                }
                Assert.assertEquals(store.content(), "old");
                Assert.assertEquals(store.scalar("SELECT COUNT(*) FROM revision"), 0);
                Assert.assertEquals(store.scalar("SELECT COUNT(*) FROM transfer"), 0);
                Assert.assertEquals(store.scalar("SELECT balance FROM account"), failure == 0 ? 1 : 100);
            }
        }
        System.out.println("PASS insufficient balance, failed transfer and failed revision cannot partially save content/version/points");
        try (final var store = new Store()) {
            store.begin();
            try (final var scope = ArticlePublicationCaches.open(store::inTransaction)) {
                CommentEditing.apply(store, "owner", ID, new JSONObject().put(Comment.COMMENT_CONTENT, " o l d "), CREATED + 300001, 2);
                store.commit();
            }
            Assert.assertEquals(store.scalar("SELECT COUNT(*) FROM revision"), 0);
            Assert.assertEquals(store.scalar("SELECT balance FROM account"), 98);
            System.out.println("PASS whitespace-only or unchanged late edits still pay existing fee without creating revision");
        }
        try (final var db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (final var stmt = db.createStatement()) {
                stmt.execute("CREATE TABLE comments(oId TEXT,commentAuthorId TEXT,commentOnArticleId TEXT,commentIP TEXT)");
                stmt.execute("INSERT INTO comments VALUES('1','owner','article','private-ip')");
            }
            for (final boolean locking : new boolean[]{false, true}) {
                Assert.assertEquals(ownedRows(db, "1", "owner", locking), 1);
                Assert.assertEquals(ownedRows(db, "1", "other", locking), 0);
                Assert.assertEquals(ownedRows(db, "missing", "owner", locking), 0);
                Assert.assertEquals(ownedRows(db, "1' OR 1=1 --", "owner", locking), 0);
                Assert.assertEquals(ownedRows(db, "1", "owner' OR 1=1 --", locking), 0);
            }
            try { CommentEditing.ownedSql("comments;DROP TABLE x", false); Assert.fail(); } catch (final IllegalArgumentException expected) { }
        }
        System.out.println("PASS actual owner/ID query binding and target projection for existing, foreign, missing and injection cases");
        for (final int scenario : new int[]{0, 1, 2}) {
            try (final var store = new Store()) {
                if (scenario == 0) store.current = null;
                if (scenario == 1) store.current.put(Comment.COMMENT_AUTHOR_ID, "other");
                if (scenario == 2) store.article.put("oId", "unrelated");
                store.begin();
                try (final var scope = ArticlePublicationCaches.open(store::inTransaction)) {
                    try { CommentEditing.apply(store, "owner", ID, request(), CREATED + 300001, 2); Assert.fail(); }
                    catch (final CommentEditing.Rejected e) { Assert.assertEquals(e.code, "not_found"); store.rollback(); }
                }
                Assert.assertEquals(store.content(), "old"); Assert.assertEquals(store.scalar("SELECT balance FROM account"), 100);
            }
        }
        System.out.println("PASS absent/foreign/mismatched article state rejected before any write");
    }
    private static int ownedRows(final Connection db, final String id, final String owner, final boolean locking) throws Exception {
        final String sql = CommentEditing.ownedSql("comments", locking);
        Assert.assertEquals(sql.endsWith(" FOR UPDATE"), locking);
        try (final var stmt = db.prepareStatement(sql.replace(" FOR UPDATE", ""))) {
            stmt.setString(1, id); stmt.setString(2, owner);
            try (final var rows = stmt.executeQuery()) {
                if (!locking) Assert.assertEquals(rows.getMetaData().getColumnCount(), 2);
                int count = 0; while (rows.next()) count++; return count;
            }
        }
    }
    private static JSONObject request() { return new JSONObject().put(Comment.COMMENT_CONTENT, "new body"); }
    private static final class Store implements CommentEditing.Store, AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        JSONObject current = new JSONObject().put("oId", ID).put(Comment.COMMENT_AUTHOR_ID, "owner")
                .put(Comment.COMMENT_ON_ARTICLE_ID, "article").put(Comment.COMMENT_CONTENT, "old")
                .put(Comment.COMMENT_ANONYMOUS, 0).put(Comment.COMMENT_VISIBLE, 1).put(Comment.COMMENT_REPLY_CNT, 7)
                .put(Comment.COMMENT_PARAGRAPH_SNAPSHOT, "paragraph snapshot");
        final JSONObject article = new JSONObject().put("oId", "article").put(Article.ARTICLE_ANONYMOUS, 0);
        boolean nullTransfer, failRevision;
        Store() throws Exception {
            exec("CREATE TABLE account(balance INT)"); exec("INSERT INTO account VALUES(100)");
            exec("CREATE TABLE content(body TEXT)"); exec("INSERT INTO content VALUES('old')");
            exec("CREATE TABLE revision(body TEXT)"); exec("CREATE TABLE transfer(amount INT)");
        }
        public boolean inTransaction() { try { return !db.getAutoCommit(); } catch (final Exception e) { throw new IllegalStateException(e); } }
        public CommentEditing.State lockAndLoad(final String actor, final String id) { return new CommentEditing.State(current, article); }
        public String normalize(final String raw) { return raw; }
        public int balance() throws Exception { return scalar("SELECT balance FROM account"); }
        public void save(final String id, final JSONObject comment) throws Exception { exec("UPDATE content SET body=?", comment.getString(Comment.COMMENT_CONTENT)); }
        public void revision(final String id, final String actor, final String content) throws Exception {
            exec("INSERT INTO revision VALUES(?)", content); if (failRevision) throw new IllegalStateException("simulated revision failure");
        }
        public String charge(final String id, final int fee, final long now) throws Exception {
            exec("UPDATE account SET balance=balance-?", fee); exec("INSERT INTO transfer VALUES(?)", fee); return nullTransfer ? null : "transfer";
        }
        void begin() throws Exception { db.setAutoCommit(false); }
        void commit() throws Exception { db.commit(); db.setAutoCommit(true); }
        void rollback() throws Exception { db.rollback(); db.setAutoCommit(true); }
        int scalar(final String sql) throws Exception { try (final var s = db.createStatement(); final var r = s.executeQuery(sql)) { r.next(); return r.getInt(1); } }
        String content() throws Exception { try (final var s = db.createStatement(); final var r = s.executeQuery("SELECT body FROM content")) { r.next(); return r.getString(1); } }
        void exec(final String sql, final Object... values) throws Exception { try (final var s = db.prepareStatement(sql)) { for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]); s.executeUpdate(); } }
        public void close() throws Exception { db.close(); }
    }
}
