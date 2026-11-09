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

/** 本人职业详情仅返回类型明确的展示和进度字段。 */
public final class ApiV3ProfessionDetailDto {
    private ApiV3ProfessionDetailDto() { }
    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "职业详情暂不可用");
    }
    public static String text(final JSONObject row, final String key, final int max, final boolean empty) {
        if (!(row.opt(key) instanceof String value) || value.length() > max || (!empty && value.isBlank())
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) throw unavailable();
        try { ApiV3Json.canonical(value); } catch (final IllegalArgumentException e) { throw unavailable(); }
        return value;
    }
    public static long number(final JSONObject row, final String key, final boolean signed) {
        if (!(row.opt(key) instanceof Number value)) throw unavailable();
        try {
            final long result = new BigDecimal(value.toString()).longValueExact();
            if (!signed && result < 0) throw unavailable();
            return result;
        } catch (final ArithmeticException | NumberFormatException e) { throw unavailable(); }
    }
    private static boolean bool(final JSONObject row, final String key) {
        final Object value = row.opt(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n && ("0".equals(n.toString()) || "1".equals(n.toString()))) return n.intValue() == 1;
        throw unavailable();
    }
    public static JSONObject profession(final JSONObject row) {
        return new JSONObject().put("professionId", text(row,"professionId",19,false))
                .put("professionCode",text(row,"professionCode",64,false))
                .put("displayName",text(row,"displayName",64,false)).put("shortName",text(row,"shortName",32,true))
                .put("description",text(row,"description",1024,true))
                .put("state", "PUBLISHED".equals(row.getString("professionStatus")) ? "ACTIVE" : "RETIRED")
                .put("totalExperience",number(row,"totalExperience",false))
                .put("levelCode",text(row,"levelCode",64,false)).put("levelName",text(row,"levelName",64,false))
                .put("requiredExperience",number(row,"requiredExperience",false));
    }
    public static JSONObject level(final JSONObject row) {
        final long order = number(row,"sortOrder",true);
        if (order < Integer.MIN_VALUE || order > Integer.MAX_VALUE) throw unavailable();
        return new JSONObject().put("levelCode",text(row,"levelCode",64,false)).put("sortOrder",order)
                .put("requiredExperience",number(row,"requiredTotalExperience",false))
                .put("displayName",text(row,"displayName",64,false)).put("shortName",text(row,"shortName",32,true))
                .put("description",text(row,"description",1024,true))
                .put("achievementDescription",text(row,"achievementDescription",1024,true)).put("topLevel",bool(row,"isTopLevel"));
    }
    private static String action(final JSONObject row) {
        final String code = text(row,"actionCode",64,false);
        // 职业自动化登记、外部事件和迁移事件都使用这套命名规则。
        if (!code.matches("[a-z][a-z0-9._-]{0,63}")) throw unavailable();
        return code;
    }
    public static JSONObject contribution(final JSONObject row) {
        return new JSONObject().put("actionCode",action(row))
                .put("experienceSum",number(row,"experienceSum",true)).put("eventCount",number(row,"eventCount",false))
                .put("lastOccurredAt",number(row,"lastOccurredAt",false));
    }
    public static JSONObject record(final JSONObject row) {
        return new JSONObject().put("actionCode",action(row))
                .put("experienceDelta",number(row,"actualExperienceDelta",true))
                .put("beforeExperience",number(row,"beforeExperience",false)).put("afterExperience",number(row,"afterExperience",false))
                .put("beforeLevelCode",text(row,"beforeLevelCode",64,true)).put("afterLevelCode",text(row,"afterLevelCode",64,true))
                .put("occurredAt",number(row,"occurredAt",false));
    }
}
