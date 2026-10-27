package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;

/** Actual purchase/ledger/option SQL, account locks, strict JDBC and HTTP/auth handlers with SQLite adapters. */
public class ApiV3InvitationPurchaseTest extends ApiV3ProfessionPrimaryTest {
    static void inject(Object target,String name,Object value)throws Exception{Class<?> t=target.getClass();while(t!=null){try{var f=t.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException e){t=t.getSuperclass();}}throw new NoSuchFieldException(name);}
    static class PurchaseFixture extends Fixture {
        String generated="AbCdEf0123456789",generatedId;int generatedCount;boolean badIndexParent;
        final ApiV3InvitationPurchaseService purchase=new ApiV3InvitationPurchaseService(){protected String newCode(){generatedCount++;return generated;}protected String newId(){return generatedId==null?super.newId():generatedId;}};
        final ApiV3InvitationPurchaseProcessor endpoint=new ApiV3InvitationPurchaseProcessor();
        PurchaseFixture()throws Exception {
            sql(observer,"ALTER TABLE x_user ADD COLUMN userUsedPoint INT DEFAULT 0");
            sql(observer,"CREATE TABLE options(oId TEXT PRIMARY KEY,optionValue TEXT)");
            sql(observer,"INSERT INTO options VALUES(?, '100'),(?, '2')",Option.ID_C_STATISTIC_MEMBER_COUNT,Option.ID_C_MISC_ALLOW_REGISTER);
            sql(observer,"CREATE TABLE invites(oId TEXT PRIMARY KEY,code TEXT COLLATE NOCASE,generatorId TEXT,userId TEXT,useTime INT,status INT,memo TEXT)");
            sql(observer,"CREATE TABLE transfers(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,sum INT,fromBalance INT,toBalance INT,time INT,type INT,dataId TEXT,memo TEXT,sourceAppId TEXT,sourceAppName TEXT,sourceScene TEXT,sourceRequestId TEXT)");
            sql(observer,"CREATE TABLE transfer_index(oId TEXT PRIMARY KEY,userId TEXT,rawId TEXT)");
            sql(observer,"CREATE INDEX idx_transfer_owner ON transfer_index(rawId,userId)");
            final var userRows=new UserRepository(){
                public String getName(){return "x_user";}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("user",q,a);}
                public JSONObject getForUpdate(String id)throws RepositoryException{var r=query("user","SELECT * FROM x_user WHERE oId=? FOR UPDATE",id);return r.isEmpty()?null:r.getFirst();}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException{Assert.assertTrue(ArticlePublicationCaches.active());exec("UPDATE x_user SET userPoint=?,userUsedPoint=? WHERE oId=?",row.getInt("userPoint"),row.optInt("userUsedPoint"),id);}
            };
            final var optionRows=new OptionRepository(){
                public String getName(){return "options";}public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{locks.add(a[0].equals(Option.ID_C_STATISTIC_MEMBER_COUNT)?"namespace":"option");return query("option",q,a);}
            };
            final var transferRows=new PointtransferRepository(){
                public String getName(){return "transfers";}public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}public Transaction beginTransaction(){return begin();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{if(badIndexParent)return List.of();return query("post-index",q,a);}
                public String add(JSONObject row)throws RepositoryException{String id=org.b3log.latke.util.Ids.genTimeMillisId();row.put("oId",id);insert("transfers",row);return id;}
            };
            final var requestRows=new ApiV3RequestRepository(){
                public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}public Transaction beginTransaction(){return begin();}
                public JSONObject getForUpdate(String id)throws RepositoryException{var r=query("receipt-read","SELECT value FROM receipts WHERE oId=?",id);return r.isEmpty()?null:new JSONObject(r.getFirst().getString("value"));}
                public String add(JSONObject row)throws RepositoryException{exec("INSERT INTO receipts VALUES(?,?)",row.getString("oId"),row.toString());return row.getString("oId");}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException{exec("UPDATE receipts SET value=? WHERE oId=?",row.toString(),id);}
            };
            final var pointService=new PointtransferMgmtService(){public void indexTransferAsync(String from,String to,String raw){throw new AssertionError("unexpected precommit/append-only index");}};
            inject(pointService,"pointtransferRepository",transferRows);inject(pointService,"userRepository",userRows);
            inject(purchase,"users",userRows);inject(purchase,"options",optionRows);inject(purchase,"points",pointService);inject(purchase,"transfers",transferRows);inject(purchase,"requests",requestRows);
            inject(purchase,"invitations",new InvitecodeRepository(){public String getName(){return "invites";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("invitation",q,a);}});
            inject(purchase,"transferIndex",new PointtransferRedcRepository(){public String getName(){return "transfer_index";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("post-index",q,a);}});
            inject(endpoint,"service",purchase);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("writer","reader","other").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":actor)).put("scope",token.equals("reader")?"profile.read":"activity.write"):null;}});
        }
        @Override Transaction begin(){
            Assert.assertTrue(AccountPairLocks.lock(actor,"sys").isHeldByCurrentThread() || roots>0,"purchase prelocks before SQL; index follows completed root");
            Transaction tx=super.begin();final Connection previous=writer;
            writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                try{
                    if(method.getName().equals("prepareStatement")){String q=(String)args[0];final var ps=(PreparedStatement)method.invoke(previous,args);return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                        try{Object r=m.invoke(ps,a);if(m.getName().equals("executeUpdate")&&writeFault(q))throw new SQLException("PRIVATE_WRITE");return r;}catch(InvocationTargetException e){throw e.getCause();}});}
                    final Object result=method.invoke(previous,args);
                    if(method.getName().equals("commit") && fault.equals("index-lost-ack") && roots>1) throw new SQLException("PRIVATE_INDEX_ACK");
                    return result;
                }catch(InvocationTargetException e){throw e.getCause();}
            });
            try{var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(delegate,writer);}catch(Exception e){throw new IllegalStateException(e);}return tx;
        }
        boolean writeFault(String q){return fault.equals("points-write")&&q.startsWith("UPDATE x_user") || fault.equals("transfer-write")&&q.startsWith("INSERT INTO transfers")
                || fault.equals("invitation-write")&&q.startsWith("INSERT INTO `invites`") || fault.equals("receipt-write")&&q.startsWith("UPDATE receipts")
                || fault.equals("post-index-write")&&q.startsWith("INSERT INTO `transfer_index`");}
        void exec(String q,Object...a)throws RepositoryException{sql(writer,q,a);}
        void insert(String table,JSONObject row)throws RepositoryException{var fields=new ArrayList<>(row.keySet());exec("INSERT INTO "+table+" ("+String.join(",",fields)+") VALUES ("+String.join(",",Collections.nCopies(fields.size(),"?"))+")",fields.stream().map(row::get).toArray());}
        List<JSONObject> query(String stage,String q,Object...a)throws RepositoryException{if(fault.equals(stage))throw new RepositoryException("PRIVATE_READ");Assert.assertTrue(delegate!=null&&delegate.isActive());return Fixture.rows(writer,q,a);}
        long count(String table)throws RepositoryException{return scalar("SELECT COUNT(*) AS value FROM "+table);}
        long balance()throws RepositoryException{return Fixture.rows(observer,"SELECT userPoint FROM x_user WHERE oId=?",actor).getFirst().getLong("userPoint");}
        JSONObject buy(String key)throws Exception{return purchase.purchase(actor,new JSONObject(),key);}
        void mode(String value)throws RepositoryException{sql(observer,"UPDATE options SET optionValue=? WHERE oId=?",value,Option.ID_C_MISC_ALLOW_REGISTER);}
        RequestContext post(String token,String key,String body,boolean route)throws Exception{
            final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/me/invitation-codes?apiKey=legacy&userId=2",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{
                raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","127.0.0.1");if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                final var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy","userId","2")));
                final var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                if(route){c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(ctx->{var r=(org.b3log.latke.http.handler.RouteResolution)ctx.attr("MATCH_RESULT");Assert.assertEquals(r.getContextHandlerMeta().getInvokeHolder().getName(),"purchase");endpoint.purchase(ctx);});c.handle();}
                else{c.addHandler(writes::handle);c.addHandler(endpoint::purchase);auth.activityWrite(c);}return c;
            }finally{raw.release();}
        }
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new PurchaseFixture()){
            InvitationPurchaseRouteBindings.register(f.endpoint,f.auth,f.writes);Dispatcher.mapping();
            var ctx=f.post("writer","purchase","{}",true);Assert.assertEquals(ctx.attr("test.status"),200);JSONObject result=(JSONObject)ctx.attr("test.data");Assert.assertEquals(result.keySet(),Set.of("code","paidPoints","expiresAt"));Assert.assertEquals(result.getInt("paidPoints"),100);Assert.assertEquals(f.balance(),400);
            var stored=Fixture.rows(f.observer,"SELECT * FROM invites").getFirst();Assert.assertEquals(result.getString("code"),stored.getString("code"));Assert.assertEquals(result.getLong("expiresAt"),Math.addExact(Long.parseLong(stored.getString("oId")),Symphonys.INVITECODE_EXPIRED));Assert.assertEquals(stored.getString("generatorId"),f.actor);Assert.assertEquals(stored.getInt("status"),1);Assert.assertEquals(f.count("transfer_index"),2);
            Assert.assertTrue(f.locks.indexOf("namespace")<f.locks.indexOf("option"));Assert.assertEquals(f.generatedCount,1);
            Assert.assertEquals(f.buy("purchase").toString(),result.toString());Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.count("invites"),1);
        }
        System.out.println("PASS 1 actual Dispatcher/auth/purchase/ledger SQL and namespace-option-account order, expiry from actual ID, public whitelist and replay");
        for(String mode:List.of("0","1","bad"))try(var f=new PurchaseFixture()){f.mode(mode);error(mode.equals("bad")?503:409,()->f.buy("mode"));Assert.assertEquals(f.count("invites"),0);Assert.assertEquals(f.count("receipts"),0);Assert.assertEquals(f.balance(),500);Assert.assertEquals(f.generatedCount,0);}
        for(int balance:new int[]{99,0,-1})try(var f=new PurchaseFixture()){sql(f.observer,"UPDATE x_user SET userPoint=? WHERE oId=?",balance,f.actor);error(409,()->f.buy("poor"));Assert.assertEquals(f.count("invites"),0);Assert.assertEquals(f.count("receipts"),0);Assert.assertEquals(f.balance(),balance);Assert.assertEquals(f.generatedCount,0);}
        try(var f=new PurchaseFixture()){sql(f.observer,"UPDATE x_user SET userPoint=100 WHERE oId=?",f.actor);f.buy("exact");Assert.assertEquals(f.balance(),0);}
        System.out.println("PASS 2 registration modes and exact balance gates precede credential generation, insufficient purchase creates nothing");
        try(var f=new PurchaseFixture()){
            var first=f.buy("durable");f.mode("1");sql(f.observer,"UPDATE invites SET status=0,userId='2',useTime=1");Assert.assertEquals(f.buy("durable").toString(),first.toString());Assert.assertEquals(f.generatedCount,1);
            sql(f.observer,"DELETE FROM invites");Assert.assertEquals(f.buy("durable").toString(),first.toString());Assert.assertEquals(f.count("transfers"),1);sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);error(404,()->f.buy("durable"));
        }
        System.out.println("PASS 3 same-key durable receipt survives mode closure, consumption and cleanup but revalidates current actor on replay");
        for(String fault:List.of("option","user","receipt-read","invitation","points-write","transfer-write","invitation-write","receipt-write","commit","savepoint","release"))try(var f=new PurchaseFixture()){
            f.fault=fault;error(503,()->f.buy("fault"));Assert.assertEquals(f.balance(),500,fault);Assert.assertEquals(f.count("invites"),0,fault);Assert.assertEquals(f.count("transfers"),0,fault);Assert.assertEquals(f.count("receipts"),0,fault);Assert.assertEquals(f.count("transfer_index"),0,fault);f.fault="";f.buy("fault");Assert.assertEquals(f.balance(),400);
        }
        System.out.println("PASS 4 current reads, charge/ledger/code/receipt writes, savepoint and strict commit failures roll back complete purchase");
        try(var f=new PurchaseFixture()){
            f.fault="lost-ack";error(503,()->f.buy("ack"));Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.count("receipts"),1);Assert.assertEquals(f.count("invites"),1);Assert.assertEquals(f.count("transfer_index"),0);f.fault="";var result=f.buy("ack");Assert.assertEquals(result.getString("code"),f.generated);Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.count("transfers"),1);Assert.assertEquals(f.count("transfer_index"),2);Assert.assertEquals(f.generatedCount,1);
        }
        for(String fault:List.of("post-index","post-index-write"))try(var f=new PurchaseFixture()){f.fault=fault;f.buy("index");Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.count("transfer_index"),0);f.fault="";f.buy("index");f.buy("index");Assert.assertEquals(f.count("transfer_index"),2);}
        try(var f=new PurchaseFixture()){f.badIndexParent=true;f.buy("parent");Assert.assertEquals(f.count("transfer_index"),0);f.badIndexParent=false;f.buy("parent");Assert.assertEquals(f.count("transfer_index"),2);}
        try(var f=new PurchaseFixture()){f.fault="index-lost-ack";f.buy("index-ack");Assert.assertEquals(f.count("transfer_index"),2);Assert.assertEquals(f.balance(),400);f.fault="";f.buy("index-ack");Assert.assertEquals(f.count("transfer_index"),2);Assert.assertEquals(f.count("transfers"),1);}
        System.out.println("PASS 5 durable commit acknowledgement loss recovers same code; verified parent and idempotent postcommit index repairs avoid duplicate charges");
        try(var f=new PurchaseFixture()){sql(f.observer,"INSERT INTO invites VALUES('1',?,'2','',0,1,'old')",f.generated.toLowerCase(Locale.ROOT));error(503,()->f.buy("collision"));Assert.assertEquals(f.balance(),500);Assert.assertEquals(f.count("invites"),1);Assert.assertEquals(f.count("transfers"),0);Assert.assertEquals(f.count("receipts"),0);}
        for(String id:List.of("bad","0","9223372036854775807"))try(var f=new PurchaseFixture()){f.generatedId=id;error(503,()->f.buy("id"));Assert.assertEquals(f.balance(),500);Assert.assertEquals(f.count("invites"),0);}
        try(var f=new PurchaseFixture()){f.generated="short";error(503,()->f.buy("generator"));Assert.assertEquals(f.balance(),500);}
        System.out.println("PASS 6 case-insensitive legacy code collision never overwrites, malformed IDs/expiry overflow/generator fail before payment");
        try(var f=new PurchaseFixture()){
            for(String token:Arrays.asList(null,"invalid","reader"))Assert.assertEquals(f.post(token,"auth","{}",false).attr("test.status"),"reader".equals(token)?403:401);
            for(String body:List.of("{\"userId\":\"2\"}","{\"paidPoints\":0}","{\"code\":\"ABCD012345678901\"}","[]","{\"x\":1,\"x\":2}"))Assert.assertNotEquals(f.post("writer","bad",body,false).attr("test.status"),200);
            Assert.assertEquals(f.post("writer",null,"{}",false).attr("test.status"),422);Assert.assertEquals(f.post("writer","large"," ".repeat(4097),false).attr("test.status"),413);Assert.assertEquals(f.count("receipts"),0);
            var ctx=f.post("writer","empty","",false);Assert.assertEquals(ctx.attr("test.status"),200);Assert.assertEquals(f.balance(),400);
        }
        System.out.println("PASS 7 Bearer scope and idempotency required, Cookie/query identity/body price/code forging and invalid JSON reject; empty purchase body accepted");
        try(var f=new PurchaseFixture()){
            f.buy("private");var ledger=Fixture.rows(f.observer,"SELECT * FROM transfers").getFirst();Assert.assertFalse(ledger.toString().contains(f.generated));Assert.assertEquals(ledger.getString("dataId"),"");Assert.assertEquals(ledger.getString("memo"),"购买邀请码");
            var receipt=Fixture.rows(f.observer,"SELECT oId,value FROM receipts").getFirst();var row=new JSONObject(receipt.getString("value"));var durable=new JSONObject(row.getString("responseJson"));durable.getJSONObject("result").put("memo","PRIVATE");row.put("responseJson",durable.toString());sql(f.observer,"UPDATE receipts SET value=? WHERE oId=?",row.toString(),receipt.getString("oId"));error(503,()->f.buy("private"));Assert.assertEquals(f.balance(),400);
        }
        System.out.println("PASS 8 credential never enters transfer logging fields and replay projection fails closed on unexpected private response keys");
        try(var f=new PurchaseFixture()){
            var first=f.buy("same-key");f.generated="ZyXwVu9876543210";var other=f.purchase.purchase("2",new JSONObject(),"same-key");Assert.assertNotEquals(first.getString("code"),other.getString("code"));Assert.assertEquals(f.count("invites"),2);Assert.assertEquals(f.count("receipts"),2);Assert.assertEquals(f.balance(),400);Assert.assertEquals(Fixture.rows(f.observer,"SELECT userPoint FROM x_user WHERE oId='2'").getFirst().getInt("userPoint"),700);
            Assert.assertEquals(f.buy("same-key").getString("code"),first.getString("code"));Assert.assertFalse(AccountPairLocks.lock(f.actor,"sys").isHeldByCurrentThread());
        }
        System.out.println("PASS 9 identical keys remain owner-scoped and cannot reveal another purchaser credential; complete account locks release");
        System.out.println("Invitation purchase isolated tests passed: 9 groups");
    }
}
