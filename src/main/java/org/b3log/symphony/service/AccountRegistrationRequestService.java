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
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.util.*;

/** Registration SMS coordination. Remote delivery never holds an SQL transaction. Not a session login. */
@Service
public class AccountRegistrationRequestService {
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private RolePermissionRepository permissions;
    @Inject private InvitecodeRepository invitations;
    @Inject private VerifycodeRepository codes;
    @Inject private UserNameMgmtService names;
    @Inject private AccountPendingRegistrationService pending;
    @Inject private VerifycodeMgmtService sms;
    public static final long RESERVATION_MILLIS=86_400_000L;
    private static final Logger LOGGER=LogManager.getLogger(AccountRegistrationRequestService.class);
    private static final SecureRandom RANDOM=new SecureRandom();
    private record Policy(String mode,String referral,String referralId,String referralPhone,boolean active,boolean link) { }
    protected long now(){return System.currentTimeMillis();}
    protected String newCode(){return String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));}
    protected boolean allowed(String ip,String name,String phone){return AccountSmsQuota.access(ip,name,phone);}
    protected boolean captchaAccepted(String proof) {
        // 极验校验保护入口；未经阿达明确允许，不得修改、移动、删除或绕过此调用。
        return AnonymousCaptchaShield.verifyProof(proof);
    }
    protected boolean acceptableName(String name){return !CensorFactory.getTextCensor().censor(name).isBlocked();}
    protected boolean send(String phone,String code){return sms.sendAccountVerifyCodeSMS(phone,code);}
    protected void unavailable(Exception failure){LOGGER.warn("Registration SMS request unavailable");}

    public JSONObject request(String name,String phone,String captcha,String invite,String referral,String language,String ip)throws Exception {
        if(UserNameRules.invalid(name)||UserExt.NULL_USER_NAME.equals(name)||phone==null||!phone.matches("1[0-9]{10}")
                ||invite==null||!invite.isEmpty()&&!invite.matches("[A-Za-z0-9]{16}")
                ||referral==null||!referral.isEmpty()&&UserNameRules.invalid(referral)
                ||language==null||!language.matches("[A-Za-z0-9_-]{0,16}"))throw ApiV3WriteInputs.invalid();
        final String proof=AccountCaptchaProof.validate(captcha);
        if(users.hasTransactionBegun())throw new IllegalStateException("registration request root required");
        try{
            if(!allowed(ip,name,phone))throw new ApiV3Exception(429,"rate_limited","验证码发送过于频繁，请稍后重试");
            // All three registration paths require the same captcha, including an otherwise valid invite code.
            if(!captchaAccepted(proof))throw new ApiV3Exception(422,"invalid_captcha","请重新完成验证");
            if(!acceptableName(name))throw new ApiV3Exception(422,"invalid_name","用户名含违规信息，请修改后重试");
            final var first=policy(referral,false);checkReferral(first,referral,phone,"");final String requested=first.mode().equals("2")?invite:"";
            if(first.mode().equals("2")&&!first.link()&&requested.isEmpty())throw invalidInvite();
            final String actor=preflightOwner(name,phone);checkReferral(first,referral,phone,actor);
            if(!requested.isEmpty())checkAvailable(inviteRows(requested,false),actor,now());
            final String code=newCode();if(code==null||!code.matches("[0-9]{6}"))throw new IllegalStateException("invalid registration SMS generator");
            if(!send(phone,code))throw new ApiV3Exception(503,"sms_unavailable","验证码发送失败，请稍后重试");
            // A provider acceptance is not an SQL commit. All database state is rechecked after delivery.
            try(final var caches=ArticlePublicationCaches.open(users::hasTransactionBegun)){
                final var tx=ApiV3Transactions.strict(users.beginTransaction());
                try{
                    names.lockNamespaceInCurrentTransaction();
                    final var current=policy(referral,true);
                    if(!first.equals(current))throw changed();
                    checkReferral(current,referral,phone,"");
                    final long time=now();if(time<=0)throw new IllegalStateException("invalid registration clock");
                    final long expiry=Math.addExact(time,RESERVATION_MILLIS);
                    final var owner=pending.prepareInCurrentTransaction(name,phone,language,time);
                    checkReferral(current,referral,phone,owner.userId());
                    final String inviteId=reserveInCurrentTransaction(owner.userId(),requested,time);
                    codes.addRegistrationPhoneCodeInCurrentTransaction(new JSONObject().put(Verifycode.USER_ID,owner.userId())
                            .put(Verifycode.TYPE,Verifycode.TYPE_C_PHONE).put(Verifycode.BIZ_TYPE,Verifycode.BIZ_TYPE_C_REGISTER)
                            .put(Verifycode.RECEIVER,phone).put(Verifycode.CODE,code).put(Verifycode.STATUS,Verifycode.STATUS_C_SENT)
                            .put(Verifycode.EXPIRED,expiry),name,current.referral(),current.referralId(),inviteId);
                    tx.commit();return new JSONObject().put("accepted",true);
                }finally{if(tx.isActive())tx.rollback();}
            }
        }catch(ApiV3Exception e){throw e;}
        catch(Exception e){unavailable(e);throw new ApiV3Exception(503,"temporarily_unavailable","验证码申请暂不可用");}
    }
    /** Compatibility adapter for the old first-stage entry; caller retains its SMS/response flow. */
    public void reserveLegacy(String actor,String phone,String invite)throws Exception {
        if(actor==null||!actor.matches("[1-9][0-9]{0,18}")||phone==null||!phone.matches("1[0-9]{10}")
                ||invite==null||!invite.matches("[A-Za-z0-9]{16}"))throw ApiV3WriteInputs.invalid();
        if(users.hasTransactionBegun())throw new IllegalStateException("legacy reservation root required");
        try{
            final var tx=ApiV3Transactions.strict(users.beginTransaction());
            try{
                names.lockNamespaceInCurrentTransaction();
                final var setting=options.getForUpdate(Option.ID_C_MISC_ALLOW_REGISTER);
                final String mode=setting==null?"":setting.optString(Option.OPTION_VALUE);
                if(mode.equals("1"))throw new ApiV3Exception(403,"registration_closed","当前不开放注册");
                if(!mode.equals("0")&&!mode.equals("2"))throw new IllegalStateException("registration setting unavailable");
                final var owners=users.select("SELECT oId,userPhone,userStatus FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",actor);
                if(owners.size()!=1||!phone.equals(owners.getFirst().optString("userPhone"))||owners.getFirst().optInt(UserExt.USER_STATUS,-1)!=UserExt.USER_STATUS_C_NOT_VERIFIED)throw changed();
                final long time=now();if(time<=0)throw new IllegalStateException("invalid registration clock");
                if(mode.equals("2"))reserveInCurrentTransaction(actor,invite,time);
                tx.commit();
            }finally{if(tx.isActive())tx.rollback();}
        }catch(ApiV3Exception e){throw e;}
        catch(Exception e){unavailable(e);throw new ApiV3Exception(503,"temporarily_unavailable","验证码申请暂不可用");}
    }
    /** Background cleanup adapter: a live one-day reservation outlives the code's original age. */
    public void expireInvitations(long maximumAge)throws Exception {
        if(users.hasTransactionBegun())throw new IllegalStateException("invitation expiry root required");
        final long time=now();if(maximumAge<=0||time<=maximumAge||time<=RESERVATION_MILLIS)throw new IllegalArgumentException("invitation expiry clock");
        try{
            final var tx=ApiV3Transactions.strict(users.beginTransaction());
            try{
                ApiV3TransactionStatements.execute("DELETE FROM `"+invitations.getName()+"` WHERE status=? AND generatorId<>? AND oId<=?"
                        +" AND (userId IS NULL OR userId='' OR COALESCE(useTime,0)<=?)",Invitecode.STATUS_C_UNUSED,Pointtransfer.ID_C_SYS,Long.toString(time-maximumAge),time-RESERVATION_MILLIS);
                tx.commit();
            }finally{if(tx.isActive())tx.rollback();}
        }catch(Exception e){unavailable(e);throw new ApiV3Exception(503,"temporarily_unavailable","邀请码清理暂不可用");}
        finally{ApiV3Transactions.releaseThreadResources();}
    }
    private Policy policy(String referral,boolean lock)throws Exception {
        final JSONObject setting;
        if(lock)setting=options.getForUpdate(Option.ID_C_MISC_ALLOW_REGISTER);
        else{final var rows=options.select("SELECT optionValue FROM `"+options.getName()+"` WHERE oId=?",Option.ID_C_MISC_ALLOW_REGISTER);setting=rows.size()==1?rows.getFirst():null;}
        final String mode=setting==null?"":setting.optString(Option.OPTION_VALUE);
        if(mode.equals("1"))throw new ApiV3Exception(403,"registration_closed","当前不开放注册");
        if(!mode.equals("0")&&!mode.equals("2"))throw new IllegalStateException("registration setting unavailable");
        String canonical=referral,referralId="",referralPhone="";boolean active=false,link=false;
        if(!referral.isEmpty()){
            final var rows=users.select("SELECT oId,userName,userRole,userPhone,userStatus FROM `"+users.getName()+"` WHERE userName=? ORDER BY oId LIMIT 2"+(lock?" FOR UPDATE":""),referral);
            if(rows.size()>1)throw changed();
            if(!rows.isEmpty()){
                final var row=rows.getFirst();canonical=row.getString(User.USER_NAME);referralId=row.getString(Keys.OBJECT_ID);referralPhone=row.optString("userPhone");
                active=row.optInt(UserExt.USER_STATUS,-1)==UserExt.USER_STATUS_C_VALID;
                if(active){
                    final var grants=permissions.select("SELECT oId FROM `"+permissions.getName()+"` WHERE roleId=? AND permissionId=? LIMIT 1"+(lock?" FOR UPDATE":""),
                            row.optString(User.USER_ROLE),Permission.PERMISSION_ID_C_COMMON_USE_INVITATION_LINK);
                    link=!grants.isEmpty();
                }
            }
        }
        return new Policy(mode,canonical,referralId,referralPhone,active,link);
    }
    private static void checkReferral(Policy policy,String supplied,String phone,String actor){
        if(!supplied.isEmpty()&&(!policy.active()||policy.referralId().isEmpty()||phone.equals(policy.referralPhone())||actor.equals(policy.referralId())))
            throw new ApiV3Exception(422,"invalid_referral","邀请人须为已激活的其他用户");
    }
    private String preflightOwner(String name,String phone)throws Exception {
        final var rows=users.select("SELECT oId,userName,userPhone,userStatus,CASE WHEN userName=? THEN 1 ELSE 0 END AS nameMatches FROM `"+users.getName()+"` WHERE userPhone=? OR (userName=? AND (userStatus<>? OR userStatus IS NULL)) ORDER BY oId LIMIT 4",name,phone,name,UserExt.USER_STATUS_C_NOT_VERIFIED);
        if(rows.size()==4)throw changed();String actor="";
        for(var row:rows){
            final int status=row.optInt(UserExt.USER_STATUS,-1);
            if(row.optInt("nameMatches")==1&&status!=UserExt.USER_STATUS_C_NOT_VERIFIED)throw new ApiV3Exception(409,"name_taken","用户名已被使用");
            if(phone.equals(row.optString("userPhone"))){
                if(!actor.isEmpty())throw changed();
                if(status!=UserExt.USER_STATUS_C_NOT_VERIFIED&&status!=UserExt.USER_STATUS_C_DEACTIVATED)throw new ApiV3Exception(409,"phone_unavailable","该手机号不可用于注册");
                actor=row.getString(Keys.OBJECT_ID);
            }
        }
        return actor;
    }
    private List<JSONObject> inviteRows(String code,boolean lock)throws Exception {
        return invitations.select("SELECT oId,code,userId,status,useTime FROM `"+invitations.getName()+"` WHERE code=? ORDER BY oId LIMIT 2"+(lock?" FOR UPDATE":""),code);
    }
    private static long deadline(JSONObject invitation){
        final Object value=invitation.opt(Invitecode.USE_TIME);if(!(value instanceof Number time)||time.longValue()<0)throw new IllegalStateException("invalid invitation reservation clock");
        return time.longValue()==0?0:Math.addExact(time.longValue(),RESERVATION_MILLIS);
    }
    private JSONObject checkAvailable(List<JSONObject> rows,String actor,long time){
        if(time<=0)throw new IllegalStateException("invalid invitation clock");
        if(rows.size()!=1||rows.getFirst().optInt(Invitecode.STATUS,-1)!=Invitecode.STATUS_C_UNUSED)throw invalidInvite();
        final var row=rows.getFirst();final String reserved=row.optString(Invitecode.USER_ID);
        if(!reserved.isEmpty()&&!reserved.equals(actor)&&deadline(row)>time)throw reserved();
        return row;
    }
    /** Namespace and pending owner are already locked. No independent commit or remote operation. */
    private String reserveInCurrentTransaction(String actor,String requested,long time)throws Exception {
        final var owned=invitations.select("SELECT oId,code,userId,status,useTime FROM `"+invitations.getName()+"` WHERE userId=? ORDER BY oId LIMIT 3 FOR UPDATE",actor);
        if(owned.size()>1)throw changed();
        JSONObject previous=owned.isEmpty()?null:owned.getFirst();
        if(previous!=null&&previous.optInt(Invitecode.STATUS,-1)!=Invitecode.STATUS_C_UNUSED)throw changed();
        JSONObject target=requested.isEmpty()?null:checkAvailable(inviteRows(requested,true),actor,time);
        if(previous!=null&&(target==null||!previous.getString(Keys.OBJECT_ID).equals(target.getString(Keys.OBJECT_ID)))){
            if(deadline(previous)>time){if(target!=null)throw reserved();target=previous;}
            else{
                final int changed=ApiV3TransactionStatements.execute("UPDATE `"+invitations.getName()+"` SET userId='',useTime=0 WHERE oId=? AND userId=? AND status=?",previous.getString(Keys.OBJECT_ID),actor,Invitecode.STATUS_C_UNUSED);
                if(changed!=1)throw changed();
            }
        }
        if(target==null)return "";
        final String id=target.getString(Keys.OBJECT_ID);
        final int changed=ApiV3TransactionStatements.execute("UPDATE `"+invitations.getName()+"` SET userId=?,useTime=? WHERE oId=? AND status=?",actor,time,id,Invitecode.STATUS_C_UNUSED);
        if(changed!=1)throw changed();return id;
    }
    private static ApiV3Exception invalidInvite(){return new ApiV3Exception(422,"invalid_invite_code","邀请码不可用");}
    private static ApiV3Exception reserved(){return new ApiV3Exception(409,"invite_code_reserved","邀请码已预留，请稍后重试");}
    private static ApiV3Exception changed(){return new ApiV3Exception(409,"registration_changed","注册信息已变化，请重新申请验证码");}
}
