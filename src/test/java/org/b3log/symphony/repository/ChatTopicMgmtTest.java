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
import org.b3log.symphony.model.*;
import org.b3log.symphony.service.ChatTopicMgmtService;
import org.b3log.symphony.service.ChatTopicDeliveryService;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.service.ChatMessageWithdrawalMgmtService;
import org.b3log.symphony.service.PointtransferMgmtService;
import org.b3log.symphony.util.ChatTopicValue;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.ChatMessageRemovalTest.reject;

/** Actual topic core and ordered change repository; option/user/transfer row persistence adapted to SQLite. */
public class ChatTopicMgmtTest {
    static class Topic extends ChatMessageWithdrawalTest.Withdrawal {
        final ChatTopicMgmtService topics=new ChatTopicMgmtService();
        Topic()throws Exception{
            exec(writer,"ALTER TABLE user ADD COLUMN userName TEXT");exec(writer,"ALTER TABLE user ADD COLUMN userPhone TEXT");exec(writer,"UPDATE user SET userName='CurrentName',userPhone='bound'");
            exec(writer,"CREATE TABLE option(oId TEXT PRIMARY KEY,optionValue TEXT,optionCategory TEXT)");
            var users=ChatMessageWithdrawalMgmtService.class.getDeclaredField("users");users.setAccessible(true);
            inject(topics,ChatTopicMgmtService.class,"users",users.get(withdrawals));inject(topics,ChatTopicMgmtService.class,"head",state);inject(topics,ChatTopicMgmtService.class,"events",events);
            inject(topics,ChatTopicMgmtService.class,"options",new OptionRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public String getName(){return "option";}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{if(faults.contains("topic.read"))throw new RepositoryException("topic unavailable");return query(active()?writer:reader,sql,args);}
                @Override public JSONObject getForUpdate(String id)throws RepositoryException{Assert.assertTrue(active());var rows=query(writer,"SELECT * FROM option WHERE oId=?",id);return rows.isEmpty()?null:rows.getFirst();}
                @Override public String add(JSONObject row)throws RepositoryException{exec(writer,"INSERT INTO option VALUES(?,?,?)",row.getString("oId"),row.getString("optionValue"),row.getString("optionCategory"));if(faults.contains("option.after"))throw new RepositoryException("lost option insert acknowledgement");return row.getString("oId");}
                @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{Assert.assertEquals(fields,new String[]{"optionValue"});exec(writer,"UPDATE option SET optionValue=? WHERE oId=?",row.getString("optionValue"),id);if(faults.contains("option.after"))throw new RepositoryException("lost option update acknowledgement");}
            });
            inject(topics,ChatTopicMgmtService.class,"points",new PointtransferMgmtService(){
                @Override public String transferInCurrentTransaction(String from,String to,int type,int amount,String data,long time,String memo,PointtransferSource source,boolean index){
                    Assert.assertEquals(from,"1");Assert.assertEquals(to,Pointtransfer.ID_C_SYS);Assert.assertEquals(type,Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_SET_DISCUSS);Assert.assertEquals(amount,16);Assert.assertFalse(index);Assert.assertEquals(source,PointtransferSource.EMPTY);
                    try{exec(writer,"UPDATE user SET userPoint=userPoint-16 WHERE oId='1'");if(faults.contains("transfer.partial"))return null;String id=UUID.randomUUID().toString();exec(writer,"INSERT INTO transfers VALUES(?,16)",id);return id;}catch(RepositoryException e){throw new IllegalStateException(e);}
                }
            });
        }
        ChatTopicMgmtService.Result change(String text)throws Exception{return owned(()->topics.changeInCurrentTransaction("1",text,current->{Assert.assertEquals(current.getString("userName"),"CurrentName");}));}
        String every()throws Exception{return all()+query(reader,"SELECT * FROM option");}
    }
    public static void main(String[] args)throws Exception{
        var properties=new Properties();properties.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(properties);Latkes.setLocalProps(properties);
        try(var f=new Topic()){
            Assert.assertNull(f.topics.readCommitted());String before=f.every();f.owned(()->{
                var result=f.topics.changeInCurrentTransaction("1","聊 <T> 😀",current->{});Assert.assertEquals(result.topic().revision(),1);Assert.assertEquals(result.topic().whoChanged(),"CurrentName");Assert.assertEquals(f.every(),before);Assert.assertTrue(query(f.second,"SELECT * FROM option").isEmpty());return result;
            });
            var saved=f.topics.readCommitted();Assert.assertEquals(saved.text(),"聊 <T> 😀");Assert.assertEquals(saved.revision(),1);Assert.assertEquals(query(f.reader,"SELECT userPoint FROM user").getFirst().getInt("userPoint"),984);
            var change=f.page(f.reader,0,1,10).getFirst();Assert.assertEquals(change.getString("kind"),"topic");Assert.assertEquals(change.getString("messageId"),"0");
            Assert.assertEquals(f.change("第二个话题").topic().revision(),2);Assert.assertEquals(f.topics.readCommitted().text(),"第二个话题");Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),2);Assert.assertTrue(query(f.reader,"SELECT * FROM chat").isEmpty());Assert.assertTrue(query(f.reader,"SELECT * FROM profession").isEmpty());
            Assert.assertEquals(f.change("第二个话题").topic().revision(),3);Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),3);Assert.assertEquals(query(f.reader,"SELECT userPoint FROM user").getFirst().getInt("userPoint"),952);
        }
        System.out.println("PASS durable topic creation/update commits exact fee, current author snapshot, ordered topic marker and option together; observers see the old state until commit, with no fake chat message or profession source");
        for(boolean existing:List.of(false,true))for(String fault:List.of("transfer.partial","event.after","state.after","option.after"))try(var f=new Topic()){
            if(existing)f.change("已有话题");String before=f.every();f.faults.add(fault);f.owned(()->{try{f.topics.changeInCurrentTransaction("1","失败话题",current->{});Assert.fail(fault);}catch(RepositoryException|RuntimeException expected){}return null;});Assert.assertEquals(f.every(),before,fault);
        }
        try(var f=new Topic()){String before=f.every();f.faults.add("commit");try{f.change("提交失败");Assert.fail();}catch(RuntimeException expected){}Assert.assertEquals(f.every(),before);}
        System.out.println("PASS partial transfer, event/head and initial or existing option failures restore fee/state/marker even when caller catches and commits; root commit rejection preserves the same snapshot");
        try(var f=new Topic()){
            exec(f.writer,"UPDATE user SET userPoint=15");String before=f.every();reject(409,"insufficient_points",()->f.change("余额不足"));Assert.assertEquals(f.every(),before);
            exec(f.writer,"UPDATE user SET userPoint=1000,userPhone=''");before=f.every();reject(403,"phone_required",()->f.change("没有手机"));Assert.assertEquals(f.every(),before);
            exec(f.writer,"UPDATE user SET userPhone='bound',userStatus=1");before=f.every();reject(404,"not_found",()->f.change("失效账号"));Assert.assertEquals(f.every(),before);
        }
        try(var f=new Topic()){String before=f.every();f.owned(()->{reject(403,"chat_muted",()->f.topics.changeInCurrentTransaction("1","显式准入",current->{throw new org.b3log.symphony.util.ApiV3Exception(403,"chat_muted","muted");}));return null;});Assert.assertEquals(f.every(),before);}
        System.out.println("PASS current account/phone/balance checks and explicit admission rejection run before charging or publishing a topic");
        for(String bad:List.of("","{}","{\"text\":\"x\",\"whoChanged\":\"Name\",\"revision\":0}","{\"text\":\"x\",\"whoChanged\":\"Name\",\"revision\":\"9223372036854775808\"}"))try(var f=new Topic()){
            exec(f.writer,"INSERT INTO option VALUES(?,?,'chat')",ChatTopicValue.OPTION_ID,bad);String before=f.every();try{f.topics.readCommitted();Assert.fail();}catch(IllegalStateException expected){}try{f.change("不覆盖坏记录");Assert.fail();}catch(IllegalStateException expected){}Assert.assertEquals(f.every(),before);
        }
        try(var f=new Topic()){
            try{f.topics.changeInCurrentTransaction("1","没有事务",current->{});Assert.fail();}catch(IllegalStateException expected){}
            String before=f.every();for(String text:List.of(" ","x".repeat(65),"broken\uD83D")){try{f.change(text);Assert.fail();}catch(IllegalArgumentException expected){}Assert.assertEquals(f.every(),before);}
            f.owned(()->{try{f.topics.readCommitted();Assert.fail();}catch(IllegalStateException expected){}return null;});
        }
        System.out.println("PASS corrupt durable values, overflowing revisions, oversized or malformed UTF-16 input and missing transaction/locks fail closed; committed reads never read a writer's own uncommitted topic");
        try(var f=new Topic()){
            ChatroomChannel.sent.clear();ChatroomChannel.discussing="暂无";final var delivery=new ChatTopicDeliveryService();inject(delivery,ChatTopicDeliveryService.class,"topics",f.topics);
            delivery.refreshCommitted();Assert.assertTrue(ChatroomChannel.sent.isEmpty());f.change("第一个话题");Assert.assertEquals(ChatroomChannel.discussing,"暂无");
            delivery.refreshCommitted();Assert.assertEquals(ChatroomChannel.discussing,"第一个话题");Assert.assertEquals(ChatroomChannel.sent.size(),1);Assert.assertEquals(ChatroomChannel.sent.getFirst().keySet(),Set.of("type","whoChanged","newDiscuss","topicRevision"));delivery.refreshCommitted();Assert.assertEquals(ChatroomChannel.sent.size(),1);
            f.change("中间话题");f.change("最新话题");delivery.refreshCommitted();Assert.assertEquals(ChatroomChannel.discussing,"最新话题");Assert.assertEquals(ChatroomChannel.sent.getLast().getString("topicRevision"),"3");
            f.faults.add("topic.read");try{delivery.refreshCommitted();Assert.fail();}catch(RepositoryException expected){}Assert.assertEquals(ChatroomChannel.sent.size(),2);f.faults.clear();
            f.owned(()->{try{delivery.refreshCommitted();Assert.fail();}catch(IllegalStateException expected){}return null;});
            final var restarted=new ChatTopicDeliveryService();inject(restarted,ChatTopicDeliveryService.class,"topics",f.topics);ChatroomChannel.discussing="暂无";restarted.refreshCommitted();Assert.assertEquals(ChatroomChannel.discussing,"最新话题");
            f.change("广播失败但已提交");ChatroomChannel.fail=true;try{delivery.refreshCommitted();Assert.fail();}catch(IllegalStateException expected){}finally{ChatroomChannel.fail=false;}
            Assert.assertEquals(ChatroomChannel.discussing,"广播失败但已提交");Assert.assertEquals(f.topics.readCommitted().text(),"广播失败但已提交");delivery.refreshCommitted();
            for(var invalid:List.of(new ChatTopicValue("回退版本","CurrentName",3),new ChatTopicValue("同版本被改写","CurrentName",4))){
                exec(f.writer,"UPDATE option SET optionValue=? WHERE oId=?",invalid.stored(),ChatTopicValue.OPTION_ID);try{delivery.refreshCommitted();Assert.fail();}catch(IllegalStateException expected){}Assert.assertEquals(ChatroomChannel.discussing,"广播失败但已提交");
            }
        }finally{ChatroomChannel.discussing="暂无";ChatroomChannel.fail=false;}
        System.out.println("PASS committed projection recovers the latest topic after missed callbacks or fresh service startup, emits only public legacy fields and revision, skips duplicates and never publishes uncommitted or unavailable state; broadcast failure cannot undo durable state");
    }
}
