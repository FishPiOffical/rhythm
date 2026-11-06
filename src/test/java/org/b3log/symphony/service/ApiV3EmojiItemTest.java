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

/** Real member/default/URL/mirror SQL, Dispatcher gates, strict roots and rollback faults. */
public class ApiV3EmojiItemTest extends ApiV3EmojiGroupTest {
    static class ItemFixture extends GroupFixture {
        final ApiV3EmojiItemService members=new ApiV3EmojiItemService();
        final ApiV3EmojiItemProcessor processor=new ApiV3EmojiItemProcessor();
        int itemWrites;
        ItemFixture()throws Exception {
            super();
            sql(observer,"ALTER TABLE emoji_group_item ADD COLUMN emojiGroupItemSort INT DEFAULT 1");sql(observer,"ALTER TABLE emoji_group_item ADD COLUMN emojiGroupItemCreateTime INT DEFAULT 1");
            sql(observer,"DROP TABLE emoji");
            sql(observer,"CREATE TABLE emoji(oId TEXT PRIMARY KEY,emojiUrl TEXT COLLATE NOCASE,emojiStatus INT DEFAULT 0,emojiUploaderId TEXT,emojiCreateTime INT)");
            sql(observer,"INSERT INTO emoji VALUES('30','https://example.com/a.png',0,'2',1),('31','https://example.com/deleted.png',1,'2',1)");
            var users=new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("user",q,a);}};
            var options=new OptionRepository(){public JSONObject getForUpdate(String id)throws RepositoryException{Assert.assertNotNull(JdbcRepository.TX.get());locks.add("namespace");if(fault.equals("namespace"))throw new RepositoryException("PRIVATE_NAMESPACE");return new JSONObject().put("oId",id);}};
            var groups=new EmojiGroupRepository(){public String getName(){return "emoji_group";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("group",q,a);}};
            var items=new EmojiGroupItemRepository(){public String getName(){return "emoji_group_item";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("item",q,a);}};
            var emojis=new EmojiRepository(){public String getName(){return "emoji";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{
                Assert.assertTrue(q.endsWith(" FOR UPDATE"));Assert.assertNotNull(JdbcRepository.TX.get());locks.add("emoji");if(fault.equals("emoji-read"))throw new RepositoryException("PRIVATE_EMOJI");
                // MySQL BINARY exact comparison is executed as UTF8 blob equality, despite NOCASE column collation.
                return Fixture.rows(writer,q.replace("BINARY emojiUrl=BINARY ?","CAST(emojiUrl AS BLOB)=CAST(? AS BLOB)"),a);
            }};
            var f=ApiV3EmojiGroupService.class.getDeclaredField("requests");f.setAccessible(true);var receipts=f.get(groupService);
            inject(members,"users",users);inject(members,"options",options);inject(members,"groups",groups);inject(members,"items",items);inject(members,"emojis",emojis);inject(members,"requests",receipts);inject(processor,"service",members);
        }
        @Override Transaction begin() {
            try {
                var tx=super.begin();var inner=writer;itemWrites=0;
                writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                    try {
                        if(m.getName().equals("prepareStatement")) {
                            String q=(String)a[0];var st=(PreparedStatement)m.invoke(inner,a);
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p2,m2,a2)->{
                                try {
                                    var out=m2.invoke(st,a2);
                                    if(m2.getName().equals("executeUpdate")) {
                                        if(q.contains("`emoji`")&&fault.equals("image-write"))throw new SQLException("PRIVATE_IMAGE");
                                        if(q.contains("`emoji_group_item`")){itemWrites++;if(fault.equals("mirror")&&itemWrites==2)throw new SQLException("PRIVATE_MIRROR");}
                                    }
                                    return out;
                                }catch(InvocationTargetException e){throw e.getCause();}
                            });
                        }
                        return m.invoke(inner,a);
                    }catch(InvocationTargetException e){throw e.getCause();}
                });
                var f=JdbcTransaction.class.getDeclaredField("connection");f.setAccessible(true);f.set(delegate,writer);return tx;
            }catch(Exception e){throw new IllegalStateException(e);}
        }
        @Override void bind(){EmojiItemRouteBindings.register(processor,auth,writes);Dispatcher.mapping();}
        RequestContext memberCall(String method,String group,String item,String token,String key,String body)throws Exception {
            final boolean upload=group.isEmpty();
            String uri=upload?"/api/v3/me/settings/emojis":"/api/v3/me/settings/emoji-groups/"+group+"/emojis"+(method.equals("POST")?"":"/"+item);
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.valueOf(method),uri+"?apiKey=legacy&userId=2",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try {
                raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","2001:db8::"+actor);
                if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy","userId","2")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(ctx->{var resolution=(org.b3log.latke.http.handler.RouteResolution)ctx.attr("MATCH_RESULT");Assert.assertEquals(resolution.getContextHandlerMeta().getInvokeHolder().getName(),method.equals("POST")?(upload?"upload":"add"):method.equals("PUT")?"update":"remove");if(method.equals("POST")){if(upload)processor.upload(ctx);else processor.add(ctx);}else if(method.equals("PUT"))processor.update(ctx);else processor.remove(ctx);});c.handle();return c;
            }finally{raw.release();}
        }
        RequestContext add(String key,String url)throws Exception{return memberCall("POST","10","","writer",key,new JSONObject().put("url",url).put("name"," 中文 😀 ").toString());}
        RequestContext upload(String key,String url)throws Exception{return memberCall("POST","","","writer",key,new JSONObject().put("url",url).toString());}
        RequestContext change(String key,String group,String item)throws Exception{return memberCall("PUT",group,item,"writer",key,"{\"name\":\"新名\",\"sort\":-4}");}
        RequestContext remove(String key,String group,String item)throws Exception{return memberCall("DELETE",group,item,"writer",key,"");}
        void cleanOwned()throws Exception{sql(observer,"DELETE FROM emoji_group_item WHERE emojiGroupItemGroupId IN ('10','11')");sql(observer,"DELETE FROM emoji_group WHERE oId='11'");sql(observer,"DELETE FROM emoji_group WHERE oId='13'");}
    }
    public static void main(String[]args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new ItemFixture()) {
            f.cleanOwned();f.bind();var out=data(f.add("new","https://example.com/A.png?signature=AbC"));Assert.assertEquals(out.keySet(),Set.of("id","groupId","emojiId","name","sort","url"));Assert.assertEquals(out.getString("name"),"中文 😀");Assert.assertEquals(out.getInt("sort"),1);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group WHERE emojiGroupType=1 AND emojiGroupUserId='"+f.actor+"'"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item WHERE emojiGroupItemEmojiId='"+out.getString("emojiId")+"'"),2L);Assert.assertEquals(f.locks.getFirst(),"namespace");Assert.assertEquals(f.locks.get(1),"user");
            data(f.add("new","https://example.com/A.png?signature=AbC"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),3L);
            var duplicate=data(f.memberCall("POST","10","","writer","repeat","{\"emojiId\":\""+out.getString("emojiId")+"\",\"name\":\"ignored\",\"sort\":99}"));Assert.assertEquals(duplicate.getString("id"),out.getString("id"));Assert.assertEquals(duplicate.getString("name"),"中文 😀");Assert.assertEquals(duplicate.getInt("sort"),1);
            data(f.change("change","10",out.getString("id")));data(f.add("new","https://example.com/A.png?signature=AbC"));Assert.assertEquals(Fixture.rows(f.observer,"SELECT emojiGroupItemName FROM emoji_group_item WHERE oId=?",out.getString("id")).getFirst().getString("emojiGroupItemName"),"新名");
        }
        System.out.println("PASS 1 actual Dispatcher/Bearer/strict-root add creates default plus mirrored members; namespace precedes actor; duplicate/replay preserves metadata and later update");
        try(var f=new ItemFixture()) {
            f.cleanOwned();f.bind();var a=data(f.upload("a","https://example.com/A.png?sig=Ab"));var b=data(f.upload("b","https://example.com/a.png?sig=Ab"));var c=data(f.upload("c","https://example.com/A.png?sig=ab"));Assert.assertNotEquals(a.getString("emojiId"),b.getString("emojiId"));Assert.assertNotEquals(a.getString("emojiId"),c.getString("emojiId"));Assert.assertEquals(data(f.upload("same","https://example.com/A.png?sig=Ab")).getString("emojiId"),a.getString("emojiId"));
            var reused=data(f.memberCall("POST","10","","writer","global","{\"emojiId\":\"30\"}"));Assert.assertEquals(reused.getString("emojiId"),"30");Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji WHERE oId='30' AND emojiUploaderId='2'"),1L);
        }
        System.out.println("PASS 2 URL pool compares UTF8 byte identity under real NOCASE schema, preserves path/query case and signatures; active images uploaded by other users are reusable");
        try(var f=new ItemFixture()) {
            sql(f.observer,"DELETE FROM emoji_group WHERE oId='13'");f.bind();sql(f.observer,"DELETE FROM emoji_group_item WHERE oId='21'");var old=data(f.memberCall("POST","10","","writer","repair","{\"emojiId\":\"30\",\"name\":\"ignored\"}"));Assert.assertEquals(old.getString("id"),"20");Assert.assertEquals(old.getString("name"),"name");Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item WHERE emojiGroupItemGroupId='11'"),1L);
            data(f.remove("custom","10","20"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),2L);var all=Fixture.rows(f.observer,"SELECT oId FROM emoji_group_item WHERE emojiGroupItemGroupId='11'").getFirst().getString("oId");data(f.remove("all","11",all));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),2L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_share"),1L);data(f.remove("all","11",all));Assert.assertEquals(f.remove("new-key","11",all).attr("test.status"),404);
        }
        System.out.println("PASS 3 duplicate add repairs missing all mirror without replacing names; custom removal preserves all; all removal cascades only actor groups, retains global/other-owner/share and replays safely");
        for(String fault:List.of("namespace","placeholder","user","group","emoji-read","image-write","item","business","mirror","receipt","commit","savepoint","release"))try(var f=new ItemFixture()) {
            f.cleanOwned();f.bind();f.fault=fault;Assert.assertEquals(f.add("fault","https://example.com/new.png").attr("test.status"),503,fault);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),2L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group WHERE emojiGroupType=1"),0L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);f.fault="";data(f.add("fault","https://example.com/new.png"));
        }
        for(String fault:List.of("business","mirror","receipt","commit","savepoint","release"))try(var f=new ItemFixture()) {
            sql(f.observer,"DELETE FROM emoji_group WHERE oId='13'");f.bind();f.fault=fault;Assert.assertEquals(f.remove("fault","11","21").attr("test.status"),503,fault);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),3L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        System.out.println("PASS 4 default/image/member/mirror/receipt and cascade deletion faults, savepoint/release and strict commit roll back complete state, recovered same key succeeds");
        try(var f=new ItemFixture()) {
            f.cleanOwned();f.bind();for(String method:List.of("POST","PUT","DELETE"))for(String token:Arrays.asList(null,"bad","reader"))Assert.assertEquals(f.memberCall(method,"10","20",token,"auth",method.equals("POST")?"{\"emojiId\":\"30\"}":method.equals("PUT")?"{}":"").attr("test.status"),token==null||token.equals("bad")?401:403);
            for(String body:List.of("{}","{\"url\":\"https://example.com/a\",\"emojiId\":\"30\"}","{\"emojiId\":\"30\",\"owner\":\"2\"}","{\"emojiId\":\"30\",\"name\":\"<b>bad</b>\"}","{\"emojiId\":\"30\",\"sort\":1.5}"))Assert.assertEquals(f.memberCall("POST","10","","writer","bad",body).attr("test.status"),422);
            Assert.assertEquals(f.memberCall("POST","12","","writer","other","{\"emojiId\":\"30\"}").attr("test.status"),404);Assert.assertEquals(f.memberCall("POST","10","","writer","deleted","{\"emojiId\":\"31\"}").attr("test.status"),404);Assert.assertEquals(f.change("other-item","10","22").attr("test.status"),404);
            Assert.assertEquals(f.memberCall("POST","10","","writer",null,"{\"emojiId\":\"30\"}").attr("test.status"),422);
        }
        for(String url:List.of("http://example.com/a","https://user:pw@example.com/a","https://example.com/a#fragment","javascript:alert(1)","https://example.com/ ","https://example.com/"+"a".repeat(500),"https://example.com/\uD800"))error(422,()->ApiV3EmojiItemInputs.url(url));
        Assert.assertEquals(ApiV3EmojiItemInputs.metadata(new JSONObject().put("name","   ")).getString("name"),"");
        Assert.assertEquals(ApiV3EmojiItemInputs.metadata(new JSONObject().put("name","\u3000\u3000")).getString("name"),"");
        Assert.assertEquals(ApiV3EmojiItemInputs.metadata(new JSONObject().put("name","\u3000 中文 😀 \u3000")).getString("name"),"中文 😀");Assert.assertEquals(ApiV3EmojiItemInputs.metadata(new JSONObject().put("name","😀".repeat(32))).getString("name").length(),64);
        System.out.println("PASS 5 no Cookie/apiKey identity fallback, correct scope, group/item binding and deleted-image denial; typed bodies/key/name/URL/Unicode boundaries");
        try(var f=new ItemFixture()) {
            f.cleanOwned();f.bind();f.fault="lost-ack";Assert.assertEquals(f.add("ack","https://example.com/new.png").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),3L);f.fault="";data(f.add("ack","https://example.com/new.png"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),3L);sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);Assert.assertEquals(f.add("ack","https://example.com/new.png").attr("test.status"),404);
        }
        try(var f=new ItemFixture()){f.cleanOwned();f.bind();f.deactivateOnBegin=true;Assert.assertEquals(f.upload("race","https://example.com/new.png").attr("test.status"),404);Assert.assertEquals(f.itemWrites,0);}
        System.out.println("PASS 6 lost acknowledgement recovers one image/default/mirror publication; fresh actor status required for retry and auth-to-root race");
        try(var f=new ItemFixture()) {
            f.cleanOwned();f.bind();sql(f.observer,"INSERT INTO emoji_group VALUES('50',?,'all-1',0,1,1,1),('51',?,'all-2',0,1,1,1)",f.actor,f.actor);Assert.assertEquals(f.add("duplicate-all","https://example.com/new.png").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),2L);
        }
        try(var f=new ItemFixture()) {
            f.cleanOwned();f.bind();sql(f.observer,"INSERT INTO emoji VALUES('32','https://example.com/a.png',0,'2',1)");Assert.assertEquals(f.add("duplicate-url","https://example.com/a.png").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group WHERE emojiGroupType=1"),0L);
        }
        try(var f=new ItemFixture()) {
            f.cleanOwned();f.bind();sql(f.observer,"INSERT INTO emoji_group_item VALUES('50','10','30','max',2147483647,1)");Assert.assertEquals(f.add("overflow","https://example.com/new.png").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),2L);
            try{f.members.addInCurrentTransaction(f.actor,"10",new JSONObject().put("emojiId","30"),false);Assert.fail();}catch(IllegalStateException expected){}
        }
        try(var f=new ItemFixture()) {
            f.cleanOwned();f.bind();try(var st=f.observer.prepareStatement("INSERT INTO emoji_group VALUES(?,?,'group',0,0,1,1)")){for(int i=0;i<4097;i++){st.setString(1,String.valueOf(1000+i));st.setString(2,f.actor);st.addBatch();}st.executeBatch();}
            Assert.assertEquals(f.upload("budget","https://example.com/new.png").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),2L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        try(var f=new ItemFixture()) {
            sql(f.observer,"DELETE FROM emoji_group WHERE oId='13'");f.bind();sql(f.observer,"INSERT INTO emoji_group_item VALUES('51','10','30','duplicate',2,1)");
            Assert.assertEquals(f.memberCall("POST","10","","writer","duplicate-member","{\"emojiId\":\"30\"}").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),4L);
        }
        System.out.println("PASS 7 duplicate default/exact URL/member, complete group budget and sort overflow reject whole operation without unrelated cleanup; primitive requires caller root");
        for(String fault:List.of("namespace","user","group","item","emoji-read","business","receipt","commit","savepoint","release"))try(var f=new ItemFixture()) {
            sql(f.observer,"DELETE FROM emoji_group WHERE oId='13'");f.bind();f.fault=fault;Assert.assertEquals(f.change("fault","10","20").attr("test.status"),503,fault);Assert.assertEquals(Fixture.rows(f.observer,"SELECT emojiGroupItemName FROM emoji_group_item WHERE oId='20'").getFirst().getString("emojiGroupItemName"),"name");Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        try(var f=new ItemFixture()) {
            sql(f.observer,"DELETE FROM emoji_group WHERE oId='13'");f.bind();var tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="mirror";
            try{f.members.removeInCurrentTransaction(f.actor,"11","21");Assert.fail();}catch(RepositoryException expected){}
            f.fault="";tx.commit();Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),3L);
            tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=3");f.fault="rollback-point";try{f.members.removeInCurrentTransaction(f.actor,"11","21");Assert.fail();}catch(RepositoryException expected){}Assert.assertFalse(tx.isActive());Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);
        }
        System.out.println("PASS 8 actual member UPDATE fault atomicity; cascade primitive savepoint preserves unrelated caller writes, rollback-point failure aborts root");
        System.out.println("Emoji item isolated tests passed: 8 groups");
    }
}
