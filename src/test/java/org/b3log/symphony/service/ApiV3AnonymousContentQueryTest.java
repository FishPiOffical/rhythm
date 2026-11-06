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
import org.b3log.symphony.processor.ApiV3AnonymousContentProcessor;
import org.b3log.symphony.processor.AnonymousContentRouteBindings;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3AnonymousContentDto;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;

/** Actual SQL/route/auth/controller/DTO on SQLite; repository connection and token/envelope adapters. */
public final class ApiV3AnonymousContentQueryTest {
    static void inject(final Object target, final String field, final Object value) throws Exception {
        var property=target.getClass().getDeclaredField(field);property.setAccessible(true);property.set(target,value);
    }
    static final class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3AnonymousContentQueryService query=new ApiV3AnonymousContentQueryService();
        final ApiV3AnonymousContentProcessor controller=new ApiV3AnonymousContentProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        String profileChangeSql="UPDATE x_user SET userName='changed-name',userRole='adminRole' WHERE oId='1'";
        int reads, bodyReads, failAt=-1, deactivateAt=-1, moveAt=-1, contentChangeAt=-1, profileChangeAt=-1;
        Fixture() throws Exception {
            org.sqlite.Function.create(db,"SHA2",new org.sqlite.Function(){protected void xFunc()throws SQLException{
                try{result(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value_text(0).getBytes(java.nio.charset.StandardCharsets.UTF_8))));}catch(Exception error){throw new SQLException(error);}
            }});
            org.sqlite.Function.create(db,"OCTET_LENGTH",new org.sqlite.Function(){protected void xFunc()throws SQLException{result(value_text(0).getBytes(java.nio.charset.StandardCharsets.UTF_8).length);}});
            sql("CREATE TABLE x_user(oId TEXT PRIMARY KEY,userStatus INT,userPassword TEXT,userName TEXT COLLATE NOCASE,userRole TEXT COLLATE NOCASE)");
            sql("CREATE TABLE x_article(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleAnonymous INT,articleStatus INT,articleTitle TEXT,articleType INT,articleCreateTime BIGINT,articleUpdateTime BIGINT,articleContent TEXT,articleRewardContent TEXT,articleIP TEXT,articleUA TEXT)");
            sql("CREATE TABLE x_comment(oId TEXT PRIMARY KEY,commentAuthorId TEXT,commentOnArticleId TEXT,commentAnonymous INT,commentStatus INT,commentCreateTime BIGINT,commentVisible INT,commentContent TEXT,commentIP TEXT,commentUA TEXT)");
            sql("INSERT INTO x_user VALUES('1',0,'PRIVATE','alice','defaultRole'),('2',0,'OTHER_PRIVATE','bob','defaultRole'),('3',4,'DISABLED','disabled','defaultRole'),('4',3,'BLOCKED_LOGIN','blocked','defaultRole'),('5',0,'EMPTY','empty','defaultRole')");
            article("10","1",1,0,"First",0,100);
            article("11","1",1,2,"Locked discussion",1,200);
            article("12","1",0,0,"PUBLIC_EXCLUDED",0,300);
            article("13","1",1,1,"INVALID_EXCLUDED",0,400);
            article("14","2",1,0,"OTHER_EXCLUDED",0,500);
            article("15","99",1,0,"DELETED_OWNER",0,600);
            article("20","1",1,0,"Tie",6,100);
            inject(query,"users",new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String text,Object...args)throws RepositoryException{return selectSQL(text,args);}});
            inject(query,"articles",new ArticleRepository(){public String getName(){return "x_article";}public List<JSONObject> select(String text,Object...args)throws RepositoryException{return selectSQL(text,args);}});
            inject(query,"comments",new CommentRepository(){public String getName(){return "x_comment";}public List<JSONObject> select(String text,Object...args)throws RepositoryException{return selectSQL(text,args);}});
            inject(controller,"query",query);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){
                if(!Set.of("reader","other","profile","writer").contains(token))return null;
                return new JSONObject().put("scope",token.equals("profile")?"profile.read":token.equals("writer")?"content.write":"content.read")
                        .put("user",new JSONObject().put("oId",token.equals("other")?"2":"1").put("userStatus",0));
            }});
            AnonymousContentRouteBindings.register(controller,auth);Dispatcher.mapping();
        }
        void article(String id,String owner,int anonymous,int status,String title,int type,long time)throws Exception {
            sql("INSERT INTO x_article VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",id,owner,anonymous,status,title,type,time,time+10,"PRIVATE_BODY","PRIVATE_REWARD","PRIVATE_IP","PRIVATE_UA");
        }
        void sql(String text,Object...args)throws SQLException {
            try(var statement=db.prepareStatement(text)){for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);statement.executeUpdate();}
        }
        List<JSONObject> selectSQL(String text,Object...args)throws RepositoryException {
            reads++;
            if(reads==failAt)throw new RepositoryException("PRIVATE_DRIVER_FAILURE");
            try {
                if(reads==deactivateAt)sql("UPDATE x_user SET userStatus=4 WHERE oId='1'");
                if(reads==moveAt)sql("UPDATE x_article SET articleAuthorId='2' WHERE oId='11'");
                Assert.assertFalse(text.contains("SELECT *"));
                Assert.assertFalse(text.contains("articleRewardContent")||text.contains("articleIP")||text.contains("articleUA")||text.contains("userPassword")||text.contains("commentContent")||text.contains("commentIP")||text.contains("commentUA"));
                if(reads==contentChangeAt)sql("UPDATE x_article SET articleContent='Invitation removed' WHERE oId='100'");
                if(reads==profileChangeAt)sql(profileChangeSql);
                if(text.startsWith("SELECT a.oId,a.articleContent"))bodyReads++;
                try(var statement=db.prepareStatement(text)){
                    for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);
                    try(var result=statement.executeQuery()){
                        var list=new ArrayList<JSONObject>();while(result.next()){
                            var row=new JSONObject();for(int i=1;i<=result.getMetaData().getColumnCount();i++)row.put(result.getMetaData().getColumnLabel(i),result.getObject(i));list.add(row);
                        }return list;
                    }
                }
            }catch(SQLException error){throw new RepositoryException(error);}
        }
        void comment(String id,String owner,String target,int anonymous,int status,int visible)throws Exception {
            sql("INSERT INTO x_comment VALUES(?,?,?,?,?,?,?,?,?,?)",id,owner,target,anonymous,status,Long.parseLong(id),visible,"PRIVATE_COMMENT","PRIVATE_IP","PRIVATE_UA");
        }
        void commentData()throws Exception{
            for(int id=100;id<=106;id++)article(String.valueOf(id),id==105?"3":"2",0,id==104?1:0,"Discussion "+id,1,id);
            sql("UPDATE x_article SET articleContent='Hello @alice ' WHERE oId='100'");
            sql("UPDATE x_article SET articleContent='Hello @bad@alice end' WHERE oId='101'");
            sql("UPDATE x_article SET articleContent='Hello @alice<em>' WHERE oId='102'");
            sql("UPDATE x_article SET articleContent='Hello @someone' WHERE oId='103'");
            sql("UPDATE x_article SET articleContent=? WHERE oId='106'","@bob\n@alice");
            String[] targets={"100","101","102","103","14","11","104","105","999","14","14","14","106"};
            for(int i=0;i<targets.length;i++)comment(String.valueOf(201+i),i==11?"2":"1",targets[i],i==9?0:1,i==10?1:0,i==2?1:0);
        }
        RequestContext http(String token,Map<String,String> params)throws Exception{return http(token,params,false);}
        RequestContext http(String token,Map<String,String> params,boolean comments)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/me/anonymous-"+(comments?"comments":"articles")+"?userId=2&apiKey=legacy");
            try {
                raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw);var queryParams=new HashMap<>(Map.of("userId","2","apiKey","legacy"));queryParams.putAll(params);request.setParams(queryParams);
                var context=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));
                context.attr("user",new JSONObject().put("oId","2"));
                context.addHandler(new org.b3log.latke.http.handler.RouteHandler());
                context.addHandler(c->{if(comments)controller.comments(c);else controller.articles(c);});context.handle();return context;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();}
    }
    interface Action {void run()throws Exception;}
    static void error(int status,Action action)throws Exception{try{action.run();Assert.fail("Expected "+status);}catch(ApiV3Exception error){Assert.assertEquals(error.status,status);}}
    static JSONArray list(JSONObject data){return data.getJSONArray("list");}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            var data=f.query.articles("1",1,20);Assert.assertEquals(data.getJSONObject("pagination").getLong("total"),3L);
            Assert.assertEquals(list(data).getJSONObject(0).getString("id"),"11");Assert.assertEquals(list(data).getJSONObject(1).getString("id"),"20");
            Assert.assertEquals(list(data).getJSONObject(2).getString("id"),"10");
            Assert.assertEquals(list(data).getJSONObject(0).keySet(),Set.of("id","title","type","createdAt","updatedAt"));
            Assert.assertFalse(data.toString().contains("PRIVATE")||data.toString().contains("Author"));
            Assert.assertEquals(list(f.query.articles("1",2,1)).getJSONObject(0).getString("id"),"20");Assert.assertTrue(list(f.query.articles("1",4,1)).isEmpty());
            Assert.assertEquals(list(f.query.articles("2",1,20)).getJSONObject(0).getString("id"),"14");Assert.assertTrue(list(f.query.articles("5",1,20)).isEmpty());
            error(404,()->f.query.articles("3",1,20));error(404,()->f.query.articles("4",1,20));error(404,()->f.query.articles("99",1,20));
            for(String actor:Arrays.asList(null,"","01","1' OR 1=1","1".repeat(33)))error(422,()->f.query.articles(actor,1,20));
            error(422,()->f.query.articles("1",0,20));error(422,()->f.query.articles("1",1,51));error(422,()->f.query.articles("1",Integer.MAX_VALUE,20));
        }
        System.out.println("PASS owner/anonymous/noninvalid and current active account filters precede count/page; owned locked/discussion/long metadata, stable tie order, honest empty pages and exact DTO whitelist");
        try(var f=new Fixture()){
            for(int i=0;i<52;i++)f.article("30"+i,"1",1,0,"Extra",0,1000+i);
            var data=f.query.articles("1",1,50);Assert.assertEquals(list(data).length(),50);Assert.assertEquals(data.getJSONObject("pagination").getLong("total"),55L);
            var second=f.query.articles("1",2,50);Assert.assertEquals(list(second).length(),5);Assert.assertEquals(second.getJSONObject("pagination").getLong("pageCount"),2L);
            var ids=new HashSet<String>();for(Object item:list(data))ids.add(((JSONObject)item).getString("id"));for(Object item:list(second))Assert.assertTrue(ids.add(((JSONObject)item).getString("id")));
        }
        System.out.println("PASS real data/count pagination beyond fifty, stable independent pages without post-LIMIT permission filtering or duplicate identities");
        try(var f=new Fixture()){
            for(String token:Arrays.asList(null,"invalid","profile","writer")){int before=f.reads;var context=f.http(token,Map.of());Assert.assertEquals(context.attr("test.status"),token==null||token.equals("invalid")?401:403);Assert.assertEquals(f.reads,before);}
            var context=f.http("reader",Map.of());Assert.assertEquals(context.attr("test.status"),200);Assert.assertEquals(context.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(context.getResponse().getHeader("Vary"),"Authorization");
            Assert.assertEquals(((JSONObject)context.attr("test.data")).getJSONObject("pagination").getLong("total"),3L);
            Assert.assertEquals(((JSONObject)f.http("other",Map.of()).attr("test.data")).getJSONObject("pagination").getLong("total"),1L);
            Assert.assertEquals(f.http("reader",Map.of("size","51")).attr("test.status"),422);Assert.assertEquals(f.http("reader",Map.of("p","0")).attr("test.status"),422);
            f.failAt=f.reads+2;var failed=f.http("reader",Map.of());Assert.assertEquals(failed.attr("test.status"),503);Assert.assertNull(failed.attr("test.data"));Assert.assertEquals(failed.attr("test.code"),"temporarily_unavailable");
        }
        System.out.println("PASS actual Dispatcher/content.read auth/controller: forged Cookie/query apiKey/userId cannot replace Bearer owner, wrong scopes fail before SQL, private headers and sanitized failures");
        for(int stage=1;stage<=4;stage++)try(var f=new Fixture()){
            f.failAt=stage;error(503,()->f.query.articles("1",1,20));
        }
        try(var f=new Fixture()){
            f.deactivateAt=4;error(404,()->f.query.articles("1",1,20));
        }
        try(var f=new Fixture()){
            f.moveAt=3;Assert.assertFalse(f.query.articles("1",1,20).toString().contains("Locked discussion"));
        }
        System.out.println("PASS all owner/count/page/final-owner DB faults fail closed; account deactivation before response rejects and page repeats current ownership after movement");
        try(var f=new Fixture()){
            f.sql("UPDATE x_article SET articleTitle=? WHERE oId='11'","<script>danger()</script>&lt;hello&gt;<b>World</b>");
            var title=list(f.query.articles("1",1,20)).getJSONObject(0).getString("title");Assert.assertFalse(title.contains("script")||title.contains("danger")||title.contains("<b>"));Assert.assertTrue(title.contains("World"));
            f.sql("UPDATE x_article SET articleTitle=? WHERE oId='11'","x".repeat(256));error(503,()->f.query.articles("1",1,20));
            error(503,()->ApiV3AnonymousContentDto.article(new JSONObject().put("oId","11").put("articleTitle",String.valueOf((char)0xD800)).put("articleType",0).put("articleCreateTime",1).put("articleUpdateTime",1)));
            f.sql("UPDATE x_article SET articleTitle='valid',articleCreateTime=-1 WHERE oId='11'");error(503,()->f.query.articles("1",1,20));
        }
        System.out.println("PASS hostile stored titles become bounded plain text, corrupt length/Unicode/timestamps fail closed, content/reward/audit/author columns never queried or returned");
        try(var f=new Fixture()){
            f.commentData();var result=f.query.comments("1",1,20);Assert.assertEquals(result.getJSONObject("pagination").getLong("total"),5L);
            var ids=new ArrayList<String>();for(Object item:list(result))ids.add(((JSONObject)item).getString("id"));Assert.assertEquals(ids,List.of("213","206","205","203","201"));
            Assert.assertEquals(list(result).getJSONObject(0).keySet(),Set.of("id","articleId","articleTitle","createdAt","visibility"));
            Assert.assertFalse(result.toString().contains("PRIVATE")||result.toString().contains("Author"));
            Assert.assertEquals(list(f.query.comments("1",2,2)).getJSONObject(0).getString("id"),"205");Assert.assertTrue(list(f.query.comments("1",4,2)).isEmpty());
            Assert.assertEquals(f.query.comments("2",1,20).getJSONObject("pagination").getLong("total"),1L);
            for(int state:List.of(1,4)){f.sql("UPDATE x_user SET userStatus=? WHERE oId='2'",state);Assert.assertEquals(f.query.comments("1",1,20).getJSONObject("pagination").getLong("total"),1L);}
            for(int state:List.of(2,3)){f.sql("UPDATE x_user SET userStatus=? WHERE oId='2'",state);Assert.assertEquals(f.query.comments("1",1,20).getJSONObject("pagination").getLong("total"),5L);}
            f.sql("UPDATE x_user SET userStatus=0 WHERE oId='2'");
            f.sql("UPDATE x_user SET userRole='adminRole' WHERE oId='1'");int bodies=f.bodyReads;
            Assert.assertEquals(f.query.comments("1",1,20).getJSONObject("pagination").getLong("total"),7L);Assert.assertEquals(f.bodyReads,bodies);
        }
        System.out.println("PASS anonymous comment owner/parent/author checks and exact shared mention parsing precede pagination; nested @ spoof denied, space/HTML/newline/tail invites accepted, self-only metadata allowed to owner, admin skips invitation scan");
        for(int changed:List.of(3,4,5))try(var f=new Fixture()){
            f.commentData();f.contentChangeAt=changed;var result=f.query.comments("1",1,20);
            Assert.assertFalse(result.toString().contains("Discussion 100"));if(changed<5)Assert.assertEquals(result.getJSONObject("pagination").getLong("total"),4L);
        }
        for(String change:List.of("UPDATE x_user SET userName='Alice' WHERE oId='1'","UPDATE x_user SET userName='other' WHERE oId='1'","UPDATE x_user SET userRole='adminRole' WHERE oId='1'","UPDATE x_user SET userRole='DEFAULTROLE' WHERE oId='1'"))for(int changed:List.of(3,4,5,6))try(var f=new Fixture()){
            f.commentData();f.profileChangeSql=change;f.profileChangeAt=changed;error(503,()->f.query.comments("1",1,20));
        }
        try(var f=new Fixture()){
            f.commentData();f.sql("UPDATE x_user SET userRole='ADMINROLE' WHERE oId='1'");Assert.assertEquals(f.query.comments("1",1,20).getJSONObject("pagination").getLong("total"),5L);
        }
        System.out.println("PASS database body hashes prevent stale invitation permission between headers/body/count/page, and actor name/role changes fail closed instead of using a stale identity snapshot");
        try(var f=new Fixture()){
            f.commentData();for(int i=0;i<513;i++){String id=String.valueOf(1000+i);f.article(id,"2",0,0,"Foreign discussion",1,1000+i);f.comment(String.valueOf(3000+i),"1",id,1,0,0);}
            error(503,()->f.query.comments("1",1,20));Assert.assertEquals(f.bodyReads,0);
            f.sql("UPDATE x_user SET userRole='adminRole' WHERE oId='1'");Assert.assertEquals(f.query.comments("1",1,20).getJSONObject("pagination").getLong("total"),520L);Assert.assertEquals(f.bodyReads,0);
        }
        try(var f=new Fixture()){
            f.commentData();for(int i=0;i<507;i++){String id=String.valueOf(1000+i);f.article(id,"2",0,0,"Foreign discussion",1,1000+i);f.comment(String.valueOf(3000+i),"1",id,1,0,0);}
            Assert.assertEquals(f.query.comments("1",1,20).getJSONObject("pagination").getLong("total"),5L);int before=f.bodyReads;
            f.article("1800","2",0,0,"One over candidate budget",1,1800);f.comment("3800","1","1800",1,0,0);error(503,()->f.query.comments("1",1,20));Assert.assertEquals(f.bodyReads,before);
        }
        try(var f=new Fixture()){
            f.commentData();f.sql("UPDATE x_article SET articleContent=? WHERE oId='100'","x".repeat(2*1024*1024+1));error(503,()->f.query.comments("1",1,20));Assert.assertEquals(f.bodyReads,0);
        }
        try(var f=new Fixture()){
            f.commentData();for(int i=0;i<513;i++){String id=String.valueOf(1000+i);f.article(id,"1",1,0,"Self discussion",1,1000+i);f.comment(String.valueOf(3000+i),"1",id,1,0,0);}
            Assert.assertEquals(f.query.comments("1",1,50).getJSONObject("pagination").getLong("total"),518L);Assert.assertEquals(list(f.query.comments("1",2,50)).length(),50);
        }
        System.out.println("PASS foreign-discussion target/body work budgets return explicit503 before loading bodies, never truncated total; owner/admin bypass candidate budget and final SQL pagination scales beyond fifty");
        for(int stage=1;stage<=6;stage++)try(var f=new Fixture()){
            f.commentData();f.failAt=stage;error(503,()->f.query.comments("1",1,20));
        }
        try(var f=new Fixture()){
            f.commentData();f.deactivateAt=6;error(404,()->f.query.comments("1",1,20));
        }
        try(var f=new Fixture()){
            f.commentData();for(String token:Arrays.asList(null,"invalid","profile","writer")){int before=f.reads;Assert.assertEquals(f.http(token,Map.of(),true).attr("test.status"),token==null||token.equals("invalid")?401:403);Assert.assertEquals(f.reads,before);}
            var result=f.http("reader",Map.of("size","2","p","2"),true);Assert.assertEquals(result.attr("test.status"),200);Assert.assertEquals(((JSONObject)result.attr("test.data")).getJSONArray("list").length(),2);Assert.assertEquals(result.getResponse().getHeader("Cache-Control"),"no-store");
            Assert.assertEquals(((JSONObject)f.http("other",Map.of(),true).attr("test.data")).getJSONObject("pagination").getLong("total"),1L);Assert.assertEquals(f.http("reader",Map.of("size","51"),true).attr("test.status"),422);
        }
        System.out.println("PASS all six comment query stages fail closed, final owner deactivation rejects, actual second route requires content.read and ignores forged legacy/target identity");
        var article=new JSONObject().put("oId","1").put("articleTitle","Title").put("articleType",0).put("articleCreateTime",1).put("articleUpdateTime",2);
        for(Object value:List.of(7,-1,0.5,"0"))error(503,()->ApiV3AnonymousContentDto.article(new JSONObject(article.toString()).put("articleType",value)));
        Assert.assertEquals(ApiV3AnonymousContentDto.article(new JSONObject(article.toString()).put("articleType",4)).getInt("type"),4);
        var comment=new JSONObject().put("oId","1").put("articleId","2").put("articleTitle","Title").put("commentCreateTime",1).put("commentVisible",0);
        for(Object value:List.of(2,-1,0.5,"0"))error(503,()->ApiV3AnonymousContentDto.comment(new JSONObject(comment.toString()).put("commentVisible",value)));
        Assert.assertEquals(ApiV3AnonymousContentDto.comment(new JSONObject(comment.toString()).put("commentVisible",1)).getInt("visibility"),1);
        System.out.println("PASS strict metadata numeric/enumeration projection rejects fractions/coercion/out-of-range values, preserving legacy article type4 and visibility0/1");


    }
}
