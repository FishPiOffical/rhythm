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
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Real lock pool and isolated SQLite adapter; no application transfer service or MySQL is exercised. */
public class AccountPairLocksTest {
    public static void main(final String[] args) throws Exception {
        final String actor = UUID.randomUUID().toString(), recipient = UUID.randomUUID().toString();
        final var actorRecipient = AccountPairLocks.lock(actor, recipient);
        final var actorSystem = AccountPairLocks.lock(actor, "sys");
        Assert.assertSame(actorRecipient, AccountPairLocks.lock(recipient, actor));
        Assert.assertNotSame(actorRecipient, actorSystem);
        final var pairs = List.of(new AccountPairLocks.Pair(actor, recipient), new AccountPairLocks.Pair(actor, "sys"),
                new AccountPairLocks.Pair(recipient, actor));
        Assert.assertEquals(AccountPairLocks.execute(pairs, () -> {
            Assert.assertEquals(actorRecipient.getHoldCount(), 1); Assert.assertEquals(actorSystem.getHoldCount(), 1);
            actorRecipient.lock(); try { Assert.assertEquals(actorRecipient.getHoldCount(), 2); } finally { actorRecipient.unlock(); }
            return "done";
        }), "done");
        Assert.assertFalse(actorRecipient.isLocked()); Assert.assertFalse(actorSystem.isLocked());
        System.out.println("PASS canonical reversed pairs, duplicate elimination and reentrant legacy transfer lock share one pool");
        final var expected = new Exception("operation failed");
        try { AccountPairLocks.execute(pairs, () -> { throw expected; }); Assert.fail(); }
        catch (final Exception e) { Assert.assertSame(e, expected); }
        Assert.assertFalse(actorRecipient.isLocked()); Assert.assertFalse(actorSystem.isLocked());
        AccountPairLocks.execute(pairs, () -> {
            Assert.assertTrue(AccountPairLocks.execute(List.of(pairs.getFirst()), actorRecipient::isHeldByCurrentThread));
            try { AccountPairLocks.execute(List.of(new AccountPairLocks.Pair(actor, "new")), () -> { Assert.fail(); return null; }); Assert.fail(); }
            catch (final IllegalStateException denied) { Assert.assertTrue(actorSystem.isHeldByCurrentThread()); }
            return null;
        });
        actorRecipient.lock();
        try {
            try { AccountPairLocks.execute(pairs, () -> { Assert.fail(); return null; }); Assert.fail(); }
            catch (final IllegalStateException denied) { Assert.assertEquals(actorRecipient.getHoldCount(), 1); }
        } finally { actorRecipient.unlock(); }
        AccountPairLocks.execute(List.of(new AccountPairLocks.Pair("fresh", "set")), () -> null);
        System.out.println("PASS failure cleanup, nested subset reuse and lock-set expansion rejection without stale thread state");
        AccountPairLocks.execute(pairs, () -> {
            Assert.assertFalse(otherThreadCanAcquire(actorRecipient)); Assert.assertFalse(otherThreadCanAcquire(actorSystem));
            Assert.assertTrue(otherThreadCanAcquire(AccountPairLocks.lock("independent", "pair")));
            return null;
        });
        Assert.assertTrue(otherThreadCanAcquire(actorRecipient)); Assert.assertTrue(otherThreadCanAcquire(actorSystem));
        System.out.println("PASS legacy single-pair contenders block on either held pair while unrelated pairs remain independent");
        final var pool = Executors.newFixedThreadPool(64, Thread.ofPlatform().daemon(true).factory());
        final var ready = new CountDownLatch(64); final var start = new CountDownLatch(1);
        final var active = new AtomicInteger(); final var completed = new AtomicInteger();
        try {
            final var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 64; i++) {
                final var inputs = i % 2 == 0 ? pairs : List.of(pairs.get(1), pairs.get(2), pairs.getFirst());
                tasks.add(pool.submit(() -> {
                    ready.countDown(); start.await();
                    return AccountPairLocks.execute(inputs, () -> {
                        Assert.assertEquals(active.incrementAndGet(), 1);
                        try {
                            Assert.assertTrue(actorRecipient.isHeldByCurrentThread()); Assert.assertTrue(actorSystem.isHeldByCurrentThread());
                            Thread.yield(); completed.incrementAndGet(); return null;
                        } finally { active.decrementAndGet(); }
                    });
                }));
            }
            Assert.assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            for (final var task : tasks) task.get(10, TimeUnit.SECONDS);
            Assert.assertEquals(completed.get(), 64);
        } finally { start.countDown(); pool.shutdownNow(); }
        System.out.println("PASS 64 callers with reversed destination ordering complete without overlapping pair ownership");
        try (final var db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (final var s = db.createStatement()) { s.execute("CREATE TABLE users(id TEXT PRIMARY KEY,balance INT)"); s.execute("INSERT INTO users VALUES('actor',100),('author',0)"); }
            final var databasePairs = List.of(new AccountPairLocks.Pair("actor", "author"), new AccountPairLocks.Pair("actor", "sys"));
            for (final boolean fail : new boolean[]{true, false}) {
                try {
                    AccountPairLocks.execute(databasePairs, () -> {
                        db.setAutoCommit(false);
                        try {
                            try (final var s = db.createStatement()) {
                                s.executeUpdate("UPDATE users SET balance=balance-5 WHERE id='actor'");
                                s.executeUpdate("UPDATE users SET balance=balance+5 WHERE id='author'");
                                Assert.assertFalse(otherThreadCanAcquire(AccountPairLocks.lock("author", "actor")));
                                Assert.assertFalse(otherThreadCanAcquire(AccountPairLocks.lock("sys", "actor")));
                                s.executeUpdate("UPDATE users SET balance=balance-2 WHERE id='actor'");
                            }
                            if (fail) throw expected;
                            db.commit();
                        } finally {
                            if (!db.getAutoCommit()) { db.rollback(); db.setAutoCommit(true); }
                        }
                        Assert.assertTrue(AccountPairLocks.lock("actor", "sys").isHeldByCurrentThread());
                        return null;
                    });
                    Assert.assertFalse(fail);
                } catch (final Exception e) { Assert.assertTrue(fail); Assert.assertSame(e, expected); }
                try (final var s = db.createStatement(); final var rows = s.executeQuery("SELECT balance FROM users ORDER BY id")) {
                    Assert.assertTrue(rows.next()); Assert.assertEquals(rows.getInt(1), fail ? 100 : 93);
                    Assert.assertTrue(rows.next()); Assert.assertEquals(rows.getInt(1), fail ? 0 : 5);
                }
                Assert.assertTrue(otherThreadCanAcquire(AccountPairLocks.lock("actor", "author")));
                Assert.assertTrue(otherThreadCanAcquire(AccountPairLocks.lock("actor", "sys")));
            }
        }
        System.out.println("PASS two-destination debit adapter rolls back or commits together while both legacy pair locks remain held");
    }
    private static boolean otherThreadCanAcquire(final ReentrantLock lock) throws Exception {
        final var pool = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon(true).factory());
        try {
            return pool.submit(() -> { if (!lock.tryLock()) return false; try { return true; } finally { lock.unlock(); } }).get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
}
