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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.ApiV3Token;
import org.b3log.symphony.model.ApiV3Client;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3ClientRepository;
import org.b3log.symphony.repository.ApiV3TokenRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 签发、验证、轮换及撤销 API v3 凭据。 */
@Service
public class ApiV3TokenMgmtService {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3TokenMgmtService.class);
    private static final long ACCESS_TTL = TimeUnit.MINUTES.toMillis(15);
    private static final long REFRESH_TTL = TimeUnit.DAYS.toMillis(30);
    public static final String WEBSITE_CLIENT = "website_v3";
    public static final String DEFAULT_SCOPES = "profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write uploads.write messages.read messages.write";

    @Inject
    private ApiV3TokenRepository apiV3TokenRepository;
    @Inject
    private ApiV3ClientRepository apiV3ClientRepository;
    @Inject private UserRepository userRepository;
    protected long now(){return System.currentTimeMillis();}

    private Transaction beginRoot(){
        if(apiV3TokenRepository.hasTransactionBegun())throw new IllegalStateException("use current-transaction token primitive");
        return ApiV3Transactions.strict(apiV3TokenRepository.beginTransaction());
    }

    public JSONObject issue(final String userId) throws ServiceException {
        final Transaction transaction = beginRoot();
        try {
            final JSONObject client = ensureWebsiteClient();
            final var expected = org.b3log.symphony.util.AccountCredentialVersions.snapshot(userId);
            if (expected == null || org.b3log.symphony.util.AccountCredentialVersions.issuance(userId, null) == null)
                throw new ServiceException("unauthorized");
            final JSONObject result = issueInternal(userId, WEBSITE_CLIENT, client.optString(ApiV3Client.SCOPE), null, null);
            recordSuccessfulLoginInCurrentTransaction(userId, now());
            transaction.commit();
            evictLoginCacheAfterTransaction(userId);
            return result;
        } catch (final Exception e) {
            rollback(transaction);
            throw new ServiceException(e);
        }
    }

    /** 仅在显式第一方登录时记录，凭据轮换和 OAuth 授权跳过此步骤。 */
    public void recordSuccessfulLoginInCurrentTransaction(final String userId, final long time) throws Exception {
        if (!apiV3TokenRepository.hasTransactionBegun()) throw new IllegalStateException("login transaction required");
        if (userId == null || !userId.matches("[1-9][0-9]{0,18}") || time <= 0)
            throw new IllegalArgumentException("login activity");
        final var rows = userRepository.select("SELECT oId,userStatus,userLatestLoginTime FROM `"
                + userRepository.getName() + "` WHERE oId=? FOR UPDATE", userId);
        if (rows.size() != 1 || rows.getFirst().optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID)
            throw new ServiceException("unauthorized");
        // 部分 JDBC 配置会对匹配但未变化的行返回零，先跳过相同时间的写入。
        if (rows.getFirst().getLong(UserExt.USER_LATEST_LOGIN_TIME) == time) return;
        if (ApiV3TransactionStatements.execute("UPDATE `" + userRepository.getName()
                + "` SET userLatestLoginTime=? WHERE oId=? AND userStatus=?", time, userId, UserExt.USER_STATUS_C_VALID) != 1)
            throw new ServiceException("login_activity_unavailable");
    }

    /** 登录提交后再清理缓存，清理失败只记日志。 */
    public void evictLoginCacheAfterTransaction(final String userId) {
        if (apiV3TokenRepository.hasTransactionBegun()) throw new IllegalStateException("login cache eviction before commit");
        try {
            userRepository.evictSettingsCaches(userId);
        } catch (final RuntimeException error) {
            LOGGER.warn("Cannot evict committed login activity cache", error);
        }
    }

    /** Existing website cookie refresh cannot be used to refresh a different registered client's grant. */
    public JSONObject refresh(final String refreshToken) throws ServiceException {
        return refreshForClient(WEBSITE_CLIENT,refreshToken);
    }

    public JSONObject refreshForClient(final String clientId,final String refreshToken) throws ServiceException {
        return refreshForClient(clientId,refreshToken,null);
    }
    public JSONObject refreshForClient(final String clientId,final String refreshToken,final String requestedScope) throws ServiceException {
        if (clientId==null||clientId.isBlank()||clientId.length()>64||!ApiV3TokenUtil.isValidFormat(refreshToken)) {
            throw new ServiceException("unauthorized");
        }
        final Transaction transaction = beginRoot();
        boolean committed = false;
        try {
            final JSONObject client = apiV3ClientRepository.getByClientIdForUpdate(clientId);
            final JSONObject current = apiV3TokenRepository.getByHashForUpdate(ApiV3TokenUtil.hash(refreshToken));
            if (null == current || !clientId.equals(current.optString(ApiV3Token.CLIENT_ID))
                    || !ApiV3Token.TYPE_REFRESH.equals(current.optString(ApiV3Token.TOKEN_TYPE))) {
                throw new ServiceException("unauthorized");
            }
            final long now = now();
            if (null == client || ApiV3Client.STATUS_ACTIVE != client.optInt(ApiV3Client.STATUS)) {
                revokeFamily(current, now);
                transaction.commit();
                committed = true;
                throw new ServiceException("unauthorized");
            }
            if (ApiV3Token.STATE_ROTATED == current.optInt(ApiV3Token.STATE)) {
                revokeFamily(current, now);
                transaction.commit();
                committed = true;
                throw new ServiceException("unauthorized");
            }
            if (ApiV3Token.STATE_ACTIVE != current.optInt(ApiV3Token.STATE) || current.optLong(ApiV3Token.EXPIRES_AT) <= now) {
                if (ApiV3Token.STATE_ACTIVE == current.optInt(ApiV3Token.STATE)) {
                    current.put(ApiV3Token.STATE, ApiV3Token.STATE_EXPIRED).put(ApiV3Token.UPDATED_AT, now);
                    apiV3TokenRepository.update(current.optString(Keys.OBJECT_ID), current);
                    transaction.commit();
                    committed = true;
                }
                throw new ServiceException("unauthorized");
            }
            // Every client, including the website, must use the current locked account status.
            final var rows=userRepository.select("SELECT oId,userStatus FROM `"+userRepository.getName()+"` WHERE oId=? FOR UPDATE",current.getString(ApiV3Token.USER_ID));
            final JSONObject user=rows.size()==1?rows.getFirst():null;
            if (null == user || UserExt.USER_STATUS_C_VALID != user.optInt(UserExt.USER_STATUS)) {
                current.put(ApiV3Token.STATE, ApiV3Token.STATE_REVOKED).put(ApiV3Token.UPDATED_AT, now);
                apiV3TokenRepository.update(current.optString(Keys.OBJECT_ID), current);
                transaction.commit();
                committed = true;
                throw new ServiceException("unauthorized");
            }
            current.put(ApiV3Token.STATE, ApiV3Token.STATE_ROTATED)
                    .put(ApiV3Token.LAST_USED_AT, now).put(ApiV3Token.UPDATED_AT, now);
            apiV3TokenRepository.update(current.optString(Keys.OBJECT_ID), current);
            String scope;
            if(WEBSITE_CLIENT.equals(clientId))scope=current.optString(ApiV3Token.SCOPE);
            else{
                final var permitted=new java.util.TreeSet<String>();
                for(final String granted:StringUtils.split(current.optString(ApiV3Token.SCOPE),' '))if(hasScope(client.optString(ApiV3Client.SCOPE),granted))permitted.add(granted);
                scope=StringUtils.join(permitted," ");
            }
            if(!WEBSITE_CLIENT.equals(clientId)&&scope.isEmpty())throw new ServiceException("invalid_scope");
            if(requestedScope!=null){
                if(requestedScope.isBlank()||requestedScope.length()>255||requestedScope.codePoints().anyMatch(Character::isISOControl))throw new ServiceException("invalid_scope");
                final String narrowed=normalizeScope(requestedScope);
                for(String item:StringUtils.split(narrowed,' ')){
                    if(!item.matches("[\\x21\\x23-\\x5B\\x5D-\\x7E]+")||!hasScope(scope,item))throw new ServiceException("invalid_scope");
                }
                scope=narrowed;
            }
            final JSONObject result = issueInternal(current.optString(ApiV3Token.USER_ID), clientId, scope,
                    current.optString(ApiV3Token.FAMILY_ID), current.optString(Keys.OBJECT_ID));
            transaction.commit();
            committed = true;
            return result;
        } catch (final ServiceException e) {
            if (!committed) rollback(transaction);
            throw e;
        } catch (final Exception e) {
            rollback(transaction);
            LOGGER.error("Refreshes API v3 token failed", e);
            throw new ServiceException(e);
        }
    }

    public JSONObject authenticate(final String token) {
        try {
            if (!ApiV3TokenUtil.isValidFormat(token)) return null;
            final JSONObject row = apiV3TokenRepository.getByHash(ApiV3TokenUtil.hash(token));
            if (null == row || !ApiV3Token.TYPE_ACCESS.equals(row.optString(ApiV3Token.TOKEN_TYPE))
                    || ApiV3Token.STATE_ACTIVE != row.optInt(ApiV3Token.STATE)
                    || row.optLong(ApiV3Token.EXPIRES_AT) <= now()) return null;
            final JSONObject client = apiV3ClientRepository.getByClientId(row.optString(ApiV3Token.CLIENT_ID));
            if (null == client || ApiV3Client.STATUS_ACTIVE != client.optInt(ApiV3Client.STATUS)) return null;
            // UserRepository.get/UserQueryService.getUser may return a stale per-node cache entry.
            final var users=userRepository.select("SELECT * FROM `"+userRepository.getName()+"` WHERE oId=? LIMIT 2",row.optString(ApiV3Token.USER_ID));
            final JSONObject user=users.size()==1?users.getFirst():null;
            if (null == user || UserExt.USER_STATUS_C_VALID != user.optInt(UserExt.USER_STATUS)) return null;
            markAccessUsed(row);
            return new JSONObject().put("user", user).put("scope", row.optString(ApiV3Token.SCOPE))
                    .put("tokenId", row.optString(Keys.OBJECT_ID)).put("clientId", row.optString(ApiV3Token.CLIENT_ID));
        } catch (final Exception e) {
            return null;
        }
    }

    public void revokeRefresh(final String refreshToken) throws ServiceException {
        revokeForClient(WEBSITE_CLIENT,refreshToken);
    }

    public void revokeForClient(final String clientId,final String refreshToken) throws ServiceException {
        if(clientId==null||clientId.isBlank()||clientId.length()>64)throw new ServiceException("unauthorized");
        if (!ApiV3TokenUtil.isValidFormat(refreshToken)) return;
        final Transaction transaction = beginRoot();
        try {
            apiV3ClientRepository.getByClientIdForUpdate(clientId);
            final JSONObject row = apiV3TokenRepository.getByHashForUpdate(ApiV3TokenUtil.hash(refreshToken));
            if (null != row && clientId.equals(row.optString(ApiV3Token.CLIENT_ID))
                    && ApiV3Token.TYPE_REFRESH.equals(row.optString(ApiV3Token.TOKEN_TYPE))) revokeFamily(row, now());
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            throw new ServiceException(e);
        }
    }

    /** OAuth revocation accepts either member of a client-bound family; legacy refresh-only revocation stays unchanged. */
    public void revokeTokenForClient(final String clientId,final String credential)throws ServiceException {
        if(clientId==null||clientId.isBlank()||clientId.length()>64)throw new ServiceException("unauthorized");
        if(!ApiV3TokenUtil.isValidFormat(credential))return;
        final Transaction transaction=beginRoot();try{
            apiV3ClientRepository.getByClientIdForUpdate(clientId);
            final JSONObject row=apiV3TokenRepository.getByHashForUpdate(ApiV3TokenUtil.hash(credential));
            if(row!=null&&clientId.equals(row.optString(ApiV3Token.CLIENT_ID))
                    &&(ApiV3Token.TYPE_REFRESH.equals(row.optString(ApiV3Token.TOKEN_TYPE))||ApiV3Token.TYPE_ACCESS.equals(row.optString(ApiV3Token.TOKEN_TYPE))))revokeFamily(row,now());
            transaction.commit();
        }catch(Exception e){rollback(transaction);throw new ServiceException(e);}
    }
    public static boolean hasScope(final String scopes, final String required) {
        return new HashSet<>(Arrays.asList(StringUtils.split(StringUtils.trimToEmpty(scopes), ' '))).contains(required);
    }

    /** Authorization-code exchange joins the code-consumption transaction; this never commits it. */
    public JSONObject issueForClientInCurrentTransaction(final String userId,final String clientId,final String scope)throws Exception {
        if(!apiV3TokenRepository.hasTransactionBegun())throw new IllegalStateException("client grant transaction required");
        if(userId==null||!userId.matches("[0-9]{1,19}")||clientId==null||clientId.isBlank()||clientId.length()>64
                ||scope==null||scope.length()>255)throw new IllegalArgumentException("client grant");
        return ApiV3TransactionSavepoint.run(()->{
            final JSONObject client=apiV3ClientRepository.getByClientIdForUpdate(clientId);
            if(client==null||client.optInt(ApiV3Client.STATUS,-1)!=ApiV3Client.STATUS_ACTIVE)throw new ServiceException("client_disabled");
            // Fresh projection, not UserCache or a request-supplied user snapshot.
            final var users=userRepository.select("SELECT oId,userStatus FROM `"+userRepository.getName()+"` WHERE oId=? FOR UPDATE",userId);
            if(users.size()!=1||users.getFirst().optInt(UserExt.USER_STATUS,-1)!=UserExt.USER_STATUS_C_VALID)throw new ServiceException("unauthorized");
            final String normalized=normalizeScope(scope);
            for(final String required:StringUtils.split(normalized,' ')){
                if(!required.matches("[\\x21\\x23-\\x5B\\x5D-\\x7E]+")||!hasScope(client.optString(ApiV3Client.SCOPE),required))throw new ServiceException("invalid_scope");
            }
            return issueInternal(userId,clientId,normalized,null,null);
        });
    }

    private JSONObject issueInternal(final String userId, final String clientId, final String scope, final String existingFamily, final String parentId)
            throws Exception {
        final long now = now();
        final String familyId = StringUtils.isBlank(existingFamily) ? UUID.randomUUID().toString().replace("-", "") : existingFamily;
        final String normalizedScope = normalizeScope(scope);
        final String access = ApiV3TokenUtil.generate();
        final String refresh = ApiV3TokenUtil.generate();
        add(access, ApiV3Token.TYPE_ACCESS, userId, clientId, normalizedScope, familyId, parentId, now + ACCESS_TTL, now);
        add(refresh, ApiV3Token.TYPE_REFRESH, userId, clientId, normalizedScope, familyId, parentId, now + REFRESH_TTL, now);
        return new JSONObject().put("accessToken", access).put("expiresIn", TimeUnit.MILLISECONDS.toSeconds(ACCESS_TTL))
                .put("refreshToken", refresh).put("refreshExpiresIn", TimeUnit.MILLISECONDS.toSeconds(REFRESH_TTL))
                .put("scope", normalizedScope).put("tokenType", "Bearer");
    }

    private JSONObject ensureWebsiteClient() throws Exception {
        final JSONObject existing = apiV3ClientRepository.getByClientIdForUpdate(WEBSITE_CLIENT);
        if (null != existing) {
            if (ApiV3Client.STATUS_ACTIVE != existing.optInt(ApiV3Client.STATUS)) {
                throw new ServiceException("client_disabled");
            }
            return existing;
        }
        final JSONObject client = new JSONObject().put(Keys.OBJECT_ID, Ids.genTimeMillisId())
                .put(ApiV3Client.CLIENT_ID, WEBSITE_CLIENT).put(ApiV3Client.NAME, "website_v3")
                .put(ApiV3Client.SCOPE, DEFAULT_SCOPES).put(ApiV3Client.STATUS, ApiV3Client.STATUS_ACTIVE);
        apiV3ClientRepository.add(client);
        return client;
    }

    private static String normalizeScope(final String scope){
        return StringUtils.join(new java.util.TreeSet<>(Arrays.asList(StringUtils.split(StringUtils.trimToEmpty(scope),' ')))," ");
    }

    private void add(final String token, final String type, final String userId, final String clientId, final String scope, final String familyId,
                     final String parentId, final long expiresAt, final long now) throws Exception {
        apiV3TokenRepository.add(new JSONObject().put(Keys.OBJECT_ID, Ids.genTimeMillisId())
                .put(ApiV3Token.TOKEN_HASH, ApiV3TokenUtil.hash(token)).put(ApiV3Token.TOKEN_TYPE, type)
                .put(ApiV3Token.USER_ID, userId).put(ApiV3Token.CLIENT_ID, clientId).put(ApiV3Token.SCOPE, scope)
                .put(ApiV3Token.FAMILY_ID, familyId).put(ApiV3Token.PARENT_ID, StringUtils.defaultString(parentId))
                .put(ApiV3Token.STATE, ApiV3Token.STATE_ACTIVE).put(ApiV3Token.CREATED_AT, now)
                .put(ApiV3Token.UPDATED_AT, now).put(ApiV3Token.LAST_USED_AT, 0L).put(ApiV3Token.EXPIRES_AT, expiresAt));
    }

    private void revokeFamily(final JSONObject token, final long now) throws Exception {
        apiV3TokenRepository.revokeFamilyInCurrentTransaction(token.getString(ApiV3Token.CLIENT_ID),
                token.getString(ApiV3Token.USER_ID),token.getString(ApiV3Token.FAMILY_ID),now);
    }

    private void markAccessUsed(final JSONObject token) {
        final Transaction transaction = beginRoot();
        try {
            apiV3TokenRepository.markAccessUsedInCurrentTransaction(token.getString(Keys.OBJECT_ID),now());
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            LOGGER.warn("Updates API v3 access token activity failed");
        }
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) transaction.rollback();
    }
}
