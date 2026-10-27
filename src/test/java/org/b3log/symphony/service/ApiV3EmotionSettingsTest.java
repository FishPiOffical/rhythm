package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
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

/** Actual SQL, controller, Bearer middleware, idempotency, savepoint and strict JDBC commit faults. */
public class ApiV3EmotionSettingsTest extends ApiV3ProfessionPrimaryTest {
    static final String PATH="/api/v3/me/settings/emotions";
    static class EmotionFixture extends Fixture {
        final ApiV3EmotionSettingsService settings=new ApiV3EmotionSettingsService();
        final ApiV3EmotionSettingsProcessor endpoint=new ApiV3EmotionSettingsProcessor();
        final ApiV3EmotionQueryService query=new ApiV3EmotionQueryService();
        boolean deactivateOnBegin;
        EmotionFixture()throws Exception {
            super();
            sql(observer,"CREATE TABLE emotion(oId TEXT PRIMARY KEY,emotionUserId TEXT,emotionContent TEXT,emotionSort INT,emotionType INT)");
            sql(observer,"INSERT INTO emotion VALUES('70',?,'smile',0,0),('71',?,'PRIVATE_OTHER_TYPE',0,1),('72','2','sob',0,0)",actor,actor);
            var users=new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{
                if(q.endsWith(" FOR UPDATE")){locks.add("user");if(fault.equals("user"))throw new RepositoryException("PRIVATE_USER");return rows(writer,q,a);}return rows(observer,q,a);
            }};
            var emotions=new EmotionRepository(){public String getName(){return "emotion";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return rows(observer,q,a);}};
            inject(settings,"users",users);inject(settings,"emotions",emotions);inject(query,"users",users);inject(query,"emotions",emotions);
            var field=ApiV3ProfessionPrimaryService.class.getDeclaredField("idempotency");field.setAccessible(true);var coordinator=field.get(service);
            field=ApiV3IdempotencyMgmtService.class.getDeclaredField("requestRepository");field.setAccessible(true);inject(settings,"requests",field.get(coordinator));inject(endpoint,"service",settings);
        }
        @Override Transaction begin(){
            try {
                if(deactivateOnBegin){deactivateOnBegin=false;sql(observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",actor);}
                var tx=super.begin();var inner=writer;
                writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                    try {
                        if(m.getName().equals("prepareStatement")){
                            String q=(String)a[0];var st=(PreparedStatement)m.invoke(inner,a);
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p2,m2,a2)->{
                                try {var r=m2.invoke(st,a2);if(m2.getName().equals("executeUpdate")&&(q.startsWith("DELETE FROM `emotion`")||q.startsWith("INSERT INTO `emotion`"))){mutations++;if(fault.equals("business")||fault.equals("insert")&&q.startsWith("INSERT")||fault.equals("rollback-point"))throw new SQLException("PRIVATE_WRITE");}return r;}
                                catch(InvocationTargetException e){throw e.getCause();}
                            });
                        }
                        return m.invoke(inner,a);
                    }catch(InvocationTargetException e){throw e.getCause();}
                });
                var f=JdbcTransaction.class.getDeclaredField("connection");f.setAccessible(true);f.set(delegate,writer);return tx;
            }catch(Exception e){throw new IllegalStateException(e);}
        }
        String selected()throws Exception{return query.emotions(actor).getJSONArray("emotions").toString();}
        RequestContext call(String token,String key,String body,boolean routed)throws Exception{
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.PUT,PATH+"?apiKey=legacy&userId=2",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{
                raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","2001:db8::"+actor);
                if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var req=new Request(null,raw);req.setBytes(body.getBytes(StandardCharsets.UTF_8));req.setParams(new HashMap<>(Map.of("userId","2","apiKey","legacy")));
                var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                if(routed){c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(endpoint::update);c.handle();}
                else{c.addHandler(writes::handle);c.addHandler(endpoint::update);auth.settingsWrite(c);}return c;
            }finally{raw.release();}
        }
        RequestContext put(String key,String body)throws Exception{return call("writer",key,body,false);}
    }
    static JSONObject data(RequestContext c){Assert.assertEquals(c.attr("test.status"),200);return (JSONObject)c.attr("test.data");}
    public static void main(String[]args)throws Exception{
        var p=new Properties();p.setProperty("runtimeDatabase","NONE");p.setProperty("contextPath","");Latkes.setLatkeProps(p);Latkes.setLocalProps(p);
        try(var f=new EmotionFixture()){
            EmotionSettingsRouteBindings.register(f.endpoint,f.auth,f.writes);Dispatcher.mapping();
            var out=data(f.call("writer","route","{\"emotions\":[\"sob\",\"+1\",\"smile\"]}",true));
            Assert.assertEquals(out.keySet(),Set.of("emotions"));Assert.assertEquals(out.getJSONArray("emotions").toString(),"[\"sob\",\"+1\",\"smile\"]");Assert.assertEquals(f.selected(),out.getJSONArray("emotions").toString());
            Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emotion WHERE emotionType=1"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emotion WHERE emotionUserId='2'"),1L);Assert.assertEquals(f.scalar("SELECT SUM(userPoint) AS value FROM x_user"),1400L);Assert.assertEquals(f.locks,List.of("user","user"));
        }
        System.out.println("PASS 1 actual Dispatcher PUT/Bearer settings.write/write guard, ordered type-zero replacement, real GET integration and owner/type isolation without charging");
        try(var f=new EmotionFixture()){
            data(f.put("clear","{\"emotions\":[]}"));Assert.assertEquals(f.selected(),ApiV3EmotionOptions.options().getJSONArray("defaults").toString());Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emotion WHERE emotionType=0 AND emotionUserId='"+f.actor+"'"),0L);
            data(f.put("newer","{\"emotions\":[\"joy\"]}"));int writes=f.mutations;var replay=data(f.put("clear","{\"emotions\":[]}"));Assert.assertEquals(replay.getJSONArray("emotions").toString(),ApiV3EmotionOptions.options().getJSONArray("defaults").toString());Assert.assertEquals(f.selected(),"[\"joy\"]");Assert.assertEquals(f.mutations,writes);Assert.assertEquals(f.put("clear","{\"emotions\":[\"joy\"]}").attr("test.status"),409);
            sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);Assert.assertEquals(f.put("clear","{\"emotions\":[]}").attr("test.status"),404);
        }
        System.out.println("PASS 2 empty array physically clears and returns GET defaults; replay returns original white-listed result without overwriting later selection and rejects content conflicts/current disabled owner");
        var all=new JSONArray();for(var alias:ApiV3EmotionSettingsInputs.allowed())all.put(alias);Assert.assertFalse(ApiV3EmotionSettingsInputs.allowed().contains("endOfEmoji"));Assert.assertEquals(ApiV3EmotionSettingsInputs.normalize(new JSONObject().put("emotions",all)).getJSONArray("emotions").length(),all.length());
        for(String b:List.of("{}","{\"emotions\":null}","{\"emotions\":\"smile\"}","{\"emotions\":[null]}","{\"emotions\":[1]}","{\"emotions\":[\"smile\",\"smile\"]}","{\"emotions\":[\" smile\"]}","{\"emotions\":[\"\"]}","{\"emotions\":[\"huaji\"]}","{\"emotions\":[\"endOfEmoji\"]}","{\"emotions\":[],\"owner\":\"2\"}"))error(422,()->ApiV3EmotionSettingsInputs.normalize(new JSONObject(b)));
        try(var f=new EmotionFixture()){
            var full=new JSONObject().put("emotions",new JSONArray(all.toString()));var out=data(f.put("full",full.toString()));Assert.assertEquals(out.getJSONArray("emotions").toString(),all.toString());Assert.assertEquals(f.selected(),all.toString());
        }
        all.put("smile");error(422,()->ApiV3EmotionSettingsInputs.normalize(new JSONObject().put("emotions",all)));
        System.out.println("PASS 3 entire dynamic picker whitelist accepted; blank/duplicate/unknown/full-library-only/sentinel/type/extra fields and over-budget arrays rejected without coercion");
        try(var f=new EmotionFixture()){
            for(String t:Arrays.asList(null,"invalid","reader"))Assert.assertEquals(f.call(t,"auth","{\"emotions\":[]}",false).attr("test.status"),t==null||t.equals("invalid")?401:403);
            for(String b:List.of("[]","{\"emotions\":[],\"emotions\":[\"joy\"]}","{\"emotions\":[],\"userId\":\"2\"}"))Assert.assertEquals(f.put("bad",b).attr("test.status"),b.contains("userId")?422:400);
            Assert.assertEquals(f.call("writer",null,"{\"emotions\":[]}",false).attr("test.status"),422);Assert.assertEquals(f.put("big"," ".repeat(16385)).attr("test.status"),413);Assert.assertEquals(f.put("bigutf8","中".repeat(5462)).attr("test.status"),413);Assert.assertEquals(f.mutations,0);
        }
        System.out.println("PASS 4 missing/wrong Bearer and scope, legacy Cookie/apiKey owner forgery, malformed/duplicate JSON, absent key and UTF-8 body overflow blocked before mutation");
        for(String fault:List.of("placeholder","user","business","insert","receipt","commit","savepoint","release"))try(var f=new EmotionFixture()){
            String before=f.selected();f.fault=fault;Assert.assertEquals(f.put("fault","{\"emotions\":[\"joy\",\"sob\"]}").attr("test.status"),503,fault);Assert.assertEquals(f.selected(),before);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);Assert.assertNull(JdbcRepository.TX.get());f.fault="";data(f.put("fault","{\"emotions\":[\"joy\",\"sob\"]}"));
        }
        System.out.println("PASS 5 actual delete/insert/receipt/root/savepoint/strict commit failures roll back complete selection and receipt; same key recovers");
        try(var f=new EmotionFixture()){
            f.fault="lost-ack";Assert.assertEquals(f.put("ack","{\"emotions\":[\"joy\"]}").attr("test.status"),503);Assert.assertEquals(f.selected(),"[\"joy\"]");f.fault="";data(f.put("later","{\"emotions\":[\"sob\"]}"));int n=f.mutations;data(f.put("ack","{\"emotions\":[\"joy\"]}"));Assert.assertEquals(f.mutations,n);Assert.assertEquals(f.selected(),"[\"sob\"]");
        }
        try(var f=new EmotionFixture()){f.deactivateOnBegin=true;Assert.assertEquals(f.put("race","{\"emotions\":[]}").attr("test.status"),404);Assert.assertEquals(f.mutations,0);}
        System.out.println("PASS 6 committed lost acknowledgement replays safely and owner deactivation between authentication and transaction blocks mutation");
        try(var f=new EmotionFixture()){
            try{f.settings.updateInCurrentTransaction(f.actor,new JSONObject().put("emotions",new JSONArray()));Assert.fail();}catch(IllegalStateException expected){}
            var tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="insert";try{f.settings.updateInCurrentTransaction(f.actor,new JSONObject().put("emotions",new JSONArray().put("joy")));Assert.fail();}catch(RepositoryException expected){}f.fault="";tx.commit();Assert.assertEquals(f.selected(),"[\"smile\"]");Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);
        }
        try(var f=new EmotionFixture()){
            var tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="rollback-point";
            try{f.settings.updateInCurrentTransaction(f.actor,new JSONObject().put("emotions",new JSONArray().put("joy")));Assert.fail();}catch(RepositoryException expected){}
            Assert.assertFalse(tx.isActive());Assert.assertNull(JdbcRepository.TX.get());Assert.assertEquals(f.selected(),"[\"smile\"]");Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),1L);
        }
        System.out.println("PASS 7 transaction primitive requires existing root and savepoint rollback preserves unrelated caller writes while rollback-to-savepoint failure aborts the whole root");
        System.out.println("Emotion settings isolated tests passed: 7 groups");
    }
}
