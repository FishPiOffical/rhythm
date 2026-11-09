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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** 校验本人专栏写入请求的字段与类型。 */
public final class ApiV3ColumnInputs {
    private ApiV3ColumnInputs() { }
    public static String text(final JSONObject body, final String field, final int max, final boolean emptyAllowed) {
        if (!body.has(field)) throw ApiV3WriteInputs.invalid();
        final String value = ApiV3WriteInputs.string(body, field, max, true);
        if ((!emptyAllowed && value.isEmpty()) || value.codePoints().anyMatch(Character::isISOControl)) throw ApiV3WriteInputs.invalid();
        return value;
    }
    public static List<String> order(final JSONObject body) {
        if (!(body.opt("columnIds") instanceof JSONArray array) || array.isEmpty() || array.length() > 200) throw ApiV3WriteInputs.invalid();
        final List<String> ids = new ArrayList<>();
        final HashSet<String> seen = new HashSet<>();
        for (final Object value : array) {
            if (!(value instanceof String id) || !id.matches("[0-9]{1,19}") || !seen.add(id)) throw ApiV3WriteInputs.invalid();
            ids.add(id);
        }
        return ids;
    }
}
