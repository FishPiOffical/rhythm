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

import org.b3log.latke.http.WebSocketChannel;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.ioc.Singleton;
import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
/**
 * Login channel.
 *
 * @author <a href="https://fishpi.cn/member/Yui">Yui</a>
 * @version 1.0.0.0, Dec 4, 2024
 */
@Singleton
public class LoginChannel implements WebSocketChannel {
    /**
     *  已经连接的WebSocket客户端列表
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void onConnect(WebSocketSession webSocketSession) {
        // 把客户端存入Map,方便发信息
        sessions.put(webSocketSession.getId(), webSocketSession);
    }

    @Override
    public void onMessage(Message message) {
        try {
            JSONObject jsonObject = new JSONObject(message.text);
            String targetId;
            String apiKey;
            WebSocketSession targetSession;
            switch (jsonObject.get("type").toString()) {
                case "1":
                    // type1: 获取web端websocket id 方便 APP 单独向指定的web发送apikey
                    message.session.sendText("targetId:" + message.session.getId());
                    break;
                case "2":
                    // type2: APP 携带apiKey发送登录
                    targetId = jsonObject.get("targetId").toString();
                    apiKey = jsonObject.get("apiKey").toString();
                    if (targetId == null) {
                        message.session.sendText("{\"code\":1,\"msg\":\"Params is not right\"}");
                        break;
                    }
                    targetSession = sessions.get(targetId);
                    if (targetSession != null) {
                        targetSession.sendText("apiKey:" + apiKey);
                        message.session.sendText("{\"code\":0,\"msg\":\"Success\"}");
                    } else {
                        message.session.sendText("{\"code\":1,\"msg\":\"Target is not exist\"}");
                    }
                    break;
                case "3":
                    // type3: APP 已经扫码,但是还没调用登录 (后续APP如果加点击确认按钮了,这个是中间状态)
                    targetId = jsonObject.get("targetId").toString();
                    if (targetId == null) {
                        message.session.sendText("{\"code\":1,\"msg\":\"Params is not right\"}");
                        break;
                    }
                    targetSession = sessions.get(targetId);
                    if (targetSession != null) {
                        targetSession.sendText("scan:ing~");
                        message.session.sendText("{\"code\":0,\"msg\":\"Success\"}");
                    } else {
                        message.session.sendText("{\"code\":1,\"msg\":\"Target is not exist\"}");
                    }
                default:
                    break;
            }
        } catch (Exception e) {
            message.session.sendText("{\"code\":1,\"msg\":\"Message parse error\"}");
        }
    }

    @Override
    public void onClose(WebSocketSession webSocketSession) {
        // 将关闭连接的websocket从Map中移除
        sessions.remove(webSocketSession.getId());
    }

    @Override
    public void onError(Error error) {
    }
}
