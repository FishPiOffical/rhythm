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
import org.b3log.latke.ioc.BeanManager;
import org.b3log.symphony.service.AccountCredentialMgmtService;
import org.b3log.latke.http.*;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.processor.ApiV3AccountProcessor;
import org.b3log.symphony.processor.LoginProcessor;
import org.b3log.symphony.service.AccountPasswordResetService;
import org.b3log.symphony.service.AccountVerificationService;
import org.b3log.symphony.util.*;
import org.b3log.symphony.util.Sessions;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.ApiV3AuthorizationCodeTest.*;

/** Actual reset/verification/revocation SQL and new/legacy controllers. SQLite, Cookie issuance and global routing are adapters. */
public class AccountPasswordResetTest {
    static final String PASSWORD="a".repeat(32),PHONE="13800000001";
    static class Controller extends ApiV3AccountProcessor {
        boolean allowed=true;@Override protected boolean resetAllowed(String ip){return allowed;}
    }
    static class Reset extends ApiV3AuthorizationRequestTest.Consent {
        int evictions;final List<String> order=new ArrayList<>();
        final AccountPasswordResetService reset=new AccountPasswordResetService(){@Override protected long now(){return 100_000;}@Override protected void unavailable(Exception e){if(faults.isEmpty())throw new AssertionError("Unexpected fixture reset failure",e);}};
        final AccountVerificationService verification=new AccountVerificationService(){@Override protected long now(){return 100_000;}};
        final Controller controller=new Controller();final LoginProcessor legacy=new LoginProcessor();
        Reset()throws Exception {
            exec(writer,"ALTER TABLE user ADD COLUMN userPhone TEXT");
            exec(writer,"UPDATE user SET userPhone='13800000001',userPassword='before' WHERE oId='1'");exec(writer,"UPDATE user SET userPhone='13800000002',userPassword='other' WHERE oId='2'");
            exec(writer,"CREATE TABLE verifycode(oId TEXT PRIMARY KEY,userId TEXT,receiver TEXT,type INTEGER,bizType INTEGER,code TEXT,status INTEGER,expired BIGINT)");sms("1","1",PHONE,1,200_000);
            final var owners=new UserRepository(){
                @Override public String getName(){return "user";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{order.add("user");return query(writer,sql,args);}
                @Override public void updateColumnsInCurrentTransaction(String id,JSONObject changes)throws RepositoryException {
                    Assert.assertEquals(changes.keySet(),Set.of("userPassword"));exec(writer,"UPDATE user SET userPassword=? WHERE oId=?",changes.getString("userPassword"),id);fault("password");
                }
                @Override public void evictSettingsCaches(String id){Assert.assertFalse(active());evictions++;}
            };
            final var proofs=new VerifycodeRepository(){
                @Override public String getName(){return "verifycode";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{order.add("verify");return query(writer,sql,args);}
                @Override public void remove(String id)throws RepositoryException{exec(writer,"DELETE FROM verifycode WHERE oId=?",id);fault("verify");}
            };
            inject(verification,AccountVerificationService.class,"users",owners);inject(verification,AccountVerificationService.class,"codes",proofs);
            inject(reset,AccountPasswordResetService.class,"verification",verification);inject(reset,AccountPasswordResetService.class,"users",owners);inject(reset,AccountPasswordResetService.class,"clients",clients);
            inject(reset,AccountPasswordResetService.class,"tokens",new ApiV3TokenRepository(){
                @Override public String getName(){return "tokens";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public int revokeUserInCurrentTransaction(String id,long now)throws RepositoryException{order.add("revoke");int n=super.revokeUserInCurrentTransaction(id,now);fault("revoke");return n;}
            });
            inject(reset,AccountPasswordResetService.class,"codes",new ApiV3AuthorizationCodeRepository(){
                @Override public String getName(){return "codes";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public int invalidatePendingForUserInCurrentTransaction(String id)throws RepositoryException{int n=super.invalidatePendingForUserInCurrentTransaction(id);fault("codes");return n;}
            });
            inject(reset,AccountPasswordResetService.class,"requests",new ApiV3AuthorizationRequestRepository(){
                @Override public String getName(){return "auth_requests";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public int invalidatePendingForUserInCurrentTransaction(String id)throws RepositoryException{int n=super.invalidatePendingForUserInCurrentTransaction(id);fault("requests");return n;}
            });
            exec(writer,"CREATE TABLE legacy_refresh(oId TEXT PRIMARY KEY,userId TEXT,state INTEGER,updatedAt BIGINT)");
            exec(writer,"INSERT INTO legacy_refresh VALUES('legacy1','1',0,0),('legacy2','2',0,0)");
            final var epochStates=BeanManager.getInstance().getReference(AccountCredentialStateRepository.class);
            final var credentialWrites=new AccountCredentialMgmtService(){@Override protected long now(){return 100_000;}};
            inject(credentialWrites,AccountCredentialMgmtService.class,"users",owners);
            inject(credentialWrites,AccountCredentialMgmtService.class,"states",epochStates);
            inject(credentialWrites,AccountCredentialMgmtService.class,"openid",new OpenIdRefreshTokenRepository(){@Override public String getName(){return "legacy_refresh";}});
            for(String name:List.of("tokens","requests","codes")){
                final var field=AccountPasswordResetService.class.getDeclaredField(name);field.setAccessible(true);
                inject(credentialWrites,AccountCredentialMgmtService.class,name,field.get(reset));
            }
            inject(reset,AccountPasswordResetService.class,"credentials",credentialWrites);
            inject(reset,AccountPasswordResetService.class,"states",epochStates);
            AccountCredentialVersions.remember(AccountCredentialVersions.current("1"));
            inject(controller,ApiV3AccountProcessor.class,"passwords",reset);inject(legacy,LoginProcessor.class,"passwordResets",reset);
        }
        void fault(String name)throws RepositoryException{if(faults.contains("reset."+name))throw new RepositoryException("fixture reset statement failure");}
        void sms(String id,String user,String phone,int purpose,long expiry)throws Exception{exec(writer,"INSERT INTO verifycode VALUES(?,?,?,1,?,'123456',0,?)",id,user,phone,purpose,expiry);}
        String resetSnapshot()throws Exception{return all()+query(reader,"SELECT * FROM credential_states ORDER BY oId")+query(reader,"SELECT * FROM legacy_refresh ORDER BY oId")+query(reader,"SELECT * FROM auth_requests ORDER BY oId")+query(reader,"SELECT * FROM user ORDER BY oId")+query(reader,"SELECT * FROM verifycode ORDER BY oId");}
        JSONObject http(boolean old,String body,String query,String type)throws Exception{
            var netty=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,old?"/reset-pwd":"/api/v3/auth/password-reset/complete"+(query==null?"":"?"+query),io.netty.buffer.Unpooled.copiedBuffer(body,java.nio.charset.StandardCharsets.UTF_8));
            try{
                if(type!=null)netty.headers().set("Content-Type",type);netty.headers().set("Cookie","ignored");var req=new Request(null,netty){@Override public String getRemoteAddr(){return "192.0.2.55";}};req.setBytes(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(old){var parser=Request.class.getDeclaredMethod("parseForm");parser.setAccessible(true);parser.invoke(req);}
                var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);var c=new RequestContext(req,new Response(null,response));c.attr("user",new JSONObject().put("oId","2"));
                if(old)legacy.resetPwd(c);else controller.completePasswordReset(c);
                var out=new JSONObject().put("status",c.attr("test.status")==null?response.status().code():c.attr("test.status"));
                if(c.attr("test.data")!=null)out.put("data",c.attr("test.data"));if(c.attr("test.code")!=null)out.put("code",c.attr("test.code"));
                if(c.getRenderer() instanceof JsonRenderer renderer)out.put("legacy",renderer.getJSONObject());return out;
            }finally{netty.release();}
        }
    }
    interface Work{void run()throws Exception;}
    static void rejected(Work action,int status)throws Exception{try{action.run();Assert.fail();}catch(ApiV3Exception expected){Assert.assertEquals(expected.status,status);}}
    public static void main(String[] args)throws Exception{
        var p=new Properties();p.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(p);Latkes.setLocalProps(p);
        try(var f=new Reset()){
            var consumed=f.issue("content.read");var pair=f.exchange(consumed.code());var other=f.grant("other","content.read");
            final var sample=f.token(pair.getString("accessToken"));f.begin();
            for(int i=0;i<250;i++)f.insert("tokens",new JSONObject(sample.toString()).put("oId",Long.toString(1_000_000_000_000_000_000L+i)).put("tokenHash","bulk-hash-"+i).put("familyId","bulk-family-"+i));f.commit();
            f.begin();var user2=f.service.issueForClientInCurrentTransaction("2",CLIENT,"content.read");f.commit();
            f.issue("content.read");f.consent.start("1",CLIENT,URI,"content.read","state",CHALLENGE,"S256");AccountCredentialVersions.remember(AccountCredentialVersions.current("2"));f.consent.start("2",CLIENT,URI,"content.read","other",CHALLENGE,"S256");AccountCredentialVersions.remember(AccountCredentialVersions.current("1"));
            f.locks.clear();Assert.assertTrue(f.reset.complete(PHONE,"123456",PASSWORD).getBoolean("changed"));Assert.assertEquals(f.locks.getFirst(),"client");Assert.assertEquals(f.order.getFirst(),"user");
            Assert.assertEquals(query(f.reader,"SELECT userPassword FROM user WHERE oId='1'").getFirst().getString("userPassword"),PASSWORD);
            Assert.assertNull(f.service.authenticate(pair.getString("accessToken")));Assert.assertNull(f.service.authenticate(other.getString("accessToken")));Assert.assertNotNull(f.service.authenticate(user2.getString("accessToken")));
            Assert.assertTrue(query(f.reader,"SELECT oId FROM tokens WHERE userId='1' AND state=0").isEmpty());
            Assert.assertTrue(query(f.reader,"SELECT * FROM codes WHERE userId='1' AND consumedAt=0").isEmpty());Assert.assertEquals(query(f.reader,"SELECT * FROM codes WHERE userId='1' AND consumedAt>0").size(),1);
            Assert.assertTrue(query(f.reader,"SELECT * FROM auth_requests WHERE userId='1' AND decision='pending'").isEmpty());Assert.assertEquals(query(f.reader,"SELECT * FROM auth_requests WHERE userId='2'").size(),1);
            Assert.assertTrue(query(f.reader,"SELECT * FROM verifycode").isEmpty());Assert.assertTrue(f.evictions>0);rejected(()->f.reset.complete(PHONE,"123456",PASSWORD),422);
        }
        System.out.println("PASS real reset atomically changes password, consumes phone proof, revokes tokens across clients and removes pending OAuth grants while preserving consumed-code evidence and another user's credentials; client locks precede account/code locks");
        for(String change:List.of("UPDATE verifycode SET bizType=0","UPDATE verifycode SET expired=100000","UPDATE user SET userPhone='13800000003' WHERE oId='1'","UPDATE user SET userStatus=1 WHERE oId='1'"))try(var f=new Reset()){
            exec(f.writer,change);String before=f.resetSnapshot();rejected(()->f.reset.complete(PHONE,"123456",PASSWORD),422);Assert.assertEquals(f.resetSnapshot(),before);
        }
        try(var f=new Reset()){
            String before=f.resetSnapshot();rejected(()->f.reset.complete("13800000002","123456",PASSWORD),422);rejected(()->f.reset.complete(PHONE,"654321",PASSWORD),422);Assert.assertEquals(f.resetSnapshot(),before);
            rejected(()->f.reset.complete(PHONE,"123456","plaintext"),422);rejected(()->f.reset.complete(PHONE,"123456",PASSWORD.toUpperCase()),422);
            f.begin();try{f.reset.complete(PHONE,"123456",PASSWORD);Assert.fail();}catch(IllegalStateException expected){}f.rollback();
        }
        System.out.println("PASS wrong account/phone/current status/purpose/code, exact expiry and invalid password digest fail without state changes; nested root ownership is rejected");
        for(String fault:List.of("reset.verify","reset.password","reset.requests","reset.codes","reset.revoke","commit"))try(var f=new Reset()){
            f.grant(CLIENT,"content.read");f.issue("content.read");f.consent.start("1",CLIENT,URI,"content.read","s",CHALLENGE,"S256");String before=f.resetSnapshot();f.faults.add(fault);
            rejected(()->f.reset.complete(PHONE,"123456",PASSWORD),503);Assert.assertEquals(f.resetSnapshot(),before,fault);Assert.assertTrue(f.evictions>0);f.faults.clear();Assert.assertTrue(f.reset.complete(PHONE,"123456",PASSWORD).getBoolean("changed"));
        }
        System.out.println("PASS consumption/password/pending-request/pending-code/token-revocation and strict commit faults restore every table; cache invalidation only runs after transaction end and valid proof remains usable after rollback");
        try(var f=new Reset()){
            for(String body:List.of("{\"userPhone\":\""+PHONE+"\",\"code\":123456,\"userPassword\":\""+PASSWORD+"\"}","{\"userPhone\":\""+PHONE+"\",\"code\":\"123456\",\"userPassword\":\""+PASSWORD+"\",\"userId\":\"2\"}"))Assert.assertEquals(f.http(false,body,null,"application/json").getInt("status"),422);
            String body=new JSONObject().put("userPhone",PHONE).put("code","123456").put("userPassword",PASSWORD).toString();Assert.assertEquals(f.http(false,body,"apiKey=ignored","application/json").getInt("status"),422);
            Assert.assertEquals(f.http(false,body,null,"text/plain").getInt("status"),415);f.controller.allowed=false;Assert.assertEquals(f.http(false,body,null,"application/json").getInt("status"),429);f.controller.allowed=true;
            var result=f.http(false,body,null,"application/json");Assert.assertEquals(result.getInt("status"),200);Assert.assertTrue(result.getJSONObject("data").getBoolean("changed"));Assert.assertFalse(result.toString().contains(PHONE));
        }
        try(var f=new Reset()){
            LoginProcessor.resetCodeLimiter.remove("192.0.2.55");Sessions.loggedIn=null;
            var body=new JSONObject().put("userId","2").put("code","123456").put("userPassword",PASSWORD);f.http(true,body.toString(),null,"application/json");Assert.assertNull(Sessions.loggedIn);
            body.put("userId","1");f.http(true,body.toString(),null,"application/json");Assert.assertEquals(Sessions.loggedIn,"1");Assert.assertTrue(query(f.reader,"SELECT * FROM verifycode").isEmpty());
        }
        System.out.println("PASS actual v3 controller enforces typed bounded JSON/media/query and budget before reset, ignores Cookie/context identity, and extracted legacy reset preserves explicit user binding and logs in only after strict commit");
        String ip="reset-concurrency-"+UUID.randomUUID();var allowed=new AtomicInteger();try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var start=new CountDownLatch(1);var tasks=new ArrayList<Future<?>>();for(int i=0;i<64;i++)tasks.add(executor.submit(()->{try{start.await();if(PasswordResetQuota.access(ip))allowed.incrementAndGet();}catch(InterruptedException e){throw new RuntimeException(e);}}));start.countDown();for(var task:tasks)task.get();
        }
        Assert.assertEquals(allowed.get(),4);Assert.assertFalse(LoginProcessor.resetCodeLimiter.access(ip));LoginProcessor.resetCodeLimiter.remove(ip);
        System.out.println("PASS actual shared legacy reset limiter admits exactly its original budget under parallel v3 attempts; changing endpoint does not obtain a separate counter");
    }
}
