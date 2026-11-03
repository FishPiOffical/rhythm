package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.model.User;
import org.b3log.symphony.processor.ApiV3ChatProcessor;
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.b3log.symphony.service.LogsService;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Real v3 handler/auth and extracted old adapter through the actual shared mutation; transport/token issuance are adapted. */
public class ChatMessageWithdrawalEndpointTest {
    static class Endpoint extends ChatMessageWithdrawalTest.Withdrawal {
        final ApiV3ChatProcessor controller=new ApiV3ChatProcessor();
        final ChatroomProcessor legacy=new ChatroomProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        String scope="content.write";
        Endpoint()throws Exception{
            inject(controller,ApiV3ChatProcessor.class,"withdrawals",withdrawals);inject(legacy,ChatroomProcessor.class,"chatMessageWithdrawals",withdrawals);
            inject(auth,ApiV3AuthMidware.class,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){@Override public JSONObject authenticate(String token){return "VALID".equals(token)?new JSONObject().put("scope",scope).put("user",new JSONObject().put("oId","1")):null;}});
        }
        JSONObject request(boolean old,String token,String type,String body,String key,String id,JSONObject user)throws Exception{
            var netty=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.DELETE,(old?"/chat-room/revoke/":"/api/v3/chat/messages/")+id+"?apiKey=conflicting-query");
            var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
            try{
                netty.headers().set("Cookie","JSESSIONID=legacy");if(token!=null)netty.headers().set("Authorization",token);if(type!=null)netty.headers().set("Content-Type",type);if(key!=null)netty.headers().set("Idempotency-Key",key);
                final var request=new Request(null,netty);netty.content().clear().writeBytes(body.getBytes(StandardCharsets.UTF_8));request.setBytes(body.getBytes(StandardCharsets.UTF_8));final var context=new RequestContext(request,new Response(null,response));context.pathVar(old?"oId":"messageId",id);context.attr(User.USER,user);
                if(old){legacy.revokeMessage(context);return ((JsonRenderer)context.getRenderer()).getJSONObject();}
                context.addHandler(controller::withdraw);auth.contentWrite(context);
                final var result=new JSONObject().put("status",context.attr("test.status"));if(context.attr("test.code")!=null)result.put("code",context.attr("test.code"));if(context.attr("test.data")!=null)result.put("data",context.attr("test.data"));
                if(result.getInt("status")==200){Assert.assertEquals(response.headers().get("Cache-Control"),"no-store");Assert.assertEquals(response.headers().get("Vary"),"Authorization");}return result;
            }finally{netty.release();}
        }
        JSONObject modern(String token,String type,String body,String key,String id)throws Exception{return request(false,token,type,body,key,id,new JSONObject().put("oId","2"));}
        JSONObject old(String id)throws Exception{return request(true,null,"application/json","{\"apiKey\":\"conflicting-body\"}",null,id,new JSONObject().put("oId","1").put("userName","Renamed"));}
    }
    public static void main(String[] args)throws Exception{
        final var properties=new Properties();properties.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(properties);Latkes.setLocalProps(properties);Latkes.setServerScheme("http");Latkes.setServerHost("localhost");Latkes.setServerPort("8080");Latkes.setContextPath("");
        try(var f=new Endpoint()){
            f.seed(1);String before=f.all();Assert.assertEquals(f.modern(null,null,"","key","123").getInt("status"),401);f.scope="content.read";Assert.assertEquals(f.modern("Bearer VALID",null,"","key","123").getInt("status"),403);f.scope="content.write";
            Assert.assertEquals(f.modern("Bearer VALID",null,"",null,"123").getInt("status"),422);
            Assert.assertEquals(f.modern("Bearer VALID",null,"","key","bad").getInt("status"),422);
            Assert.assertEquals(f.modern("Bearer VALID","text/plain","{}","key","123").getInt("status"),415);
            Assert.assertEquals(f.modern("Bearer VALID","application/json","{bad}","key","123").getInt("status"),400);
            for(String body:List.of("{\"apiKey\":\"old\"}","{\"userId\":\"2\"}","{\"cost\":0}"))Assert.assertEquals(f.modern("Bearer VALID","application/json",body,"key","123").getInt("status"),422);
            Assert.assertEquals(f.all(),before);final var result=f.modern("Bearer VALID",null,"","key","123");Assert.assertEquals(result.getInt("status"),200);Assert.assertEquals(result.getJSONObject("data").getInt("cost"),0);
            Assert.assertEquals(f.modern("Bearer VALID","application/json","{}","key","123").toString(),result.toString());Assert.assertEquals(f.delivered,1);
            Assert.assertEquals(f.modern("Bearer VALID",null,"","different","123").getInt("status"),404);
        }
        System.out.println("PASS actual v3 withdrawal handler requires content.write Bearer/key, empty or empty-JSON body and numeric ID, rejects forged cost/identity and Cookie/apiKey fallback, then returns/replays the committed receipt with private headers");
        try(var f=new Endpoint()){
            LogsService.calls=0;f.seed(1);final var first=f.old("123");Assert.assertEquals(first.getInt("code"),0);Assert.assertTrue(first.getString("msg").endsWith("本次消耗积分: 0"));Assert.assertEquals(LogsService.calls,1);
            exec(f.writer,"INSERT INTO chat VALUES('124',?)",new JSONObject().put("userOId","1").put("userName","BeforeRename").put("content","second").toString());
            Assert.assertEquals(f.modern("Bearer VALID",null,"","second","124").getJSONObject("data").getInt("cost"),32);
            exec(f.writer,"INSERT INTO chat VALUES('125',?)",new JSONObject().put("userOId","1").put("content","third").toString());Assert.assertTrue(f.old("125").getString("msg").endsWith("本次消耗积分: 64"));Assert.assertEquals(LogsService.calls,2);
            String after=f.all();Assert.assertEquals(f.old("125").getInt("code"),-1);Assert.assertEquals(f.all(),after);
            Assert.assertEquals(f.request(true,null,null,"",null,"123",null).getInt("code"),-1);
        }
        System.out.println("PASS extracted old controller trusts loginCheck's current user over conflicting query/body keys, preserves success code/msg and audits only after commit; old-v3-old withdrawals share 0/32/64");
        try(var f=new Endpoint()){
            f.seed(1);exec(f.writer,"UPDATE user SET userRole='adminRole'");exec(f.writer,"UPDATE chat SET content=?",new JSONObject().put("userOId","2").put("userName","Renamed").put("content","other").toString());String before=f.all();
            Assert.assertEquals(f.modern("Bearer VALID",null,"","owner-only","123").getInt("status"),404);Assert.assertEquals(f.all(),before);
            Assert.assertEquals(f.old("123").getString("msg"),"撤回成功。");Assert.assertTrue(query(f.reader,"SELECT * FROM transfers").isEmpty());
        }
        try(var f=new Endpoint()){
            f.seed(1);f.counter("{\"day\":\"2026-10-18\",\"count\":1}");exec(f.writer,"UPDATE user SET userPoint=31");String before=f.all();Assert.assertEquals(f.old("123").getString("msg"),"少年，你的积分不足！要为自己的言行负责~");Assert.assertEquals(f.all(),before);
        }
        System.out.println("PASS v3 remains owner-only even for operators while the old operator moderation response stays unchanged; old insufficient-points errors preserve every row");
    }
}
