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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.repository.RepeaterLikeRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.math.BigDecimal;

/** Target-row serialization for explicit set and legacy toggle; caller owns the root transaction. */
@Service
public class RepeaterLikeMutationService {
    @Inject private UserRepository users;
    @Inject private RepeaterContentRepository contents;
    @Inject private RepeaterLikeRepository likes;
    public JSONObject setInCurrentTransaction(final String actor, final String itemId, final Boolean desired) throws Exception {
        id(actor); id(itemId);
        if (!contents.hasTransactionBegun()) throw new IllegalStateException("repeater like transaction required");
        return ApiV3TransactionSavepoint.run(() -> {
            owner(users, actor);
            final var targets=contents.select("SELECT oId,repeaterContentStatus FROM `"+contents.getName()+"` WHERE oId=? FOR UPDATE", itemId);
            if (targets.size()!=1 || !itemId.equals(targets.getFirst().optString("oId")) || exact(targets.getFirst(),"repeaterContentStatus")!=0) throw ApiV3Requests.notFound();
            final var existing=likes.select("SELECT oId FROM `"+likes.getName()+"` WHERE repeaterLikeContentId=? AND repeaterLikeUserId=? ORDER BY oId LIMIT 4097 FOR UPDATE",itemId,actor);
            if (existing.size()>4096) throw unavailable();
            final boolean liked=desired==null?existing.isEmpty():desired;
            if (liked && existing.isEmpty()) {
                if (ApiV3TransactionStatements.execute("INSERT INTO `"+likes.getName()+"` (oId,repeaterLikeContentId,repeaterLikeUserId,repeaterLikeCreatedTime) VALUES(?,?,?,?)",Ids.genTimeMillisId(),itemId,actor,System.currentTimeMillis())!=1) throw unavailable();
            } else if (!liked && !existing.isEmpty()) {
                if (ApiV3TransactionStatements.execute("DELETE FROM `"+likes.getName()+"` WHERE repeaterLikeContentId=? AND repeaterLikeUserId=?",itemId,actor)!=existing.size()) throw unavailable();
            } else if (liked && existing.size()>1) {
                final String keep=existing.getFirst().getString("oId");
                if (ApiV3TransactionStatements.execute("DELETE FROM `"+likes.getName()+"` WHERE repeaterLikeContentId=? AND repeaterLikeUserId=? AND oId<>?",itemId,actor,keep)!=existing.size()-1) throw unavailable();
            }
            // A locking read is required: the caller may already hold an older repeatable-read snapshot.
            // All mutations take the content row first, so this counts the latest committed rows plus our changes.
            final var count=likes.select("SELECT COUNT(*) AS total FROM `"+likes.getName()+"` WHERE repeaterLikeContentId=? FOR UPDATE",itemId);
            if (count.size()!=1) throw unavailable(); final long total=exact(count.getFirst(),"total");
            if (total<0 || total>Integer.MAX_VALUE) throw unavailable();
            if (ApiV3TransactionStatements.execute("UPDATE `"+contents.getName()+"` SET repeaterContentLikeCount=?,repeaterContentUpdatedTime=? WHERE oId=?",total,System.currentTimeMillis(),itemId)!=1) throw unavailable();
            return new JSONObject().put("id",itemId).put("liked",liked).put("likeCount",total);
        });
    }
    static void owner(final UserRepository users, final String actor) throws RepositoryException {
        final var rows=users.select("SELECT oId,userStatus FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",actor);
        if(rows.size()!=1 || !actor.equals(rows.getFirst().optString("oId")) || exact(rows.getFirst(),"userStatus")!=UserExt.USER_STATUS_C_VALID) throw ApiV3Requests.notFound();
    }
    static void id(final String id) { if(id==null || !id.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid(); }
    static long exact(final JSONObject row, final String key) {
        if(!(row.opt(key) instanceof Number number)) throw unavailable();
        try{return new BigDecimal(number.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException e){throw unavailable();}
    }
    static ApiV3Exception unavailable() { return new ApiV3Exception(503,"temporarily_unavailable","复读机操作暂不可用"); }
}
