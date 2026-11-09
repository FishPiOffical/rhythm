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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ApiV3AuthorizationRequest;
import org.b3log.symphony.repository.ApiV3AuthorizationRequestRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/** 服务端记录用户明确同意的授权。HTTP 入口须传入 Cookie 会话身份，并校验请求来源和 CSRF。 */
@Service
public class ApiV3AuthorizationRequestService {
    private static final long TTL=TimeUnit.MINUTES.toMillis(10);
    @Inject private ApiV3AuthorizationRequestRepository requests;
    @Inject private ApiV3AuthorizationCodeService codes;
    public record Prepared(String requestId,String consentToken,String clientId,String clientName,String redirectUri,String scope,long expiresAt){
        @Override public String toString(){return "Prepared[requestId=<redacted>, consentToken=<redacted>, expiresAt="+expiresAt+"]";}
    }
    public record Decision(String redirectUri,boolean approved){
        @Override public String toString(){return "Decision[redirectUri=<redacted>, approved="+approved+"]";}
    }
    protected long now(){return System.currentTimeMillis();}
    private static ApiV3Exception invalid(){return new ApiV3Exception(400,"invalid_request","授权请求无效或已失效，请重新发起授权");}
    private static void actor(String id){if(id==null||!id.matches("[1-9][0-9]{0,18}"))throw invalid();}
    private static String requestHash(String raw)throws Exception {
        if(!ApiV3TokenUtil.isValidFormat(raw))throw invalid();return ApiV3TokenUtil.hash("oauth-request\0"+raw);
    }
    private static String consentHash(String raw)throws Exception {
        if(!ApiV3TokenUtil.isValidFormat(raw))throw invalid();return ApiV3TokenUtil.hash("oauth-consent\0"+raw);
    }
    private Transaction begin(){
        if(requests.hasTransactionBegun())throw new IllegalStateException("authorization request root already active");return ApiV3Transactions.strict(requests.beginTransaction());
    }
    protected void verifyCookieCredential(String actor) {
        if(org.b3log.symphony.util.AccountCredentialVersions.issuance(actor,null)==null)throw invalid();
    }
    public Prepared start(String cookieActor,String client,String uri,String scope,String state,String challenge,String method)throws Exception {
        actor(cookieActor);ApiV3Pkce.requireS256(challenge,method);ApiV3OAuthCallbacks.validateState(state);
        final Transaction tx=begin();try{
            final var registered=codes.prepareConsentClientInCurrentTransaction(client,uri,scope);codes.verifyConsentUserInCurrentTransaction(cookieActor);verifyCookieCredential(cookieActor);
            final long time=now();if(time<=0)throw new IllegalStateException("invalid consent clock");final long expires=Math.addExact(time,TTL);
            final String handle=ApiV3TokenUtil.generate(),proof=ApiV3TokenUtil.generate(),hash=requestHash(handle);
            final var row=new JSONObject().put(Keys.OBJECT_ID,hash).put(ApiV3AuthorizationRequest.USER_ID,cookieActor)
                    .put(ApiV3AuthorizationRequest.CLIENT_ID,registered.clientId()).put(ApiV3AuthorizationRequest.REDIRECT_URI,registered.redirectUri())
                    .put(ApiV3AuthorizationRequest.SCOPE,registered.scope()).put(ApiV3AuthorizationRequest.STATE,state)
                    .put(ApiV3AuthorizationRequest.CHALLENGE,challenge).put(ApiV3AuthorizationRequest.CONSENT_HASH,consentHash(proof))
                    .put(ApiV3AuthorizationRequest.CREATED_AT,time).put(ApiV3AuthorizationRequest.EXPIRES_AT,expires)
                    .put(ApiV3AuthorizationRequest.DECIDED_AT,0).put(ApiV3AuthorizationRequest.DECISION,ApiV3AuthorizationRequest.PENDING);
            if(!hash.equals(requests.add(row)))throw new IllegalStateException("authorization request identity changed");
            tx.commit();return new Prepared(handle,proof,registered.clientId(),registered.name(),registered.redirectUri(),registered.scope(),expires);
        }finally{if(tx.isActive())tx.rollback();}
    }
    public Decision decide(String cookieActor,String rawRequest,String rawProof,boolean approved)throws Exception {
        actor(cookieActor);final String hash=requestHash(rawRequest),proof=consentHash(rawProof);
        // 无锁预读仅为取得绑定本人且不可变的客户端字段，持锁后再复核所有字段。
        if(requests.hasTransactionBegun())throw new IllegalStateException("authorization decision root already active");
        final var hint=requests.hintForOwner(hash,cookieActor);if(hint==null||!hash.equals(hint.optString(Keys.OBJECT_ID)))throw invalid();
        final Transaction tx=begin();try{
            final var client=codes.prepareConsentClientInCurrentTransaction(hint.getString(ApiV3AuthorizationRequest.CLIENT_ID),hint.getString(ApiV3AuthorizationRequest.REDIRECT_URI),hint.getString(ApiV3AuthorizationRequest.SCOPE));
            final var row=requests.getForOwnerForUpdate(hash,cookieActor);final long time=now();
            if(row==null||!hash.equals(row.optString(Keys.OBJECT_ID))||!cookieActor.equals(row.optString(ApiV3AuthorizationRequest.USER_ID))
                    ||!client.clientId().equals(row.optString(ApiV3AuthorizationRequest.CLIENT_ID))||!client.redirectUri().equals(row.optString(ApiV3AuthorizationRequest.REDIRECT_URI))
                    ||!client.scope().equals(row.optString(ApiV3AuthorizationRequest.SCOPE))||!ApiV3AuthorizationRequest.PENDING.equals(row.optString(ApiV3AuthorizationRequest.DECISION))
                    ||!MessageDigest.isEqual(proof.getBytes(StandardCharsets.US_ASCII),row.optString(ApiV3AuthorizationRequest.CONSENT_HASH).getBytes(StandardCharsets.US_ASCII)))throw invalid();
            final long created=row.optLong(ApiV3AuthorizationRequest.CREATED_AT,-1),expires=row.optLong(ApiV3AuthorizationRequest.EXPIRES_AT,-1);
            if(time<=0||created<=0||created>time||created>Long.MAX_VALUE-TTL||expires!=created+TTL||expires<=time||row.optLong(ApiV3AuthorizationRequest.DECIDED_AT,-1)!=0)throw invalid();
            final String state=row.getString(ApiV3AuthorizationRequest.STATE),challenge=row.getString(ApiV3AuthorizationRequest.CHALLENGE);ApiV3OAuthCallbacks.validateState(state);ApiV3Pkce.requireS256(challenge,"S256");
            codes.verifyConsentUserInCurrentTransaction(cookieActor);verifyCookieCredential(cookieActor);
            final String code=approved?codes.issueInCurrentTransaction(cookieActor,client.clientId(),client.redirectUri(),client.scope(),challenge,"S256").code():null;
            final String callback=ApiV3OAuthCallbacks.result(client.redirectUri(),state,code,approved);
            row.put(ApiV3AuthorizationRequest.DECISION,approved?ApiV3AuthorizationRequest.APPROVED:ApiV3AuthorizationRequest.DENIED).put(ApiV3AuthorizationRequest.DECIDED_AT,time);
            requests.update(hash,row,ApiV3AuthorizationRequest.DECISION,ApiV3AuthorizationRequest.DECIDED_AT);
            tx.commit();return new Decision(callback,approved);
        }finally{if(tx.isActive())tx.rollback();}
    }
}
