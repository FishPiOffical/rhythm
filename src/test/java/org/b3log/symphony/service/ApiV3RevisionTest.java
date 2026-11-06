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
import org.b3log.symphony.processor.ApiV3RevisionProcessor;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import org.sqlite.Function;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Actual SQLite queries with MySQL SHA2/CHAR_LENGTH compatibility functions, no HTTP server. */
public class ApiV3RevisionTest {
 static void inject(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 static void sql(Connection c,String q,Object...args)throws RepositoryException{try(var p=c.prepareStatement(q)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate();}catch(SQLException e){throw new RepositoryException(e);}}
 static List<JSONObject> rows(Connection c,String q,Object...args)throws RepositoryException{try(var p=c.prepareStatement(q)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){var result=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));result.add(row);}return result;}}catch(SQLException e){throw new RepositoryException(e);}}
 static JSONObject data(String title,String content){return new JSONObject().put("articleTitle",title).put("articleContent",content);}
 static class F implements AutoCloseable {
  final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");final ApiV3RevisionQueryService query=new ApiV3RevisionQueryService();final ApiV3RevisionProcessor controller=new ApiV3RevisionProcessor();final ApiV3AuthMidware auth=new ApiV3AuthMidware();int reads,metas;boolean fail;String race="";boolean inMetaHash,inRevisionHash,hashRace;long metadataLimit,phaseHashBytes,totalMetaHashBytes,totalRevisionHashBytes;int hashBatches;
  F()throws Exception {
   Function.create(db,"CHAR_LENGTH",new Function(){protected void xFunc()throws SQLException{String s=value_text(0);if(s==null)result();else result(s.codePointCount(0,s.length()));}});
   Function.create(db,"OCTET_LENGTH",new Function(){protected void xFunc()throws SQLException{String s=value_text(0);if(s==null)result();else result(s.getBytes(StandardCharsets.UTF_8).length);}});
   Function.create(db,"SHA2",new Function(){protected void xFunc()throws SQLException{try{Assert.assertEquals(value_int(1),256);long bytes=value_text(0).getBytes(StandardCharsets.UTF_8).length;if(inMetaHash){phaseHashBytes+=bytes;totalMetaHashBytes+=bytes;Assert.assertTrue(phaseHashBytes<=metadataLimit,"metadata SHA2 must remain within verified phase byte budget");}if(inRevisionHash)totalRevisionHashBytes+=bytes;result(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value_text(0).getBytes(StandardCharsets.UTF_8))));}catch(Exception e){throw new SQLException(e);}}});
   sql(db,"CREATE TABLE u(oId TEXT PRIMARY KEY,userStatus INT,userName TEXT,userRole TEXT)");sql(db,"INSERT INTO u VALUES('1',0,'reader','readRole'),('2',0,'author','none'),('3',0,'commenter','none'),('4',4,'disabled','readRole'),('5',0,'outsider','none')");
   sql(db,"CREATE TABLE a(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleStatus INT,articleType INT,articleAnonymousView INT,articleUpdateTime INT,articleTitle TEXT,articleContent TEXT,articleRewardContent TEXT)");
   sql(db,"INSERT INTO a VALUES('10','2',0,0,0,500,'Current <title>','new body','PRIVATE_REWARD'),('11','2',0,1,0,500,'Discussion','@reader body','PRIVATE_REWARD'),('12','2',0,0,0,500,'No history','body','PRIVATE_REWARD')");
   sql(db,"CREATE TABLE c(oId TEXT PRIMARY KEY,commentAuthorId TEXT,commentOnArticleId TEXT,commentStatus INT,commentVisible INT,commentCreateTime INT,commentContent TEXT)");sql(db,"INSERT INTO c VALUES('20','3','10',0,0,600,'comment new'),('21','3','11',0,0,600,'discussion comment'),('22','3','10',0,1,600,'private new')");
   sql(db,"CREATE TABLE rev(oId TEXT PRIMARY KEY,revisionDataId TEXT,revisionDataType INT,revisionData TEXT,revisionAuthorId TEXT)");
   for(String id:List.of("100","200"))sql(db,"INSERT INTO rev VALUES(?,'10',0,?,'PRIVATE_EDITOR')",id,data("Old <x>","old\nbody<script>alert(1)</script>").put("articleRewardContent","PRIVATE_REWARD").toString());
   sql(db,"INSERT INTO rev VALUES('201','11',0,?,'PRIVATE_EDITOR')",data("Old discussion","@oldReader old").toString());
   for(String target:List.of("20","21","22"))sql(db,"INSERT INTO rev VALUES(?,?,1,?,'PRIVATE_EDITOR')","3"+target,target,new JSONObject().put("commentContent","comment old").put("commentIP","PRIVATE_IP").toString());
   sql(db,"CREATE TABLE rp(oId TEXT PRIMARY KEY,roleId TEXT COLLATE NOCASE,permissionId TEXT COLLATE NOCASE)");sql(db,"INSERT INTO rp VALUES('1','readRole','commonViewArticleHistory'),('2','readRole','commonViewCommentHistory')");
   inject(query,"users",new UserRepository(){public String getName(){return "u";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
   inject(query,"articles",new ArticleRepository(){public String getName(){return "a";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
   inject(query,"comments",new CommentRepository(){public String getName(){return "c";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
   inject(query,"revisions",new RevisionRepository(){public String getName(){return "rev";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
   inject(query,"permissions",new RolePermissionRepository(){public String getName(){return "rp";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
   inject(controller,"query",query);inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("reader","wrong","owner").contains(token)?new JSONObject().put("scope",token.equals("wrong")?"profile.read":"content.read").put("user",new JSONObject().put("oId",token.equals("owner")?"2":"1")):null;}});
  }
  List<JSONObject> selectSQL(String q,Object...a)throws RepositoryException {
   Assert.assertTrue(q.startsWith("SELECT "));Assert.assertFalse(q.contains("SELECT *"));Assert.assertFalse(q.contains("FOR UPDATE"));reads++;if(fail)throw new RepositoryException("PRIVATE_SQL");
   inMetaHash=q.contains("oId IN (");inRevisionHash=q.contains("FROM `rev`")&&q.contains("SHA2");
   final List<JSONObject> result;try{result=rows(db,q,a);}finally{inMetaHash=false;inRevisionHash=false;}
   if(q.contains("LIMIT 4097")){Assert.assertFalse(q.contains("SHA2"),"length budget must run before every historical hash");metadataLimit=0;phaseHashBytes=0;for(var row:result)metadataLimit+=row.getLong("dataBytes");}
   if(q.contains("oId IN (")){hashBatches++;Assert.assertTrue(a.length<=770);if(!hashRace&&Set.of("body-grow","middle-grow").contains(race)){hashRace=true;sql(db,race.equals("body-grow")?"UPDATE rev SET revisionData=? WHERE oId='200'":"UPDATE rev SET revisionData=? WHERE revisionDataId='10'",data("","x".repeat(2097152)).toString());}}

   if(q.contains("LIMIT 4097")&&++metas==1&&!race.isEmpty()&&!Set.of("body-grow","middle-grow").contains(race)){
    switch(race){case "metadata-grow"->sql(db,"UPDATE rev SET revisionData=? WHERE revisionDataId='10'",data("","x".repeat(2097152)).toString());case "grant"->sql(db,"DELETE FROM rp");case "actor"->sql(db,"UPDATE u SET userStatus=4 WHERE oId='1'");case "owner"->sql(db,"UPDATE a SET articleAuthorId='3' WHERE oId='10'");case "discussion"->sql(db,"UPDATE a SET articleType=1,articleContent='@outsider body' WHERE oId='10'");case "comment"->sql(db,"UPDATE c SET commentVisible=1 WHERE oId='20'");case "revision"->sql(db,"UPDATE rev SET revisionData=? WHERE oId='100'",data("new","mutated").toString());default->throw new AssertionError();}
   }return result;
  }
  RequestContext http(String token,String path,Map<String,String> params)throws Exception {
   var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,path);try{raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);var req=new Request(null,raw);req.setParams(new HashMap<>(params));var ctx=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));ctx.attr("user",new JSONObject().put("oId","2"));ctx.addHandler(new org.b3log.latke.http.handler.RouteHandler());ctx.addHandler(c->{var resolution=(org.b3log.latke.http.handler.RouteResolution)c.attr("MATCH_RESULT");switch(resolution.getContextHandlerMeta().getInvokeHolder().getName()){case "articles"->controller.articles(c);case "article"->controller.article(c);case "comments"->controller.comments(c);case "comment"->controller.comment(c);default->throw new AssertionError();}});ctx.handle();return ctx;}finally{raw.release();}
  }
  public void close()throws Exception{db.close();}
 }
 interface Action{void run()throws Exception;}static void error(int status,Action action)throws Exception{try{action.run();Assert.fail("Expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertFalse(e.getMessage().contains("PRIVATE"));}}
 static JSONArray items(JSONObject o){return o.getJSONArray("items");}
 public static void main(String[]args)throws Exception {
  var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");props.setProperty("servePath","http://localhost:8080");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
  try(var f=new F()){
   var list=f.query.list("1",0,"10",1,2);Assert.assertEquals(items(list).length(),2);Assert.assertEquals(list.getJSONObject("pagination").getInt("total"),3);Assert.assertEquals(items(list).getJSONObject(0).keySet(),Set.of("id","time","index","current"));Assert.assertEquals(items(f.query.list("1",0,"10",2,2)).getJSONObject(0).getString("id"),"current");Assert.assertEquals(items(f.query.list("1",0,"10",3,2)).length(),0);
   var detail=f.query.detail("1",0,"10","100");Assert.assertEquals(detail.keySet(),Set.of("id","time","index","current","title","content"));Assert.assertFalse(detail.getString("title").contains("<"));Assert.assertTrue(detail.getString("content").contains("\n"));Assert.assertFalse(detail.getString("content").contains("<script"));Assert.assertFalse(detail.toString().contains("PRIVATE"));Assert.assertEquals(f.query.detail("1",0,"10","current").getString("content"),"new body");
   Assert.assertEquals(f.query.detail("1",1,"20","320").getString("content"),"comment old");Assert.assertTrue(f.query.detail("1",1,"20","current").isNull("time"));Assert.assertEquals(f.query.detail("1",1,"20","current").getInt("index"),2);
  }System.out.println("PASS revision 1 real article/comment SQL, ascending paginated metadata, matching virtual current, exact whitelist and production title/HTML cleaner with newline preservation");
  try(var f=new F()){
   Assert.assertEquals(items(f.query.list("2",0,"10",1,20)).length(),3);Assert.assertEquals(items(f.query.list("3",1,"20",1,20)).length(),2);
   error(403,()->f.query.list("5",0,"10",1,20));error(403,()->f.query.list("5",1,"20",1,20));
   sql(f.db,"UPDATE rp SET roleId='READROLE'");error(403,()->f.query.list("1",0,"10",1,20));sql(f.db,"UPDATE rp SET roleId='readRole',permissionId=UPPER(permissionId)");error(403,()->f.query.list("1",1,"20",1,20));
   sql(f.db,"DELETE FROM rp");Assert.assertEquals(items(f.query.list("2",0,"10",1,20)).length(),3);Assert.assertEquals(items(f.query.list("3",1,"20",1,20)).length(),2);
  }System.out.println("PASS revision 2 actual current role grants exact Java case semantics despite CI SQL, author exemption, separate article/comment historical permissions");
  try(var f=new F()){
   f.query.detail("1",0,"11","201");f.query.detail("1",1,"21","321");sql(f.db,"UPDATE a SET articleContent='@outsider body' WHERE oId='11'");error(404,()->f.query.detail("1",0,"11","201"));error(404,()->f.query.detail("1",1,"21","321"));
   error(404,()->f.query.detail("1",1,"22","322"));f.query.detail("3",1,"22","322");sql(f.db,"INSERT INTO rp VALUES('3','none','commonViewCommentHistory')");f.query.detail("2",1,"22","322");
   sql(f.db,"UPDATE u SET userRole='adminRole' WHERE oId='1'");sql(f.db,"INSERT INTO rp VALUES('4','adminRole','commonViewCommentHistory')");error(404,()->f.query.detail("1",1,"22","322"));
   sql(f.db,"UPDATE u SET userStatus=4 WHERE oId='2'");error(404,()->f.query.detail("3",1,"20","320"));
  }System.out.println("PASS revision 3 current discussion invitation and parent readability, private comment only either author, no admin privacy override, current parent author validity");
  try(var f=new F()){
   Assert.assertEquals(items(f.query.list("1",0,"12",1,20)).length(),0);error(404,()->f.query.detail("1",0,"12","current"));
   sql(f.db,"UPDATE a SET articleTitle='Old <x>',articleContent=? WHERE oId='10'"," old\n body<script>alert(1)</script> ");Assert.assertEquals(items(f.query.list("1",0,"10",1,20)).length(),2);error(404,()->f.query.detail("1",0,"10","current"));
   sql(f.db,"UPDATE c SET commentContent=' comment  old ' WHERE oId='20'");Assert.assertEquals(items(f.query.list("1",1,"20",1,20)).length(),1);error(404,()->f.query.detail("1",1,"20","current"));
   error(404,()->f.query.detail("1",0,"11","100"));error(404,()->f.query.detail("1",1,"20","100"));error(404,()->f.query.detail("1",0,"10","999"));
   for(var id:Arrays.asList(null,"0","1 OR 1=1","1".repeat(20)))error(422,()->f.query.list("1",0,id,1,20));error(422,()->f.query.list("1",0,"10",0,20));error(422,()->f.query.list("1",0,"10",1,51));
  }System.out.println("PASS revision 4 no history/current fabrication, raw whitespace-only equality for either type, target/type-bound revisions and invalid ID/page/size rejection");
  try(var f=new F()){
   sql(f.db,"UPDATE rev SET revisionData=? WHERE oId='200'","x".repeat(1048577));error(503,()->f.query.list("1",0,"10",1,20));
   sql(f.db,"UPDATE rev SET revisionData='not json' WHERE oId='200'");error(503,()->f.query.list("1",0,"10",1,20));
   sql(f.db,"UPDATE rev SET revisionData=? WHERE oId='200'",data("Old <x>","old").put("articleContent",7).toString());error(503,()->f.query.list("1",0,"10",1,20));
  }
  try(var f=new F()){
   for(int i=0;i<4096;i++)sql(f.db,"INSERT INTO rev VALUES(?,'10',0,'{}','PRIVATE')",String.valueOf(1000+i));error(503,()->f.query.list("1",0,"10",1,20));
  }
  try(var f=new F()){
   final String data=data("Old","x".repeat(990000)).toString();for(int i=0;i<9;i++)sql(f.db,"INSERT INTO rev VALUES(?,'10',0,?,'PRIVATE')",String.valueOf(1000+i),data);error(503,()->f.query.list("1",0,"10",1,20));
  }
  try(var f=new F()){
   sql(f.db,"UPDATE a SET articleContent=? WHERE oId='10'","x".repeat(1048577));error(503,()->f.query.list("1",0,"10",1,20));f.fail=true;error(503,()->f.query.list("1",0,"10",1,20));
  }System.out.println("PASS revision 5 bounded metadata/body/count/format budgets reject whole request without truncating, typed stored JSON and sanitized driver errors");
  for(JSONObject broken:List.of(new JSONObject(),new JSONObject().put("articleTitle","missing body"),new JSONObject().put("articleContent","missing title"),data("title","body").put("articleContent",JSONObject.NULL)))try(var f=new F()){
   sql(f.db,"UPDATE rev SET revisionData=? WHERE oId='200'",broken.toString());error(503,()->f.query.list("1",0,"10",1,20));
  }
  try(var f=new F()){
   sql(f.db,"UPDATE rev SET revisionData='{}' WHERE oId='320'");error(503,()->f.query.detail("1",1,"20","320"));
   sql(f.db,"UPDATE a SET articleContent=NULL WHERE oId='10'");error(503,()->f.query.list("1",0,"10",1,20));
  }
  for(String glyph:List.of("中","😀"))try(var f=new F()){
   int overhead=data("","").toString().getBytes(StandardCharsets.UTF_8).length;
   int glyphBytes=glyph.getBytes(StandardCharsets.UTF_8).length;
   int count=(1048576-overhead)/glyphBytes;
   String content=glyph.repeat(count)+"x".repeat((1048576-overhead)%glyphBytes);
   final String exact=data("",content).toString();Assert.assertEquals(exact.getBytes(StandardCharsets.UTF_8).length,1048576);
   sql(f.db,"UPDATE rev SET revisionData=? WHERE oId='200'",exact);Assert.assertEquals(items(f.query.list("1",0,"10",1,20)).length(),3);Assert.assertFalse(f.query.detail("1",0,"10","200").getString("content").isEmpty());
   sql(f.db,"UPDATE rev SET revisionData=? WHERE oId='200'",data("",content+glyph).toString());error(503,()->f.query.list("1",0,"10",1,20));
   sql(f.db,"UPDATE rev SET revisionData=? WHERE oId='200'",data("","old").toString());sql(f.db,"UPDATE a SET articleContent=? WHERE oId='10'",glyph.repeat(1048576/glyphBytes+1));error(503,()->f.query.list("1",0,"10",1,20));
  }
  try(var f=new F()){
   final String multibyte=data("","中".repeat(300000)).toString();for(int i=0;i<10;i++)sql(f.db,"INSERT INTO rev VALUES(?,'10',0,?,'PRIVATE')",String.valueOf(1000+i),multibyte);error(503,()->f.query.list("1",0,"10",1,20));
  }
  try(var f=new F()){
   sql(f.db,"UPDATE c SET commentContent=? WHERE oId='20'","😀".repeat(262144));Assert.assertFalse(f.query.detail("1",1,"20","current").getString("content").isEmpty());
   sql(f.db,"UPDATE c SET commentContent=commentContent||'x' WHERE oId='20'");error(503,()->f.query.list("1",1,"20",1,20));
  }
  System.out.println("PASS revision 8 required stored title/content and current typed fields, Chinese/emoji exact UTF8 byte boundary and overflow, aggregate bytes despite lower character counts");
  try(var f=new F()){
   final String big=data("","中".repeat(300000)).toString();for(int i=0;i<10;i++)sql(f.db,"INSERT INTO rev VALUES(?,'10',0,?,'PRIVATE')",String.valueOf(1000+i),big);error(503,()->f.query.list("1",0,"10",1,20));Assert.assertEquals(f.totalRevisionHashBytes,0L);Assert.assertEquals(f.hashBatches,0);
  }
  try(var f=new F()){
   for(int i=0;i<300;i++)sql(f.db,"INSERT INTO rev VALUES(?,'10',0,?,'PRIVATE')",String.valueOf(1000+i),data("title","中😀content").toString());
   long expected=rows(f.db,"SELECT SUM(OCTET_LENGTH(revisionData)) AS n FROM rev WHERE revisionDataId='10'").getFirst().getLong("n");
   f.query.list("1",0,"10",1,20);Assert.assertEquals(f.hashBatches,6);Assert.assertEquals(f.totalMetaHashBytes,expected*2);Assert.assertTrue(f.phaseHashBytes<=8388608L);
  }
  try(var f=new F()){
   f.race="metadata-grow";error(503,()->f.query.list("1",0,"10",1,20));Assert.assertEquals(f.totalMetaHashBytes,0L);Assert.assertEquals(f.totalRevisionHashBytes,0L);
  }
  try(var f=new F()){
   f.race="body-grow";error(503,()->f.query.list("1",0,"10",1,20));Assert.assertTrue(f.totalMetaHashBytes>0);Assert.assertEquals(f.totalRevisionHashBytes,f.totalMetaHashBytes);
  }
  try(var f=new F()){
   for(int i=0;i<300;i++)sql(f.db,"INSERT INTO rev VALUES(?,'10',0,?,'PRIVATE')",String.valueOf(1000+i),data("title","initial").toString());
   f.race="middle-grow";error(503,()->f.query.list("1",0,"10",1,20));Assert.assertEquals(f.hashBatches,2);Assert.assertTrue(f.totalMetaHashBytes>0);Assert.assertTrue(f.totalMetaHashBytes<=f.metadataLimit);
  }
  System.out.println("PASS revision 9 length-only preflight before every SHA2, bounded 128-row batches, actual measured hash bytes <= verified 8MiB, over-budget zero hash and between-phase/body/batch growth never hashes enlarged data");
  for(String race:List.of("grant","actor","owner","discussion","revision"))try(var f=new F()){f.race=race;error(race.equals("grant")?403:race.equals("actor")||race.equals("discussion")?404:503,()->f.query.detail("1",0,"10","100"));}
  try(var f=new F()){f.race="comment";error(404,()->f.query.detail("1",1,"20","320"));}
  System.out.println("PASS revision 6 authorization and full bounded data SHA2 snapshots rechecked after cleaning: grant/status/author/invitation/privacy/revision changes return no content");
  try(var f=new F()){
   ApiV3RevisionProcessor.registerRoutes(f.controller,f.auth);Dispatcher.mapping();for(String token:Arrays.asList(null,"invalid","wrong")){int reads=f.reads;Assert.assertEquals(f.http(token,"/api/v3/articles/10/revisions?apiKey=legacy",Map.of("apiKey","legacy")).attr("test.status"),"wrong".equals(token)?403:401);Assert.assertEquals(f.reads,reads);}
   for(String path:List.of("/api/v3/articles/10/revisions","/api/v3/articles/10/revisions/100","/api/v3/comments/20/revisions","/api/v3/comments/20/revisions/320")){var response=f.http("reader",path,Map.of());Assert.assertEquals(response.attr("test.status"),200);Assert.assertEquals(response.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(response.getResponse().getHeader("Vary"),"Authorization");}
   Assert.assertEquals(f.http("reader","/api/v3/articles/10/revisions?size=51",Map.of("size","51")).attr("test.status"),422);
  }System.out.println("PASS revision 7 all four real Dispatcher/controller routes content.read-only, legacy identity rejected and no-store/current page parsing");
 }
}
