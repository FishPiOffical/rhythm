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

import org.b3log.latke.Latkes;
import org.b3log.latke.http.WebSocketChannel;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.processor.channel.ApiV3RealtimeChannel;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.service.ApiV3RealtimeService;
import org.b3log.symphony.service.ApiV3SocketTicketService;
import org.b3log.symphony.repository.ApiV3NotificationRevisionRepository;
import org.b3log.symphony.repository.ApiV3ArticleRevisionRepository;
import org.b3log.symphony.repository.ApiV3ChatEventRepository;
import io.netty.handler.codec.http.websocketx.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;

/** Real facade/channel/session and Netty writes; grant authority and global firewall are explicit adapters. */
public class ApiV3RealtimeChannelTest {
    static class Revisions extends ApiV3NotificationRevisionRepository {
        final Map<String,String> values=new java.util.concurrent.ConcurrentHashMap<>();
        boolean unavailable;
        @Override public Map<String,String> snapshot(Collection<String> owners)throws RepositoryException {
            if(unavailable)throw new RepositoryException("injected marker failure");
            final var result=new HashMap<String,String>();for(String owner:owners)if(values.containsKey(owner))result.put(owner,values.get(owner));return result;
        }
    }
    static class Authority extends ApiV3SocketTicketService {
        final String ticket=ApiV3TokenUtil.generate();boolean consumed,allowed=true,transaction;
        String channel="notifications",target="";
        @Override public Grant consume(String value){if(consumed||!ticket.equals(value))throw new IllegalArgumentException();consumed=true;return new Grant("123","456",System.currentTimeMillis()+60000,channel,target);}
        @Override public boolean authorized(Grant grant){return allowed;}
        @Override public void requireNoTransaction(){if(transaction)throw new IllegalStateException("active transaction");}
        @Override public int removeExpired(){return 0;}
    }
    static class PrivateRevisions extends org.b3log.symphony.repository.ApiV3PrivateRevisionRepository {
        final Map<String,String> values=new java.util.concurrent.ConcurrentHashMap<>();boolean unavailable;
        @Override public Map<String,String> snapshot(Collection<String> owners)throws RepositoryException {
            if(unavailable)throw new RepositoryException("private marker unavailable");
            final var result=new HashMap<String,String>();for(String owner:owners)if(values.containsKey(owner))result.put(owner,values.get(owner));return result;
        }
    }
    static class ArticleRevisions extends ApiV3ArticleRevisionRepository {
        final Map<String,String> values=new java.util.concurrent.ConcurrentHashMap<>();boolean unavailable;
        @Override public Map<String,String> snapshot(Collection<String> ids)throws RepositoryException {
            if(unavailable)throw new RepositoryException("article marker source unavailable");
            final var result=new HashMap<String,String>();for(String id:ids)if(values.containsKey(id))result.put(id,values.get(id));return result;
        }
    }
    static class ChatEvents extends ApiV3ChatEventRepository {
        long head;boolean unavailable,missingEvents;int removedCalls;
        final List<JSONObject> events=new java.util.concurrent.CopyOnWriteArrayList<>();
        void add(String kind,String id){head++;events.add(new JSONObject().put("revision",head).put("messageId",id).put("kind",kind));}
        @Override public long head()throws RepositoryException{if(unavailable)throw new RepositoryException("chat source unavailable");return head;}
        @Override public List<JSONObject> after(long revision,long through,int limit)throws RepositoryException{
            if(unavailable||missingEvents)throw new RepositoryException("chat events unavailable");
            return events.stream().filter(r->r.getLong("revision")>revision&&r.getLong("revision")<=through).limit(limit).toList();
        }
        @Override public int removeExpired(long cutoff){Assert.assertTrue(cutoff<System.currentTimeMillis());removedCalls++;return removedCalls%2==1?200:3;}
    }
    static void inject(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    static void inbound(ApiV3RealtimeChannel channel,WebSocketSession session,String text)throws Exception{
        var ctor=WebSocketChannel.Message.class.getDeclaredConstructor(String.class,WebSocketSession.class);ctor.setAccessible(true);channel.onMessage(ctor.newInstance(text,session));
    }
    static JSONObject read(ApiV3SocketHandshakeTest.Fixture fixture,boolean wait)throws Exception{
        long end=System.currentTimeMillis()+(wait?2000:0);
        do{fixture.channel.runPendingTasks();Object frame=fixture.channel.readOutbound();if(frame!=null){Assert.assertTrue(frame instanceof TextWebSocketFrame);var text=(TextWebSocketFrame)frame;try{return new JSONObject(text.text());}finally{text.release();}}if(!wait)return null;Thread.sleep(5);}while(System.currentTimeMillis()<end);
        Assert.fail("expected outbound frame");return null;
    }
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");for(String name:List.of("latkeProps","localProps")){var field=Latkes.class.getDeclaredField(name);field.setAccessible(true);field.set(null,props);}Latkes.setContextPath("");
        var service=new ApiV3RealtimeService();var authority=new Authority();var revisions=new Revisions();inject(service,"tickets",authority);inject(service,"revisions",revisions);inject(service,"topicDelivery",new org.b3log.symphony.service.ChatTopicDeliveryService());var channel=new ApiV3RealtimeChannel();inject(channel,"realtime",service);
        try(var f=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.1")){
            f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10485760));f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10485760));
            var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,channel);
            channel.onConnect(f.session);Assert.assertEquals(read(f,true).getString("type"),"authentication.required");
            inbound(channel,f.session,new JSONObject().put("type","authenticate").put("ticket",authority.ticket).toString());Assert.assertEquals(read(f,true).getString("type"),"ready");
            service.pollNotifications();revisions.values.put("999","other");service.pollNotifications();Assert.assertNull(read(f,false));revisions.values.put("123","first");service.pollNotifications();var changed=read(f,true);Assert.assertEquals(changed.getString("type"),"notifications.changed");Assert.assertEquals(changed.getJSONObject("data").length(),0);
            service.pollNotifications();Assert.assertNull(read(f,false));revisions.values.put("123","second");service.pollNotifications();Assert.assertEquals(read(f,true).getString("type"),"notifications.changed");
            authority.transaction=true;try{service.pollNotifications();Assert.fail();}catch(IllegalStateException expected){}authority.transaction=false;Assert.assertNull(read(f,false));
            inbound(channel,f.session,"{\"type\":\"ping\"}");Assert.assertEquals(read(f,true).getString("type"),"pong");
            authority.allowed=false;inbound(channel,f.session,"{\"type\":\"ping\"}");Assert.assertFalse(f.channel.isActive());Assert.assertNull(read(f,false));
        }
        System.out.println("PASS actual channel/facade authenticates, synchronizes only changed owner revisions outside transactions, deduplicates markers, pongs and closes revoked peers");
        try(var f=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path()+"?apiKey=PRIVATE","192.0.2.1")){
            var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,channel);channel.onConnect(f.session);Assert.assertFalse(f.channel.isActive());Assert.assertNull(read(f,false));
        }
        AnonymousViewCheckMidware.ipBlacklistCache.put("192.0.2.1",true);
        try(var f=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.1")){
            f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10485760));f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10485760));
            var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,channel);channel.onConnect(f.session);Assert.assertFalse(f.channel.isActive());Assert.assertNull(read(f,false));
        }finally{AnonymousViewCheckMidware.ipBlacklistCache.invalidate("192.0.2.1");}
        System.out.println("PASS actual new channel rejects query credentials and explicitly enforces the blacklist before sending its authentication challenge");
        var replacement=new Authority();inject(service,"tickets",replacement);revisions.unavailable=true;
        try(var f=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.1")){
            f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10485760));f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10485760));
            var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,channel);channel.onConnect(f.session);read(f,true);
            inbound(channel,f.session,new JSONObject().put("type","authenticate").put("ticket",replacement.ticket).toString());Assert.assertEquals(read(f,true).getString("type"),"ready");
            long end=System.currentTimeMillis()+2500;while(f.channel.isActive()&&System.currentTimeMillis()<end){f.channel.runPendingTasks();Thread.sleep(10);}Assert.assertFalse(f.channel.isActive());
        }
        System.out.println("PASS live notification connections close when the committed marker source becomes unavailable");
        var articleAuthority=new Authority();articleAuthority.channel="article";articleAuthority.target="789";
        var articleRevisions=new ArticleRevisions();inject(service,"tickets",articleAuthority);inject(service,"articleRevisions",articleRevisions);
        try(var f=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.1")){
            f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10485760));f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10485760));
            var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,channel);channel.onConnect(f.session);read(f,true);
            inbound(channel,f.session,new JSONObject().put("type","authenticate").put("ticket",articleAuthority.ticket).toString());var ready=read(f,true).getJSONObject("data");Assert.assertEquals(ready.getString("channel"),"article");Assert.assertEquals(ready.getString("articleId"),"789");
            service.pollNotifications();service.pollArticles();Assert.assertNull(read(f,false));
            articleRevisions.values.put("987","unrelated");service.pollArticles();Assert.assertNull(read(f,false));
            articleRevisions.values.put("789","one");service.pollArticles();var changed=read(f,true);Assert.assertEquals(changed.getString("type"),"article.changed");Assert.assertEquals(changed.getJSONObject("data").toString(),new JSONObject().put("articleId","789").toString());
            service.pollArticles();Assert.assertNull(read(f,false));
            articleAuthority.transaction=true;try{service.pollArticles();Assert.fail();}catch(IllegalStateException expected){}articleAuthority.transaction=false;
            articleAuthority.allowed=false;articleRevisions.values.put("789","two");service.pollArticles();Assert.assertFalse(f.channel.isActive());Assert.assertNull(read(f,false));
        }
        System.out.println("PASS actual article channel/facade polls only subscribed committed markers, deduplicates, rejects writer snapshots and reauthorizes before ID-only changes");
        var articleReplacement=new Authority();articleReplacement.channel="article";articleReplacement.target="789";inject(service,"tickets",articleReplacement);articleRevisions.unavailable=true;
        try(var f=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.1")){
            f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10485760));f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10485760));
            var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,channel);channel.onConnect(f.session);read(f,true);
            inbound(channel,f.session,new JSONObject().put("type","authenticate").put("ticket",articleReplacement.ticket).toString());Assert.assertEquals(read(f,true).getString("type"),"ready");
            long end=System.currentTimeMillis()+2500;while(f.channel.isActive()&&System.currentTimeMillis()<end){f.channel.runPendingTasks();Thread.sleep(10);}Assert.assertFalse(f.channel.isActive());
        }
        System.out.println("PASS existing article connections close when article synchronization fails rather than remaining ready without updates");
        var chatAuthority=new Authority();chatAuthority.channel="chat";var chatEvents=new ChatEvents();inject(service,"tickets",chatAuthority);inject(service,"chatEvents",chatEvents);
        try(var f=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.1")){
            f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10485760));f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10485760));
            var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,channel);channel.onConnect(f.session);read(f,true);
            inbound(channel,f.session,new JSONObject().put("type","authenticate").put("ticket",chatAuthority.ticket).toString());Assert.assertEquals(read(f,true).getJSONObject("data").getString("channel"),"chat");
            service.pollChat();Assert.assertEquals(read(f,true).getString("type"),"chat.sync_required");service.pollChat();Assert.assertNull(read(f,false));
            chatEvents.add("created","789");service.pollChat();var message=read(f,true);Assert.assertEquals(message.getString("type"),"chat.message.created");Assert.assertEquals(message.getJSONObject("data").keySet(),Set.of("messageId","revision"));Assert.assertEquals(message.getJSONObject("data").getString("revision"),"1");
            var barrage=new ApiV3BarrageEvent("00000000-0000-4000-8000-000000000002","hello 😀","#FFFFFF",1000,"123","Name","Nickname","https://example.com/avatar.png");
            service.publishBarrage(barrage);var animation=read(f,true);Assert.assertEquals(animation.getString("type"),"chat.barrager");Assert.assertEquals(animation.getJSONObject("data").toString(),barrage.dto().toString());
            chatAuthority.transaction=true;try{service.publishBarrage(barrage);Assert.fail();}catch(IllegalStateException expected){}chatAuthority.transaction=false;
            chatEvents.add("topic","0");service.pollChat();var topic=read(f,true);Assert.assertEquals(topic.getString("type"),"chat.topic_changed");Assert.assertEquals(topic.getJSONObject("data").keySet(),Set.of("revision"));Assert.assertEquals(topic.getJSONObject("data").getString("revision"),"2");
            chatEvents.add("deleted","789");chatEvents.events.clear();service.pollChat();Assert.assertEquals(read(f,true).getString("type"),"chat.sync_required");
            chatAuthority.transaction=true;try{service.pollChat();Assert.fail();}catch(IllegalStateException expected){}chatAuthority.transaction=false;
            Assert.assertEquals(service.cleanupChatEvents(),203);Assert.assertEquals(chatEvents.removedCalls,2);
            chatAuthority.allowed=false;chatEvents.add("reactions","987");service.pollChat();Assert.assertFalse(f.channel.isActive());Assert.assertNull(read(f,false));
        }
        System.out.println("PASS actual chat facade/channel handles baseline, versioned ID hints, retention gaps, writer guards, bounded cleanup and revoked peers");
        var chatReplacement=new Authority();chatReplacement.channel="chat";inject(service,"tickets",chatReplacement);
        try(var f=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.1")){
            f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10485760));f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10485760));
            var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,channel);channel.onConnect(f.session);read(f,true);
            inbound(channel,f.session,new JSONObject().put("type","authenticate").put("ticket",chatReplacement.ticket).toString());Assert.assertEquals(read(f,true).getString("type"),"ready");chatEvents.missingEvents=true;
            long end=System.currentTimeMillis()+2500;while(f.channel.isActive()&&System.currentTimeMillis()<end){f.channel.runPendingTasks();Thread.sleep(10);}Assert.assertFalse(f.channel.isActive());
        }
        System.out.println("PASS periodic chat source failure closes its authenticated connections rather than leaving an unusable ready stream");
        var isolated=new ApiV3RealtimeService();var chatSource=new ChatEvents();var notificationSource=new Revisions();
        var topicProjection=new org.b3log.symphony.service.ChatTopicDeliveryService();inject(isolated,"topicDelivery",topicProjection);

        final String chatTicket=ApiV3TokenUtil.generate(),notificationTicket=ApiV3TokenUtil.generate();
        var grants=new HashMap<String,ApiV3SocketTicketService.Grant>();
        grants.put(chatTicket,new ApiV3SocketTicketService.Grant("123","456",System.currentTimeMillis()+60000,"chat",""));
        grants.put(notificationTicket,new ApiV3SocketTicketService.Grant("321","654",System.currentTimeMillis()+60000));
        var sharedAuthority=new Authority(){@Override public Grant consume(String value){final var grant=grants.remove(value);if(grant==null)throw new IllegalArgumentException();return grant;}};
        inject(isolated,"tickets",sharedAuthority);isolated.pollTopic();Assert.assertEquals(topicProjection.calls,1);inject(isolated,"chatEvents",chatSource);inject(isolated,"revisions",notificationSource);inject(isolated,"articleRevisions",new ArticleRevisions());
        var isolatedChannel=new ApiV3RealtimeChannel();inject(isolatedChannel,"realtime",isolated);isolated.start();
        try(var c=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.10");var n=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.11")){
            for(final var f:List.of(c,n)){
                f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10485760));f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10485760));
                var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,isolatedChannel);isolatedChannel.onConnect(f.session);read(f,true);
                inbound(isolatedChannel,f.session,new JSONObject().put("type","authenticate").put("ticket",f==c?chatTicket:notificationTicket).toString());Assert.assertEquals(read(f,true).getString("type"),"ready");
            }
            chatSource.missingEvents=true;
            long end=System.currentTimeMillis()+2500;while(c.channel.isActive()&&System.currentTimeMillis()<end){c.channel.runPendingTasks();n.channel.runPendingTasks();Thread.sleep(10);}
            Assert.assertFalse(c.channel.isActive());Assert.assertTrue(n.channel.isActive());
            inbound(isolatedChannel,n.session,"{\"type\":\"ping\"}");Assert.assertEquals(read(n,true).getString("type"),"pong");
        }
        Assert.assertTrue(topicProjection.calls>1,"topic projection must run even without topic subscribers");
        System.out.println("PASS startup scheduling recovers topic projection and probes an idle chat event source and its outage closes chat peers while preserving a healthy notification connection");
        var privateAuthority=new Authority();privateAuthority.channel="messages";var privateRevisions=new PrivateRevisions();inject(service,"tickets",privateAuthority);inject(service,"privateRevisions",privateRevisions);
        try(var f=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.1")){
            f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10485760));f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10485760));
            var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,channel);channel.onConnect(f.session);read(f,true);
            inbound(channel,f.session,new JSONObject().put("type","authenticate").put("ticket",privateAuthority.ticket).toString());var ready=read(f,true).getJSONObject("data");Assert.assertEquals(ready.getString("channel"),"messages");Assert.assertEquals(ready.keySet(),Set.of("channel","expiresAt"));
            service.pollMessages();privateRevisions.values.put("321","other");service.pollMessages();Assert.assertNull(read(f,false));
            privateRevisions.values.put("123","one");service.pollMessages();var hint=read(f,true);Assert.assertEquals(hint.getString("type"),"messages.changed");Assert.assertEquals(hint.getJSONObject("data").length(),0);
            service.pollMessages();Assert.assertNull(read(f,false));privateAuthority.transaction=true;try{service.pollMessages();Assert.fail();}catch(IllegalStateException expected){}privateAuthority.transaction=false;
            privateAuthority.allowed=false;privateRevisions.values.put("123","two");service.pollMessages();Assert.assertFalse(f.channel.isActive());Assert.assertNull(read(f,false));
        }
        var privateReplacement=new Authority();privateReplacement.channel="messages";inject(service,"tickets",privateReplacement);privateRevisions.unavailable=true;
        try(var f=new ApiV3SocketHandshakeTest.Fixture(ApiV3RealtimeChannel.path(),"192.0.2.1")){
            f.channel.pipeline().addBefore("latke-websocket","wsdecoder",new WebSocket13FrameDecoder(true,true,10485760));f.channel.pipeline().addBefore("latke-websocket","wsaggregator",new WebSocketFrameAggregator(10485760));
            var callback=WebSocketSession.class.getDeclaredField("webSocketChannel");callback.setAccessible(true);callback.set(f.session,channel);channel.onConnect(f.session);read(f,true);
            inbound(channel,f.session,new JSONObject().put("type","authenticate").put("ticket",privateReplacement.ticket).toString());Assert.assertEquals(read(f,true).getString("type"),"ready");
            long end=System.currentTimeMillis()+2500;while(f.channel.isActive()&&System.currentTimeMillis()<end){f.channel.runPendingTasks();Thread.sleep(10);}Assert.assertFalse(f.channel.isActive());
        }
        System.out.println("PASS private facade polls only subscribed owner markers outside writers, deduplicates empty hints, rechecks grants and closes live connections on committed-source failure");

    }
}
