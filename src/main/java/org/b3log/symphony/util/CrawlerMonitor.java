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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 当前实例启动内的爬虫请求统计，不参与验证和放行决策。 */
public final class CrawlerMonitor {
    private static final int MAX_PATHS_PER_GROUP = 1000;
    private static final int MAX_SOURCES_PER_GROUP = 1000;
    private static final int MAX_PATH_LENGTH = 2048;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Hong_Kong"));
    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();

    private static final class Visit {
        long count;
        long lastSeen;

        void record(final long now) {
            count++;
            lastSeen = now;
        }
    }

    private static final class Group {
        final SearchEngines.Engine engine;
        final boolean verified;
        final Visit total = new Visit();
        final Map<String, Visit> paths = new LinkedHashMap<>();
        final Map<String, Source> sources = new LinkedHashMap<>();
        long omitted;
        long sourceOmitted;

        Group(final SearchEngines.Engine engine, final boolean verified) {
            this.engine = engine;
            this.verified = verified;
        }
    }

    private static final class Source {
        final Visit visits = new Visit();
        SearchEngines.Verification verification;
    }

    private CrawlerMonitor() {
    }

    /** 复用入口已取得的验证结果，禁止为统计再次查询 DNS。 */
    public static void record(final SearchEngines.Verification verification, final String ip, final String uri) {
        if (verification == null) return;
        final SearchEngines.Engine engine = verification.engine();
        final boolean verified = verification.verified();
        final String key = engine.key() + (verified ? "-verified" : "-unverified");
        final String path = requestPath(uri);
        final long now = System.currentTimeMillis();
        synchronized (GROUPS) {
            final Group group = GROUPS.computeIfAbsent(key, ignored -> new Group(engine, verified));
            group.total.record(now);
            // 来源与路径独立限量，路径未保留时仍可定位来源及其最近一次真实校验。
            if (ip == null || ip.isBlank() || ip.length() > 64
                    || (!group.sources.containsKey(ip) && group.sources.size() >= MAX_SOURCES_PER_GROUP)) {
                group.sourceOmitted++;
            } else {
                final Source source = group.sources.computeIfAbsent(ip, ignored -> new Source());
                source.visits.record(now);
                source.verification = verification;
            }
            // 不截断后合并不同长路径；未保留的请求单独计数，总次数不丢失。
            if (path == null || (!group.paths.containsKey(path) && group.paths.size() >= MAX_PATHS_PER_GROUP)) {
                group.omitted++;
                return;
            }
            group.paths.computeIfAbsent(path, ignored -> new Visit()).record(now);
        }
    }

    /** 只返回选中分组的来源和路径，避免每秒轮询传输所有爬虫的全部明细。 */
    public static Map<String, Object> snapshot(final String selectedGroup) {
        synchronized (GROUPS) {
            final List<Map<String, Object>> groups = new ArrayList<>();
            GROUPS.forEach((key, group) -> groups.add(Map.of(
                    "key", key, "name", group.engine.displayName(), "verified", group.verified,
                    "count", group.total.count, "lastSeen", time(group.total.lastSeen),
                    "omitted", group.omitted, "pathCount", group.paths.size(),
                    "sourceOmitted", group.sourceOmitted, "sourceCount", group.sources.size())));
            final List<Map<String, Object>> paths = new ArrayList<>();
            final List<Map<String, Object>> sources = new ArrayList<>();
            final Group selected = GROUPS.get(selectedGroup);
            if (selected != null) {
                selected.sources.entrySet().stream()
                        .sorted(Comparator.<Map.Entry<String, Source>>comparingLong(entry -> entry.getValue().visits.lastSeen)
                                .reversed().thenComparing(Map.Entry::getKey))
                        .forEach(entry -> {
                            final Source source = entry.getValue();
                            final SearchEngines.Verification result = source.verification;
                            sources.add(Map.of("ip", entry.getKey(), "count", source.visits.count,
                                    "lastSeen", time(source.visits.lastSeen), "reason", result.reason(),
                                    "ptr", result.ptr(), "forward", result.forward(), "checkedAt", time(result.checkedAt()),
                                    "cached", result.cached()));
                        });
                selected.paths.entrySet().stream()
                        .sorted(Comparator.<Map.Entry<String, Visit>>comparingLong(entry -> entry.getValue().lastSeen)
                                .reversed().thenComparing(Map.Entry::getKey))
                        .forEach(entry -> paths.add(Map.of("path", entry.getKey(), "count", entry.getValue().count,
                                "lastSeen", time(entry.getValue().lastSeen))));
            }
            return Map.of("groups", groups, "selectedGroup", selected == null ? "" : selectedGroup, "paths", paths,
                    "sources", sources, "sourceOmitted", selected == null ? 0L : selected.sourceOmitted);
        }
    }

    private static String requestPath(final String uri) {
        if (uri == null || !uri.startsWith("/")) return null;
        final int query = uri.indexOf('?');
        final int fragment = uri.indexOf('#');
        final int end = Math.min(query < 0 ? uri.length() : query, fragment < 0 ? uri.length() : fragment);
        if (end > MAX_PATH_LENGTH) return null;
        return uri.substring(0, end).replaceAll("[\\p{Cntrl}]", " ");
    }

    private static String time(final long timestamp) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }
}
