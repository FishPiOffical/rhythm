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
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/** 授权同意与交换的内部实现，从服务端取得已批准的用户和权限范围。 */
@Service
public class ApiV3AuthorizationCodeService {
    public static final long CODE_TTL_MILLIS=TimeUnit.MINUTES.toMillis(5);
    private static final long TTL=CODE_TTL_MILLIS;
    @Inject private ApiV3ClientRepository clients;
    @Inject private ApiV3ClientRedirectRepository redirects;
    @Inject private ApiV3AuthorizationCodeRepository codes;
    @Inject private ApiV3TokenRepository tokenRows;
    @Inject private UserRepository users;
    @Inject private ApiV3TokenMgmtService tokens;
    public record Issued(String code, long expiresAt) {
        @Override
        public String toString() {
            return "Issued[code=<redacted>, expiresAt=" + expiresAt + "]";
        }
    }

    protected long now() {
        return System.currentTimeMillis();
    }

    private static ApiV3Exception rejected() {
        return new ApiV3Exception(400, "invalid_grant", "授权凭据无效或已失效");
    }

    protected boolean development() {
        return Latkes.RuntimeMode.DEVELOPMENT == Latkes.getRuntimeMode();
    }
    private void binding(String client,String uri){
        if(client==null||!client.matches("[\\x21-\\x7E]{1,64}")||uri==null||uri.isEmpty()||uri.length()>2048)throw rejected();
        if(!ApiV3OAuthRedirects.valid(uri,development()))throw rejected();
    }
    private JSONObject registered(String client,String uri)throws Exception {
        final var row=clients.getByClientIdForUpdate(client);
        if(row==null||!client.equals(row.optString(ApiV3Client.CLIENT_ID))||row.optInt(ApiV3Client.STATUS,-1)!=ApiV3Client.STATUS_ACTIVE
                ||ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(client)||redirects.getRegisteredForUpdate(client,uri)==null)throw rejected();
        return row;
    }
    private void currentUser(String id)throws Exception {
        if(id==null||!id.matches("[1-9][0-9]{0,18}"))throw rejected();
        final var rows=users.select("SELECT oId,userStatus FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",id);
        if(rows.size()!=1||!id.equals(rows.getFirst().optString(Keys.OBJECT_ID))||rows.getFirst().optInt(UserExt.USER_STATUS,-1)!=UserExt.USER_STATUS_C_VALID)throw rejected();
    }
    private static String scopes(String scope,JSONObject client,boolean shrink){
        if (scope == null || scope.isBlank() || scope.length() > 255)
            throw rejected();
        final var result = new TreeSet<String>();
        for(String item:scope.split(" ",-1)){
            if(item.isEmpty()||!item.matches("[\\x21\\x23-\\x5B\\x5D-\\x7E]+"))throw rejected();
            if (ApiV3TokenMgmtService.hasScope(client.optString(ApiV3Client.SCOPE), item))
                result.add(item);
            else if (!shrink)
                throw rejected();
        }
        if (result.isEmpty())
            throw rejected();
        return String.join(" ", result);
    }
    public record ConsentClient(String clientId,String name,String redirectUri,String scope){ }
    /** 在调用方的事务中复核客户端当前登记状态。 */
    public ConsentClient prepareConsentClientInCurrentTransaction(String client,String uri,String requestedScope)throws Exception {
        if(!codes.hasTransactionBegun())throw new IllegalStateException("consent transaction required");
        binding(client, uri);
        final var row = registered(client, uri);
        final String name = row.optString(ApiV3Client.NAME);
        if (name.isBlank() || name.length() > 128)
            throw new IllegalStateException("invalid client display name");
        ApiV3Json.canonical(name);
        return new ConsentClient(client,name,uri,scopes(requestedScope,row,false));
    }
    public void verifyConsentUserInCurrentTransaction(String actor)throws Exception {
        if (!codes.hasTransactionBegun())
            throw new IllegalStateException("consent transaction required");
        currentUser(actor);
    }
    /** 仅供服务端授权确认处理器在校验请求来源、CSRF 和用户同意后调用。 */
    public Issued issueInCurrentTransaction(String approvedUser,String client,String uri,String approvedScope,String challenge,String method)throws Exception {
        if(!codes.hasTransactionBegun())throw new IllegalStateException("consent owner transaction required");
        binding(client, uri);
        ApiV3Pkce.requireS256(challenge, method);
        return ApiV3TransactionSavepoint.run(()->{
            final var registration = registered(client, uri);
            currentUser(approvedUser);
            final String scope = scopes(approvedScope, registration, false);
            final long time = now(), expires = Math.addExact(time, TTL);
            if (time <= 0)
                throw new IllegalStateException("invalid grant clock");
            final String raw=ApiV3TokenUtil.generate(),hash=ApiV3TokenUtil.hash("oauth-code\0"+raw);
            final var row=new JSONObject().put(Keys.OBJECT_ID,hash).put(ApiV3AuthorizationCode.CLIENT_ID,client)
                    .put(ApiV3AuthorizationCode.USER_ID,approvedUser).put(ApiV3AuthorizationCode.REDIRECT_URI,uri)
                    .put(ApiV3AuthorizationCode.SCOPE,scope).put(ApiV3AuthorizationCode.CHALLENGE,challenge)
                    .put(ApiV3AuthorizationCode.CREATED_AT,time).put(ApiV3AuthorizationCode.EXPIRES_AT,expires)
                    .put(ApiV3AuthorizationCode.CONSUMED_AT,0).put(ApiV3AuthorizationCode.FAMILY_ID,"");
            if(!hash.equals(codes.add(row)))throw new IllegalStateException("authorization code identity changed");
            return new Issued(raw,expires);
        });
    }
    /** 在自身的严格事务中消费授权码并签发令牌对，明文令牌不用于重放。 */
    public JSONObject exchange(String client,String uri,String raw,String verifier)throws Exception {
        binding(client, uri);
        if (!ApiV3TokenUtil.isValidFormat(raw) || !ApiV3Pkce.validVerifier(verifier))
            throw rejected();
        if(codes.hasTransactionBegun())throw new IllegalStateException("exchange root transaction already active");
        final Transaction transaction=ApiV3Transactions.strict(codes.beginTransaction());
        try {
            final var registration = registered(client, uri);
            final String hash = ApiV3TokenUtil.hash("oauth-code\0" + raw);
            final var code = codes.getForUpdate(hash);
            final long time = now();
            if(code==null||!hash.equals(code.optString(Keys.OBJECT_ID))||!client.equals(code.optString(ApiV3AuthorizationCode.CLIENT_ID))
                    ||!uri.equals(code.optString(ApiV3AuthorizationCode.REDIRECT_URI))
                    ||!ApiV3Pkce.matches(code.optString(ApiV3AuthorizationCode.CHALLENGE),verifier))throw rejected();
            final String actor=code.getString(ApiV3AuthorizationCode.USER_ID);
            final long created=code.optLong(ApiV3AuthorizationCode.CREATED_AT,-1),expires=code.optLong(ApiV3AuthorizationCode.EXPIRES_AT,-1),consumed=code.optLong(ApiV3AuthorizationCode.CONSUMED_AT,-1);
            final String storedFamily=code.optString(ApiV3AuthorizationCode.FAMILY_ID);
            if(!actor.matches("[1-9][0-9]{0,18}")||created<=0||created>Long.MAX_VALUE-TTL||expires!=created+TTL||consumed<0||consumed>time
                    ||(consumed==0?!storedFamily.isEmpty():!storedFamily.matches("[0-9a-f]{32}")))throw rejected();
            if (code.optLong(ApiV3AuthorizationCode.CONSUMED_AT) > 0) {
                final String family = code.optString(ApiV3AuthorizationCode.FAMILY_ID);
                if (family.isEmpty())
                    throw rejected();
                // 凭据绑定已核验，先提交原令牌家族的撤销，再返回授权码无效。
                tokenRows.revokeFamilyInCurrentTransaction(client, actor, family, time);
                transaction.commit();
                throw rejected();
            }
            if(time<=0||code.optLong(ApiV3AuthorizationCode.CREATED_AT)>time||code.optLong(ApiV3AuthorizationCode.EXPIRES_AT)<=time)throw rejected();
            currentUser(actor);
            final String scope = scopes(code.getString(ApiV3AuthorizationCode.SCOPE), registration, true);
            final JSONObject issued=tokens.issueForClientInCurrentTransaction(actor,client,scope);
            final var refresh=tokenRows.getByHashForUpdate(ApiV3TokenUtil.hash(issued.getString("refreshToken")));
            if(refresh==null||!client.equals(refresh.optString(ApiV3Token.CLIENT_ID))||!actor.equals(refresh.optString(ApiV3Token.USER_ID))
                    ||!ApiV3Token.TYPE_REFRESH.equals(refresh.optString(ApiV3Token.TOKEN_TYPE))||refresh.optString(ApiV3Token.FAMILY_ID).isEmpty())throw new IllegalStateException("issued refresh binding lost");
            code.put(ApiV3AuthorizationCode.CONSUMED_AT,time).put(ApiV3AuthorizationCode.FAMILY_ID,refresh.getString(ApiV3Token.FAMILY_ID));
            codes.update(hash,code,ApiV3AuthorizationCode.CONSUMED_AT,ApiV3AuthorizationCode.FAMILY_ID);
            transaction.commit();
            return issued;
        } finally {
            if (transaction.isActive())
                transaction.rollback();
        }
    }
}
