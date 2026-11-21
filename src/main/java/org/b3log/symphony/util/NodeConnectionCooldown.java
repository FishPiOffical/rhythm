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
final class NodeConnectionCooldown {

    private static final long COOLDOWN_NANOS = 5 * 60 * 1_000_000_000L;
    private final ConcurrentHashMap<String, Long> retryAfter = new ConcurrentHashMap<>();

    synchronized boolean shouldTryInternal(final String node, final long now) {
        final Long retryTime = retryAfter.get(node);
        if (retryTime != null && now < retryTime) {
            return false;
        }
        retryAfter.put(node, now + COOLDOWN_NANOS);
        return true;
    }

    void internalSucceeded(final String node) {
        retryAfter.remove(node);
    }

    void internalFailed(final String node, final long now) {
        retryAfter.put(node, now + COOLDOWN_NANOS);
    }
}
