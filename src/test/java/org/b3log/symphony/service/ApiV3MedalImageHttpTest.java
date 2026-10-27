package org.b3log.symphony.service;
import org.b3log.latke.http.*;import org.b3log.symphony.processor.*;import org.b3log.symphony.processor.middleware.*;import org.json.*;import org.testng.Assert;import io.netty.handler.codec.http.*;import java.util.*;
public class ApiV3MedalImageHttpTest {
    static RequestContext request(ApiV3MedalImageProcessor controller,String token,String path,Map<String,String> params)throws Exception{
        var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,path);raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
        try{
            var request=new Request(null,raw){public String getRemoteAddr(){return "203.0.113.17";}};var map=new HashMap<>(params);map.putIfAbsent("medalId","7");map.put("apiKey","legacy");request.setParams(map);
            final byte[][] sent={null};var response=new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)){public void sendBytes(byte[] bytes){sent[0]=bytes;}};
            var c=new RequestContext(request,response);c.attr("user",new JSONObject().put("oId","2"));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(ctx->{var r=(org.b3log.latke.http.handler.RouteResolution)ctx.attr("MATCH_RESULT");if(r.getContextHandlerMeta().getInvokeHolder().getName().equals("mine"))controller.mine(ctx);else controller.displayed(ctx);});c.handle();if(sent[0]!=null)c.attr("image",sent[0]);return c;
        }finally{raw.release();}
    }
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");org.b3log.latke.Latkes.setLatkeProps(props);org.b3log.latke.Latkes.setLocalProps(props);
        try(var f=new ApiV3MedalImageTest.Fixture()){
            var service=new ApiV3MedalImageService(){protected byte[] render(String p){return ApiV3MedalImageTest.bytes(ApiV3MedalImageTest.SVG);}};
            var field=ApiV3MedalImageService.class.getDeclaredField("query");field.setAccessible(true);field.set(service,f.query);
            var controller=new ApiV3MedalImageProcessor();ApiV3MedalImageTest.inject(controller,"images",service);
            var auth=new ApiV3AuthMidware();ApiV3MedalImageTest.inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("reader","other","wrong").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":"1")).put("scope",token.equals("wrong")?"content.read":"profile.read"):null;}});
            MedalImageRouteBindings.register(controller,auth);Dispatcher.mapping();
            for(String path:List.of("/api/v3/me/medals/image","/api/v3/users/Owner/medals/image")){
                for(String token:new String[]{null,"bad","wrong"})Assert.assertEquals(request(controller,token,path,Map.of()).attr("test.status"),"wrong".equals(token)?403:401);
            }
            var own=request(controller,"reader","/api/v3/me/medals/image",Map.of());Assert.assertNotNull(own.attr("image"));Assert.assertEquals(own.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(own.getResponse().getHeader("Vary"),"Authorization");Assert.assertEquals(own.getResponse().getHeader("X-Content-Type-Options"),"nosniff");Assert.assertTrue(own.getResponse().getHeader("Content-Security-Policy").contains("sandbox"));
            Assert.assertEquals(request(controller,"other","/api/v3/me/medals/image",Map.of()).attr("test.status"),404);
            Assert.assertEquals(request(controller,"other","/api/v3/users/Owner/medals/image",Map.of()).attr("test.status"),404);
            f.sql("UPDATE g SET display=1");Assert.assertNotNull(request(controller,"other","/api/v3/users/Owner/medals/image",Map.of("size","mini")).attr("image"));
            Assert.assertEquals(request(controller,"reader","/api/v3/me/medals/image",Map.of("size","other")).attr("test.status"),422);
            f.sql("INSERT INTO m VALUES ('A /中文','Unicode medal','')");f.sql("INSERT INTO g VALUES ('1','A /中文',0,0)");
            Assert.assertNotNull(request(controller,"reader","/api/v3/me/medals/image",Map.of("medalId","A /中文")).attr("image"));
            f.sql("INSERT INTO m VALUES ('key%2F+literal','Literal medal','')");f.sql("INSERT INTO g VALUES ('1','key%2F+literal',0,0)");
            Assert.assertNotNull(request(controller,"reader","/api/v3/me/medals/image",Map.of("medalId","key%2F+literal")).attr("image"));
            Assert.assertEquals(request(controller,"reader","/api/v3/me/medals/image",Map.of("medalId","bad\nkey")).attr("test.status"),422);
            f.fail=true;var failure=request(controller,"reader","/api/v3/me/medals/image",Map.of());Assert.assertEquals(failure.attr("test.status"),503);Assert.assertNull(failure.attr("image"));
        }
        System.out.println("PASS medal image HTTP actual Dispatcher/auth/controller owner/public/scope/Cookie/apiKey/hidden/mini/headers/error boundary (isolated SQLite and renderer)");
    }
}
