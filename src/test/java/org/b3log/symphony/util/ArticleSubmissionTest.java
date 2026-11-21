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

import org.testng.Assert;
import org.b3log.symphony.model.Article;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZoneId;

/** Actual submission transition and history SQL; SQLite adapters do not model MySQL row locks or full services. */
public class ArticleSubmissionTest {
    private static final long NOW = Instant.parse("2026-10-16T01:00:00Z").toEpochMilli();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static void main(final String[] args) throws Exception {
        try (final Fixture f = new Fixture()) {
            try { ArticleSubmission.apply(f, () -> NOW, ZONE, false); Assert.fail(); } catch (final IllegalStateException expected) { }
            f.begin();
            try { ArticleSubmission.apply(f, () -> NOW, ZONE, false); Assert.fail(); } catch (final IllegalStateException expected) { }
            try (final var ignored = ArticlePublicationCaches.open(f::inTransaction)) {
                final var result = ArticleSubmission.apply(f, () -> { Assert.assertTrue(f.locked); return NOW; }, ZONE, false);
                Assert.assertEquals(result.publication(), "published"); Assert.assertEquals(result.weeklyTransferId(), "weekly");
                Assert.assertEquals(f.balance(), 980); Assert.assertEquals(f.count("ledger"), 2); Assert.assertEquals(f.count("published"), 1);
                Assert.assertTrue(f.inTransaction()); f.commit();
            }
            System.out.println("PASS required transaction/cache boundary, locked reads, credit before publication and caller-owned commit");
        }
        for (final boolean creditFailure : new boolean[]{false, true}) {
            try (final Fixture f = new Fixture()) {
                f.failCredit = creditFailure; f.failPublish = !creditFailure;
                f.begin();
                try (final var ignored = ArticlePublicationCaches.open(f::inTransaction)) {
                    try { ArticleSubmission.apply(f, () -> NOW, ZONE, false); Assert.fail(); }
                    catch (final IllegalStateException expected) { f.rollback(); }
                }
                Assert.assertEquals(f.balance(), 0); Assert.assertEquals(f.count("ledger"), 0); Assert.assertEquals(f.count("published"), 0);
                if (creditFailure) Assert.assertFalse(f.published);
            }
        }
        System.out.println("PASS failed publication or partial weekly credit rolls back reward, content and ledger together");
        try (final Fixture f = new Fixture()) {
            f.phone = false; f.begin();
            try (final var ignored = ArticlePublicationCaches.open(f::inTransaction)) {
                try { ArticleSubmission.apply(f, () -> NOW, ZONE, false); Assert.fail(); }
                catch (final ArticleSubmissionPolicy.Rejected e) { Assert.assertEquals(e.code, "phone_required"); f.rollback(); }
            }
            Assert.assertEquals(f.balance(), 0); Assert.assertEquals(f.count("ledger"), 0); Assert.assertFalse(f.published);
            f.phone = true; f.existing = true; f.exec("UPDATE account SET balance=100"); f.begin();
            try (final var ignored = ArticlePublicationCaches.open(f::inTransaction)) {
                final var result = ArticleSubmission.apply(f, () -> NOW, ZONE, false); Assert.assertNull(result.weeklyTransferId()); f.commit();
            }
            Assert.assertEquals(f.balance(), 80); Assert.assertEquals(f.count("ledger"), 1);
            System.out.println("PASS policy rejection has no writes and existing weekly title does not grant another reward");
        }
        try (final Fixture f = new Fixture()) {
            f.exec("CREATE TABLE history(oId BIGINT,articleAuthorId TEXT,articleAnonymous INT,articleStatus INT,articleCreateTime BIGINT)");
            f.exec("INSERT INTO history VALUES(?,?,?,?,?)", 111L, "owner", Article.ARTICLE_ANONYMOUS_C_PUBLIC, Article.ARTICLE_STATUS_C_VALID, 20L);
            f.exec("INSERT INTO history VALUES(?,?,?,?,?)", 222L, "owner", Article.ARTICLE_ANONYMOUS_C_PUBLIC, Article.ARTICLE_STATUS_C_VALID, 10L);
            f.exec("INSERT INTO history VALUES(?,?,?,?,?)", 333L, "other", Article.ARTICLE_ANONYMOUS_C_PUBLIC, Article.ARTICLE_STATUS_C_VALID, 99L);
            f.exec("INSERT INTO history VALUES(?,?,?,?,?)", 444L, "owner", Article.ARTICLE_ANONYMOUS_C_ANONYMOUS, Article.ARTICLE_STATUS_C_VALID, 99L);
            f.exec("INSERT INTO history VALUES(?,?,?,?,?)", 555L, "owner", Article.ARTICLE_ANONYMOUS_C_PUBLIC, Article.ARTICLE_STATUS_C_INVALID, 99L);
            try (final var query = f.db.prepareStatement(ArticleSubmissionPolicy.latestPublicSql("history", false))) {
                query.setString(1, "owner"); query.setInt(2, Article.ARTICLE_ANONYMOUS_C_PUBLIC); query.setInt(3, Article.ARTICLE_STATUS_C_INVALID);
                try (final var rows = query.executeQuery()) { Assert.assertTrue(rows.next()); Assert.assertEquals(rows.getLong(1), 111L); Assert.assertFalse(rows.next()); }
                query.setString(1, "owner' OR '1'='1"); try (final var rows = query.executeQuery()) { Assert.assertFalse(rows.next()); }
            }
            Assert.assertTrue(ArticleSubmissionPolicy.latestPublicSql("history", true).endsWith(" FOR UPDATE"));
            System.out.println("PASS bound history SQL isolates author, public/non-invalid status and create-time order while returning article ID");
        }
    }
    private static final class Fixture implements ArticleSubmission.Store<String>, AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        boolean locked, phone = true, failCredit, failPublish, published, existing;
        Fixture() throws Exception {
            exec("CREATE TABLE account(balance INT)"); exec("INSERT INTO account VALUES(0)");
            exec("CREATE TABLE ledger(type TEXT,amount INT)"); exec("CREATE TABLE published(id INT)");
        }
        void begin() throws Exception { db.setAutoCommit(false); locked = false; }
        void commit() throws Exception { db.commit(); db.setAutoCommit(true); }
        void rollback() throws Exception { db.rollback(); db.setAutoCommit(true); }
        public boolean inTransaction() { try { return !db.getAutoCommit(); } catch (final Exception e) { throw new RuntimeException(e); } }
        public void lockPublicationState() { Assert.assertTrue(inTransaction()); locked = true; }
        public ArticleSubmissionPolicy.Submission submission() {
            Assert.assertTrue(locked); return new ArticleSubmissionPolicy.Submission(phone, "摸鱼周报 20261016", "文".repeat(128), "日常");
        }
        public ArticleSubmissionPolicy.History history(final ArticleSubmissionPolicy.Submission request) {
            Assert.assertTrue(locked); return new ArticleSubmissionPolicy.History(NOW - 86_400_000, existing);
        }
        public String creditWeekly(final int amount, final long now) throws Exception {
            Assert.assertTrue(locked); Assert.assertEquals(amount, 1000); Assert.assertEquals(now, NOW);
            exec("UPDATE account SET balance=balance+?", amount); exec("INSERT INTO ledger VALUES('weekly',?)", amount);
            return failCredit ? null : "weekly";
        }
        public String publish() throws Exception {
            published = true; Assert.assertTrue(locked); exec("INSERT INTO published VALUES(1)");
            if (failPublish) throw new IllegalStateException("simulated publication failure");
            exec("UPDATE account SET balance=balance-20"); exec("INSERT INTO ledger VALUES('publication',20)"); return "published";
        }
        int balance() throws Exception { return scalar("SELECT balance FROM account"); }
        int count(final String table) throws Exception { return scalar("SELECT COUNT(*) FROM " + table); }
        int scalar(final String sql) throws Exception { try (final var s = db.createStatement(); final var r = s.executeQuery(sql)) { r.next(); return r.getInt(1); } }
        void exec(final String sql, final Object... values) throws Exception {
            try (final var s = db.prepareStatement(sql)) { for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]); s.executeUpdate(); }
        }
        public void close() throws Exception { db.close(); }
    }
}
