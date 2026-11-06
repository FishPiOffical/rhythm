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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.service.ChatMessageWithdrawalMgmtService;
import org.b3log.symphony.service.PointtransferMgmtService;
import org.b3log.symphony.service.ApiV3IdempotencyMgmtService;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.AccountPairLocks;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;
import org.testng.Assert;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Callable;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.ChatMessageRemovalTest.reject;

/** Real compound withdrawal and SQL/savepoints; user persistence and transfer have fault-injecting SQLite adapters. */
public class ChatMessageWithdrawalTest {
    static class Withdrawal extends ChatMessageRemovalTest.Removal {
        long clock=LocalDate.of(2026,10,18).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        int delivered,indexed,refreshed;
        final ChatMessageWithdrawalMgmtService withdrawals=new ChatMessageWithdrawalMgmtService(){
            @Override protected long now(){return clock;}
            @Override protected void afterWithdrawn(Result result){Assert.assertFalse(active());Assert.assertFalse(ArticlePublicationCaches.active());delivered++;super.afterWithdrawn(result);}
        };
        Withdrawal()throws Exception {
            exec(writer,"ALTER TABLE user ADD COLUMN userRole TEXT");exec(writer,"ALTER TABLE user ADD COLUMN userPoint INTEGER");exec(writer,"ALTER TABLE user ADD COLUMN userStatus INTEGER");
            exec(writer,"UPDATE user SET userRole='member',userPoint=1000,userStatus=0");
            exec(writer,"CREATE TABLE cloud(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");
            exec(writer,"CREATE TABLE transfers(oId TEXT PRIMARY KEY,cost INTEGER)");
            exec(writer,"CREATE TABLE requests(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            inject(withdrawals,ChatMessageWithdrawalMgmtService.class,"messages",management);
            inject(withdrawals,ChatMessageWithdrawalMgmtService.class,"users",new UserRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public JSONObject getForUpdate(String id)throws RepositoryException{var rows=query(writer,"SELECT * FROM user WHERE oId=?",id);return rows.isEmpty()?null:rows.getFirst();}
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public void refreshSettingsCache(String id){Assert.assertFalse(active());Assert.assertFalse(ArticlePublicationCaches.active());refreshed++;}
            });
            inject(withdrawals,ChatMessageWithdrawalMgmtService.class,"cloud",new CloudRepository(){
                @Override public String getName(){return "cloud";}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{if(faults.contains("counter.read"))throw new RepositoryException("counter unavailable");return query(writer,sql,args);}
                @Override public String add(JSONObject row)throws RepositoryException{exec(writer,"INSERT INTO cloud VALUES(?,?,?,?)","counter",row.getString("userId"),row.getString("gameId"),row.getString("data"));if(faults.contains("counter.after"))throw new RepositoryException("counter lost ack");return "counter";}
                @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{exec(writer,"UPDATE cloud SET data=? WHERE oId=?",row.getString("data"),id);if(faults.contains("counter.after"))throw new RepositoryException("counter update lost ack");}
            });
            inject(withdrawals,ChatMessageWithdrawalMgmtService.class,"points",new PointtransferMgmtService(){
                @Override public void indexTransferAsync(String from,String to,String id){Assert.assertFalse(active());Assert.assertFalse(ArticlePublicationCaches.active());indexed++;}
                @Override public String transferInCurrentTransaction(String from,String to,int type,int sum,String data,long time,String memo,PointtransferSource source,boolean index){
                    Assert.assertEquals(from,"1");Assert.assertEquals(to,Pointtransfer.ID_C_SYS);Assert.assertEquals(type,Pointtransfer.TRANSFER_TYPE_C_CHAT_ROOM_REVOKE);Assert.assertFalse(index);Assert.assertEquals(source,PointtransferSource.EMPTY);
                    try{
                        exec(writer,"UPDATE user SET userPoint=userPoint-? WHERE oId=?",sum,from);
                        if(faults.contains("transfer.partial"))return null;
                        String id=UUID.randomUUID().toString();exec(writer,"INSERT INTO transfers VALUES(?,?)",id,sum);return id;
                    }catch(RepositoryException e){throw new IllegalStateException(e);}
                }
            });
            final var idempotency=new ApiV3IdempotencyMgmtService();
            inject(idempotency,ApiV3IdempotencyMgmtService.class,"requestRepository",new ApiV3RequestRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public Transaction beginTransaction(){try{begin();return ApiV3Transactions.strict(tx);}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public JSONObject getForUpdate(String id)throws RepositoryException{var rows=query(writer,"SELECT * FROM requests WHERE oId=?",id);return rows.isEmpty()?null:rows.getFirst();}
                @Override public String add(JSONObject row)throws RepositoryException{exec(writer,"INSERT INTO requests VALUES(?,?,?,?,?,?)",row.getString("oId"),row.getString("userId"),row.getString("operation"),row.getString("requestHash"),row.getString("responseJson"),row.getLong("createdAt"));return row.getString("oId");}
                @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{exec(writer,"UPDATE requests SET responseJson=? WHERE oId=?",row.getString("responseJson"),id);if(faults.contains("receipt.after"))throw new RepositoryException("receipt lost ack");}
            });
            inject(withdrawals,ChatMessageWithdrawalMgmtService.class,"idempotency",idempotency);
        }
        String all()throws Exception{return full()+query(reader,"SELECT * FROM cloud ORDER BY oId")+query(reader,"SELECT * FROM user ORDER BY oId")+query(reader,"SELECT * FROM transfers ORDER BY oId")+query(reader,"SELECT * FROM requests ORDER BY oId");}
        void counter(String raw)throws Exception{exec(writer,"INSERT INTO cloud VALUES('counter','1',?,?)",ChatMessageWithdrawalMgmtService.COUNTER_GAME_ID,raw);}
        <T>T owned(Callable<T> work)throws Exception{return AccountPairLocks.execute(List.of(new AccountPairLocks.Pair("1",Pointtransfer.ID_C_SYS)),()->{
            try(var cache=ArticlePublicationCaches.open(this::active)){begin();try{var value=work.call();commit();return value;}finally{if(active())rollback();}}
        });}
        ChatMessageWithdrawalMgmtService.Result remove(boolean moderation)throws Exception{return owned(()->withdrawals.withdrawInCurrentTransaction("1","123",moderation));}
        @Override void begin()throws Exception {
            super.begin();
            if(faults.remove("commit.lost")){
                final var original=tx.getConnection();
                final var wrapped=(java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Connection.class},(proxy,method,args)->{
                    try{Object value=method.invoke(original,args);if(method.getName().equals("commit"))throw new java.sql.SQLException("lost successful commit acknowledgement");return value;}
                    catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
                var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,wrapped);
            }
        }
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        for(int count=0;count<3;count++)try(var f=new Withdrawal()){
            f.seed(2);if(count>0)f.counter(new JSONObject().put("day","2026-10-18").put("count",count).toString());
            String before=f.all();final int expected=count;
            f.owned(()->{var r=f.withdrawals.withdrawInCurrentTransaction("1","123",false);Assert.assertEquals(r.cost(),expected*32);Assert.assertFalse(r.privileged());Assert.assertFalse(r.transferId().isEmpty());Assert.assertEquals(f.all(),before);Assert.assertEquals(query(f.second,"SELECT * FROM transfers").size(),0);return r;});
            Assert.assertTrue(query(f.reader,"SELECT * FROM chat").isEmpty());Assert.assertEquals(query(f.reader,"SELECT userPoint FROM user").getFirst().getInt("userPoint"),1000-count*32);
            var counter=new JSONObject(query(f.reader,"SELECT data FROM cloud").getFirst().getString("data"));Assert.assertEquals(counter.getInt("count"),count+1);Assert.assertEquals(counter.getString("day"),"2026-10-18");Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),1);
            String after=f.all();reject(404,"not_found",()->f.remove(false));Assert.assertEquals(f.all(),after);
        }
        System.out.println("PASS compound withdrawal commits fee, ledger including zero cost, daily count, deletion, event, reactions and profession reversal together; observers see none before commit and missing targets never charge");
        try(var f=new Withdrawal()){
            f.seed(1);Assert.assertEquals(f.remove(false).cost(),0);
            final var restarted=new ChatMessageWithdrawalMgmtService(){@Override protected long now(){return f.clock;}};
            for(String name:List.of("users","cloud","messages","points")){
                var field=ChatMessageWithdrawalMgmtService.class.getDeclaredField(name);field.setAccessible(true);inject(restarted,ChatMessageWithdrawalMgmtService.class,name,field.get(f.withdrawals));
            }
            for(int count=1;count<=2;count++){
                String id=String.valueOf(123+count);exec(f.writer,"INSERT INTO chat VALUES(?,?)",id,new JSONObject().put("userOId","1").put("userName","OldName").put("content","next").toString());
                Assert.assertEquals(f.owned(()->restarted.withdrawInCurrentTransaction("1",id,false)).cost(),count*32);
            }
            Assert.assertEquals(new JSONObject(query(f.reader,"SELECT data FROM cloud").getFirst().getString("data")).getInt("count"),3);
            Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),3);Assert.assertEquals(query(f.reader,"SELECT userPoint FROM user").getFirst().getInt("userPoint"),904);
        }
        System.out.println("PASS successive different messages share the durable 0/32/64 counter across fresh service instances and historical display names");
        for(String fault:List.of("counter.read","counter.after","transfer.partial","data.after","event.after","reaction.after","reversal.lookup"))try(var f=new Withdrawal()){
            f.seed(2);f.counter("{\"day\":\"2026-10-18\",\"count\":2}");String before=f.all();f.faults.add(fault);
            f.owned(()->{try{f.withdrawals.withdrawInCurrentTransaction("1","123",false);Assert.fail(fault);}catch(RepositoryException|RuntimeException expected){}return null;});Assert.assertEquals(f.all(),before,fault);
        }
        try(var f=new Withdrawal()){f.seed(1);String before=f.all();f.faults.add("commit");try{f.remove(false);Assert.fail();}catch(RuntimeException expected){}Assert.assertEquals(f.all(),before);Assert.assertFalse(f.active());}
        System.out.println("PASS partial transfer, counter, deletion and profession failures restore fee and counter even if caller catches and commits; rejected root commit preserves all state");
        for(String raw:List.of("{}","bad","{\"day\":\"2026-10-18\",\"count\":-1}","{\"day\":\"2026-10-18\",\"count\":\"1\"}","{\"day\":\"2026-10-18\",\"count\":1.5}","{\"day\":\"2026-10-19\",\"count\":1}","{\"day\":\"2026-10-18\",\"count\":2147483647}"))try(var f=new Withdrawal()){
            f.seed(1);f.counter(raw);String before=f.all();try{f.remove(false);Assert.fail(raw);}catch(RuntimeException expected){}Assert.assertEquals(f.all(),before);
        }
        try(var f=new Withdrawal()){f.seed(1);f.counter("{\"day\":\"2026-10-18\",\"count\":1}");exec(f.writer,"INSERT INTO cloud SELECT 'duplicate',userId,gameId,data FROM cloud");String before=f.all();try{f.remove(false);Assert.fail();}catch(IllegalStateException expected){}Assert.assertEquals(f.all(),before);}
        try(var f=new Withdrawal()){f.seed(1);f.counter("{\"day\":\"2025-12-31\",\"count\":5}");Assert.assertEquals(f.remove(false).cost(),0);}
        System.out.println("PASS corrupt, duplicate, future and overflowing counters fail closed without charging; a new natural day resets the fee");
        try(var f=new Withdrawal()){f.seed(1);f.counter("{\"day\":\"2026-10-18\",\"count\":1}");exec(f.writer,"UPDATE user SET userPoint=31");String before=f.all();reject(409,"insufficient_points",()->f.remove(false));Assert.assertEquals(f.all(),before);}
        try(var f=new Withdrawal()){f.seed(1);exec(f.writer,"UPDATE user SET userPoint=-1");String before=f.all();reject(409,"insufficient_points",()->f.remove(false));Assert.assertEquals(f.all(),before);}
        for(int state:List.of(1,2))try(var f=new Withdrawal()){f.seed(1);exec(f.writer,"UPDATE user SET userStatus=?",state);String before=f.all();reject(404,"not_found",()->f.remove(false));Assert.assertEquals(f.all(),before);}
        try(var f=new Withdrawal()){f.seed(1);exec(f.writer,"UPDATE chat SET content=?",new JSONObject().put("userOId","2").put("userName","Writer").put("content","other").toString());String before=f.all();reject(404,"not_found",()->f.remove(true));Assert.assertEquals(f.all(),before);}
        System.out.println("PASS current invalid users, insufficient or negative first-free balance, and username-reuse ownership cannot debit or delete; moderation flag alone grants no privilege");
        for(boolean moderation:List.of(false,true))try(var f=new Withdrawal()){
            f.seed(1);f.counter("corrupt ordinary counter");exec(f.writer,"UPDATE user SET userRole='adminRole',userPoint=-1");
            if(moderation)exec(f.writer,"UPDATE chat SET content=?",new JSONObject().put("userOId","2").put("content","other").toString());
            var r=f.remove(moderation);Assert.assertTrue(r.privileged());Assert.assertEquals(r.cost(),0);Assert.assertEquals(r.transferId(),"");Assert.assertTrue(query(f.reader,"SELECT * FROM transfers").isEmpty());Assert.assertEquals(query(f.reader,"SELECT data FROM cloud").getFirst().getString("data"),"corrupt ordinary counter");
        }
        try(var f=new Withdrawal()){f.seed(1);exec(f.writer,"UPDATE user SET userRole='adminRole'");exec(f.writer,"UPDATE chat SET content=?",new JSONObject().put("userOId","2").put("content",new JSONObject().put("msgType","redPacket")).toString());String before=f.all();reject(409,"message_not_removable",()->f.remove(true));Assert.assertEquals(f.all(),before);}
        try(var f=new Withdrawal()){f.seed(1);try{f.withdrawals.withdrawInCurrentTransaction("1","123",false);Assert.fail();}catch(IllegalStateException expected){}f.begin();try{f.withdrawals.withdrawInCurrentTransaction("1","123",false);Assert.fail();}catch(IllegalStateException expected){}f.rollback();}
        System.out.println("PASS current operator roles preserve free uncounted withdrawals and authorized moderation, still reject redpackets; owner transaction, prior lock and cache boundary are mandatory");
        try(var f=new Withdrawal()){
            ChatroomChannel.sent.clear();f.seed(1);final var viewer=new JSONObject().put("oId","1");
            final JSONObject receipt=f.withdrawals.withdraw(viewer,"123","withdraw-1");Assert.assertEquals(receipt.keySet(),Set.of("id","removed","cost"));Assert.assertEquals(receipt.getInt("cost"),0);Assert.assertTrue(receipt.getBoolean("removed"));
            Assert.assertEquals(f.delivered,1);Assert.assertEquals(f.indexed,1);Assert.assertEquals(f.refreshed,1);Assert.assertEquals(ChatroomChannel.sent.size(),1);Assert.assertEquals(ChatroomChannel.sent.getFirst().keySet(),Set.of("type","oId"));String after=f.all();
            Assert.assertEquals(f.withdrawals.withdraw(viewer,"123","withdraw-1").toString(),receipt.toString());Assert.assertEquals(f.all(),after);Assert.assertEquals(f.delivered,1);Assert.assertEquals(f.indexed,1);
            reject(404,"not_found",()->f.withdrawals.withdraw(viewer,"123","new-key"));Assert.assertEquals(f.all(),after);Assert.assertEquals(f.delivered,1);
            exec(f.writer,"INSERT INTO chat VALUES('124',?)",new JSONObject().put("userOId","1").put("content","other").toString());
            Assert.assertEquals(f.withdrawals.withdrawLegacy("1","124").cost(),32);Assert.assertEquals(f.delivered,2);Assert.assertEquals(f.indexed,2);
        }
        System.out.println("PASS real idempotency root replays a receipt after deletion without another charge, counter or delivery; prepared legacy and v3 adapters share one daily record and publish only after commit/cache refresh");
        for(String fault:List.of("receipt.after","commit","reversal.lookup"))try(var f=new Withdrawal()){
            ChatroomChannel.sent.clear();f.seed(1);String before=f.all();f.faults.add(fault);try{f.withdrawals.withdraw(new JSONObject().put("oId","1"),"123","failed-key");Assert.fail(fault);}catch(Exception expected){}
            Assert.assertEquals(f.all(),before,fault);Assert.assertEquals(f.delivered,0);Assert.assertEquals(f.indexed,0);Assert.assertTrue(ChatroomChannel.sent.isEmpty());Assert.assertFalse(f.active());Assert.assertFalse(ArticlePublicationCaches.active());
        }
        try(var f=new Withdrawal()){f.seed(1);ChatroomChannel.fail=true;try{Assert.assertTrue(f.withdrawals.withdraw(new JSONObject().put("oId","1"),"123","delivery-failed").getBoolean("removed"));Assert.assertTrue(query(f.reader,"SELECT * FROM chat").isEmpty());}finally{ChatroomChannel.fail=false;}}
        try(var f=new Withdrawal()){
            f.seed(1);f.faults.add("commit.lost");final var viewer=new JSONObject().put("oId","1");
            try{f.withdrawals.withdraw(viewer,"123","unknown-result");Assert.fail();}catch(IllegalStateException expected){}
            Assert.assertTrue(query(f.reader,"SELECT * FROM chat").isEmpty());Assert.assertEquals(query(f.reader,"SELECT * FROM requests").size(),1);Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),1);Assert.assertEquals(f.delivered,0);
            Assert.assertTrue(f.withdrawals.withdraw(viewer,"123","unknown-result").getBoolean("removed"));Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),1);Assert.assertEquals(f.delivered,0);Assert.assertEquals(f.indexed,0);
        }
        System.out.println("PASS receipt, commit and mutation failures roll back with no delivery/index; broadcast failure or lost successful commit acknowledgement preserves one durable receipt resolved by the original key without duplicate charging");
    }
}
