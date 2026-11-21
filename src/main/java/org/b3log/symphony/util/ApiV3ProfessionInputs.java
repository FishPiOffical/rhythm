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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 在共用职业 Mapper 前拒绝类型强转；旧页面表单协议不受影响。 */
public final class ApiV3ProfessionInputs {
    private ApiV3ProfessionInputs() { }
    private static final Set<String> ARRAYS = Set.of("levels", "presentations", "rewards", "professionIds");
    private static final Set<String> INTEGERS = Set.of("sortOrder", "requiredTotalExperience");

    public static void validate(final JSONObject payload) {
        for (final String key : payload.keySet()) {
            final Object value = payload.opt(key);
            if (ARRAYS.contains(key)) {
                if (!(value instanceof JSONArray array)) throw ApiV3WriteInputs.invalid();
                for (final Object item : array) {
                    if ("professionIds".equals(key)) {
                        if (!(item instanceof String)) throw ApiV3WriteInputs.invalid();
                    } else {
                        if (!(item instanceof JSONObject object)) throw ApiV3WriteInputs.invalid();
                        validate(object);
                    }
                }
            } else if (INTEGERS.contains(key)) {
                if (!(value instanceof Number number)) throw ApiV3WriteInputs.invalid();
                try {
                    final BigDecimal decimal = new BigDecimal(number.toString());
                    if ("sortOrder".equals(key)) decimal.intValueExact();
                    else decimal.longValueExact();
                } catch (final ArithmeticException | NumberFormatException e) { throw ApiV3WriteInputs.invalid(); }
            } else if ("isTopLevel".equals(key)) {
                if (!(value instanceof Boolean)) throw ApiV3WriteInputs.invalid();
            } else {
                if (!(value instanceof String text) || text.length() > 16_000_000 || text.indexOf(0) >= 0) {
                    throw ApiV3WriteInputs.invalid();
                }
                if ("configJson".equals(key)) {
                    try { ApiV3Json.parse(text.getBytes(StandardCharsets.UTF_8), ApiV3Json.MAX_IMPORT_BODY_BYTES); }
                    catch (final IllegalArgumentException e) { throw ApiV3WriteInputs.invalid(); }
                }
            }
        }
    }
}
