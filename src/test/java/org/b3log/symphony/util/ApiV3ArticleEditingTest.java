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
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;

/** Actual owned edit coordinator; SQLite adapters do not execute the real service or MySQL locks. */
public class ApiV3ArticleEditingTest {
    public static void main(final String[] args) throws Exception {
        try (final Store store = new Store()) {
            final var patch = ApiV3ArticleEditInputs.normalize(new JSONObject().put("title", "new title"));
            final String hashInput = ApiV3Json.canonical(patch);
            try { ApiV3ArticleEditing.apply(store, "owner", patch); Assert.fail(); } catch (final IllegalStateException expected) { }
            store.db.setAutoCommit(false);
            try (final var ignored = ArticlePublicationCaches.open(store::inTransaction)) {
                final var result = ApiV3ArticleEditing.apply(store, "owner", patch);
                Assert.assertEquals(result.keySet(), Set.of("id", "permalink"));
                Assert.assertEquals(result.getString("id"), "1"); Assert.assertTrue(store.inTransaction()); store.commit();
            }
            Assert.assertEquals(store.scalar("SELECT balance FROM account"), 95);
            Assert.assertEquals(store.title(), "new title"); Assert.assertEquals(ApiV3Json.canonical(patch), hashInput);
            System.out.println("PASS owned partial edit in caller transaction, preserved replay patch and ID/permalink-only result");
        }
        for (int scenario = 0; scenario < 3; scenario++) {
            try (final Store store = new Store()) {
                if (scenario == 0) store.current = null;
                if (scenario == 1) store.current.put(Article.ARTICLE_AUTHOR_ID, "other");
                if (scenario == 2) store.current.put(Article.ARTICLE_STATUS, Article.ARTICLE_STATUS_C_INVALID);
                store.db.setAutoCommit(false);
                try (final var ignored = ArticlePublicationCaches.open(store::inTransaction)) {
                    try { ApiV3ArticleEditing.apply(store, "owner", ApiV3ArticleEditInputs.normalize(new JSONObject().put("title", "new title"))); Assert.fail(); }
                    catch (final ApiV3Exception e) { Assert.assertEquals(e.status, scenario == 2 ? 403 : 404); store.rollback(); }
                }
                Assert.assertFalse(store.validated); Assert.assertFalse(store.saved); Assert.assertEquals(store.scalar("SELECT balance FROM account"), 100);
            }
        }
        System.out.println("PASS missing/foreign/locked article rejected before moderation or saving");
        try (final Store store = new Store()) {
            store.fail = true; store.db.setAutoCommit(false);
            try (final var ignored = ArticlePublicationCaches.open(store::inTransaction)) {
                try { ApiV3ArticleEditing.apply(store, "owner", ApiV3ArticleEditInputs.normalize(new JSONObject().put("title", "new title"))); Assert.fail(); }
                catch (final IllegalStateException expected) { store.rollback(); }
            }
            Assert.assertEquals(store.title(), "old title"); Assert.assertEquals(store.scalar("SELECT balance FROM account"), 100);
            System.out.println("PASS save failure rolls content and debit back together");
        }
        try (final Store store = new Store()) {
            store.reject = true; store.db.setAutoCommit(false);
            try (final var ignored = ArticlePublicationCaches.open(store::inTransaction)) {
                try { ApiV3ArticleEditing.apply(store, "owner", ApiV3ArticleEditInputs.normalize(new JSONObject().put("title", "new title"))); Assert.fail(); }
                catch (final ApiV3Exception expected) { store.rollback(); }
            }
            Assert.assertFalse(store.saved); Assert.assertEquals(store.title(), "old title");
            System.out.println("PASS validation failure cannot change persisted content");
        }
    }
    private static final class Store implements ApiV3ArticleEditing.Store, AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        JSONObject current = ApiV3ArticleEditInputsTest.current(); boolean validated, saved, fail, reject;
        Store() throws Exception { exec("CREATE TABLE account(balance INT)"); exec("INSERT INTO account VALUES(100)"); exec("CREATE TABLE content(title TEXT)"); exec("INSERT INTO content VALUES('old title')"); }
        public boolean inTransaction() { try { return !db.getAutoCommit(); } catch (final Exception e) { throw new RuntimeException(e); } }
        public ApiV3ArticleEditing.State lockAndLoad() { return null == current ? null : new ApiV3ArticleEditing.State(current, null); }
        public void validate(final JSONObject request) { validated = true; if (reject) throw new ApiV3Exception(422, "content_rejected", "rejected"); }
        public ApiV3ArticleEditing.Saved save(final JSONObject request) throws Exception {
            Assert.assertTrue(validated); saved = true;
            exec("UPDATE content SET title=?", request.getString(Article.ARTICLE_TITLE)); exec("UPDATE account SET balance=balance-5");
            if (fail) throw new IllegalStateException("simulated edit failure");
            return new ApiV3ArticleEditing.Saved("1", "/article/1");
        }
        void commit() throws Exception { db.commit(); db.setAutoCommit(true); }
        void rollback() throws Exception { db.rollback(); db.setAutoCommit(true); }
        int scalar(final String sql) throws Exception { try (final var s = db.createStatement(); final var r = s.executeQuery(sql)) { r.next(); return r.getInt(1); } }
        String title() throws Exception { try (final var s = db.createStatement(); final var r = s.executeQuery("SELECT title FROM content")) { r.next(); return r.getString(1); } }
        void exec(final String sql, final Object... values) throws Exception { try (final var s = db.prepareStatement(sql)) { for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]); s.executeUpdate(); } }
        public void close() throws Exception { db.close(); }
    }
}
