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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.SettingsProcessor;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.*;

/** Activates an existing pending phone registration; never calls the self-committing addUser. */
@Service
public class AccountRegistrationService {
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private InvitecodeRepository invitations;
    @Inject private UserNameMgmtService names;
    @Inject private AccountVerificationService verification;
    @Inject private PointtransferMgmtService points;
    @Inject private NotificationMgmtService notifications;
    @Inject private AccountRegistrationEffectsService effects;
    private record Plan(String actor,String name,String referralId,String referralName,String inviteId,String generator) { }
    public record Transfer(String recipient,String id) { }
    public record Result(String userId,String name,List<String> defeated,List<Transfer> transfers,List<String> recipients) { }
    protected long now(){return System.currentTimeMillis();}
    protected void unavailable(Exception e){LogManager.getLogger(getClass()).warn("Account registration unavailable");}
    protected void afterCommit(Result result){effects.afterCommit(result);}

    public JSONObject complete(String phone,String code,String password,int appRole,String mbti,String referral)throws Exception {
        if(phone==null||!phone.matches("1[0-9]{10}")||password==null||!password.matches("[a-f0-9]{32}"))throw invalid();
        final var result=complete("userPhone",phone,code,password,appRole,mbti,referral);
        return new JSONObject().put("userId",result.userId()).put("registered",true);
    }
    public String completeForUserId(String id,String code,String password,int appRole,String mbti,String referral)throws Exception {
        if(id==null||!id.matches("[1-9][0-9]{0,18}")||password==null||password.isEmpty()||password.length()>32)throw invalid();
        ApiV3Json.canonical(password);
        return complete("oId",id,code,password,appRole,mbti,referral).userId();
    }
    private Result complete(String selector,String value,String code,String password,int appRole,String mbti,String referral)throws Exception {
        if(code==null||!code.matches("[0-9]{6}")||appRole!=UserExt.USER_APP_ROLE_C_HACKER&&appRole!=UserExt.USER_APP_ROLE_C_PAINTER
                ||mbti==null||mbti.length()>32||referral==null||referral.length()>64)throw invalid();
        // Invalid referral names historically receive no referral bonus. The v3 adapter validates its input first.
        final String acceptedReferral=UserNameRules.invalid(referral)?"":referral;
        if(users.hasTransactionBegun())throw new IllegalStateException("registration root required");
        final Result result;
        try{
            final Plan plan=plan(selector,value,acceptedReferral);
            final var destinations=new ArrayList<String>();destinations.add(plan.actor());
            if(!plan.referralId().isEmpty())destinations.add(plan.referralId());
            if(!plan.generator().isEmpty()&&!Pointtransfer.ID_C_SYS.equals(plan.generator()))destinations.add(plan.generator());
            result=points.executeWithTransferLocks(Pointtransfer.ID_C_SYS,destinations,()->activate(plan,selector,value,code,password,appRole,SettingsProcessor.checkMBTI(mbti)));
        }catch(ApiV3Exception e){throw e;}
        catch(Exception e){unavailable(e);throw new ApiV3Exception(503,"temporarily_unavailable","注册暂不可用");}
        // A delivery failure cannot turn committed activation into a failed registration response.
        try{afterCommit(result);}catch(RuntimeException e){LogManager.getLogger(getClass()).warn("Committed registration effects unavailable");}
        return result;
    }
    private Plan plan(String selector,String value,String referral)throws Exception {
        final var rows=users.select("SELECT oId,userName FROM `"+users.getName()+"` WHERE "+selector+"=? ORDER BY oId",value);
        if(rows.size()!=1)throw proof();final var actor=rows.getFirst();final String id=actor.getString(Keys.OBJECT_ID),name=actor.optString(User.USER_NAME);
        if(UserNameRules.invalid(name)||UserExt.NULL_USER_NAME.equals(name))throw proof();
        String referralId="",referralName=referral;
        if(!referral.isEmpty()){
            final var matches=users.select("SELECT oId,userName FROM `"+users.getName()+"` WHERE userName=? ORDER BY oId LIMIT 2",referral);
            if(matches.size()>1)throw changed();
            if(!matches.isEmpty()){referralId=matches.getFirst().getString(Keys.OBJECT_ID);referralName=matches.getFirst().getString(User.USER_NAME);}
        }
        final var codes=invites(id,false);if(codes.size()>1)throw changed();
        final var invite=codes.isEmpty()?null:codes.getFirst();
        final boolean unused=invite!=null&&invite.optInt(Invitecode.STATUS,-1)==Invitecode.STATUS_C_UNUSED;
        return new Plan(id,name,referralId,referralName,unused?invite.getString(Keys.OBJECT_ID):"",unused?invite.optString(Invitecode.GENERATOR_ID):"");
    }
    private List<JSONObject> invites(String actor,boolean lock)throws Exception {
        return invitations.select("SELECT oId,userId,generatorId,status FROM `"+invitations.getName()+"` WHERE userId=? ORDER BY oId LIMIT 2"+(lock?" FOR UPDATE":""),actor);
    }
    private Result activate(Plan plan,String selector,String value,String code,String password,int role,String mbti)throws Exception {
        try(final var caches=ArticlePublicationCaches.open(users::hasTransactionBegun)){
            final var tx=ApiV3Transactions.strict(users.beginTransaction());
            final var touched=new LinkedHashSet<String>();
            try{
                names.lockNamespaceInCurrentTransaction();
                final var setting=options.getForUpdate(Option.ID_C_MISC_ALLOW_REGISTER);
                final String mode=setting==null?"":setting.optString(Option.OPTION_VALUE);
                if("1".equals(mode))throw new ApiV3Exception(403,"registration_closed","当前不开放注册");
                if(!"0".equals(mode)&&!"2".equals(mode))throw new IllegalStateException("registration setting unavailable");
                final var ids=new TreeSet<String>();ids.add(plan.actor());if(!plan.referralId().isEmpty())ids.add(plan.referralId());
                if(!plan.generator().isEmpty()&&!Pointtransfer.ID_C_SYS.equals(plan.generator()))ids.add(plan.generator());
                final var args=new ArrayList<Object>(ids);args.add(plan.name());
                final String referralPredicate=plan.referralName().isEmpty()?"":" OR userName=?";
                if(!plan.referralName().isEmpty())args.add(plan.referralName());
                final var locked=users.select("SELECT oId,userName,userPhone,userStatus FROM `"+users.getName()+"` WHERE oId IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") OR userName=?"+referralPredicate+" ORDER BY oId FOR UPDATE",args.toArray());
                final var byId=new HashMap<String,JSONObject>();for(var row:locked)byId.put(row.getString(Keys.OBJECT_ID),row);
                final var owner=byId.get(plan.actor());
                if(owner==null||!plan.name().equals(owner.optString(User.USER_NAME))||!value.equals(owner.optString(selector)))throw changed();
                if(owner.optInt(UserExt.USER_STATUS,-1)!=UserExt.USER_STATUS_C_NOT_VERIFIED)throw proof();
                if(!plan.referralId().isEmpty()&&(!byId.containsKey(plan.referralId())||!plan.referralName().equals(byId.get(plan.referralId()).optString(User.USER_NAME))))throw changed();
                if(plan.referralId().isEmpty()&&!plan.referralName().isEmpty()
                        &&locked.stream().anyMatch(row->plan.referralName().equalsIgnoreCase(row.optString(User.USER_NAME))))throw changed();
                if(!plan.generator().isEmpty()&&!Pointtransfer.ID_C_SYS.equals(plan.generator())&&!byId.containsKey(plan.generator()))throw changed();
                final var defeated=new ArrayList<String>();
                for(var row:locked){
                    if(row.getString(Keys.OBJECT_ID).equals(plan.actor()))continue;
                    // Accepted account names contain ASCII letters, digits and hyphens only.
                    if(!row.optString(User.USER_NAME).equalsIgnoreCase(plan.name()))continue;
                    final int status=row.optInt(UserExt.USER_STATUS,-1);
                    if(status!=UserExt.USER_STATUS_C_NOT_VERIFIED)throw new ApiV3Exception(409,"name_taken","用户名已被使用");
                    defeated.add(row.getString(Keys.OBJECT_ID));
                }
                final var currentInvites=invites(plan.actor(),true);
                if(currentInvites.size()>1)throw changed();
                final var current=currentInvites.isEmpty()?null:currentInvites.getFirst();
                final boolean unused=current!=null&&current.optInt(Invitecode.STATUS,-1)==Invitecode.STATUS_C_UNUSED;
                if(unused!=!plan.inviteId().isEmpty()||unused&&(!plan.inviteId().equals(current.optString(Keys.OBJECT_ID))||!plan.generator().equals(current.optString(Invitecode.GENERATOR_ID))))throw changed();
                if(!verification.consumeRegistrationCodeInCurrentTransaction(plan.actor(),code,plan.referralName(),plan.referralId(),plan.inviteId()))throw proof();
                final long time=now();if(time<=0)throw new IllegalStateException("registration clock");
                touched.add(plan.actor());
                users.updateColumnsInCurrentTransaction(plan.actor(),new JSONObject().put(User.USER_PASSWORD,password).put(UserExt.USER_APP_ROLE,role).put(UserExt.USER_STATUS,UserExt.USER_STATUS_C_VALID).put("mbti",mbti));
                for(String id:defeated){touched.add(id);users.updateColumnsInCurrentTransaction(id,new JSONObject().put(User.USER_NAME,UserExt.NULL_USER_NAME).put(User.USER_EMAIL,""));}
                final var transfers=new ArrayList<Transfer>();final var recipients=new LinkedHashSet<String>();
                transfer(transfers,plan.actor(),Pointtransfer.TRANSFER_TYPE_C_INIT,Pointtransfer.TRANSFER_SUM_C_INIT,plan.actor(),time);
                notify(recipients,plan.actor(),"",Notification.DATA_TYPE_C_SYS_ANNOUNCE_NEW_USER);
                if(!plan.referralId().isEmpty()){
                    transfer(transfers,plan.actor(),Pointtransfer.TRANSFER_TYPE_C_INVITED_REGISTER,Pointtransfer.TRANSFER_SUM_C_INVITE_REGISTER,plan.referralId(),time);
                    transfer(transfers,plan.referralId(),Pointtransfer.TRANSFER_TYPE_C_INVITE_REGISTER,Pointtransfer.TRANSFER_SUM_C_INVITE_REGISTER,plan.actor(),time);
                    notify(recipients,plan.referralId(),plan.actor(),Notification.DATA_TYPE_C_INVITATION_LINK_USED);
                }
                if(unused){
                    int changed=ApiV3TransactionStatements.execute("UPDATE `"+invitations.getName()+"` SET status=?,useTime=? WHERE oId=? AND userId=? AND status=?",Invitecode.STATUS_C_USED,time,plan.inviteId(),plan.actor(),Invitecode.STATUS_C_UNUSED);
                    if(changed!=1)throw changed();
                    if(!plan.generator().isEmpty()&&!Pointtransfer.ID_C_SYS.equals(plan.generator())){
                        transfer(transfers,plan.generator(),Pointtransfer.TRANSFER_TYPE_C_INVITECODE_USED,Pointtransfer.TRANSFER_SUM_C_INVITECODE_USED,plan.actor(),time);
                        notify(recipients,plan.generator(),plan.actor(),Notification.DATA_TYPE_C_INVITECODE_USED);
                    }
                }
                tx.commit();return new Result(plan.actor(),plan.name(),List.copyOf(defeated),List.copyOf(transfers),List.copyOf(recipients));
            }finally{
                try{if(tx.isActive())tx.rollback();}
                finally{if(!users.hasTransactionBegun())for(String id:touched)try{users.evictUserNameCaches(id,plan.name());}catch(RuntimeException e){LogManager.getLogger(getClass()).warn("Registration cache invalidation unavailable");}}
            }
        }
    }
    private void transfer(List<Transfer> records,String to,int type,int sum,String data,long time){
        final String id=points.transferInCurrentTransaction(Pointtransfer.ID_C_SYS,to,type,sum,data,time,"",PointtransferSource.EMPTY,false);
        if(id==null||id.isEmpty())throw new IllegalStateException("registration reward failed");records.add(new Transfer(to,id));
    }
    private void notify(Set<String> recipients,String recipient,String data,int type)throws Exception {
        notifications.addAccountRegistrationNotificationInCurrentTransaction(recipient,data,type);recipients.add(recipient);
    }
    private static ApiV3Exception invalid(){return new ApiV3Exception(422,"invalid_request","请求字段不合法");}
    private static ApiV3Exception proof(){return new ApiV3Exception(422,"invalid_verification","验证码无效或已失效");}
    private static ApiV3Exception changed(){return new ApiV3Exception(409,"registration_changed","注册信息已变化，请重新获取验证码");}
}
