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
package org.b3log.symphony.util;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.NetUtil;
import org.b3log.latke.http.WebSocketSession;

import java.lang.reflect.Field;

/**
 * v3 WebSocket 接收凭据前，从内置 Latke 的握手中读取传输元数据。
 * 查询参数可以覆盖框架写入的 ip 参数，因此只接受与预期路径完全一致、没有查询串的请求。
 */
public final class ApiV3SocketHandshake {
    private ApiV3SocketHandshake() {
    }

    public record Peer(String ip) {
    }

    public static boolean writable(final WebSocketSession session) {
        try {
            final var c = (ChannelHandlerContext) Metadata.CONTEXT.get(session);
            return c != null && c.channel().isActive() && c.channel().isWritable();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * 请求认证帧之前，为当前连接设置单帧和分片聚合后的大小上限。
     */
    public static boolean prepare(final WebSocketSession session) {
        try {
            final var context = (ChannelHandlerContext) Metadata.CONTEXT.get(session);
            if (context == null)
                return false;
            final java.util.concurrent.Callable<Boolean> configure = () -> {
                final var handler = context.pipeline().get(Metadata.HANDLER);
                final var handshaker = handler == null ? null : (WebSocketServerHandshaker) Metadata.HANDSHAKER.get(handler);
                if (handshaker == null || handshaker.version() != WebSocketVersion.V13)
                    return false;
                final var decoder = context.pipeline().context(WebSocketFrameDecoder.class);
                final var aggregator = context.pipeline().context(WebSocketFrameAggregator.class);
                if (decoder == null || aggregator == null)
                    return false;
                context.pipeline().replace(decoder.name(), decoder.name(),
                        new WebSocket13FrameDecoder(handshaker.decoderConfig().toBuilder().maxFramePayloadLength(1024).build()));
                context.pipeline().replace(aggregator.name(), aggregator.name(), new WebSocketFrameAggregator(1024));
                return true;
            };
            // 解码器和聚合器在连接所属的事件循环内替换。
            if (context.executor().inEventLoop())
                return configure.call();
            final var task = context.executor().submit(configure);
            try {
                return task.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                task.cancel(false);
                return false;
            }
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    /**
     * 返回 null 表示框架元数据不受支持或请求已被拒绝，调用方须关闭连接。
     */
    public static Peer inspect(final WebSocketSession session, final String expectedPath) {
        if (null == session || null == expectedPath || !expectedPath.startsWith("/")
                || expectedPath.startsWith("//") || expectedPath.contains("?") || expectedPath.contains("#"))
            return null;
        try {
            final var context = (ChannelHandlerContext) Metadata.CONTEXT.get(session);
            if (null == context) return null;
            final var handler = context.pipeline().get(Metadata.HANDLER);
            if (null == handler) return null;
            final var handshaker = (WebSocketServerHandshaker) Metadata.HANDSHAKER.get(handler);
            if (null == handshaker || !expectedPath.equals(handshaker.uri())) return null;
            // 确认握手路径没有查询串后，才读取框架写入的 IP。
            final String ip = session.getParameter("ip");
            if (null == ip || ip.length() > 128 || (!NetUtil.isValidIpV4Address(ip) && !NetUtil.isValidIpV6Address(ip)))
                return null;
            return new Peer(ip);
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            // 框架内部结构不兼容时直接失败，避免回退到会话、Cookie 或请求参数。
            return null;
        }
    }

    private static final class Metadata {
        static final Field CONTEXT = field(WebSocketSession.class, "ctx");
        static final Class<? extends ChannelHandler> HANDLER = handlerType();
        static final Field HANDSHAKER = field(HANDLER, "handshaker");

        static Field field(final Class<?> type, final String name) {
            try {
                final Field value = type.getDeclaredField(name);
                value.setAccessible(true);
                return value;
            } catch (final ReflectiveOperationException e) {
                throw new IllegalStateException("unsupported Latke socket metadata", e);
            }
        }

        static Class<? extends ChannelHandler> handlerType() {
            try {
                return Class.forName("org.b3log.latke.http.WebSocketHandler").asSubclass(ChannelHandler.class);
            } catch (final ReflectiveOperationException e) {
                throw new IllegalStateException("unsupported Latke socket metadata", e);
            }
        }
    }
}
