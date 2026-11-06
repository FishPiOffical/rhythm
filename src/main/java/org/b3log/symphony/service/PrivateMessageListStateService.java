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
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import java.math.BigInteger;
import java.util.*;

/** Shared old/new list projection. Current committed messages determine the head, never a delayed event. */
@Service
public class PrivateMessageListStateService {
    @Inject private UserRepository users;
    @Inject private PrivateMessageRevisionService revisions;
    @Inject private ChatInfoRepository messages;
    @Inject private ChatListRepository lists;
    public static String session(String actor,String peer){
        ApiV3PrivateMessages.requireId(actor);ApiV3PrivateMessages.requireId(peer);
        if(actor.equals(peer))throw ApiV3WriteInputs.invalid();
        return new BigInteger(actor).compareTo(new BigInteger(peer))<0?actor+"_"+peer:peer+"_"+actor;
    }
    public void refreshInCurrentTransaction(String actor,String peer)throws Exception {
        final String session=session(actor,peer);
        if(!lists.hasTransactionBegun())throw new IllegalStateException("private list transaction required");
        // Same ordering as publication; also serializes old asynchronous projections with v3 writes.
        users.select("SELECT oId FROM `"+users.getName()+"` WHERE oId IN (?,?) ORDER BY oId FOR UPDATE",actor,peer);
        final var heads=messages.select("SELECT oId,time FROM `"+messages.getName()+"` WHERE ((fromId=? AND toId=?) OR (fromId=? AND toId=?)) ORDER BY oId DESC LIMIT 1 FOR UPDATE",actor,peer,peer,actor);
        if(heads.isEmpty()){
            ApiV3TransactionStatements.execute("DELETE FROM `"+lists.getName()+"` WHERE sessionId=?",session);
            return;
        }
        final var head=heads.getFirst();final String id=head.getString("oId");ApiV3PrivateMessages.requireId(id);
        final var existing=lists.select("SELECT oId FROM `"+lists.getName()+"` WHERE sessionId=? ORDER BY oId LIMIT 1 FOR UPDATE",session);
        if(existing.isEmpty())ApiV3TransactionStatements.execute("INSERT INTO `"+lists.getName()+"` (oId,sessionId,time,lastMessageId) VALUES(?,?,?,?)",Ids.genTimeMillisId(),session,head.getString("time"),id);
        else ApiV3TransactionStatements.execute("UPDATE `"+lists.getName()+"` SET time=?,lastMessageId=? WHERE sessionId=?",head.getString("time"),id,session);
    }
    /** Only the old event worker owns this root. The event's session/time/body are untrusted projection hints. */
    public void refreshLegacy(String messageId)throws Exception {
        ApiV3PrivateMessages.requireId(messageId);
        if(lists.hasTransactionBegun())throw new IllegalStateException("private list root required");
        try{
            final var tx=ApiV3Transactions.strict(lists.beginTransaction());
            try{
                final var rows=messages.select("SELECT fromId,toId FROM `"+messages.getName()+"` WHERE oId=?",messageId);
                if(!rows.isEmpty()){
                    final String from=rows.getFirst().getString("fromId"),to=rows.getFirst().getString("toId");
                    refreshInCurrentTransaction(from,to);
                    revisions.touchInCurrentTransaction(java.util.Set.of(from,to));
                }
                tx.commit();
            }finally{if(tx.isActive())tx.rollback();}
        }finally{ApiV3Transactions.releaseThreadResources();}
    }
}
