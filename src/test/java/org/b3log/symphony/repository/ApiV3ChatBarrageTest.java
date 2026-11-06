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
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatPublicationTest.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.exec;

/** Real barrage service/controller and strict fee+receipt transaction, with explicit ancillary adapters. */
public class ApiV3ChatBarrageTest {
    static JSONObject send(Fixture f,String text,String key)throws Exception{return f.barrage.send(f.viewer(),text,"",key);}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        Latkes.setServerScheme("http");Latkes.setServerHost("localhost");Latkes.setServerPort("8080");Latkes.setContextPath("");
        Assert.assertEquals(ChatBarrageInputs.prepare(" <b>Hi</b> &lt;i&gt;😀 ","#a1B2c3"),new ChatBarrageInputs("Hi <i>😀","#A1B2C3"));
        Assert.assertEquals(ChatBarrageInputs.prepare("x".repeat(30)+"😀","").content().length(),32);
        for(String raw:List.of(" ","<script>bad()</script>","x".repeat(33),"x".repeat(31)+"😀","\uD83D","nul\u0000","x".repeat(4097)))rejected(422,"invalid_barrage",()->ChatBarrageInputs.prepare(raw,""));
        for(String color:List.of("red","#fff","#123456;","javascript:x"," #123456","#zzzzzz"))rejected(422,"invalid_barrage",()->ChatBarrageInputs.prepare("Hi",color));
        System.out.println("PASS approved plain-text cleaning, entity round-trip, UTF-16 and raw-body bounds reject malformed/empty/oversized content without truncating emoji and accept only six-digit colors");
        try(var f=new Fixture()){
            exec(f.writer,"UPDATE user SET userNickname='<img src=x>',userAvatarURL='javascript:bad' WHERE oId='1'");
            var first=send(f,"<b>Hello</b>","one");Assert.assertEquals(first.keySet(),Set.of("id","createdAt","cost","unit"));Assert.assertEquals(first.getInt("cost"),5);Assert.assertEquals(f.balance(),95);Assert.assertEquals(f.count("transfer"),1);Assert.assertEquals(f.count("request"),1);
            for(String table:List.of("chat","event","profession","option"))Assert.assertEquals(f.count(table),0,table);
            var event=f.barrageRealtime.sent.getFirst();Assert.assertEquals(event.keySet(),Set.of("id","content","color","createdAt","author"));Assert.assertEquals(event.getString("content"),"Hello");Assert.assertEquals(event.getString("color"),"#FFFFFF");Assert.assertEquals(event.getJSONObject("author").keySet(),Set.of("id","userName","userNickname","userAvatarURL"));Assert.assertEquals(event.getJSONObject("author").getString("userName"),f.name);Assert.assertEquals(event.getJSONObject("author").getString("userAvatarURL"),"");Assert.assertFalse(event.toString().contains("PRIVATE"));Assert.assertFalse(event.toString().contains("FORGED"));
            Assert.assertEquals(ChatroomChannel.sent.getFirst().getString("type"),"barrager");Assert.assertEquals(f.delivery.activities,1);Assert.assertEquals(f.indexed,1);
            ChatroomProcessor.barrageCost=7;ChatroomProcessor.barrageUnit="新单位";
            Assert.assertEquals(send(f,"<b>Hello</b>","one").toString(),first.toString());Assert.assertEquals(f.balance(),95);Assert.assertEquals(f.barrageRealtime.sent.size(),1);Assert.assertEquals(f.delivery.activities,1);
            rejected(409,"idempotency_conflict",()->send(f,"changed","one"));
            var next=send(f,"Next","two");Assert.assertEquals(next.getInt("cost"),7);Assert.assertEquals(next.getString("unit"),"新单位");Assert.assertEquals(f.balance(),88);
        }
        System.out.println("PASS real barrage fees and acceptance receipts commit once, current locked authors are projected safely, changed prices cannot alter replay and delivery does not create historical messages or cursor events");
        for(String fault:List.of("transfer.null","request.after","commit"))try(var f=new Fixture()){
            f.faults.add(fault);try{send(f,"rollback",fault);Assert.fail(fault);}catch(Exception expected){}f.noPublication();Assert.assertTrue(f.barrageRealtime.sent.isEmpty());
        }
        try(var f=new Fixture()){
            f.faults.add("commit.lost");try{send(f,"acknowledgement lost","lost");Assert.fail();}catch(Exception expected){}
            Assert.assertEquals(f.balance(),95);Assert.assertTrue(f.barrageRealtime.sent.isEmpty());var first=send(f,"acknowledgement lost","lost");Assert.assertEquals(first.getInt("cost"),5);Assert.assertEquals(f.balance(),95);Assert.assertTrue(f.barrageRealtime.sent.isEmpty());
        }
        try(var f=new Fixture()){
            ChatroomChannel.fail=true;f.barrageRealtime.fail=true;try{var first=send(f,"best effort","delivery");Assert.assertEquals(send(f,"best effort","delivery").toString(),first.toString());Assert.assertEquals(f.balance(),95);Assert.assertEquals(f.count("transfer"),1);}finally{ChatroomChannel.fail=false;}
        }
        System.out.println("PASS partial transfer, receipt and strict commit failures roll back fees; lost acknowledgements and postcommit transport failures never recharge or replay an ephemeral animation");
        try(var f=new Fixture()){
            ChatroomProcessor.barrageCost=0;Assert.assertEquals(send(f,"free","free").getInt("cost"),0);Assert.assertEquals(f.balance(),100);Assert.assertEquals(f.count("transfer"),0);Assert.assertEquals(f.indexed,0);
        }
        try(var f=new Fixture()){
            ChatroomProcessor.barrageCost=-1;rejected(503,"unavailable",()->send(f,"invalid price","price"));f.noPublication();
        }
        try(var f=new Fixture()){
            exec(f.writer,"UPDATE user SET userPoint=4 WHERE oId='1'");rejected(409,"insufficient_points",()->send(f,"insufficient","balance"));Assert.assertEquals(f.balance(),4);Assert.assertEquals(f.count("request"),0);
            exec(f.writer,"UPDATE user SET userPoint=100,userPhone='' WHERE oId='1'");rejected(403,"phone_required",()->send(f,"phone","phone"));
            exec(f.writer,"UPDATE user SET userPhone='bound',userStatus=1 WHERE oId='1'");rejected(404,"not_found",()->send(f,"status","status"));
        }
        try(var f=new Fixture()){
            f.restriction("sys-mute",Long.toString(NOW+1000));rejected(403,"chat_muted",()->send(f,"muted","mute"));f.noPublication();
        }
        try(var f=new Fixture()){
            f.restriction("sys-risk",Long.toString(NOW+1000));rejected(409,"confirmation_required",()->send(f,"风险弹幕确认内容","risk"));Assert.assertEquals(f.balance(),100);send(f,"风险弹幕确认内容","risk");Assert.assertEquals(f.balance(),95);
        }
        System.out.println("PASS zero and invalid prices, insufficient balance and current phone/status/mute/risk restrictions obey the v3 contract without trusting the Bearer profile snapshot");
        try(var f=new Fixture()){
            for(int i=0;i<5;i++)send(f,"quota "+i,"q"+i);
            rejected(429,"rate_limited",()->send(f,"over quota","six"));Assert.assertEquals(f.balance(),75);Assert.assertEquals(f.count("request"),5);
            Assert.assertEquals(send(f,"quota 0","q0").getInt("cost"),5);Assert.assertEquals(f.balance(),75);
        }
        try(var f=new Fixture()){
            f.restriction("sys-risk",Long.toString(NOW+1000));rejected(409,"confirmation_required",()->send(f,"自动重试弹幕内容","auto"));
            f.faults.add("request.once");send(f,"自动重试弹幕内容","auto");Assert.assertEquals(f.balance(),95);Assert.assertEquals(f.count("transfer"),1);Assert.assertEquals(f.barrageRealtime.sent.size(),1);
            exec(f.writer,"DELETE FROM cloud");for(int i=0;i<4;i++)send(f,"after retry "+i,"after"+i);
            rejected(429,"rate_limited",()->send(f,"sixth","sixth"));Assert.assertEquals(f.balance(),75);Assert.assertEquals(f.count("request"),5);
        }
        System.out.println("PASS automatic receipt-storage retry reuses one quota/confirmation decision, commits one fee and animation, and preserves the remaining send budget");
        String shared="SharedQuota"+System.nanoTime();for(int i=0;i<5;i++)Assert.assertTrue(ChatBarrageQuota.take(shared));Assert.assertFalse(ChatBarrageQuota.take(shared));for(int i=0;i<8;i++)Assert.assertTrue(ChatBarrageQuota.take("admin"));
        System.out.println("PASS shared legacy/v3 barrage quota bounds new sends while successful receipt replay bypasses the quota and the existing admin-name exemption remains");
        try(var f=new Fixture()){
            Assert.assertEquals(f.request(null,"application/json","{\"content\":\"Hi\"}","key",2).getInt("status"),401);
            f.scope="content.read";Assert.assertEquals(f.request("Bearer VALID","application/json","{\"content\":\"Hi\"}","key",2).getInt("status"),403);f.scope="content.write";
            for(String raw:List.of("{}","{\"content\":\"Hi\",\"cost\":0}","{\"content\":3}","{\"content\":\"Hi\",\"color\":\"red\"}"))Assert.assertEquals(f.request("Bearer VALID","application/json",raw,"bad",2).getInt("status"),422);
            Assert.assertEquals(f.request("Bearer VALID","text/plain","{}","key",2).getInt("status"),415);
            Assert.assertEquals(f.request("Bearer VALID","application/json","{\"content\":\"Hi\"}",null,2).getInt("status"),422);
            var result=f.request("Bearer VALID","application/json","{\"content\":\"Hi\"}","controller",2);Assert.assertEquals(result.getInt("status"),200);Assert.assertEquals(result.getJSONObject("data").getInt("cost"),5);Assert.assertEquals(f.balance(),95);
        }
        System.out.println("PASS actual barrage controller requires content.write Bearer and strict body/media/key input, ignores legacy Cookie/query identity and returns only the acceptance receipt");
    }
}
