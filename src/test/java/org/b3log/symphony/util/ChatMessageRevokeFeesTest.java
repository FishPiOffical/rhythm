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
import java.time.LocalDate;

/** Confirmed fee contract, independent of transaction ownership and pending legacy compatibility choices. */
public class ChatMessageRevokeFeesTest {
    public static void main(String[] args) {
        final var day = LocalDate.of(2026, 10, 18);
        var first = ChatMessageRevokeFees.quote(null, 0, day, false);
        Assert.assertEquals(first.cost(), 0);Assert.assertEquals(first.nextCount(), 1);Assert.assertTrue(first.counted());
        for (int count = 1; count < 20; count++) {
            final var value = ChatMessageRevokeFees.quote(day, count, day, false);
            Assert.assertEquals(value.cost(), count * 32);Assert.assertEquals(value.nextCount(), count + 1);Assert.assertEquals(value.day(), day);
        }
        for (var pair : new LocalDate[][]{{LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 1)}, {LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)}}) {
            final var value = ChatMessageRevokeFees.quote(pair[0], 19, pair[1], false);
            Assert.assertEquals(value.cost(), 0);Assert.assertEquals(value.nextCount(), 1);
        }
        final var admin = ChatMessageRevokeFees.quote(day, Integer.MAX_VALUE, day, true);
        Assert.assertEquals(admin.cost(), 0);Assert.assertFalse(admin.counted());
        System.out.println("PASS confirmed daily fees are 0/32/64 with no one-per-day cap, month/year reset correctly and existing privileged withdrawals skip counting");
        for (int count : new int[]{-1, -32}) try { ChatMessageRevokeFees.quote(day, count, day, false);Assert.fail(); } catch (IllegalStateException expected) { }
        try { ChatMessageRevokeFees.quote(null, 1, day, false);Assert.fail(); } catch (IllegalStateException expected) { }
        final int maximum = Integer.MAX_VALUE / 32;
        Assert.assertEquals(ChatMessageRevokeFees.quote(day, maximum, day, false).cost(), maximum * 32);
        try { ChatMessageRevokeFees.quote(day, maximum + 1, day, false);Assert.fail(); }
        catch (ApiV3Exception e) { Assert.assertEquals(e.status, 409);Assert.assertEquals(e.code, "withdrawal_limit"); }
        System.out.println("PASS invalid stored counts fail closed and fee overflow cannot wrap into a negative charge");
    }
}
