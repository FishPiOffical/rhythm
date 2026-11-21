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

import org.b3log.latke.Keys;
import org.b3log.symphony.model.Notification;
import org.json.JSONObject;

/**
 * 响应只使用通知字段的公开投影，省略原始引用、所有者和内容字段。
 */
public final class ApiV3NotificationDto {
    private ApiV3NotificationDto() {
    }

    public static JSONObject base(final JSONObject row) {
        final int type = row.optInt(Notification.NOTIFICATION_DATA_TYPE);
        return new JSONObject().put("id", row.optString(Keys.OBJECT_ID)).put("type", type)
                .put("category", ApiV3NotificationTypes.category(type)).put("title", ApiV3NotificationTypes.label(type))
                .put("hasRead", row.optBoolean(Notification.NOTIFICATION_HAS_READ)).put("createdAt", row.optLong(Keys.OBJECT_ID))
                .put("descriptionHtml", "").put("description", "").put("url", "").put("contentAvailable", true);
    }

    public static JSONObject unavailable(final JSONObject dto) {
        dto.remove("article");
        dto.remove("comment");
        dto.remove("actor");
        return dto.put("contentAvailable", false).put("url", "").put("descriptionHtml", "相关内容不存在或不可访问")
                .put("description", "相关内容不存在或不可访问");
    }
}
