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

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 用户只可编辑明确列出的展示字段，权益字段由服务端管理。 */
public final class ApiV3MembershipConfiguration {
    private ApiV3MembershipConfiguration() { }
    private static final Set<String> FIELDS = Set.of("bold", "underline", "color");
    private static final Set<String> EFFECTS = Set.of("rainbow", "neon", "fire", "ocean", "forest", "sunset", "metal", "galaxy");
    private static final Set<String> VIP4 = Set.of("VIP4_YEAR", "VIP4_MONTH");
    private static final int MAX_CONFIG_BYTES = 102400;

    public static JSONObject validateChanges(final JSONObject body) {
        if (body == null || body.isEmpty() || !FIELDS.containsAll(body.keySet())) throw ApiV3WriteInputs.invalid();
        final JSONObject result = new JSONObject();
        for (final String key : body.keySet()) {
            final Object value = body.opt(key);
            if ("color".equals(key)) {
                if (!(value instanceof String color) || !(color.matches("#[0-9a-fA-F]{6}") || EFFECTS.contains(color)))
                    throw ApiV3WriteInputs.invalid();
            } else if (!(value instanceof Boolean)) throw ApiV3WriteInputs.invalid();
            result.put(key, value);
        }
        return result;
    }

    public static JSONObject defaults(final JSONObject level) {
        if (level == null || !(level.opt("lvCode") instanceof String code) || code.isBlank()
                || code.length() > 32 || code.codePoints().anyMatch(Character::isISOControl)) throw unavailable();
        try { ApiV3Json.canonical(code); } catch (final IllegalArgumentException e) { throw unavailable(); }
        final Object raw = level.opt("benefits");
        if (!(raw instanceof String input)) throw unavailable();
        bounded(input);
        final JSONObject defaults = input.isBlank() ? new JSONObject() : stored(input);
        for (final String key : FIELDS) {
            if (!defaults.has(key)) continue;
            final Object value = defaults.opt(key);
            if ("color".equals(key)) {
                if (!(value instanceof String color) || !colorAllowed(code, color)) throw unavailable();
            } else if (!(value instanceof Boolean)) throw unavailable();
        }
        // JSON 转义可能增加长度，因此限制实际入库值的长度。
        bounded(defaults.toString());
        return defaults;
    }

    public static JSONObject merge(final JSONObject level, final String storedConfig, final JSONObject changes) {
        final JSONObject normalized = validateChanges(changes), template = defaults(level);
        if (storedConfig != null) bounded(storedConfig);
        final JSONObject result = storedConfig == null || storedConfig.isBlank() ? new JSONObject() : stored(storedConfig);
        for (final String key : normalized.keySet()) {
            if (!template.has(key)) throw ApiV3WriteInputs.invalid();
            final Object value = normalized.get(key);
            if ("color".equals(key) && !colorAllowed(level.getString("lvCode"), (String) value)) throw ApiV3WriteInputs.invalid();
            result.put(key, value);
        }
        // 保留所有服务端管理的键，同时限制合并后入库数据的大小。
        if (result.toString().getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_BYTES) throw unavailable();
        return result;
    }

    /** 只比较支持的展示字段类型，允许默认值和权益差异。 */
    public static JSONObject displayDefinition(final JSONObject level) {
        final JSONObject defaults = defaults(level), result = new JSONObject();
        for (final String field : FIELDS) if (defaults.has(field)) result.put(field, "color".equals(field) ? "string" : "boolean");
        return result;
    }

    public static JSONObject publicConfig(final JSONObject config) {
        final JSONObject result = new JSONObject();
        for (final String field : Set.of("bold", "underline")) if (config.opt(field) instanceof Boolean value) result.put(field, value);
        if (config.opt("color") instanceof String color && (color.matches("#[0-9a-fA-F]{6}") || EFFECTS.contains(color))) result.put("color", color);
        return result;
    }
    private static JSONObject stored(final String input) {
        bounded(input);
        final byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        try { return ApiV3Json.parse(bytes); }
        catch (final IllegalArgumentException e) { throw unavailable(); }
    }
    private static void bounded(final String input) {
        if (input.length() > MAX_CONFIG_BYTES || input.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_BYTES) throw unavailable();
        try { ApiV3Json.canonical(input); } catch (final IllegalArgumentException e) { throw unavailable(); }
    }
    public static boolean colorAllowed(final String code, final String color) {
        return color.matches("#[0-9a-fA-F]{6}") || VIP4.contains(code) && EFFECTS.contains(color);
    }
    public static ApiV3Exception unavailable() { return new ApiV3Exception(503, "temporarily_unavailable", "会员配置暂不可用"); }
}
