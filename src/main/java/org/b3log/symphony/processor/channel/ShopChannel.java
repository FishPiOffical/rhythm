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
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.AdminProcessor;
import org.b3log.symphony.processor.ApiProcessor;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shop channel.
 *
 * @author <a href="https://github.com/adlered">adlered</a>
 */
@Singleton
public class ShopChannel implements WebSocketChannel {

    /**
     * Session set.
     */
    public static final Map<String, Set<WebSocketSession>> SESSIONS = new ConcurrentHashMap();

    /**
     * Called when the socket connection with the browser is established.
     *
     * @param session session
     */
    @Override
    public void onConnect(final WebSocketSession session) {
        JSONObject user = null;
        try {
            user = new JSONObject(session.getHttpSession().getAttribute(User.USER));
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
        final Set<WebSocketSession> userSessions = SESSIONS.getOrDefault(userId, Collections.newSetFromMap(new ConcurrentHashMap()));
        userSessions.add(session);

        SESSIONS.put(userId, userSessions);

        sendMsg(userId, getMOTD() +
                "欢迎来到系统商店！<span style=\"color: #00bff3\">" + user.optString(User.USER_NAME) + "</span><br>" +
                "<span style=\"color: #EABF04\">输入\"help\"获取帮助信息。</span>"
        );
        sendMsg(userId, "<br><b style=\"color: green\">系统商店测试版 v1.0.0，如遇漏洞请勿继续交易，请反馈至微信 1101635162，必有重谢！<br>故意通过漏洞交易并隐瞒不报，将没收全部所得并处以封禁 :)</b>");
    }

    private String getMOTD() {
        int total = 3;
        Random random = new Random();
        int num = random.nextInt(total);
        switch (num) {
            case 0:
                return "<br><br>" +
                        ".------..------..------..------.<br>" +
                        "|S.--.&nbsp;||H.--.&nbsp;||O.--.&nbsp;||P.--.&nbsp;|<br>" +
                        "|&nbsp;:/\\:&nbsp;||&nbsp;:/\\:&nbsp;||&nbsp;:/\\:&nbsp;||&nbsp;:/\\:&nbsp;|<br>" +
                        "|&nbsp;:\\/:&nbsp;||&nbsp;(__)&nbsp;||&nbsp;:\\/:&nbsp;||&nbsp;(__)&nbsp;|<br>" +
                        "|&nbsp;'--'S||&nbsp;'--'H||&nbsp;'--'O||&nbsp;'--'P|<br>" +
                        "`------'`------'`------'`------'<br><br>";
            case 1:
                return "<br>" +
                        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br>" +
                        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;>X<&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;***&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;xxx&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`&nbsp;&nbsp;___&nbsp;&nbsp;'&nbsp;&nbsp;&nbsp;<br>" +
                        "&nbsp;&nbsp;&nbsp;&nbsp;(o&nbsp;o)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(o&nbsp;o)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(o&nbsp;o)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;(O&nbsp;o)&nbsp;&nbsp;-&nbsp;&nbsp;<br>" +
                        "ooO--(_)--Ooo-ooO--(_)--Ooo-ooO--(_)--Ooo-ooO--(_)--Ooo-<br><br>";
            case 2:
                return "" +
                        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br>" +
                        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;,--,&nbsp;&nbsp;&nbsp;&nbsp;,----..&nbsp;&nbsp;&nbsp;,-.----.&nbsp;&nbsp;&nbsp;&nbsp;<br>" +
                        "&nbsp;&nbsp;.--.--.&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;,--.'|&nbsp;&nbsp;&nbsp;/&nbsp;&nbsp;&nbsp;/&nbsp;&nbsp;&nbsp;\\&nbsp;&nbsp;\\&nbsp;&nbsp;&nbsp;&nbsp;/&nbsp;&nbsp;\\&nbsp;&nbsp;&nbsp;<br>" +
                        "&nbsp;/&nbsp;&nbsp;/&nbsp;&nbsp;&nbsp;&nbsp;'.&nbsp;&nbsp;&nbsp;&nbsp;,--,&nbsp;&nbsp;|&nbsp;:&nbsp;&nbsp;/&nbsp;&nbsp;&nbsp;.&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;|&nbsp;&nbsp;&nbsp;:&nbsp;&nbsp;&nbsp;&nbsp;\\&nbsp;&nbsp;<br>" +
                        "|&nbsp;&nbsp;:&nbsp;&nbsp;/`.&nbsp;/&nbsp;,---.'|&nbsp;&nbsp;:&nbsp;'&nbsp;.&nbsp;&nbsp;&nbsp;/&nbsp;&nbsp;&nbsp;;.&nbsp;&nbsp;\\|&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;.\\&nbsp;:&nbsp;<br>" +
                        ";&nbsp;&nbsp;|&nbsp;&nbsp;|--`&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;|&nbsp;:&nbsp;_'&nbsp;|.&nbsp;&nbsp;&nbsp;;&nbsp;&nbsp;&nbsp;/&nbsp;&nbsp;`&nbsp;;.&nbsp;&nbsp;&nbsp;:&nbsp;&nbsp;|:&nbsp;|&nbsp;<br>" +
                        "|&nbsp;&nbsp;:&nbsp;&nbsp;;_&nbsp;&nbsp;&nbsp;&nbsp;:&nbsp;&nbsp;&nbsp;:&nbsp;|.'&nbsp;&nbsp;|;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;;&nbsp;\\&nbsp;;&nbsp;||&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;\\&nbsp;:&nbsp;<br>" +
                        "&nbsp;\\&nbsp;&nbsp;\\&nbsp;&nbsp;&nbsp;&nbsp;`.&nbsp;|&nbsp;&nbsp;&nbsp;'&nbsp;'&nbsp;&nbsp;;&nbsp;:|&nbsp;&nbsp;&nbsp;:&nbsp;&nbsp;|&nbsp;;&nbsp;|&nbsp;'|&nbsp;&nbsp;&nbsp;:&nbsp;.&nbsp;&nbsp;&nbsp;/&nbsp;<br>" +
                        "&nbsp;&nbsp;`----.&nbsp;&nbsp;&nbsp;\\'&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;.'.&nbsp;|.&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;'&nbsp;'&nbsp;'&nbsp;:;&nbsp;&nbsp;&nbsp;|&nbsp;|`-'&nbsp;&nbsp;<br>" +
                        "&nbsp;&nbsp;__&nbsp;\\&nbsp;&nbsp;\\&nbsp;&nbsp;||&nbsp;&nbsp;&nbsp;|&nbsp;:&nbsp;&nbsp;|&nbsp;''&nbsp;&nbsp;&nbsp;;&nbsp;&nbsp;\\;&nbsp;/&nbsp;&nbsp;||&nbsp;&nbsp;&nbsp;|&nbsp;;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br>" +
                        "&nbsp;/&nbsp;&nbsp;/`--'&nbsp;&nbsp;/'&nbsp;&nbsp;&nbsp;:&nbsp;|&nbsp;&nbsp;:&nbsp;;&nbsp;\\&nbsp;&nbsp;&nbsp;\\&nbsp;&nbsp;',&nbsp;&nbsp;/&nbsp;:&nbsp;&nbsp;&nbsp;'&nbsp;|&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br>" +
                        "'--'.&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;/&nbsp;|&nbsp;&nbsp;&nbsp;|&nbsp;'&nbsp;&nbsp;,/&nbsp;&nbsp;&nbsp;;&nbsp;&nbsp;&nbsp;:&nbsp;&nbsp;&nbsp;&nbsp;/&nbsp;&nbsp;:&nbsp;&nbsp;&nbsp;:&nbsp;:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br>" +
                        "&nbsp;&nbsp;`--'---'&nbsp;&nbsp;;&nbsp;&nbsp;&nbsp;:&nbsp;;--'&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\\&nbsp;&nbsp;&nbsp;\\&nbsp;.'&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;|&nbsp;:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br>" +
                        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;,/&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`---`&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`---'.|&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br>" +
                        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;'---'&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`---`&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br>" +
                        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br><br>";
            default:
                return "";
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
     * @param msg the specified message, for example,
     *                "userId": "",
     *                "cmd": ""
     */
    public static void sendMsg(String uid, String msg) {
        JSONObject message = new JSONObject();
        message.put(UserExt.USER_T_ID, uid);
        message.put("message", msg);
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
                    AdminProcessor.manager.onMessageSent(7, msgStr.length());
                }
            }
        }
    }

    /**
     * Removes the specified session.
     *
     * @param session the specified session
     */
    private void removeSession(final WebSocketSession session) {
        final Session httpSession = session.getHttpSession();
        final String userStr = httpSession.getAttribute(User.USER);
        if (null == userStr) {
            return;
        }
        final JSONObject user = new JSONObject(userStr);
        final String userId = user.optString(Keys.OBJECT_ID);
        Set<WebSocketSession> userSessions = SESSIONS.get(userId);
        userSessions.remove(session);
    }
}
