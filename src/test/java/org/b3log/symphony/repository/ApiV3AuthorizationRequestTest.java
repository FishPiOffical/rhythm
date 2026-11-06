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
import org.b3log.symphony.service.ApiV3AuthorizationRequestService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.ApiV3AuthorizationCodeTest.*;

/** Actual owner-bound consent state, authorization code and token core; explicit SQLite/base persistence adapters. */
public class ApiV3AuthorizationRequestTest {
    static class Consent extends Grants {
        final ApiV3AuthorizationRequestService consent=new ApiV3AuthorizationRequestService(){@Override protected long now(){return service.clock;}};
        final ApiV3AuthorizationRequestRepository requests=new ApiV3AuthorizationRequestRepository(){
            @Override public String getName(){return "auth_requests";}
            @Override public boolean hasTransactionBegun(){return active();}
            @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{locks.add(sql.endsWith(" FOR UPDATE")?"request-lock":"request-hint");return query(active()?writer:reader,sql,args);}
            @Override public String add(JSONObject row)throws RepositoryException{insert("auth_requests",row);if(faults.contains("request.add.after"))throw new RepositoryException("consent insert failed");return row.getString("oId");}
            @Override public void remove(String id)throws RepositoryException{exec(writer,"DELETE FROM auth_requests WHERE oId=?",id);if(faults.contains("request.remove.after"))throw new RepositoryException("cleanup request failure");}
            @Override public void update(String id,JSONObject row,String...columns)throws RepositoryException{Assert.assertEquals(columns,new String[]{"decision","decidedAt"});exec(writer,"UPDATE auth_requests SET decision=?,decidedAt=? WHERE oId=?",row.getString("decision"),row.getLong("decidedAt"),id);if(faults.contains("request.update.after"))throw new RepositoryException("consent decision failed");}
        };
        Consent()throws Exception{
            exec(writer,"CREATE TABLE auth_requests(oId TEXT PRIMARY KEY,userId TEXT,clientId TEXT,redirectUri TEXT,scope TEXT,state TEXT,codeChallenge TEXT,consentHash TEXT,createdAt BIGINT,expiresAt BIGINT,decidedAt BIGINT,decision TEXT)");
            inject(consent,ApiV3AuthorizationRequestService.class,"requests",requests);inject(consent,ApiV3AuthorizationRequestService.class,"codes",grants);
        }
        ApiV3AuthorizationRequestService.Prepared start(String state)throws Exception{return consent.start("1",CLIENT,URI,"content.read content.write",state,CHALLENGE,"S256");}
        ApiV3AuthorizationRequestService.Decision decide(ApiV3AuthorizationRequestService.Prepared p,boolean yes)throws Exception{return consent.decide("1",p.requestId(),p.consentToken(),yes);}
        String every()throws Exception{return all()+query(reader,"SELECT * FROM auth_requests ORDER BY oId");}
    }
    interface Work{void run()throws Exception;}
    static void rejected(Work work)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,400);Assert.assertEquals(e.code,"invalid_request");}}
    static Map<String,String> params(String uri){
        var values=new HashMap<String,String>();for(String pair:java.net.URI.create(uri).getRawQuery().split("&")){String[] parts=pair.split("=",2);String key=URLDecoder.decode(parts[0],StandardCharsets.UTF_8);Assert.assertFalse(values.containsKey(key));values.put(key,parts.length>1?URLDecoder.decode(parts[1],StandardCharsets.UTF_8):"");}return values;
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Consent()){
            String state="nonce + & % = 中文 😀";var prepared=f.start(state);Assert.assertEquals(prepared.scope(),"content.read content.write");Assert.assertEquals(prepared.expiresAt(),700_000);Assert.assertNotEquals(prepared.requestId(),prepared.consentToken());Assert.assertFalse(prepared.toString().contains(prepared.requestId()));Assert.assertFalse(prepared.toString().contains(prepared.consentToken()));
            var row=query(f.reader,"SELECT * FROM auth_requests").getFirst();Assert.assertFalse(row.toString().contains(prepared.requestId()));Assert.assertFalse(row.toString().contains(prepared.consentToken()));Assert.assertEquals(row.getString("state"),state);Assert.assertTrue(query(f.reader,"SELECT * FROM codes").isEmpty());Assert.assertTrue(query(f.reader,"SELECT * FROM tokens").isEmpty());
            f.locks.clear();var approved=f.decide(prepared,true);var values=params(approved.redirectUri());Assert.assertEquals(values.keySet(),Set.of("mode","code","state"));Assert.assertEquals(values.get("state"),state);Assert.assertEquals(values.get("mode"),"exact");Assert.assertFalse(approved.toString().contains(values.get("code")));Assert.assertTrue(approved.approved());
            Assert.assertTrue(f.locks.indexOf("client")<f.locks.indexOf("request-lock"));Assert.assertTrue(f.locks.indexOf("request-lock")<f.locks.indexOf("grant-user"));Assert.assertEquals(query(f.reader,"SELECT decision FROM auth_requests").getFirst().getString("decision"),"approved");
            var issued=f.exchange(values.get("code"));Assert.assertNotNull(f.service.authenticate(issued.getString("accessToken")));rejected(()->f.decide(prepared,true));rejected(()->f.decide(prepared,false));Assert.assertNotNull(f.service.authenticate(issued.getString("accessToken")));Assert.assertEquals(query(f.reader,"SELECT * FROM codes").size(),1);
        }
        System.out.println("PASS server-owned request stores only handle/proof hashes, approval consumes once with one code, callback preserves exact query/state, and the issued code completes real PKCE token exchange without consent replay side effects");
        try(var f=new Consent()){
            var prepared=f.start("cancel-state");var denied=f.decide(prepared,false);Assert.assertFalse(denied.approved());Assert.assertEquals(params(denied.redirectUri()),Map.of("mode","exact","error","access_denied","state","cancel-state"));Assert.assertTrue(query(f.reader,"SELECT * FROM codes").isEmpty());Assert.assertTrue(query(f.reader,"SELECT * FROM tokens").isEmpty());rejected(()->f.decide(prepared,true));
        }
        try(var f=new Consent()){
            var prepared=f.start("");String before=f.every();rejected(()->f.consent.decide("2",prepared.requestId(),prepared.consentToken(),true));rejected(()->f.consent.decide("1",prepared.requestId(),ApiV3TokenUtil.generate(),true));Assert.assertEquals(f.every(),before);Assert.assertFalse(params(f.decide(prepared,false).redirectUri()).containsKey("state"));
        }
        System.out.println("PASS explicit denial creates no authorization code, empty optional state stays absent, wrong owner/proof cannot consume or approve a request and completed decisions cannot be flipped");
        for(String fault:List.of("request.add.after","commit"))try(var f=new Consent()){
            String before=f.every();f.faults.add(fault);try{f.start("state");Assert.fail(fault);}catch(Exception expected){}Assert.assertEquals(f.every(),before);
        }
        for(boolean yes:List.of(false,true))for(String fault:List.of("request.update.after","commit"))try(var f=new Consent()){
            var prepared=f.start("state");String before=f.every();f.faults.add(fault);try{f.decide(prepared,yes);Assert.fail(fault);}catch(Exception expected){}Assert.assertEquals(f.every(),before);f.faults.clear();Assert.assertEquals(f.decide(prepared,yes).approved(),yes);
        }
        try(var f=new Consent()){
            var prepared=f.start("state");String before=f.every();f.faults.add("code.add.after");try{f.decide(prepared,true);Assert.fail();}catch(RepositoryException expected){}Assert.assertEquals(f.every(),before);f.faults.clear();Assert.assertTrue(f.decide(prepared,true).approved());
        }
        System.out.println("PASS creation, code issuance, decision update and strict commit failures restore the complete pending request and code state, allowing the original proof to retry without a half approval");
        for(String mutation:List.of("UPDATE user SET userStatus=1 WHERE oId='1'","UPDATE clients SET status=1 WHERE clientId='native_app'","UPDATE redirects SET status=1","UPDATE clients SET scope='content.read' WHERE clientId='native_app'"))try(var f=new Consent()){
            var prepared=f.start("state");exec(f.writer,mutation);String before=f.every();try{f.decide(prepared,true);Assert.fail();}catch(ApiV3Exception expected){}Assert.assertEquals(f.every(),before);
        }
        try(var f=new Consent()){
            var prepared=f.start("state");f.service.clock=prepared.expiresAt();String before=f.every();rejected(()->f.decide(prepared,true));Assert.assertEquals(f.every(),before);
        }
        for(String mutation:List.of("decidedAt=1","createdAt=999999999","decision='approved'","expiresAt=999999999","consentHash='bad'"))try(var f=new Consent()){
            var prepared=f.start("state");exec(f.writer,"UPDATE auth_requests SET "+mutation);String before=f.every();rejected(()->f.decide(prepared,true));Assert.assertEquals(f.every(),before);
        }
        System.out.println("PASS decisions recheck current account/client/redirect/scope and exact lifetime; corrupt state fails closed without a code or decision mutation");
        for(String state:List.of("x".repeat(1025),"中".repeat(342),"bad\nstate","\uD83D"))try{ApiV3OAuthCallbacks.validateState(state);Assert.fail();}catch(IllegalArgumentException expected){}
        for(String uri:List.of("https://client.example/cb?code=old","https://client.example/cb?%73tate=old","https://client.example/cb?ERROR=bad","com.example.app:/cb?iss=spoof"))Assert.assertFalse(ApiV3OAuthRedirects.valid(uri,true),uri);
        Assert.assertTrue(ApiV3OAuthRedirects.valid("https://client.example/回调?mode=中文",false));Assert.assertFalse(ApiV3OAuthRedirects.valid("https://client.example/"+"中".repeat(1024),false));
        String unicodeCallback=ApiV3OAuthCallbacks.result("https://client.example/回调?mode=中文","state",ApiV3TokenUtil.generate(),true);Assert.assertTrue(unicodeCallback.chars().allMatch(c->c<128));Assert.assertEquals(java.net.URI.create(unicodeCallback).getPath(),"/回调");Assert.assertEquals(params(unicodeCallback).get("mode"),"中文");
        String raw=ApiV3TokenUtil.generate();Assert.assertEquals(params(ApiV3OAuthCallbacks.result("com.example.app:/cb?mode=native","state +😀",raw,true)),Map.of("mode","native","code",raw,"state","state +😀"));
        try(var f=new Consent()){
            f.begin();try{try{f.start("state");Assert.fail();}catch(IllegalStateException expected){}}finally{f.rollback();}
        }
        System.out.println("PASS bounded Unicode state and safe response composition prevent reserved-parameter collisions, preserve native callback values and reject nested owner transactions");
    }
}
