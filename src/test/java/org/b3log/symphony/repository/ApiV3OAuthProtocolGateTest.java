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
package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.symphony.processor.ApiV3OAuthProcessor;
import org.b3log.symphony.processor.ApiV3Processor;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.service.*;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.ApiV3AuthorizationCodeTest.*;
import static org.b3log.symphony.repository.ApiV3OAuthHttpTest.form;

/** Real proof/HTTP/write core and routes; Cookie loading and anonymous challenge response are explicit adapters. */
public class ApiV3OAuthProtocolGateTest {
    static class C extends ApiV3OAuthProcessor {
        int exchanges,revokes;Runnable beforeExchange=()->{};
        protected JSONObject cookieUser(RequestContext c){return null;}
        protected String site(){return "https://fishpi.cn";}
        public void token(RequestContext c){Assert.assertNull(c.attr(ApiV3Processor.IDENTITY));exchanges++;beforeExchange.run();super.token(c);}
        public void revoke(RequestContext c){Assert.assertNull(c.attr(ApiV3Processor.IDENTITY));revokes++;super.revoke(c);}
    }
    static class F extends ApiV3OAuthCredentialQueryTest.Proof {
        final C c=new C();int anonymousCalls;
        F()throws Exception{
            var cors=new ApiV3OAuthCorsService();inject(cors,ApiV3OAuthCorsService.class,"clients",clients);inject(cors,ApiV3OAuthCorsService.class,"redirects",redirects);
            for(var e:Map.<String,Object>of("codes",grants,"tokens",service,"credentials",proof,"cors",cors).entrySet())inject(c,ApiV3OAuthProcessor.class,e.getKey(),e.getValue());
            ApiV3OAuthProcessor.registerRoutes(c,new AnonymousViewCheckMidware(){public void handle(RequestContext context){anonymousCalls++;context.setStatus(302);context.setHeader("Location","/test");context.abort();}});Dispatcher.mapping();
        }
        JSONObject call(String endpoint,Map<String,String> fields,Map<String,String> headers)throws Exception{
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/oauth/"+endpoint);String body=form(fields);
            try{
                raw.headers().set("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");raw.headers().set("Cookie","fake-page-session");headers.forEach(raw.headers()::set);
                var request=new Request(null,raw){public String getRemoteAddr(){return "192.0.2.221";}};request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","fake-legacy-key")));
                var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);var context=new RequestContext(request,new Response(null,response));context.attr("user",new JSONObject().put("oId","2"));
                context.addHandler(new org.b3log.latke.http.handler.RouteHandler());context.addHandler(v->{var r=(org.b3log.latke.http.handler.RouteResolution)v.attr("MATCH_RESULT");try{r.getContextHandlerMeta().getInvokeHolder().invoke(c,v);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}});context.handle();
                Assert.assertNull(context.attr(ApiV3Processor.IDENTITY));Assert.assertTrue(context.getResponse().getCookies().isEmpty());Assert.assertEquals(response.headers().get("Cache-Control"),"no-store");
                var result=new JSONObject().put("status",response.status().code());if(context.getRenderer() instanceof JsonRenderer renderer)result.put("body",renderer.getJSONObject());return result;
            }finally{raw.release();}
        }
        Map<String,String> code(String code){return Map.of("grant_type","authorization_code","client_id",CLIENT,"redirect_uri",URI,"code",code,"code_verifier",VERIFIER);}
    }
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);
        try(var f=new F()){
            var issued=f.issue("content.read");f.c.beforeExchange=()->{try{Assert.assertEquals(query(f.reader,"SELECT consumedAt FROM codes").getFirst().getLong("consumedAt"),0L);Assert.assertTrue(query(f.reader,"SELECT * FROM tokens").isEmpty());}catch(Exception e){throw new IllegalStateException(e);}};
            var result=f.call("token",f.code(issued.code()),Map.of());Assert.assertEquals(result.getInt("status"),200);Assert.assertTrue(result.getJSONObject("body").has("access_token"));Assert.assertEquals(f.anonymousCalls,0);Assert.assertEquals(f.c.exchanges,1);
        }
        System.out.println("PASS protocol 1 real native no-Cookie PKCE request passes endpoint-local read-only proof to strict exchange, with no anonymous redirect/session/ordinary Bearer identity or premature code consumption");
        try(var f=new F()){
            var issued=f.issue("content.read");var valid=f.code(issued.code());
            for(Map<String,String> changes:List.of(Map.of("code_verifier","b".repeat(43)),Map.of("client_id","other"),Map.of("redirect_uri",URI+"&tamper=1"),Map.of("code","guessed"),Map.of("refresh_token","unexpected"))){var values=new HashMap<>(valid);values.putAll(changes);Assert.assertEquals(f.call("token",values,Map.of()).getInt("status"),302);}
            Assert.assertEquals(f.c.exchanges,0);Assert.assertEquals(f.anonymousCalls,5);Assert.assertTrue(query(f.reader,"SELECT * FROM tokens").isEmpty());
            Assert.assertEquals(f.call("token",valid,Map.of("Origin","https://evil.example")).getInt("status"),302);Assert.assertEquals(f.call("token",valid,Map.of("Authorization","Bearer fake")).getInt("status"),302);
        }
        System.out.println("PASS protocol 2 wrong PKCE/client/redirect/guessed code/mixed grants, forbidden origin and Authorization retain the anonymous gate; fake legacy Cookie/apiKey/user attr cannot authenticate");
        try(var f=new F()){
            var pair=f.grant(CLIENT,"content.read");var values=Map.of("grant_type","refresh_token","client_id",CLIENT,"refresh_token",pair.getString("refreshToken"));
            var next=f.call("token",values,Map.of());Assert.assertEquals(next.getInt("status"),200);String access=next.getJSONObject("body").getString("access_token");Assert.assertNotNull(f.service.authenticate(access));
            var replay=f.call("token",values,Map.of());Assert.assertEquals(replay.getInt("status"),400);Assert.assertEquals(replay.getJSONObject("body").getString("error"),"invalid_grant");Assert.assertNull(f.service.authenticate(access));Assert.assertEquals(f.anonymousCalls,0);
        }
        try(var f=new F()){
            var issued=f.issue("content.read");var first=f.call("token",f.code(issued.code()),Map.of());var replay=f.call("token",f.code(issued.code()),Map.of());Assert.assertEquals(replay.getInt("status"),400);Assert.assertNull(f.service.authenticate(first.getJSONObject("body").getString("access_token")));Assert.assertEquals(f.anonymousCalls,0);
        }
        System.out.println("PASS protocol 3 current refresh rotates normally and correctly bound rotated-refresh/consumed-code replays reach only existing family-revocation logic without minting credentials or challenge bypass for guessed proofs");
        try(var f=new F()){
            var pair=f.grant(CLIENT,"content.read");Assert.assertEquals(f.call("revoke",Map.of("client_id",CLIENT,"token",pair.getString("accessToken")),Map.of()).getInt("status"),200);Assert.assertEquals(f.c.revokes,1);Assert.assertEquals(f.anonymousCalls,0);Assert.assertNull(f.service.authenticate(pair.getString("accessToken")));
            Assert.assertEquals(f.call("revoke",Map.of("client_id",CLIENT,"token",pair.getString("accessToken")),Map.of()).getInt("status"),302);
        }
        try(var f=new F()){
            var pair=f.grant(CLIENT,"content.read");exec(f.writer,"UPDATE user SET userStatus=4 WHERE oId='1'");Assert.assertEquals(f.call("token",Map.of("grant_type","refresh_token","client_id",CLIENT,"refresh_token",pair.getString("refreshToken")),Map.of()).getInt("status"),302);Assert.assertEquals(f.c.exchanges,0);
        }
        System.out.println("PASS protocol 4 valid client-bound access token admits native revocation; revoked credentials and disabled owners fall back to anonymous checks");
        try(var f=new F()){
            var issued=f.issue("content.read");f.c.beforeExchange=()->{try{exec(f.writer,"UPDATE user SET userStatus=4 WHERE oId='1'");}catch(Exception e){throw new IllegalStateException(e);}};
            var response=f.call("token",f.code(issued.code()),Map.of());Assert.assertNotEquals(response.getInt("status"),200);Assert.assertEquals(f.anonymousCalls,0);Assert.assertTrue(query(f.reader,"SELECT * FROM tokens").isEmpty());Assert.assertEquals(query(f.reader,"SELECT consumedAt FROM codes").getFirst().getLong("consumedAt"),0L);
        }
        System.out.println("PASS protocol 5 a user changed after proof still fails fresh write-core authorization; endpoint admission cannot authorize a stale grant");
        for(String callback:List.of("cn.fishpi.client:/callback?from=desktop","http://127.0.0.1:45678/callback"))try(var f=new F()){
            boolean development=callback.startsWith("http:");f.development=development;Latkes.setRuntimeMode(development?Latkes.RuntimeMode.DEVELOPMENT:Latkes.RuntimeMode.PRODUCTION);f.register(CLIENT,callback);
            var issued=f.issueAt(callback,"content.read");var values=new HashMap<>(f.code(issued.code()));values.put("redirect_uri",callback);Assert.assertEquals(f.call("token",values,Map.of()).getInt("status"),200);Assert.assertEquals(f.anonymousCalls,0);
        }
        Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);
        System.out.println("PASS protocol 6 precisely registered native private-scheme and development loopback callbacks complete the real PKCE exchange without browser session or broad callback exceptions");
    }
}
