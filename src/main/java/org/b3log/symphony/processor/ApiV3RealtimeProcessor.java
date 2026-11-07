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
package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.processor.channel.ApiV3RealtimeChannel;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3SocketTicketService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.repository.ApiV3NotificationRevisionRepository;
import org.b3log.symphony.repository.ApiV3PrivateRevisionRepository;
import org.b3log.symphony.repository.ApiV3ArticleRevisionRepository;
import org.b3log.symphony.service.ApiV3RealtimeTargetService;
import org.b3log.symphony.service.ApiV3RealtimeService;
import org.b3log.symphony.repository.ApiV3ChatEventRepository;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3RealtimeProcessor {
    @Inject private ApiV3SocketTicketService tickets;
    @Inject private ApiV3NotificationRevisionRepository revisions;
    @Inject private ApiV3ArticleRevisionRepository articleRevisions;
    @Inject private ApiV3PrivateRevisionRepository privateRevisions;
    @Inject private ApiV3RealtimeTargetService targets;
    @Inject private ApiV3ChatEventRepository chatEvents;
    public static void register(){
        final var beans=BeanManager.getInstance();
        final var p=beans.getReference(ApiV3RealtimeProcessor.class);
        final var auth=beans.getReference(ApiV3AuthMidware.class);
        final var writes=beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/realtime/tickets",p::ticket,auth::notificationsRead,writes::handle);
        Dispatcher.post("/api/v3/articles/{articleId}/realtime-ticket",p::articleTicket,auth::contentRead,writes::handle);
        Dispatcher.post("/api/v3/chat/realtime-ticket",p::chatTicket,auth::contentRead,writes::handle);
        Dispatcher.post("/api/v3/me/messages/realtime-ticket",p::messagesTicket,auth::messagesRead,writes::handle);
        Dispatcher.webSocket("/api/v3/realtime",beans.getReference(ApiV3RealtimeChannel.class));
        beans.getReference(ApiV3RealtimeService.class).start();
    }
    public void ticket(RequestContext context){
        ApiV3Requests.respond(context,()->{
            ApiV3WriteInputs.body(context,Set.of(),true);
            final var identity=(JSONObject)context.attr(ApiV3Processor.IDENTITY);
            final String actor=ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID);
            try{revisions.snapshot(Set.of(actor));}
            catch(RepositoryException e){throw new ApiV3Exception(503,"realtime_unavailable","实时通知服务暂不可用");}
            final var issued=tickets.issue(actor,identity.getString("tokenId"));
            return new JSONObject().put("ticket",issued.ticket()).put("expiresIn",issued.expiresIn())
                    .put("path",ApiV3RealtimeChannel.path()).put("channel","notifications");
        });
    }
    public void articleTicket(RequestContext context){
        ApiV3Requests.respond(context,()->{
            ApiV3WriteInputs.body(context,Set.of(),true);
            final String id=ApiV3Requests.id(context,"articleId");
            final var identity=(JSONObject)context.attr(ApiV3Processor.IDENTITY);
            final String actor=ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID);
            try{
                if(!targets.articleVisible(actor,id))throw ApiV3Requests.notFound();
                articleRevisions.snapshot(Set.of(id));
            }catch(RepositoryException e){throw new ApiV3Exception(503,"realtime_unavailable","实时文章服务暂不可用");}
            final var issued=tickets.issueArticle(actor,identity.getString("tokenId"),id);
            return new JSONObject().put("ticket",issued.ticket()).put("expiresIn",issued.expiresIn())
                    .put("path",ApiV3RealtimeChannel.path()).put("channel","article").put("articleId",id);
        });
    }
    public void chatTicket(RequestContext context){
        ApiV3Requests.respond(context,()->{
            ApiV3WriteInputs.body(context,Set.of(),true);
            final var identity=(JSONObject)context.attr(ApiV3Processor.IDENTITY);
            final String actor=ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID);
            try{final long head=chatEvents.head();chatEvents.after(head,head,1);}
            catch(RepositoryException e){throw new ApiV3Exception(503,"realtime_unavailable","实时聊天室服务暂不可用");}
            final var issued=tickets.issueChat(actor,identity.getString("tokenId"));
            return new JSONObject().put("ticket",issued.ticket()).put("expiresIn",issued.expiresIn()).put("path",ApiV3RealtimeChannel.path()).put("channel","chat");
        });
    }
    public void messagesTicket(RequestContext context){
        ApiV3Requests.respond(context,()->{
            ApiV3WriteInputs.body(context,Set.of(),true);
            final var identity=(JSONObject)context.attr(ApiV3Processor.IDENTITY);
            final String actor=ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID);
            try{privateRevisions.snapshot(Set.of(actor));}
            catch(RepositoryException e){throw new ApiV3Exception(503,"realtime_unavailable","实时私信服务暂不可用");}
            final var issued=tickets.issueMessages(actor,identity.getString("tokenId"));
            return new JSONObject().put("ticket",issued.ticket()).put("expiresIn",issued.expiresIn())
                    .put("path",ApiV3RealtimeChannel.path()).put("channel","messages");
        });
    }

}
