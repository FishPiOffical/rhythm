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

import org.apache.logging.log4j.LogManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Opt-in publication boundary: repositories bypass caches until the caller ends its transaction. */
public final class ArticlePublicationCaches implements AutoCloseable {
    private static final ThreadLocal<ArticlePublicationCaches> CURRENT = new ThreadLocal<>();
    private final Map<String, Runnable> invalidations = new LinkedHashMap<>();
    private final BooleanSupplier transactionActive;
    private boolean closed;
    private ArticlePublicationCaches(final BooleanSupplier transactionActive) { this.transactionActive = transactionActive; }
    public static ArticlePublicationCaches open(final BooleanSupplier transactionActive) {
        if (active()) throw new IllegalStateException("publication cache boundary already active");
        final var scope = new ArticlePublicationCaches(transactionActive);
        CURRENT.set(scope);
        return scope;
    }
    public static boolean active() { return null != CURRENT.get(); }
    public static void afterTransaction(final String key, final Runnable action) {
        final var scope = CURRENT.get();
        if (null == scope) throw new IllegalStateException("publication cache boundary required");
        scope.invalidations.put(key, action);
    }
    /** Called after commit or rollback; never publishes the objects modified by the transaction. */
    @Override public void close() {
        if (closed) return;
        if (CURRENT.get() != this) throw new IllegalStateException("publication cache boundary owner mismatch");
        closed = true;
        CURRENT.remove();
        if (transactionActive.getAsBoolean()) throw new IllegalStateException("publication cache refresh before transaction end");
        for (final var action : invalidations.values()) {
            try { action.run(); }
            catch (final RuntimeException e) { LogManager.getLogger(ArticlePublicationCaches.class).error("Publication cache invalidation failed", e); }
        }
    }
}
