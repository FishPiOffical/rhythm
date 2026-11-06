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
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;

/** Actual prefixed SELECTs, bounded paging, owner checks, projection, auth and routes; SQLite/token/envelope adapters. */
public class ApiV3EmotionQueryTest {
    static void inject(Object target, String name,Object value)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3EmotionQueryService query=new ApiV3EmotionQueryService();
        final ApiV3EmotionProcessor controller=new ApiV3EmotionProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware(); boolean fail, moveOnCount;int reads, failAt=-1;
        Fixture()throws Exception {
            sql("CREATE TABLE x_user(oId TEXT PRIMARY KEY,userStatus INT,userPassword TEXT)");
            sql("CREATE TABLE x_emotion(oId TEXT,emotionUserId TEXT,emotionType INT,emotionSort INT,emotionContent TEXT)");
            sql("CREATE TABLE x_emoji_group(oId TEXT PRIMARY KEY,emojiGroupUserId TEXT,emojiGroupName TEXT,emojiGroupSort INT,emojiGroupType INT,secret TEXT)");
            sql("CREATE TABLE x_emoji(oId TEXT PRIMARY KEY,emojiUrl TEXT,emojiStatus INT,emojiUploaderId TEXT)");
            sql("CREATE TABLE x_emoji_group_item(oId TEXT PRIMARY KEY,emojiGroupItemGroupId TEXT,emojiGroupItemEmojiId TEXT,emojiGroupItemName TEXT,emojiGroupItemSort INT)");
            sql("INSERT INTO x_user VALUES('1',0,'PRIVATE'),('2',0,'OTHER'),('3',0,'EMPTY'),('4',4,'DISABLED')");
            sql("INSERT INTO x_emotion VALUES('1','1',0,2,'joy'),('2','1',0,1,'smile'),('3','2',0,0,'sob'),('4','1',1,0,'OTHER_TYPE')");
            sql("INSERT INTO x_emoji_group VALUES('10','1','All',0,1,'SECRET'),('11','1','Custom',1,0,'SECRET'),('12','2','Other',0,1,'OTHER_SECRET')");
            sql("INSERT INTO x_emoji VALUES('20','https://example.test/a.png?x=1&y=2',0,'PRIVATE_UPLOADER'),('21','javascript:alert(1)',0,'SECRET'),('22','https://example.test/deleted.png',1,'SECRET')");
            sql("INSERT INTO x_emoji_group_item VALUES('30','10','20','first',1),('31','10','21','second',1),('32','10','22','deleted',2),('33','10','99','missing',3),('34','12','20','other',0)");
            inject(query,"users",new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"emotions",new EmotionRepository(){public String getName(){return "x_emotion";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"groups",new EmojiGroupRepository(){public String getName(){return "x_emoji_group";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"items",new EmojiGroupItemRepository(){public String getName(){return "x_emoji_group_item";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(query,"emojis",new EmojiRepository(){public String getName(){return "x_emoji";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}});
            inject(controller,"query",query);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("reader","other","wrong").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":"1")).put("scope",token.equals("wrong")?"settings.write":"profile.read"):null;}});
        }
        List<JSONObject> selectSQL(String text,Object...args)throws RepositoryException {
            reads++;Assert.assertTrue(text.startsWith("SELECT "));Assert.assertFalse(text.contains("SELECT *"));Assert.assertFalse(text.contains("userPassword"));Assert.assertFalse(text.contains("emojiUploaderId"));
            if(fail || reads==failAt)throw new RepositoryException("PRIVATE_DRIVER_ERROR");
            if(moveOnCount && text.startsWith("SELECT COUNT(*) AS total FROM `x_emoji_group_item`")) {
                moveOnCount=false;try {sql("UPDATE x_emoji_group SET emojiGroupUserId='2' WHERE oId='10'");}catch(Exception e){throw new RepositoryException(e);}
            }
            try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}
            catch(SQLException e){throw new RepositoryException(e);}
        }
        void sql(String text,Object...args)throws Exception {try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}}
        RequestContext http(String token,String path)throws Exception {return http(token,path,Map.of());}
        RequestContext http(String token,String path,Map<String,String> params)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,path+"?userId=2&apiKey=legacy");
            try{
                raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw);var queryParams=new HashMap<>(Map.of("userId","2","apiKey","legacy"));queryParams.putAll(params);request.setParams(queryParams);
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{
                    var resolution=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");
                    switch(resolution.getContextHandlerMeta().getInvokeHolder().getName()){
                        case "emotions"->controller.emotions(context);case "options"->controller.options(context);case "groups"->controller.groups(context);case "items"->controller.items(context);default->throw new AssertionError();
                    }
                });c.handle();return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();}
    }
    interface Action {void run()throws Exception;}
    static void error(int status,Action action)throws Exception{try{action.run();Assert.fail("Expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            Assert.assertEquals(f.query.emotions("1").getJSONArray("emotions").toList(),List.of("smile","joy"));
            Assert.assertEquals(f.query.emotions("2").getJSONArray("emotions").toList(),List.of("sob"));
            Assert.assertEquals(f.query.emotions("3").getJSONArray("emotions").toList(),Arrays.asList(ApiV3EmotionOptions.DEFAULTS.split(",")));
            var result=f.query.groups("1",1,1);Assert.assertEquals(result.getJSONArray("list").length(),1);Assert.assertEquals(result.getJSONObject("pagination").getLong("total"),2L);
            Assert.assertEquals(result.getJSONArray("list").getJSONObject(0).keySet(),Set.of("id","name","sort","type"));
            Assert.assertEquals(result.getJSONArray("list").getJSONObject(0).getString("id"),"10");Assert.assertEquals(f.query.groups("1",2,1).getJSONArray("list").getJSONObject(0).getString("id"),"11");
            Assert.assertTrue(f.query.groups("1",3,1).getJSONArray("list").isEmpty());Assert.assertTrue(f.query.groups("3",1,20).getJSONArray("list").isEmpty());
            Assert.assertEquals(f.selectSQL("SELECT COUNT(*) AS total FROM x_emoji_group").getFirst().getInt("total"),3);
            var list=f.query.items("1","10",1,20);Assert.assertEquals(list.getJSONObject("pagination").getLong("total"),2L);
            Assert.assertEquals(list.getJSONArray("list").getJSONObject(0).keySet(),Set.of("id","emojiId","name","sort","url"));
            Assert.assertEquals(list.getJSONArray("list").getJSONObject(0).getString("url"),"https://example.test/a.png?x=1&y=2");Assert.assertEquals(list.getJSONArray("list").getJSONObject(1).getString("url"),"");
            Assert.assertEquals(f.query.items("1","10",2,1).getJSONArray("list").getJSONObject(0).getString("id"),"31");
            error(404,()->f.query.items("1","12",1,20));error(404,()->f.query.items("1","99",1,20));error(404,()->f.query.groups("4",1,20));error(404,()->f.query.emotions("99"));
            for(String id:Arrays.asList(null,"","01","1' OR 1=1","1".repeat(33)))error(422,()->f.query.emotions(id));
            error(422,()->f.query.groups("1",0,20));error(422,()->f.query.items("1","10",1,51));error(422,()->f.query.groups("1",Integer.MAX_VALUE,20));
            f.sql("UPDATE x_user SET userStatus=4 WHERE oId='1'");error(404,()->f.query.emotions("1"));error(404,()->f.query.items("1","10",1,20));
        }
        System.out.println("PASS actual prefixed SELECTs: current active owner, default favorites, read-only empty groups, pagination, stable ordering, foreign/missing group rejection, missing/deleted emoji filtering and response field allowlists");
        try(var f=new Fixture()) {
            for(String url:List.of("javascript:alert(1)","data:text/html,hi","//example.test/x","https://user:pass@example.test/x","https://example.test/\\x","https://example.test/\nx","x".repeat(2049))){f.sql("UPDATE x_emoji SET emojiUrl=? WHERE oId='20'",url);Assert.assertEquals(f.query.items("1","10",1,20).getJSONArray("list").getJSONObject(0).getString("url"),"");}
            f.sql("UPDATE x_emoji_group SET emojiGroupName=? WHERE oId='10'","x".repeat(65));f.sql("UPDATE x_emoji_group_item SET emojiGroupItemName=? WHERE oId='30'","x".repeat(256));
            Assert.assertEquals(f.query.groups("1",1,20).getJSONArray("list").getJSONObject(0).getString("name"),"");Assert.assertEquals(f.query.items("1","10",1,20).getJSONArray("list").getJSONObject(0).getString("name"),"");
            f.sql("UPDATE x_emotion SET emotionContent=? WHERE oId='1'","x".repeat(65));f.sql("UPDATE x_emotion SET emotionContent=? WHERE oId='2'","bad\nvalue");Assert.assertTrue(f.query.emotions("1").getJSONArray("emotions").isEmpty());
            for(int i=0;i<4095;i++)f.sql("INSERT INTO x_emotion VALUES(?,'1',0,0,'smile')","n"+i);error(503,()->f.query.emotions("1"));
        }
        System.out.println("PASS hostile stored URLs and oversized/control-character names/favorites bounded without remote fetching; excessive stored favorites fail closed");
        try(var f=new Fixture()) {
            EmotionRouteBindings.register(f.controller,f.auth);Dispatcher.mapping();
            final List<String> paths=List.of("/api/v3/me/settings/emotions","/api/v3/me/settings/emotion-options","/api/v3/me/settings/emoji-groups","/api/v3/me/settings/emoji-groups/10/emojis");
            for(String path:paths){for(String token:Arrays.asList(null,"invalid","wrong")){int before=f.reads;var c=f.http(token,path);Assert.assertEquals(c.attr("test.status"),token==null||token.equals("invalid")?401:403);Assert.assertEquals(f.reads,before);}var c=f.http("reader",path);Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(c.getResponse().getHeader("Vary"),"Authorization");}
            Assert.assertEquals(((JSONObject)f.http("reader",paths.getFirst()).attr("test.data")).getJSONArray("emotions").toList(),List.of("smile","joy"));
            Assert.assertEquals(((JSONObject)f.http("other",paths.getFirst()).attr("test.data")).getJSONArray("emotions").toList(),List.of("sob"));
            Assert.assertEquals(f.http("other",paths.getLast()).attr("test.status"),404);
            Assert.assertEquals(f.http("reader",paths.get(2),Map.of("size","51")).attr("test.status"),422);
            Assert.assertEquals(f.http("reader",paths.getLast(),Map.of("p","0")).attr("test.status"),422);
            Assert.assertEquals(((JSONObject)f.http("reader",paths.getLast(),Map.of("p","2","size","1")).attr("test.data")).getJSONArray("list").getJSONObject(0).getString("id"),"31");
            Assert.assertEquals(f.http("reader","/api/v3/me/settings/emoji-groups/not-an-id/emojis").attr("test.status"),422);
            f.fail=true;var c=f.http("reader",paths.getFirst());Assert.assertEquals(c.attr("test.status"),503);Assert.assertEquals(c.attr("test.code"),"temporarily_unavailable");Assert.assertNull(c.attr("test.data"));
        }
        System.out.println("PASS all four actual Dispatcher/auth/controller routes require profile.read Bearer, ignore forged Cookie/apiKey/userId, no-store responses, ownership and sanitized DB failure");
        try(var f=new Fixture()) {
            f.moveOnCount=true;
            var data=f.query.items("1","10",1,20);Assert.assertTrue(data.getJSONArray("list").isEmpty());Assert.assertEquals(data.getJSONObject("pagination").getLong("total"),0L);
            f.sql("UPDATE x_emoji_group SET emojiGroupUserId='1' WHERE oId='10'");
            f.failAt=f.reads+3;error(503,()->f.query.items("1","10",1,20));
            f.failAt=f.reads+2;error(503,()->f.query.groups("1",1,20));
        }
        System.out.println("PASS group reassignment between ownership and data SELECT cannot disclose entries; count-stage failures report 503");
        var first=ApiV3EmotionOptions.legacyGroups();String original=first[0][0];first[0][0]="MUTATED";Assert.assertEquals(ApiV3EmotionOptions.legacyGroups()[0][0],original);
        var options=ApiV3EmotionOptions.options();Assert.assertEquals(options.keySet(),Set.of("groups","defaults"));Assert.assertFalse(options.toString().contains("endOfEmoji"));Assert.assertFalse(options.toString().contains("MUTATED"));
        options.getJSONArray("defaults").put("MUTATED");Assert.assertFalse(ApiV3EmotionOptions.options().toString().contains("MUTATED"));
        System.out.println("PASS original picker catalog/defaults remain shared and defensive copies prevent nested array/JSON mutations");
    }
}
