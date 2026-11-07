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
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.processor.ApiV3UserSuggestionProcessor;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;
public class ApiV3UserSuggestionsTest{
 static void check(boolean v){if(!v)throw new AssertionError();}
 static void inject(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 static RequestContext request(ApiV3UserSuggestionProcessor p,String token,String qs)throws Exception{
  var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/suggestions/users"+(qs==null?"":"?"+qs));
  try{if(token!=null)raw.headers().set("Authorization",token);raw.headers().set("Cookie","legacy=valid");var req=new Request(null,raw){public String getRemoteAddr(){return "192.0.2.65";}};req.setParams(new HashMap<>(Map.of("apiKey","old")));var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(p::suggestions);c.handle();return c;}finally{raw.release();}
 }
 static int status(RequestContext c){return (Integer)c.attr("status");}
 public static void main(String[] args)throws Exception{
  var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");props.setProperty("serverScheme","https");props.setProperty("serverHost","fishpi.cn");props.setProperty("serverPort","443");props.setProperty("runtimeMode","PRODUCTION");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);Latkes.setServerScheme("https");Latkes.setServerHost("fishpi.cn");Latkes.setServerPort("443");Latkes.setContextPath("");
  try(var db=DriverManager.getConnection("jdbc:sqlite::memory:")){
   try(var st=db.createStatement()){st.execute("CREATE TABLE users(oId TEXT PRIMARY KEY,userName TEXT,userNickname TEXT,userAvatarURL TEXT,userStatus INT,userPassword TEXT,userPhone TEXT)");st.execute("INSERT INTO users VALUES('1','owner','本人','',0,'secret','phone'),('2','Alpha','A <img onerror=x>','https://example.invalid/a.png?sig=1',0,'private','phone'),('3','alpha_hidden','bad','',4,'secret','phone'),('4','alpha_wait','bad','',1,'secret','phone'),('5','a_b','n','javascript:bad()',0,'secret','phone'),('6','acb','other','',0,'secret','phone')");for(int i=0;i<14;i++)st.execute("INSERT INTO users VALUES('"+(100+i)+"','match"+String.format("%02d",i)+"','','',0,'secret','phone')");}
   var service=new ApiV3UserSuggestionQueryService();boolean[] fail={false};int[] queries={0};
   inject(service,"users",new UserRepository(){public String getName(){return "users";}public List<JSONObject> select(String sql,Object...args)throws RepositoryException{queries[0]++;if(fail[0])throw new RepositoryException("private db failure");check(sql.startsWith("SELECT "));try(var ps=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);try(var rows=ps.executeQuery()){var list=new ArrayList<JSONObject>();var meta=rows.getMetaData();while(rows.next()){var value=new JSONObject();for(int i=1;i<=meta.getColumnCount();i++)value.put(meta.getColumnLabel(i),rows.getObject(i));list.add(value);}return list;}}catch(SQLException e){throw new RepositoryException(e);}}});
   check(service.suggestions("1","").getJSONArray("list").isEmpty());check(queries[0]==1);
   var list=service.suggestions("1","aLP").getJSONArray("list");check(list.length()==1);var user=list.getJSONObject(0);check(user.keySet().equals(Set.of("id","userName","userNickname","userAvatarURL")));check(user.getString("userName").equals("Alpha"));check(user.getString("userNickname").contains("<img"));check(user.getString("userAvatarURL").endsWith("?sig=1"));
   check(service.suggestions("1","a_").getJSONArray("list").length()==1);check(service.suggestions("1","a_").getJSONArray("list").getJSONObject(0).getString("userAvatarURL").isEmpty());
   list=service.suggestions("1","MATCH").getJSONArray("list");check(list.length()==10);check(list.getJSONObject(0).getString("userName").equals("match00"));check(list.getJSONObject(9).getString("userName").equals("match09"));
   System.out.println("PASS suggestions 1 actual SQL case-insensitive prefix/literal underscore/current status, stable first ten, four-field projection and safe avatar");
   for(String prefix:List.of("%","a b","a' OR 1=1","中文","a/","a".repeat(65),"a\n")){try{service.suggestions("1",prefix);throw new AssertionError();}catch(ApiV3Exception e){check(e.status==422);}}
   for(String actor:List.of("3","4","999")){try{service.suggestions(actor,"a");throw new AssertionError();}catch(ApiV3Exception e){check(e.status==404);}}
   fail[0]=true;try{service.suggestions("1","a");throw new AssertionError();}catch(ApiV3Exception e){check(e.status==503);check(!e.getMessage().contains("private"));}fail[0]=false;
   System.out.println("PASS suggestions 2 invalid input/actor rejected, database failures are not fabricated empty results");
   var p=new ApiV3UserSuggestionProcessor();inject(p,"query",service);var auth=new ApiV3AuthMidware();inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String t){return Set.of("good","wrong").contains(t)?new JSONObject().put("user",new JSONObject().put("oId","1")).put("scope",t.equals("good")?"profile.read":"content.read"):null;}});ApiV3UserSuggestionProcessor.registerRoutes(p,auth);Dispatcher.mapping();
   for(String token:Arrays.asList(null,"Basic good","Bearer revoked","Bearer expired"))check(status(request(p,token,"prefix=al"))==401);check(status(request(p,"Bearer wrong","prefix=al"))==403);
   for(String qs:List.of("prefix=a&prefix=b","prefix=%FF","prefix=%25","prefix=a%0A","prefix=中文","prefix=abc&owner=2"))check(status(request(p,"Bearer good",qs))==422);
   var c=request(p,"Bearer good","prefix=AL");check(status(c)==200);check(((JSONObject)c.attr("data")).getJSONArray("list").length()==1);check("no-store".equals(c.getResponse().getHeader("Cache-Control")));check("Authorization".equals(c.getResponse().getHeader("Vary")));check(status(request(p,"Bearer good",null))==200);
   System.out.println("PASS suggestions 3 real route/Bearer/profile.read, strict query/duplicate/UTF8 boundary, no Cookie/apiKey fallback and no-store");
   var counts=ApiV3UserSuggestionProcessor.class.getDeclaredField("COUNTS");counts.setAccessible(true);((com.github.benmanes.caffeine.cache.Cache<?,?>)counts.get(null)).invalidateAll();for(int i=0;i<120;i++)check(status(request(p,"Bearer good","prefix="))==200);c=request(p,"Bearer good","prefix=a");check(status(c)==429);check("60".equals(c.getResponse().getHeader("Retry-After")));((com.github.benmanes.caffeine.cache.Cache<?,?>)counts.get(null)).invalidateAll();var field=ApiV3UserSuggestionProcessor.class.getDeclaredField("QUERIES");field.setAccessible(true);var permits=(java.util.concurrent.Semaphore)field.get(null);permits.acquire(4);try{check(status(request(p,"Bearer good","prefix=a"))==503);}finally{permits.release(4);}check(status(request(p,"Bearer good","prefix=a"))==200);
   System.out.println("PASS suggestions 4 bounded request/concurrency guards recover without database mutation");
  }
 }
}
