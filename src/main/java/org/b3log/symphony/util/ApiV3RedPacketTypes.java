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

/**
 * 供客户端只读查询已识别的红包类型，积分操作仍需独立授权。
 */
public final class ApiV3RedPacketTypes {
    private ApiV3RedPacketTypes() {
    }

    public static JSONObject catalog() {
        final JSONArray types = new JSONArray();
        types.put(type("random", "拼手气红包", false));
        types.put(type("average", "普通红包", false));
        types.put(type("specify", "专属红包", false));
        types.put(type("heartbeat", "心跳红包", true));
        types.put(type("rockPaperScissors", "猜拳红包", true));
        return new JSONObject().put("types", types)
                .put("creationSupported", true).put("claimSupported", true);
    }

    private static JSONObject type(final String id, final String name, final boolean mayLosePoints) {
        return new JSONObject().put("id", id).put("name", name).put("mayLosePoints", mayLosePoints);
    }
}
