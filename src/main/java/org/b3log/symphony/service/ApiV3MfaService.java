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
import org.b3log.symphony.repository.ApiV3MfaPreparationRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** 第一方多因素认证，短期设置凭据不进入普通幂等回执。 */
@Service
public class ApiV3MfaService {
    @Inject private ApiV3SecurityProofService proofs;
    @Inject private AccountCredentialMgmtService credentials;
    @Inject private ApiV3MfaPreparationRepository preparations;
    @Inject private ApiV3RequestRepository requests;
    @Inject private UserRepository users;
    private static final long TTL=10*60*1000L;
    protected long now() {
        return System.currentTimeMillis();
    }
    public JSONObject prepare(JSONObject identity,JSONObject body,String ip) {
        final var input = ApiV3MfaInputs.parse(body, false);
        proofs.checkAttempts(identity, ip);
        Transaction root=null;
        try {
            if(preparations.hasTransactionBegun())throw new IllegalStateException("MFA preparation owns root");
            root=ApiV3Transactions.strict(preparations.beginTransaction());
            final JSONObject result=prepareInCurrentTransaction(identity,input);
            root.commit();
            return result;
        } catch (ApiV3Exception error) {
            rollback(root);
            throw error;
        } catch (Exception error) {
            rollback(root);
            throw unavailable();
        }
    }

    public JSONObject enable(JSONObject identity, JSONObject body, String key, String ip) {
        return mutate(identity, ApiV3MfaInputs.parse(body, true), key, ip, true);
    }

