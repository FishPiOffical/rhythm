package org.b3log.symphony.util;

import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.handler.ContextHandlerMeta;
import org.b3log.latke.http.handler.RouteHandler;
import org.b3log.latke.http.handler.RouteResolution;
import org.b3log.latke.util.Requests;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 * Bounded process-local security telemetry. It resets with the JVM and never stores
 * request bodies, cookies, authorization values, or raw query strings.
 */
public final class SecurityMonitor {
    private static final int MAX_EVENTS = 100;
    private static final int MINUTE_BUCKETS = 60;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Hong_Kong"));
    private static final LongAdder TOTAL = new LongAdder();
    private static final ConcurrentHashMap<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> IP_VISITS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> HANDLER_CACHE = new ConcurrentHashMap<>();
    private static final long[] MINUTES = new long[MINUTE_BUCKETS];
    private static long minuteEpoch = System.currentTimeMillis() / 60000L;
    private static final Deque<Map<String, Object>> EVENTS = new ArrayDeque<>();

    private SecurityMonitor() {
    }

    public static void record(final String type, final String result, final String reason) {
        record(null, null, null, null, type, result, reason);
    }

    public static void record(final RequestContext context, final String type, final String result,
                              final String reason, final String flow) {
        final String ip = Requests.getRemoteAddr(context.getRequest());
        record(ip, context.method(), context.requestURI(), flow, type, result, reason);
    }

    public static void record(final String ip, final String method, final String uri, final String flow,
                              final String type, final String result, final String reason) {
        final String safeType = clean(type, "unknown");
        final String safeResult = clean(result, "unknown");
        final String safeReason = clean(reason, "");
        final long now = System.currentTimeMillis();
        TOTAL.increment();
        COUNTERS.computeIfAbsent(safeType + "." + safeResult, ignored -> new LongAdder()).increment();
        synchronized (MINUTES) {
            final long currentMinute = now / 60000L;
            if (currentMinute != minuteEpoch) {
                final int steps = (int) Math.min(MINUTE_BUCKETS, Math.max(0L, currentMinute - minuteEpoch));
                for (int i = 1; i <= steps; i++) MINUTES[(int) ((minuteEpoch + i) % MINUTE_BUCKETS)] = 0L;
                minuteEpoch = currentMinute;
            }
            MINUTES[(int) (currentMinute % MINUTE_BUCKETS)]++;
        }
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", now);
        event.put("time", TIME_FORMAT.format(Instant.ofEpochMilli(now)));
        event.put("ip", clean(ip, "-"));
        event.put("userStatus", userStatus(ip));
        event.put("method", clean(method, "-"));
        event.put("uri", clean(uri, "-"));
        event.put("handler", resolveHandler(uri, method));
        event.put("flow", clean(flow, "-"));
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
            for (int i = 0; i < 10; i++) {
                final long bucket = now - i;
                if (bucket >= 0) lastMinute += MINUTES[(int) (bucket % MINUTE_BUCKETS)];
            }
        }
        result.put("last10Minutes", lastMinute);
        result.put("retention", "本次启动内，最多保留100条记录");
        return result;
    }

    public static List<Map<String, Object>> recentEvents() {
        synchronized (EVENTS) {
            return new ArrayList<>(EVENTS);
        }
    }

    private static String resolveHandler(final String uri, final String method) {
        if (uri == null || method == null) return "未匹配";
        final String cacheKey = method + " " + uri;
        final String cached = HANDLER_CACHE.get(cacheKey);
        if (cached != null) return cached;
        try {
            final RouteResolution resolution = RouteHandler.doMatch(uri, method);
            final ContextHandlerMeta meta = resolution.getContextHandlerMeta();
            final Method invokeHolder = meta == null ? null : meta.getInvokeHolder();
            if (invokeHolder != null) {
                return cacheHandler(cacheKey, invokeHolder.getDeclaringClass().getSimpleName() + "#" + invokeHolder.getName());
            }
            return cacheHandler(cacheKey, meta == null || meta.getHandler() == null ? "已匹配" : meta.getHandler().getClass().getSimpleName());
        } catch (final Exception ignored) {
            return "未匹配";
        }
    }

    private static String userStatus(final String ip) {
        if (ip == null || ip.isBlank()) return "-";
        if (Sessions.getUser() != null) return "已登录";
        final String whitelistReason = AnonymousCaptchaShield.whitelistReason(ip);
        if (whitelistReason != null) return "访客+白名单IP（" + whitelistReason + "）";
        final LongAdder visits = IP_VISITS.computeIfAbsent(ip, ignored -> new LongAdder());
        visits.increment();
        if (IP_VISITS.size() > 100000) IP_VISITS.clear();
        return "访客（访问次数 " + visits.sum() + "）";
    }

    private static String cacheHandler(final String key, final String handler) {
        if (HANDLER_CACHE.size() >= 2000) HANDLER_CACHE.clear();
        HANDLER_CACHE.putIfAbsent(key, handler);
        return handler;
    }

    private static String clean(final String value, final String fallback) {
        if (value == null || value.isBlank()) return fallback;
        final String normalized = value.replaceAll("[\\r\\n\\t]", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }
}
