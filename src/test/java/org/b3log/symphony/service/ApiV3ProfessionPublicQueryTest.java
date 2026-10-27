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

/** Real profile and shared ranking SQL, DTOs, auth, routes and anonymous shield, isolated SQLite adapters. */
public class ApiV3ProfessionPublicQueryTest {
    static final Set<String> MODULES=Set.of("primaryProfession","allProfessions","experience","rarity","majorContributions","contributionStats","activityFeed","levelHistory","rewards","ranking");
    static void inject(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static JSONObject policy(String scope){var json=new JSONObject();for(String key:MODULES)json.put(key,scope);return json;}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3ProfessionPublicQueryService query=new ApiV3ProfessionPublicQueryService();
        final ApiV3ProfessionRankingQueryService ranking=new ApiV3ProfessionRankingQueryService();
        final ApiV3ProfessionPublicProcessor controller=new ApiV3ProfessionPublicProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();final AnonymousViewCheckMidware anonymous=new AnonymousViewCheckMidware();
        int reads,failAt=-1,changeAt=-1,disableAt=-1;boolean rewriteBody,changeSourceOnRecheck;final Map<String,Integer> sourceReads=new HashMap<>();final long now=System.currentTimeMillis();final String ip="203.0.113.57";
        Fixture()throws Exception {
            org.sqlite.Function.create(db,"SHA2",new org.sqlite.Function(){protected void xFunc()throws SQLException{try{result(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value_text(0).getBytes(java.nio.charset.StandardCharsets.UTF_8))));}catch(java.security.NoSuchAlgorithmException e){throw new SQLException(e);}}});
            org.sqlite.Function.create(db,"OCTET_LENGTH",new org.sqlite.Function(){protected void xFunc()throws SQLException{result(value_text(0).getBytes(java.nio.charset.StandardCharsets.UTF_8).length);}});
            sql("CREATE TABLE u(oId TEXT,userName TEXT COLLATE NOCASE,userRole TEXT,userStatus INT,userNickname TEXT,userAvatarURL TEXT,userBreezemoonStatus INT DEFAULT 0,userPassword TEXT)");
            sql("CREATE TABLE p(oId TEXT,professionCode TEXT,status TEXT,currentRevisionId TEXT)");
            sql("CREATE TABLE r(oId TEXT,professionId TEXT,status TEXT,displayName TEXT,shortName TEXT,description TEXT)");
            sql("CREATE TABLE s(oId TEXT,professionId TEXT,status TEXT)");
            sql("CREATE TABLE l(oId TEXT,schemeId TEXT,levelCode TEXT COLLATE NOCASE,displayName TEXT,requiredTotalExperience)");
            sql("CREATE TABLE up(oId TEXT,userId TEXT,professionId TEXT,professionRevisionId TEXT,levelSchemeId TEXT,totalExperience,currentLevelCode TEXT)");
            sql("CREATE TABLE pr(oId TEXT,userId TEXT,primaryProfessionId TEXT,moduleVisibilityJson TEXT,userPrivacyVersion)");
            sql("CREATE TABLE c(oId TEXT,userId TEXT,professionId TEXT,actionCode TEXT,experienceSum,eventCount,lastOccurredAt)");
            sql("CREATE TABLE e(oId TEXT,userId TEXT,professionId TEXT,sourceEventId TEXT,actionCode TEXT,actualExperienceDelta,beforeLevelCode TEXT,afterLevelCode TEXT,occurredAt,status TEXT COLLATE NOCASE)");
            sql("CREATE TABLE src(oId TEXT,subjectUserId TEXT,visibilityClass TEXT,targetType TEXT,targetId TEXT)");
            sql("CREATE TABLE rw(oId TEXT,userId TEXT,professionId TEXT,levelCode TEXT,rewardCode TEXT,rewardType TEXT,status TEXT,grantedAt)");
            sql("CREATE TABLE a(oId TEXT,articleAuthorId TEXT,articleType INT,articleStatus INT,articleAnonymous INT,articleAnonymousView INT,articleContent TEXT)");
            sql("CREATE TABLE cm(oId TEXT,commentAuthorId TEXT,commentOnArticleId TEXT,commentVisible INT,commentStatus INT,commentAnonymous INT)");
            sql("CREATE TABLE m(oId TEXT,breezemoonStatus INT,breezemoonAuthorId TEXT)");sql("CREATE TABLE rp(oId TEXT,repeaterContentStatus INT,repeaterContentAuthorId TEXT)");sql("CREATE TABLE ch(oId TEXT)");
            sql("INSERT INTO u VALUES('1','Alice','member',0,'Alice','',0,'PRIVATE_PASSWORD'),('2','Bob','member',0,'Bob','',0,'PRIVATE_PASSWORD'),('3','Admin','adminRole',0,'Admin','',0,'PRIVATE'),('4','Disabled','member',4,'','',0,'PRIVATE')");
            sql("INSERT INTO p VALUES('10','writer','PUBLISHED','101'),('11','reader','PUBLISHED','110'),('12','old','RETIRED','120')");
            sql("INSERT INTO r VALUES('100','10','RETIRED','Historical writer','Old','Old description'),('101','10','PUBLISHED','Current writer','New','New description'),('110','11','PUBLISHED','Reader','',''),('120','12','RETIRED','Retired','','')");
            sql("INSERT INTO s VALUES('300','10','RETIRED'),('301','10','PUBLISHED'),('310','11','PUBLISHED'),('320','12','RETIRED')");
            sql("INSERT INTO l VALUES('400','300','L1','Historical level',0),('401','301','L2','New level',0),('410','310','L1','Reader level',0),('420','320','L1','Old level',0)");
            sql("INSERT INTO up VALUES('500','1','10','100','300',50,'L1'),('501','2','10','101','301',90,'L2'),('502','1','12','120','320',10,'L1')");
            sql("INSERT INTO pr VALUES('600','1','11',?,1),('601','2','10',?,1)",policy("PUBLIC").toString(),policy("PUBLIC").toString());
            sql("INSERT INTO c VALUES('700','1','10','article.publish',50,2,?),('701','1','10','article.remove',-5,1,?)",now,now);
            sql("INSERT INTO rw VALUES('800','1','10','L1','first','MEDAL','GRANTED',?)",now);
            sql("INSERT INTO a VALUES('900','2',0,0,0,0,'PRIVATE_ARTICLE_BODY'),('901','2',1,0,0,0,'@Alice invited'),('902','2',0,0,1,0,'ANONYMOUS_BODY'),('903','2',0,0,0,1,'LOGIN_ONLY')");
            sql("INSERT INTO cm VALUES('910','1','900',1,0,0),('911','1','901',0,0,0),('912','1','900',0,0,1)");
            sql("INSERT INTO m VALUES('920',0,'1')");sql("INSERT INTO rp VALUES('930',0,'1')");sql("INSERT INTO ch VALUES('940')");
            source("1000","PUBLIC","article","900");source("1001","PUBLIC","article","901");source("1002","PUBLIC","article","902");source("1003","PUBLIC","comment","910");source("1004","PUBLIC","chatroom_message","940");source("1005","PRIVATE","article","900");source("1006","PUBLIC","breezemoon","920");source("1007","PUBLIC","repeater","930");source("1008","PUBLIC","article","903");source("1009","PUBLIC","comment","912");
            for(int i=0;i<10;i++)sql("INSERT INTO e VALUES(?,?,?,?,?,?,?,?,?,?)","20"+i,"1","10","100"+i,"article.publish",i+1,"L1","L2",now-i,"APPLIED");
            var users=new UserRepository(){public String getName(){return "u";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}};
            var professions=new ProfessionRepository(){public String getName(){return "p";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}};
            var revisions=new ProfessionRevisionRepository(){public String getName(){return "r";}};
            var schemes=new ProfessionLevelSchemeRepository(){public String getName(){return "s";}};
            var levels=new ProfessionLevelRepository(){public String getName(){return "l";}};
            var progress=new UserProfessionRepository(){public String getName(){return "up";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}};
            var privacy=new UserProfessionPrivacyRepository(){public String getName(){return "pr";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}};
            for(Object target:List.of(query,ranking)){inject(target,"users",users);inject(target,"professions",professions);inject(target,"revisions",revisions);inject(target,"schemes",schemes);inject(target,"levels",levels);inject(target,"progress",progress);inject(target,"privacy",privacy);}
            inject(query,"contributions",new UserProfessionContributionRepository(){public String getName(){return "c";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"effects",new ProfessionEventEffectRepository(){public String getName(){return "e";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"sources",new ProfessionSourceEventRepository(){public String getName(){return "src";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"rewards",new ProfessionRewardGrantRepository(){public String getName(){return "rw";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"articles",new ArticleRepository(){public String getName(){return "a";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"comments",new CommentRepository(){public String getName(){return "cm";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"moons",new BreezemoonRepository(){public String getName(){return "m";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"repeaters",new RepeaterContentRepository(){public String getName(){return "rp";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"chats",new ChatRoomRepository(){public String getName(){return "ch";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"ranking",ranking);inject(controller,"query",query);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("owner","bob","admin","wrong").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("bob")?"2":token.equals("admin")?"3":"1")).put("scope",token.equals("wrong")?"content.read":"profile.read"):null;}});
            inject(anonymous,"optionQueryService",new OptionQueryService());resetShield();AnonymousViewCheckMidware.setEnabled(false);
        }
        void resetShield(){AnonymousViewCheckMidware.whiteList.clear();AnonymousViewCheckMidware.ipVisitCountCache.invalidateAll();AnonymousViewCheckMidware.ipFirstVisitTimeCache.invalidateAll();AnonymousViewCheckMidware.ipLastCaptchaPassTimeCache.invalidateAll();AnonymousViewCheckMidware.ipBlacklistCache.invalidateAll();AnonymousViewCheckMidware.setFirstVisitCaptchaEnabled(true);}
        void source(String id,String visibility,String type,String target)throws Exception{sql("INSERT INTO src VALUES(?,?,?,?,?)",id,"1",visibility,type,target);}
        void visibility(JSONObject value)throws Exception{sql("UPDATE pr SET moduleVisibilityJson=? WHERE userId='1'",value.toString());}
        void sql(String q,Object...a)throws Exception{try(var p=db.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);p.executeUpdate();}}
        List<JSONObject> selectSQL(String q,Object...a)throws RepositoryException{
            reads++;Assert.assertTrue(q.startsWith("SELECT "));for(String secret:List.of("SELECT *","userPassword","payloadJson","rewardConfigJson","externalReferenceId","createdBy"))Assert.assertFalse(q.contains(secret));
            if(reads==failAt)throw new RepositoryException("PRIVATE_DRIVER_SECRET");
            try{
                if(rewriteBody&&q.startsWith("SELECT articleContent")){rewriteBody=false;sql("UPDATE a SET articleContent='@Bobby invited' WHERE oId='901'");}
                if(changeSourceOnRecheck&&q.startsWith("SELECT subjectUserId")){
                    String source=(String)a[0];int count=sourceReads.merge(source,1,Integer::sum);if(count==2){changeSourceOnRecheck=false;sql("UPDATE src SET visibilityClass='PRIVATE' WHERE oId=?",source);}
                }
                if(reads==changeAt)visibility(policy("HIDDEN"));if(reads==disableAt)sql("UPDATE u SET userStatus=4 WHERE oId='1'");
                try(var p=db.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);try(var r=p.executeQuery()){var rows=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));rows.add(row);}return rows;}}
            }catch(Exception e){throw new RepositoryException(e);}
        }
        RequestContext http(String token)throws Exception{
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/users/Alice/professions?apiKey=legacy");
            try{raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw){public String getRemoteAddr(){return ip;}};request.setParams(new HashMap<>(Map.of("apiKey","legacy")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)){public void sendRedirect(String uri){setHeader("Location",uri);setStatus(302);}});c.attr("user",new JSONObject().put("oId","1"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->controller.profile(context));c.handle();return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();resetShield();AnonymousViewCheckMidware.setEnabled(true);}
    }
    interface Action{void run()throws Exception;}
    static void error(int code,Action action)throws Exception{try{action.run();Assert.fail("Expected "+code);}catch(ApiV3Exception e){Assert.assertEquals(e.status,code);Assert.assertFalse(e.getMessage().contains("PRIVATE"));}}
    static void safe(JSONObject value){for(String secret:List.of("PRIVATE","ANONYMOUS_BODY","LOGIN_ONLY","sourceEventId","targetId","sourceLabel","progressId","moduleVisibilityJson","userRole","rewardConfigJson","externalReferenceId"))Assert.assertFalse(value.toString().contains(secret),secret);}
    public static void main(String[]args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            var value=f.query.profile("Alice","");safe(value);var primary=value.getJSONObject("primaryProfession");Assert.assertEquals(primary.getString("state"),"SELECTED");Assert.assertFalse(primary.has("levelCode"));Assert.assertFalse(primary.has("totalExperience"));
            var profession=value.getJSONArray("professions").getJSONObject(0);Assert.assertEquals(profession.getString("displayName"),"Historical writer");Assert.assertEquals(profession.getString("levelName"),"Historical level");Assert.assertEquals(profession.getJSONObject("rarity").getLong("rank"),2L);
            Assert.assertEquals(value.getJSONArray("ranking").getJSONObject(0).getLong("totalUsers"),2L);Assert.assertEquals(value.getJSONArray("professions").getJSONObject(1).getString("state"),"RETIRED");
            Assert.assertEquals(value.getJSONArray("activityFeed").length(),3);Assert.assertEquals(f.query.profile("Alice","1").getJSONArray("activityFeed").length(),7);Assert.assertEquals(f.query.profile("Alice","2").getJSONArray("activityFeed").length(),7);
            Assert.assertEquals(value.getJSONArray("majorContributions").length(),2);Assert.assertEquals(value.getJSONArray("contributionStats").getJSONObject(0).getLong("experienceSum"),45L);Assert.assertEquals(value.getJSONArray("rewards").length(),1);
        }System.out.println("PASS 1 actual historical metadata, selected definition without invented zero progress, retired state, current shared visible ranking and strict all-module whitelists");
        try(var f=new Fixture()){
            for(String scope:List.of("PUBLIC","LOGGED_IN","SELF","HIDDEN")){
                f.visibility(policy(scope));for(String actor:List.of("","1","2","3")){
                    boolean allow=scope.equals("PUBLIC")||scope.equals("LOGGED_IN")&&!actor.isEmpty()||scope.equals("SELF")&&actor.equals("1");
                    if(allow)safe(f.query.profile("Alice",actor));else error(404,()->f.query.profile("Alice",actor));
                }
            }
            var policy=policy("HIDDEN").put("primaryProfession","PUBLIC");f.visibility(policy);var value=f.query.profile("Alice","");Assert.assertEquals(value.keySet(),Set.of("primaryProfession"));
            f.sql("DELETE FROM pr WHERE userId='1'");Assert.assertTrue(f.query.profile("Alice","").has("professions"));Assert.assertEquals(f.selectSQL("SELECT COUNT(*) AS total FROM pr").getFirst().getInt("total"),1);
        }System.out.println("PASS 2 all four visibility scopes, no administrator or owner HIDDEN bypass, omitted modules, missing privacy read-only ALL_PUBLIC defaults");
        try(var f=new Fixture()){
            f.visibility(policy("PUBLIC").put("experience","HIDDEN"));var value=f.query.profile("Alice","1");safe(value);
            for(String forbidden:List.of("totalExperience","requiredExperience","experienceSum","experienceDelta"))Assert.assertFalse(value.toString().contains(forbidden));
            Assert.assertTrue(value.getJSONArray("majorContributions").getJSONObject(0).has("eventCount"));Assert.assertTrue(value.getJSONArray("levelHistory").getJSONObject(0).has("afterLevelCode"));
            f.visibility(policy("PUBLIC").put("ranking","HIDDEN"));value=f.query.profile("Alice","1");Assert.assertFalse(value.has("ranking"));Assert.assertFalse(value.getJSONArray("professions").getJSONObject(0).has("rarity"));
        }System.out.println("PASS 3 experience privacy cannot be bypassed by contributions, thresholds or activity; levels/counts preserved and rarity requires target inclusion in visible ranking");
        try(var f=new Fixture()){
            f.sql("UPDATE a SET articleContent='@Nobody invited' WHERE oId='901'");Assert.assertEquals(f.query.profile("Alice","1").getJSONArray("activityFeed").length(),6);
            f.sql("DELETE FROM ch");Assert.assertEquals(f.query.profile("Alice","1").getJSONArray("activityFeed").length(),5);
            f.sql("UPDATE u SET userStatus=4 WHERE oId='2'");Assert.assertEquals(f.query.profile("Alice","1").getJSONArray("activityFeed").length(),2);
        }System.out.println("PASS 4 PUBLIC source plus current discussion invitation, parent article/private comment, anonymous origin, author state and message existence checks, no owner shortcut");
        try(var f=new Fixture()){
            for(String name:Arrays.asList(null,"","x' OR 1=1","a/b","a".repeat(65)))error(422,()->f.query.profile(name,""));error(404,()->f.query.profile("Disabled",""));error(404,()->f.query.profile("Missing",""));error(404,()->f.query.profile("Alice","4"));
            f.visibility(new JSONObject().put("primaryProfession","PUBLIC"));error(503,()->f.query.profile("Alice",""));f.visibility(policy("PUBLIC"));f.sql("UPDATE up SET totalExperience=0.5 WHERE oId='500'");error(503,()->f.query.profile("Alice",""));
        }
        try(var f=new Fixture()){f.sql("INSERT INTO pr SELECT '999',userId,primaryProfessionId,moduleVisibilityJson,userPrivacyVersion FROM pr WHERE userId='1'");error(503,()->f.query.profile("Alice",""));}
        System.out.println("PASS 5 input grammar, unavailable users, malformed privacy, duplicate records and invalid numeric state fail closed without private diagnostics");
        try(var f=new Fixture()){
            for(int i=0;i<503;i++)f.sql("INSERT INTO e VALUES(?,?,?,?,?,?,?,?,?,?)","90"+i,"1","10","1000","article.publish",1,"L1","L1",f.now,"APPLIED");error(503,()->f.query.profile("Alice",""));
        }
        try(var f=new Fixture()){f.sql("UPDATE a SET articleContent=? WHERE oId='901'","x".repeat(2097153));error(503,()->f.query.profile("Alice","1"));}
        System.out.println("PASS 6 full-view candidate and discussion body budgets fail 503 instead of silently truncating data or reading unbounded source content");
        int totalReads;try(var f=new Fixture()){f.query.profile("Alice","");totalReads=f.reads;}
        for(int i=1;i<=totalReads;i++)try(var f=new Fixture()){f.failAt=i;error(503,()->f.query.profile("Alice",""));}
        try(var f=new Fixture()){f.changeAt=5;error(503,()->f.query.profile("Alice",""));}
        try(var f=new Fixture()){f.disableAt=5;error(404,()->f.query.profile("Alice",""));}
        System.out.println("PASS 7 every real SELECT failure sanitized; final privacy/account/progress rechecks reject mixed or revoked responses");
        try(var f=new Fixture()){
            ApiV3ProfessionPublicProcessor.registerRoutes(f.controller,f.auth,f.anonymous);Dispatcher.mapping();
            for(String token:List.of("bad","wrong")){int reads=f.reads;Assert.assertEquals(f.http(token).attr("test.status"),token.equals("bad")?401:403);Assert.assertEquals(f.reads,reads);}
            f.visibility(policy("SELF"));Assert.assertEquals(f.http(null).attr("test.status"),404);Assert.assertEquals(f.http("bob").attr("test.status"),404);Assert.assertEquals(f.http("owner").attr("test.status"),200);
            f.visibility(policy("PUBLIC"));var c=f.http(null);Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(c.getResponse().getHeader("Vary"),"Authorization");
            f.resetShield();AnonymousViewCheckMidware.setEnabled(true);int reads=f.reads;c=f.http(null);Assert.assertEquals(c.getResponse().getHeader("Location"),"/test");Assert.assertEquals(f.reads,reads);Assert.assertEquals(f.http("owner").attr("test.status"),200);
        }System.out.println("PASS 8 real Dispatcher/auth/controller and anonymous shield, explicit credentials reject invalid scopes; Cookie/apiKey never grant SELF or shield bypass; first visit redirects before SQL");
        try(var f=new Fixture()){f.sql("UPDATE up SET currentLevelCode='l1' WHERE oId='500'");error(503,()->f.query.profile("Alice",""));}
        try(var f=new Fixture()){f.sql("UPDATE e SET status='applied' WHERE oId='200'");error(503,()->f.query.profile("Alice",""));}
        try(var f=new Fixture()){f.sql("UPDATE src SET subjectUserId='2' WHERE oId='1000'");error(503,()->f.query.profile("Alice",""));}
        try(var f=new Fixture()){f.rewriteBody=true;error(503,()->f.query.profile("Alice","1"));}
        try(var f=new Fixture()){f.changeSourceOnRecheck=true;error(503,()->f.query.profile("Alice","1"));}
        try(var f=new Fixture()){
            f.sql("UPDATE u SET userBreezemoonStatus=1 WHERE oId='1'");Assert.assertEquals(f.query.profile("Alice","").getJSONArray("activityFeed").length(),2);
            Assert.assertEquals(f.query.profile("Alice","1").getJSONArray("activityFeed").length(),7);
            f.sql("UPDATE rp SET repeaterContentAuthorId='4'");Assert.assertEquals(f.query.profile("Alice","").getJSONArray("activityFeed").length(),1);
        }
        try(var f=new Fixture()){
            f.sql("INSERT INTO up VALUES('503','1','11','110','310',5,'L1')");
            for(int i=0;i<2050;i++){
                String id="5"+String.format("%05d",i);f.sql("INSERT INTO u VALUES(?,?,?,0,?,?,0,?)",id,"U"+i,"member","U"+i,"","PRIVATE");
                f.sql("INSERT INTO up VALUES(?,?,?,?,?,?,?)","6"+id,id,"10","101","301",1,"L2");
                f.sql("INSERT INTO up VALUES(?,?,?,?,?,?,?)","7"+id,id,"11","110","310",1,"L1");
            }
            error(503,()->f.query.profile("Alice",""));
        }
        System.out.println("PASS 9 exact Java level binding under NOCASE SQL, source subject ownership, equal-length body hash binding and final authorization snapshots, moon privacy/repeater author validity, whole-request actual ranking candidate budget");
        System.out.println("Profession public isolated tests passed: 9 groups");
    }
}
