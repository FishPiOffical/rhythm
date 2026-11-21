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

import org.b3log.symphony.util.UserOnlineSynchronizer;
import org.b3log.symphony.util.UserOnlineTracker;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 可控时钟及存储故障回归，不访问真实数据库，也不代替 MySQL 事务验收。 */
public class UserOnlineTrackerTest {
    private static final class Clock {
        long wall = 1_000_000, nanos = -1_000_000;
        final UserOnlineTracker tracker = new UserOnlineTracker(() -> wall, () -> nanos);

        void advance(long millis) {
            wall += millis;
            nanos += TimeUnit.MILLISECONDS.toNanos(millis);
        }
    }

    @Test(timeOut = 60000)
    public void acknowledgedOfflineUsersResumeWithoutLosingRemainder() {
        final var c = new Clock();
        final Object connection = new Object();
        c.tracker.connected(connection, "a", Long.MAX_VALUE);
        c.advance(30_000);
        c.tracker.disconnected(connection);
        c.tracker.acknowledge(c.tracker.pending("a"));
        Assert.assertTrue(c.tracker.pending().isEmpty());
        c.tracker.connected(connection, "a", Long.MAX_VALUE);
        c.advance(30_000);
        c.tracker.connected(connection, "a", c.wall);
        Assert.assertFalse(c.tracker.isOnline("a"), "过期登记不得保留同一连接的旧租约");
        Assert.assertEquals(c.tracker.pending().getFirst().minutes(), 1L);
    }

    @Test(timeOut = 60000)
    public void mixedConnectionsCountUnionAndKeepRemainder() {
        final var c = new Clock();
        final Object legacy = new Object(), v3 = new Object();
        c.tracker.connected(legacy, "a", Long.MAX_VALUE);
        c.advance(30_000);
        c.tracker.connected(v3, "a", c.wall + 60_000);
        c.advance(10_000);
        c.tracker.disconnected(legacy);
        c.tracker.disconnected(legacy);
        Assert.assertTrue(c.tracker.isOnline("a"));
        Assert.assertEquals(c.tracker.pending("a").minutes(), 0L, "未结束区间不提前发奖");
        c.advance(30_000);
        c.tracker.disconnected(v3);
        Assert.assertEquals(c.tracker.pending("a").minutes(), 1L, "重叠的30秒只能计一次");
        c.advance(600_000);
        c.tracker.connected(legacy, "a", Long.MAX_VALUE);
        c.advance(50_000);
        c.tracker.disconnected(legacy);
        Assert.assertEquals(c.tracker.pending("a").minutes(), 2L, "跨连接保留10秒余数，不计离线间隔");
    }

    @Test(timeOut = 60000)
    public void expiryUsesDeadlineAndLateHeartbeatCannotRevive() {
        final var c = new Clock();
        final Object v3 = new Object();
        c.tracker.connected(v3, "a", c.wall + 60_000);
        c.advance(25_000);
        Assert.assertTrue(c.tracker.renew(v3, c.wall + 60_000));
        c.advance(120_000);
        Assert.assertFalse(c.tracker.isOnline("a"));
        Assert.assertFalse(c.tracker.renew(v3, c.wall + 60_000));
        Assert.assertEquals(c.tracker.pending("a").minutes(), 1L);
        Assert.assertEquals(c.tracker.pending("a").settledAt(), c.wall - 60_000);
        c.tracker.disconnected(v3);
        Assert.assertEquals(c.tracker.pending("a").minutes(), 1L);
    }

    @Test(timeOut = 60000)
    public void expiredGapOwnerChangeAndStopAreIdempotent() {
        final var c = new Clock();
        final Object connection = new Object();
        c.tracker.connected(connection, "a", c.wall + 60_000);
        c.advance(180_000);
        c.tracker.connected(connection, "a", Long.MAX_VALUE);
        c.advance(60_000);
        c.tracker.connected(connection, "b", Long.MAX_VALUE);
        Assert.assertFalse(c.tracker.isOnline("a"));
        Assert.assertEquals(c.tracker.pending("a").minutes(), 2L);
        c.advance(60_000);
        c.tracker.stop();
        final var snapshot = c.tracker.pending("b");
        Assert.assertEquals(snapshot.minutes(), 1L);
        Assert.assertFalse(snapshot.online());
        c.advance(60_000);
        c.tracker.stop();
        c.tracker.disconnected(connection);
        Assert.assertEquals(c.tracker.pending("b"), snapshot);
    }

