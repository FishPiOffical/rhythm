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

import java.util.Locale;
import java.util.Set;

/**
 * 排序方向枚举忽略大小写，保留传入值两端的空白进行校验。
 */
public final class ApiV3MedalOrderInputs {
    private ApiV3MedalOrderInputs() {
    }

    public static String direction(final JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("direction")) || !(body.opt("direction") instanceof String value))
            throw ApiV3WriteInputs.invalid();
        return direction(value);
    }

    public static String direction(final String value) {
        if (value == null || value.length() > 4)
            throw ApiV3WriteInputs.invalid();
        final String normalized = value.toLowerCase(Locale.ROOT);
        if (!Set.of("up", "down").contains(normalized))
            throw ApiV3WriteInputs.invalid();
        return normalized;
    }

    public static int order(final Object value) {
        final long number = ApiV3MedalDisplay.expiry(value);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
            throw ApiV3MedalDisplay.inconsistent();
        return (int) number;
    }
}
