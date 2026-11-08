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

import org.apache.logging.log4j.LogManager;
import org.apache.commons.lang.time.DateUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.VerifycodeRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Locale;

/** Anonymous reset request: existing captcha + shared SMS quota, explicit current phone binding. */
@Service
public class AccountPasswordResetRequestService {
    @Inject private UserRepository users;
    @Inject private VerifycodeRepository codes;
    @Inject private VerifycodeMgmtService sms;
    private static final SecureRandom RANDOM=new SecureRandom();
    protected long now(){return System.currentTimeMillis();}
    protected String newCode(){return String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));}
    protected boolean allowed(String ip,String name,String phone){return AccountSmsQuota.access(ip,name,phone);}
    protected boolean captchaAccepted(String proof) {
        // 极验校验保护入口；未经阿达明确允许，不得修改、移动、删除或绕过此调用。
        return AnonymousCaptchaShield.verifyProof(proof);
    }
    protected boolean send(String phone,String code){return sms.sendAccountVerifyCodeSMS(phone,code);}
    protected void unavailable(Exception e){LogManager.getLogger(getClass()).warn("Password reset request unavailable");}
    public JSONObject request(String phone,String captcha,String ip)throws Exception {
        if(phone==null||!phone.matches("1[0-9]{10}"))throw ApiV3WriteInputs.invalid();
        final String proof=AccountCaptchaProof.validate(captcha);
        if(users.hasTransactionBegun())throw new IllegalStateException("reset request root required");
        boolean verified=false;
        try{
            final var matches=users.select("SELECT oId,userName,userPhone,userStatus FROM `"+users.getName()+"` WHERE userPhone=? ORDER BY oId LIMIT 2",phone);
            final var owner=matches.size()==1?matches.getFirst():null;
            final boolean eligible=owner!=null&&owner.optInt(UserExt.USER_STATUS,-1)==UserExt.USER_STATUS_C_VALID;
            final String quotaName=eligible?owner.optString(User.USER_NAME):"phone:"+phone;
            if(!allowed(ip,quotaName,phone))throw new ApiV3Exception(429,"rate_limited","验证码发送过于频繁，请稍后重试");
            if(!captchaAccepted(proof))throw new ApiV3Exception(422,"invalid_captcha","请重新完成验证");
            verified=true;
            // The same public receipt for absent/disabled/ambiguous accounts does not disclose membership.
            if(!eligible)return accepted();
            final String code=newCode();if(code==null||!code.matches("[0-9]{6}"))throw new IllegalStateException("invalid SMS generator");
            // No DB lock/transaction is held while calling a remote provider. Its result is not an SQL commit.
            if(!send(phone,code)){LogManager.getLogger(getClass()).warn("Password reset SMS not accepted");return accepted();}
            final var tx=ApiV3Transactions.strict(codes.beginTransaction());
            try{
                final String actor=owner.getString(Keys.OBJECT_ID);
                final var current=users.select("SELECT oId,userPhone,userStatus FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",actor);
                if(current.size()!=1||!phone.equals(current.getFirst().optString("userPhone"))||current.getFirst().optInt(UserExt.USER_STATUS,-1)!=UserExt.USER_STATUS_C_VALID)return accepted();
                final long time=now();if(time<=0)throw new IllegalStateException("invalid SMS clock");
                codes.addAccountPhoneCodeInCurrentTransaction(new JSONObject().put(Verifycode.USER_ID,actor).put(Verifycode.TYPE,Verifycode.TYPE_C_PHONE)
                        .put(Verifycode.BIZ_TYPE,Verifycode.BIZ_TYPE_C_RESET_PWD).put(Verifycode.RECEIVER,phone).put(Verifycode.CODE,code)
                        .put(Verifycode.STATUS,Verifycode.STATUS_C_SENT).put(Verifycode.EXPIRED,DateUtils.addDays(new Date(time),1).getTime()));
                tx.commit();return accepted();
            }finally{if(tx.isActive())tx.rollback();}
        }catch(ApiV3Exception e){if(verified){unavailable(e);return accepted();}throw e;}
        catch(Exception e){unavailable(e);if(verified)return accepted();throw new ApiV3Exception(503,"temporarily_unavailable","验证码申请暂不可用");}
    }
    private static JSONObject accepted(){return new JSONObject().put("accepted",true);}
}
