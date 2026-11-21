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

import java.time.LocalDate;
import java.util.Objects;

/** Confirmed daily policy: first withdrawal free, then 32 points per previous successful withdrawal. */
public final class ChatMessageRevokeFees {
    public static final int POINT_STEP = 32;
    public record Quote(LocalDate day, int cost, int nextCount, boolean counted) { }
    private ChatMessageRevokeFees() { }
    /** Caller supplies the platform's natural day, and persists nextCount only with a successful removal. */
    public static Quote quote(final LocalDate recordedDay, final int count, final LocalDate today, final boolean privileged) {
        Objects.requireNonNull(today);
        // Existing operators skip the counter and transfer branch entirely.
        if (privileged) return new Quote(today, 0, 0, false);
        if (count < 0 || recordedDay == null && count != 0) throw new IllegalStateException("invalid stored chat withdrawal counter");
        final int used = today.equals(recordedDay) ? count : 0;
        try { return new Quote(today, Math.multiplyExact(used, POINT_STEP), Math.addExact(used, 1), true); }
        catch (final ArithmeticException e) { throw new ApiV3Exception(409, "withdrawal_limit", "今日撤回次数过多"); }
    }
}
