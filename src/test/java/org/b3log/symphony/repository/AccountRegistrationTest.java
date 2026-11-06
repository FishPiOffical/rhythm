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
import org.b3log.latke.http.*;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.symphony.processor.ApiV3AccountProcessor;
import org.b3log.symphony.processor.LoginProcessor;
import org.b3log.symphony.util.Sessions;
import io.netty.handler.codec.http.*;
import org.b3log.latke.repository.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.model.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Actual activation/verification SQL and account notifications, SQLite plus explicit point/cache adapters. */
public class AccountRegistrationTest {
    static final String PASSWORD="e10adc3949ba59abbe56e057f20f883e";
    static class Registration extends AccountVerificationTest.Verification {
        boolean allowed=true;int deliveries,transfers,notes;
        final ApiV3AccountProcessor controller=new ApiV3AccountProcessor(){@Override protected boolean registrationAllowed(String ip){return allowed;}};
        final LoginProcessor legacy=new LoginProcessor();final List<String> order=new ArrayList<>();Runnable beforeTransaction=()->{};
        final AccountRegistrationService registration=new AccountRegistrationService(){
            @Override protected long now(){return clock;}
            @Override protected void unavailable(Exception e){if(faults.isEmpty())throw new AssertionError("Unexpected registration failure",e);}
            @Override protected void afterCommit(Result r){Assert.assertFalse(active());deliveries++;Assert.assertEquals(r.userId(),"1");if(faults.contains("delivery"))throw new IllegalStateException("fixture");}
        };
        Registration()throws Exception {
            exec(writer,"ALTER TABLE user ADD COLUMN userPassword TEXT");exec(writer,"ALTER TABLE user ADD COLUMN userEmail TEXT");
            exec(writer,"ALTER TABLE user ADD COLUMN userPoint INTEGER DEFAULT 0");exec(writer,"ALTER TABLE user ADD COLUMN userAppRole INTEGER");exec(writer,"ALTER TABLE user ADD COLUMN mbti TEXT");
            exec(writer,"UPDATE user SET userName='new-user',userPassword='before',userEmail='pending@example.test',userPoint=17 WHERE oId='1'");
            exec(writer,"UPDATE user SET userName='inviter',userPassword='other' WHERE oId='2'");
            exec(writer,"INSERT INTO user(oId,userName,userPhone,userStatus,userPoint,userPassword) VALUES('3','generator','13800000003',0,19,'third')");
            exec(writer,"CREATE TABLE option(oId TEXT PRIMARY KEY,optionValue TEXT)");exec(writer,"INSERT INTO option VALUES(?, '0')",Option.ID_C_MISC_ALLOW_REGISTER);exec(writer,"INSERT INTO option VALUES(?, '3')",Option.ID_C_STATISTIC_MEMBER_COUNT);
            exec(writer,"CREATE TABLE invitecode(oId TEXT PRIMARY KEY,userId TEXT,generatorId TEXT,status INTEGER,useTime BIGINT)");exec(writer,"INSERT INTO invitecode VALUES('10','1','3',1,0)");
            exec(writer,"CREATE TABLE transfers(oId TEXT PRIMARY KEY,recipient TEXT,type INTEGER,sum INTEGER,dataId TEXT)");
            exec(writer,"CREATE TABLE notification(oId TEXT PRIMARY KEY,userId TEXT,dataId TEXT,dataType INTEGER,hasRead BOOLEAN)");
            final var owners=new UserRepository(){
                @Override public String getName(){return "user";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public Transaction beginTransaction(){try{beforeTransaction.run();begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException {if(active())order.add("users");return query(writer,sql,args);}
                @Override public void updateColumnsInCurrentTransaction(String id,JSONObject changes)throws RepositoryException{
                    Assert.assertTrue(active());Assert.assertTrue(ArticlePublicationCaches.active());final var keys=new ArrayList<>(changes.keySet());Collections.sort(keys);
                    final var args=new ArrayList<Object>();for(String key:keys)args.add(changes.get(key));args.add(id);
                    exec(writer,"UPDATE user SET "+String.join(",",keys.stream().map(key->key+"=?").toList())+" WHERE oId=?",args.toArray());fault("user."+id);
                }
                @Override public void evictUserNameCaches(String id,String old){Assert.assertFalse(active());order.add("cache");}
            };
            final var opts=new OptionRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public JSONObject getForUpdate(String id)throws RepositoryException {Assert.assertTrue(active());order.add(id.equals(Option.ID_C_STATISTIC_MEMBER_COUNT)?"namespace":"setting");final var rows=query(writer,"SELECT * FROM option WHERE oId=?",id);return rows.isEmpty()?null:rows.getFirst();}
            };
            final var names=new UserNameMgmtService();inject(names,UserNameMgmtService.class,"users",owners);inject(names,UserNameMgmtService.class,"options",opts);
            inject(verification,AccountVerificationService.class,"users",owners);
            final var inviteRepo=new InvitecodeRepository(){
                @Override public String getName(){return "invitecode";}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException {if(active())order.add("invite");return query(writer,sql,args);}
            };
            final var pointService=new PointtransferMgmtService(){
                @Override public <T>T executeWithTransferLocks(String actor,List<String> recipients,java.util.concurrent.Callable<T> operation)throws Exception{
                    Assert.assertFalse(active());Assert.assertTrue(recipients.contains("1"));Assert.assertTrue(Set.of("1","2","3").containsAll(recipients));return super.executeWithTransferLocks(actor,recipients,operation);
                }
                @Override public String transferInCurrentTransaction(String from,String to,int type,int sum,String data,long time,String memo,PointtransferSource source,boolean index){
                    Assert.assertTrue(active());Assert.assertTrue(AccountPairLocks.lock(from,to).isHeldByCurrentThread());Assert.assertFalse(index);Assert.assertTrue(ArticlePublicationCaches.active());
                    final String id=Integer.toString(++transfers);try{exec(writer,"UPDATE user SET userPoint=userPoint+? WHERE oId=?",sum,to);exec(writer,"INSERT INTO transfers VALUES(?,?,?,?,?)",id,to,type,sum,data);fault("transfer."+id);return id;}catch(RepositoryException e){return null;}
                }
            };
            final var notificationService=new NotificationMgmtService();inject(notificationService,NotificationMgmtService.class,"notificationRepository",new NotificationRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public String add(JSONObject row)throws RepositoryException {Assert.assertTrue(active());String id=Integer.toString(++notes);exec(writer,"INSERT INTO notification VALUES(?,?,?,?,?)",id,row.getString("userId"),row.getString("dataId"),row.getInt("dataType"),row.getBoolean("hasRead"));fault("notification."+id);return id;}
            });
            for(var entry:Map.<String,Object>of("users",owners,"options",opts,"names",names,"invitations",inviteRepo,"verification",verification,"points",pointService,"notifications",notificationService).entrySet())inject(registration,AccountRegistrationService.class,entry.getKey(),entry.getValue());
            inject(controller,ApiV3AccountProcessor.class,"registrations",registration);inject(legacy,LoginProcessor.class,"registrations",registration);
            valid("100");
        }
        void fault(String key)throws RepositoryException{if(faults.contains(key))throw new RepositoryException("fixture registration write failure");}
        JSONObject http(boolean old,String body,String query,String type)throws Exception{
            final var netty=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,(old?"/register2":"/api/v3/auth/registration/complete")+(query==null?"":"?"+query),io.netty.buffer.Unpooled.copiedBuffer(body,java.nio.charset.StandardCharsets.UTF_8));
            try{
                if(type!=null)netty.headers().set("Content-Type",type);netty.headers().set("Cookie","ignored");
                final var req=new Request(null,netty){@Override public String getRemoteAddr(){return "192.0.2.90";}};req.setBytes(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(old){var parser=Request.class.getDeclaredMethod("parseForm");parser.setAccessible(true);parser.invoke(req);}
                final var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);final var c=new RequestContext(req,new Response(null,response));c.attr("user",new JSONObject().put("oId","2"));
                if(old)legacy.register2(c);else controller.completeRegistration(c);
                final var out=new JSONObject().put("status",c.attr("test.status")==null?response.status().code():c.attr("test.status"));
                if(c.attr("test.data")!=null)out.put("data",c.attr("test.data"));
                if(c.getRenderer() instanceof JsonRenderer renderer)out.put("legacy",renderer.getJSONObject());return out;
            }finally{netty.release();}
        }
        JSONObject complete()throws Exception{return registration.complete("13800000001","123456",PASSWORD,1,"infj-t","inviter");}
        String all()throws Exception{String s="";for(String table:List.of("user","verifycode","invitecode","transfers","notification"))s+=query(reader,"SELECT * FROM "+table+" ORDER BY oId");return s;}
    }
    interface Work{void run()throws Exception;}
    static void reject(Work work,int status)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    public static void main(String[] args)throws Exception{
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Registration()){
            exec(f.writer,"INSERT INTO user(oId,userName,userPhone,userStatus,userPoint,userEmail) VALUES('4','new-user','13800000004',2,23,'duplicate@example.test')");
            final var result=f.complete();Assert.assertEquals(result.keySet(),Set.of("userId","registered"));Assert.assertEquals(f.deliveries,1);
            final var owner=query(f.reader,"SELECT * FROM user WHERE oId='1'").getFirst();Assert.assertEquals(owner.getInt("userStatus"),0);Assert.assertEquals(owner.getString("userPassword"),PASSWORD);Assert.assertEquals(owner.getInt("userAppRole"),1);Assert.assertEquals(owner.getString("mbti"),"INFJ-T");
            Assert.assertEquals(owner.getInt("userPoint"),17+Pointtransfer.TRANSFER_SUM_C_INIT+Pointtransfer.TRANSFER_SUM_C_INVITE_REGISTER);
            Assert.assertEquals(query(f.reader,"SELECT * FROM user WHERE oId='3'").getFirst().getInt("userPoint"),19+Pointtransfer.TRANSFER_SUM_C_INVITECODE_USED);
            Assert.assertEquals(query(f.reader,"SELECT * FROM user WHERE oId='4'").getFirst().getString("userName"),UserExt.NULL_USER_NAME);Assert.assertEquals(query(f.reader,"SELECT * FROM user WHERE oId='4'").getFirst().getInt("userPoint"),23);
            Assert.assertTrue(query(f.reader,"SELECT * FROM verifycode").isEmpty());Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),4);Assert.assertEquals(query(f.reader,"SELECT * FROM notification").size(),3);Assert.assertEquals(query(f.reader,"SELECT status FROM invitecode").getFirst().getInt("status"),0);
            Assert.assertEquals(f.order.subList(0,3),List.of("namespace","setting","users"));String after=f.all();reject(f::complete,422);Assert.assertEquals(f.all(),after);Assert.assertEquals(f.deliveries,1);
        }
        try(var f=new Registration()){
            exec(f.writer,"DELETE FROM invitecode");
            Assert.assertTrue(f.registration.complete("13800000001","123456",PASSWORD,0,"","missing-user").getBoolean("registered"));
            Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),1);Assert.assertEquals(query(f.reader,"SELECT * FROM notification").size(),1);
        }
        try(var f=new Registration()){
            exec(f.writer,"UPDATE option SET optionValue='2' WHERE oId=?",Option.ID_C_MISC_ALLOW_REGISTER);
            exec(f.writer,"UPDATE invitecode SET generatorId='sys'");
            Assert.assertTrue(f.complete().getBoolean("registered"));Assert.assertEquals(query(f.reader,"SELECT * FROM transfers").size(),3);Assert.assertEquals(query(f.reader,"SELECT * FROM notification").size(),2);
        }
        System.out.println("PASS real registration atomically activates the pending phone owner, consumes proof, preserves existing balances, grants original initial/referral/invite rewards and notifications, consumes invitation and defeats only pending duplicate names; replay never rewards again");
        for(String mutation:List.of("UPDATE user SET userStatus=0 WHERE oId='1'","UPDATE user SET userPhone='13800000009' WHERE oId='1'","UPDATE verifycode SET bizType=1","UPDATE verifycode SET expired=100000","UPDATE verifycode SET code='654321'","DELETE FROM verifycode"))try(var f=new Registration()){
            exec(f.writer,mutation);String before=f.all();reject(f::complete,422);Assert.assertEquals(f.all(),before);Assert.assertEquals(f.deliveries,0);
        }
        try(var f=new Registration()){exec(f.writer,"UPDATE option SET optionValue='1' WHERE oId=?",Option.ID_C_MISC_ALLOW_REGISTER);String before=f.all();reject(f::complete,403);Assert.assertEquals(f.all(),before);}
        try(var f=new Registration()){exec(f.writer,"INSERT INTO user(oId,userName,userStatus) VALUES('4','new-user',0)");String before=f.all();reject(f::complete,409);Assert.assertEquals(f.all(),before);}
        System.out.println("PASS current account/phone, SMS purpose/expiry/value and registration switch are checked before activation; closed registration and active name collisions preserve proof and all rewards");
        for(String fault:List.of("user.1","transfer.1","transfer.2","transfer.3","transfer.4","notification.1","notification.2","notification.3","commit","delete.1"))try(var f=new Registration()){
            String before=f.all();f.faults.add(fault);reject(f::complete,503);Assert.assertEquals(f.all(),before,fault);Assert.assertEquals(f.deliveries,0);Assert.assertFalse(f.active());Assert.assertFalse(ArticlePublicationCaches.active());f.faults.clear();f.complete();Assert.assertEquals(f.deliveries,1);
        }
        try(var f=new Registration()){exec(f.writer,"INSERT INTO user(oId,userName,userStatus) VALUES('4','new-user',2)");String before=f.all();f.faults.add("user.4");reject(f::complete,503);Assert.assertEquals(f.all(),before);}
        try(var f=new Registration()){
            exec(f.writer,"CREATE TRIGGER reject_invitation BEFORE UPDATE ON invitecode BEGIN SELECT RAISE(ABORT,'fixture invitation update failure'); END");
            String before=f.all();f.faults.add("invite.update");reject(f::complete,503);Assert.assertEquals(f.all(),before);Assert.assertEquals(f.deliveries,0);
        }
        System.out.println("PASS activation/duplicate cleanup/SMS deletion/every reward/every notification/strict commit faults restore all tables and proof; a fresh retry succeeds and no precommit effects run");
        for(String mutation:List.of("UPDATE user SET userName='renamed' WHERE oId='1'","UPDATE user SET userName='changed-referral' WHERE oId='2'","UPDATE invitecode SET generatorId='2'","UPDATE invitecode SET status=0","DELETE FROM invitecode"))try(var f=new Registration()){
            f.beforeTransaction=()->{try{exec(f.writer,mutation);}catch(Exception e){throw new IllegalStateException(e);}};
            reject(f::complete,409);Assert.assertEquals(f.deliveries,0);Assert.assertFalse(query(f.reader,"SELECT * FROM verifycode").isEmpty());Assert.assertTrue(query(f.reader,"SELECT * FROM transfers").isEmpty());
        }
        try(var f=new Registration()){
            f.beforeTransaction=()->{try{exec(f.writer,"UPDATE user SET userName='missing-user' WHERE oId='2'");}catch(Exception e){throw new IllegalStateException(e);}};
            reject(()->f.registration.complete("13800000001","123456",PASSWORD,0,"","missing-user"),409);
            Assert.assertFalse(query(f.reader,"SELECT * FROM verifycode").isEmpty());Assert.assertTrue(query(f.reader,"SELECT * FROM transfers").isEmpty());
        }
        try(var f=new Registration()){f.faults.add("delivery");Assert.assertTrue(f.complete().getBoolean("registered"));Assert.assertEquals(f.deliveries,1);Assert.assertTrue(query(f.reader,"SELECT * FROM verifycode").isEmpty());}
        System.out.println("PASS complete account-pair set is fixed before the root and rechecked against locked username/referral/invitation bindings; concurrent binding changes reject without proof loss and delivery failure does not undo committed activation");
        try(var f=new Registration()){
            for(String password:List.of("","abc",PASSWORD.toUpperCase()))reject(()->f.registration.complete("13800000001","123456",password,0,"","inviter"),422);
            reject(()->f.registration.completeForUserId("1","000000",PASSWORD,0,"","inviter"),422);
            f.begin();try{f.registration.completeForUserId("1","123456",PASSWORD,0,"","inviter");Assert.fail();}catch(IllegalStateException expected){}f.rollback();
            Assert.assertEquals(f.registration.completeForUserId("1","123456","legacy",0,"invalid-mbti","inviter"),"1");
            Assert.assertEquals(query(f.reader,"SELECT userPassword,mbti FROM user WHERE oId='1'").getFirst().getString("userPassword"),"legacy");
        }
        System.out.println("PASS v3 password/role/proof formats, explicit legacy user binding and root ownership are enforced; legacy normal password length and existing MBTI normalization remain compatible");
        final var body=new JSONObject().put("userPhone","13800000001").put("code","123456").put("userPassword",PASSWORD).put("userAppRole",1).put("mbti","infj-t").put("referral","inviter");
        try(var f=new Registration()){
            Sessions.loggedIn=null;Assert.assertTrue(f.http(false,body.toString(),null,"application/json").getJSONObject("data").getBoolean("registered"));Assert.assertNull(Sessions.loggedIn);
        }
        for(var mutation:List.of(new JSONObject(body.toString()).put("userId","2"),new JSONObject(body.toString()).put("apiKey","old"),new JSONObject(body.toString()).put("userAppRole","1"),new JSONObject(body.toString()).put("userPhone",123),new JSONObject(body.toString()).put("referral","bad/name")))try(var f=new Registration()){
            String before=f.all();Assert.assertEquals(f.http(false,mutation.toString(),null,"application/json").getInt("status"),422);Assert.assertEquals(f.all(),before);
        }
        try(var f=new Registration()){
            String before=f.all();Assert.assertEquals(f.http(false,body.toString(),"r=inviter","application/json").getInt("status"),422);
            Assert.assertEquals(f.http(false,body.toString(),null,"text/plain").getInt("status"),415);
            Assert.assertEquals(f.http(false," ".repeat(4097),null,"application/json").getInt("status"),413);
            f.allowed=false;Assert.assertEquals(f.http(false,body.toString(),null,"application/json").getInt("status"),429);Assert.assertEquals(f.all(),before);
        }
        try(var f=new Registration()){
            Sessions.loggedIn=null;var old=new JSONObject(body.toString()).put("userId","1");old.remove("code");String before=f.all();
            Assert.assertEquals(f.http(true,old.toString(),"r=inviter","application/json").getJSONObject("legacy").getInt("code"),-1);Assert.assertEquals(f.all(),before);Assert.assertNull(Sessions.loggedIn);
            old.put("code","123456");Assert.assertEquals(f.http(true,old.toString(),"r=inviter","application/json").getJSONObject("legacy").getInt("code"),0);Assert.assertEquals(Sessions.loggedIn,"1");
        }
        System.out.println("PASS actual v3 typed/bounded JSON handler and extracted old activation require phone/user-bound proof; unknown/query/API-key/role coercion and budget fail safely, old code is mandatory and only old success logs in after commit");
        LoginProcessor.verifyCodeLimiter=new pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter(60,4);
        final var accepted=new java.util.concurrent.atomic.AtomicInteger();try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            final var futures=new ArrayList<java.util.concurrent.Future<?>>();for(int i=0;i<64;i++)futures.add(executor.submit(()->{if(RegistrationVerificationQuota.access("192.0.2.91"))accepted.incrementAndGet();}));
            for(var future:futures)future.get();
        }
        Assert.assertEquals(accepted.get(),4);Assert.assertFalse(LoginProcessor.verifyCodeLimiter.access("192.0.2.91"));
        System.out.println("PASS old verification and activation plus v3 share the exact legacy limiter object and its unchanged budget is atomic under concurrent attempts");
        try(var f=new Registration()){
            final var calls=new ArrayList<String>();final var effects=new AccountRegistrationEffectsService(){@Override protected void startOnlineRewards(String name){Assert.assertFalse(f.active());calls.add("sky:"+name);}};
            inject(effects,AccountRegistrationEffectsService.class,"users",new UserRepository(){@Override public boolean hasTransactionBegun(){return f.active();}@Override public String getName(){return "user";}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(f.writer,sql,args);}});
            inject(effects,AccountRegistrationEffectsService.class,"points",new PointtransferMgmtService(){@Override public void indexTransferAsync(String from,String to,String id){Assert.assertFalse(f.active());calls.add("index:"+id);}});
            inject(effects,AccountRegistrationEffectsService.class,"notifications",new NotificationMgmtService(){@Override public void publishRefreshNotification(String id){Assert.assertFalse(f.active());calls.add("notify:"+id);}});
            inject(effects,AccountRegistrationEffectsService.class,"avatars",new AvatarQueryService());
            exec(f.writer,"ALTER TABLE user ADD COLUMN userAvatarURL TEXT");exec(f.writer,"UPDATE user SET userStatus=0,userAvatarURL='https://example.test/avatar' WHERE oId='1'");
            effects.afterCommit(new AccountRegistrationService.Result("1","new-user",List.of(),List.of(new AccountRegistrationService.Transfer("1","receipt")),List.of("1","2")));
            Assert.assertEquals(calls,List.of("index:receipt","notify:1","notify:2","sky:new-user"));Assert.assertEquals(UserQueryService.USER_NAMES.size(),1);Assert.assertEquals(UserQueryService.USER_NAMES.getFirst().keySet(),Set.of("userName","userNameLowerCase","userAvatarURL"));
        }
        System.out.println("PASS actual committed-effects coordinator indexes receipts, refreshes notifications, publishes only public username/avatar projection and starts online-reward work after the owner transaction; background liveness/SMS delivery remain unexecuted");

    }
}
