package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Actual migration with SQLite transactions and a fixture renderer; not MySQL or real Markdown. */
public class LongArticleParagraphMigrationTest {
    public static void main(final String[] args) throws Exception {
        final var properties = new Properties(); properties.setProperty("runtimeDatabase", "NONE");
        for (final String name : List.of("latkeProps", "localProps")) {
            final var field = Latkes.class.getDeclaredField(name); field.setAccessible(true); field.set(null, properties);
        }
        final var service = new LongArticleParagraphService();
        final var links = new ShortLinkQueryService();
        final var articles = new ArticleRows();
        inject(links, "articleRepository", articles); inject(service, "shortLinkQueryService", links);
        final String url = Latkes.getServePath() + "/article/1700000000001";
        final String source = "prefix " + url + " suffix";
        final String edited = source + "\n\nanother ordinary paragraph with no relation";
        final var legacy = service.extractMarkdown("10", source).getFirst();
        final var provisional = service.extractMarkdown("10", source, id -> null).getFirst();
        Assert.assertNotEquals(legacy.getId(), provisional.getId());
        final java.util.function.Function<String, LongArticleParagraphService.TitleSnapshot> titles = id ->
                new LongArticleParagraphService.TitleSnapshot("SECRET-title", false);
        try (final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (final var stmt = db.createStatement()) {
                stmt.execute("CREATE TABLE comment(oId TEXT PRIMARY KEY,commentOnArticleId TEXT,commentType INT,commentParagraphId TEXT,"
                        + "commentParagraphKind TEXT,commentParagraphIndex INT,commentParagraphSnapshot TEXT,commentParagraphStatus INT,"
                        + "commentSharpURL TEXT,commentContent TEXT,commentOriginalCommentId TEXT)");
            }
            insert(db, "1", legacy.getId(), "legacy snapshot", "");
            insert(db, "2", provisional.getId(), "provisional snapshot", "");
            insert(db, "3", provisional.getId(), "reply snapshot", "2");
            final var rows = new Rows(db); inject(service, "commentRepository", rows);
            try { service.migrateCommentsInCurrentTransaction("10", source, edited, titles); Assert.fail(); }
            catch (final IllegalStateException expected) { }
            transaction(rows, false, () -> service.migrateCommentsInCurrentTransaction("10", source, edited, titles));
            Assert.assertEquals(rows.find("2").getString(Comment.COMMENT_PARAGRAPH_ID), provisional.getId());
            Assert.assertEquals(rows.find("3").getString(Comment.COMMENT_PARAGRAPH_ID), provisional.getId());
            System.out.println("PASS actual migration requires caller transaction and rollback restores provisional root/reply associations");

            rows.failAt = 2;
            try { transaction(rows, true, () -> service.migrateCommentsInCurrentTransaction("10", source, edited, titles)); Assert.fail(); }
            catch (final RepositoryException expected) { }
            Assert.assertEquals(rows.find("2").getString(Comment.COMMENT_PARAGRAPH_ID), provisional.getId());
            rows.failAt = 0;
            transaction(rows, true, () -> service.migrateCommentsInCurrentTransaction("10", source, edited, titles));
            for (final String id : List.of("1", "2", "3")) {
                final var row = rows.find(id);
                Assert.assertEquals(row.getString(Comment.COMMENT_PARAGRAPH_ID), legacy.getId());
                Assert.assertEquals(row.getString(Comment.COMMENT_CONTENT), "body " + id);
                Assert.assertEquals(row.getString(Comment.COMMENT_SHARP_URL), "/article/10?paragraph=" + legacy.getId() + "#" + id);
                Assert.assertEquals(row.getInt(Comment.COMMENT_PARAGRAPH_STATUS), 0);
            }
            Assert.assertEquals(rows.find("2").getString(Comment.COMMENT_PARAGRAPH_SNAPSHOT), "provisional snapshot");
            Assert.assertEquals(rows.find("3").getString(Comment.COMMENT_ORIGINAL_COMMENT_ID), "2");
            System.out.println("PASS provisional roots/replies and legacy IDs migrate together; second-write failure rolls back and column whitelist preserves content/snapshots");

            transaction(rows, true, () -> service.migrateCommentsInCurrentTransaction("10", edited, "<p>completely different</p>", titles));
            for (final String id : List.of("1", "2", "3")) {
                Assert.assertEquals(rows.find(id).getInt(Comment.COMMENT_PARAGRAPH_STATUS), 1);
                Assert.assertEquals(rows.find(id).getString(Comment.COMMENT_CONTENT), "body " + id);
            }
            System.out.println("PASS genuinely removed paragraphs remain orphaned without deleting roots/replies or source content");

            articles.title = "SECRET</a></p><p>injected structure";
            try {
                transaction(rows, true, () -> service.migrateCommentsInCurrentTransaction("10", source, edited,
                        id -> new LongArticleParagraphService.TitleSnapshot(articles.title, false), true));
                Assert.fail("Expected unresolvable structure rejection");
            } catch (final org.b3log.symphony.util.ApiV3Exception expected) { Assert.assertEquals(expected.status, 409); }
            Assert.assertEquals(rows.updates, 0);
            Assert.assertEquals(rows.find("2").getString(Comment.COMMENT_CONTENT), "body 2");
            System.out.println("PASS strict v3 migration rejects ambiguous source structure before association writes and caller rolls back");
        }
    }
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private static void transaction(final Rows rows, final boolean commit, final Work work) throws Exception {
        rows.updates = 0; rows.db.setAutoCommit(false);
        try (final var caches = ArticlePublicationCaches.open(rows::hasTransactionBegun)) {
            try { work.run(); if (commit) rows.db.commit(); else rows.db.rollback(); }
            finally { if (!rows.db.getAutoCommit()) rows.db.rollback(); rows.db.setAutoCommit(true); }
        }
    }
    private static void inject(final Object bean, final String name, final Object value) throws Exception {
        final var field = bean.getClass().getDeclaredField(name); field.setAccessible(true); field.set(bean, value);
    }
    private static void insert(final Connection db, final String id, final String anchor, final String snapshot, final String parent) throws Exception {
        try (final var stmt = db.prepareStatement("INSERT INTO comment VALUES(?,'10',1,?,'paragraph',0,?,0,'old-url',?,?)")) {
            stmt.setString(1, id); stmt.setString(2, anchor); stmt.setString(3, snapshot); stmt.setString(4, "body " + id); stmt.setString(5, parent); stmt.executeUpdate();
        }
    }
    private static class ArticleRows extends ArticleRepository {
        String title = "SECRET-title";
        @Override public List<JSONObject> getList(final Query query) { return List.of(new JSONObject().put(Article.ARTICLE_TITLE, title)); }
    }
    private static class Rows extends CommentRepository {
        final Connection db; int updates, failAt;
        Rows(final Connection db) { this.db = db; }
        @Override public String getName() { return "comment"; }
        @Override public boolean hasTransactionBegun() {
            try { return !db.getAutoCommit(); } catch (final Exception e) { throw new IllegalStateException(e); }
        }
        JSONObject find(final String id) throws Exception { return select("SELECT * FROM comment WHERE oId=?", id).getFirst(); }
        @Override public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
            try (final var stmt = db.prepareStatement(sql.replace(" FOR UPDATE", ""))) {
                for (int i = 0; i < args.length; i++) stmt.setObject(i + 1, args[i]);
                final var rows = new ArrayList<JSONObject>();
                try (final var result = stmt.executeQuery()) {
                    while (result.next()) {
                        final var row = new JSONObject();
                        for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) row.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                        rows.add(row);
                    }
                }
                return rows;
            } catch (final Exception e) { throw new RepositoryException(e); }
        }
        @Override public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
            Assert.assertTrue(hasTransactionBegun());
            Assert.assertEquals(Set.of(fields), Set.of(Comment.COMMENT_PARAGRAPH_ID, Comment.COMMENT_PARAGRAPH_KIND,
                    Comment.COMMENT_PARAGRAPH_INDEX, Comment.COMMENT_PARAGRAPH_STATUS, Comment.COMMENT_SHARP_URL));
            if (++updates == failAt) throw new RepositoryException("fixture failure");
            try (final var stmt = db.prepareStatement("UPDATE comment SET " + String.join(",", java.util.Arrays.stream(fields).map(f -> f + "=?").toList()) + " WHERE oId=?")) {
                for (int i = 0; i < fields.length; i++) stmt.setObject(i + 1, row.opt(fields[i]));
                stmt.setString(fields.length + 1, id); stmt.executeUpdate();
            } catch (final Exception e) { throw new RepositoryException(e); }
        }
    }
}
