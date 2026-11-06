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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Production SQL, routes, controller, auth and shared budget on explicitly isolated SQLite. */
public final class ApiV3InvitationTest {
    static final String PATH="/api/v3/invitation-codes/status", UNUSED="Aa1234567890BbCc", USED="Used1234567890Aa", STOPPED="Stop1234567890Aa";
    static void inject(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    interface Action{void run()throws Exception;}
    static void error(int status,Action action)throws Exception{try{action.run();Assert.fail("expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertFalse(e.getMessage().contains("PRIVATE"));}}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3InvitationQueryService service=new ApiV3InvitationQueryService();
        final ApiV3InvitationProcessor processor=new ApiV3InvitationProcessor();final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        int selects,failAt;boolean revokeAfterStatus;final List<String> queries=new ArrayList<>();
        Fixture()throws Exception{
            sql("CREATE TABLE x_user(oId TEXT PRIMARY KEY,userStatus INTEGER,userPassword TEXT)");
            sql("CREATE TABLE x_invite(oId TEXT PRIMARY KEY,code TEXT,status INTEGER,userId TEXT,useTime BIGINT,generatorId TEXT,memo TEXT)");
            sql("INSERT INTO x_user VALUES('1',0,'PRIVATE_PASSWORD'),('2',0,'PRIVATE_PASSWORD'),('3',4,'PRIVATE_PASSWORD')");
            sql("INSERT INTO x_invite VALUES('10',?,1,'PENDING_PRIVATE_PHONE',9999999999999,'PRIVATE_GENERATOR','PRIVATE_MEMO')",UNUSED);
            sql("INSERT INTO x_invite VALUES('11',?,0,'USED_PRIVATE_OWNER',9999999999999,'PRIVATE_GENERATOR','PRIVATE_MEMO')",USED);
            sql("INSERT INTO x_invite VALUES('12',?,2,'','0','sys','PRIVATE_MEMO')",STOPPED);
            inject(service,"users",new UserRepository(){@Override public String getName(){return "x_user";}@Override public List<JSONObject> select(String s,Object...args)throws RepositoryException{return read(s,args);}});
            inject(service,"invitations",new InvitecodeRepository(){@Override public String getName(){return "x_invite";}@Override public List<JSONObject> select(String s,Object...args)throws RepositoryException{var rows=read(s,args);if(revokeAfterStatus){try{sql("UPDATE x_user SET userStatus=4 WHERE oId='1'");}catch(Exception e){throw new RepositoryException(e);}}return rows;}});
            inject(processor,"query",service);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){@Override public JSONObject authenticate(String value){if(!Set.of("good","other","wrong").contains(value))return null;return new JSONObject().put("user",new JSONObject().put("oId",value.equals("other")?"2":"1")).put("scope",value.equals("wrong")?"content.read":"activity.read");}});
            var field=ApiV3WriteMidware.class.getDeclaredField("COUNTS");field.setAccessible(true);((com.github.benmanes.caffeine.cache.Cache<?,?>)field.get(null)).invalidateAll();
            InvitationRouteBindings.register(processor,auth,new ApiV3WriteMidware());Dispatcher.mapping();
        }
        void sql(String s,Object...args)throws Exception{try(var p=db.prepareStatement(s)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate();}}
        List<JSONObject> read(String s,Object...args)throws RepositoryException{
            selects++;queries.add(s);Assert.assertTrue(s.startsWith("SELECT "),"read-only repository calls");if(failAt==selects)throw new RepositoryException("PRIVATE_DRIVER_CODE_AND_PHONE");
            Assert.assertFalse(s.contains("SELECT *"));Assert.assertFalse(s.contains("userPassword"));Assert.assertFalse(s.contains("useTime"));Assert.assertFalse(s.contains("memo"));Assert.assertFalse(s.contains("generatorId"));Assert.assertFalse(s.contains("userId"));
            try(var p=db.prepareStatement(s)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){var rows=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));rows.add(row);}return rows;}}catch(SQLException e){throw new RepositoryException(e);}
        }
        JSONObject http(String token,String body,Map<String,String> params,String contentType)throws Exception{
            var bytes=body.getBytes(StandardCharsets.UTF_8);var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,PATH,Unpooled.wrappedBuffer(bytes));var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
            try{
                if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(contentType!=null)raw.headers().set("Content-Type",contentType);raw.headers().set("Cookie","PRIVATE_FAKE_COOKIE");
                var request=new Request(null,raw){@Override public byte[] getBytes(){return bytes;}@Override public String getRemoteAddr(){return "192.0.2.41";}};request.setParams(new HashMap<>(params));
                var c=new RequestContext(request,new Response(null,response));c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var resolution=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");try{resolution.getContextHandlerMeta().getInvokeHolder().invoke(processor,context);}catch(ReflectiveOperationException e){throw new AssertionError(e);}});c.handle();
                int status=(int)c.attr("test.status");var out=new JSONObject().put("status",status);if(c.attr("test.data")!=null)out.put("data",c.attr("test.data"));if(c.attr("test.code")!=null)out.put("error",c.attr("test.code"));
                Assert.assertFalse(out.toString().contains("PRIVATE"));Assert.assertFalse(out.toString().contains(UNUSED));if(status==200){Assert.assertEquals(response.headers().get("Cache-Control"),"no-store");Assert.assertEquals(response.headers().get("Vary"),"Authorization");}return out;
            }finally{raw.release();}
        }
        JSONObject http(String token,String body)throws Exception{return http(token,body,Map.of(),"application/json");}
        String snapshot()throws Exception{try(var p=db.createStatement();var r=p.executeQuery("SELECT GROUP_CONCAT(oId||code||status||userId||useTime||generatorId||memo,';') FROM x_invite")){return r.next()?r.getString(1):"";}}
        public void close()throws Exception{db.close();}
    }
    public static void main(String[]args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            String before=f.snapshot();for(String code:List.of(UNUSED,USED,STOPPED)){var result=f.service.status("1",code);Assert.assertEquals(result.keySet(),Set.of("status"));Assert.assertEquals(result.getString("status"),code.equals(UNUSED)?"unused":code.equals(USED)?"used":"stopped");}
            Assert.assertEquals(f.service.status("2","  "+UNUSED+"  ").getString("status"),"unused");Assert.assertEquals(before,f.snapshot());
            f.sql("UPDATE x_invite SET useTime=0,userId='',generatorId='sys' WHERE code=?",UNUSED);Assert.assertEquals(f.service.status("1",UNUSED).getString("status"),"unused");
            error(404,()->f.service.status("1","NotFound12345678"));
        }
        System.out.println("PASS known-code stored states only, exact bounded projections, trim/case preserved, independent actor, live reservation/age/system details never influence or leak, read leaves all records unchanged");
        try(var f=new Fixture()){
            for(String code:Arrays.asList(null,""," ","A".repeat(15),"A".repeat(17),"' OR 1=1 --","a".repeat(15)+"\n","a".repeat(15)+"鱼"))error(422,()->f.service.status("1",code));
            for(String actor:Arrays.asList(null,"","0","01","1'","1".repeat(20)))error(422,()->f.service.status(actor,UNUSED));
            error(404,()->f.service.status("3",UNUSED));error(404,()->f.service.status("99",UNUSED));
            f.sql("INSERT INTO x_invite VALUES('20',?,1,'','0','','')",UNUSED);error(503,()->f.service.status("1",UNUSED));f.sql("DELETE FROM x_invite WHERE oId='20'");
            for(Object bad:List.of(9,0.5,"unknown")){f.sql("UPDATE x_invite SET status=? WHERE code=?",bad,UNUSED);error(503,()->f.service.status("1",UNUSED));}
        }
        System.out.println("PASS exact 16-character registration alphabet and actor bounds reject injection/Unicode and malformed data; unknown code404, duplicate rows or invalid stored states503");
        for(int stage=1;stage<=3;stage++)try(var f=new Fixture()){f.failAt=stage;error(503,()->f.service.status("1",UNUSED));}
        try(var f=new Fixture()){f.revokeAfterStatus=true;error(404,()->f.service.status("1",UNUSED));}
        System.out.println("PASS failure at each SQL phase is unavailable rather than missing/empty success, and current account deactivation during lookup prevents owner result");
        try(var f=new Fixture()){
            for(String token:Arrays.asList(null,"invalid")){int before=f.selects;Assert.assertEquals(f.http(token,"{\"code\":\""+UNUSED+"\"}").getInt("status"),401);Assert.assertEquals(f.selects,before);}
            int before=f.selects;Assert.assertEquals(f.http("wrong","{\"code\":\""+UNUSED+"\"}").getInt("status"),403);Assert.assertEquals(f.selects,before);
            Assert.assertEquals(f.http(null,"{}",Map.of("apiKey","PRIVATE","code",UNUSED),"application/json").getInt("status"),401);
            var out=f.http("good","{\"code\":\" "+UNUSED+" \"}",Map.of("userId","2","code",STOPPED),"application/json");Assert.assertEquals(out.getInt("status"),200);Assert.assertEquals(out.getJSONObject("data").toString(),"{\"status\":\"unused\"}");
            for(String body:List.of("{}","{\"code\":3}","{\"code\":null}","{\"code\":\"bad\"}","{\"code\":\""+UNUSED+"\",\"userId\":\"2\"}"))Assert.assertEquals(f.http("good",body).getInt("status"),422);
            Assert.assertEquals(f.http("good","{\"code\":\"one\",\"code\":\"two\"}").getInt("status"),400);Assert.assertEquals(f.http("good","{\"code\":\""+"A".repeat(65537)+"\"}").getInt("status"),413);Assert.assertEquals(f.http("good","{} ",Map.of(),"text/plain").getInt("status"),415);
        }
        System.out.println("PASS actual Dispatcher/auth/controller use activity.read Bearer, ignore Cookie/apiKey/query identity overrides, validate JSON and return no-store one-field DTO without code echo");
        try(var f=new Fixture()){
            int denied=0;for(int i=0;i<140;i++)if(f.http("good","{\"code\":\""+UNUSED+"\"}").getInt("status")==429)denied++;
            Assert.assertTrue(denied>0);Assert.assertTrue(f.selects<140*3);Assert.assertEquals(org.b3log.latke.http.handler.RouteHandler.doMatch(PATH,"GET"),null);
        }
        System.out.println("PASS shared authenticated request budget prevents unbounded known-code enumeration before SQL; no GET/query-only code endpoint or persisted idempotency state");
    }
}
