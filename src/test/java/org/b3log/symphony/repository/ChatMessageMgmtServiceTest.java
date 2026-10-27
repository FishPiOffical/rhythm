package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.service.ChatMessageMgmtService;
import org.b3log.symphony.service.ProfessionSourceEventCaptureService;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Real publication, profession capture, event hooks and strict JDBC root; SQLite replaces the connection pool. */
public class ChatMessageMgmtServiceTest {
    static class Publication extends Fixture {
        final ChatMessageMgmtService management=new ChatMessageMgmtService();
        final ProfessionSourceEventCaptureService capture=new ProfessionSourceEventCaptureService();
        Publication()throws Exception {
            exec(writer,"CREATE TABLE profession(oId TEXT PRIMARY KEY,content TEXT)");
            final var repository=new Messages(){
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override protected String insertMessage(JSONObject row)throws RepositoryException{return super.insertMessage(new JSONObject(row.toString()).put("oId","123"));}
            };
            inject(repository,ChatRoomRepository.class,"changes",events);
            inject(management,ChatMessageMgmtService.class,"messages",repository);
            inject(management,ChatMessageMgmtService.class,"redPackets",new ChatRedPacketRepository(){public JSONObject current(String id,boolean lock){return null;}});
            inject(management,ChatMessageMgmtService.class,"professions",capture);
            inject(capture,ProfessionSourceEventCaptureService.class,"sourceEventRepository",new ProfessionSourceEventRepository(){
                @Override public String add(JSONObject row)throws RepositoryException {
                    if(faults.contains("profession.before"))throw new RepositoryException("profession unavailable");
                    exec(writer,"INSERT INTO profession VALUES(?,?)",row.getString("oId"),row.toString());
                    if(faults.contains("profession.after"))throw new RepositoryException("profession acknowledgement lost");
                    if(faults.contains("profession.runtime"))throw new IllegalStateException("capture runtime failure");
                    return row.getString("oId");
                }
            });
        }
        JSONObject envelope(){return new JSONObject().put("userOId",1L).put("userName","Writer").put("time",1792304100000L).put("content","hello😀").put("client","Web/Test");}
        void empty()throws Exception{Assert.assertTrue(query(reader,"SELECT * FROM chat").isEmpty());Assert.assertTrue(query(reader,"SELECT * FROM event").isEmpty());Assert.assertTrue(query(reader,"SELECT * FROM profession").isEmpty());Assert.assertEquals(head(reader),0L);}
    }
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Publication()){
            var input=f.envelope();String original=input.toString();
            try{f.management.saveInCurrentTransaction(input,"1");Assert.fail();}catch(IllegalStateException expected){}
            f.begin();var saved=f.management.saveInCurrentTransaction(input,"1");Assert.assertEquals(input.toString(),original);Assert.assertNotSame(saved,input);f.empty();
            try{f.management.saveLegacy(input,"1");Assert.fail();}catch(IllegalStateException expected){}
            f.commit();Assert.assertEquals(query(f.reader,"SELECT * FROM chat").size(),1);Assert.assertEquals(f.head(f.reader),1L);
            var fact=new JSONObject(query(f.reader,"SELECT content FROM profession").getFirst().getString("content"));
            Assert.assertEquals(fact.getString("eventKey"),"chatroom.message.published:123");Assert.assertEquals(fact.getString("subjectUserId"),"1");
            var payload=new JSONObject(fact.getString("payloadJson"));Assert.assertEquals(payload.getInt("contentLength"),6);Assert.assertEquals(payload.getString("messageType"),"TEXT");Assert.assertEquals(payload.getLong("sentAt"),1792304100000L);
        }
        System.out.println("PASS publication primitive requires an owner transaction and commits message, ordered change and actual profession source together without mutating caller state");
        for(String fault:List.of("profession.before","profession.after","profession.runtime","event.before","event.after","state.after"))try(var f=new Publication()){
            f.begin();f.faults.add(fault);
            try{f.management.saveInCurrentTransaction(f.envelope(),"1");Assert.fail(fault);}catch(RepositoryException|IllegalStateException expected){}
            f.commit();f.empty();
        }
        System.out.println("PASS repository/runtime failures including lost profession acknowledgement undo the full publication even when the outer caller catches and commits");
        try(var f=new Publication()){
            f.begin();try{f.management.saveInCurrentTransaction(f.envelope(),"2");Assert.fail();}catch(IllegalArgumentException expected){}f.commit();f.empty();
            f.begin();f.management.saveInCurrentTransaction(f.envelope(),"1");f.rollback();f.empty();
        }
        System.out.println("PASS actor mismatch rejects before persistence and caller rollback removes message, event sequence and profession fact");
        try(var f=new Publication()){
            f.faults.add("commit");var input=f.envelope();String before=input.toString();
            try{f.management.saveLegacy(input,"1");Assert.fail();}catch(RuntimeException expected){}
            Assert.assertEquals(input.toString(),before);Assert.assertFalse(f.active());f.empty();
            f.faults.clear();var saved=f.management.saveLegacy(input,"1");Assert.assertEquals(saved.getString("oId"),"123");Assert.assertFalse(f.active());Assert.assertEquals(query(f.reader,"SELECT * FROM profession").size(),1);
        }
        for(String fault:List.of("profession.runtime","profession.after"))try(var f=new Publication()){
            f.faults.add(fault);try{f.management.saveLegacy(f.envelope(),"1");Assert.fail();}catch(RepositoryException|IllegalStateException expected){}
            Assert.assertFalse(f.active());f.empty();
        }
        System.out.println("PASS legacy strict root propagates rejected commits and all capture failures, rolls back and releases the transaction before reporting success");
    }
}
