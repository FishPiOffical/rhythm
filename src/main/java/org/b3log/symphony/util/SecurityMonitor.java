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

import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.handler.ContextHandlerMeta;
import org.b3log.latke.http.handler.RouteHandler;
import org.b3log.latke.http.handler.RouteResolution;
import org.b3log.latke.model.User;
import org.b3log.latke.util.Requests;
import org.json.JSONObject;

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
    private static final ThreadLocal<Map<String, Object>> CURRENT_EVENT = new ThreadLocal<>();
    private static final int MAX_OBSERVATIONS = 200;
    private static final long OBSERVATION_TTL = java.util.concurrent.TimeUnit.HOURS.toMillis(2);
    private static final Deque<Observation> OBSERVATIONS = new ArrayDeque<>();
    private static long discardedObservations;

    private static final class Observation {
        final long started = System.currentTimeMillis();
        final String ip;
        final String kind;
        String state;
        String detail;
        String failure = "";
        boolean passed;
        boolean recovered;

        Observation(final String ip, final String kind, final String state, final String detail) {
            this.ip = clean(ip, "-");
            this.kind = kind;
            this.state = state;
            this.detail = detail;
        }
    }

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
        if ("captcha".equals(safeType) && "challenge".equals(safeResult)) {
            synchronized (OBSERVATIONS) {
                addObservation(new Observation(ip, "验证码", "待到达", "已要求进入验证页"));
            }
        }
        if ("captcha".equals(safeType) && "redirect".equals(safeResult)) {
            synchronized (OBSERVATIONS) {
                final Observation observation = captchaObservation(ip);
                if (observation == null || observation.recovered) {
                    addObservation(new Observation(ip, "验证码", "待到达", "验证码黑名单要求进入验证页"));
                } else if (observation.passed) {
                    fail(observation, "验证通过后仍被验证码黑名单跳转");
                }
            }
        }
        if ("cc".equals(safeType) && ("deny".equals(safeResult) || "ban".equals(safeResult))) {
            synchronized (OBSERVATIONS) {
                final Observation observation = captchaObservation(ip);
                if (observation != null && observation.passed && !observation.recovered) {
                    fail(observation, "验证码已通过，仍被 CC 拦截");
                }
            }
        }
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
            EVENTS.addFirst(event);
            while (EVENTS.size() > MAX_EVENTS) EVENTS.removeLast();
        }
        CURRENT_EVENT.set(event);
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
        synchronized (OBSERVATIONS) {
            pruneObservations();
            final List<Map<String, Object>> observations = new ArrayList<>();
            for (final Observation observation : OBSERVATIONS) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("time", TIME_FORMAT.format(Instant.ofEpochMilli(observation.started)));
                row.put("ip", observation.ip);
                row.put("kind", observation.kind);
                row.put("state", observation.state);
                row.put("detail", observation.detail);
                row.put("failure", observation.failure);
                observations.add(row);
            }
            result.put("observations", observations);
            result.put("observationDiscarded", discardedObservations);
        }
        return result;
    }

    /** 只记录真实到达展示分支，不把跳转指令当作页面已到达。 */
    public static void challengePageReached(final String ip) {
        synchronized (OBSERVATIONS) {
            Observation observation = captchaObservation(ip);
            if (observation == null || observation.recovered) {
                observation = new Observation(ip, "验证码", "已到达", "已进入验证页展示分支");
                addObservation(observation);
            }
            observation.state = "已到达";
            observation.detail = "已进入验证页，等待服务端渲染";
        }
    }

    public static void captchaValidated(final String ip, final boolean accepted, final boolean cleared) {
        synchronized (OBSERVATIONS) {
            Observation observation = captchaObservation(ip);
            if (observation == null || observation.recovered) {
                observation = new Observation(ip, "验证码", "待确认", "未观察到本轮验证页");
                addObservation(observation);
            }
            if (!accepted) {
                observation.state = "未通过";
                observation.detail = "验证未通过或供应商不可用，限制不变";
                return;
            }
            observation.passed = true;
            observation.state = "待恢复";
            observation.detail = "验证已通过，等待后续业务页面";
            if (!cleared) fail(observation, "验证通过后验证码限制仍存在");
        }
    }

    /** 只接受真实模板渲染结果；不根据 HTTP 200、静态资源或后台轮询推断恢复。 */
    public static void pageRendered(final RequestContext context, final String template, final boolean rendered) {
        if (!"GET".equals(context.method())) return;
        final String ip = Requests.getRemoteAddr(context.getRequest());
        synchronized (OBSERVATIONS) {
            final Observation observation = captchaObservation(ip);
            if (observation == null || observation.recovered) return;
            if ("/test".equals(context.requestURI()) && "captcha.ftl".equals(template)) {
                if (!rendered) {
                    fail(observation, "验证页服务端渲染失败");
                } else if (!observation.passed) {
                    observation.state = "待验证";
                    observation.detail = "验证页已由服务端渲染，等待完成验证";
                }
            } else if (observation.passed && !"/test".equals(context.requestURI())
                    && !context.requestURI().startsWith("/admin/")) {
                if (!rendered || context.attr(RequestContext.ERROR_CODE) != null) {
                    fail(observation, "验证通过后页面处理失败");
                } else if (context.getResponse().getHeader("Location") == null
                        && !AnonymousCaptchaShield.isRestricted(ip)) {
                    observation.recovered = true;
                    observation.state = "已恢复";
                    observation.detail = "验证通过后业务页面已由服务端渲染";
                }
            }
        }
    }

    public static void ipsetResult(final String ip, final String operation, final boolean verified,
                                   final String detail) {
        synchronized (OBSERVATIONS) {
            final Observation observation = new Observation(ip, "Linux 防火墙",
                    verified ? "已核实" : "执行异常", clean(operation + "：" + detail, ""));
            if (!verified) observation.failure = observation.detail;
            addObservation(observation);
        }
    }

    public static void requestFinished(final RequestContext context) {
        if (!"GET".equals(context.method()) || context.getRequest().isStaticResource()
                || context.requestURI().startsWith("/admin/")) return;
        if (context.attr(RequestContext.ERROR_CODE) == null
                && !(context.getRenderer() instanceof org.b3log.latke.http.renderer.Http500Renderer)
                && !(context.getRenderer() instanceof org.b3log.latke.http.renderer.Http404Renderer)) return;
        final String ip = Requests.getRemoteAddr(context.getRequest());
        synchronized (OBSERVATIONS) {
            final Observation observation = captchaObservation(ip);
            if (observation != null && !observation.recovered) {
                if ("/test".equals(context.requestURI())) {
                    fail(observation, "验证页请求处理失败");
                } else if (observation.passed) {
                    fail(observation, "验证通过后请求处理失败");
                }
            }
        }
    }

    private static void fail(final Observation observation, final String reason) {
        observation.state = "异常";
        observation.detail = reason;
        if (observation.failure.isEmpty()) observation.failure = reason;
    }

    private static Observation captchaObservation(final String ip) {
        pruneObservations();
        for (final Observation observation : OBSERVATIONS) {
            if (observation.ip.equals(ip) && "验证码".equals(observation.kind)) return observation;
        }
        return null;
    }

    private static void addObservation(final Observation observation) {
        pruneObservations();
        OBSERVATIONS.addFirst(observation);
        if (OBSERVATIONS.size() > MAX_OBSERVATIONS) {
            OBSERVATIONS.removeLast();
            discardedObservations++;
        }
    }

    private static void pruneObservations() {
        final long cutoff = System.currentTimeMillis() - OBSERVATION_TTL;
        while (!OBSERVATIONS.isEmpty() && OBSERVATIONS.peekLast().started < cutoff) {
            OBSERVATIONS.removeLast();
            discardedObservations++;
        }
    }

    public static List<Map<String, Object>> recentEvents() {
        synchronized (EVENTS) {
            final List<Map<String, Object>> copy = new ArrayList<>(EVENTS.size());
            for (final Map<String, Object> event : EVENTS) {
                copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(event)));
            }
            return copy;
        }
    }

    /** Adds the authenticated user to the current request's already-recorded firewall event. */
    public static void markAuthenticated(final JSONObject user) {
        final Map<String, Object> event = CURRENT_EVENT.get();
        if (event != null && user != null) {
            final String userName = user.optString(User.USER_NAME, "未知用户");
            synchronized (EVENTS) {
                event.put("userStatus", "本人登录（用户：" + clean(userName, "未知用户") + "）");
            }
        }
        CURRENT_EVENT.remove();
    }

    public static void clearRequest() {
        CURRENT_EVENT.remove();
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
