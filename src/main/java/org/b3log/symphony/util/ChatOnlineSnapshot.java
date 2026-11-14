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

import java.util.List;

/** Atomically published remote membership, containing no node addresses or credentials. */
public final class ChatOnlineSnapshot {
    private ChatOnlineSnapshot() { }
    public record Sample(List<String> names, long sampledAt, boolean complete) {
        public Sample { names = List.copyOf(names); }
    }
    private static volatile Sample current = new Sample(List.of(), 0, false);
    public static Sample current() { return current; }
    public static void publish(final List<String> names, final long sampledAt, final boolean complete) {
        current = new Sample(names, sampledAt, complete);
    }
}
