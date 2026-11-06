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

/** Actual bounded service SQL, typed DTO and HTTP/auth/routes on isolated SQLite. */
public class ApiV3ProfessionGuideQueryTest {
    static void inject(Object target,String key,Object value)throws Exception{var f=target.getClass().getDeclaredField(key);f.setAccessible(true);f.set(target,value);}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3ProfessionGuideQueryService query=new ApiV3ProfessionGuideQueryService();
        final ApiV3ProfessionGuideProcessor controller=new ApiV3ProfessionGuideProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        int reads,failAt=-1,disableAt=-1,changeAt=-1;
        Fixture()throws Exception {
            sql("CREATE TABLE u(oId TEXT,userStatus INT,userPassword TEXT)");
            sql("CREATE TABLE p(oId TEXT,status TEXT,currentRevisionId TEXT,currentLevelSchemeId TEXT)");
            sql("CREATE TABLE r(oId TEXT,professionId TEXT,status TEXT)");
            sql("CREATE TABLE s(oId TEXT,professionId TEXT,professionRevisionId TEXT,status TEXT)");
            sql("CREATE TABLE l(oId TEXT,schemeId TEXT,levelCode TEXT)");
            sql("CREATE TABLE a(oId TEXT,professionId TEXT,currentRevisionId TEXT,status TEXT,sortOrder INT)");
            sql("CREATE TABLE ar(oId TEXT,automationId TEXT,professionId TEXT,status TEXT,triggerType TEXT,configurationJson TEXT)");
            sql("CREATE TABLE rw(oId TEXT,schemeId TEXT,levelId TEXT,levelCode TEXT,rewardType TEXT,rewardConfigJson TEXT,sortOrder INT)");
            sql("INSERT INTO u VALUES('1',0,'PRIVATE'),('2',0,'PRIVATE'),('3',4,'PRIVATE')");
            sql("INSERT INTO p VALUES('10','PUBLISHED','100','200'),('11','RETIRED','110','210'),('12','PUBLISHED','120','220')");
            sql("INSERT INTO r VALUES('100','10','PUBLISHED'),('101','10','RETIRED'),('110','11','RETIRED'),('120','12','PUBLISHED')");
            sql("INSERT INTO s VALUES('200','10','100','PUBLISHED'),('201','10','101','RETIRED'),('220','12','120','PUBLISHED')");
            sql("INSERT INTO l VALUES('300','200','L1'),('301','200','L2'),('302','201','L1')");
            sql("INSERT INTO a VALUES('400','10','500','PUBLISHED',1),('401','10','501','DRAFT',0),('402','11','502','PUBLISHED',0),('403','10','503','PUBLISHED',2)");
            sql("INSERT INTO ar VALUES('500','400','10','PUBLISHED','article.published','PRIVATE_CONDITION'),('501','401','10','DRAFT','comment.published','PRIVATE_CONDITION'),('502','402','11','PUBLISHED','comment.published','PRIVATE_CONDITION'),('503','403','10','PUBLISHED','article.published','PRIVATE_ACTIONS')");
            sql("INSERT INTO rw VALUES('600','200','300','L1','POINT',?,1),('601','200','301','L2','MEDAL',?,2),('602','201','302','L1','POINT',?,0)","{\"amount\":12,\"memo\":\"PRIVATE_MEMO\"}","{\"medalId\":\"PRIVATE_MEDAL\",\"durationMillis\":0,\"data\":\"PRIVATE_DATA\"}","{\"amount\":999}");
            inject(query,"users",new UserRepository(){public String getName(){return "u";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"professions",new ProfessionRepository(){public String getName(){return "p";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"revisions",new ProfessionRevisionRepository(){public String getName(){return "r";}});
            inject(query,"schemes",new ProfessionLevelSchemeRepository(){public String getName(){return "s";}});
            inject(query,"levels",new ProfessionLevelRepository(){public String getName(){return "l";}});
            inject(query,"rewards",new ProfessionLevelRewardRepository(){public String getName(){return "rw";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"automations",new ProfessionAutomationRepository(){public String getName(){return "a";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"automationRevisions",new ProfessionAutomationRevisionRepository(){public String getName(){return "ar";}});
            inject(controller,"query",query);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String t){return Set.of("owner","other","wrong","disabled").contains(t)?new JSONObject().put("scope",t.equals("wrong")?"content.read":"profile.read").put("user",new JSONObject().put("oId",t.equals("other")?"2":t.equals("disabled")?"3":"1")):null;}});
        }
        void sql(String q,Object...a)throws Exception{try(var p=db.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);p.executeUpdate();}}
        List<JSONObject> selectSQL(String q,Object...a)throws RepositoryException{
            reads++;Assert.assertTrue(q.startsWith("SELECT "));Assert.assertFalse(q.contains("SELECT *"));
            for(String secret:List.of("userPassword","configurationJson","createdBy","effectId","sourceEventId"))Assert.assertFalse(q.contains(secret));
            if(reads==failAt)throw new RepositoryException("PRIVATE_DRIVER_FAILURE");
            try{
                if(reads==disableAt)sql("UPDATE u SET userStatus=4 WHERE oId='1'");
                if(reads==changeAt)sql("UPDATE p SET currentRevisionId='101' WHERE oId='10'");
                try(var p=db.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);try(var r=p.executeQuery()){var rows=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));rows.add(row);}return rows;}}
            }catch(Exception e){throw new RepositoryException(e);}
        }
        RequestContext http(String token,String profession)throws Exception{
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/professions/"+profession+"/guides?apiKey=legacy&userId=2");
            try{raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw);request.setParams(new HashMap<>(Map.of("apiKey","legacy","userId","2")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->controller.guides(context));c.handle();return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();}
    }
    interface Action{void run()throws Exception;}
    static void error(int status,Action a)throws Exception{try{a.run();Assert.fail("Expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertFalse(e.getMessage().contains("PRIVATE"));}}
    static void safe(JSONObject o){for(String s:List.of("PRIVATE","configurationJson","rewardConfigJson","currentRevisionId","schemeId","levelId","automationId","effectId","userId"))Assert.assertFalse(o.toString().contains(s),s);}
    public static void main(String[]args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            var d=f.query.guides("1","10");safe(d);Assert.assertEquals(d.keySet(),Set.of("professionId","triggers","rewards"));Assert.assertEquals(d.getString("professionId"),"10");
            Assert.assertEquals(d.getJSONArray("triggers").length(),2);Assert.assertEquals(d.getJSONArray("triggers").getJSONObject(0).keySet(),Set.of("triggerType"));
            Assert.assertEquals(d.getJSONArray("rewards").getJSONObject(0).getLong("amount"),12L);Assert.assertEquals(d.getJSONArray("rewards").getJSONObject(1).keySet(),Set.of("rewardType"));
            Assert.assertEquals(f.query.guides("2","10").toString(),d.toString());Assert.assertTrue(f.query.guides("1","12").getJSONArray("triggers").isEmpty());
            Assert.assertTrue(f.query.guides("1","12").getJSONArray("rewards").isEmpty());
            error(404,()->f.query.guides("1","11"));error(404,()->f.query.guides("1","99"));
        }
        System.out.println("PASS 1 actual published selection metadata, current scheme rewards only, duplicate trigger preservation, strict response whitelist, no owner progress synthesis");
        try(var f=new Fixture()){
            final var before=f.query.guides("1","10");
            // Actual publishProfession shape: retire old definition, publish the new one, only move currentRevisionId.
            f.sql("UPDATE r SET status='RETIRED' WHERE oId='100'");
            f.sql("INSERT INTO r VALUES('102','10','PUBLISHED')");
            f.sql("UPDATE p SET currentRevisionId='102' WHERE oId='10'");
            final var after=f.query.guides("1","10");Assert.assertTrue(before.similar(after));
            f.sql("UPDATE r SET status='DRAFT' WHERE oId='100'");error(503,()->f.query.guides("1","10"));
        }
        System.out.println("PASS 8 real definition-publication shape preserves current scheme rewards through its retired definition revision; draft historical binding rejected");
        for(String sql:List.of("UPDATE r SET professionId='11' WHERE oId='100'","UPDATE r SET status='DRAFT' WHERE oId='100'","UPDATE s SET professionRevisionId='110' WHERE oId='200'","UPDATE s SET professionRevisionId='999' WHERE oId='200'","UPDATE s SET professionId='11' WHERE oId='200'","UPDATE s SET status='RETIRED' WHERE oId='200'","DELETE FROM r WHERE oId='100'","INSERT INTO s SELECT * FROM s WHERE oId='200'"))try(var f=new Fixture()){f.sql(sql);error(503,()->f.query.guides("1","10"));}
        System.out.println("PASS 2 exact profession revision and scheme ownership/status, missing and duplicate metadata reject instead of mixing versions");
        for(String sql:List.of("UPDATE ar SET automationId='401' WHERE oId='500'","UPDATE ar SET professionId='11' WHERE oId='500'","UPDATE ar SET status='DRAFT' WHERE oId='500'","UPDATE ar SET triggerType='private.trigger' WHERE oId='500'","DELETE FROM ar WHERE oId='500'","UPDATE rw SET levelId='302' WHERE oId='600'","UPDATE rw SET levelCode='L2' WHERE oId='600'","INSERT INTO ar SELECT * FROM ar WHERE oId='500'","INSERT INTO l SELECT * FROM l WHERE oId='300'"))try(var f=new Fixture()){f.sql(sql);error(503,()->f.query.guides("1","10"));}
        System.out.println("PASS 3 automation/current revision exact parent binding and reward same-scheme level ID/code binding; malformed and duplicate joins fail closed");
        for(String raw:List.of("{}","{\"amount\":0}","{\"amount\":0.5}","{\"amount\":\"1\"}","{\"amount\":2147483648}","{\"amount\":1,\"effectId\":\"PRIVATE\"}","{\"amount\":1,\"memo\":\"<script>\"}","not json","{\"amount\":1,\"memo\":\""+"x".repeat(4096)+"\"}"))try(var f=new Fixture()){f.sql("UPDATE rw SET rewardConfigJson=? WHERE oId='600'",raw);error(503,()->f.query.guides("1","10"));}
        for(String raw:List.of("{}","{\"medalId\":1}","{\"medalId\":\"m\",\"durationMillis\":-1}","{\"medalId\":\"m\",\"durationMillis\":\"1\"}","{\"medalId\":\"m\",\"data\":null}"))try(var f=new Fixture()){f.sql("UPDATE rw SET rewardConfigJson=? WHERE oId='601'",raw);error(503,()->f.query.guides("1","10"));}
        try(var f=new Fixture()){f.sql("UPDATE rw SET rewardConfigJson=? WHERE oId='600'","{\n\"amount\": 4\n}");Assert.assertEquals(f.query.guides("1","10").getJSONArray("rewards").getJSONObject(0).getLong("amount"),4L);f.sql("UPDATE rw SET rewardType='SECRET' WHERE oId='600'");error(503,()->f.query.guides("1","10"));}
        error(503,()->ApiV3ProfessionGuideDto.number(new JSONObject().put("n",new java.math.BigInteger("9223372036854775808")),"n"));
        error(503,()->ApiV3ProfessionGuideDto.text(new JSONObject().put("n","\uD800"),"n",64,false));
        System.out.println("PASS 4 strict reward JSON/numbers/types and Unicode, safe bound amount and medal summary, oversized/malformed values never coerce or leak");
        try(var f=new Fixture()){for(int i=0;i<511;i++){f.sql("INSERT INTO a VALUES(?,?,?,'PUBLISHED',0)","8"+i,"10","9"+i);f.sql("INSERT INTO ar VALUES(?,?,?,'PUBLISHED','article.published','PRIVATE')","9"+i,"8"+i,"10");}error(503,()->f.query.guides("1","10"));}
        try(var f=new Fixture()){for(int i=0;i<511;i++)f.sql("INSERT INTO rw VALUES(?,?,?,?,?,?,0)","8"+i,"200","300","L1","POINT","{\"amount\":1}");error(503,()->f.query.guides("1","10"));}
        System.out.println("PASS 5 actual limit-plus-one technical budgets reject oversized sources/rewards without truncation");
        for(int stage=1;stage<=6;stage++)try(var f=new Fixture()){f.failAt=stage;error(503,()->f.query.guides("1","10"));}
        try(var f=new Fixture()){f.disableAt=6;error(404,()->f.query.guides("1","10"));}
        try(var f=new Fixture()){f.changeAt=5;error(503,()->f.query.guides("1","10"));}
        for(String value:Arrays.asList(null,"","1 OR 1=1","x","1".repeat(20)))try(var f=new Fixture()){error(422,()->f.query.guides("1",value));error(422,()->f.query.guides(value,"10"));Assert.assertEquals(f.reads,0);}
        try(var f=new Fixture()){error(404,()->f.query.guides("3","10"));}
        System.out.println("PASS 6 sanitized SQL failures at every stage, active actor and current profession version recheck, invalid IDs and disabled actors");
        try(var f=new Fixture()){
            ApiV3ProfessionGuideProcessor.registerRoutes(f.controller,f.auth);Dispatcher.mapping();
            for(String t:Arrays.asList(null,"bad","wrong")){int before=f.reads;Assert.assertEquals(f.http(t,"10").attr("test.status"),"wrong".equals(t)?403:401);Assert.assertEquals(f.reads,before);}
            var c=f.http("owner","10");Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(c.getResponse().getHeader("Vary"),"Authorization");safe((JSONObject)c.attr("test.data"));
            Assert.assertEquals(f.http("other","10").attr("test.status"),200);Assert.assertEquals(f.http("disabled","10").attr("test.status"),404);Assert.assertEquals(f.http("owner","11").attr("test.status"),404);Assert.assertEquals(f.http("owner","invalid").attr("test.status"),422);
        }
        System.out.println("PASS 7 actual Dispatcher/controller/auth route rejects Cookie/apiKey fallback and wrong scope, active Bearer-only metadata and no-store envelope");
        System.out.println("Profession guide isolated tests passed: 8 groups");
    }
}
