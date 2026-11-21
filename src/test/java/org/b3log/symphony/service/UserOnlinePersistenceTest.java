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

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.UserOnlineSynchronizer;
import org.b3log.symphony.util.UserOnlineTracker;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** 执行真实持久化方法；仓储替身只模拟事务，不连接生产或测试数据库。 */
public class UserOnlinePersistenceTest {
    private final List<Object> previousProperties = new ArrayList<>();

    @BeforeClass
    public void isolateDatabase() throws Exception {
        for (final String name : List.of("latkeProps", "localProps")) {
            final var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            previousProperties.add(field.get(null));
            final Properties properties = new Properties();
            properties.setProperty("runtimeDatabase", "NONE");
            field.set(null, properties);
        }
    }

    @AfterClass(alwaysRun = true)
    public void restoreProperties() throws Exception {
        int index = 0;
        for (final String name : List.of("latkeProps", "localProps")) {
            final var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, previousProperties.get(index++));
        }
    }

    @Test(timeOut = 60000)
    public void locksCurrentRowAndPublishesCacheOnlyAfterCommit() throws Exception {
        final var f = new Fixture();
        f.service.persistOnlineState("actor", false, before -> before + 1, 1234);
        Assert.assertEquals(f.calls, List.of("begin", "lock", "update", "event:9:10:1234", "commit", "refresh"));
        Assert.assertEquals(f.row.getInt(UserExt.ONLINE_MINUTE), 10);
        Assert.assertFalse(f.row.getBoolean(UserExt.USER_ONLINE_FLAG));
        Assert.assertEquals(f.events, List.of("9:10"));
        f.calls.clear();
        f.service.persistOnlineState("actor", false, before -> before, 1234);
        Assert.assertEquals(f.calls, List.of("begin", "lock", "event:10:10:1234", "commit", "refresh"));
        Assert.assertEquals(f.events.size(), 1, "不增加分钟时不能重复产生职业事件");
    }

    @Test(timeOut = 60000)
    public void eventAndCommitFailuresRollbackWithoutPublishingCache() throws Exception {
        for (final String fault : List.of("event", "commit")) {
            final var f = new Fixture();
            f.fault = fault;
            try {
                f.service.persistOnlineState("actor", false, before -> before + 1, 1234);
                Assert.fail("应向同步器暴露失败");
            } catch (RepositoryException | IllegalStateException expected) {
                Assert.assertFalse(f.active);
                Assert.assertTrue(f.calls.contains("rollback"));
                Assert.assertFalse(f.calls.contains("evict"));
                Assert.assertEquals(f.row.getInt(UserExt.ONLINE_MINUTE), 9);
                Assert.assertTrue(f.row.getBoolean(UserExt.USER_ONLINE_FLAG));
                Assert.assertTrue(f.events.isEmpty());
            }
        }
    }

    @Test(timeOut = 60000)
    public void lostCommitAcknowledgementRetriesWithoutDuplicateTimeOrEvent() throws Exception {
        final var f = new Fixture();
        f.fault = "afterCommit";
        final long[] clock = {1000};
        final var tracker = new UserOnlineTracker(() -> clock[0], () -> TimeUnit.MILLISECONDS.toNanos(clock[0]));
        final Object connection = new Object();
        tracker.connected(connection, "actor", Long.MAX_VALUE);
        clock[0] += 60_000;
        tracker.disconnected(connection);
        final var sync = new UserOnlineSynchronizer(tracker, f.service::persistOnlineState);
        try {
            sync.synchronize("actor");
            Assert.fail();
        } catch (IllegalStateException expected) {
            Assert.assertNotNull(tracker.pending("actor"));
        }
        f.fault = "";
        sync.synchronize("actor");
        Assert.assertNull(tracker.pending("actor"));
        Assert.assertEquals(f.row.getInt(UserExt.ONLINE_MINUTE), 10);
        Assert.assertEquals(f.events, List.of("9:10"));
        Assert.assertFalse(f.active);
    }

    private static void inject(final Object target, final String name, final Object value) throws Exception {
        final var field = UserMgmtService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Fixture {
        final UserMgmtService service = new UserMgmtService();
        JSONObject row = new JSONObject().put(UserExt.ONLINE_MINUTE, 9).put(UserExt.USER_ONLINE_FLAG, true);
        JSONObject staged;
        final List<String> calls = new ArrayList<>(), events = new ArrayList<>(), stagedEvents = new ArrayList<>();
        boolean active;
        String fault = "";

        Fixture() throws Exception {
            inject(service, "userRepository", new UserRepository() {
                @Override
                public Transaction beginTransaction() {
                    Assert.assertFalse(active);
                    active = true;
                    calls.add("begin");
                    staged = new JSONObject(row.toString());
                    stagedEvents.clear();
                    return new Transaction() {
                        public String getId() { return "online-test"; }
                        public boolean isActive() { return active; }
                        public void rollback() { calls.add("rollback"); active = false; stagedEvents.clear(); }
                        public void commit() {
                            calls.add("commit");
                            if (fault.equals("commit")) throw new IllegalStateException("injected commit failure");
                            row = staged;
                            events.addAll(stagedEvents);
                            active = false;
                            if (fault.equals("afterCommit")) throw new IllegalStateException("injected lost acknowledgement");
                        }
                    };
                }

                @Override
                public JSONObject getForUpdate(final String id) {
                    Assert.assertTrue(active);
                    calls.add("lock");
                    return staged;
                }

                @Override
                public void updateColumnsInCurrentTransaction(final String id, final JSONObject changes) {
                    Assert.assertTrue(active);
                    calls.add("update");
                    changes.keySet().forEach(key -> staged.put(key, changes.get(key)));
                }

                @Override
                public void refreshSettingsCache(final String id) {
                    Assert.assertFalse(active, "提交前不能发布缓存");
                    calls.add("refresh");
                }
            });
            inject(service, "professionSourceEventCaptureService", new ProfessionSourceEventCaptureService() {
                @Override
                public void onlineMinutesSettled(final String id, final int before, final int after,
                                                 final long occurredAt) throws RepositoryException {
                    Assert.assertTrue(active);
                    calls.add("event:" + before + ":" + after + ":" + occurredAt);
                    if (fault.equals("event")) throw new RepositoryException("injected event failure");
                    if (after > before) stagedEvents.add(before + ":" + after);
                }
            });
        }
    }
}
