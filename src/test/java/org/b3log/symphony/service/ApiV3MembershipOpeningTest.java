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
import org.b3log.symphony.cache.MembershipCache;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.time.*;

/** Production opening/point/bag SQL and strict JDBC, with SQLite/identity/async-index adapters. */
public class ApiV3MembershipOpeningTest extends ApiV3ProfessionPrimaryTest {
    static List<JSONObject> rows(Connection c,String q,Object...a)throws RepositoryException{return Fixture.rows(c,q,a);}
    static void inject(Object target,String name,Object value)throws Exception{Class<?> type=target.getClass();while(type!=null){try{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;}catch(NoSuchFieldException e){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
    static class OpeningFixture extends Fixture {
        long clock=System.currentTimeMillis();ZoneId timeZone=ZoneId.systemDefault();
        final ApiV3MembershipOpeningService opening=new ApiV3MembershipOpeningService(){protected long now(){return clock;}protected ZoneId zone(){return timeZone;}};
        final ApiV3MembershipOpeningProcessor endpoint=new ApiV3MembershipOpeningProcessor();
        final List<String> indexed=new ArrayList<>();int invalidations;
        OpeningFixture()throws Exception {
            sql(observer,"ALTER TABLE x_user ADD COLUMN userUsedPoint INT DEFAULT 0");
            sql(observer,"UPDATE x_user SET userPoint=100000 WHERE oId=?",actor);
            sql(observer,"CREATE TABLE plans(oId TEXT PRIMARY KEY,lvCode TEXT,lvName TEXT,price INT,durationType TEXT,durationValue INT,benefits TEXT)");
            sql(observer,"CREATE TABLE members(oId TEXT PRIMARY KEY,userId TEXT,lvCode TEXT,state INT,expiresAt INT,configJson TEXT,createdAt INT,updatedAt INT)");
            sql(observer,"CREATE TABLE activations(oId TEXT PRIMARY KEY,userId TEXT,lvCode TEXT,price INT,durationType TEXT,durationValue INT,couponCode TEXT,configJson TEXT,createdAt INT,updatedAt INT)");
            sql(observer,"CREATE TABLE coupons(oId TEXT PRIMARY KEY,coupon_code TEXT COLLATE NOCASE,coupon_type INT,discount INT,times INT,updatedAt INT)");
            sql(observer,"CREATE TABLE transfers(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,sum INT,fromBalance INT,toBalance INT,time INT,type INT,dataId TEXT,memo TEXT,sourceAppId TEXT,sourceAppName TEXT,sourceScene TEXT,sourceRequestId TEXT)");
            sql(observer,"CREATE TABLE bags(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");
            sql(observer,"CREATE TABLE transfer_index(oId TEXT PRIMARY KEY,userId TEXT,rawId TEXT)");
            sql(observer,"CREATE INDEX idx_membership_transfer_owner ON transfer_index(rawId,userId)");
            plan("10","VIP1_YEAR",1000);plan("11","VIP2_YEAR",1500);plan("12","VIP3_YEAR",2000);plan("13","VIP4_YEAR",3000);plan("14","VIP1_MONTH",100);
            sql(observer,"INSERT INTO coupons VALUES('30','SAVEHALF',0,50,2,0),('31','UNLIMITED',0,25,-1,0),('32','USEDUP00',0,80,0,0),('33','NOTDISC0',1,50,3,0),('34','FREEZERO',0,0,1,0)");
            final var userRows=new UserRepository(){
                public String getName(){return "x_user";}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("user",q,a);}
                public JSONObject getForUpdate(String id)throws RepositoryException{var r=query("user","SELECT * FROM x_user WHERE oId=? FOR UPDATE",id);return r.isEmpty()?null:r.getFirst();}
                public void update(String id,JSONObject value,String...fields)throws RepositoryException{
                    Assert.assertTrue(ArticlePublicationCaches.active());
                    exec("UPDATE x_user SET userPoint=?,userUsedPoint=? WHERE oId=?",value.getInt("userPoint"),value.optInt("userUsedPoint"),id);
                }
            };
            final var planRows=new MembershipLevelRepository(){public String getName(){return "plans";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("level",q,a);}};
            final var memberRows=new MembershipRepository(){public String getName(){return "members";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("membership",q,a);}};
            final var activationRows=new MembershipActivationRepository(){public String getName(){return "activations";}};
            final var couponRows=new CouponRepository(){public String getName(){return "coupons";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("coupon",q,a);}};
            final var transferRows=new PointtransferRepository(){
                public String getName(){return "transfers";}public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}
                public Transaction beginTransaction(){return begin();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("post-index",q,a);}
                public String add(JSONObject row)throws RepositoryException{String id=org.b3log.latke.util.Ids.genTimeMillisId();row.put("oId",id);insert("transfers",row);return id;}
            };
            final var cloud=new CloudRepository(){
                public String getName(){return "bags";}public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("bag",q,a);}
                public String add(JSONObject row)throws RepositoryException{String id=org.b3log.latke.util.Ids.genTimeMillisId();row.put("oId",id);insert("bags",row);return id;}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException{exec("UPDATE bags SET data=? WHERE oId=?",row.getString("data"),id);}
                public void remove(String id)throws RepositoryException{exec("DELETE FROM bags WHERE oId=?",id);}
            };
            final var ledger=new ApiV3RequestRepository(){
                public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}public Transaction beginTransaction(){return begin();}
                public JSONObject getForUpdate(String id)throws RepositoryException{var r=rows(writer,"SELECT value FROM receipts WHERE oId=?",id);return r.isEmpty()?null:new JSONObject(r.getFirst().getString("value"));}
                public String add(JSONObject value)throws RepositoryException{exec("INSERT INTO receipts VALUES(?,?)",value.getString("oId"),value.toString());return value.getString("oId");}
                public void update(String id,JSONObject value,String...fields)throws RepositoryException{exec("UPDATE receipts SET value=? WHERE oId=?",value.toString(),id);}
            };
            final var pointService=new PointtransferMgmtService(){public void indexTransferAsync(String from,String to,String transfer){throw new AssertionError("legacy append-only index must not be used on replay");}};
            inject(pointService,"pointtransferRepository",transferRows);inject(pointService,"userRepository",userRows);
            final var bagService=new BagMgmtService();inject(bagService,"cloudRepository",cloud);inject(bagService,"userRepository",userRows);
            inject(opening,"users",userRows);inject(opening,"levels",planRows);inject(opening,"memberships",memberRows);inject(opening,"activations",activationRows);
            inject(opening,"coupons",couponRows);inject(opening,"requests",ledger);inject(opening,"points",pointService);inject(opening,"bags",bagService);inject(opening,"transfers",transferRows);inject(opening,"cloud",cloud);
            inject(opening,"transferIndex",new PointtransferRedcRepository(){public String getName(){return "transfer_index";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("post-index",q,a);}});
            inject(opening,"cache",new MembershipCache(){public void remove(String user){Assert.assertFalse(ArticlePublicationCaches.active());invalidations++;if(fault.equals("post-effect"))throw new IllegalStateException("PRIVATE_CACHE");}});
            inject(endpoint,"service",opening);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("writer","reader","other").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":actor)).put("scope",token.equals("reader")?"profile.read":"activity.write"):null;}});
        }
        @Override Transaction begin(){
            Transaction tx=super.begin();final Connection previous=writer;
            writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                try {
                    if(method.getName().equals("prepareStatement")){
                        final String q=(String)args[0];final PreparedStatement ps=(PreparedStatement)method.invoke(previous,args);
                        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                            try {Object result=m.invoke(ps,a);if(m.getName().equals("executeUpdate")&&writeFault(q))throw new SQLException("PRIVATE_WRITE");return result;}
                            catch(InvocationTargetException e){throw e.getCause();}
                        });
                    }
                    return method.invoke(previous,args);
                }catch(InvocationTargetException e){throw e.getCause();}
            });
            try{var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(delegate,writer);}catch(Exception e){throw new IllegalStateException(e);}
            return tx;
        }
        boolean writeFault(String q){return (fault.equals("points-write")&&q.startsWith("UPDATE x_user"))||(fault.equals("transfer-write")&&q.contains("INSERT INTO transfers"))
                ||(fault.equals("coupon-write")&&q.startsWith("UPDATE `coupons`"))||(fault.equals("membership-write")&&(q.startsWith("INSERT INTO `members`")||q.startsWith("UPDATE `members`")))
                ||(fault.equals("activation-write")&&q.startsWith("INSERT INTO `activations`"))||(fault.equals("bag-write")&&(q.startsWith("INSERT INTO bags")||q.startsWith("UPDATE bags")))
                ||(fault.equals("receipt-write")&&q.startsWith("UPDATE receipts"))||(fault.equals("post-index-write")&&q.startsWith("INSERT INTO `transfer_index`"));}
        void exec(String q,Object...a)throws RepositoryException{sql(writer,q,a);}
        void insert(String table,JSONObject value)throws RepositoryException{var fields=new ArrayList<>(value.keySet());String columns=String.join(",",fields);String marks=String.join(",",Collections.nCopies(fields.size(),"?"));exec("INSERT INTO "+table+" ("+columns+") VALUES ("+marks+")",fields.stream().map(value::get).toArray());}
        List<JSONObject> query(String stage,String q,Object...a)throws RepositoryException{
            if(fault.equals(stage))throw new RepositoryException("PRIVATE_READ");
            Assert.assertTrue(delegate!=null&&delegate.isActive());return rows(writer,q,a);
        }
        void plan(String id,String code,int price)throws RepositoryException{sql(observer,"INSERT INTO plans VALUES(?,?,?,?,'YEAR',365,?)",id,code,code,price,"{\"bold\":false,\"underline\":false,\"autoCheckin\":true,\"checkinCard\":20}");}
        JSONObject member()throws RepositoryException{var r=rows(observer,"SELECT * FROM members WHERE userId=?",actor);return r.isEmpty()?null:r.getFirst();}
        JSONObject bag()throws RepositoryException{var r=rows(observer,"SELECT data FROM bags WHERE userId=?",actor);return r.isEmpty()?new JSONObject():new JSONObject(r.getFirst().getString("data"));}
        long count(String table)throws RepositoryException{return scalar("SELECT COUNT(*) AS value FROM "+table);}
        long balance()throws RepositoryException{return rows(observer,"SELECT userPoint FROM x_user WHERE oId=?",actor).getFirst().getLong("userPoint");}
        JSONObject buy(String key,String level,String coupon)throws Exception{return opening.open(actor,new ApiV3MembershipOpeningInputs.Opening(level,coupon),key);}
        RequestContext post(String token,String key,String body,boolean route)throws Exception{
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/me/membership?apiKey=legacy&userId=2",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try {raw.headers().set("Content-Type","application/json");raw.headers().set("X-Real-IP","127.0.1."+(Integer.parseInt(actor)%250+1));raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy","userId","2")));var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));
                if(route){c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->endpoint.open(context));c.handle();}
                else{c.addHandler(writes::handle);c.addHandler(endpoint::open);auth.activityWrite(c);}return c;
            }finally{raw.release();}
        }
    }
    public static void main(String[]args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new OpeningFixture()){
            MembershipOpeningRouteBindings.register(f.endpoint,f.auth,f.writes);Dispatcher.mapping();var c=f.post("writer","basic","{\"levelId\":\"10\"}",true);Assert.assertEquals(c.attr("test.status"),200);var result=(JSONObject)c.attr("test.data");Assert.assertEquals(result.keySet(),Set.of("id","levelCode","paidPoints","expiresAt"));Assert.assertEquals(f.balance(),99000);Assert.assertEquals(f.bag().getInt("checkin1day"),20);Assert.assertEquals(f.count("activations"),1);Assert.assertEquals(f.count("transfers"),1);Assert.assertEquals(f.count("transfer_index"),2);
            JSONObject defaults=new JSONObject(f.member().getString("configJson"));Assert.assertTrue(defaults.getBoolean("autoCheckin"));Assert.assertFalse(defaults.getBoolean("bold"));long expires=LocalDate.now().plusDays(366).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();Assert.assertEquals(result.getLong("expiresAt"),expires);Assert.assertEquals(f.buy("basic","10","").getLong("expiresAt"),expires);Assert.assertEquals(f.balance(),99000);Assert.assertEquals(f.bag().getInt("checkin1day"),20);Assert.assertEquals(f.count("transfer_index"),2);
            var replay=f.post("writer","basic","{\"levelId\":\"10\"}",false);Assert.assertEquals(replay.attr("test.status"),200);Assert.assertEquals(((JSONObject)replay.attr("test.data")).keySet(),Set.of("id","levelCode","paidPoints","expiresAt"));Assert.assertEquals(f.count("transfer_index"),2);
            var receipt=new JSONObject(rows(f.observer,"SELECT value FROM receipts").getFirst().getString("value"));var durable=new JSONObject(receipt.getString("responseJson"));Assert.assertEquals(durable.keySet(),Set.of("result","indexTransferId"));Assert.assertFalse(String.valueOf(replay.attr("test.data")).contains("indexTransferId"));
            var ledger=rows(f.observer,"SELECT dataId,sourceRequestId FROM transfers").getFirst();Assert.assertTrue(ledger.getString("dataId").startsWith("开通 "));Assert.assertFalse(ledger.getString("dataId").contains(ledger.getString("sourceRequestId")));
        }
        System.out.println("PASS 1 actual route/auth/SQL/point transfer/bag defaults and local natural-date expiry commit atomically, replay restores indexing without duplicate charge or gift");
        for(String[] choice:new String[][]{{"11","checkin1day","50"},{"12","checkin1day","120"},{"13","sysCheckinRemain","366"},{"14","none","0"}})try(var f=new OpeningFixture()){
            f.buy("gift",choice[0],"");Assert.assertEquals(f.bag().optInt(choice[1]),Integer.parseInt(choice[2]));Assert.assertEquals(f.count("members"),1);
        }
        System.out.println("PASS 2 exact annual plan starter gifts and no monthly gift preserve all other config/benefits");
        try(var f=new OpeningFixture()){
            var r=f.buy("coupon","10"," savehalf ");Assert.assertEquals(r.getInt("paidPoints"),500);Assert.assertEquals(f.balance(),99500);Assert.assertEquals(f.scalar("SELECT times AS value FROM coupons WHERE oId='30'"),1);Assert.assertEquals(f.buy("coupon","10","savehalf").getInt("paidPoints"),500);Assert.assertEquals(f.scalar("SELECT times AS value FROM coupons WHERE oId='30'"),1);
            sql(f.observer,"UPDATE members SET expiresAt=1");f.buy("free","11","FREEZERO");Assert.assertEquals(f.balance(),99500);Assert.assertEquals(f.scalar("SELECT times AS value FROM coupons WHERE oId='34'"),0);Assert.assertEquals(f.bag().getInt("checkin1day"),70);
        }
        try(var f=new OpeningFixture()){f.buy("unlimited","10","UNLIMITED");Assert.assertEquals(f.balance(),99750);Assert.assertEquals(f.scalar("SELECT times AS value FROM coupons WHERE oId='31'"),-1);}
        System.out.println("PASS 3 case-insensitive stored SQL coupon matching, ASCII trim, rounded discount/free/unlimited and one-time consumption follow confirmed rules");
        for(String code:List.of("UNKNOWN0","USEDUP00","NOTDISC0"))try(var f=new OpeningFixture()){error(422,()->f.buy("bad","10",code));Assert.assertEquals(f.balance(),100000);Assert.assertEquals(f.count("receipts"),0);Assert.assertNull(f.member());Assert.assertTrue(f.bag().isEmpty());}
        for(String column:List.of("discount=-1","discount=101","times=-2"))try(var f=new OpeningFixture()){sql(f.observer,"UPDATE coupons SET "+column+" WHERE oId='30'");error(422,()->f.buy("bad","10","SAVEHALF"));Assert.assertEquals(f.count("transfers"),0);}
        System.out.println("PASS 4 missing/non-discount/exhausted/out-of-policy coupons return 422 without charge, gift, activation or receipt");
        for(long[] state:new long[][]{{1,Long.MAX_VALUE},{1,0},{0,0}})try(var f=new OpeningFixture()){sql(f.observer,"INSERT INTO members VALUES('90',?,'VIP1_YEAR',?,?, '{}',1,1)",f.actor,state[0],state[1]);error(409,()->f.buy("active","10","SAVEHALF"));Assert.assertEquals(f.count("transfers"),0);Assert.assertEquals(f.scalar("SELECT times AS value FROM coupons WHERE oId='30'"),2);}
        for(long[] state:new long[][]{{0,Long.MAX_VALUE},{1,1},{0,1}})try(var f=new OpeningFixture()){sql(f.observer,"INSERT INTO members VALUES('90',?,'VIP1_YEAR',?,?, '{\"bold\":true}',1,1)",f.actor,state[0],state[1]);f.buy("inactive","10","");Assert.assertFalse(new JSONObject(f.member().getString("configJson")).getBoolean("bold"));}
        try(var f=new OpeningFixture()){sql(f.observer,"UPDATE x_user SET userPoint=5 WHERE oId=?",f.actor);error(409,()->f.buy("poor","10","SAVEHALF"));Assert.assertEquals(f.count("transfers"),0);Assert.assertEquals(f.count("receipts"),0);}
        System.out.println("PASS 5 active/permanent purchases reject, inactive nonpermanent and expired renew with server defaults; insufficient balance does not consume coupon");
        for(String fault:List.of("user","level","coupon","membership","points-write","transfer-write","coupon-write","membership-write","activation-write","bag-write","receipt-write","commit","savepoint","release"))try(var f=new OpeningFixture()){
            f.fault=fault;error(503,()->f.buy("failure","10","SAVEHALF"));Assert.assertEquals(f.balance(),100000,fault);Assert.assertEquals(f.count("transfers"),0,fault);Assert.assertEquals(f.count("members"),0,fault);Assert.assertEquals(f.count("activations"),0,fault);Assert.assertEquals(f.count("bags"),0,fault);Assert.assertEquals(f.count("receipts"),0,fault);Assert.assertEquals(f.scalar("SELECT times AS value FROM coupons WHERE oId='30'"),2,fault);Assert.assertTrue(f.indexed.isEmpty());f.fault="";f.buy("failure","10","SAVEHALF");Assert.assertEquals(f.balance(),99500);
        }
        System.out.println("PASS 6 every read/write/points/bag/receipt/savepoint/strict JDBC commit fault rolls back the whole purchase and same-key retry recovers");
        try(var f=new OpeningFixture()){
            f.fault="lost-ack";error(503,()->f.buy("ack","10","SAVEHALF"));Assert.assertEquals(f.balance(),99500);Assert.assertEquals(f.count("receipts"),1);Assert.assertEquals(f.bag().getInt("checkin1day"),20);Assert.assertEquals(f.count("transfer_index"),0);f.fault="";f.buy("ack","10","SAVEHALF");Assert.assertEquals(f.balance(),99500);Assert.assertEquals(f.count("activations"),1);Assert.assertEquals(f.count("transfer_index"),2);
            sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);error(404,()->f.buy("ack","10","SAVEHALF"));
        }
        try(var f=new OpeningFixture()){f.fault="post-effect";f.buy("effect","10","");Assert.assertEquals(f.balance(),99000);Assert.assertEquals(f.count("members"),1);}
        for(String fault:List.of("post-index","post-index-write"))try(var f=new OpeningFixture()){f.fault=fault;f.buy("index-retry","10","");Assert.assertEquals(f.count("transfer_index"),0);Assert.assertEquals(f.balance(),99000);f.fault="";f.buy("index-retry","10","");f.buy("index-retry","10","");Assert.assertEquals(f.count("transfer_index"),2);Assert.assertEquals(f.balance(),99000);}
        System.out.println("PASS 7 committed-but-lost acknowledgement replays the durable receipt and repairs postcommit indexing; revoked owner fails replay and postcommit effects cannot reverse success");
        try(var f=new OpeningFixture()){
            for(String token:Arrays.asList(null,"invalid","reader"))Assert.assertEquals(f.post(token,"auth","{\"levelId\":\"10\"}",false).attr("test.status"),token==null||token.equals("invalid")?401:403);
            for(String body:List.of("{}","{\"levelId\":10}","{\"levelId\":\"10\",\"price\":0}","{\"levelId\":\"10\",\"userId\":\"2\"}","{\"levelId\":\"10\",\"configJson\":\"{}\"}","{\"levelId\":\"10\",\"couponCode\":\"short\"}","{\"levelId\":\"10\",\"levelId\":\"11\"}"))Assert.assertNotEquals(f.post("writer","bad",body,false).attr("test.status"),200);
            Assert.assertEquals(f.post("writer",null,"{\"levelId\":\"10\"}",false).attr("test.status"),422);Assert.assertEquals(f.post("writer","large"," ".repeat(4097),false).attr("test.status"),413);Assert.assertEquals(f.count("receipts"),0);
            f.buy("conflict","10","");error(409,()->f.buy("conflict","11",""));
        }
        System.out.println("PASS 8 body/key/owner/price/config forging, duplicate JSON and byte boundaries reject before mutation, valid receipt keys bind complete normalized requests");
        for(String invalid:List.of("UPDATE plans SET price=1.5 WHERE oId='10'","UPDATE plans SET durationValue=0 WHERE oId='10'","UPDATE coupons SET discount='PRIVATE' WHERE oId='30'","INSERT INTO coupons SELECT '39',coupon_code,coupon_type,discount,times,updatedAt FROM coupons WHERE oId='30'","INSERT INTO members VALUES('90',ACTOR,'VIP1_YEAR',7,1,'{}',1,1)","INSERT INTO members VALUES('bad-id',ACTOR,'VIP1_YEAR',1,1,'{}',1,1)","INSERT INTO bags VALUES('91',ACTOR,'sys-bag','{\"checkin1day\":\"bad\"}')","INSERT INTO bags VALUES('91',ACTOR,'sys-bag','{}'),('92',ACTOR,'sys-bag','{}')","INSERT INTO members VALUES('90',ACTOR,'VIP1_YEAR',1,1,'{}',1,1),('91',ACTOR,'VIP1_YEAR',1,1,'{}',1,1)"))try(var f=new OpeningFixture()){
            sql(f.observer,invalid.replace("ACTOR","'"+f.actor+"'"));error(503,()->f.buy("malformed","10","SAVEHALF"));Assert.assertEquals(f.balance(),100000);Assert.assertEquals(f.count("receipts"),0);
        }
        try(var f=new OpeningFixture()){sql(f.observer,"INSERT INTO bags VALUES('91',?,'sys-bag','{\"checkin1day\":2147483647,\"other\":8}')",f.actor);f.buy("cap","10","");Assert.assertEquals(f.bag().getInt("checkin1day"),Integer.MAX_VALUE);Assert.assertEquals(f.bag().getInt("other"),8);}
        System.out.println("PASS 9 malformed numeric/duplicate coupon/member/bag data fails closed without partial payments; legacy gift-cap saturation preserves unrelated items");
        try(var f=new OpeningFixture()){
            sql(f.observer,"UPDATE plans SET lvName=? WHERE oId='10'","<script>"+"😀&<>".repeat(8));f.buy("html","10","");var r=rows(f.observer,"SELECT dataId,sourceRequestId FROM transfers").getFirst();Assert.assertFalse(r.getString("dataId").contains("<"));Assert.assertTrue(r.getString("dataId").contains("&lt;"));Assert.assertTrue(r.getString("dataId").length()<=128);
            var plan=rows(f.observer,"EXPLAIN QUERY PLAN SELECT oId FROM transfer_index WHERE userId=? AND rawId=? LIMIT 2",f.actor,rows(f.observer,"SELECT oId FROM transfers").getFirst().getString("oId"));Assert.assertTrue(plan.toString().contains("idx_membership_transfer_owner"));
            var receipt=rows(f.observer,"SELECT oId,value FROM receipts").getFirst();var row=new JSONObject(receipt.getString("value"));var durable=new JSONObject(row.getString("responseJson"));durable.put("indexTransferId","bad");row.put("responseJson",durable.toString());sql(f.observer,"UPDATE receipts SET value=? WHERE oId=?",row.toString(),receipt.getString("oId"));error(503,()->f.buy("html","10",""));Assert.assertEquals(f.balance(),99000);Assert.assertEquals(f.count("transfer_index"),2);
        }
        System.out.println("PASS 10 user-facing ledger labels encode bounded whole codepoints without billing hashes; indexed recipient repair and malformed private receipt fail closed");
        for(String[] day:new String[][]{{"America/New_York","2026-03-07T12:00:00","2026-03-09T00:00:00"},{"Asia/Shanghai","2028-02-28T12:00:00","2028-03-01T00:00:00"}})try(var f=new OpeningFixture()){
            f.timeZone=ZoneId.of(day[0]);f.clock=LocalDateTime.parse(day[1]).atZone(f.timeZone).toInstant().toEpochMilli();sql(f.observer,"UPDATE plans SET durationValue=1 WHERE oId='10'");var r=f.buy("calendar","10","");Assert.assertEquals(Instant.ofEpochMilli(r.getLong("expiresAt")).atZone(f.timeZone).toLocalDateTime().toString(),day[2].substring(0,16));
        }
        System.out.println("PASS 11 fixed clock natural-day expiration crosses DST and leap-month boundaries at local midnight rather than fixed 24-hour arithmetic");
        final var legacy=new MembershipMgmtService();final var checked=new java.util.concurrent.atomic.AtomicInteger();
        final var legacyPoints=new PointtransferMgmtService();inject(legacyPoints,"pointtransferRepository",new PointtransferRepository(){public boolean hasTransactionBegun(){return false;}});inject(legacy,"pointtransferMgmtService",legacyPoints);
        inject(legacy,"membershipRepository",new MembershipRepository(){public Transaction beginTransaction(){Assert.assertTrue(AccountPairLocks.lock("123",org.b3log.symphony.model.Pointtransfer.ID_C_SYS).isHeldByCurrentThread());checked.incrementAndGet();throw new IllegalStateException("controlled pre-DB interruption");}});
        for(String id:List.of("123",""))try{legacy.openMembership(id,"10","{}","");Assert.fail("expected controlled failure");}catch(org.b3log.latke.service.ServiceException expected){}
        Assert.assertEquals(checked.get(),1);Assert.assertFalse(AccountPairLocks.lock("123",org.b3log.symphony.model.Pointtransfer.ID_C_SYS).isHeldByCurrentThread());
        System.out.println("PASS 12 actual legacy membership wrapper compiles and holds account pair before DB begin, releases on interruption, rejects blank parameters before DB");
        System.out.println("Membership opening isolated tests passed: 12 groups");
    }
}
