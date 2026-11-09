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

import org.jsoup.Jsoup;
import org.json.JSONObject;

/** 仅返回固定的勋章字段，省略原始样式 JSON、授予记录及其标识。 */
public final class ApiV3MedalDto {
    private ApiV3MedalDto() { }
    private static String text(final String value, final int limit) {
        if (value.length() > limit || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) return "";
        return Jsoup.parse(value).text();
    }
    public static JSONObject project(final JSONObject row, final boolean owner) {
        String description = row.optString("medal_description");
        final String data = row.optString("data");
        if (description.length() > 1024 || data.length() > 128) description = "";
        else {
            final String[] vars = data.split(";", -1);
            if (!data.isEmpty()) for (int i = 0; i < vars.length; i++) {
                description = description.replace("{var" + (i + 1) + "}", vars[i]);
                if (description.length() > 1024) {
                    description = "";
                    break;
                }
            }
        }
        final JSONObject dto = new JSONObject().put("id", text(row.optString("medal_id"), 128))
                .put("name", text(row.optString("medal_name"), 128)).put("type", text(row.optString("medal_type"), 128))
                .put("description", text(description, 1024)).put("order", row.optInt("display_order"))
                .put("expiresAt", row.optLong("expire_time"));
        if (owner) {
            final Object displayed = row.opt("display");
            dto.put("displayed", Boolean.TRUE.equals(displayed) || displayed instanceof Number number && number.intValue() == 1
                    || "true".equalsIgnoreCase(String.valueOf(displayed)) || "1".equals(String.valueOf(displayed)));
        }
        return dto;
    }
}
