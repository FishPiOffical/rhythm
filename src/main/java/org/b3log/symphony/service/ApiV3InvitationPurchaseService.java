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
import org.b3log.symphony.model.Invitecode;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.InvitecodeRepository;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.repository.PointtransferRepository;
import org.b3log.symphony.repository.PointtransferRedcRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3InvitationPurchaseInputs;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

/** 扣费、新生成的邀请码和仅本人可读的回执一并提交。 */
@Service
public class ApiV3InvitationPurchaseService {
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private InvitecodeRepository invitations;
    @Inject private ApiV3RequestRepository requests;
    @Inject private PointtransferMgmtService points;
    @Inject private PointtransferRepository transfers;
    @Inject private PointtransferRedcRepository transferIndex;
    private static final String OPERATION="invitation.purchase";
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM=new SecureRandom();
    protected String newCode() {
        final var value=new StringBuilder(16);
        for(int i=0;i<16;i++) value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return value.toString();
    }
    protected String newId() { return Ids.genTimeMillisId(); }
    public JSONObject purchase(final String actor,final JSONObject body,final String key)throws Exception {
        if(actor==null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
        final var normalized=ApiV3InvitationPurchaseInputs.parse(body);
        final String billingId=ApiV3TokenUtil.hash(OPERATION+"\0"+actor+"\0"+key);
        try {
            final JSONObject durable=points.executeWithTransferLocks(actor,List.of(Pointtransfer.ID_C_SYS),()-> {
                try(final var caches=ArticlePublicationCaches.open(requests::hasTransactionBegun)) {
                    final ApiV3MutationStore store=new ApiV3MutationStore() {
                        public Transaction beginTransaction(){return ApiV3Transactions.strict(requests.beginTransaction());}
                        public boolean hasTransactionBegun(){return requests.hasTransactionBegun();}
                        public JSONObject getForUpdate(String id)throws RepositoryException {
                            // 沿用注册与重置的锁顺序：命名空间、注册选项、当前账号。
                            if(options.getForUpdate(Option.ID_C_STATISTIC_MEMBER_COUNT)==null) throw unavailable();
                            options.getForUpdate(Option.ID_C_MISC_ALLOW_REGISTER);
                            requireActor(actor);
                            return requests.getForUpdate(id);
                        }
                        public String add(JSONObject value)throws RepositoryException{return requests.add(value);}
                        public void update(String id,JSONObject value,String...fields)throws RepositoryException{requests.update(id,value,fields);}
                    };
                    return ApiV3IdempotencyMgmtService.runAtomic(store,actor,OPERATION,key,normalized,
                            ()->purchaseInCurrentTransaction(actor,billingId));
                }
            });
            final JSONObject result=publicResult(durable);
            index(actor,billingId,durable.getString("indexTransferId"));
            return result;
        }catch(ApiV3Exception e){throw e;}
        catch(Exception e){throw unavailable();}
    }
    private JSONObject requireActor(final String actor)throws RepositoryException {
        final var rows=users.select("SELECT oId,userStatus,userPoint FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",actor);
        if(rows.size()!=1 || !actor.equals(rows.getFirst().optString("oId")) || number(rows.getFirst().opt("userStatus"))!=UserExt.USER_STATUS_C_VALID) throw ApiV3Requests.notFound();
        final long balance=number(rows.getFirst().opt("userPoint"));
        if(balance<Integer.MIN_VALUE || balance>Integer.MAX_VALUE) throw unavailable();
        return rows.getFirst();
    }
    private JSONObject purchaseInCurrentTransaction(final String actor,final String billingId)throws Exception {
        if(!requests.hasTransactionBegun()) throw new IllegalStateException("invitation purchase transaction required");
        return ApiV3TransactionSavepoint.run(()-> {
            final var policy=options.getForUpdate(Option.ID_C_MISC_ALLOW_REGISTER);
            if(policy==null || !(policy.opt(Option.OPTION_VALUE) instanceof String mode)
                    || !Set.of("0","1","2").contains(mode)) throw unavailable();
            if(!"2".equals(mode)) throw new ApiV3Exception(409,"invitation_purchase_unavailable","当前不能购买邀请码");
            final int paid=Pointtransfer.TRANSFER_SUM_C_BUY_INVITECODE;
            if(paid<=0 || Symphonys.INVITECODE_EXPIRED<=0) throw unavailable();
            if(number(requireActor(actor).opt("userPoint"))<paid) throw new ApiV3Exception(409,"insufficient_points","积分不足");
            final String code=newCode();
            if(code==null || !code.matches("[A-Za-z0-9]{16}")) throw unavailable();
            // 数据库没有全局邀请码唯一约束，遇到与旧码重复的候选时应另选。
            if(!invitations.select("SELECT oId FROM `"+invitations.getName()+"` WHERE code=? LIMIT 1 FOR UPDATE",code).isEmpty()) throw unavailable();
            final String id=newId();
            if(id==null || !id.matches("[1-9][0-9]{0,18}")) throw unavailable();
            final long created=Long.parseLong(id),expires=Math.addExact(created,Symphonys.INVITECODE_EXPIRED);
            final String transferId=points.transferInCurrentTransaction(actor,Pointtransfer.ID_C_SYS,
                    Pointtransfer.TRANSFER_TYPE_C_BUY_INVITECODE,paid,"",created,"购买邀请码",
                    new PointtransferSource("","",OPERATION,billingId),false);
            if(transferId==null || !transferId.matches("[1-9][0-9]{0,18}")) throw unavailable();
            if(ApiV3TransactionStatements.execute("INSERT INTO `"+invitations.getName()+"` (oId,code,generatorId,userId,useTime,status,memo) VALUES (?,?,?,?,?,?,?)",
                    id,code,actor,"",0,Invitecode.STATUS_C_UNUSED,"User ["+actor+"] generated")!=1) throw unavailable();
            final JSONObject durable=new JSONObject().put("result",new JSONObject().put("code",code).put("expiresAt",expires).put("paidPoints",paid))
                    .put("indexTransferId",transferId);
            publicResult(durable);
            return durable;
        });
    }
    private void index(final String actor,final String billingId,final String transferId) {
        Transaction transaction=null;
        try {
            if(transfers.hasTransactionBegun()) throw new IllegalStateException("committed invitation transfer indexing required");
            transaction=ApiV3Transactions.strict(transfers.beginTransaction());
            final var rows=transfers.select("SELECT oId FROM `"+transfers.getName()+"` WHERE oId=? AND fromId=? AND toId=? AND type=? AND sourceScene=? AND sourceRequestId=? LIMIT 2 FOR UPDATE",
                    transferId,actor,Pointtransfer.ID_C_SYS,Pointtransfer.TRANSFER_TYPE_C_BUY_INVITECODE,OPERATION,billingId);
            if(rows.size()!=1 || !transferId.equals(rows.getFirst().optString("oId"))) throw unavailable();
            for(final String owner:List.of(actor,Pointtransfer.ID_C_SYS)) {
                final var existing=transferIndex.select("SELECT oId FROM `"+transferIndex.getName()+"` WHERE userId=? AND rawId=? LIMIT 2 FOR UPDATE",owner,transferId);
                if(existing.isEmpty() && ApiV3TransactionStatements.execute("INSERT INTO `"+transferIndex.getName()+"` (oId,userId,rawId) VALUES (?,?,?)",Ids.genTimeMillisId(),owner,transferId)!=1) throw unavailable();
            }
            transaction.commit();
        }catch(Exception e) {
            if(transaction!=null && transaction.isActive()) try{transaction.rollback();}catch(RuntimeException ignored){ }
            LogManager.getLogger(getClass()).warn("Invitation purchase transfer indexing delayed");
        }
    }
    private static long number(final Object value) {
        if(!(value instanceof Number number)) throw unavailable();
        try{return new BigDecimal(number.toString()).longValueExact();}
        catch(ArithmeticException|NumberFormatException e){throw unavailable();}
    }
    private static JSONObject publicResult(final JSONObject durable) {
        if(!durable.keySet().equals(Set.of("result","indexTransferId")) || !(durable.opt("result") instanceof JSONObject result)
                || !durable.optString("indexTransferId").matches("[1-9][0-9]{0,18}")
                || !result.keySet().equals(Set.of("code","expiresAt","paidPoints"))
                || !(result.opt("code") instanceof String code) || !code.matches("[A-Za-z0-9]{16}")) throw unavailable();
        final long expires=number(result.opt("expiresAt")),paid=number(result.opt("paidPoints"));
        if(expires<=0 || paid<=0 || paid>Integer.MAX_VALUE) throw unavailable();
        return new JSONObject().put("code",code).put("expiresAt",expires).put("paidPoints",paid);
    }
    public static ApiV3Exception unavailable(){return new ApiV3Exception(503,"temporarily_unavailable","邀请码购买暂不可用");}
}
