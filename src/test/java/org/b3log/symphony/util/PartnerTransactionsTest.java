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

public class PartnerTransactionsTest {
    @Test
    public void mapsLatkeConnectionAcquisitionFailure() {
        var error = Assert.expectThrows(ApiV3Exception.class, () -> PartnerTransactions.begin(() -> {
            throw new IllegalStateException("Begin a transaction failed");
        }));
        Assert.assertEquals(error.status, 503);
        Assert.assertEquals(error.code, "partner_busy");
    }

    @Test
    public void preservesUnrelatedTransactionFailures() {
        var original = new IllegalStateException("transaction already active");
        var error = Assert.expectThrows(IllegalStateException.class, () -> PartnerTransactions.begin(() -> {
            throw original;
        }));
        Assert.assertSame(error, original);
    }
}
