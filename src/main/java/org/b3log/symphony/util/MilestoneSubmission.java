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

import org.b3log.symphony.model.Milestone;
import org.json.JSONObject;

/** Internal row whitelist; input content policy is checked by the entry point. */
public final class MilestoneSubmission {
    private MilestoneSubmission() { }
    public static JSONObject pending(final String actor, final JSONObject input, final long now) {
        if (null == actor || actor.isBlank()) throw new IllegalArgumentException("milestone author required");
        return new JSONObject().put(Milestone.MILESTONE_TITLE, input.getString(Milestone.MILESTONE_TITLE))
                .put(Milestone.MILESTONE_DATE, input.getLong(Milestone.MILESTONE_DATE))
                .put(Milestone.MILESTONE_END_DATE, input.optLong(Milestone.MILESTONE_END_DATE))
                .put(Milestone.MILESTONE_CONTENT, input.getString(Milestone.MILESTONE_CONTENT))
                .put(Milestone.MILESTONE_MEDIA_URL, input.optString(Milestone.MILESTONE_MEDIA_URL))
                .put(Milestone.MILESTONE_MEDIA_TYPE, input.optString(Milestone.MILESTONE_MEDIA_TYPE))
                .put(Milestone.MILESTONE_MEDIA_CAPTION, input.optString(Milestone.MILESTONE_MEDIA_CAPTION))
                .put(Milestone.MILESTONE_LINK, input.optString(Milestone.MILESTONE_LINK))
                .put(Milestone.MILESTONE_AUTHOR_ID, actor).put(Milestone.MILESTONE_STATUS, Milestone.STATUS_C_PENDING)
                .put(Milestone.MILESTONE_CREATE_TIME, now).put(Milestone.MILESTONE_UPDATE_TIME, now);
    }
}
