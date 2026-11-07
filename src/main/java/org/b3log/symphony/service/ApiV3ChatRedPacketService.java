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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Account transfers, public message, private assignments and replay receipts share one strict root. */
@Service
public class ApiV3ChatRedPacketService {
    @Inject private UserRepository users;
    @Inject private ChatRoomRepository chat;
    @Inject private ChatRedPacketRepository packets;
    @Inject private ChatRedPacketSlotRepository slots;
    @Inject private ApiV3RequestRepository requests;
    @Inject private MembershipRepository memberships;
    @Inject private NotificationRepository notifications;
    @Inject private NotificationMgmtService notificationDelivery;
    @Inject private PointtransferMgmtService points;
    @Inject private PointtransferRepository transfers;
    @Inject private PointtransferRedcRepository transferIndex;
    @Inject private ChatMessageMgmtService messages;
    @Inject private ChatMessageValidationService validation;
    @Inject private ChatMessageRestrictionQueryService restrictions;
    @Inject private ChatMessagePenaltyMgmtService penalties;
    @Inject private ApiV3ChatPublicationService admissionEffects;
    @Inject private ChatroomProcessor delivery;
    protected long now(){return System.currentTimeMillis();}

    public boolean recognizes(String messageId)throws RepositoryException {
        return messageId!=null&&messageId.matches("[1-9][0-9]{0,18}")&&packets.current(messageId,false)!=null;
    }
    public JSONObject create(JSONObject viewer,JSONObject body,String key)throws Exception {
        try{return createCore(viewer,body,key);}catch(ApiV3Exception error){throw error;}catch(Exception error){throw unavailable();}
    }
    private JSONObject createCore(JSONObject viewer,JSONObject body,String key)throws Exception {
        final String actor=actor(viewer);final var input=ApiV3RedPacketInputs.prepare(body,actor);
        final String requestId=requestId(actor,"chat.red-packets.create",key);
        final JSONObject previous=receiptHint(actor,requestId,"chat.red-packets.create");
        final String fee=previous==null?feeRecipient():previous.getString("feeRecipientId");
        if(!id(fee))throw unavailable();
        final var ids=new TreeSet<String>(input.recipientIds());ids.add(actor);ids.add(fee);
        final var saved=new AtomicReference<JSONObject>();final var repeat=new AtomicReference<ChatMessageAbuseState.Repeat>();
        final var penalty=new AtomicReference<ChatMessagePenaltyMgmtService.Result>();final var confirmed=new AtomicReference<String>();
        final var execution=points.executeWithTransferLocks(Pointtransfer.ID_C_SYS,List.of(actor,fee),()->{
            try(final var cache=ArticlePublicationCaches.open(requests::hasTransactionBegun)){
                cacheActor(actor);
                return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(guarded(actor,ids),actor,"chat.red-packets.create",key,input.normalized(),()->
                    ApiV3TransactionSavepoint.run(()->{
                        final JSONObject owner=requireActor(actor,true);
                        final var recipients=new HashMap<String,JSONObject>();
                        for(String recipient:input.recipientIds())recipients.put(recipient,requireActor(recipient,false));
                        final JSONObject collector=requireActor(fee,false);
                        if(!"admin".equals(collector.optString(User.USER_NAME)))throw new ApiV3Exception(409,"recipient_changed","收款账号已变更，请重试");
                        final String command="[redpacket]"+input.normalized()+"[/redpacket]";
                        final String prepared;
                        try{prepared=validation.prepare(owner,command);}catch(ChatMessageValidation.Rejected e){throw new ApiV3Exception(e.status,e.code,e.getMessage());}
                        if(!command.equals(prepared))throw new ApiV3Exception(422,"invalid_content","红包内容不合法");
                        final long time=now();
                        ChatMessageRestrictions.check(restrictions.readInCurrentTransaction(actor),operator(owner),command,time,()->{
                            if(command.equals(confirmed.get()))return true;boolean yes=ChatMessageAbuseState.confirmRisk(actor,command);if(yes)confirmed.set(command);return yes;
                        });
                        if(repeat.get()==null)repeat.set(ChatMessageAbuseState.repeated(owner.getString(User.USER_NAME),command));
                        penalty.set(repeat.get()==ChatMessageAbuseState.Repeat.PENALIZE?penalties.applyInCurrentTransaction(actor,time):null);
                        final var price=ApiV3RedPacketInputs.price(input,premium(actor,time));
                        if(number(requireActor(actor,true).opt(UserExt.USER_POINT))<price.principal())throw new ApiV3Exception(409,"insufficient_points","积分不足");
                        final JSONObject card=new JSONObject().put("msgType","redPacket").put("type",input.type()).put("money",price.perShare())
                                .put("count",input.count()).put("got",0).put("msg",input.message()).put("who",new JSONArray())
                                .put("senderId",actor).put("settlement",ChatRedPacket.MARKER);
                        final JSONObject envelope=new JSONObject().put("userName",owner.getString(User.USER_NAME)).put("userNickname",owner.optString(UserExt.USER_NICKNAME))
                                .put("userAvatarURL",owner.optString(UserExt.USER_AVATAR_URL)).put("userOId",Long.parseLong(actor))
                                .put("content",card.toString()).put("time",time);
                        final JSONObject message=messages.saveInCurrentTransaction(envelope,actor);final String messageId=message.getString(Keys.OBJECT_ID);
                        if(!id(messageId))throw unavailable();
                        final var source=new PointtransferSource("","","chat.red-packets.create",requestId);
                        final String main=transfer(actor,Pointtransfer.ID_C_SYS,Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_SEND_RED_PACKET,price.principal(),messageId,time,source);
                        final String tax=price.tax()==0?"":transfer(Pointtransfer.ID_C_SYS,fee,Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT,price.tax(),messageId,time,source);
                        packets.add(new JSONObject().put("oId",messageId).put("senderId",actor).put("type",input.type()).put("message",input.message())
                                .put("money",input.money()).put("count",input.count()).put("got",0).put("principalPoints",price.principal())
                                .put("poolPoints",price.pool()).put("taxPoints",price.tax()).put("feeRecipientId",fee).put("createdAt",time).put("senderGesture",input.gesture()));
                        final List<Integer> allocation=input.type().equals("random")?ApiV3RedPacketInputs.allocate(price.pool(),input.count()):input.type().equals("heartbeat")?ApiV3RedPacketInputs.heartbeat(price.pool(),input.count()):Collections.nCopies(input.count(),input.type().equals("rockPaperScissors")?0:price.perShare());
                        for(int i=0;i<input.count();i++)slots.add(new JSONObject().put("oId",ApiV3TokenUtil.hash("packet-slot\0"+messageId+"\0"+i))
                                .put("messageId",messageId).put("ordinal",i).put("amount",allocation.get(i))
                                .put("recipientId",input.type().equals("specify")?input.recipientIds().get(i):"").put("claimUserId","").put("claimedAt",0).put("transferId","").put("gesture",-1).put("senderRefundPoints",0).put("senderTransferId",""));
                        for(String recipient:input.recipientIds())notifications.add(new JSONObject().put("userId",recipient).put("dataId",messageId)
                                .put("dataType",Notification.DATA_TYPE_C_RED_PACKET).put("hasRead",false).put("content",""));
                        users.updateColumnsInCurrentTransaction(actor,new JSONObject().put(UserExt.USER_LATEST_CMT_TIME,time));saved.set(message);
                        return new JSONObject().put("result",new JSONObject().put("id",messageId).put("createdAt",time).put("principalPoints",price.principal()).put("poolPoints",price.pool()).put("taxPoints",price.tax()))
                                .put("feeRecipientId",fee).put("transferId",main).put("taxTransferId",tax);
                    }));
            }
        });
        final JSONObject receipt=execution.result();final JSONObject result=creationResult(receipt);
        index(actor,Pointtransfer.ID_C_SYS,receipt.getString("transferId"),"chat.red-packets.create",requestId);
        if(!receipt.getString("taxTransferId").isEmpty())index(Pointtransfer.ID_C_SYS,fee,receipt.getString("taxTransferId"),"chat.red-packets.create",requestId);
        if(execution.applied()){
            safely(()->admissionEffects.afterSharedAdmission(actor,saved.get().getString("userName"),repeat.get(),penalty.get()));
            safely(()->delivery.afterApiRedPacketPublished(saved.get()));
            for(String recipient:input.recipientIds())safely(()->notificationDelivery.publishRefreshNotification(recipient));
        }
        return result;
    }

