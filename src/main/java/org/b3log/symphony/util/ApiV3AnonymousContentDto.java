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

/**
 * 仅投影元数据，省略匿名作者标识、正文、打赏和请求审计字段。
 */
public final class ApiV3AnonymousContentDto {
    private ApiV3AnonymousContentDto() {
    }

    public static JSONObject article(final JSONObject row) {
        final String id = row.getString("oId"), title = row.getString("articleTitle");
        if (!id.matches("[0-9]{1,32}") || title.length() > 255)
            throw unavailable();
        try {
            ApiV3Json.canonical(title);
        } catch (final IllegalArgumentException error) {
            throw unavailable();
        }
        return new JSONObject().put("id", id).put("title", Jsoup.parse(title).text())
                .put("type", number(row, "articleType", 6))
                .put("createdAt", time(row, "articleCreateTime")).put("updatedAt", time(row, "articleUpdateTime"));
    }

    public static JSONObject comment(final JSONObject row) {
        final String id = row.getString("oId"), articleId = row.getString("articleId"), title = row.getString("articleTitle");
        if (!id.matches("[0-9]{1,32}") || !articleId.matches("[0-9]{1,32}") || title.length() > 255)
            throw unavailable();
        try {
            ApiV3Json.canonical(title);
        } catch (final IllegalArgumentException error) {
            throw unavailable();
        }
        return new JSONObject().put("id", id).put("articleId", articleId).put("articleTitle", Jsoup.parse(title).text())
                .put("createdAt", time(row, "commentCreateTime")).put("visibility", number(row, "commentVisible", 1));
    }

    private static long time(final JSONObject row, final String field) {
        final Object value = row.opt(field);
        if (!(value instanceof Number) || !value.toString().matches("[0-9]{1,19}"))
            throw unavailable();
        try {
            return Long.parseLong(value.toString());
        } catch (final NumberFormatException error) {
            throw unavailable();
        }
    }

    private static int number(final JSONObject row, final String field, final int max) {
        final long value = time(row, field);
        if (value > max)
            throw unavailable();
        return (int) value;
    }

    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "匿名内容暂不可用");
    }
}
