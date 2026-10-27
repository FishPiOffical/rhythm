package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;

/** Real endpoints, middleware, group/item SQL, idempotency and strict root with isolated JDBC adapters. */
public class ApiV3EmojiGroupTest extends ApiV3ProfessionPrimaryTest {
    static class GroupFixture extends Fixture {
        final ApiV3EmojiGroupService groupService = new ApiV3EmojiGroupService();
        final ApiV3EmojiGroupProcessor endpoint = new ApiV3EmojiGroupProcessor();
        boolean deactivateOnBegin;
        GroupFixture() throws Exception {
            super();
            sql(observer,"CREATE TABLE emoji_group(oId TEXT PRIMARY KEY,emojiGroupUserId TEXT,emojiGroupName TEXT,emojiGroupSort INT,emojiGroupType INT,emojiGroupCreateTime INT,emojiGroupUpdateTime INT)");
            sql(observer,"CREATE TABLE emoji_group_item(oId TEXT PRIMARY KEY,emojiGroupItemGroupId TEXT,emojiGroupItemEmojiId TEXT,emojiGroupItemName TEXT)");
            sql(observer,"CREATE TABLE emoji(oId TEXT PRIMARY KEY,emojiUrl TEXT)");
            sql(observer,"CREATE TABLE emoji_share(oId TEXT PRIMARY KEY,emojiShareGroupId TEXT,value TEXT)");
            sql(observer,"INSERT INTO emoji_group VALUES('10',?,'first',0,0,1,1),('11',?,'全部',0,1,1,1),('12','2','other',0,0,1,1),('13',?,'broken',0,2,1,1)",actor,actor,actor);
            sql(observer,"INSERT INTO emoji_group_item VALUES('20','10','30','name'),('21','11','30','all-name'),('22','12','30','other-name')");
            sql(observer,"INSERT INTO emoji VALUES('30','https://example.com/a.png')");
            sql(observer,"INSERT INTO emoji_share VALUES('40','10','PRIVATE_SNAPSHOT')");
            var users=new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("user",q,a);}};
            var groups=new EmojiGroupRepository(){public String getName(){return "emoji_group";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("group",q,a);}};
            var items=new EmojiGroupItemRepository(){public String getName(){return "emoji_group_item";}};
            var field=ApiV3ProfessionPrimaryService.class.getDeclaredField("idempotency");field.setAccessible(true);var coordinator=field.get(service);field=ApiV3IdempotencyMgmtService.class.getDeclaredField("requestRepository");field.setAccessible(true);var receipts=field.get(coordinator);
            inject(groupService,"users",users);inject(groupService,"groups",groups);inject(groupService,"items",items);inject(groupService,"requests",receipts);inject(endpoint,"service",groupService);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("writer","other","reader").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":actor)).put("scope",token.equals("reader")?"profile.read":"settings.write"):null;}});
        }
        @Override Transaction begin() {
            try {
                if(deactivateOnBegin){deactivateOnBegin=false;sql(observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",actor);}
                var tx=super.begin();var inner=writer;
                writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                    try {
                        if(m.getName().equals("prepareStatement")) {
                            String q=(String)a[0];var st=(PreparedStatement)m.invoke(inner,a);
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p2,m2,a2)->{
                                try {
                                    var out=m2.invoke(st,a2);
                                    if(m2.getName().equals("executeUpdate")&&q.contains("`emoji_group")) {
                                        mutations++;
                                        if(fault.equals("business")||fault.equals("rollback-point")||(fault.equals("cascade")&&q.startsWith("DELETE FROM `emoji_group`")))throw new SQLException("PRIVATE_WRITE");
                                    }
                                    return out;
                                }catch(InvocationTargetException e){throw e.getCause();}
                            });
                        }
                        return m.invoke(inner,a);
                    }catch(InvocationTargetException e){throw e.getCause();}
                });
                var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(delegate,writer);return tx;
            }catch(Exception e){throw new IllegalStateException(e);}
        }
        void bind() { EmojiGroupRouteBindings.register(endpoint,auth,writes);Dispatcher.mapping(); }
        RequestContext call(String method,String target,String token,String key,String body)throws Exception {
            String uri="/api/v3/me/settings/emoji-groups"+(method.equals("POST")?"":"/"+target);
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.valueOf(method),uri+"?apiKey=legacy&userId=2",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try {
                raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","2001:db8::"+actor);
                if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy","userId","2")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(ctx->{var resolution=(org.b3log.latke.http.handler.RouteResolution)ctx.attr("MATCH_RESULT");Assert.assertEquals(resolution.getContextHandlerMeta().getInvokeHolder().getName(),method.equals("POST")?"create":method.equals("PUT")?"update":"delete");if(method.equals("POST"))endpoint.create(ctx);else if(method.equals("PUT"))endpoint.update(ctx);else endpoint.delete(ctx);});c.handle();return c;
            }finally{raw.release();}
        }
        RequestContext create(String key,String name)throws Exception{return call("POST","","writer",key,new JSONObject().put("name",name).toString());}
        RequestContext update(String key,String name)throws Exception{return call("PUT","10","writer",key,new JSONObject().put("name",name).put("sort",-3).toString());}
        RequestContext delete(String key)throws Exception{return call("DELETE","10","writer",key,"");}
        String groups()throws Exception{return Fixture.rows(observer,"SELECT * FROM emoji_group ORDER BY oId").toString();}
    }
    static JSONObject data(RequestContext c){Assert.assertEquals(c.attr("test.status"),200);return (JSONObject)c.attr("test.data");}
    public static void main(String[]args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new GroupFixture()) {
            f.bind();var out=data(f.create("create","  中文 😀 空格  "));Assert.assertEquals(out.keySet(),Set.of("id","name","sort","type"));Assert.assertEquals(out.getString("name"),"中文 😀 空格");Assert.assertEquals(out.getInt("sort"),0);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group"),5L);
            Assert.assertEquals(data(f.create("create","中文 😀 空格")).getString("id"),out.getString("id"));Assert.assertEquals(f.create("create","different").attr("test.status"),409);
            var changed=data(f.update("update","changed"));Assert.assertEquals(changed.getInt("sort"),-3);data(f.update("newer","latest"));Assert.assertEquals(data(f.update("update","changed")).getString("name"),"changed");Assert.assertEquals(Fixture.rows(f.observer,"SELECT emojiGroupName FROM emoji_group WHERE oId='10'").getFirst().getString("emojiGroupName"),"latest");
            Assert.assertEquals(f.locks.getFirst(),"user");
        }
        System.out.println("PASS 1 actual POST/PUT Dispatcher/auth, normalized name and defaults, signed sort, same-key replay/conflict and replay preserving newer state");
        try(var f=new GroupFixture()) {
            f.bind();data(f.delete("delete"));Assert.assertEquals(data(f.delete("delete")).keySet(),Set.of("id","deleted"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group WHERE oId='10'"),0L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item WHERE emojiGroupItemGroupId='10'"),0L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_share"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),2L);Assert.assertEquals(f.delete("new-delete").attr("test.status"),404);
        }
        try(var f=new GroupFixture()) {f.bind();sql(f.observer,"DELETE FROM emoji_group_item");sql(f.observer,"DELETE FROM emoji_group");data(f.create("no-default","first"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group WHERE emojiGroupType=1"),0L);}
        System.out.println("PASS 2 delete group and its members atomically retains images/other-group members/shares; same-key replay succeeds and create never initializes default group");
        try(var f=new GroupFixture()) {
            f.bind();String before=f.groups();for(String method:List.of("PUT","DELETE"))for(String target:List.of("11","12","99","13"))Assert.assertEquals(f.call(method,target,"writer","blocked-"+method+target,method.equals("PUT")?"{\"name\":\"valid\"}":"").attr("test.status"),target.equals("11")?409:target.equals("13")?503:404);
            Assert.assertEquals(f.create("duplicate","first").attr("test.status"),409);Assert.assertEquals(f.update("default-name","全部").attr("test.status"),409);Assert.assertEquals(f.groups(),before);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        System.out.println("PASS 3 exact owner/type checks, protected default, absent/other/corrupt group and existing names fail without retained receipts");
        for(String method:List.of("POST","PUT","DELETE"))for(String fault:List.of("placeholder","user","group","business","receipt","commit","savepoint","release"))try(var f=new GroupFixture()) {
            f.bind();String before=f.groups();f.fault=fault;Assert.assertEquals(f.call(method,"10","writer","fault",method.equals("DELETE")?"":"{\"name\":\"valid\"}").attr("test.status"),503,method+fault);Assert.assertEquals(f.groups(),before);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),3L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);f.fault="";data(f.call(method,"10","writer","fault",method.equals("DELETE")?"":"{\"name\":\"valid\"}"));
        }
        try(var f=new GroupFixture()){f.bind();f.fault="cascade";Assert.assertEquals(f.delete("cascade").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),3L);}
        System.out.println("PASS 4 real SQL write/read/receipt/savepoint/strict commit and cascade-midpoint failures roll back all state; recovered same-key attempts succeed");
        try(var f=new GroupFixture()) {
            f.bind();for(String method:List.of("POST","PUT","DELETE"))for(String token:Arrays.asList(null,"bad","reader"))Assert.assertEquals(f.call(method,"10",token,"auth",method.equals("DELETE")?"":"{\"name\":\"valid\"}").attr("test.status"),token==null||token.equals("bad")?401:403);
            for(String body:List.of("{}","[]","{\"name\":\"valid\",\"owner\":\"2\"}","{\"name\":\"valid\",\"name\":\"other\"}","{\"name\":\"valid\",\"sort\":1.1}","{\"name\":\"valid\",\"sort\":2147483648}"))Assert.assertNotEquals(f.call("POST","","writer","bad",body).attr("test.status"),200);
            Assert.assertEquals(f.call("DELETE","10","writer","bad","{\"name\":\"valid\"}").attr("test.status"),422);Assert.assertEquals(f.call("POST","","writer",null,"{\"name\":\"valid\"}").attr("test.status"),422);Assert.assertEquals(f.call("POST","","writer","large","中".repeat(6000)).attr("test.status"),413);
        }
        for(String name:List.of(""," ","\u3000","中".repeat(65),"<b>text</b>","<!--comment-->","\nvalid","valid\u0000","\uD800"))error(422,()->ApiV3EmojiGroupInputs.normalize(new JSONObject().put("name",name)));
        Assert.assertEquals(ApiV3EmojiGroupInputs.normalize(new JSONObject().put("name","\u3000 中文 \u3000")).getString("name"),"中文");
        Assert.assertEquals(ApiV3EmojiGroupInputs.normalize(new JSONObject().put("name","😀".repeat(32))).getString("name").length(),64);Assert.assertEquals(ApiV3EmojiGroupInputs.normalize(new JSONObject().put("name","1 < 2 & 3 > 2").put("sort",Integer.MIN_VALUE)).getInt("sort"),Integer.MIN_VALUE);
        System.out.println("PASS 5 Bearer-only scope/owner boundaries, JSON/body/key/typed-sort, control/HTML/Unicode and UTF16 limits reject invalid input while emoji and plain comparison text remain valid");
        try(var f=new GroupFixture()) {
            f.bind();f.fault="lost-ack";Assert.assertEquals(f.create("ack","valid").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group"),5L);f.fault="";data(f.create("ack","valid"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group"),5L);
            data(f.update("ok","first-updated"));sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);Assert.assertEquals(f.update("ok","first-updated").attr("test.status"),404);Assert.assertEquals(f.create("disabled","new").attr("test.status"),404);
        }
        try(var f=new GroupFixture()){f.bind();f.deactivateOnBegin=true;Assert.assertEquals(f.create("race","valid").attr("test.status"),404);Assert.assertEquals(f.mutations,0);}
        System.out.println("PASS 6 lost commit acknowledgement replays one durable group; disabled actors blocked for fresh attempts and replay including auth-to-transaction race");
        try(var f=new GroupFixture()) {
            try{f.groupService.deleteInCurrentTransaction(f.actor,"10");Assert.fail();}catch(IllegalStateException expected){}
            var tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="cascade";try{f.groupService.deleteInCurrentTransaction(f.actor,"10");Assert.fail();}catch(RepositoryException expected){}f.fault="";tx.commit();Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),3L);
            tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=3");f.fault="rollback-point";try{f.groupService.deleteInCurrentTransaction(f.actor,"10");Assert.fail();}catch(RepositoryException expected){}Assert.assertFalse(tx.isActive());Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);
        }
        System.out.println("PASS 7 primitives require root, savepoint preserves unrelated writes and rollback-point failure aborts the whole root");
        System.out.println("Emoji group isolated tests passed: 7 groups");
    }
}
