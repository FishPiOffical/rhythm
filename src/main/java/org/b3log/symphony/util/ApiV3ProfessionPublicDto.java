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

/**
 * 按白名单投影公开职业字段，各处均独立省略经验值。
 */
public final class ApiV3ProfessionPublicDto {
    private ApiV3ProfessionPublicDto() {
    }

    public static String text(final JSONObject row, final String key, final int max, final boolean empty) {
        return ApiV3ProfessionDetailDto.text(row, key, max, empty);
    }

    public static long number(final JSONObject row, final String key, final boolean signed) {
        return ApiV3ProfessionDetailDto.number(row, key, signed);
    }

    public static String id(final JSONObject row, final String key) {
        final String id = text(row, key, 19, false);
        if (!id.matches("[0-9]{1,19}")) throw ApiV3ProfessionDetailDto.unavailable();
        return id;
    }

    public static JSONObject definition(final JSONObject row) {
        return new JSONObject().put("professionId", id(row, "professionId"))
                .put("professionCode", text(row, "professionCode", 64, false)).put("displayName", text(row, "displayName", 64, false))
                .put("shortName", text(row, "shortName", 32, true)).put("description", text(row, "description", 1024, true));
    }

    public static JSONObject progress(final JSONObject row, final boolean experience) {
        final JSONObject value = definition(row).put("state", "PUBLISHED".equals(row.getString("professionStatus")) ? "ACTIVE" : "RETIRED")
                .put("levelCode", text(row, "levelCode", 64, false)).put("levelName", text(row, "levelName", 64, false));
        // 隐藏的数值也要校验，但不能借其他模块暴露。
        final long total = number(row, "totalExperience", false), required = number(row, "requiredExperience", false);
        if (experience) value.put("totalExperience", total).put("requiredExperience", required);
        return value;
    }

    public static String action(final JSONObject row) {
        final String value = text(row, "actionCode", 64, false);
        if (!value.matches("[a-z][a-z0-9._-]{0,63}")) throw ApiV3ProfessionDetailDto.unavailable();
        return value;
    }

    public static JSONObject contribution(final JSONObject row, final boolean experience) {
        final JSONObject value = new JSONObject().put("professionId", id(row, "professionId")).put("actionCode", action(row))
                .put("eventCount", number(row, "eventCount", false)).put("lastOccurredAt", number(row, "lastOccurredAt", false));
        final long sum = number(row, "experienceSum", true);
        if (experience) value.put("experienceSum", sum);
        return value;
    }
}
