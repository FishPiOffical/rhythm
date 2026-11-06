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
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.service.ChatMessageMgmtService;
import org.b3log.symphony.service.ProfessionSourceEventCaptureService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ChatMessageRemovalPolicy;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Real removal, typed reaction SQL, profession reversal and savepoints; SQLite replaces MySQL and ID allocation. */
public class ChatMessageRemovalTest {
    static class Removal extends ChatMessageMgmtServiceTest.Publication {
        Removal()throws Exception {
            exec(writer,"ALTER TABLE profession ADD COLUMN eventKey TEXT");exec(writer,"CREATE UNIQUE INDEX profession_event_key ON profession(eventKey)");
            final var repository=new Messages(){
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override protected String insertMessage(JSONObject row)throws RepositoryException{return super.insertMessage(new JSONObject(row.toString()).put("oId","123"));}
            };
            inject(repository,ChatRoomRepository.class,"changes",events);inject(management,ChatMessageMgmtService.class,"messages",repository);
            inject(management,ChatMessageMgmtService.class,"reactions",new Reactions(){
                @Override public void remove(String id)throws RepositoryException{super.remove(id);if(faults.contains("reaction.after"))throw new RepositoryException("reaction cleanup failed");}
            });
            inject(capture,ProfessionSourceEventCaptureService.class,"sourceEventRepository",new ProfessionSourceEventRepository(){
                @Override public String add(JSONObject row)throws RepositoryException{
                    if(faults.contains("reversal.before")&&row.getString("eventType").equals("profession.source.reversed"))throw new RepositoryException("reversal before");
                    exec(writer,"INSERT INTO profession VALUES(?,?,?)",row.getString("oId"),row.toString(),row.getString("eventKey"));
                    if((faults.contains("reversal.after")||faults.contains("reversal.lookup"))&&row.getString("eventType").equals("profession.source.reversed"))throw new RepositoryException("reversal after");
                    return row.getString("oId");
                }
                @Override public List<JSONObject> getPositiveByTarget(String type,String id)throws RepositoryException{
                    final var out=new ArrayList<JSONObject>();
                    for(var row:query(writer,"SELECT content FROM profession")){
                        var value=new JSONObject(row.getString("content"));
                        if(type.equals(value.optString("targetType"))&&id.equals(value.optString("targetId"))&&value.optString("reversalOfEventKey").isEmpty())out.add(value);
                    }
                    return out;
                }
                @Override public JSONObject getByEventKey(String key)throws RepositoryException{
                    if(faults.contains("reversal.lookup"))throw new RepositoryException("cannot confirm reversal");
                    var found=query(writer,"SELECT content FROM profession WHERE eventKey=?",key);return found.isEmpty()?null:new JSONObject(found.getFirst().getString("content"));
                }
            });
        }
        void seed(int reactions)throws Exception{
            atomic(()->management.saveInCurrentTransaction(envelope(),"1"));
            for(int i=0;i<reactions;i++)reaction("r"+i,Reaction.TARGET_TYPE_CHAT,"123");
            reaction("article",Reaction.TARGET_TYPE_ARTICLE,"123");reaction("comment",Reaction.TARGET_TYPE_COMMENT,"123");reaction("other-chat",Reaction.TARGET_TYPE_CHAT,"456");
        }
        void reaction(String id,String type,String target)throws RepositoryException{exec(writer,"INSERT INTO reaction VALUES(?,?,?,?,?,?,?,?)",id,"1",type,target,"emoji","heart",1,1);}
        String full()throws Exception{return snapshot()+query(reader,"SELECT * FROM reaction ORDER BY oId")+query(reader,"SELECT * FROM profession ORDER BY oId");}
    }
    interface Work{void run()throws Exception;}
    static void reject(int status,String code,Work work)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertEquals(e.code,code);}}
    static JSONObject row(String author,Object content){return new JSONObject().put("oId","123").put("content",new JSONObject().put("userOId",author).put("userName","SameName").put("content",content).toString());}
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        ChatMessageRemovalPolicy.requireOwner(row("1","hello"),"1");
        ChatMessageRemovalPolicy.requireOwner(row("1","{'msgType':'redPacket'}"),"1");
        ChatMessageRemovalPolicy.requireOwner(row("1","{malformed ordinary text"),"1");
        ChatMessageRemovalPolicy.requireOwner(row("1",new JSONObject().put("msgType","music")),"1");
        for(Object card:List.of(new JSONObject().put("msgType","redPacket"),new JSONObject().put("msgType","redPacket").toString()))reject(409,"message_not_removable",()->ChatMessageRemovalPolicy.requireOwner(row("1",card),"1"));
        reject(404,"not_found",()->ChatMessageRemovalPolicy.requireOwner(row("2","hello"),"1"));
        reject(404,"not_found",()->ChatMessageRemovalPolicy.requireOwner(row("0","hello"),"1"));
        reject(404,"not_found",()->ChatMessageRemovalPolicy.requireOwner(new JSONObject().put("content",new JSONObject().put("userName","SameName").put("content","hello").toString()),"1"));
        for(String malformed:List.of("bad","{\"userOId\":\"1\",\"userOId\":\"2\",\"content\":\"hello\"}","x".repeat(102401)))reject(404,"not_found",()->ChatMessageRemovalPolicy.requireOwner(new JSONObject().put("content",malformed),"1"));
        reject(404,"not_found",()->ChatMessageRemovalPolicy.requireOwner(row("1",7),"1"));
        System.out.println("PASS explicit stored author-ID ownership rejects renamed-user/name-reuse spoofing, bots and unavailable envelopes; both valid redpacket representations block while ordinary malformed JSON text remains removable");
        try(var f=new Removal()){
            f.seed(600);String before=f.full();f.begin();Assert.assertTrue(f.management.removeInCurrentTransaction("123",row->{Assert.assertEquals(row.keySet(),Set.of("oId","content"));ChatMessageRemovalPolicy.requireOwner(row,"1");}));Assert.assertEquals(f.full(),before);
            Assert.assertEquals(f.head(f.second),1L);Assert.assertEquals(query(f.second,"SELECT * FROM chat").size(),1);Assert.assertEquals(query(f.second,"SELECT * FROM reaction").size(),603);Assert.assertEquals(query(f.second,"SELECT * FROM profession").size(),1);
            f.commit();Assert.assertEquals(f.head(f.second),2L);
            Assert.assertTrue(query(f.reader,"SELECT * FROM chat").isEmpty());Assert.assertEquals(query(f.reader,"SELECT * FROM reaction ORDER BY oId").stream().map(r->r.getString("oId")).toList(),List.of("article","comment","other-chat"));
            Assert.assertEquals(f.head(f.reader),2L);Assert.assertEquals(f.page(f.reader,1,2,200).getFirst().getString("kind"),"deleted");
            var facts=query(f.reader,"SELECT content FROM profession");Assert.assertEquals(facts.size(),2);
            var reversed=facts.stream().map(r->new JSONObject(r.getString("content"))).filter(r->r.getString("eventType").equals("profession.source.reversed")).findFirst().orElseThrow();
            Assert.assertEquals(reversed.getString("reversalOfEventKey"),"chatroom.message.published:123");Assert.assertEquals(reversed.getString("targetType"),"chatroom_message");Assert.assertEquals(reversed.getString("subjectUserId"),"1");Assert.assertEquals(reversed.getString("visibilityClass"),"SELF");
            String removed=f.full();f.atomic(()->Assert.assertFalse(f.management.removeInCurrentTransaction("123",row->{Assert.fail("missing target must not run policy");})));Assert.assertEquals(f.full(),removed);
        }
        System.out.println("PASS actual removal commits one deleted event and profession reversal with all 600 target reactions, preserving same-ID article/comment reactions and unrelated chat; missing replay is a no-op");
        for(String fault:List.of("data.after","event.before","event.after","state.before","state.after","reaction.after","reversal.before","reversal.lookup"))try(var f=new Removal()){
            f.seed(3);String before=f.full();f.begin();f.faults.add(fault);
            try{f.management.removeInCurrentTransaction("123",row->ChatMessageRemovalPolicy.requireOwner(row,"1"));Assert.fail(fault);}catch(RepositoryException expected){}
            f.commit();Assert.assertEquals(f.full(),before,fault);
        }
        try(var f=new Removal()){
            f.seed(1);String before=f.full();f.begin();reject(404,"not_found",()->f.management.removeInCurrentTransaction("123",row->ChatMessageRemovalPolicy.requireOwner(row,"2")));f.commit();Assert.assertEquals(f.full(),before);
            f.begin();Assert.assertTrue(f.management.removeInCurrentTransaction("123",row->ChatMessageRemovalPolicy.requireOwner(row,"1")));f.rollback();Assert.assertEquals(f.full(),before);
        }
        System.out.println("PASS data/event/head/reaction/reversal failures and policy rejection preserve every row even if the outer caller catches and commits; explicit rollback restores the same snapshot");
        try(var f=new Removal()){
            f.seed(1);f.faults.add("reversal.after");f.begin();Assert.assertTrue(f.management.removeInCurrentTransaction("123",null));f.commit();
            Assert.assertTrue(query(f.reader,"SELECT * FROM chat").isEmpty());Assert.assertEquals(query(f.reader,"SELECT * FROM profession").size(),2);Assert.assertEquals(f.head(f.reader),2L);
        }
        System.out.println("PASS actual reversal addIfAbsent resolves a lost insert acknowledgement by its event key, so confirmed complete reversal can commit with removal rather than being mistaken for an absent fact");
        try(var f=new Removal()){
            f.seed(1);String before=f.full();try{f.management.removeInCurrentTransaction("123",null);Assert.fail();}catch(IllegalStateException expected){}
            f.begin();for(String bad:List.of("","x","1 OR 1=1","1".repeat(33)))try{f.management.removeInCurrentTransaction(bad,null);Assert.fail();}catch(IllegalArgumentException expected){}f.commit();Assert.assertEquals(f.full(),before);
            f.begin();try{f.management.removeLegacy("123");Assert.fail();}catch(IllegalStateException expected){}f.rollback();
            f.faults.add("commit");try{f.management.removeLegacy("123");Assert.fail();}catch(IllegalStateException expected){}Assert.assertEquals(f.full(),before);Assert.assertFalse(f.active());
            f.faults.clear();Assert.assertTrue(f.management.removeLegacy("123"));Assert.assertFalse(f.active());Assert.assertFalse(f.management.removeLegacy("123"));
        }
        System.out.println("PASS strict legacy removal rejects nested roots and invalid IDs, propagates commit rejection with full rollback and releases resources; confirmed removal and absent targets return accurately");
    }
}
