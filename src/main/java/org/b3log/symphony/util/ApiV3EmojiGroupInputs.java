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

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 校验表情分组设置的字段类型，分组名称须为纯文本。
 */
public final class ApiV3EmojiGroupInputs {
    private static final Pattern MARKUP = Pattern.compile("<\\s*(?:/?\\s*[a-zA-Z]|!|\\?)");

    private ApiV3EmojiGroupInputs() {
    }

    public static JSONObject normalize(final JSONObject body) {
        if (body == null || !Set.of("name", "sort").containsAll(body.keySet())
                || !(body.opt("name") instanceof String raw) || raw.length() > 4096) throw ApiV3WriteInputs.invalid();
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029') throw ApiV3WriteInputs.invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= raw.length() || !Character.isLowSurrogate(raw.charAt(i))) throw ApiV3WriteInputs.invalid();
            } else if (Character.isLowSurrogate(c)) throw ApiV3WriteInputs.invalid();
        }
        final String name = raw.strip();
        if (name.isEmpty() || name.length() > 64 || MARKUP.matcher(name).find()) throw ApiV3WriteInputs.invalid();
        return new JSONObject().put("name", name).put("sort", ApiV3WriteInputs.integer(body, "sort", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }
}
