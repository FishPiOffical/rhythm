package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.ApiV3RealtimeSessions;
import org.b3log.symphony.util.UserOnlineStatus;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

/** 新旧连接共同在线、隐私投影和异常关闭的回归测试。 */
public class UserOnlineStatusTest {
    @Test
    public void connectionsAndPrivacy() {
        final Object legacy = new Object();
        final Object secondLegacyTab = new Object();
        final Object v3 = new Object();
        final String userId = "online-regression";
        final JSONObject user = new JSONObject().put(Keys.OBJECT_ID, userId)
                .put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID)
                .put(UserExt.USER_ONLINE_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED)
                .put(UserExt.USER_ONLINE_FLAG, true);
        try {
            Assert.assertFalse(UserOnlineStatus.isOnline(user), "数据库遗留标志不能冒充有效连接");
            UserOnlineStatus.connected(legacy, userId, Long.MAX_VALUE);
            UserOnlineStatus.connected(secondLegacyTab, userId, Long.MAX_VALUE);
            UserOnlineStatus.connected(v3, userId, Long.MAX_VALUE);
            Assert.assertEquals(UserOnlineStatus.publicFlag(user), Boolean.TRUE);
            UserOnlineStatus.disconnected(legacy);
            UserOnlineStatus.disconnected(legacy);
            UserOnlineStatus.disconnected(secondLegacyTab);
            Assert.assertTrue(UserOnlineStatus.isOnline(user), "旧版所有标签关闭后，V3 仍然在线");
            user.put(UserExt.USER_ONLINE_STATUS, UserExt.USER_XXX_STATUS_C_DISABLED);
            Assert.assertNull(UserOnlineStatus.publicFlag(user));
            Assert.assertFalse(new JSONObject().put("userOnlineFlag", UserOnlineStatus.publicFlag(user)).has("userOnlineFlag"),
                    "V3 公开 JSON 隐藏在线字段，不用离线值冒充未知状态");
            Assert.assertTrue(UserOnlineStatus.isOnline(user), "隐身不改变本人真实连接状态");
            user.remove(UserExt.USER_ONLINE_STATUS);
            Assert.assertNull(UserOnlineStatus.publicFlag(user));
            user.put(UserExt.USER_ONLINE_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
            user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_DEACTIVATED);
            Assert.assertNull(UserOnlineStatus.publicFlag(user));
            user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
            UserOnlineStatus.disconnected(v3);
            Assert.assertEquals(UserOnlineStatus.publicFlag(user), Boolean.FALSE);
            UserOnlineStatus.connected(v3, userId, 100);
            Assert.assertTrue(UserOnlineStatus.isOnline(userId, 99));
            Assert.assertFalse(UserOnlineStatus.isOnline(userId, 100), "票据到期立即不计在线");
            UserOnlineStatus.connected(v3, "other-owner", Long.MAX_VALUE);
            Assert.assertFalse(UserOnlineStatus.isOnline(userId, 0), "同一连接换身份不能遗留旧用户");
            Assert.assertTrue(UserOnlineStatus.isOnline("other-owner", 0));
        } finally {
            UserOnlineStatus.disconnected(legacy);
            UserOnlineStatus.disconnected(secondLegacyTab);
            UserOnlineStatus.disconnected(v3);
        }
    }

    @Test
    public void authenticatedRealtimeLifecycle() {
        final long[] clock = {1000};
        final boolean[] authorized = {true};
        final String actor = "online-v3-regression";
        final var registry = new ApiV3RealtimeSessions(new ApiV3RealtimeSessions.Authority() {
            public ApiV3SocketTicketService.Grant consume(final String ticket) {
                return new ApiV3SocketTicketService.Grant(actor, "parent", 2000, "chat", "");
            }

            public boolean authorized(final ApiV3SocketTicketService.Grant grant) {
                return authorized[0];
            }
        }, (ip, record) -> true, () -> clock[0]);
        final Object legacy = new Object();
        try {
            registry.open("pending", new ApiV3RealtimeSessionsTest.Transport(), "192.0.2.1");
            Assert.assertFalse(UserOnlineStatus.isOnline(actor, clock[0]), "未认证的连接不能登记在线");
            final String auth = new JSONObject().put("type", "authenticate").put("ticket", "A".repeat(43)).toString();
            registry.receive("pending", auth);
            Assert.assertTrue(UserOnlineStatus.isOnline(actor, clock[0]));
            registry.open("second", new ApiV3RealtimeSessionsTest.Transport(), "192.0.2.2");
            registry.receive("second", auth);
            registry.close("pending");
            Assert.assertTrue(UserOnlineStatus.isOnline(actor, clock[0]), "关闭一个 V3 标签页不能离线");
            UserOnlineStatus.connected(legacy, actor, Long.MAX_VALUE);
            authorized[0] = false;
            registry.receive("second", "{\"type\":\"ping\"}");
            Assert.assertTrue(UserOnlineStatus.isOnline(actor, clock[0]), "V3 授权失效不影响旧版有效连接");
            UserOnlineStatus.disconnected(legacy);
            Assert.assertFalse(UserOnlineStatus.isOnline(actor, clock[0]));
            authorized[0] = true;
            final var failed = new ApiV3RealtimeSessionsTest.Transport();
            registry.open("failed-ready", failed, "192.0.2.3");
            failed.failSend = true;
            registry.receive("failed-ready", auth);
            Assert.assertFalse(UserOnlineStatus.isOnline(actor, clock[0]), "ready 发送失败不得遗留在线");
            registry.open("expires", new ApiV3RealtimeSessionsTest.Transport(), "192.0.2.4");
            registry.receive("expires", auth);
            clock[0] = 2000;
            Assert.assertFalse(UserOnlineStatus.isOnline(actor, clock[0]));
        } finally {
            for (final String key : new String[]{"pending", "second", "failed-ready", "expires"}) registry.close(key);
            UserOnlineStatus.disconnected(legacy);
        }
    }

    public static void main(final String[] args) {
        final var test = new UserOnlineStatusTest();
        test.connectionsAndPrivacy();
        test.authenticatedRealtimeLifecycle();
        System.out.println("PASS 新旧在线状态、隐私、多标签、失效及失败连接回归");
    }
}
