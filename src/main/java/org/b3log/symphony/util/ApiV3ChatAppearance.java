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

import java.util.Set;

/**
 * 不可变的 v1 聊天气泡样式目录，历史快照无需重验作者当前的会员状态。
 */
public final class ApiV3ChatAppearance {
    private ApiV3ChatAppearance() {
    }

    private static final Set<String> BUBBLES = Set.of("classic", "candy", "paipai-orange", "fishpond-blue", "sea-soda", "sunset-letter", "star-route");
    private static final Set<String> FRAMES = Set.of("none", "gold", "paipai-orange", "fishpond-blue", "tide", "paipai-stamp", "star-ring");

    public static JSONObject defaults() {
        return new JSONObject().put("bubbleId", "classic").put("frameId", "none").put("version", 1);
    }

    public static JSONObject input(JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("bubbleId", "frameId"))
                || !(body.opt("bubbleId") instanceof String bubble) || !BUBBLES.contains(bubble)
                || !(body.opt("frameId") instanceof String frame) || !FRAMES.contains(frame))
            throw ApiV3WriteInputs.invalid();
        return new JSONObject().put("bubbleId", bubble).put("frameId", frame);
    }

    public static boolean premium(JSONObject value) {
        return "candy".equals(value.optString("bubbleId")) || "gold".equals(value.optString("frameId"));
    }

    public static JSONObject effective(JSONObject value, boolean vip) {
        final JSONObject current = publicSnapshot(value);
        if (!vip) {
            if ("candy".equals(current.optString("bubbleId")))
                current.put("bubbleId", "classic");
            if ("gold".equals(current.optString("frameId")))
                current.put("frameId", "none");
        }
        return current;
    }

    public static JSONObject publicSnapshot(Object raw) {
        if (!(raw instanceof JSONObject value) || !(value.opt("version") instanceof Number n) || n.doubleValue() != 1)
            return defaults();
        return new JSONObject().put("bubbleId", BUBBLES.contains(value.optString("bubbleId")) ? value.getString("bubbleId") : "classic")
                .put("frameId", FRAMES.contains(value.optString("frameId")) ? value.getString("frameId") : "none").put("version", 1);
    }
}
