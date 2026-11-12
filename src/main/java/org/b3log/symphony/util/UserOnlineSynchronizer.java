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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntUnaryOperator;

/** 每用户串行同步，失败保留同一个累计目标，提交结果未知时重试也不重复加时。 */
public final class UserOnlineSynchronizer {
    public interface Store {
        void persist(String userId, boolean online, IntUnaryOperator target, long settledAt) throws Exception;
    }

    private static final class Write {
        final ReentrantLock lock = new ReentrantLock();
        UserOnlineTracker.Snapshot pending;
        Integer target;
        long credited;
    }

    private final UserOnlineTracker tracker;
    private final Store store;
    private final ConcurrentHashMap<String, Write> writes = new ConcurrentHashMap<>();

    public UserOnlineSynchronizer(final UserOnlineTracker tracker, final Store store) {
        this.tracker = tracker;
        this.store = store;
    }

    public void synchronize(final String userId) throws Exception {
        final Write write = writes.computeIfAbsent(userId, ignored -> new Write());
        if (!write.lock.tryLock()) {
            return;
        }
        try {
            while (true) {
                if (write.pending == null) {
                    write.pending = tracker.pending(userId);
                }
                if (write.pending == null) {
                    return;
                }
                final var snapshot = write.pending;
                final int delta = Math.toIntExact(snapshot.minutes() - write.credited);
                store.persist(userId, tracker.isOnline(userId), before -> {
                    if (write.target == null) {
                        write.target = Math.addExact(before, delta);
                    }
                    return Math.max(before, write.target);
                }, snapshot.settledAt());
                write.credited = snapshot.minutes();
                tracker.acknowledge(snapshot);
                write.pending = null;
                write.target = null;
            }
        } finally {
            write.lock.unlock();
        }
    }
}
