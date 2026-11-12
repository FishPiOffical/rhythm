package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.ApiV3RealtimeSessions;
import org.b3log.symphony.util.UserOnlineStatus;
import org.b3log.symphony.util.UserOnlineTracker;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** 新旧连接共同在线、隐私投影和异常关闭的回归测试。 */
public class UserOnlineStatusTest {
    private final List<Object> previousProperties = new ArrayList<>();

    @BeforeClass
    public void isolateDatabase() throws Exception {
        // 真实关闭路径会释放 JDBC 线程资源；隔离框架配置，禁止测试连接本地数据库。
        for (final String name : List.of("latkeProps", "localProps")) {
            final var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            previousProperties.add(field.get(null));
            final Properties properties = new Properties();
            properties.setProperty("runtimeDatabase", "NONE");
            field.set(null, properties);
        }
    }

    @AfterClass(alwaysRun = true)
    public void restoreProperties() throws Exception {
        int index = 0;
        for (final String name : List.of("latkeProps", "localProps")) {
            final var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, previousProperties.get(index++));
        }
    }

    @Test(timeOut = 60000)
    public void legacyCloseCleansRecordedOwnerWithoutReauthentication() throws Exception {
        final String actor = "legacy-close-regression";
        final var constructor = WebSocketSession.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        final WebSocketSession first = (WebSocketSession) constructor.newInstance(new Object[]{null});
        final WebSocketSession second = (WebSocketSession) constructor.newInstance(new Object[]{null});
        final Object v3 = new Object();
        final int[] updates = {0};
        final var channel = new UserChannel();
        final var service = UserChannel.class.getDeclaredField("userMgmtService");
        service.setAccessible(true);
        service.set(channel, new UserMgmtService() {
            @Override
            public void updateOnlineDetails(final String userId, final String ip) {
                Assert.assertEquals(userId, actor);
                updates[0]++;
            }
        });
        final var ownership = UserChannel.class.getDeclaredField("CONNECTION_OWNERS");
        ownership.setAccessible(true);
        @SuppressWarnings("unchecked")
        final var owners = (java.util.Map<WebSocketSession, String>) ownership.get(null);
        try {
            for (final var socket : java.util.List.of(first, second)) {
                owners.put(socket, actor);
                UserChannel.SESSIONS.computeIfAbsent(actor, ignored -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(socket);
                UserOnlineStatus.connected(socket, actor, Long.MAX_VALUE);
            }
            UserOnlineStatus.connected(v3, actor, Long.MAX_VALUE);
            // 已撤销凭据也必须按登记归属清理；关闭路径不重新读取当前身份。
            channel.onClose(first);
            channel.onClose(first);
            Assert.assertEquals(updates[0], 0);
            Assert.assertTrue(UserOnlineStatus.isOnline(actor));
            channel.onClose(second);
            channel.onClose(second);
            Assert.assertEquals(updates[0], 1);
            Assert.assertFalse(UserChannel.SESSIONS.containsKey(actor));
            Assert.assertFalse(owners.containsKey(first));
            Assert.assertFalse(owners.containsKey(second));
            Assert.assertTrue(UserOnlineStatus.isOnline(actor), "旧标签全关不能清掉V3的在线状态");
            UserOnlineStatus.disconnected(v3);
            Assert.assertFalse(UserOnlineStatus.isOnline(actor));
        } finally {
            for (final var socket : java.util.List.of(first, second)) {
                owners.remove(socket);
                UserOnlineStatus.disconnected(socket);
            }
            UserOnlineStatus.disconnected(v3);
            UserChannel.SESSIONS.remove(actor);
        }
    }

    @Test(timeOut = 60000)
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
            UserOnlineStatus.connected(v3, "other-owner", Long.MAX_VALUE);
            Assert.assertFalse(UserOnlineStatus.isOnline(userId), "同一连接换身份不能遗留旧用户");
            Assert.assertTrue(UserOnlineStatus.isOnline("other-owner"));
        } finally {
            UserOnlineStatus.disconnected(legacy);
            UserOnlineStatus.disconnected(secondLegacyTab);
            UserOnlineStatus.disconnected(v3);
        }
    }

    @Test(timeOut = 60000)
    public void authenticatedRealtimeLifecycle() {
        final long[] clock = {1000};
        final var tracker = new UserOnlineTracker(() -> clock[0],
                () -> java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(clock[0]));
        final boolean[] authorized = {true};
        final String actor = "online-v3-regression";
        final var registry = new ApiV3RealtimeSessions(new ApiV3RealtimeSessions.Authority() {
            public ApiV3SocketTicketService.Grant consume(final String ticket) {
                return new ApiV3SocketTicketService.Grant(actor, "parent", 2000, "chat", "");
            }

            public boolean authorized(final ApiV3SocketTicketService.Grant grant) {
                return authorized[0];
            }
        }, (ip, record) -> true, () -> clock[0], tracker);
        final Object legacy = new Object();
        try {
            registry.open("pending", new ApiV3RealtimeSessionsTest.Transport(), "192.0.2.1");
            Assert.assertFalse(tracker.isOnline(actor), "未认证的连接不能登记在线");
            final String auth = new JSONObject().put("type", "authenticate").put("ticket", "A".repeat(43)).toString();
            registry.receive("pending", auth);
            Assert.assertTrue(tracker.isOnline(actor));
            registry.open("second", new ApiV3RealtimeSessionsTest.Transport(), "192.0.2.2");
            registry.receive("second", auth);
            registry.close("pending");
            Assert.assertTrue(tracker.isOnline(actor), "关闭一个 V3 标签页不能离线");
            tracker.connected(legacy, actor, Long.MAX_VALUE);
            authorized[0] = false;
            registry.receive("second", "{\"type\":\"ping\"}");
            Assert.assertTrue(tracker.isOnline(actor), "V3 授权失效不影响旧版有效连接");
            tracker.disconnected(legacy);
            Assert.assertFalse(tracker.isOnline(actor));
            authorized[0] = true;
            final var failed = new ApiV3RealtimeSessionsTest.Transport();
            registry.open("failed-ready", failed, "192.0.2.3");
            failed.failSend = true;
            registry.receive("failed-ready", auth);
            Assert.assertFalse(tracker.isOnline(actor), "ready 发送失败不得遗留在线");
            registry.open("expires", new ApiV3RealtimeSessionsTest.Transport(), "192.0.2.4");
            registry.receive("expires", auth);
            clock[0] = 2000;
            Assert.assertFalse(tracker.isOnline(actor));
        } finally {
            for (final String key : new String[]{"pending", "second", "failed-ready", "expires"}) registry.close(key);
            tracker.disconnected(legacy);
        }
    }

    public static void main(final String[] args) {
        final var test = new UserOnlineStatusTest();
        test.connectionsAndPrivacy();
        test.authenticatedRealtimeLifecycle();
        test.heartbeatTimeoutDoesNotRenewFromServerTraffic();
        test.authorizationExpiryWinsOverHeartbeat();
        System.out.println("PASS 新旧在线状态、隐私、多标签、失效及失败连接回归");
    }

    @Test(timeOut = 60000)
    public void heartbeatTimeoutDoesNotRenewFromServerTraffic() {
        final long[] clock = {1000};
        final var tracker = new UserOnlineTracker(() -> clock[0],
                () -> java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(clock[0]));
        final var registry = registry(clock, tracker, 1_000_000);
        final var transport = new ApiV3RealtimeSessionsTest.Transport();
        final String auth = new JSONObject().put("type", "authenticate").put("ticket", "A".repeat(43)).toString();
        registry.open("first", transport, "192.0.2.1");
        registry.receive("first", auth);
        clock[0] += 25_000;
        registry.receive("first", "{\"type\":\"ping\"}");
        Assert.assertEquals(transport.sent.getLast().getString("type"), "pong");
        clock[0] += 50_000;
        registry.tick();
        registry.notificationsChanged("actor");
        Assert.assertFalse(transport.closed);
        clock[0] += 10_000;
        registry.sweep();
        Assert.assertTrue(transport.closed, "授权检查和服务端推送均不能延长客户端心跳期限");
        Assert.assertFalse(tracker.isOnline("actor"));
        Assert.assertEquals(tracker.pending("actor").minutes(), 1L);
        registry.receive("first", "{\"type\":\"ping\"}");
        Assert.assertFalse(tracker.isOnline("actor"), "迟到心跳不能复活");

        final Object legacy = new Object();
        tracker.connected(legacy, "actor", Long.MAX_VALUE);
        registry.open("second", new ApiV3RealtimeSessionsTest.Transport(), "192.0.2.2");
        registry.receive("second", auth);
        clock[0] += 60_000;
        registry.notificationsChanged("actor");
        Assert.assertEquals(registry.size(), 0, "发送前也必须清理超时订阅");
        Assert.assertTrue(tracker.isOnline("actor"), "V3超时不能误伤仍连接的旧版");
        tracker.disconnected(legacy);
        Assert.assertFalse(tracker.isOnline("actor"));
    }

    @Test(timeOut = 60000)
    public void authorizationExpiryWinsOverHeartbeat() {
        final long[] clock = {1000};
        final var tracker = new UserOnlineTracker(() -> clock[0],
                () -> java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(clock[0]));
        final var registry = registry(clock, tracker, 31_000);
        final var transport = new ApiV3RealtimeSessionsTest.Transport();
        registry.open("short", transport, "192.0.2.1");
        registry.receive("short", new JSONObject().put("type", "authenticate").put("ticket", "A".repeat(43)).toString());
        clock[0] = 26_000;
        registry.receive("short", "{\"type\":\"ping\"}");
        clock[0] = 31_000;
        Assert.assertFalse(tracker.isOnline("actor"));
        registry.sweep();
        Assert.assertTrue(transport.closed);
        Assert.assertEquals(registry.size(), 0);
    }

    private static ApiV3RealtimeSessions registry(final long[] clock, final UserOnlineTracker tracker, final long expiresAt) {
        return new ApiV3RealtimeSessions(new ApiV3RealtimeSessions.Authority() {
            public ApiV3SocketTicketService.Grant consume(String ticket) {
                return new ApiV3SocketTicketService.Grant("actor", "parent", expiresAt, "notifications", "");
            }

            public boolean authorized(ApiV3SocketTicketService.Grant grant) {
                return true;
            }
        }, (ip, record) -> true, () -> clock[0], tracker);
    }
}
