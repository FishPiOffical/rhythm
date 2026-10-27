package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.LivenessRewardClaim;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class ApiV3IdempotencyMgmtServiceTest {
    @Test
    public void successfulRetryReturnsSameResultWithoutRepeatingMutation() throws Exception {
        final Store store = new Store();
        final JSONObject a = run(store, "user", "create", "secret-key", new JSONObject("{\"b\":2,\"a\":1}"));
        final JSONObject b = run(store, "user", "create", "secret-key", new JSONObject("{\"a\":1,\"b\":2}"));
        Assert.assertEquals(a.toString(), b.toString());
        Assert.assertEquals(store.value, 1);
        Assert.assertFalse(store.rows.toString().contains("secret-key"));
    }

    @Test
    public void differentPayloadConflictsButUsersAndOperationsAreIndependent() throws Exception {
        final Store store = new Store();
        run(store, "user", "create", "key", new JSONObject().put("value", 1));
        try {
            run(store, "user", "create", "key", new JSONObject().put("value", 2));
            Assert.fail("Expected conflict");
        } catch (final ApiV3Exception e) { Assert.assertEquals(e.status, 409); }
        Assert.assertEquals(store.value, 1);
        run(store, "other", "create", "key", new JSONObject());
        run(store, "user", "update:1", "key", new JSONObject());
        Assert.assertEquals(store.value, 3);
    }

    @Test
    public void failedBusinessOperationRollsBackMutationAndKeyTogether() throws Exception {
        final Store store = new Store();
        try {
            ApiV3IdempotencyMgmtService.runAtomic(store, "user", "create", "key", new JSONObject(), () -> {
                store.increment();
                throw new ApiV3Exception(422, "invalid_request", "invalid");
            });
            Assert.fail("Expected failure");
        } catch (final ApiV3Exception expected) { }
        Assert.assertEquals(store.value, 0);
        Assert.assertTrue(store.rows.isEmpty());
        run(store, "user", "create", "key", new JSONObject());
        Assert.assertEquals(store.value, 1);
    }

    @Test
    public void resultPersistenceFailureRollsBackThenRetriesWholeTransaction() throws Exception {
        final Store store = new Store();
        store.failUpdateOnce = true;
        run(store, "user", "create", "key", new JSONObject());
        Assert.assertEquals(store.value, 1);
        Assert.assertEquals(store.begins, 2);
    }

    @Test
    public void uncertainCommitReadsCommittedResultOnRetry() throws Exception {
        final Store store = new Store();
        store.failAfterCommitOnce = true;
        run(store, "user", "create", "key", new JSONObject());
        Assert.assertEquals(store.value, 1);
        Assert.assertEquals(store.begins, 2);
    }

    @Test
    public void concurrentIdenticalRequestsCommitOnce() throws Exception {
        final Store store = new Store();
        try (final var executor = Executors.newFixedThreadPool(8)) {
            final List<Callable<JSONObject>> calls = new ArrayList<>();
            for (int i = 0; i < 32; i++) calls.add(() -> run(store, "user", "create", "key", new JSONObject()));
            for (final var result : executor.invokeAll(calls)) Assert.assertEquals(result.get().getInt("value"), 1);
        }
        Assert.assertEquals(store.value, 1);
        Assert.assertEquals(store.rows.size(), 1);
    }

    private JSONObject run(final Store store, final String user, final String operation, final String key, final JSONObject body) throws Exception {
        return ApiV3IdempotencyMgmtService.runAtomic(store, user, operation, key, body, () -> new JSONObject().put("value", store.increment()));
    }

    /** Transactional in-memory adapter: tests actual engine code, not SQL locking behavior. */
    private static final class Store implements ApiV3MutationStore {
        private final ReentrantLock lock = new ReentrantLock();
        private final ThreadLocal<Tx> current = new ThreadLocal<>();
        private Map<String, JSONObject> rows = new HashMap<>();
        private int value;
        private int begins;
        private boolean failUpdateOnce;
        private boolean failAfterCommitOnce;
        @Override public Transaction beginTransaction() {
            lock.lock();
            begins++;
            final Tx tx = new Tx();
            current.set(tx);
            return tx;
        }
        @Override public boolean hasTransactionBegun() { return null != current.get(); }
        @Override public JSONObject getForUpdate(final String id) { return current.get().staged.get(id); }
        @Override public String add(final JSONObject row) {
            final String id = row.getString(Keys.OBJECT_ID);
            current.get().staged.put(id, new JSONObject(row.toString()));
            return id;
        }
        @Override public void update(final String id, final JSONObject row, final String... properties) throws RepositoryException {
            if (failUpdateOnce) { failUpdateOnce = false; throw new RepositoryException("simulated storage failure"); }
            current.get().staged.put(id, new JSONObject(row.toString()));
        }
        private int increment() { return ++current.get().stagedValue; }
        private final class Tx implements Transaction {
            private final Map<String, JSONObject> staged = new HashMap<>();
            private int stagedValue = value;
            private boolean active = true;
            private Tx() { for (final var entry : rows.entrySet()) staged.put(entry.getKey(), new JSONObject(entry.getValue().toString())); }
            @Override public void commit() {
                rows = staged;
                value = stagedValue;
                finish();
                if (failAfterCommitOnce) {
                    failAfterCommitOnce = false;
                    throw new IllegalStateException(new RepositoryException("simulated lost commit acknowledgment"));
                }
            }
            @Override public void rollback() { finish(); }
            @Override public boolean isActive() { return active; }
            private void finish() { active = false; current.remove(); lock.unlock(); }
        }
    }

    @Test
    public void outcomeDistinguishesFreshCommitFromReplayAndUncertainCommit() throws Exception {
        final Store store = new Store();
        final var first = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(store, "user", "broadcast", "key", new JSONObject(), () -> new JSONObject().put("id", "one"));
        final var replay = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(store, "user", "broadcast", "key", new JSONObject(), () -> { throw new AssertionError("replayed mutation"); });
        Assert.assertTrue(first.applied()); Assert.assertFalse(replay.applied()); Assert.assertEquals(replay.result().getString("id"), "one");
        final Store uncertain = new Store(); uncertain.failAfterCommitOnce = true;
        final var recovered = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(uncertain, "user", "broadcast", "key", new JSONObject(), () -> new JSONObject().put("id", "two"));
        Assert.assertFalse(recovered.applied()); Assert.assertEquals(recovered.result().getString("id"), "two");
    }

    @Test
    public void dateBoundRewardReplaysBeforeFreshDateGuard() throws Exception {
        final Store store = new Store();
        final long[] now = {java.time.Instant.parse("2026-10-17T04:00:00Z").toEpochMilli()};
        final var zone = java.time.ZoneId.of("Asia/Shanghai");
        final JSONObject body = new JSONObject().put("expectedDate", "20261016");
        final ApiV3IdempotencyMgmtService.Mutation mutation = () -> LivenessRewardClaim.claim(new LivenessRewardClaim.Store() {
            public void lockOwner() { Assert.assertTrue(store.hasTransactionBegun()); }
            public boolean alreadyClaimed(LivenessRewardClaim.Period period) { return false; }
            public int amount(LivenessRewardClaim.Period period) { return 50; }
            public String transfer(int amount, long at) { return "transfer-" + store.increment(); }
        }, () -> now[0], zone, body.getString("expectedDate")).dto();
        final var first = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(store, "owner", "activity.yesterday-reward", "dated", body, mutation);
        now[0] += 86_400_000L;
        final var replay = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(store, "owner", "activity.yesterday-reward", "dated", body, mutation);
        Assert.assertTrue(first.applied()); Assert.assertFalse(replay.applied()); Assert.assertEquals(replay.result().getString("date"), "20261016"); Assert.assertEquals(store.value, 1);
        try { ApiV3IdempotencyMgmtService.runAtomicWithOutcome(store, "owner", "activity.yesterday-reward", "fresh-expired", body, mutation); Assert.fail(); }
        catch (LivenessRewardClaim.DateChanged expected) { Assert.assertEquals(store.value, 1); Assert.assertEquals(store.rows.size(), 1); }
        try { ApiV3IdempotencyMgmtService.runAtomicWithOutcome(store, "owner", "activity.yesterday-reward", "dated", new JSONObject().put("expectedDate", "20261017"), mutation); Assert.fail(); }
        catch (ApiV3Exception expected) { Assert.assertEquals(store.value, 1); }
    }

    public static void main(final String[] args) throws Exception {
        final var test = new ApiV3IdempotencyMgmtServiceTest();
        test.successfulRetryReturnsSameResultWithoutRepeatingMutation();
        test.differentPayloadConflictsButUsersAndOperationsAreIndependent();
        test.failedBusinessOperationRollsBackMutationAndKeyTogether();
        test.resultPersistenceFailureRollsBackThenRetriesWholeTransaction();
        test.uncertainCommitReadsCommittedResultOnRetry();
        test.concurrentIdenticalRequestsCommitOnce();
        test.outcomeDistinguishesFreshCommitFromReplayAndUncertainCommit();
        test.dateBoundRewardReplaysBeforeFreshDateGuard();
        System.out.println("PASS: 8 ApiV3Idempotency tests (including 32 concurrent requests; SQL integration not covered)");
    }
}
