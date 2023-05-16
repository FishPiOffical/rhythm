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

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.event.Event;
import org.b3log.latke.event.EventManager;
import org.b3log.latke.http.Session;
import org.b3log.latke.http.WebSocketChannel;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.event.EventTypes;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.processor.ChatProcessor;
import org.b3log.symphony.repository.ChatInfoRepository;
import org.b3log.symphony.repository.ChatUnreadRepository;
import org.b3log.symphony.service.OptionQueryService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.ReservedWords;
import org.b3log.symphony.util.Strings;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User channel.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Nov 6, 2019
 * @since 1.4.0
 */
@Singleton
public class ChatChannel implements WebSocketChannel {

    @Inject
    private UserQueryService userQueryService;

    @Inject
    private OptionQueryService optionQueryService;

    @Inject
    private ChatInfoRepository chatInfoRepository;

    @Inject
    private ChatUnreadRepository chatUnreadRepository;

    @Inject
    private EventManager eventManager;

    /**
     * Session set.
     */
    public static final Map<String, Set<WebSocketSession>> SESSIONS = new ConcurrentHashMap();

    public static void sendMsg(String userId, String toUserId, String oId) {
        String chatHex = Strings.uniqueId(new String[]{toUserId, userId});
        JSONObject message = new JSONObject();
        message.put("type", "revoke");
        message.put("data", oId);

        final Set<WebSocketSession> sessions = SESSIONS.get(chatHex);
        for (final WebSocketSession session : sessions) {
            session.sendText(message.toString());
        }
    }

    /**
     * Called when the socket connection with the browser is established.
     *
     * @param session session
     */
    @Override
    public void onConnect(final WebSocketSession session) {
        JSONObject user = null;
        try {
            String apiKey = session.getParameter("apiKey");
            user = ApiProcessor.getUserByKey(apiKey);
        } catch (NullPointerException ignored) {
        }
        if (null == user) {
            session.close();
            return;
        }
        String toUser = session.getParameter("toUser");
        JSONObject toUserJSON = userQueryService.getUserByName(toUser);
        if (toUserJSON == null) {
            if (!toUser.equals("FileTransfer")) {
                session.close();
                return;
            }
            toUserJSON = new JSONObject();
            toUserJSON.put(Keys.OBJECT_ID, "1000000000086");
        }
        String toUserId = toUserJSON.optString(Keys.OBJECT_ID);
        String userId = user.optString(Keys.OBJECT_ID);
        if (userId.equals(toUserId)) {
            session.close();
            return;
        }
        String chatHex = Strings.uniqueId(new String[]{toUserId, userId});

        final Set<WebSocketSession> userSessions = SESSIONS.getOrDefault(chatHex, Collections.newSetFromMap(new ConcurrentHashMap()));

        final Session httpSession = session.getHttpSession();
        httpSession.setAttribute(User.USER, user.toString());
        httpSession.setAttribute("chatHex", chatHex);
        httpSession.setAttribute("fromId", userId);
        httpSession.setAttribute("toId", toUserId);

        userSessions.add(session);
        SESSIONS.put(chatHex, userSessions);
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
        JSONObject result = new JSONObject();
        result.put("code", 0);
        result.put("msg", "");
        result.put("data", "");

        final Session httpSession = message.session.getHttpSession();
        String fromId = httpSession.getAttribute("fromId");
        String toId = httpSession.getAttribute("toId");
        if (fromId == null || toId == null) {
            message.session.close();
            return;
        }
        String chatHex = Strings.uniqueId(new String[]{fromId, toId});
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        message.text = ReservedWords.processReservedWord(message.text);
        String content = message.text;
        content = StringUtils.trim(content);
        if (StringUtils.isBlank(content) || content.length() > 1024) {
            result.put("code", -1);
            result.put("msg", "内容为空或大于1024个字，发送失败");
            message.session.sendText(result.toString());
            return;
        }

        // 存入数据库
        JSONObject chatInfo = new JSONObject();
        chatInfo.put("fromId", fromId);
        chatInfo.put("toId", toId);
        chatInfo.put("user_session", chatHex);
        chatInfo.put("time", time);
        chatInfo.put("content", content);
        String chatInfoOId;
        try {
            Transaction transaction = chatInfoRepository.beginTransaction();
            chatInfoOId = chatInfoRepository.add(chatInfo);
            transaction.commit();
        } catch (RepositoryException e) {
            result.put("code", -1);
            result.put("msg", "发送出错，" + e.getMessage());
            message.session.sendText(result.toString());
            return;
        }
        JSONObject chatUnread = new JSONObject();
        chatUnread.put("fromId", fromId);
        chatUnread.put("toId", toId);
        chatUnread.put("user_session", chatHex);
        try {
            Transaction transaction = chatUnreadRepository.beginTransaction();
            chatUnreadRepository.add(chatUnread);
            transaction.commit();
        } catch (RepositoryException e) {
            result.put("code", -1);
            result.put("msg", "发送出错，" + e.getMessage());
            message.session.sendText(result.toString());
            return;
        }

        // 格式化并发送给WS用户
        try {
            JSONObject info = chatInfoRepository.get(chatInfoOId);
            // 渲染 Markdown
            String markdown = info.optString("content");
            String html = ChatProcessor.processMarkdown(markdown);
            info.put("content", html);
            info.put("markdown", markdown);
            info.put("preview", ChatProcessor.makePreview(markdown));
            // 嵌入用户信息
            JSONObject senderJSON = userQueryService.getUser(fromId);
            info.put("senderUserName", senderJSON.optString(User.USER_NAME));
            info.put("senderAvatar", senderJSON.optString(UserExt.USER_AVATAR_URL));
            JSONObject receiverJSON = userQueryService.getUser(toId);
            if (!toId.equals("1000000000086")) {
                info.put("receiverUserName", receiverJSON.optString(User.USER_NAME));
                info.put("receiverAvatar", receiverJSON.optString(UserExt.USER_AVATAR_URL));
            } else {
                info.put("receiverUserName", "文件传输助手");
                info.put("receiverAvatar", "https://file.fishpi.cn/2022/06/e1541bfe4138c144285f11ea858b6bf6-ba777366.jpeg");
            }
            // 返回给发送者同样的拷贝
            final Set<WebSocketSession> senderSessions = SESSIONS.get(chatHex);
            if (senderSessions != null) {
                for (final WebSocketSession session : senderSessions) {
                    session.sendText(info.toString());
                }
            }
            // 给接收者发送通知
            final JSONObject cmd = new JSONObject();
            cmd.put(UserExt.USER_T_ID, toId);
            cmd.put(Common.COMMAND, "newIdleChatMessage");
            cmd.put("senderUserName", info.optString("senderUserName"));
            cmd.put("senderAvatar", info.optString("senderAvatar"));
            cmd.put("preview", info.optString("preview"));
            UserChannel.sendCmd(cmd);
        } catch (RepositoryException e) {
            result.put("code", -1);
            result.put("msg", "发送出错，" + e.getMessage());
            message.session.sendText(result.toString());
            return;
        }

        eventManager.fireEventAsynchronously(new Event<>(EventTypes.PRIVATE_CHAT, chatInfo));
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
     * Removes the specified session.
     *
     * @param session the specified session
     */
    private void removeSession(final WebSocketSession session) {
        final Session httpSession = session.getHttpSession();
        final String chatHex = httpSession.getAttribute("chatHex");
        if (null == chatHex) {
            return;
        }
        Set<WebSocketSession> userSessions = SESSIONS.get(chatHex);
        userSessions.remove(session);
    }


}
