/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.b3log.symphony.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class AccountCredentialVersionsTest {
    @Test(timeOut = 60_000)
    public void acceptsLegacyKeyWhenPasswordMatchesAfterCredentialVersionChanges() {
        Assert.assertTrue(AccountCredentialVersions.matchesPayload(true, 4, 0, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(true, 4, 0, "password", "old-password"));
    }

    @Test(timeOut = 60_000)
    public void requiresMatchingVersionForNewKey() {
        Assert.assertTrue(AccountCredentialVersions.matchesPayload(false, 4, 4, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(false, 4, 3, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(false, 4, 4, "password", "old-password"));
    }

    public static void main(final String[] args) {
        Assert.assertTrue(AccountCredentialVersions.matchesPayload(true, 4, 0, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(true, 4, 0, "password", "old-password"));
        Assert.assertTrue(AccountCredentialVersions.matchesPayload(false, 4, 4, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(false, 4, 3, "password", "password"));
        Assert.assertFalse(AccountCredentialVersions.matchesPayload(false, 4, 4, "password", "old-password"));
        System.out.println("PASS API key legacy and credential epoch compatibility");
    }
}
