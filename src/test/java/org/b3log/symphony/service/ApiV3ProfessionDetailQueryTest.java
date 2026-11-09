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
import java.util.concurrent.TimeUnit;

/** 执行实际服务查询、类型化 DTO、Dispatcher、鉴权和控制器，隔离数据库、令牌、仓库及响应封装由夹具适配。 */
public class ApiV3ProfessionDetailQueryTest {
    static void inject(Object target,String key,Object value)throws Exception{var f=target.getClass().getDeclaredField(key);f.setAccessible(true);f.set(target,value);}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3ProfessionDetailQueryService query=new ApiV3ProfessionDetailQueryService();
        final ApiV3ProfessionDetailProcessor controller=new ApiV3ProfessionDetailProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        final long now=System.currentTimeMillis();
        int reads,failAt=-1,disableAt=-1,changeAt=-1;
        Fixture()throws Exception {
            sql("CREATE TABLE u(oId TEXT,userStatus INT,userPassword TEXT)");
            sql("CREATE TABLE p(oId TEXT,professionCode TEXT,status TEXT,currentRevisionId TEXT)");
            sql("CREATE TABLE r(oId TEXT,professionId TEXT,status TEXT,displayName TEXT,shortName TEXT,description TEXT,createdBy TEXT)");
            sql("CREATE TABLE s(oId TEXT,professionId TEXT,status TEXT)");
            sql("CREATE TABLE l(oId TEXT,schemeId TEXT,levelCode TEXT,sortOrder INT,requiredTotalExperience,displayName TEXT,shortName TEXT,description TEXT,achievementDescription TEXT,isTopLevel)");
            sql("CREATE TABLE up(oId TEXT,userId TEXT,professionId TEXT,professionRevisionId TEXT,levelSchemeId TEXT,totalExperience,currentLevelCode TEXT)");
            sql("CREATE TABLE c(oId TEXT,userId TEXT,professionId TEXT,actionCode TEXT,experienceSum,eventCount,lastOccurredAt)");
            sql("CREATE TABLE e(oId TEXT,userId TEXT,professionId TEXT,actionCode TEXT,actualExperienceDelta,beforeExperience,afterExperience,beforeLevelCode TEXT,afterLevelCode TEXT,occurredAt,status TEXT,sourceEventId TEXT)");
            sql("INSERT INTO u VALUES('1',0,'SECRET'),('2',0,'SECRET'),('3',0,'SECRET'),('4',4,'SECRET')");
            sql("INSERT INTO p VALUES('10','writer','PUBLISHED','101'),('11','reader','RETIRED','110')");
            sql("INSERT INTO r VALUES('100','10','RETIRED','Old writer','old','Historical description','SECRET'),('101','10','PUBLISHED','New writer','new','Current description','SECRET'),('110','11','RETIRED','Reader','','','SECRET')");
            sql("INSERT INTO s VALUES('300','10','RETIRED'),('301','10','PUBLISHED'),('310','11','RETIRED')");
            sql("INSERT INTO l VALUES('400','300','L1',0,0,'Old level','L','Old description','Old achievement',0),('401','300','L2',1,100,'Next level','N','','',1),('402','301','L3',0,0,'New scheme','','','',1),('410','310','L1',0,0,'Reader level','','','',1)");
            sql("INSERT INTO up VALUES('500','1','10','100','300',50,'L1'),('501','1','11','110','310',5,'L1'),('502','2','10','101','301',90,'L3')");
            sql("INSERT INTO c VALUES('600','1','10','article.publish',50,2,?),('601','1','10','article.delete',-5,1,?),('602','2','10','article.publish',9000,1,?),('603','1','11','read',7,1,?),('604','1','10','zero',100,0,?)",now,now,now,now,now);
            for(int i=0;i<65;i++)effect("7"+String.format("%03d",i),"1","10",i==0?-2:2,now-i,"APPLIED");
            effect("8000","2","10",999,now+10,"APPLIED");effect("8001","1","11",999,now+10,"APPLIED");
            effect("8002","1","10",999,now-TimeUnit.DAYS.toMillis(91),"APPLIED");effect("8003","1","10",999,now+10,"PENDING");effect("8004","1","10",0,now+10,"APPLIED");
            inject(query,"users",new UserRepository(){public String getName(){return "u";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"professions",new ProfessionRepository(){public String getName(){return "p";}});
            inject(query,"revisions",new ProfessionRevisionRepository(){public String getName(){return "r";}});
            inject(query,"schemes",new ProfessionLevelSchemeRepository(){public String getName(){return "s";}});
            inject(query,"levels",new ProfessionLevelRepository(){public String getName(){return "l";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"progress",new UserProfessionRepository(){public String getName(){return "up";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"contributions",new UserProfessionContributionRepository(){public String getName(){return "c";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"effects",new ProfessionEventEffectRepository(){public String getName(){return "e";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(controller,"query",query);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String t){return Set.of("owner","other","empty","wrong","disabled").contains(t)?new JSONObject().put("scope",t.equals("wrong")?"content.read":"profile.read").put("user",new JSONObject().put("oId",t.equals("other")?"2":t.equals("empty")?"3":t.equals("disabled")?"4":"1")):null;}});
        }
        void effect(String id,String actor,String profession,int delta,long at,String state)throws Exception{sql("INSERT INTO e VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",id,actor,profession,"article.publish",delta,50,50+delta,"L1","L1",at,state,"PRIVATE_SOURCE");}
        void sql(String q,Object...a)throws Exception{try(var p=db.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);p.executeUpdate();}}
        List<JSONObject> selectSQL(String q,Object...a)throws RepositoryException{
            reads++;Assert.assertTrue(q.startsWith("SELECT "));Assert.assertFalse(q.contains("SELECT *"));
            for(String secret:List.of("userPassword","createdBy","sourceEventId","configJson","effectKey"))Assert.assertFalse(q.contains(secret));
            if(reads==failAt)throw new RepositoryException("PRIVATE_DRIVER_SECRET");
            try{if(reads==disableAt)sql("UPDATE u SET userStatus=4 WHERE oId='1'");if(reads==changeAt)sql("UPDATE up SET totalExperience=51 WHERE oId='500'");
                try(var p=db.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);try(var r=p.executeQuery()){var rows=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));rows.add(row);}return rows;}}
            }catch(SQLException e){throw new RepositoryException(e);}catch(Exception e){throw new RepositoryException(e);}
        }
        RequestContext http(String token,String path,Map<String,String> values)throws Exception{
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,path+"?apiKey=legacy&userId=2");
            try{raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw);var params=new HashMap<>(Map.of("apiKey","legacy","userId","2"));params.putAll(values);request.setParams(params);
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var route=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");switch(route.getContextHandlerMeta().getInvokeHolder().getName()){case "detail"->controller.detail(context);case "records"->controller.records(context);default->throw new AssertionError();}});c.handle();return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();}
    }
    interface Action{void run()throws Exception;}
    static void error(int status,Action a)throws Exception{try{a.run();Assert.fail("Expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertFalse(e.getMessage().contains("SECRET"));}}
    static void safe(JSONObject o){for(String secret:List.of("SECRET","PRIVATE_SOURCE","sourceEventId","userId","professionRevisionId","levelSchemeId","effectKey","progressId","rarity"))Assert.assertFalse(o.toString().contains(secret),secret);}
    public static void main(String[]args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            var d=f.query.detail("1","10");safe(d);Assert.assertEquals(d.keySet(),Set.of("profession","levels","contributions"));
            var p=d.getJSONObject("profession");Assert.assertEquals(p.getString("displayName"),"Old writer");Assert.assertEquals(p.getString("levelName"),"Old level");Assert.assertEquals(p.getLong("totalExperience"),50L);
            Assert.assertEquals(d.getJSONArray("levels").length(),2);Assert.assertEquals(d.getJSONArray("levels").getJSONObject(1).getString("levelCode"),"L2");
            Assert.assertEquals(d.getJSONArray("contributions").length(),2);Assert.assertEquals(d.getJSONArray("contributions").getJSONObject(1).getLong("experienceSum"),-5L);
            Assert.assertEquals(f.query.detail("1","11").getJSONObject("profession").getString("state"),"RETIRED");Assert.assertEquals(f.query.detail("2","10").getJSONObject("profession").getString("displayName"),"New writer");
            error(404,()->f.query.detail("3","10"));error(404,()->f.query.records("3","10",1,20));error(404,()->f.query.detail("2","11"));
            Assert.assertEquals(f.selectSQL("SELECT COUNT(*) AS total FROM up").getFirst().getInt("total"),3);
        }
        System.out.println("PASS 1 actual historical revision/scheme and owner level route/contribution views, retired owner data, signed sums, no zero-progress synthesis or private metadata");
        try(var f=new Fixture()){
            var page=f.query.records("1","10",1,50);safe(page);Assert.assertEquals(page.getJSONObject("pagination").getLong("total"),65L);Assert.assertEquals(page.getJSONArray("list").length(),50);
            Assert.assertEquals(page.getJSONArray("list").getJSONObject(0).getLong("experienceDelta"),-2L);Assert.assertEquals(f.query.records("1","10",2,50).getJSONArray("list").length(),15);Assert.assertTrue(f.query.records("1","10",3,50).getJSONArray("list").isEmpty());
            Assert.assertEquals(f.query.records("2","10",1,20).getJSONObject("pagination").getLong("total"),1L);
            for(var row:page.getJSONArray("list"))Assert.assertEquals(((JSONObject)row).keySet(),Set.of("actionCode","experienceDelta","beforeExperience","afterExperience","beforeLevelCode","afterLevelCode","occurredAt"));
        }
        System.out.println("PASS 2 real count/page beyond fifty, owner/profession/nonzero/APPLIED/ninety-day filtering, signed adjustments and exact record DTO whitelist");
        try(var f=new Fixture()){
            for(String value:Arrays.asList(null,"","1 OR 1=1","x","1".repeat(20)))error(422,()->f.query.detail("1",value));
            error(422,()->f.query.records("1","10",0,20));error(422,()->f.query.records("1","10",1,51));error(422,()->f.query.records("1","10",Integer.MAX_VALUE,20));error(404,()->f.query.detail("4","10"));
            f.sql("UPDATE r SET status='DRAFT' WHERE oId='100'");error(503,()->f.query.detail("1","10"));f.sql("UPDATE r SET status='RETIRED' WHERE oId='100'");
            f.sql("UPDATE s SET professionId='11' WHERE oId='300'");error(503,()->f.query.records("1","10",1,20));f.sql("UPDATE s SET professionId='10' WHERE oId='300'");
            f.sql("INSERT INTO up SELECT '999',userId,professionId,professionRevisionId,levelSchemeId,totalExperience,currentLevelCode FROM up WHERE oId='500'");error(503,()->f.query.detail("1","10"));
        }
        System.out.println("PASS 3 invalid identities/pages, missing/disabled owners, draft/wrong-parent metadata and duplicate progress fail closed");
        try(var f=new Fixture()){
            f.sql("UPDATE l SET isTopLevel=2 WHERE oId='401'");error(503,()->f.query.detail("1","10"));f.sql("UPDATE l SET isTopLevel=1 WHERE oId='401'");
            f.sql("UPDATE l SET requiredTotalExperience=0.5 WHERE oId='401'");error(503,()->f.query.detail("1","10"));f.sql("UPDATE l SET requiredTotalExperience=100 WHERE oId='401'");
            f.sql("UPDATE c SET eventCount=0.5 WHERE oId='600'");error(503,()->f.query.detail("1","10"));f.sql("UPDATE c SET eventCount=2 WHERE oId='600'");
            f.sql("UPDATE c SET actionCode='<script>' WHERE oId='600'");error(503,()->f.query.detail("1","10"));f.sql("UPDATE c SET actionCode='article.publish' WHERE oId='600'");
            f.sql("UPDATE e SET beforeExperience=-1 WHERE oId='7000'");error(503,()->f.query.records("1","10",1,20));
            error(503,()->ApiV3ProfessionDetailDto.number(new JSONObject().put("n",new java.math.BigInteger("9223372036854775808")),"n",true));
            error(503,()->ApiV3ProfessionDetailDto.text(new JSONObject().put("n","\uD800"),"n",64,false));
        }
        System.out.println("PASS 4 strict numeric/boolean/Unicode fields reject fractions, overflow, negative progress and malformed text rather than coercing stored metadata");
        try(var f=new Fixture()){
            for(int i=0;i<511;i++)f.sql("INSERT INTO l VALUES(?,?,?,?,?,?,?,?,?,?)","9"+i,"300","X"+i,i+2,i+200,"Level","","","",0);
            error(503,()->f.query.detail("1","10"));
        }
        try(var f=new Fixture()){
            for(int i=0;i<511;i++)f.sql("INSERT INTO c VALUES(?,?,?,?,?,?,?)","9"+i,"1","10","action"+i,1,1,f.now);
            error(503,()->f.query.detail("1","10"));
        }
        System.out.println("PASS 5 oversized route/contribution views reject the entire response instead of silently truncating metadata");
        for(int stage=1;stage<=6;stage++)try(var f=new Fixture()){f.failAt=stage;error(503,()->f.query.detail("1","10"));}
        for(int stage=1;stage<=6;stage++)try(var f=new Fixture()){f.failAt=stage;error(503,()->f.query.records("1","10",1,20));}
        try(var f=new Fixture()){f.disableAt=6;error(404,()->f.query.detail("1","10"));}
        try(var f=new Fixture()){f.changeAt=5;error(503,()->f.query.records("1","10",1,20));}
        System.out.println("PASS 6 all SELECT stages sanitize failures, final active-owner check rejects concurrent disable and progress recheck prevents mixed routes");
        try(var f=new Fixture()){
            ApiV3ProfessionDetailProcessor.registerRoutes(f.controller,f.auth);Dispatcher.mapping();
            for(String path:List.of("/api/v3/me/professions/10","/api/v3/me/professions/10/records")){
                for(String t:Arrays.asList(null,"bad","wrong")){int before=f.reads;Assert.assertEquals(f.http(t,path,Map.of()).attr("test.status"),"wrong".equals(t)?403:401);Assert.assertEquals(f.reads,before);}
                var c=f.http("owner",path,Map.of());Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(c.getResponse().getHeader("Vary"),"Authorization");safe((JSONObject)c.attr("test.data"));
                Assert.assertEquals(f.http("empty",path,Map.of()).attr("test.status"),404);Assert.assertEquals(f.http("disabled",path,Map.of()).attr("test.status"),404);
            }
            Assert.assertEquals(((JSONObject)f.http("other","/api/v3/me/professions/10",Map.of()).attr("test.data")).getJSONObject("profession").getLong("totalExperience"),90L);
            Assert.assertEquals(f.http("owner","/api/v3/me/professions/10/records",Map.of("size","51")).attr("test.status"),422);
            Assert.assertEquals(f.http("owner","/api/v3/me/professions/invalid",Map.of()).attr("test.status"),422);
        }
        System.out.println("PASS 7 production Dispatcher/controller/auth for both GETs reject Cookie/apiKey impersonation, scope bypass and invalid paging; return only active owner data without cache");
        System.out.println("Profession detail isolated tests passed: 7 groups");
    }
}
