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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.symphony.model.ApiV3ChatEvent;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ApiV3ChatEventRepository extends AbstractRepository {
    @Inject
    private ApiV3ChatStateRepository state;

    public ApiV3ChatEventRepository() {
        super(ApiV3ChatEvent.REPOSITORY);
    }

    @Override
    public Transaction beginTransaction() {
        return ApiV3Transactions.strict(super.beginTransaction());
    }

    protected Connection currentConnection() {
        final var transaction = JdbcRepository.TX.get();
        if (transaction == null || !transaction.isActive())
            throw new IllegalStateException("chat event transaction required");
        return transaction.getConnection();
    }

    /**
     * The locked room head serializes revision assignment through durable commit, not message-ID creation time.
     */
    public long appendInCurrentTransaction(String messageId, String kind) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("chat event transaction required");
        if (!ApiV3ChatEvent.validChange(kind, messageId)) throw new IllegalArgumentException("invalid chat change");
        final long previous = state.lockHeadInCurrentTransaction();
        if (previous == Long.MAX_VALUE) throw new RepositoryException("chat event revision exhausted");
        final long revision = previous + 1, now = System.currentTimeMillis();
        try (var statement = currentConnection().prepareStatement("INSERT INTO `" + getName() + "` (oId,revision,messageId,kind,createdAt) VALUES(?,?,?,?,?)")) {
            statement.setString(1, Long.toString(revision));
            statement.setLong(2, revision);
            statement.setString(3, messageId);
            statement.setString(4, kind);
            statement.setLong(5, now);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(e);
        }
        state.update(ApiV3ChatEvent.ROOM, new JSONObject().put("revision", revision).put("updatedAt", now), "revision", "updatedAt");
        return revision;
    }

    public long head() throws RepositoryException {
        return state.head();
    }

    /**
     * 按调用方固定的上界读取已提交事件，供轮询按顺序补齐游标。
     */
    public List<JSONObject> after(long revision, long through, int limit) throws RepositoryException {
        if (hasTransactionBegun())
            throw new IllegalStateException("chat snapshot inside writer transaction");
        if (revision < 0 || through < revision || limit < 1 || limit > 200)
            throw new IllegalArgumentException("invalid chat event range");
        return select("SELECT revision,messageId,kind FROM `" + getName()
                + "` WHERE revision>? AND revision<=? ORDER BY revision ASC LIMIT ?", revision, through, limit);
    }

    /**
     * 每批锁定并清理过期事件，提交后才返回删除数。
     */
    public int removeExpired(long cutoff) throws RepositoryException {
        if (hasTransactionBegun())
            throw new IllegalStateException("chat cleanup inside writer transaction");
        final var tx = beginTransaction();
        try {
            final var rows = select("SELECT oId FROM `" + getName()
                    + "` WHERE createdAt<? ORDER BY revision ASC LIMIT 200 FOR UPDATE", cutoff);
            for (final var row : rows)
                remove(row.getString("oId"));
            tx.commit();
            return rows.size();
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }
}
