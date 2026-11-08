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
package org.b3log.symphony.service;

import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.AnonymousCaptchaShield;
import org.b3log.symphony.util.ApiV3RealtimeSessions;
import org.b3log.symphony.util.Firewall;
import org.b3log.symphony.util.ApiV3SocketHandshake;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.repository.ApiV3NotificationRevisionRepository;
import org.b3log.symphony.repository.ApiV3PrivateRevisionRepository;
import org.b3log.symphony.repository.ApiV3ArticleRevisionRepository;
import org.b3log.symphony.repository.ApiV3ChatEventRepository;
import org.b3log.symphony.util.ApiV3ChatStream;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Transport adapter and bounded best-effort fanout. No business writes or legacy message forwarding. */
@Service
public class ApiV3RealtimeService {
    @Inject private ApiV3SocketTicketService tickets;
    @Inject private ApiV3NotificationRevisionRepository revisions;
    @Inject private ApiV3ArticleRevisionRepository articleRevisions;
    @Inject private ApiV3PrivateRevisionRepository privateRevisions;
    @Inject private ApiV3ChatEventRepository chatEvents;
    @Inject private ChatTopicDeliveryService topicDelivery;
    private ApiV3ChatStream chatStream;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),
            Thread.ofPlatform().daemon().name("v3-realtime-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("v3-realtime-timer").factory());
    private final AtomicBoolean ticking=new AtomicBoolean();
    private final AtomicBoolean cleaning=new AtomicBoolean();
    private final AtomicBoolean polling=new AtomicBoolean();
    private final ConcurrentHashMap<String,String> seen=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,String> seenMessages=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,String> seenArticles=new ConcurrentHashMap<>();
    private volatile ApiV3RealtimeSessions sessions;
    public void start(){registry();}
    public Set<String> chatUserIds(){final var active=sessions;return active==null?Set.of():active.chatUserIds();}
    public int articleConnectionCount(final String articleId){final var active=sessions;return active==null?0:active.articleConnectionCount(articleId);}
    private synchronized ApiV3RealtimeSessions registry(){
        if(sessions==null){
            sessions=new ApiV3RealtimeSessions(new ApiV3RealtimeSessions.Authority(){
                public ApiV3SocketTicketService.Grant consume(String ticket)throws Exception{return tickets.consume(ticket);}
                public boolean authorized(ApiV3SocketTicketService.Grant grant)throws Exception{return tickets.authorized(grant);}
            // 实时连接也须经过验证码盾；未经阿达明确允许，不得修改、移动、删除或绕过此调用。
            },(ip,record)->!AnonymousCaptchaShield.isRestricted(ip)
                    &&(!record||Firewall.recordAndMaybeBan(ip)),System::currentTimeMillis);
            timer.scheduleAtFixedRate(()->{
                sessions.sweep();
                if(polling.compareAndSet(false,true))try{executeWorker(()->{
                    try{
                        try{pollNotifications();pollArticles();pollMessages();}
                        catch(Exception ignored){org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 realtime synchronization unavailable");sessions.closeAll();}
                        try{pollTopic();}
                        catch(Exception ignored){org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("Committed chat topic projection unavailable");}
                        try{pollChat();}
                        catch(Exception ignored){org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 chat synchronization unavailable");sessions.closeChat();resetChat();}
                    }
                    finally{polling.set(false);}
                });}
                catch(RejectedExecutionException ignored){polling.set(false);}
                if(ticking.compareAndSet(false,true))try{executeWorker(()->{try{sessions.tick();}finally{ticking.set(false);}});}
                catch(RejectedExecutionException ignored){ticking.set(false);}
            },1,1,TimeUnit.SECONDS);
            timer.scheduleWithFixedDelay(()->{
                if(cleaning.compareAndSet(false,true))try{executeWorker(()->{
                    try{
                        try{for(int batch=0;batch<5;batch++)if(tickets.removeExpired()<200)break;}
                        catch(Exception ignored){org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 expired socket-ticket cleanup failed");}
                        try{cleanupChatEvents();}
                        catch(Exception ignored){org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 expired chat-event cleanup failed");}
                    }
                    finally{cleaning.set(false);}
                });}catch(RejectedExecutionException ignored){cleaning.set(false);}
            },1,1,TimeUnit.MINUTES);
        }
        return sessions;
    }
    public void open(WebSocketSession session,String ip){
        final ApiV3RealtimeSessions active=sessions==null?registry():sessions;
        active.open(session,new ApiV3RealtimeSessions.Transport(){
        public void send(String text){if(!ApiV3SocketHandshake.writable(session))throw new IllegalStateException("socket backpressure");session.sendText(text);}public void close(){session.close();}
    },ip);}
    public void receive(WebSocketSession session,String text){final var registry=sessions;if(registry!=null)registry.receive(session,text);else session.close();}
    public void close(WebSocketSession session){final var registry=sessions;if(registry!=null)registry.close(session);}
    private void executeWorker(Runnable work){workers.execute(()->{
        try{work.run();}
        finally{try{ApiV3Transactions.releaseThreadResources();}catch(RuntimeException ignored){org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 background JDBC cleanup failed");}}
    });}
    /** Read committed markers, including writes produced by legacy callers or another backend instance. */
    public synchronized void pollNotifications()throws Exception {
        if(sessions==null)return;tickets.requireNoTransaction();
        final Set<String> owners=sessions.owners();if(owners.isEmpty()){seen.clear();return;}
        final var snapshot=revisions.snapshot(owners);seen.keySet().retainAll(owners);
        for(final String owner:owners){
            final String revision=snapshot.getOrDefault(owner,"");final String previous=seen.get(owner);
            if(!revision.equals(previous)&&(!revision.isEmpty()||previous!=null))sessions.notificationsChanged(owner);
            seen.put(owner,revision);
        }
    }
    public synchronized void pollArticles()throws Exception {
        if(sessions==null)return;tickets.requireNoTransaction();
        final Set<String> ids=sessions.articleIds();if(ids.isEmpty()){seenArticles.clear();return;}
        final var snapshot=articleRevisions.snapshot(ids);seenArticles.keySet().retainAll(ids);
        for(final String id:ids){
            final String revision=snapshot.getOrDefault(id,"");final String previous=seenArticles.get(id);
            if(!revision.equals(previous)&&(!revision.isEmpty()||previous!=null))sessions.articleChanged(id);
            seenArticles.put(id,revision);
        }
    }
    /** Coalesced per-owner refresh hints, never message history, bodies, peers or unread state. */
    public synchronized void pollMessages()throws Exception {
        if(sessions==null)return;tickets.requireNoTransaction();
        final Set<String> owners=sessions.messageOwners();if(owners.isEmpty()){seenMessages.clear();return;}
        final var snapshot=privateRevisions.snapshot(owners);seenMessages.keySet().retainAll(owners);
        for(final String owner:owners){
            final String revision=snapshot.getOrDefault(owner,"");final String previous=seenMessages.get(owner);
            if(!revision.equals(previous)&&(!revision.isEmpty()||previous!=null))sessions.messagesChanged(owner);
            seenMessages.put(owner,revision);
        }
    }
    private synchronized void resetChat(){if(chatStream!=null)chatStream.reset();}
    /** Bounded, transient delivery on this instance; never part of the durable message cursor. */
    public void publishBarrage(final org.b3log.symphony.util.ApiV3BarrageEvent event){
        tickets.requireNoTransaction();final var active=sessions;if(active==null||!active.hasChat())return;
        executeWorker(()->{tickets.requireNoTransaction();active.chatBarrage(event);});
    }
    public void pollTopic()throws Exception {tickets.requireNoTransaction();topicDelivery.refreshCommitted();}
    public synchronized void pollChat()throws Exception {
        if(sessions==null)return;tickets.requireNoTransaction();
        if(!sessions.hasChat()){resetChat();return;}
        if(chatStream==null)chatStream=new ApiV3ChatStream(new ApiV3ChatStream.Source(){
            public long head()throws Exception{return chatEvents.head();}
            public java.util.List<org.json.JSONObject> after(long revision,long through,int limit)throws Exception{return chatEvents.after(revision,through,limit);}
        },new ApiV3ChatStream.Sink(){
            public void changed(String kind,String id,long revision){sessions.chatChanged(kind,id,revision);}
            public void syncRequired(){sessions.chatSyncRequired();}
        });
        chatStream.poll();
    }
    public int cleanupChatEvents()throws Exception {
        tickets.requireNoTransaction();int removed=0;
        final long cutoff=System.currentTimeMillis()-7L*24*60*60*1000;
        for(int batch=0;batch<5;batch++){final int count=chatEvents.removeExpired(cutoff);removed+=count;if(count<200)break;}
        return removed;
    }
}
