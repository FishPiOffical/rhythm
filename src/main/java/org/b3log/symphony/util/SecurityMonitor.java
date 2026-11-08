package org.b3log.symphony.util;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight process-local security telemetry. It is bounded and resets with the JVM;
 * it never stores request bodies, cookies, authorization values, or raw query strings.
 */
public final class SecurityMonitor {
    private static final int MAX_EVENTS = 100;
    private static final int MINUTE_BUCKETS = 60;
    private static final LongAdder TOTAL = new LongAdder();
    private static final ConcurrentHashMap<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();
    private static final long[] MINUTES = new long[MINUTE_BUCKETS];
    private static long minuteEpoch = System.currentTimeMillis() / 60000L;
    private static final Deque<Map<String, Object>> EVENTS = new ArrayDeque<>();

    private SecurityMonitor() {
    }

    public static void record(final String type, final String result, final String reason) {
        final String safeType = clean(type, "unknown");
        final String safeResult = clean(result, "unknown");
        final String safeReason = clean(reason, "");
        TOTAL.increment();
        COUNTERS.computeIfAbsent(safeType + "." + safeResult, ignored -> new LongAdder()).increment();
        synchronized (MINUTES) {
            final long now = System.currentTimeMillis() / 60000L;
            if (now != minuteEpoch) {
                if (now > minuteEpoch) {
                    final int steps = (int) Math.min(MINUTE_BUCKETS, now - minuteEpoch);
                    for (int i = 1; i <= steps; i++) MINUTES[(int) ((minuteEpoch + i) % MINUTE_BUCKETS)] = 0L;
                }
                minuteEpoch = now;
            }
            MINUTES[(int) (now % MINUTE_BUCKETS)]++;
        }
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("time", Instant.now().toString());
        event.put("type", safeType);
        event.put("result", safeResult);
        event.put("reason", safeReason);
        synchronized (EVENTS) {
            EVENTS.addFirst(Collections.unmodifiableMap(event));
            while (EVENTS.size() > MAX_EVENTS) EVENTS.removeLast();
        }
    }

    public static Map<String, Object> snapshot() {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", TOTAL.sum());
        final Map<String, Long> counts = new LinkedHashMap<>();
        COUNTERS.forEach((key, value) -> counts.put(key, value.sum()));
        result.put("counts", counts);
        long lastMinute = 0L;
        synchronized (MINUTES) {
            final long now = System.currentTimeMillis() / 60000L;
            for (int i = 0; i < 10; i++) lastMinute += MINUTES[(int) ((now - i) % MINUTE_BUCKETS)];
        }
        result.put("last10Minutes", lastMinute);
        result.put("retention", "本次启动内，最多保留100条抽样记录");
        return result;
    }

    public static List<Map<String, Object>> recentEvents() {
        synchronized (EVENTS) {
            return new ArrayList<>(EVENTS);
        }
    }

    private static String clean(final String value, final String fallback) {
        if (value == null || value.isBlank()) return fallback;
        final String normalized = value.replaceAll("[\\r\\n\\t]", " ");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }
}
