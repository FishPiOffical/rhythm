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
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import sun.misc.Unsafe;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.*;

/** Actual SQL/body/auth/write budget/controller/coordinator/savepoint/strict commit; SQLite and Token adapters only. */
public class ApiV3ProfessionPrimaryTest {
    static void inject(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    static int sequence=100;
    static class Fixture implements AutoCloseable {
        final String actor=String.valueOf(++sequence);
        final Path file=Files.createTempFile("rhythm-primary-",".sqlite");
        final String url="jdbc:sqlite:"+file;
        final Connection observer=DriverManager.getConnection(url);
        final ApiV3ProfessionPrimaryService service=new ApiV3ProfessionPrimaryService();
        final ApiV3ProfessionPrimaryProcessor controller=new ApiV3ProfessionPrimaryProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        final ApiV3WriteMidware writes=new ApiV3WriteMidware();
        JdbcTransaction delegate;Connection writer;
        String fault="";boolean raceGrowth,pendingGrowth;int roots,commits,mutations;final List<String> locks=new ArrayList<>();
        Fixture()throws Exception {
            sql(observer,"CREATE TABLE x_user(oId TEXT PRIMARY KEY,userStatus INT,userPoint INT,userPassword TEXT)");
            sql(observer,"CREATE TABLE x_profession(oId TEXT PRIMARY KEY,status TEXT)");
            sql(observer,"CREATE TABLE x_user_profession_privacy(oId TEXT PRIMARY KEY,userId TEXT UNIQUE,onboardingState TEXT,primaryProfessionId TEXT,privacyPreset TEXT,moduleVisibilityJson TEXT,userProgressVersion INT,userPrivacyVersion INT,publicStatsVersion INT,createdAt INT,updatedAt INT)");
            sql(observer,"CREATE TABLE receipts(oId TEXT PRIMARY KEY,value TEXT)");sql(observer,"CREATE TABLE x_user_profession(oId TEXT,userId TEXT,professionId TEXT,totalExperience INT)");sql(observer,"CREATE TABLE unrelated(value INT)");sql(observer,"INSERT INTO unrelated VALUES(1)");
            sql(observer,"INSERT INTO x_user VALUES(?,0,500,'PRIVATE'),('2',0,800,'OTHER'),('4',4,100,'DISABLED')",actor);
            sql(observer,"INSERT INTO x_profession VALUES('10','PUBLISHED'),('11','PUBLISHED'),('12','DRAFT'),('13','RETIRED')");
            sql(observer,"INSERT INTO x_user_profession VALUES('50',?,'10',55)",actor);
            var users=new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("user",q,a);}};
            var professions=new ProfessionRepository(){public String getName(){return "x_profession";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("profession",q,a);}};
            var privacy=new UserProfessionPrivacyRepository(){public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}public String getName(){return "x_user_profession_privacy";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("privacy",q,a);}};
            var receipts=new ApiV3RequestRepository(){
                public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}
                public Transaction beginTransaction(){return begin();}
                public JSONObject getForUpdate(String id)throws RepositoryException {var rows=rows(writer,"SELECT value FROM receipts WHERE oId=?",id);return rows.isEmpty()?null:new JSONObject(rows.getFirst().getString("value"));}
                public String add(JSONObject row)throws RepositoryException {sql(writer,"INSERT INTO receipts VALUES(?,?)",row.getString("oId"),row.toString());if(fault.equals("placeholder"))throw new RepositoryException("PRIVATE_PLACEHOLDER");return row.getString("oId");}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException {sql(writer,"UPDATE receipts SET value=? WHERE oId=?",row.toString(),id);if(fault.equals("receipt"))throw new RepositoryException("PRIVATE_RECEIPT");}
            };
            var idempotency=new ApiV3IdempotencyMgmtService();inject(idempotency,"requestRepository",receipts);
            inject(service,"users",users);inject(service,"professions",professions);inject(service,"privacy",privacy);inject(service,"idempotency",idempotency);inject(controller,"service",service);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("writer","other","reader","disabled").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":token.equals("disabled")?"4":actor)).put("scope",token.equals("reader")?"profile.read":"settings.write"):null;}});
        }
        Transaction begin(){
            try{
                roots++;if(pendingGrowth){pendingGrowth=false;sql(observer,"INSERT INTO x_user_profession_privacy VALUES('62',?,'UNDECIDED','','CUSTOM','GROWTH_PRIVACY',9,4,10,1,2)",actor);}
                final Connection real=DriverManager.getConnection(url);real.setAutoCommit(false);
                writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                    try{
                        if(method.getName().equals("commit")){commits++;if(fault.equals("commit"))throw new SQLException("PRIVATE_COMMIT");real.commit();if(fault.equals("lost-ack"))throw new SQLException("PRIVATE_ACK");return null;}
                        if(method.getName().equals("setSavepoint")&&fault.equals("savepoint"))throw new SQLException("PRIVATE_SAVEPOINT");
                        if(method.getName().equals("releaseSavepoint")&&fault.equals("release"))throw new SQLException("PRIVATE_RELEASE");
                        if(method.getName().equals("rollback")&&args!=null&&args.length==1&&fault.equals("rollback-point"))throw new SQLException("PRIVATE_ROLLBACK_POINT");
                        if(method.getName().equals("prepareStatement")){
                            String q=(String)args[0];var prepared=real.prepareStatement(q.replace(" FOR UPDATE",""));
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                                try{var value=m.invoke(prepared,a);if(m.getName().equals("executeUpdate")&&(q.startsWith("INSERT INTO `x_user_profession_privacy`")||q.startsWith("UPDATE `x_user_profession_privacy`"))){mutations++;if(Set.of("business","rollback-point").contains(fault))throw new SQLException("PRIVATE_BUSINESS");}return value;}catch(InvocationTargetException e){throw e.getCause();}
                            });
                        }
                        return method.invoke(real,args);
                    }catch(InvocationTargetException e){throw e.getCause();}
                });
                var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);delegate=(JdbcTransaction)((Unsafe)access.get(null)).allocateInstance(JdbcTransaction.class);
                var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(delegate,writer);field=JdbcTransaction.class.getDeclaredField("isActive");field.setAccessible(true);field.setBoolean(delegate,true);JdbcRepository.TX.set(delegate);
                return ApiV3Transactions.strict(delegate);
            }catch(Exception e){throw new IllegalStateException(e);}
        }
        List<JSONObject> selectSQL(String stage,String q,Object...args)throws RepositoryException{
            if(stage.equals("user")&&!q.endsWith(" FOR UPDATE")){if(fault.equals("preflight"))throw new RepositoryException("PRIVATE_PREFLIGHT");return rows(observer,q,args);}
            Assert.assertNotNull(JdbcRepository.TX.get());Assert.assertTrue(delegate.isActive());Assert.assertTrue(q.endsWith(" FOR UPDATE"));Assert.assertFalse(q.contains("SELECT *"));locks.add(stage);
            if(fault.equals(stage))throw new RepositoryException("PRIVATE_READ");
            if(stage.equals("privacy")&&raceGrowth){raceGrowth=false;pendingGrowth=true;throw new RepositoryException("simulated competing unique-owner insert");}
            return rows(writer,q,args);
        }
        void seedState()throws Exception{sql(observer,"INSERT INTO x_user_profession_privacy VALUES('60',?,'SKIPPED','13','CUSTOM','KEEP_PRIVATE_JSON',7,8,9,1,2)",actor);}
        JSONObject state()throws RepositoryException {var r=rows(observer,"SELECT * FROM x_user_profession_privacy WHERE userId=?",actor);return r.isEmpty()?null:r.getFirst();}
        long scalar(String q)throws RepositoryException{return rows(observer,q).getFirst().getLong("value");}
        static void sql(Connection c,String q,Object...args)throws RepositoryException{try(var s=c.prepareStatement(q)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}catch(SQLException e){throw new RepositoryException(e);}}
        static List<JSONObject> rows(Connection c,String q,Object...args)throws RepositoryException{try(var s=c.prepareStatement(q.replace(" FOR UPDATE",""))){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var list=new ArrayList<JSONObject>();while(r.next()){var value=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)value.put(r.getMetaData().getColumnLabel(i),r.getObject(i));list.add(value);}return list;}}catch(SQLException e){throw new RepositoryException(e);}}
        RequestContext put(String token,String key,String body,boolean route)throws Exception{
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.PUT,"/api/v3/me/professions/primary?apiKey=legacy&userId=2",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","127.0.0.1");if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("userId","2","apiKey","legacy")));var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                if(route){c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var r=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");Assert.assertEquals(r.getContextHandlerMeta().getInvokeHolder().getName(),"set");controller.set(context);});c.handle();}
                else{c.addHandler(writes::handle);c.addHandler(controller::set);auth.settingsWrite(c);}return c;
            }finally{raw.release();}
        }
        RequestContext put(String key,String profession)throws Exception{return put("writer",key,new JSONObject().put("professionId",profession).toString(),false);}
        public void close()throws Exception{if(delegate!=null&&delegate.isActive())delegate.rollback();JdbcRepository.TX.remove();observer.close();Files.deleteIfExists(file);}
    }
    interface Action{void run()throws Exception;}
    static void error(int code,Action action)throws Exception{try{action.run();Assert.fail("Expected "+code);}catch(ApiV3Exception e){Assert.assertEquals(e.status,code);}}
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            ProfessionPrimaryRouteBindings.register(f.controller,f.auth,f.writes);Dispatcher.mapping();
            var c=f.put("writer","first","{\"professionId\":\"11\"}",true);Assert.assertEquals(c.attr("test.status"),200);var data=(JSONObject)c.attr("test.data");Assert.assertEquals(data.keySet(),Set.of("onboardingState","primaryProfessionId"));Assert.assertEquals(data.getString("primaryProfessionId"),"11");
            Assert.assertEquals(f.locks,List.of("user","profession","privacy"));var state=f.state();Assert.assertEquals(state.getString("onboardingState"),"SELECTED");Assert.assertEquals(state.getString("privacyPreset"),"ALL_PUBLIC");var visibility=new JSONObject(state.getString("moduleVisibilityJson"));Assert.assertEquals(visibility.length(),10);for(String module:visibility.keySet())Assert.assertEquals(visibility.getString(module),"PUBLIC");Assert.assertEquals(state.getLong("userProgressVersion"),1L);Assert.assertEquals(state.getLong("userPrivacyVersion"),0L);Assert.assertEquals(state.getLong("publicStatsVersion"),0L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM x_user_profession WHERE professionId='11'"),0L);Assert.assertEquals(f.scalar("SELECT SUM(totalExperience) AS value FROM x_user_profession"),55L);Assert.assertEquals(f.scalar("SELECT SUM(userPoint) AS value FROM x_user"),1400L);
            Assert.assertEquals(f.put("second","10").attr("test.status"),200);int mutations=f.mutations;Assert.assertEquals(f.put("first","11").attr("test.status"),200);Assert.assertEquals(f.mutations,mutations);Assert.assertEquals(f.state().getString("primaryProfessionId"),"10");Assert.assertEquals(f.state().getLong("userProgressVersion"),2L);Assert.assertEquals(f.put("first","10").attr("test.status"),409);
            sql(f.observer,"UPDATE x_profession SET status='RETIRED' WHERE oId='11'");Assert.assertEquals(f.put("first","11").attr("test.status"),200);Assert.assertEquals(f.state().getString("primaryProfessionId"),"10");
            sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);Assert.assertEquals(f.put("first","11").attr("test.status"),404);Assert.assertEquals(f.state().getString("primaryProfessionId"),"10");
            Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(c.getResponse().getHeader("Vary"),"Authorization");
        }
        System.out.println("PASS actual PUT/auth/controller/coordinator: any published profession without progress, free selection/default state, lock order, version increment and replay preserves later selection");
        try(var f=new Fixture()){
            f.seedState();var old=f.state();Assert.assertEquals(f.put("change","11").attr("test.status"),200);var state=f.state();for(String field:List.of("privacyPreset","moduleVisibilityJson","userPrivacyVersion","publicStatsVersion","createdAt","oId","userId"))Assert.assertEquals(state.get(field),old.get(field));Assert.assertEquals(state.getLong("userProgressVersion"),8L);Assert.assertTrue(state.getLong("updatedAt")>2);
            Assert.assertEquals(f.put("same-selection-new-key","11").attr("test.status"),200);Assert.assertEquals(f.state().getLong("userProgressVersion"),9L);
            sql(f.observer,"UPDATE x_user_profession_privacy SET userProgressVersion=userProgressVersion+1,publicStatsVersion=publicStatsVersion+1 WHERE userId=?",f.actor);Assert.assertEquals(f.put("after-growth","10").attr("test.status"),200);Assert.assertEquals(f.state().getLong("userProgressVersion"),11L);Assert.assertEquals(f.state().getLong("publicStatsVersion"),10L);
            Assert.assertEquals(f.put("other","other-key","{\"professionId\":\"11\"}",false).attr("test.status"),200);Assert.assertEquals(f.state().getString("primaryProfessionId"),"10");Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM x_user_profession_privacy"),2L);
        }
        System.out.println("PASS field-only existing-state updates preserve CUSTOM privacy and counters/timestamps/owner, new-key same selection follows old version rule, committed growth versions retained and actors isolated");
        try(var f=new Fixture()){
            for(String token:Arrays.asList(null,"invalid","reader")){int before=f.roots;Assert.assertEquals(f.put(token,"auth","{\"professionId\":\"11\"}",false).attr("test.status"),token==null||token.equals("invalid")?401:403);Assert.assertEquals(f.roots,before);}
            for(String body:List.of("{}","{\"professionId\":null}","{\"professionId\":11}","{\"professionId\":\" 11 \"}","{\"professionId\":\"11\",\"userId\":\"2\"}","{\"professionId\":\"11\",\"privacyPreset\":\"ALL_PUBLIC\"}","{\"professionId\":\"11\",\"professionId\":\"10\"}","[]","{\"professionId\":\"1' OR 1=1\"}"))Assert.assertNotEquals(f.put("writer","bad",body,false).attr("test.status"),200);
            Assert.assertEquals(f.put("writer",null,"{\"professionId\":\"11\"}",false).attr("test.status"),422);Assert.assertEquals(f.put("writer","bad key","{\"professionId\":\"11\"}",false).attr("test.status"),422);
            for(String id:List.of("12","13","99"))Assert.assertEquals(f.put("target-"+id,id).attr("test.status"),404);
            Assert.assertEquals(f.put("disabled","disabled-key","{\"professionId\":\"11\"}",false).attr("test.status"),404);sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);Assert.assertEquals(f.put("newly-disabled","11").attr("test.status"),404);Assert.assertNull(f.state());Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        System.out.println("PASS missing/invalid/wrong-scope Bearer, Cookie/query identity forgery, body/key/ID validation and absent/draft/retired/currently-disabled targets cannot mutate or retain receipts");
        for(boolean existing:List.of(false,true))for(String fault:List.of("preflight","placeholder","user","profession","privacy","business","receipt","commit","savepoint","release"))try(var f=new Fixture()){
            if(existing)f.seedState();String before=f.state()==null?null:f.state().toString();f.fault=fault;var response=f.put("failure","11");Assert.assertEquals(response.attr("test.status"),503,fault);Assert.assertFalse(String.valueOf(response.attr("test.message")).contains("PRIVATE"));Assert.assertEquals(f.state()==null?null:f.state().toString(),before);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);Assert.assertNull(JdbcRepository.TX.get());
            f.fault="";Assert.assertEquals(f.put("failure","11").attr("test.status"),200);Assert.assertEquals(f.state().getString("primaryProfessionId"),"11");
        }
        System.out.println("PASS actual strict root rollback removes new/existing mutations and receipts for placeholder/read/write/receipt/commit/savepoint/release faults; same-key retry succeeds after recovery");
        try(var f=new Fixture()){
            f.fault="lost-ack";Assert.assertEquals(f.put("ack","11").attr("test.status"),503);Assert.assertEquals(f.state().getLong("userProgressVersion"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),1L);
            f.fault="";Assert.assertEquals(f.put("newer","10").attr("test.status"),200);int mutations=f.mutations;Assert.assertEquals(f.put("ack","11").attr("test.status"),200);Assert.assertEquals(f.mutations,mutations);Assert.assertEquals(f.state().getString("primaryProfessionId"),"10");Assert.assertEquals(f.state().getLong("userProgressVersion"),2L);
        }
        System.out.println("PASS actual JDBC lost commit acknowledgement reports failure, durable same-key recovery replays receipt without duplicate version increments or overwriting later selection");
        try(var f=new Fixture()){
            try{f.service.setInCurrentTransaction(f.actor,"11");Assert.fail();}catch(IllegalStateException expected){}
            var root=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="business";try{f.service.setInCurrentTransaction(f.actor,"11");Assert.fail();}catch(RepositoryException expected){}f.fault="";root.commit();Assert.assertNull(f.state());Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);
            root=f.begin();f.service.setInCurrentTransaction(f.actor,"11");Assert.assertNull(f.state());root.rollback();Assert.assertNull(f.state());
        }
        try(var f=new Fixture()){
            var root=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="rollback-point";try{f.service.setInCurrentTransaction(f.actor,"11");Assert.fail();}catch(RepositoryException expected){}Assert.assertFalse(root.isActive());Assert.assertNull(f.state());Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),1L);Assert.assertNull(JdbcRepository.TX.get());
        }
        System.out.println("PASS primitive requires existing root, never commits caller, savepoint preserves unrelated work after caught failure and rollback-to-savepoint failure aborts whole root");
        try(var f=new Fixture()){
            f.seedState();sql(f.observer,"UPDATE x_user_profession_privacy SET userProgressVersion=? WHERE userId=?",Long.MAX_VALUE,f.actor);Assert.assertEquals(f.put("overflow","11").attr("test.status"),503);Assert.assertEquals(f.state().getString("primaryProfessionId"),"13");Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
            sql(f.observer,"UPDATE x_user_profession_privacy SET userProgressVersion=-1 WHERE userId=?",f.actor);Assert.assertEquals(f.put("negative","11").attr("test.status"),503);
        }
        System.out.println("PASS broken/overflow progress versions fail without changing selection or persisting idempotency result");
        try(var f=new Fixture()){
            f.raceGrowth=true;Assert.assertEquals(f.put("creation-race","11").attr("test.status"),200);Assert.assertEquals(f.roots,2);Assert.assertEquals(f.state().getLong("userProgressVersion"),10L);Assert.assertEquals(f.state().getLong("publicStatsVersion"),10L);Assert.assertEquals(f.state().getLong("userPrivacyVersion"),4L);Assert.assertEquals(f.state().getString("moduleVisibilityJson"),"GROWTH_PRIVACY");Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),1L);
        }
        try(var f=new Fixture()){
            sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);var root=f.begin();error(404,()->f.service.setInCurrentTransaction(f.actor,"11"));root.commit();Assert.assertNull(f.state());
        }
        System.out.println("PASS simulated competing first privacy creation retries a fresh root and preserves committed growth versions; primitive independently rechecks current locked actor status");
    }
    static void sql(Connection c,String q,Object...args)throws RepositoryException{Fixture.sql(c,q,args);}
}
