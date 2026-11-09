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

import org.b3log.latke.http.*;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.processor.ApiV3AccountProcessor;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpMethod;
import java.lang.reflect.Field;
import java.util.*;

/** 验证实际的注册策略、控制器、URI 解码和响应封装。存储和角色查询由显式测试夹具提供。 */
public class ApiV3RegistrationPolicyTest {
    static class Fixture {
        String mode="0";boolean missing,storageFailure,exists=true,allow=true;int userReads,permissionReads;
        JSONObject privateUser=new JSONObject().put("oId","100").put("userName","Referrer").put("userPhone","PRIVATE_PHONE")
                .put("userPassword","PRIVATE_PASSWORD").put("apiKey","PRIVATE_KEY");
        final ApiV3RegistrationPolicyService policy=new ApiV3RegistrationPolicyService();
        final ApiV3AccountProcessor controller=new ApiV3AccountProcessor();
        Fixture()throws Exception{
            inject(policy,"options",new OptionRepository(){@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{
                Assert.assertTrue(sql.startsWith("SELECT optionValue FROM "));Assert.assertEquals(args,new Object[]{Option.ID_C_MISC_ALLOW_REGISTER});if(storageFailure)throw new RepositoryException("fixture unavailable");return missing?List.of():List.of(new JSONObject().put(Option.OPTION_VALUE,mode));
            }});
            inject(policy,"users",new UserQueryService(){@Override public JSONObject getUserByName(String name){Assert.assertEquals(name,"Referrer");userReads++;return exists?privateUser:null;}});
            inject(policy,"roles",new RoleQueryService(){@Override public Map<String,JSONObject> getUserPermissionsGrantMap(String id){Assert.assertEquals(id,"100");permissionReads++;return allow?Map.of(Permission.PERMISSION_ID_C_COMMON_USE_INVITATION_LINK,new JSONObject().put(Permission.PERMISSION_T_GRANT,true).put("internal","PRIVATE_PERMISSION")):Map.of();}});
            inject(controller,"registration",policy);
        }
        JSONObject call(String query)throws Exception{
            final var request=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.GET,"/api/v3/auth/registration-options"+(query==null?"":"?"+query));
            final var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
            try{
                request.headers().set("Cookie","ignored-fixture");var parsed=new Request(null,request);
                var parse=Request.class.getDeclaredMethod("parseQueryStr");parse.setAccessible(true);parse.invoke(parsed);
                var c=new RequestContext(parsed,new Response(null,response));c.attr("user",privateUser);
                controller.registrationOptions(c);Assert.assertEquals(response.headers().get("Cache-Control"),"no-store");Assert.assertNotNull(response.headers().get("X-Request-Id"));
                return new JSONObject().put("status",response.status().code()).put("json",((JsonRenderer)c.getRenderer()).getJSONObject());
            }finally{request.release();}
        }
    }
    static void inject(Object target,String field,Object value)throws Exception{Field f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}
    public static void main(String[] args)throws Exception{
        var properties=new Properties();properties.setProperty("runtimeDatabase","NONE");
        org.b3log.latke.Latkes.setLatkeProps(properties);org.b3log.latke.Latkes.setLocalProps(properties);
        var f=new Fixture();var open=f.policy.options(null);Assert.assertEquals(open.getString("mode"),"open");Assert.assertTrue(open.getBoolean("enabled"));Assert.assertFalse(open.getBoolean("inviteCodeRequired"));Assert.assertFalse(open.getBoolean("referralAccepted"));
        f.mode="2";var invitation=f.policy.options("");Assert.assertEquals(invitation.getString("mode"),"invitation");Assert.assertTrue(invitation.getBoolean("inviteCodeRequired"));
        f.mode="1";var closed=f.policy.options("Referrer");Assert.assertFalse(closed.getBoolean("enabled"));Assert.assertFalse(closed.getBoolean("referralAccepted"));Assert.assertFalse(closed.getBoolean("inviteCodeRequired"));Assert.assertEquals(f.userReads,0);
        Assert.assertEquals(open.getJSONObject("username").getInt("maxLength"),64);Assert.assertEquals(open.getJSONArray("appRoles").getJSONObject(0).getInt("value"),0);Assert.assertEquals(open.getJSONArray("appRoles").getJSONObject(1).getInt("value"),1);
        System.out.println("PASS current open/invitation/closed registration options preserve the old write policy; closing registration overrides invitation links and only public username/role choices are returned");

        f=new Fixture();f.mode="2";Assert.assertFalse(f.policy.options(" Referrer ").getBoolean("inviteCodeRequired"));Assert.assertTrue(f.policy.options("Referrer").getBoolean("referralAccepted"));
        f.allow=false;Assert.assertTrue(f.policy.options("Referrer").getBoolean("inviteCodeRequired"));Assert.assertFalse(f.policy.options("Referrer").getBoolean("referralAccepted"));
        f.exists=false;int previous=f.permissionReads;Assert.assertFalse(f.policy.options("Referrer").getBoolean("referralAccepted"));Assert.assertEquals(f.permissionReads,previous);
        System.out.println("PASS referral input does not itself waive an invitation code: the current resolved user's invitation permission is required, withdrawing it takes effect on the next read and nonexistent users do not trigger permission lookup");

        f=new Fixture();for(String name:List.of("x".repeat(65),"bad_name","bad/name","管理员","Referrer\nname","FileTransfer","adminUser")){
            try{f.policy.options(name);Assert.fail(name);}catch(ApiV3Exception e){Assert.assertEquals(e.status,422);}
        }
        Assert.assertEquals(f.userReads,0);f.missing=true;try{f.policy.options(null);Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,503);}
        f.missing=false;for(String invalid:List.of("","3","open")){f.mode=invalid;try{f.policy.options(null);Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,503);}}
        System.out.println("PASS referral length/character/reserved-name rules reject before lookup; missing or unknown registration settings fail closed instead of inventing an open mode");

        f=new Fixture();f.mode="2";var ok=f.call("referral=Referrer&apiKey=ignored-fixture");Assert.assertEquals(ok.getInt("status"),200);Assert.assertEquals(ok.getJSONObject("json").getInt("code"),0);
        var data=ok.getJSONObject("json").getJSONObject("data");Assert.assertEquals(data.keySet(),Set.of("mode","enabled","inviteCodeRequired","referralAccepted","username","appRoles"));Assert.assertFalse(ok.toString().contains("PRIVATE_"));Assert.assertFalse(ok.toString().contains("ignored-fixture"));
        var bad=f.call("referral=bad%2Fname");Assert.assertEquals(bad.getInt("status"),422);Assert.assertTrue(bad.getJSONObject("json").isNull("data"));
        f.storageFailure=true;var unavailable=f.call(null);Assert.assertEquals(unavailable.getInt("status"),500);Assert.assertTrue(unavailable.getJSONObject("json").isNull("data"));
        System.out.println("PASS actual account controller plus real request query decoding and envelope helpers return no-store JSON with request ID; Cookie/apiKey/context users confer no registration authority and private inviter fields never appear");
    }
}
