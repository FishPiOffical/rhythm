package org.b3log.symphony.repository;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.util.Ids;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class UserIpLeaseRepository extends AbstractRepository {
    @Inject
    private UserRepository users;

    public UserIpLeaseRepository() {
        super(UserIpLease.REPOSITORY);
    }

    public List<UserIpLease> active(final long now) throws RepositoryException {
        return select("SELECT l.userId,u.userName,l.ip,l.lastSeenAt,l.expiresAt FROM `" + getName()
                + "` l INNER JOIN `" + users.getName() + "` u ON u.oId=l.userId"
                + " WHERE l.expiresAt>? AND u.userStatus=?", now, UserExt.USER_STATUS_C_VALID).stream()
                .map(row -> new UserIpLease(row.getString("userId"), row.getString("userName"), row.getString("ip"),
                        row.getLong("lastSeenAt"), row.getLong("expiresAt"))).toList();
    }

    public void persist(final List<Change> batch) throws RepositoryException {
        if (batch.isEmpty()) return;
        final var newIds = allocateNewLeaseIds(batch);
        try {
            final var tx = ApiV3Transactions.strict(beginTransaction());
            final var connection = JdbcRepository.TX.get().getConnection();
            try (var insert = connection.prepareStatement("INSERT INTO `" + getName()
                         + "` (oId,userId,ip,lastSeenAt,expiresAt) VALUES (?,?,?,?,?)");
                 var renew = connection.prepareStatement("UPDATE `" + getName()
                         + "` SET lastSeenAt=?,expiresAt=? WHERE userId=? AND ip=?")) {
                for (final var change : batch) {
                    for (final var lease : change.current().values()) {
                        final String newId = newIds.get(lease);
                        if (newId == null) {
                            renew.setLong(1, lease.lastSeenAt());
                            renew.setLong(2, lease.expiresAt());
                            renew.setString(3, lease.userId());
                            renew.setString(4, lease.ip());
                            renew.addBatch();
                        } else {
                            insert.setString(1, newId);
                            insert.setString(2, lease.userId());
                            insert.setString(3, lease.ip());
                            insert.setLong(4, lease.lastSeenAt());
                            insert.setLong(5, lease.expiresAt());
                            insert.addBatch();
                        }
                    }
                }
                if (!newIds.isEmpty()) insert.executeBatch();
                renew.executeBatch();
            }
            removeSuperseded(batch, connection);
            tx.commit();
        } catch (final SQLException error) {
            throw new RepositoryException(error);
        } finally {
            ApiV3Transactions.releaseThreadResources();
        }
    }

    private Map<UserIpLease, String> allocateNewLeaseIds(final List<Change> batch) throws RepositoryException {
        final var userIds = batch.stream().filter(change -> !change.current().isEmpty()).map(Change::userId).toList();
        if (userIds.isEmpty()) return Map.of();
        final Map<String, Set<String>> existing = new HashMap<>();
        final String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        try {
            for (final var row : select("SELECT userId,ip FROM `" + getName()
                    + "` WHERE userId IN (" + placeholders + ")", userIds.toArray())) {
                existing.computeIfAbsent(row.getString("userId"), ignored -> new HashSet<>()).add(row.getString("ip"));
            }
        } finally {
            ApiV3Transactions.releaseThreadResources();
        }
        final Map<UserIpLease, String> newIds = new HashMap<>();
        for (final var change : batch) {
            final var addresses = existing.getOrDefault(change.userId(), Set.of());
            for (final var lease : change.current().values()) {
                if (!addresses.contains(lease.ip())) newIds.put(lease, Ids.genTimeMillisId());
            }
        }
        return newIds;
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
