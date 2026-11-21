package org.b3log.symphony.repository;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.model.UserIpLease;
import org.b3log.symphony.model.UserIpLease.Change;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Repository
public class UserIpLeaseRepository extends AbstractRepository {
    @Inject
    private UserRepository users;

    public UserIpLeaseRepository() {
        super(UserIpLease.REPOSITORY);
    }

    public List<UserIpLease> active(final long now) throws RepositoryException {
        return select("SELECT l.userId,l.ip,l.lastSeenAt,l.expiresAt FROM `" + getName()
                + "` l INNER JOIN `" + users.getName() + "` u ON u.oId=l.userId"
                + " WHERE l.expiresAt>? AND u.userStatus=?", now, UserExt.USER_STATUS_C_VALID).stream()
                .map(row -> new UserIpLease(row.getString("userId"), row.getString("ip"),
                        row.getLong("lastSeenAt"), row.getLong("expiresAt"))).toList();
    }

    public void persist(final List<Change> batch) throws RepositoryException {
        try {
            final var tx = ApiV3Transactions.strict(beginTransaction());
            final var connection = JdbcRepository.TX.get().getConnection();
            try (var upsert = connection.prepareStatement("INSERT INTO `" + getName()
                     + "` (oId,userId,ip,lastSeenAt,expiresAt) VALUES (?,?,?,?,?) AS incoming"
                     + " ON DUPLICATE KEY UPDATE lastSeenAt=incoming.lastSeenAt,expiresAt=incoming.expiresAt")) {
                for (final var change : batch) {
                    for (final var lease : change.current().values()) {
                        upsert.setString(1, UUID.randomUUID().toString().replace("-", ""));
                        upsert.setString(2, lease.userId());
                        upsert.setString(3, lease.ip());
                        upsert.setLong(4, lease.lastSeenAt());
                        upsert.setLong(5, lease.expiresAt());
                        upsert.addBatch();
                    }
                }
                upsert.executeBatch();
            }
            removeSuperseded(batch, connection);
            tx.commit();
        } catch (final SQLException error) {
            throw new RepositoryException(error);
        } finally {
            ApiV3Transactions.releaseThreadResources();
        }
    }

    private void removeSuperseded(final List<Change> batch, final Connection connection) throws SQLException {
        final var groups = new HashMap<Integer, List<Change>>();
        for (final var change : batch) {
            groups.computeIfAbsent(change.current().size(), ignored -> new ArrayList<>()).add(change);
        }
        for (final var group : groups.entrySet()) {
            final int size = group.getKey();
            final String keep = size == 0 ? "" : " AND ip NOT IN ("
                    + String.join(",", Collections.nCopies(size, "?")) + ")";
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM `" + getName() + "` WHERE userId=?" + keep)) {
                for (final var change : group.getValue()) {
                    delete.setString(1, change.userId());
                    int index = 2;
                    for (final String ip : change.current().keySet()) delete.setString(index++, ip);
                    delete.addBatch();
                }
                delete.executeBatch();
            }
        }
    }

    public void purgeExpired(final long now) throws RepositoryException {
        try {
            final var tx = ApiV3Transactions.strict(beginTransaction());
            try (var statement = JdbcRepository.TX.get().getConnection().prepareStatement(
                    "DELETE FROM `" + getName() + "` WHERE expiresAt<=? ORDER BY expiresAt LIMIT 1000")) {
                statement.setLong(1, now);
                statement.executeUpdate();
            }
            tx.commit();
        } catch (final SQLException error) {
            throw new RepositoryException(error);
        } finally {
            ApiV3Transactions.releaseThreadResources();
        }
    }

    public List<JSONObject> findByUserName(final String userName) throws RepositoryException {
        return select("SELECT u.userName,l.ip,l.lastSeenAt,l.expiresAt FROM `" + users.getName()
                + "` u INNER JOIN `" + getName() + "` l ON l.userId=u.oId WHERE u.userName=?"
                + " ORDER BY l.lastSeenAt DESC,l.ip", userName);
    }

    public List<JSONObject> findRecentUsers() throws RepositoryException {
        return select("WITH recent AS (SELECT l.userId,MAX(l.lastSeenAt) AS latest FROM `" + getName()
                + "` l INNER JOIN `" + users.getName() + "` u ON u.oId=l.userId"
                + " GROUP BY l.userId ORDER BY latest DESC,l.userId LIMIT 200)"
                + " SELECT u.userName,l.ip,l.lastSeenAt,l.expiresAt FROM recent r JOIN `" + getName()
                + "` l ON l.userId=r.userId JOIN `" + users.getName() + "` u ON u.oId=r.userId"
                + " ORDER BY r.latest DESC,r.userId,l.lastSeenAt DESC,l.ip");
    }
}
