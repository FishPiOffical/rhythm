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
import org.testng.annotations.Test;

public class AccountCredentialVersionsTest {
    @Test(timeOut = 60_000)
    public void acceptsLegacyKeyOnlyForLegacyCredentialVersion() {
        Assert.assertTrue(AccountCredentialVersions.matchesPayload(0, 0, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(0, 0, "password", "old-password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(4, 0, "password", "password"));
    }

    @Test(timeOut = 60_000)
    public void requiresMatchingVersionForNewKey() {
        Assert.assertTrue(AccountCredentialVersions.matchesPayload(4, 4, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(4, 3, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(4, 4, "password", "old-password"));
    }

    public static void main(final String[] args) {
        Assert.assertTrue(AccountCredentialVersions.matchesPayload(0, 0, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(0, 0, "password", "old-password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(4, 0, "password", "password"));
        Assert.assertTrue(AccountCredentialVersions.matchesPayload(4, 4, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(4, 3, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(4, 4, "password", "old-password"));
        System.out.println("PASS API key legacy and credential epoch compatibility");
    }
}
