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

/** Actual strict JdbcTransaction, coordinator, savepoints, order SQL and HTTP route; only DB dialect/token/transport adapted. */
public class ApiV3MedalOrderTest {
    static int actorSequence=100;
    static void inject(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    static JdbcTransaction transaction(Connection connection)throws Exception {
        var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);var tx=(JdbcTransaction)((Unsafe)access.get(null)).allocateInstance(JdbcTransaction.class);
        var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,connection);field=JdbcTransaction.class.getDeclaredField("isActive");field.setAccessible(true);field.setBoolean(tx,true);JdbcRepository.TX.set(tx);return tx;
    }
    static class Fixture implements AutoCloseable {
        final String actor=String.valueOf(++actorSequence);final Path file=Files.createTempFile("rhythm-medal-order-",".sqlite");final String url="jdbc:sqlite:"+file;
        final Connection observer=DriverManager.getConnection(url);Connection writer;JdbcTransaction delegate;
        final ApiV3MedalOrderService service=new ApiV3MedalOrderService();final ApiV3MedalOrderProcessor controller=new ApiV3MedalOrderProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();final ApiV3WriteMidware writes=new ApiV3WriteMidware();
        boolean failWrite,failReceipt,beforeCommit,afterCommit,failSavepointRollback,expandLockedHoldings;int updates,commits,rollbacks,reads;
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
            inject(service,"users",new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...args)throws RepositoryException{return selectLocked(q,args);}});
            inject(service,"medals",new MedalRepository(){public String getName(){return "x_medal";}public List<JSONObject> select(String q,Object...args)throws RepositoryException{return selectLocked(q,args);}});
            inject(service,"grants",new UserMedalRepository(){public String getName(){return "x_user_medal";}public boolean hasTransactionBegun(){return active();}public List<JSONObject> select(String q,Object...args)throws RepositoryException{
                final var rows=q.endsWith(" FOR UPDATE")?selectLocked(q,args):selectRows(q,args);
                if(expandLockedHoldings&&q.endsWith(" FOR UPDATE"))while(rows.size()<4097)rows.add(new JSONObject(rows.getFirst().toString()));
                return rows;
            }});
            inject(service,"requests",receipts);inject(controller,"management",service);
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
                            final String text=args[0].toString();Assert.assertEquals(text,"UPDATE `x_user_medal` SET display_order=? WHERE oId=? AND user_id=? AND medal_id=?");
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
        RequestContext post(String token,String key,String medal,String body)throws Exception{return post(token,key,medal,body,"application/json");}
        RequestContext post(String token,String key,String medal,String body,String type)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/me/medals/"+medal+"/move?userId=2&apiKey=legacy",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{raw.headers().set("X-Real-IP","127.0.0."+(actorSequence%250+1));raw.headers().set("Cookie","legacy");raw.headers().set("Content-Type",type);if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("userId","2","apiKey","legacy")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                // Real Dispatcher, populated from the actual processor route declaration.
                MedalOrderRouteBindings.register(controller,auth,writes);Dispatcher.mapping();
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var resolution=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");Assert.assertEquals(resolution.getContextHandlerMeta().getInvokeHolder().getName(),"move");controller.move(context);});c.handle();return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{if(active())delegate.rollback();JdbcRepository.TX.remove();observer.close();Files.deleteIfExists(file);}
    }
    interface Action{void run()throws Exception;}
    static void error(int code,Action action)throws Exception{try{action.run();Assert.fail("Expected "+code);}catch(ApiV3Exception e){Assert.assertEquals(e.status,code);}}
    static String order(Fixture f)throws Exception{return f.selectFrom(f.observer,"SELECT medal_id,display_order FROM x_user_medal WHERE user_id=? ORDER BY display_order,oId",f.actor).toString();}
    static JSONObject move(Fixture f,String medal,String direction,String key)throws Exception{return f.service.move(f.actor,medal,direction,key);}
    public static void main(String[]args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()) {
            var result=f.post("writer","one","0","{\"direction\":\"DOWN\"}");Assert.assertEquals(result.attr("test.status"),200);var data=(JSONObject)result.attr("test.data");Assert.assertEquals(data.keySet(),Set.of("id","direction","order"));Assert.assertEquals(data.getInt("order"),1);Assert.assertEquals(data.getString("direction"),"down");
            Assert.assertEquals(f.scalar("SELECT display_order FROM x_user_medal WHERE oId='20'"),1L);Assert.assertEquals(f.scalar("SELECT display_order FROM x_user_medal WHERE oId='21'"),0L);
            move(f,"0","up","two");String later=order(f);int updates=f.updates;Assert.assertEquals(move(f,"0","DOWN","one").getInt("order"),1);Assert.assertEquals(order(f),later);Assert.assertEquals(f.updates,updates);error(409,()->move(f,"0","up","one"));
            Assert.assertEquals(f.scalar("SELECT userPoint FROM x_user WHERE oId='"+f.actor+"'"),500L);Assert.assertEquals(f.scalar("SELECT display FROM x_user_medal WHERE oId='20'"),1L);Assert.assertEquals(f.scalar("SELECT display_order FROM x_user_medal WHERE oId='22'"),3L);
            Assert.assertEquals(result.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertFalse(f.active());Assert.assertNull(JdbcRepository.TX.get());
            f.sql("UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);error(404,()->move(f,"0","down","one"));
        }
        System.out.println("PASS 1 actual POST/settings.write/Bearer owner, adjacent swap and complete normalization, order-only fields, semantic replay no repeated movement or newer-state overwrite, inactive owner replay rejected");
        try(var f=new Fixture()) {
            for(String token:Arrays.asList(null,"bad","reader")){int before=f.reads;Assert.assertEquals(f.post(token,"auth","0","{\"direction\":\"up\"}").attr("test.status"),token==null||token.equals("bad")?401:403);Assert.assertEquals(f.reads,before);}
            for(String body:List.of("{}","{\"direction\":null}","{\"direction\":1}","{\"direction\":\" up\"}","{\"direction\":\"left\"}","{\"direction\":\"up\",\"userId\":\"2\"}"))Assert.assertEquals(f.post("writer","bad","0",body).attr("test.status"),422);
            Assert.assertEquals(f.post("writer",null,"0","{\"direction\":\"up\"}").attr("test.status"),422);Assert.assertEquals(f.post("writer","bad key","0","{\"direction\":\"up\"}").attr("test.status"),422);
            Assert.assertEquals(f.post("writer","duplicate","0","{\"direction\":\"up\",\"direction\":\"down\"}").attr("test.status"),400);Assert.assertEquals(f.post("writer","mime","0","{\"direction\":\"up\"}","text/plain").attr("test.status"),415);Assert.assertEquals(f.post("writer","long","a".repeat(129),"{\"direction\":\"up\"}").attr("test.status"),422);
            Assert.assertEquals(f.updates,0);Assert.assertEquals(f.receiptCount(),0L);
        }
        System.out.println("PASS 2 authentication/scope/JSON/field/direction/key/path rejection before SQL writes; Cookie/apiKey/body owner do not elevate identity");
        try(var f=new Fixture()) {
            // Same-order ties resolve by holding oId, including the hidden and expired records.
            f.sql("UPDATE x_user_medal SET display_order=7 WHERE user_id=?",f.actor);f.sql("UPDATE x_user_medal SET display=0,expire_time=1 WHERE oId='21'");
            Assert.assertEquals(move(f,"0","down","hidden-neighbor").getInt("order"),1);Assert.assertEquals(f.scalar("SELECT display_order FROM x_user_medal WHERE oId='21'"),0L);
            Assert.assertEquals(move(f,"alpha-勋章","down","expired-target").getInt("order"),1);Assert.assertEquals(f.scalar("SELECT expire_time FROM x_user_medal WHERE oId='21'"),1L);Assert.assertEquals(f.scalar("SELECT display FROM x_user_medal WHERE oId='21'"),0L);
        }
        try(var f=new Fixture()) {Assert.assertEquals(move(f,"0","up","first-boundary").getInt("order"),0);Assert.assertEquals(f.scalar("SELECT display_order FROM x_user_medal WHERE oId='21'"),1L);int writes=f.updates;move(f,"0","up","stable-boundary");Assert.assertEquals(f.updates,writes);Assert.assertEquals(move(f,"alpha-勋章","down","last-boundary").getInt("order"),1);}
        System.out.println("PASS 3 hidden/expired neighbors and expired target retain legacy membership, deterministic holding-ID ties, boundary still renumbers whole list, no expiration deletion or display mutation");
        for(String sql:List.of("INSERT INTO x_user_medal VALUES('23',ACTOR,'0',1,0,2,'DUP')","INSERT INTO x_medal VALUES('13','0','Duplicate','PRIVATE')","DELETE FROM x_medal WHERE medal_id='alpha-勋章'","UPDATE x_user_medal SET display_order=0.5 WHERE oId='21'","UPDATE x_user_medal SET display_order=2147483648 WHERE oId='21'","UPDATE x_user_medal SET expire_time=0.5 WHERE oId='21'","UPDATE x_user_medal SET display=0.5 WHERE oId='21'"))try(var f=new Fixture()) {
            f.sql(sql.replace("ACTOR","'"+f.actor+"'"));String before=order(f);error(409,()->move(f,"0","down","corrupt"));Assert.assertEquals(order(f),before);Assert.assertEquals(f.receiptCount(),0L);Assert.assertEquals(f.updates,0);
        }
        try(var f=new Fixture()){error(404,()->move(f,"third","up","not-held"));error(404,()->move(f,"missing","up","missing"));Assert.assertEquals(f.receiptCount(),0L);}
        System.out.println("PASS 4 duplicate/missing definitions and holdings, corrupt order/expiry/display fail without writes or receipt; unowned/missing targets404");
        for(String fault:List.of("write","receipt","beforeCommit"))try(var f=new Fixture()) {
            String before=order(f);f.failWrite=fault.equals("write");f.failReceipt=fault.equals("receipt");f.beforeCommit=fault.equals("beforeCommit");error(503,()->move(f,"0","down","fault"));Assert.assertEquals(order(f),before);Assert.assertEquals(f.receiptCount(),0L);Assert.assertFalse(f.active());Assert.assertNull(JdbcRepository.TX.get());f.failWrite=f.failReceipt=f.beforeCommit=false;move(f,"0","down","fault");Assert.assertEquals(f.receiptCount(),1L);
        }
        try(var f=new Fixture()){f.afterCommit=true;error(503,()->move(f,"0","down","ack"));Assert.assertEquals(f.scalar("SELECT display_order FROM x_user_medal WHERE oId='20'"),1L);Assert.assertEquals(f.receiptCount(),1L);int writes=f.updates;f.afterCommit=false;Assert.assertEquals(move(f,"0","down","ack").getInt("order"),1);Assert.assertEquals(f.updates,writes);}
        System.out.println("PASS 5 actual SQL/receipt/strict JdbcTransaction commit faults rollback complete list; committed-but-lost acknowledgement safely replays durable receipt without second move");
        try(var f=new Fixture()) {
            try{f.service.moveInCurrentTransaction(f.actor,"0","down");Assert.fail();}catch(IllegalStateException expected){}String before=order(f);
            var root=f.begin();f.failWrite=true;try{f.service.moveInCurrentTransaction(f.actor,"0","down");Assert.fail();}catch(RepositoryException expected){}f.failWrite=false;root.commit();Assert.assertEquals(order(f),before);
            root=f.begin();f.failWrite=f.failSavepointRollback=true;try{f.service.moveInCurrentTransaction(f.actor,"0","down");Assert.fail();}catch(RepositoryException expected){}Assert.assertFalse(f.active());Assert.assertEquals(order(f),before);Assert.assertNull(JdbcRepository.TX.get());
        }
        System.out.println("PASS 6 actual savepoint restores partial normalization if caller catches; savepoint rollback failure aborts owning root; primitive requires transaction");
        try(var f=new Fixture()) {
            for(int i=0;i<75;i++){String id="bulk-"+i;f.sql("INSERT INTO x_medal VALUES(?,?,'Bulk','PRIVATE')","def-"+i,id);f.sql("INSERT INTO x_user_medal VALUES(?,?,?,1,0,?,'PRIVATE')","holding-"+i,f.actor,id,100+i);}
            Assert.assertEquals(move(f,"bulk-74","up","full").getInt("order"),75);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM x_user_medal WHERE user_id='"+f.actor+"'"),77L);Assert.assertEquals(f.scalar("SELECT MAX(display_order) FROM x_user_medal WHERE user_id='"+f.actor+"'"),76L);Assert.assertEquals(f.scalar("SELECT COUNT(DISTINCT display_order) FROM x_user_medal WHERE user_id='"+f.actor+"'"),77L);
        }
        System.out.println("PASS 7 complete 77-holding list exceeds legacy default page, no truncated neighbor selection or normalization");
        try(var f=new Fixture()) {
            f.sql("CREATE INDEX medal_identifier ON x_medal(medal_id)");
            f.sql("WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i+1 FROM n WHERE i<4093) INSERT INTO x_medal SELECT 'budget-def-'||i,'budget-'||i,'Budget','PRIVATE' FROM n");
            f.sql("WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i+1 FROM n WHERE i<4093) INSERT INTO x_user_medal SELECT 'budget-holding-'||i,?,'budget-'||i,1,0,100+i,'PRIVATE' FROM n",f.actor);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM x_user_medal WHERE user_id='"+f.actor+"'"),4096L);
            move(f,"0","up","at-budget");Assert.assertEquals(f.scalar("SELECT COUNT(DISTINCT display_order) FROM x_user_medal WHERE user_id='"+f.actor+"'"),4096L);
            f.sql("INSERT INTO x_medal VALUES('over-def','over','Budget','PRIVATE')");
            f.sql("INSERT INTO x_user_medal VALUES('over-holding',?,'over',1,0,5000,'PRIVATE')",f.actor);
            String before=order(f);int writes=f.updates;long receipts=f.receiptCount();error(503,()->move(f,"0","down","over-budget"));
            Assert.assertEquals(order(f),before);Assert.assertEquals(f.updates,writes);Assert.assertEquals(f.receiptCount(),receipts);
        }
        try(var f=new Fixture()) {
            String before=order(f);f.expandLockedHoldings=true;error(503,()->move(f,"0","down","current-over-budget"));Assert.assertEquals(order(f),before);Assert.assertEquals(f.updates,0);Assert.assertEquals(f.receiptCount(),0L);
        }
        System.out.println("PASS 8 exact 4096-holding technical budget permits complete normalization; 4097 hint/current holdings fail503 without truncation, order mutation or receipt");
        for(Object value:Arrays.asList(0.5,2147483648L,new java.math.BigInteger("18446744073709551616"),Double.NaN,null,"0"))error(409,()->ApiV3MedalOrderInputs.order(value));Assert.assertEquals(ApiV3MedalOrderInputs.order(Integer.MIN_VALUE),Integer.MIN_VALUE);Assert.assertEquals(ApiV3MedalOrderInputs.direction("DOWN"),"down");
        System.out.println("Medal order isolated tests passed: 8 groups");
    }
}
