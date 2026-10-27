package org.b3log.symphony.util;

import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only discovery of recognized packet types; this does not authorize a money operation. */
public final class ApiV3RedPacketTypes {
    private ApiV3RedPacketTypes() { }

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
