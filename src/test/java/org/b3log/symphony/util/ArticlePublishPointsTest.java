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
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/** Actual transition with an SQLite transaction adapter; does not run the full points service or MySQL locks. */
public class ArticlePublishPointsTest {
    private static final ArticlePublishPoints.Rates RATES = new ArticlePublishPoints.Rates(10, 20, 30, 100, 1, 2, 3);
    public static void main(final String[] args) throws Exception {
        final var publicPlan = ArticlePublishPoints.plan(RATES, false, true, 81, 50, 100);
        Assert.assertEquals(publicPlan.requiredBalance(), 199L);
        Assert.assertEquals(publicPlan.debits(), List.of(new ArticlePublishPoints.Debit(1, 19),
                new ArticlePublishPoints.Debit(2, 20), new ArticlePublishPoints.Debit(3, 30)));
        final var anonymous = ArticlePublishPoints.plan(RATES, true, true, 81, 50, 100);
        Assert.assertEquals(anonymous.requiredBalance(), 100L); Assert.assertTrue(anonymous.debits().isEmpty());
        final var qna = ArticlePublishPoints.plan(RATES, false, false, 0, 0, 100);
        Assert.assertEquals(qna.requiredBalance(), 110L); Assert.assertEquals(qna.debits().size(), 1);
        try { publicPlan.debits().clear(); Assert.fail(); } catch (final UnsupportedOperationException expected) { }
        System.out.println("PASS distinct balance reservation/charges, fixed reward fee, QnA reserve and anonymous threshold");

        final var large = ArticlePublishPoints.plan(RATES, false, true, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        Assert.assertEquals(large.requiredBalance(), 2L * Integer.MAX_VALUE + 40);
        try { ArticlePublishPoints.plan(RATES, false, false, Long.MAX_VALUE, 0, 0); Assert.fail(); } catch (final ArithmeticException expected) { }
        for (final long followers : List.of(-1L, -100L)) {
            try { ArticlePublishPoints.plan(RATES, false, false, followers, 0, 0); Assert.fail(); } catch (final IllegalArgumentException expected) { }
        }
        try { ArticlePublishPoints.plan(RATES, false, false, 0, -1, 0); Assert.fail(); } catch (final IllegalArgumentException expected) { }
        try { ArticlePublishPoints.plan(RATES, true, false, 0, 0, -1); Assert.fail(); } catch (final IllegalArgumentException expected) { }
        System.out.println("PASS reservation overflow, transfer capacity and negative input boundaries");

        try (final Fixture f = new Fixture()) {
            try { ArticlePublishPoints.charge(f, publicPlan); Assert.fail(); } catch (final IllegalStateException expected) { }
            Assert.assertFalse(f.locked);
            f.begin(); f.content();
            final var transfers = ArticlePublishPoints.charge(f, publicPlan);
            Assert.assertEquals(transfers.size(), 3); Assert.assertFalse(f.db.getAutoCommit());
            Assert.assertEquals(f.balance(), 931); Assert.assertEquals(f.count("ledger"), 3);
            f.db.commit(); f.db.setAutoCommit(true);
            Assert.assertEquals(f.count("article"), 1);
            System.out.println("PASS transaction required, locked balance and caller-owned content/debit commit");
        }
        try (final Fixture f = new Fixture()) {
            f.begin(); f.content(); f.failAt = 2;
            try { ArticlePublishPoints.charge(f, publicPlan); Assert.fail(); } catch (final IllegalStateException expected) { f.db.rollback(); }
            Assert.assertEquals(f.balance(), 1000); Assert.assertEquals(f.count("ledger"), 0); Assert.assertEquals(f.count("article"), 0);
            System.out.println("PASS partial debit failure propagates and caller rolls back content, balance and ledger together");
        }
        try (final Fixture f = new Fixture()) {
            f.begin(); f.exec("UPDATE account SET balance=198");
            try { ArticlePublishPoints.charge(f, publicPlan); Assert.fail(); } catch (final ArticlePublishPoints.InsufficientBalance expected) { }
            Assert.assertEquals(f.count("ledger"), 0); f.db.rollback();
            f.exec("UPDATE account SET balance=100");
            Assert.assertTrue(ArticlePublishPoints.charge(f, anonymous).isEmpty());
            Assert.assertEquals(f.balance(), 100); Assert.assertEquals(f.count("ledger"), 0);
            f.db.rollback();
            try { ArticlePublishPoints.charge(f, large); Assert.fail(); } catch (final ArticlePublishPoints.InsufficientBalance expected) { }
            Assert.assertEquals(f.count("ledger"), 0);
            System.out.println("PASS insufficient reservation has no debit, large inputs cannot wrap and anonymous publication stays free");
        }
        try (final Fixture f = new Fixture()) {
            final var smallPrice = ArticlePublishPoints.plan(RATES, false, false, 0, 1, 0);
            Assert.assertEquals(smallPrice.requiredBalance(), 11L); Assert.assertEquals(smallPrice.minimumBalance(), 30L);
            f.begin(); f.exec("UPDATE account SET balance=11");
            try { ArticlePublishPoints.charge(f, smallPrice); Assert.fail(); } catch (final ArticlePublishPoints.InsufficientBalance expected) { }
            Assert.assertEquals(f.count("ledger"), 0); Assert.assertEquals(f.balance(), 11); f.db.rollback();
            System.out.println("PASS fixed activation fee exceeding reward price is fully funded before the first debit");
        }
    }
    private static final class Fixture implements ArticlePublishPoints.Store, AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        boolean locked; int failAt = -1, calls;
        Fixture() throws Exception {
            exec("CREATE TABLE account(balance INT)"); exec("INSERT INTO account VALUES(1000)");
            exec("CREATE TABLE ledger(id INT,type INT,amount INT)"); exec("CREATE TABLE article(id INT)");
        }
        void begin() throws Exception { db.setAutoCommit(false); locked = false; }
        void content() throws Exception { exec("INSERT INTO article VALUES(1)"); }
        public boolean inTransaction() { try { return !db.getAutoCommit(); } catch (final Exception e) { throw new RuntimeException(e); } }
        public int lockAuthorBalance() throws Exception { Assert.assertTrue(inTransaction()); locked = true; return balance(); }
        public String debit(final int type, final int amount) throws Exception {
            Assert.assertTrue(locked); Assert.assertTrue(inTransaction());
            exec("UPDATE account SET balance=balance-?", amount);
            exec("INSERT INTO ledger VALUES(?,?,?)", ++calls, type, amount);
            return calls == failAt || balance() < 0 ? null : "transfer-" + calls;
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
