package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.processor.ApiV3ArticleStickProcessor;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.b3log.symphony.service.ApiV3InvitationPurchaseTest.inject;
import static org.b3log.symphony.service.ApiV3ProfessionPrimaryTest.*;

/** Production stick/point/receipt/HTTP, strict JDBC with explicit repository/cache/SQLite adapters. */
public class ApiV3ArticleStickTest {
    static Object dependency(Object object,String field)throws Exception {
        var f=ApiV3InvitationPurchaseService.class.getDeclaredField(field);f.setAccessible(true);return f.get(object);
    }
    static class F extends ApiV3InvitationPurchaseTest.PurchaseFixture {
        long time=1700000000000L;
        final ApiV3ArticleStickService sticks=new ApiV3ArticleStickService(){protected long now(){return time;}};
        final ApiV3ArticleStickProcessor endpoint=new ApiV3ArticleStickProcessor();
        final ArticleRepository articles=new ArticleRepository(){
            public String getName(){return "x_articles";}
            public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}
            public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("article-read",q,a);}
            public void update(String id,JSONObject row,String...fields)throws RepositoryException {
                Assert.assertTrue(hasTransactionBegun());Assert.assertEquals(List.of(fields),List.of("articleStick"));
                exec("UPDATE x_articles SET articleStick=? WHERE oId=?",row.getLong("articleStick"),id);
            }
        };
        F()throws Exception {
            sql(observer,"CREATE TABLE x_articles(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleStatus INT,articleAnonymousView INT,articleType INT,articleStick BIGINT,articleContent TEXT)");
            sql(observer,"INSERT INTO x_articles VALUES('10',?,0,0,0,0,'KEEP_PRIVATE'),('11',?,0,0,0,0,'OTHER')",actor,actor);
            inject(sticks,"articles",articles);
            for(String field:List.of("users","points","transfers","requests"))inject(sticks,field,dependency(purchase,field));
            inject(sticks,"indexes",dependency(purchase,"transferIndex"));
            inject(endpoint,"sticks",sticks);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token) {
                if(!Set.of("both","content","activity").contains(token))return null;
                return new JSONObject().put("user",viewer()).put("scope",token.equals("both")?"content.write activity.write":token+".write");
            }});
        }
        @Override boolean writeFault(String q){return fault.equals("article-write")&&q.startsWith("UPDATE x_articles")||super.writeFault(q);}
        JSONObject viewer(){return new JSONObject().put("oId",actor);}
        JSONObject stick(String key)throws Exception{return sticks.stick(viewer(),"10",key);}
        boolean expire(long expected,long now,long duration)throws Exception{var tx=begin();try{boolean result=articles.expireStickIfCurrentInCurrentTransaction("10",expected,now,duration);tx.commit();return result;}finally{if(tx.isActive())tx.rollback();}}
        long current()throws Exception{return Fixture.rows(observer,"SELECT articleStick FROM x_articles WHERE oId='10'").getFirst().getLong("articleStick");}
        RequestContext http(String token,String key,String body)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/articles/10/stick?apiKey=legacy",
                    io.netty.buffer.Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try {
                raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");
                if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw){public String getRemoteAddr(){return "192.0.2.4";}};
                request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy")));
                var context=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));
                context.attr("user",new JSONObject().put("oId","2"));context.addHandler(new org.b3log.latke.http.handler.RouteHandler());
                context.addHandler(endpoint::stick);context.handle();return context;
            }finally{raw.release();}
        }
    }
    interface Work{void run()throws Exception;}
    static void rejected(int status,Work work)throws Exception{try{work.run();Assert.fail("expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    public static void main(String[] args)throws Exception {
        var p=new Properties();p.setProperty("runtimeDatabase","NONE");p.setProperty("contextPath","");Latkes.setLatkeProps(p);Latkes.setLocalProps(p);
        try(var f=new F()){
            var first=f.stick("one");Assert.assertEquals(first.keySet(),Set.of("id","stuckAt","paidPoints"));Assert.assertEquals(first.getInt("paidPoints"),100);
            Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.current(),f.time);Assert.assertEquals(f.count("transfers"),1);Assert.assertEquals(f.count("transfer_index"),2);
            long previous=f.time;f.time+=10;f.stick("two");Assert.assertEquals(f.balance(),300);Assert.assertEquals(f.current(),f.time);
            Assert.assertEquals(f.stick("one").getLong("stuckAt"),previous);Assert.assertEquals(f.current(),f.time);Assert.assertEquals(f.count("transfers"),2);
            rejected(409,()->f.sticks.stick(f.viewer(),"11","one"));
            Assert.assertEquals(Fixture.rows(f.observer,"SELECT articleContent FROM x_articles WHERE oId='10'").getFirst().getString("articleContent"),"KEEP_PRIVATE");
        }
        System.out.println("PASS stick 1 timestamp/100-point charge/receipt commit together; new keys refresh, old keys never recharge or overwrite later time; public projection only");
        for(String fault:List.of("article-write","points-write","transfer-write","receipt-write","commit"))try(var f=new F()){
            f.fault=fault;rejected(503,()->f.stick("fault"));Assert.assertEquals(f.current(),0);Assert.assertEquals(f.balance(),500);Assert.assertEquals(f.count("transfers"),0);Assert.assertEquals(f.count("receipts"),0);
            f.fault="";f.stick("fault");Assert.assertEquals(f.current(),f.time);Assert.assertEquals(f.balance(),400);
        }
        System.out.println("PASS stick 2 failure after article, balance, ledger, receipt or commit rolls back all durable states; original key recovers");
        try(var f=new F()){
            sql(f.observer,"UPDATE x_articles SET articleAuthorId='2' WHERE oId='10'");rejected(404,()->f.stick("foreign"));
            sql(f.observer,"UPDATE x_articles SET articleAuthorId=? WHERE oId='10'",f.actor);
            sql(f.observer,"UPDATE x_articles SET articleStatus=? WHERE oId='10'",Article.ARTICLE_STATUS_C_LOCKED);rejected(404,()->f.stick("locked"));
            sql(f.observer,"UPDATE x_articles SET articleStatus=0,articleStick=? WHERE oId='10'",Long.MAX_VALUE);rejected(409,()->f.stick("permanent"));
            sql(f.observer,"UPDATE x_articles SET articleStick=0 WHERE oId='10'");sql(f.observer,"UPDATE x_user SET userPoint=99 WHERE oId=?",f.actor);rejected(409,()->f.stick("poor"));
            Assert.assertEquals(f.current(),0);Assert.assertEquals(f.count("transfers"),0);Assert.assertEquals(f.count("receipts"),0);
        }
        try(var f=new F()){f.stick("replay");sql(f.observer,"UPDATE x_user SET userStatus=1 WHERE oId=?",f.actor);rejected(404,()->f.stick("replay"));}
        try(var f=new F()){var result=f.stick("removed");sql(f.observer,"DELETE FROM x_articles WHERE oId='10'");Assert.assertEquals(f.stick("removed").toString(),result.toString());rejected(404,()->f.stick("new-removed"));Assert.assertEquals(f.count("transfers"),1);}
        System.out.println("PASS stick 3 current actor/ownership/valid status, balance and permanent administrator stick enforced including replay");
        try(var f=new F()){
            ApiV3ArticleStickProcessor.registerRoutes(f.endpoint,f.auth,f.writes);Dispatcher.mapping();
            for(String token:Arrays.asList(null,"bad","content","activity"))Assert.assertEquals(f.http(token,"http","{}").attr("test.status"),token==null||token.equals("bad")?401:403);
            Assert.assertEquals(f.http("both",null,"{}").attr("test.status"),422);
            Assert.assertEquals(f.http("both","forged","{\"userId\":\"2\"}").attr("test.status"),422);
            Assert.assertEquals(f.http("both","http","{}").attr("test.status"),200);Assert.assertEquals(f.balance(),400);
        }
        System.out.println("PASS stick 4 actual POST registration requires both scopes/key and empty strict body; Cookie/query/forged user do not authorize");
        try(var f=new F()){
            f.fault="lost-ack";rejected(503,()->f.stick("ack"));Assert.assertEquals(f.current(),f.time);Assert.assertEquals(f.balance(),400);
            f.fault="";f.stick("ack");Assert.assertEquals(f.count("transfers"),1);Assert.assertEquals(f.count("transfer_index"),2);
        }
        try(var f=new F()){f.badIndexParent=true;f.stick("index");Assert.assertEquals(f.count("transfer_index"),0);f.badIndexParent=false;f.stick("index");Assert.assertEquals(f.count("transfer_index"),2);Assert.assertEquals(f.count("transfers"),1);}
        try(var f=new F()){sql(f.observer,"UPDATE x_user SET userUsedPoint=2147483647 WHERE oId=?",f.actor);rejected(503,()->f.stick("overflow"));Assert.assertEquals(f.current(),0);Assert.assertEquals(f.balance(),500);}
        System.out.println("PASS stick 5 committed lost acknowledgement replays one charge, index repair verifies parent and overflow rolls back timestamp");
        try(var f=new F()){
            long old=f.time-10000;sql(f.observer,"UPDATE x_articles SET articleStick=? WHERE oId='10'",old);f.stick("renew");
            Assert.assertFalse(f.expire(old,f.time+10,1000));Assert.assertEquals(f.current(),f.time);
            sql(f.observer,"UPDATE x_articles SET articleStick=? WHERE oId='10'",Long.MAX_VALUE);
            Assert.assertFalse(f.expire(old,f.time+10,1000));Assert.assertEquals(f.current(),Long.MAX_VALUE);
            sql(f.observer,"UPDATE x_articles SET articleStick=? WHERE oId='10'",old);
            Assert.assertTrue(f.expire(old,f.time,1000));Assert.assertEquals(f.current(),0);
            Assert.assertEquals(Fixture.rows(f.observer,"SELECT articleContent FROM x_articles WHERE oId='10'").getFirst().getString("articleContent"),"KEEP_PRIVATE");
        }
        System.out.println("PASS stick 6 actual expiry helper current-lock recheck preserves renewed/permanent sticks and only expires unchanged old time");
    }
}
