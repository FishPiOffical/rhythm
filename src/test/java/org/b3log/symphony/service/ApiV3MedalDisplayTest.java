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
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Actual strict JdbcTransaction, coordinator, savepoints, display SQL and HTTP route; only DB dialect/token/transport adapted. */
public class ApiV3MedalDisplayTest {
    static int actorSequence=100;
    static void inject(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    static JdbcTransaction transaction(Connection connection)throws Exception {
        var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);var tx=(JdbcTransaction)((Unsafe)access.get(null)).allocateInstance(JdbcTransaction.class);
        var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,connection);field=JdbcTransaction.class.getDeclaredField("isActive");field.setAccessible(true);field.setBoolean(tx,true);JdbcRepository.TX.set(tx);return tx;
    }
    static class Fixture implements AutoCloseable {
        final String actor=String.valueOf(++actorSequence);final Path file=Files.createTempFile("rhythm-medal-display-",".sqlite");final String url="jdbc:sqlite:"+file;
        final Connection observer=DriverManager.getConnection(url);Connection writer;JdbcTransaction delegate;
        final ApiV3MedalDisplayService service=new ApiV3MedalDisplayService();final ApiV3MedalDisplayProcessor controller=new ApiV3MedalDisplayProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();final ApiV3WriteMidware writes=new ApiV3WriteMidware();
        boolean failWrite,failReceipt,beforeCommit,afterCommit,failSavepointRollback;int updates,commits,rollbacks,reads;
        final ApiV3RequestRepository receipts=new ApiV3RequestRepository(){
            public boolean hasTransactionBegun(){return active();}
            public Transaction beginTransaction(){return begin();}
            public JSONObject getForUpdate(String id)throws RepositoryException{var rows=selectRows("SELECT value FROM receipt WHERE oId=?",id);return rows.isEmpty()?null:new JSONObject(rows.getFirst().getString("value"));}
            public String add(JSONObject row)throws RepositoryException{sql(writer,"INSERT INTO receipt VALUES(?,?)",row.getString("oId"),row.toString());return row.getString("oId");}
            public void update(String id,JSONObject row,String...fields)throws RepositoryException{sql(writer,"UPDATE receipt SET value=? WHERE oId=?",row.toString(),id);if(failReceipt)throw new RepositoryException("PRIVATE_RECEIPT_FAILURE");}
        };
        Fixture()throws Exception {
            sql(observer,"CREATE TABLE x_user(oId TEXT PRIMARY KEY,userStatus INT,userPassword TEXT,userPoint INT)");
            sql(observer,"INSERT INTO x_user VALUES(?,0,'PRIVATE_PASSWORD',500),('2',0,'OTHER_PASSWORD',700)",actor);
            sql(observer,"CREATE TABLE x_medal(oId TEXT PRIMARY KEY,medal_id TEXT,medal_name TEXT,medal_attr TEXT)");
            sql(observer,"INSERT INTO x_medal VALUES('10','0','First','PRIVATE_ATTR'),('11','alpha-勋章','Second','OTHER_PRIVATE_ATTR'),('12','third','Third','PRIVATE_ATTR')");
            sql(observer,"CREATE TABLE x_user_medal(oId TEXT PRIMARY KEY,user_id TEXT,medal_id TEXT,display INT,expire_time INTEGER,display_order INT,data TEXT)");
            sql(observer,"INSERT INTO x_user_medal VALUES('20',?,'0',1,0,7,'PRIVATE_GRANT_DATA'),('21',?,'alpha-勋章',1,0,9,'PRIVATE_GRANT_DATA'),('22','2','0',1,0,3,'OTHER_PRIVATE_DATA')",actor,actor);
            sql(observer,"CREATE TABLE receipt(oId TEXT PRIMARY KEY,value TEXT)");
            inject(service,"users",new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...args)throws RepositoryException{if(q.endsWith(" FOR UPDATE"))return selectLocked(q,args);Assert.assertFalse(active());Assert.assertEquals(q,"SELECT oId FROM `x_user` WHERE oId=? AND userStatus=?");return selectFrom(observer,q,args);}});
            inject(service,"medals",new MedalRepository(){public String getName(){return "x_medal";}public List<JSONObject> select(String q,Object...args)throws RepositoryException{return selectLocked(q,args);}});
            inject(service,"grants",new UserMedalRepository(){public String getName(){return "x_user_medal";}public boolean hasTransactionBegun(){return active();}public List<JSONObject> select(String q,Object...args)throws RepositoryException{return selectLocked(q,args);}});
            var coordinator=new ApiV3IdempotencyMgmtService();inject(coordinator,"requestRepository",receipts);inject(service,"idempotency",coordinator);inject(controller,"management",service);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("writer","other","reader").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":actor)).put("scope",token.equals("reader")?"profile.read":"settings.write"):null;}});
        }
        boolean active(){return delegate!=null&&delegate.isActive();}
        Transaction begin(){
            try{
                final var real=DriverManager.getConnection(url);real.setAutoCommit(false);
                writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                    try{
                        if(method.getName().equals("commit")){commits++;if(beforeCommit)throw new SQLException("PRIVATE_BEFORE_COMMIT");real.commit();if(afterCommit)throw new SQLException("PRIVATE_AFTER_COMMIT");return null;}
                        if(method.getName().equals("rollback")){if(args!=null&&args.length==1&&failSavepointRollback)throw new SQLException("PRIVATE_SAVEPOINT_ROLLBACK");rollbacks++;}
                        if(method.getName().equals("prepareStatement")&&args[0].toString().startsWith("UPDATE `x_user_medal`")){
                            final String text=args[0].toString();Assert.assertEquals(text,"UPDATE `x_user_medal` SET display=? WHERE oId=? AND user_id=? AND medal_id=?");
                            final var statement=(PreparedStatement)method.invoke(real,args);
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{try{if(m.getName().equals("executeUpdate")){updates++;int result=statement.executeUpdate();if(failWrite)throw new SQLException("PRIVATE_WRITE_FAILURE");return result;}return m.invoke(statement,a);}catch(InvocationTargetException e){throw e.getCause();}});
                        }
                        return method.invoke(real,args);
                    }catch(InvocationTargetException e){throw e.getCause();}
                });delegate=transaction(writer);return ApiV3Transactions.strict(delegate);
            }catch(Exception e){throw new IllegalStateException(e);}
        }
        List<JSONObject> selectLocked(String text,Object...args)throws RepositoryException{Assert.assertTrue(active());Assert.assertTrue(text.endsWith(" FOR UPDATE"));Assert.assertFalse(text.contains("SELECT *"));Assert.assertFalse(text.contains("medal_attr"));return selectRows(text.substring(0,text.length()-11),args);}
        List<JSONObject> selectRows(String text,Object...args)throws RepositoryException{return selectFrom(writer,text,args);}
        List<JSONObject> selectFrom(Connection db,String text,Object...args)throws RepositoryException{reads++;try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var rows=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));rows.add(row);}return rows;}}catch(SQLException e){throw new RepositoryException(e);}}
        static void sql(Connection db,String text,Object...args)throws RepositoryException{try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}catch(SQLException e){throw new RepositoryException(e);}}
        void sql(String text,Object...args)throws RepositoryException{sql(observer,text,args);}
        long scalar(String text)throws Exception{try(var s=observer.createStatement();var r=s.executeQuery(text)){r.next();return r.getLong(1);}}
        long displayed()throws Exception{return scalar("SELECT display FROM x_user_medal WHERE oId='20'");}
        long receiptCount()throws Exception{return scalar("SELECT COUNT(*) FROM receipt");}
        RequestContext put(String token,String key,String medal,String body)throws Exception{return put(token,key,medal,body,"application/json");}
        RequestContext put(String token,String key,String medal,String body,String type)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.PUT,"/api/v3/me/medals/"+medal+"/display?userId=2&apiKey=legacy",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{raw.headers().set("X-Real-IP","127.0.0."+(actorSequence%250+1));raw.headers().set("Cookie","legacy");raw.headers().set("Content-Type",type);if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("userId","2","apiKey","legacy")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                // Real Dispatcher, populated from the actual processor route declaration.
                MedalDisplayRouteBindings.register(controller,auth,writes);Dispatcher.mapping();
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var resolution=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");Assert.assertEquals(resolution.getContextHandlerMeta().getInvokeHolder().getName(),"display");controller.display(context);});c.handle();return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{if(active())delegate.rollback();JdbcRepository.TX.remove();observer.close();Files.deleteIfExists(file);}
    }
    interface Action{void run()throws Exception;}
    static void error(int code,Action action)throws Exception{try{action.run();Assert.fail("Expected "+code);}catch(ApiV3Exception e){Assert.assertEquals(e.status,code);}}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            var result=f.put("writer","first","0","{\"displayed\":false}");Assert.assertEquals(result.attr("test.status"),200);Assert.assertEquals(((JSONObject)result.attr("test.data")).keySet(),Set.of("id","displayed"));Assert.assertFalse(((JSONObject)result.attr("test.data")).getBoolean("displayed"));Assert.assertEquals(f.displayed(),0L);
            Assert.assertEquals(f.put("writer","second","0","{\"displayed\":true}").attr("test.status"),200);int updates=f.updates;
            Assert.assertFalse(((JSONObject)f.put("writer","first","0","{\"displayed\":false}").attr("test.data")).getBoolean("displayed"));Assert.assertEquals(f.displayed(),1L);Assert.assertEquals(f.updates,updates);
            Assert.assertEquals(f.put("writer","first","0","{\"displayed\":true}").attr("test.status"),409);Assert.assertEquals(f.receiptCount(),2L);
            Assert.assertEquals(f.put("writer","nonnumeric","alpha-%E5%8B%8B%E7%AB%A0","{\"displayed\":false}").attr("test.status"),200);
            Assert.assertEquals(f.put("writer","first","alpha-%E5%8B%8B%E7%AB%A0","{\"displayed\":false}").attr("test.status"),409);
            Assert.assertEquals(f.scalar("SELECT userPoint FROM x_user WHERE oId='"+f.actor+"'"),500L);Assert.assertEquals(f.scalar("SELECT display_order FROM x_user_medal WHERE oId='20'"),7L);Assert.assertEquals(f.scalar("SELECT display FROM x_user_medal WHERE oId='22'"),1L);
            Assert.assertEquals(result.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertFalse(f.active());Assert.assertNull(JdbcRepository.TX.get());
            f.sql("UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);error(404,()->f.service.set(f.actor,"0",false,"first"));
            f.sql("UPDATE x_user SET userStatus=0 WHERE oId=?",f.actor);
            f.sql("UPDATE x_user_medal SET expire_time=1 WHERE oId='20'");Assert.assertFalse(f.service.set(f.actor,"0",false,"first").getBoolean("displayed"));Assert.assertEquals(f.displayed(),1L);
            f.sql("DELETE FROM x_medal WHERE medal_id='0'");Assert.assertFalse(f.service.set(f.actor,"0",false,"first").getBoolean("displayed"));Assert.assertEquals(f.displayed(),1L);
            f.sql("DELETE FROM x_user WHERE oId=?",f.actor);error(404,()->f.service.set(f.actor,"0",false,"first"));


        }
        System.out.println("PASS actual PUT/SQL/strict coordinator: explicit setting, zero and nonnumeric ID, Bearer owner, only display changes, replay does not overwrite later state, target/body conflict409");
        try(var f=new Fixture()){
            for(String token:Arrays.asList(null,"bad","reader")){int before=f.reads;Assert.assertEquals(f.put(token,"auth","0","{\"displayed\":false}").attr("test.status"),token==null||token.equals("bad")?401:403);Assert.assertEquals(f.reads,before);}
            for(String body:List.of("{}","{\"displayed\":null}","{\"displayed\":1}","{\"displayed\":\"false\"}","{\"displayed\":false,\"userId\":\"2\"}"))Assert.assertEquals(f.put("writer","invalid","0",body).attr("test.status"),422);
            Assert.assertEquals(f.put("writer",null,"0","{\"displayed\":false}").attr("test.status"),422);Assert.assertEquals(f.put("writer","bad key","0","{\"displayed\":false}").attr("test.status"),422);
            Assert.assertEquals(f.put("writer","json","0","{\"displayed\":true,\"displayed\":false}").attr("test.status"),400);Assert.assertEquals(f.put("writer","mime","0","{\"displayed\":false}","text/plain").attr("test.status"),415);
            Assert.assertEquals(f.put("writer","too-long","a".repeat(129),"{\"displayed\":false}").attr("test.status"),422);Assert.assertEquals(f.updates,0);Assert.assertEquals(f.receiptCount(),0L);
        }
        System.out.println("PASS auth/scope/JSON/key/field/body validation before mutation; Cookie/apiKey/userId cannot supply owner and malformed requests leave no receipt");
        try(var f=new Fixture()){Assert.assertTrue(f.service.set(f.actor,"0",true,"already-shown").getBoolean("displayed"));Assert.assertEquals(f.updates,0);Assert.assertEquals(f.receiptCount(),1L);}

        try(var f=new Fixture()){
            error(404,()->f.service.set(f.actor,"third",false,"not-held"));error(404,()->f.service.set(f.actor,"missing",false,"missing-definition"));
            f.sql("UPDATE x_user_medal SET expire_time=1 WHERE oId='20'");error(404,()->f.service.set(f.actor,"0",false,"expired"));
            f.sql("UPDATE x_user_medal SET expire_time=-1 WHERE oId='20'");Assert.assertFalse(f.service.set(f.actor,"0",false,"negative").getBoolean("displayed"));
            f.sql("UPDATE x_user_medal SET display=1,expire_time=0 WHERE oId='20'");
            f.sql("INSERT INTO x_user_medal VALUES('23',?,'0',1,0,8,'DUPLICATE')",f.actor);error(409,()->f.service.set(f.actor,"0",false,"duplicate-holding"));Assert.assertEquals(f.displayed(),1L);f.sql("DELETE FROM x_user_medal WHERE oId='23'");
            f.sql("INSERT INTO x_medal VALUES('13','0','Duplicate','PRIVATE')");error(409,()->f.service.set(f.actor,"0",false,"duplicate-definition"));f.sql("DELETE FROM x_medal WHERE oId='13'");
            f.sql("UPDATE x_user_medal SET display=0.5 WHERE oId='20'");error(409,()->f.service.set(f.actor,"0",false,"corrupt-display"));
            f.sql("UPDATE x_user_medal SET display=1,expire_time=0.5 WHERE oId='20'");error(409,()->f.service.set(f.actor,"0",false,"corrupt-expiry"));
            f.sql("UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);error(404,()->f.service.set(f.actor,"0",false,"deactivated"));Assert.assertEquals(f.receiptCount(),1L);
        }
        System.out.println("PASS locked current actor/definition/owned-grant checks: missing, expired and inactive404; duplicate definitions/holdings409 without random writes; negative expiry retains owner permanent rule");
        for(String fault:List.of("write","receipt","beforeCommit"))try(var f=new Fixture()){
            f.failWrite=fault.equals("write");f.failReceipt=fault.equals("receipt");f.beforeCommit=fault.equals("beforeCommit");error(503,()->f.service.set(f.actor,"0",false,"fault"));Assert.assertEquals(f.displayed(),1L);Assert.assertEquals(f.receiptCount(),0L);Assert.assertFalse(f.active());Assert.assertNull(JdbcRepository.TX.get());
            f.failWrite=f.failReceipt=f.beforeCommit=false;Assert.assertFalse(f.service.set(f.actor,"0",false,"fault").getBoolean("displayed"));Assert.assertEquals(f.displayed(),0L);Assert.assertEquals(f.receiptCount(),1L);
        }
        try(var f=new Fixture()){
            f.afterCommit=true;error(503,()->f.service.set(f.actor,"0",false,"lost-ack"));Assert.assertEquals(f.displayed(),0L);Assert.assertEquals(f.receiptCount(),1L);int updates=f.updates;
            f.afterCommit=false;Assert.assertFalse(f.service.set(f.actor,"0",false,"lost-ack").getBoolean("displayed"));Assert.assertEquals(f.updates,updates);Assert.assertEquals(f.receiptCount(),1L);
        }
        System.out.println("PASS real bundled JdbcTransaction strict commit: update/receipt/precommit faults roll back both; lost commit acknowledgement preserves durable receipt and same-key recovery replays without mutation");
        try(var f=new Fixture()){
            try{f.service.setInCurrentTransaction(f.actor,"0",false);Assert.fail();}catch(IllegalStateException expected){}
            var root=f.begin();f.failWrite=true;try{f.service.setInCurrentTransaction(f.actor,"0",false);Assert.fail();}catch(RepositoryException expected){}f.failWrite=false;root.commit();Assert.assertEquals(f.displayed(),1L);Assert.assertEquals(f.receiptCount(),0L);
            root=f.begin();f.failWrite=f.failSavepointRollback=true;try{f.service.setInCurrentTransaction(f.actor,"0",false);Assert.fail();}catch(RepositoryException expected){}Assert.assertFalse(f.active());Assert.assertEquals(f.displayed(),1L);Assert.assertNull(JdbcRepository.TX.get());
        }
        System.out.println("PASS actual savepoint restores partial update even if caller catches; failed local rollback aborts the owning transaction; primitive requires active caller transaction");
        for(Object value:Arrays.asList(0.5,1.5,4294967297L,new java.math.BigInteger("18446744073709551616"),Double.NaN,Double.POSITIVE_INFINITY,null,JSONObject.NULL))error(409,()->ApiV3MedalDisplay.stored(value));
        for(Object value:Arrays.asList(0.5,new java.math.BigInteger("18446744073709551616"),Double.NaN,"0",null))error(409,()->ApiV3MedalDisplay.expiry(value));
        Assert.assertTrue(ApiV3MedalDisplay.stored(1L));Assert.assertFalse(ApiV3MedalDisplay.stored(0));Assert.assertTrue(ApiV3MedalDisplay.stored(true));Assert.assertEquals(ApiV3MedalDisplay.expiry(Long.MIN_VALUE),Long.MIN_VALUE);
        error(422,()->ApiV3MedalDisplay.medalId(String.valueOf((char)0xD800)));
        error(422,()->ApiV3MedalDisplay.medalId(String.valueOf((char)0xDC00)));
        Assert.assertEquals(ApiV3MedalDisplay.medalId(new String(Character.toChars(0x1F41F))),new String(Character.toChars(0x1F41F)));
        System.out.println("PASS corrupt fractional/overflow/NaN display and expiry values rejected409 without truncation; normal JDBC Boolean/integer and signed BIGINT preserved");

    }
}
