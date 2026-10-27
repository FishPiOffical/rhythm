package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.nio.file.*;
import java.util.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** Actual service/input/receipt/strict JDBC; explicit DAO/cache/censor/quota and SQLite SQL adapters. */
public class ApiV3ProfileTagsTest {
    static class Fixture extends ApiV3QqSettingsTest.Fixture {
        boolean blocked, quota, relationFaultOnce, replaceInsertedId; int checked, relationWrites, failRelation; List<String> cachePhases = new ArrayList<>();
        final ApiV3ProfileTagsService tagSettings = new ApiV3ProfileTagsService() {
            protected long now() { return NOW; }
            protected void checkQuota(String actor) { Assert.assertTrue(active()); if (quota) throw new ApiV3Exception(429,"quota","fixture"); }
            protected void validateContent(String actor, JSONObject user, String value) {
                Assert.assertTrue(active()); checked++;
                if (blocked) throw new ApiV3Exception(422,"content_rejected","fixture");
            }
        };
        Fixture() throws Exception {
            for (String column : List.of("userTags", "userNickname", "userIntro", "userURL", "userRole")) sql(db,"ALTER TABLE u ADD " + column + " TEXT DEFAULT ''");
            sql(db,"UPDATE u SET userRole='normal',userTags='' ");
            final var schema = new JSONObject(Files.readString(Path.of("src/main/resources/repository.json"))).getJSONArray("repositories");
            JSONObject definition = null;
            for (int i=0;i<schema.length();i++) if (schema.getJSONObject(i).getString("name").equals("tag")) definition=schema.getJSONObject(i);
            Assert.assertNotNull(definition); final var columns=new ArrayList<String>();
            for (Object item:definition.getJSONArray("keys")) {final var value=(JSONObject)item;final String name=value.getString("name"),type=value.getString("type");columns.add(name + (name.equals("oId")?" TEXT PRIMARY KEY":type.equals("String")?" TEXT COLLATE NOCASE":type.equals("double")||type.equals("Double")?" REAL":" INTEGER"));}
            sql(db,"CREATE TABLE tag_data("+String.join(",",columns)+")");
            sql(db,"CREATE TABLE user_tag_data(oId TEXT PRIMARY KEY,user_oId TEXT,tag_oId TEXT,type INT)");
            sql(db,"CREATE TABLE option_data(oId TEXT PRIMARY KEY,optionValue TEXT)");sql(db,"INSERT INTO option_data VALUES(?,?)",Option.ID_C_STATISTIC_TAG_COUNT,"1");
            known("10","JavaScript","javascript",0,"");
            sql(db,"INSERT INTO user_tag_data VALUES('90','2','10',2),('91','1','10',0),('92','1','10',1)");
            final var tagRepo=new TagRepository(){
                public String getName(){return "tag_data";}public boolean hasTransactionBegun(){return active();}
                public List<JSONObject> select(String q,Object...args)throws RepositoryException{return selectSQL(q.replace("CHAR_LENGTH(","LENGTH(").replace("LOCATE(LOWER(tagTitle),LOWER(?))","INSTR(LOWER(?),LOWER(tagTitle))"),args);}
                public String add(JSONObject value)throws RepositoryException{Assert.assertTrue(active());Assert.assertTrue(ArticlePublicationCaches.active());final var actual=new JSONObject(value.toString());if(replaceInsertedId)actual.put("oId",org.b3log.latke.util.Ids.genTimeMillisId());insert(writer,"tag_data",actual);ArticlePublicationCaches.afterTransaction("tag:"+actual.getString("oId"),()->{Assert.assertFalse(active());cachePhases.add("tag-after");});return actual.getString("oId");}
            };
            final var links=new UserTagRepository(){public String getName(){return "user_tag_data";}public String add(JSONObject value)throws RepositoryException{Assert.assertTrue(active());insert(writer,"user_tag_data",value);relationWrites++;if(failRelation>0&&relationWrites>=failRelation&&(!relationFaultOnce||relationWrites==failRelation))throw new RepositoryException("relation fault");return value.getString("oId");}};
            final var optionRepo=new OptionRepository(){public String getName(){return "option_data";}public boolean hasTransactionBegun(){return active();}public List<JSONObject> select(String q,Object...args)throws RepositoryException{return selectSQL(q,args);}public void update(String id,JSONObject value,String...fields)throws RepositoryException{Assert.assertTrue(ArticlePublicationCaches.active());sql(writer,"UPDATE option_data SET optionValue=? WHERE oId=?",value.getString("optionValue"),id);ArticlePublicationCaches.afterTransaction("stat",()->{Assert.assertFalse(active());cachePhases.add("stat-after");});}};
            inject(tagSettings,"users",ApiV3QqSettingsTest.field(proof,ApiV3SecurityProofService.class,"users"));
            inject(tagSettings,"requests",ApiV3QqSettingsTest.field(service,ApiV3IdentityApplicationService.class,"requests"));
            inject(tagSettings,"tags",tagRepo);inject(tagSettings,"relations",links);inject(tagSettings,"options",optionRepo);inject(endpointQq,"tags",tagSettings);
        }
        void known(String id,String title,String uri,int status,String icon)throws Exception {insert(db,"tag_data",new JSONObject().put("oId",id).put("tagTitle",title).put("tagURI",uri).put("tagStatus",status).put("tagIconPath",icon));}
        void insert(java.sql.Connection connection,String table,JSONObject value)throws RepositoryException {final var keys=new ArrayList<>(value.keySet());final var args=keys.stream().map(value::get).toArray();sql(connection,"INSERT INTO "+table+"("+String.join(",",keys)+") VALUES("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",args);}
        JSONObject save(String value,String key){return tagSettings.update("1",new JSONObject().put("tags",value),key);}
        String storedTags()throws Exception{return rows(db,"SELECT userTags FROM u WHERE oId='1'").getFirst().getString("userTags");}
        int self()throws Exception{return rows(db,"SELECT COUNT(*) n FROM user_tag_data WHERE user_oId='1' AND type=2").getFirst().getInt("n");}
        int tagCount()throws Exception{return rows(db,"SELECT COUNT(*) n FROM tag_data").getFirst().getInt("n");}
        int statistic()throws Exception{return Integer.parseInt(rows(db,"SELECT optionValue FROM option_data").getFirst().getString("optionValue"));}
        RequestContext httpTags(String method,String token,String key,String body)throws Exception {
            final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.valueOf(method),"/api/v3/me/settings/tags");
            try{if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);raw.headers().set("Content-Type","application/json");raw.headers().set("X-Real-IP","127.0.0.1");
                final var request=new Request(null,raw);request.setBytes(body==null?new byte[0]:body.getBytes(java.nio.charset.StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy")));
                final var context=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));context.addHandler(new org.b3log.latke.http.handler.RouteHandler());context.addHandler(c->{if(method.equals("GET"))endpointQq.tags(c);else endpointQq.updateTags(c);});context.handle();return context;
            }finally{raw.release();}
        }
    }
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        Assert.assertEquals(ApiV3ProfileTagInputs.tokens(new JSONObject().put("tags"," JS；js、 C#，ES ; ")),List.of("JS","C#","ES"));
        Assert.assertEquals(ApiV3ProfileTagInputs.tokens(new JSONObject().put("tags","  ")),List.of());
        for(String value:List.of("a,"+"x".repeat(13),"a,<b>","a,😀","a,/path","x".repeat(129),",;，"))error(422,()->ApiV3ProfileTagInputs.tokens(new JSONObject().put("tags",value)));
        Assert.assertEquals(Tag.normalizeAlias("js"),"JavaScript");Assert.assertEquals(Tag.normalizeAlias("Go语言"),"golang");Assert.assertEquals(Tag.normalizeAlias("ES"),"Elasticsearch");
        Assert.assertEquals(Tag.formatTags("Elasticsearch,bad?"),"Elasticsearch");
        System.out.println("PASS tags 1 strict every-item validation, separators/whitespace/case dedupe, raw bounds and shared legacy aliases/whitelist remain intact");
        try(var f=new Fixture()){
            Assert.assertEquals(f.save("JS； javaScript、ES","same").getString("tags"),"JavaScript,Elasticsearch");Assert.assertEquals(f.storedTags(),"JavaScript,Elasticsearch");Assert.assertEquals(f.self(),2);Assert.assertEquals(f.statistic(),2);Assert.assertEquals(f.tagCount(),2);Assert.assertEquals(f.count("receipt"),1);Assert.assertFalse(f.cachePhases.isEmpty());
            Assert.assertEquals(rows(f.db,"SELECT COUNT(*) n FROM user_tag_data WHERE user_oId='2' AND type=2").getFirst().getInt("n"),1);
            Assert.assertEquals(rows(f.db,"SELECT COUNT(*) n FROM user_tag_data WHERE user_oId='1' AND type IN(0,1)").getFirst().getInt("n"),3);
            Assert.assertEquals(f.stored(),"00123");f.save("B","later");Assert.assertEquals(f.save("JS； javaScript、ES","same").getString("tags"),"JavaScript,Elasticsearch");Assert.assertEquals(f.storedTags(),"B");Assert.assertEquals(f.self(),1);Assert.assertEquals(f.statistic(),3);
            error(409,()->f.save("X","same"));f.save("","clear");Assert.assertEquals(f.self(),0);Assert.assertEquals(f.tagCount(),3);Assert.assertEquals(f.statistic(),3);
        }
        System.out.println("PASS tags 2 setting/tag/creator/self/count/receipt commit atomically, preserve others/type0/type1/QQ, replay never overwrites newer tags, clear preserves global tags");
        try(var f=new Fixture()){f.replaceInsertedId=true;f.save("ProviderId","provider-id");Assert.assertEquals(rows(f.db,"SELECT COUNT(*) n FROM user_tag_data u LEFT JOIN tag_data t ON u.tag_oId=t.oId WHERE t.oId IS NULL").getFirst().getInt("n"),0);}
        try(var f=new Fixture()){
            f.known("11","Java","java",0,"icon");f.known("12","Elastic","elastic",0,"icon");
            Assert.assertEquals(f.save("JavaScript,Elasticsearch","icon").getString("tags"),"Java,Elasticsearch");
            f.known("13","SomeTag","alias",0,"");Assert.assertEquals(f.save("ALIAS","uri").getString("tags"),"SomeTag");
            f.known("14","bad","bad",1,"");error(422,()->f.save("bad","disabled"));
            f.known("15","Dup","dup",0,"");f.known("16","dup","duplicate",0,"");error(409,()->f.save("Dup","ambiguous"));
        }
        System.out.println("PASS tags 3 current valid longest icon normalization, whitelist bypass, existing title/URI case, disabled and duplicate data handling");
        for(String input:List.of("a,b,c,d,e","系统公告"))try(var f=new Fixture()){error(422,()->f.save(input,"invalid"));Assert.assertEquals(f.tagCount(),1);Assert.assertEquals(f.self(),0);Assert.assertEquals(f.count("receipt"),0);}
        try(var f=new Fixture()){sql(f.db,"UPDATE u SET userRole=? WHERE oId='1'",Role.ROLE_ID_C_ADMIN.toUpperCase(Locale.ROOT));error(422,()->f.save("系统公告","fake-admin"));sql(f.db,"UPDATE u SET userRole=? WHERE oId='1'",Role.ROLE_ID_C_ADMIN);Assert.assertEquals(f.save("系统公告","admin").getString("tags"),"系统公告");}
        for(boolean blocked:List.of(true,false))try(var f=new Fixture()){f.blocked=blocked;f.quota=!blocked;error(blocked?422:429,()->f.save("A","censor"));Assert.assertEquals(f.tagCount(),1);Assert.assertEquals(f.statistic(),1);Assert.assertEquals(f.count("receipt"),0);}
        System.out.println("PASS tags 4 >4 unique/reserved/exact-admin/censor/quota rejection before any durable mutation, no silent discard or truncation");
        for(String fault:List.of("tag","relation","stat","user","receipt","commit"))try(var f=new Fixture()){
            switch(fault){case "tag"->f.writeFault="INSERT INTO tag_data";case "relation"->f.failRelation=2;case "stat"->f.writeFault="UPDATE option_data";case "user"->f.writeFault="UPDATE `u`";default->f.fault=fault;}
            try{error(503,()->f.save("A,B","fault"));}catch(AssertionError failure){throw new AssertionError("fault="+fault+" relationWrites="+f.relationWrites+" failRelation="+f.failRelation,failure);}Assert.assertEquals(f.tagCount(),1);Assert.assertEquals(f.statistic(),1);Assert.assertEquals(f.self(),0);Assert.assertEquals(f.storedTags(),"");Assert.assertEquals(f.count("receipt"),0);
        }
        try(var f=new Fixture()){f.fault="ack";error(503,()->f.save("A","ack"));Assert.assertEquals(f.tagCount(),2);Assert.assertEquals(f.self(),1);f.fault="";Assert.assertEquals(f.save("A","ack").getString("tags"),"A");Assert.assertEquals(f.tagCount(),2);Assert.assertEquals(f.count("receipt"),1);sql(f.db,"UPDATE u SET userStatus=1 WHERE oId='1'");error(404,()->f.save("A","ack"));}
        try(var f=new Fixture()){f.failRelation=2;f.relationFaultOnce=true;Assert.assertEquals(f.save("A,B","transient").getString("tags"),"A,B");Assert.assertEquals(f.tagCount(),3);Assert.assertEquals(f.statistic(),3);Assert.assertEquals(f.self(),2);Assert.assertEquals(f.count("receipt"),1);}
        System.out.println("PASS tags 5 every persistent write/commit failure rolls back; transient retry and lost ACK complete once; inactive owner cannot replay");
        try(var f=new Fixture()){
            org.b3log.symphony.processor.ApiV3ProfileSettingsProcessor.registerRoutes(f.endpointQq,f.auth,f.writes);Dispatcher.mapping();
            Assert.assertEquals(f.httpTags("GET",null,null,null).attr("test.status"),401);Assert.assertEquals(f.httpTags("GET","scope",null,null).attr("test.status"),403);
            Assert.assertEquals(f.httpTags("PUT","owner",null,"{\"tags\":\"JS\"}").attr("test.status"),422);Assert.assertEquals(f.httpTags("PUT","owner","http","{\"tags\":\"JS\"}").attr("test.status"),200);
            final var read=f.httpTags("GET","owner",null,null);Assert.assertEquals(((JSONObject)read.attr("test.data")).getString("tags"),"JavaScript");Assert.assertEquals(read.getResponse().getHeader("Cache-Control"),"no-store");
            Assert.assertEquals(f.httpTags("PUT","owner","owner-id","{\"tags\":\"JS\",\"userId\":\"2\"}").attr("test.status"),422);
        }
        System.out.println("PASS tags 6 actual HTTP GET/PUT Bearer/scope/key/shape gates and owner metadata projection");
        for(String count:List.of("not-a-number","2147483647"))try(var f=new Fixture()){sql(f.db,"UPDATE option_data SET optionValue=?",count);error(503,()->f.save("A","bad-count"));Assert.assertEquals(f.tagCount(),1);Assert.assertEquals(f.self(),0);Assert.assertEquals(f.count("receipt"),0);}
        try(var f=new Fixture()){f.fault="read";error(503,()->f.tagSettings.read("1"));}
        try(var f=new Fixture()){for(int i=0;i<129;i++)f.known(String.valueOf(100+i),"Java","java"+i,0,"icon");error(503,()->f.save("Java","over-icons"));Assert.assertEquals(f.count("receipt"),0);Assert.assertEquals(f.self(),0);}
        System.out.println("PASS tags 7 corrupt/overflow statistics, read failures and normalization query budgets fail explicitly without partial settings");
    }
}
