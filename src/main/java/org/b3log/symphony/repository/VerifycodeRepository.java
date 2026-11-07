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
package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.Verifycode;
import java.util.List;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.RegistrationProofContext;
import org.json.JSONObject;
import org.b3log.latke.repository.RepositoryException;

/**
 * Verifycode repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Jul 3, 2015
 * @since 1.3.0
 */
@Repository
public class VerifycodeRepository extends AbstractRepository {

    /**
     * Public constructor.
     */
    public VerifycodeRepository() {
        super(Verifycode.VERIFYCODE);
    }

    /** Fixed phone-account proof insert without the framework's raw driver-exception logging. */
    public String addAccountPhoneCodeInCurrentTransaction(JSONObject row)throws RepositoryException {
        return addAccountPhoneCode(row, "");
    }
    public String addRegistrationPhoneCodeInCurrentTransaction(JSONObject row,String name,String referral,String referralId,String inviteId)throws RepositoryException {
        if(row==null||row.optInt(Verifycode.BIZ_TYPE,-1)!=Verifycode.BIZ_TYPE_C_REGISTER)throw new IllegalArgumentException("registration proof purpose");
        return addAccountPhoneCode(row,RegistrationProofContext.encode(name,referral,referralId,inviteId));
    }
    private String addAccountPhoneCode(JSONObject row,String context)throws RepositoryException {
        if(!hasTransactionBegun())throw new IllegalStateException("verification owner transaction required");
        if(row==null||!row.keySet().equals(java.util.Set.of("userId","type","bizType","receiver","code","status","expired"))
                ||!(row.opt("userId") instanceof String actor)||!actor.matches("[1-9][0-9]{0,18}")
                ||!(row.opt("receiver") instanceof String phone)||!phone.matches("1[0-9]{10}")
                ||!(row.opt("code") instanceof String code)||!code.matches("[0-9]{6}")
                ||row.optInt(Verifycode.TYPE,-1)!=Verifycode.TYPE_C_PHONE
                ||row.optInt(Verifycode.BIZ_TYPE,-1)!=Verifycode.BIZ_TYPE_C_REGISTER&&row.optInt(Verifycode.BIZ_TYPE,-1)!=Verifycode.BIZ_TYPE_C_RESET_PWD
                ||row.optInt(Verifycode.STATUS,-1)!=Verifycode.STATUS_C_SENT||row.optLong(Verifycode.EXPIRED)<=0)throw new IllegalArgumentException("invalid account phone proof");
        final String id=Ids.genTimeMillisId();
        if(context.isEmpty())ApiV3TransactionStatements.execute("INSERT INTO `"+getName()+"` (oId,userId,type,bizType,receiver,code,status,expired) VALUES(?,?,?,?,?,?,?,?)",
                id,actor,Verifycode.TYPE_C_PHONE,row.getInt(Verifycode.BIZ_TYPE),phone,code,Verifycode.STATUS_C_SENT,row.getLong(Verifycode.EXPIRED));
        else ApiV3TransactionStatements.execute("INSERT INTO `"+getName()+"` (oId,userId,type,bizType,receiver,code,status,expired,accountContext) VALUES(?,?,?,?,?,?,?,?,?)",
                id,actor,Verifycode.TYPE_C_PHONE,row.getInt(Verifycode.BIZ_TYPE),phone,code,Verifycode.STATUS_C_SENT,row.getLong(Verifycode.EXPIRED),context);
        return id;
    }

    /** Account writes must not resolve a six-digit code globally or across business purposes. */
    public List<JSONObject> phoneCodesForUpdate(String userId,String phone,String code,int purpose,long now)throws RepositoryException {
        if(!hasTransactionBegun())throw new IllegalStateException("verification owner transaction required");
        return select("SELECT oId,userId,receiver,type,bizType,status,expired"+(purpose==Verifycode.BIZ_TYPE_C_REGISTER?",accountContext":"")+" FROM `"+getName()+"`"
                +" WHERE userId=? AND receiver=? AND type=? AND bizType=? AND code=?"
                +" AND status IN (?,?) AND expired>? ORDER BY oId FOR UPDATE",userId,phone,Verifycode.TYPE_C_PHONE,purpose,code,
                Verifycode.STATUS_C_UNSENT,Verifycode.STATUS_C_SENT,now);
    }
}
