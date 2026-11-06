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

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.util.*;

/** All real declarations through Latke RouteHandler and real auth/write middleware; terminal IoC invocation is adapted. */
public class PrivateMessageRoutingTest {
    static void selected(RequestContext c){
        final var route=(org.b3log.latke.http.handler.RouteResolution)c.attr("MATCH_RESULT");
        final String method=route.getContextHandlerMeta().getInvokeHolder().getName();
        c.attr("selected",switch(method){case "send"->"send:"+c.pathVar("peerId");case "markRead"->"peer-read:"+c.pathVar("peerId");case "markAllRead"->"all-read";case "history"->"history:"+c.pathVar("peerId");case "withdraw"->"withdraw:"+c.pathVar("messageId");default->method;});
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        final var processor=new ApiV3PrivateMessageProcessor(){
            @Override public void send(RequestContext c){c.attr("selected","send:"+c.pathVar("peerId"));}
            @Override public void markRead(RequestContext c){c.attr("selected","peer-read:"+c.pathVar("peerId"));}
            @Override public void markAllRead(RequestContext c){c.attr("selected","all-read");}
            @Override public void history(RequestContext c){c.attr("selected","history:"+c.pathVar("peerId"));}
        };
        final var auth=new ApiV3AuthMidware();var tokenField=ApiV3AuthMidware.class.getDeclaredField("apiV3TokenMgmtService");tokenField.setAccessible(true);tokenField.set(auth,new ApiV3TokenMgmtService(){@Override public JSONObject authenticate(String token){return Set.of("reader","writer").contains(token)?new JSONObject().put("user",new JSONObject().put("oId","1")).put("scope",token.equals("writer")?"messages.write":"messages.read"):null;}});
        AllPrivateRouteBindings.register(processor,auth,new ApiV3WriteMidware());Dispatcher.mapping();
        for(String suffix:List.of("2/messages","2/read","read"))for(String token:Arrays.asList(null,"reader","writer")){
            final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/me/conversations/"+suffix);
            try{
                raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw){@Override public String getRemoteAddr(){return "192.0.2.152";}};request.setParams(new HashMap<>());
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(PrivateMessageRoutingTest::selected);c.handle();
                if(!"writer".equals(token)){Assert.assertNull(c.attr("selected"));Assert.assertEquals(c.attr("test.status"),token==null?401:403);}
                else Assert.assertEquals(c.attr("selected"),suffix.equals("read")?"all-read":suffix.endsWith("messages")?"send:2":"peer-read:2");
            }finally{raw.release();}
        }
        for(String token:Arrays.asList(null,"reader","writer")){
            final var deletion=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.DELETE,"/api/v3/me/messages/100");
            try{
                if(token!=null)deletion.headers().set("Authorization","Bearer "+token);var req=new Request(null,deletion){@Override public String getRemoteAddr(){return "192.0.2.154";}};req.setParams(new HashMap<>());
                var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(PrivateMessageRoutingTest::selected);c.handle();
                if("writer".equals(token))Assert.assertEquals(c.attr("selected"),"withdraw:100");else{Assert.assertNull(c.attr("selected"));Assert.assertEquals(c.attr("test.status"),token==null?401:403);}
            }finally{deletion.release();}
        }
        final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/me/conversations/2/messages");
        try{raw.headers().set("Authorization","Bearer reader");var req=new Request(null,raw);req.setParams(new HashMap<>());var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(PrivateMessageRoutingTest::selected);c.handle();Assert.assertEquals(c.attr("selected"),"history:2");}finally{raw.release();}
        System.out.println("PASS actual Latke route mapping/resolution distinguishes withdrawal DELETE and all-read, peer-read and send POST paths from existing GET history, binds peerId and rejects missing/read-only credentials before any mutation handler");
    }
}
