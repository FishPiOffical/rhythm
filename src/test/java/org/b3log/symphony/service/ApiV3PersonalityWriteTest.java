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
package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.symphony.processor.PersonalityRouteBindings;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;

/** Actual HTTP, profile quota, current-owner primitive, coordinator and SQL adapter; no application DB. */
public class ApiV3PersonalityWriteTest {
    static int sequence=100;
    static ApiV3AvatarWriteTest.Fixture fixture()throws Exception{return new ApiV3AvatarWriteTest.Fixture("personality",String.valueOf(++sequence));}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=fixture()){
            PersonalityRouteBindings.register(f.controller,f.auth,f.writes);org.b3log.latke.http.Dispatcher.mapping();
            var c=f.put("writer","first","{\"mbti\":\" infp-a \"}",true);Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(((JSONObject)c.attr("test.data")).keySet(),Set.of("mbti"));Assert.assertEquals(f.queryMBTI(),"INFP-A");
            Assert.assertEquals(f.put("second","intj").attr("test.status"),200);int updates=f.updates;
            Assert.assertEquals(((JSONObject)f.put("first","INFP-A").attr("test.data")).getString("mbti"),"INFP-A");Assert.assertEquals(f.updates,updates);Assert.assertEquals(f.queryMBTI(),"INTJ");
            Assert.assertEquals(f.put("first","ENFP").attr("test.status"),409);Assert.assertEquals(f.updates,updates);
            Assert.assertEquals(f.put("clear", "  ").attr("test.status"),200);Assert.assertEquals(f.queryMBTI(),"");
            Assert.assertEquals(f.row("SELECT * FROM users WHERE oId=?",f.actor).getString("userAvatarURL"),"https://old.example/one");Assert.assertEquals(f.row("SELECT * FROM users WHERE oId=?",f.actor).getString("userPassword"),"PRIVATE");Assert.assertEquals(f.row("SELECT * FROM users WHERE oId='2'").getString("mbti"),"ENFP");Assert.assertTrue(f.refreshes>0);
            for(int i=0;i<2;i++)Assert.assertEquals(f.put("quota-"+i,"ISTJ").attr("test.status"),200);
            Assert.assertEquals(f.put("quota-exhausted","ISFP").attr("test.status"),429);Assert.assertEquals(f.queryMBTI(),"ISTJ");
            Assert.assertEquals(f.put("first","infp-a").attr("test.status"),200);Assert.assertEquals(f.queryMBTI(),"ISTJ");
        }
        System.out.println("PASS MBTI PUT actual route/scope/normalization, SQL field isolation and GET; clear and replay preserve later changes, normalized keys match, conflict409 and original shared profile quota429 do not overwrite state");
        try(var f=fixture()){
            for(String token:Arrays.asList(null,"invalid","reader"))Assert.assertEquals(f.put(token,"auth","{\"mbti\":\"INTJ\"}",false).attr("test.status"),token==null||token.equals("invalid")?401:403);
            for(String body:List.of("{}","{\"mbti\":null}","{\"mbti\":1}","{\"mbti\":\"INTJ-A-extra\"}","{\"mbti\":\"ENFP\",\"userId\":\"2\"}"))Assert.assertEquals(f.put("writer","invalid",body,false).attr("test.status"),422);
            Assert.assertEquals(f.put("writer",null,"{\"mbti\":\"INTJ\"}",false).attr("test.status"),422);Assert.assertEquals(f.updates,0);
            f.sql("UPDATE users SET userStatus=4 WHERE oId=?",f.actor);Assert.assertEquals(f.put("disabled","INTJ").attr("test.status"),404);Assert.assertNull(f.row("SELECT * FROM receipts"));
        }
        System.out.println("PASS MBTI HTTP rejects missing/wrong-scope Bearer, null/nonstring/extra fields, trailing fragments and missing key; newly disabled owner cannot mutate or retain a receipt");
        for(String fault:List.of("write","receipt","commit"))try(var f=fixture()){
            f.failWrite=fault.equals("write");f.failReceipt=fault.equals("receipt");f.failCommit=fault.equals("commit");Assert.assertNotEquals(f.put("failure","ESTJ-T").attr("test.status"),200);
            Assert.assertEquals(f.queryMBTI(),"INTJ");Assert.assertEquals(f.row("SELECT * FROM users WHERE oId=?",f.actor).getLong("userUpdateTime"),1L);Assert.assertNull(f.row("SELECT * FROM receipts"));Assert.assertFalse(f.users.hasTransactionBegun());
            f.failWrite=f.failReceipt=f.failCommit=false;Assert.assertEquals(f.put("failure","ESTJ-T").attr("test.status"),200);Assert.assertEquals(f.queryMBTI(),"ESTJ-T");
        }
        System.out.println("PASS real MBTI coordinator rolls back value/time and idempotency receipt on user-write/receipt/commit faults; same-key recovery succeeds and cache refresh never joins an active transaction");
    }
}
