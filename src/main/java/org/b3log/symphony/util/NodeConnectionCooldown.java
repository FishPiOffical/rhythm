package org.b3log.symphony.util;

import java.util.concurrent.ConcurrentHashMap;
final class NodeConnectionCooldown {

    private static final long COOLDOWN_NANOS = 5 * 60 * 1_000_000_000L;
    private final ConcurrentHashMap<String, Long> retryAfter = new ConcurrentHashMap<>();

    synchronized boolean shouldTryInternal(final String node, final long now) {
        final Long retryTime = retryAfter.get(node);
        if (retryTime != null && now < retryTime) {
            return false;
        }
        retryAfter.put(node, now + COOLDOWN_NANOS);
        return true;
    }

    void internalSucceeded(final String node) {
        retryAfter.remove(node);
    }

    void internalFailed(final String node, final long now) {
        retryAfter.put(node, now + COOLDOWN_NANOS);
    }
}
