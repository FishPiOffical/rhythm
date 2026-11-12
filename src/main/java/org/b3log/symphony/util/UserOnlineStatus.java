package org.b3log.symphony.util;

import org.b3log.latke.Keys;
import org.b3log.symphony.model.UserExt;
import org.json.JSONObject;

/**
 * 当前实例中新旧客户端共用的在线连接登记，不使用数据库遗留标志推断在线。
 */
public final class UserOnlineStatus {
    private static final UserOnlineTracker TRACKER = new UserOnlineTracker(System::currentTimeMillis, System::nanoTime);

    private UserOnlineStatus() {
    }

    public static void connected(final Object connection, final String userId, final long expiresAt) {
        TRACKER.connected(connection, userId, expiresAt);
    }

    public static void disconnected(final Object connection) {
        TRACKER.disconnected(connection);
    }

    public static boolean renew(final Object connection, final long expiresAt) {
        return TRACKER.renew(connection, expiresAt);
    }

    public static UserOnlineTracker tracker() {
        return TRACKER;
    }

    public static boolean isOnline(final JSONObject user) {
        return user != null && isOnline(user.optString(Keys.OBJECT_ID));
    }

    public static boolean isOnline(final String userId) {
        return TRACKER.isOnline(userId);
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
