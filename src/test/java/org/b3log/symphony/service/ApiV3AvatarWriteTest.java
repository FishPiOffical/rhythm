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
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.middleware.*;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Actual settings HTTP/coordinator/current-owner mutation. SQLite and cache/token adapters; no MySQL/server/image fetch. */
public class ApiV3AvatarWriteTest {
    static void inject(Object target,Class<?> type,String name,Object value)throws Exception {var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static class Fixture implements AutoCloseable {
        final String group,actor;
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3SettingsProcessor controller=new ApiV3SettingsProcessor();final ApiV3AuthMidware auth=new ApiV3AuthMidware();final ApiV3WriteMidware writes=new ApiV3WriteMidware();
        boolean failWrite,failReceipt,failCommit;int updates,refreshes;
        final UserRepository users=new UserRepository(){
            @Override public boolean hasTransactionBegun(){try{return !db.getAutoCommit();}catch(SQLException e){throw new IllegalStateException(e);}}
            @Override public JSONObject getForUpdate(String id)throws RepositoryException {Assert.assertTrue(hasTransactionBegun());return row("SELECT * FROM users WHERE oId=?",id);}
            @Override public void updateSettingsInCurrentTransaction(String id,JSONObject values)throws RepositoryException {
                Assert.assertTrue(hasTransactionBegun());updates++;
                if ("avatar".equals(group)) {
                    Assert.assertEquals(values.keySet(),Set.of("userAvatarURL","userAvatarType","userUpdateTime"));
                    sql("UPDATE users SET userAvatarURL=?,userAvatarType=?,userUpdateTime=? WHERE oId=?",values.get("userAvatarURL"),values.get("userAvatarType"),values.get("userUpdateTime"),id);
                } else {
                    Assert.assertEquals(values.keySet(),Set.of("mbti","userUpdateTime"));
                    sql("UPDATE users SET mbti=?,userUpdateTime=? WHERE oId=?",values.get("mbti"),values.get("userUpdateTime"),id);
                }
                if(failWrite)throw new RepositoryException("injected write failure");
            }
            @Override public String getName(){return "users";}
            @Override public List<JSONObject> select(String text,Object...args)throws RepositoryException {var found=row(text,args);return found==null?List.of():List.of(found);}
            @Override public void refreshSettingsCache(String id){Assert.assertFalse(hasTransactionBegun());refreshes++;}
        };
        final ApiV3RequestRepository receipts=new ApiV3RequestRepository(){
            @Override public boolean hasTransactionBegun(){return users.hasTransactionBegun();}
            @Override public Transaction beginTransaction(){try{db.setAutoCommit(false);}catch(SQLException e){throw new IllegalStateException(e);}return new Transaction(){boolean active=true;public boolean isActive(){return active;}public void commit(){if(failCommit)throw new IllegalStateException("commit rejected");try{db.commit();db.setAutoCommit(true);active=false;}catch(SQLException e){throw new IllegalStateException(e);}}public void rollback(){try{db.rollback();db.setAutoCommit(true);active=false;}catch(SQLException e){throw new IllegalStateException(e);}}};}
            @Override public JSONObject getForUpdate(String id)throws RepositoryException {var result=row("SELECT value FROM receipts WHERE oId=?",id);return result==null?null:new JSONObject(result.getString("value"));}
            @Override public String add(JSONObject value)throws RepositoryException {sql("INSERT INTO receipts VALUES(?,?)",value.getString("oId"),value.toString());return value.getString("oId");}
            @Override public void update(String id,JSONObject value,String...fields)throws RepositoryException {sql("UPDATE receipts SET value=? WHERE oId=?",value.toString(),id);if(failReceipt)throw new RepositoryException("injected receipt failure");}
        };
        Fixture()throws Exception {this("avatar","1");}
        Fixture(String group,String actor)throws Exception {
            this.group=group;this.actor=actor;
            sql("CREATE TABLE users(oId TEXT PRIMARY KEY,userStatus INT,userAvatarURL TEXT,userAvatarType INT,userUpdateTime INT,userPassword TEXT,userPoint INT,mbti TEXT)");
            sql("INSERT INTO users VALUES(?,0,'https://old.example/one',0,1,'PRIVATE',100,'INTJ'),('2',0,'https://old.example/two',0,1,'OTHER',200,'ENFP')",actor);sql("CREATE TABLE receipts(oId TEXT PRIMARY KEY,value TEXT)");
            var primitive=new UserSettingsMgmtService();inject(primitive,UserSettingsMgmtService.class,"users",users);
            var coordinator=new ApiV3IdempotencyMgmtService();inject(coordinator,ApiV3IdempotencyMgmtService.class,"requestRepository",receipts);
            var mgmt=new ApiV3SettingsMgmtService();inject(mgmt,ApiV3SettingsMgmtService.class,"settings",primitive);inject(mgmt,ApiV3SettingsMgmtService.class,"idempotency",coordinator);
            inject(controller,ApiV3SettingsProcessor.class,"management",mgmt);
            var personalityQuery=new ApiV3PersonalityQueryService();inject(personalityQuery,ApiV3PersonalityQueryService.class,"users",users);inject(controller,ApiV3SettingsProcessor.class,"personalities",personalityQuery);
            var query=new ApiV3AvatarQueryService();inject(query,ApiV3AvatarQueryService.class,"users",users);inject(controller,ApiV3SettingsProcessor.class,"avatars",query);
            inject(auth,ApiV3AuthMidware.class,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){@Override public JSONObject authenticate(String token){return Set.of("writer","other","reader").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":actor)).put("scope",token.equals("reader")?"profile.read":"settings.write profile.read"):null;}});
        }
        void sql(String text,Object...args)throws RepositoryException {try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}catch(SQLException e){throw new RepositoryException(e);}}
        JSONObject row(String text,Object...args)throws RepositoryException {try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){if(!r.next())return null;var out=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)out.put(r.getMetaData().getColumnLabel(i),r.getObject(i));return out;}}catch(SQLException e){throw new RepositoryException(e);}}
        RequestContext put(String token,String key,String body,boolean route)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.PUT,"/api/v3/me/settings/"+group+"?userId=2&apiKey=legacy",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{raw.headers().set("X-Real-IP","127.0.0.1");raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy","userId","2")));var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                if(route){c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var r=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");Assert.assertEquals(r.getContextHandlerMeta().getInvokeHolder().getName(),"avatar".equals(group)?"updateAvatar":"updatePersonality");if("avatar".equals(group))controller.updateAvatar(context);else controller.updatePersonality(context);});c.handle();}
                else{c.addHandler(writes::handle);if("avatar".equals(group))c.addHandler(controller::updateAvatar);else c.addHandler(controller::updatePersonality);auth.settingsWrite(c);}return c;
            }finally{raw.release();}
        }
        String queryURL()throws Exception {var q=new ApiV3AvatarQueryService();inject(q,ApiV3AvatarQueryService.class,"users",users);return q.read("1").getString("avatarURL");}
        RequestContext put(String key,String value)throws Exception{return put("writer",key,new JSONObject().put("avatar".equals(group)?"avatarURL":"mbti",value).toString(),false);}
        String queryMBTI()throws Exception {var q=new ApiV3PersonalityQueryService();inject(q,ApiV3PersonalityQueryService.class,"users",users);return q.read(actor).getString("mbti");}
        public void close()throws Exception{db.close();}
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            AvatarRouteBindings.register(f.controller,f.auth,f.writes);Dispatcher.mapping();
            var c=f.put("writer","first","{\"avatarURL\":\"https://external.example/image?sig=a%2Fb\"}",true);Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(((JSONObject)c.attr("test.data")).keySet(),Set.of("avatarURL"));
            Assert.assertEquals(f.row("SELECT * FROM users WHERE oId='1'").getInt("userAvatarType"),2);Assert.assertEquals(f.row("SELECT * FROM users WHERE oId='2'").getInt("userAvatarType"),0);
            Assert.assertEquals(f.put("second","https://second.example/x").attr("test.status"),200);int updates=f.updates;
            Assert.assertEquals(((JSONObject)f.put("first","https://external.example/image?sig=a%2Fb").attr("test.data")).getString("avatarURL"),"https://external.example/image?sig=a%2Fb");Assert.assertEquals(f.updates,updates);
            Assert.assertEquals(f.queryURL(),"https://second.example/x");Assert.assertEquals(f.put("first","https://different.example/x").attr("test.status"),409);Assert.assertEquals(f.updates,updates);
            Assert.assertEquals(f.row("SELECT * FROM users WHERE oId='1'").getString("userPassword"),"PRIVATE");Assert.assertEquals(f.row("SELECT * FROM users WHERE oId='1'").getInt("userPoint"),100);Assert.assertTrue(f.refreshes>0);
        }
        System.out.println("PASS actual avatar PUT route, Bearer owner, signed external URL, strict field mutation and GET; replay returns receipt without overwriting newer avatar; conflict is 409; cache refresh is outside transaction");
        try(var f=new Fixture()){
            for(String token:Arrays.asList(null,"invalid","reader"))Assert.assertEquals(f.put(token,"auth","{\"avatarURL\":\"https://example.test/x\"}",false).attr("test.status"),token==null||token.equals("invalid")?401:403);
            for(String body:List.of("{}","{\"avatarURL\":null}","{\"avatarURL\":\"http://example.test/x\"}","{\"avatarURL\":\"https://example.test/x\",\"userId\":\"2\"}"))Assert.assertEquals(f.put("writer","invalid",body,false).attr("test.status"),422);
            Assert.assertEquals(f.put("writer",null,"{\"avatarURL\":\"https://example.test/x\"}",false).attr("test.status"),422);Assert.assertEquals(f.updates,0);
            f.sql("UPDATE users SET userStatus=4 WHERE oId='1'");Assert.assertEquals(f.put("disabled","https://example.test/x").attr("test.status"),404);Assert.assertNull(f.row("SELECT * FROM receipts"));
        }
        System.out.println("PASS avatar writes reject missing/wrong-scope credentials, forged identity fields, malformed URLs, missing key and newly disabled user without durable receipt or change");
        for(String fault:List.of("write","receipt","commit"))try(var f=new Fixture()){
            f.failWrite=fault.equals("write");f.failReceipt=fault.equals("receipt");f.failCommit=fault.equals("commit");Assert.assertNotEquals(f.put("failure","https://new.example/x").attr("test.status"),200);
            Assert.assertEquals(f.queryURL(),"https://old.example/one");Assert.assertNull(f.row("SELECT * FROM receipts"));Assert.assertFalse(f.users.hasTransactionBegun());
            f.failWrite=f.failReceipt=f.failCommit=false;Assert.assertEquals(f.put("failure","https://new.example/x").attr("test.status"),200);Assert.assertEquals(f.queryURL(),"https://new.example/x");
        }
        System.out.println("PASS real avatar coordinator rolls back avatar/type/time and idempotency receipt on write/receipt/commit rejection; same-key retry succeeds after recovery");
    }
}
