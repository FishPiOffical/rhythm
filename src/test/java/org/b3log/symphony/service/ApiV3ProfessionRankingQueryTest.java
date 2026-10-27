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

/** Real ranking SQL with a SQLite database; controller/auth/Dispatcher use production classes. */
public class ApiV3ProfessionRankingQueryTest {
    static void inject(Object target,String key,Object value)throws Exception{var f=target.getClass().getDeclaredField(key);f.setAccessible(true);f.set(target,value);}
    static JSONObject visibility(String ranking,String experience){var j=new JSONObject();for(String k:List.of("primaryProfession","allProfessions","experience","rarity","majorContributions","contributionStats","activityFeed","levelHistory","rewards","ranking"))j.put(k,"PUBLIC");return j.put("ranking",ranking).put("experience",experience);}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3ProfessionRankingQueryService query=new ApiV3ProfessionRankingQueryService();
        final ApiV3ProfessionRankingProcessor controller=new ApiV3ProfessionRankingProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        int reads,failAt=-1,changeAt=-1;String changeSql="UPDATE pr SET moduleVisibilityJson='{}' WHERE userId='2'";
        Fixture()throws Exception{
            sql("CREATE TABLE u(oId TEXT COLLATE NOCASE,userStatus INT,userName TEXT,userNickname TEXT,userAvatarURL TEXT,userRole TEXT,userPassword TEXT)");
            sql("CREATE TABLE p(oId TEXT,professionCode TEXT,status TEXT COLLATE NOCASE,currentRevisionId TEXT)");
            sql("CREATE TABLE r(oId TEXT,professionId TEXT,status TEXT COLLATE NOCASE,displayName TEXT)");
            sql("CREATE TABLE s(oId TEXT,professionId TEXT,status TEXT COLLATE NOCASE)");
            sql("CREATE TABLE l(oId TEXT,schemeId TEXT,levelCode TEXT COLLATE NOCASE,displayName TEXT)");
            sql("CREATE TABLE up(oId TEXT,userId TEXT,professionId TEXT,professionRevisionId TEXT,levelSchemeId TEXT,totalExperience,currentLevelCode TEXT)");
            sql("CREATE TABLE pr(oId TEXT,userId TEXT,moduleVisibilityJson TEXT,userPrivacyVersion INT)");
            sql("INSERT INTO p VALUES('10','writer','PUBLISHED','101'),('11','reader','RETIRED','110')");
            sql("INSERT INTO r VALUES('100','10','RETIRED','Historical writer'),('101','10','PUBLISHED','Current writer'),('110','11','RETIRED','Reader')");
            sql("INSERT INTO s VALUES('300','10','RETIRED'),('301','10','PUBLISHED')");
            sql("INSERT INTO l VALUES('400','300','L1','Historical level'),('401','301','L2','Current level')");
            for(int i=1;i<=75;i++)candidate(i,i==1?1:1000-i,i%2==0);
            privacy(2,"HIDDEN","PUBLIC");privacy(3,"SELF","PUBLIC");privacy(4,"LOGGED_IN","PUBLIC");privacy(5,"PUBLIC","HIDDEN");privacy(6,"PUBLIC","SELF");
            sql("UPDATE u SET userStatus=4 WHERE oId='7'");sql("UPDATE u SET userName='_' WHERE oId='8'");
            inject(query,"users",new UserRepository(){public String getName(){return "u";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"professions",new ProfessionRepository(){public String getName(){return "p";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"revisions",new ProfessionRevisionRepository(){public String getName(){return "r";}});
            inject(query,"schemes",new ProfessionLevelSchemeRepository(){public String getName(){return "s";}});
            inject(query,"levels",new ProfessionLevelRepository(){public String getName(){return "l";}});
            inject(query,"privacy",new UserProfessionPrivacyRepository(){public String getName(){return "pr";}});
            inject(query,"progress",new UserProfessionRepository(){public String getName(){return "up";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(controller,"query",query);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String t){return Set.of("owner","other","wrong","disabled").contains(t)?new JSONObject().put("scope",t.equals("wrong")?"content.read":"profile.read").put("user",new JSONObject().put("oId",t.equals("other")?"3":t.equals("disabled")?"7":"1")):null;}});
        }
        void candidate(int n,int experience,boolean historical)throws Exception{
            String id=""+n;sql("INSERT INTO u VALUES(?,0,?,'',?,'adminRole','PRIVATE_PASSWORD')",id,"user"+n,"https://example.com/avatar.png");
            sql("INSERT INTO up VALUES(?,?,?,?,?,?,?)","1000"+n,id,"10",historical?"100":"101",historical?"300":"301",experience,historical?"L1":"L2");
        }
        void privacy(int n,String rank,String experience)throws Exception{sql("INSERT INTO pr VALUES(?,?,?,0)","2000"+n,""+n,visibility(rank,experience).toString());}
        void sql(String q,Object...a)throws Exception{try(var p=db.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);p.executeUpdate();}}
        List<JSONObject> selectSQL(String q,Object...a)throws RepositoryException{
            reads++;Assert.assertTrue(q.startsWith("SELECT "));Assert.assertFalse(q.contains("SELECT *"));
            for(String secret:List.of("userPassword","userRole","userEmail","userPhone","presentation","configJson"))Assert.assertFalse(q.contains(secret));
            if(reads==failAt)throw new RepositoryException("PRIVATE_DRIVER_SECRET");
            try{if(reads==changeAt)sql(changeSql);try(var p=db.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);try(var r=p.executeQuery()){var rows=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));rows.add(row);}return rows;}}}
            catch(Exception e){throw new RepositoryException(e);}
        }
        RequestContext http(String token,Map<String,String> values)throws Exception{
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/professions/10/ranking?apiKey=legacy&userId=2");
            try{raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw);var params=new HashMap<>(Map.of("apiKey","legacy","userId","2"));params.putAll(values);request.setParams(params);
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(controller::ranking);c.handle();return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();}
    }
    interface Action{void run()throws Exception;}
    static void error(int status,Action a)throws Exception{try{a.run();Assert.fail("Expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertFalse(e.getMessage().contains("SECRET"));}}
    static void safe(JSONObject o){for(String secret:List.of("PRIVATE_","userId","actualUserId","progressId","professionRevisionId","levelSchemeId","privacyId","moduleVisibilityJson","requiredExperience"))Assert.assertFalse(o.toString().contains(secret),secret);}
    public static void main(String[]args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            var result=f.query.ranking("1","10",1,50);safe(result);Assert.assertEquals(result.getJSONObject("pagination").getInt("total"),71);Assert.assertEquals(result.getJSONArray("list").length(),50);
            var first=result.getJSONArray("list").getJSONObject(0);Assert.assertEquals(first.getString("userName"),"user4");Assert.assertEquals(first.getString("professionName"),"Historical writer");Assert.assertEquals(first.getString("levelName"),"Historical level");Assert.assertEquals(first.getInt("rank"),1);
            Assert.assertEquals(first.keySet(),Set.of("rank","userName","userNickname","userAvatarURL","professionId","professionCode","professionName","levelCode","levelName","totalExperience"));
            var second=f.query.ranking("1","10",2,50);Assert.assertEquals(second.getJSONArray("list").length(),21);Assert.assertEquals(second.getJSONArray("list").getJSONObject(0).getInt("rank"),51);
            Assert.assertTrue(f.query.ranking("1","10",3,50).getJSONArray("list").isEmpty());Assert.assertEquals(f.query.ranking("3","10",1,50).getJSONObject("pagination").getInt("total"),72);
            Assert.assertEquals(f.selectSQL("SELECT COUNT(*) AS total FROM pr").getFirst().getInt("total"),5);
        }System.out.println("PASS 1 complete visible filtering before count/page/rank beyond fifty, owner SELF, login visibility, historical metadata, no privacy defaults written or secret DTO fields");
        try(var f=new Fixture()){
            f.sql("UPDATE up SET totalExperience=2000 WHERE userId IN ('9','10')");var list=f.query.ranking("1","10",1,50).getJSONArray("list");
            int a=-1,b=-1;for(int i=0;i<list.length();i++){if(list.getJSONObject(i).getString("userName").equals("user10"))a=i;if(list.getJSONObject(i).getString("userName").equals("user9"))b=i;}Assert.assertTrue(a>=0&&b==a+1);
            f.sql("UPDATE pr SET moduleVisibilityJson=? WHERE userId='4'",visibility("PUBLIC","SELF").toString());var hidden=f.query.ranking("1","10",1,20).getJSONArray("list");
            for(Object item:hidden)if(((JSONObject)item).getString("userName").equals("user4"))Assert.assertFalse(((JSONObject)item).has("totalExperience"));
        }System.out.println("PASS 2 stable equal-experience userId ordering; hidden experience preserves explicitly permitted rank/level but omits exact experience; no administrator bypass");
        try(var f=new Fixture()){
            for(String invalid:List.of("{}",visibility("public","PUBLIC").toString(),visibility("PUBLIC","PUBLIC").put("ranking",true).toString(),"x")){
                f.sql("UPDATE pr SET moduleVisibilityJson=? WHERE userId='2'",invalid);error(503,()->f.query.ranking("1","10",1,20));}
            f.sql("UPDATE pr SET moduleVisibilityJson=? WHERE userId='2'",visibility("HIDDEN","PUBLIC").toString());
            f.sql("INSERT INTO pr VALUES('999','2',?,0)",visibility("PUBLIC","PUBLIC").toString());error(503,()->f.query.ranking("1","10",1,20));
        }System.out.println("PASS 3 malformed/incomplete/typed/lowercase privacy and duplicate privacy fail closed with CI database fixture");
        try(var f=new Fixture()){
            f.sql("UPDATE up SET totalExperience=0.5 WHERE userId='4'");error(503,()->f.query.ranking("1","10",1,20));f.sql("UPDATE up SET totalExperience=996 WHERE userId='4'");
            f.sql("UPDATE s SET professionId='11' WHERE oId='300'");error(503,()->f.query.ranking("1","10",1,20));f.sql("UPDATE s SET professionId='10' WHERE oId='300'");
            f.sql("UPDATE r SET status='published' WHERE oId='100'");error(503,()->f.query.ranking("1","10",1,20));
            error(503,()->ApiV3ProfessionRankingDto.number(new JSONObject().put("n",new java.math.BigInteger("9223372036854775808")),"n"));
            error(503,()->ApiV3ProfessionRankingDto.text(new JSONObject().put("n","\uD800"),"n",10,false));
        }System.out.println("PASS 4 wrong historical parent, invalid status, fractions/overflow and malformed Unicode fail closed");
        for(String change:List.of("UPDATE pr SET moduleVisibilityJson='{}' WHERE userId='2'","UPDATE u SET userStatus=4 WHERE oId='4'","UPDATE up SET totalExperience=99 WHERE userId='4'","UPDATE r SET displayName='Changed' WHERE oId='100'"))try(var f=new Fixture()){f.changeAt=4;f.changeSql=change;error(503,()->f.query.ranking("1","10",1,20));}
        try(var f=new Fixture()){f.changeAt=6;f.changeSql="UPDATE u SET userStatus=4 WHERE oId='1'";error(404,()->f.query.ranking("1","10",1,20));}
        for(int i=1;i<=6;i++)try(var f=new Fixture()){f.failAt=i;error(503,()->f.query.ranking("1","10",1,20));}
        System.out.println("PASS 5 final candidate/privacy/actor/status/metadata changes reject mixed response and all SQL failures return sanitized errors");
        try(var f=new Fixture()){
            for(int i=76;i<=4100;i++)f.candidate(i,1,false);error(503,()->f.query.ranking("1","10",1,20));
        }System.out.println("PASS 6 bounded complete population returns 503 instead of fake total or silently truncated ranking");
        try(var f=new Fixture()){
            for(String bad:Arrays.asList(null,"","x","1 OR 1=1","1".repeat(20)))error(422,()->f.query.ranking("1",bad,1,20));
            error(422,()->f.query.ranking("1","10",0,20));error(422,()->f.query.ranking("1","10",1,51));error(422,()->f.query.ranking("1","10",Integer.MAX_VALUE,20));
            error(404,()->f.query.ranking("7","10",1,20));error(404,()->f.query.ranking("1","11",1,20));error(404,()->f.query.ranking("1","999",1,20));
            f.sql("UPDATE p SET status='published' WHERE oId='10'");error(404,()->f.query.ranking("1","10",1,20));
        }System.out.println("PASS 7 malformed identities/paging and missing, inactive or unpublished current targets reject before data projection");
        try(var f=new Fixture()){
            ApiV3ProfessionRankingProcessor.registerRoutes(f.controller,f.auth);Dispatcher.mapping();
            for(String t:Arrays.asList(null,"bad","wrong")){int before=f.reads;Assert.assertEquals(f.http(t,Map.of()).attr("test.status"),"wrong".equals(t)?403:401);Assert.assertEquals(before,f.reads);}
            var c=f.http("owner",Map.of());Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(c.getResponse().getHeader("Vary"),"Authorization");safe((JSONObject)c.attr("test.data"));
            Assert.assertEquals(f.http("disabled",Map.of()).attr("test.status"),404);Assert.assertEquals(f.http("owner",Map.of("size","51")).attr("test.status"),422);
        }System.out.println("PASS 8 actual route/controller/Bearer auth requires profile.read and current viewer, ignores Cookie/apiKey impersonation, validates paging and disables caching");
        try(var f=new Fixture()){
            var logged=f.query.summary("10","1","4").orElseThrow();Assert.assertEquals(logged.getInt("rank"),1);Assert.assertEquals(logged.getInt("totalUsers"),71);
            Assert.assertEquals(logged.getDouble("surpassedPercent"),70*100D/71);
            Assert.assertEquals(logged.keySet(),Set.of("rank","totalUsers","surpassedPercent"));
            Assert.assertTrue(f.query.summary("10","","4").isEmpty());Assert.assertTrue(f.query.summary("10","","3").isEmpty());
            Assert.assertTrue(f.query.summary("10","1","2").isEmpty());Assert.assertTrue(f.query.summary("10","1","7").isEmpty());
            Assert.assertEquals(f.query.summary("10","3","3").orElseThrow().getInt("rank"),1);
            Assert.assertEquals(f.query.summary("10","","5").orElseThrow().getInt("totalUsers"),70);
            error(422,()->f.query.summary("10",null,"4"));error(404,()->f.query.summary("10","7","4"));
            f.sql("UPDATE pr SET moduleVisibilityJson=? WHERE userId='2'",visibility("HIDDEN","PUBLIC").toString(2));Assert.assertTrue(f.query.summary("10","1","2").isEmpty());
        }System.out.println("PASS 9 shared summary uses the identical visible population for public/anonymous profiles, no hidden-target result, exact stable rank percentage, and valid formatted privacy JSON");
        try(var f=new Fixture()){
            var budget=new ApiV3ProfessionRankingQueryService.CandidateBudget();
            for(int i=0;i<54;i++)Assert.assertTrue(f.query.summary("10","1","4",budget).isPresent()); // 74 actual candidates * 2 * 54 = 7992.
            Assert.assertTrue(f.query.summary("10","1","4",budget).isPresent()); // 8140 rows still fit.
            error(503,()->f.query.summary("10","1","4",budget));
            Assert.assertTrue(f.query.summary("10","1","4").isPresent()); // Independent calls retain the single-board contract.
        }
        try(var f=new Fixture()){
            var budget=new ApiV3ProfessionRankingQueryService.CandidateBudget();
            for(int i=0;i<55;i++)f.query.summary("10","1","4",budget);
            // Actual new candidates, not any old COUNT estimate, consume the remaining shared request allowance.
            for(int i=76;i<200;i++)f.candidate(i,1,false);
            error(503,()->f.query.summary("10","1","4",budget));
        }System.out.println("PASS 10 shared public-profile budget counts actual rows across both reads and all boards, catches concurrent population growth, and leaves standalone rankings unchanged");
        System.out.println("Profession ranking isolated tests passed: 10 groups");
    }
}
