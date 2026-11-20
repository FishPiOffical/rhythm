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
