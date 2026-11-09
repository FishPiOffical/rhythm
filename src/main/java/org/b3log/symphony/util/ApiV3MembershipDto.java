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
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 只投影固定、带类型的会员字段，省略管理员和用户的原始配置。
 */
public final class ApiV3MembershipDto {
    private ApiV3MembershipDto() {
    }

    public static JSONObject inactive() {
        return new JSONObject().put("active", false).put("levelCode", "").put("expiresAt", 0)
                .put("config", new JSONObject());
    }

    public static JSONObject membership(final JSONObject row, final long now) {
        if (row == null) return inactive();
        final long expiresAt = nonnegative(row.opt("expiresAt"));
        if (expiresAt != 0 && expiresAt <= now) return inactive();
        return new JSONObject().put("active", true).put("levelCode", text(row.opt("lvCode"), 32))
                .put("expiresAt", expiresAt).put("config", configuration(row.opt("configJson")));
    }

    public static JSONObject level(final JSONObject row) {
        return new JSONObject().put("id", text(row.opt("oId"), 19)).put("code", text(row.opt("lvCode"), 32))
                .put("name", text(row.opt("lvName"), 64)).put("price", integer(row.opt("price"), false))
                .put("durationType", text(row.opt("durationType"), 16))
                .put("durationValue", integer(row.opt("durationValue"), true))
                .put("benefits", configuration(row.opt("benefits")));
    }

    public static JSONObject configuration(final Object raw) {
        final JSONObject result = new JSONObject();
        if (!(raw instanceof String input) || input.isBlank() || input.length() > 102400) return result;
        final JSONObject source;
        try {
            source = ApiV3Json.parse(input.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return result;
        }
        for (final String key : Set.of("bold", "underline", "autoCheckin", "metal", "jointVip")) {
            if (source.opt(key) instanceof Boolean value) result.put(key, value);
        }
        if (source.opt("checkinCard") instanceof Number value) {
            try {
                final long n = nonnegative(value);
                if (n <= Integer.MAX_VALUE)
                    result.put("checkinCard", n);
            } catch (ApiV3Exception ignored) { /* Invalid optional display value is omitted. */ }
        }
        if (source.opt("color") instanceof String color && (color.matches("#[0-9a-fA-F]{6}")
                || Set.of("rainbow", "neon", "fire", "ocean", "forest", "sunset", "metal", "galaxy").contains(color))) {
            result.put("color", color);
        }
        return result;
    }

    private static String text(final Object raw, final int max) {
        if (!(raw instanceof String input)) return "";
        final String clean = Jsoup.clean(input, "", Safelist.none()).strip();
        final StringBuilder result = new StringBuilder();
        clean.codePoints().filter(c -> !Character.isISOControl(c)).limit(max).forEach(result::appendCodePoint);
        return result.toString();
    }

    private static long nonnegative(final Object raw) {
        if (raw == null || !raw.toString().matches("[0-9]{1,19}")) throw unavailable();
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            throw unavailable();
        }
    }

    private static long integer(final Object raw, final boolean positive) {
        final long value = nonnegative(raw);
        if (value > Integer.MAX_VALUE || positive && value == 0) throw unavailable();
        return value;
    }

    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "会员信息暂不可用");
    }
}
