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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.util.Execs;
import org.b3log.symphony.cache.UserCache;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight CC firewall: if an IP exceeds {@link #THRESHOLD} requests within a minute, ban it via ipset.
 *
 * Call {@link #recordAndMaybeBan(String)} on each request to enforce the threshold.
 */
public final class Firewall {

    private static final Logger LOGGER = LogManager.getLogger(Firewall.class);

    /**
     * Default requests-per-minute threshold.
     */
    private static final int DEFAULT_THRESHOLD = 400;

    /**
     * Runtime threshold, adjustable and reset on restart.
     */
    private static volatile int threshold = DEFAULT_THRESHOLD;

    /**
     * Runtime enable flag, reset on restart.
     */
    private static volatile boolean enabled = true;

    /**
     * One-minute window length in milliseconds.
     */
    private static final long WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(1);

    /**
     * Per-IP counters keyed by current minute bucket.
     */
    private static final Map<String, Counter> COUNTERS = new ConcurrentHashMap<>();

    /**
     * Already banned IPs to avoid duplicate shell calls.
     */
    private static final Set<String> BANNED = ConcurrentHashMap.newKeySet();

    private Firewall() {
    }

    /**
     * Record one request from the given IP, and ban it if it crosses the threshold.
     *
     * @param ip client IP
     * @return {@code true} if the request is allowed, {@code false} if already banned or ban was triggered
     */
    public static boolean recordAndMaybeBan(final String ip) {
        if (StringUtils.isBlank(ip) || !enabled) {
            return true;
        }

        final long nowBucket = System.currentTimeMillis() / WINDOW_MILLIS;
        final int effectiveThreshold = UserCache.hasUserByIP(ip) ? threshold : Math.min(threshold, 250);
        final Counter counter = COUNTERS.compute(ip, (key, existing) -> {
            if (existing == null || existing.bucket != nowBucket) {
                return new Counter(nowBucket, 1);
            }
            existing.count.increment();
            return existing;
        });

        // Small, opportunistic cleanup of stale buckets to keep the map lean.
        if ((COUNTERS.size() & 0xFF) == 0) {
            cleanupOldBuckets(nowBucket);
        }

        if (counter.count.sum() > effectiveThreshold && BANNED.add(ip)) {
            // Run ban asynchronously on a virtual thread to keep request path light.
            Thread.startVirtualThread(() -> {
                try {
                    final String result = Execs.exec(new String[]{"sh", "-c", "ipset add fishpi " + ip}, 1000 * 3);
                    LOGGER.log(Level.WARN, "CC firewall banned [{}], result: {}", ip, result);
                } catch (final Exception e) {
                    LOGGER.log(Level.ERROR, "CC firewall ban failed for [" + ip + "]", e);
                }
            });
            return false;
        }

        System.out.println("CC firewall allowed " + ip + " [" + counter.count.sum() + "]");

        return !BANNED.contains(ip);
    }

    public static void cleanupOldBuckets(final long currentBucket) {
        COUNTERS.entrySet().removeIf(entry -> entry.getValue().bucket != currentBucket);
    }

    /**
     * Enables or disables the firewall (temporary, resets on restart).
     */
    public static void setEnabled(final boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Updates the runtime threshold (temporary, resets on restart).
     */
    public static void setThreshold(final int value) {
        if (value <= 0) {
            threshold = DEFAULT_THRESHOLD;
        } else {
            threshold = value;
        }
    }

    public static int getThreshold() {
        return threshold;
    }

    public static int getDefaultThreshold() {
        return DEFAULT_THRESHOLD;
    }

    private static final class Counter {
        private final long bucket;
        private final LongAdder count = new LongAdder();

        private Counter(final long bucket, final int count) {
            this.bucket = bucket;
            this.count.add(count);
        }
    }
}
