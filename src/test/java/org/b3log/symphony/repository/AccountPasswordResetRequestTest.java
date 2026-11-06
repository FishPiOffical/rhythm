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
import org.b3log.latke.repository.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.util.*;
import com.tencentcloudapi.sms.v20210111.models.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.AccountPasswordResetTest.*;

/** Real reset issuance SQL + completion; SMS/captcha/dispatcher and SDK transport are explicit adapters. */
public class AccountPasswordResetRequestTest {
    static final String PROOF=new JSONObject().put("lot_number","lot").put("captcha_output","opaque").put("pass_token","proof").put("gen_time","1234567890").toString();
    static class Requesting extends Reset {
        boolean allowed=true,captcha=true,delivery=true;int sends,captchas,quotas,inserts;
        Runnable duringSend=()->{};String generated="654321";
        final AccountPasswordResetRequestService request=new AccountPasswordResetRequestService(){
            @Override protected long now(){return 100_000;}
            @Override protected String newCode(){return generated;}
            @Override protected boolean allowed(String ip,String name,String phone){Assert.assertFalse(active());quotas++;return allowed;}
            @Override protected boolean captchaAccepted(String value){Assert.assertFalse(active());captchas++;Assert.assertEquals(new JSONObject(value).keySet(),Set.of("lot_number","captcha_output","pass_token","gen_time"));return captcha;}
            @Override protected boolean send(String phone,String code){Assert.assertFalse(active());Assert.assertEquals(phone,PHONE);Assert.assertEquals(code,generated);sends++;duringSend.run();return delivery;}
            @Override protected void unavailable(Exception e){if(faults.isEmpty())throw new AssertionError("Unexpected reset request failure",e);}
        };
        Requesting()throws Exception{
            exec(writer,"ALTER TABLE user ADD COLUMN userName TEXT");exec(writer,"UPDATE user SET userName='member' WHERE oId='1'");
            final var owners=new UserRepository(){
                @Override public String getName(){return "user";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{if(faults.contains("request.query"))throw new RepositoryException("fixture query");return query(writer,sql,args);}
            };
            inject(request,AccountPasswordResetRequestService.class,"users",owners);
            inject(request,AccountPasswordResetRequestService.class,"codes",new VerifycodeRepository(){
                @Override public String getName(){return "verifycode";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public String addAccountPhoneCodeInCurrentTransaction(JSONObject row)throws RepositoryException{
                    Assert.assertTrue(active());Assert.assertEquals(row.keySet(),Set.of("userId","type","bizType","receiver","code","status","expired"));
                    inserts++;final String id=super.addAccountPhoneCodeInCurrentTransaction(row);
                    if(faults.contains("request.insert"))throw new RepositoryException("fixture insert acknowledgement");return id;
                }
            });
            inject(controller,ApiV3AccountProcessor.class,"resetRequests",request);
        }
        JSONObject ask()throws Exception{return request.request(PHONE,PROOF,"192.0.2.100");}
        JSONObject http(String body,String query,String type)throws Exception{
            final var netty=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/auth/password-reset/request"+(query==null?"":"?"+query),io.netty.buffer.Unpooled.copiedBuffer(body,java.nio.charset.StandardCharsets.UTF_8));
            try{
                if(type!=null)netty.headers().set("Content-Type",type);netty.headers().set("Cookie","ignored");
                final var req=new Request(null,netty){@Override public String getRemoteAddr(){return "192.0.2.100";}};req.setBytes(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                final var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));controller.requestPasswordReset(c);
                var out=new JSONObject().put("status",c.attr("test.status"));if(c.attr("test.data")!=null)out.put("data",c.attr("test.data"));return out;
            }finally{netty.release();}
        }
    }
    interface Work{void run()throws Exception;}
    static void rejected(Work work,int status)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    static SendSmsResponse response(String phone,String code){final var status=new SendStatus();status.setPhoneNumber(phone);status.setCode(code);final var response=new SendSmsResponse();response.setSendStatusSet(new SendStatus[]{status});return response;}
    public static void main(String[] args)throws Exception{
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("runtimeMode","PRODUCTION");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);
        try(var f=new Requesting()){
            String before=query(f.reader,"SELECT * FROM user").toString();Assert.assertEquals(f.ask().keySet(),Set.of("accepted"));Assert.assertEquals(f.sends,1);Assert.assertEquals(f.captchas,1);
            final var proof=query(f.reader,"SELECT * FROM verifycode WHERE code='654321'").getFirst();Assert.assertEquals(proof.getString("userId"),"1");Assert.assertEquals(proof.getString("receiver"),PHONE);Assert.assertEquals(proof.getInt("type"),1);Assert.assertEquals(proof.getInt("bizType"),1);Assert.assertEquals(proof.getInt("status"),1);Assert.assertEquals(proof.getLong("expired"),86_500_000L);
            Assert.assertEquals(query(f.reader,"SELECT * FROM user").toString(),before);Assert.assertEquals(query(f.reader,"SELECT * FROM verifycode").size(),2);
            Assert.assertTrue(f.reset.complete(PHONE,"654321",PASSWORD).getBoolean("changed"));Assert.assertTrue(query(f.reader,"SELECT * FROM verifycode WHERE code='654321'").isEmpty());rejected(()->f.reset.complete(PHONE,"654321",PASSWORD),422);
        }
        System.out.println("PASS actual reset request persists only a confirmed delivery's current phone/user/reset proof under strict commit, preserves older codes, returns a whitelist receipt and completes through the real password-reset/revocation core exactly once");
        for(String mutation:List.of("DELETE FROM user WHERE oId='1'","UPDATE user SET userStatus=2 WHERE oId='1'","UPDATE user SET userStatus=1 WHERE oId='1'","UPDATE user SET userPhone='13800000009' WHERE oId='1'","UPDATE user SET userPhone='13800000001' WHERE oId='2'"))try(var f=new Requesting()){
            exec(f.writer,mutation);String before=f.resetSnapshot();Assert.assertTrue(f.ask().getBoolean("accepted"));Assert.assertEquals(f.quotas,1);Assert.assertEquals(f.captchas,1);Assert.assertEquals(f.sends,0);Assert.assertEquals(f.resetSnapshot(),before);
        }
        try(var f=new Requesting()){f.allowed=false;String before=f.resetSnapshot();rejected(f::ask,429);Assert.assertEquals(f.captchas,0);Assert.assertEquals(f.sends,0);Assert.assertEquals(f.resetSnapshot(),before);}
        try(var f=new Requesting()){f.captcha=false;String before=f.resetSnapshot();rejected(f::ask,422);Assert.assertEquals(f.sends,0);Assert.assertEquals(f.resetSnapshot(),before);}
        System.out.println("PASS absent/inactive/ambiguous phones receive the same public acceptance after quota/captcha without SMS or mutation; quota failure never calls captcha/provider and a failed captcha never sends SMS");
        try(var f=new Requesting()){f.delivery=false;String before=f.resetSnapshot();Assert.assertTrue(f.ask().getBoolean("accepted"));Assert.assertEquals(f.resetSnapshot(),before);Assert.assertEquals(f.inserts,0);}
        for(String fault:List.of("request.query","request.insert","commit"))try(var f=new Requesting()){
            String before=f.resetSnapshot();f.faults.add(fault);if(fault.equals("request.query"))rejected(f::ask,503);else Assert.assertTrue(f.ask().getBoolean("accepted"));Assert.assertEquals(f.resetSnapshot(),before);Assert.assertFalse(f.active());
        }
        for(String mutation:List.of("UPDATE user SET userPhone='13800000009' WHERE oId='1'","UPDATE user SET userStatus=1 WHERE oId='1'","DELETE FROM user WHERE oId='1'"))try(var f=new Requesting()){
            f.duringSend=()->{try{exec(f.writer,mutation);}catch(Exception e){throw new IllegalStateException(e);}};Assert.assertTrue(f.ask().getBoolean("accepted"));Assert.assertEquals(f.sends,1);Assert.assertEquals(f.inserts,0);Assert.assertFalse(f.active());
        }
        System.out.println("PASS rejected delivery and insert/strict commit faults preserve the generic acceptance without a new valid proof; query unavailability returns generic 503; a phone/status/deletion change during remote delivery is rechecked under lock and cannot bind SMS to the changed account");
        for(String proof:List.of("","{}","not json","{\"lot_number\":\"a\",\"lot_number\":\"b\",\"captcha_output\":\"x\",\"pass_token\":\"x\",\"gen_time\":\"x\"}",new JSONObject(PROOF).put("url","https://attacker.invalid").toString(),new JSONObject(PROOF).put("gen_time",123).toString(),new JSONObject(PROOF).put("pass_token","x".repeat(4096)).toString(),new JSONObject(PROOF).put("lot_number","a\nb").toString())){
            try{AccountCaptchaProof.validate(proof);Assert.fail(proof);}catch(ApiV3Exception expected){Assert.assertEquals(expected.status,422);}
        }
        try(var f=new Requesting()){
            for(String phone:List.of("","123","+8613800000001","１3800000001"))rejected(()->f.request.request(phone,PROOF,"ip"),422);
            f.begin();try{f.ask();Assert.fail();}catch(IllegalStateException expected){}f.rollback();Assert.assertEquals(f.sends,0);
        }
        System.out.println("PASS bounded typed four-field captcha proof rejects malformed/unknown/duplicate-style input, controls and oversized values before provider traffic; malformed phone and nested owner transaction are rejected");
        final var body=new JSONObject().put("userPhone",PHONE).put("captcha",PROOF);
        try(var f=new Requesting()){Assert.assertTrue(f.http(body.toString(),null,"application/json").getJSONObject("data").getBoolean("accepted"));}
        for(var invalid:List.of(new JSONObject(body.toString()).put("userId","2"),new JSONObject(body.toString()).put("apiKey","key"),new JSONObject(body.toString()).put("captcha",new JSONObject(PROOF)),new JSONObject(body.toString()).put("userPhone",123)))try(var f=new Requesting()){
            Assert.assertEquals(f.http(invalid.toString(),null,"application/json").getInt("status"),422);Assert.assertEquals(f.sends,0);
        }
        try(var f=new Requesting()){
            Assert.assertEquals(f.http(body.toString(),"captcha=proof","application/json").getInt("status"),422);Assert.assertEquals(f.http(body.toString(),null,"text/plain").getInt("status"),415);Assert.assertEquals(f.http(" ".repeat(8193),null,"application/json").getInt("status"),413);Assert.assertEquals(f.sends,0);
        }
        System.out.println("PASS actual v3 handler accepts only bounded typed JSON, rejects query credentials/unknown fields/media/coercions, and ignores ambient Cookie/context identity before reaching the current phone proof service");
        LoginProcessor.verifySMSCodeLimiterOfIPLong=new pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter(86400,10);LoginProcessor.verifySMSCodeLimiterOfIP=new pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter(600,5);LoginProcessor.verifySMSCodeLimiterOfName=new pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter(600,5);LoginProcessor.verifySMSCodeLimiterOfPhone=new pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter(600,5);
        final var admitted=new java.util.concurrent.atomic.AtomicInteger();try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            final var all=new ArrayList<java.util.concurrent.Future<?>>();for(int i=0;i<64;i++)all.add(executor.submit(()->{if(AccountSmsQuota.access("192.0.2.101","member",PHONE))admitted.incrementAndGet();}));for(var future:all)future.get();
        }
        Assert.assertEquals(admitted.get(),5);Assert.assertFalse(LoginProcessor.verifySMSCodeLimiterOfIP.access("192.0.2.101"));
        System.out.println("PASS all account SMS entry points share the original limiter objects, budgets and short-circuit ordering; concurrent attempts cannot obtain a separate v3 allowance");
        final class Sender extends VerifycodeMgmtService {
            SendSmsResponse result;String target,value;int calls;
            @Override protected SendSmsResponse requestTencentSms(String[] phone,String[] code){calls++;Assert.assertEquals(phone.length,1);Assert.assertEquals(code.length,1);target=phone[0];value=code[0];return result;}
        }
        var sender=new Sender();Symphonys.SMS_ENABLE="tencent";
        sender.result=response("+86"+PHONE,"Ok");Assert.assertTrue(sender.sendAccountVerifyCodeSMS(PHONE,"123456"));Assert.assertEquals(sender.target,"+86"+PHONE);Assert.assertEquals(sender.value,"123456");
        for(String code:List.of("","ok","FailedOperation.TemplateIncorrectOrUnapproved")){sender.result=response("+86"+PHONE,code);Assert.assertFalse(sender.sendAccountVerifyCodeSMS(PHONE,"123456"));}
        sender.result=response("+8613800000009","Ok");Assert.assertFalse(sender.sendAccountVerifyCodeSMS(PHONE,"123456"));
        sender.result=new SendSmsResponse();Assert.assertFalse(sender.sendAccountVerifyCodeSMS(PHONE,"123456"));sender.result.setSendStatusSet(new SendStatus[0]);Assert.assertFalse(sender.sendAccountVerifyCodeSMS(PHONE,"123456"));sender.result.setSendStatusSet(new SendStatus[]{null});Assert.assertFalse(sender.sendAccountVerifyCodeSMS(PHONE,"123456"));
        sender.result=null;Assert.assertFalse(sender.sendAccountVerifyCodeSMS(PHONE,"123456"));int calls=sender.calls;Assert.assertFalse(sender.sendAccountVerifyCodeSMS("bad","123456"));Assert.assertEquals(sender.calls,calls);
        sender.result=response("+86"+PHONE,"Failure");Assert.assertTrue(sender.sendMsgTencentCloud(new String[]{PHONE},new String[]{"123456"}));
        System.out.println("PASS actual confirmed SMS method requires exactly one matching E.164 recipient and exact provider success status, rejects empty/wrong/failed results and malformed proof; legacy provider return contract remains unchanged; remote SDK is not executed");
        for(String invalid:Arrays.asList(null,"","id with whitespace","<script>","x".repeat(129))){
            final var options=new AccountCaptchaService(){@Override protected String configuredId(){return invalid;}};
            rejected(options::options,503);
        }
        final var options=new AccountCaptchaService(){@Override protected String configuredId(){return "public-widget-id";}};
        final var publicConfig=options.options();Assert.assertEquals(publicConfig.keySet(),Set.of("provider","captchaId"));Assert.assertEquals(publicConfig.getString("provider"),"geetest4");
        try(var f=new Requesting()){
            inject(f.controller,ApiV3AccountProcessor.class,"captchas",options);
            final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/auth/captcha-options?apiKey=ignored");
            try{final var c=new RequestContext(new Request(null,raw),new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));f.controller.captchaOptions(c);Assert.assertEquals(((JSONObject)c.attr("test.data")).toMap(),publicConfig.toMap());}finally{raw.release();}
        }
        System.out.println("PASS captcha options service/controller expose only the existing public widget identifier and provider, reject unavailable/malformed configuration and never expose verification keys or grant identity through optional apiKey");

    }
}
