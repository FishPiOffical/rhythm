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
import org.b3log.latke.repository.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.model.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Actual SMS coordinator/pending core/binding SQL. Providers and JDBC pool are explicit adapters. */
public class AccountRegistrationRequestTest {
    static final String INVITE="AbCdEfGh12345678",PHONE=AccountPendingRegistrationTest.PHONE,NAME=AccountPendingRegistrationTest.NAME;
    static final String CAPTCHA=new JSONObject().put("lot_number","lot").put("captcha_output","output").put("pass_token","proof").put("gen_time","1234567890").toString();
    static Object field(Object target,Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static class Requesting extends AccountPendingRegistrationTest.Pending {
        long clock=1_800_000_000_000L;int quota,captchas,sends,censors,legacySends,legacyProofs;boolean allowed=true,captcha=true,send=true,acceptable=true;Runnable duringSms=()->{};
        final AccountRegistrationRequestService request=new AccountRegistrationRequestService(){
            @Override protected long now(){return clock;}
            @Override protected String newCode(){return "314159";}
            @Override protected boolean allowed(String ip,String name,String phone){quota++;Assert.assertFalse(active());return allowed;}
            @Override protected boolean captchaAccepted(String proof){captchas++;if(faults.contains("captcha.throw"))throw new IllegalStateException("PRIVATE_CAPTCHA_"+proof);Assert.assertFalse(active());Assert.assertEquals(new JSONObject(proof).keySet(),new JSONObject(CAPTCHA).keySet());return captcha;}
            @Override protected boolean acceptableName(String name){censors++;Assert.assertFalse(active());return acceptable;}
            @Override protected boolean send(String phone,String code){sends++;if(faults.contains("provider.throw"))throw new IllegalStateException("PRIVATE_SMS_"+phone+":"+code);Assert.assertFalse(active());Assert.assertFalse(ArticlePublicationCaches.active());duringSms.run();return send;}
            @Override protected void unavailable(Exception e){if(faults.contains("log")){super.unavailable(e);return;}if(faults.isEmpty())throw new AssertionError("Unexpected request failure",e);}
        };
        final VerifycodeRepository codes;
        final org.b3log.symphony.processor.ApiV3AccountProcessor controller=new org.b3log.symphony.processor.ApiV3AccountProcessor();
        Requesting()throws Exception {
            exec(writer,"CREATE TABLE invitecode(oId TEXT PRIMARY KEY,code TEXT,userId TEXT,generatorId TEXT,status INTEGER,useTime BIGINT)");
            exec(writer,"INSERT INTO invitecode VALUES('10',?,'','sys',1,0)",INVITE);
            exec(writer,"CREATE TABLE verifycode(oId TEXT PRIMARY KEY,userId TEXT,receiver TEXT,type INTEGER,bizType INTEGER,code TEXT,status INTEGER,expired BIGINT,accountContext TEXT DEFAULT '')");
            exec(writer,"CREATE TABLE role_permission(oId TEXT PRIMARY KEY,roleId TEXT,permissionId TEXT)");
            codes=new VerifycodeRepository(){
                @Override public String getName(){return "verifycode";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(active()?writer:reader,sql,args);}
                @Override public String addRegistrationPhoneCodeInCurrentTransaction(JSONObject row,String name,String referral,String referralId,String invite)throws RepositoryException {
                    String id=super.addRegistrationPhoneCodeInCurrentTransaction(row,name,referral,referralId,invite);fault("proof.insert");return id;
                }
                @Override public void remove(String id)throws RepositoryException{exec(writer,"DELETE FROM verifycode WHERE oId=?",id);}
            };
            for(var entry:Map.<String,Object>of("users",owners,"options",field(service,AccountPendingRegistrationService.class,"options"),"names",field(service,AccountPendingRegistrationService.class,"names"),"pending",service,"codes",codes,
                    "invitations",new InvitecodeRepository(){@Override public String getName(){return "invitecode";}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(active()?writer:reader,sql,args);}},
                    "permissions",new RolePermissionRepository(){@Override public String getName(){return "role_permission";}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(active()?writer:reader,sql,args);}}).entrySet())inject(request,AccountRegistrationRequestService.class,entry.getKey(),entry.getValue());
        }
        JSONObject http(String body,String query,String type)throws Exception {return http(body,query,type,null);}
        JSONObject http(String body,String query,String type,List<org.b3log.latke.http.function.Handler> handlers)throws Exception {
            final var raw=new io.netty.handler.codec.http.DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/auth/registration/request"+(query==null?"":"?"+query),io.netty.buffer.Unpooled.copiedBuffer(body,java.nio.charset.StandardCharsets.UTF_8));
            try{
                if(type!=null)raw.headers().set("Content-Type",type);raw.headers().set("Cookie","legacy-ignored");raw.headers().set("Authorization","Bearer ignored");
                final var req=new org.b3log.latke.http.Request(null,raw){@Override public String getRemoteAddr(){return "192.0.2.130";}};req.setBytes(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                final var response=new io.netty.handler.codec.http.DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpResponseStatus.OK);
                final var c=new org.b3log.latke.http.RequestContext(req,new org.b3log.latke.http.Response(null,response){@Override public void sendRedirect(String url){setStatus(302);setHeader("Location",url);}});
                inject(controller,org.b3log.symphony.processor.ApiV3AccountProcessor.class,"registrationRequests",request);c.attr("user",new JSONObject().put("oId","999"));if(handlers==null)controller.requestRegistration(c);else{c.addHandlers(handlers);c.handle();}
                var out=new JSONObject().put("status",c.attr("test.status")==null?response.status().code():c.attr("test.status"));if(c.getResponse().getHeader("Location")!=null)out.put("location",c.getResponse().getHeader("Location"));if(c.attr("test.data")!=null)out.put("data",c.attr("test.data"));if(response.status().code()!=302)Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");return out;
            }finally{raw.release();}
        }
        JSONObject legacy(String name,String phone)throws Exception {
            final var processor=new org.b3log.symphony.processor.LoginProcessor();
            inject(processor,processor.getClass(),"registrationRequests",request);
            inject(processor,processor.getClass(),"userMgmtService",new UserMgmtService(){@Override public String addUser(JSONObject row)throws org.b3log.latke.service.ServiceException{
                Assert.assertEquals(row.getString("userPhone"),phone);Assert.assertEquals(row.getString("userName"),name);Assert.assertEquals(row.getString("userPassword"),"");
                try{return query(reader,"SELECT oId FROM user WHERE userPhone=?",phone).getFirst().getString("oId");}catch(Exception e){throw new org.b3log.latke.service.ServiceException("fixture old account lookup");}
            }});
            inject(processor,processor.getClass(),"optionQueryService",new OptionQueryService(){@Override public String getAllowRegister(){try{return query(reader,"SELECT optionValue FROM option WHERE oId=?",Option.ID_C_MISC_ALLOW_REGISTER).getFirst().getString("optionValue");}catch(Exception e){throw new IllegalStateException(e);}}});
            inject(processor,processor.getClass(),"langPropsService",new org.b3log.latke.service.LangPropsService(){@Override public String get(String key){return key;}});
            inject(processor,processor.getClass(),"verifycodeMgmtService",new VerifycodeMgmtService(){
                @Override public String addVerifycode(JSONObject row)throws org.b3log.latke.service.ServiceException{
                    Assert.assertFalse(active());final String id=Long.toString(clock+10+(++legacyProofs));
                    try{begin();try{exec(writer,"INSERT INTO verifycode(oId,userId,receiver,type,bizType,code,status,expired) VALUES(?,?,?,?,?,?,?,?)",id,row.getString("userId"),row.getString("receiver"),row.getInt("type"),row.getInt("bizType"),row.getString("code"),row.getInt("status"),row.getLong("expired"));commit();return id;}finally{if(active())rollback();}}catch(Exception e){throw new org.b3log.latke.service.ServiceException("fixture old proof insert");}
                }
                @Override public boolean sendVerifyCodeSMS(String target,String digits){Assert.assertFalse(active());Assert.assertEquals(target,phone);Assert.assertTrue(digits.matches("[0-9]{6}"));legacySends++;return true;}
            });
            final String body=new JSONObject().put("userName",name).put("userPhone",phone).put("invitecode",INVITE).toString();
            final var raw=new io.netty.handler.codec.http.DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/register",io.netty.buffer.Unpooled.copiedBuffer(body,java.nio.charset.StandardCharsets.UTF_8));
            org.b3log.symphony.censor.CensorFactory.provider=value->org.b3log.symphony.censor.CensorResult.pass();org.b3log.latke.util.Locales.setLocale(Locale.SIMPLIFIED_CHINESE);
            try{
                raw.headers().set("Content-Type","application/json");final var req=new org.b3log.latke.http.Request(null,raw){@Override public String getRemoteAddr(){return "192.0.2.135";}};req.setBytes(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var parser=req.getClass().getSuperclass().getDeclaredMethod("parseForm");parser.setAccessible(true);parser.invoke(req);
                var c=new org.b3log.latke.http.RequestContext(req,new org.b3log.latke.http.Response(null,new io.netty.handler.codec.http.DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","999"));processor.register(c);
                return ((org.b3log.latke.http.renderer.JsonRenderer)c.getRenderer()).getJSONObject();
            }finally{org.b3log.symphony.censor.CensorFactory.provider=null;raw.release();}
        }
        void mode(String mode)throws Exception{exec(writer,"UPDATE option SET optionValue=? WHERE oId=?",mode,Option.ID_C_MISC_ALLOW_REGISTER);}
        JSONObject ask(String invite,String referral)throws Exception{return request.request(NAME,PHONE,CAPTCHA,invite,referral,"zh_CN","192.0.2.130");}
        String snapshotAll()throws Exception{return all()+query(reader,"SELECT * FROM verifycode ORDER BY oId")+query(reader,"SELECT * FROM invitecode ORDER BY oId");}
        JSONObject proof()throws Exception{return query(reader,"SELECT * FROM verifycode ORDER BY oId DESC LIMIT 1").getFirst();}
        void inviter()throws Exception{user("2","inviter","13800000002",0,0);exec(writer,"UPDATE user SET userRole='inviter-role' WHERE oId='2'");exec(writer,"INSERT INTO role_permission VALUES('1','inviter-role',?)",Permission.PERMISSION_ID_C_COMMON_USE_INVITATION_LINK);}
    }
    static AccountRegistrationRequestService activationRequest(AccountRegistrationTest.Registration f)throws Exception {
        exec(f.writer,"ALTER TABLE user ADD COLUMN userRole TEXT");exec(f.writer,"ALTER TABLE user ADD COLUMN userLatestLoginTime BIGINT DEFAULT 0");
        exec(f.writer,"ALTER TABLE invitecode ADD COLUMN code TEXT");exec(f.writer,"UPDATE invitecode SET code=?",INVITE);
        exec(f.writer,"CREATE TABLE role_permission(oId TEXT PRIMARY KEY,roleId TEXT,permissionId TEXT)");
        exec(f.writer,"UPDATE option SET optionValue='2' WHERE oId=?",Option.ID_C_MISC_ALLOW_REGISTER);
        var owners=(UserRepository)field(f.registration,AccountRegistrationService.class,"users");
        var original=(OptionRepository)field(f.registration,AccountRegistrationService.class,"options");
        var settings=new OptionRepository(){@Override public String getName(){return "option";}@Override public boolean hasTransactionBegun(){return f.active();}@Override public JSONObject getForUpdate(String id)throws RepositoryException{return original.getForUpdate(id);}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(f.writer,sql,args);}};
        var names=field(f.registration,AccountRegistrationService.class,"names");
        var pending=new AccountPendingRegistrationService();inject(pending,AccountPendingRegistrationService.class,"users",owners);inject(pending,AccountPendingRegistrationService.class,"options",settings);inject(pending,AccountPendingRegistrationService.class,"names",names);
        var request=new AccountRegistrationRequestService(){@Override protected long now(){return f.clock;}@Override protected String newCode(){return "314159";}@Override protected boolean allowed(String ip,String name,String phone){return true;}@Override protected boolean captchaAccepted(String proof){return true;}@Override protected boolean acceptableName(String name){return true;}@Override protected boolean send(String phone,String code){Assert.assertFalse(f.active());return true;}@Override protected void unavailable(Exception e){throw new AssertionError(e);}};
        for(var entry:Map.<String,Object>of("users",owners,"options",settings,"names",names,"pending",pending,"codes",field(f.verification,AccountVerificationService.class,"codes"),"invitations",field(f.registration,AccountRegistrationService.class,"invitations"),
                "permissions",new RolePermissionRepository(){@Override public String getName(){return "role_permission";}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(f.writer,sql,args);}}).entrySet())inject(request,AccountRegistrationRequestService.class,entry.getKey(),entry.getValue());
        return request;
    }
    static JSONObject requestHttp(AccountRegistrationTest.Registration f,AccountRegistrationRequestService service)throws Exception {
        inject(f.controller,org.b3log.symphony.processor.ApiV3AccountProcessor.class,"registrationRequests",service);
        final String body=new JSONObject().put("userName","new-user").put("userPhone",PHONE).put("captcha",CAPTCHA).put("invitecode",INVITE).put("referral","inviter").toString();
        final var raw=new io.netty.handler.codec.http.DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/auth/registration/request",io.netty.buffer.Unpooled.copiedBuffer(body,java.nio.charset.StandardCharsets.UTF_8));
        try{
            raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy-ignored");
            final var req=new org.b3log.latke.http.Request(null,raw){@Override public String getRemoteAddr(){return "192.0.2.136";}};req.setBytes(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            final var response=new org.b3log.latke.http.Response(null,new io.netty.handler.codec.http.DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpResponseStatus.OK));
            final var c=new org.b3log.latke.http.RequestContext(req,response);c.attr("user",new JSONObject().put("oId","999"));f.controller.requestRegistration(c);
            Assert.assertEquals(response.getHeader("Cache-Control"),"no-store");Assert.assertEquals(c.attr("test.status"),200);return (JSONObject)c.attr("test.data");
        }finally{raw.release();}
    }
    interface Work{void run()throws Exception;}
    static void rejected(Work work,int status)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception expected){Assert.assertEquals(expected.status,status,expected.code);}}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Requesting()){
            Assert.assertEquals(f.ask("","").keySet(),Set.of("accepted"));Assert.assertEquals(f.quota,1);Assert.assertEquals(f.captchas,1);Assert.assertEquals(f.sends,1);
            var proof=f.proof();Assert.assertEquals(proof.getString("userId"),"100");Assert.assertEquals(proof.getString("receiver"),PHONE);Assert.assertEquals(proof.getString("code"),"314159");Assert.assertEquals(proof.getInt("status"),1);Assert.assertEquals(proof.getLong("expired"),f.clock+86_400_000L);Assert.assertTrue(RegistrationProofContext.matches(proof.getString("accountContext"),NAME,"","",""));
            final var verify=new AccountVerificationService(){@Override protected long now(){return f.clock;}};inject(verify,AccountVerificationService.class,"users",f.owners);inject(verify,AccountVerificationService.class,"codes",f.codes);
            f.begin();Assert.assertTrue(verify.consumeRegistrationCodeInCurrentTransaction("100","314159","","",""));f.rollback();Assert.assertEquals(query(f.reader,"SELECT * FROM verifycode").size(),1);
        }
        System.out.println("PASS real registration request requires shared quota/captcha/moderation, sends outside every SQL/cache boundary, then atomically creates a pending account and sent one-day name-bound proof; actual verification consumes only with its owner transaction");
        for(String path:List.of("open","link","code"))try(var f=new Requesting()){
            if(!path.equals("open"))f.mode("2");if(path.equals("link"))f.inviter();f.captcha=false;String original=f.snapshotAll();
            rejected(()->f.ask(path.equals("code")?INVITE:"",path.equals("link")?"inviter":""),422);Assert.assertEquals(f.captchas,1);Assert.assertEquals(f.sends,0);Assert.assertEquals(f.snapshotAll(),original);
        }
        try(var f=new Requesting()){f.mode("2");f.inviter();Assert.assertTrue(f.ask("","inviter").getBoolean("accepted"));Assert.assertTrue(RegistrationProofContext.matches(f.proof().getString("accountContext"),NAME,"inviter","2",""));}
        try(var f=new Requesting()){f.mode("2");String original=f.snapshotAll();rejected(()->f.ask("",""),422);Assert.assertEquals(f.sends,0);Assert.assertEquals(f.snapshotAll(),original);}
        System.out.println("PASS open, invitation-link and invitation-code registration all require captcha; link eligibility uses current active user role and actual permission rows, canonicalizes the referral and cannot silently omit a required invite");
        try(var f=new Requesting()){
            f.mode("2");f.ask(INVITE,"");var row=query(f.reader,"SELECT * FROM invitecode").getFirst();Assert.assertEquals(row.getString("userId"),"100");Assert.assertEquals(row.getLong("useTime"),f.clock);
            f.clock+=1000;f.ask(INVITE,"");Assert.assertEquals(f.additions,1);Assert.assertEquals(query(f.reader,"SELECT * FROM verifycode").size(),2);Assert.assertEquals(query(f.reader,"SELECT useTime FROM invitecode").getFirst().getLong("useTime"),f.clock);
            String original=f.snapshotAll();int sent=f.sends;rejected(()->f.request.request("other","13800000009",CAPTCHA,INVITE,"","zh_CN","192.0.2.131"),409);Assert.assertEquals(f.sends,sent);Assert.assertEquals(f.snapshotAll(),original);
            f.clock+=86_400_000L;f.request.request("other","13800000009",CAPTCHA,INVITE,"","zh_CN","192.0.2.131");
        }
        System.out.println("PASS the same phone reuses its pending ID and renews one invite lease with new proof; another phone is rejected before SMS during the exclusive day and the exact expiry boundary releases the invitation");
        for(String fault:List.of("add","statistic","proof.insert","commit"))try(var f=new Requesting()){
            f.mode("2");String original=f.snapshotAll();f.faults.add(fault);rejected(()->f.ask(INVITE,""),503);Assert.assertEquals(f.snapshotAll(),original,fault);Assert.assertFalse(f.active());Assert.assertFalse(ArticlePublicationCaches.active());Assert.assertEquals(f.sends,1);
        }
        try(var f=new Requesting()){
            f.mode("2");exec(f.writer,"CREATE TRIGGER reject_reservation BEFORE UPDATE ON invitecode BEGIN SELECT RAISE(ABORT,'PRIVATE_INVITATION_SQL'); END");f.faults.add("reservation");String original=f.snapshotAll();rejected(()->f.ask(INVITE,""),503);Assert.assertEquals(f.snapshotAll(),original);
        }
        try(var f=new Requesting()){f.send=false;String original=f.snapshotAll();rejected(()->f.ask("",""),503);Assert.assertEquals(f.snapshotAll(),original);Assert.assertEquals(f.additions,0);}
        System.out.println("PASS provider rejection creates no account/proof/lease; account/statistic/reservation/proof/strict commit faults roll back all database changes despite an already accepted physical SMS, with caches released after rollback only");
        for(String mutation:List.of("mode","lease","permission"))try(var f=new Requesting()){
            f.mode("2");if(mutation.equals("permission"))f.inviter();
            f.duringSms=()->{try{switch(mutation){case "mode"->f.mode("1");case "lease"->exec(f.writer,"UPDATE invitecode SET userId='9',useTime=?",f.clock);case "permission"->exec(f.writer,"DELETE FROM role_permission");}}catch(Exception e){throw new IllegalStateException(e);}};
            rejected(()->f.ask(mutation.equals("permission")?"":INVITE,mutation.equals("permission")?"inviter":""),mutation.equals("mode")?403:409);Assert.assertTrue(query(f.reader,"SELECT * FROM verifycode").isEmpty());Assert.assertEquals(f.additions,mutation.equals("lease")?1:0);Assert.assertFalse(f.active());
        }
        System.out.println("PASS registration closure, competing reservation and revoked invitation-link permission during remote SMS delivery are rechecked under the owner transaction and cannot leave an account/proof or overwrite another lease");
        try(var f=new Requesting()){f.allowed=false;rejected(()->f.ask("",""),429);Assert.assertEquals(f.captchas,0);Assert.assertEquals(f.sends,0);}
        try(var f=new Requesting()){f.acceptable=false;rejected(()->f.ask("",""),422);Assert.assertEquals(f.sends,0);}
        try(var f=new Requesting()){f.mode("1");rejected(()->f.ask("",""),403);Assert.assertEquals(f.sends,0);}
        try(var f=new Requesting()){f.mode("2");exec(f.writer,"UPDATE invitecode SET status=0");rejected(()->f.ask(INVITE,""),422);Assert.assertEquals(f.sends,0);}
        System.out.println("PASS existing quota rejection, name moderation, closed registration and unusable invites fail before provider delivery; the coordinator grants no Cookie login, apiKey identity or registration reward");
        try(var f=new Requesting()){
            f.mode("2");f.ask(INVITE,"");f.user("2","other","13800000002",2,0);String before=f.snapshotAll();
            rejected(()->f.request.reserveLegacy("2","13800000002",INVITE),409);Assert.assertEquals(f.snapshotAll(),before);
            f.clock+=1000;f.request.reserveLegacy("100",PHONE,INVITE);Assert.assertEquals(query(f.reader,"SELECT useTime FROM invitecode").getFirst().getLong("useTime"),f.clock);
            rejected(()->f.request.reserveLegacy("100","13800000009",INVITE),409);
            f.clock+=86_400_000L;f.request.reserveLegacy("2","13800000002",INVITE);Assert.assertEquals(query(f.reader,"SELECT userId FROM invitecode").getFirst().getString("userId"),"2");
        }
        try(var f=new Requesting()){
            f.mode("2");f.user("2","other","13800000002",2,0);String before=f.snapshotAll();f.faults.add("commit");rejected(()->f.request.reserveLegacy("2","13800000002",INVITE),503);Assert.assertEquals(f.snapshotAll(),before);
        }
        System.out.println("PASS shared legacy reservation adapter shares the actual namespace/user/invitation locks and strict transaction: other phones cannot overwrite a live v3 lease, the owner can renew, expiry releases it and failed commit preserves all rows; the old controller has a separate compatibility regression");
        final String body=new JSONObject().put("userName",NAME).put("userPhone",PHONE).put("captcha",CAPTCHA).toString();
        try(var f=new Requesting()){Assert.assertEquals(f.http(body,null,"application/json").getInt("status"),200);Assert.assertEquals(f.proof().getString("userId"),"100");}
        for(String problem:List.of("type","json","unknown","query","oversize","coercion","phone","name","captcha","language"))try(var f=new Requesting()){
            String raw=body;String type="application/json",query=null;int expected=422;
            switch(problem){
                case "type"->{type="text/plain";expected=415;}
                case "json"->{raw="{invalid";expected=400;}
                case "unknown"->raw=new JSONObject(body).put("userId","999").toString();
                case "query"->query="apiKey=ignored";
                case "oversize"->{raw=" ".repeat(8193);expected=413;}
                case "coercion"->raw=new JSONObject(body).put("userName",123).toString();
                case "phone"->raw=new JSONObject(body).put("userPhone","+8613800000001").toString();
                case "name"->raw=new JSONObject(body).put("userName","bad/name").toString();
                case "captcha"->raw=new JSONObject(body).put("captcha","{}").toString();
                case "language"->raw=new JSONObject(body).put("userLanguage","../en").toString();
            }
            String before=f.snapshotAll();Assert.assertEquals(f.http(raw,query,type).getInt("status"),expected,problem);Assert.assertEquals(f.sends,0);Assert.assertEquals(f.snapshotAll(),before);
        }
        System.out.println("PASS real HTTP handler validates JSON media/body/field types, rejects forged IDs/query credentials/invalid names/phones/captcha/language and never uses ambient Cookie/Bearer/attr identity; the production route has a separate anonymous gate regression");

        try(var f=new AccountRegistrationTest.Registration()){
            var request=activationRequest(f);Sessions.loggedIn=null;Assert.assertTrue(requestHttp(f,request).getBoolean("accepted"));
            final String activationBody=new JSONObject().put("userPhone",PHONE).put("code","314159").put("userPassword",AccountRegistrationTest.PASSWORD).put("userAppRole",1).put("mbti","infj-t").put("referral","inviter").toString();
            Assert.assertTrue(f.http(false,activationBody,null,"application/json").getJSONObject("data").getBoolean("registered"));Assert.assertNull(Sessions.loggedIn);
            String committed=f.all();Assert.assertEquals(f.http(false,activationBody,null,"application/json").getInt("status"),422);Assert.assertEquals(f.all(),committed);
            Assert.assertEquals(query(f.reader,"SELECT userStatus FROM user WHERE oId='1'").getFirst().getInt("userStatus"),0);
            Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),4);Assert.assertEquals(query(f.reader,"SELECT * FROM notification").size(),3);Assert.assertEquals(f.deliveries,1);
        }
        for(String change:List.of("name","referral","referral-owner","invitation"))try(var f=new AccountRegistrationTest.Registration()){
            var request=activationRequest(f);request.request("new-user",PHONE,CAPTCHA,INVITE,"inviter","zh_CN","192.0.2.130");
            if(change.equals("name"))exec(f.writer,"UPDATE user SET userName='renamed' WHERE oId='1'");
            if(change.equals("referral-owner")){exec(f.writer,"UPDATE user SET userName='former-inviter' WHERE oId='2'");exec(f.writer,"UPDATE user SET userName='inviter' WHERE oId='3'");}
            if(change.equals("invitation"))exec(f.writer,"UPDATE invitecode SET userId='9'");
            String original=f.all();rejected(()->f.registration.complete(PHONE,"314159",AccountRegistrationTest.PASSWORD,1,"infj-t",change.equals("referral")?"generator":"inviter"),422);
            Assert.assertEquals(f.all(),original);Assert.assertEquals(f.deliveries,0);
        }
        System.out.println("PASS actual HTTP registration request to HTTP activation with replay rejection binds the same name/referral/invitation, commits original rewards and notifications exactly once; a prior rename or altered referral/invite cannot use the new SMS to activate or reward another registration context");

        try(var f=new Requesting()){
            f.mode("2");exec(f.writer,"UPDATE invitecode SET generatorId='2'");f.ask(INVITE,"");
            exec(f.writer,"INSERT INTO invitecode VALUES('11','other','','2',1,0),('12','used','2','2',0,0),('13','system','','sys',1,0)");
            exec(f.writer,"INSERT INTO invitecode VALUES(?,'young','','2',1,0)",Long.toString(f.clock));
            exec(f.writer,"INSERT INTO invitecode VALUES('15','old-lease','9','2',1,?),('16','future-lease','9','2',1,?)",f.clock-86_400_000L,f.clock+1000);
            f.request.expireInvitations(259_200_000L);
            Assert.assertEquals(query(f.reader,"SELECT oId FROM invitecode ORDER BY oId").stream().map(row->row.getString("oId")).toList(),List.of("10","12","13","16",Long.toString(f.clock)));
            f.clock+=86_400_000L;f.request.expireInvitations(259_200_000L);Assert.assertTrue(query(f.reader,"SELECT * FROM invitecode WHERE oId='10'").isEmpty());
        }
        try(var f=new Requesting()){
            exec(f.writer,"UPDATE invitecode SET generatorId='2'");String before=f.snapshotAll();f.faults.add("commit");rejected(()->f.request.expireInvitations(259_200_000L),503);Assert.assertEquals(f.snapshotAll(),before);
        }
        System.out.println("PASS shared background expiry executes actual scoped DELETE: old unused non-system codes expire only after their live reservation ends, exact boundary releases it, used/system/young/future-reserved rows remain and strict commit failure restores all rows");

        for(String failure:List.of("captcha.throw","provider.throw"))try(var f=new Requesting()){
            final var messages=new ArrayList<String>();final var appender=new org.apache.logging.log4j.core.appender.AbstractAppender("registration-private",null,null,false,null){@Override public void append(org.apache.logging.log4j.core.LogEvent e){messages.add(e.getMessage().getFormattedMessage()+(e.getThrown()==null?"":e.getThrown().getMessage()));}};
            appender.start();final var actualLogger=(org.apache.logging.log4j.core.Logger)field(null,AccountRegistrationRequestService.class,"LOGGER");final var loggerContext=actualLogger.getContext();final var configuration=loggerContext.getConfiguration();final String loggerName=actualLogger.getName();final var old=configuration.getLoggers().get(loggerName);
            final var capture=new org.apache.logging.log4j.core.config.LoggerConfig(loggerName,org.apache.logging.log4j.Level.ALL,false);capture.addAppender(appender,org.apache.logging.log4j.Level.ALL,null);configuration.addLogger(loggerName,capture);loggerContext.updateLoggers();
            try{f.faults.add("log");f.faults.add(failure);String original=f.snapshotAll();rejected(()->f.ask("",""),503);Assert.assertEquals(f.snapshotAll(),original);
                Assert.assertFalse(messages.isEmpty());String logged=String.join("\n",messages);for(String secret:List.of("PRIVATE_",PHONE,"314159","lot_number","pass_token"))Assert.assertFalse(logged.contains(secret),secret);
            }finally{configuration.removeLogger(loggerName);if(old!=null)configuration.addLogger(loggerName,old);loggerContext.updateLoggers();appender.stop();}
        }
        System.out.println("PASS actual service failure logging masks provider/captcha exception payloads and never records phone, SMS digits or opaque captcha proof; provider failures do not start account mutations");
        try(var f=new Fixture()){
            exec(f.writer,"CREATE TABLE verifycode(oId TEXT PRIMARY KEY,code TEXT)");exec(f.writer,"INSERT INTO verifycode VALUES('1','123456')");
            String migration=java.nio.file.Files.readString(java.nio.file.Path.of("sql/20261019_account_registration.sql"));int at=migration.indexOf("'ALTER TABLE")+1;Assert.assertTrue(at>0);var ddl=new StringBuilder();
            for(int i=at;i<migration.length();i++){char c=migration.charAt(i);if(c=='\''){if(i+1<migration.length()&&migration.charAt(i+1)=='\''){ddl.append('\'');i++;continue;}break;}ddl.append(c);}
            exec(f.writer,ddl.toString().replace("`symphony_verifycode`","verifycode"));Assert.assertEquals(query(f.reader,"SELECT * FROM verifycode").getFirst().getString("accountContext"),"");Assert.assertEquals(query(f.reader,"SELECT code FROM verifycode").getFirst().getString("code"),"123456");
            final var metadata=new JSONObject(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/repository.json"))).getJSONArray("repositories");boolean found=false;
            for(Object item:metadata){var table=(JSONObject)item;if(!table.getString("name").equals("verifycode"))continue;for(Object key:table.getJSONArray("keys")){var column=(JSONObject)key;if(!column.getString("name").equals("accountContext"))continue;Assert.assertEquals(column.getString("type"),"String");Assert.assertEquals(column.getInt("length"),512);found=true;}}
            Assert.assertTrue(found);
        }
        System.out.println("PASS actual migration ALTER literal adds the metadata-compatible context column and preserves existing SMS values with an empty legacy binding on SQLite; MySQL information_schema/PREPARE/idempotent execution still requires deployment verification");

        for(String kind:List.of("missing","pending","blocked","disabled","deactivated","self","same-active-phone"))try(var f=new Requesting()){
            String ref="inviter";
            if(kind.equals("self")){f.user("1","my-name",PHONE,2,0);ref="my-name";}
            else if(!kind.equals("missing"))f.user("2","inviter",kind.equals("same-active-phone")?PHONE:"13800000002",switch(kind){case "pending"->2;case "blocked"->1;case "disabled"->3;case "deactivated"->4;default->0;},0);
            String original=f.snapshotAll();final String supplied=ref;rejected(()->f.ask("",supplied),422);Assert.assertEquals(f.sends,0);Assert.assertEquals(f.snapshotAll(),original);
        }
        try(var f=new Requesting()){f.mode("2");f.inviter();exec(f.writer,"DELETE FROM role_permission");Assert.assertTrue(f.ask(INVITE,"inviter").getBoolean("accepted"));}
        try(var f=new Requesting()){f.mode("2");f.inviter();exec(f.writer,"DELETE FROM role_permission");rejected(()->f.ask("","inviter"),422);Assert.assertEquals(f.sends,0);}
        System.out.println("PASS approved v3 referral rule rejects missing/inactive/self recipients before SMS, while an active other inviter without invitation-link permission may still receive the original reward when a valid code is supplied; link permission remains a separate requirement");
        try(var f=new Requesting()){
            final boolean[] pass={false};final int[] gates={0};
            org.b3log.latke.http.function.Handler gate=c->{gates[0]++;if(!pass[0]){c.sendRedirect("/test");c.abort();return;}c.handle();};
            org.b3log.symphony.processor.AccountRegistrationRequestRouteBindings.register(f.controller,gate);
            var registry=org.b3log.latke.http.Dispatcher.class.getDeclaredField("routerGroups");registry.setAccessible(true);int checked=0;
            for(Object group:(List<?>)registry.get(null))for(Object route:(List<?>)field(group,group.getClass(),"routers")){
                @SuppressWarnings("unchecked") var paths=(List<String>)field(route,route.getClass(),"uriTemplates");if(!paths.equals(List.of("/api/v3/auth/registration/request")))continue;checked++;
                Assert.assertEquals(((List<?>)field(route,route.getClass(),"httpRequestMethods")).getFirst(),org.b3log.latke.http.HttpMethod.POST);
                @SuppressWarnings("unchecked") var chain=new ArrayList<org.b3log.latke.http.function.Handler>((List<org.b3log.latke.http.function.Handler>)field(group,group.getClass(),"middlewares"));Assert.assertEquals(chain.size(),1);chain.add((org.b3log.latke.http.function.Handler)field(route,route.getClass(),"handler"));
                String original=f.snapshotAll();var blocked=f.http(body,null,"application/json",chain);Assert.assertEquals(blocked.getInt("status"),302);Assert.assertEquals(blocked.getString("location"),"/test");Assert.assertEquals(f.captchas,0);Assert.assertEquals(f.sends,0);Assert.assertEquals(f.snapshotAll(),original);
                pass[0]=true;Assert.assertEquals(f.http(body,null,"application/json",chain).getInt("status"),200);Assert.assertEquals(f.sends,1);
            }
            Assert.assertEquals(checked,1);Assert.assertEquals(gates[0],2);
        }
        System.out.println("PASS actual new production POST route installs anonymous middleware before the real request handler in the bundled Dispatcher: a blocked gate reaches no captcha/SMS/database mutation and a completed gate reaches the bound registration flow");
        try(var f=new Requesting()){
            f.mode("2");exec(f.writer,"UPDATE invitecode SET generatorId='2'");f.ask(INVITE,"");var cleanup=new LegacyInviteExpiry();inject(cleanup,LegacyInviteExpiry.class,"registrationRequests",f.request);cleanup.expireInvitecodes();Assert.assertEquals(query(f.reader,"SELECT * FROM invitecode").size(),1);
            f.clock+=86_400_000L;cleanup.expireInvitecodes();Assert.assertTrue(query(f.reader,"SELECT * FROM invitecode").isEmpty());
        }
        System.out.println("PASS actual legacy cleanup wrapper delegates to the strict shared expiry core and preserves an active v3 reservation until its exact one-day boundary instead of deleting it at the original code age");

        try(var f=new Requesting()){
            f.mode("2");f.ask(INVITE,"");f.user("2","legacy-other","13800000009",2,0);
            final var refused=f.legacy("legacy-other","13800000009");Assert.assertEquals(refused.keySet(),Set.of("code","msg"));Assert.assertEquals(refused.getInt("code"),-1);Assert.assertEquals(f.legacySends,0);Assert.assertEquals(query(f.reader,"SELECT userId FROM invitecode").getFirst().getString("userId"),"100");
            f.clock+=1000;final var accepted=f.legacy(NAME,PHONE);Assert.assertEquals(accepted.keySet(),Set.of("code","msg"));Assert.assertEquals(accepted.getInt("code"),0);Assert.assertEquals(accepted.getString("msg"),"verifycodeSentLabel");Assert.assertEquals(f.legacySends,1);Assert.assertEquals(f.legacyProofs,2);Assert.assertEquals(query(f.reader,"SELECT useTime FROM invitecode").getFirst().getLong("useTime"),f.clock);
            int sent=f.sends;rejected(()->f.request.request("legacy-other","13800000009",CAPTCHA,INVITE,"","zh_CN","192.0.2.136"),409);Assert.assertEquals(f.sends,sent);
        }
        System.out.println("PASS actual legacy first-stage controller invokes the same reservation guard and retains its code/msg envelope and SMS sequence: a different phone cannot overwrite v3, the owner can renew, and v3 cannot overwrite that renewal; legacy pending/proof writes remain separate as documented");
        try(var f=new AccountRegistrationTest.Registration()){
            Assert.assertTrue(f.registration.complete(PHONE,"123456",AccountRegistrationTest.PASSWORD,1,"","new-user").getBoolean("registered"));Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),4);
        }
        System.out.println("PASS the new referral-recipient rule applies only to new v3 requests: a previously issued empty-context proof retains its original referral award behavior and amounts, as explicitly approved");

    }
}
