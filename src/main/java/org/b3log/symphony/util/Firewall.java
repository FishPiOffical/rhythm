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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.symphony.cache.UserCache;

import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;
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

    private static final int IPSET_TIMEOUT_MILLIS = 3000;

    private static final String IPSET_NAME = "fishpi";

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

    private static final Set<String> CC_WHITELIST = ConcurrentHashMap.newKeySet();

    /**
     * 已封禁的 IP，避免重复执行 shell；无过期，需管理员手动解封。
     */
    private static final Cache<String, Boolean> BANNED = Caffeine.newBuilder()
            .maximumSize(100000)
            .build();

    private Firewall() {
    }

    /**
     * Record one request from the given IP, and ban it if it crosses the threshold.
     *
     * @param ip client IP
     * @return {@code true} if the request is allowed, {@code false} if already banned or ban was triggered
     */
    public static boolean recordAndMaybeBan(final String ip) {
        return recordAndMaybeBan(ip, null, null, null);
    }

    /**
     * Record one request from the given IP, and ban it if it crosses the threshold.
     * Verified search engine crawlers are whitelisted so that they are never counted or banned.
     *
     * @param ip client IP
     * @param ua client user agent
     * @return {@code true} if the request is allowed, {@code false} if already banned or ban was triggered
     */
    public static boolean recordAndMaybeBan(final String ip, final String ua) {
        return recordAndMaybeBan(ip, ua, null, null);
    }

    /** Records the request location for the admin security monitor without storing query data. */
    public static boolean recordAndMaybeBan(final String ip, final String ua, final String method,
                                            final String uri) {
        if (StringUtils.isBlank(ip) || !enabled) {
            SecurityMonitor.record(ip, method, uri, "BeforeRequestHandler -> Firewall",
                    "cc", "allow", enabled ? "无效IP" : "开关关闭");
            return true;
        }

        if (null != SearchEngines.verifiedCrawler(ip, ua)) {
            SecurityMonitor.record(ip, method, uri, "BeforeRequestHandler -> Firewall",
                    "cc", "allow", "已验证蜘蛛");
            return true;
        }

        if (CC_WHITELIST.contains(ip)) {
            final boolean allowed = null == BANNED.getIfPresent(ip);
            SecurityMonitor.record(ip, method, uri, "BeforeRequestHandler -> Firewall",
                    "cc", allowed ? "allow" : "deny", allowed ? "CC白名单" : "已封禁");
            return allowed;
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

        if (counter.count.sum() > effectiveThreshold) {
            banIpIfAbsent(ip, "请求频率超限");
            SecurityMonitor.record(ip, method, uri, "BeforeRequestHandler -> Firewall",
                    "cc", "ban", "请求频率超限");
            return false;
        }

        final boolean allowed = null == BANNED.getIfPresent(ip);
        SecurityMonitor.record(ip, method, uri, "BeforeRequestHandler -> Firewall",
                "cc", allowed ? "allow" : "deny", allowed ? "阈值内" : "已封禁");
        return allowed;
    }

    public static boolean banIpIfAbsent(final String ip, final String reason) {
        if (StringUtils.isBlank(ip) || null != BANNED.asMap().putIfAbsent(ip, Boolean.TRUE)) {
            return false;
        }

        Thread.startVirtualThread(() -> {
            try {
                final String result = runIpset("add", ip);
                LOGGER.log(Level.WARN, "[SHIELD] ip={} 结果=封禁 原因={} 执行结果={}", ip, reason, result);
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, reason + " ban failed for [" + ip + "]", e);
            }
        });
        return true;
    }

    public static String banIp(final String ip) {
        BANNED.put(ip, Boolean.TRUE);
        return runIpset("add", ip);
    }

    public static String unbanIp(final String ip) {
        BANNED.invalidate(ip);
        final String result = runIpset("del", ip);
        addCcWhitelist(ip);
        return result;
    }

    /** Exempts an IP from automatic CC counting without overriding manual bans. */
    public static void addCcWhitelist(final String ip) {
        if (StringUtils.isBlank(ip)) {
            throw new IllegalArgumentException("IP must not be blank");
        }
        CC_WHITELIST.add(ip);
        COUNTERS.remove(ip);
    }

    private static String runIpset(final String operation, final String ip) {
        return runIpset(operation, ip, Firewall::executeIpset);
    }

    record CommandResult(int exitCode, boolean timedOut, String output) {
    }

    @FunctionalInterface
    interface IpsetRunner {
        CommandResult run(String operation, String ip) throws Exception;
    }

    static String runIpset(final String operation, final String ip, final IpsetRunner runner) {
        if (StringUtils.isBlank(operation) || StringUtils.isBlank(ip)) {
            throw new IllegalArgumentException("ipset operation and IP must not be blank");
        }
        final String action = "add".equals(operation) ? "封禁" : "解封";
        try {
            final CommandResult change = runner.run(operation, ip);
            final CommandResult test = runner.run("test", ip);
            final boolean present = !test.timedOut() && test.exitCode() == 0;
            // ipset 的退出码 1 也可能是权限/集合错误，必须同时核对明确的非成员结果。
            final boolean absent = !test.timedOut() && test.exitCode() == 1
                    && test.output().contains(" is NOT in set " + IPSET_NAME);
            final boolean verified = !change.timedOut() && change.exitCode() == 0
                    && ("add".equals(operation) ? present : absent);
            final String detail = "命令=" + commandSummary(change) + "；成员核对="
                    + (present ? "存在" : absent ? "不存在" : commandSummary(test));
            SecurityMonitor.ipsetResult(ip, action, verified, detail);
            if (!verified) LOGGER.warn("[SHIELD] ip={} 结果=防火墙执行异常 操作={} {}", ip, action, detail);
            return detail;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            final String detail = "命令异常：" + e.getClass().getSimpleName();
            SecurityMonitor.ipsetResult(ip, action, false, detail);
            LOGGER.warn("[SHIELD] ip={} 结果=防火墙执行异常 操作={}", ip, action, e);
            return detail;
        }
    }

    private static String commandSummary(final CommandResult result) {
        if (result.timedOut()) return "超时";
        if (result.exitCode() == 0) return "成功";
        final String output = result.output().replaceAll("[\\r\\n\\t]", " ");
        return "退出码 " + result.exitCode() + " " + output.substring(0, Math.min(160, output.length()));
    }

    private static CommandResult executeIpset(final String operation, final String ip) throws Exception {
        final CommandLine command = new CommandLine("ipset");
        command.addArgument(operation, false);
        command.addArgument(IPSET_NAME, false);
        command.addArgument(ip, false);
        final DefaultExecutor executor = new DefaultExecutor();
        final ExecuteWatchdog watchdog = new ExecuteWatchdog(IPSET_TIMEOUT_MILLIS);
        executor.setWatchdog(watchdog);
        executor.setExitValues(null);
        // 只保留诊断所需前缀，子进程输出持续消费，避免输出管道堵塞。
        final ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override public synchronized void write(final byte[] bytes, final int offset, final int length) {
                super.write(bytes, offset, Math.min(length, Math.max(0, 1024 - size())));
            }
            @Override public synchronized void write(final int value) {
                if (size() < 1024) super.write(value);
            }
        };
        executor.setStreamHandler(new PumpStreamHandler(output));
        final Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("LC_ALL", "C");
        final int exit = executor.execute(command, environment);
        return new CommandResult(exit, watchdog.killedProcess(), output.toString(java.nio.charset.StandardCharsets.UTF_8));
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
