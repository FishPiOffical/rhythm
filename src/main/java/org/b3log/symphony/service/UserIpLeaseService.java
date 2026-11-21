package org.b3log.symphony.service;

import io.netty.util.NetUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.model.UserIpLease;
import org.b3log.symphony.model.UserIpLease.Change;
import org.b3log.symphony.repository.UserIpLeaseRepository;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

@Singleton
public class UserIpLeaseService {
    private static final Logger LOGGER = LogManager.getLogger(UserIpLeaseService.class);
    private static final int FLUSH_BATCH_SIZE = 200;
    private static final String RENEWED = UserIpLeaseService.class.getName();
    private static final LeaseIndex INDEX = new LeaseIndex();

    @Inject
    private UserIpLeaseRepository repository;

    public void start() throws RepositoryException {
        try {
            INDEX.load(repository.active(System.currentTimeMillis()));
        } finally {
            ApiV3Transactions.releaseThreadResources();
        }
        Symphonys.SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(this::flush, 1, 1, TimeUnit.MINUTES);
    }

    public static boolean isIpCovered(final String source) {
        return INDEX.covers(normalize(source), System.currentTimeMillis());
    }

    public static UserIpLease matchingLease(final String source) {
        return INDEX.match(normalize(source), System.currentTimeMillis());
    }

    public static void renew(final JSONObject user, final RequestContext context) {
        if (user == null || user.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID
                || context.getRequest().isStaticResource() || "OPTIONS".equals(context.method())) return;
        final String userId = user.optString(Keys.OBJECT_ID);
        if (userId.isBlank() || userId.equals(context.attr(RENEWED))) return;
        final String ip = normalize(Requests.getRemoteAddr(context));
        if (ip == null) return;
        INDEX.renew(userId, user.optString(User.USER_NAME), ip, user.optString(User.USER_ROLE));
        context.attr(RENEWED, userId);
    }

    static String normalize(final String source) {
        if (source == null || source.length() > 45 || source.indexOf('%') >= 0 || source.indexOf('[') >= 0) return null;
        if (NetUtil.isValidIpV4Address(source)) return source;
        final byte[] address = NetUtil.isValidIpV6Address(source) ? NetUtil.createByteArrayFromIpAddressString(source) : null;
        return address == null ? null : NetUtil.bytesToIpAddress(address);
    }

    private void flush() {
        final long now = System.currentTimeMillis();
        try {
            final List<Change> changes = INDEX.pending(now);
            for (int start = 0; start < changes.size(); start += FLUSH_BATCH_SIZE) {
                final var batch = changes.subList(start, Math.min(start + FLUSH_BATCH_SIZE, changes.size()));
                repository.persist(batch);
                INDEX.committed(batch);
            }
            repository.purgeExpired(now);
        } catch (final Exception error) {
            LOGGER.error("账号 IP 租约批量写入失败，将在下次重试", error);
        }
    }

    public List<JSONObject> findByUserName(final String userName) throws RepositoryException {
        return repository.findByUserName(userName);
    }

    public List<JSONObject> findRecentUsers() throws RepositoryException {
        return repository.findRecentUsers();
    }

    private record Policy(int limit, long ttl) {
        static Policy forRole(final String role) {
            return switch (role) {
                case Role.ROLE_ID_C_INTRODUCTION_MEMBER -> new Policy(2, TimeUnit.HOURS.toMillis(72));
                case Role.ROLE_ID_C_SUPER_MEMBER, Role.ROLE_ID_C_DISCIPLINE_COMMITTEE,
                     Role.ROLE_ID_C_OP, Role.ROLE_ID_C_ADMIN -> new Policy(5, TimeUnit.DAYS.toMillis(30));
                default -> new Policy(1, TimeUnit.HOURS.toMillis(48));
            };
        }
    }

    static final class LeaseIndex {
        private static final Comparator<UserIpLease> RECENCY = Comparator.comparingLong(UserIpLease::lastSeenAt)
                .thenComparing(UserIpLease::ip);
        private static final Comparator<UserIpLease> COVERAGE = Comparator.comparingLong(UserIpLease::expiresAt)
                .thenComparingLong(UserIpLease::lastSeenAt).thenComparing(UserIpLease::userId);
        private final Map<String, Map<String, UserIpLease>> owners = new HashMap<>();
        private final Map<String, NavigableSet<UserIpLease>> leasesByIp = new HashMap<>();
        private final TreeMap<Long, Set<UserIpLease>> expiryQueue = new TreeMap<>();
        private final Set<String> dirty = new HashSet<>();

