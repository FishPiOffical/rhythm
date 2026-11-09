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
        final String normalizedContent = Jsoup.clean(StringUtils.trimToEmpty(content), Safelist.none()).replaceAll("\\s+", " ");
        if (!RepeaterContent.isSupportedType(normalizedType) || normalizedContent.length() < RepeaterContent.MIN_CONTENT_LENGTH
                || normalizedContent.length() > RepeaterContent.MAX_CONTENT_LENGTH) throw ApiV3WriteInputs.invalid();
        validateUnicode(normalizedContent);
        return new JSONObject().put("type", normalizedType).put("content", normalizedContent);
    }

    private static void validateUnicode(final String text) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw ApiV3WriteInputs.invalid();
            } else if (Character.isLowSurrogate(c) || c == 0) throw ApiV3WriteInputs.invalid();
        }
    }
}
