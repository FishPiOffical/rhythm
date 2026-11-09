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

import org.b3log.symphony.model.Milestone;
import org.json.JSONObject;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/** 校验里程碑投稿的 Markdown 输入，上限沿用里程碑的存储约定。 */
public final class ApiV3MilestoneInputs {
    private ApiV3MilestoneInputs() { }
    public static final Set<String> FIELDS = Set.of("title", "date", "endDate", "content", "mediaURL", "mediaType", "mediaCaption", "link");
    public static JSONObject normalize(final JSONObject body) {
        if (!FIELDS.containsAll(body.keySet())) throw ApiV3WriteInputs.invalid();
        final String title = text(body, "title", 255, true, false), content = text(body, "content", 102400, true, true);
        final LocalDate start = date(text(body, "date", 10, true, false));
        final String endText = text(body, "endDate", 10, false, false);
        final LocalDate end = endText.isEmpty() ? null : date(endText);
        if (null != end && end.isBefore(start)) throw ApiV3WriteInputs.invalid();
        final String media = url(text(body, "mediaURL", 1024, false, false));
        final String type = text(body, "mediaType", 16, false, false);
        if (!Set.of("", "image", "video").contains(type) || (!media.isEmpty() && type.isEmpty())) throw ApiV3WriteInputs.invalid();
        return new JSONObject().put(Milestone.MILESTONE_TITLE, title)
                .put(Milestone.MILESTONE_DATE, start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .put(Milestone.MILESTONE_END_DATE, null == end ? 0 : end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .put(Milestone.MILESTONE_CONTENT, content).put(Milestone.MILESTONE_MEDIA_URL, media)
                .put(Milestone.MILESTONE_MEDIA_TYPE, type).put(Milestone.MILESTONE_MEDIA_CAPTION, text(body, "mediaCaption", 255, false, false))
                .put(Milestone.MILESTONE_LINK, url(text(body, "link", 1024, false, false)));
    }
    private static String text(final JSONObject body, final String field, final int max, final boolean required, final boolean multiline) {
        final String text = ApiV3WriteInputs.string(body, field, max, true);
        if ((required && text.isBlank()) || text.codePoints().anyMatch(cp -> Character.isISOControl(cp) && !(multiline && (cp == '\n' || cp == '\r' || cp == '\t')))) throw ApiV3WriteInputs.invalid();
        return text;
    }
    private static LocalDate date(final String value) {
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw ApiV3WriteInputs.invalid();
            final LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1) throw ApiV3WriteInputs.invalid();
            return date;
        } catch (final java.time.DateTimeException e) { throw ApiV3WriteInputs.invalid(); }
    }
    private static String url(final String value) {
        if (value.isEmpty()) return "";
        try {
            final URI uri = new URI(value);
            if (value.codePoints().anyMatch(cp -> Character.isWhitespace(cp) || Character.isISOControl(cp))) throw ApiV3WriteInputs.invalid();
            if (value.startsWith("/")) {
                if (value.startsWith("//") || null != uri.getRawAuthority()) throw ApiV3WriteInputs.invalid();
            } else if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || null == uri.getHost() || null != uri.getUserInfo()) throw ApiV3WriteInputs.invalid();
            final String normalized = value.replace("'", "%27");
            if (normalized.length() > 1024) throw ApiV3WriteInputs.invalid();
            return normalized;
        } catch (final java.net.URISyntaxException e) { throw ApiV3WriteInputs.invalid(); }
    }
}
