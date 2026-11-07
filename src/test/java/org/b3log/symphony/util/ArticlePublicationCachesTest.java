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

import org.testng.Assert;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;

public class ArticlePublicationCachesTest {
    public static void main(final String[] args) throws Exception {
        Assert.assertFalse(ArticlePublicationCaches.active());
        try { ArticlePublicationCaches.afterTransaction("x", () -> { }); Assert.fail(); } catch (final IllegalStateException expected) { }
        final List<String> calls = new ArrayList<>();
        final var scope = ArticlePublicationCaches.open(() -> false);
        try (scope) {
            Assert.assertTrue(ArticlePublicationCaches.active());
            try { ArticlePublicationCaches.open(() -> false); Assert.fail(); } catch (final IllegalStateException expected) { }
            ArticlePublicationCaches.afterTransaction("user:1", () -> { Assert.assertFalse(ArticlePublicationCaches.active()); calls.add("durable-user"); });
            ArticlePublicationCaches.afterTransaction("tag:1", () -> calls.add("old-tag"));
            ArticlePublicationCaches.afterTransaction("tag:1", () -> calls.add("durable-tag"));
            Assert.assertTrue(calls.isEmpty());
        }
        scope.close();
        Assert.assertEquals(calls, List.of("durable-user", "durable-tag"));
        Assert.assertFalse(ArticlePublicationCaches.active());
        System.out.println("PASS no early cache publication, keyed invalidations after close, duplicate close and nested boundary rejection");
        try {
            try (final var ignored = ArticlePublicationCaches.open(() -> false)) {
                ArticlePublicationCaches.afterTransaction("rollback", () -> calls.add("rollback"));
                throw new IllegalStateException("simulated body failure");
            }
        } catch (final IllegalStateException expected) { }
        Assert.assertFalse(ArticlePublicationCaches.active()); Assert.assertEquals(calls.getLast(), "rollback");
        try (final var ignored = ArticlePublicationCaches.open(() -> false)) {
            ArticlePublicationCaches.afterTransaction("failure", () -> { throw new IllegalStateException("simulated invalidation failure"); });
            ArticlePublicationCaches.afterTransaction("next", () -> calls.add("next"));
        }
        Assert.assertFalse(ArticlePublicationCaches.active()); Assert.assertEquals(calls.getLast(), "next");
        System.out.println("PASS rollback cleanup, cache callback failure isolation and thread-local removal");
        final var barrier = new CyclicBarrier(2);
        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var one = executor.submit(() -> concurrentScope(barrier, "one"));
            final var two = executor.submit(() -> concurrentScope(barrier, "two"));
            Assert.assertEquals(one.get(), List.of("one")); Assert.assertEquals(two.get(), List.of("two"));
        }
        Assert.assertFalse(ArticlePublicationCaches.active());
        System.out.println("PASS concurrent publication cache boundaries stay isolated by thread");
        final var transactionActive = new java.util.concurrent.atomic.AtomicBoolean(true);
        try {
            try (final var ignored = ArticlePublicationCaches.open(transactionActive::get)) {
                ArticlePublicationCaches.afterTransaction("uncommitted", () -> Assert.fail("uncommitted data must not refill caches"));
            }
            Assert.fail();
        } catch (final IllegalStateException expected) { }
        Assert.assertFalse(ArticlePublicationCaches.active());
        try (final var ignored = ArticlePublicationCaches.open(transactionActive::get)) {
            ArticlePublicationCaches.afterTransaction("committed", () -> calls.add("committed"));
            transactionActive.set(false);
        }
        Assert.assertEquals(calls.getLast(), "committed");
        System.out.println("PASS active transaction blocks cache refill and clears boundary; ended transaction permits refill");
    }
    private static List<String> concurrentScope(final CyclicBarrier barrier, final String name) throws Exception {
        final List<String> calls = new ArrayList<>();
        try (final var ignored = ArticlePublicationCaches.open(() -> false)) {
            ArticlePublicationCaches.afterTransaction("same-key", () -> calls.add(name));
            barrier.await(); Assert.assertTrue(ArticlePublicationCaches.active()); Assert.assertTrue(calls.isEmpty());
        }
        Assert.assertFalse(ArticlePublicationCaches.active()); return calls;
    }
}
