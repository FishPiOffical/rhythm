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
import org.b3log.latke.http.Session;
import org.b3log.latke.http.WebSocketChannel;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.util.UserOnlineStatus;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.AdminProcessor;
import org.b3log.symphony.service.UserMgmtService;
import org.b3log.symphony.service.UserOnlineService;
import org.json.JSONObject;

import java.util.Collections;
import java.util.WeakHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

    /**
     * Session set.
     */
    public static final Map<String, Set<WebSocketSession>> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<WebSocketSession, String> CONNECTION_OWNERS = new ConcurrentHashMap<>();

    // 框架异步分派回调，关闭可能早于连接回调；弱引用仅防止同一会话被重新登记。
    private final Set<WebSocketSession> closed = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Called when the socket connection with the browser is established.
     *
     * @param session session
     */
    @Override
    public void onConnect(final WebSocketSession session) {
        try {
            final JSONObject user = org.b3log.symphony.util.Sessions.currentSocketUser(session);
            if (user == null) {
                return;
            }
            final String userId = user.getString(Keys.OBJECT_ID);
            synchronized (this) {
                if (closed.contains(session) || CONNECTION_OWNERS.containsKey(session)) {
                    return;
                }
                UserOnlineStatus.connected(session, userId, Long.MAX_VALUE);
                SESSIONS.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
                CONNECTION_OWNERS.put(session, userId);
            }
            updateDetails(session, userId);
        } finally {
            ApiV3Transactions.releaseThreadResources();
        }
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
        AdminProcessor.manager.onMessageSent(8, message.text.length() + 350);
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
                    if(org.b3log.symphony.util.Sessions.currentSocketUser(session)==null){session.close();continue;}
                    session.sendText(msgStr);
                    AdminProcessor.manager.onMessageSent(8, msgStr.length());
                }
            }
        }
    }

    private static final ExecutorService MESSAGE_POOL = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    public static void sendCmdToAll(final JSONObject message) {
        final String msgStr = message.toString();
        MESSAGE_POOL.submit(() -> {
            int i = 0;
            for (final String userId : SESSIONS.keySet()) {
                i++;
                if (i % 1 == 0) {
                    try {
                        Thread.sleep(15);
                    } catch (Exception ignored) {
                    }
                }
                try {
                    final Set<WebSocketSession> sessions = SESSIONS.get(userId);
                    for (final WebSocketSession session : sessions) {
                        if(org.b3log.symphony.util.Sessions.currentSocketUser(session)==null){session.close();continue;}
                        session.sendText(msgStr);
                        AdminProcessor.manager.onMessageSent(8, msgStr.length());
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
    private void removeSession(final WebSocketSession session) {
        final String userId;
        final boolean lastLegacy;
        synchronized (this) {
            closed.add(session);
            UserOnlineStatus.disconnected(session);
            userId = CONNECTION_OWNERS.remove(session);
            if (userId == null) {
                return;
            }
            final Set<WebSocketSession> userSessions = SESSIONS.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    SESSIONS.remove(userId, userSessions);
                }
            }
            lastLegacy = userSessions == null || userSessions.isEmpty();
        }
        if (lastLegacy) {
            try {
                updateDetails(session, userId);
            } finally {
                ApiV3Transactions.releaseThreadResources();
            }
        }
    }

    private void updateDetails(final WebSocketSession session, final String userId) {
        final Session httpSession = session.getHttpSession();
        final String ip = httpSession == null ? null : httpSession.getAttribute(Common.IP);
        userMgmtService.updateOnlineDetails(userId, ip);
    }

    /** 沿用现有停机入口，在数据库关闭前结算新旧连接的共同区间。 */
    public static void settlement() {
        BeanManager.getInstance().getReference(UserOnlineService.class).stop();
    }
}
