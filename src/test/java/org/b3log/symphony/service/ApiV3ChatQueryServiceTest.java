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
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.processor.ApiV3Processor;
import org.b3log.symphony.processor.ApiV3ChatProcessor;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpMethod;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

/** 覆盖实际查询、控制器、DTO 和权限范围，令牌验证及 Markdown 转换由夹具适配。 */
public class ApiV3ChatQueryServiceTest {
    static void inject(Object bean,String field,Object value)throws Exception{var f=bean.getClass().getDeclaredField(field);f.setAccessible(true);f.set(bean,value);}
    interface Work{void run()throws Exception;}
    static void reject(int code,Work work)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,code);}}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");final ApiV3ChatQueryService service=new ApiV3ChatQueryService();
        final ApiV3ChatProcessor processor=new ApiV3ChatProcessor();final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        final List<String> sql=new ArrayList<>();boolean unavailable;
        org.b3log.symphony.util.ChatTopicValue currentTopic;boolean topicFailure;
        String scope="content.read";
        Fixture()throws Exception{
            try(var s=db.createStatement()){s.execute("CREATE TABLE chat_room(oId TEXT PRIMARY KEY,content TEXT)");}
            var repository=new ChatRoomRepository(){
                @Override public String getName(){return "chat_room";}
                @Override public List<JSONObject> select(String text,Object...args)throws RepositoryException{
                    sql.add(text);if(unavailable)throw new RepositoryException("database unavailable");
                    try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next())out.add(new JSONObject().put("oId",r.getString("oId")).put("content",r.getString("content")));return out;}}catch(SQLException e){throw new RepositoryException(e);}
                }
            };
            inject(service,"messages",repository);inject(processor,"query",service);
            inject(service,"topics",new ChatTopicMgmtService(){@Override public org.b3log.symphony.util.ChatTopicValue readCommitted()throws RepositoryException{if(topicFailure)throw new RepositoryException("topic unavailable");return currentTopic;}});
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){@Override public JSONObject authenticate(String token){return token.equals("VALID")?new JSONObject().put("scope",scope).put("user",new JSONObject().put("oId","123")):null;}});
        }
        void raw(String id,String content)throws Exception{try(var s=db.prepareStatement("INSERT INTO chat_room VALUES(?,?)")){s.setString(1,id);s.setString(2,content);s.executeUpdate();}}
        void message(String id,Object content)throws Exception{raw(id,new JSONObject().put("userOId",123L).put("userName","Reader").put("userNickname","<b>Nickname</b>").put("userAvatarURL","https://file.fishpi.cn/avatar.png").put("time",1700000000000L).put("client","Web/Test").put("content",content).put("userPassword","PRIVATE-password").put("apiKey","PRIVATE-key").put("userEmail","PRIVATE-email").put("ip","PRIVATE-ip").put("user",new JSONObject().put("token","PRIVATE-token")).toString());}
        JSONObject request(String credential,Map<String,String> params,String id,Consumer<RequestContext> handler)throws Exception{
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.GET,"/api/v3/chat/messages?apiKey=legacy");
            var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
            try{
                if(credential!=null)raw.headers().set("Authorization",credential);raw.headers().set("Cookie","JSESSIONID=legacy");
                var request=new Request(null,raw);request.setParams(new HashMap<>(params));var c=new RequestContext(request,new Response(null,response));
                c.pathVars(new HashMap<>());if(id!=null)c.pathVar("messageId",id);c.attr(User.USER,new JSONObject().put("oId","321"));
                c.addHandler(handler::accept);auth.contentRead(c);
                final var out=new JSONObject().put("status",c.attr("test.status"));
                if(c.attr("test.data")!=null)out.put("data",c.attr("test.data"));if(c.attr("test.code")!=null)out.put("code",c.attr("test.code"));
                if(out.getInt("status")==200){Assert.assertEquals(response.headers().get("Cache-Control"),"no-store");Assert.assertEquals(response.headers().get("Vary"),"Authorization");Assert.assertEquals(((JSONObject)c.attr(ApiV3Processor.IDENTITY)).getJSONObject("user").getString("oId"),"123");}
                return out;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();}
    }
    static List<String> ids(JSONObject value){var out=new ArrayList<String>();for(Object row:value.getJSONArray("list"))out.add(((JSONObject)row).getString("id"));return out;}
    static void noPrivate(JSONObject value){Assert.assertFalse(value.toString().contains("PRIVATE"),value.keySet().toString());}
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);Latkes.setServerScheme("http");Latkes.setServerHost("localhost");Latkes.setServerPort("8080");Latkes.setContextPath("");
        try(var f=new Fixture()){
            for(int i=101;i<=110;i++)f.message(""+i,"message-"+i);
            var first = f.service.list(null, null, 3);
            Assert.assertEquals(ids(first), List.of("108", "109", "110"));
            Assert.assertTrue(first.getBoolean("hasMore"));
            Assert.assertEquals(first.getString("nextBefore"), "108");
            Assert.assertEquals(first.getString("nextAfter"), "110");
            var older = f.service.list(first.getString("nextBefore"), null, 3);
            Assert.assertEquals(ids(older), List.of("105", "106", "107"));

            // 新消息加入后，旧游标仍定位到同一页，正向游标可取到新增内容。
            f.message("111", "new");
            Assert.assertEquals(ids(f.service.list("108", null, 3)), ids(older));
            Assert.assertEquals(ids(f.service.list(null, "107", 3)), List.of("108", "109", "110"));
            Assert.assertTrue(f.service.list(null, "107", 3).getBoolean("hasMore"));
            Assert.assertEquals(ids(f.service.list(null, "110", 3)), List.of("111"));
            Assert.assertFalse(f.service.list(null, "110", 3).getBoolean("hasMore"));
            var empty = f.service.list("101", null, 3);
            Assert.assertTrue(ids(empty).isEmpty());
            Assert.assertFalse(empty.getBoolean("hasMore"));
            Assert.assertEquals(empty.getString("nextBefore"), "");
            var dto = f.service.detail("105");
            Assert.assertEquals(dto.getString("content"), "message-105");
            Assert.assertEquals(dto.getLong("createdAt"), 1700000000000L);
            noPrivate(dto);
        }
        System.out.println("PASS actual bounded cursor SQL returns chronological slices without overlap, supports incremental reads and remains stable when newer messages arrive");
        try(var f=new Fixture()){
            for(int i=101;i<=110;i++)f.message(""+i,"message-"+i);
            var around = f.service.context("105", 2);
            Assert.assertEquals(ids(around), List.of("103", "104", "105", "106", "107"));
            Assert.assertTrue(around.getBoolean("hasMoreBefore"));
            Assert.assertTrue(around.getBoolean("hasMoreAfter"));
            Assert.assertEquals(around.getString("nextBefore"), "103");
            Assert.assertEquals(around.getString("nextAfter"), "107");
            var edge = f.service.context("101", 2);
            Assert.assertEquals(ids(edge), List.of("101", "102", "103"));
            Assert.assertFalse(edge.getBoolean("hasMoreBefore"));
            Assert.assertEquals(edge.getString("nextBefore"), "101");
            try (var s = f.db.createStatement()) {
                s.executeUpdate("DELETE FROM chat_room WHERE oId='105'");
            }
            reject(404, () -> f.service.detail("105"));
            reject(404, () -> f.service.context("105", 2));
            Assert.assertEquals(ids(f.service.list("108",null,3)),List.of("104","106","107"));
        }
        System.out.println("PASS context includes an existing anchor and bounded neighbors once, reports both edges and rejects a withdrawn/missing anchor");
        try(var f=new Fixture()){
            for(String id:List.of(""," ","x","1' OR 1=1 --","1".repeat(33)))reject(422,()->f.service.list(id,null,20));
            reject(422, () -> f.service.list("1", "2", 20));
            reject(422, () -> f.service.list(null, null, 0));
            reject(422, () -> f.service.list(null, null, 51));
            reject(422, () -> f.service.context("1", 26));
            Assert.assertTrue(f.sql.isEmpty());
            f.unavailable = true;
            try {
                f.service.list(null, null, 20);
                Assert.fail();
            } catch (RepositoryException expected) {
            }
        }
        System.out.println("PASS malformed/injected/conflicting cursors and out-of-range limits reject before SQL; database errors propagate rather than becoming an empty successful history");
        try(var f=new Fixture()){
            f.message("101","<script>alert(1)</script><p onclick='bad()' style='color:red'>hello</p><a href='javascript:bad()'>bad</a><img src='x' onerror='bad()'>");
            var dto=f.service.detail("101");Assert.assertEquals(dto.keySet(),Set.of("id","createdAt","type","content","contentHtml","author","client","card"));noPrivate(dto);Assert.assertEquals(dto.getString("type"),"text");Assert.assertTrue(dto.getString("content").contains("<script>"));
            String html=dto.getString("contentHtml");for(String bad:List.of("<script","onclick","onerror","javascript:","style="))Assert.assertFalse(html.contains(bad),html);Assert.assertTrue(html.contains("hello"));
            var author=dto.getJSONObject("author");Assert.assertEquals(author.keySet(),Set.of("id","userName","userNickname","userAvatarURL"));Assert.assertEquals(author.getString("userNickname"),"Nickname");Assert.assertEquals(author.getString("id"),"123");
        }
        System.out.println("PASS text retains raw Markdown separately while the extracted production chat sanitizer removes executable HTML; envelope/author fields are explicit whitelists");
        try(var f=new Fixture()){
            var packet=new JSONObject().put("msgType","redPacket").put("type","rockPaperScissors").put("money",256).put("count",1).put("got",0).put("msg","<b>领取</b>").put("gesture","PRIVATE-gesture").put("token","PRIVATE-key").put("who",new JSONArray().put(new JSONObject().put("ip","PRIVATE-ip"))).put("recivers","PRIVATE-recipients");
            f.message("101",packet.toString());f.message("102",packet);
            for(String id:List.of("101","102")){var dto=f.service.detail(id);noPrivate(dto);Assert.assertEquals(dto.getString("type"),"redPacket");Assert.assertEquals(dto.getString("content"),"");Assert.assertEquals(dto.getString("contentHtml"),"");Assert.assertEquals(dto.getJSONObject("card").keySet(),Set.of("type","money","count","got","message"));Assert.assertEquals(dto.getJSONObject("card").getString("message"),"领取");}
            f.message("103",new JSONObject().put("msgType","music").put("title","<b>Song</b>").put("source","javascript:bad()").put("coverURL","https://name:password@example.com/img").put("from","netease").put("token","PRIVATE").toString());
            var music=f.service.detail("103");noPrivate(music);Assert.assertEquals(music.getString("type"),"music");Assert.assertEquals(music.getJSONObject("card").getString("source"),"");Assert.assertEquals(music.getJSONObject("card").getString("coverURL"),"");Assert.assertEquals(music.getJSONObject("card").getString("title"),"Song");
            f.message("104",new JSONObject().put("msgType","weather").put("date","2026-10-18").put("weatherCode","CLEAR_DAY").put("max","25").put("min","18").put("t","22").put("st","体感").put("userEmail","PRIVATE").toString());var weather=f.service.detail("104");noPrivate(weather);Assert.assertEquals(weather.getJSONObject("card").keySet(),Set.of("date","weatherCode","max","min","t","st"));
        }
        System.out.println("PASS string/object rich cards preserve supported display fields, hide red-packet private state and unknown metadata, and reject unsafe media URLs");
        try(var f=new Fixture()){
            f.raw("101","{broken");f.raw("102","{\"content\":\"one\",\"content\":\"two\"}");f.raw("103","x".repeat(102401));
            f.raw("104",new JSONObject().put("content",new JSONArray()).toString());
            for(String id:List.of("101","102","103","104")){var dto=f.service.detail(id);Assert.assertEquals(dto.getString("type"),"unavailable");Assert.assertEquals(dto.getString("content"),"");}
            Assert.assertEquals(ids(f.service.list(null,null,2)),List.of("103","104"));Assert.assertEquals(ids(f.service.list("103",null,2)),List.of("101","102"));
            f.raw("105",new JSONObject().put("userOId",0).put("userName","马库斯").put("time","bad").put("content","hello").toString());var bot=f.service.detail("105");Assert.assertEquals(bot.getJSONObject("author").getString("id"),"");Assert.assertEquals(bot.getJSONObject("author").getString("userName"),"马库斯");Assert.assertEquals(bot.getLong("createdAt"),105L);
        }
        System.out.println("PASS malformed/duplicate/oversized stored envelopes become cursor-preserving placeholders without raw data; bot snapshots keep their public display identity");
        try(var f=new Fixture()){
            for(int i=101;i<=180;i++)f.message(""+i,"message");
            Assert.assertEquals(f.service.list(null,null,50).getJSONArray("list").length(),50);
            var context=f.service.context("140",25);Assert.assertEquals(context.getJSONArray("list").length(),51);Assert.assertEquals(new HashSet<>(ids(context)).size(),51);
            String nested="{\"content\":{\"x\":"+"[".repeat(40)+"0"+"]".repeat(40)+"}}";
            f.raw("181",nested);Assert.assertEquals(f.service.detail("181").getString("type"),"unavailable");
            f.message("182",new JSONObject().put("msgType","music").put("source","123456").put("coverURL","https://file.fishpi.cn/cover.png").put("title","Song").toString());
            Assert.assertEquals(f.service.detail("182").getJSONObject("card").getString("source"),"123456");
        }
        System.out.println("PASS maximum history/context sizes stay bounded with distinct messages; deeply nested stored data is unavailable and numeric music IDs remain usable");
        try(var f=new Fixture()){
            f.message("101","hello");for(String credential:Arrays.asList(null,"Bearer INVALID")){var denied=f.request(credential,Map.of("apiKey","legacy"),null,f.processor::list);Assert.assertEquals(denied.getInt("status"),401);}Assert.assertTrue(f.sql.isEmpty());
            f.scope="profile.read";Assert.assertEquals(f.request("Bearer VALID",Map.of(),"101",f.processor::detail).getInt("status"),403);Assert.assertTrue(f.sql.isEmpty());f.scope="content.read";
            var list=f.request("Bearer VALID",Map.of("size","1"),null,f.processor::list);Assert.assertEquals(list.getInt("status"),200);Assert.assertEquals(ids(list.getJSONObject("data")),List.of("101"));
            Assert.assertEquals(f.request("Bearer VALID",Map.of(),"101",f.processor::detail).getJSONObject("data").getString("id"),"101");
            Assert.assertEquals(f.request("Bearer VALID",Map.of("radius","1"),"101",f.processor::context).getJSONObject("data").getString("anchorId"),"101");
            Assert.assertEquals(f.request("Bearer VALID",Map.of("before","bad"),null,f.processor::list).getInt("status"),422);
            Assert.assertEquals(f.request("Bearer VALID",Map.of("before","101","after","101"),null,f.processor::list).getInt("status"),422);
            Assert.assertEquals(f.request("Bearer VALID",Map.of(),"999",f.processor::detail).getInt("status"),404);
            f.unavailable=true;Assert.assertEquals(f.request("Bearer VALID",Map.of(),null,f.processor::list).getInt("status"),500);
        }
        System.out.println("PASS actual Bearer/scope middleware, controller, request parsing and SQL chain rejects Cookie/apiKey fallback and returns controlled 401/403/422/404/500 errors");
        try(var f=new Fixture()){
            org.b3log.symphony.processor.channel.ChatroomChannel.discussing="聊聊 <T> 与 emoji 😀";
            org.b3log.symphony.processor.ChatroomProcessor.setBarragerPrice(7,"测试单位");
            var topic=f.request("Bearer VALID",Map.of(),null,f.processor::topic).getJSONObject("data");
            Assert.assertEquals(topic.keySet(),Set.of("text"));Assert.assertEquals(topic.getString("text"),"聊聊 <T> 与 emoji 😀");
            var price=f.request("Bearer VALID",Map.of(),null,f.processor::barragerPrice).getJSONObject("data");
            Assert.assertEquals(price.keySet(),Set.of("amount","unit"));Assert.assertEquals(price.getInt("amount"),7);Assert.assertEquals(price.getString("unit"),"测试单位");
            for(var handler:List.<Consumer<RequestContext>>of(f.processor::topic,f.processor::barragerPrice)){
                Assert.assertEquals(f.request(null,Map.of("apiKey","legacy"),null,handler).getInt("status"),401);
                f.scope="profile.read";Assert.assertEquals(f.request("Bearer VALID",Map.of(),null,handler).getInt("status"),403);f.scope="content.read";
            }
            Assert.assertTrue(f.sql.isEmpty());
            f.currentTopic=new org.b3log.symphony.util.ChatTopicValue("持久话题","Author",17);
            Assert.assertEquals(f.request("Bearer VALID",Map.of(),null,f.processor::topic).getJSONObject("data").getString("text"),"持久话题");
            f.topicFailure=true;Assert.assertEquals(f.request("Bearer VALID",Map.of(),null,f.processor::topic).getInt("status"),500);
        } finally {
            org.b3log.symphony.processor.channel.ChatroomChannel.discussing="暂无";
            org.b3log.symphony.processor.ChatroomProcessor.setBarragerPrice(5,"积分");
        }
        System.out.println("PASS topic and barrager price read actual controller/query with content.read Bearer, private headers, plain-text/number whitelists and no history queries or mutation");
        org.b3log.symphony.processor.ChatroomProcessor.setBarragerPrice(0,"unit-0");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var writer=pool.submit(()->{for(int i=0;i<10000;i++)org.b3log.symphony.processor.ChatroomProcessor.setBarragerPrice(i,"unit-"+i);});
            var reader=pool.submit(()->{var service=new ApiV3ChatQueryService();for(int i=0;i<10000;i++){var value=service.barragerPrice();Assert.assertEquals(value.getString("unit"),"unit-"+value.getInt("amount"));}});
            writer.get();reader.get();
        } finally {org.b3log.symphony.processor.ChatroomProcessor.setBarragerPrice(5,"积分");}
        System.out.println("PASS extracted production price setter/getter keep amount and unit coherent under concurrent updates without changing legacy values");
    }
}
