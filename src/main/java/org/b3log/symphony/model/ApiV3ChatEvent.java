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
package org.b3log.symphony.model;

import java.util.Set;

/** Committed public-room changes; no message contents, user snapshots or credentials. */
public final class ApiV3ChatEvent {
    private ApiV3ChatEvent() { }
    public static final String REPOSITORY="api_v3_chat_event";
    public static final String STATE_REPOSITORY="api_v3_chat_state";
    public static final String ROOM="main";
    public static final String TOPIC="topic";
    public static final String TOPIC_TARGET="0";
    public static final Set<String> KINDS=Set.of("created","updated","deleted","reactions",TOPIC);
    public static boolean validChange(final String kind, final String id) {
        return kind != null && KINDS.contains(kind) && id != null && id.matches("[0-9]{1,32}")
                && (!TOPIC.equals(kind) || TOPIC_TARGET.equals(id));
    }
}
