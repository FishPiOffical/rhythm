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

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** One shared pool for legacy transfers and outer transactions with several transfer destinations. */
public final class AccountPairLocks {
    private AccountPairLocks() { }
    private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<String>> ORDERED = new ThreadLocal<>();
    public record Pair(String from, String to) { }
    // Preserve the existing key, including its legacy null fallback; business callers supply valid IDs.
    private static String key(final String from, final String to) {
        if (null == from || null == to) return String.valueOf(from) + "->" + String.valueOf(to);
        return from.compareTo(to) <= 0 ? from + "->" + to : to + "->" + from;
    }
    public static ReentrantLock lock(final String from, final String to) {
        return LOCKS.computeIfAbsent(key(from, to), ignored -> new ReentrantLock());
    }
    /** Call before opening a transaction; retain the whole set until the caller commits or rolls back. */
    public static <T> T execute(final Collection<Pair> pairs, final Callable<T> operation) throws Exception {
        Objects.requireNonNull(operation);
        final var ordered = new TreeMap<String, ReentrantLock>();
        for (final Pair pair : pairs) {
            Objects.requireNonNull(pair);
            ordered.put(key(pair.from(), pair.to()), lock(pair.from(), pair.to()));
        }
        final var parent = ORDERED.get();
        if (null != parent) {
            if (!parent.containsAll(ordered.keySet())) throw new IllegalStateException("cannot expand held account-pair set");
            return operation.call();
        }
        // Do not expand from a legacy lock on one of these pairs: the outer boundary must acquire the whole set.
        if (ordered.values().stream().anyMatch(ReentrantLock::isHeldByCurrentThread)) {
            throw new IllegalStateException("account-pair set must be acquired before legacy transfers");
        }
        final var acquired = new java.util.ArrayList<ReentrantLock>();
        try {
            for (final var pairLock : ordered.values()) { pairLock.lock(); acquired.add(pairLock); }
            ORDERED.set(new HashSet<>(ordered.keySet()));
            return operation.call();
        } finally {
            ORDERED.remove();
            for (int i = acquired.size() - 1; i >= 0; i--) acquired.get(i).unlock();
        }
    }
}
