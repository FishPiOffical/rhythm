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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** 沿用现有改名规则和费用，用户名、扣费及重试回执在同一个严格事务中提交。 */
@Service
public class ApiV3UserNameService {
    @Inject private UserRepository users;
    @Inject private UserNameMgmtService names;
    @Inject private ApiV3RequestRepository requests;
    @Inject private PointtransferMgmtService points;
    @Inject private PointtransferRepository transfers;
    @Inject private PointtransferRedcRepository index;
    private static final String OPERATION=UserNamePointData.SCENE;
    protected boolean allowed(String actor){return ProfileWriteQuota.access(actor);}
    protected void inspect(String name){if(CensorFactory.getTextCensor().censor(name).isBlocked())throw new ApiV3Exception(422,"invalid_user_name","用户名含违规信息");}
    public JSONObject rename(String actor,JSONObject body,String key)throws Exception {
        if(actor==null||!actor.matches("[1-9][0-9]{0,18}")||body==null||!body.keySet().equals(Set.of("userName"))
                ||!(body.opt("userName") instanceof String name)||UserNameRules.invalid(name))throw ApiV3WriteInputs.invalid();
        // 在取得数据库锁和账号锁之前执行内容审核，已提交回执的回放不依赖本次审核结果。
        ApiV3Exception rejected=null;
        try {
            if(!allowed(actor))throw new ApiV3Exception(429,"too_many_requests","操作频繁，请稍候重试");
            inspect(name);
        } catch (ApiV3Exception error) {
            rejected = error;
        } catch (Exception error) {
            rejected = unavailable();
        }
        final ApiV3Exception reviewFailure=rejected;
        final String billingId=ApiV3TokenUtil.hash(OPERATION+"\0"+actor+"\0"+key);
        final var renamed=new AtomicReference<UserNameMgmtService.Rename>();
        try {
            final JSONObject receipt=points.executeWithTransferLocks(actor,List.of(Pointtransfer.ID_C_SYS),()->{
                try(final var caches=ArticlePublicationCaches.open(requests::hasTransactionBegun)) {
                    final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                        public Transaction beginTransaction() {
                            return ApiV3Transactions.strict(requests.beginTransaction());
                        }

                        public boolean hasTransactionBegun() {
                            return requests.hasTransactionBegun();
                        }

                        public JSONObject getForUpdate(String id) throws RepositoryException {
                            names.lockNamespaceInCurrentTransaction();
                            requireActor(actor);
                            return requests.getForUpdate(id);
                        }

                        public String add(JSONObject value) throws RepositoryException {
                            return requests.add(value);
                        }

                        public void update(String id, JSONObject value, String... fields) throws RepositoryException {
                            requests.update(id, value, fields);
                        }
                    };
                    return ApiV3IdempotencyMgmtService.runAtomic(guarded,actor,OPERATION,key,new JSONObject().put("userName",name),()->
                            ApiV3TransactionSavepoint.run(()->{
                                if(reviewFailure!=null)throw reviewFailure;
                                JSONObject owner = requireActor(actor);
                                final int price = Pointtransfer.TRANSFER_SUM_C_CHANGE_USERNAME;
                                if(price<=0)throw unavailable();
                                if(number(owner.opt("userPoint"))<price)throw new ApiV3Exception(409,"insufficient_points","积分不足");
                                final UserNameMgmtService.Rename result;
                                try {
                                    result = names.renameInCurrentTransaction(actor, name);
                                } catch (UserNameMgmtService.Rejected error) {
                                    throw switch (error.code) {
                                        case "name_taken" -> new ApiV3Exception(409, "name_taken", "用户名已被使用");
                                        case "invalid_name" -> ApiV3WriteInputs.invalid();
                                        default -> ApiV3Requests.notFound();
                                    };
                                }
                                if(result.oldName()==null||result.oldName().length()>64)throw unavailable();
                                renamed.set(result);
                                final String transfer=points.transferInCurrentTransaction(actor,Pointtransfer.ID_C_SYS,
                                        Pointtransfer.TRANSFER_TYPE_C_CHANGE_USERNAME,price,name,System.currentTimeMillis(),result.oldName(),
                                        new PointtransferSource("","",OPERATION,billingId),false);
                                if(transfer==null||!transfer.matches("[1-9][0-9]{0,18}"))throw unavailable();
                                return new JSONObject().put("userName",name).put("paidPoints",price).put("indexTransferId",transfer);
                            }));
                }
            });
            final JSONObject result=project(receipt);
            // 改名事务结束后按原账目修复索引；回放同样可以补齐索引。
            repairIndex(actor, billingId, receipt.getString("indexTransferId"));
            return result;
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (!requests.hasTransactionBegun())
                names.afterTransaction(actor, renamed.get());
        }
    }
    private JSONObject requireActor(String actor)throws RepositoryException {
        final var owners=users.select("SELECT oId,userStatus,userPoint FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",actor);
        if(owners.size()!=1||!actor.equals(owners.getFirst().optString("oId"))||number(owners.getFirst().opt("userStatus"))!=0)throw ApiV3Requests.notFound();
        final long balance = number(owners.getFirst().opt("userPoint"));
        if (balance < Integer.MIN_VALUE || balance > Integer.MAX_VALUE)
            throw unavailable();
        return owners.getFirst();
    }
    private void repairIndex(String actor,String billingId,String transfer) {
        Transaction root=null;
        try {
            if(transfers.hasTransactionBegun())throw new IllegalStateException("committed name transfer required");
            root=ApiV3Transactions.strict(transfers.beginTransaction());
            final var rows=transfers.select("SELECT oId FROM `"+transfers.getName()+"` WHERE oId=? AND fromId=? AND toId=? AND type=? AND sourceScene=? AND sourceRequestId=? LIMIT 2 FOR UPDATE",
                    transfer,actor,Pointtransfer.ID_C_SYS,Pointtransfer.TRANSFER_TYPE_C_CHANGE_USERNAME,OPERATION,billingId);
            if(rows.size()!=1||!transfer.equals(rows.getFirst().optString("oId")))throw unavailable();
            for(String owner:List.of(actor,Pointtransfer.ID_C_SYS)){
                final var found=index.select("SELECT oId FROM `"+index.getName()+"` WHERE userId=? AND rawId=? LIMIT 2 FOR UPDATE",owner,transfer);
                if(found.isEmpty()&&ApiV3TransactionStatements.execute("INSERT INTO `"+index.getName()+"` (oId,userId,rawId) VALUES(?,?,?)",Ids.genTimeMillisId(),owner,transfer)!=1)throw unavailable();
            }
            root.commit();
        }catch(Exception error){if(root!=null&&root.isActive())try{root.rollback();}catch(RuntimeException ignored){ }org.apache.logging.log4j.LogManager.getLogger(getClass()).warn("Username transfer indexing delayed");}
    }
    private static JSONObject project(JSONObject row){
        if(!row.keySet().equals(Set.of("userName","paidPoints","indexTransferId"))||!(row.opt("userName") instanceof String name)||UserNameRules.invalid(name)
                ||!row.optString("indexTransferId").matches("[1-9][0-9]{0,18}")||number(row.opt("paidPoints"))<=0||number(row.opt("paidPoints"))>Integer.MAX_VALUE)throw unavailable();
        return new JSONObject().put("userName",name).put("paidPoints",row.getInt("paidPoints"));
    }
    private static long number(Object value) {
        if (!(value instanceof Number n))
            throw unavailable();
        try {
            return new BigDecimal(n.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw unavailable();
        }
    }
    public static ApiV3Exception unavailable(){return new ApiV3Exception(503,"temporarily_unavailable","修改用户名暂不可用");}
}
