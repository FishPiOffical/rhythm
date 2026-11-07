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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.model.Verifycode;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.VerifycodeRepository;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.RegistrationProofContext;

/** Phone verification shared by future account mutation cores; no session or global-code fallback. */
@Service
public class AccountVerificationService {
    @Inject private UserRepository users;
    @Inject private VerifycodeRepository codes;
    protected long now(){return System.currentTimeMillis();}

    /**
     * The owner acquires its complete namespace/account/client lock set before opening the transaction.
     * This method locks the current user, then bound codes; consumption commits only with the account write.
     * A public endpoint must also enforce the shared verification-attempt budget before calling it.
     */
    public boolean consumePhoneCodeInCurrentTransaction(String userId,String code,int purpose)throws Exception {
        return consume(userId,code,purpose,null,null,null);
    }
    public boolean consumeRegistrationCodeInCurrentTransaction(String userId,String code,String referral,String referralId,String inviteId)throws Exception {
        return consume(userId,code,Verifycode.BIZ_TYPE_C_REGISTER,referral,referralId,inviteId);
    }
    private boolean consume(String userId,String code,int purpose,String referral,String referralId,String inviteId)throws Exception {
        if(!users.hasTransactionBegun()||!codes.hasTransactionBegun())throw new IllegalStateException("account verification owner transaction required");
        if(userId==null||!userId.matches("[1-9][0-9]{0,18}")||code==null||!code.matches("[0-9]{6}")
                ||purpose!=Verifycode.BIZ_TYPE_C_REGISTER&&purpose!=Verifycode.BIZ_TYPE_C_RESET_PWD)throw new IllegalArgumentException("account verification input");
        return ApiV3TransactionSavepoint.run(()->{
            final long time=now();if(time<=0)throw new IllegalStateException("invalid verification clock");
            final var owners=users.select("SELECT oId,userPhone,userStatus"+(purpose==Verifycode.BIZ_TYPE_C_REGISTER?",userName":"")+" FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",userId);
            if(owners.size()!=1)return false;final var owner=owners.getFirst();
            final int required=purpose==Verifycode.BIZ_TYPE_C_REGISTER?UserExt.USER_STATUS_C_NOT_VERIFIED:UserExt.USER_STATUS_C_VALID;
            final String phone=owner.optString("userPhone");
            if(!userId.equals(owner.optString(Keys.OBJECT_ID))||owner.optInt(UserExt.USER_STATUS,-1)!=required||!phone.matches("1[0-9]{10}"))return false;
            final var matches=codes.phoneCodesForUpdate(userId,phone,code,purpose,time);
            if(matches.isEmpty())return false;
            boolean compatible=false;
            for(final var row:matches){
                final int status=row.optInt(Verifycode.STATUS,-1);
                if(purpose!=Verifycode.BIZ_TYPE_C_REGISTER||RegistrationProofContext.matches(row.optString(Verifycode.ACCOUNT_CONTEXT),owner.optString("userName"),referral,referralId,inviteId))compatible=true;
                if(!userId.equals(row.optString(Verifycode.USER_ID))||!phone.equals(row.optString(Verifycode.RECEIVER))
                        ||row.optInt(Verifycode.TYPE,-1)!=Verifycode.TYPE_C_PHONE||row.optInt(Verifycode.BIZ_TYPE,-1)!=purpose
                        ||status!=Verifycode.STATUS_C_UNSENT&&status!=Verifycode.STATUS_C_SENT||row.optLong(Verifycode.EXPIRED)<=time)return false;
            }
            if(!compatible)return false;
            // Same-owner, same-purpose duplicate SMS values must not permit a second activation/reset.
            for(final var row:matches)codes.remove(row.getString(Keys.OBJECT_ID));
            return true;
        });
    }
}
