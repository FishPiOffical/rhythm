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
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;

/** Actual transfer/fee/notification+revision/receipt SQL, Pointtransfer primitive, strict JDBC and routed HTTP/auth. */
public class ApiV3PointTransferTest extends ApiV3ProfessionPrimaryTest {
    static void inject(Object target,String name,Object value)throws Exception{Class<?> t=target.getClass();while(t!=null){try{var f=t.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException e){t=t.getSuperclass();}}throw new NoSuchFieldException(name);}
    static class TransferFixture extends Fixture {
        int refreshes;boolean refreshFails,badIndexParent;long clock=2000000000000L;
        final ApiV3PointTransferService transfer=new ApiV3PointTransferService(){protected long now(){return clock;}};
        final ApiV3PointTransferProcessor endpoint=new ApiV3PointTransferProcessor();
        TransferFixture()throws Exception {
            sql(observer,"ALTER TABLE x_user ADD COLUMN userUsedPoint INT DEFAULT 0");sql(observer,"ALTER TABLE x_user ADD COLUMN userName TEXT COLLATE NOCASE");
            sql(observer,"UPDATE x_user SET userName=CASE WHEN oId=? THEN 'alice' WHEN oId='2' THEN 'bob' ELSE 'disabled' END",actor);
            sql(observer,"INSERT INTO x_user(oId,userStatus,userPoint,userPassword,userUsedPoint,userName) VALUES('3',0,100,'PRIVATE',0,'admin')");
            sql(observer,"CREATE TABLE memberships(oId TEXT,userId TEXT,lvCode TEXT,state INT,expiresAt INT)");
            sql(observer,"CREATE TABLE requests(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt INT)");
            sql(observer,"CREATE TABLE transfers(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,sum INT,fromBalance INT,toBalance INT,time INT,type INT,dataId TEXT,memo TEXT,sourceAppId TEXT,sourceAppName TEXT,sourceScene TEXT,sourceRequestId TEXT)");
            sql(observer,"CREATE TABLE transfer_index(oId TEXT PRIMARY KEY,userId TEXT,rawId TEXT)");sql(observer,"CREATE INDEX idx_transfer_owner ON transfer_index(rawId,userId)");
            sql(observer,"CREATE TABLE notices(oId TEXT PRIMARY KEY,userId TEXT,dataId TEXT,dataType INT,hasRead INT,content TEXT)");
            sql(observer,"CREATE TABLE notice_revisions(oId TEXT PRIMARY KEY,revision TEXT,updatedAt INT)");
            final var userRows=new UserRepository(){
                public String getName(){return "x_user";}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("user",q,a);}
                public JSONObject getForUpdate(String id)throws RepositoryException{var r=query("user","SELECT * FROM x_user WHERE oId=? FOR UPDATE",id);return r.isEmpty()?null:r.getFirst();}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException{Assert.assertTrue(ArticlePublicationCaches.active());exec("UPDATE x_user SET userPoint=?,userUsedPoint=? WHERE oId=?",row.getInt("userPoint"),row.optInt("userUsedPoint"),id);}
            };
            final var transferRows=new PointtransferRepository(){
                public String getName(){return "transfers";}public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}public Transaction beginTransaction(){return begin();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{if(badIndexParent)return List.of();return query("post-index",q,a);}
                public String add(JSONObject row)throws RepositoryException{String id=org.b3log.latke.util.Ids.genTimeMillisId();row.put("oId",id);insert("transfers",row);return id;}
            };
            final var requestRows=new ApiV3RequestRepository(){
                public String getName(){return "requests";}
                public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}public Transaction beginTransaction(){return begin();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("receipt-hint",q,a);}
                public JSONObject getForUpdate(String id)throws RepositoryException{var r=query("receipt-read","SELECT * FROM requests WHERE oId=? FOR UPDATE",id);return r.isEmpty()?null:r.getFirst();}
                public String add(JSONObject row)throws RepositoryException{insert("requests",row);return row.getString("oId");}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException{exec("UPDATE requests SET responseJson=? WHERE oId=?",row.getString("responseJson"),id);}
            };
            final var pointService=new PointtransferMgmtService(){
                public void indexTransferAsync(String from,String to,String raw){throw new AssertionError("unexpected precommit/append-only index");}
                public <T>T executeWithTransferLocks(String from,List<String> destinations,java.util.concurrent.Callable<T> op)throws Exception{return super.executeWithTransferLocks(from,destinations,()->{Assert.assertFalse(hasActive());for(String to:destinations)Assert.assertTrue(AccountPairLocks.lock(from,to).isHeldByCurrentThread());return op.call();});}
            };
            final var notices=new NotificationRepository(){
                public String getName(){return "notices";}public boolean hasTransactionBegun(){return hasActive();}
                protected String insertNotification(JSONObject row)throws RepositoryException{String id=org.b3log.latke.util.Ids.genTimeMillisId();row.put("oId",id);insert("notices",row);return id;}
            };
            final var revisions=new ApiV3NotificationRevisionRepository(){
                public String getName(){return "notice_revisions";}public boolean hasTransactionBegun(){return hasActive();}
                protected String upsertSql(){return "INSERT INTO notice_revisions(oId,revision,updatedAt) VALUES(?,?,?) ON CONFLICT(oId) DO UPDATE SET revision=excluded.revision,updatedAt=excluded.updatedAt";}
            };
            inject(notices,"revisions",revisions);
            inject(pointService,"pointtransferRepository",transferRows);inject(pointService,"userRepository",userRows);
            inject(transfer,"users",userRows);inject(transfer,"points",pointService);inject(transfer,"transfers",transferRows);inject(transfer,"requests",requestRows);
            inject(transfer,"memberships",new MembershipRepository(){public String getName(){return "memberships";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("membership",q,a);}});
            inject(transfer,"notifications",notices);inject(transfer,"notificationService",new NotificationMgmtService(){public void publishRefreshNotification(String user){Assert.assertFalse(hasActive());Assert.assertEquals(countUnchecked("notices"),(long)refreshes+1);refreshes++;if(refreshFails)throw new IllegalStateException("PRIVATE_REFRESH");}});
            inject(transfer,"transferIndex",new PointtransferRedcRepository(){public String getName(){return "transfer_index";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("post-index",q,a);}});
            inject(endpoint,"service",transfer);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("writer","reader","admin").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("admin")?"3":actor)).put("scope",token.equals("reader")?"profile.read":"activity.write"):null;}});
        }
        boolean hasActive(){return delegate!=null&&delegate.isActive();}
        @Override Transaction begin(){
            Transaction tx=super.begin();final Connection previous=writer;
            writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                try{
                    if(method.getName().equals("prepareStatement")){String q=(String)args[0];final var ps=(PreparedStatement)method.invoke(previous,args);return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                        try{Object r=m.invoke(ps,a);if(m.getName().equals("executeUpdate")&&writeFault(q))throw new SQLException("PRIVATE_WRITE");return r;}catch(InvocationTargetException e){throw e.getCause();}});}
                    final Object result=method.invoke(previous,args);
                    if(method.getName().equals("commit") && fault.equals("index-lost-ack") && roots>1)throw new SQLException("PRIVATE_INDEX_ACK");
                    return result;
                }catch(InvocationTargetException e){throw e.getCause();}
            });
            try{var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(delegate,writer);}catch(Exception e){throw new IllegalStateException(e);}return tx;
        }
        boolean writeFault(String q){return fault.equals("points-write")&&q.startsWith("UPDATE x_user") || fault.equals("transfer-write")&&q.startsWith("INSERT INTO transfers")
                || fault.equals("fee-write")&&q.startsWith("INSERT INTO transfers")&&currentTransferCount()==2
                || fault.equals("notice-write")&&q.startsWith("INSERT INTO notices") || fault.equals("revision-write")&&q.startsWith("INSERT INTO notice_revisions")
                || fault.equals("receipt-write")&&q.startsWith("UPDATE requests") || fault.equals("post-index-write")&&q.startsWith("INSERT INTO `transfer_index`");}
        long currentTransferCount(){try{return Fixture.rows(writer,"SELECT COUNT(*) AS value FROM transfers").getFirst().getLong("value");}catch(RepositoryException e){throw new IllegalStateException(e);}}
        void exec(String q,Object...a)throws RepositoryException{sql(writer,q,a);}
        void insert(String table,JSONObject row)throws RepositoryException{var fields=new ArrayList<>(row.keySet());exec("INSERT INTO "+table+" ("+String.join(",",fields)+") VALUES ("+String.join(",",Collections.nCopies(fields.size(),"?"))+")",fields.stream().map(row::get).toArray());}
        List<JSONObject> query(String stage,String q,Object...a)throws RepositoryException{
            if(fault.equals(stage))throw new RepositoryException("PRIVATE_READ");
            final boolean current=q.endsWith(" FOR UPDATE");if(current){Assert.assertTrue(hasActive());if(q.startsWith("SELECT oId,userName,userStatus"))locks.add("user:"+a[0]);}
            return Fixture.rows(current?writer:observer,q,a);
        }
        long count(String table)throws RepositoryException{return scalar("SELECT COUNT(*) AS value FROM "+table);}
        long countUnchecked(String table){try{return count(table);}catch(Exception e){throw new IllegalStateException(e);}}
        long balance(String user)throws RepositoryException{return Fixture.rows(observer,"SELECT userPoint FROM x_user WHERE oId=?",user).getFirst().getLong("userPoint");}
        JSONObject pay(String key)throws Exception{return pay(actor,"bob",100,key);}
        JSONObject pay(String sender,String recipient,int amount,String key)throws Exception{return transfer.transfer(sender,new JSONObject().put("userName",recipient).put("amount",amount).put("memo"," <b>谢谢</b> "),key);}
        void membership(String code,int state,long expires)throws RepositoryException{sql(observer,"INSERT INTO memberships VALUES('600',?,?,?,?)",actor,code,state,expires);}
        RequestContext post(String token,String key,String body,boolean route)throws Exception{
            final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/me/point-transfers?apiKey=legacy&userId=2",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{
                raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","127.0.0.1");if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                final var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy","userId","2")));
                final var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                if(route){c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(ctx->{var r=(org.b3log.latke.http.handler.RouteResolution)ctx.attr("MATCH_RESULT");Assert.assertEquals(r.getContextHandlerMeta().getInvokeHolder().getName(),"transfer");endpoint.transfer(ctx);});c.handle();}
                else{c.addHandler(writes::handle);c.addHandler(endpoint::transfer);auth.activityWrite(c);}return c;
            }finally{raw.release();}
        }
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new TransferFixture()){
            PointTransferRouteBindings.register(f.endpoint,f.auth,f.writes);Dispatcher.mapping();
            var c=f.post("writer","transfer","{\"userName\":\"bob\",\"amount\":100,\"memo\":\" <b>谢谢</b> \"}",true);Assert.assertEquals(c.attr("test.status"),200);var r=(JSONObject)c.attr("test.data");
            Assert.assertEquals(r.keySet(),Set.of("transferId","recipient","amount","receivedPoints","feePoints","memo"));Assert.assertEquals(r.getInt("amount"),100);Assert.assertEquals(r.getInt("receivedPoints"),99);Assert.assertEquals(r.getInt("feePoints"),1);Assert.assertEquals(r.getString("memo"),"谢谢");Assert.assertEquals(r.getJSONObject("recipient").keySet(),Set.of("userId","userName"));
            Assert.assertEquals(f.balance(f.actor),400);Assert.assertEquals(f.balance("2"),899);Assert.assertEquals(f.balance("3"),101);Assert.assertEquals(f.count("notices"),1);Assert.assertEquals(f.count("notice_revisions"),1);Assert.assertEquals(f.count("transfers"),2);Assert.assertEquals(f.count("transfer_index"),4);Assert.assertEquals(f.refreshes,1);
            var sorted=new TreeSet<String>(List.of(f.actor,"2","3"));Assert.assertEquals(f.locks.subList(0,3),sorted.stream().map(x->"user:"+x).toList());
            Assert.assertEquals(f.pay("transfer").toString(),r.toString());Assert.assertEquals(f.refreshes,1);Assert.assertEquals(f.count("notices"),1);Assert.assertEquals(f.balance(f.actor),400);
        }
        System.out.println("PASS 1 actual Dispatcher/auth/strict main+fee+notification+revision SQL and sorted account locks, public projection and same-key replay");
        for(int amount:new int[]{2,99,100,199,200,499})try(var f=new TransferFixture()){
            var r=f.pay(f.actor,"bob",amount,"fee");int fee=Math.max(1,amount/100);Assert.assertEquals(r.getInt("feePoints"),fee);Assert.assertEquals(r.getInt("receivedPoints"),amount-fee);Assert.assertEquals(f.balance(f.actor),500-amount);
        }
        for(String code:List.of("VIP4_YEAR","VIP4_MONTH"))for(long expiry:new long[]{0,2000000000001L})try(var f=new TransferFixture()){f.membership(code,1,expiry);var r=f.pay("premium");Assert.assertEquals(r.getInt("feePoints"),0);Assert.assertEquals(f.balance("2"),900);Assert.assertEquals(f.count("transfers"),1);Assert.assertEquals(f.count("transfer_index"),2);}
        for(String code:List.of("VIP4_EXTRA","vip4_year","VIP3_YEAR","VIP4_YEAR"))try(var f=new TransferFixture()){f.membership(code,1,2000000000000L);Assert.assertEquals(f.pay("expired").getInt("feePoints"),1);}
        try(var f=new TransferFixture()){f.membership("VIP4_YEAR",0,Long.MAX_VALUE);Assert.assertEquals(f.pay("inactive").getInt("feePoints"),1);}
        try(var f=new TransferFixture()){f.membership("VIP4_YEAR",1,0);sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId='3'");Assert.assertEquals(f.pay("premium-disabled-fee").getInt("feePoints"),0);Assert.assertEquals(f.balance("3"),100);}
        try(var f=new TransferFixture()){sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId='3'");error(404,()->f.pay("ordinary-disabled-fee"));Assert.assertEquals(f.count("transfers"),0);}
        System.out.println("PASS 2 exact integer fee boundaries and only current precise active/permanent VIP4 codes exempt");
        try(var f=new TransferFixture()){var r=f.pay("3","bob",100,"admin");Assert.assertEquals(r.getInt("feePoints"),0);Assert.assertEquals(f.balance("3"),0);Assert.assertEquals(f.balance("2"),900);Assert.assertEquals(f.count("transfers"),1);Assert.assertEquals(Fixture.rows(f.observer,"SELECT fromId FROM transfers").getFirst().getString("fromId"),"3");}
        try(var f=new TransferFixture()){var r=f.pay(f.actor,"admin",100,"admin-recipient");Assert.assertEquals(r.getInt("feePoints"),1);Assert.assertEquals(f.balance("3"),200);Assert.assertEquals(f.count("transfers"),2);}
        try(var f=new TransferFixture()){error(409,()->f.pay("3","bob",101,"admin-poor"));Assert.assertEquals(f.count("transfers"),0);}
        System.out.println("PASS 3 actual fee-recipient account exempts its own outgoing fee, no sys debit; admin receiving aggregates full debit safely");
        try(var f=new TransferFixture()){
            var first=f.pay("durable");sql(f.observer,"UPDATE x_user SET userName='renamed',userStatus=4 WHERE oId='2'");sql(f.observer,"UPDATE x_user SET userName='retired',userStatus=4 WHERE oId='3'");f.membership("VIP4_YEAR",1,1);
            Assert.assertEquals(f.pay("durable").toString(),first.toString());Assert.assertEquals(f.count("transfers"),2);error(409,()->f.pay(f.actor,"bob",101,"durable"));
            sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);error(404,()->f.pay("durable"));
        }
        System.out.println("PASS 4 replay survives receiver/admin rename or deactivation and membership change, but requires current actor and identical request");
        for(String fault:List.of("receipt-hint","user","receipt-read","membership","points-write","transfer-write","fee-write","notice-write","revision-write","receipt-write","commit","savepoint","release"))try(var f=new TransferFixture()){
            f.fault=fault;error(503,()->f.pay("fault"));Assert.assertEquals(f.balance(f.actor),500,fault);Assert.assertEquals(f.balance("2"),800,fault);Assert.assertEquals(f.balance("3"),100,fault);for(String t:List.of("transfers","requests","notices","notice_revisions","transfer_index"))Assert.assertEquals(f.count(t),0,fault);f.fault="";f.pay("fault");Assert.assertEquals(f.balance(f.actor),400);
        }
        System.out.println("PASS 5 read, payment, ledger, notification/revision, receipt, savepoint and swallowed commit failures roll back entire transaction");
        try(var f=new TransferFixture()){f.fault="lost-ack";error(503,()->f.pay("ack"));Assert.assertEquals(f.balance(f.actor),400);Assert.assertEquals(f.count("notices"),1);Assert.assertEquals(f.count("transfer_index"),0);f.fault="";f.pay("ack");Assert.assertEquals(f.balance(f.actor),400);Assert.assertEquals(f.count("transfers"),2);Assert.assertEquals(f.count("transfer_index"),4);}
        for(String fault:List.of("post-index","post-index-write","index-lost-ack"))try(var f=new TransferFixture()){f.fault=fault;f.pay("index");Assert.assertEquals(f.balance(f.actor),400);f.fault="";f.pay("index");f.pay("index");Assert.assertEquals(f.count("transfer_index"),4);Assert.assertEquals(f.count("transfers"),2);}
        try(var f=new TransferFixture()){f.badIndexParent=true;f.pay("parent");Assert.assertEquals(f.count("transfer_index"),0);f.badIndexParent=false;f.pay("parent");Assert.assertEquals(f.count("transfer_index"),4);}
        try(var f=new TransferFixture()){f.refreshFails=true;f.pay("refresh");Assert.assertEquals(f.balance(f.actor),400);Assert.assertEquals(f.count("notices"),1);f.pay("refresh");Assert.assertEquals(f.count("notices"),1);}
        System.out.println("PASS 6 durable lost acknowledgement and verified idempotent postcommit index repairs avoid duplicate payment; refresh failure leaves successful transfer");
        try(var f=new TransferFixture()){
            for(String name:List.of("alice","disabled","missing")){error(name.equals("alice")?422:404,()->f.pay(f.actor,name,100,"bad-"+name));Assert.assertEquals(f.count("transfers"),0);}
            sql(f.observer,"UPDATE x_user SET userName=? WHERE oId='2'",UserExt.COM_BOT_NAME);error(422,()->f.pay(f.actor,UserExt.COM_BOT_NAME,100,"bot"));
        }
        try(var f=new TransferFixture()){sql(f.observer,"UPDATE x_user SET userPoint=? WHERE oId='2'",Integer.MAX_VALUE-50);error(409,()->f.pay("overflow"));Assert.assertEquals(f.balance(f.actor),500);}
        try(var f=new TransferFixture()){sql(f.observer,"UPDATE x_user SET userPoint=? WHERE oId='3'",Integer.MAX_VALUE-99);error(409,()->f.pay(f.actor,"admin",100,"combined-overflow"));Assert.assertEquals(f.balance(f.actor),500);}
        for(String code:List.of("Admin","ADMIN"))try(var f=new TransferFixture()){sql(f.observer,"UPDATE x_user SET userName=? WHERE oId='3'",code);error(503,()->f.pay("case"));Assert.assertEquals(f.count("transfers"),0);}
        for(String mutation:List.of("INSERT INTO memberships VALUES('600','ACTOR','VIP4_YEAR',1,0),('601','ACTOR','VIP4_MONTH',1,0)","INSERT INTO memberships VALUES('600','ACTOR','VIP4_YEAR',2,0)"))try(var f=new TransferFixture()){sql(f.observer,mutation.replace("ACTOR",f.actor));error(503,()->f.pay("corrupt"));Assert.assertEquals(f.count("transfers"),0);}
        System.out.println("PASS 7 self/bot/inactive/missing recipients, recipient aggregate overflow, admin CI impostor and corrupt membership fail closed");
        try(var f=new TransferFixture()){
            for(String token:Arrays.asList(null,"bad","reader"))Assert.assertEquals(f.post(token,"auth","{\"userName\":\"bob\",\"amount\":100}",false).attr("test.status"),"reader".equals(token)?403:401);
            for(String body:List.of("{}","[]","{\"userName\":\"bob\",\"amount\":1}","{\"userName\":\"bob\",\"amount\":\"100\"}","{\"userName\":\"bob\",\"amount\":2.5}","{\"userName\":\"bob\",\"amount\":2147483648}","{\"userName\":\"bob\",\"amount\":100,\"fromId\":\"sys\"}","{\"userName\":\"bob\",\"amount\":100,\"feePoints\":0}","{\"userName\":\"bob\",\"amount\":100,\"memo\":\""+"a".repeat(129)+"\"}","{\"userName\":\"bob\",\"amount\":2,\"amount\":3}"))Assert.assertNotEquals(f.post("writer","bad",body,false).attr("test.status"),200,body);
            Assert.assertEquals(f.post("writer",null,"{\"userName\":\"bob\",\"amount\":100}",false).attr("test.status"),422);Assert.assertEquals(f.post("writer","large"," ".repeat(4097),false).attr("test.status"),413);Assert.assertEquals(f.count("transfers"),0);
        }
        for(String name:List.of("bo\nb","bob@example.com","bob' OR 1=1"))error(422,()->ApiV3PointTransferInputs.parse(new JSONObject().put("userName",name).put("amount",2)));
        for(String memo:List.of("&".repeat(128),"ab\u0001cd",new String(new char[]{(char)0xD800})))error(422,()->ApiV3PointTransferInputs.parse(new JSONObject().put("userName","bob").put("amount",2).put("memo",memo)));
        System.out.println("PASS 8 Bearer activity scope/key required, Cookie/query spoofing ignored and strict amount/body/memo/unknown-field, username and post-clean storage limits");
        try(var f=new TransferFixture()){
            f.pay("private");var receipt=Fixture.rows(f.observer,"SELECT * FROM requests").getFirst();var durable=new JSONObject(receipt.getString("responseJson"));durable.getJSONObject("result").put("private","PRIVATE");sql(f.observer,"UPDATE requests SET responseJson=? WHERE oId=?",durable.toString(),receipt.getString("oId"));error(503,()->f.pay("private"));Assert.assertEquals(f.balance(f.actor),400);
        }
        try(var f=new TransferFixture()){
            var first=f.pay("shared");var second=f.pay("2","alice",100,"shared");Assert.assertNotEquals(first.getString("transferId"),second.getString("transferId"));Assert.assertEquals(f.count("requests"),2);Assert.assertFalse(AccountPairLocks.lock(f.actor,"2").isHeldByCurrentThread());Assert.assertFalse(AccountPairLocks.lock(f.actor,"3").isHeldByCurrentThread());
        }
        System.out.println("PASS 9 response projection rejects unexpected private keys, owner-scoped keys and complete account locks release");
        System.out.println("Point transfer isolated tests passed: 9 groups");
    }
}
