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
package org.b3log.symphony.processor.channel;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.WebSocketChannel;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.AdminProcessor;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.service.AvatarQueryService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.NodeUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Chatroom channel.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Nov 6, 2019
 * @since 1.4.0
 */
@Singleton
public class ChatroomChannel implements WebSocketChannel {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ChatroomChannel.class);

    /**
     * Session set.
     */
    public static final Set<WebSocketSession> SESSIONS = Collections.newSetFromMap(new ConcurrentHashMap());

    /**
     * Online user information storage.
     */
    public static final ConcurrentHashMap<WebSocketSession, JSONObject> onlineUsers = new ConcurrentHashMap<>();
    /**
     * 当前讨论话题
     */
    public static String discussing = "暂无";

    public static final Map<String, Long> userActive = Collections.synchronizedMap(new HashMap<>());

    /**
     * Called when the socket connection with the browser is established.
     *
     * @param session session
     */
    @Override
    public void onConnect(final WebSocketSession session) {
        if (NodeUtil.wsOnline == null || NodeUtil.wsOnline.isEmpty()) {
            String text = "{\"userOId\":1630399192600,\"userAvatarURL\":\"https://file.fishpi.cn/2023/12/blob-1d3b18ec.png\",\"userNickname\":\"阿达\",\"oId\":\"" + System.currentTimeMillis() + "\",\"userName\":\"adlered\",\"type\":\"msg\",\"content\":\"<p><\\/p><h3>您正在使用故障转移聊天室频道<\\/h3>\\n<p>目前聊天室节点全部故障，您已被转移至故障转移专用节点。<\\/p>\\n<p>出现此消息通知，不影响正常聊天。<\\/p>\\n<p><\\/p>\",\"md\":\"### 您正在使用故障转移聊天室频道\\n\\n目前聊天室节点全部故障，您已被转移至故障转移专用节点。\\n\\n出现此消息通知，不影响正常聊天。\",\"userAvatarURL20\":\"https://file.fishpi.cn/2023/12/blob-1d3b18ec.png\",\"sysMetal\":\"{\\\"list\\\":[{\\\"data\\\":\\\"\\\",\\\"name\\\":\\\"摸鱼派铁粉\\\",\\\"description\\\":\\\"捐助摸鱼派达1024RMB; 编号No.9\\\",\\\"attr\\\":\\\"url=https://file.fishpi.cn/2021/12/ht3-b97ea102.jpg&backcolor=ee3a8c&fontcolor=ffffff\\\",\\\"enabled\\\":true},{\\\"data\\\":\\\"\\\",\\\"name\\\":\\\"摸鱼派忠粉\\\",\\\"description\\\":\\\"捐助摸鱼派达256RMB; 编号No.20\\\",\\\"attr\\\":\\\"url=https://file.fishpi.cn/2021/12/ht2-bea67b29.jpg&backcolor=87cefa&fontcolor=efffff\\\",\\\"enabled\\\":true},{\\\"data\\\":\\\"\\\",\\\"name\\\":\\\"摸鱼派粉丝\\\",\\\"description\\\":\\\"捐助摸鱼派达16RMB; 编号No.38\\\",\\\"attr\\\":\\\"url=https://file.fishpi.cn/2021/12/ht1-d8149de4.jpg&backcolor=ffffff&fontcolor=ff3030\\\",\\\"enabled\\\":true}]}\",\"client\":\"Web/PC网页端\",\"time\":\"" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(System.currentTimeMillis()) + "\",\"userAvatarURL210\":\"https://file.fishpi.cn/2023/12/blob-1d3b18ec.png\",\"userAvatarURL48\":\"https://file.fishpi.cn/2023/12/blob-1d3b18ec.png\"}";
            sendText(session, text);
        } else {
            // 发送过期客户端消息
            String text = "{\"userOId\":1630399192600,\"userAvatarURL\":\"https://file.fishpi.cn/2023/12/blob-1d3b18ec.png\",\"userNickname\":\"阿达\",\"oId\":\"" + System.currentTimeMillis() + "\",\"userName\":\"adlered\",\"type\":\"msg\",\"content\":\"<p><\\/p><h3>您正在使用过期的聊天室频道<\\/h3>\\n<p>您的客户端正在使用过期的协议连接到摸鱼派聊天室，该协议已被停用，无法接收消息。<\\/p>\\n<p>请将客户端更新到最新版本以享受更快的聊天室服务，如已是最新版本，请联系客户端开发者更新客户端。<\\/p>\\n<p>新版摸鱼派聊天室协议开发指南：<a href=\\\"https://fishpi.cn/article/1733591297543\\\" target=\\\"_blank\\\" rel=\\\"nofollow\\\">https://fishpi.cn/article/1733591297543<\\/a><\\/p>\\n<p><\\/p>\",\"md\":\"### 您正在使用过期的客户端\\n\\n您的客户端正在使用过期的协议连接到摸鱼派聊天室，该协议已被停用，无法接收消息。\\n\\n请将客户端更新到最新版本以享受更快的聊天室服务，如已是最新版本，请联系客户端开发者更新客户端。\\n\\n新版摸鱼派聊天室协议开发指南：https://fishpi.cn/article/1733591297543\",\"userAvatarURL20\":\"https://file.fishpi.cn/2023/12/blob-1d3b18ec.png\",\"sysMetal\":\"{\\\"list\\\":[{\\\"data\\\":\\\"\\\",\\\"name\\\":\\\"摸鱼派铁粉\\\",\\\"description\\\":\\\"捐助摸鱼派达1024RMB; 编号No.9\\\",\\\"attr\\\":\\\"url=https://file.fishpi.cn/2021/12/ht3-b97ea102.jpg&backcolor=ee3a8c&fontcolor=ffffff\\\",\\\"enabled\\\":true},{\\\"data\\\":\\\"\\\",\\\"name\\\":\\\"摸鱼派忠粉\\\",\\\"description\\\":\\\"捐助摸鱼派达256RMB; 编号No.20\\\",\\\"attr\\\":\\\"url=https://file.fishpi.cn/2021/12/ht2-bea67b29.jpg&backcolor=87cefa&fontcolor=efffff\\\",\\\"enabled\\\":true},{\\\"data\\\":\\\"\\\",\\\"name\\\":\\\"摸鱼派粉丝\\\",\\\"description\\\":\\\"捐助摸鱼派达16RMB; 编号No.38\\\",\\\"attr\\\":\\\"url=https://file.fishpi.cn/2021/12/ht1-d8149de4.jpg&backcolor=ffffff&fontcolor=ff3030\\\",\\\"enabled\\\":true}]}\",\"client\":\"Web/PC网页端\",\"time\":\"" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(System.currentTimeMillis()) + "\",\"userAvatarURL210\":\"https://file.fishpi.cn/2023/12/blob-1d3b18ec.png\",\"userAvatarURL48\":\"https://file.fishpi.cn/2023/12/blob-1d3b18ec.png\"}";
            sendText(session, text);
        }
        String userStr = session.getHttpSession().getAttribute(User.USER);
        try {
            userStr = ApiProcessor.getUserByKey(session.getParameter("apiKey")).toString();
        } catch (NullPointerException ignored) {
        }
        if (null != userStr) {
            final JSONObject user = new JSONObject(userStr);
            String userName = user.optString(User.USER_NAME);
            boolean joined = true;
            for (Map.Entry<WebSocketSession, JSONObject> entry : onlineUsers.entrySet()) {
                String name = entry.getValue().optString(User.USER_NAME);
                if (userName.equals(name)) {
                    joined = false;
                }
            }
            if (joined) {
                String msg = getCustomMessage(1, userName);
                if (!msg.isEmpty()) {
                    sendCustomMessage(msg);
                }
            }
            onlineUsers.put(session, user);
            SESSIONS.add(session);
            // 单独发送在线信息
            final String msgStr = getOnline().toString();
            sendText(session, msgStr);
            NodeUtil.sendTell(userName, msgStr);
            AdminProcessor.manager.onMessageSent(4, msgStr.length());
            // 保存 Active 信息
            userActive.put(user.optString("userName"), System.currentTimeMillis());
        } else {
            session.close();
        }
    }

    private static SimpleCurrentLimiter customMessageCurrentLimit = new SimpleCurrentLimiter(60, 6);
    public static String getCustomMessage(int type, String userName) {
        if (customMessageCurrentLimit.access(userName)) {
            final BeanManager beanManager = BeanManager.getInstance();
            UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
            CloudRepository cloudRepository = beanManager.getReference(CloudRepository.class);
            JSONObject user = userQueryService.getUserByName(userName);
            String userId = user.optString(Keys.OBJECT_ID);
            String msg = "";

            try {
                Query cloudQuery = new Query()
                        .setFilter(CompositeFilterOperator.and(
                                new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                                new PropertyFilter("gameId", FilterOperator.EQUAL, "sys-custom-message")
                        ));
                JSONObject result = cloudRepository.getFirst(cloudQuery);
                String data = result.optString("data");
                if (data.contains("&&&")) {
                    if (type == 1) {
                        msg = data.split("&&&")[0];
                    } else if (type == 0) {
                        msg = data.split("&&&")[1];
                    }
                } else {
                    msg = "";
                }
            } catch (Exception e) {
                msg = "";
            }

            if (!msg.isEmpty()) {
                msg = msg.replaceAll("\\{userName}", user.optString(User.USER_NAME));
                msg = msg.replaceAll("\\{userNickName}", user.optString(UserExt.USER_NICKNAME));
                msg = msg.replaceAll("\\{userPoint}", user.optString(UserExt.USER_POINT));
            }
            return msg;
        } else {
            return "";
        }
    }

    public static boolean removeCustomMessage(String userName) {
        final BeanManager beanManager = BeanManager.getInstance();
        UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
        CloudRepository cloudRepository = beanManager.getReference(CloudRepository.class);
        String userId = userQueryService.getUserByName(userName).optString(Keys.OBJECT_ID);

        try {
            final Transaction transaction = cloudRepository.beginTransaction();
            Query cloudDeleteQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, "sys-custom-message")
                    ));
            cloudRepository.remove(cloudDeleteQuery);
            transaction.commit();

            return true;
        } catch (RepositoryException e) {
            return false;
        }
    }

    public static boolean addCustomMessage(String userName, String message) {
        final BeanManager beanManager = BeanManager.getInstance();
        UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
        CloudRepository cloudRepository = beanManager.getReference(CloudRepository.class);
        String userId = userQueryService.getUserByName(userName).optString(Keys.OBJECT_ID);

        try {
            final Transaction transaction = cloudRepository.beginTransaction();
            Query cloudDeleteQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, "sys-custom-message")
                    ));
            cloudRepository.remove(cloudDeleteQuery);
            JSONObject cloudJSON = new JSONObject();
            cloudJSON.put("userId", userId)
                    .put("gameId", "sys-custom-message")
                    .put("data", message);
            cloudRepository.add(cloudJSON);
            transaction.commit();

            return true;
        } catch (RepositoryException e) {
            return false;
        }
    }

    public static void sendCustomMessage(String msg) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Common.TYPE, "customMessage");
        jsonObject.put("message", msg);
        String message = jsonObject.toString();
        NodeUtil.sendAll(message);
        new Thread(() -> {
            int i = 0;
            for (WebSocketSession s : ChatroomChannel.SESSIONS) {
                i++;
                if (i % 50 == 0) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception ignored) {
                    }
                }
                sendText(s, message);
                AdminProcessor.manager.onMessageSent(4, message.length());
            }
        }).start();
    }

    /**
     * Called when the connection closed.
     *
     * @param session session
     */
    @Override
    public void onClose(final WebSocketSession session) {
        removeSession(session);
    }

    /**
     * Called when a message received from the browser.
     *
     * @param message message
     */
    @Override
    public void onMessage(final Message message) {
    }

    /**
     * Called when a error received.
     *
     * @param error error
     */
    @Override
    public void onError(final Error error) {
        removeSession(error.session);
    }

    /**
     * Notifies the specified chat message to browsers.
     *
     * @param message the specified message, for example,
     *                {
     *                "userName": "",
     *                "content": ""
     *                }
     */
    public static int notQuickCheck = 50;
    public static int notQuickSleep = 50;
    public static int quickCheck = 100;
    public static int quickSleep = 50;

    //用于消息发送的线程池
    private static final ThreadPoolExecutor MESSAGE_POOL = new ThreadPoolExecutor(
            1,
            1,
            120L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>()
            );

    public static void notifyChat(final JSONObject message) {
        final BeanManager beanManager = BeanManager.getInstance();
        final AvatarQueryService avatarQueryService = beanManager.getReference(AvatarQueryService.class);
        if (!message.has(Common.TYPE)) {
            message.put(Common.TYPE, "msg");
        }
        String type = message.optString(Common.TYPE);
        String sender = message.optString(User.USER_NAME);
        boolean isRedPacket = false;
        try {
            JSONObject content = new JSONObject(message.optString("content"));
            String msgType = content.optString("msgType");
            if (msgType.equals("redPacket")) {
                isRedPacket = true;
            }
        } catch (Exception ignored) {
        }
        boolean quick = sender.isEmpty() || isRedPacket || type.equals("redPacketStatus") || type.equals("revoke") || type.equals("discussChanged");
        avatarQueryService.fillUserAvatarURL(message);
        final String msgStr = message.toString();
        if (quick) {
            NodeUtil.sendAll(msgStr);
        } else {
            NodeUtil.sendMsg(sender, msgStr);
        }
        // 先给发送人反馈
        if (!quick) {
            List<WebSocketSession> senderSessions = new ArrayList<>();
            for (Map.Entry<WebSocketSession, JSONObject> entry : onlineUsers.entrySet()) {
                String userName = entry.getValue().optString(User.USER_NAME);
                if (userName.equals(sender)) {
                    senderSessions.add(entry.getKey());
                }
            }
            for (WebSocketSession session : senderSessions) {
                sendText(session, msgStr);
                AdminProcessor.manager.onMessageSent(4, msgStr.length());
            }
        }
        MESSAGE_POOL.submit(() -> {
            int i = 0;
            for (WebSocketSession session : SESSIONS) {
                try {
                    i++;
                    if (!quick && i % notQuickCheck == 0) {
                        try {
                            Thread.sleep(notQuickSleep);
                        } catch (Exception ignored) {
                        }
                    } else if (quick && i % quickCheck == 0) {
                        try {
                            Thread.sleep(quickSleep);
                        } catch (Exception ignored) {
                        }
                    }
                    if (!quick) {
                        String toUser = onlineUsers.get(session).optString(User.USER_NAME);
                        if (!sender.equals(toUser)) {
                            sendText(session, msgStr);
                            AdminProcessor.manager.onMessageSent(4, msgStr.length());
                        }
                    } else {
                        sendText(session, msgStr);
                        AdminProcessor.manager.onMessageSent(4, msgStr.length());
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * Removes the specified session.
     *
     * @param session the specified session
     */
    public static synchronized void removeSession(final WebSocketSession session) {
        String userName = "";
        try {
            JSONObject user = onlineUsers.get(session);
            userName = user.optString(User.USER_NAME);
            onlineUsers.remove(session);
            boolean left = true;
            for (Map.Entry<WebSocketSession, JSONObject> entry : onlineUsers.entrySet()) {
                String name = entry.getValue().optString(User.USER_NAME);
                if (userName.equals(name)) {
                    left = false;
                }
            }
            if (left) {
                String msg = getCustomMessage(0, userName);
                if (!msg.isEmpty()) {
                    sendCustomMessage(msg);
                }
            }
        } catch (NullPointerException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }

        SESSIONS.remove(session);
    }

    public static Map<String, Long> check() {
        Map<String, JSONObject> filteredOnlineUsers = new HashMap<>();
        for (JSONObject object : onlineUsers.values()) {
            String name = object.optString(User.USER_NAME);
            filteredOnlineUsers.put(name, object);
        }
        Long currentTime = System.currentTimeMillis();
        int sixHours = 1000 * 60 * 60 * 6;
        Map<String, Long> needKickUsers = new HashMap<>();
        List<String> users = new ArrayList<>();
        for (String user : filteredOnlineUsers.keySet()) {
            try {
                Long activeTime = userActive.get(user);
                Long spareTime = currentTime - activeTime;
                if (spareTime >= sixHours) {
                    needKickUsers.put(user, (spareTime / (1000 * 60 * 60)));
                    users.add(user);
                }
            } catch (Exception ignored) {
            }
        }

        new Thread(() -> {
            List<WebSocketSession> senderSessions = new ArrayList<>();
            for (Map.Entry<WebSocketSession, JSONObject> entry : onlineUsers.entrySet()) {
                try {
                    String tempUserName = entry.getValue().optString(User.USER_NAME);
                    if (users.contains(tempUserName)) {
                        senderSessions.add(entry.getKey());
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.ERROR, "Failed to log user entry", e);
                }
            }
            for (WebSocketSession session : senderSessions) {
                removeSession(session);
            }
            JdbcRepository.dispose();
        }).start();

        return needKickUsers;
    }

    /**
     * 获得聊天室在线人数和在线成员信息
     * @return
     */
    public static JSONObject getOnline() {
        final BeanManager beanManager = BeanManager.getInstance();
        final AvatarQueryService avatarQueryService = beanManager.getReference(AvatarQueryService.class);
        try {
            // 使用 HashMap 去重
            Map<String, JSONObject> filteredOnlineUsers = new HashMap<>();
            for (JSONObject object : onlineUsers.values()) {
                String name = object.optString(User.USER_NAME);
                filteredOnlineUsers.put(name, object);
            }

            for (int i = 0; i < NodeUtil.remoteUsers.length(); i++) {
                try {
                    JSONObject temp = NodeUtil.remoteUsers.getJSONObject(i);
                    filteredOnlineUsers.put(temp.optString(User.USER_NAME), temp);
                } catch (Exception ignored) {
                }
            }

            JSONArray onlineArray = new JSONArray();
            for (String user : filteredOnlineUsers.keySet()) {
                JSONObject object = filteredOnlineUsers.get(user);

                String avatar = object.optString(UserExt.USER_AVATAR_URL);
                String homePage = Latkes.getStaticServePath() + "/member/" + user;

                JSONObject generated = new JSONObject();
                generated.put(User.USER_NAME, user);
                generated.put(UserExt.USER_AVATAR_URL, avatar);
                avatarQueryService.fillUserAvatarURL(generated);
                generated.put("homePage", homePage);
                onlineArray.put(generated);
            }

            JSONObject result = new JSONObject();
            result.put(Common.ONLINE_CHAT_CNT, filteredOnlineUsers.size());
            result.put(Common.TYPE, "online");
            result.put("users", onlineArray);
            result.put("discussing", discussing);

            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new JSONObject().put(Common.ONLINE_CHAT_CNT, 99999).put(Common.TYPE, "online").put("users", new JSONArray());
    }

    // 发送在线信息
    private static boolean onlineMsgLock = false;
    public static void sendOnlineMsg() {
        if (!onlineMsgLock) {
            onlineMsgLock = true;
            final String msgStr = getOnline().toString();
            NodeUtil.sendSlow(msgStr);
            new Thread(() -> {
                int i = 0;
                for (WebSocketSession s : SESSIONS) {
                    i++;
                    if (i % 1 == 0) {
                        try {
                            Thread.sleep(500);
                        } catch (Exception ignored) {
                        }
                    }
                    sendText(s, msgStr);
                    AdminProcessor.manager.onMessageSent(4, msgStr.length());
                }
                onlineMsgLock = false;
            }).start();
        }
    }

    public static void sendText(WebSocketSession wss, String text) {
        wss.sendText(text);
    }
}
