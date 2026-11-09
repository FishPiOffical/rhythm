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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 独立解析有界的 UTF-8 OAuth 表单参数，不读取框架合并后的查询和请求体参数。 */
public final class ApiV3OAuthForms {
    public static final int MAX_BYTES = 32768;

    private ApiV3OAuthForms() { }

    private static ApiV3Exception invalid() {
        return new ApiV3Exception(400, "invalid_request", "请求参数不合法");
    }

    public static Map<String, String> parse(byte[] bytes, Set<String> allowed) {
        if (bytes == null || bytes.length > MAX_BYTES)
            throw invalid();
        try {
            return parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(), allowed);
        } catch (java.nio.charset.CharacterCodingException e) {
            throw invalid();
        }
    }

    public static Map<String, String> parse(String raw, Set<String> allowed) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_BYTES
                || raw.chars().anyMatch(c -> c > 127 || Character.isISOControl(c)))
            throw invalid();
        try {
            ApiV3Json.canonical(raw);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw invalid();
        final Map<String, String> result = new LinkedHashMap<>();
        for (String pair : raw.split("&", -1)) {
            if (result.size() >= 16 || pair.isEmpty())
                throw invalid();
            final int separator = pair.indexOf('=');
            if (separator < 1)
                throw invalid();
            final String key = decode(pair.substring(0, separator)), value = decode(pair.substring(separator + 1));
            // 先解码参数名，再检查白名单和重复项。
            if (!allowed.contains(key) || result.containsKey(key))
                throw invalid();
            result.put(key, value);
        }
        return Map.copyOf(result);
    }
    private static String decode(String value) {
        try {
            final var bytes = new ByteArrayOutputStream();
            for (int i = 0; i < value.length();) {
                final char c = value.charAt(i);
                if (c == '%') {
                    if (i + 2 >= value.length())
                        throw invalid();
                    int a = Character.digit(value.charAt(i + 1), 16), b = Character.digit(value.charAt(i + 2), 16);
                    if (a < 0 || b < 0)
                        throw invalid();
                    bytes.write(a * 16 + b);
                    i += 3;
                } else if (c == '+') {
                    bytes.write(' ');
                    i++;
                } else {
                    int cp = value.codePointAt(i);
                    bytes.writeBytes(new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8));
                    i += Character.charCount(cp);
                }
            }
            final String decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            if (decoded.indexOf(0) >= 0)
                throw invalid();
            ApiV3Json.canonical(decoded);
            return decoded;
        } catch (java.nio.charset.CharacterCodingException | IllegalArgumentException e) {
            throw invalid();
        }
    }
    public static String required(Map<String, String> values, String key, int max) {
        final String value = values.get(key);
        if (value == null || value.isEmpty() || value.length() > max)
            throw invalid();
        return value;
    }
}