    public JSONObject detail(JSONObject viewer,String messageId)throws Exception {
        try{return detailCore(viewer,messageId);}catch(ApiV3Exception error){throw error;}catch(Exception error){throw unavailable();}
    }
    private JSONObject detailCore(JSONObject viewer,String messageId)throws Exception {
        final String actor=actor(viewer);identity(messageId);
        if(requests.hasTransactionBegun())throw new IllegalStateException("packet read root required");
        final Transaction root=ApiV3Transactions.strict(requests.beginTransaction());
        try{
            final JSONObject owner=requireActor(actor,false);final JSONObject message=message(messageId);
            final JSONObject packet=packet(messageId,message);final List<JSONObject> current=slots.current(messageId,true);validateSlots(packet,current);
            final JSONObject result=detailInCurrentTransaction(owner,packet,current);root.commit();return result;
        }finally{if(root.isActive())root.rollback();}
    }
    public JSONObject claim(JSONObject viewer,String messageId,String key)throws Exception {return claim(viewer,messageId,new JSONObject(),key);}
    public JSONObject claim(JSONObject viewer,String messageId,JSONObject body,String key)throws Exception {
        try{return claimCore(viewer,messageId,body,key);}catch(ApiV3Exception error){throw error;}catch(Exception error){throw unavailable();}
    }
    private JSONObject claimCore(JSONObject viewer,String messageId,JSONObject body,String key)throws Exception {
        final String actor=actor(viewer);identity(messageId);requestId(actor,"chat.red-packets.claim",key);
        final JSONObject hint=packets.current(messageId,false);if(hint==null)throw ApiV3Requests.notFound();
        final String hintedType=hint.getString("type"),sender=hint.getString("senderId");if(!id(sender))throw unavailable();
        final JSONObject input=ApiV3RedPacketInputs.claim(body,hintedType);final boolean rps=hintedType.equals("rockPaperScissors");
        final var participants=new TreeSet<String>();participants.add(actor);if(rps)participants.add(sender);
        final JSONObject normalized=new JSONObject().put("id",messageId);for(String name:input.keySet())normalized.put(name,input.get(name));
        final var quotaTaken=new AtomicBoolean();final var updated=new AtomicReference<JSONObject>();
        final var execution=points.executeWithTransferLocks(Pointtransfer.ID_C_SYS,new ArrayList<>(participants),()->{
            try(final var cache=ArticlePublicationCaches.open(requests::hasTransactionBegun)){
                for(String participant:participants)cacheActor(participant);
                return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(guarded(actor,participants),actor,"chat.red-packets.claim",key,normalized,()->
                    ApiV3TransactionSavepoint.run(()->{
                        final JSONObject owner=requireActor(actor,false);final JSONObject message=message(messageId);final JSONObject packet=packet(messageId,message);
                        if(!hintedType.equals(packet.optString("type"))||!sender.equals(packet.optString("senderId")))throw unavailable();
                        final List<JSONObject> current=slots.current(messageId,true);validateSlots(packet,current);
                        if(rps&&sender.equals(actor))throw new ApiV3Exception(403,"sender_cannot_claim","不能领取本人发出的猜拳红包");
                        for(JSONObject slot:current)if(actor.equals(slot.optString("claimUserId"))){
                            if(rps&&slot.getInt("gesture")!=input.getInt("gesture"))throw new ApiV3Exception(409,"gesture_conflict","已使用其他出拳领取");
                            return claimReceipt(messageId,slot,packet);
                        }
                        final JSONObject chosen=current.stream().filter(slot->slot.optString("claimUserId").isEmpty()
                                &&(!packet.getString("type").equals("specify")||actor.equals(slot.optString("recipientId")))).findFirst().orElse(null);
                        if(chosen==null){if(packet.getString("type").equals("specify")&&current.stream().noneMatch(slot->actor.equals(slot.optString("recipientId"))))
                            throw new ApiV3Exception(403,"recipient_required","该红包仅限指定用户领取");throw new ApiV3Exception(409,"red_packet_empty","红包已领完");}
                        final long time=now();final var state=restrictions.readInCurrentTransaction(actor);
                        ChatMessageRestrictions.check(new ChatMessageRestrictions.State(state.globalMuteUntil(),state.personalMuteUntil(),0),operator(owner),"",time,()->true);
                        if(state.riskUntil()>time&&!quotaTaken.get()){
                            if(!ChatroomProcessor.takeRedPacketRiskQuota(actor))throw new ApiV3Exception(429,"rate_limited","当前领取次数已达上限");quotaTaken.set(true);
                        }
                        final int pool=packet.getInt("poolPoints");
                        if(ApiV3RedPacketInputs.risky(hintedType)&&number(owner.opt(UserExt.USER_POINT))<pool)throw new ApiV3Exception(409,"insufficient_points","领取所需积分不足");
                        int amount=chosen.getInt("amount"),refund=0;
                        if(rps){
                            // Escrow belongs to the original account even when that account is inactive.
                            final JSONObject senderAccount=users.getForUpdate(sender);
                            if(senderAccount==null||!sender.equals(senderAccount.optString("oId")))
                                throw new ApiV3Exception(409,"sender_unavailable","发送者账号不存在，暂不能结算");
                            final int gesture=input.getInt("gesture"),sourceGesture=packet.getInt("senderGesture");
                            final int delta=sourceGesture-gesture;
                            amount=sourceGesture==gesture?0:delta==1||delta==-2?pool:-pool;
                            refund=pool-amount;chosen.put("gesture",gesture).put("senderRefundPoints",refund);
                        }
                        final String sourceId=ApiV3TokenUtil.hash(messageId+"\0"+actor);
                        final var source=new PointtransferSource("","","chat.red-packets.claim",sourceId);
                        final String transfer=amount==0?"":amount>0?transfer(Pointtransfer.ID_C_SYS,actor,Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_RECEIVE_RED_PACKET,amount,messageId,time,source):
                                transfer(actor,Pointtransfer.ID_C_SYS,Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_SEND_RED_PACKET,-amount,messageId,time,source);
                        final String refundTransfer=refund==0?"":transfer(Pointtransfer.ID_C_SYS,sender,Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_RECEIVE_RED_PACKET,refund,messageId,time,source);
                        chosen.put("amount",amount).put("claimUserId",actor).put("claimedAt",time).put("transferId",transfer).put("senderTransferId",refundTransfer);
                        slots.update(chosen.getString("oId"),chosen,"amount","claimUserId","claimedAt","transferId","gesture","senderRefundPoints","senderTransferId");
                        packet.put("got",packet.getInt("got")+1);packets.update(messageId,packet,"got");
                        validateSlots(packet,current);
                        final JSONObject envelope=new JSONObject(message.getString("content"));final JSONObject card=new JSONObject(envelope.getString("content"));
                        final JSONArray who=publicClaims(current,true);card.put("got",packet.getInt("got")).put("who",who);
                        if(rps)card.put("senderGesture",packet.getInt("senderGesture"));envelope.put("content",card.toString());
                        chat.update(messageId,new JSONObject().put("content",envelope.toString()),"content");
                        final var unread=notifications.select("SELECT * FROM `"+notifications.getName()+"` WHERE userId=? AND dataId=? AND dataType=? AND hasRead=? LIMIT 2 FOR UPDATE",actor,messageId,Notification.DATA_TYPE_C_RED_PACKET,false);
                        if(unread.size()>1)throw unavailable();for(JSONObject row:unread){row.put("hasRead",true);notifications.update(row.getString("oId"),row,"hasRead");}
                        updated.set(new JSONObject().put("type","redPacketStatus").put("whoGive",envelope.optString("userName")).put("whoGot",owner.getString("userName"))
                                .put("got",packet.getInt("got")).put("count",packet.getInt("count")).put("oId",messageId));
                        return claimReceipt(messageId,chosen,packet);
                    }));
            }
        });
        final var receipt=execution.result();final JSONObject result=claimResult(receipt);final int amount=result.getInt("amount");
        final String sourceId=ApiV3TokenUtil.hash(messageId+"\0"+actor);
        if(!receipt.getString("transferId").isEmpty())index(amount>0?Pointtransfer.ID_C_SYS:actor,amount>0?actor:Pointtransfer.ID_C_SYS,receipt.getString("transferId"),"chat.red-packets.claim",sourceId);
        if(rps&&!receipt.getString("senderTransferId").isEmpty()){
            if(!sender.equals(receipt.optString("senderId")))throw unavailable();
            index(Pointtransfer.ID_C_SYS,sender,receipt.getString("senderTransferId"),"chat.red-packets.claim",sourceId);
        }
        if(execution.applied()&&updated.get()!=null){safely(()->ChatroomChannel.notifyChat(updated.get()));safely(()->notificationDelivery.publishRefreshNotification(actor));}
        return result;
    }

