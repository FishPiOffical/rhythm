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
package org.b3log.symphony.util;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.service.ChatMessageRestrictionQueryService;
import org.json.JSONObject;
import org.testng.Assert;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual policy/state and locked-read SQL; SQLite strips FOR UPDATE and cannot prove MySQL row locking. */
public class ChatMessageAdmissionTest {
    interface Work {void run()throws Exception;}
    static void rejected(int status,String code,Work work)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertEquals(e.code,code);}}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ChatMessageRestrictionQueryService service=new ChatMessageRestrictionQueryService();
        boolean active=true,unavailable;int reads;
        Fixture()throws Exception{
            try(var s=db.createStatement()){s.execute("CREATE TABLE cloud(oId INTEGER PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");}
            var cloud=new CloudRepository(){
                @Override public boolean hasTransactionBegun(){return active;}
                @Override public String getName(){return "cloud";}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{
                    reads++;Assert.assertTrue(sql.endsWith("LIMIT 4 FOR UPDATE"));Assert.assertFalse(sql.contains("SELECT *"));if(unavailable)throw new RepositoryException("unavailable");
                    try(var s=db.prepareStatement(sql.replace(" FOR UPDATE",""))){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var rows=new ArrayList<JSONObject>();while(r.next())rows.add(new JSONObject().put("userId",r.getString(1)).put("gameId",r.getString(2)).put("data",r.getString(3)));return rows;}}catch(SQLException e){throw new RepositoryException(e);}
                }
            };
            var field=ChatMessageRestrictionQueryService.class.getDeclaredField("cloud");field.setAccessible(true);field.set(service,cloud);
        }
        void add(String actor,String game,String data)throws Exception{try(var s=db.prepareStatement("INSERT INTO cloud(userId,gameId,data) VALUES(?,?,?)")){s.setString(1,actor);s.setString(2,game);s.setString(3,data);s.executeUpdate();}}
        public void close()throws Exception{db.close();}
    }
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        var clear=new ChatMessageRestrictions.State(0,0,0);var calls=new AtomicInteger();
        ChatMessageRestrictions.check(clear,false,"hello",100,()->{calls.incrementAndGet();return false;});Assert.assertEquals(calls.get(),0);
        rejected(403,"chat_muted",()->ChatMessageRestrictions.check(new ChatMessageRestrictions.State(101,999,0),false,"hello",100,()->true));
        ChatMessageRestrictions.check(new ChatMessageRestrictions.State(101,999,0),true,"hello",100,()->true);
        rejected(403,"chat_muted",()->ChatMessageRestrictions.check(new ChatMessageRestrictions.State(100,999,0),true,"hello",100,()->true));
        ChatMessageRestrictions.check(new ChatMessageRestrictions.State(100,100,100),false,"hello",100,()->false);
        rejected(403,"chat_muted",()->ChatMessageRestrictions.check(new ChatMessageRestrictions.State(Long.MAX_VALUE,0,0),false,"hello",Long.MAX_VALUE-1,()->true));
        System.out.println("PASS explicit expiry boundaries avoid negative-second/overflow sentinels and preserve global priority, operator exception and personal mute");
        var risk=new ChatMessageRestrictions.State(0,0,101);
        rejected(403,"risk_content_rejected",()->ChatMessageRestrictions.check(risk,false,"![image](url)",100,()->{Assert.fail();return true;}));
        rejected(403,"risk_content_rejected",()->ChatMessageRestrictions.check(risk,false,"1234",100,()->{Assert.fail();return true;}));
        rejected(409,"confirmation_required",()->ChatMessageRestrictions.check(risk,false,"12345",100,()->false));
        ChatMessageRestrictions.check(risk,false,"12345",100,()->true);
        rejected(409,"confirmation_required",()->ChatMessageRestrictions.check(new ChatMessageRestrictions.State(101,0,101),false,"12345",100,()->false));
        rejected(403,"chat_muted",()->ChatMessageRestrictions.check(new ChatMessageRestrictions.State(101,0,101),false,"12345",100,()->true));
        System.out.println("PASS risk image/length validation precedes shared repeat confirmation and mute checks; five UTF-16 characters pass the existing boundary");
        Assert.assertFalse(ChatMessageAbuseState.confirmRisk("one","hello"));Assert.assertFalse(ChatMessageAbuseState.confirmRisk("two","hello"));Assert.assertTrue(ChatMessageAbuseState.confirmRisk("one","hello"));Assert.assertFalse(ChatMessageAbuseState.confirmRisk("one","hello"));Assert.assertFalse(ChatMessageAbuseState.confirmRisk("one","other"));Assert.assertTrue(ChatMessageAbuseState.confirmRisk("one","other"));
        try(var pool=Executors.newFixedThreadPool(2)){
            var ready=new CountDownLatch(1);var a=pool.submit(()->{ready.await();return ChatMessageAbuseState.confirmRisk("parallel","hello");});var b=pool.submit(()->{ready.await();return ChatMessageAbuseState.confirmRisk("parallel","hello");});ready.countDown();Assert.assertNotEquals(a.get(),b.get());
        }
        ChatMessageAbuseState.confirmRisk("evicted","hello");for(int i=0;i<2001;i++)ChatMessageAbuseState.confirmRisk("bounded-"+i,"hello");Assert.assertFalse(ChatMessageAbuseState.confirmRisk("evicted","hello"));
        System.out.println("PASS confirmation is actor/text-bound, consumes on match, replaces changed text, admits only one concurrent matching pair and retains the old 2000-entry bound");
        var reference=new SimpleCurrentLimiter(15*60,5);String name="shared-repeat-test";ChatMessageAbuseState.accepted("hello");
        for(int i=0;i<14;i++){
            ChatMessageAbuseState.Repeat expected;
            if(reference.access(name))expected=reference.get(name).getFrequency()==5?ChatMessageAbuseState.Repeat.WARN:ChatMessageAbuseState.Repeat.NONE;
            else{reference.remove(name);expected=ChatMessageAbuseState.Repeat.PENALIZE;}
            Assert.assertEquals(ChatMessageAbuseState.repeated(name,"hello"),expected);
        }
        Assert.assertEquals(ChatMessageAbuseState.repeated(name,"different"),ChatMessageAbuseState.Repeat.NONE);
        for(String ignored:List.of("[redpacket]not closed","only ending[/redpacket]")){ChatMessageAbuseState.accepted(ignored);Assert.assertEquals(ChatMessageAbuseState.repeated(name,ignored),ChatMessageAbuseState.Repeat.NONE);}
        System.out.println("PASS shared repetition decisions match the actual existing limiter across warning/penalty/reset cycles and preserve both legacy redpacket exclusions");
        try(var f=new Fixture()){
            Assert.assertEquals(f.service.readInCurrentTransaction("123"),clear);
            f.add("all:fish:mute","sys-mute","999");f.add("123","sys-mute","1000");f.add("123","sys-risk","1001");f.add("456","sys-risk","99999");f.add("123","unrelated","secret");
            Assert.assertEquals(f.service.readInCurrentTransaction("123"),new ChatMessageRestrictions.State(999,1000,1001));
            Assert.assertEquals(f.service.readInCurrentTransaction("456"),new ChatMessageRestrictions.State(999,0,99999));
            f.active=false;try{f.service.readInCurrentTransaction("123");Assert.fail();}catch(IllegalStateException expected){}f.active=true;
            for(String actor:List.of("", "123' OR 1=1 --","x","1".repeat(33))){try{f.service.readInCurrentTransaction(actor);Assert.fail();}catch(IllegalArgumentException expected){}}
            f.unavailable=true;try{f.service.readInCurrentTransaction("123");Assert.fail();}catch(RepositoryException expected){}
        }
        for(String bad:List.of("", "garbage", "-1", "9223372036854775808"))try(var f=new Fixture()){
            f.add("123","sys-risk",bad);try{f.service.readInCurrentTransaction("123");Assert.fail();}catch(RepositoryException expected){}
        }
        try(var f=new Fixture()){
            f.add("123","sys-risk","0");f.add("123","sys-risk","0");try{f.service.readInCurrentTransaction("123");Assert.fail();}catch(RepositoryException expected){}
        }
        System.out.println("PASS actual bounded parameterized restriction SQL isolates global/current actor keys, requires the owner transaction and fails closed on errors/malformed/duplicate stored records");
    }
}
