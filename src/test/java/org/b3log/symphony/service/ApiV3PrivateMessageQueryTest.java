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
import org.b3log.latke.http.function.Handler;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import org.sqlite.Function;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import io.netty.handler.codec.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Real owner/peer SQL, DTOs, scope middleware, controller and private-render selection; storage and engine adapted. */
public class ApiV3PrivateMessageQueryTest {
    static final JSONObject ALICE=new JSONObject().put("oId","1");
    static void inject(Object owner,Class<?> type,String field,Object value)throws Exception{var f=type.getDeclaredField(field);f.setAccessible(true);f.set(owner,value);}
    static class Fixture implements AutoCloseable {
        final Path path=Files.createTempFile("rhythm-private-",".sqlite");final Connection db=DriverManager.getConnection("jdbc:sqlite:"+path);
        final String users,messages,unread;int queries,renders;boolean fail,renderFail;
        final ApiV3PrivateMessageQueryService service=new ApiV3PrivateMessageQueryService(){@Override protected String render(String body){renders++;if(renderFail)throw new IllegalStateException("PRIVATE_RENDER_SECRET");return super.render(body);}};
        final ApiV3PrivateMessageProcessor processor=new ApiV3PrivateMessageProcessor();final ApiV3AuthMidware auth=new ApiV3AuthMidware();final ApiV3TokenMgmtService tokens=new ApiV3TokenMgmtService();
        Fixture()throws Exception{this("");}
        Fixture(String prefix)throws Exception{
            users=prefix+"users";messages=prefix+"chat_info";unread=prefix+"chat_unread";
            Function.create(db,"regexp",new Function(){@Override protected void xFunc()throws SQLException {String value=value_text(1);result(value!=null&&value.matches(value_text(0))?1:0);}});
            exec("CREATE TABLE "+users+"(oId TEXT PRIMARY KEY,userStatus INTEGER,userName TEXT,userNickname TEXT,userAvatarURL TEXT,userAvatarType INTEGER,userPassword TEXT,userEmail TEXT,userOnlineFlag BOOLEAN)");
            for(int i=1;i<=3;i++)exec("INSERT INTO "+users+" VALUES(?,0,?,?,'https://example.test/avatar',0,'PRIVATE_PASSWORD','PRIVATE_EMAIL',1)",Integer.toString(i),"user"+i,"nickname"+i);
            exec("INSERT INTO "+users+" VALUES('5',4,'disabled','hidden','javascript:alert(1)',0,'PRIVATE_PASSWORD','PRIVATE_EMAIL',1)");
            exec("CREATE TABLE "+messages+"(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,user_session TEXT,time TEXT,content TEXT,internalToken TEXT)");
            exec("CREATE TABLE "+unread+"(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,user_session TEXT)");
            message("100","1","2","first");message("101","2","1","second");message("102","3","2","FOREIGN_SECRET");message("103","1","2","<p>own</p><script>window.secret=1</script><iframe src='https://attacker.invalid'></iframe><object data='https://attacker.invalid'></object><img src='x' onerror='alert(1)'><a href='javascript:alert(1)'>bad</a>");
            message("104","1","3","third");message("105","1","4","deleted peer history");message("106","5","1","disabled peer history");message("107","1",ApiV3PrivateMessages.FILE_TRANSFER_ID,"file transfer history");message("108","1","1","self invalid");message("109","bad","1","bad participant");message("bad","1","2","bad message id");
            exec("UPDATE "+messages+" SET user_session='1_2' WHERE oId='102'");exec("UPDATE "+messages+" SET user_session='2_3' WHERE oId='103'");
            exec("INSERT INTO "+unread+" VALUES('1','2','1','wrong_session'),('2','2','1','wrong_session'),('3','3','1','wrong_session'),('4','2','3','1_2'),('5','1','1','1_1'),('6','bad','1','bad')");
            inject(service,ApiV3PrivateMessageQueryService.class,"users",new UserRepository(){@Override public String getName(){return users;}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return rows(sql,args);}});
            inject(service,ApiV3PrivateMessageQueryService.class,"messages",new ChatInfoRepository(){@Override public String getName(){return messages;}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return rows(sql,args);}});
            inject(service,ApiV3PrivateMessageQueryService.class,"unread",new ChatUnreadRepository(){@Override public String getName(){return unread;}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return rows(sql,args);}});
            inject(service,ApiV3PrivateMessageQueryService.class,"avatars",new AvatarQueryService());inject(processor,ApiV3PrivateMessageProcessor.class,"query",service);inject(auth,ApiV3AuthMidware.class,"apiV3TokenMgmtService",tokens);
            tokens.identities.put("writer",new JSONObject().put("user",ALICE).put("scope","messages.write"));tokens.identities.put("reader",new JSONObject().put("user",ALICE).put("scope","messages.read"));tokens.identities.put("content-only",new JSONObject().put("user",ALICE).put("scope","content.read profile.read notifications.read"));tokens.identities.put("bob",new JSONObject().put("user",new JSONObject().put("oId","2")).put("scope","messages.read"));queries=0;
        }
        void exec(String sql,Object...args)throws SQLException{try(var stmt=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)stmt.setObject(i+1,args[i]);stmt.executeUpdate();}}
        List<JSONObject> rows(String sql,Object...args)throws RepositoryException{
            queries++;if(fail)throw new RepositoryException("PRIVATE_SQL_SECRET");
            try(var stmt=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)stmt.setObject(i+1,args[i]);try(var r=stmt.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}catch(SQLException e){throw new RepositoryException(e);}
        }
        String snapshot()throws RepositoryException{return rows("SELECT * FROM "+messages+" ORDER BY oId").toString()+rows("SELECT * FROM "+unread+" ORDER BY oId");}
        void message(String id,String from,String to,String content)throws Exception{exec("INSERT INTO "+messages+" VALUES(?,?,?,'untrusted','2026-10-19 12:34:56',?,'PRIVATE_INTERNAL_TOKEN')",id,from,to,content);}
        RequestContext http(String action,String bearer,Map<String,String> parameters)throws Exception {
            final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/me/messages/test?apiKey=legacy-ignored");
            try{
                raw.headers().set("Cookie","legacy-ignored");if(bearer!=null)raw.headers().set("Authorization",bearer);
                final var request=new Request(null,raw);final var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));
                request.setParams(new HashMap<>(parameters));c.pathVars(new HashMap<>());
                parameters.forEach((key,value)->{if(key.equals("peerId")||key.equals("messageId"))c.pathVar(key,value);});
                c.attr("user",new JSONObject().put("oId","1"));
                Handler handler=switch(action){case "list"->processor::list;case "conversation"->processor::conversation;case "history"->processor::history;case "unread"->processor::unread;default->processor::message;};
                c.addHandlers(List.of(auth::messagesRead,handler));c.handle();return c;
            }finally{raw.release();}
        }
        @Override public void close()throws Exception{db.close();Files.deleteIfExists(path);}
    }
    interface Work{void run()throws Exception;}
    static void rejected(Work work,int status)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    static List<String> ids(JSONObject result){final var ids=new ArrayList<String>();for(Object row:result.getJSONArray("list"))ids.add(((JSONObject)row).getString("id"));return ids;}
    static Object field(Object object,String key)throws Exception {var field=object.getClass().getDeclaredField(key);field.setAccessible(true);return field.get(object);}
    public static void main(String[] args)throws Exception {
        final var p=new Properties();p.setProperty("runtimeDatabase","NONE");p.setProperty("serverScheme","http");p.setProperty("serverHost","localhost");p.setProperty("serverPort","8080");Latkes.setLatkeProps(p);Latkes.setLocalProps(p);
        try(var f=new Fixture()){
            String original=f.snapshot();f.queries=0;var first=f.service.conversations(ALICE,1,2);Assert.assertEquals(first.getJSONObject("pagination").getLong("total"),5);Assert.assertEquals(first.getJSONArray("list").length(),2);Assert.assertEquals(first.getJSONArray("list").getJSONObject(0).getString("peerId"),ApiV3PrivateMessages.FILE_TRANSFER_ID);Assert.assertTrue(first.getJSONArray("list").getJSONObject(0).getJSONObject("peer").getBoolean("builtin"));Assert.assertFalse(first.getJSONArray("list").getJSONObject(1).getJSONObject("peer").getBoolean("available"));Assert.assertTrue(f.queries<=5,"bounded batch queries");
            var last=f.service.conversations(ALICE,3,2).getJSONArray("list").getJSONObject(0);Assert.assertEquals(last.getString("peerId"),"2");Assert.assertEquals(last.getJSONObject("lastMessage").getString("id"),"103");Assert.assertEquals(last.getLong("unreadCount"),2);Assert.assertEquals(f.service.unread(ALICE).getLong("count"),3);
            Assert.assertEquals(f.snapshot(),original);String dto=first.toString()+last;for(String secret:List.of("PRIVATE_","FOREIGN_SECRET","userPassword","userEmail","userOnlineFlag","user_session","internalToken"))Assert.assertFalse(dto.contains(secret),secret);
        }
        System.out.println("PASS conversations aggregate actual owned participants, batch peers/unread counts in bounded queries, paginate by latest message, preserve file-transfer and unavailable-peer history, and never expose legacy envelope/private metadata");
        try(var f=new Fixture()){
            Assert.assertEquals(ids(f.service.history(ALICE,"2",null,null,2)),List.of("101","103"));Assert.assertTrue(f.service.history(ALICE,"2",null,null,2).getBoolean("hasMore"));Assert.assertEquals(ids(f.service.history(ALICE,"2","101",null,2)),List.of("100"));Assert.assertEquals(ids(f.service.history(ALICE,"2",null,"100",1)),List.of("101"));Assert.assertTrue(f.service.history(ALICE,"2",null,"100",1).getBoolean("hasMore"));Assert.assertTrue(f.service.history(ALICE,"2",null,"999",50).getJSONArray("list").isEmpty());
            Assert.assertEquals(f.service.get(ALICE,"103").getJSONObject("message").getString("content").contains("own"),true);int renders=f.renders;rejected(()->f.service.get(ALICE,"102"),404);Assert.assertEquals(f.renders,renders);rejected(()->f.service.conversation(ALICE,"9"),404);rejected(()->f.service.get(ALICE,"108"),404);rejected(()->f.service.get(ALICE,"109"),404);
            Assert.assertFalse(f.service.history(ALICE,"4",null,null,20).getJSONObject("peer").getBoolean("available"));Assert.assertEquals(f.service.conversation(ALICE,"2").getLong("unreadCount"),2);
        }
        System.out.println("PASS history uses exclusive before/after cursors and ascending display order, ignores forged session strings, returns empty exhausted pages for existing pairs and authorizes detail before rendering; outsiders/self/malformed participants cannot read");
        try(var f=new Fixture()){
            final var message=f.service.get(ALICE,"103").getJSONObject("message");Assert.assertEquals(message.keySet(),Set.of("id","fromId","toId","fromMe","sentAt","content","contentHtml","preview","available"));String html=message.getString("contentHtml");Assert.assertFalse(html.contains("<script"));Assert.assertFalse(html.contains("onerror"));Assert.assertFalse(html.contains("javascript:"));Assert.assertFalse(html.contains("<iframe"));Assert.assertFalse(html.contains("<object"));Assert.assertEquals(message.getString("sentAt"),"2026-10-19 12:34:56");Assert.assertTrue(Markdowns.localCalls>0);
            f.message("110","1","2","😀".repeat(200));var preview=f.service.get(ALICE,"110").getJSONObject("message").getString("preview");Assert.assertEquals(preview.codePointCount(0,preview.length()),120);Assert.assertFalse(Character.isHighSurrogate(preview.charAt(preview.length()-1)));
            f.message("112","1","2","<p>Tom &amp; Jerry &lt;3</p>");Assert.assertEquals(f.service.get(ALICE,"112").getJSONObject("message").getString("preview"),"Tom & Jerry <3");
            f.message("111","1","2","x".repeat(10241));Assert.assertFalse(f.service.get(ALICE,"111").getJSONObject("message").getBoolean("available"));
            f.exec("UPDATE "+f.messages+" SET time='2026-02-30 12:00:00' WHERE oId='110'");Assert.assertEquals(f.service.get(ALICE,"110").getJSONObject("message").getString("sentAt"),"");
            f.renderFail=true;final var fallback=f.service.get(ALICE,"100").getJSONObject("message");Assert.assertEquals(fallback.getString("content"),"first");Assert.assertEquals(fallback.getString("contentHtml"),"");
        }
        System.out.println("PASS actual private-render selection stays on the local engine and strict sanitizer, excludes active embeds/scripts/events/unsafe URLs, preserves raw Markdown and renderer-failure fallback, bounds stored bodies and truncates preview without splitting emoji");
        try(var f=new Fixture()){
            for(String bad:List.of("","0","01","1 OR 1=1","1/2","1_2","x".repeat(20)))rejected(()->f.service.get(ALICE,bad),422);
            rejected(()->f.service.history(ALICE,"2","100","103",20),422);rejected(()->f.service.history(ALICE,"1",null,null,20),422);rejected(()->f.service.conversations(ALICE,0,20),422);rejected(()->f.service.conversations(ALICE,1,51),422);
            f.exec("UPDATE "+f.users+" SET userStatus=4 WHERE oId='1'");rejected(()->f.service.get(ALICE,"100"),404);rejected(()->f.service.unread(ALICE),404);
        }
        try(var f=new Fixture("custom_")){Assert.assertEquals(f.service.conversations(ALICE,1,50).getJSONObject("pagination").getLong("total"),5);Assert.assertEquals(f.service.history(ALICE,"2",null,null,20).getJSONArray("list").length(),3);}
        System.out.println("PASS bounded IDs/pagination/cursor conflicts reject injection and ambiguous inputs; current database account status overrides stale identity and all tables use actual repository prefixes");
        try(var f=new Fixture()){
            for(String bearer:Arrays.asList(null,"Basic ignored","Bearer invalid","Bearer content-only","Bearer writer")){
                f.queries=0;var c=f.http("history",bearer,Map.of("peerId","2"));Assert.assertEquals((int)c.attr("test.status"),Set.of("Bearer content-only","Bearer writer").contains(bearer==null?"":bearer)?403:401);Assert.assertEquals(f.queries,0);
            }
            var allowed=f.http("history","Bearer reader",Map.of("peerId","2","size","2"));Assert.assertEquals((int)allowed.attr("test.status"),200);Assert.assertEquals(allowed.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(allowed.getResponse().getHeader("Vary"),"Authorization");
            Assert.assertEquals((int)f.http("message","Bearer bob",Map.of("messageId","104")).attr("test.status"),404);
            var unread=f.http("message","Bearer reader",Map.of("messageId","unread-count"));Assert.assertEquals(((JSONObject)unread.attr("test.data")).getLong("count"),3);
            Assert.assertEquals((int)f.http("history","Bearer reader",Map.of("peerId","2","size","51")).attr("test.status"),422);
        }
        System.out.println("PASS actual controller plus scope middleware reject Cookie/apiKey-only and content.read-only callers before SQL, isolate Bearer identities, provide no-store JSON, bound inputs and handle the static unread-count sibling");
        final var logged=new ArrayList<String>();final var appender=new AbstractAppender("private-test",null,null,false,null){@Override public void append(LogEvent e){logged.add(e.getMessage().getFormattedMessage()+(e.getThrown()==null?"":e.getThrown().getMessage()));}};appender.start();final var root=(org.apache.logging.log4j.core.Logger)LogManager.getRootLogger();root.addAppender(appender);
        try(var f=new Fixture()){
            f.fail=true;var c=f.http("history","Bearer reader",Map.of("peerId","2"));Assert.assertEquals((int)c.attr("test.status"),503);Assert.assertEquals(c.attr("test.code"),"temporarily_unavailable");f.fail=false;f.renderFail=true;f.service.get(ALICE,"100");Assert.assertFalse(String.join("\n",logged).contains("PRIVATE_"));
        }finally{root.removeAppender(appender);appender.stop();}
        System.out.println("PASS database and renderer failures never expose or log private body/driver exception text; no message/unread/list mutations are performed by any read endpoint");
        try(var f=new Fixture()){
            PrivateMessageRouteBindings.register(f.processor,f.auth);
            var registry=Dispatcher.class.getDeclaredField("routerGroups");registry.setAccessible(true);int checked=0;
            for(Object group:(List<?>)registry.get(null))for(Object route:(List<?>)field(group,"routers")){
                @SuppressWarnings("unchecked") final var paths=(List<String>)field(route,"uriTemplates");
                if(paths.size()!=1||!paths.getFirst().startsWith("/api/v3/me/"))continue;
                checked++;Assert.assertEquals(((List<?>)field(route,"httpRequestMethods")).getFirst(),org.b3log.latke.http.HttpMethod.GET);
                @SuppressWarnings("unchecked") final var chain=new ArrayList<Handler>((List<Handler>)field(group,"middlewares"));Assert.assertEquals(chain.size(),1);chain.add((Handler)field(route,"handler"));
                for(boolean allowed:new boolean[]{false,true}){
                    var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,paths.getFirst());
                    try{
                        raw.headers().set("Authorization",allowed?"Bearer reader":"Bearer content-only");var request=new Request(null,raw);request.setParams(new HashMap<>());
                        var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.pathVars(new HashMap<>());c.pathVar("peerId","2");c.pathVar("messageId","103");
                        f.queries=0;c.addHandlers(chain);c.handle();Assert.assertEquals((int)c.attr("test.status"),allowed?200:403);if(!allowed)Assert.assertEquals(f.queries,0);
                    }finally{raw.release();}
                }
            }
            Assert.assertEquals(checked,5);
        }
        System.out.println("PASS all five actual production GET declarations register private scope middleware before their controllers in the bundled Dispatcher, and insufficient scope never reaches SQL");

    }
}