        synchronized void load(final List<UserIpLease> leases) {
            for (final UserIpLease lease : leases) {
                owners.computeIfAbsent(lease.userId(), ignored -> new HashMap<>()).put(lease.ip(), lease);
                addIp(lease);
            }
        }

        synchronized void renew(final String userId, final String userName, final String ip, final String role) {
            renew(userId, userName, ip, role, System.currentTimeMillis());
        }

        synchronized boolean covers(final String ip, final long now) {
            return match(ip, now) != null;
        }

        synchronized UserIpLease match(final String ip, final long now) {
            final var leases = leasesByIp.get(ip);
            if (leases == null) return null;
            final UserIpLease latest = leases.last();
            return latest.expiresAt() > now ? latest : null;
        }

        synchronized void renew(final String userId, final String userName, final String ip, final String role, final long now) {
            final Policy policy = Policy.forRole(role);
            final var leases = owners.computeIfAbsent(userId, ignored -> new HashMap<>());
            prune(leases, now);
            leases.replaceAll((address, lease) -> {
                final long expiresAt = Math.min(lease.expiresAt(), lease.lastSeenAt() + policy.ttl());
                if (lease.expiresAt() == expiresAt && lease.userName().equals(userName)) return lease;
                removeIp(lease);
                final var adjusted = new UserIpLease(userId, userName, address, lease.lastSeenAt(), expiresAt);
                addIp(adjusted);
                return adjusted;
            });
            prune(leases, now);
            final UserIpLease existing = leases.get(ip);
            if (existing != null) removeIp(existing);
            final var renewed = new UserIpLease(userId, userName, ip, now, now + policy.ttl());
            leases.put(ip, renewed);
            addIp(renewed);
            while (leases.size() > policy.limit()) {
                final var oldest = leases.values().stream().filter(lease -> lease != renewed)
                        .min(RECENCY).orElseThrow();
                leases.remove(oldest.ip());
                removeIp(oldest);
            }
            dirty.add(userId);
        }

        synchronized List<Change> pending(final long now) {
            while (!expiryQueue.isEmpty() && expiryQueue.firstKey() <= now) {
                for (final var lease : expiryQueue.pollFirstEntry().getValue()) {
                    final var leases = owners.get(lease.userId());
                    leases.remove(lease.ip());
                    if (leases.isEmpty()) owners.remove(lease.userId());
                    removeFromIpIndex(lease);
                    dirty.add(lease.userId());
                }
            }
            final var changes = new ArrayList<Change>(dirty.size());
            for (final String userId : dirty) {
                changes.add(new Change(userId, Map.copyOf(owners.getOrDefault(userId, Map.of()))));
            }
            return changes;
        }

        synchronized void committed(final List<Change> changes) {
            for (final Change change : changes) {
                if (change.current().equals(owners.getOrDefault(change.userId(), Map.of()))) dirty.remove(change.userId());
            }
        }

        private void prune(final Map<String, UserIpLease> leases, final long now) {
            leases.values().removeIf(lease -> {
                if (lease.expiresAt() > now) return false;
                removeIp(lease);
                return true;
            });
        }

        private void addIp(final UserIpLease lease) {
            leasesByIp.computeIfAbsent(lease.ip(), ignored -> new TreeSet<>(COVERAGE)).add(lease);
            expiryQueue.computeIfAbsent(lease.expiresAt(), ignored -> new HashSet<>()).add(lease);
        }

        private void removeIp(final UserIpLease lease) {
            final var leases = expiryQueue.get(lease.expiresAt());
            leases.remove(lease);
            if (leases.isEmpty()) expiryQueue.remove(lease.expiresAt());
            removeFromIpIndex(lease);
        }

        private void removeFromIpIndex(final UserIpLease lease) {
            final var leases = leasesByIp.get(lease.ip());
            leases.remove(lease);
            if (leases.isEmpty()) leasesByIp.remove(lease.ip());
        }
    }
}
