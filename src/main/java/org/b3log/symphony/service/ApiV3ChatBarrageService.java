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
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Ephemeral display: fee and acceptance receipt are durable, transport is best-effort after commit. */
@Service
public class ApiV3ChatBarrageService {
    @Inject private UserRepository users;
    @Inject private PointtransferMgmtService points;
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    @Inject private ChatMessageValidationService validation;
    @Inject private ChatMessageRestrictionQueryService restrictions;
    @Inject private ChatroomProcessor delivery;
    @Inject private ApiV3RealtimeService realtime;
    protected long now(){return System.currentTimeMillis();}
    public JSONObject send(JSONObject viewer,String raw,String color,String key)throws Exception {
        final String actor=viewer==null?"":viewer.optString(Keys.OBJECT_ID);
        if(!actor.matches("[1-9][0-9]{0,18}"))throw new ApiV3Exception(401,"unauthorized","登录凭据无效");
        ChatBarrageInputs.prepare(raw,color);
        final var event=new AtomicReference<ApiV3BarrageEvent>();final var transfer=new AtomicReference<String>();
        final var quotaName=new AtomicReference<String>();final var confirmed=new AtomicReference<String>();
        final var outcome=points.executeWithTransferLocks(actor,List.of(Pointtransfer.ID_C_SYS),()->{
            if(users.hasTransactionBegun())throw new IllegalStateException("barrage root transaction already active");
            try(final var caches=ArticlePublicationCaches.open(users::hasTransactionBegun)){
                ArticlePublicationCaches.afterTransaction("barrage-user:"+actor,()->{try{users.refreshSettingsCache(actor);}catch(Exception e){users.evictSettingsCaches(actor);}});
                return idempotency.executeWithOutcome(actor,"chat.barrager.send",key,new JSONObject().put("content",raw).put("color",color==null?"":color),()->{
                    final JSONObject current=users.getForUpdate(actor);
                    if(current==null||current.optInt(UserExt.USER_STATUS,-1)!=UserExt.USER_STATUS_C_VALID)throw new ApiV3Exception(404,"not_found","用户不存在或不可用");
                    if(current.optString("userPhone").isBlank())throw new ApiV3Exception(403,"phone_required","请先绑定手机号码");
                    final String prepared;
                    try{prepared=validation.prepare(current,raw);}catch(ChatMessageValidation.Rejected e){throw new ApiV3Exception(e.status,e.code,e.getMessage());}
                    final var input=ChatBarrageInputs.prepare(prepared,color);final long time=now();
                    ChatMessageRestrictions.check(restrictions.readInCurrentTransaction(actor),DataModelService.hasPermission(current.optString(User.USER_ROLE),3),input.content(),time,()->{
                        if(input.content().equals(confirmed.get()))return true;final boolean yes=ChatMessageAbuseState.confirmRisk(actor,input.content());if(yes)confirmed.set(input.content());return yes;
                    });
                    final String name=current.getString(User.USER_NAME);
                    if(!name.equals(quotaName.get())){
                        if(!ChatBarrageQuota.take(name))throw new ApiV3Exception(429,"rate_limited","弹幕发送过于频繁");quotaName.set(name);
                    }
                    final var price=ChatroomProcessor.getBarragerPrice();
                    if(price.amount()<0||price.unit()==null||price.unit().length()>128)throw new ApiV3Exception(503,"unavailable","弹幕价格暂不可用");
                    if(current.optInt(UserExt.USER_POINT)<price.amount())throw insufficient();
                    final var published=new ApiV3BarrageEvent(UUID.randomUUID().toString(),input.content(),input.color(),time,actor,name,current.optString(UserExt.USER_NICKNAME),current.optString(UserExt.USER_AVATAR_URL));
                    String transferId="";
                    if(price.amount()>0){transferId=points.transferInCurrentTransaction(actor,Pointtransfer.ID_C_SYS,Pointtransfer.TRANSFER_TYPE_C_CHAT_ROOM_SEND_BARRAGER,price.amount(),"",time,"",PointtransferSource.EMPTY,false);if(transferId==null)throw insufficient();}
                    event.set(published);transfer.set(transferId);
                    return new JSONObject().put("id",published.id()).put("createdAt",time).put("cost",price.amount()).put("unit",price.unit());
                });
            }
        });
        if(outcome.applied()){
            if(users.hasTransactionBegun()||ArticlePublicationCaches.active())throw new IllegalStateException("barrage effects before commit");
            if(!transfer.get().isEmpty())safely(()->points.indexTransferAsync(actor,Pointtransfer.ID_C_SYS,transfer.get()));
            safely(()->delivery.afterApiBarragePublished(event.get().dto()));
            safely(()->realtime.publishBarrage(event.get()));
        }
        return outcome.result();
    }
    private static ApiV3Exception insufficient(){return new ApiV3Exception(409,"insufficient_points","积分不足，无法发送弹幕");}
    @FunctionalInterface private interface Effect{void run()throws Exception;}
    private static void safely(Effect effect){try{effect.run();}catch(Exception e){LogManager.getLogger(ApiV3ChatBarrageService.class).error("Deliver committed barrage failed",e);}}
}
