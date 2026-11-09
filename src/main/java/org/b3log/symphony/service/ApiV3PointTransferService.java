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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.MembershipRepository;
import org.b3log.symphony.repository.NotificationRepository;
import org.b3log.symphony.repository.PointtransferRepository;
import org.b3log.symphony.repository.PointtransferRedcRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3PointTransferInputs;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 转账主账、手续费、收款方通知和本人回执在同一个严格根事务中提交。 */
@Service
public class ApiV3PointTransferService {
    @Inject private UserRepository users;
    @Inject private MembershipRepository memberships;
    @Inject private ApiV3RequestRepository requests;
    @Inject private PointtransferMgmtService points;
    @Inject private PointtransferRepository transfers;
    @Inject private PointtransferRedcRepository transferIndex;
    @Inject private NotificationRepository notifications;
    @Inject private NotificationMgmtService notificationService;
    private static final String OPERATION="points.transfer";
    protected long now(){return System.currentTimeMillis();}
    private record Targets(String receiverId,String receiverName,String feeId) { }
    public JSONObject transfer(final String actor,final JSONObject body,final String key)throws Exception {
        if(!id(actor)) throw ApiV3Requests.invalid();
        final var input=ApiV3PointTransferInputs.parse(body);
        if(key==null || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw ApiV3Requests.invalid();
        final String requestId=ApiV3TokenUtil.hash("request-key\0"+actor+"\0"+OPERATION+"\0"+key);
        final String billingId=ApiV3TokenUtil.hash(OPERATION+"\0"+actor+"\0"+key);
        try {
            // 持久回执只用来确定加锁对象，根事务仍须复核幂等键、请求体和当前用户。
            // 收款人改名或停用后仍能回放已完成的回执，避免重新执行该笔转账。
            final JSONObject hint=receiptHint(actor,requestId);
            final Targets targets=hint==null?resolve(input.userName()):targets(hint);
            final var destinations=new ArrayList<String>();destinations.add(targets.receiverId());destinations.add(targets.feeId());
            final var execution=points.executeWithTransferLocks(actor,destinations,()-> {
                try(final var cache=ArticlePublicationCaches.open(requests::hasTransactionBegun)) {
                    final ApiV3MutationStore store=new ApiV3MutationStore() {
                        public Transaction beginTransaction(){return ApiV3Transactions.strict(requests.beginTransaction());}
                        public boolean hasTransactionBegun(){return requests.hasTransactionBegun();}
                        public JSONObject getForUpdate(String id)throws RepositoryException {
                            lockAccounts(actor,targets);
                            return requests.getForUpdate(id);
                        }
                        public String add(JSONObject row)throws RepositoryException{return requests.add(row);}
                        public void update(String id,JSONObject row,String...fields)throws RepositoryException{requests.update(id,row,fields);}
                    };
                    return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(store,actor,OPERATION,key,input.normalized(),
                            ()->apply(actor,input,targets,billingId));
                }
            });
            final var durable=execution.result();
            final JSONObject result=publicResult(durable);
            index(actor,billingId,durable);
            if(execution.applied()) try {notificationService.publishRefreshNotification(targets.receiverId());}
            catch(RuntimeException e){LogManager.getLogger(getClass()).warn("Point transfer notification refresh delayed");}
            return result;
        }catch(ApiV3Exception e){throw e;}
        catch(Exception e){throw unavailable();}
    }
    private JSONObject receiptHint(final String actor,final String requestId)throws RepositoryException {
        final var rows=requests.select("SELECT responseJson FROM `"+requests.getName()+"` WHERE oId=? AND userId=? AND operation=? LIMIT 2",requestId,actor,OPERATION);
        if(rows.size()>1) throw unavailable();
        if(rows.isEmpty())return null;
        final Object value=rows.getFirst().opt("responseJson");
        if(!(value instanceof String raw) || raw.length()>65536)throw unavailable();
        final JSONObject durable=new JSONObject(raw);publicResult(durable);return durable;
    }
    private Targets resolve(final String receiverName)throws RepositoryException {
        final JSONObject receiver=byName(receiverName,false);
        final JSONObject fee=byName("admin",true);
        return new Targets(receiver.getString("oId"),receiver.getString("userName"),fee.getString("oId"));
    }
    private JSONObject byName(final String name,final boolean administrator)throws RepositoryException {
        final var rows=users.select("SELECT oId,userName FROM `"+users.getName()+"` WHERE userName=? ORDER BY oId LIMIT 2",name);
        if(rows.isEmpty()){if(administrator)throw unavailable();throw ApiV3Requests.notFound();}
        if(rows.size()!=1)throw unavailable();
        final var row=rows.getFirst();
        if(!id(row.optString("oId")) || !(row.opt("userName") instanceof String canonical) || canonical.isBlank() || canonical.length()>64)throw unavailable();
        if(administrator && !"admin".equals(canonical))throw unavailable();
        return row;
    }
    private HashMap<String,JSONObject> lockAccounts(final String actor,final Targets targets)throws RepositoryException {
        final var locked=new HashMap<String,JSONObject>();
        final var ids=new TreeSet<String>(List.of(actor,targets.receiverId(),targets.feeId()));
        for(final String id:ids) {
            final var rows=users.select("SELECT oId,userName,userStatus,userPoint,userUsedPoint FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",id);
            if(rows.size()>1)throw unavailable();
            if(!rows.isEmpty()) {
                final var row=rows.getFirst();if(!id.equals(row.optString("oId")))throw unavailable();locked.put(id,row);
            }
        }
        validAccount(locked.get(actor));return locked;
    }
    private JSONObject apply(final String actor,final ApiV3PointTransferInputs.Transfer input,final Targets targets,final String billingId)throws Exception {
        if(!requests.hasTransactionBegun())throw new IllegalStateException("point transfer transaction required");
        return ApiV3TransactionSavepoint.run(()-> {
            final var locked=lockAccounts(actor,targets);
            final JSONObject owner=locked.get(actor),receiver=locked.get(targets.receiverId()),feeRecipient=locked.get(targets.feeId());
            validAccount(receiver);
            if(!targets.receiverName().equals(receiver.optString("userName")))throw new ApiV3Exception(409,"recipient_changed","收款账号已变更，请重试");
            if(actor.equals(targets.receiverId()) || UserExt.COM_BOT_NAME.equals(receiver.optString("userName")))throw ApiV3Requests.invalid();
            final boolean premium=premium(actor);
            final boolean feeAccount=actor.equals(targets.feeId());
            if(feeAccount && !"admin".equals(owner.optString("userName")))throw new ApiV3Exception(409,"recipient_changed","收款账号已变更，请重试");
            final int fee=ApiV3PointTransferInputs.fee(input.amount(),feeAccount || premium);
            if(fee>0) {
                validAccount(feeRecipient);
                if(!"admin".equals(feeRecipient.optString("userName")))throw new ApiV3Exception(409,"recipient_changed","收款账号已变更，请重试");
            }
            final int received=Math.subtractExact(input.amount(),fee);
            if(number(owner.opt("userPoint"))<input.amount())throw new ApiV3Exception(409,"insufficient_points","积分不足");
            checkBalance(receiver,received);
            if(fee>0)checkBalance(feeRecipient,fee+(targets.receiverId().equals(targets.feeId())?(long)received:0L));
            final long time=now();
            final String main=points.transferInCurrentTransaction(actor,targets.receiverId(),Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT,
                    received,targets.receiverId(),time,input.memo(),new PointtransferSource("","",OPERATION,billingId),false);
            if(!id(main))throw unavailable();
            String feeTransfer="";
            if(fee>0) {
                feeTransfer=points.transferInCurrentTransaction(actor,targets.feeId(),Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT,
                        fee,actor,time,"转账手续费",new PointtransferSource("","",OPERATION,billingId),false);
                if(!id(feeTransfer))throw unavailable();
            }
            final String notification=notifications.add(new JSONObject().put(Notification.NOTIFICATION_USER_ID,targets.receiverId())
                    .put(Notification.NOTIFICATION_DATA_ID,main).put(Notification.NOTIFICATION_DATA_TYPE,Notification.DATA_TYPE_C_POINT_TRANSFER)
                    .put(Notification.NOTIFICATION_HAS_READ,false).put(Notification.NOTIFICATION_CONTENT,""));
            if(!id(notification))throw unavailable();
            final JSONObject result=new JSONObject().put("transferId",main).put("recipient",new JSONObject().put("userId",targets.receiverId()).put("userName",targets.receiverName()))
                    .put("amount",input.amount()).put("receivedPoints",received).put("feePoints",fee).put("memo",input.memo());
            final JSONObject durable=new JSONObject().put("result",result).put("feeRecipientId",targets.feeId()).put("feeTransferId",feeTransfer);
            publicResult(durable);return durable;
        });
    }
    private boolean premium(final String actor)throws RepositoryException {
        final var rows=memberships.select("SELECT oId,userId,lvCode,state,expiresAt FROM `"+memberships.getName()+"` WHERE userId=? ORDER BY oId LIMIT 2 FOR UPDATE",actor);
        if(rows.size()>1)throw unavailable();if(rows.isEmpty())return false;
        final var row=rows.getFirst();
        if(!actor.equals(row.optString("userId")) || !id(row.optString("oId")) || !(row.opt("lvCode") instanceof String code) || code.length()>32)throw unavailable();
        final long state=number(row.opt("state")),expires=number(row.opt("expiresAt"));
        if(state!=0 && state!=1 || expires<0)throw unavailable();
        return state==1 && (expires==0 || expires>now()) && Set.of("VIP4_YEAR","VIP4_MONTH").contains(code);
    }
    private void validAccount(final JSONObject row) {
        if(row==null || number(row.opt("userStatus"))!=UserExt.USER_STATUS_C_VALID)throw ApiV3Requests.notFound();
        final long point=number(row.opt("userPoint")),used=number(row.opt("userUsedPoint"));
        if(point<Integer.MIN_VALUE || point>Integer.MAX_VALUE || used<Integer.MIN_VALUE || used>Integer.MAX_VALUE
                || !(row.opt("userName") instanceof String name) || name.isBlank() || name.length()>64)throw unavailable();
    }
    private void checkBalance(final JSONObject row,final long increment) {
        if(number(row.opt("userPoint"))+increment>Integer.MAX_VALUE)throw new ApiV3Exception(409,"points_overflow","收款账号积分已达上限");
    }
    private static Targets targets(final JSONObject durable) {
        final var recipient=publicResult(durable).getJSONObject("recipient");
        return new Targets(recipient.getString("userId"),recipient.getString("userName"),durable.getString("feeRecipientId"));
    }
    private void index(final String actor,final String billingId,final JSONObject durable) {
        Transaction tx=null;
        try {
            if(transfers.hasTransactionBegun())throw new IllegalStateException("postcommit index required");
            final var result=publicResult(durable);final var targets=targets(durable);
            tx=ApiV3Transactions.strict(transfers.beginTransaction());
            repair(actor,targets.receiverId(),result.getString("transferId"),result.getInt("receivedPoints"),billingId);
            if(result.getInt("feePoints")>0)repair(actor,targets.feeId(),durable.getString("feeTransferId"),result.getInt("feePoints"),billingId);
            tx.commit();
        }catch(Exception e) {
            if(tx!=null && tx.isActive())try{tx.rollback();}catch(RuntimeException ignored){ }
            LogManager.getLogger(getClass()).warn("Point transfer indexing delayed");
        }
    }
    private void repair(final String actor,final String recipient,final String transferId,final int sum,final String billingId)throws RepositoryException {
        final var rows=transfers.select("SELECT oId FROM `"+transfers.getName()+"` WHERE oId=? AND fromId=? AND toId=? AND sum=? AND type=? AND sourceScene=? AND sourceRequestId=? LIMIT 2 FOR UPDATE",
                transferId,actor,recipient,sum,Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT,OPERATION,billingId);
        if(rows.size()!=1 || !transferId.equals(rows.getFirst().optString("oId")))throw unavailable();
        for(final String owner:List.of(actor,recipient)) {
            final var existing=transferIndex.select("SELECT oId FROM `"+transferIndex.getName()+"` WHERE userId=? AND rawId=? LIMIT 2 FOR UPDATE",owner,transferId);
            if(existing.isEmpty() && ApiV3TransactionStatements.execute("INSERT INTO `"+transferIndex.getName()+"` (oId,userId,rawId) VALUES (?,?,?)",Ids.genTimeMillisId(),owner,transferId)!=1)throw unavailable();
        }
    }
    private static JSONObject publicResult(final JSONObject durable) {
        if(!durable.keySet().equals(Set.of("result","feeRecipientId","feeTransferId")) || !id(durable.optString("feeRecipientId"))
                || !(durable.opt("result") instanceof JSONObject result) || !result.keySet().equals(Set.of("transferId","recipient","amount","receivedPoints","feePoints","memo"))
                || !id(result.optString("transferId")) || !(result.opt("recipient") instanceof JSONObject recipient)
                || !recipient.keySet().equals(Set.of("userId","userName")) || !id(recipient.optString("userId"))
                || !(recipient.opt("userName") instanceof String name) || name.isBlank() || name.length()>64
                || !(result.opt("memo") instanceof String memo) || memo.length()>255)throw unavailable();
        final long amount=number(result.opt("amount")),received=number(result.opt("receivedPoints")),fee=number(result.opt("feePoints"));
        if(amount<2 || amount>Integer.MAX_VALUE || received<1 || fee<0 || amount!=received+fee
                || fee>0 && !id(durable.optString("feeTransferId")) || fee==0 && !"".equals(durable.opt("feeTransferId")))throw unavailable();
        return new JSONObject().put("transferId",result.getString("transferId")).put("recipient",new JSONObject().put("userId",recipient.getString("userId")).put("userName",name))
                .put("amount",amount).put("receivedPoints",received).put("feePoints",fee).put("memo",memo);
    }
    private static boolean id(final String value){return value!=null && value.matches("[1-9][0-9]{0,18}");}
    private static long number(final Object value) {
        if(!(value instanceof Number number))throw unavailable();
        try{return new BigDecimal(number.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException e){throw unavailable();}
    }
    public static ApiV3Exception unavailable(){return new ApiV3Exception(503,"temporarily_unavailable","积分转账暂不可用");}
}