    public JSONObject disable(JSONObject identity, JSONObject body, String key, String ip) {
        return mutate(identity, ApiV3MfaInputs.parse(body, false), key, ip, false);
    }
    private JSONObject mutate(JSONObject identity,ApiV3MfaInputs.Proof input,String key,String ip,boolean enable) {
        proofs.checkAttempts(identity,ip);
        final String cacheActor=actor(identity);
        try {
            final JSONObject[] current={null};
            final ApiV3MutationStore store = new ApiV3MutationStore() {
                public Transaction beginTransaction() {
                    return ApiV3Transactions.strict(requests.beginTransaction());
                }

                public boolean hasTransactionBegun() {
                    return requests.hasTransactionBegun();
                }
                public JSONObject getForUpdate(String receipt)throws RepositoryException{
                    current[0]=proofs.requireInCurrentTransaction(identity,input.currentPassword(),input.currentTotp());
                    return requests.getForUpdate(receipt);
                }
                public String add(JSONObject row) throws RepositoryException {
                    return requests.add(row);
                }

                public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                    requests.update(id, row, fields);
                }
            };
            final String actor=actor(identity);
            final JSONObject normalized=enable?new JSONObject().put("preparationHash",ApiV3TokenUtil.hash(input.preparationId())):new JSONObject();
            final JSONObject result=ApiV3IdempotencyMgmtService.runAtomic(store,actor,enable?"security.mfa.enable":"security.mfa.disable",key,normalized,
                    ()->changeInCurrentTransaction(current[0],input,enable));
            if(!(result.opt("enabled") instanceof Boolean))throw unavailable();
            return new JSONObject().put("enabled",result.getBoolean("enabled"));
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (!requests.hasTransactionBegun())
                try {
                    users.evictSettingsCaches(cacheActor);
                } catch (RuntimeException ignored) {
                }
        }
    }
    public JSONObject prepareInCurrentTransaction(JSONObject identity,ApiV3MfaInputs.Proof input)throws Exception {
        if(!preparations.hasTransactionBegun())throw new IllegalStateException("MFA preparation root required");
        return ApiV3TransactionSavepoint.run(()->{
            final JSONObject current=proofs.requireInCurrentTransaction(identity,input.currentPassword(),input.currentTotp());
            final String actor=current.getString("oId");
            if(!secret(current).isEmpty())throw new ApiV3Exception(409,"mfa_already_enabled","已启用MFA，请先停用后重新绑定");
            final var rows=preparations.select("SELECT oId,userId FROM `"+preparations.getName()+"` WHERE oId=? FOR UPDATE",actor);
            if(rows.size()>1||!rows.isEmpty()&&!actor.equals(rows.getFirst().optString("userId")))throw unavailable();
            final String handle=ApiV3TokenUtil.generate(),secret=ApiV3MfaSecrets.generate();
            final long time = now();
            if (time <= 0)
                throw unavailable();
            final long expiry = Math.addExact(time, TTL);
            final String hash=ApiV3TokenUtil.hash(handle),binding=binding(current);
            final int changed;
            if(rows.isEmpty())changed=ApiV3TransactionStatements.execute("INSERT INTO `"+preparations.getName()+"` (oId,userId,preparationHash,secret,proofBinding,state,createdAt,expiresAt) VALUES(?,?,?,?,?,0,?,?)",actor,actor,hash,secret,binding,time,expiry);
            else changed=ApiV3TransactionStatements.execute("UPDATE `"+preparations.getName()+"` SET preparationHash=?,secret=?,proofBinding=?,state=0,createdAt=?,expiresAt=? WHERE oId=? AND userId=?",hash,secret,binding,time,expiry,actor,actor);
            if(changed!=1)throw unavailable();
            return new JSONObject().put("preparationId",handle).put("secret",secret).put("otpauthUri",ApiV3MfaSecrets.uri(actor,secret)).put("expiresAt",expiry);
        });
    }
    private JSONObject changeInCurrentTransaction(JSONObject current,ApiV3MfaInputs.Proof input,boolean enable)throws Exception {
        if(!requests.hasTransactionBegun()||current==null)throw new IllegalStateException("MFA authenticated root required");
        return ApiV3TransactionSavepoint.run(()->{
            final String actor = current.getString("oId");
            final String next;
            if(enable) {
                if(!secret(current).isEmpty())throw new ApiV3Exception(409,"mfa_already_enabled","已启用MFA，请先停用后重新绑定");
                final var rows=preparations.select("SELECT oId,userId,preparationHash,secret,proofBinding,state,createdAt,expiresAt FROM `"+preparations.getName()+"` WHERE oId=? FOR UPDATE",actor);
                if (rows.size() != 1)
                    throw expired();
                final var row = rows.getFirst();
                if(!actor.equals(row.optString("oId"))||!actor.equals(row.optString("userId"))||number(row,"state")!=0
                        ||number(row,"createdAt")<=0||number(row,"createdAt")>now()||number(row,"expiresAt")<=now()
                        ||number(row,"expiresAt")-number(row,"createdAt")!=TTL
                        ||!equal(row.optString("preparationHash"),ApiV3TokenUtil.hash(input.preparationId()))||!equal(row.optString("proofBinding"),binding(current)))throw expired();
                if (!(row.opt("secret") instanceof String value) || !value.matches("[A-Z2-7]{32}"))
                    throw unavailable();
                next = value;
                if(!ApiV3MfaSecrets.check(next,input.newTotp(),now()))throw new ApiV3Exception(422,"invalid_mfa_code","新的MFA验证码无效");
                // 同一事务内消费准备记录并清掉其中的密钥，再更新账号和撤销原凭据。
                if(ApiV3TransactionStatements.execute("UPDATE `"+preparations.getName()+"` SET state=1,secret='',proofBinding='' WHERE oId=? AND userId=? AND state=0",actor,actor)!=1)throw unavailable();
            }else {
                next="";
                ApiV3TransactionStatements.execute("DELETE FROM `"+preparations.getName()+"` WHERE oId=? AND userId=?",actor,actor);
            }
            final int changed=ApiV3TransactionStatements.execute("UPDATE `"+users.getName()+"` SET secret2fa=?,userUpdateTime=? WHERE oId=?",next,now(),actor);
            // 已锁定认证用户行。部分 JDBC 模式下，重复写入相同的停用值会返回零。
            if(changed!=1&&!(changed==0&&!enable&&secret(current).isEmpty()))throw unavailable();
            credentials.revokeInCurrentTransaction(actor);
            return new JSONObject().put("enabled",enable);
        });
    }
    private static String actor(JSONObject identity) {
        final JSONObject user = identity == null ? null : identity.optJSONObject("user");
        final String id = user == null ? "" : user.optString("oId");
        if (!id.matches("[1-9][0-9]{0,18}"))
            throw ApiV3Requests.forbidden();
        return id;
    }

    private static String secret(JSONObject current) {
        if (!(current.opt("secret2fa") instanceof String value))
            throw unavailable();
        return value;
    }

    private static String binding(JSONObject current)throws Exception {
        final long epoch = number(current, "credentialEpoch");
        if (epoch < 0)
            throw unavailable();
        if(!(current.opt("userPassword") instanceof String password))throw unavailable();
        return ApiV3TokenUtil.hash("mfa-preparation:"+current.getString("oId")+":"+epoch+":"+password+":"+secret(current));
    }
    private static boolean equal(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static long number(JSONObject row, String key) {
        if (!(row.opt(key) instanceof Number n))
            throw unavailable();
        try {
            return new BigDecimal(n.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw unavailable();
        }
    }

    private static void rollback(Transaction root) {
        if (root != null && root.isActive())
            root.rollback();
    }

    private static ApiV3Exception expired() {
        return new ApiV3Exception(409, "mfa_preparation_unavailable", "MFA准备不存在、已失效或已使用，请重新准备");
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "MFA设置暂不可用");
    }
}
