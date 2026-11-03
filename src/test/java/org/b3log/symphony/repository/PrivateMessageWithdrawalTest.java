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

/** Actual sender-only removal, linked unread cleanup, list projection, strict replay and HTTP. */
public class PrivateMessageWithdrawalTest {
    static Object field(Object target,Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static class Withdrawal extends PrivateMessagePublicationTest.Publication {
        final PrivateMessageWithdrawalService removal=new PrivateMessageWithdrawalService();int refreshed;boolean changeHint;
        final ApiV3PrivateMessageWithdrawalService api=new ApiV3PrivateMessageWithdrawalService(){@Override protected void afterRemoved(String actor,String peer){Assert.assertFalse(active());Assert.assertNull(org.b3log.latke.repository.jdbc.JdbcRepository.TX.get());refreshed++;}};
        Withdrawal()throws Exception{this("");}
        Withdrawal(String prefix)throws Exception {
            super(prefix);inject(removal,PrivateMessageWithdrawalService.class,"revisions",revisions);inject(removal,PrivateMessageWithdrawalService.class,"users",field(projection,PrivateMessageListStateService.class,"users"));inject(removal,PrivateMessageWithdrawalService.class,"lists",projection);
            inject(removal,PrivateMessageWithdrawalService.class,"messages",new ChatInfoRepository(){
                @Override public String getName(){return info;}@Override public boolean hasTransactionBegun(){return active();}
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{
                    var result=query(writer,sql,args);if(changeHint&&!sql.contains("FOR UPDATE")){changeHint=false;exec(writer,"UPDATE "+info+" SET toId='3' WHERE oId=?",args[0]);}return result;
                }
            });
            inject(removal,PrivateMessageWithdrawalService.class,"unread",new ChatUnreadRepository(){@Override public String getName(){return table;}});
            inject(api,ApiV3PrivateMessageWithdrawalService.class,"withdrawal",removal);inject(api,ApiV3PrivateMessageWithdrawalService.class,"idempotency",field(service,ApiV3PrivateMessageStateService.class,"idempotency"));inject(controller,ApiV3PrivateMessageProcessor.class,"withdrawal",api);
        }
        JSONObject remove(String actor,String id,String key)throws Exception{return api.remove(new JSONObject().put("oId",actor),id,key);}
        JSONObject http(String id,String token,String body,String key,String query,String type)throws Exception {
            final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.DELETE,"/api/v3/me/messages/"+id+(query==null?"":"?"+query));
            try{
                raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization",token);if(type!=null)raw.headers().set("Content-Type",type);if(key!=null)raw.headers().set("Idempotency-Key",key);
                final var req=new Request(null,raw){@Override public String getRemoteAddr(){return "192.0.2.153";}};raw.content().clear().writeBytes(body.getBytes(StandardCharsets.UTF_8));req.setBytes(body.getBytes(StandardCharsets.UTF_8));
                final var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.pathVars(new HashMap<>());c.pathVar("messageId",id);c.attr("user",new JSONObject().put("oId","2"));c.addHandler(new ApiV3WriteMidware()::handle);c.addHandler(controller::withdraw);auth.messagesWrite(c);
                final var out=new JSONObject().put("status",c.attr("test.status"));if(c.attr("test.data")!=null)out.put("data",c.attr("test.data"));if(out.getInt("status")==200)Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");return out;
            }finally{raw.release();}
        }
    }
    static JSONObject legacy(Withdrawal f,String id,String queryKey,String bodyKey)throws Exception {
        var processor=new ChatProcessor();inject(processor,ChatProcessor.class,"privateWithdrawals",f.removal);
        inject(processor,ChatProcessor.class,"privateMessages",new ApiV3PrivateMessageQueryService(){@Override public JSONObject unread(JSONObject viewer)throws Exception{Assert.assertFalse(f.active());return new JSONObject().put("count",query(f.reader,"SELECT COUNT(*) AS n FROM "+f.table+" WHERE toId=?",viewer.getString("oId")).getFirst().getLong("n"));}});
        final String body=bodyKey==null?"{}":new JSONObject().put("apiKey",bodyKey).toString();
        var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/chat/revoke?oId="+id+(queryKey==null?"":"&apiKey="+queryKey),io.netty.buffer.Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
        try{
            raw.headers().set("Content-Type","application/json");var req=new Request(null,raw);raw.content().clear().writeBytes(body.getBytes(StandardCharsets.UTF_8));req.setBytes(body.getBytes(StandardCharsets.UTF_8));var queryParser=Request.class.getDeclaredMethod("parseQueryStr");queryParser.setAccessible(true);queryParser.invoke(req);var parser=Request.class.getDeclaredMethod("parseForm");parser.setAccessible(true);parser.invoke(req);
            final var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","3"));c.addHandler(processor::revoke);new org.b3log.symphony.processor.middleware.ApiCheckMidware().handle(c);
            return ((org.b3log.latke.http.renderer.JsonRenderer)c.getRenderer()).getJSONObject();
        }finally{raw.release();}
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Withdrawal()){
            String first=f.send("2","first-private-body","send1").getString("id"),second=f.send("2","second-private-body","send2").getString("id");
            exec(f.writer,"INSERT INTO "+f.table+" VALUES('4000','3','1','forged',?),('4001','1','3','forged',?)",first,first);
            final var removed=f.remove("1",first,"remove1");Assert.assertEquals(removed.keySet(),Set.of("id","peerId","removed"));Assert.assertTrue(removed.getBoolean("removed"));Assert.assertEquals(removed.getString("peerId"),"2");Assert.assertEquals(f.refreshed,1);
            Assert.assertTrue(query(f.reader,"SELECT * FROM "+f.info+" WHERE oId=?",first).isEmpty());Assert.assertTrue(query(f.reader,"SELECT * FROM "+f.table+" WHERE messageId=? AND fromId='1' AND toId='2'",first).isEmpty());
            Assert.assertEquals(query(f.reader,"SELECT * FROM "+f.table+" WHERE messageId=?",first).size(),2);Assert.assertEquals(query(f.reader,"SELECT * FROM "+f.table+" WHERE messageId=?",second).size(),1);Assert.assertEquals(query(f.reader,"SELECT * FROM "+f.table+" WHERE oId='103'").size(),1);
            Assert.assertEquals(query(f.reader,"SELECT lastMessageId FROM "+f.listing).getFirst().getString("lastMessageId"),second);
            String committed=f.all();Assert.assertEquals(f.remove("1",first,"remove1").toString(),removed.toString());Assert.assertEquals(f.all(),committed);Assert.assertEquals(f.refreshed,2);reject(()->f.remove("1",first,"other-key"),404);
            f.remove("1",second,"remove2");Assert.assertEquals(query(f.reader,"SELECT lastMessageId FROM "+f.listing).getFirst().getString("lastMessageId"),"100");
            f.remove("1","100","remove-old");Assert.assertTrue(query(f.reader,"SELECT * FROM "+f.listing).isEmpty());Assert.assertEquals(query(f.reader,"SELECT * FROM "+f.table+" WHERE oId='103'").size(),1);
            Assert.assertFalse(query(f.reader,"SELECT * FROM requests WHERE operation LIKE 'private.withdraw:%'").toString().contains("private-body"));
        }
        System.out.println("PASS sender withdrawal deletes only the owned message and exact message/from/to-linked unread, preserves unknown historical and forged cross-peer records, repairs nonlatest/latest/empty list heads atomically and replays an ID-only receipt without another deletion");
        for(String fault:List.of("message","unread","list","receipt","commit","release.after"))try(var f=new Withdrawal()){
            String id=f.send("2","private","send").getString("id"),before=f.all();
            if(Set.of("message","unread","list").contains(fault)){String table=fault.equals("message")?f.info:fault.equals("unread")?f.table:f.listing;String operation=fault.equals("list")?"UPDATE":"DELETE";exec(f.writer,"CREATE TRIGGER deny_withdraw BEFORE "+operation+" ON "+table+" BEGIN SELECT RAISE(ABORT,'PRIVATE_WITHDRAW_SQL'); END");}else f.faults.add(fault);
            reject(()->f.remove("1",id,"fault"),503);Assert.assertEquals(f.all(),before,fault);Assert.assertEquals(f.refreshed,0);Assert.assertEquals(query(f.reader,"SELECT * FROM requests WHERE operation LIKE 'private.withdraw:%'").size(),0);Assert.assertFalse(f.active());
        }
        try(var f=new Withdrawal()){
            String id=f.send("2","private","send").getString("id"),before=f.all();f.begin();exec(f.writer,"INSERT INTO unrelated VALUES('earlier')");exec(f.writer,"CREATE TRIGGER deny_withdraw BEFORE DELETE ON "+f.table+" BEGIN SELECT RAISE(ABORT,'PRIVATE_WITHDRAW_SQL'); END");try{f.removal.removeInCurrentTransaction("1",id);Assert.fail();}catch(RepositoryException expected){}f.commit();Assert.assertEquals(f.all(),before);Assert.assertEquals(query(f.reader,"SELECT * FROM unrelated").size(),1);
        }
        System.out.println("PASS message/unread/list/receipt/savepoint-release/strict-commit faults roll back every withdrawal component, and a caller that catches a local deletion failure cannot commit half a removal or lose unrelated earlier work");
        try(var f=new Withdrawal("custom_")){
            String id=f.send("2","private","send").getString("id"),before=f.all();reject(()->f.remove("2",id,"recipient"),404);reject(()->f.remove("3",id,"outsider"),404);Assert.assertEquals(f.all(),before);
            f.changeHint=true;reject(()->f.remove("1",id,"changed"),409);Assert.assertEquals(f.all(),before);
            exec(f.writer,"UPDATE user SET userStatus=4 WHERE oId='1'");before=f.all();reject(()->f.remove("1",id,"disabled"),404);Assert.assertEquals(f.all(),before);
        }
        for(String peer:List.of("2",ApiV3PrivateMessages.FILE_TRANSFER_ID))try(var f=new Withdrawal()){
            String id=f.send(peer,"private","send").getString("id");if(peer.equals("2"))exec(f.writer,"DELETE FROM user WHERE oId='2'");Assert.assertTrue(f.remove("1",id,"own-history").getBoolean("removed"));
        }
        System.out.println("PASS current sender status and ownership override stale attrs, recipients/outsiders cannot withdraw, a changed peer aborts before extending locks, and missing former recipients or builtin file transfer do not prevent removal of one's own historical message");
        try(var f=new Withdrawal()){
            String id=f.send("2","private","send").getString("id");f.faults.add("commit.lost");reject(()->f.remove("1",id,"lost"),503);Assert.assertEquals(f.refreshed,0);String committed=f.all();Assert.assertTrue(f.remove("1",id,"lost").getBoolean("removed"));Assert.assertEquals(f.all(),committed);Assert.assertEquals(f.refreshed,1);
        }
        try(var f=new Withdrawal()){
            String id=f.send("2","private","send").getString("id");Assert.assertTrue(f.removal.removeLegacy("1",id).getBoolean("removed"));Assert.assertFalse(f.active());Assert.assertEquals(query(f.reader,"SELECT * FROM "+f.table+" WHERE oId='103'").size(),1);
        }
        System.out.println("PASS successful-commit acknowledgement loss recovers the original withdrawal receipt and cache refresh on same-key retry; the prepared legacy root also strictly commits precise cleanup without historical unread loss");
        try(var f=new Withdrawal()){
            String id=f.send("2","private","send").getString("id"),before=f.all();
            for(String token:Arrays.asList(null,"Basic invalid","Bearer invalid"))Assert.assertEquals(f.http(id,token,"{}","http",null,"application/json").getInt("status"),401);
            f.scope="messages.read content.write";Assert.assertEquals(f.http(id,"Bearer VALID","{}","http",null,"application/json").getInt("status"),403);f.scope="messages.write";
            Assert.assertEquals(f.http(id,"Bearer VALID","{\"userId\":\"2\"}","http",null,"application/json").getInt("status"),422);Assert.assertEquals(f.http(id,"Bearer VALID","{}","http","apiKey=ignored","application/json").getInt("status"),422);
            for(String invalid:List.of("0","01","bad","1 OR 1=1"))Assert.assertEquals(f.http(invalid,"Bearer VALID","{}","http",null,"application/json").getInt("status"),422);
            Assert.assertEquals(f.http(id,"Bearer VALID","{}",null,null,"application/json").getInt("status"),422);
            Assert.assertEquals(f.http(id,"Bearer VALID","{}","http",null,"text/plain").getInt("status"),415);Assert.assertEquals(f.http(id,"Bearer VALID"," ".repeat(1025),"http",null,"application/json").getInt("status"),413);Assert.assertEquals(f.all(),before);
            final var result=f.http(id,"Bearer VALID","","http",null,null);Assert.assertEquals(result.getInt("status"),200);Assert.assertEquals(result.getJSONObject("data").getString("id"),id);
        }
        System.out.println("PASS actual DELETE controller and Bearer/write middleware require messages.write and a valid key, reject forged identity/query/nonempty invalid media/oversize input, and return only committed id/peer/removed data");
        ApiProcessor.identities.put("actor",new JSONObject().put("oId","1").put("userName","actor"));ApiProcessor.identities.put("peer",new JSONObject().put("oId","2").put("userName","peer"));
        for(String mode:List.of("query","body","conflict"))try(var f=new Withdrawal()){
            String id=f.send("2","private","send").getString("id");int sent=org.b3log.symphony.processor.channel.ChatChannel.sent;
            Assert.assertEquals(legacy(f,id,mode.equals("body")?null:mode.equals("conflict")?"peer":"actor",mode.equals("query")?null:"actor").get("result"),0,mode);
            final var audit=new JSONObject(org.b3log.symphony.service.LogsService.privateAudit);Assert.assertEquals(audit.keySet(),Set.of("messageId"));Assert.assertEquals(audit.getString("messageId"),id);
            Assert.assertTrue(query(f.reader,"SELECT * FROM "+f.info+" WHERE oId=?",id).isEmpty());Assert.assertEquals(query(f.reader,"SELECT * FROM "+f.table+" WHERE oId='103'").size(),1);Assert.assertEquals(org.b3log.symphony.processor.channel.ChatChannel.sent,sent+1);
            var command=org.b3log.symphony.processor.channel.UserChannel.commands.getLast();Assert.assertEquals(command.getLong("count"),1L);Assert.assertEquals(command.getString("userId"),"2");
        }
        System.out.println("PASS actual old controller and ApiCheck use authenticated query/body API-key identity, honor body precedence over conflicting query/attrs, preserve result=0 and revoke event, retain unknown unread and refresh the committed receiver count");
        try(var f=new Withdrawal()){
            String id=f.send("2","private","send").getString("id"),before=f.all();int sent=org.b3log.symphony.processor.channel.ChatChannel.sent;
            Assert.assertEquals(legacy(f,id,null,null).getString("result"),"Unauthorized");Assert.assertEquals(legacy(f,id,"peer",null).getInt("result"),-1);Assert.assertEquals(f.all(),before);
            f.faults.add("commit");Assert.assertEquals(legacy(f,id,"actor",null).getInt("result"),-1);Assert.assertEquals(f.all(),before);Assert.assertEquals(org.b3log.symphony.processor.channel.ChatChannel.sent,sent);
        }
        try(var f=new Withdrawal()){
            String id=f.send("2","private","send").getString("id");org.b3log.symphony.processor.channel.ChatChannel.fail=true;
            try{Assert.assertEquals(legacy(f,id,"actor",null).getInt("result"),0);Assert.assertTrue(query(f.reader,"SELECT * FROM "+f.info+" WHERE oId=?",id).isEmpty());}finally{org.b3log.symphony.processor.channel.ChatChannel.fail=false;}
        }
        System.out.println("PASS old unauthorized/foreign/strict-commit failure returns no partial deletion or event, and a failed post-commit legacy transport no longer reports a committed withdrawal as failed");
        final var logged=new ArrayList<String>();final var appender=new org.apache.logging.log4j.core.appender.AbstractAppender("private-withdraw-log",null,null,false,null){@Override public void append(org.apache.logging.log4j.core.LogEvent event){logged.add(event.getMessage().getFormattedMessage()+(event.getThrown()==null?"":event.getThrown().toString()));}};appender.start();
        final var logField=ApiV3PrivateMessageWithdrawalService.class.getDeclaredField("LOGGER");logField.setAccessible(true);final var logger=(org.apache.logging.log4j.core.Logger)logField.get(null);final var context=logger.getContext();final var configuration=context.getConfiguration();final String loggerName=logger.getName();final var old=configuration.getLoggers().get(loggerName);
        final var capture=new org.apache.logging.log4j.core.config.LoggerConfig(loggerName,org.apache.logging.log4j.Level.ALL,false);capture.addAppender(appender,org.apache.logging.log4j.Level.ALL,null);configuration.addLogger(loggerName,capture);context.updateLoggers();
        try(var f=new Withdrawal()){
            String id=f.send("2","PRIVATE_CONTENT_MARKER","send").getString("id");exec(f.writer,"CREATE TRIGGER deny_withdraw BEFORE DELETE ON "+f.info+" BEGIN SELECT RAISE(ABORT,'PRIVATE_WITHDRAW_SQL'); END");reject(()->f.remove("1",id,"log"),503);
            Assert.assertFalse(logged.isEmpty());String text=String.join("\n",logged);for(String secret:List.of("PRIVATE_CONTENT_MARKER","PRIVATE_WITHDRAW_SQL","SQLException"))Assert.assertFalse(text.contains(secret),secret);
        }finally{configuration.removeLogger(loggerName);if(old!=null)configuration.addLogger(loggerName,old);context.updateLoggers();appender.stop();}
        System.out.println("PASS actual private withdrawal logger emits a generic availability event with no private body, raw SQL/driver exception or receiver unread state in response/receipt");

    }
}
