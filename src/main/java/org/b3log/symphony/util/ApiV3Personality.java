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
import java.util.List;
import java.util.Locale;

/** MBTI choices already offered by account settings; no user/contact fields. */
public final class ApiV3Personality {
    private ApiV3Personality() { }
    public static final List<String> TYPES = List.of("INTJ", "INTP", "ENTJ", "ENTP", "INFJ", "INFP", "ENFJ", "ENFP", "ISTJ", "ISFJ", "ESTJ", "ESFJ", "ISTP", "ISFP", "ESTP", "ESFP");
    public static JSONObject options() {
        return new JSONObject().put("types", new JSONArray(TYPES)).put("variants", new JSONArray(List.of("A", "T")));
    }
    public static String normalize(final String input) {
        if (input == null) throw ApiV3WriteInputs.invalid();
        final String value = input.trim();
        if (!value.isEmpty() && !value.matches("(?i)[IE][NS][TF][JP](?:-[AT])?")) throw ApiV3WriteInputs.invalid();
        return value.toUpperCase(Locale.ROOT);
    }
    public static JSONObject dto(final JSONObject row) {
        final String value = row.optString("mbti");
        return new JSONObject().put("mbti", value.matches("[IE][NS][TF][JP](?:-[AT])?") ? value : "");
    }
}
