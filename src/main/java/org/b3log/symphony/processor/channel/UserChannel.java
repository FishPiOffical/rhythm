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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Session;
import org.b3log.latke.http.WebSocketChannel;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.service.UserMgmtService;
import org.b3log.symphony.service.UserQueryService;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User channel.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Nov 6, 2019
 * @since 1.4.0
 */
@Singleton
public class UserChannel implements WebSocketChannel {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(UserChannel.class);

    /**
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Session set.
     */
    public static final Map<String, Set<WebSocketSession>> SESSIONS = new ConcurrentHashMap();

    /**
     * Online time count.
     */
    private static final Map<String, JSONObject> userOnline = new HashMap<>();

    /**
     * Called when the socket connection with the browser is established.
     *
     * @param session session
     */
    @Override
    public void onConnect(final WebSocketSession session) {
        final Session httpSession = session.getHttpSession();
        JSONObject user = null;
        try {
            user = new JSONObject(httpSession.getAttribute(User.USER));
        } catch (NullPointerException ignored) {
        }
        try {
            user = ApiProcessor.getUserByKey(session.getParameter("apiKey"));
        } catch (NullPointerException ignored) {
        }
        if (null == user) {
            session.close();
            return;
        }

        final String userId = user.optString(Keys.OBJECT_ID);
        // 开始记录在线时间
        if (!userOnline.containsKey(userId)) {
            JSONObject onlineUser = new JSONObject();
            // 当前时间戳
            onlineUser.put("timeStamp", System.currentTimeMillis());
            // 用户之前的在线时间
            onlineUser.put("onlineMinute", userQueryService.getOnlineMinute(userId));
            userOnline.put(userId, onlineUser);
        }

        final Set<WebSocketSession> userSessions = SESSIONS.getOrDefault(userId, Collections.newSetFromMap(new ConcurrentHashMap()));
        userSessions.add(session);

        SESSIONS.put(userId, userSessions);

        final BeanManager beanManager = BeanManager.getInstance();
        final UserMgmtService userMgmtService = beanManager.getReference(UserMgmtService.class);
        final String ip = httpSession.getAttribute(Common.IP);
        userMgmtService.updateOnlineStatus(userId, ip, true, true);
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
        final Session session = message.session.getHttpSession();
        final String userStr = session.getAttribute(User.USER);
        if (null == userStr) {
            return;
        }
        final JSONObject user = new JSONObject(userStr);

        final String userId = user.optString(Keys.OBJECT_ID);
        final BeanManager beanManager = BeanManager.getInstance();
        final UserMgmtService userMgmtService = beanManager.getReference(UserMgmtService.class);
        final String ip = session.getAttribute(Common.IP);
        userMgmtService.updateOnlineStatus(userId, ip, true, true);
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
     * Sends command to browsers.
     *
     * @param message the specified message, for example,
     *                "userId": "",
     *                "cmd": ""
     */
    public static void sendCmd(final JSONObject message) {
        final String recvUserId = message.optString(UserExt.USER_T_ID);
        if (StringUtils.isBlank(recvUserId)) {
            return;
        }

        final String msgStr = message.toString();

        for (final String userId : SESSIONS.keySet()) {
            if (userId.equals(recvUserId)) {
                final Set<WebSocketSession> sessions = SESSIONS.get(userId);
                for (final WebSocketSession session : sessions) {
                    session.sendText(msgStr);
                }
            }
        }
    }

    public static void sendCmdToAll(final JSONObject message) {
        final String msgStr = message.toString();

        for (final String userId : SESSIONS.keySet()) {
            final Set<WebSocketSession> sessions = SESSIONS.get(userId);
            for (final WebSocketSession session : sessions) {
                session.sendText(msgStr);
            }
        }
    }

    /**
     * Removes the specified session.
     *
     * @param session the specified session
     */
    private synchronized void removeSession(final WebSocketSession session) {
        final Session httpSession = session.getHttpSession();
        final String userStr = httpSession.getAttribute(User.USER);
        if (null == userStr) {
            return;
        }
        final JSONObject user = new JSONObject(userStr);
        final String userId = user.optString(Keys.OBJECT_ID);
        final BeanManager beanManager = BeanManager.getInstance();
        final UserMgmtService userMgmtService = beanManager.getReference(UserMgmtService.class);
        final String ip = httpSession.getAttribute(Common.IP);

        Set<WebSocketSession> userSessions = SESSIONS.get(userId);
        if (null == userSessions) {
            userMgmtService.updateOnlineStatus(userId, ip, false, true);
            return;
        }

        userSessions.remove(session);
        if (userSessions.isEmpty()) {
            // 停止记录在线时间
            if (userOnline.containsKey(userId)) {
                JSONObject onlineUser = userOnline.get(userId);
                long timeStamp = onlineUser.optLong("timeStamp");
                int onlineMinute = onlineUser.optInt("onlineMinute");
                long onlineTime = System.currentTimeMillis() - timeStamp;
                int calcOnlineMinutes = (int) onlineTime / 1000 / 60;
                onlineMinute = onlineMinute + calcOnlineMinutes;
                userMgmtService.setOnlineMinute(userId, onlineMinute);
                userOnline.remove(userId);
            }
            userMgmtService.updateOnlineStatus(userId, ip, false, true);
            return;
        }
    }

    /**
     * 在服务器停止前，结算用户的在线时间
     */
    public static void settlement() {
        LOGGER.log(Level.INFO, "Settlement user online time...");
        for (String key : userOnline.keySet()) {
            JSONObject onlineUser = userOnline.get(key);
            long timeStamp = onlineUser.optLong("timeStamp");
            int onlineMinute = onlineUser.optInt("onlineMinute");
            long onlineTime = System.currentTimeMillis() - timeStamp;
            int calcOnlineMinutes = (int) onlineTime / 1000 / 60;
            onlineMinute = onlineMinute + calcOnlineMinutes;
            System.out.println("UserId [" + key + "], Online time [" + onlineMinute + "]");
            final BeanManager beanManager = BeanManager.getInstance();
            final UserMgmtService userMgmtService = beanManager.getReference(UserMgmtService.class);
            userMgmtService.setOnlineMinute(key, onlineMinute);
        }
    }
}
