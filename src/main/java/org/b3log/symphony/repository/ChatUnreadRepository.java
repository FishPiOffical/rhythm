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

@Repository
public class ChatUnreadRepository extends AbstractRepository {
    @Inject private PrivateMessageRevisionService revisions;

    @Override public String add(JSONObject row)throws RepositoryException {
        requireTransaction();
        try{return ApiV3TransactionSavepoint.run(()->{
            final String id=insertUnread(row);
            final String owner=row.optString("toId");
            if(ApiV3PrivateMessages.validId(owner))revisions.touchInCurrentTransaction(java.util.Set.of(owner));
            return id;
        });}catch(RepositoryException e){throw e;}
        catch(Exception e){throw new RepositoryException("Private unread persistence unavailable");}
    }
    /** Constrain even a broad legacy query to the authenticated owner. */
    public void removeForOwnerInCurrentTransaction(String owner,Query query)throws RepositoryException {
        requireTransaction();ApiV3PrivateMessages.requireId(owner);
        try{ApiV3TransactionSavepoint.run(()->{
            final var guard=new PropertyFilter("toId",FilterOperator.EQUAL,owner);
            removeUnread(new Query().setFilter(query.getFilter()==null?guard:CompositeFilterOperator.and(guard,query.getFilter())));
            revisions.touchInCurrentTransaction(java.util.Set.of(owner));
            return null;
        });}catch(RepositoryException e){throw e;}
        catch(Exception e){throw new RepositoryException("Private unread persistence unavailable");}
    }
    /** One legacy unread step; the entire legacy send still has separate message/list steps. */
    public String addLegacy(JSONObject row)throws RepositoryException {
        if(hasTransactionBegun())throw new IllegalStateException("private unread root required");
        try{
            final var tx=ApiV3Transactions.strict(beginTransaction());
            try{final String id=add(row);tx.commit();return id;}finally{if(tx.isActive())tx.rollback();}
        }catch(RepositoryException e){throw e;}
        catch(Exception e){throw new RepositoryException("Private unread persistence unavailable");}
        finally{ApiV3Transactions.releaseThreadResources();}
    }
    private void requireTransaction(){if(!hasTransactionBegun())throw new IllegalStateException("private unread transaction required");}
    protected String insertUnread(JSONObject row)throws RepositoryException{return super.add(row);}
    protected void removeUnread(Query query)throws RepositoryException{super.remove(query);}

    /**
     * Public constructor.
     */
    public ChatUnreadRepository() {
        super("chat_unread");
    }

}
