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
import java.util.LinkedHashSet;
import java.util.Set;

/** 接收公开选择器中的有序别名，拒绝模板占位值和未登记的表情名称。 */
public final class ApiV3EmotionSettingsInputs {
    private ApiV3EmotionSettingsInputs() { }
    public static Set<String> allowed() {
        final Set<String> aliases = new LinkedHashSet<>();
        for (final Object group : ApiV3EmotionOptions.options().getJSONArray("groups"))
            for (final Object alias : (JSONArray) group) aliases.add((String) alias);
        return Set.copyOf(aliases);
    }
    public static JSONObject normalize(final JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("emotions")) || !(body.opt("emotions") instanceof JSONArray values))
            throw ApiV3Requests.invalid();
        final Set<String> allowed = allowed(), seen = new LinkedHashSet<>();
        if (values.length() > allowed.size()) throw ApiV3Requests.invalid();
        final JSONArray result = new JSONArray();
        for (final Object value : values) {
            if (!(value instanceof String alias) || !allowed.contains(alias) || !seen.add(alias)) throw ApiV3Requests.invalid();
            result.put(alias);
        }
        return new JSONObject().put("emotions", result);
    }
    public static JSONObject effective(final JSONObject normalized) {
        final JSONArray aliases = normalized.getJSONArray("emotions");
        return new JSONObject().put("emotions", aliases.isEmpty()
                ? ApiV3EmotionOptions.options().getJSONArray("defaults") : new JSONArray(aliases.toString()));
    }
}
