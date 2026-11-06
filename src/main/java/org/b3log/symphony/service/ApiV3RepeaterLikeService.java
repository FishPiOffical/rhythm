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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

@Service
public class ApiV3RepeaterLikeService {
    @Inject private UserRepository users;
    @Inject private ApiV3RequestRepository requests;
    @Inject private RepeaterLikeMutationService mutation;
    public JSONObject set(final String actor, final String itemId, final boolean liked, final String key) throws Exception {
        RepeaterLikeMutationService.id(actor); RepeaterLikeMutationService.id(itemId);
        try {
            final ApiV3MutationStore guarded=new ApiV3MutationStore(){
                public Transaction beginTransaction(){return ApiV3Transactions.strict(requests.beginTransaction());}
                public boolean hasTransactionBegun(){return requests.hasTransactionBegun();}
                public JSONObject getForUpdate(final String id)throws RepositoryException{RepeaterLikeMutationService.owner(users,actor);return requests.getForUpdate(id);}
                public String add(final JSONObject row)throws RepositoryException{return requests.add(row);}
                public void update(final String id,final JSONObject row,final String...fields)throws RepositoryException{requests.update(id,row,fields);}
            };
            final JSONObject stored=ApiV3IdempotencyMgmtService.runAtomic(guarded,actor,"repeater.like",key,new JSONObject().put("id",itemId).put("liked",liked),()->mutation.setInCurrentTransaction(actor,itemId,liked));
            if(!itemId.equals(stored.optString("id")) || !(stored.opt("liked") instanceof Boolean) || RepeaterLikeMutationService.exact(stored,"likeCount")<0 || RepeaterLikeMutationService.exact(stored,"likeCount")>Integer.MAX_VALUE) throw RepeaterLikeMutationService.unavailable();
            return new JSONObject().put("id",itemId).put("liked",stored.getBoolean("liked")).put("likeCount",stored.getLong("likeCount"));
        }catch(final ApiV3Exception e){throw e;}catch(final Exception e){throw RepeaterLikeMutationService.unavailable();}
    }
}
