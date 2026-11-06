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
import org.b3log.symphony.model.ApiV3SocketTicket;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;

@Repository
public class ApiV3SocketTicketRepository extends AbstractRepository {
    public ApiV3SocketTicketRepository() { super(ApiV3SocketTicket.REPOSITORY); }
    @Override public Transaction beginTransaction() { return ApiV3Transactions.strict(super.beginTransaction()); }
    public JSONObject getForUpdate(final String hash) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("socket ticket transaction required");
        final var rows = select("SELECT * FROM `" + getName() + "` WHERE oId=? FOR UPDATE", hash);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public java.util.List<JSONObject> expiredBatchForUpdate(final long cutoff) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("socket ticket transaction required");
        return select("SELECT oId FROM `" + getName() + "` WHERE expiresAt<? ORDER BY expiresAt,oId LIMIT 200 FOR UPDATE",cutoff);
    }
}
