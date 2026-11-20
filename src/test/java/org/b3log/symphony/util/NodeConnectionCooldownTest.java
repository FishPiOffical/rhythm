package org.b3log.symphony.util;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class NodeConnectionCooldownTest {

    @Test
    public void coolsDownFailedInternalPathAndAllowsLaterProbe() {
        final NodeConnectionCooldown cooldown = new NodeConnectionCooldown();
        final long now = 1_000_000_000L;

        assertTrue(cooldown.shouldTryInternal("node", now));
        cooldown.internalFailed("node", now);
        assertFalse(cooldown.shouldTryInternal("node", now + 1));
        assertTrue(cooldown.shouldTryInternal("node", now + 5 * 60 * 1_000_000_000L));
        cooldown.internalSucceeded("node");
        assertTrue(cooldown.shouldTryInternal("node", now + 5 * 60 * 1_000_000_000L + 1));
    }
}
