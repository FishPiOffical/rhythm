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
