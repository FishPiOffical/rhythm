package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ApiV3GameSave;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;

@Repository
public class ApiV3GameSaveRepository extends AbstractRepository {
    public ApiV3GameSaveRepository(){super(ApiV3GameSave.TABLE);}
    @Override public Transaction beginTransaction(){return ApiV3Transactions.strict(super.beginTransaction());}
    public JSONObject current(String actor,String game,boolean lock)throws RepositoryException {
        if(lock&&!hasTransactionBegun())throw new IllegalStateException("game save transaction required");
        final var rows=select("SELECT oId,userId,gameId,revision,updatedAt,byteLength,sha256,CASE WHEN OCTET_LENGTH(data)<=2097152 THEN data ELSE NULL END AS data FROM `"+getName()+"` WHERE userId=? AND gameId=? LIMIT 2"+(lock?" FOR UPDATE":""),actor,game);
        if(rows.size()>1)throw new RepositoryException("duplicate private game slot");return rows.isEmpty()?null:rows.getFirst();
    }
}