    /** Legacy bridge keeps the established response field names; assignments stay sender-only. */
    public JSONObject legacyOpen(JSONObject viewer,String messageId)throws Exception {
        return legacyOpen(viewer,messageId,new JSONObject());
    }
    public JSONObject legacyOpen(JSONObject viewer,String messageId,JSONObject body)throws Exception {
        final String actor=actor(viewer);identity(messageId);
        if(body.has("expectedUserId")&&!actor.equals(body.opt("expectedUserId")))throw new ApiV3Exception(409,"actor_changed","当前账号已变化，请刷新页面");
        if(body.has("viewOnly")&&!(body.opt("viewOnly") instanceof Boolean))throw ApiV3WriteInputs.invalid();
        final boolean viewOnly=body.optBoolean("viewOnly",false);
        final JSONObject legacyPacket=packets.current(messageId,false);
        if(legacyPacket==null)throw ApiV3Requests.notFound();
        final JSONObject proof=new JSONObject();
        if(ApiV3RedPacketInputs.risky(legacyPacket.optString("type"))&&!viewOnly){
            if(!Boolean.TRUE.equals(body.opt("riskAccepted")))throw new ApiV3Exception(422,"risk_confirmation_required","请明确确认可能扣除积分的风险");
            proof.put("riskAccepted",true);
            if(legacyPacket.optString("type").equals("rockPaperScissors"))proof.put("gesture",body.opt("gesture"));
        }
        try{if(!viewOnly)claim(viewer,messageId,proof,"legacy."+ApiV3TokenUtil.hash(actor+"\0"+messageId));}
        catch(ApiV3Exception e){if(!Set.of("red_packet_empty","recipient_required").contains(e.code))throw e;}
        final JSONObject data=detail(viewer,messageId);final JSONObject sender=data.getJSONObject("sender");
        final JSONObject info=new JSONObject().put("userName",sender.optString("userName")).put("userAvatarURL",sender.optString("userAvatarURL"))
                .put("userAvatarURL48",sender.optString("userAvatarURL"))
                .put("count",data.getInt("count")).put("got",data.getInt("got")).put("msg",data.getString("message"));
        if(data.has("senderGesture"))info.put("gesture",data.getInt("senderGesture"));
        final JSONArray who=new JSONArray();for(Object value:data.getJSONArray("claims")){final JSONObject item=(JSONObject)value,u=item.getJSONObject("user");who.put(new JSONObject().put("userId",u.getString("id")).put("userName",u.optString("userName"))
                .put("avatar",u.optString("userAvatarURL")).put("userMoney",item.getInt("amount")).put("time",format(item.getLong("claimedAt"))));}
        final JSONArray recipients=new JSONArray();if(data.has("recipientIds"))for(Object id:data.getJSONArray("recipientIds")){JSONObject user=publicUser(id.toString());recipients.put(user.optString("userName"));}
        return new JSONObject().put("who",who).put("info",info).put("recivers",recipients);
    }
    private ApiV3MutationStore guarded(String actor,Collection<String> ids){return new ApiV3MutationStore(){
        public Transaction beginTransaction(){return ApiV3Transactions.strict(requests.beginTransaction());}
        public boolean hasTransactionBegun(){return requests.hasTransactionBegun();}
        public JSONObject getForUpdate(String id)throws RepositoryException {for(String user:new TreeSet<>(ids))users.getForUpdate(user);requireActor(actor,false);return requests.getForUpdate(id);}
        public String add(JSONObject row)throws RepositoryException{return requests.add(row);}
        public void update(String id,JSONObject row,String...fields)throws RepositoryException{requests.update(id,row,fields);}
    };}
    private JSONObject requireActor(String actor,boolean phone)throws RepositoryException {
        final JSONObject owner=users.getForUpdate(actor);
        if(owner==null||!actor.equals(owner.optString("oId"))||number(owner.opt(UserExt.USER_STATUS))!=UserExt.USER_STATUS_C_VALID)throw ApiV3Requests.notFound();
        if(phone&&owner.optString("userPhone").isBlank())throw new ApiV3Exception(403,"phone_required","请先绑定手机号码");return owner;
    }
    private JSONObject message(String id)throws RepositoryException {
        final var rows=chat.select("SELECT oId,content FROM `"+chat.getName()+"` WHERE oId=? FOR UPDATE",id);
        if(rows.size()!=1)throw ApiV3Requests.notFound();return rows.getFirst();
    }
    private JSONObject packet(String id,JSONObject message)throws RepositoryException {
        final JSONObject packet=packets.current(id,true);if(packet==null)throw ApiV3Requests.notFound();
        try{
            final JSONObject envelope=new JSONObject(message.getString("content")),card=new JSONObject(envelope.getString("content"));
            if(!ChatRedPacket.MARKER.equals(card.optString("settlement"))||!"redPacket".equals(card.optString("msgType"))
                    ||!packet.getString("senderId").equals(envelope.optString("userOId"))||!packet.getString("type").equals(card.optString("type")))throw unavailable();
        }catch(ApiV3Exception e){throw e;}catch(RuntimeException e){throw unavailable();}return packet;
    }
    private void validateSlots(JSONObject packet,List<JSONObject> rows){
        final int count=exact(packet,"count"),got=exact(packet,"got");final String type=packet.optString("type");
        final boolean random=type.equals("random"),heartbeat=type.equals("heartbeat"),rps=type.equals("rockPaperScissors"),totalPrice=random||heartbeat||rps;
        if(count<1||count>100||rows.size()!=count||got<0||got>count||!Set.of("random","average","specify","heartbeat","rockPaperScissors").contains(type)
                ||heartbeat&&count<5||rps&&count!=1)throw unavailable();
        final int money=exact(packet,"money"),pool=exact(packet,"poolPoints"),principal=exact(packet,"principalPoints"),tax=exact(packet,"taxPoints");
        final int net=money*(type.equals("specify")?99:rps?85:97)/100,multiplier=totalPrice?1:count;
        if(money<(rps?256:32)||money>10240||principal!=money*multiplier||pool<1||pool>principal||tax!=principal-pool
                ||pool!=principal&&pool!=net*multiplier||!totalPrice&&pool%count!=0)throw unavailable();
        if(rps){if(exact(packet,"senderGesture")<0||exact(packet,"senderGesture")>2)throw unavailable();}
        else if(packet.optInt("senderGesture",-1)!=-1)throw unavailable();
        final int extra=heartbeat?pool/count+10:0;int total=0,claimed=0,positive=0;final var actors=new HashSet<String>();final var recipients=new HashSet<String>();
        for(int i=0;i<rows.size();i++){
            final JSONObject row=rows.get(i);final int amount=exact(row,"amount");final boolean taken=!row.optString("claimUserId").isEmpty();
            final int refund=row.has("senderRefundPoints")?exact(row,"senderRefundPoints"):0,gesture=row.has("gesture")?exact(row,"gesture"):-1;
            if(!packet.getString("oId").equals(row.optString("messageId"))||exact(row,"ordinal")!=i)throw unavailable();
            if(heartbeat){if(amount>0){if(amount!=pool+extra)throw unavailable();positive++;}else if(amount < -extra)throw unavailable();}
            else if(rps){
                if(!taken){if(amount!=0||gesture!=-1||refund!=0)throw unavailable();}
                else {final int delta=packet.getInt("senderGesture")-gesture;final int expected=delta==0?0:delta==1||delta==-2?pool:-pool;
                    if(gesture<0||gesture>2||amount!=expected||refund!=pool-amount||row.optString("claimUserId").equals(packet.optString("senderId")))throw unavailable();}
            }else if(amount<0||amount>10240||!random&&amount!=pool/count)throw unavailable();
            total=Math.addExact(total,amount);
            if(!rps&&(gesture!=-1||refund!=0||!row.optString("senderTransferId").isEmpty()))throw unavailable();
            if(taken){if(!id(row.optString("claimUserId"))||!actors.add(row.getString("claimUserId"))||number(row.opt("claimedAt"))<=0
                    ||(amount==0?!row.optString("transferId").isEmpty():!id(row.optString("transferId"))))throw unavailable();claimed++;}
            else if(number(row.opt("claimedAt"))!=0||!row.optString("transferId").isEmpty())throw unavailable();
            if(refund==0?!row.optString("senderTransferId").isEmpty():!id(row.optString("senderTransferId")))throw unavailable();
            if(type.equals("specify")){if(!id(row.optString("recipientId"))||!recipients.add(row.getString("recipientId"))||row.getString("recipientId").equals(packet.getString("senderId"))
                    ||taken&&!row.getString("recipientId").equals(row.getString("claimUserId")))throw unavailable();}
            else if(!row.optString("recipientId").isEmpty())throw unavailable();
        }
        if(claimed!=got||heartbeat&&positive!=1||!rps&&total!=pool||rps&&got==1&&total+rows.getFirst().getInt("senderRefundPoints")!=pool)throw unavailable();
    }
    private JSONObject detailInCurrentTransaction(JSONObject owner,JSONObject packet,List<JSONObject> rows)throws RepositoryException {
        final JSONObject result=new JSONObject().put("id",packet.getString("oId")).put("type",packet.getString("type")).put("message",packet.getString("message"))
                .put("money",packet.getInt("money")).put("count",packet.getInt("count")).put("got",packet.getInt("got"))
                .put("principalPoints",packet.getInt("principalPoints")).put("poolPoints",packet.getInt("poolPoints")).put("taxPoints",packet.getInt("taxPoints"))
                .put("createdAt",packet.getLong("createdAt")).put("sender",publicUser(packet.getString("senderId"))).put("claims",publicClaims(rows,false)).put("currentUserClaim",JSONObject.NULL);
        for(JSONObject row:rows)if(owner.getString("oId").equals(row.optString("claimUserId")))result.put("currentUserClaim",claimProjection(row));
        if(packet.getString("type").equals("rockPaperScissors")&&(owner.getString("oId").equals(packet.getString("senderId"))||packet.getInt("got")==packet.getInt("count")))result.put("senderGesture",packet.getInt("senderGesture"));
        if(owner.getString("oId").equals(packet.getString("senderId"))&&packet.getString("type").equals("specify")){var ids=new JSONArray();for(JSONObject row:rows)ids.put(row.getString("recipientId"));result.put("recipientIds",ids);}
        return result;
    }
    private JSONArray publicClaims(List<JSONObject> rows,boolean legacy)throws RepositoryException {
        final JSONArray result=new JSONArray();final var claimed=new ArrayList<JSONObject>();for(JSONObject row:rows)if(!row.optString("claimUserId").isEmpty())claimed.add(row);
        claimed.sort(Comparator.comparingLong((JSONObject row)->row.getLong("claimedAt")).thenComparingInt(row->row.getInt("ordinal")));
        for(JSONObject row:claimed){final JSONObject user=publicUser(row.getString("claimUserId"));result.put(legacy?
                new JSONObject().put("userId",user.getString("id")).put("userName",user.optString("userName")).put("avatar",user.optString("userAvatarURL")).put("userMoney",row.getInt("amount")).put("time",format(row.getLong("claimedAt"))):
                claimProjection(row).put("user",user));}return result;
    }
    private JSONObject publicUser(String id)throws RepositoryException {
        final var rows=users.select("SELECT oId,userName,userNickname,userAvatarURL FROM `"+users.getName()+"` WHERE oId=?",id);
        final JSONObject row=rows.size()==1?rows.getFirst():new JSONObject();
        return new JSONObject().put("id",id).put("userName",row.optString("userName")).put("userNickname",row.optString("userNickname"))
                .put("userAvatarURL",ApiV3CommunityDto.safeUrl(row.optString("userAvatarURL")));
    }
    private boolean premium(String actor,long time)throws RepositoryException {
        final var rows=memberships.select("SELECT oId,userId,lvCode,state,expiresAt FROM `"+memberships.getName()+"` WHERE userId=? ORDER BY oId LIMIT 2 FOR UPDATE",actor);
        if(rows.size()>1)throw unavailable();if(rows.isEmpty())return false;final JSONObject row=rows.getFirst();
        if(!actor.equals(row.optString("userId"))||number(row.opt("expiresAt"))<0)throw unavailable();
        return number(row.opt("state"))==1&&(number(row.opt("expiresAt"))==0||number(row.opt("expiresAt"))>time)&&Set.of("VIP4_YEAR","VIP4_MONTH").contains(row.optString("lvCode"));
    }
    private String feeRecipient()throws RepositoryException {
        final var rows=users.select("SELECT oId,userName FROM `"+users.getName()+"` WHERE userName=? ORDER BY oId LIMIT 2","admin");
        if(rows.size()!=1||!"admin".equals(rows.getFirst().optString("userName"))||!id(rows.getFirst().optString("oId")))throw unavailable();return rows.getFirst().getString("oId");
    }
    private JSONObject receiptHint(String actor,String id,String operation)throws RepositoryException {
        final var rows=requests.select("SELECT responseJson FROM `"+requests.getName()+"` WHERE oId=? AND userId=? AND operation=? LIMIT 2",id,actor,operation);
        if(rows.size()>1)throw unavailable();if(rows.isEmpty())return null;try{final JSONObject row=new JSONObject(rows.getFirst().getString("responseJson"));creationResult(row);return row;}catch(RuntimeException e){throw unavailable();}
    }
    private String transfer(String from,String to,int type,int amount,String messageId,long time,PointtransferSource source){
        final String result=points.transferInCurrentTransaction(from,to,type,amount,messageId,time,"",source,false);if(!id(result))throw unavailable();return result;
    }
    private void cacheActor(String actor){ArticlePublicationCaches.afterTransaction("packet-user:"+actor,()->{try{users.refreshSettingsCache(actor);}catch(Exception e){users.evictSettingsCaches(actor);}});}
    private void index(String from,String to,String id,String operation,String requestId){
        Transaction root=null;
        try{
            if(transfers.hasTransactionBegun())throw new IllegalStateException("committed packet ledger required");
            root=ApiV3Transactions.strict(transfers.beginTransaction());
            final var parent=transfers.select("SELECT oId FROM `"+transfers.getName()+"` WHERE oId=? AND fromId=? AND toId=? AND sourceScene=? AND sourceRequestId=? LIMIT 2 FOR UPDATE",id,from,to,operation,requestId);
            if(parent.size()!=1||!id.equals(parent.getFirst().optString("oId")))throw unavailable();
            for(String owner:List.of(from,to)){
                final var found=transferIndex.select("SELECT oId FROM `"+transferIndex.getName()+"` WHERE userId=? AND rawId=? LIMIT 2 FOR UPDATE",owner,id);
                if(found.isEmpty()&&ApiV3TransactionStatements.execute("INSERT INTO `"+transferIndex.getName()+"` (oId,userId,rawId) VALUES(?,?,?)",org.b3log.latke.util.Ids.genTimeMillisId(),owner,id)!=1)throw unavailable();
            }
            root.commit();
        }catch(Exception e){if(root!=null&&root.isActive())try{root.rollback();}catch(RuntimeException ignored){ }org.apache.logging.log4j.LogManager.getLogger(getClass()).warn("Committed packet ledger indexing delayed");}
    }
    private static JSONObject creationResult(JSONObject receipt){
        if(!receipt.keySet().equals(Set.of("result","feeRecipientId","transferId","taxTransferId"))||!id(receipt.optString("feeRecipientId"))||!id(receipt.optString("transferId"))
                ||!receipt.optString("taxTransferId").isEmpty()&&!id(receipt.optString("taxTransferId")))throw unavailable();
        final JSONObject result=receipt.optJSONObject("result");if(result==null||!result.keySet().equals(Set.of("id","createdAt","principalPoints","poolPoints","taxPoints"))||!id(result.optString("id")))throw unavailable();
        final long principal=number(result.opt("principalPoints")),pool=number(result.opt("poolPoints")),tax=number(result.opt("taxPoints"));
        if(number(result.opt("createdAt"))<=0||principal<32||principal>1024000||pool<1||pool>principal||tax<0||tax>principal||principal!=pool+tax||tax==0!=receipt.getString("taxTransferId").isEmpty())throw unavailable();
        return new JSONObject(result.toString());
    }
    private static JSONObject claimProjection(JSONObject row){
        final JSONObject result=new JSONObject().put("amount",row.getInt("amount")).put("claimedAt",row.getLong("claimedAt"));
        if(row.optInt("gesture",-1)>=0)result.put("gesture",row.getInt("gesture")).put("outcome",row.getInt("amount")==0?"tie":row.getInt("amount")>0?"win":"lose").put("senderRefundPoints",row.getInt("senderRefundPoints"));
        return result;
    }
    private static JSONObject claimReceipt(String messageId,JSONObject row,JSONObject packet){
        final JSONObject receipt=new JSONObject().put("result",claimProjection(row).put("id",messageId)).put("transferId",row.getString("transferId"));
        if(packet.getString("type").equals("rockPaperScissors"))receipt.put("senderId",packet.getString("senderId")).put("senderTransferId",row.getString("senderTransferId"));
        return receipt;
    }
    private static JSONObject claimResult(JSONObject receipt){
        final JSONObject result=receipt.optJSONObject("result");final boolean rps=receipt.has("senderId");
        if(!receipt.keySet().equals(rps?Set.of("result","transferId","senderId","senderTransferId"):Set.of("result","transferId"))||result==null
                ||!result.keySet().equals(rps?Set.of("id","amount","claimedAt","gesture","outcome","senderRefundPoints"):Set.of("id","amount","claimedAt"))
                ||!id(result.optString("id"))||!receipt.optString("transferId").isEmpty()&&!id(receipt.optString("transferId")))throw unavailable();
        final long amount=number(result.opt("amount"));if(amount < -10240||amount>12300||number(result.opt("claimedAt"))<=0||amount==0!=receipt.getString("transferId").isEmpty())throw unavailable();
        if(rps){final int gesture=exact(result,"gesture"),refund=exact(result,"senderRefundPoints");
            final long pool=amount+refund;
            if(!id(receipt.optString("senderId"))||gesture<0||gesture>2||refund<0||refund>20480||pool<217||pool>10240||(amount!=0&&amount!=pool&&amount!=-pool)||amount>10240
                    ||!result.optString("outcome").equals(amount==0?"tie":amount>0?"win":"lose")
                    ||(refund==0?!receipt.optString("senderTransferId").isEmpty():!id(receipt.optString("senderTransferId"))))throw unavailable();}
        return new JSONObject(result.toString());
    }
    private static String actor(JSONObject viewer){final String id=viewer==null?"":viewer.optString("oId");if(!id(id))throw new ApiV3Exception(401,"unauthorized","登录凭据无效");return id;}
    private static String requestId(String actor,String operation,String key)throws Exception{if(key==null||!key.matches("[A-Za-z0-9._:-]{1,128}"))throw ApiV3WriteInputs.invalid();return ApiV3TokenUtil.hash("request-key\0"+actor+"\0"+operation+"\0"+key);}
    private static boolean id(String id){return id!=null&&id.matches("[1-9][0-9]{0,18}");}
    private static void identity(String id){if(!id(id))throw ApiV3WriteInputs.invalid();}
    private static long number(Object value){try{if(!(value instanceof Number number))throw unavailable();return new BigDecimal(number.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException e){throw unavailable();}}
    private static int exact(JSONObject row,String key){return Math.toIntExact(number(row.opt(key)));}
    private static boolean operator(JSONObject owner){return DataModelService.hasPermission(owner.optString(User.USER_ROLE),3);}
    private static String format(long time){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time);}
    private static ApiV3Exception unavailable(){return new ApiV3Exception(503,"temporarily_unavailable","红包暂不可用");}
    @FunctionalInterface private interface Effect{void run()throws Exception;}
    private static void safely(Effect effect){try{effect.run();}catch(Exception e){org.apache.logging.log4j.LogManager.getLogger(ApiV3ChatRedPacketService.class).warn("Committed red-packet delivery delayed");}}
}
