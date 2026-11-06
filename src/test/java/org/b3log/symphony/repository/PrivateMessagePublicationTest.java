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
package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.PrivateMessageReadStateTest.reject;

/** Actual publication SQL/savepoint, mixed old event projection, HTTP and strict idempotency. */
public class PrivateMessagePublicationTest {
    static class Publication extends PrivateMessageReadStateTest.Reads {
        final String info,listing;long next=1_000_000_000_000L;int delivered;
        final PrivateMessageListStateService projection=new PrivateMessageListStateService();
        final PrivateMessagePublicationService publication=new PrivateMessagePublicationService(){@Override protected String newId(){return Long.toString(++next);}@Override protected String time(){return "2026-10-20 12:34:56";}};
        final ApiV3PrivateMessagePublicationService sending=new ApiV3PrivateMessagePublicationService(){@Override protected void afterPublished(String actor,String peer){Assert.assertFalse(active());Assert.assertNull(org.b3log.latke.repository.jdbc.JdbcRepository.TX.get());Assert.assertEquals(actor,"1");delivered++;}};
        final ChatListService legacy=new ChatListService();
        Publication()throws Exception{this("");}
        Publication(String prefix)throws Exception{
            super(prefix);info=prefix+"private_info";listing=prefix+"private_list";
            String migration=java.nio.file.Files.readString(java.nio.file.Path.of("sql/20261020_api_v3_private_withdrawal.sql"));int at=migration.indexOf("'ALTER TABLE")+1;Assert.assertTrue(at>0);var ddl=new StringBuilder();
            for(int i=at;i<migration.length();i++){char c=migration.charAt(i);if(c=='\''){if(i+1<migration.length()&&migration.charAt(i+1)=='\''){ddl.append('\'');i++;continue;}break;}ddl.append(c);}
            exec(writer,ddl.toString().replace("`symphony_chat_unread`",table));
            Assert.assertTrue(query(reader,"SELECT messageId FROM "+table).stream().allMatch(row->row.getString("messageId").isEmpty()));
            org.sqlite.Function.create(writer,"substring_index",new org.sqlite.Function(){@Override protected void xFunc()throws java.sql.SQLException{
                String text=value_text(0),delimiter=value_text(1);int n=value_int(2);String[] pieces=text.split(java.util.regex.Pattern.quote(delimiter),-1);
                result(n==0?"":String.join(delimiter,n>0?Arrays.copyOfRange(pieces,0,Math.min(n,pieces.length)):Arrays.copyOfRange(pieces,Math.max(0,pieces.length+n),pieces.length)));
            }});
            exec(writer,"INSERT INTO user VALUES('2',0),('3',4),('4',2)");
            exec(writer,"CREATE TABLE "+info+"(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,user_session TEXT,time TEXT,content TEXT)");
            exec(writer,"CREATE TABLE "+listing+"(oId TEXT PRIMARY KEY,sessionId TEXT,time TEXT,lastMessageId TEXT)");
            exec(writer,"INSERT INTO "+info+" VALUES('100','1','2','1_2','2026-10-19 12:34:56','old private body')");
            final var owners=new UserRepository(){@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql,args);}};
            final var messages=new ChatInfoRepository(){@Override public String getName(){return info;}@Override public boolean hasTransactionBegun(){return active();}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql,args);}};
            final var lists=new ChatListRepository(){
                @Override public String getName(){return listing;}@Override public boolean hasTransactionBegun(){return active();}
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{if(faults.contains("list.read"))throw new RepositoryException("PRIVATE_LIST_ERROR");return query(writer,sql,args);}
            };
            for(var entry:Map.<String,Object>of("users",owners,"messages",messages,"lists",lists,"revisions",revisions).entrySet())inject(projection,PrivateMessageListStateService.class,entry.getKey(),entry.getValue());
            inject(publication,PrivateMessagePublicationService.class,"revisions",revisions);
            inject(publication,PrivateMessagePublicationService.class,"users",owners);inject(publication,PrivateMessagePublicationService.class,"messages",messages);inject(publication,PrivateMessagePublicationService.class,"lists",projection);
            inject(publication,PrivateMessagePublicationService.class,"unread",new ChatUnreadRepository(){@Override public String getName(){return table;}@Override public boolean hasTransactionBegun(){return active();}});
            final var idField=ApiV3PrivateMessageStateService.class.getDeclaredField("idempotency");idField.setAccessible(true);
            inject(sending,ApiV3PrivateMessagePublicationService.class,"idempotency",idField.get(service));inject(sending,ApiV3PrivateMessagePublicationService.class,"publication",publication);
            inject(controller,ApiV3PrivateMessageProcessor.class,"publication",sending);inject(legacy,ChatListService.class,"projection",projection);inject(legacy,ChatListService.class,"chatListRepository",lists);
        }
        @Override void begin()throws Exception {
            super.begin();
            if(faults.remove("commit.lost")){
                final var original=tx.getConnection();
                final var wrapped=(java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Connection.class},(proxy,method,args)->{
                    try{Object value=method.invoke(original,args);if(method.getName().equals("commit"))throw new java.sql.SQLException("lost successful commit acknowledgement");return value;}
                    catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
                var field=org.b3log.latke.repository.jdbc.JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,wrapped);
            }
        }
        JSONObject send(String peer,String body,String key)throws Exception{return sending.send(new JSONObject().put("oId","1"),peer,body,key);}
        String all()throws Exception{return data()+query(reader,"SELECT * FROM private_revision ORDER BY oId")+query(reader,"SELECT * FROM "+info+" ORDER BY oId")+query(reader,"SELECT * FROM "+listing+" ORDER BY oId");}
        JSONObject http(String peer,String bearer,String body,String key,String query,String type,boolean routes)throws Exception {
            final String path="/api/v3/me/conversations/"+peer+"/messages";
            final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,path+(query==null?"":"?"+query));
            try{
                raw.headers().set("Cookie","legacy");if(bearer!=null)raw.headers().set("Authorization",bearer);if(key!=null)raw.headers().set("Idempotency-Key",key);if(type!=null)raw.headers().set("Content-Type",type);
                var request=new Request(null,raw){@Override public String getRemoteAddr(){return "192.0.2.151";}};raw.content().clear().writeBytes(body.getBytes(StandardCharsets.UTF_8));request.setBytes(body.getBytes(StandardCharsets.UTF_8));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.pathVars(new HashMap<>());c.pathVar("peerId",peer);c.attr("user",new JSONObject().put("oId","2"));
                if(routes){
                    PrivateReadRouteBindings.register(controller,auth,new ApiV3WriteMidware());final var registry=Dispatcher.class.getDeclaredField("routerGroups");registry.setAccessible(true);boolean found=false;
                    for(Object group:(List<?>)registry.get(null))for(Object route:(List<?>)PrivateMessageReadStateTest.field(group,"routers")){
                        if(!((List<?>)PrivateMessageReadStateTest.field(route,"uriTemplates")).equals(List.of("/api/v3/me/conversations/{peerId}/messages")))continue;
                        @SuppressWarnings("unchecked") final var chain=new ArrayList<org.b3log.latke.http.function.Handler>((List<org.b3log.latke.http.function.Handler>)PrivateMessageReadStateTest.field(group,"middlewares"));Assert.assertEquals(chain.size(),2);chain.add((org.b3log.latke.http.function.Handler)PrivateMessageReadStateTest.field(route,"handler"));c.addHandlers(chain);found=true;
                    }Assert.assertTrue(found);c.handle();
                }else{c.addHandler(new ApiV3WriteMidware()::handle);c.addHandler(controller::send);auth.messagesWrite(c);}
                final var result=new JSONObject().put("status",c.attr("test.status"));if(c.attr("test.data")!=null)result.put("data",c.attr("test.data"));if(result.getInt("status")==200)Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");return result;
            }finally{raw.release();}
        }
    }
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Publication()){
            String before=f.all();try{f.publication.publishInCurrentTransaction("1","2","body");Assert.fail();}catch(IllegalStateException expected){}Assert.assertEquals(f.all(),before);
            ReservedWords.filter=body->body.replace("blocked","filtered");final var first=f.send("2","  **hello** blocked 😀  ","first");ReservedWords.filter=body->body;
            Assert.assertEquals(first.keySet(),Set.of("id","peerId","sentAt"));Assert.assertEquals(first.getString("peerId"),"2");Assert.assertEquals(f.delivered,1);
            final var stored=query(f.reader,"SELECT * FROM "+f.info+" WHERE oId=?",first.getString("id")).getFirst();Assert.assertEquals(stored.getString("content"),"**hello** filtered 😀");Assert.assertEquals(stored.getString("fromId"),"1");Assert.assertEquals(stored.getString("toId"),"2");Assert.assertEquals(stored.getString("user_session"),"1_2");
            Assert.assertEquals(query(f.reader,"SELECT * FROM "+f.table+" WHERE oId=?",Long.toString(f.next)).size(),1);Assert.assertEquals(query(f.reader,"SELECT messageId FROM "+f.table+" WHERE oId=?",Long.toString(f.next)).getFirst().getString("messageId"),first.getString("id"));Assert.assertEquals(query(f.reader,"SELECT lastMessageId FROM "+f.listing).getFirst().getString("lastMessageId"),first.getString("id"));
            final String committed=f.all();Assert.assertEquals(f.send("2","  **hello** blocked 😀  ","first").toString(),first.toString());Assert.assertEquals(f.all(),committed);Assert.assertEquals(f.delivered,2);reject(()->f.send("2","changed","first"),409);
            final var receipt=query(f.reader,"SELECT * FROM requests").getFirst().toString();Assert.assertFalse(receipt.contains("hello"));Assert.assertFalse(receipt.contains("blocked"));
        }
        System.out.println("PASS private publication atomically stores trimmed reserved-word-filtered Markdown, unread row and canonical old list head; idempotent retry does not resend and cache eviction safely repeats and durable receipt contains IDs/time but no private body");
        for(String peer:List.of("3","4","9"))try(var f=new Publication()){String before=f.all();reject(()->f.send(peer,"body","invalid"),404);Assert.assertEquals(f.all(),before);Assert.assertEquals(f.delivered,0);}
        try(var f=new Publication()){f.send(ApiV3PrivateMessages.FILE_TRANSFER_ID,"file body","file");Assert.assertEquals(query(f.reader,"SELECT toId FROM "+f.info+" ORDER BY oId DESC LIMIT 1").getFirst().getString("toId"),ApiV3PrivateMessages.FILE_TRANSFER_ID);}
        try(var f=new Publication()){exec(f.writer,"UPDATE user SET userStatus=4 WHERE oId='1'");String before=f.all();reject(()->f.send("2","body","inactive"),404);Assert.assertEquals(f.all(),before);}
        System.out.println("PASS current sorted user locks require an activated sender and activated other recipient, reject nonexistent/pending/disabled targets, and preserve the explicitly approved builtin file-transfer destination");
        for(String fault:List.of("message","unread","list","list.read","receipt","commit"))try(var f=new Publication()){
            String before=f.all();if(Set.of("message","unread","list").contains(fault)){String table=fault.equals("message")?f.info:fault.equals("unread")?f.table:f.listing;exec(f.writer,"CREATE TRIGGER reject_private_insert BEFORE INSERT ON "+table+" BEGIN SELECT RAISE(ABORT,'PRIVATE_SQL_BODY'); END");}else f.faults.add(fault);
            reject(()->f.send("2","PRIVATE_CONTENT","fault"),503);Assert.assertEquals(f.all(),before,fault);Assert.assertTrue(query(f.reader,"SELECT * FROM requests").isEmpty());Assert.assertEquals(f.delivered,0);Assert.assertFalse(f.active());
        }
        try(var f=new Publication()){
            String before=f.all();f.begin();exec(f.writer,"INSERT INTO unrelated VALUES('earlier')");exec(f.writer,"CREATE TRIGGER reject_private_insert BEFORE INSERT ON "+f.table+" BEGIN SELECT RAISE(ABORT,'PRIVATE_SQL_BODY'); END");
            try{f.publication.publishInCurrentTransaction("1","2","body");Assert.fail();}catch(RepositoryException expected){}f.commit();Assert.assertEquals(f.all(),before);Assert.assertEquals(query(f.reader,"SELECT * FROM unrelated").size(),1);
        }
        System.out.println("PASS message/unread/list/receipt/current-read/strict-commit faults undo every private write and suppress effects; publication savepoint also prevents partial messages when an owner catches the error and commits unrelated work");
        try(var f=new Publication("custom_")){
            final var first=f.send("2","newest","first");f.legacy.update(new JSONObject().put("oId","100").put("user_session","forged_session").put("time","bad"));
            Assert.assertEquals(query(f.reader,"SELECT lastMessageId FROM "+f.listing).getFirst().getString("lastMessageId"),first.getString("id"));Assert.assertEquals(query(f.reader,"SELECT * FROM "+f.listing).size(),1);
            Assert.assertEquals(f.legacy.getChatList("1").size(),1);Assert.assertEquals(f.legacy.getChatList("2").getFirst().getString("lastMessageId"),first.getString("id"));Assert.assertTrue(f.legacy.getChatList("3").isEmpty());Assert.assertTrue(f.legacy.getChatList("1' OR 1=1 --").isEmpty());
            exec(f.writer,"INSERT INTO "+f.listing+" VALUES('duplicate','1_2','bad','100')");f.send("2","latest","second");final var heads=query(f.reader,"SELECT DISTINCT lastMessageId FROM "+f.listing);Assert.assertEquals(heads.size(),1);
        }
        System.out.println("PASS actual old ChatListService adapter shares strict projection and recomputes the current head rather than regressing on delayed events; custom prefixes and preexisting duplicate session rows preserve a consistent latest pointer");
        try(var f=new Publication()){
            for(String bad:List.of(""," ","x".repeat(10241),"nul\0","\uD800"))reject(()->f.send("2",bad,"invalid"),422);
            reject(()->f.send("1","body","self"),422);reject(()->f.send("2","body",null),422);
            ReservedWords.filter=body->"x".repeat(10241);try{reject(()->f.send("2","body","expanded"),422);}finally{ReservedWords.filter=body->body;}
            f.send("2","x".repeat(10240),"maximum");Assert.assertEquals(query(f.reader,"SELECT LENGTH(content) AS total FROM "+f.info+" ORDER BY oId DESC LIMIT 1").getFirst().getInt("total"),10240);
        }
        System.out.println("PASS existing 10240 UTF-16 Markdown bound is checked before and after filtering, blank/NUL/malformed Unicode/self/missing-key input is rejected and the full limit persists without truncation");
        try(var f=new Publication()){
            final String body=new JSONObject().put("content","**private**").toString();String before=f.all();
            for(String bearer:Arrays.asList(null,"Basic ignored","Bearer invalid"))Assert.assertEquals(f.http("2",bearer,body,"http",null,"application/json",false).getInt("status"),401);
            f.scope="messages.read content.write";Assert.assertEquals(f.http("2","Bearer VALID",body,"http",null,"application/json",false).getInt("status"),403);f.scope="messages.write";
            Assert.assertEquals(f.http("2","Bearer VALID","{\"content\":\"body\",\"fromId\":\"2\"}","http",null,"application/json",false).getInt("status"),422);
            Assert.assertEquals(f.http("2","Bearer VALID",body,"http","apiKey=ignored","application/json",false).getInt("status"),422);
            Assert.assertEquals(f.http("2","Bearer VALID",body,"http",null,"text/plain",false).getInt("status"),415);
            Assert.assertEquals(f.http("2","Bearer VALID"," ".repeat(65537),"http",null,"application/json",false).getInt("status"),413);Assert.assertEquals(f.all(),before);
            final var response=f.http("2","Bearer VALID",body,"http",null,"application/json",true);Assert.assertEquals(response.getInt("status"),200);Assert.assertEquals(response.getJSONObject("data").getString("peerId"),"2");Assert.assertEquals(f.delivered,1);
        }
        System.out.println("PASS actual production POST declaration, Bearer scope, write budget and HTTP controller reject ambient identity/forged author/query/invalid media/oversize bodies and return an ID-only committed send receipt");
        try(var f=new Publication()){
            f.faults.add("commit.lost");reject(()->f.send("2","lost-ack-body","lost-ack"),503);Assert.assertEquals(f.delivered,0);
            String committed=f.all();final var recovered=f.send("2","lost-ack-body","lost-ack");Assert.assertEquals(f.all(),committed);Assert.assertEquals(f.delivered,1);
            Assert.assertEquals(query(f.reader,"SELECT * FROM "+f.info+" WHERE oId=?",recovered.getString("id")).size(),1);Assert.assertEquals(query(f.reader,"SELECT * FROM requests").size(),1);
        }
        try(var f=new Publication()){
            var sent=f.send("2","only-current","last");f.begin();exec(f.writer,"DELETE FROM "+f.info);f.projection.refreshInCurrentTransaction("1","2");Assert.assertTrue(query(f.writer,"SELECT * FROM "+f.listing).isEmpty());f.rollback();Assert.assertEquals(query(f.reader,"SELECT lastMessageId FROM "+f.listing).getFirst().getString("lastMessageId"),sent.getString("id"));
            f.atomic(()->{exec(f.writer,"DELETE FROM "+f.info);f.projection.refreshInCurrentTransaction("1","2");});Assert.assertTrue(query(f.reader,"SELECT * FROM "+f.listing).isEmpty());
        }
        System.out.println("PASS actual successful-commit acknowledgement loss recovers the same linked message receipt and repairs cache eviction on retry; deleting a pair's last message removes its list projection only with the owner commit");
        try(var f=new Publication()){
            int path=0;
            for(var row:List.of(LegacyPrivateUnreadBindings.record0("1","2","1_2","2000"),LegacyPrivateUnreadBindings.record1("1","2","1_2","2001"),LegacyPrivateUnreadBindings.record2("1","2","1_2","2002"))){
                Assert.assertEquals(row.getString("messageId"),Integer.toString(2000+path));final String id=Integer.toString(3000+path++);
                f.atomic(()->ApiV3TransactionStatements.execute("INSERT INTO `"+f.table+"` (oId,fromId,toId,user_session,messageId) VALUES(?,?,?,?,?)",id,row.getString("fromId"),row.getString("toId"),row.getString("user_session"),row.getString("messageId")));
                final var projected=LegacyPrivateUnreadBindings.publicRecord(new JSONObject(row.toString()));Assert.assertFalse(projected.has("messageId"));Assert.assertEquals(projected.keySet(),Set.of("fromId","toId","user_session"));
            }
            Assert.assertEquals(query(f.reader,"SELECT messageId FROM "+f.table+" WHERE oId IN ('3000','3001','3002') ORDER BY oId").stream().map(row->row.getString("messageId")).toList(),List.of("2000","2001","2002"));
        }
        System.out.println("PASS all three actual legacy unread construction fragments preserve the new message association with migrated empty legacy defaults, while the actual old response projection removes that internal field and keeps normal keys");
        final var logged=new ArrayList<String>();final var appender=new org.apache.logging.log4j.core.appender.AbstractAppender("private-write-log",null,null,false,null){@Override public void append(org.apache.logging.log4j.core.LogEvent event){logged.add(event.getMessage().getFormattedMessage()+(event.getThrown()==null?"":event.getThrown().toString()));}};
        appender.start();final var logField=ApiV3PrivateMessagePublicationService.class.getDeclaredField("LOGGER");logField.setAccessible(true);final var logger=(org.apache.logging.log4j.core.Logger)logField.get(null);
        final var context=logger.getContext();final var configuration=context.getConfiguration();final String loggerName=logger.getName();final var old=configuration.getLoggers().get(loggerName);
        final var capture=new org.apache.logging.log4j.core.config.LoggerConfig(loggerName,org.apache.logging.log4j.Level.ALL,false);capture.addAppender(appender,org.apache.logging.log4j.Level.ALL,null);configuration.addLogger(loggerName,capture);context.updateLoggers();
        try(var f=new Publication()){
            f.faults.add("list.read");reject(()->f.send("2","PRIVATE_CONTENT_MARKER","log"),503);
            Assert.assertFalse(logged.isEmpty());final String messages=String.join("\n",logged);for(String secret:List.of("PRIVATE_CONTENT_MARKER","PRIVATE_LIST_ERROR","SQLException"))Assert.assertFalse(messages.contains(secret),secret);
        }finally{configuration.removeLogger(loggerName);if(old!=null)configuration.addLogger(loggerName,old);context.updateLoggers();appender.stop();}
        System.out.println("PASS actual private publication logger emits a generic availability event without private content, raw driver error or exception text");

    }
}
