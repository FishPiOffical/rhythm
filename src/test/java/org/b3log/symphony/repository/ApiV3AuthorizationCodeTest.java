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
import org.b3log.symphony.model.*;
import org.b3log.symphony.service.ApiV3AuthorizationCodeService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Actual grant core/token core and bound redirect/code queries; base persistence explicitly adapted to SQLite. */
public class ApiV3AuthorizationCodeTest {
    static final String CLIENT="native_app",URI="https://client.example/callback?mode=exact",VERIFIER="a".repeat(43),CHALLENGE=ApiV3Pkce.challenge(VERIFIER);
    static class Grants extends ApiV3TokenLifecycleTest.Fixture {
        boolean development;
        final ApiV3AuthorizationCodeService grants=new ApiV3AuthorizationCodeService(){@Override protected long now(){return service.clock;}@Override protected boolean development(){return development;}};
        final ApiV3AuthorizationCodeRepository codes=new ApiV3AuthorizationCodeRepository(){
            @Override public String getName(){return "codes";}
            @Override public boolean hasTransactionBegun(){return active();}
            @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{locks.add("code");return query(active()?writer:reader,sql,args);}
            @Override public String add(JSONObject row)throws RepositoryException{insert("codes",row);if(faults.contains("code.add.after"))throw new RepositoryException("code insert failed");return row.getString("oId");}
            @Override public void remove(String id)throws RepositoryException{exec(writer,"DELETE FROM codes WHERE oId=?",id);if(faults.contains("code.remove.after"))throw new RepositoryException("cleanup code failure");}
            @Override public void update(String id,JSONObject row,String...columns)throws RepositoryException{Assert.assertEquals(columns,new String[]{"consumedAt","familyId"});exec(writer,"UPDATE codes SET consumedAt=?,familyId=? WHERE oId=?",row.getLong("consumedAt"),row.getString("familyId"),id);if(faults.contains("code.update.after"))throw new RepositoryException("code update failed");}
        };
        final ApiV3ClientRedirectRepository redirects=new ApiV3ClientRedirectRepository(){
            @Override public String getName(){return "redirects";}
            @Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{locks.add("redirect");return query(active()?writer:reader,sql,args);}
        };
        Grants()throws Exception{
            client(CLIENT,"content.read content.write profile.read");client("other","content.read");
            exec(writer,"CREATE TABLE redirects(oId TEXT PRIMARY KEY,clientId TEXT,redirectUri TEXT,status INTEGER)");
            exec(writer,"CREATE TABLE codes(oId TEXT PRIMARY KEY,clientId TEXT,userId TEXT,redirectUri TEXT,scope TEXT,codeChallenge TEXT,createdAt BIGINT,expiresAt BIGINT,consumedAt BIGINT,familyId TEXT)");
            register(CLIENT,URI);
            for(var e:Map.<String,Object>of("clients",clients,"redirects",redirects,"codes",codes,"tokenRows",tokens,"tokens",service,"users",new UserRepository(){@Override public String getName(){return "user";}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{locks.add("grant-user");return query(writer,sql,args);}}).entrySet())inject(grants,ApiV3AuthorizationCodeService.class,e.getKey(),e.getValue());
        }
        void register(String client,String uri)throws Exception{exec(writer,"INSERT INTO redirects VALUES(?,?,?,0)",ApiV3ClientRedirectRepository.identity(client,uri),client,uri);}
        ApiV3AuthorizationCodeService.Issued issue(String scope)throws Exception{return issueAt(URI,scope);}
        ApiV3AuthorizationCodeService.Issued issueAt(String uri,String scope)throws Exception{begin();try{var value=grants.issueInCurrentTransaction("1",CLIENT,uri,scope,CHALLENGE,"S256");commit();return value;}finally{if(active())rollback();}}
        String all()throws Exception{return snapshot()+query(reader,"SELECT * FROM codes ORDER BY oId")+query(reader,"SELECT * FROM redirects ORDER BY oId");}
        JSONObject exchange(String raw)throws Exception{return grants.exchange(CLIENT,URI,raw,VERIFIER);}
    }
    interface Work{void run()throws Exception;}
    static void invalid(Work work)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,400);Assert.assertEquals(e.code,"invalid_grant");}}
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Grants()){
            var code=f.issue("content.write content.read");Assert.assertEquals(code.expiresAt(),400_000);Assert.assertTrue(ApiV3TokenUtil.isValidFormat(code.code()));Assert.assertFalse(code.toString().contains(code.code()));
            var stored=query(f.reader,"SELECT * FROM codes").getFirst();Assert.assertFalse(stored.toString().contains(code.code()));Assert.assertFalse(stored.toString().contains(VERIFIER));Assert.assertEquals(stored.getString("scope"),"content.read content.write");Assert.assertEquals(stored.getLong("consumedAt"),0);
            f.locks.clear();var result=f.exchange(code.code());Assert.assertEquals(result.getString("scope"),"content.read content.write");Assert.assertTrue(f.locks.indexOf("client")<f.locks.indexOf("redirect"));Assert.assertTrue(f.locks.indexOf("redirect")<f.locks.indexOf("code"));
            Assert.assertEquals(query(f.reader,"SELECT * FROM tokens").size(),2);Assert.assertEquals(query(f.reader,"SELECT consumedAt FROM codes").getFirst().getLong("consumedAt"),100_000);
            Assert.assertNotNull(f.service.authenticate(result.getString("accessToken")));var other=f.grant(CLIENT,"content.read");invalid(()->f.exchange(code.code()));Assert.assertNull(f.service.authenticate(result.getString("accessToken")));Assert.assertNotNull(f.service.authenticate(other.getString("accessToken")));
        }
        System.out.println("PASS registered S256 grant stores only a hash and binding, consumes once with its token pair, and valid-proof replay revokes exactly the issued family without affecting another grant");
        try(var f=new Grants()){
            var code=f.issue("content.read");String before=f.all();
            for(String uri:List.of(URI+"&extra=1",URI.replace("callback","Callback"),URI.replace("client.example","client.example.evil"),"http://client.example/callback","https://client.example/callback#fragment"))invalid(()->f.grants.exchange(CLIENT,uri,code.code(),VERIFIER));
            invalid(()->f.grants.exchange("other",URI,code.code(),VERIFIER));invalid(()->f.grants.exchange(CLIENT,URI,code.code(),"b".repeat(43)));Assert.assertEquals(f.all(),before);
            var result=f.exchange(code.code());invalid(()->f.grants.exchange(CLIENT,URI,code.code(),"b".repeat(43)));Assert.assertNotNull(f.service.authenticate(result.getString("accessToken")));
        }
        System.out.println("PASS exact client/URI including path case and query binding, PKCE proof and syntax reject without consuming a valid code or letting a wrong-proof replay revoke tokens");
        for(String fault:List.of("add.access","add.refresh","code.update.after","commit"))try(var f=new Grants()){
            var code=f.issue("content.read");String before=f.all();f.faults.add(fault);try{f.exchange(code.code());Assert.fail(fault);}catch(Exception expected){}Assert.assertEquals(f.all(),before,fault);f.faults.clear();Assert.assertNotNull(f.exchange(code.code()).getString("accessToken"));
        }
        try(var f=new Grants()){
            String before=f.all();f.faults.add("code.add.after");f.begin();try{try{f.grants.issueInCurrentTransaction("1",CLIENT,URI,"content.read",CHALLENGE,"S256");Assert.fail();}catch(RepositoryException expected){}f.commit();}finally{if(f.active())f.rollback();}Assert.assertEquals(f.all(),before);
        }
        try(var f=new Grants()){
            var code=f.issue("content.read");var result=f.exchange(code.code());String before=f.all();f.faults.add("family.after");try{f.exchange(code.code());Assert.fail();}catch(RepositoryException expected){}Assert.assertEquals(f.all(),before);Assert.assertNotNull(f.service.authenticate(result.getString("accessToken")));
        }
        System.out.println("PASS half-token, code consumption, strict root commit and replay revocation failures roll back atomically; local code-issue failure leaves no grant even if its owner catches and commits");
        for(String mutation:List.of("UPDATE user SET userStatus=1 WHERE oId='1'","UPDATE clients SET status=1 WHERE clientId='native_app'","UPDATE redirects SET status=1"))try(var f=new Grants()){
            var code=f.issue("content.read");exec(f.writer,mutation);String before=f.all();invalid(()->f.exchange(code.code()));Assert.assertEquals(f.all(),before);
        }
        try(var f=new Grants()){
            var code=f.issue("content.read content.write");exec(f.writer,"UPDATE clients SET scope='content.read profile.read' WHERE clientId=?",CLIENT);var result=f.exchange(code.code());Assert.assertEquals(result.getString("scope"),"content.read");
        }
        try(var f=new Grants()){
            var code=f.issue("content.read");f.service.clock=code.expiresAt();invalid(()->f.exchange(code.code()));Assert.assertTrue(query(f.reader,"SELECT * FROM tokens").isEmpty());
        }
        System.out.println("PASS current user/client/redirect revocation and exact expiry fail closed, while current client scope reduction shrinks the original approval without gaining newly allowed permissions");
        try(var f=new Grants()){
            try{f.grants.issueInCurrentTransaction("1",CLIENT,URI,"content.read",CHALLENGE,"S256");Assert.fail();}catch(IllegalStateException expected){}
            f.begin();try{try{f.exchange(ApiV3TokenUtil.generate());Assert.fail();}catch(IllegalStateException expected){}f.commit();}finally{if(f.active())f.rollback();}
            for(String scope:List.of("","content.read unknown","content.read  content.write"))invalid(()->f.issue(scope));
            for(String method:List.of("plain","","s256")){f.begin();try{try{f.grants.issueInCurrentTransaction("1",CLIENT,URI,"content.read",CHALLENGE,method);Assert.fail();}catch(IllegalArgumentException expected){}}finally{f.rollback();}}
            Assert.assertTrue(query(f.reader,"SELECT * FROM codes").isEmpty());
        }
        try(var f=new Grants()){
            f.begin();try{f.grants.issueInCurrentTransaction("1",CLIENT,URI,"content.read",CHALLENGE,"S256");Assert.assertTrue(query(f.reader,"SELECT * FROM codes").isEmpty());Assert.assertTrue(query(f.reader,"SELECT * FROM tokens").isEmpty());}finally{f.rollback();}
            Assert.assertTrue(query(f.reader,"SELECT * FROM codes").isEmpty());
        }
        for(String mutation:List.of("consumedAt=-1","expiresAt=999999999","familyId='uncommitted-family'","userId='invalid-id'"))try(var f=new Grants()){
            var code=f.issue("content.read");exec(f.writer,"UPDATE codes SET "+mutation);String before=f.all();invalid(()->f.exchange(code.code()));Assert.assertEquals(f.all(),before);
        }
        System.out.println("PASS observers cannot read an uncommitted authorization grant, rollback removes it and corrupt lifetime/consumption/family/user bindings fail closed without issuing tokens");
        for(String uri:List.of("https://client.example/cb","https://client.example:8443/cb?one=1","com.example.app:/oauth2redirect"))Assert.assertTrue(ApiV3OAuthRedirects.valid(uri,false),uri);
        for(String uri:List.of("http://localhost:3000/cb","http://127.0.0.1:3000/cb","http://[::1]:3000/cb")){Assert.assertTrue(ApiV3OAuthRedirects.valid(uri,true),uri);Assert.assertFalse(ApiV3OAuthRedirects.valid(uri,false),uri);}
        for(String uri:List.of("javascript:alert(1)","data:text/plain,hello","file:///callback","myapp:/callback","com.example.app://callback","com.example.app:callback","https://user:pass@client.example/cb","https://client.example/cb#","http://localhost.evil/cb","http://127.1/cb","https://client.example:0/cb","https://client.example:65536/cb","https://client.example:/cb","https://client.example/\u0000"))Assert.assertFalse(ApiV3OAuthRedirects.valid(uri,true),uri);
        try(var f=new Grants()){
            String nativeUri="com.example.app:/oauth2redirect";f.register(CLIENT,nativeUri);var nativeCode=f.issueAt(nativeUri,"content.read");Assert.assertTrue(f.grants.exchange(CLIENT,nativeUri,nativeCode.code(),VERIFIER).has("accessToken"));
            String loopback="http://127.0.0.1:3000/cb";f.register(CLIENT,loopback);invalid(()->f.issueAt(loopback,"content.read"));f.development=true;var dev=f.issueAt(loopback,"content.read");f.development=false;invalid(()->f.grants.exchange(CLIENT,loopback,dev.code(),VERIFIER));f.development=true;Assert.assertTrue(f.grants.exchange(CLIENT,loopback,dev.code(),VERIFIER).has("accessToken"));
        }
        System.out.println("PASS approved HTTPS, development-only loopback and registered reverse-domain native callbacks apply at issue and exchange, rejecting unsafe schemes, credentials, fragments and host/port disguises");
        System.out.println("PASS grant primitives require an owner transaction, exchange owns its root and invalid scopes or non-S256 methods cannot create a grant");
    }
}
