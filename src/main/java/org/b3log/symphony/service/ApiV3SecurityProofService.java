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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import com.github.benmanes.caffeine.cache.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 共享第一方实时鉴权和逐请求的密码、TOTP 证明。结果仅供服务内部使用。 */
@Service
public class ApiV3SecurityProofService {
    private static final Cache<String,AtomicInteger> ATTEMPTS=Caffeine.newBuilder().maximumSize(100000).expireAfterWrite(10,TimeUnit.MINUTES).build();
    @Inject private ApiV3ClientRepository clients;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private UserRepository users;
    @Inject private AccountCredentialStateRepository states;
    protected long now() {
        return System.currentTimeMillis();
    }
    public void checkAttempts(final JSONObject identity,final String ip){
        final String actor=actor(identity);
        if(ATTEMPTS.get("user:"+actor,k->new AtomicInteger()).incrementAndGet()>12
                ||ATTEMPTS.get("ip:"+(ip==null?"":ip),k->new AtomicInteger()).incrementAndGet()>60)
            throw new ApiV3Exception(429,"too_many_requests","安全验证过于频繁，请稍后重试");
    }
    /** 调用方持有严格根事务，此处按统一顺序取得凭据相关锁。 */
    public JSONObject requireInCurrentTransaction(final JSONObject identity,final String currentPassword,final String currentTotp)throws RepositoryException {
        ApiV3SecurityInputs.digest(currentPassword);
        if (currentTotp != null && !currentTotp.isEmpty())
            ApiV3SecurityInputs.totp(currentTotp);
        if(!users.hasTransactionBegun()||!tokens.hasTransactionBegun())throw new IllegalStateException("security root required");
        clients.lockForAccountResetInCurrentTransaction();
        final JSONObject user=authorize(identity,"settings.write",true);
        final String stored=string(user,"userPassword");
        if(!MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8),currentPassword.getBytes(StandardCharsets.UTF_8)))throw proofInvalid();
        final String secret=string(user,"secret2fa");
        if(!secret.isEmpty()){
            if(currentTotp==null||!currentTotp.matches("[0-9]{6}"))throw proofInvalid();
            if(!secret.matches("[A-Za-z2-7]{16,128}={0,6}"))throw unavailable();
            if(!MultiFactorAuthenticator.checkCode(secret,Long.parseLong(currentTotp),now()))throw proofInvalid();
        }
        return user;
    }
    /** 私人设置流程只校验当前第一方授权，返回的投影不含密码或多因素认证字段。 */
    public JSONObject authorizeFirstPartyInCurrentTransaction(final JSONObject identity, final String scope)throws RepositoryException {
        if(!java.util.Set.of("profile.read","settings.write").contains(scope))throw new IllegalArgumentException("private workflow scope");
        if(!users.hasTransactionBegun()||!tokens.hasTransactionBegun())throw new IllegalStateException("first-party root required");
        clients.lockForAccountResetInCurrentTransaction();
        return new JSONObject().put("oId",authorize(identity,scope,true).getString("oId"));
    }
    /** 使用当前第一方 profile.read 授权查询 MFA 启用状态，整个过程只读。 */
    public JSONObject read(final JSONObject identity) throws RepositoryException {
        return authorize(identity, "profile.read", false);
    }

    private JSONObject authorize(final JSONObject identity,final String scope,final boolean lock)throws RepositoryException {
        final String actor = actor(identity), tokenId = identity.optString("tokenId");
        if (!tokenId.matches("[1-9][0-9]{0,18}"))
            throw ApiV3Requests.forbidden();
        final String suffix=lock?" FOR UPDATE":"";
        final var websites=clients.select("SELECT clientId,status,scope FROM `"+clients.getName()+"` WHERE clientId=?"+suffix,ApiV3TokenMgmtService.WEBSITE_CLIENT);
        final var owners=users.select("SELECT oId,userStatus,userPassword,secret2fa FROM `"+users.getName()+"` WHERE oId=?"+suffix,actor);
        final var access=tokens.select("SELECT oId,userId,clientId,tokenType,state,expiresAt,scope FROM `"+tokens.getName()+"` WHERE oId=?"+suffix,tokenId);
        if(websites.size()!=1||owners.size()!=1||access.size()!=1)throw ApiV3Requests.forbidden();
        final var website = websites.getFirst();
        final var user = owners.getFirst();
        final var token = access.getFirst();
        if(!ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(website.optString("clientId"))||integer(website,"status")!=0||!ApiV3TokenMgmtService.hasScope(string(website,"scope"),scope)
                ||!actor.equals(user.optString("oId"))||integer(user,"userStatus")!=0
                ||!tokenId.equals(token.optString("oId"))||!actor.equals(token.optString("userId"))||!ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(token.optString("clientId"))
                ||!"access".equals(token.optString("tokenType"))||integer(token,"state")!=0||integer(token,"expiresAt")<=now()||!ApiV3TokenMgmtService.hasScope(string(token,"scope"),scope))throw ApiV3Requests.forbidden();
        final JSONObject state=states.current(actor);
        if(state==null||!actor.equals(state.optString("oId"))||integer(state,"userStatus")!=0||!string(user,"userPassword").equals(string(state,"userPassword")))throw ApiV3Requests.forbidden();
        final long epoch=AccountCredentialStateRepository.epoch(state.opt("credentialEpoch"));
        return new JSONObject(user.toString()).put("credentialEpoch",epoch);
    }
    private static String actor(final JSONObject identity) {
        final JSONObject user = identity == null ? null : identity.optJSONObject("user");
        final String id = user == null ? "" : user.optString("oId");
        if (!id.matches("[1-9][0-9]{0,18}"))
            throw ApiV3Requests.forbidden();
        return id;
    }

    private static String string(final JSONObject row, final String field) {
        if (!(row.opt(field) instanceof String value))
            throw unavailable();
        return value;
    }

    private static long integer(final JSONObject row, final String field) {
        if (!(row.opt(field) instanceof Number n))
            throw unavailable();
        try {
            return new java.math.BigDecimal(n.toString()).longValueExact();
        } catch (ArithmeticException e) {
            throw unavailable();
        }
    }

    private static ApiV3Exception proofInvalid() {
        return new ApiV3Exception(422, "invalid_security_proof", "安全验证失败");
    }

    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "账号安全设置暂不可用");
    }
}
