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
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatPublicationTest.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.exec;

/** Actual topic controller/admission/idempotency/core with the publication fixture's explicit SQLite adapters. */
public class ApiV3ChatTopicTest {
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        Latkes.setServerScheme("http");Latkes.setServerHost("localhost");Latkes.setServerPort("8080");Latkes.setContextPath("");
        try(var f=new Fixture()){
            var receipt=f.topic.set(f.viewer(),"聊什么 😀","original");Assert.assertEquals(receipt.keySet(),Set.of("text","cost","revision"));Assert.assertEquals(receipt.getInt("cost"),16);Assert.assertEquals(f.balance(),84);Assert.assertEquals(f.count("event"),1);Assert.assertEquals(f.count("chat"),0);Assert.assertEquals(f.count("profession"),0);Assert.assertEquals(f.indexed,1);
            f.topic.set(f.viewer(),"第二个话题","later");Assert.assertEquals(f.balance(),68);
            Assert.assertEquals(f.topic.set(f.viewer(),"聊什么 😀","original").toString(),receipt.toString());Assert.assertEquals(f.topicCore.readCommitted().text(),"第二个话题");Assert.assertEquals(f.balance(),68);Assert.assertEquals(f.count("event"),2);Assert.assertEquals(f.indexed,2);
            rejected(409,"idempotency_conflict",()->f.topic.set(f.viewer(),"另一个请求","original"));
            f.topicWrites.legacy("1","旧入口话题");Assert.assertEquals(f.topicCore.readCommitted().text(),"旧入口话题");Assert.assertEquals(f.balance(),52);Assert.assertEquals(f.count("event"),3);
        }
        System.out.println("PASS actual v3 topic commits fee/option/event/receipt without a chat message, original-key replay cannot revert a later topic, and legacy changes share committed state and exact fee");
        for(String fault:List.of("transfer.null","event.before","option.after","request.after","commit"))try(var f=new Fixture()){
            f.faults.add(fault);try{f.topic.set(f.viewer(),"失败话题",fault);Assert.fail(fault);}catch(Exception expected){}f.noPublication();Assert.assertEquals(f.count("option"),0);
        }
        for(String fault:List.of("transfer.null","option.after","commit"))try(var f=new Fixture()){
            f.faults.add(fault);try{f.topicWrites.legacy("1","失败话题");Assert.fail(fault);}catch(Exception expected){}f.noPublication();Assert.assertEquals(f.count("option"),0);
        }
        System.out.println("PASS v3 and legacy partial transfer, option/event/receipt and strict commit failures roll back topic fees and state with no postcommit effects");
        try(var f=new Fixture()){
            f.faults.add("commit.lost");try{f.topic.set(f.viewer(),"已提交但响应丢失","lost");Assert.fail();}catch(Exception expected){}
            Assert.assertEquals(f.balance(),84);Assert.assertEquals(f.count("request"),1);Assert.assertEquals(f.indexed,0);
            var result=f.topic.set(f.viewer(),"已提交但响应丢失","lost");Assert.assertEquals(result.getString("text"),"已提交但响应丢失");Assert.assertEquals(f.balance(),84);Assert.assertEquals(f.count("event"),1);
        }
        System.out.println("PASS lost commit acknowledgement resolves through the original key without a second topic change or fee");
        try(var f=new Fixture()){
            for(String text:List.of(" ","a".repeat(65),"\uD83D","\t"))rejected(422,"invalid_request",()->f.topic.set(f.viewer(),text,"bad"));
            Assert.assertEquals(ChatTopicInputs.legacy("a".repeat(63)+"😀"),"a".repeat(63));
            Assert.assertEquals(ChatTopicInputs.v3("a".repeat(62)+"😀").length(),64);
            f.noPublication();
            exec(f.writer,"UPDATE user SET userPhone='' WHERE oId='1'");rejected(403,"phone_required",()->f.topic.set(f.viewer(),"手机校验","phone"));
            exec(f.writer,"UPDATE user SET userPhone='bound',userStatus=1 WHERE oId='1'");rejected(404,"not_found",()->f.topic.set(f.viewer(),"状态校验","status"));
        }
        try(var f=new Fixture()){
            f.restriction("sys-mute",Long.toString(NOW+1000));rejected(403,"chat_muted",()->f.topic.set(f.viewer(),"禁言话题","mute"));f.noPublication();
        }
        try(var f=new Fixture()){
            f.restriction("sys-risk",Long.toString(NOW+1000));rejected(409,"confirmation_required",()->f.topic.set(f.viewer(),"风险确认话题","risk"));Assert.assertEquals(f.balance(),100);
            f.faults.add("event.before");f.retryEvent=true;var receipt=f.topic.set(f.viewer(),"风险确认话题","risk");Assert.assertEquals(receipt.getInt("cost"),16);Assert.assertEquals(f.balance(),84);Assert.assertEquals(f.count("event"),1);
        }
        System.out.println("PASS approved text and UTF-16 boundaries, current phone/status/mute and risk confirmation reject before fees; an automatic storage retry does not consume confirmation twice");
        try(var f=new Fixture()){
            String text="重复话题";f.warmRepeat(ChatTopicInputs.OPEN+text+ChatTopicInputs.CLOSE);
            var result=f.topic.set(f.viewer(),text,"repeat");Assert.assertEquals(result.getInt("cost"),16);Assert.assertEquals(f.balance(),54);Assert.assertEquals(f.count("transfer"),2);
            int events=f.count("event");Assert.assertEquals(f.topic.set(f.viewer(),text,"repeat").toString(),result.toString());Assert.assertEquals(f.balance(),54);Assert.assertEquals(f.count("event"),events);
        }
        try(var f=new Fixture()){
            String text="余额不够复读与话题";f.warmRepeat(ChatTopicInputs.OPEN+text+ChatTopicInputs.CLOSE);exec(f.writer,"UPDATE user SET userPoint=45 WHERE oId='1'");
            rejected(409,"insufficient_points",()->f.topic.set(f.viewer(),text,"repeat-insufficient"));Assert.assertEquals(f.balance(),45);
            for(String table:List.of("option","event","request","notification","transfer","cloud"))Assert.assertEquals(f.count(table),0,table);Assert.assertEquals(f.indexed,0);
        }
        System.out.println("PASS topic repeat penalties share admission state, replay only once and roll back together with topic fees when the combined balance is insufficient");
        try(var f=new Fixture()){
            Assert.assertEquals(f.request(null,"application/json","{\"text\":\"测试\"}","key",true).getInt("status"),401);
            f.scope="content.read";Assert.assertEquals(f.request("Bearer VALID","application/json","{\"text\":\"测试\"}","key",true).getInt("status"),403);f.scope="content.write";
            for(String raw:List.of("{}","{\"text\":\"\"}","{\"text\":\"x\",\"cost\":0}","{\"text\":3}"))Assert.assertEquals(f.request("Bearer VALID","application/json",raw,"bad",true).getInt("status"),422);
            Assert.assertEquals(f.request("Bearer VALID","text/plain","{}","key",true).getInt("status"),415);
            Assert.assertEquals(f.request("Bearer VALID","application/json","{\"text\":\"测试\"}",null,true).getInt("status"),422);
            var one=f.request("Bearer VALID","application/json","{\"text\":\"测试\"}","controller",true);Assert.assertEquals(one.getInt("status"),200);Assert.assertEquals(one.getJSONObject("data").getInt("cost"),16);
            Assert.assertEquals(f.request("Bearer VALID","application/json","{\"text\":\"测试\"}","controller",true).getJSONObject("data").toString(),one.getJSONObject("data").toString());Assert.assertEquals(f.balance(),84);
        }
        System.out.println("PASS actual topic controller requires content.write Bearer, ignores Cookie/query identity and rejects malformed/unknown fields, media and missing keys before returning a private replayable receipt");
    }
}
