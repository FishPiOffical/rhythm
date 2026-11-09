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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ApiV3AppLoginRequest;
import org.b3log.symphony.repository.ApiV3AppLoginRequestRepository;
import org.b3log.symphony.repository.ApiV3ClientRepository;
import org.b3log.symphony.repository.ApiV3TokenRepository;
import org.b3log.symphony.repository.AccountCredentialStateRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Set;
import java.util.TreeSet;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import static org.b3log.symphony.util.ApiV3AppLoginProtocol.*;

/** 每次状态转换都在同一个严格事务内复核网站签发授权、原生客户端和凭据版本。 */
@Service
public class ApiV3AppLoginService {
    @Inject private ApiV3AppLoginRequestRepository requests;
    @Inject private ApiV3ClientRepository clients;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private AccountCredentialStateRepository states;
    @Inject private UserRepository users;
    @Inject private ApiV3SecurityProofService proofs;
    @Inject private ApiV3TokenMgmtService grants;
    protected long now() {
        return System.currentTimeMillis();
    }

    protected String handle() {
        return ApiV3TokenUtil.generate();
    }

    protected JSONObject issue(final String actor, final String scope) throws Exception {
        return grants.issueForClientInCurrentTransaction(actor, ApiV3AppLoginRequest.APP_CLIENT, scope);
    }
    public JSONObject create(final JSONObject identity) {
        actor(identity);
        return root(() -> {
            final JSONObject client = lockClient();
            final String owner = proofs.authorizeFirstPartyInCurrentTransaction(identity,"settings.write").getString("oId");
            proofs.authorizeFirstPartyInCurrentTransaction(identity,"profile.read");
            final JSONObject state = states.current(owner);
            if (state == null) throw forbidden();
            final var issuerRows = tokens.select("SELECT scope FROM `"+tokens.getName()+"` WHERE oId=? FOR UPDATE",identity.getString("tokenId"));
            if (issuerRows.size()!=1) throw forbidden();
            final var allowed = new TreeSet<String>();
            for (String scope : ApiV3TokenMgmtService.DEFAULT_SCOPES.split(" "))
                if (ApiV3TokenMgmtService.hasScope(issuerRows.getFirst().getString("scope"),scope)
                        && ApiV3TokenMgmtService.hasScope(client.getString("scope"),scope)) allowed.add(scope);
            if (!allowed.contains("profile.read")) throw forbidden();
            final String scope = String.join(" ",allowed), raw = handle(), hash = handleHash(raw);
            final Snapshot fresh = ApiV3AppLoginProtocol.create(now());
            // 创建请求时顺带限量清理旧记录；每次读取当前请求仍独立检查过期时间。
            ApiV3TransactionStatements.execute("DELETE FROM `"+requests.getName()+"` WHERE expiresAt<? LIMIT 200",Math.max(0L,now()-86_400_000L));
            change("INSERT INTO `"+requests.getName()+"` (oId,userId,issuerTokenId,credentialEpoch,scope,state,codeChallenge,createdAt,expiresAt,updatedAt) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    hash,owner,identity.getString("tokenId"),AccountCredentialStateRepository.epoch(state.opt("credentialEpoch")),scope,
                    fresh.state().name(),"",fresh.createdAt(),fresh.expiresAt(),fresh.createdAt());
            return new JSONObject().put("request",raw).put("qrPayload",qr(raw)).put("expiresAt",fresh.expiresAt()).put("state","waiting");
        });
    }
    public JSONObject webStatus(final JSONObject identity, final String raw) {
        return operation(identity, raw, null, "web-status");
    }

    public JSONObject webDecision(final JSONObject identity, final String raw, final String decision) {
        if (!Set.of("approve","deny").contains(decision)) throw ApiV3WriteInputs.invalid();
        return operation(identity,raw,null,"web-"+decision);
    }
    public JSONObject claim(final String raw, final String challenge) {
        if (!canonical32(challenge)) throw ApiV3WriteInputs.invalid();
        return operation(null,raw,challenge,"claim");
    }
    public JSONObject appStatus(final String raw, final String verifier) {
        return operation(null, raw, verifier, "app-status");
    }

