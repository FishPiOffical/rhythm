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
import org.b3log.symphony.processor.ApiV3ChatMuteProcessor;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;
public class ApiV3ChatMutesTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static void inject(Object o,String name,Object value)throws Exception{var type=o.getClass();while(type!=null&&!java.util.Arrays.stream(type.getDeclaredFields()).anyMatch(field->field.getName().equals(name)))type=type.getSuperclass();var f=type.getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 static RequestContext request(ApiV3ChatMuteProcessor p,String token,String qs)throws Exception{
  var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/chat/mutes"+(qs==null?"":"?"+qs));
  try{if(token!=null)raw.headers().set("Authorization",token);raw.headers().set("Cookie","legacy=valid");var req=new Request(null,raw){public String getRemoteAddr(){return "192.0.2.65";}};req.setParams(new HashMap<>(Map.of("apiKey","old")));var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(p::list);c.handle();return c;}finally{raw.release();}
 }
 static int status(RequestContext c){return (Integer)c.attr("status");}
 public static void main(String[] args)throws Exception{
  var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");props.setProperty("serverScheme","https");props.setProperty("serverHost","fishpi.cn");props.setProperty("serverPort","443");props.setProperty("runtimeMode","PRODUCTION");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);Latkes.setServerScheme("https");Latkes.setServerHost("fishpi.cn");Latkes.setServerPort("443");Latkes.setContextPath("");

  try(var db=DriverManager.getConnection("jdbc:sqlite::memory:")){
   try(var st=db.createStatement()){
    st.execute("CREATE TABLE users(oId TEXT PRIMARY KEY,userName TEXT,userNickname TEXT,userAvatarURL TEXT,userStatus INT,userPassword TEXT)");
    st.execute("CREATE TABLE cloud(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");
    st.execute("INSERT INTO users VALUES('1','renamed','<img onerror=x>','javascript:bad()',0,'secret'),('2','active','nick','https://example.invalid/a.png?sig=1',0,'secret'),('3','disabled','','',4,'secret'),('4','waiting','','',1,'secret'),('5','expired','','',0,'secret'),('6','boundary','','',0,'secret')");
    st.execute("INSERT INTO cloud VALUES('1','1','sys-mute','3000'),('2','2','sys-mute','2000'),('3','3','sys-mute','9000'),('4','4','sys-mute','9000'),('5','5','sys-mute','999'),('6','6','sys-mute','1000'),('7','all:fish:mute','sys-mute','9000'),('8','1','sys-risk','9000'),('9','si:guo','record','{bad legacy names}')");
   }
   final boolean[] fail={false};
   var service=new ApiV3ChatMuteQueryService(){protected long now(){return 1000;}};
   inject(service,"users",new UserRepository(){public String getName(){return "users";}});
   inject(service,"cloud",new CloudRepository(){public String getName(){return "cloud";}public List<JSONObject> select(String sql,Object...args)throws RepositoryException{if(fail[0])throw new RepositoryException("private SQL");try(var ps=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);try(var rs=ps.executeQuery()){var out=new ArrayList<JSONObject>();var m=rs.getMetaData();while(rs.next()){var row=new JSONObject();for(int i=1;i<=m.getColumnCount();i++)row.put(m.getColumnLabel(i),rs.getObject(i));out.add(row);}return out;}}catch(SQLException e){throw new RepositoryException(e);}}});
   var result=service.list(1,20);var list=result.getJSONArray("list");check(list.length()==2);check(result.getJSONObject("pagination").getInt("total")==2);check(list.getJSONObject(0).getString("id").equals("2"));check(list.getJSONObject(1).getString("userName").equals("renamed"));check(list.getJSONObject(1).getString("userAvatarURL").isEmpty());check(list.getJSONObject(1).getString("userNickname").contains("<img"));check(list.getJSONObject(0).getString("userAvatarURL").endsWith("sig=1"));check(list.getJSONObject(0).keySet().equals(Set.of("id","userName","userNickname","userAvatarURL","expiresAt")));
   System.out.println("PASS mutes 1 actual SQL ID binding/current valid status/expiry excludes global risk and legacy snapshots, public projection/safe avatar");
   try(var st=db.createStatement()){for(int i=10;i<62;i++){st.execute("INSERT INTO users VALUES('"+i+"','user"+i+"','','',0,'secret')");st.execute("INSERT INTO cloud VALUES('"+i+"','"+i+"','sys-mute','4000')");}}
   result=service.list(2,20);check(result.getJSONArray("list").length()==20);check(result.getJSONObject("pagination").getInt("total")==54);check(result.getJSONObject("pagination").getInt("pageCount")==3);check(result.getBoolean("hasMore"));check(service.list(3,20).getJSONArray("list").length()==14);check(!service.list(3,20).getBoolean("hasMore"));check(service.list(4,20).getJSONArray("list").isEmpty());check(service.list(1,50).getJSONArray("list").length()==50);
   System.out.println("PASS mutes 2 expiry then ID ordering and complete total/page count, 20/50 pages and out-of-range empty page");
   for(int[] args2:List.of(new int[]{0,20},new int[]{1,51},new int[]{Integer.MAX_VALUE,20}))try{service.list(args2[0],args2[1]);throw new AssertionError();}catch(ApiV3Exception e){check(e.status==422);}
   for(String value:List.of("bad","9223372036854775808","-1")){try(var st=db.createStatement()){st.execute("UPDATE cloud SET data='"+value+"' WHERE oId='1'");}try{service.list(1,20);throw new AssertionError();}catch(ApiV3Exception e){check(e.status==503);} }
   try(var st=db.createStatement()){st.execute("UPDATE cloud SET data='3000' WHERE oId='1'");st.execute("INSERT INTO cloud VALUES('999','1','sys-mute','1')");}try{service.list(1,20);throw new AssertionError();}catch(ApiV3Exception e){check(e.status==503);}try(var st=db.createStatement()){st.execute("DELETE FROM cloud WHERE oId='999'");}
   fail[0]=true;try{service.list(1,20);throw new AssertionError();}catch(ApiV3Exception e){check(e.status==503);check(!e.getMessage().contains("private"));}fail[0]=false;
   var budget=new ApiV3ChatMuteQueryService(){protected List<JSONObject> rows(){return Collections.nCopies(10001,new JSONObject());}};try{budget.list(1,20);throw new AssertionError();}catch(ApiV3Exception e){check(e.status==503);}
   System.out.println("PASS mutes 3 malformed/overflow/duplicate state, DB failure and read budget fail explicitly without false empty or partial list");
   var p=new ApiV3ChatMuteProcessor();inject(p,"query",service);var auth=new ApiV3AuthMidware();inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String t){return Set.of("good","wrong").contains(t)?new JSONObject().put("user",new JSONObject().put("oId","1")).put("scope",t.equals("good")?"content.read":"profile.read"):null;}});ApiV3ChatMuteProcessor.registerRoutes(p,auth);Dispatcher.mapping();
   for(String token:Arrays.asList(null,"Basic good","Bearer revoked"))check(status(request(p,token,null))==401);check(status(request(p,"Bearer wrong",null))==403);
   for(String qs:List.of("p=1&p=2","size=51","p=0","p=-1","p=%FF","p=1&owner=2","size=1.5","p=2147483647","p=1%0A"))check(status(request(p,"Bearer good",qs))==422);
   var c=request(p,"Bearer good",null);check(status(c)==200);check(((JSONObject)c.attr("data")).getJSONObject("pagination").getInt("size")==20);check("no-store".equals(c.getResponse().getHeader("Cache-Control")));check("Authorization".equals(c.getResponse().getHeader("Vary")));check(status(request(p,"Bearer good","p=2&size=50"))==200);
   System.out.println("PASS mutes 4 actual route content.read/Bearer required, no legacy Cookie/apiKey fallback; strict UTF8/query/defaults/no-store");
   var counts=ApiV3ChatMuteProcessor.class.getDeclaredField("COUNTS");counts.setAccessible(true);((com.github.benmanes.caffeine.cache.Cache<?,?>)counts.get(null)).invalidateAll();for(int i=0;i<120;i++)check(status(request(p,"Bearer good",null))==200);check(status(request(p,"Bearer good",null))==429);((com.github.benmanes.caffeine.cache.Cache<?,?>)counts.get(null)).invalidateAll();var field=ApiV3ChatMuteProcessor.class.getDeclaredField("QUERIES");field.setAccessible(true);var permits=(java.util.concurrent.Semaphore)field.get(null);permits.acquire(4);try{check(status(request(p,"Bearer good",null))==503);}finally{permits.release(4);}check(status(request(p,"Bearer good",null))==200);
   System.out.println("PASS mutes 5 bounded requests/concurrency release correctly and do not mutate restrictions");
  }
 }
}
