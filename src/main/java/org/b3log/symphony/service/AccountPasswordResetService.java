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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Verifycode;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3Json;
import org.json.JSONObject;

/** Anonymous phone proof, never Cookie/apiKey/Bearer identity, owns the reset transaction. */
@Service
public class AccountPasswordResetService {
    @Inject private AccountVerificationService verification;
    @Inject private AccountCredentialMgmtService credentials;
    @Inject private AccountCredentialStateRepository states;
    @Inject private UserRepository users;
    @Inject private ApiV3ClientRepository clients;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private ApiV3AuthorizationRequestRepository requests;
    @Inject private ApiV3AuthorizationCodeRepository codes;
    protected long now(){return System.currentTimeMillis();}
    protected void unavailable(Exception error){LogManager.getLogger(getClass()).warn("Account password reset unavailable");}

    public JSONObject complete(String phone,String code,String password)throws Exception {
        if(phone==null||!phone.matches("1[0-9]{10}")||code==null||!code.matches("[0-9]{6}")
                ||password==null||!password.matches("[a-f0-9]{32}"))throw new ApiV3Exception(422,"invalid_request","请求字段不合法");
        reset("userPhone",phone,code,password);return new JSONObject().put("changed",true);
    }
    /** Legacy caller supplies an explicit account ID; never resolve its proof against another phone owner. */
    public String completeForUserId(String userId,String code,String password)throws Exception {
        if(userId==null||!userId.matches("[1-9][0-9]{0,18}")||code==null||!code.matches("[0-9]{6}")
                ||password==null||password.isEmpty()||password.length()>32)throw new ApiV3Exception(422,"invalid_request","请求字段不合法");
        ApiV3Json.canonical(password);
        return reset("oId",userId,code,password);
    }
    private String reset(String selector,String value,String code,String password)throws Exception {
        if(tokens.hasTransactionBegun())throw new IllegalStateException("password reset root required");
        org.b3log.symphony.util.AccountCredentialVersions.clear();
        String actor=null;final var tx=ApiV3Transactions.strict(tokens.beginTransaction());
        try{
            clients.lockForAccountResetInCurrentTransaction();
            final var owners=users.select("SELECT oId FROM `"+users.getName()+"` WHERE "+selector+"=? ORDER BY oId FOR UPDATE",value);
            if(owners.size()!=1)throw invalidProof();actor=owners.getFirst().optString(Keys.OBJECT_ID);
            if(!verification.consumePhoneCodeInCurrentTransaction(actor,code,Verifycode.BIZ_TYPE_C_RESET_PWD))throw invalidProof();
            users.updateColumnsInCurrentTransaction(actor,new JSONObject().put(User.USER_PASSWORD,password));
            credentials.revokeInCurrentTransaction(actor);
            final var epochState=states.current(actor);
            final long epoch=AccountCredentialStateRepository.epoch(epochState.opt("credentialEpoch"));
            tx.commit();
            org.b3log.symphony.util.AccountCredentialVersions.remember(new org.b3log.symphony.util.AccountCredentialVersions.Proof(actor,epoch,password));
            return actor;
        }catch(ApiV3Exception e){throw e;}
        catch(Exception e){
            // Driver/JSON exceptions may contain a phone, SMS code or password digest.
            unavailable(e);
            throw new ApiV3Exception(503,"temporarily_unavailable","重置密码暂不可用");
        }finally{
            try{if(tx.isActive())tx.rollback();}
            finally{if(actor!=null&&!tokens.hasTransactionBegun())try{users.evictSettingsCaches(actor);}catch(RuntimeException e){LogManager.getLogger(getClass()).warn("Account credential cache invalidation unavailable");}}
        }
    }
    private static ApiV3Exception invalidProof(){return new ApiV3Exception(422,"invalid_verification","验证码无效或已失效");}
}
