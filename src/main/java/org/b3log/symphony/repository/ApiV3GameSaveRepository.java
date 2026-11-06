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
