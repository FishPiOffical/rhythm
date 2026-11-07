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

public class CommentPublishPointsTest {
    public static void main(final String[] args) throws Exception {
        final var rates = new CommentPublishPoints.Rates(5, 1, 2, 3000, 11, 12);
        final var normal = CommentPublishPoints.plan(rates, "actor", "author", "sys", false, false, false);
        Assert.assertEquals(normal.requiredBalance(), 5L); Assert.assertEquals(normal.debits().size(), 1);
        Assert.assertEquals(normal.debits().getFirst(), new CommentPublishPoints.Debit("author", 11, 5));
        final var own = CommentPublishPoints.plan(rates, "actor", "actor", "sys", false, false, false);
        Assert.assertEquals(own.requiredBalance(), 1L);
        Assert.assertEquals(own.debits().getFirst(), new CommentPublishPoints.Debit("sys", 11, 1));
        System.out.println("PASS ordinary comment pays article author and self-comment uses existing system fee");
        final var privateOther = CommentPublishPoints.plan(rates, "actor", "author", "sys", false, false, true);
        Assert.assertEquals(privateOther.requiredBalance(), 7L); Assert.assertEquals(privateOther.debits().size(), 2);
        Assert.assertEquals(privateOther.debits().getFirst(), new CommentPublishPoints.Debit("author", 11, 5));
        Assert.assertEquals(privateOther.debits().getLast(), new CommentPublishPoints.Debit("sys", 12, 2));
        final var privateOwn = CommentPublishPoints.plan(rates, "actor", "actor", "sys", false, false, true);
        Assert.assertEquals(privateOwn.requiredBalance(), 3L);
        Assert.assertEquals(privateOwn.debits().getFirst(), new CommentPublishPoints.Debit("sys", 11, 1));
        Assert.assertEquals(privateOwn.debits().getLast(), new CommentPublishPoints.Debit("sys", 12, 2));
        System.out.println("PASS private comment adds separate system fee and preserves two transfer types even for self-comment");
        for (final boolean articleAnonymous : new boolean[]{false, true}) {
            final var anonymous = CommentPublishPoints.plan(rates, "actor", "author", "sys", true, articleAnonymous, true);
            Assert.assertEquals(anonymous.requiredBalance(), 3000L); Assert.assertTrue(anonymous.debits().isEmpty());
        }
        final var onAnonymousArticle = CommentPublishPoints.plan(rates, "actor", "author", "sys", false, true, true);
        Assert.assertEquals(onAnonymousArticle.requiredBalance(), 7L); Assert.assertTrue(onAnonymousArticle.debits().isEmpty());
        System.out.println("PASS anonymous publication preserves reservation-only rules and does not debit base or private fee");
        final var maximum = CommentPublishPoints.plan(new CommentPublishPoints.Rates(Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 3000, 11, 12),
                "actor", "author", "sys", true, true, true);
        Assert.assertEquals(maximum.requiredBalance(), 4294967294L); Assert.assertTrue(maximum.debits().isEmpty());
        final var zero = CommentPublishPoints.plan(new CommentPublishPoints.Rates(0, 0, 0, 0, 11, 12), "actor", "author", "sys", false, false, false);
        Assert.assertEquals(zero.debits().size(), 1); Assert.assertEquals(zero.debits().getFirst().amount(), 0);
        try { normal.debits().clear(); Assert.fail(); } catch (final UnsupportedOperationException expected) { }
        try { new CommentPublishPoints.Rates(-1, 0, 0, 0, 11, 12); Assert.fail(); } catch (final IllegalArgumentException expected) { }
        System.out.println("PASS long balance reservation avoids overflow, zero transfers remain represented and plans are immutable");
        for (final boolean fail : new boolean[]{true, false}) {
            try (final var store = new Store()) {
                try { CommentPublishPoints.charge(store, privateOther); Assert.fail(); } catch (final IllegalStateException expected) { }
                store.failSecond = fail; store.db.setAutoCommit(false);
                store.exec("INSERT INTO body VALUES('published')"); store.exec("UPDATE notice SET hasRead=1"); store.exec("INSERT INTO watching VALUES('article')");
                try {
                    final var payments = CommentPublishPoints.charge(store, privateOther);
                    Assert.assertEquals(payments.size(), 2);
                    Assert.assertEquals(payments.getFirst().recipient(), "author"); Assert.assertEquals(payments.getLast().recipient(), "sys");
                    Assert.assertTrue(store.inTransaction()); store.db.commit();
                } catch (final IllegalStateException expected) { Assert.assertTrue(fail); store.db.rollback(); }
                finally { store.db.setAutoCommit(true); }
                Assert.assertEquals(store.scalar("SELECT balance FROM users WHERE id='actor'"), fail ? 100 : 93);
                Assert.assertEquals(store.scalar("SELECT balance FROM users WHERE id='author'"), fail ? 0 : 5);
                Assert.assertEquals(store.scalar("SELECT COUNT(*) FROM transfer"), fail ? 0 : 2);
                Assert.assertEquals(store.scalar("SELECT COUNT(*) FROM body"), fail ? 0 : 1);
                Assert.assertEquals(store.scalar("SELECT hasRead FROM notice"), fail ? 0 : 1);
                Assert.assertEquals(store.scalar("SELECT COUNT(*) FROM watching"), fail ? 0 : 1);
            }
        }
        System.out.println("PASS real payment coordinator joins caller transaction; second-debit failure rolls back content/read/watch adapters and both accounts");
        try (final var store = new Store()) {
            store.db.setAutoCommit(false);
            try { CommentPublishPoints.charge(store, CommentPublishPoints.plan(rates, "actor", "author", "sys", true, false, true)); Assert.fail(); }
            catch (final CommentPublishPoints.InsufficientBalance expected) { store.db.rollback(); }
            Assert.assertEquals(store.scalar("SELECT COUNT(*) FROM transfer"), 0);
            store.exec("UPDATE users SET balance=4000 WHERE id='actor'");
            Assert.assertTrue(CommentPublishPoints.charge(store, CommentPublishPoints.plan(rates, "actor", "author", "sys", true, false, true)).isEmpty());
            Assert.assertEquals(store.scalar("SELECT balance FROM users WHERE id='actor'"), 4000); store.db.rollback();
        }
        System.out.println("PASS anonymous reservation is still enforced by payment coordinator without fabricating debits");

    }
    private static final class Store implements CommentPublishPoints.Store, AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        boolean failSecond;
        Store() throws Exception {
            exec("CREATE TABLE users(id TEXT,balance INT)"); exec("INSERT INTO users VALUES('actor',100),('author',0)");
            exec("CREATE TABLE transfer(recipient TEXT,amount INT)"); exec("CREATE TABLE body(content TEXT)");
            exec("CREATE TABLE notice(hasRead INT)"); exec("INSERT INTO notice VALUES(0)"); exec("CREATE TABLE watching(target TEXT)");
        }
        public boolean inTransaction() { try { return !db.getAutoCommit(); } catch (final Exception e) { throw new IllegalStateException(e); } }
        public int lockedBalance() throws Exception { return scalar("SELECT balance FROM users WHERE id='actor'"); }
        public String debit(final CommentPublishPoints.Debit debit) throws Exception {
            exec("UPDATE users SET balance=balance-? WHERE id='actor'", debit.amount());
            if (!"sys".equals(debit.recipient())) exec("UPDATE users SET balance=balance+? WHERE id=?", debit.amount(), debit.recipient());
            exec("INSERT INTO transfer VALUES(?,?)", debit.recipient(), debit.amount());
            return failSecond && "sys".equals(debit.recipient()) ? null : debit.recipient();
        }
        int scalar(final String sql) throws Exception { try (final var s = db.createStatement(); final var rows = s.executeQuery(sql)) { rows.next(); return rows.getInt(1); } }
        void exec(final String sql, final Object... args) throws Exception {
            try (final var s = db.prepareStatement(sql)) { for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]); s.executeUpdate(); }
        }
        public void close() throws Exception { db.close(); }
    }

}
