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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** 当前实例的连接区间并集；这里只操作内存，不持锁访问数据库。 */
public final class UserOnlineTracker {
    public record Snapshot(String userId, long version, boolean online, long minutes, long settledAt) {
    }

    private record Lease(String userId, long deadline) {
    }

    private static final class State {
        final Map<Object, Long> connections = new HashMap<>();
        long cursor, remainder, minutes, version, acknowledged, settledAt;
        boolean online;
    }

    private final LongSupplier wallClock;
    private final LongSupplier ticker;
    private final Map<Object, Lease> owners = new HashMap<>();
    private final Map<String, State> users = new HashMap<>();
    // 历史用户只保留余数；调度仅扫描在线或尚未同步的用户。
    private final Set<String> candidates = new HashSet<>();
    private boolean stopped;

    public UserOnlineTracker(final LongSupplier wallClock, final LongSupplier ticker) {
        this.wallClock = wallClock;
        this.ticker = ticker;
    }

    public synchronized void connected(final Object connection, final String userId, final long expiresAt) {
        if (connection == null || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("online connection owner");
        }
        if (stopped) {
            throw new IllegalStateException("online tracking stopped");
        }
        final Lease previous = owners.get(connection);
        if (previous != null && !previous.userId().equals(userId)) {
            disconnected(connection);
        }
        final long now = ticker.getAsLong();
        final State state = users.computeIfAbsent(userId, ignored -> new State());
        advance(state, now);
        final long remaining = expiresAt == Long.MAX_VALUE ? Long.MAX_VALUE
                : Math.max(0, expiresAt - wallClock.getAsLong());
        if (remaining == 0) {
            disconnected(connection);
            return;
        }
        final long deadline = remaining == Long.MAX_VALUE ? Long.MAX_VALUE
                : now + TimeUnit.MILLISECONDS.toNanos(remaining);
        state.connections.put(connection, deadline);
        owners.put(connection, new Lease(userId, deadline));
        candidates.add(userId);
        if (!state.online) {
            state.online = true;
            state.cursor = now;
            state.version++;
        }
    }

    /** 续期不创建新连接；已到期的连接不能被迟到的心跳复活。 */
    public synchronized boolean renew(final Object connection, final long expiresAt) {
        final Lease lease = owners.get(connection);
        if (lease == null) {
            return false;
        }
        final State state = users.get(lease.userId());
        final long now = ticker.getAsLong();
        advance(state, now);
        if (!state.connections.containsKey(connection)) {
            return false;
        }
        final long remaining = expiresAt - wallClock.getAsLong();
        if (remaining <= 0) {
            disconnected(connection);
            return false;
        }
        final long deadline = now + TimeUnit.MILLISECONDS.toNanos(remaining);
        state.connections.put(connection, deadline);
        owners.put(connection, new Lease(lease.userId(), deadline));
        return true;
    }

    public synchronized void disconnected(final Object connection) {
        final Lease lease = owners.remove(connection);
        if (lease == null) {
            return;
        }
        final State state = users.get(lease.userId());
        final long now = ticker.getAsLong();
        advance(state, now);
        state.connections.remove(connection);
        finishIfOffline(state, now, now);
    }

    public synchronized boolean isOnline(final String userId) {
        final State state = users.get(userId);
        if (state == null) {
            return false;
        }
        advance(state, ticker.getAsLong());
        return state.online;
    }

    public synchronized List<Snapshot> pending() {
        final List<Snapshot> result = new ArrayList<>();
        final long now = ticker.getAsLong();
        for (final String userId : candidates) {
            final State state = users.get(userId);
            advance(state, now);
            if (state.version > state.acknowledged) {
                result.add(new Snapshot(userId, state.version, state.online, state.minutes, state.settledAt));
            }
        }
        return result;
    }

    public synchronized Snapshot pending(final String userId) {
        final State state = users.get(userId);
        if (state == null) {
            return null;
        }
        advance(state, ticker.getAsLong());
        return state.version > state.acknowledged
                ? new Snapshot(userId, state.version, state.online, state.minutes, state.settledAt) : null;
    }

    public synchronized void acknowledge(final Snapshot snapshot) {
        final State state = users.get(snapshot.userId());
        state.acknowledged = Math.max(state.acknowledged, snapshot.version());
        if (!state.online && state.version == state.acknowledged) {
            candidates.remove(snapshot.userId());
        }
    }

    /** 停机先冻结登记，再结束所有区间；后续重复关闭不会再次累计。 */
    public synchronized void stop() {
        stopped = true;
        final long now = ticker.getAsLong();
        for (final State state : users.values()) {
            advance(state, now);
            state.connections.clear();
            finishIfOffline(state, now, now);
        }
        owners.clear();
    }

    private void advance(final State state, final long now) {
        if (!state.online) {
            return;
        }
        long end = state.cursor;
        for (final long deadline : state.connections.values()) {
            end = Math.max(end, Math.min(now, deadline));
        }
        state.remainder = Math.addExact(state.remainder, Math.max(0, end - state.cursor));
        state.cursor = now;
        state.connections.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) {
                return false;
            }
            owners.remove(entry.getKey());
            return true;
        });
        finishIfOffline(state, end, now);
    }

    private void finishIfOffline(final State state, final long end, final long now) {
        if (!state.online || !state.connections.isEmpty()) {
            return;
        }
        state.online = false;
        final long minute = TimeUnit.MINUTES.toNanos(1);
        state.minutes = Math.addExact(state.minutes, state.remainder / minute);
        state.remainder %= minute;
        state.settledAt = wallClock.getAsLong() - TimeUnit.NANOSECONDS.toMillis(Math.max(0, now - end));
        state.version++;
    }
}
