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

import org.b3log.latke.Latkes;
import org.b3log.latke.http.WebSocketChannel;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.service.ApiV3RealtimeService;
import org.b3log.symphony.util.ApiV3SocketHandshake;
import org.b3log.symphony.util.ApiV3Transactions;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Singleton
public class ApiV3RealtimeChannel implements WebSocketChannel {
    @Inject
    private ApiV3RealtimeService realtime;
    private final Set<WebSocketSession> closed = Collections.newSetFromMap(new WeakHashMap<>());

    public static String path() {
        return Latkes.getContextPath() + "/api/v3/realtime";
    }

    @Override
    public void onConnect(WebSocketSession session) {
        final var peer = ApiV3SocketHandshake.inspect(session, path());
        if (peer == null || !ApiV3SocketHandshake.prepare(session)) {
            session.close();
            return;
        }
        synchronized (closed) {
            // 关闭回调可能先于异步连接回调到达，不能重新接纳已关闭的会话。
            if (!closed.contains(session)) {
                realtime.open(session, peer.ip());
            }
        }
    }

    @Override
    public void onMessage(Message message) {
        try {
            realtime.receive(message.session, message.text);
        } finally {
            // 每条消息处理后释放线程绑定的事务资源，释放失败时关闭连接。
            try {
                ApiV3Transactions.releaseThreadResources();
            } catch (RuntimeException ignored) {
                realtime.close(message.session);
            }
        }
    }

    @Override
    public void onClose(WebSocketSession session) {
        synchronized (closed) {
            closed.add(session);
        }
        realtime.close(session);
    }

    @Override
    public void onError(Error error) {
        onClose(error.session);
    }
}
