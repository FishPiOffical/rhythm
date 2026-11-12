package org.b3log.symphony.util;

import org.b3log.latke.Keys;
import org.b3log.symphony.model.UserExt;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 当前实例中新旧客户端共用的在线连接登记，不使用数据库遗留标志推断在线。
 */
public final class UserOnlineStatus {
    private static final Map<Object, String> OWNERS = new HashMap<>();
    private static final Map<String, Map<Object, Long>> USERS = new HashMap<>();

    private UserOnlineStatus() {
    }

    public static synchronized void connected(final Object connection, final String userId, final long expiresAt) {
        if (connection == null || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("online connection owner");
        }
        disconnected(connection);
        OWNERS.put(connection, userId);
        USERS.computeIfAbsent(userId, ignored -> new HashMap<>()).put(connection, expiresAt);
    }

    public static synchronized void disconnected(final Object connection) {
        final String userId = OWNERS.remove(connection);
        if (userId == null) return;
        final Map<Object, Long> connections = USERS.get(userId);
        connections.remove(connection);
        if (connections.isEmpty()) USERS.remove(userId);
    }

    public static boolean isOnline(final JSONObject user) {
        return user != null && isOnline(user.optString(Keys.OBJECT_ID), System.currentTimeMillis());
    }

    public static synchronized boolean isOnline(final String userId, final long now) {
        final Map<Object, Long> connections = USERS.get(userId);
        // 每个标签页、每种订阅独立登记；过期票据在定时清理之前也不能维持在线。
        return connections != null && connections.values().stream().anyMatch(expiresAt -> expiresAt > now);
    }

    public static Boolean publicFlag(final JSONObject user) {
        // 未明确公开、已失效或注销的账号不提供在线信息；本人接口可单独读取实际状态。
        if (user == null || user.optInt(UserExt.USER_ONLINE_STATUS, -1) != UserExt.USER_XXX_STATUS_C_ENABLED
                || user.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID) {
            return null;
        }
        return isOnline(user);
    }
}
