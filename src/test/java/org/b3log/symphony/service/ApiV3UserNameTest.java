package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.processor.ApiV3UserNameProcessor;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.b3log.symphony.service.ApiV3InvitationPurchaseTest.inject;
import static org.b3log.symphony.service.ApiV3ProfessionPrimaryTest.*;

/** Real shared rename/point/receipt primitives and SQL; external review, transport/cache are adapters. */
public class ApiV3UserNameTest {
    static Object dependency(Object service,String field)throws Exception{var f=service.getClass().getSuperclass().getDeclaredField(field);f.setAccessible(true);return f.get(service);}
    static class F extends ApiV3InvitationPurchaseTest.PurchaseFixture {
        boolean permitted=true,blocked,reviewUnavailable;int reviews,cacheRefresh;String cachedName;
        final ApiV3UserNameService rename=new ApiV3UserNameService(){
            protected boolean allowed(String user){Assert.assertFalse(delegate!=null&&delegate.isActive());return permitted;}
            protected void inspect(String name){Assert.assertFalse(delegate!=null&&delegate.isActive());reviews++;if(blocked)throw new ApiV3Exception(422,"invalid_user_name","blocked");if(reviewUnavailable)throw new IllegalStateException("PRIVATE_REVIEW");}
        };
        final UserNameMgmtService names=new UserNameMgmtService();final ApiV3UserNameProcessor controller=new ApiV3UserNameProcessor();
        F()throws Exception {
            sql(observer,"CREATE TRIGGER rename_ledger_bounds BEFORE INSERT ON transfers WHEN length(NEW.dataId)>128 OR length(NEW.memo)>255 BEGIN SELECT RAISE(ABORT,'ledger column limit'); END");
            sql(observer,"ALTER TABLE x_user ADD COLUMN userName TEXT COLLATE NOCASE DEFAULT ''");sql(observer,"UPDATE x_user SET userName='Old' WHERE oId=?",actor);
            final var owners=new UserRepository(){
                public String getName(){return "x_user";}public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("rename-read",q,a);}
                public JSONObject getForUpdate(String id)throws RepositoryException{var values=select("SELECT * FROM x_user WHERE oId=? FOR UPDATE",id);return values.isEmpty()?null:values.getFirst();}
                public void updateColumnsInCurrentTransaction(String id,JSONObject values)throws RepositoryException{Assert.assertEquals(values.keySet(),Set.of("userName"));exec("UPDATE x_user SET userName=? WHERE oId=?",values.getString("userName"),id);}
                public void evictUserNameCaches(String id,String previous){Assert.assertFalse(hasTransactionBegun());}
                public void refreshSettingsCache(String id)throws RepositoryException{Assert.assertFalse(hasTransactionBegun());cacheRefresh++;cachedName=Fixture.rows(observer,"SELECT userName FROM x_user WHERE oId=?",id).getFirst().getString("userName");}
            };
            inject(names,"users",owners);inject(names,"options",dependency(purchase,"options"));
            inject(rename,"users",owners);inject(rename,"names",names);
            for(String field:List.of("requests","points","transfers"))inject(rename,field,dependency(purchase,field));
            inject(rename,"index",dependency(purchase,"transferIndex"));inject(controller,"settings",rename);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){if(!Set.of("both","activity","settings").contains(token))return null;return new JSONObject().put("user",new JSONObject().put("oId",actor)).put("scope",token.equals("both")?"activity.write settings.write":token+".write");}});
        }
        @Override boolean writeFault(String q){if(fault.equals("rename-write"))return q.startsWith("UPDATE x_user SET userName");if(fault.equals("points-write"))return q.startsWith("UPDATE x_user SET userPoint");return super.writeFault(q);}
        JSONObject change(String key,String name)throws Exception{return rename.rename(actor,new JSONObject().put("userName",name),key);}
        String current()throws Exception{return Fixture.rows(observer,"SELECT userName FROM x_user WHERE oId=?",actor).getFirst().getString("userName");}
        RequestContext http(String token,String key,String body)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.PUT,"/api/v3/me/settings/user-name?apiKey=legacy",io.netty.buffer.Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try {if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);raw.headers().set("Content-Type","application/json");var request=new Request(null,raw){public String getRemoteAddr(){return "192.0.2.1";}};request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy")));var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(controller::rename);c.handle();return c;}finally{raw.release();}
        }
    }
    interface Work{void run()throws Exception;}
    static void rejected(int status,Work work)throws Exception{try{work.run();Assert.fail("expected "+status);}catch(ApiV3Exception error){Assert.assertEquals(error.status,status);}}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new F()) {
            var result=f.change("first","New");Assert.assertEquals(result.keySet(),Set.of("userName","paidPoints"));Assert.assertEquals(result.getInt("paidPoints"),100);Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.current(),"New");Assert.assertEquals(f.cachedName,"New");Assert.assertEquals(f.count("transfers"),1);Assert.assertEquals(f.count("transfer_index"),2);
            f.change("later","Newest");f.permitted=false;f.blocked=true;Assert.assertEquals(f.change("first","New").toString(),result.toString());Assert.assertEquals(f.current(),"Newest");Assert.assertEquals(f.balance(),300);Assert.assertEquals(f.count("transfers"),2);rejected(409,()->f.change("first","Different"));
        }
        System.out.println("PASS rename 1 actual name+price+ledger+receipt SQL commit, post-commit cache/index; retry does not recharge or overwrite a later name even when preflight is blocked");
        for(String fault:List.of("rename-write","points-write","transfer-write","receipt-write","commit"))try(var f=new F()) {
            f.fault=fault;rejected(503,()->f.change("failure","New"));Assert.assertEquals(f.current(),"Old");Assert.assertEquals(f.balance(),500);Assert.assertEquals(f.count("transfers"),0);Assert.assertEquals(f.count("receipts"),0);f.fault="";Assert.assertEquals(f.change("failure","New").getString("userName"),"New");
        }
        System.out.println("PASS rename 2 failures after name, debit, ledger, receipt and strict commit restore all state; same key can recover");
        try(var f=new F()){rejected(409,()->f.change("same","Old"));rejected(409,()->f.change("case","old"));sql(f.observer,"UPDATE x_user SET userPoint=99 WHERE oId=?",f.actor);rejected(409,()->f.change("poor","New"));Assert.assertEquals(f.current(),"Old");Assert.assertEquals(f.count("receipts"),0);}
        try(var f=new F()){f.permitted=false;rejected(429,()->f.change("limited","New"));Assert.assertEquals(f.reviews,0);f.permitted=true;f.blocked=true;rejected(422,()->f.change("blocked","New"));f.blocked=false;f.reviewUnavailable=true;rejected(503,()->f.change("unavailable","New"));Assert.assertEquals(f.balance(),500);}
        for(String name:List.of(""," x","x ","a_b","中","FileTransfer","admin","XADMINx","x".repeat(65),"<x>","x\n"))try(var f=new F()){rejected(422,()->f.change("bad",name));Assert.assertEquals(f.reviews,0);}
        try(var f=new F()){Assert.assertEquals(f.change("max","x".repeat(64)).getString("userName").length(),64);sql(f.observer,"UPDATE x_user SET userStatus=1 WHERE oId=?",f.actor);rejected(404,()->f.change("max","x".repeat(64)));}
        System.out.println("PASS rename 3 unchanged/taken names, balance, exact old ASCII rules, review/profile quota, 64-unit bound and current disabled replay rejection");
        try(var f=new F()){ApiV3UserNameProcessor.registerRoutes(f.controller,f.auth,f.writes);Dispatcher.mapping();for(String token:Arrays.asList(null,"bad","activity","settings"))Assert.assertEquals(f.http(token,"http","{\"userName\":\"New\"}").attr("test.status"),token==null||token.equals("bad")?401:403);Assert.assertEquals(f.http("both",null,"{\"userName\":\"New\"}").attr("test.status"),422);Assert.assertEquals(f.http("both","http","{\"userName\":\"New\"}").attr("test.status"),200);Assert.assertEquals(f.current(),"New");Assert.assertEquals(f.http("both","owner","{\"userName\":\"Other\",\"userId\":\"2\"}").attr("test.status"),422);}
        System.out.println("PASS rename 4 real PUT route requires both scopes, no legacy identity fallback, strict body/key and current actor ownership");
        try(var f=new F()){
            f.fault="lost-ack";rejected(503,()->f.change("ack","New"));Assert.assertEquals(f.current(),"New");Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.cachedName,"New");
            f.fault="";f.permitted=false;Assert.assertEquals(f.change("ack","New").getString("userName"),"New");Assert.assertEquals(f.count("transfers"),1);Assert.assertEquals(f.count("transfer_index"),2);
        }
        try(var f=new F()){f.badIndexParent=true;f.change("index","New");Assert.assertEquals(f.count("transfer_index"),0);f.badIndexParent=false;f.change("index","New");Assert.assertEquals(f.count("transfer_index"),2);Assert.assertEquals(f.count("transfers"),1);}
        try(var f=new F()){f.fault="index-lost-ack";f.change("index-ack","New");f.fault="";f.change("index-ack","New");Assert.assertEquals(f.count("transfer_index"),2);Assert.assertEquals(f.count("transfers"),1);}
        try(var f=new F()){sql(f.observer,"UPDATE x_user SET userUsedPoint=2147483647 WHERE oId=?",f.actor);rejected(503,()->f.change("overflow","New"));Assert.assertEquals(f.current(),"Old");Assert.assertEquals(f.balance(),500);Assert.assertEquals(f.count("transfers"),0);}
        System.out.println("PASS rename 5 lost commit acknowledgement, durable-only parent verification and repeatable index repair preserve a single charge; point arithmetic overflow restores the name");
        for(String name:List.of("-","---","abc-def","n".repeat(64)))try(var f=new F()) {
            final String previous="p".repeat(64);sql(f.observer,"UPDATE x_user SET userName=? WHERE oId=?",previous,f.actor);
            f.change("separate",name);var record=Fixture.rows(f.observer,"SELECT * FROM transfers").getFirst();
            Assert.assertEquals(record.getString("dataId"),name);Assert.assertEquals(record.getString("memo"),previous);
            Assert.assertTrue(record.getString("dataId").length()<=128);Assert.assertTrue(record.getString("memo").length()<=255);
            Assert.assertEquals(UserNamePointData.description(record,"{oldName} -> {newName}"),previous+" -> "+name);
        }
        var unsafe=new JSONObject().put("sourceScene",UserNamePointData.SCENE).put("memo","<b>Old</b>").put("dataId","-");
        Assert.assertEquals(UserNamePointData.description(unsafe,"{oldName} -> {newName}"),Escapes.escapeHTML("<b>Old</b>")+" -> -");
        Assert.assertEquals(UserNamePointData.description(new JSONObject().put("dataId","Old-New"),"{oldName} -> {newName}"),"Old -> New");
        System.out.println("PASS rename 6 ledger consumer renders hyphens and 64-to-64 names without delimiter ambiguity/column overflow; new descriptions encode stored names and preserve legacy rows");
    }
}
