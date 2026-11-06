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

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.channel.ArticleChannel;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.UserRepository;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.util.*;
import static org.b3log.symphony.service.ApiV3SocketTicketServiceTest.*;

/** Actual target SQL, registry/grants, facade, controller and route; SQLite, legacy counter and token adapters. */
public class ApiV3ArticleHeatTest {
    static void inject(Object target,Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static class Live extends ApiV3RealtimeSessionsTest.Live {
        final ApiV3ArticleHeatQueryService heat=new ApiV3ArticleHeatQueryService();final ApiV3ArticleHeatProcessor controller=new ApiV3ArticleHeatProcessor();final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        Live()throws Exception {
            ArticleChannel.ARTICLE_VIEWS.clear();
            var target=new ApiV3RealtimeTargetService();inject(target,ApiV3RealtimeTargetService.class,"articles",new ArticleRepository());inject(target,ApiV3RealtimeTargetService.class,"users",new UserRepository());
            var realtime=new ApiV3RealtimeService();inject(realtime,ApiV3RealtimeService.class,"sessions",registry);
            inject(heat,ApiV3ArticleHeatQueryService.class,"targets",target);inject(heat,ApiV3ArticleHeatQueryService.class,"realtime",realtime);inject(controller,ApiV3ArticleHeatProcessor.class,"query",heat);
            inject(auth,ApiV3AuthMidware.class,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){@Override public JSONObject authenticate(String token){return Set.of("reader","wrong").contains(token)?new JSONObject().put("user",new JSONObject().put("oId","123")).put("scope",token.equals("reader")?"content.read":"profile.read"):null;}});
        }
        RequestContext http(String token,String article,boolean route)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/articles/"+article+"/heat?userId=321&apiKey=old");
            try{raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);var request=new Request(null,raw){@Override public String getRemoteAddr(){return "192.0.2.42";}};request.setParams(new HashMap<>(Map.of("userId","321","apiKey","old")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","321"));
                if(route){c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var r=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");Assert.assertEquals(r.getContextHandlerMeta().getInvokeHolder().getName(),"heat");controller.heat(context);});c.handle();}
                else{c.pathVar("articleId",article);c.addHandler(controller::heat);auth.contentRead(c);}return c;
            }finally{raw.release();}
        }
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Live()){
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");
            Assert.assertEquals(f.heat.read("123","789").getLong("heat"),0L);
            ArticleChannel.ARTICLE_VIEWS.put("789",3);ArticleChannel.ARTICLE_VIEWS.put("987",99);
            var a=f.article("a","789");var b=f.article("b","789");var other=f.article("other","987");var pending=f.open("pending","192.0.2.200");f.authenticate("notifications","321","654");
            var dto=f.heat.read("123","789");Assert.assertEquals(dto.keySet(),Set.of("articleId","heat"));Assert.assertEquals(dto.getLong("heat"),5L);Assert.assertEquals(f.registry.articleConnectionCount("789"),2);
            f.registry.close("b");Assert.assertEquals(f.heat.read("123","789").getLong("heat"),4L);
            ArticleChannel.ARTICLE_VIEWS.put("789",Integer.MAX_VALUE);Assert.assertEquals(f.heat.read("123","789").getLong("heat"),2147483648L);
            ArticleChannel.ARTICLE_VIEWS.put("789",-9);Assert.assertEquals(f.heat.read("123","789").getLong("heat"),1L);
            write("UPDATE symphony_api_v3_token SET state=2 WHERE oId='456'");Assert.assertEquals(f.registry.articleConnectionCount("789"),0);Assert.assertTrue(a.closed);Assert.assertFalse(pending.closed);Assert.assertFalse(other.closed);
        }
        System.out.println("PASS article heat sums legacy and authenticated same-target connections, counts duplicate tabs, excludes pending/other channels, handles missing/negative/large counters and removes revoked grants without emitting identity");
        try(var f=new Live()){
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");var a=f.article("a","789");
            write("UPDATE symphony_article SET articleType=?,articleContent='@Reader invited' WHERE oId='789'",org.b3log.symphony.model.Article.ARTICLE_TYPE_C_DISCUSSION);
            Assert.assertEquals(f.heat.read("123","789").getLong("heat"),1L);
            write("UPDATE symphony_article SET articleContent='@SomebodyElse' WHERE oId='789'");Assert.assertEquals(f.http("reader","789",false).attr("test.status"),404);Assert.assertEquals(f.registry.articleConnectionCount("789"),0);Assert.assertTrue(a.closed);
            write("UPDATE symphony_article SET articleType=0,articleStatus=1 WHERE oId='789'");Assert.assertEquals(f.http("reader","789",false).attr("test.status"),404);
            write("UPDATE symphony_article SET articleStatus=0 WHERE oId='789'");write("UPDATE symphony_user SET userStatus=1 WHERE oId='321'");Assert.assertEquals(f.http("reader","789",false).attr("test.status"),404);
            write("UPDATE symphony_user SET userStatus=0 WHERE oId='321'");write("UPDATE symphony_user SET userStatus=4 WHERE oId='123'");Assert.assertEquals(f.http("reader","789",false).attr("test.status"),404);
        }
        System.out.println("PASS current target SQL denies inaccessible discussion, invalid article, blocked author and newly disabled reader before counting; invitation loss closes existing article grants");
        try(var f=new Live()){
            ArticleHeatRouteBindings.register(f.controller,f.auth);Dispatcher.mapping();
            for(String token:Arrays.asList(null,"invalid","wrong"))Assert.assertEquals(f.http(token,"789",true).attr("test.status"),token==null||token.equals("invalid")?401:403);
            var c=f.http("reader","789",true);Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(((JSONObject)c.attr("test.data")).getString("articleId"),"789");Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");
            Assert.assertEquals(f.http("reader","789' OR 1=1",false).attr("test.status"),422);Assert.assertEquals(f.http("reader","999",false).attr("test.status"),404);
            f.fail="target.select";c=f.http("reader","789",false);Assert.assertEquals(c.attr("test.status"),503);Assert.assertNull(c.attr("test.data"));
        }
        System.out.println("PASS actual heat route/controller requires content.read Bearer, ignores Cookie/query owner forgery, bounds article ID, returns no-store whitelist and hides missing/invisible/source-failure metadata");
    }
}
