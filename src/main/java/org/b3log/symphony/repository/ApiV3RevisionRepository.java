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
import org.b3log.latke.repository.jdbc.JdbcRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * Shared committed invalidation storage; mutation and marker must use the same caller-owned transaction.
 */
public abstract class ApiV3RevisionRepository extends AbstractRepository {
    protected ApiV3RevisionRepository(String repository) {
        super(repository);
    }

    protected Connection currentConnection() {
        final var transaction = JdbcRepository.TX.get();
        if (transaction == null || !transaction.isActive())
            throw new IllegalStateException("revision transaction required");
        return transaction.getConnection();
    }

    protected String upsertSql() {
        return "INSERT INTO `" + getName() + "` (oId,revision,updatedAt) VALUES(?,?,?)"
                + " ON DUPLICATE KEY UPDATE revision=VALUES(revision),updatedAt=VALUES(updatedAt)";
    }

    /**
     * Join the resource's transaction; the marker is observable only with its committed writes.
     */
    public void touchInCurrentTransaction(String owner) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("revision transaction required");
        if (owner == null || owner.isEmpty()) return;
        try (var statement = currentConnection().prepareStatement(upsertSql())) {
            statement.setString(1, owner);
            statement.setString(2, UUID.randomUUID().toString().replace("-", ""));
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * 在写事务之外分批查询，只读取已提交的修订标记，所有 ID 均通过参数绑定。
     */
    public Map<String, String> snapshot(Collection<String> owners) throws RepositoryException {
        if (hasTransactionBegun())
            throw new IllegalStateException("revision snapshot inside writer transaction");
        final var result = new HashMap<String, String>();
        final var ids = new ArrayList<>(new LinkedHashSet<>(owners));
        for (int start = 0; start < ids.size(); start += 200) {
            final var batch = ids.subList(start, Math.min(start + 200, ids.size()));
            for (final var row : select("SELECT oId,revision FROM `" + getName()
                    + "` WHERE oId IN (" + String.join(",", Collections.nCopies(batch.size(), "?")) + ")", batch.toArray()))
                result.put(row.getString("oId"), row.getString("revision"));
        }
        return result;
    }
}
