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
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ApiV3AuthorizationRequest;
import org.json.JSONObject;
import org.b3log.symphony.util.ApiV3TransactionStatements;

@Repository
public class ApiV3AuthorizationRequestRepository extends AbstractRepository {
    public ApiV3AuthorizationRequestRepository() {
        super(ApiV3AuthorizationRequest.TABLE);
    }

    public int invalidatePendingForUserInCurrentTransaction(String userId) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("consent transaction required");
        if (userId == null || !userId.matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("account identity");
        return ApiV3TransactionStatements.execute("DELETE FROM `" + getName() + "` WHERE userId=? AND decision=?", userId, ApiV3AuthorizationRequest.PENDING);
    }

    /**
     * 仅预读必要字段，使授权决策事务能先锁客户端，再锁授权请求。
     */
    public JSONObject hintForOwner(final String hash, final String actor) throws RepositoryException {
        return one("SELECT oId,clientId,redirectUri,scope FROM `" + getName() + "` WHERE oId=? AND userId=?", hash, actor);
    }

    public JSONObject getForOwnerForUpdate(final String hash, final String actor) throws RepositoryException {
        if (!hasTransactionBegun())
            throw new IllegalStateException("consent transaction required");
        return one("SELECT * FROM `" + getName() + "` WHERE oId=? AND userId=? FOR UPDATE", hash, actor);
    }

    private JSONObject one(String sql, String hash, String actor) throws RepositoryException {
        final var rows = select(sql, hash, actor);
        if (rows.size() > 1)
            throw new RepositoryException("duplicate authorization request");
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