    @Test(timeOut = 60000)
    public void longConnectionAndWallClockRollback() {
        final var c = new Clock();
        final Object connection = new Object();
        c.tracker.connected(connection, "a", Long.MAX_VALUE);
        c.advance(TimeUnit.DAYS.toMillis(30));
        c.wall -= TimeUnit.DAYS.toMillis(31);
        c.tracker.disconnected(connection);
        Assert.assertEquals(c.tracker.pending("a").minutes(), TimeUnit.DAYS.toMinutes(30));
    }

    @Test(timeOut = 60000)
    public void rollbackAndAmbiguousCommitRetrySameTarget() throws Exception {
        for (final boolean committed : new boolean[]{false, true}) {
            final var c = new Clock();
            final Object connection = new Object();
            final int[] balance = {9}, events = {0};
            final boolean[] fail = {true};
            c.tracker.connected(connection, "a", Long.MAX_VALUE);
            c.advance(60_000);
            c.tracker.disconnected(connection);
            final var sync = new UserOnlineSynchronizer(c.tracker, (id, online, target, time) -> {
                final int after = target.applyAsInt(balance[0]);
                if (committed || !fail[0]) {
                    if (after > balance[0]) events[0]++;
                    balance[0] = after;
                }
                if (fail[0]) {
                    fail[0] = false;
                    throw new IllegalStateException("injected database failure");
                }
            });
            try {
                sync.synchronize("a");
                Assert.fail("应暴露存储失败");
            } catch (IllegalStateException expected) {
                Assert.assertNotNull(c.tracker.pending("a"));
            }
            sync.synchronize("a");
            sync.synchronize("a");
            Assert.assertEquals(balance[0], 10);
            Assert.assertEquals(events[0], 1, "提交结果未知时也不能重复职业事件");
            Assert.assertNull(c.tracker.pending("a"));
        }
    }

    @Test(timeOut = 60000)
    public void retryKeepsNewMinutesAndUsesCurrentFlag() throws Exception {
        final var c = new Clock();
        final Object connection = new Object();
        final int[] balance = {100};
        final boolean[] fail = {true}, flag = {false};
        final var sync = new UserOnlineSynchronizer(c.tracker, (id, online, target, time) -> {
            if (fail[0]) {
                target.applyAsInt(balance[0]);
                fail[0] = false;
                throw new IllegalStateException("rollback");
            }
            balance[0] = target.applyAsInt(balance[0]);
            flag[0] = online;
        });
        c.tracker.connected(connection, "a", Long.MAX_VALUE);
        c.advance(60_000);
        c.tracker.disconnected(connection);
        try {
            sync.synchronize("a");
            Assert.fail();
        } catch (IllegalStateException expected) {
            // 离线结算失败后用户重连，旧任务不能永久写回离线。
        }
        c.tracker.connected(connection, "a", Long.MAX_VALUE);
        sync.synchronize("a");
        Assert.assertTrue(flag[0]);
        Assert.assertEquals(balance[0], 101);
        c.advance(120_000);
        c.tracker.disconnected(connection);
        sync.synchronize("a");
        Assert.assertFalse(flag[0]);
        Assert.assertEquals(balance[0], 103);
    }

    @Test(timeOut = 60000)
    public void slowUserDoesNotBlockOtherUsersOrConnections() throws Exception {
        final var c = new Clock();
        final var entered = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var otherSaved = new AtomicBoolean();
        final var sync = new UserOnlineSynchronizer(c.tracker, (id, online, target, time) -> {
            if (id.equals("a")) {
                entered.countDown();
                Assert.assertTrue(release.await(5, TimeUnit.SECONDS));
            } else {
                otherSaved.set(true);
            }
            target.applyAsInt(0);
        });
        final Object a = new Object(), b = new Object();
        c.tracker.connected(a, "a", Long.MAX_VALUE);
        c.tracker.connected(b, "b", Long.MAX_VALUE);
        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var job = executor.submit(() -> { sync.synchronize("a"); return null; });
            try {
                Assert.assertTrue(entered.await(5, TimeUnit.SECONDS));
                sync.synchronize("a");
                sync.synchronize("b");
                Assert.assertTrue(otherSaved.get());
                c.tracker.disconnected(a);
                Assert.assertFalse(c.tracker.isOnline("a"));
            } finally {
                release.countDown();
            }
            job.get(5, TimeUnit.SECONDS);
            Assert.assertNull(c.tracker.pending("a"));
        }
    }
}
