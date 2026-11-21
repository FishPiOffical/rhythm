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

import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

/** Shared business quotas across the legacy and v3 entry points. */
public final class FishGameWriteQuota {
    private static final SimpleCurrentLimiter SUBMISSION = new SimpleCurrentLimiter(60 * 30, 10);
    private static final SimpleCurrentLimiter COMMENT = new SimpleCurrentLimiter(60 * 10, 5);
    private static final SimpleCurrentLimiter VOTE = new SimpleCurrentLimiter(60, 10);
    private FishGameWriteQuota() { }
    public enum Kind { SUBMISSION, COMMENT, VOTE }
    public static boolean access(final Kind kind, final String actor) {
        final SimpleCurrentLimiter limiter = switch (kind) { case SUBMISSION -> SUBMISSION; case COMMENT -> COMMENT; case VOTE -> VOTE; };
        synchronized (limiter) { return limiter.access(actor); }
    }
}
