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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import org.b3log.latke.http.WebSocketSession;
import org.testng.Assert;
import java.util.Map;

/** Uses the actual bundled Latke session/handler fields and Netty pipeline, not replicas. */
public class ApiV3SocketHandshakeTest {
    static final String PATH="/api/v3/socket";
    static class Fixture implements AutoCloseable {
        final EmbeddedChannel channel=new EmbeddedChannel();
        final Object handler;
        final WebSocketSession session;
        Fixture(String target,String ip)throws Exception {
            final var type=Class.forName("org.b3log.latke.http.WebSocketHandler");
            final var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);handler=ctor.newInstance();
            channel.pipeline().addLast("latke-websocket",(ChannelHandler)handler);
            final var handshaker=type.getDeclaredField("handshaker");handshaker.setAccessible(true);handshaker.set(handler,new WebSocketServerHandshaker13(target,null,true,1024));
            final var sessionCtor=WebSocketSession.class.getDeclaredConstructor(ChannelHandlerContext.class);sessionCtor.setAccessible(true);session=sessionCtor.newInstance(channel.pipeline().context((ChannelHandler)handler));
            final var parameters=WebSocketSession.class.getDeclaredField("params");parameters.setAccessible(true);
            @SuppressWarnings("unchecked") final Map<String,String> params=(Map<String,String>)parameters.get(session);
            if(null!=ip)params.put("ip",ip);
        }
        @Override public void close(){channel.finishAndReleaseAll();}
    }
    public static void main(String[] args)throws Exception {
        for(String ip:new String[]{"127.0.0.1","192.0.2.1","2001:db8::1234","::1"})try(var f=new Fixture(PATH,ip)){Assert.assertEquals(ApiV3SocketHandshake.inspect(f.session,PATH).ip(),ip);}
        try(var f=new Fixture("/rhythm/api/v3/socket","192.0.2.1")){Assert.assertEquals(ApiV3SocketHandshake.inspect(f.session,"/rhythm/api/v3/socket").ip(),"192.0.2.1");Assert.assertNull(ApiV3SocketHandshake.inspect(f.session,PATH));}
        System.out.println("PASS actual Latke handler/session metadata exposes exact query-free target and IPv4/IPv6, including deployment context paths");
        for(String target:new String[]{PATH+"?ip=192.0.2.1",PATH+"?apiKey=PRIVATE",PATH+"?ticket=PRIVATE",PATH+"?",PATH+"#fragment","/api/v3/%73ocket","/api/v3/socket/","/other","http://localhost"+PATH})try(var f=new Fixture(target,"192.0.2.1")){Assert.assertNull(ApiV3SocketHandshake.inspect(f.session,PATH),target);}
        System.out.println("PASS IP-overwriting queries, credentials in URL, empty queries and mismatched/encoded targets cannot become trusted metadata");
        for(String ip:new String[]{null,"","not-an-ip","192.0.2.1, 127.0.0.1","192.0.2.1\r\n","999.1.1.1","x".repeat(129)})try(var f=new Fixture(PATH,ip)){Assert.assertNull(ApiV3SocketHandshake.inspect(f.session,PATH));}
        System.out.println("PASS missing/malformed/oversized forwarded IP metadata fails closed without DNS lookup");
        try(var f=new Fixture(PATH,"192.0.2.1")){
            for(String path:new String[]{null,"socket","//socket",PATH+"?x",PATH+"#x"})Assert.assertNull(ApiV3SocketHandshake.inspect(f.session,path));
            final var handshaker=f.handler.getClass().getDeclaredField("handshaker");handshaker.setAccessible(true);handshaker.set(f.handler,null);Assert.assertNull(ApiV3SocketHandshake.inspect(f.session,PATH));
        }
        try(var f=new Fixture(PATH,"192.0.2.1")){f.channel.pipeline().remove((ChannelHandler)f.handler);Assert.assertNull(ApiV3SocketHandshake.inspect(f.session,PATH));}
        Assert.assertNull(ApiV3SocketHandshake.inspect(null,PATH));
        System.out.println("PASS missing handshaker/pipeline/session and invalid expected paths fail closed; no Cookie or session identity is used");
        try(var f=new Fixture(PATH,"192.0.2.1")){
            Assert.assertFalse(ApiV3SocketHandshake.prepare(f.session));
            f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10*1024*1024));
            f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10*1024*1024));
            Assert.assertTrue(ApiV3SocketHandshake.prepare(f.session));Assert.assertTrue(ApiV3SocketHandshake.writable(f.session));
        }
        Assert.assertFalse(ApiV3SocketHandshake.writable(null));
        System.out.println("PASS actual Netty pipeline supports channel-local frame/fragment limits and writable checks; missing handlers fail closed");
    }
}
