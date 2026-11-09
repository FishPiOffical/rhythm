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

import java.net.URI;

/**
 * 临时弹幕投递使用不可变的公开字段，省略完整用户对象和旧消息封装。
 */
public record ApiV3BarrageEvent(String id, String content, String color, long createdAt,
                                String authorId, String userName, String userNickname, String userAvatarURL) {
    public ApiV3BarrageEvent {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || createdAt < 0 || authorId == null || !authorId.matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("barrage identity");
        valid(content, 32);
        valid(userName, 64);
        valid(userNickname, 64);
        if (content.isBlank() || userName.isBlank() || color == null || !color.matches("#[0-9A-F]{6}"))
            throw new IllegalArgumentException("barrage content");
        userAvatarURL = safeAvatar(userAvatarURL);
    }

    private static void valid(String value, int max) {
        if (value == null || value.length() > max)
            throw new IllegalArgumentException("barrage public text");
        ApiV3Json.canonical(value);
    }

    private static String safeAvatar(String value) {
        if (value == null || value.length() > 2048)
            return "";
        try {
            final URI url = URI.create(value);
            return ("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
                    && url.getHost() != null && url.getUserInfo() == null ? url.toASCIIString() : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public JSONObject dto() {
        return new JSONObject().put("id", id).put("content", content).put("color", color).put("createdAt", createdAt)
                .put("author", new JSONObject().put("id", authorId).put("userName", userName)
                        .put("userNickname", userNickname).put("userAvatarURL", userAvatarURL));
    }
}
