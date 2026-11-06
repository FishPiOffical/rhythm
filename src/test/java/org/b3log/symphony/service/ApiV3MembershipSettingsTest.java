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
import org.b3log.symphony.cache.MembershipCache;
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

/** Actual SQL/auth/controller/strict JDBC; shared SQLite connection and explicit external-cache adapter. */
public class ApiV3MembershipSettingsTest extends ApiV3ProfessionPrimaryTest {
    static final String PATH="/api/v3/me/membership/config";
    static final String BENEFITS="{\"bold\":false,\"underline\":false,\"color\":\"#112233\",\"autoCheckin\":false,\"checkinCard\":20}";
    static final String STORED="{\"bold\":false,\"underline\":true,\"color\":\"#ABCDEF\",\"autoCheckin\":true,\"checkinCard\":8,\"opaque\":{\"secret\":\"PRIVATE\"}}";
    static class MemberFixture extends Fixture {
        final ApiV3MembershipSettingsService settings=new ApiV3MembershipSettingsService();
        final ApiV3MembershipSettingsProcessor endpoint=new ApiV3MembershipSettingsProcessor();
        int invalidations;
        boolean deactivateOnBegin,changeTierOnLock,cacheFailure;
        MemberFixture()throws Exception {
            super();
            sql(observer,"CREATE TABLE member(oId TEXT,userId TEXT,lvCode TEXT,state,expiresAt,configJson TEXT,createdAt INT,updatedAt INT)");
            sql(observer,"CREATE TABLE tier(oId TEXT PRIMARY KEY,lvCode TEXT,benefits TEXT)");
            sql(observer,"INSERT INTO tier VALUES('10','VIP4_YEAR',?),('11','VIP4_YEAR',?)",BENEFITS,BENEFITS.replace("20","50"));
            sql(observer,"INSERT INTO member VALUES('90',?,'VIP4_YEAR',1,?, ?,1,2)",actor,System.currentTimeMillis()+86400000L,STORED);
            inject(settings,"users",new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return read("user",q,a);}});
            inject(settings,"levels",new MembershipLevelRepository(){public String getName(){return "tier";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return read("level",q,a);}});
            inject(settings,"memberships",new MembershipRepository(){public String getName(){return "member";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{
                if(changeTierOnLock&&q.endsWith(" FOR UPDATE")){changeTierOnLock=false;sql(writer,"UPDATE member SET lvCode='VIP1_YEAR' WHERE userId=?",actor);}
                return read("member",q,a);
            }});
            var field=ApiV3ProfessionPrimaryService.class.getDeclaredField("idempotency");field.setAccessible(true);var coordinator=field.get(service);
            field=ApiV3IdempotencyMgmtService.class.getDeclaredField("requestRepository");field.setAccessible(true);inject(settings,"requests",field.get(coordinator));
            inject(settings,"cache",new MembershipCache(){public void remove(String id){Assert.assertTrue(delegate==null||!delegate.isActive());invalidations++;if(cacheFailure)throw new IllegalStateException("PRIVATE_CACHE");}});
            inject(endpoint,"service",settings);
        }
        @Override Transaction begin(){
            try{
                if(deactivateOnBegin){deactivateOnBegin=false;sql(observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",actor);}
                final Transaction tx=super.begin();final Connection inner=writer;
                writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                    try{
                        if(m.getName().equals("prepareStatement")){
                            final String q=(String)a[0];final PreparedStatement st=(PreparedStatement)m.invoke(inner,a);
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p2,m2,a2)->{
                                try{Object result=m2.invoke(st,a2);if(m2.getName().equals("executeUpdate")&&q.startsWith("UPDATE `member`")){mutations++;if(Set.of("business","rollback-point").contains(fault))throw new SQLException("PRIVATE_BUSINESS");}return result;}
                                catch(InvocationTargetException e){throw e.getCause();}
                            });
                        }
                        return m.invoke(inner,a);
                    }catch(InvocationTargetException e){throw e.getCause();}
                });
                var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(delegate,writer);return tx;
            }catch(Exception e){throw new IllegalStateException(e);}
        }
        List<JSONObject> read(String stage,String q,Object...a)throws RepositoryException{
            Assert.assertNotNull(JdbcRepository.TX.get());Assert.assertFalse(q.contains("SELECT *"));
            if(q.endsWith(" FOR UPDATE"))locks.add(stage);
            if(fault.equals(stage))throw new RepositoryException("PRIVATE_READ");
            return rows(writer,q,a);
        }
        JSONObject member()throws Exception{return rows(observer,"SELECT * FROM member WHERE userId=?",actor).getFirst();}
        JSONObject config()throws Exception{return new JSONObject(member().getString("configJson"));}
        RequestContext call(String token,String key,String body,boolean routed)throws Exception{
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.PUT,PATH+"?apiKey=legacy&userId=2",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{
                raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","2001:db8::"+actor);
                if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var req=new Request(null,raw);req.setBytes(body.getBytes(StandardCharsets.UTF_8));req.setParams(new HashMap<>(Map.of("userId","2","apiKey","legacy")));
                var ctx=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));ctx.attr("user",new JSONObject().put("oId","2"));
                if(routed){ctx.addHandler(new org.b3log.latke.http.handler.RouteHandler());ctx.addHandler(endpoint::update);ctx.handle();}
                else{ctx.addHandler(writes::handle);ctx.addHandler(endpoint::update);auth.settingsWrite(ctx);}return ctx;
            }finally{raw.release();}
        }
        RequestContext put(String key,String body)throws Exception{return call("writer",key,body,false);}
    }
    static JSONObject data(RequestContext ctx){Assert.assertEquals(ctx.attr("test.status"),200);return (JSONObject)ctx.attr("test.data");}
    static JSONObject level(String code,String benefits){return new JSONObject().put("lvCode",code).put("benefits",benefits);}
    public static void main(String[]args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new MemberFixture()){
            MembershipSettingsRouteBindings.register(f.endpoint,f.auth,f.writes);Dispatcher.mapping();
            var before=f.member();var out=data(f.call("writer","route","{\"bold\":true}",true));
            Assert.assertEquals(out.keySet(),Set.of("config"));Assert.assertEquals(out.getJSONObject("config").keySet(),Set.of("bold","underline","color"));
            Assert.assertEquals(f.locks,List.of("user","user","level","member"));
            Assert.assertTrue(f.config().getBoolean("bold"));Assert.assertTrue(f.config().getBoolean("underline"));Assert.assertTrue(f.config().getBoolean("autoCheckin"));Assert.assertEquals(f.config().getInt("checkinCard"),8);Assert.assertEquals(f.config().getJSONObject("opaque").getString("secret"),"PRIVATE");
            for(String field:List.of("state","expiresAt","lvCode","createdAt","oId","userId"))Assert.assertEquals(f.member().get(field),before.get(field));
            Assert.assertEquals(f.scalar("SELECT SUM(userPoint) AS value FROM x_user"),1400L);Assert.assertEquals(f.invalidations,1);
            Assert.assertEquals(out.toString().contains("PRIVATE"),false);
        }
        System.out.println("PASS 1 actual PUT/settings.write/auth/controller/SQL, owner-level-membership lock order, partial display-only updates preserve all entitlements and metadata without charging");
        try(var f=new MemberFixture()){
            data(f.put("first","{\"bold\":true}"));data(f.put("later","{\"bold\":false,\"color\":\"rainbow\"}"));int writes=f.mutations;
            data(f.put("first","{\"bold\":true}"));Assert.assertEquals(f.mutations,writes);Assert.assertFalse(f.config().getBoolean("bold"));Assert.assertEquals(f.config().getString("color"),"rainbow");
            Assert.assertEquals(f.put("first","{\"bold\":false}").attr("test.status"),409);
            sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);Assert.assertEquals(f.put("first","{\"bold\":true}").attr("test.status"),404);
        }
        try(var f=new MemberFixture()){f.deactivateOnBegin=true;Assert.assertEquals(f.put("race","{\"bold\":true}").attr("test.status"),404);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);}
        System.out.println("PASS 2 idempotent replay cannot overwrite later display values; conflicting content and current-account revocation/race fail before mutation or receipt replay");
        for(String code:List.of("VIP1_YEAR","VIP4_YEAR","VIP4_MONTH","MY_VIP4_YEAR","VIP4")){
            var level=level(code,BENEFITS);var defaults=ApiV3MembershipConfiguration.defaults(level);defaults.put("autoCheckin",true);
            Assert.assertFalse(ApiV3MembershipConfiguration.defaults(level).getBoolean("autoCheckin"));
            for(String effect:List.of("rainbow","neon","fire","ocean","forest","sunset","metal","galaxy")) {
                if(Set.of("VIP4_YEAR","VIP4_MONTH").contains(code))Assert.assertEquals(ApiV3MembershipConfiguration.merge(level,STORED,new JSONObject().put("color",effect)).getString("color"),effect);
                else error(422,()->ApiV3MembershipConfiguration.merge(level,STORED,new JSONObject().put("color",effect)));
            }
            Assert.assertEquals(ApiV3MembershipConfiguration.merge(level,STORED,new JSONObject().put("color","#aAbBcC")).getString("color"),"#aAbBcC");
        }
        error(422,()->ApiV3MembershipConfiguration.merge(level("VIP1_YEAR","{\"bold\":false}"),"{}",new JSONObject().put("color","#000000")));
        for(String body:List.of("{}","{\"autoCheckin\":true}","{\"bold\":1}","{\"underline\":null}","{\"color\":\" red \"}","{\"color\":\"#123\"}","{\"color\":\"RAINBOW\"}"))error(422,()->ApiV3MembershipConfiguration.validateChanges(new JSONObject(body)));
        error(503,()->ApiV3MembershipConfiguration.defaults(level("VIP1_YEAR","{\"color\":\"red\"}")));
        System.out.println("PASS 3 supported-field/strict-boolean/hex rules and exact VIP4 codes protect all eight effects; defaults are service-derived deep copies and malformed templates fail closed");
        try(var f=new MemberFixture()){
            sql(f.observer,"UPDATE tier SET benefits=? WHERE oId='11'",BENEFITS.replace("#112233","#445566").replace("\"bold\":false","\"bold\":true"));
            sql(f.observer,"UPDATE member SET configJson='' WHERE userId=?",f.actor);data(f.put("empty","{\"bold\":true}"));Assert.assertEquals(f.config().keySet(),Set.of("bold"));Assert.assertTrue(f.config().getBoolean("bold"));
            Assert.assertFalse(f.config().has("autoCheckin"));Assert.assertFalse(f.config().has("checkinCard"));Assert.assertFalse(f.config().has("color"));
            sql(f.observer,"UPDATE member SET configJson='   ' WHERE userId=?",f.actor);data(f.put("blank","{\"underline\":false}"));Assert.assertEquals(f.config().keySet(),Set.of("underline"));
            sql(f.observer,"UPDATE member SET configJson='{\"bold\":false}' WHERE userId=?",f.actor);data(f.put("missing","{\"bold\":true}"));Assert.assertEquals(f.config().keySet(),Set.of("bold"));
            sql(f.observer,"UPDATE tier SET benefits=? WHERE oId='11'",BENEFITS.replace("\"color\":\"#112233\",",""));Assert.assertEquals(f.put("ambiguous","{\"bold\":false}").attr("test.status"),503);
        }
        System.out.println("PASS 4 same-code duration tiers may differ in display defaults and entitlements but must agree on supported display fields/types; blank and sparse config remain sparse and never acquire readonly defaults or unsubmitted display fields");
        for(String change:List.of("state=0","expiresAt=1","configJson='BROKEN'"))try(var f=new MemberFixture()){
            String before=f.member().toString();sql(f.observer,"UPDATE member SET "+change+" WHERE userId=?",f.actor);before=f.member().toString();
            Assert.assertEquals(f.put("bad","{\"bold\":true}").attr("test.status"),change.equals("configJson='BROKEN'")?503:404);Assert.assertEquals(f.member().toString(),before);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        try(var f=new MemberFixture()){sql(f.observer,"UPDATE member SET expiresAt=0");data(f.put("permanent","{\"bold\":true}"));}
        try(var f=new MemberFixture()){f.changeTierOnLock=true;Assert.assertEquals(f.put("tier-race","{\"bold\":true}").attr("test.status"),503);Assert.assertEquals(f.member().getString("lvCode"),"VIP4_YEAR");}
        try(var f=new MemberFixture()){sql(f.observer,"INSERT INTO member SELECT '91',userId,lvCode,state,expiresAt,configJson,createdAt,updatedAt FROM member");Assert.assertEquals(f.put("duplicate","{\"bold\":true}").attr("test.status"),503);}
        System.out.println("PASS 5 inactive/expired/duplicate/malformed memberships and changed tier snapshots fail without writes; permanent active memberships may edit display");
        for(String body:List.of("{}","[]","{\"owner\":\"2\"}","{\"configJson\":\"{}\"}","{\"bold\":true,\"bold\":false}"))try(var f=new MemberFixture()){Assert.assertNotEquals(f.put("bad",body).attr("test.status"),200);Assert.assertEquals(f.mutations,0);}
        try(var f=new MemberFixture()){
            for(String token:Arrays.asList(null,"invalid","reader"))Assert.assertEquals(f.call(token,"auth","{\"bold\":true}",false).attr("test.status"),token==null||token.equals("invalid")?401:403);
            Assert.assertEquals(f.call("writer",null,"{\"bold\":true}",false).attr("test.status"),422);Assert.assertEquals(f.put("large"," ".repeat(4097)).attr("test.status"),413);Assert.assertEquals(f.put("utf8","中".repeat(1366)).attr("test.status"),413);
            Assert.assertEquals(f.mutations,0);
        }
        System.out.println("PASS 6 transport rejects owner/Cookie/apiKey forgery, missing/wrong Bearer scope, duplicate/unknown JSON, missing key and raw UTF-8 body overflow");
        for(String fault:List.of("placeholder","user","level","member","business","receipt","commit","savepoint","release"))try(var f=new MemberFixture()){
            final String before=f.member().toString();f.fault=fault;Assert.assertEquals(f.put("fault","{\"bold\":true}").attr("test.status"),503,fault);Assert.assertEquals(f.member().toString(),before);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);Assert.assertNull(JdbcRepository.TX.get());
            f.fault="";data(f.put("fault","{\"bold\":true}"));
        }
        System.out.println("PASS 7 actual JDBC/savepoint/root and receipt faults roll back config and receipt together, recover with same key, and invalidate only after transaction end");
        try(var f=new MemberFixture()){
            f.fault="lost-ack";Assert.assertEquals(f.put("lost","{\"bold\":true}").attr("test.status"),503);Assert.assertTrue(f.config().getBoolean("bold"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),1L);
            f.fault="";data(f.put("newer","{\"bold\":false}"));int writes=f.mutations;data(f.put("lost","{\"bold\":true}"));Assert.assertEquals(f.mutations,writes);Assert.assertFalse(f.config().getBoolean("bold"));
            f.cacheFailure=true;data(f.put("cache-error","{\"underline\":false}"));Assert.assertFalse(f.config().getBoolean("underline"));
        }
        System.out.println("PASS 8 committed-but-lost acknowledgement recovers the original receipt without overwriting newer config; cache invalidation failure never reverses committed success");
        try(var f=new MemberFixture()){
            try{f.settings.updateInCurrentTransaction(f.actor,new JSONObject().put("bold",true));Assert.fail();}catch(IllegalStateException expected){}
            var tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="business";try{f.settings.updateInCurrentTransaction(f.actor,new JSONObject().put("bold",true));Assert.fail();}catch(RepositoryException expected){}f.fault="";tx.commit();Assert.assertFalse(f.config().getBoolean("bold"));Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);
        }
        System.out.println("PASS 9 primitive requires caller-owned transaction and preserves unrelated work when its savepoint rolls back");
        for(String value:List.of(" ".repeat(102401),"中".repeat(34134),"{\"opaque\":\""+"</".repeat(40000)+"\"}","{\"opaque\":\""+String.valueOf((char)0xD800)+"\"}")) {
            error(503,()->ApiV3MembershipConfiguration.defaults(level("VIP4_YEAR",value)));
            error(503,()->ApiV3MembershipConfiguration.merge(level("VIP4_YEAR",BENEFITS),value,new JSONObject().put("bold",true)));
        }
        try(var f=new MemberFixture()){
            for(int i=12;i<75;i++)sql(f.observer,"INSERT INTO tier VALUES(?,'VIP4_YEAR',?)",String.valueOf(i),BENEFITS);
            Assert.assertEquals(f.put("budget","{\"bold\":true}").attr("test.status"),503);Assert.assertFalse(f.config().getBoolean("bold"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        System.out.println("PASS 10 oversized blank/UTF-8 stored templates and configuration, invalid Unicode, and over-budget tier sets fail closed without arbitrary grants or truncated selection");
        System.out.println("Membership settings isolated tests passed: 10 groups");
    }
}
