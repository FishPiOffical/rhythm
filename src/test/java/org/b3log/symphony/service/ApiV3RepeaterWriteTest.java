package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;

/** Real publication capture and like SQL, strict commits, replay, legacy toggle and middleware routes. */
public class ApiV3RepeaterWriteTest extends ApiV3ProfessionPrimaryTest {
    static class RepeaterFixture extends Fixture {
        final ApiV3RepeaterPublicationService publication=new ApiV3RepeaterPublicationService();
        final ApiV3RepeaterLikeService like=new ApiV3RepeaterLikeService();
        final RepeaterLikeMutationService mutation=new RepeaterLikeMutationService();
        final RepeaterMgmtService legacy=new RepeaterMgmtService();
        final ApiV3RepeaterPublicationProcessor post=new ApiV3RepeaterPublicationProcessor();
        final ApiV3RepeaterLikeProcessor likes=new ApiV3RepeaterLikeProcessor();
        final RepeaterContentRepository contents;
        boolean deactivateOnBegin;
        RepeaterFixture()throws Exception{
            super();
            sql(observer,"CREATE TABLE repeater_content(oId TEXT PRIMARY KEY,repeaterContentType TEXT,repeaterContent TEXT,repeaterContentAuthorId TEXT,repeaterContentSource TEXT,repeaterContentStatus INT,repeaterContentLikeCount INT,repeaterContentCreatedTime INT,repeaterContentUpdatedTime INT)");
            sql(observer,"CREATE TABLE repeater_like(oId TEXT PRIMARY KEY,repeaterLikeContentId TEXT,repeaterLikeUserId TEXT,repeaterLikeCreatedTime INT)");
            sql(observer,"CREATE TABLE source_events(oId TEXT PRIMARY KEY,value TEXT)");
            sql(observer,"INSERT INTO repeater_content VALUES('10','joke','seed','2','seed',0,99,1,1),('11','fish','removed','2','user',1,0,1,1)");
            var users=new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("user",q,a);}};
            contents=new RepeaterContentRepository(){
                public String getName(){return "repeater_content";}public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}public Transaction beginTransaction(){return begin();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("target",q,a);}
                public String add(JSONObject row)throws RepositoryException{insertJson("repeater_content",row);return row.getString("oId");}
            };
            var likeRows=new RepeaterLikeRepository(){public String getName(){return "repeater_like";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{if(q.contains("COUNT(*)"))Assert.assertTrue(q.endsWith(" FOR UPDATE"),"count must be a current locking read, never an existing RR snapshot");if(q.contains(" FOR UPDATE"))return selectSQL("like",q,a);throw new AssertionError("non-locking like read");}};
            var events=new ProfessionSourceEventRepository(){public String add(JSONObject row)throws RepositoryException{sql(writer,"INSERT INTO source_events VALUES(?,?)",row.getString("oId"),row.toString());if(fault.equals("capture"))throw new RepositoryException("PRIVATE_CAPTURE");return row.getString("oId");}};
            var capture=new ProfessionSourceEventCaptureService();inject(capture,"sourceEventRepository",events);
            var field=ApiV3ProfessionPrimaryService.class.getDeclaredField("idempotency");field.setAccessible(true);var coordinator=field.get(service);field=ApiV3IdempotencyMgmtService.class.getDeclaredField("requestRepository");field.setAccessible(true);var receipts=field.get(coordinator);
            inject(publication,"users",users);inject(publication,"contents",contents);inject(publication,"capture",capture);inject(publication,"requests",receipts);
            inject(mutation,"users",users);inject(mutation,"contents",contents);inject(mutation,"likes",likeRows);
            inject(like,"users",users);inject(like,"requests",receipts);inject(like,"mutation",mutation);
            inject(legacy,"repeaterContentRepository",contents);inject(legacy,"repeaterLikeMutationService",mutation);
            inject(post,"service",publication);inject(likes,"service",like);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("writer","other","reader").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":actor)).put("scope",token.equals("reader")?"content.read":"content.write"):null;}});
        }
        void insertJson(String table,JSONObject row)throws RepositoryException{
            var keys=new ArrayList<>(row.keySet());sql(writer,"INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(row::get).toArray());if(fault.equals("publication"))throw new RepositoryException("PRIVATE_PUBLICATION");
        }
        @Override Transaction begin(){
            try{
                if(deactivateOnBegin){deactivateOnBegin=false;sql(observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",actor);}
                var tx=super.begin();var inner=writer;
                writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                    try{
                        if(m.getName().equals("prepareStatement")){
                            String q=(String)a[0];var st=(PreparedStatement)m.invoke(inner,a);
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p2,m2,a2)->{
                                try{var out=m2.invoke(st,a2);if(m2.getName().equals("executeUpdate")&&(q.contains("`repeater_like`")||q.contains("`repeater_content`"))){mutations++;if(fault.equals("business")||fault.equals("rollback-point"))throw new SQLException("PRIVATE_WRITE");}return out;}catch(InvocationTargetException e){throw e.getCause();}
                            });
                        }
                        return m.invoke(inner,a);
                    }catch(InvocationTargetException e){throw e.getCause();}
                });
                var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(delegate,writer);return tx;
            }catch(Exception e){throw new IllegalStateException(e);}
        }
        RequestContext call(String method,String target,String token,String key,String body,boolean route)throws Exception{
            String uri="/api/v3/repeater/items"+(method.equals("POST")?"":"/"+target+"/like");
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.valueOf(method),uri+"?apiKey=legacy",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{
                raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","2001:db8::"+actor);
                if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                if(route){c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(ctx->{var resolution=(org.b3log.latke.http.handler.RouteResolution)ctx.attr("MATCH_RESULT");Assert.assertEquals(resolution.getContextHandlerMeta().getInvokeHolder().getName(),method.equals("POST")?"publish":method.equals("PUT")?"like":"unlike");if(method.equals("POST"))post.publish(ctx);else if(method.equals("PUT"))likes.like(ctx);else likes.unlike(ctx);});c.handle();}
                else{var pathField=RequestContext.class.getDeclaredField("pathVars");pathField.setAccessible(true);pathField.set(c,Map.of("itemId",target));c.addHandler(writes::handle);c.addHandler(ctx->{if(method.equals("POST"))post.publish(ctx);else if(method.equals("PUT"))likes.like(ctx);else likes.unlike(ctx);});auth.contentWrite(c);}
                return c;
            }finally{raw.release();}
        }
        RequestContext post(String key,String content)throws Exception{return call("POST","","writer",key,new JSONObject().put("type","joke").put("content",content).toString(),true);}
        RequestContext set(boolean liked,String key)throws Exception{return call(liked?"PUT":"DELETE","10","writer",key,"",true);}
    }
    static JSONObject data(RequestContext c){Assert.assertEquals(c.attr("test.status"),200);return (JSONObject)c.attr("test.data");}
    public static void main(String[]args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        // Route handlers bind mutable fixture references below; mapping is rebuilt for each fresh fixture.
        try(var f=new RepeaterFixture()){
            bind(f);var out=data(f.post("post","  <b>Hello</b>\n world  "));Assert.assertEquals(out.keySet(),Set.of("id","type","createdTime"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_content"),3L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM source_events"),1L);
            var row=Fixture.rows(f.observer,"SELECT * FROM repeater_content WHERE oId=?",out.getString("id")).getFirst();Assert.assertEquals(row.getString("repeaterContent"),"Hello world");Assert.assertEquals(row.getString("repeaterContentAuthorId"),f.actor);
            var event=new JSONObject(Fixture.rows(f.observer,"SELECT value FROM source_events").getFirst().getString("value"));Assert.assertEquals(event.getString("eventType"),"repeater.published");Assert.assertEquals(event.getString("targetId"),out.getString("id"));Assert.assertEquals(event.getString("subjectUserId"),f.actor);Assert.assertFalse(event.getString("payloadJson").contains("Hello"));Assert.assertFalse(Fixture.rows(f.observer,"SELECT * FROM receipts").toString().contains("Hello"));
            data(f.post("post","<b>Hello</b> world"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM source_events"),1L);Assert.assertEquals(f.post("post","different").attr("test.status"),409);
        }
        System.out.println("PASS 1 actual Dispatcher POST/content.write and typed text normalization; real profession capture, content and body-free receipt share a strict root and replay");
        try(var f=new RepeaterFixture()){
            bind(f);Assert.assertTrue(data(f.set(true,"like")).getBoolean("liked"));Assert.assertTrue(f.locks.indexOf("target")<f.locks.indexOf("like"),"target must lock before any actor like/current count read");Assert.assertEquals(data(f.set(true,"like-again")).getLong("likeCount"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_like"),1L);
            Assert.assertFalse(data(f.set(false,"unlike")).getBoolean("liked"));data(f.set(false,"unlike-again"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_like"),0L);
            data(f.set(true,"like"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_like"),0L);Assert.assertEquals(f.set(false,"like").attr("test.status"),409);
            var result=f.legacy.toggleLike(f.actor,"10");Assert.assertEquals(result.keySet(),Set.of("liked","repeaterContentLikeCount"));Assert.assertTrue(result.getBoolean("liked"));data(f.set(true,"after-old"));Assert.assertFalse(f.legacy.toggleLike(f.actor,"10").getBoolean("liked"));Assert.assertEquals(f.scalar("SELECT repeaterContentLikeCount AS value FROM repeater_content WHERE oId='10'"),0L);
        }
        System.out.println("PASS 2 actual PUT/DELETE set semantics, same-key replay never overwrites later state, key conflict, real legacy toggle and preserved legacy response names");
        try(var f=new RepeaterFixture()){
            bind(f);sql(f.observer,"INSERT INTO repeater_like VALUES('20','10',?,1),('21','10',?,1),('22','10','2',1),('23','10','2',1)",f.actor,f.actor);
            Assert.assertEquals(data(f.set(true,"dedupe")).getLong("likeCount"),3L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_like WHERE repeaterLikeUserId='2'"),2L);Assert.assertEquals(data(f.set(false,"remove")).getLong("likeCount"),2L);
            sql(f.observer,"INSERT INTO repeater_like VALUES('24','10',?,1),('25','10',?,1)",f.actor,f.actor);Assert.assertFalse(f.legacy.toggleLike(f.actor,"10").getBoolean("liked"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_like"),2L);
        }
        System.out.println("PASS 3 only actor historical duplicates converge to one/zero; other users' rows remain, count is actual remaining rows and legacy clears actor duplicates atomically");
        for(String fault:List.of("placeholder","user","publication","capture","receipt","commit","savepoint","release"))try(var f=new RepeaterFixture()){
            bind(f);f.fault=fault;Assert.assertEquals(f.post("fault","valid text").attr("test.status"),503,fault);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_content"),2L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM source_events"),0L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);f.fault="";data(f.post("fault","valid text"));
        }
        for(String fault:List.of("placeholder","user","target","like","business","receipt","commit","savepoint","release"))try(var f=new RepeaterFixture()){
            bind(f);f.fault=fault;Assert.assertEquals(f.set(true,"fault").attr("test.status"),503,fault);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_like"),0L);Assert.assertEquals(f.scalar("SELECT repeaterContentLikeCount AS value FROM repeater_content WHERE oId='10'"),99L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);f.fault="";data(f.set(true,"fault"));
        }
        System.out.println("PASS 4 publication/capture and like/count/receipt/read/savepoint/release/strict-commit faults leave no partial state; same key succeeds after recovery");
        for(String fault:List.of("user","target","like","business","commit","savepoint","release"))try(var f=new RepeaterFixture()){
            f.fault=fault;try{f.legacy.toggleLike(f.actor,"10");Assert.fail();}catch(org.b3log.latke.service.ServiceException expected){}Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_like"),0L);Assert.assertEquals(f.scalar("SELECT repeaterContentLikeCount AS value FROM repeater_content WHERE oId='10'"),99L);
        }
        try(var f=new RepeaterFixture()){var tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");try{f.legacy.toggleLike(f.actor,"10");Assert.fail();}catch(org.b3log.latke.service.ServiceException expected){}Assert.assertTrue(tx.isActive());tx.rollback();Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),1L);}
        System.out.println("PASS 5 real legacy method uses strict commit and rejects nesting without committing an unrelated caller root");
        try(var f=new RepeaterFixture()){
            bind(f);for(String method:List.of("POST","PUT","DELETE"))for(String token:Arrays.asList(null,"bad","reader"))Assert.assertEquals(f.call(method,"10",token,"auth","{}",true).attr("test.status"),token==null||token.equals("bad")?401:403);
            for(String body:List.of("{}","[]","{\"type\":\"joke\",\"content\":\"valid\",\"owner\":\"2\"}","{\"type\":\"joke\",\"content\":\"valid\",\"content\":\"other\"}"))Assert.assertNotEquals(f.call("POST","","writer","bad",body,true).attr("test.status"),200);
            Assert.assertEquals(f.call("PUT","10","writer","bad","{\"liked\":false}",true).attr("test.status"),422);Assert.assertEquals(f.call("DELETE","10","writer",null,"",true).attr("test.status"),422);
            Assert.assertEquals(f.call("POST","","writer","large","中".repeat(6000),true).attr("test.status"),413);Assert.assertEquals(f.call("PUT","11","writer","removed","",true).attr("test.status"),404);Assert.assertEquals(f.call("PUT","99","writer","absent","",true).attr("test.status"),404);
            sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);Assert.assertEquals(f.set(true,"disabled").attr("test.status"),404);Assert.assertEquals(f.post("disabled-post","valid text").attr("test.status"),404);
        }
        for(String type:List.of("joke","kfc","fish"))Assert.assertEquals(ApiV3RepeaterPublicationInputs.normalize(new JSONObject().put("type",type).put("content","中".repeat(500))).getString("content").length(),500);
        for(Object text:List.of("x","中".repeat(501),1,"\uD800","\u0000ok"))error(422,()->ApiV3RepeaterPublicationInputs.normalize(new JSONObject().put("type","joke").put("content",text)));
        System.out.println("PASS 6 missing/wrong Bearer/scope, Cookie/apiKey owner forgery, malformed/duplicate/extra JSON, missing key, body/Unicode/text boundaries and unavailable actor/targets rejected");
        try(var f=new RepeaterFixture()){
            bind(f);f.fault="lost-ack";Assert.assertEquals(f.post("ack","valid text").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM source_events"),1L);f.fault="";data(f.post("ack","valid text"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM source_events"),1L);
            f.fault="lost-ack";Assert.assertEquals(f.set(true,"ack-like").attr("test.status"),503);f.fault="";data(f.set(false,"newer"));data(f.set(true,"ack-like"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_like"),0L);
        }
        try(var f=new RepeaterFixture()){bind(f);f.deactivateOnBegin=true;Assert.assertEquals(f.set(true,"race").attr("test.status"),404);Assert.assertEquals(f.mutations,0);}
        System.out.println("PASS 7 lost commit acknowledgements replay publication once and preserve later unlike; actor deactivation after auth blocks writes");
        try(var f=new RepeaterFixture()){
            try{f.publication.publishInCurrentTransaction(f.actor,new JSONObject().put("type","fish").put("content","valid"));Assert.fail();}catch(IllegalStateException expected){}
            try{f.mutation.setInCurrentTransaction(f.actor,"10",true);Assert.fail();}catch(IllegalStateException expected){}
            var tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="capture";try{f.publication.publishInCurrentTransaction(f.actor,new JSONObject().put("type","fish").put("content","valid"));Assert.fail();}catch(RepositoryException expected){}f.fault="";tx.commit();Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM source_events"),0L);Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);
            tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=3");f.fault="rollback-point";try{f.mutation.setInCurrentTransaction(f.actor,"10",true);Assert.fail();}catch(RepositoryException expected){}Assert.assertFalse(tx.isActive());Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);
        }
        System.out.println("PASS 8 primitives require root and savepoints protect caller writes; savepoint rollback failure aborts whole root");
        try(var f=new RepeaterFixture()){
            bind(f);try(var st=f.observer.prepareStatement("INSERT INTO repeater_like VALUES(?,'10',?,1)")){for(int i=0;i<4097;i++){st.setString(1,"r"+i);st.setString(2,f.actor);st.addBatch();}st.executeBatch();}
            Assert.assertEquals(f.set(false,"budget").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM repeater_like"),4097L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        System.out.println("PASS 9 excessive duplicate history fails whole operation without truncated cleanup or receipt");
        System.out.println("Repeater writes isolated tests passed: 9 groups");
    }
    static void bind(RepeaterFixture f)throws Exception{RepeaterWriteRouteBindings.publication(f.post,f.auth,f.writes);RepeaterWriteRouteBindings.likes(f.likes,f.auth,f.writes);Dispatcher.mapping();}
}
