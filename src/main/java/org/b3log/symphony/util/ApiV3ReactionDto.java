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

import org.b3log.symphony.model.Reaction;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 表态响应按白名单投影，包括提示框内嵌的公开用户资料。
 */
public final class ApiV3ReactionDto {
    private ApiV3ReactionDto() {
    }

    public static JSONObject from(final JSONObject raw) {
        final JSONArray summary = new JSONArray(), source = raw.optJSONArray(Reaction.RESPONSE_SUMMARY);
        if (null != source) for (int i = 0; i < source.length(); i++) {
            final JSONObject item = source.optJSONObject(i);
            if (null == item || !Reaction.isValidEmojiValue(item.optString("value")) || item.optLong("count") <= 0)
                continue;
            final JSONArray names = new JSONArray(), profiles = new JSONArray();
            final JSONArray details = item.optJSONArray("userDetails");
            if (null != details) for (int j = 0; j < details.length(); j++) {
                final JSONObject user = details.optJSONObject(j);
                if (null == user) continue;
                final String displayName = user.optString("displayName");
                if (!displayName.isEmpty()) names.put(displayName);
                profiles.put(new JSONObject().put("userName", user.optString("userName")).put("displayName", displayName)
                        .put("avatarURL", ApiV3CommunityDto.safeUrl(user.optString("avatarURL"))));
            }
            summary.put(new JSONObject().put("value", item.optString("value")).put("emoji", Reaction.emojiOf(item.optString("value")))
                    .put("count", item.optLong("count")).put("selected", item.optBoolean("selected"))
                    .put("users", names).put("userDetails", profiles));
        }
        final String current = raw.optString(Reaction.FIELD_CURRENT_USER_REACTION);
        return new JSONObject().put("targetId", raw.optString("targetId")).put("targetType", raw.optString("targetType"))
                .put("groupType", Reaction.GROUP_EMOJI).put("currentUserReaction", Reaction.isValidEmojiValue(current) ? current : "").put("summary", summary);
    }

    public static String value(final JSONObject body) {
        final String value = ApiV3WriteInputs.string(body, "value", 32, true);
        if (!Reaction.isValidEmojiValue(value)) throw ApiV3WriteInputs.invalid();
        return value;
    }

    public static JSONArray emojis() {
        final JSONArray list = new JSONArray();
        for (final String value : Reaction.emojiValues())
            list.put(new JSONObject().put("value", value).put("emoji", Reaction.emojiOf(value)));
        return list;
    }
}
