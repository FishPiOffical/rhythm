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
import java.math.BigDecimal;
import java.util.Set;

/** 仅查询已知邀请码，省略注册可用性和预留信息。 */
public final class ApiV3Invitation {
    private ApiV3Invitation() { }
    public static String code(final JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("code"))) throw ApiV3WriteInputs.invalid();
        return code(ApiV3WriteInputs.string(body, "code", 16, true));
    }
    public static String code(final String value) {
        if (value == null) throw ApiV3WriteInputs.invalid();
        final String result = value.trim();
        if (!result.matches("[A-Za-z0-9]{16}")) throw ApiV3WriteInputs.invalid();
        return result;
    }
    public static JSONObject status(final JSONObject row) {
        if (!(row.opt("status") instanceof Number number)) throw unavailable();
        final int value;
        try { value = new BigDecimal(number.toString()).intValueExact(); }
        catch (final ArithmeticException | NumberFormatException e) { throw unavailable(); }
        final String state = switch (value) {
            case 0 -> "used";
            case 1 -> "unused";
            case 2 -> "stopped";
            default -> throw unavailable();
        };
        return new JSONObject().put("status", state);
    }
    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "邀请码状态暂不可用");
    }
}
