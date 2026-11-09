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
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ApiV3Token;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import java.sql.Connection;
import java.sql.SQLException;
import org.json.JSONObject;


@Repository
public class ApiV3TokenRepository extends AbstractRepository {
    public ApiV3TokenRepository() {
        super(ApiV3Token.API_V3_TOKEN);
    }
    @Override public Transaction beginTransaction() { return ApiV3Transactions.strict(super.beginTransaction()); }

    public JSONObject getByHash(final String tokenHash) throws RepositoryException {
        return getFirst(new Query().setFilter(new PropertyFilter(ApiV3Token.TOKEN_HASH,
                FilterOperator.EQUAL, tokenHash)).setPageCount(1));
    }

    public JSONObject getByHashForUpdate(final String tokenHash) throws RepositoryException {
        if (!hasTransactionBegun())
            throw new IllegalStateException("token transaction required");
        final var rows = select("SELECT * FROM `" + getName() + "` WHERE tokenHash=? FOR UPDATE", tokenHash);
        if (rows.size() > 1)
            throw new RepositoryException("duplicate v3 token hash");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    protected Connection currentConnection() {
        final var transaction = JdbcRepository.TX.get();
        if (transaction == null || !transaction.isActive())
            throw new IllegalStateException("token transaction required");
        return transaction.getConnection();
    }
    /** The reset owner holds client and current user locks; do not paginate account revocation. */
    public int revokeUserInCurrentTransaction(String userId,long now)throws RepositoryException {
        if(!hasTransactionBegun())throw new IllegalStateException("token transaction required");
        if(userId==null||!userId.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("account identity");
        return ApiV3TransactionStatements.execute("UPDATE `"+getName()+"` SET state=?,updatedAt=? WHERE userId=? AND state=?",
                ApiV3Token.STATE_REVOKED,now,userId,ApiV3Token.STATE_ACTIVE);
    }
    /** 在同一事务中撤销指定客户端、用户和家族的全部有效令牌，不分页。 */
    public int revokeFamilyInCurrentTransaction(final String clientId, final String userId, final String familyId,
                                                final long now) throws RepositoryException {
        if (!hasTransactionBegun())
            throw new IllegalStateException("token transaction required");
        if (clientId == null || clientId.isBlank() || userId == null || userId.isBlank() || familyId == null || familyId.isBlank())
            throw new IllegalArgumentException("token family");
        try (var statement = currentConnection().prepareStatement("UPDATE `" + getName()
                + "` SET state=?,updatedAt=? WHERE clientId=? AND userId=? AND familyId=? AND state=?")) {
            statement.setInt(1, ApiV3Token.STATE_REVOKED);
            statement.setLong(2, now);
            statement.setString(3, clientId);
            statement.setString(4, userId);
            statement.setString(5, familyId);
            statement.setInt(6, ApiV3Token.STATE_ACTIVE);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }
    /** 只更新仍有效且未过期的访问令牌，避免旧鉴权快照影响已撤销的记录。 */
    public void markAccessUsedInCurrentTransaction(final String id, final long now) throws RepositoryException {
        if (!hasTransactionBegun())
            throw new IllegalStateException("token transaction required");
        try (var statement = currentConnection().prepareStatement("UPDATE `" + getName()
                + "` SET lastUsedAt=?,updatedAt=? WHERE oId=? AND tokenType=? AND state=? AND expiresAt>?")) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            statement.setString(3, id);
            statement.setString(4, ApiV3Token.TYPE_ACCESS);
            statement.setInt(5, ApiV3Token.STATE_ACTIVE);
            statement.setLong(6, now);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

}
