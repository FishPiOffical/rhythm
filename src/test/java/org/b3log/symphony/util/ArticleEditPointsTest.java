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

/** Real fee/charge helpers with an SQLite adapter, not the full article editor or MySQL locks. */
public class ArticleEditPointsTest {
    public static void main(final String[] args) throws Exception {
        final int fee = ArticleEditPoints.fee(5, 81, 2, 3);
        Assert.assertEquals(fee, 24);
        Assert.assertFalse(ArticleEditPoints.pastFreeWindow(1000, 301000));
        Assert.assertTrue(ArticleEditPoints.pastFreeWindow(1000, 301001));
        final var free = ArticleEditPoints.plan(false, false, fee, false, 30, 1, 2);
        Assert.assertEquals(free.minimumBalance(), 24L); Assert.assertTrue(free.debits().isEmpty());
        final var charged = ArticleEditPoints.plan(false, true, fee, true, 30, 1, 2);
        Assert.assertEquals(charged.debits().size(), 2); Assert.assertEquals(charged.minimumBalance(), 54L);
        final var activate = ArticleEditPoints.plan(false, false, fee, true, 30, 1, 2);
        Assert.assertEquals(activate.debits().size(), 1); Assert.assertEquals(activate.minimumBalance(), 30L);
        final var anonymous = ArticleEditPoints.plan(true, true, fee, true, 30, 1, 2);
        Assert.assertEquals(anonymous.minimumBalance(), 0L); Assert.assertTrue(anonymous.debits().isEmpty());
        System.out.println("PASS edit fee formula, exact five-minute boundary, grace-window balance threshold and anonymous exemption");
        try { ArticleEditPoints.fee(Integer.MAX_VALUE, 1, 0, 0); Assert.fail(); } catch (final ArithmeticException expected) { }
        try { ArticleEditPoints.fee(0, 0, Long.MAX_VALUE, 1); Assert.fail(); } catch (final ArithmeticException expected) { }
        try { ArticleEditPoints.fee(0, 0, Long.MAX_VALUE / 2 + 1, 0); Assert.fail(); } catch (final ArithmeticException expected) { }
        try { ArticleEditPoints.fee(0, -1, 0, 0); Assert.fail(); } catch (final IllegalArgumentException expected) { }
        System.out.println("PASS checked collection/watcher arithmetic and transfer integer capacity");
        try (final Store s = new Store()) {
            s.db.setAutoCommit(false);
            Assert.assertEquals(ArticlePublishPoints.charge(s, charged).size(), 2);
            Assert.assertEquals(s.balance(), 46); s.db.commit();
            Assert.assertEquals(s.scalar("SELECT COUNT(*) FROM ledger"), 2);
            System.out.println("PASS edit and reward activation charged in caller-owned transaction");
        }
        try (final Store s = new Store()) {
            s.failSecond = true; s.db.setAutoCommit(false);
            try { ArticlePublishPoints.charge(s, charged); Assert.fail(); } catch (final IllegalStateException expected) { s.db.rollback(); }
            Assert.assertEquals(s.balance(), 100); Assert.assertEquals(s.scalar("SELECT COUNT(*) FROM ledger"), 0);
            System.out.println("PASS second edit debit failure rolls all fees and ledger rows back");
        }
        try (final Store s = new Store()) {
            s.exec("UPDATE account SET balance=23"); s.db.setAutoCommit(false);
            try { ArticlePublishPoints.charge(s, free); Assert.fail(); } catch (final ArticlePublishPoints.InsufficientBalance expected) { s.db.rollback(); }
            s.exec("UPDATE account SET balance=24");
            Assert.assertTrue(ArticlePublishPoints.charge(s, free).isEmpty()); Assert.assertEquals(s.balance(), 24);
            try { ArticlePublishPoints.charge(s, activate); Assert.fail(); } catch (final ArticlePublishPoints.InsufficientBalance expected) { }
            Assert.assertEquals(s.scalar("SELECT COUNT(*) FROM ledger"), 0); s.db.rollback();
            s.exec("UPDATE account SET balance=-10");
            Assert.assertTrue(ArticlePublishPoints.charge(s, anonymous).isEmpty());
            Assert.assertEquals(s.balance(), -10); Assert.assertEquals(s.scalar("SELECT COUNT(*) FROM ledger"), 0);
            s.db.rollback();
            System.out.println("PASS free-window reservation retained and activation cannot leave partial charges");
        }
    }
    private static final class Store implements ArticlePublishPoints.Store, AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:"); boolean locked, failSecond; int calls;
        Store() throws Exception { exec("CREATE TABLE account(balance INT)"); exec("INSERT INTO account VALUES(100)"); exec("CREATE TABLE ledger(type INT,amount INT)"); }
        public boolean inTransaction() { try { return !db.getAutoCommit(); } catch (final Exception e) { throw new RuntimeException(e); } }
        public int lockAuthorBalance() throws Exception { locked = true; return balance(); }
        public String debit(final int type, final int amount) throws Exception {
            Assert.assertTrue(locked); exec("UPDATE account SET balance=balance-?", amount); exec("INSERT INTO ledger VALUES(?,?)", type, amount);
            return ++calls == 2 && failSecond ? null : "transfer-" + calls;
        }
        int balance() throws Exception { return scalar("SELECT balance FROM account"); }
        int scalar(final String sql) throws Exception { try (final var s = db.createStatement(); final var r = s.executeQuery(sql)) { r.next(); return r.getInt(1); } }
        void exec(final String sql, final Object... values) throws Exception {
            try (final var s = db.prepareStatement(sql)) { for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]); s.executeUpdate(); }
        }
        public void close() throws Exception { db.close(); }
    }
}
