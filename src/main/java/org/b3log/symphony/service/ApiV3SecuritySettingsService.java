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
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

/** 修改密码和撤销已有凭据在调用方的同一个严格事务中提交。 */
@Service
public class ApiV3SecuritySettingsService {
    @Inject private ApiV3SecurityProofService proof;
    @Inject private AccountCredentialMgmtService credentials;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private UserRepository users;
    public JSONObject mfa(final JSONObject identity) {
        try {
            final JSONObject before = proof.read(identity);
            final JSONObject after = proof.read(identity);
            // 两次当前授权投影一致时才返回 MFA 开关状态。
            if (!ApiV3Json.canonical(before).equals(ApiV3Json.canonical(after)))
                throw ApiV3SecurityProofService.unavailable();
            return new JSONObject().put("enabled", !before.getString("secret2fa").isEmpty());
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            throw ApiV3SecurityProofService.unavailable();
        }
    }

    public JSONObject password(final JSONObject identity, final JSONObject body, final String ip) throws Exception {
        final var input = ApiV3SecurityInputs.password(body);
        proof.checkAttempts(identity, ip);
        if (tokens.hasTransactionBegun())
            throw new IllegalStateException("password root required");
        final Transaction tx;
        try {
            tx = ApiV3Transactions.strict(tokens.beginTransaction());
        } catch (Exception e) {
            throw ApiV3SecurityProofService.unavailable();
        }
        String actor = null;
        try {
            final JSONObject current = proof.requireInCurrentTransaction(identity, input.currentPassword(), input.currentTotp());
            actor = current.getString("oId");
            if(!input.newPassword().equals(current.getString("userPassword"))&&ApiV3TransactionStatements.execute("UPDATE `"+users.getName()+"` SET userPassword=? WHERE oId=?",input.newPassword(),actor)!=1)throw ApiV3SecurityProofService.unavailable();
            credentials.revokeInCurrentTransaction(actor);
            tx.commit();
            return new JSONObject().put("changed", true).put("reauthenticationRequired", true);
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            throw ApiV3SecurityProofService.unavailable();
        } finally {
            try {
                if (tx.isActive())
                    tx.rollback();
            } finally {
                if (actor != null && !tokens.hasTransactionBegun())
                    try {
                        users.evictSettingsCaches(actor);
                    } catch (RuntimeException ignored) {
                    }
            }
        }
    }
}
