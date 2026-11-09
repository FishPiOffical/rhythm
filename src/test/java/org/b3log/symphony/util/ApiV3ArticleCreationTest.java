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
import org.b3log.symphony.model.ArticleDraft;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;

/**
 * 验证实际的文章创建协调流程。SQLite 适配器替代应用服务，框架类保留。
 */
public class ApiV3ArticleCreationTest {
    public static void main(final String[] args) throws Exception {
        try (final Fixture f = new Fixture()) {
            final var request = input("1");
            final String original = ApiV3Json.canonical(request);
            try {
                ApiV3ArticleCreation.apply(f, request);
                Assert.fail();
            } catch (final IllegalStateException expected) {
            }
            f.begin();
            try {
                ApiV3ArticleCreation.apply(f, request);
                Assert.fail();
            } catch (final IllegalStateException expected) {
            }
            try (final var ignored = ArticlePublicationCaches.open(f::inTransaction)) {
                final var dto = ApiV3ArticleCreation.apply(f, request);
                Assert.assertEquals(dto.keySet(), Set.of("id", "permalink"));
                Assert.assertEquals(dto.getString("id"), "123");
                Assert.assertEquals(dto.getString("permalink"), "/article/123");
                Assert.assertTrue(f.inTransaction());
                Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM drafts WHERE owner='owner'"), 0);
                Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM drafts WHERE owner='other'"), 1);
                Assert.assertEquals(ApiV3Json.canonical(request), original);
                f.commit();
            }
            Assert.assertEquals(f.scalar("SELECT balance FROM account"), 80);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM content"), 1);
            System.out.println("PASS atomic creation, owned draft deletion, immutable replay input and ID/permalink-only response");
        }
        for (final String invalid : new String[]{"2", "99"}) {
            try (final Fixture f = new Fixture()) {
                f.begin();
                try (final var ignored = ArticlePublicationCaches.open(f::inTransaction)) {
                    try {
                        ApiV3ArticleCreation.apply(f, input(invalid));
                        Assert.fail();
                    } catch (final ApiV3Exception e) {
                        Assert.assertEquals(e.status, 404);
                        f.rollback();
                    }
                }
                Assert.assertEquals(f.scalar("SELECT balance FROM account"), 100);
                Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM content"), 0);
                Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM drafts"), 2);
            }
        }
        System.out.println("PASS missing/foreign draft rolls publication and debit back without touching either owner's draft");
        for (final boolean permission : new boolean[]{true, false}) {
            try (final Fixture f = new Fixture()) {
                f.authorized = permission;
                f.rejected = permission;
                f.begin();
                try (final var ignored = ArticlePublicationCaches.open(f::inTransaction)) {
                    try {
                        ApiV3ArticleCreation.apply(f, input("1"));
                        Assert.fail();
                    } catch (final ApiV3Exception e) {
                        Assert.assertEquals(e.status, permission ? 422 : 403);
                        f.rollback();
                    }
                }
                Assert.assertFalse(f.published);
                Assert.assertEquals(f.scalar("SELECT balance FROM account"), 100);
                Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM drafts"), 2);
            }
        }
        System.out.println("PASS permission and moderation failures stop before publication or draft mutation");
        try (final Fixture f = new Fixture()) {
            f.begin();
            try (final var ignored = ArticlePublicationCaches.open(f::inTransaction)) {
                ApiV3ArticleCreation.apply(f, input(""));
                f.commit();
            }
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM drafts"), 2);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM content"), 1);
            System.out.println("PASS creation without draft preserves existing drafts");
        }
    }

    private static JSONObject input(final String draft) {
        return ApiV3ArticleInputs.normalize(new JSONObject().put("title", "title").put("content", "正文内容").put("tags", "新人报道").put("draftId", draft));
    }

    private static final class Fixture implements ApiV3ArticleCreation.Store, AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        boolean authorized = true, rejected, locked, validated, published;

        Fixture() throws Exception {
            exec("CREATE TABLE account(balance INT)");
            exec("INSERT INTO account VALUES(100)");
            exec("CREATE TABLE content(id TEXT,title TEXT,owner TEXT)");
            exec("CREATE TABLE drafts(id TEXT,owner TEXT)");
            exec("INSERT INTO drafts VALUES('1','owner'),('2','other')");
        }

        void begin() throws Exception {
            db.setAutoCommit(false);
        }

        void commit() throws Exception {
            db.commit();
            db.setAutoCommit(true);
        }

        void rollback() throws Exception {
            db.rollback();
            db.setAutoCommit(true);
        }

        public boolean inTransaction() {
            try {
                return !db.getAutoCommit();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void lockAndAuthorize() {
            locked = true;
            if (!authorized) throw new ApiV3Exception(403, "forbidden", "forbidden");
        }

        public void validate(final JSONObject request) {
            Assert.assertTrue(locked);
            if (rejected) throw new ApiV3Exception(422, "content_rejected", "rejected");
            request.put(Article.ARTICLE_TITLE, "normalized title");
            validated = true;
        }

        public ApiV3ArticleCreation.Created publish(final JSONObject request) throws Exception {
            Assert.assertTrue(validated);
            published = true;
            exec("INSERT INTO content VALUES('123',?,'owner')", request.getString(Article.ARTICLE_TITLE));
            exec("UPDATE account SET balance=balance-20");
            return new ApiV3ArticleCreation.Created("123", "/article/123");
        }

        public void removeDraft(final String id) throws Exception {
            Assert.assertTrue(published);
            try (final var s = db.prepareStatement("DELETE FROM drafts WHERE id=? AND owner=?")) {
                s.setString(1, id);
                s.setString(2, "owner");
                if (s.executeUpdate() != 1) throw new ApiV3Exception(404, "not_found", "draft unavailable");
            }
        }

        int scalar(final String sql) throws Exception {
            try (final var s = db.createStatement(); final var r = s.executeQuery(sql)) {
                r.next();
                return r.getInt(1);
            }
        }

        void exec(final String sql, final Object... values) throws Exception {
            try (final var s = db.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
                s.executeUpdate();
            }
        }

        public void close() throws Exception {
            db.close();
        }
    }
}
