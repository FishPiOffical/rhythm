package org.b3log.symphony.service;

import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserIpLease;
import org.b3log.symphony.service.UserIpLeaseService.LeaseIndex;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UserIpLeaseServiceTest {
    private static final String FIRST = "192.0.2.1";
    private static final String SECOND = "192.0.2.2";
    private static final long START = 1_000;

    @Test
    public void sharedAddressSurvivesOnlyWhileAnotherLeaseIsActive() {
        final var index = new LeaseIndex();
        index.renew("first", FIRST, Role.ROLE_ID_C_DEFAULT, START);
        index.renew("second", FIRST, Role.ROLE_ID_C_ADMIN, START);
        index.renew("first", SECOND, Role.ROLE_ID_C_DEFAULT, START + 1);
        Assert.assertTrue(index.covers(FIRST, START + 1));
        Assert.assertTrue(index.covers(SECOND, START + 1));
        final long expiry = START + TimeUnit.DAYS.toMillis(30);
        Assert.assertTrue(index.covers(FIRST, expiry - 1));
        Assert.assertFalse(index.covers(FIRST, expiry));
    }

    @Test
    public void persistedAddressIsEvictedImmediatelyAndRemovedInNextBatch() {
        final var index = new LeaseIndex();
        index.renew("account", FIRST, Role.ROLE_ID_C_DEFAULT, START);
        index.committed(index.pending(START));
        index.renew("account", SECOND, Role.ROLE_ID_C_DEFAULT, START + 1);
        Assert.assertFalse(index.covers(FIRST, START + 1));
        final var change = index.pending(START + 1).getFirst();
        Assert.assertFalse(change.current().containsKey(FIRST));
        Assert.assertEquals(change.current().get(SECOND).expiresAt(), START + 1 + TimeUnit.HOURS.toMillis(48));
    }

    @Test
    public void policyKeepsMostRecentAddressesAndPrunesAtExpiry() {
        for (final var role : new String[]{Role.ROLE_ID_C_INTRODUCTION_MEMBER, Role.ROLE_ID_C_SUPER_MEMBER,
                Role.ROLE_ID_C_DISCIPLINE_COMMITTEE, Role.ROLE_ID_C_OP, Role.ROLE_ID_C_ADMIN}) {
            final boolean member = role.equals(Role.ROLE_ID_C_INTRODUCTION_MEMBER);
            final int limit = member ? 2 : 5;
            final long ttl = member ? TimeUnit.HOURS.toMillis(72) : TimeUnit.DAYS.toMillis(30);
            final var index = new LeaseIndex();
            for (int sequence = 0; sequence <= limit; sequence++) {
                index.renew("account", "192.0.2." + sequence, role, START + sequence);
            }
            final var leases = index.pending(START + limit).getFirst().current();
            Assert.assertEquals(leases.size(), limit);
            Assert.assertFalse(leases.containsKey("192.0.2.0"));
            Assert.assertEquals(leases.get(FIRST).expiresAt(), START + 1 + ttl);
            index.committed(index.pending(START + limit));
            Assert.assertTrue(index.pending(START + limit + ttl).getFirst().current().isEmpty());
        }
    }

    @Test
    public void concurrentRenewalsCoalesceAndCommitDoesNotDropNewerRequest() throws Exception {
        final var index = new LeaseIndex();
        try (final var executor = Executors.newFixedThreadPool(8)) {
            for (int request = 0; request < 256; request++) {
                executor.submit(() -> index.renew("account", FIRST, Role.ROLE_ID_C_VISITOR));
            }
        }
        final var firstBatch = index.pending(System.currentTimeMillis());
        Assert.assertEquals(firstBatch.size(), 1);
        Assert.assertEquals(firstBatch.getFirst().current().size(), 1);
        final long newer = firstBatch.getFirst().current().get(FIRST).lastSeenAt() + 1;
        index.renew("account", FIRST, Role.ROLE_ID_C_VISITOR, newer);
        index.committed(firstBatch);
        Assert.assertEquals(index.pending(newer).getFirst().current().get(FIRST).lastSeenAt(), newer);
        index.committed(index.pending(newer));
        Assert.assertTrue(index.pending(newer).isEmpty());
    }

    @Test
    public void ipv6SpellingDoesNotConsumeExtraQuota() {
        Assert.assertEquals(UserIpLeaseService.normalize("2001:0DB8:0:0:0:0:0:0001"), "2001:db8::1");
        Assert.assertNull(UserIpLeaseService.normalize("example.com"));
        Assert.assertNull(UserIpLeaseService.normalize("192.0.2.1,192.0.2.2"));
    }

    @Test
    public void startupSnapshotPreservesLeasesAndExpiresUntouchedAccounts() {
        final var index = new LeaseIndex();
        final long expiry = START + TimeUnit.HOURS.toMillis(48);
        index.load(List.of(new UserIpLease("account", FIRST, START, expiry)));
        Assert.assertTrue(index.covers(FIRST, expiry - 1));
        Assert.assertTrue(index.pending(expiry - 1).isEmpty());
        Assert.assertFalse(index.covers(FIRST, expiry));
        final var batch = index.pending(expiry);
        Assert.assertTrue(batch.getFirst().current().isEmpty());
        index.committed(batch);
        Assert.assertTrue(index.pending(expiry).isEmpty());
    }
}
