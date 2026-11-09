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

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.function.UnaryOperator;

/** 校验客户端卡片数据及大小，URL 只作为资源标识，不在此处访问。 */
public final class ApiV3ChatCardInputs {
    public static final Set<String> WEATHER_CODES = Set.of("CLEAR_DAY", "CLEAR_NIGHT", "CLOUDY", "DUST", "FOG",
            "HEAVY_HAZE", "HEAVY_RAIN", "HEAVY_SNOW", "LIGHT_HAZE", "LIGHT_RAIN", "LIGHT_SNOW", "MODERATE_HAZE",
            "MODERATE_RAIN", "MODERATE_SNOW", "PARTLY_CLOUDY_DAY", "PARTLY_CLOUDY_NIGHT", "SAND", "STORM_RAIN", "STORM_SNOW", "WIND");
    private ApiV3ChatCardInputs() { }
    public static JSONObject music(JSONObject body) {
        keys(body, Set.of("title", "from", "source", "coverURL"));
        final String source = string(body, "source", false);
        return new JSONObject().put("title", text(string(body, "title", false), 128, false))
                .put("from", text(string(body, "from", true), 64, true))
                .put("source", source.matches("[0-9]{1,32}") ? source : https(source))
                .put("coverURL", string(body, "coverURL", true).isEmpty() ? "" : https(string(body, "coverURL", true)));
    }
    public static JSONObject weather(JSONObject body) {
        keys(body, Set.of("city", "description", "days"));
        final Object raw = body.opt("days");
        if (!(raw instanceof JSONArray days) || days.length() != 5)
            throw ApiV3WriteInputs.invalid();
        final JSONArray normalized = new JSONArray();
        LocalDate previous = null;
        for (int i = 0; i < 5; i++) {
            if (!(days.opt(i) instanceof JSONObject day))
                throw ApiV3WriteInputs.invalid();
            keys(day, Set.of("date", "code", "min", "max"));
            final String date = string(day, "date", false), code = string(day, "code", false);
            if (date.startsWith("0000-") || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || !WEATHER_CODES.contains(code))
                throw ApiV3WriteInputs.invalid();
            final LocalDate parsed;
            try {
                parsed = LocalDate.parse(date);
            } catch (DateTimeParseException error) {
                throw ApiV3WriteInputs.invalid();
            }
            if (previous != null && !parsed.equals(previous.plusDays(1)))
                throw ApiV3WriteInputs.invalid();
            previous = parsed;
            final int min = temperature(day.opt("min")), max = temperature(day.opt("max"));
            if (min > max)
                throw ApiV3WriteInputs.invalid();
            normalized.put(new JSONObject().put("date", date).put("code", code).put("min", min).put("max", max));
        }
        return new JSONObject().put("city", text(string(body, "city", false), 64, false))
                .put("description", text(string(body, "description", true), 128, true)).put("days", normalized);
    }
    /** 内容审核只处理可见文字，保留签名 URL 和 JSON 键的原值。 */
    public static JSONObject prepare(String kind, JSONObject normalized, UnaryOperator<String> moderate) {
        final JSONObject card = new JSONObject(normalized.toString());
        for (String key : "music".equals(kind) ? new String[]{"title", "from"} : new String[]{"city", "description"}) {
            final String value = card.getString(key);
            if (!value.isEmpty())
                card.put(key, moderate.apply(value));
        }
        return "music".equals(kind) ? music(card) : weather(card);
    }
    /** Stored legacy-compatible envelope, consumed by both existing chat skins and v3 raw-message reads. */
    public static String content(String kind, JSONObject normalized) {
        if ("music".equals(kind)) return new JSONObject(normalized.toString()).put("msgType", "music")
                .put("cardVersion", 3).put("payload", new JSONObject(normalized.toString())).toString();
        final JSONArray days = normalized.getJSONArray("days");
        final String[] dates = new String[5], codes = new String[5], min = new String[5], max = new String[5];
        for (int i = 0; i < 5; i++) { final JSONObject d = days.getJSONObject(i); dates[i] = d.getString("date");
            codes[i] = d.getString("code"); min[i] = Integer.toString(d.getInt("min")); max[i] = Integer.toString(d.getInt("max")); }
        return new JSONObject().put("msgType", "weather").put("date", String.join(",", dates)).put("weatherCode", String.join(",", codes))
                .put("min", String.join(",", min)).put("max", String.join(",", max))
                .put("t", normalized.getString("city")).put("st", normalized.getString("description"))
                .put("cardVersion", 3).put("payload", new JSONObject(normalized.toString())).toString();
    }
    private static int temperature(Object raw) {
        if (!(raw instanceof Integer || raw instanceof Long || raw instanceof java.math.BigInteger))
            throw ApiV3WriteInputs.invalid();
        final long value;
        try {
            value = new java.math.BigInteger(raw.toString()).longValueExact();
        } catch (ArithmeticException error) {
            throw ApiV3WriteInputs.invalid();
        }
        if (value < -100 || value > 100)
            throw ApiV3WriteInputs.invalid();
        return (int) value;
    }
    private static String text(String value, int max, boolean empty) {
        // 去除首尾空白前先拒绝控制字符，避免首尾换行在校验前被移除。
        unicode(value);
        final String result = value.strip();
        if ((!empty && result.isEmpty()) || result.length() > max || result.matches("(?s).*<[^>]*>.*")) throw ApiV3WriteInputs.invalid();
        return result;
    }
    private static String https(String value) {
        unicode(value);
        if (value.isEmpty() || value.length() > 2048 || !value.equals(value.strip()) || value.indexOf('\\') >= 0) throw ApiV3WriteInputs.invalid();
        try {
            final URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getPort() < -1 || uri.getPort() > 65535) throw ApiV3WriteInputs.invalid();
            return value;
        } catch (java.net.URISyntaxException error) {
            throw ApiV3WriteInputs.invalid();
        }
    }
    private static void unicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (Character.isISOControl(c))
                throw ApiV3WriteInputs.invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw ApiV3WriteInputs.invalid();
            } else if (Character.isLowSurrogate(c))
                throw ApiV3WriteInputs.invalid();
        }
    }
    private static void keys(JSONObject body, Set<String> allowed) {
        if (body == null || !allowed.containsAll(body.keySet()))
            throw ApiV3WriteInputs.invalid();
    }
    private static String string(JSONObject body, String key, boolean optional) {
        final Object raw = body.has(key) ? body.opt(key) : optional ? "" : null;
        if (!(raw instanceof String value))
            throw ApiV3WriteInputs.invalid();
        return value;
    }
}
