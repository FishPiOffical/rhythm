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
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;

/** Real query/DTO/controller/Dispatcher/auth/anonymous shield; SQLite, token and environment adapters. */
public class ApiV3MedalQueryTest {
    static void inject(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3MedalQueryService query=new ApiV3MedalQueryService();final ApiV3MedalProcessor controller=new ApiV3MedalProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();final AnonymousViewCheckMidware anonymous=new AnonymousViewCheckMidware();
        int reads,failAt=-1;boolean fail,hideOnCount,hideOnData,deactivateOnData;final String ip="203.0.113.17";
        Fixture()throws Exception {
            sql("CREATE TABLE x_user(oId TEXT PRIMARY KEY,userName TEXT,userStatus INT,userPassword TEXT,userEmail TEXT)");
            sql("CREATE TABLE x_medal(oId TEXT PRIMARY KEY,medal_id TEXT,medal_name TEXT,medal_type TEXT,medal_description TEXT,medal_attr TEXT)");
            sql("CREATE TABLE x_user_medal(oId TEXT PRIMARY KEY,user_id TEXT,medal_id TEXT,data TEXT,expire_time INTEGER,display INT,display_order INT)");
            sql("INSERT INTO x_user VALUES('1','Alice',0,'PRIVATE_PASSWORD','PRIVATE_EMAIL'),('2','Bob',0,'OTHER_PASSWORD','OTHER_EMAIL'),('3','Empty',0,'PRIVATE','PRIVATE'),('4','Disabled',4,'PRIVATE','PRIVATE')");
            sql("INSERT INTO x_medal VALUES('10','0','First','Rare','Hello {var1} / {var2}','{BROKEN_PRIVATE_ATTR'),('11','1','Hidden','Normal','Hidden description','{PRIVATE_ATTR}'),('12','2','Expired','Normal','Expired description','{PRIVATE_ATTR}'),('13','3','Other','Rare','Other description','{PRIVATE_ATTR}'),('14','4','Suspended','Rare','Disabled description','{PRIVATE_ATTR}')");
            sql("INSERT INTO x_user_medal VALUES('20','1','0','Alice;<b>Gold</b>',0,1,0),('21','1','1','SECRET_HIDDEN_DATA',0,0,1),('22','1','2','SECRET_EXPIRED_DATA',1,1,2),('23','2','3','OTHER_PRIVATE_DATA',0,1,0),('24','4','4','DISABLED_PRIVATE_DATA',0,1,0),('25','1','missing','ORPHAN',0,1,3)");
            inject(query,"users",new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"medals",new MedalRepository(){public String getName(){return "x_medal";}});
            inject(query,"grants",new UserMedalRepository(){public String getName(){return "x_user_medal";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(controller,"query",query);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("reader","other","wrong").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":"1")).put("scope",token.equals("wrong")?"content.read":"profile.read"):null;}});
            inject(anonymous,"optionQueryService",new OptionQueryService());
            resetShield();AnonymousViewCheckMidware.setEnabled(false);
        }
        void resetShield(){AnonymousViewCheckMidware.whiteList.clear();AnonymousViewCheckMidware.ipVisitCountCache.invalidateAll();AnonymousViewCheckMidware.ipFirstVisitTimeCache.invalidateAll();AnonymousViewCheckMidware.ipLastCaptchaPassTimeCache.invalidateAll();AnonymousViewCheckMidware.ipBlacklistCache.invalidateAll();AnonymousViewCheckMidware.setFirstVisitCaptchaEnabled(true);}
        List<JSONObject> selectSQL(String text,Object...args)throws RepositoryException {
            reads++;Assert.assertTrue(text.startsWith("SELECT "));for(String forbidden:List.of("SELECT *","userPassword","userEmail","medal_attr"))Assert.assertFalse(text.contains(forbidden));
            if(fail||reads==failAt)throw new RepositoryException("PRIVATE_DRIVER_ERROR");
            if(hideOnCount && text.startsWith("SELECT COUNT(*)")){hideOnCount=false;try{sql("UPDATE x_user_medal SET display=0 WHERE oId='20'");}catch(Exception e){throw new RepositoryException(e);}}
            if((hideOnData || deactivateOnData) && text.startsWith("SELECT m.medal_id")){
                try{if(hideOnData)sql("UPDATE x_user_medal SET display=0 WHERE oId='20'");if(deactivateOnData)sql("UPDATE x_user SET userStatus=4 WHERE oId='1'");}catch(Exception e){throw new RepositoryException(e);}
                hideOnData=false;deactivateOnData=false;
            }
            try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var rows=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));rows.add(row);}return rows;}}
            catch(SQLException e){throw new RepositoryException(e);}
        }
        void sql(String text,Object...args)throws Exception{try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}}
        long countGrants()throws Exception {try(var s=db.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM x_user_medal")){return r.getLong(1);}}
        RequestContext http(String token,String path)throws Exception{return http(token,path,Map.of());}
        RequestContext http(String token,String path,Map<String,String> extra)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,path+"?userId=2&apiKey=legacy");
            try {
                raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw){@Override public String getRemoteAddr(){return ip;}};
                var params=new HashMap<>(Map.of("userId","2","apiKey","legacy"));params.putAll(extra);request.setParams(params);
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)){@Override public void sendRedirect(String uri){setHeader("Location",uri);setStatus(302);}});c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var resolution=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");if(resolution.getContextHandlerMeta().getInvokeHolder().getName().equals("mine"))controller.mine(context);else controller.displayed(context);});
                c.handle();return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();resetShield();AnonymousViewCheckMidware.setEnabled(true);}
    }
    interface Action{void run()throws Exception;}
    static void error(int status,Action action)throws Exception{try{action.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()) {
            long before=f.countGrants();var own=f.query.mine("1",1,20);Assert.assertEquals(own.getJSONObject("pagination").getLong("total"),2L);Assert.assertEquals(own.getJSONArray("list").length(),2);
            var first=own.getJSONArray("list").getJSONObject(0);Assert.assertEquals(first.keySet(),Set.of("id","name","type","description","order","expiresAt","displayed"));Assert.assertEquals(first.getString("id"),"0");Assert.assertEquals(first.getString("description"),"Hello Alice / Gold");Assert.assertTrue(first.getBoolean("displayed"));Assert.assertFalse(own.getJSONArray("list").getJSONObject(1).getBoolean("displayed"));
            var pub=f.query.displayed("Alice",1,20);Assert.assertEquals(pub.getJSONArray("list").length(),1);Assert.assertEquals(pub.getJSONObject("pagination").getLong("total"),1L);Assert.assertEquals(pub.getJSONArray("list").getJSONObject(0).keySet(),Set.of("id","name","type","description","order","expiresAt"));
            Assert.assertFalse(pub.toString().contains("SECRET"));Assert.assertFalse(pub.toString().contains("PRIVATE"));Assert.assertFalse(pub.toString().contains("data"));Assert.assertFalse(pub.toString().contains("medal_attr"));Assert.assertEquals(f.countGrants(),before);
            Assert.assertEquals(f.query.mine("1",2,1).getJSONArray("list").getJSONObject(0).getString("id"),"1");Assert.assertTrue(f.query.mine("1",3,1).getJSONArray("list").isEmpty());
            Assert.assertTrue(f.query.displayed("Empty",1,20).getJSONArray("list").isEmpty());Assert.assertEquals(f.query.mine("2",1,20).getJSONArray("list").getJSONObject(0).getString("id"),"3");
            error(404,()->f.query.displayed("Missing",1,20));error(404,()->f.query.displayed("Disabled",1,20));error(404,()->f.query.mine("4",1,20));error(422,()->f.query.mine("1' OR 1=1",1,20));error(422,()->f.query.displayed("bad/name",1,20));error(422,()->f.query.mine("1",0,20));error(422,()->f.query.displayed("Alice",1,51));
            f.hideOnData=true;Assert.assertTrue(f.query.displayed("Alice",1,20).getJSONArray("list").isEmpty());
            f.sql("UPDATE x_user_medal SET display=1 WHERE oId='20'");
            f.deactivateOnData=true;Assert.assertTrue(f.query.displayed("Alice",1,20).getJSONArray("list").isEmpty());
            f.sql("UPDATE x_user SET userStatus=0 WHERE oId='1'");
            f.hideOnCount=true;Assert.assertTrue(f.query.displayed("Alice",1,20).getJSONArray("list").isEmpty());
            f.sql("UPDATE x_user_medal SET display=1,expire_time=? WHERE oId='20'",System.currentTimeMillis()-1);Assert.assertTrue(f.query.displayed("Alice",1,20).getJSONArray("list").isEmpty());
            f.sql("UPDATE x_user_medal SET expire_time=? WHERE oId='20'",System.currentTimeMillis()+3600000);Assert.assertEquals(f.query.displayed("Alice",1,20).getJSONArray("list").length(),1);
            f.sql("UPDATE x_user_medal SET expire_time=-1 WHERE oId='20'");Assert.assertEquals(f.query.mine("1",1,20).getJSONArray("list").length(),2);Assert.assertTrue(f.query.displayed("Alice",1,20).getJSONArray("list").isEmpty());
            f.sql("UPDATE x_user_medal SET expire_time=0 WHERE oId='20'");
            f.sql("UPDATE x_user SET userStatus=3 WHERE oId='1'");Assert.assertEquals(f.query.displayed("Alice",1,20).getJSONArray("list").length(),1);error(404,()->f.query.mine("1",1,20));
            for(int state:List.of(1,2,4)){f.sql("UPDATE x_user SET userStatus=? WHERE oId='1'",state);error(404,()->f.query.displayed("Alice",1,20));}

        }
        System.out.println("PASS actual prefixed count/page SQL: current owner, hidden/public boundary, expiry/missing joins, stable bounded pagination, state recheck and read-only grants");
        try(var f=new Fixture()) {
            MedalRouteBindings.register(f.controller,f.auth,f.anonymous);Dispatcher.mapping();
            for(String token:Arrays.asList(null,"bad","wrong")){int before=f.reads;Assert.assertEquals(f.http(token,"/api/v3/me/medals").attr("test.status"),token==null||token.equals("bad")?401:403);Assert.assertEquals(f.reads,before);}
            for(String token:List.of("bad","wrong")){int before=f.reads;Assert.assertEquals(f.http(token,"/api/v3/users/Alice/medals").attr("test.status"),token.equals("bad")?401:403);Assert.assertEquals(f.reads,before);}
            var own=f.http("reader","/api/v3/me/medals");Assert.assertEquals(own.attr("test.status"),200);Assert.assertEquals(((JSONObject)own.attr("test.data")).getJSONArray("list").length(),2);Assert.assertEquals(own.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(own.getResponse().getHeader("Vary"),"Authorization");
            Assert.assertEquals(((JSONObject)f.http("other","/api/v3/me/medals").attr("test.data")).getJSONArray("list").getJSONObject(0).getString("id"),"3");
            var pub=f.http(null,"/api/v3/users/Alice/medals");Assert.assertEquals(pub.attr("test.status"),200);Assert.assertEquals(((JSONObject)pub.attr("test.data")).getJSONArray("list").length(),1);
            Assert.assertEquals(f.http("reader","/api/v3/me/medals",Map.of("size","51")).attr("test.status"),422);Assert.assertEquals(f.http(null,"/api/v3/users/Alice/medals",Map.of("p","0")).attr("test.status"),422);
            Assert.assertEquals(f.http(null,"/api/v3/users/Disabled/medals").attr("test.status"),404);Assert.assertEquals(f.http(null,"/api/v3/users/bad.name/medals").attr("test.status"),422);
            f.fail=true;var unavailable=f.http("reader","/api/v3/me/medals");Assert.assertEquals(unavailable.attr("test.status"),503);Assert.assertEquals(unavailable.attr("test.code"),"temporarily_unavailable");Assert.assertNull(unavailable.attr("test.data"));
        }
        System.out.println("PASS both real routes/controller/auth: only Bearer owns /me, optional profile scope public reads, forged Cookie/apiKey/userId ignored, DTO headers and sanitized unavailable result");
        try(var f=new Fixture()) {
            AnonymousViewCheckMidware.setEnabled(true);
            // Rebind live fixture handlers so the actual anonymous gate and counters are exercised.
            MedalRouteBindings.register(f.controller,f.auth,f.anonymous);Dispatcher.mapping();
            int before=f.reads;var first=f.http(null,"/api/v3/users/Alice/medals");Assert.assertEquals(first.getResponse().getHeader("Location"),"/test");Assert.assertTrue(AnonymousViewCheckMidware.ipBlacklistCache.getIfPresent(f.ip));Assert.assertEquals(f.reads,before);
            f.resetShield();AnonymousViewCheckMidware.setFirstVisitCaptchaEnabled(false);
            for(int i=1;i<=4;i++)Assert.assertEquals(f.http(null,"/api/v3/users/Alice/medals").attr("test.status"),200);
            before=f.reads;var fifth=f.http(null,"/api/v3/users/Alice/medals");Assert.assertEquals(fifth.getResponse().getHeader("Location"),"/test");Assert.assertEquals(f.reads,before);
            f.resetShield();Assert.assertEquals(f.http("reader","/api/v3/users/Alice/medals").attr("test.status"),200);Assert.assertNull(AnonymousViewCheckMidware.ipVisitCountCache.getIfPresent(f.ip));
        }
        System.out.println("PASS actual AnonymousViewCheckMidware route chain: new anonymous IP first visit and fifth visit redirected before SQL; valid Bearer identified without Cookie/apiKey fallback");
        try(var f=new Fixture()) {
            f.failAt=f.reads+2;error(503,()->f.query.mine("1",1,20));f.failAt=f.reads+3;error(503,()->f.query.displayed("Alice",1,20));
            f.sql("UPDATE x_medal SET medal_name=?,medal_description=? WHERE medal_id='0'","x".repeat(129),"{var1}".repeat(100));f.sql("UPDATE x_user_medal SET data=? WHERE oId='20'","a".repeat(128));
            var row=f.query.mine("1",1,20).getJSONArray("list").getJSONObject(0);Assert.assertEquals(row.getString("name"),"");Assert.assertEquals(row.getString("description"),"");
            f.sql("UPDATE x_medal SET medal_description=? WHERE medal_id='0'","<script>alert(1)</script><b>safe</b> {var1}");f.sql("UPDATE x_user_medal SET data=? WHERE oId='20'","<img src=x onerror=alert(2)>");
            Assert.assertEquals(f.query.mine("1",1,20).getJSONArray("list").getJSONObject(0).getString("description"),"safe");
        }
        System.out.println("PASS bounded plain-text variable projection, raw/malformed styling JSON excluded, no grant data/internal identifiers, count/page-stage failures are 503");
    }
}
