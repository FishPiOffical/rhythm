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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import java.util.List;
import java.util.concurrent.Callable;

/** Shared root/cache/lock boundary. Legacy admission has already run before entering legacy(). */
@Service
public class ChatTopicWriteService {
    @Inject private UserRepository users;
    @Inject private ChatTopicMgmtService topics;
    @Inject private ChatTopicDeliveryService delivery;
    @Inject private PointtransferMgmtService points;
    public <T>T boundary(final String actor,final Callable<T> work)throws Exception {
        if(actor==null||!actor.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("topic actor");
        if(users.hasTransactionBegun())throw new IllegalStateException("topic root transaction already active");
        return AccountPairLocks.execute(List.of(new AccountPairLocks.Pair(actor,Pointtransfer.ID_C_SYS)),()->{
            try(final var caches=ArticlePublicationCaches.open(users::hasTransactionBegun)){
                ArticlePublicationCaches.afterTransaction("topic-user:"+actor,()->{
                    try{users.refreshSettingsCache(actor);}catch(Exception e){users.evictSettingsCaches(actor);}
                });
                return work.call();
            }
        });
    }
    public void legacy(final String actor,final String raw)throws Exception {
        final String text=ChatTopicInputs.legacy(raw);
        final var result=boundary(actor,()->{
            final Transaction transaction=ApiV3Transactions.strict(users.beginTransaction());
            try{final var changed=topics.changeInCurrentTransaction(actor,text,current->{});transaction.commit();return changed;}
            finally{if(transaction.isActive())transaction.rollback();}
        });
        afterCommitted(result);
    }
    public void afterCommitted(final ChatTopicMgmtService.Result result){
        if(users.hasTransactionBegun()||ArticlePublicationCaches.active())throw new IllegalStateException("topic delivery before commit");
        try{points.indexTransferAsync(result.actor(),Pointtransfer.ID_C_SYS,result.transferId());}
        catch(RuntimeException e){LogManager.getLogger(getClass()).error("Index committed topic fee failed",e);}
        try{delivery.refreshCommitted();}
        catch(Exception e){LogManager.getLogger(getClass()).error("Project committed topic failed",e);}
    }
}
