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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.model.User;
import org.b3log.symphony.processor.ApiV3Processor;
import org.b3log.symphony.processor.ApiV3RealtimeProcessor;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.b3log.symphony.service.ApiV3SocketTicketService;
import org.b3log.symphony.service.ApiV3RealtimeTargetService;
import org.b3log.latke.repository.RepositoryException;
import org.json.JSONObject;
import org.testng.Assert;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.List;

/** Real middleware/input/controller; authentication and final envelope adapter are explicit test boundaries. */
public class ApiV3RealtimeProcessorTest {
    static class Fixture implements AutoCloseable {
        final DefaultFullHttpRequest raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.POST,"/api/v3/realtime/tickets?apiKey=old");
        final DefaultHttpResponse response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);final RequestContext c;
        Fixture(String body,String header){raw.headers().set("Content-Type","application/json");if(header!=null)raw.headers().set("Authorization",header);var request=new Request(null,raw);raw.content().clear().writeBytes(body.getBytes(StandardCharsets.UTF_8));request.setBytes(body.getBytes(StandardCharsets.UTF_8));c=new RequestContext(request,new Response(null,response));c.attr(User.USER,new JSONObject().put("oId","321"));c.addHandler(context->context.attr("test.accepted",true));}
        public void close(){raw.release();}
    }
    static class Authentication extends ApiV3TokenMgmtService {
        String scope="notifications.read";
        @Override public JSONObject authenticate(String value){if(!value.equals("VALID"))return null;return new JSONObject().put("user",new JSONObject().put("oId","123")).put("tokenId","456").put("scope",scope);}
    }
    static class Tickets extends ApiV3SocketTicketService {
        int issues;
        int articleIssues;
        int chatIssues;int messageIssues;
        @Override public Issued issueMessages(String actor,String tokenId){Assert.assertEquals(actor,"123");Assert.assertEquals(tokenId,"456");messageIssues++;return new Issued("OPAQUE-PRIVATE-TICKET",60);}
        @Override public Issued issue(String actor,String tokenId){Assert.assertEquals(actor,"123");Assert.assertEquals(tokenId,"456");issues++;return new Issued("OPAQUE-TICKET",60);}
        @Override public Issued issueArticle(String actor,String tokenId,String articleId){Assert.assertEquals(actor,"123");Assert.assertEquals(tokenId,"456");Assert.assertEquals(articleId,"789");articleIssues++;return new Issued("OPAQUE-ARTICLE-TICKET",60);}
        @Override public Issued issueChat(String actor,String tokenId){Assert.assertEquals(actor,"123");Assert.assertEquals(tokenId,"456");chatIssues++;return new Issued("OPAQUE-CHAT-TICKET",60);}
    }
    static class Targets extends ApiV3RealtimeTargetService {
        boolean visible=true,unavailable;
        @Override public boolean articleVisible(String actor,String id)throws RepositoryException {
            Assert.assertEquals(actor,"123");Assert.assertEquals(id,"789");if(unavailable)throw new RepositoryException("target unavailable");return visible;
        }
    }
    static void inject(Object bean,String name,Object value)throws Exception{var field=bean.getClass().getDeclaredField(name);field.setAccessible(true);field.set(bean,value);}
    public static void main(String[] args)throws Exception {
        var props=new java.util.Properties();props.setProperty("runtimeDatabase","NONE");for(String key:new String[]{"latkeProps","localProps"}){var field=org.b3log.latke.Latkes.class.getDeclaredField(key);field.setAccessible(true);field.set(null,props);}
        org.b3log.latke.Latkes.setContextPath("");
        var auth=new ApiV3AuthMidware();var authentication=new Authentication();inject(auth,"apiV3TokenMgmtService",authentication);var p=new ApiV3RealtimeProcessor();var tickets=new Tickets();var revisions=new ApiV3RealtimeChannelTest.Revisions();inject(p,"tickets",tickets);inject(p,"revisions",revisions);
        for(String value:new String[]{null,"Bearer INVALID"})try(var f=new Fixture("{}",value)){auth.notificationsRead(f.c);Assert.assertNull(f.c.attr("test.accepted"));Assert.assertEquals(f.c.attr("test.status"),401);}
        authentication.scope="profile.read";try(var f=new Fixture("{}","Bearer VALID")){auth.notificationsRead(f.c);Assert.assertNull(f.c.attr("test.accepted"));Assert.assertEquals(f.c.attr("test.status"),403);}authentication.scope="notifications.read";
        try(var f=new Fixture("{}","Bearer VALID")){auth.notificationsRead(f.c);Assert.assertEquals(f.c.attr("test.accepted"),true);p.ticket(f.c);var data=(JSONObject)f.c.attr("test.data");Assert.assertEquals(data.keySet(),Set.of("ticket","expiresIn","path","channel"));Assert.assertEquals(data.getString("channel"),"notifications");Assert.assertEquals(data.getString("path"),"/api/v3/realtime");Assert.assertEquals(f.response.headers().get("Cache-Control"),"no-store");Assert.assertEquals(f.response.headers().get("Vary"),"Authorization");Assert.assertEquals(tickets.issues,1);}
        System.out.println("PASS actual scope middleware and ticket controller use Bearer identity/token row only; Cookie/apiKey identity cannot substitute, and grant DTO is not cached");
        for(String body:new String[]{"{\"userId\":\"321\"}","{\"tokenId\":\"999\"}","{\"channel\":\"admin\"}","{\"scope\":\"admin\"}","{\"apiKey\":\"old\"}"})try(var f=new Fixture(body,"Bearer VALID")){auth.notificationsRead(f.c);p.ticket(f.c);Assert.assertEquals(f.c.attr("test.status"),422);Assert.assertEquals(tickets.issues,1);}
        System.out.println("PASS actual ticket controller rejects client-selected user, parent token, scope, channel and legacy credentials before issuance");
        revisions.unavailable=true;try(var f=new Fixture("{}","Bearer VALID")){auth.notificationsRead(f.c);p.ticket(f.c);Assert.assertEquals(f.c.attr("test.status"),503);Assert.assertEquals(tickets.issues,1);}
        System.out.println("PASS ticket issuance fails with a controlled unavailable response before minting when notification synchronization is unavailable");
        var articleRevisions=new ApiV3RealtimeChannelTest.ArticleRevisions();var targets=new Targets();inject(p,"articleRevisions",articleRevisions);inject(p,"targets",targets);
        authentication.scope="notifications.read";try(var f=new Fixture("{}","Bearer VALID")){auth.contentRead(f.c);Assert.assertEquals(f.c.attr("test.status"),403);Assert.assertNull(f.c.attr("test.accepted"));}
        authentication.scope="content.read";
        try(var f=new Fixture("{}","Bearer VALID")){f.c.pathVars(new java.util.HashMap<>());f.c.pathVar("articleId","789");auth.contentRead(f.c);p.articleTicket(f.c);Assert.assertEquals(f.c.attr("test.status"),200);var data=(JSONObject)f.c.attr("test.data");Assert.assertEquals(data.keySet(),Set.of("ticket","expiresIn","path","channel","articleId"));Assert.assertEquals(data.getString("channel"),"article");Assert.assertEquals(data.getString("articleId"),"789");Assert.assertEquals(tickets.articleIssues,1);}
        for(String body:new String[]{"{\"articleId\":\"987\"}","{\"userId\":\"321\"}","{\"channel\":\"notifications\"}","{\"apiKey\":\"old\"}"})try(var f=new Fixture(body,"Bearer VALID")){f.c.pathVars(new java.util.HashMap<>());f.c.pathVar("articleId","789");auth.contentRead(f.c);p.articleTicket(f.c);Assert.assertEquals(f.c.attr("test.status"),422);Assert.assertEquals(tickets.articleIssues,1);}
        try(var f=new Fixture("{}","Bearer VALID")){f.c.pathVars(new java.util.HashMap<>());f.c.pathVar("articleId","789' OR 1=1");auth.contentRead(f.c);p.articleTicket(f.c);Assert.assertEquals(f.c.attr("test.status"),422);}
        System.out.println("PASS article ticket controller requires content.read, binds the path ID and Bearer actor/token, and rejects client-selected subscription fields");
        targets.visible=false;try(var f=new Fixture("{}","Bearer VALID")){f.c.pathVars(new java.util.HashMap<>());f.c.pathVar("articleId","789");auth.contentRead(f.c);p.articleTicket(f.c);Assert.assertEquals(f.c.attr("test.status"),404);Assert.assertEquals(tickets.articleIssues,1);}targets.visible=true;
        for(boolean source:new boolean[]{false,true}){
            articleRevisions.unavailable=!source;targets.unavailable=source;
            try(var f=new Fixture("{}","Bearer VALID")){f.c.pathVars(new java.util.HashMap<>());f.c.pathVar("articleId","789");auth.contentRead(f.c);p.articleTicket(f.c);Assert.assertEquals(f.c.attr("test.status"),503);Assert.assertEquals(tickets.articleIssues,1);}
        }
        System.out.println("PASS invisible articles return 404 and permission/marker source failure returns 503 before issuing an article credential");
        var chatEvents=new ApiV3RealtimeChannelTest.ChatEvents();inject(p,"chatEvents",chatEvents);
        authentication.scope="notifications.read";try(var f=new Fixture("{}","Bearer VALID")){auth.contentRead(f.c);Assert.assertEquals(f.c.attr("test.status"),403);Assert.assertNull(f.c.attr("test.accepted"));}
        authentication.scope="content.read";try(var f=new Fixture("{}","Bearer VALID")){auth.contentRead(f.c);p.chatTicket(f.c);var data=(JSONObject)f.c.attr("test.data");Assert.assertEquals(data.keySet(),Set.of("ticket","expiresIn","path","channel"));Assert.assertEquals(data.getString("channel"),"chat");Assert.assertEquals(tickets.chatIssues,1);}
        for(String body:new String[]{"{\"userId\":\"321\"}","{\"channel\":\"notifications\"}","{\"room\":\"admin\"}","{\"apiKey\":\"old\"}"})try(var f=new Fixture(body,"Bearer VALID")){auth.contentRead(f.c);p.chatTicket(f.c);Assert.assertEquals(f.c.attr("test.status"),422);Assert.assertEquals(tickets.chatIssues,1);}
        for(boolean stateFailure:new boolean[]{true,false}){
            chatEvents.unavailable=stateFailure;chatEvents.missingEvents=!stateFailure;
            try(var f=new Fixture("{}","Bearer VALID")){auth.contentRead(f.c);p.chatTicket(f.c);Assert.assertEquals(f.c.attr("test.status"),503);Assert.assertEquals(tickets.chatIssues,1);}
        }
        System.out.println("PASS chat ticket endpoint uses content.read Bearer identity, rejects subscription overrides and checks both state/event sources before issuing");
        var privateRevisions=new ApiV3RealtimeChannelTest.PrivateRevisions();inject(p,"privateRevisions",privateRevisions);
        for(String scope:List.of("messages.write","content.read","notifications.read")){
            authentication.scope=scope;try(var f=new Fixture("{}","Bearer VALID")){auth.messagesRead(f.c);Assert.assertEquals(f.c.attr("test.status"),403);Assert.assertNull(f.c.attr("test.accepted"));}
        }
        authentication.scope="messages.read";
        try(var f=new Fixture("{}","Bearer VALID")){auth.messagesRead(f.c);p.messagesTicket(f.c);Assert.assertEquals(f.c.attr("test.status"),200);var data=(JSONObject)f.c.attr("test.data");Assert.assertEquals(data.keySet(),Set.of("ticket","expiresIn","path","channel"));Assert.assertEquals(data.getString("channel"),"messages");Assert.assertEquals(data.getString("path"),"/api/v3/realtime");Assert.assertEquals(f.response.headers().get("Cache-Control"),"no-store");Assert.assertEquals(tickets.messageIssues,1);}
        for(String body:List.of("{\"userId\":\"321\"}","{\"peerId\":\"321\"}","{\"scope\":\"messages.write\"}","{\"channel\":\"notifications\"}","{\"ticket\":\"PRIVATE\"}"))try(var f=new Fixture(body,"Bearer VALID")){auth.messagesRead(f.c);p.messagesTicket(f.c);Assert.assertEquals(f.c.attr("test.status"),422);Assert.assertEquals(tickets.messageIssues,1);}
        privateRevisions.unavailable=true;try(var f=new Fixture("{}","Bearer VALID")){auth.messagesRead(f.c);p.messagesTicket(f.c);Assert.assertEquals(f.c.attr("test.status"),503);Assert.assertEquals(tickets.messageIssues,1);}
        System.out.println("PASS private ticket endpoint requires messages.read, binds only the Bearer actor/token, returns no-store minimal credentials and rejects overrides or missing marker storage before issue");

    }
}
