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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.*;

/** Pending-account primitive. Caller owns captcha/invitation authorization, SMS policy and the strict root. */
@Service
public class AccountPendingRegistrationService {
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private UserNameMgmtService names;
    public record Pending(String userId,boolean created) { }
    protected String newId(){return Ids.genTimeMillisId();}
    protected String defaultAvatar(){return AvatarQueryService.DEFAULT_AVATAR_URL;}

    /** No independent commit, avatar upload, SMS, reward, notification or session/cache publication. */
    public Pending prepareInCurrentTransaction(String name,String phone,String language,long time)throws Exception {
        if(!users.hasTransactionBegun()||!options.hasTransactionBegun()||!ArticlePublicationCaches.active())throw new IllegalStateException("pending registration owner/cache boundary required");
        if(UserNameRules.invalid(name)||UserExt.NULL_USER_NAME.equals(name)||phone==null||!phone.matches("1[0-9]{10}")
                ||language==null||!language.matches("[A-Za-z0-9_-]{0,16}")||time<=0)throw ApiV3WriteInputs.invalid();
        return ApiV3TransactionSavepoint.run(()->prepareLocked(name,phone,language,time));
    }
    private Pending prepareLocked(String name,String phone,String language,long time)throws Exception {
        names.lockNamespaceInCurrentTransaction();
        final var registration=options.getForUpdate(Option.ID_C_MISC_ALLOW_REGISTER);
        final String mode=registration==null?"":registration.optString(Option.OPTION_VALUE);
        if("1".equals(mode))throw new ApiV3Exception(403,"registration_closed","当前不开放注册");
        if(!"0".equals(mode)&&!"2".equals(mode))throw new IllegalStateException("registration setting unavailable");
        // Only non-pending name holders can block a name. A namespace lock serializes pending names too,
        // so there is no reason to lock an unbounded number of other phones' unverified rows.
        final var rows=users.select("SELECT oId,userName,userPhone,userStatus,userLatestLoginTime,"
                +"CASE WHEN userName=? THEN 1 ELSE 0 END AS nameMatches FROM `"+users.getName()+"`"
                +" WHERE userPhone=? OR (userName=? AND (userStatus<>? OR userStatus IS NULL)) ORDER BY oId LIMIT 4 FOR UPDATE",
                name,phone,name,UserExt.USER_STATUS_C_NOT_VERIFIED);
        if(rows.size()==4)throw conflict("registration_changed","注册信息存在冲突");
        final var phoneOwners=new ArrayList<JSONObject>();
        for(var row:rows){
            final int state=row.optInt(UserExt.USER_STATUS,-1);
            if(row.optInt("nameMatches")==1&&state!=UserExt.USER_STATUS_C_NOT_VERIFIED)throw conflict("name_taken","用户名已被使用");
            if(phone.equals(row.optString("userPhone")))phoneOwners.add(row);
        }
        if(phoneOwners.size()>1)throw conflict("registration_changed","注册信息存在冲突");
        if(!phoneOwners.isEmpty()){
            final var owner=phoneOwners.getFirst();final String id=owner.getString(Keys.OBJECT_ID);final int state=owner.optInt(UserExt.USER_STATUS,-1);
            if(state==UserExt.USER_STATUS_C_NOT_VERIFIED){
                evictAfterTransaction(id,owner.optString(User.USER_NAME),name);
                users.updateColumnsInCurrentTransaction(id,new JSONObject().put(User.USER_NAME,name));
                return new Pending(id,false);
            }
            if(state!=UserExt.USER_STATUS_C_DEACTIVATED)throw conflict("phone_unavailable","该手机号不可用于注册");
            final Object last=owner.opt(UserExt.USER_LATEST_LOGIN_TIME);
            if(!(last instanceof Number number)||number.longValue()<0)throw new IllegalStateException("invalid deactivation timestamp");
            final long lastTime=number.longValue();
            if(lastTime>time)throw conflict("phone_cooling_down","手机号尚处于注销等待期");
            final var eligibleAt=Calendar.getInstance();eligibleAt.setTimeInMillis(lastTime);eligibleAt.add(Calendar.MONTH,1);
            if(eligibleAt.getTimeInMillis()<=lastTime)throw new IllegalStateException("deactivation timestamp overflow");
            if(time<eligibleAt.getTimeInMillis())throw conflict("phone_cooling_down","手机号尚处于注销等待期");
            evictAfterTransaction(id,owner.optString(User.USER_NAME),owner.optString(User.USER_NAME));
            users.updateColumnsInCurrentTransaction(id,new JSONObject().put("userPhone",""));
        }
        final var statistic=options.getForUpdate(Option.ID_C_STATISTIC_MEMBER_COUNT);
        if(statistic==null)throw new IllegalStateException("member count unavailable");
        final int current=Integer.parseInt(statistic.getString(Option.OPTION_VALUE));if(current<0)throw new IllegalStateException("invalid member count");
        int next=Math.addExact(current,1);if(next==8888||next==9999)next=Math.addExact(next,1);
        final var locale=options.getForUpdate(Option.ID_C_MISC_LANGUAGE);if(locale==null)throw new IllegalStateException("registration language unavailable");
        final var owned=new JSONObject().put(User.USER_NAME,name).put("userPhone",phone).put(User.USER_PASSWORD,"")
                .put(UserExt.USER_LANGUAGE,language.isEmpty()?"zh_CN":language);
        final var row=UserRegistrationDefaults.create(owned,"",locale.optString(Option.OPTION_VALUE),time);
        final String id=newId();if(id==null||!id.matches("[1-9][0-9]{0,18}"))throw new IllegalStateException("invalid registration id");
        row.put(Keys.OBJECT_ID,id).put(UserExt.USER_NO,next).put(UserExt.USER_AVATAR_URL,defaultAvatar());
        evictAfterTransaction(id,name,name);
        users.add(row);
        // Namespace was locked first; update just the statistic column and defer its cache invalidation.
        options.update(Option.ID_C_STATISTIC_MEMBER_COUNT,new JSONObject().put(Option.OPTION_VALUE,Integer.toString(next)),Option.OPTION_VALUE);
        return new Pending(id,true);
    }
    private void evictAfterTransaction(String actor,String oldName,String newName){
        for(String name:new LinkedHashSet<>(Arrays.asList(oldName,newName))){
            ArticlePublicationCaches.afterTransaction("registration-user:"+actor+":"+name,()->{
                if(users.hasTransactionBegun())throw new IllegalStateException("pending account cache before transaction end");
                users.evictUserNameCaches(actor,name);
            });
        }
    }
    private static ApiV3Exception conflict(String code,String message){return new ApiV3Exception(409,code,message);}
}
