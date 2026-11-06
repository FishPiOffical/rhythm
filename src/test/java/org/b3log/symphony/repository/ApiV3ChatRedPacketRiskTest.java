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
import org.b3log.symphony.service.ApiV3ChatRedPacketService;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.ApiV3ChatRedPacketTest.*;

/** Real signed settlement/points/message/receipt SQL; shares the safe packet fixture adapters. */
public final class ApiV3ChatRedPacketRiskTest {
    static JSONObject heart(){return new JSONObject().put("type","heartbeat").put("money",1000).put("count",5).put("riskAccepted",true);}
    static JSONObject rps(int gesture){return new JSONObject().put("type","rockPaperScissors").put("money",1000).put("count",1).put("gesture",gesture).put("riskAccepted",true);}
    static JSONObject proof(){return new JSONObject().put("riskAccepted",true);}
    static JSONObject move(int gesture){return proof().put("gesture",gesture);}
    static void negativeFirst(F f)throws Exception{
        var rows=query(f.writer,"SELECT * FROM slots ORDER BY ordinal");
        var negative=rows.stream().filter(r->r.getInt("amount")<0).findFirst().orElseThrow();
        var first=rows.getFirst();int a=first.getInt("amount"),b=negative.getInt("amount");
        exec(f.writer,"UPDATE slots SET amount=? WHERE oId=?",b,first.getString("oId"));
        exec(f.writer,"UPDATE slots SET amount=? WHERE oId=?",a,negative.getString("oId"));
    }
    static ApiV3ChatRedPacketService freshService(F f)throws Exception{
        var service=new ApiV3ChatRedPacketService(){protected long now(){return NOW;}};
        for(var field:ApiV3ChatRedPacketService.class.getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())){field.setAccessible(true);field.set(service,field.get(f.service));}
        return service;
    }
    static void untouched(F f)throws Exception{Assert.assertEquals(f.count("packets"),0L);Assert.assertEquals(f.balance("1"),10000L);Assert.assertEquals(f.count("transfers"),0L);}
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        for(JSONObject body:List.of(new JSONObject(heart().toString()).put("riskAccepted",false),new JSONObject(heart().toString()).put("riskAccepted","true"),new JSONObject(heart().toString()).put("count",4),new JSONObject(heart().toString()).put("count",101),new JSONObject(rps(0).toString()).put("money",255),new JSONObject(rps(0).toString()).put("count",2),rps(-1),rps(3),new JSONObject(rps(0).toString()).put("gesture","0"))){try(var f=new F()){rejected(422,()->f.create(body));untouched(f);}}
        try(var f=new F()){var body=heart();body.remove("riskAccepted");rejected(422,()->f.create(body));untouched(f);}
        System.out.println("PASS risk packet 1 explicit boolean acceptance, heartbeat count and RPS amount/count/gesture boundaries reject without writes");
        for(int count:List.of(5,6,100))for(int money:List.of(32,1000,10240))try(var f=new F()){
            exec(f.writer,"UPDATE user SET userPoint=20000 WHERE oId='1'");var created=f.create(heart().put("count",count).put("money",money));int pool=money*97/100,extra=pool/count+10;
            Assert.assertEquals(created.getInt("principalPoints"),money);Assert.assertEquals(created.getInt("poolPoints"),pool);Assert.assertEquals(created.getInt("taxPoints"),money-pool);
            var rows=query(f.reader,"SELECT amount FROM slots");Assert.assertEquals(rows.size(),count);Assert.assertEquals(rows.stream().mapToInt(r->r.getInt("amount")).sum(),pool);Assert.assertEquals(rows.stream().filter(r->r.getInt("amount")>0).count(),1L);Assert.assertEquals(rows.stream().mapToInt(r->r.getInt("amount")).max().orElseThrow(),pool+extra);
            String detail=f.service.detail(f.viewer("2"),"123").toString();Assert.assertFalse(detail.contains("ordinal"));Assert.assertFalse(detail.contains("senderGesture"));Assert.assertFalse(detail.contains("transferId"));Assert.assertEquals(new JSONObject(detail).getJSONArray("claims").length(),0);
        }
        System.out.println("PASS risk packet 2 persisted signed heartbeat allocation conserves pool at nine boundaries and unpublished slots stay private");
        try(var f=new F()){
            f.create(heart());negativeFirst(f);int amount=query(f.reader,"SELECT amount FROM slots WHERE ordinal=0").getFirst().getInt("amount");
            rejected(422,()->f.service.claim(f.viewer("2"),"123","missing-proof"));rejected(422,()->f.service.claim(f.viewer("2"),"123",proof().put("riskAccepted",false),"false-proof"));
            exec(f.writer,"UPDATE user SET userPoint=969 WHERE oId='2'");rejected(409,()->f.service.claim(f.viewer("2"),"123",proof(),"balance"));Assert.assertEquals(query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"),0);
            exec(f.writer,"UPDATE user SET userPoint=10000 WHERE oId='2'");var result=freshService(f).claim(f.viewer("2"),"123",proof(),"signed");Assert.assertEquals(result.getInt("amount"),amount);Assert.assertEquals(f.balance("2"),10000L+amount);Assert.assertEquals(f.service.claim(f.viewer("2"),"123",proof(),"signed").toString(),result.toString());Assert.assertEquals(f.balance("2"),10000L+amount);
            f.begin();try{rejected(409,()->f.management.removeInCurrentTransaction("123",null));}finally{f.rollback();}
            Assert.assertEquals(f.service.claim(f.viewer("1"),"123",proof(),"sender-can-claim").getString("id"),"123");
            Assert.assertEquals(f.service.detail(f.viewer("4"),"123").getJSONArray("claims").length(),2);
            rejected(422,()->f.service.legacyOpen(f.viewer("4"),"123"));
        }
        System.out.println("PASS risk packet 3 heartbeat balance precondition, signed debit/replay, sender participation, legacy proof rejection and unfinished delete guard");
        for(String fault:List.of("slots.after","packets.after","transfers.after","receipts.after","data.after","event.after","commit"))try(var f=new F()){
            f.create(heart());negativeFirst(f);long ledger=f.count("transfers"),events=f.count("event");f.faults.add(fault);
            rejected(503,()->f.service.claim(f.viewer("2"),"123",proof(),"rollback"));Assert.assertEquals(f.balance("2"),10000L);Assert.assertEquals(f.count("transfers"),ledger);Assert.assertEquals(f.count("event"),events);Assert.assertEquals(query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"),0);
            f.faults.clear();Assert.assertTrue(f.service.claim(f.viewer("2"),"123",proof(),"rollback").getInt("amount")<0);
        }
        System.out.println("PASS risk packet 4 negative heartbeat debit rolls back with all slot/head/message/event/receipt failures and same-key recovery");
        for(int sender=0;sender<3;sender++)for(int claimant=0;claimant<3;claimant++)try(var f=new F()){
            final int chosenGesture=claimant;var created=f.create(rps(sender));Assert.assertEquals(created.getInt("poolPoints"),850);Assert.assertEquals(created.getInt("taxPoints"),150);Assert.assertEquals(f.balance("1"),9000L);Assert.assertEquals(f.balance("3"),10150L);
            Assert.assertEquals(f.service.detail(f.viewer("1"),"123").getInt("senderGesture"),sender);Assert.assertFalse(f.service.detail(f.viewer("2"),"123").has("senderGesture"));
            String raw=query(f.reader,"SELECT content FROM chat").getFirst().getString("content");Assert.assertFalse(raw.contains("senderGesture"));Assert.assertFalse(new JSONObject(new JSONObject(raw).getString("content")).has("gesture"));
            rejected(403,()->f.service.claim(f.viewer("1"),"123",move(0),"own"));rejected(422,()->f.service.claim(f.viewer("2"),"123",proof(),"missing-gesture"));
            int diff=sender-claimant;String outcome=diff==0?"tie":diff==1||diff==-2?"win":"lose";int amount=outcome.equals("win")?850:outcome.equals("lose")?-850:0,refund=850-amount;
            var result=f.service.claim(f.viewer("2"),"123",move(claimant),"rps");Assert.assertEquals(result.getString("outcome"),outcome);Assert.assertEquals(result.getInt("gesture"),claimant);Assert.assertEquals(result.getInt("amount"),amount);Assert.assertEquals(result.getInt("senderRefundPoints"),refund);Assert.assertEquals(f.balance("2"),10000L+amount);Assert.assertEquals(f.balance("1"),9000L+refund);Assert.assertEquals(f.balance("3"),10150L);
            Assert.assertEquals(f.service.claim(f.viewer("2"),"123",move(claimant),"rps").toString(),result.toString());rejected(409,()->f.service.claim(f.viewer("2"),"123",move((chosenGesture+1)%3),"rps"));rejected(409,()->f.service.claim(f.viewer("2"),"123",move((chosenGesture+1)%3),"different-key"));
            Assert.assertEquals(f.service.detail(f.viewer("4"),"123").getInt("senderGesture"),sender);rejected(409,()->f.service.claim(f.viewer("4"),"123",move(0),"too-late"));
            f.begin();Assert.assertTrue(f.management.removeInCurrentTransaction("123",null));f.commit();Assert.assertEquals(f.count("slots"),1L);
        }
        System.out.println("PASS risk packet 5 all nine RPS gesture pairs, escrow/refund/tax conservation, sender ownership, pre-settlement secrecy, immutable gesture replay and settled deletion");
        for(String fault:List.of("slots.after","packets.after","transfers.after","receipts.after","data.after","event.after","commit"))try(var f=new F()){
            f.create(rps(0));long ledger=f.count("transfers");f.faults.add(fault);rejected(503,()->f.service.claim(f.viewer("2"),"123",move(1),"loss"));Assert.assertEquals(f.balance("2"),10000L);Assert.assertEquals(f.balance("1"),9000L);Assert.assertEquals(f.count("transfers"),ledger);Assert.assertEquals(query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"),0);
            f.faults.clear();Assert.assertEquals(f.service.claim(f.viewer("2"),"123",move(1),"loss").getInt("amount"),-850);Assert.assertEquals(f.balance("1"),10700L);
        }
        try(var f=new F()){f.create(rps(0));exec(f.writer,"UPDATE user SET userPoint=849 WHERE oId='2'");rejected(409,()->f.service.claim(f.viewer("2"),"123",move(2),"poor-winner"));Assert.assertEquals(query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"),0);}
        for(JSONObject body:List.of(heart(),rps(0)))try(var f=new F()){exec(f.writer,"INSERT INTO membership VALUES('1','1','VIP4_MONTH',1,0)");var created=f.create(body);Assert.assertEquals(created.getInt("taxPoints"),0);Assert.assertEquals(created.getInt("poolPoints"),1000);Assert.assertEquals(f.balance("3"),10000L);}
        System.out.println("PASS risk packet 6 RPS losing debit plus double refund roll back atomically, pre-draw solvency holds even for winner and exact active VIP4 exempts both types");
        try(var f=new F()){
            final String path="/api/v3/chat/red-packets";
            Assert.assertEquals(f.http("POST",path,"both","missing-risk",new JSONObject(heart().toString()).put("riskAccepted",false).toString()).attr("test.status"),422);
            Assert.assertEquals(f.http("POST",path,"both","risk-create",heart().toString()).attr("test.status"),200);
            Assert.assertEquals(f.http("POST",path+"/123/claims","activity","missing-risk","{}").attr("test.status"),422);
            Assert.assertEquals(f.http("POST",path+"/123/claims","activity","claim",proof().toString()).attr("test.status"),200);
            Assert.assertEquals(f.http("POST",path+"/123/claims","activity","claim",proof().put("riskAccepted",false).toString()).attr("test.status"),422);
        }
        try(var f=new F()){
            final String path="/api/v3/chat/red-packets";
            Assert.assertEquals(f.http("POST",path,"both","rps-create",rps(0).toString()).attr("test.status"),200);
            Assert.assertEquals(f.http("POST",path+"/123/claims","activity","claim",proof().toString()).attr("test.status"),422);
            Assert.assertEquals(f.http("POST",path+"/123/claims","activity","claim",move(1).toString()).attr("test.status"),403);
        }
        System.out.println("PASS risk packet 7 actual HTTP routes enforce current risk acceptance and typed gesture before settlement, retaining scopes and sender ownership");

        for(int gesture=0;gesture<3;gesture++)try(var f=new F()){
            f.create(rps(0));exec(f.writer,"UPDATE user SET userStatus=4 WHERE oId='1'");
            var result=f.service.claim(f.viewer("2"),"123",move(gesture),"inactive-sender");
            Assert.assertEquals(f.balance("1"),9000L+result.getInt("senderRefundPoints"));
            Assert.assertEquals(query(f.reader,"SELECT userStatus FROM user WHERE oId='1'").getFirst().getInt("userStatus"),4);
            Assert.assertEquals(f.balance("2"),10000L+result.getInt("amount"));Assert.assertEquals(f.count("receipts"),2L);
        }
        try(var f=new F()){
            f.create(rps(0));exec(f.writer,"DELETE FROM user WHERE oId='1'");
            rejected(409,()->f.service.claim(f.viewer("2"),"123",move(1),"deleted-sender"));
            Assert.assertEquals(f.balance("2"),10000L);Assert.assertEquals(f.count("receipts"),1L);
            Assert.assertEquals(query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"),0);
            Assert.assertEquals(query(f.reader,"SELECT claimUserId FROM slots").getFirst().getString("claimUserId"),"");
        }
        System.out.println("PASS risk packet 8 inactive sender settles all outcomes without reactivation, deleted sender returns409 without debit/slot/receipt");
        try(var f=new F()){
            f.create(rps(0));var view=f.service.legacyOpen(f.viewer("2"),"123",new JSONObject().put("viewOnly",true));
            Assert.assertEquals(view.getJSONObject("info").getInt("got"),0);Assert.assertFalse(view.getJSONObject("info").has("gesture"));
            rejected(422,()->f.service.legacyOpen(f.viewer("2"),"123",new JSONObject().put("riskAccepted",true).put("gesture","1")));
            var claimed=f.service.legacyOpen(f.viewer("2"),"123",move(1));Assert.assertEquals(claimed.getJSONObject("info").getInt("got"),1);
            Assert.assertEquals(claimed.getJSONObject("info").getInt("gesture"),0);Assert.assertTrue(claimed.getJSONObject("info").has("userAvatarURL48"));
            f.service.legacyOpen(f.viewer("2"),"123",move(1));Assert.assertEquals(f.balance("2"),9150L);
            rejected(409,()->f.service.legacyOpen(f.viewer("2"),"123",move(2)));
        }
        try(var f=new F()){
            f.create(heart());negativeFirst(f);rejected(422,()->f.service.legacyOpen(f.viewer("2"),"123"));
            f.service.legacyOpen(f.viewer("2"),"123",proof());Assert.assertEquals(query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"),1);
        }
        System.out.println("PASS risk packet 9 legacy bridge read-only never claims, typed explicit risk/gesture settles and replays without private pre-settlement gesture");

        try(var f=new F()){
            f.create(rps(0));Assert.assertEquals(f.legacy("2","123").getInt("code"),-1);
            rejected(409,()->f.service.legacyOpen(f.viewer("2"),"123",move(1).put("expectedUserId","4")));
            Assert.assertEquals(f.legacy("2","123",move(1),"text/plain").getInt("code"),-1);Assert.assertEquals(f.balance("2"),10000L);
            Assert.assertEquals(f.legacy("2","123",new JSONObject().put("viewOnly",true)).getJSONObject("info").getInt("got"),0);
            f.legacy("2","123",move(1));Assert.assertEquals(f.balance("2"),9150L);
            f.legacy("2","123",move(1));Assert.assertEquals(f.balance("2"),9150L);
        }
        try(var f=new F()){
            Assert.assertEquals(f.legacy("2","999",new JSONObject().put("viewOnly",true)).getInt("code"),-1);
            Assert.assertEquals(f.legacy("2","999",proof()).getInt("code"),-1);
            Assert.assertEquals(f.legacy("2","999",new JSONObject().put("expectedUserId","2")).getInt("code"),-1);
            Assert.assertEquals(f.count("transfers"),0L);
        }
        System.out.println("PASS risk packet 10 actual legacy HTTP handler forwards typed body proof and read-only flag using authenticated identity, no duplicate settlement");

    }
}
