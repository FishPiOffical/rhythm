package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.b3log.symphony.model.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.processor.ApiV3ChatProcessor;
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Actual v3 service/controller/idempotency/penalty/event/profession; see runner for explicit ancillary adapters. */
public class ApiV3ChatPublicationTest {
    static final long NOW=2_000_000;
    static final AtomicInteger IDS=new AtomicInteger();
    static class Fixed extends ApiV3ChatPublicationService {@Override protected long now(){return NOW;}}
    static class Fixture extends ChatMessageMgmtServiceTest.Publication {
        final String name="Writer"+IDS.incrementAndGet();
        final ApiV3RealtimeService barrageRealtime=new ApiV3RealtimeService();
        final ApiV3ChatBarrageService barrage=new ApiV3ChatBarrageService(){@Override protected long now(){return NOW;}};
        final ApiV3ChatTopicService topic=new ApiV3ChatTopicService(){@Override protected long now(){return NOW;}};
        final ChatTopicWriteService topicWrites=new ChatTopicWriteService();final ChatTopicMgmtService topicCore=new ChatTopicMgmtService();
        final Fixed publication=new Fixed();final ChatMessagePenaltyMgmtService penalties=new ChatMessagePenaltyMgmtService();
        final ChatMessageRestrictionQueryService restrictions=new ChatMessageRestrictionQueryService();
        final NotificationMgmtService notifications=new NotificationMgmtService();final ChatroomProcessor delivery=new ChatroomProcessor();
        final ApiV3ChatProcessor controller=new ApiV3ChatProcessor();final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        int begins,indexed,caches,evictions,transferAttempts,sequence;boolean retryEvent;String scope="content.write";
        Fixture()throws Exception {
            for(String column:List.of("userName TEXT","userNickname TEXT","userAvatarURL TEXT","userPhone TEXT","userRole TEXT","userStatus INTEGER","userPoint INTEGER","userLatestCmtTime BIGINT","userPassword TEXT"))exec(writer,"ALTER TABLE user ADD COLUMN "+column);
            exec(writer,"UPDATE user SET userName=?,userNickname='nickname',userAvatarURL='https://example.com/avatar.png',userPhone='PRIVATE-phone',userRole='member',userStatus=0,userPoint=100,userLatestCmtTime=0,userPassword='PRIVATE-password'",name);
            exec(writer,"CREATE TABLE option(oId TEXT PRIMARY KEY,optionValue TEXT,optionCategory TEXT)");
            exec(writer,"CREATE TABLE cloud(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");
            exec(writer,"CREATE TABLE request(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            exec(writer,"CREATE TABLE notification(oId TEXT PRIMARY KEY,content TEXT)");exec(writer,"CREATE TABLE transfer(oId TEXT PRIMARY KEY,amount INTEGER)");
            ChatroomChannel.sent.clear();ChatroomChannel.fail=false;ChatroomProcessor.barrageCost=5;ChatroomProcessor.barrageUnit="积分";
            final var userRepo=new UserRepository(){
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public JSONObject getForUpdate(String actor)throws RepositoryException {var list=query(writer,"SELECT * FROM user WHERE oId=?",actor);return list.isEmpty()?null:list.getFirst();}
                @Override public void updateColumnsInCurrentTransaction(String actor,JSONObject row)throws RepositoryException {
                    Assert.assertTrue(active());Assert.assertTrue(ArticlePublicationCaches.active());Assert.assertEquals(row.keySet(),Set.of("userLatestCmtTime"));exec(writer,"UPDATE user SET userLatestCmtTime=? WHERE oId=?",row.getLong("userLatestCmtTime"),actor);if(faults.contains("latest.after"))throw new RepositoryException("latest failed");
                }
                @Override public void refreshSettingsCache(String actor)throws RepositoryException {Assert.assertFalse(active());Assert.assertFalse(ArticlePublicationCaches.active());caches++;query(reader,"SELECT userPoint FROM user WHERE oId=?",actor);}
                @Override public void evictSettingsCaches(String actor){Assert.assertFalse(active());evictions++;}
            };
            final var cloudRepo=new CloudRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public String getName(){return "cloud";}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException {if(faults.contains("restriction.read"))throw new RepositoryException("restriction unavailable");return query(writer,sql,args);}
                @Override public String add(JSONObject row)throws RepositoryException {String id="c"+(++sequence);exec(writer,"INSERT INTO cloud VALUES(?,?,?,?)",id,row.getString("userId"),row.getString("gameId"),row.getString("data"));if(faults.contains("cloud.after"))throw new RepositoryException("cloud failed");return id;}
                @Override public void remove(String id)throws RepositoryException {exec(writer,"DELETE FROM cloud WHERE oId=?",id);}
                @Override public void update(String id,JSONObject row,String...columns)throws RepositoryException {Assert.assertEquals(columns,new String[]{"data"});exec(writer,"UPDATE cloud SET data=? WHERE oId=?",row.getString("data"),id);}
            };
            final var points=new PointtransferMgmtService(){
                @Override public String transferInCurrentTransaction(String from,String to,int kind,int amount,String dataId,long time,String memo,PointtransferSource source,boolean index) {
                    Assert.assertTrue(active());Assert.assertTrue(AccountPairLocks.lock(from,to).isHeldByCurrentThread());Assert.assertTrue(ArticlePublicationCaches.active());Assert.assertFalse(index);if(amount==5||amount==7)Assert.assertEquals(kind,Pointtransfer.TRANSFER_TYPE_C_CHAT_ROOM_SEND_BARRAGER);else if(amount==16)Assert.assertEquals(kind,Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_SET_DISCUSS);else {Assert.assertEquals(amount,30);Assert.assertEquals(kind,Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT);}transferAttempts++;
                    try{exec(writer,"UPDATE user SET userPoint=userPoint-? WHERE oId=?",amount,from);String id="t"+(++sequence);exec(writer,"INSERT INTO transfer VALUES(?,?)",id,amount);return faults.contains("transfer.null")?null:id;}catch(RepositoryException e){throw new IllegalStateException(e);}
                }
                @Override public void indexTransferAsync(String actor,String to,String id){Assert.assertFalse(active());Assert.assertFalse(ArticlePublicationCaches.active());indexed++;}
            };
            notifications.notificationRepository=new NotificationRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public String add(JSONObject row)throws RepositoryException {String id="n"+(++sequence);exec(writer,"INSERT INTO notification VALUES(?,?)",id,row.toString());if(faults.contains("notification.after"))throw new RepositoryException("notification failed");return id;}
            };
            final var requests=new ApiV3RequestRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public String getName(){return "request";}
                @Override public Transaction beginTransaction(){try{begins++;if(retryEvent&&begins>2)faults.remove("event.before");begin();return ApiV3Transactions.strict(tx);}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql,args);}
                @Override public String add(JSONObject row)throws RepositoryException {exec(writer,"INSERT INTO request VALUES(?,?,?,?,?,?)",row.getString("oId"),row.getString("userId"),row.getString("operation"),row.getString("requestHash"),row.getString("responseJson"),row.getLong("createdAt"));return row.getString("oId");}
                @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException {exec(writer,"UPDATE request SET responseJson=? WHERE oId=?",row.getString("responseJson"),id);if(faults.contains("request.after")||faults.remove("request.once"))throw new RepositoryException("request update failed");}
            };
            final var idempotency=new ApiV3IdempotencyMgmtService();inject(idempotency,ApiV3IdempotencyMgmtService.class,"requestRepository",requests);
            final var messageRepo=new Messages(){@Override public Transaction beginTransaction(){try{begin();if(faults.contains("notice.event"))faults.add("event.before");if(faults.contains("notice.commit"))faults.add("commit");return tx;}catch(Exception e){throw new IllegalStateException(e);}}@Override protected String insertMessage(JSONObject row)throws RepositoryException {return super.insertMessage(new JSONObject(row.toString()).put("oId",Integer.toString(1000+(++sequence))));}};
            inject(messageRepo,ChatRoomRepository.class,"changes",events);inject(management,ChatMessageMgmtService.class,"messages",messageRepo);
            inject(restrictions,ChatMessageRestrictionQueryService.class,"cloud",cloudRepo);
            for(var e:Map.<String,Object>of("cloud",cloudRepo,"users",userRepo,"points",points,"notifications",notifications).entrySet())inject(penalties,ChatMessagePenaltyMgmtService.class,e.getKey(),e.getValue());
            delivery.chatRoomRepository=messageRepo;delivery.notificationMgmtService=notifications;
            final var validation=new ChatMessageValidationService(){@Override protected long now(){return NOW;}};
            for(var e:Map.<String,Object>ofEntries(Map.entry("requests",requests),Map.entry("users",userRepo),Map.entry("points",points),Map.entry("idempotency",idempotency),Map.entry("validation",validation),Map.entry("restrictions",restrictions),Map.entry("penalties",penalties),Map.entry("messages",management),Map.entry("cloud",new CloudService()),Map.entry("notifications",notifications),Map.entry("delivery",delivery)).entrySet())inject(publication,ApiV3ChatPublicationService.class,e.getKey(),e.getValue());
            final var notices=new ChatMessageNoticeService();inject(notices,ChatMessageNoticeService.class,"messages",messageRepo);inject(notices,ChatMessageNoticeService.class,"delivery",delivery);inject(publication,ApiV3ChatPublicationService.class,"notices",notices);
            final var optionRepo=new OptionRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public String getName(){return "option";}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(active()?writer:reader,sql,args);}
                @Override public JSONObject getForUpdate(String id)throws RepositoryException {var rows=query(writer,"SELECT * FROM option WHERE oId=?",id);return rows.isEmpty()?null:rows.getFirst();}
                @Override public String add(JSONObject row)throws RepositoryException{exec(writer,"INSERT INTO option VALUES(?,?,?)",row.getString("oId"),row.getString("optionValue"),row.getString("optionCategory"));if(faults.contains("option.after"))throw new RepositoryException("option failed");return row.getString("oId");}
                @Override public void update(String id,JSONObject row,String...columns)throws RepositoryException {Assert.assertEquals(columns,new String[]{"optionValue"});exec(writer,"UPDATE option SET optionValue=? WHERE oId=?",row.getString("optionValue"),id);if(faults.contains("option.after"))throw new RepositoryException("option failed");}
            };
            for(var e:Map.<String,Object>of("users",userRepo,"options",optionRepo,"head",state,"events",events,"points",points).entrySet())inject(topicCore,ChatTopicMgmtService.class,e.getKey(),e.getValue());
            final var projection=new ChatTopicDeliveryService();inject(projection,ChatTopicDeliveryService.class,"topics",topicCore);
            for(var e:Map.<String,Object>of("users",userRepo,"topics",topicCore,"delivery",projection,"points",points).entrySet())inject(topicWrites,ChatTopicWriteService.class,e.getKey(),e.getValue());
            for(var e:Map.<String,Object>ofEntries(Map.entry("users",userRepo),Map.entry("writes",topicWrites),Map.entry("topics",topicCore),Map.entry("idempotency",idempotency),Map.entry("validation",validation),Map.entry("restrictions",restrictions),Map.entry("penalties",penalties),Map.entry("publication",publication)).entrySet())inject(topic,ApiV3ChatTopicService.class,e.getKey(),e.getValue());
            for(var e:Map.<String,Object>of("users",userRepo,"points",points,"idempotency",idempotency,"validation",validation,"restrictions",restrictions,"delivery",delivery,"realtime",barrageRealtime).entrySet())inject(barrage,ApiV3ChatBarrageService.class,e.getKey(),e.getValue());
            inject(controller,ApiV3ChatProcessor.class,"barrages",barrage);
            inject(controller,ApiV3ChatProcessor.class,"topics",topic);
            inject(controller,ApiV3ChatProcessor.class,"publication",publication);
            inject(auth,ApiV3AuthMidware.class,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){@Override public JSONObject authenticate(String token){return token.equals("VALID")?new JSONObject().put("scope",scope).put("user",viewer()):null;}});
        }
        @Override void begin()throws Exception {
            super.begin();
            if(faults.remove("commit.lost")){
                final var original=tx.getConnection();
                final var wrapped=(java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Connection.class},(proxy,method,args)->{
                    try {Object value=method.invoke(original,args);if(method.getName().equals("commit"))throw new java.sql.SQLException("lost successful commit acknowledgement");return value;}
                    catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
                var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,wrapped);
            }
        }
        JSONObject viewer(){return new JSONObject().put("oId","1").put("userName","FORGED-name").put("userPhone","").put("userRole","admin");}
        JSONObject send(String text,String key)throws Exception{return publication.publish(viewer(),text,"Web/Test",key);}
        void restriction(String game,String data)throws Exception{exec(writer,"INSERT INTO cloud VALUES(?,?,?,?)","c"+(++sequence),"1",game,data);}
        int count(String table)throws Exception{return query(reader,"SELECT COUNT(*) AS count FROM "+table).getFirst().getInt("count");}
        int balance()throws Exception{return query(reader,"SELECT userPoint FROM user WHERE oId='1'").getFirst().getInt("userPoint");}
        void noPublication()throws Exception {Assert.assertEquals(count("chat"),0);Assert.assertEquals(count("event"),0);Assert.assertEquals(count("profession"),0);Assert.assertEquals(count("request"),0);Assert.assertEquals(count("notification"),0);Assert.assertEquals(count("transfer"),0);Assert.assertEquals(balance(),100);Assert.assertTrue(ChatroomChannel.sent.isEmpty());Assert.assertEquals(delivery.activities,0);Assert.assertEquals(indexed,0);Assert.assertEquals(notifications.refreshes,0);Assert.assertFalse(active());Assert.assertFalse(ArticlePublicationCaches.active());}
        void warmRepeat(String content){ChatMessageAbuseState.accepted(content);for(int i=0;i<10;i++)if(ChatMessageAbuseState.repeated(name,content)==ChatMessageAbuseState.Repeat.WARN)return;Assert.fail("warning boundary not reached");}
        JSONObject request(String token,String type,String raw,String key)throws Exception {return request(token,type,raw,key,false);}
        JSONObject request(String token,String type,String raw,String key,boolean topicRequest)throws Exception {return request(token,type,raw,key,topicRequest?1:0);}
        JSONObject request(String token,String type,String raw,String key,int operation)throws Exception {
            var req=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/chat/messages?apiKey=legacy");
            var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
            try{req.headers().set("Cookie","JSESSIONID=legacy");if(token!=null)req.headers().set("Authorization",token);if(type!=null)req.headers().set("Content-Type",type);if(key!=null)req.headers().set("Idempotency-Key",key);
                var request=new Request(null,req);request.setBytes(raw.getBytes(StandardCharsets.UTF_8));var c=new RequestContext(request,new Response(null,response));c.attr(User.USER,new JSONObject().put("oId","2"));if(operation==4)c.addHandler(controller::sendWeather);else if(operation==3)c.addHandler(controller::sendMusic);else if(operation==2)c.addHandler(controller::sendBarrage);else if(operation==1)c.addHandler(controller::setTopic);else c.addHandler(controller::create);auth.contentWrite(c);
                var out=new JSONObject().put("status",c.attr("test.status"));if(c.attr("test.code")!=null)out.put("code",c.attr("test.code"));if(c.attr("test.data")!=null)out.put("data",c.attr("test.data"));
                if(out.getInt("status")==200){Assert.assertEquals(response.headers().get("Cache-Control"),"no-store");Assert.assertEquals(response.headers().get("Vary"),"Authorization");}return out;
            }finally{req.release();}
        }
    }
    interface Work{void run()throws Exception;}
    static void rejected(int status,String code,Work work)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertEquals(e.code,code);}}
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);Latkes.setServerScheme("http");Latkes.setServerHost("localhost");Latkes.setServerPort("8080");Latkes.setContextPath("");
        try(var f=new Fixture()){
            f.delivery.mentionUsers=List.of(new JSONObject().put("oId","2"),new JSONObject().put("oId","2"),new JSONObject());
            var first=f.send(" hello 😀 ","same-key");Assert.assertEquals(first.keySet(),Set.of("id","createdAt"));Assert.assertEquals(first.getLong("createdAt"),NOW);
            Assert.assertEquals(f.count("chat"),1);Assert.assertEquals(f.count("event"),1);Assert.assertEquals(f.count("profession"),1);Assert.assertEquals(f.count("request"),1);Assert.assertEquals(f.delivery.activities,1);Assert.assertEquals(ChatroomChannel.sent.size(),1);Assert.assertEquals(f.notifications.mentions.size(),1);
            var stored=new JSONObject(query(f.reader,"SELECT content FROM chat").getFirst().getString("content"));Assert.assertEquals(stored.getString("userName"),f.name);Assert.assertEquals(stored.getLong("userOId"),1L);Assert.assertEquals(stored.getString("content"),"hello 😀");Assert.assertEquals(stored.getString("client"),"Web/Test");Assert.assertFalse(stored.toString().contains("PRIVATE"));Assert.assertFalse(stored.toString().contains("FORGED"));
            Assert.assertEquals(query(f.reader,"SELECT userLatestCmtTime FROM user").getFirst().getLong("userLatestCmtTime"),NOW);
            f.restriction("sys-mute",Long.toString(NOW+1000));Assert.assertEquals(f.send(" hello 😀 ","same-key").toString(),first.toString());Assert.assertEquals(f.count("chat"),1);Assert.assertEquals(ChatroomChannel.sent.size(),1);Assert.assertEquals(f.delivery.activities,1);
            rejected(409,"idempotency_conflict",()->f.send("other","same-key"));
        }
        try(var f=new Fixture()){
            exec(f.writer,"INSERT INTO user(oId,userName,userNickname,userAvatarURL,userPhone,userRole,userStatus,userPoint,userLatestCmtTime) VALUES('2','Second','Second','','bound','member',0,100,0)");
            var one=f.send("same content","shared-key");var two=f.publication.publish(new JSONObject().put("oId","2"),"same content","Web/Test","shared-key");
            Assert.assertNotEquals(one.getString("id"),two.getString("id"));Assert.assertEquals(f.count("request"),2);Assert.assertEquals(f.count("profession"),2);
        }
        System.out.println("PASS real publication commits message/profession/event/latest-time/replay result, derives author from current locked profile and replays once without repeating activity, mentions or broadcast even after mute");
        try(var f=new Fixture()){
            for(String command:List.of("[redpacket]x[/redpacket]","[music]x[/music]","[weather]x[/weather]","[setdiscuss]x[/setdiscuss]","[barrager]x[/barrager]","执法 禁言 x 1","zf jy x 1","{\"msgType\":\"redPacket\"}"))rejected(422,"special_message_required",()->f.send(command,"command"));
            ReservedWords.process=s->"[weather]x[/weather]";try{rejected(422,"special_message_required",()->f.send("innocent","processed"));}finally{ReservedWords.process=s->s;}f.noPublication();
            exec(f.writer,"UPDATE user SET userPhone='' WHERE oId='1'");rejected(403,"phone_required",()->f.send("hello","phone"));exec(f.writer,"UPDATE user SET userPhone='bound',userStatus=1 WHERE oId='1'");rejected(404,"not_found",()->f.send("hello","status"));
        }
        System.out.println("PASS raw and post-moderation business commands reject without execution/storage, and stale Bearer profile cannot bypass current phone or status requirements");
        try(var f=new Fixture()){
            f.restriction("sys-risk",Long.toString(NOW+1000));rejected(409,"confirmation_required",()->f.send("risk ordinary text","risk-key"));Assert.assertEquals(f.count("request"),0);
            f.faults.add("event.before");f.retryEvent=true;var result=f.send("risk ordinary text","risk-key");Assert.assertTrue(result.has("id"));Assert.assertTrue(f.begins>=3);Assert.assertEquals(f.count("chat"),1);Assert.assertEquals(ChatroomChannel.sent.size(),1);
            Assert.assertEquals(f.send("risk ordinary text","risk-key").toString(),result.toString());
        }
        try(var f=new Fixture()){
            f.restriction("sys-risk",Long.toString(NOW+1000));ChatMessageAbuseState.confirmRisk("1","original filtered text");
            f.faults.add("event.before");f.retryEvent=true;final var calls=new AtomicInteger();
            ReservedWords.process=text->calls.incrementAndGet()==1?"original filtered text":"changed filtered text";
            try{rejected(409,"confirmation_required",()->f.send("constant original body","changed-filter"));f.noPublication();}
            finally{ReservedWords.process=text->text;}
        }
        System.out.println("PASS risk confirmation retries the same body/key and storage retry reuses its confirmation rather than requesting a third click; committed replay bypasses policy side effects");
        try(var f=new Fixture()){
            String text=f.name+" repeat";f.warmRepeat(text);var result=f.send(text,"penalty");Assert.assertEquals(f.balance(),70);Assert.assertEquals(f.count("transfer"),1);Assert.assertEquals(f.count("notification"),1);Assert.assertEquals(f.count("cloud"),2);Assert.assertEquals(f.indexed,1);Assert.assertEquals(f.notifications.refreshes,1);Assert.assertEquals(ChatroomChannel.sent.size(),2);
            var mute=query(f.reader,"SELECT data FROM cloud WHERE gameId='sys-mute'").getFirst().getString("data");Assert.assertEquals(Long.parseLong(mute),NOW+900000);
            var notification=new JSONObject(query(f.reader,"SELECT content FROM notification").getFirst().getString("content"));Assert.assertEquals(notification.getInt("dataType"),Notification.DATA_TYPE_C_ABUSE_POINT_DEDUCT);Assert.assertEquals(notification.getString("userId"),"1");
            Assert.assertEquals(f.send(text,"penalty").toString(),result.toString());Assert.assertEquals(f.balance(),70);Assert.assertEquals(f.indexed,1);Assert.assertEquals(ChatroomChannel.sent.size(),2);rejected(403,"chat_muted",()->f.send("other text","new-key"));
        }
        try(var f=new Fixture()){
            exec(f.writer,"UPDATE user SET userPoint=10 WHERE oId='1'");String text=f.name+" poor repeat";f.warmRepeat(text);f.send(text,"poor");Assert.assertEquals(f.balance(),10);Assert.assertEquals(f.count("cloud"),2);Assert.assertEquals(f.count("transfer"),0);Assert.assertEquals(f.count("notification"),0);Assert.assertTrue(ChatroomChannel.sent.getFirst().getString("md").contains("未扣分"));
        }
        System.out.println("PASS repeat penalty commits mute registry, deduction and notification with the message; replay cannot deduct twice, while an insufficient account remains muted without an invented transfer");
        for(String fault:List.of("cloud.after","transfer.null","notification.after","profession.after","latest.after","request.after","commit"))try(var f=new Fixture()){
            String text=f.name+" fail repeat";f.warmRepeat(text);f.faults.add(fault);try{f.send(text,"failure");Assert.fail(fault);}catch(Exception expected){}f.noPublication();Assert.assertEquals(f.count("cloud"),0);
        }
        try(var f=new Fixture()){
            f.faults.add("restriction.read");try{f.send("hello","restriction");Assert.fail();}catch(RepositoryException expected){}f.noPublication();
        }
        System.out.println("PASS cloud/partial-transfer/notification/profession/latest-time/idempotency/commit failures roll back every durable effect, release caches/locks and publish no success side effects");
        try(var f=new Fixture()){
            var result=f.send("<script>bad()</script><img src='x' onerror='bad()'><b>visible</b>","html");Assert.assertTrue(result.has("id"));String html=ChatroomChannel.sent.getFirst().getString("content");Assert.assertFalse(html.contains("<script"));Assert.assertFalse(html.contains("onerror"));Assert.assertTrue(html.contains("visible"));
            Assert.assertEquals(ChatroomChannel.sent.getFirst().keySet(),Set.of("content","md","oId","userName","userNickname","userAvatarURL","userOId","sysMetal","client","time","reactionSummary"));
        }
        try(var f=new Fixture()){
            ChatroomChannel.fail=true;var result=f.send("delivery failure","lost-broadcast");Assert.assertTrue(result.has("id"));Assert.assertEquals(f.count("chat"),1);Assert.assertEquals(f.send("delivery failure","lost-broadcast").toString(),result.toString());Assert.assertEquals(f.delivery.activities,1);
        }
        System.out.println("PASS extracted production postcommit adapter sanitizes HTML and projects legacy fields; channel failure cannot undo a committed send or trigger duplicate replay effects");
        try(var f=new Fixture()){
            String body="{\"content\":\"controller text\",\"client\":\"Web/Test\"}";
            Assert.assertEquals(f.request(null,"application/json",body,"key").getInt("status"),401);f.scope="content.read";Assert.assertEquals(f.request("Bearer VALID","application/json",body,"key").getInt("status"),403);f.scope="content.write";
            Assert.assertEquals(f.request("Bearer VALID","text/plain",body,"key").getInt("status"),415);
            Assert.assertEquals(f.request("Bearer VALID","application/json",body,null).getInt("status"),422);
            for(String bad:List.of("{}","{\"content\":7}","{\"content\":\"hello\",\"userId\":\"2\"}","{\"content\":\"hello\",\"apiKey\":\"old\"}","{\"content\":\"hello\",\"client\":7}"))Assert.assertEquals(f.request("Bearer VALID","application/json",bad,"key").getInt("status"),422);
            Assert.assertEquals(f.request("Bearer VALID","application/json","{\"content\":\"first\",\"content\":\"second\"}","key").getInt("status"),400);
            Assert.assertEquals(f.count("chat"),0);Assert.assertEquals(f.request("Bearer VALID","application/json",body,"key").getInt("status"),200);Assert.assertEquals(f.request("Bearer VALID","application/json",body,"key").getInt("status"),200);Assert.assertEquals(f.count("chat"),1);
        }
        for(String fault:List.of("notice.event","notice.commit"))try(var f=new Fixture()){
            String text=f.name+" failed notice";f.warmRepeat(text);f.faults.add(fault);var result=f.send(text,"notice-failure");
            Assert.assertEquals(f.count("chat"),1);Assert.assertEquals(f.count("event"),1);Assert.assertEquals(f.count("profession"),1);Assert.assertEquals(f.balance(),70);Assert.assertEquals(ChatroomChannel.sent.size(),1);Assert.assertEquals(f.delivery.activities,1);Assert.assertFalse(f.active());
            f.faults.clear();Assert.assertEquals(f.send(text,"notice-failure").toString(),result.toString());Assert.assertEquals(f.count("chat"),1);
        }
        System.out.println("PASS system notice event/strict-commit failures roll back only that notice, return JDBC resources and preserve the already-committed user message/penalty without replaying side effects");
        try(var f=new Fixture()){
            f.faults.add("commit.lost");try{f.send("commit acknowledgement lost","unknown-result");Assert.fail();}catch(IllegalStateException expected){}
            Assert.assertFalse(f.active());Assert.assertEquals(f.count("chat"),1);Assert.assertEquals(f.count("event"),1);Assert.assertEquals(f.count("profession"),1);Assert.assertEquals(f.count("request"),1);Assert.assertTrue(ChatroomChannel.sent.isEmpty());Assert.assertEquals(f.delivery.activities,0);
            var result=f.send("commit acknowledgement lost","unknown-result");Assert.assertTrue(result.has("id"));Assert.assertEquals(f.count("chat"),1);Assert.assertTrue(ChatroomChannel.sent.isEmpty());Assert.assertEquals(f.delivery.activities,0);
        }
        System.out.println("PASS lost successful commit acknowledgement resolves by the original key/body to one durable result, with no speculative or repeated postcommit effects");
        System.out.println("PASS actual controller/auth uses content.write Bearer and strict JSON/body/key validation with private headers, ignores legacy Cookie/query identity and returns replayed IDs");
    }
}
