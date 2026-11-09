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
import org.b3log.symphony.model.ApiV3AuthorizationCode;
import org.json.JSONObject;
import org.b3log.symphony.util.ApiV3TransactionStatements;

@Repository
public class ApiV3AuthorizationCodeRepository extends AbstractRepository {
    public ApiV3AuthorizationCodeRepository() {
        super(ApiV3AuthorizationCode.TABLE);
    }

    public int invalidatePendingForUserInCurrentTransaction(String userId) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("authorization code transaction required");
        if (userId == null || !userId.matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("account identity");
        return ApiV3TransactionStatements.execute("DELETE FROM `" + getName() + "` WHERE userId=? AND consumedAt=0", userId);
    }

    /**
     * 在授权码消费事务内按哈希锁定记录，交由调用方检查并标记消费。
     */
    public JSONObject getForUpdate(final String hash) throws RepositoryException {
        if (!hasTransactionBegun())
            throw new IllegalStateException("authorization code transaction required");
        final var rows = select("SELECT * FROM `" + getName() + "` WHERE oId=? FOR UPDATE", hash);
        if (rows.size() > 1)
            throw new RepositoryException("duplicate authorization code");
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