    public JSONObject appDecision(final String raw, final String verifier, final String decision) {
        if (!Set.of("confirm","deny").contains(decision)) throw ApiV3WriteInputs.invalid();
        return operation(null,raw,verifier,"app-"+decision);
    }
    public JSONObject exchange(final String raw, final String verifier) {
        return operation(null, raw, verifier, "exchange");
    }

    private JSONObject operation(final JSONObject identity, final String raw, final String proof, final String action) {
        if (!canonical32(raw) || (proof!=null && !canonical32(proof))) throw ApiV3WriteInputs.invalid();
        final String hash = handleHash(raw);
        try {
            final JSONObject hint = requests.hint(hash);
            if (hint == null) throw missing();
            if (identity!=null && !actor(identity).equals(hint.optString("userId"))) throw missing();
            final JSONObject response = root(() -> {
                final JSONObject client = lockClient();
                if (identity!=null) proofs.authorizeFirstPartyInCurrentTransaction(identity,action.equals("web-status")?"profile.read":"settings.write");
                final JSONObject issuer = new JSONObject().put("user",new JSONObject().put("oId",hint.getString("userId"))).put("tokenId",hint.getString("issuerTokenId"));
                proofs.authorizeFirstPartyInCurrentTransaction(issuer,"settings.write");
                proofs.authorizeFirstPartyInCurrentTransaction(issuer,"profile.read");
                final JSONObject currentState = states.current(hint.getString("userId"));
                if (currentState == null || AccountCredentialStateRepository.epoch(currentState.opt("credentialEpoch"))!=hint.getLong("credentialEpoch")) throw forbidden();
                final JSONObject row = requests.currentForUpdate(hash);
                if (row==null) throw missing();
                // 锁定请求后复核它与预读时用于鉴权的账号、签发凭据和权限范围一致。
                for (String key : Set.of("oId","userId","issuerTokenId","credentialEpoch","scope"))
                    if (!row.get(key).toString().equals(hint.get(key).toString())) throw forbidden();
                final var issuerRows = tokens.select("SELECT scope FROM `"+tokens.getName()+"` WHERE oId=? FOR UPDATE",hint.getString("issuerTokenId"));
                if (issuerRows.size()!=1) throw forbidden();
                for (String scope : row.getString("scope").split(" "))
                    if (!ApiV3TokenMgmtService.hasScope(client.getString("scope"),scope)
                            || !ApiV3TokenMgmtService.hasScope(issuerRows.getFirst().getString("scope"),scope)) throw forbidden();
                final Snapshot current = new Snapshot(State.valueOf(row.getString("state")),row.getLong("createdAt"),row.getLong("expiresAt"),row.getString("codeChallenge"));
                final long time = now();
                if (time < current.createdAt()) throw unavailable();
                if (time >= current.expiresAt()) throw new ApiV3Exception(410,"app_login_expired","扫码请求已过期");
                if (action.startsWith("app-") || action.equals("exchange")) verify(row,proof);
                final Snapshot next = switch(action) {
                    case "claim" -> ApiV3AppLoginProtocol.claim(current,proof,time);
                    case "app-confirm" -> confirmApp(current,proof,time);
                    case "app-deny" -> denyApp(current,proof,time);
                    case "web-approve" -> approveWeb(current,time);
                    case "web-deny" -> denyWeb(current,time);
                    case "exchange" -> consume(current,proof,time);
                    default -> current;
                };
                JSONObject issued = null;
                if (action.equals("exchange")) {
                    // 凭据签发、登录时间和请求消费随同一事务提交。
                    issued = issue(row.getString("userId"),row.getString("scope"));
                    grants.recordSuccessfulLoginInCurrentTransaction(row.getString("userId"), time);
                }
                if (!next.equals(current)) change("UPDATE `"+requests.getName()+"` SET state=?,codeChallenge=?,updatedAt=? WHERE oId=? AND state=? AND codeChallenge=?",
                        next.state().name(),next.challenge(),time,hash,current.state().name(),current.challenge());
                if (issued!=null) return tokenResponse(issued,row.getString("scope"));
                final JSONObject result = new JSONObject().put("state",next.state().name().toLowerCase(java.util.Locale.ROOT)).put("expiresAt",next.expiresAt());
                if (action.equals("claim") || action.equals("app-status")) result.put("account",account(row.getString("userId")));
                return result;
            });
            if (action.equals("exchange")) grants.evictLoginCacheAfterTransaction(hint.getString("userId"));
            return response;
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }
    private JSONObject lockClient() throws Exception {
        clients.lockForAccountResetInCurrentTransaction();
        final JSONObject client = clients.getByClientIdForUpdate(ApiV3AppLoginRequest.APP_CLIENT);
        if (client==null || !ApiV3AppLoginRequest.APP_CLIENT.equals(client.optString("clientId")) || client.getInt("status")!=0 || !ApiV3TokenMgmtService.hasScope(client.getString("scope"),"profile.read")) throw forbidden();
        return client;
    }
    private JSONObject account(final String actor) throws Exception {
        final var rows = users.select("SELECT oId,userName,userNickname,userAvatarURL FROM `"+users.getName()+"` WHERE oId=? AND userStatus=0",actor);
        if (rows.size()!=1) throw forbidden();
        final JSONObject user=rows.getFirst();
        return new JSONObject().put("id",user.getString("oId")).put("userName",user.getString("userName"))
                .put("userNickname",user.optString("userNickname")).put("userAvatarURL",user.optString("userAvatarURL"));
    }
    private static void verify(final JSONObject row, final String proof) {
        if (!canonical32(proof) || !MessageDigest.isEqual(row.getString("codeChallenge").getBytes(StandardCharsets.US_ASCII),challenge(proof).getBytes(StandardCharsets.US_ASCII))) throw new ApiV3Exception(403,"invalid_app_proof","APP绑定证明无效");
    }
    private static JSONObject tokenResponse(final JSONObject issued, final String scope) {
        final String access=issued.optString("accessToken"),refresh=issued.optString("refreshToken");
        if (!ApiV3TokenUtil.isValidFormat(access)||!ApiV3TokenUtil.isValidFormat(refresh)||!scope.equals(issued.optString("scope"))
                ||!"Bearer".equals(issued.optString("tokenType"))||issued.optLong("expiresIn")<=0||issued.optLong("refreshExpiresIn")<=0) throw unavailable();
        return new JSONObject().put("accessToken",access).put("refreshToken",refresh).put("tokenType","Bearer")
                .put("expiresIn",issued.getLong("expiresIn")).put("refreshExpiresIn",issued.getLong("refreshExpiresIn"))
                .put("scope",scope).put("clientId",ApiV3AppLoginRequest.APP_CLIENT);
    }
    @FunctionalInterface
    private interface Work {
        JSONObject run() throws Exception;
    }

    private JSONObject root(final Work work) {
        if (requests.hasTransactionBegun()) throw new IllegalStateException("app login strict root required");
        Transaction tx = null;
        try {
            tx = ApiV3Transactions.strict(requests.beginTransaction());
            final JSONObject result = work.run();
            tx.commit();
            return result;
        } catch (Rejected error) {
            rollback(tx);
            throw switch (error.reason()) {
                case BAD_PROOF -> new ApiV3Exception(403, "invalid_app_proof", "APP绑定证明无效");
                case EXPIRED -> new ApiV3Exception(410, "app_login_expired", "扫码请求已过期");
                case ALREADY_USED, DENIED -> new ApiV3Exception(410, "app_login_unavailable", "扫码请求已失效");
                default -> new ApiV3Exception(409, "app_login_conflict", "扫码请求状态不允许此操作");
            };
        } catch (ApiV3Exception error) {
            rollback(tx);
            throw error;
        } catch (Exception error) {
            rollback(tx);
            throw unavailable();
        }
    }

    private static void rollback(Transaction tx) {
        if (tx != null && tx.isActive())
            tx.rollback();
    }

    private static String actor(JSONObject identity) {
        final JSONObject user = identity == null ? null : identity.optJSONObject("user");
        final String actor = user == null ? "" : user.optString("oId");
        if (!actor.matches("[1-9][0-9]{0,18}"))
            throw forbidden();
        return actor;
    }

    private static void change(String sql, Object... args) throws Exception {
        if (ApiV3TransactionStatements.execute(sql, args) != 1)
            throw unavailable();
    }

    private static ApiV3Exception forbidden() {
        return new ApiV3Exception(403, "forbidden", "当前授权不允许此扫码请求");
    }

    private static ApiV3Exception missing() {
        return new ApiV3Exception(404, "not_found", "扫码请求不存在");
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "扫码登录暂不可用");
    }
}
