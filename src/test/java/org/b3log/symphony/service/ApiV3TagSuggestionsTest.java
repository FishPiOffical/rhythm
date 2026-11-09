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
import org.b3log.symphony.processor.ApiV3TagSuggestionProcessor;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;
public class ApiV3TagSuggestionsTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static void inject(Object o,String name,Object v)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
 static Connection db;static boolean fail=false;
 static List<JSONObject> select(String sql,Object...args)throws RepositoryException{
  check(sql.startsWith("SELECT "));if(fail)throw new RepositoryException("private database detail");
  try(var ps=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);try(var rs=ps.executeQuery()){var list=new ArrayList<JSONObject>();var m=rs.getMetaData();while(rs.next()){var r=new JSONObject();for(int i=1;i<=m.getColumnCount();i++)r.put(m.getColumnLabel(i),rs.getObject(i));list.add(r);}return list;}}catch(SQLException e){throw new RepositoryException(e);}
 }
 static RequestContext request(ApiV3TagSuggestionProcessor p,String token,String query)throws Exception{
  var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/suggestions/tags"+(query==null?"":"?"+query));
  try{if(token!=null)raw.headers().set("Authorization",token);raw.headers().set("Cookie","legacy=valid");var req=new Request(null,raw){public String getRemoteAddr(){return "192.0.2.67";}};req.setParams(new HashMap<>(Map.of("apiKey","old")));var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(p::suggestions);c.handle();return c;}finally{raw.release();}
 }
 static int status(RequestContext c){return (Integer)c.attr("status");}
 public static void main(String[] args)throws Exception{
  var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");props.setProperty("serverScheme","https");props.setProperty("serverHost","fishpi.cn");props.setProperty("serverPort","443");props.setProperty("runtimeMode","PRODUCTION");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);Latkes.setServerScheme("https");Latkes.setServerHost("fishpi.cn");Latkes.setServerPort("443");Latkes.setContextPath("");
  try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:")){db=connection;try(var st=db.createStatement()){
   st.execute("CREATE TABLE users(oId TEXT PRIMARY KEY,userStatus INT)");st.execute("INSERT INTO users VALUES('1',0),('2',4)");st.execute("CREATE TABLE tags(oId TEXT PRIMARY KEY,tagTitle VARCHAR(255),tagStatus INT,tagReferenceCount INT)");st.execute("CREATE TABLE articles(oId TEXT PRIMARY KEY)");st.execute("INSERT INTO articles VALUES('10')");st.execute("CREATE TABLE links(oId TEXT PRIMARY KEY,tag_oId VARCHAR(19),article_oId VARCHAR(19))");
   st.execute("INSERT INTO tags VALUES('1','C++',0,20),('2','C#',0,15),('3','中文',0,10),('4','a_b',0,8),('5','acb',0,7),('6','Unreferenced',0,0),('7','Disabled',1,100),('8','Stale',0,200)");for(int i=1;i<=7;i++)st.execute("INSERT INTO links VALUES('"+i+"','"+i+"','10')");st.execute("INSERT INTO links VALUES('8','8','999')");for(int i=0;i<10;i++){st.execute("INSERT INTO tags VALUES('"+(20+i)+"','Test"+i+"',0,"+(100+i)+")");st.execute("INSERT INTO links VALUES('"+(20+i)+"','"+(20+i)+"','10')");}
  }
  var s=new ApiV3TagSuggestionQueryService();inject(s,"users",new UserRepository(){public String getName(){return "users";}public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return ApiV3TagSuggestionsTest.select(sql,args);}});inject(s,"tags",new TagRepository(){public String getName(){return "tags";}public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return ApiV3TagSuggestionsTest.select(sql,args);}});inject(s,"relations",new TagArticleRepository(){public String getName(){return "links";}});inject(s,"articles",new ArticleRepository(){public String getName(){return "articles";}});
  var hot=s.suggestions("1","");check(hot.keySet().equals(Set.of("list")));check(hot.getJSONArray("list").length()==7);check(hot.getJSONArray("list").getString(0).equals("Test9"));check(s.suggestions("1"," c ").getJSONArray("list").length()==2);check(s.suggestions("1","C++").getJSONArray("list").getString(0).equals("C++"));check(s.suggestions("1","a_").getJSONArray("list").length()==1);check(s.suggestions("1","Stale").getJSONArray("list").isEmpty());check(s.suggestions("1","Disabled").getJSONArray("list").isEmpty());check(s.suggestions("1","Unref").getJSONArray("list").isEmpty());check(s.suggestions("1","中").getJSONArray("list").getString(0).equals("中文"));
  System.out.println("PASS tag suggestions 1 actual SQL hot seven/prefix case/Chinese/C++/literal underscore, valid tags and existing article association");
  check(ApiV3TagSuggestionQueryService.prefix("\uFEFF\u00a0C#\u3000").equals("c#"));
  check(ApiV3TagSuggestionQueryService.prefix("x".repeat(64)).length() == 64);
  for (String bad : List.of("a b", "%", "bad/", "<html>", "😄", "x".repeat(65), "x\u0000")) {
   try {
    s.suggestions("1", bad);
    throw new AssertionError();
   } catch (ApiV3Exception e) {
    check(e.status == 422);
   }
  }
  try {
   s.suggestions("2", "c");
   throw new AssertionError();
  } catch (ApiV3Exception e) {
   check(e.status == 404);
  }
  fail = true;
  try {
   s.suggestions("1", "c");
   throw new AssertionError();
  } catch (ApiV3Exception e) {
   check(e.status == 503);
   check(!e.getMessage().contains("private"));
  }
  fail = false;
  System.out.println("PASS tag suggestions 2 confirmed character/trim/64 boundary, invalid actor and fail-closed database errors");
  var p = new ApiV3TagSuggestionProcessor();
  inject(p, "query", s);
  var auth = new ApiV3AuthMidware();
  inject(auth, "apiV3TokenMgmtService", new ApiV3TokenMgmtService() {
   public JSONObject authenticate(String token) {
    return Set.of("good", "wrong").contains(token)
            ? new JSONObject().put("user", new JSONObject().put("oId", "1"))
                    .put("scope", token.equals("good") ? "content.read" : "profile.read")
            : null;
   }
  });
  ApiV3TagSuggestionProcessor.registerRoutes(p, auth);
  Dispatcher.mapping();
  for (String token : Arrays.asList(null, "Basic good", "Bearer expired")) check(status(request(p, token, null)) == 401);
  check(status(request(p, "Bearer wrong", null)) == 403);
  for (String query : List.of("prefix=a&prefix=b", "prefix=%FF", "prefix=%25", "prefix=a&owner=2"))
   check(status(request(p, "Bearer good", query)) == 422);
  var c = request(p, "Bearer good", "prefix=C%2B%2B");
  check(status(c) == 200);
  check(((JSONObject) c.attr("data")).getJSONArray("list").getString(0).equals("C++"));
  check("no-store".equals(c.getResponse().getHeader("Cache-Control")));
  check(status(request(p, "Bearer good", null)) == 200);
  System.out.println("PASS tag suggestions 3 actual registered route/Bearer/content.read, strict query and percent-encoded punctuation, no Cookie/apiKey fallback");
  var field = ApiV3TagSuggestionProcessor.class.getDeclaredField("COUNTS");
  field.setAccessible(true);
  ((com.github.benmanes.caffeine.cache.Cache<?, ?>) field.get(null)).invalidateAll();
  for (int i = 0; i < 120; i++) check(status(request(p, "Bearer good", "prefix=none")) == 200);
  check(status(request(p, "Bearer good", null)) == 429);
  ((com.github.benmanes.caffeine.cache.Cache<?, ?>) field.get(null)).invalidateAll();
  field = ApiV3TagSuggestionProcessor.class.getDeclaredField("QUERIES");
  field.setAccessible(true);
  var sem = (java.util.concurrent.Semaphore) field.get(null);
  sem.acquire(4);
  try {
   check(status(request(p, "Bearer good", null)) == 503);
  } finally {
   sem.release(4);
  }
  check(status(request(p, "Bearer good", null)) == 200);
  System.out.println("PASS tag suggestions 4 bounded rate/concurrency rejection and recovery without mutation");
  }
 }
}
