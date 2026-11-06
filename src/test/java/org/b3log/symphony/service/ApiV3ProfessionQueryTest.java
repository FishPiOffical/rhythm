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

/** Real projected SELECTs and Dispatcher/auth/controller, with explicit SQLite/token/anonymous-hook adapters. */
public class ApiV3ProfessionQueryTest {
    static void inject(Object target,String name,Object value)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static final Set<String> MODULES=Set.of("primaryProfession","allProfessions","experience","rarity","majorContributions","contributionStats","activityFeed","levelHistory","rewards","ranking");
    static JSONObject visibility(String scope){var json=new JSONObject();for(String module:MODULES)json.put(module,scope);return json;}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3ProfessionQueryService query=new ApiV3ProfessionQueryService();
        final ApiV3ProfessionProcessor controller=new ApiV3ProfessionProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        final AnonymousViewCheckMidware anonymous=new AnonymousViewCheckMidware();
        int reads,failAt=-1,disableAt=-1;
        Fixture()throws Exception {
            sql("CREATE TABLE x_user(oId TEXT PRIMARY KEY,userStatus INT,userPassword TEXT)");
            sql("CREATE TABLE x_profession(oId TEXT PRIMARY KEY,professionCode TEXT,currentRevisionId TEXT,currentLevelSchemeId TEXT,status TEXT,sortOrder INT)");
            sql("CREATE TABLE x_profession_revision(oId TEXT PRIMARY KEY,professionId TEXT,status TEXT,displayName TEXT,shortName TEXT,description TEXT,defaultPresentationJson TEXT,createdBy TEXT)");
            sql("CREATE TABLE x_profession_level(oId TEXT PRIMARY KEY,schemeId TEXT,levelCode TEXT,displayName TEXT,description TEXT,requiredTotalExperience INT)");
            sql("CREATE TABLE x_user_profession(oId TEXT PRIMARY KEY,userId TEXT,professionId TEXT,professionRevisionId TEXT,levelSchemeId TEXT,totalExperience INT,currentLevelCode TEXT,dataVersion INT,lastEffectAt INT)");
            sql("CREATE TABLE x_user_profession_privacy(oId TEXT,userId TEXT,onboardingState TEXT,primaryProfessionId TEXT,privacyPreset TEXT,moduleVisibilityJson TEXT,userPrivacyVersion INT)");
            sql("INSERT INTO x_user VALUES('1',0,'OWNER_PASSWORD'),('2',0,'OTHER_PASSWORD'),('3',0,'EMPTY'),('4',4,'DISABLED')");
            sql("INSERT INTO x_profession VALUES('10','writer','101','301','PUBLISHED',2),('11','reader','111','311','PUBLISHED',1),('12','draft','121','321','DRAFT',0),('13','retired','131','331','RETIRED',0),('14','bad-link','111','341','PUBLISHED',0),('15','bad-draft','151','351','PUBLISHED',0)");
            sql("INSERT INTO x_profession_revision VALUES('100','10','RETIRED','Old writer','old','Old description','PRIVATE_CONFIG','ADMIN'),('101','10','PUBLISHED','Writer','write','Current description','PRIVATE_CONFIG','ADMIN'),('111','11','PUBLISHED','Reader','read','Read description','PRIVATE_CONFIG','ADMIN'),('121','12','DRAFT','DRAFT_SECRET','','','','ADMIN'),('131','13','RETIRED','Retired','','','','ADMIN'),('151','15','DRAFT','DRAFT_POINTER_SECRET','','','','ADMIN')");
            sql("INSERT INTO x_profession_level VALUES('400','300','L1','Old level','Old level description',0),('401','301','L2','New level','New description',100),('411','311','L1','Reader level','Reader description',0),('431','331','L1','Retired level','Retired description',0)");
            sql("INSERT INTO x_user_profession VALUES('500','1','10','100','300',50,'L1',999,777),('501','1','13','131','331',20,'L1',999,777),('502','2','11','111','311',99,'L1',999,777)");
            sql("INSERT INTO x_user_profession_privacy VALUES('600','1','SELECTED','10','FULLY_HIDDEN',?,999)",visibility("HIDDEN").toString());
            inject(query,"users",new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"professions",new ProfessionRepository(){public String getName(){return "x_profession";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"revisions",new ProfessionRevisionRepository(){public String getName(){return "x_profession_revision";}});
            inject(query,"levels",new ProfessionLevelRepository(){public String getName(){return "x_profession_level";}});
            inject(query,"progress",new UserProfessionRepository(){public String getName(){return "x_user_profession";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"privacy",new UserProfessionPrivacyRepository(){public String getName(){return "x_user_profession_privacy";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(controller,"query",query);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("reader","other","wrong","disabled").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":token.equals("disabled")?"4":"1")).put("scope",token.equals("wrong")?"activity.read":"profile.read"):null;}});
        }
        List<JSONObject> selectSQL(String text,Object...args)throws RepositoryException {
            reads++;Assert.assertTrue(text.startsWith("SELECT "));Assert.assertFalse(text.contains("SELECT *"));
            for(String forbidden:List.of("userPassword","createdBy","defaultPresentationJson","dataVersion","lastEffectAt","userPrivacyVersion"))Assert.assertFalse(text.contains(forbidden));
            if(reads==failAt)throw new RepositoryException("DRIVER_PRIVATE_SECRET");
            if(reads==disableAt)try{sql("UPDATE x_user SET userStatus=4 WHERE oId='1'");}catch(Exception e){throw new RepositoryException(e);}
            try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}
            catch(SQLException e){throw new RepositoryException(e);}
        }
        void sql(String text,Object...args)throws Exception {try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}}
        RequestContext http(String token,String path,Map<String,String> params)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,path+"?userId=2&apiKey=legacy");
            try{raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw);var values=new HashMap<>(Map.of("userId","2","apiKey","legacy"));values.putAll(params);request.setParams(values);
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var resolution=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");switch(resolution.getContextHandlerMeta().getInvokeHolder().getName()){case "catalog"->controller.catalog(context);case "mine"->controller.mine(context);default->throw new AssertionError();}});c.handle();return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();}
    }
    interface Action{void run()throws Exception;}
    static void error(int status,Action action)throws Exception{try{action.run();Assert.fail("Expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertFalse(e.getMessage().contains("SECRET"));}}
    static void noSecrets(JSONObject value){String s=value.toString();for(String key:List.of("createdBy","defaultPresentationJson","dataVersion","lastEffectAt","userPrivacyVersion","userPassword","userId","PRIVATE","DRAFT_SECRET","DRAFT_POINTER_SECRET"))Assert.assertFalse(s.contains(key),key);}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            var first=f.query.catalog(1,1);Assert.assertEquals(first.getJSONObject("pagination").getLong("total"),2L);Assert.assertEquals(first.getJSONArray("list").getJSONObject(0).getString("professionId"),"11");
            var second=f.query.catalog(2,1);Assert.assertEquals(second.getJSONArray("list").getJSONObject(0).getString("professionId"),"10");Assert.assertEquals(second.getJSONArray("list").getJSONObject(0).getString("displayName"),"Writer");
            Assert.assertEquals(second.getJSONArray("list").getJSONObject(0).keySet(),Set.of("professionId","professionCode","displayName","shortName","description"));Assert.assertTrue(f.query.catalog(3,1).getJSONArray("list").isEmpty());noSecrets(first);noSecrets(second);
            f.sql("UPDATE x_profession SET status='RETIRED' WHERE oId='10'");Assert.assertEquals(f.query.catalog(1,20).getJSONObject("pagination").getLong("total"),1L);
            f.sql("UPDATE x_profession_revision SET status='DRAFT' WHERE oId='111'");Assert.assertTrue(f.query.catalog(1,20).getJSONArray("list").isEmpty());
        }
        System.out.println("PASS published directory filters stable/revision state and revision ownership before count/page; stable ordering, empty/overflow pages and metadata allowlist");
        try(var f=new Fixture()){
            var mine=f.query.mine("1",1,1);Assert.assertEquals(mine.getJSONObject("pagination").getLong("total"),2L);Assert.assertEquals(mine.getJSONObject("state").getString("privacyPreset"),"FULLY_HIDDEN");
            Assert.assertEquals(mine.getJSONObject("state").getJSONObject("moduleVisibility").keySet(),MODULES);Assert.assertEquals(mine.getJSONObject("state").getString("primaryProfessionState"),"ACTIVE");
            var item=mine.getJSONArray("list").getJSONObject(0);Assert.assertEquals(item.getString("displayName"),"Old writer");Assert.assertEquals(item.getString("levelName"),"Old level");Assert.assertEquals(item.getLong("totalExperience"),50L);
            Assert.assertEquals(item.keySet(),Set.of("professionId","professionCode","displayName","shortName","description","state","totalExperience","levelCode","levelName","levelDescription","requiredExperience"));noSecrets(mine);
            Assert.assertEquals(f.query.mine("1",2,1).getJSONArray("list").getJSONObject(0).getString("state"),"RETIRED");Assert.assertTrue(f.query.mine("1",3,1).getJSONArray("list").isEmpty());
            Assert.assertEquals(f.query.mine("2",1,20).getJSONArray("list").getJSONObject(0).getString("professionId"),"11");
            var empty=f.query.mine("3",1,20);Assert.assertTrue(empty.getJSONArray("list").isEmpty());Assert.assertEquals(empty.getJSONObject("state").getString("onboardingState"),"UNDECIDED");Assert.assertEquals(empty.getJSONObject("state").getString("primaryProfessionState"),"NONE");
            Assert.assertEquals(f.selectSQL("SELECT COUNT(*) AS total FROM x_user_profession_privacy").getFirst().getInt("total"),1);
            f.sql("INSERT INTO x_user_profession_privacy VALUES('603','3','SELECTED','11','ALL_PUBLIC',?,0)",visibility("PUBLIC").toString());
            var selected=f.query.mine("3",1,20);Assert.assertTrue(selected.getJSONArray("list").isEmpty());Assert.assertEquals(selected.getJSONObject("state").getString("primaryProfessionId"),"11");Assert.assertEquals(selected.getJSONObject("state").getString("primaryProfessionState"),"ACTIVE");
            f.sql("UPDATE x_user_profession_privacy SET primaryProfessionId='11' WHERE userId='1'");Assert.assertEquals(f.query.mine("1",1,20).getJSONArray("list").length(),2);
            f.sql("UPDATE x_user_profession_privacy SET primaryProfessionId='13' WHERE userId='1'");Assert.assertEquals(f.query.mine("1",1,20).getJSONObject("state").getString("primaryProfessionState"),"RETIRED");
            f.sql("UPDATE x_user_profession_privacy SET primaryProfessionId='99' WHERE userId='1'");Assert.assertEquals(f.query.mine("1",1,20).getJSONObject("state").getString("primaryProfessionState"),"DELETED");
        }
        System.out.println("PASS owner-only hidden privacy, historical profession/level snapshot, primary active/retired/deleted, read-only default state and actual-progress pagination");
        try(var f=new Fixture()){
            for(String actor:Arrays.asList(null,"","01","1' OR 1=1","1".repeat(33)))error(422,()->f.query.mine(actor,1,20));error(404,()->f.query.mine("4",1,20));error(404,()->f.query.mine("99",1,20));
            error(422,()->f.query.catalog(0,20));error(422,()->f.query.catalog(Integer.MAX_VALUE,20));error(422,()->f.query.mine("1",1,51));
            f.sql("UPDATE x_user_profession_privacy SET moduleVisibilityJson='invalid' WHERE userId='1'");error(503,()->f.query.mine("1",1,20));
            f.sql("UPDATE x_user_profession_privacy SET moduleVisibilityJson=? WHERE userId='1'",visibility("SECRET").toString());error(503,()->f.query.mine("1",1,20));
            f.sql("UPDATE x_user_profession_privacy SET moduleVisibilityJson=? WHERE userId='1'",visibility("SELF").toString());Assert.assertEquals(f.query.mine("1",1,20).getJSONObject("state").getJSONObject("moduleVisibility").getString("experience"),"SELF");
            f.sql("INSERT INTO x_user_profession_privacy SELECT '601',userId,onboardingState,primaryProfessionId,privacyPreset,moduleVisibilityJson,userPrivacyVersion FROM x_user_profession_privacy");error(503,()->f.query.mine("1",1,20));
        }
        System.out.println("PASS invalid identities/paging, duplicate/broken privacy and malformed visibility fail closed without exposing stored JSON");
        try(var f=new Fixture()){
            f.sql("UPDATE x_user_profession SET totalExperience=-1 WHERE oId='500'");error(503,()->f.query.mine("1",1,20));f.sql("UPDATE x_user_profession SET totalExperience=50 WHERE oId='500'");
            f.sql("UPDATE x_profession_revision SET status='DRAFT' WHERE oId='100'");error(503,()->f.query.mine("1",1,20));f.sql("UPDATE x_profession_revision SET status='RETIRED' WHERE oId='100'");
            f.sql("DELETE FROM x_profession_level WHERE oId='400'");error(503,()->f.query.mine("1",1,20));
        }
        System.out.println("PASS corrupt negative experience, draft historical revision and missing level cannot disclose partial progress");
        for(int point=1;point<=6;point++)try(var f=new Fixture()){f.failAt=point;error(503,()->f.query.mine("1",1,20));}
        for(int point=1;point<=2;point++)try(var f=new Fixture()){f.failAt=point;error(503,()->f.query.catalog(1,20));}
        try(var f=new Fixture()){f.disableAt=6;error(404,()->f.query.mine("1",1,20));}
        System.out.println("PASS SQL faults at every owner/catalog stage sanitize as 503; account disabled during read rejects final owner response");
        try(var f=new Fixture()){
            ProfessionRouteBindings.register(f.controller,f.auth,f.anonymous);Dispatcher.mapping();
            final String catalog="/api/v3/professions",mine="/api/v3/me/professions";
            for(String path:List.of(catalog,mine)){
                for(String token:List.of("invalid","wrong")){int before=f.reads,hits=f.anonymous.hits;var c=f.http(token,path,Map.of());Assert.assertEquals(c.attr("test.status"),token.equals("wrong")?403:401);Assert.assertEquals(f.reads,before);Assert.assertEquals(f.anonymous.hits,hits);}
                var c=f.http("reader",path,Map.of());Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(c.getResponse().getHeader("Vary"),"Authorization");
            }
            Assert.assertEquals(f.http(null,mine,Map.of()).attr("test.status"),401);Assert.assertEquals(f.http(null,catalog,Map.of()).attr("test.status"),200);Assert.assertTrue(f.anonymous.hits>=2);
            Assert.assertEquals(((JSONObject)f.http("reader",mine,Map.of()).attr("test.data")).getJSONArray("list").getJSONObject(0).getString("professionId"),"10");
            Assert.assertEquals(((JSONObject)f.http("other",mine,Map.of()).attr("test.data")).getJSONArray("list").getJSONObject(0).getString("professionId"),"11");
            Assert.assertEquals(f.http("disabled",mine,Map.of()).attr("test.status"),404);
            Assert.assertEquals(f.http("reader",catalog,Map.of("p","0")).attr("test.status"),422);Assert.assertEquals(f.http("reader",mine,Map.of("size","51")).attr("test.status"),422);
            Assert.assertEquals(((JSONObject)f.http(null,catalog,Map.of("p","2","size","1")).attr("test.data")).getJSONArray("list").getJSONObject(0).getString("professionId"),"10");
            f.anonymous.block=true;int before=f.reads;Assert.assertEquals(f.http(null,catalog,Map.of()).attr("test.status"),403);Assert.assertEquals(f.reads,before);
            f.failAt=f.reads+1;var c=f.http("reader",mine,Map.of());Assert.assertEquals(c.attr("test.status"),503);Assert.assertEquals(c.attr("test.code"),"temporarily_unavailable");Assert.assertNull(c.attr("test.data"));
        }
        System.out.println("PASS actual two Dispatcher/controller/auth routes: anonymous-hook ordering/block, invalid401/wrongscope403, cookie/apiKey cannot impersonate, no-store and sanitized HTTP failures");
    }
}
