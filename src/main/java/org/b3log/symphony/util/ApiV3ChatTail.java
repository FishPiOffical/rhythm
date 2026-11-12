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

import java.util.Set;
import java.util.regex.Pattern;

/** 小尾巴只保存单行纯文本；字数以 Java 25 的扩展字素边界为准。 */
public final class ApiV3ChatTail {
    public static final int MAX_GRAPHEMES = 24;
    public static final int MAX_UTF16_LENGTH = 512;
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private ApiV3ChatTail() { }

    public static JSONObject input(final JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("enabled", "text"))
                || !(body.opt("enabled") instanceof Boolean enabled) || !(body.opt("text") instanceof String text))
            throw ApiV3WriteInputs.invalid();
        final String normalized = text(text);
        if (enabled && normalized.isEmpty())
            throw new ApiV3Exception(422, "invalid_chat_tail", "请填写小尾巴内容");
        return new JSONObject().put("enabled", enabled).put("text", normalized);
    }

    public static String text(final String value) {
        if (value == null || value.length() > MAX_UTF16_LENGTH)
            throw invalid();
        // 不允许换行、双向控制或隐藏控制字符；Emoji 连接符和标签序列需要保留。
        for (int offset = 0; offset < value.length();) {
            final int point = value.codePointAt(offset);
            final int type = Character.getType(point);
            if (type == Character.SURROGATE || Character.isISOControl(point)
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.FORMAT && point != 0x200D && !(point >= 0xE0020 && point <= 0xE007F))
                throw invalid();
            offset += Character.charCount(point);
        }
        final String normalized = value.strip();
        final var matcher = GRAPHEME.matcher(normalized);
        int count = 0;
        while (matcher.find()) if (++count > MAX_GRAPHEMES) throw invalid();
        if (!normalized.isEmpty() && normalized.codePoints().noneMatch(ApiV3ChatTail::visible)) throw invalid();
        return normalized;
    }

    private static boolean visible(final int point) {
        final int type = Character.getType(point);
        return !Character.isWhitespace(point) && !Character.isSpaceChar(point)
                && type != Character.FORMAT && type != Character.NON_SPACING_MARK
                && type != Character.COMBINING_SPACING_MARK && type != Character.ENCLOSING_MARK;
    }

    /** 排除纯引用和纯媒体；图片替代文字不能让单独的图片变成普通文字消息。 */
    public static boolean eligibleHtml(final String html) {
        final var body = Jsoup.parseBodyFragment(html).body();
        body.select("blockquote,img,picture,audio,video,iframe,object,embed,svg,script,style").remove();
        return body.text().codePoints().anyMatch(ApiV3ChatTail::visible);
    }

    /** 历史坏快照只丢弃尾巴，不把可见的原消息变成不可用。 */
    public static JSONObject snapshot(final Object value) {
        if (!(value instanceof JSONObject snapshot) || !(snapshot.opt("text") instanceof String text)) return null;
        try {
            final String normalized = text(text);
            return normalized.isEmpty() ? null : new JSONObject().put("text", normalized);
        } catch (ApiV3Exception invalid) {
            return null;
        }
    }

    private static ApiV3Exception invalid() {
        return new ApiV3Exception(422, "invalid_chat_tail", "小尾巴须为单行文字或 Emoji，最多24个字，且不超过512个文本单元");
    }
}
