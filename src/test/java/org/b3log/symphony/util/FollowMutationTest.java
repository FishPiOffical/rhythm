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
package org.b3log.symphony.util;

import org.b3log.latke.Keys;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Follow;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests the actual shared transition, with an isolated transactional store. */
public class FollowMutationTest {
    @Test public void allKindsAreStateIdempotentAndNeverDecrementAbsentRelations() throws Exception {
        for (int type = 0; type <= 3; type++) {
            final Store store = new Store(); store.active = true;
            Assert.assertTrue(FollowMutation.set(store, "owner", "target", type, true, null));
            Assert.assertFalse(FollowMutation.set(store, "owner", "target", type, true, null));
            Assert.assertEquals(store.rows.size(), 1); Assert.assertEquals(store.counter, type == 0 ? 0 : 1);
            Assert.assertTrue(FollowMutation.set(store, "owner", "target", type, false, null));
            Assert.assertFalse(FollowMutation.set(store, "owner", "target", type, false, null));
            Assert.assertTrue(store.rows.isEmpty()); Assert.assertEquals(store.counter, 0);
        }
    }
    @Test public void validatesLockedTargetBeforeAnyChangeAndCanRemoveDeletedTarget() throws Exception {
        final Store store = new Store(); store.active = true;
        try { FollowMutation.set(store, "owner", "target", 2, true, row -> { Assert.assertSame(row, store.target); throw new RepositoryException("denied"); }); Assert.fail(); }
        catch (final RepositoryException expected) { Assert.assertTrue(store.rows.isEmpty()); }
        FollowMutation.set(store, "owner", "target", 2, true, null);
        store.target = null;
        Assert.assertTrue(FollowMutation.set(store, "owner", "target", 2, false, null));
        Assert.assertTrue(store.rows.isEmpty());
        Assert.assertFalse(FollowMutation.set(store, "owner", "target", 2, true, null));
    }
    @Test public void ownerAndTypeIsolationAndHistoricalDuplicatesAreHandled() throws Exception {
        final Store store = new Store(); store.active = true;
        FollowMutation.set(store, "other", "target", 2, true, null);
        FollowMutation.set(store, "owner", "target", 3, true, null);
        FollowMutation.set(store, "owner", "target", 2, true, null);
        final JSONObject duplicate = new JSONObject(store.rows.getLast().toString()).put(Keys.OBJECT_ID, "duplicate");
        store.rows.add(duplicate); store.counter++;
        Assert.assertTrue(FollowMutation.set(store, "owner", "target", 2, false, null));
        Assert.assertEquals(store.rows.size(), 2); Assert.assertEquals(store.counter, 2);
        Assert.assertFalse(FollowMutation.set(store, "owner' OR 1=1", "target", 2, false, null));
    }
    @Test public void noTransactionOrMissingActorCannotWriteAndFailuresCanRollback() throws Exception {
        final Store store = new Store();
        try { FollowMutation.set(store, "owner", "target", 2, true, null); Assert.fail(); } catch (final IllegalStateException expected) { }
        store.active = true; store.actorExists = false;
        try { FollowMutation.set(store, "owner", "target", 2, true, null); Assert.fail(); } catch (final RepositoryException expected) { }
        Assert.assertTrue(store.rows.isEmpty()); store.actorExists = true; store.failCounter = true;
        try { FollowMutation.set(store, "owner", "target", 2, true, null); Assert.fail(); }
        catch (final RepositoryException expected) {
            // The caller owns commit/rollback; the production idempotency coordinator rolls back this partial transition.
            store.rows.clear(); store.counter = 0;
        }
        store.failCounter = false;
        Assert.assertTrue(FollowMutation.set(store, "owner", "target", 2, true, null)); Assert.assertEquals(store.counter, 1);
        try { FollowMutation.set(store, "owner", "target", 4, true, null); Assert.fail(); } catch (final IllegalArgumentException expected) { }
    }
    static class Store implements FollowMutation.Store {
        boolean active, failCounter, actorExists = true;
        int counter, next;
        JSONObject target = new JSONObject();
        final List<JSONObject> rows = new ArrayList<>();
        public boolean hasTransaction() { return active; }
        public boolean lockActor(final String actor) { return actorExists; }
        public JSONObject lockTarget(final String id, final int type) { return target; }
        public List<JSONObject> relations(final String actor, final String id, final int type) {
            return rows.stream().filter(r -> actor.equals(r.optString(Follow.FOLLOWER_ID)) && id.equals(r.optString(Follow.FOLLOWING_ID)) && type == r.optInt(Follow.FOLLOWING_TYPE)).toList();
        }
        public void add(final JSONObject row) { rows.add(row.put(Keys.OBJECT_ID, "" + ++next)); }
        public void remove(final String id) { rows.removeIf(r -> id.equals(r.optString(Keys.OBJECT_ID))); }
        public void adjust(final JSONObject row, final String id, final int type, final int delta) throws RepositoryException {
            if (failCounter) throw new RepositoryException("counter failed"); counter += delta;
        }
    }
    public static void main(final String[] args) throws Exception {
        final var instance = new FollowMutationTest(); int count = 0;
        for (final var method : FollowMutationTest.class.getDeclaredMethods()) if (method.isAnnotationPresent(Test.class)) {
            try { method.invoke(instance); System.out.println("PASS " + method.getName()); count++; }
            catch (final java.lang.reflect.InvocationTargetException e) { throw new AssertionError(method.getName(), e.getCause()); }
        }
        System.out.println("Follow transition tests passed: " + count);
    }
}
