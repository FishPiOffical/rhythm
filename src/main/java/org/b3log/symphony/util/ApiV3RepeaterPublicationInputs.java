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

import org.apache.commons.lang.StringUtils;
import org.b3log.symphony.model.RepeaterContent;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.Set;

/**
 * 沿用复读内容规范化规则，并校验输入类型及大小。
 */
public final class ApiV3RepeaterPublicationInputs {
    private ApiV3RepeaterPublicationInputs() {
    }

    public static JSONObject normalize(final JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("type", "content"))
                || !(body.opt("type") instanceof String type) || !(body.opt("content") instanceof String content)
                || type.length() > 32 || content.length() > 8192) throw ApiV3WriteInputs.invalid();
        validateUnicode(type);
        validateUnicode(content);
        final String normalizedType = StringUtils.trimToEmpty(type);
        final String normalizedContent = plainText(content);
        if (!RepeaterContent.isSupportedType(normalizedType) || normalizedContent.length() < RepeaterContent.MIN_CONTENT_LENGTH
                || normalizedContent.length() > RepeaterContent.MAX_CONTENT_LENGTH) throw ApiV3WriteInputs.invalid();
        validateUnicode(normalizedContent);
        return new JSONObject().put("type", normalizedType).put("content", normalizedContent);
    }

    public static String plainText(final String content) {
        if (content == null || content.length() > 8192) throw ApiV3WriteInputs.invalid();
        validateUnicode(content);
        final var document = Jsoup.parseBodyFragment(content);
        document.select("script,style").remove();
        return document.body().wholeText().replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    /** 仅用于核对升级前已经成功的回执，不用于新内容入库。 */
    public static JSONObject legacyReceipt(final JSONObject body) {
        return new JSONObject().put("type", StringUtils.trimToEmpty(body.getString("type")))
                .put("content", Jsoup.clean(StringUtils.trimToEmpty(body.getString("content")), Safelist.none())
                        .replaceAll("\\s+", " "));
    }

    private static void validateUnicode(final String text) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw ApiV3WriteInputs.invalid();
            } else if (Character.isLowSurrogate(c)
                    || Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') throw ApiV3WriteInputs.invalid();
        }
    }
}
