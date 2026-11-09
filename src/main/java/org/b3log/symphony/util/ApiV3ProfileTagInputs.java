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

import org.b3log.symphony.model.Tag;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 规范化之前逐个校验原始标签，遇到无效值直接拒绝。 */
public final class ApiV3ProfileTagInputs {
    private ApiV3ProfileTagInputs() { }
    public static List<String> tokens(JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("tags"))
                || !(body.opt("tags") instanceof String raw) || raw.length() > 128)
            throw ApiV3WriteInputs.invalid();
        if (raw.isBlank())
            return List.of();
        final String compact = raw.replaceAll("\\s+", "").replace('，', ',').replace('、', ',').replace('；', ',').replace(';', ',');
        final List<String> result = new ArrayList<>();
        for (String token : compact.split(",", -1)) {
            // 连续分隔符之间允许空项，仅由分隔符组成的输入会在下方拒绝。
            if (token.isEmpty())
                continue;
            requireTitle(token);
            if (result.stream().noneMatch(value -> value.equalsIgnoreCase(token)))
                result.add(token);
        }
        if (result.isEmpty())
            throw ApiV3WriteInputs.invalid();
        return result;
    }
    public static void requireTitle(String value) {
        if (value == null || value.isEmpty() || value.length() > 128
                || value.codePoints().anyMatch(cp -> Character.isISOControl(cp) || Character.isWhitespace(cp))
                || value.indexOf('<') >= 0 || value.indexOf('>') >= 0 || value.indexOf(',') >= 0 || value.indexOf(';') >= 0)
            throw ApiV3WriteInputs.invalid();
        if (!Tag.TAG_TITLE_PATTERN.matcher(value).matches()
                || !Tag.containsWhiteListTags(value) && value.length() > Tag.MAX_TAG_TITLE_LENGTH)
            throw ApiV3WriteInputs.invalid();
    }
    public static JSONObject result(List<String> titles) {
        if (titles.size() > Tag.MAX_TAG_COUNT)
            throw ApiV3WriteInputs.invalid();
        for (String title : titles)
            requireTitle(title);
        final String value = String.join(",", titles);
        if (value.length() > 128)
            throw ApiV3WriteInputs.invalid();
        return new JSONObject().put("tags", value);
    }
}
