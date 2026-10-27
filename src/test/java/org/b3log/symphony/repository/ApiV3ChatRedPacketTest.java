package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.*;
import org.b3log.symphony.model.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import java.util.*;
import org.b3log.latke.http.*;
import org.b3log.symphony.processor.ApiV3ChatRedPacketProcessor;
import org.b3log.symphony.processor.middleware.*;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Actual publication/event/profession/points/packet/receipt core; SQLite, repository and delivery adapters. */
public final class ApiV3ChatRedPacketTest {
    static int sequence;
    static final long NOW=2_000_000;
    static void set(Object o,Class<?> type,String field,Object value)throws Exception{inject(o,type,field,value);}
    static class F extends ChatMessageMgmtServiceTest.Publication {
        final ApiV3ChatRedPacketService service=new ApiV3ChatRedPacketService(){protected long now(){return NOW;}};
        final PointtransferMgmtService points=new PointtransferMgmtService();
        final ChatRedPacketRepository packets=new ChatRedPacketRepository(){
            public String getName(){return "packets";}public boolean hasTransactionBegun(){return active();}
            public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(writer,q,a);}
            public String add(JSONObject r)throws RepositoryException{return insert("packets",r);}
            public void update(String id,JSONObject r,String...f)throws RepositoryException{updateRow("packets",id,r,f);}
        };
        final ChatRedPacketSlotRepository slots=new ChatRedPacketSlotRepository(){
            public String getName(){return "slots";}public boolean hasTransactionBegun(){return active();}
            public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(writer,q,a);}
            public String add(JSONObject r)throws RepositoryException{return insert("slots",r);}
            public void update(String id,JSONObject r,String...f)throws RepositoryException{updateRow("slots",id,r,f);}
        };
        F()throws Exception{
            for(String col:List.of("userStatus INT DEFAULT 0","userPoint INT DEFAULT 10000","userUsedPoint INT DEFAULT 0","userName TEXT","userNickname TEXT DEFAULT 'nick'","userAvatarURL TEXT DEFAULT 'https://example.com/a.png'","userPhone TEXT DEFAULT 'PRIVATE_PHONE'","userRole TEXT DEFAULT 'member'","userLatestCmtTime BIGINT DEFAULT 0"))exec(writer,"ALTER TABLE user ADD COLUMN "+col);
            exec(writer,"UPDATE user SET userName=?", "Writer"+(++sequence));exec(writer,"INSERT INTO user(oId,userName) VALUES('2','Reader'),('3','admin'),('4','Other')");
            exec(writer,"CREATE TABLE packets(oId TEXT PRIMARY KEY,senderId TEXT,type TEXT,message TEXT,money INT,count INT,got INT,principalPoints INT,poolPoints INT,taxPoints INT,feeRecipientId TEXT,createdAt BIGINT,senderGesture INT DEFAULT -1)");
            exec(writer,"CREATE TABLE slots(oId TEXT PRIMARY KEY,messageId TEXT,ordinal INT,amount INT,recipientId TEXT,claimUserId TEXT,claimedAt BIGINT,transferId TEXT,gesture INT DEFAULT -1,senderRefundPoints INT DEFAULT 0,senderTransferId TEXT DEFAULT '')");
            exec(writer,"CREATE TABLE receipts(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            exec(writer,"CREATE TABLE transfers(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,sum INT,fromBalance INT,toBalance INT,time BIGINT,type INT,dataId TEXT,memo TEXT,sourceAppId TEXT,sourceAppName TEXT,sourceScene TEXT,sourceRequestId TEXT)");
            exec(writer,"CREATE TABLE transfer_index(oId TEXT PRIMARY KEY,userId TEXT,rawId TEXT)");
            exec(writer,"CREATE TABLE membership(oId TEXT,userId TEXT,lvCode TEXT,state INT,expiresAt BIGINT)");
            exec(writer,"CREATE TABLE cloud(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");
            exec(writer,"CREATE TABLE notification(oId TEXT PRIMARY KEY,userId TEXT,dataId TEXT,dataType INT,hasRead BOOLEAN,content TEXT)");
            var users=new UserRepository(){
                public JSONObject getForUpdate(String id)throws RepositoryException{var r=select("SELECT * FROM user WHERE oId=? FOR UPDATE",id);return r.isEmpty()?null:r.getFirst();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(writer,q,a);}
                public void updateColumnsInCurrentTransaction(String id,JSONObject r)throws RepositoryException{updateRow("user",id,r,r.keySet().toArray(new String[0]));}
                public void update(String id,JSONObject r,String...f)throws RepositoryException{updateRow("user",id,r,f);}
            };
            var receipts=new ApiV3RequestRepository(){
                public String getName(){return "receipts";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return root();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(writer,q,a);}
                public JSONObject getForUpdate(String id)throws RepositoryException{var r=select("SELECT * FROM receipts WHERE oId=? FOR UPDATE",id);return r.isEmpty()?null:r.getFirst();}
                public String add(JSONObject r)throws RepositoryException{return insert("receipts",r);}
                public void update(String id,JSONObject r,String...f)throws RepositoryException{updateRow("receipts",id,r,r.keySet().stream().filter(k->!k.equals("oId")).toArray(String[]::new));}
            };
            var transfers=new PointtransferRepository(){
                public String getName(){return "transfers";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return root();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(writer,q,a);}
                public String add(JSONObject r)throws RepositoryException{if(!r.has("oId"))r.put("oId",org.b3log.latke.util.Ids.genTimeMillisId());return insert("transfers",r);}
            };
            var notices=new NotificationRepository(){
                public String getName(){return "notification";}
                public String add(JSONObject r)throws RepositoryException{r.put("oId",org.b3log.latke.util.Ids.genTimeMillisId());return insert("notification",r);}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(writer,q,a);}
                public void update(String id,JSONObject r,String...f)throws RepositoryException{updateRow("notification",id,r,f);}
            };
            var membership=new MembershipRepository(){public String getName(){return "membership";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(writer,q,a);}};
            var restrictions=new ChatMessageRestrictionQueryService();set(restrictions,ChatMessageRestrictionQueryService.class,"cloud",new CloudRepository(){public String getName(){return "cloud";}public boolean hasTransactionBegun(){return active();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(writer,q,a);}});
            var field=ChatMessageMgmtService.class.getDeclaredField("messages");field.setAccessible(true);var chat=(ChatRoomRepository)field.get(management);
            set(management,ChatMessageMgmtService.class,"redPackets",packets);
            set(management,ChatMessageMgmtService.class,"reactions",new Reactions());
            set(capture,ProfessionSourceEventCaptureService.class,"sourceEventRepository",new ProfessionSourceEventRepository(){
                public String add(JSONObject row)throws RepositoryException{exec(writer,"INSERT INTO profession VALUES(?,?)",row.getString("oId"),row.toString());if(faults.contains("profession.after"))throw new RepositoryException("profession failure");return row.getString("oId");}
                public List<JSONObject> getPositiveByTarget(String type,String id)throws RepositoryException{var rows=new ArrayList<JSONObject>();for(var r:query(writer,"SELECT content FROM profession")){var value=new JSONObject(r.getString("content"));if(type.equals(value.optString("targetType"))&&id.equals(value.optString("targetId"))&&value.optString("reversalOfEventKey").isEmpty())rows.add(value);}return rows;}
                public JSONObject getByEventKey(String key)throws RepositoryException{for(var r:query(writer,"SELECT content FROM profession")){var value=new JSONObject(r.getString("content"));if(key.equals(value.optString("eventKey")))return value;}return null;}
            });
            set(points,PointtransferMgmtService.class,"userRepository",users);set(points,PointtransferMgmtService.class,"pointtransferRepository",transfers);
            var delivery=new org.b3log.symphony.processor.ChatroomProcessor();delivery.chatRoomRepository=chat;
            Map<String,Object> dependencies=new HashMap<>();dependencies.put("users",users);dependencies.put("chat",chat);dependencies.put("packets",packets);dependencies.put("slots",slots);dependencies.put("requests",receipts);dependencies.put("memberships",membership);dependencies.put("notifications",notices);dependencies.put("notificationDelivery",new NotificationMgmtService());dependencies.put("points",points);dependencies.put("transfers",transfers);dependencies.put("transferIndex",new PointtransferRedcRepository(){public String getName(){return "transfer_index";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(writer,q,a);}});dependencies.put("messages",management);dependencies.put("validation",new ChatMessageValidationService());dependencies.put("restrictions",restrictions);dependencies.put("penalties",new ChatMessagePenaltyMgmtService());dependencies.put("admissionEffects",new ApiV3ChatPublicationService());dependencies.put("delivery",delivery);
            for(var e:dependencies.entrySet())set(service,ApiV3ChatRedPacketService.class,e.getKey(),e.getValue());
        }
        Transaction root(){try{begin();return ApiV3Transactions.strict(tx);}catch(Exception e){throw new IllegalStateException(e);}}
        @Override void begin()throws Exception{
            super.begin();final Connection previous=tx.getConnection();
            final Connection wrapped=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                try{Object result=m.invoke(previous,a);if(m.getName().equals("commit")&&faults.contains("ack"))throw new SQLException("fake lost acknowledgement");return result;}catch(InvocationTargetException e){throw e.getCause();}
            });
            var f=JdbcTransaction.class.getDeclaredField("connection");f.setAccessible(true);f.set(tx,wrapped);
        }
        String insert(String table,JSONObject r)throws RepositoryException{var names=new ArrayList<>(r.keySet());exec(writer,"INSERT INTO "+table+" ("+String.join(",",names)+") VALUES("+String.join(",",Collections.nCopies(names.size(),"?"))+")",names.stream().map(r::get).toArray());if(faults.contains(table+".after"))throw new RepositoryException("fake private write failure");return r.getString("oId");}
        void updateRow(String table,String id,JSONObject r,String...fields)throws RepositoryException{var a=new ArrayList<Object>();for(String f:fields)a.add(r.get(f));a.add(id);exec(writer,"UPDATE "+table+" SET "+String.join(",",Arrays.stream(fields).map(f->f+"=?").toList())+" WHERE oId=?",a.toArray());if(faults.contains(table+".after"))throw new RepositoryException("fake private write failure");}
        JSONObject viewer(String id){return new JSONObject().put("oId",id);}
        long balance(String id)throws Exception{return query(reader,"SELECT userPoint FROM user WHERE oId=?",id).getFirst().getLong("userPoint");}
        JSONObject create(JSONObject body)throws Exception{return service.create(viewer("1"),body,"create");}
        long count(String table)throws Exception{return query(reader,"SELECT COUNT(*) AS n FROM "+table).getFirst().getLong("n");}
        RequestContext http(String method,String path,String token,String key,String body)throws Exception{
            var processor=new ApiV3ChatRedPacketProcessor();set(processor,ApiV3ChatRedPacketProcessor.class,"packets",service);
            var auth=new ApiV3AuthMidware();set(auth,ApiV3AuthMidware.class,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String value){if(!Set.of("both","read","activity","content").contains(value))return null;return new JSONObject().put("user",viewer("1")).put("scope",switch(value){case "both"->"activity.write content.write content.read";case "read"->"content.read";case "activity"->"activity.write";default->"content.write";});}});
            ApiV3ChatRedPacketProcessor.registerRoutes(processor,auth,new ApiV3WriteMidware());Dispatcher.mapping();
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.valueOf(method),path,io.netty.buffer.Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{raw.headers().set("Content-Type","application/json");if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw){public String getRemoteAddr(){return "192.0.2.1";}};request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>());
                var context=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));context.attr("user",viewer("4"));
                context.addHandler(new org.b3log.latke.http.handler.RouteHandler());context.addHandler(c->{var r=(org.b3log.latke.http.handler.RouteResolution)c.attr("MATCH_RESULT");try{r.getContextHandlerMeta().getInvokeHolder().invoke(processor,c);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}});context.handle();return context;
            }finally{raw.release();}
        }
        JSONObject legacy(String actor,String id)throws Exception{
            var bridge=new org.b3log.symphony.processor.LegacyRedPacketBridge();bridge.apiV3RedPackets=service;
            String body=new JSONObject().put("oId",id).put("apiKey","spoofed-other").toString();
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/chat-room/red-packet/open",io.netty.buffer.Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try{raw.headers().set("Content-Type","application/json");var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setJSON(new JSONObject(body));var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr(org.b3log.latke.model.User.USER,viewer(actor));bridge.openRedPacket(c);Assert.assertEquals(bridge.legacyFallbacks,0);return ((org.b3log.latke.http.renderer.JsonRenderer)c.getRenderer()).getJSONObject();}finally{raw.release();}
        }
    }
    interface Work{void run()throws Exception;}
    static void rejected(int code,Work work)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,code);}}
    static JSONObject average(){return new JSONObject().put("type","average").put("money",100).put("count",2).put("message","hello");}
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new F()){
            var created=f.create(average());Assert.assertEquals(created.getInt("principalPoints"),200);Assert.assertEquals(created.getInt("poolPoints"),194);Assert.assertEquals(created.getInt("taxPoints"),6);Assert.assertEquals(f.balance("1"),9800);Assert.assertEquals(f.balance("3"),10006);Assert.assertEquals(f.count("profession"),1);Assert.assertEquals(f.count("event"),1);
            Assert.assertEquals(f.create(average()).toString(),created.toString());Assert.assertEquals(f.balance("1"),9800);Assert.assertEquals(f.count("transfers"),2);
            var claim=f.service.claim(f.viewer("2"),"123","claim");Assert.assertEquals(claim.getInt("amount"),97);Assert.assertEquals(f.balance("2"),10097);Assert.assertEquals(f.service.claim(f.viewer("2"),"123","other-key").toString(),claim.toString());Assert.assertEquals(f.balance("2"),10097);
            f.begin();try{rejected(409,()->f.management.removeInCurrentTransaction("123",null));}finally{f.rollback();}
            f.service.claim(f.viewer("4"),"123","claim");var detail=f.service.detail(f.viewer("2"),"123");Assert.assertEquals(detail.getInt("got"),2);Assert.assertEquals(detail.getJSONArray("claims").length(),2);Assert.assertFalse(detail.toString().contains("PRIVATE_PHONE"));
            f.begin();Assert.assertTrue(f.management.removeInCurrentTransaction("123",null));f.commit();Assert.assertEquals(f.count("chat"),0);Assert.assertEquals(f.count("slots"),2);
        }
        System.out.println("PASS packet 1 real shared message/event/profession and points commit with tax conservation; idempotent creation/claims, pending deletion409, settled removal retains settlement");
        for(String fault:List.of("packets.after","slots.after","transfers.after","receipts.after","profession.after","event.after","commit"))try(var f=new F()){
            f.faults.add(fault);try{f.create(average());Assert.fail(fault);}catch(Exception expected){}Assert.assertEquals(f.balance("1"),10000);Assert.assertEquals(f.balance("3"),10000);for(String table:List.of("chat","event","profession","packets","slots","transfers","receipts"))Assert.assertEquals(f.count(table),0L,table+fault);
        }
        System.out.println("PASS packet 2 head/slot/funds/receipt/profession/event/strict commit failures roll back every durable state and never debit the sender");
        try(var f=new F()){
            f.create(new JSONObject().put("type","specify").put("money",100).put("recipientIds",new JSONArray(List.of("2","4"))).put("message","gift"));
            Assert.assertEquals(f.balance("1"),9800);Assert.assertEquals(f.balance("3"),10002);Assert.assertFalse(f.service.detail(f.viewer("2"),"123").has("recipientIds"));Assert.assertEquals(f.service.detail(f.viewer("1"),"123").getJSONArray("recipientIds").length(),2);
            String raw=query(f.reader,"SELECT content FROM chat").getFirst().getString("content");Assert.assertFalse(raw.contains("recipientIds"));Assert.assertFalse(raw.contains("recivers"));
            rejected(403,()->f.service.claim(f.viewer("3"),"123","outsider"));Assert.assertEquals(f.service.claim(f.viewer("2"),"123","only-me").getInt("amount"),99);Assert.assertEquals(f.count("notification"),2);Assert.assertEquals(query(f.reader,"SELECT hasRead FROM notification WHERE userId='2'").getFirst().getInt("hasRead"),1);
        }
        System.out.println("PASS packet 3 exclusive stable IDs and sender-only recipient projection; no assignment names in public message, outsiders denied and own notification read atomically");
        for(int n=1;n<=100;n++)for(int money:List.of(31,32,97,9932)){var values=ApiV3RedPacketInputs.allocate(money,n,new Random(1234+n));Assert.assertEquals(values.size(),n);Assert.assertEquals(values.stream().mapToInt(Integer::intValue).sum(),money);Assert.assertTrue(values.stream().allMatch(x->x>=0));}
        try(var f=new F()){exec(f.writer,"INSERT INTO membership VALUES('1','1','VIP4_YEAR',1,0)");var result=f.create(average());Assert.assertEquals(result.getInt("taxPoints"),0);Assert.assertEquals(result.getInt("poolPoints"),200);Assert.assertEquals(f.count("transfers"),1);}
        System.out.println("PASS packet 4 legacy allocation conservation across 400 boundaries, allowed zero amounts and exact permanent VIP4 exemption without tax transfer");
        for(String fault:List.of("slots.after","packets.after","transfers.after","receipts.after","data.after","event.after","commit"))try(var f=new F()){
            f.create(average());long events=f.count("event"),ledger=f.count("transfers");f.faults.add(fault);rejected(503,()->f.service.claim(f.viewer("2"),"123","failed-claim"));
            Assert.assertEquals(f.balance("2"),10000);Assert.assertEquals(f.count("event"),events);Assert.assertEquals(f.count("transfers"),ledger);Assert.assertEquals(query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"),0);Assert.assertTrue(query(f.reader,"SELECT claimUserId FROM slots").stream().allMatch(r->r.getString("claimUserId").isEmpty()));
            f.faults.clear();Assert.assertEquals(f.service.claim(f.viewer("2"),"123","failed-claim").getInt("amount"),97);
        }
        System.out.println("PASS packet 5 claiming failures roll back credited points, slot/head/message/event/receipt together; original key can recover exactly once");
        try(var f=new F()){
            final String path="/api/v3/chat/red-packets";
            for(String token:Arrays.asList(null,"bad","read","activity","content"))Assert.assertEquals(f.http("POST",path,token,"create",average().toString()).attr("test.status"),token==null||token.equals("bad")?401:403);
            Assert.assertEquals(f.count("packets"),0);Assert.assertEquals(f.http("POST",path,"both",null,average().toString()).attr("test.status"),422);
            Assert.assertEquals(f.http("POST",path,"both","bad",new JSONObject(average().toString()).put("userId","4").toString()).attr("test.status"),422);
            Assert.assertEquals(f.http("POST",path,"both","create",average().toString()).attr("test.status"),200);Assert.assertEquals(f.balance("1"),9800);Assert.assertEquals(f.balance("4"),10000);
            Assert.assertEquals(f.http("GET",path+"/123","activity",null,"").attr("test.status"),403);Assert.assertEquals(f.http("GET",path+"/123","read",null,"").attr("test.status"),200);
            Assert.assertEquals(f.http("POST",path+"/123/claims","read","claim","{}").attr("test.status"),403);
            Assert.assertEquals(f.http("POST",path+"/123/claims","activity","claim","{}").attr("test.status"),200);
        }
        System.out.println("PASS packet 6 real three Dispatcher routes/scopes/strict bodies and keys; no legacy identity fallback or userId override");
        try(var f=new F()){
            f.create(new JSONObject().put("type","specify").put("money",100).put("recipientIds",new JSONArray(List.of("2","4"))));
            var result=f.service.legacyOpen(f.viewer("2"),"123");Assert.assertEquals(result.keySet(),Set.of("who","info","recivers"));Assert.assertTrue(result.getJSONArray("recivers").isEmpty());Assert.assertEquals(f.balance("2"),10099);
            f.service.legacyOpen(f.viewer("2"),"123");Assert.assertEquals(f.balance("2"),10099);Assert.assertEquals(f.service.legacyOpen(f.viewer("1"),"123").getJSONArray("recivers").length(),2);
        }
        try(var f=new F()){exec(f.writer,"UPDATE user SET userStatus=4 WHERE oId='2'");rejected(404,()->f.create(new JSONObject().put("type","specify").put("money",100).put("recipientIds",new JSONArray(List.of("2")))));Assert.assertEquals(f.balance("1"),10000);}
        try(var f=new F()){exec(f.writer,"INSERT INTO membership VALUES('1','1','VIP4_YEAR_fake',1,0)");Assert.assertEquals(f.create(average()).getInt("taxPoints"),6);}
        for(JSONObject body:List.of(new JSONObject(average().toString()).put("type","heartbeat"),new JSONObject(average().toString()).put("money",31),new JSONObject(average().toString()).put("money",10241),new JSONObject(average().toString()).put("count",101),new JSONObject(average().toString()).put("message","a".repeat(21)),new JSONObject().put("type","specify").put("money",100).put("recipientIds",new JSONArray(List.of("2","2"))),new JSONObject().put("type","specify").put("money",100).put("recipientIds",new JSONArray(List.of("1")))))try(var f=new F()){rejected(422,()->f.create(body));Assert.assertEquals(f.count("packets"),0);}
        System.out.println("PASS packet 7 legacy success DTO and repeated opening share settlement; sender-only assignments, inactive recipients, exact VIP code and confirmed input boundaries");
        try(var f=new F()){
            f.create(average());var result=f.legacy("2","123");Assert.assertEquals(result.keySet(),Set.of("who","info","recivers"));Assert.assertEquals(f.balance("2"),10097);
            f.legacy("2","123");Assert.assertEquals(f.balance("2"),10097);Assert.assertEquals(query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"),1);
            exec(f.writer,"UPDATE packets SET got=3");f.begin();try{rejected(503,()->f.management.removeInCurrentTransaction("123",null));}finally{f.rollback();}
        }
        System.out.println("PASS packet 8 actual legacy handler bridge prefix uses authenticated attr despite body apiKey, renders original JSON shape, never falls into old settlement, and corrupted head cannot bypass deletion guard");
        try(var f=new F()){
            f.create(new JSONObject().put("type","random").put("money",32).put("count",100));
            int first=query(f.reader,"SELECT amount FROM slots WHERE ordinal=0").getFirst().getInt("amount");exec(f.writer,"UPDATE slots SET amount=amount+? WHERE ordinal=1",first);exec(f.writer,"UPDATE slots SET amount=0 WHERE ordinal=0");exec(f.writer,"UPDATE user SET userPhone='' WHERE oId='2'");
            long ledger=f.count("transfers");var result=f.service.claim(f.viewer("2"),"123","zero");Assert.assertEquals(result.getInt("amount"),0);Assert.assertEquals(f.balance("2"),10000);Assert.assertEquals(f.count("transfers"),ledger);Assert.assertEquals(f.service.claim(f.viewer("2"),"123","zero").toString(),result.toString());Assert.assertEquals(f.service.detail(f.viewer("2"),"123").getInt("got"),1);
        }
        try(var f=new F()){
            f.create(average());exec(f.writer,"INSERT INTO cloud VALUES('1','2','sys-mute','3000000')");rejected(403,()->f.service.claim(f.viewer("2"),"123","muted"));Assert.assertEquals(f.balance("2"),10000);Assert.assertEquals(query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"),0);
        }
        System.out.println("PASS packet 9 zero-share claim is durable/idempotent without a zero-value transfer, retains legacy no-phone claim eligibility, and current mute rejects before credit");
        try(var f=new F()){
            f.faults.add("ack");rejected(503,()->f.create(average()));Assert.assertEquals(f.balance("1"),9800);Assert.assertEquals(f.count("packets"),1);Assert.assertEquals(f.count("transfer_index"),0);
            f.faults.clear();f.create(average());Assert.assertEquals(f.balance("1"),9800);Assert.assertEquals(f.count("transfers"),2);Assert.assertEquals(f.count("transfer_index"),4);
            f.faults.add("ack");rejected(503,()->f.service.claim(f.viewer("2"),"123","ack-claim"));Assert.assertEquals(f.balance("2"),10097);
            f.faults.clear();Assert.assertEquals(f.service.claim(f.viewer("2"),"123","ack-claim").getInt("amount"),97);Assert.assertEquals(f.balance("2"),10097);Assert.assertEquals(query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"),1);
        }
        System.out.println("PASS packet 10 create and claim lost commit acknowledgements recover a single durable settlement and repair indexes on original-key replay");
    }
}
