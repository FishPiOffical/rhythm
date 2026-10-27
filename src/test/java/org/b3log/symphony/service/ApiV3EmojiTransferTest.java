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

/** Real share/import/cloud SQL and reused member/group primitives under one strict root. */
public class ApiV3EmojiTransferTest extends ApiV3EmojiItemTest {
    static class TransferFixture extends ItemFixture {
        final ApiV3EmojiTransferService transfer=new ApiV3EmojiTransferService();
        final ApiV3EmojiTransferProcessor endpointTransfer=new ApiV3EmojiTransferProcessor();
        TransferFixture()throws Exception {
            super();sql(observer,"DELETE FROM emoji_group WHERE oId='13'");
            sql(observer,"DROP TABLE emoji_share");sql(observer,"CREATE TABLE emoji_share(oId TEXT PRIMARY KEY,emojiShareCode TEXT UNIQUE,emojiShareOwnerUserId TEXT,emojiShareGroupId TEXT,emojiShareGroupName TEXT,emojiShareSnapshot TEXT,emojiShareEmojiCount INT,emojiShareImportedCount INT,emojiShareCreatedTime INT)");
            sql(observer,"CREATE TABLE cloud(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");
            for(String name:List.of("users","options","groups","items","emojis","requests")){var f=ApiV3EmojiItemService.class.getDeclaredField(name);f.setAccessible(true);inject(transfer,name,f.get(members));}
            var shares=new EmojiShareRepository(){public String getName(){return "emoji_share";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("share",q,a);}};
            var cloud=new CloudRepository(){public String getName(){return "cloud";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL("cloud",q,a);}};
            inject(transfer,"shares",shares);inject(transfer,"cloud",cloud);inject(transfer,"members",members);inject(transfer,"groupWrites",groupService);inject(endpointTransfer,"service",transfer);
        }
        @Override Transaction begin(){
            try{
                var tx=super.begin();var inner=writer;
                writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                    try{
                        if(m.getName().equals("prepareStatement")){
                            String q=(String)a[0];var st=(PreparedStatement)m.invoke(inner,a);
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p2,m2,a2)->{
                                try{var out=m2.invoke(st,a2);if(m2.getName().equals("executeUpdate")&&q.contains("`emoji_share`")&&(fault.equals("share-write")||fault.equals("counter")&&q.startsWith("UPDATE")))throw new SQLException("PRIVATE_SHARE");return out;}catch(InvocationTargetException e){throw e.getCause();}
                            });
                        }
                        return m.invoke(inner,a);
                    }catch(InvocationTargetException e){throw e.getCause();}
                });
                var f=JdbcTransaction.class.getDeclaredField("connection");f.setAccessible(true);f.set(delegate,writer);return tx;
            }catch(Exception e){throw new IllegalStateException(e);}
        }
        @Override void bind(){EmojiTransferRouteBindings.register(endpointTransfer,auth,writes);Dispatcher.mapping();}
        RequestContext transferCall(String kind,String group,String token,String key,String body)throws Exception {
            String uri=switch(kind){case "share"->"/api/v3/me/settings/emoji-groups/"+group+"/shares";case "import"->"/api/v3/me/settings/emoji-group-imports";default->"/api/v3/me/settings/emoji-migrations";};
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,uri+"?apiKey=legacy&userId=2",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{
                raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","2001:db8::"+actor);
                if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy","userId","2")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(ctx->{var resolution=(org.b3log.latke.http.handler.RouteResolution)ctx.attr("MATCH_RESULT");Assert.assertEquals(resolution.getContextHandlerMeta().getInvokeHolder().getName(),kind.equals("import")?"importShare":kind);if(kind.equals("share"))endpointTransfer.share(ctx);else if(kind.equals("import"))endpointTransfer.importShare(ctx);else endpointTransfer.migrate(ctx);});c.handle();return c;
            }finally{raw.release();}
        }
        RequestContext share(String key)throws Exception{return transferCall("share","10","writer",key,"");}
        RequestContext importCode(String key,String code)throws Exception{return transferCall("import","","writer",key,new JSONObject().put("shareCode",code).toString());}
        RequestContext migrate(String key)throws Exception{return transferCall("migrate","","writer",key,"");}
        void seedShare(String snapshot)throws Exception {sql(observer,"INSERT INTO emoji_share VALUES('60','ABCDEFGH1234','2','deleted-source','Source',?,1,0,1)",snapshot);}
        void seedCloud(String data)throws Exception {sql(observer,"INSERT INTO cloud VALUES('70',?,'emojis',?)",actor,data);}
        String snapshot(String url){return new JSONArray().put(new JSONObject().put("url",url).put("name","名字 😀").put("sort",1)).toString();}
    }
    public static void main(String[]args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new TransferFixture()) {
            f.bind();var shared=data(f.share("share"));Assert.assertEquals(shared.keySet(),Set.of("shareCode","groupName","emojiCount","createdTime"));Assert.assertTrue(shared.getString("shareCode").matches("[A-Z0-9]{12}"));Assert.assertEquals(shared.getInt("emojiCount"),1);Assert.assertEquals(data(f.share("share")).getString("shareCode"),shared.getString("shareCode"));Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_share"),1L);
            final var stored=Fixture.rows(f.observer,"SELECT emojiShareSnapshot FROM emoji_share").getFirst();Assert.assertEquals(new JSONArray(stored.getString("emojiShareSnapshot")).getJSONObject(0).keySet(),Set.of("url","name","sort"));
            sql(f.observer,"DELETE FROM emoji_group_item WHERE emojiGroupItemGroupId='10'");sql(f.observer,"DELETE FROM emoji_group WHERE oId='10'");
            final var first=data(f.importCode("import-1",shared.getString("shareCode").toLowerCase(Locale.ROOT)));Assert.assertEquals(first.keySet(),Set.of("groupId","groupName","emojiCount","sourceGroupName"));Assert.assertEquals(first.getString("groupName"),"first");Assert.assertEquals(data(f.importCode("import-1",shared.getString("shareCode"))).getString("groupId"),first.getString("groupId"));
            final var second=data(f.importCode("import-2",shared.getString("shareCode")));Assert.assertNotEquals(first.getString("groupId"),second.getString("groupId"));Assert.assertEquals(second.getString("groupName"),"first-导入");Assert.assertEquals(f.scalar("SELECT emojiShareImportedCount AS value FROM emoji_share"),2L);
        }
        System.out.println("PASS 1 actual Dispatcher sharing whitelist and secure code/replay; deleting source does not invalidate snapshot, each new import key creates independent group and increments counter once");
        try(var f=new TransferFixture()) {
            f.cleanOwned();f.bind();f.seedShare(f.snapshot("https://example.com/A.png?sig=AbC"));var imported=data(f.importCode("import","ABCDEFGH1234"));Assert.assertEquals(imported.getInt("emojiCount"),1);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji WHERE emojiUrl='https://example.com/A.png?sig=AbC'"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group WHERE emojiGroupType=1 AND emojiGroupUserId='"+f.actor+"'"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item WHERE emojiGroupItemGroupId='"+imported.getString("groupId")+"'"),1L);
        }
        try(var f=new TransferFixture()) {
            f.cleanOwned();f.bind();final String source=new JSONArray().put("https://example.com/new.png?sig=A").put("https://example.com/new.png?sig=A").put("https://example.com/a.png").toString();f.seedCloud(source);var out=data(f.migrate("migrate"));Assert.assertEquals(out.keySet(),Set.of("groupId","processedCount","addedCount"));Assert.assertEquals(out.getInt("processedCount"),2);Assert.assertEquals(out.getInt("addedCount"),2);Assert.assertEquals(data(f.migrate("migrate")).getInt("addedCount"),2);Assert.assertEquals(data(f.migrate("again")).getInt("addedCount"),0);Assert.assertEquals(Fixture.rows(f.observer,"SELECT data FROM cloud").getFirst().getString("data"),source);
        }
        System.out.println("PASS 2 import creates all mirror/default and exact signed URL once; cloud owner copy deduplicates, counts real additions, preserves source and existing members, replay reports original counts");
        for(String bad:List.of("[{\"url\":\"http://example.com/bad\",\"name\":\"valid\",\"sort\":1}]","[{\"url\":\"https://example.com/a.png\",\"name\":\"<b>bad</b>\",\"sort\":1}]","[{\"url\":\"https://example.com/deleted.png\",\"name\":\"\",\"sort\":1}]","[null]","[1]","[{\"url\":\"https://example.com/a.png\",\"name\":\"a\",\"sort\":1,\"private\":\"x\"}]","not-json","[]"))try(var f=new TransferFixture()) {
            f.bind();f.seedShare(bad);String before=f.groups();Assert.assertEquals(f.importCode("bad","ABCDEFGH1234").attr("test.status"),422,bad);Assert.assertEquals(f.groups(),before);Assert.assertEquals(f.scalar("SELECT emojiShareImportedCount AS value FROM emoji_share"),0L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        for(String bad:List.of("[\"https://example.com/new.png\",\"http://example.com/bad\"]","[\"https://example.com/new.png\",\"https://example.com/deleted.png\"]","[\"https://example.com/new.png\",null]","{\"wrong\":[]}","[\"https://example.com/new.png\",]"))try(var f=new TransferFixture()) {
            f.bind();f.seedCloud(bad);Assert.assertEquals(f.migrate("bad").attr("test.status"),422,bad);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),2L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        try(var f=new TransferFixture()){f.bind();sql(f.observer,"UPDATE emoji_group_item SET emojiGroupItemName='<b>bad</b>' WHERE oId='20'");Assert.assertEquals(f.share("bad").attr("test.status"),422);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_share"),0L);}
        try(var f=new TransferFixture()){f.bind();sql(f.observer,"UPDATE emoji SET emojiStatus=1 WHERE oId='30'");Assert.assertEquals(f.share("bad").attr("test.status"),422);}
        System.out.println("PASS 3 every invalid source JSON/item/name/HTTP URL/deleted image rejects whole share/import/cloud batch with zero writes, including valid prefix followed by invalid item");
        for(String fault:List.of("namespace","placeholder","user","group","item","emoji-read","share","share-write","receipt","commit","savepoint","release"))try(var f=new TransferFixture()) {
            f.bind();f.fault=fault;Assert.assertEquals(f.share("fault").attr("test.status"),503,fault);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_share"),0L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);f.fault="";data(f.share("fault"));
        }
        for(String fault:List.of("namespace","placeholder","user","group","share","emoji-read","business","image-write","item","mirror","counter","receipt","commit","savepoint","release"))try(var f=new TransferFixture()) {
            f.cleanOwned();f.bind();f.seedShare(f.snapshot("https://example.com/new.png"));String before=f.groups();f.fault=fault;Assert.assertEquals(f.importCode("fault","ABCDEFGH1234").attr("test.status"),503,fault);Assert.assertEquals(f.groups(),before);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),2L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_group_item"),1L);Assert.assertEquals(f.scalar("SELECT emojiShareImportedCount AS value FROM emoji_share"),0L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);f.fault="";data(f.importCode("fault","ABCDEFGH1234"));
        }
        for(String fault:List.of("cloud","image-write","business","item","receipt","commit","savepoint","release"))try(var f=new TransferFixture()) {
            f.cleanOwned();f.bind();f.seedCloud("[\"https://example.com/new.png\"]");String before=f.groups();f.fault=fault;Assert.assertEquals(f.migrate("fault").attr("test.status"),503,fault);Assert.assertEquals(f.groups(),before);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),2L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        System.out.println("PASS 4 actual share/image/default/group/member/mirror/counter/receipt/savepoint/strict commit faults fully roll back each capability, with recovered same-key success");
        try(var f=new TransferFixture()) {
            f.bind();for(String kind:List.of("share","import","migrate"))for(String token:Arrays.asList(null,"bad","reader"))Assert.assertEquals(f.transferCall(kind,"10",token,"auth",kind.equals("import")?"{\"shareCode\":\"ABCDEFGH1234\"}":"").attr("test.status"),token==null||token.equals("bad")?401:403);
            Assert.assertEquals(f.transferCall("share","11","writer","default","").attr("test.status"),409);Assert.assertEquals(f.transferCall("share","12","writer","other","").attr("test.status"),404);Assert.assertEquals(f.importCode("absent","ABCDEFGH1234").attr("test.status"),404);Assert.assertEquals(f.migrate("absent").attr("test.status"),409);
            Assert.assertEquals(f.transferCall("share","10","writer",null,"").attr("test.status"),422);Assert.assertEquals(f.transferCall("migrate","","writer","extra","{\"userId\":\"2\"}").attr("test.status"),422);Assert.assertEquals(f.transferCall("import","","writer","bad","{\"shareCode\":\"ABCDEFGH1234\",\"scope\":\"all\"}").attr("test.status"),422);
        }
        try(var f=new TransferFixture()){f.bind();sql(f.observer,"DELETE FROM emoji_group_item WHERE emojiGroupItemGroupId='10'");Assert.assertEquals(f.share("empty").attr("test.status"),409);}
        System.out.println("PASS 5 real route gates reject missing/wrong Bearer/scope and Cookie/apiKey actor forgery; owner/default/empty/absent/body/key boundaries");
        for(String kind:List.of("share","import","migrate"))try(var f=new TransferFixture()) {
            if(!kind.equals("share"))f.cleanOwned();f.bind();if(kind.equals("import"))f.seedShare(f.snapshot("https://example.com/new.png"));if(kind.equals("migrate"))f.seedCloud("[\"https://example.com/new.png\"]");
            final String body=kind.equals("import")?"{\"shareCode\":\"ABCDEFGH1234\"}":"";
            f.fault="lost-ack";Assert.assertEquals(f.transferCall(kind,"10","writer","ack",body).attr("test.status"),503);f.fault="";data(f.transferCall(kind,"10","writer","ack",body));
            if(kind.equals("share"))Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_share"),1L);
            if(kind.equals("import"))Assert.assertEquals(f.scalar("SELECT emojiShareImportedCount AS value FROM emoji_share"),1L);
            if(kind.equals("migrate"))Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji"),3L);
            sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);Assert.assertEquals(f.transferCall(kind,"10","writer","ack",body).attr("test.status"),404);
        }
        try(var f=new TransferFixture()){f.bind();f.deactivateOnBegin=true;Assert.assertEquals(f.share("race").attr("test.status"),404);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_share"),0L);}
        System.out.println("PASS 6 lost commit acknowledgements recover one share/import/copy and one counter increment; fresh actor required on replay and auth-to-root race");
        try(var f=new TransferFixture()) {
            f.bind();String longName="😀".repeat(32);sql(f.observer,"UPDATE emoji_group SET emojiGroupName=? WHERE oId='10'",longName);f.seedShare(f.snapshot("https://example.com/a.png"));sql(f.observer,"UPDATE emoji_share SET emojiShareGroupName=?",longName);
            String name=data(f.importCode("long-name","ABCDEFGH1234")).getString("groupName");Assert.assertTrue(name.length()<=64);Assert.assertTrue(name.endsWith("-导入"));ApiV3EmojiGroupInputs.normalize(new JSONObject().put("name",name));
        }
        try(var f=new TransferFixture()) {
            f.bind();JSONArray tooMany=new JSONArray();for(int i=0;i<257;i++)tooMany.put("https://example.com/a.png");f.seedCloud(tooMany.toString());Assert.assertEquals(f.migrate("budget").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        try(var f=new TransferFixture()) {
            f.bind();f.seedCloud("[\"https://example.com/a.png\"]");sql(f.observer,"INSERT INTO cloud VALUES('71',?,'emojis','[]')",f.actor);Assert.assertEquals(f.migrate("duplicate-source").attr("test.status"),503);
        }
        try(var f=new TransferFixture()) {
            try{f.transfer.applyInCurrentTransaction(f.actor,"share",new JSONObject().put("groupId","10"));Assert.fail();}catch(IllegalStateException expected){}
            var tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="share-write";try{f.transfer.applyInCurrentTransaction(f.actor,"share",new JSONObject().put("groupId","10"));Assert.fail();}catch(RepositoryException expected){}f.fault="";tx.commit();Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM emoji_share"),0L);
            f.seedShare(f.snapshot("https://example.com/new.png"));tx=f.begin();sql(f.writer,"UPDATE unrelated SET value=3");f.fault="rollback-point";try{f.transfer.applyInCurrentTransaction(f.actor,"import",new JSONObject().put("shareCode","ABCDEFGH1234"));Assert.fail();}catch(RepositoryException expected){}Assert.assertFalse(tx.isActive());Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);
        }
        try(var f=new TransferFixture()) {
            f.bind();f.seedShare(f.snapshot("https://example.com/a.png"));
            sql(f.observer,"UPDATE emoji_share SET emojiShareImportedCount=2147483648");
            Assert.assertEquals(f.importCode("invalid-counter","ABCDEFGH1234").attr("test.status"),503);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
            Assert.assertEquals(f.scalar("SELECT emojiShareImportedCount AS value FROM emoji_share"),2147483648L);
        }
        System.out.println("PASS 7 imported suffix respects 64 UTF16 and surrogate boundaries; complete technical budget/duplicate-source/counter rejection; primitive savepoint preserves caller writes and aborts root on rollback failure");
        System.out.println("Emoji transfer isolated tests passed: 7 groups");
    }
}
