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
import java.util.LinkedHashMap;
import java.util.Map;

/** Process-local legacy chat policy state shared by every sending adapter. */
public final class ChatMessageAbuseState {
    public enum Repeat { NONE, WARN, PENALIZE }
    private static final SimpleCurrentLimiter REPEATS = new SimpleCurrentLimiter(15 * 60, 5);
    private static final Map<String, String> CONFIRMATIONS = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(final Map.Entry<String, String> eldest) { return size() > 2000; }
    };
    private static String latestMessage = "";
    private ChatMessageAbuseState() { }

    /** A rejected first attempt intentionally remains pending for the user's next identical attempt. */
    public static synchronized boolean confirmRisk(final String actor, final String content) {
        if (actor == null || actor.isEmpty() || content == null || content.isEmpty()) throw new IllegalArgumentException("confirmation input");
        if (content.equals(CONFIRMATIONS.get(actor))) { CONFIRMATIONS.remove(actor); return true; }
        CONFIRMATIONS.put(actor, content);
        return false;
    }
    public static synchronized Repeat repeated(final String userName, final String content) {
        if (content.startsWith("[redpacket]") || content.endsWith("[/redpacket]") || !content.equals(latestMessage)) return Repeat.NONE;
        if (REPEATS.access(userName)) return REPEATS.get(userName).getFrequency() == 5 ? Repeat.WARN : Repeat.NONE;
        REPEATS.remove(userName);
        return Repeat.PENALIZE;
    }
    public static synchronized void accepted(final String content) { latestMessage = content; }
}
