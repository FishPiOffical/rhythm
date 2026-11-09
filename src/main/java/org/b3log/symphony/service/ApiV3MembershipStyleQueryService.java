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
package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 共享完整展示快照，版本未变化时也会重新校验权限。
 */
@Service
public class ApiV3MembershipStyleQueryService {
    private static final long CACHE_MS = 30_000;
    private static final int MAX_ROWS = 10_000, MAX_BYTES = 2_000_000;
    @Inject
    private UserRepository users;
    @Inject
    private MembershipRepository memberships;
    @Inject
    private MembershipLevelRepository levels;

    private record Snapshot(String version, String list, long until, long generation) {
    }

    private volatile Snapshot cached;

    protected long now() {
        return System.currentTimeMillis();
    }

    public static String version(String value) {
        if (value != null && !value.matches("[0-9a-f]{64}")) throw ApiV3Requests.invalid();
        return value;
    }

    public JSONObject styles(String actor, String version) {
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.forbidden();
        version(version);
        try {
            final var current = users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=0 LIMIT 1", actor);
            if (current.size() != 1) throw ApiV3Requests.notFound();
            final Snapshot snapshot = snapshot();
            final boolean unchanged = snapshot.version.equals(version);
            final JSONObject result = new JSONObject().put("version", snapshot.version).put("unchanged", unchanged)
                    .put("maxAgeMs", Math.max(0, Math.min(CACHE_MS, snapshot.until - now())));
            if (!unchanged) result.put("list", new JSONArray(snapshot.list));
            return result;
        } catch (RepositoryException error) {
            throw unavailable();
        }
    }

    private synchronized Snapshot snapshot() throws RepositoryException {
        final long time = now(), generation = MembershipStyleCache.generation();
        if (cached != null && cached.generation == generation && time < cached.until) return cached;
        final String joins = " FROM `" + users.getName() + "` u JOIN `" + memberships.getName()
                + "` m ON m.userId=u.oId AND m.state=1 LEFT JOIN `" + levels.getName() + "` l ON l.lvCode=m.lvCode";
        final String visible = " WHERE u.userStatus=0 AND (m.expiresAt=0 OR m.expiresAt>? OR m.expiresAt<0 OR m.expiresAt IS NULL)";
        // 在同一条 SQL 中限制原始输入大小，再由 Repository 读取结果。
        final String bytes = "CASE WHEN LENGTH(m.configJson)<=102400 THEN LENGTH(m.configJson) ELSE 0 END"
                + "+CASE WHEN LENGTH(l.benefits)<=102400 THEN LENGTH(l.benefits) ELSE 0 END";
        final var rows = memberships.select("SELECT u.oId AS userId,m.lvCode,m.expiresAt,"
                + "CASE WHEN budget.sourceBytes<=" + MAX_BYTES + " AND LENGTH(m.configJson)<=102400 THEN m.configJson ELSE NULL END AS configJson,"
                + "l.oId AS levelId,l.lvCode AS levelCode,"
                + "CASE WHEN budget.sourceBytes<=" + MAX_BYTES + " AND LENGTH(l.benefits)<=102400 THEN l.benefits ELSE NULL END AS benefits,budget.sourceBytes"
                + joins + " CROSS JOIN (SELECT COALESCE(SUM(" + bytes + "),0) AS sourceBytes" + joins + visible + ") budget"
                + visible + " ORDER BY u.oId LIMIT " + (MAX_ROWS + 1), time, time);
        if (rows.size() > MAX_ROWS) throw unavailable(); // 超限时返回错误，避免把截断名单当成完整结果。
        if (!rows.isEmpty() && expiry(rows.getFirst().opt("sourceBytes")) > MAX_BYTES) throw unavailable();
        final Map<String, Object> configs = new TreeMap<>();
        final Set<String> seen = new HashSet<>();
        long until = time + CACHE_MS;
        for (final JSONObject row : rows) {
            final String id = row.optString("userId");
            if (!id.matches("[1-9][0-9]{0,18}")) throw unavailable();
            try {
                final long expiry = expiry(row.opt("expiresAt"));
                if (expiry != 0 && expiry <= time) continue;
                if (!seen.add(id)) {
                    configs.put(id, JSONObject.NULL);
                    continue;
                }
                if (expiry != 0) until = Math.min(until, expiry);
                configs.put(id, ApiV3Json.canonical(style(row)));
            } catch (ApiV3Exception | IllegalArgumentException invalidRow) {
                // 单个会员数据异常时保留其余昵称，null 表示配置未知。
                seen.add(id);
                configs.put(id, JSONObject.NULL);
            }
        }
        final JSONArray list = new JSONArray();
        for (final var entry : configs.entrySet())
            list.put(new JSONObject().put("userId", entry.getKey()).put("configJson", entry.getValue()));
        final String encoded = ApiV3Json.canonical(list);
        if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw unavailable();
        final String revision;
        try {
            revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        // 读取期间若有写入使快照失效，下次请求须重建快照。
        cached = new Snapshot(revision, encoded, until, generation);
        return cached;
    }

    private static JSONObject style(JSONObject row) {
        if (!row.has("levelId") || row.isNull("levelId")) throw unavailable();
        final String code = row.optString("lvCode");
        if (code.isBlank() || code.length() > 32 || !code.equals(row.optString("levelCode"))) throw unavailable();
        final JSONObject definition = ApiV3MembershipConfiguration.defaults(new JSONObject().put("lvCode", code).put("benefits", row.opt("benefits")));
        final JSONObject config = config(row.opt("configJson")), style = new JSONObject();
        for (final String field : Set.of("bold", "underline"))
            if (definition.has(field) && config.opt(field) instanceof Boolean flag) style.put(field, flag);
        if (definition.has("color") && config.opt("color") instanceof String color && ApiV3MembershipConfiguration.colorAllowed(code, color))
            style.put("color", color);
        return style;
    }

    private static JSONObject config(Object raw) {
        if (!(raw instanceof String text) || text.length() > 102400) throw unavailable();
        if (text.isBlank()) return new JSONObject();
        if (text.getBytes(StandardCharsets.UTF_8).length > 102400) throw unavailable();
        try {
            return ApiV3Json.parse(text.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            throw unavailable();
        }
    }

    private static long expiry(Object raw) {
        if (!(raw instanceof Number number)) throw unavailable();
        try {
            long value = new BigDecimal(number.toString()).longValueExact();
            if (value < 0)
                throw unavailable();
            return value;
        } catch (ArithmeticException | NumberFormatException error) {
            throw unavailable();
        }
    }

    private static ApiV3Exception unavailable() {
        return ApiV3MembershipConfiguration.unavailable();
    }
}
