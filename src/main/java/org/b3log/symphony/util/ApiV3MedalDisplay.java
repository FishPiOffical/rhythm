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

/**
 * 展示设置必须是布尔值，勋章 ID 按长度受限的原始键匹配，包括 "0"。
 */
public final class ApiV3MedalDisplay {
    private ApiV3MedalDisplay() {
    }

    public static String medalId(final String id) {
        if (id == null || id.isBlank() || id.length() > 128 || id.codePoints().anyMatch(Character::isISOControl))
            throw ApiV3WriteInputs.invalid();
        try {
            ApiV3Json.canonical(id);
        } catch (final IllegalArgumentException e) {
            throw ApiV3WriteInputs.invalid();
        }
        return id;
    }

    public static boolean displayed(final JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("displayed")) || !(body.opt("displayed") instanceof Boolean value))
            throw ApiV3WriteInputs.invalid();
        return value;
    }

    public static boolean stored(final Object value) {
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number) {
            final long number = expiry(value);
            if (number == 0 || number == 1) return number == 1;
            throw inconsistent();
        }
        if ("true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(value)) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value)) || "0".equals(value)) return false;
        throw inconsistent();
    }

    public static long expiry(final Object value) {
        if (!(value instanceof Number)) throw inconsistent();
        try {
            return new java.math.BigDecimal(value.toString()).longValueExact();
        } catch (final ArithmeticException | NumberFormatException e) {
            throw inconsistent();
        }
    }

    public static ApiV3Exception inconsistent() {
        return new ApiV3Exception(409, "inconsistent_state", "勋章状态暂不可修改");
    }
}
