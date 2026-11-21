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

import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.latke.ioc.Inject;
import org.b3log.symphony.service.PrivateMessageRevisionService;
import org.b3log.symphony.util.ApiV3PrivateMessages;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;
import java.util.TreeSet;

@Repository
public class ChatInfoRepository extends AbstractRepository {
    @Inject private PrivateMessageRevisionService revisions;

    /** Legacy inserts join their caller's transaction; v3 uses its own prepared mutation boundary. */
    @Override public String add(JSONObject row)throws RepositoryException {
        if(!hasTransactionBegun())throw new IllegalStateException("private message transaction required");
        try{return ApiV3TransactionSavepoint.run(()->{
            final String id=insertMessage(row);
            final var owners=new TreeSet<String>();
            for(String field:java.util.List.of("fromId","toId")){
                final String owner=row.optString(field);
                if(ApiV3PrivateMessages.validId(owner))owners.add(owner);
            }
            revisions.touchInCurrentTransaction(owners);
            return id;
        });}catch(RepositoryException e){throw e;}
        catch(Exception e){throw new RepositoryException("Private message persistence unavailable");}
    }
    /** Strict single-step root for legacy HTTP and socket callers; no outer transaction may be active. */
    public String addLegacy(JSONObject row)throws RepositoryException {
        if(hasTransactionBegun())throw new IllegalStateException("private message root required");
        try{
            final var tx=ApiV3Transactions.strict(beginTransaction());
            try{final String id=add(row);tx.commit();return id;}finally{if(tx.isActive())tx.rollback();}
        }catch(RepositoryException e){throw e;}
        catch(Exception e){throw new RepositoryException("Private message persistence unavailable");}
        finally{ApiV3Transactions.releaseThreadResources();}
    }
    protected String insertMessage(JSONObject row)throws RepositoryException{return super.add(row);}

    /**
     * Public constructor.
     */
    public ChatInfoRepository() {
        super("chat_info");
    }

}
