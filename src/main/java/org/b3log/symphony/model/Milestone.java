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

/**
 * This class defines all milestone model relevant keys.
 *
 * @author rhythm
 * @version 1.0.0.0, Jan 15, 2026
 * @since 3.7.0
 */
public final class Milestone {

    /**
     * Milestone.
     */
    public static final String MILESTONE = "milestone";

    /**
     * Milestones.
     */
    public static final String MILESTONES = "milestones";

    /**
     * Key of milestone id.
     */
    public static final String MILESTONE_ID = "oId";

    /**
     * Key of milestone title.
     */
    public static final String MILESTONE_TITLE = "milestoneTitle";

    /**
     * Key of milestone date.
     */
    public static final String MILESTONE_DATE = "milestoneDate";

    /**
     * Key of milestone end date (for period milestones).
     */
    public static final String MILESTONE_END_DATE = "milestoneEndDate";

    /**
     * Key of milestone content.
     */
    public static final String MILESTONE_CONTENT = "milestoneContent";

    /**
     * Key of milestone media URL (image/video).
     */
    public static final String MILESTONE_MEDIA_URL = "milestoneMediaUrl";

    /**
     * Key of milestone media type.
     */
    public static final String MILESTONE_MEDIA_TYPE = "milestoneMediaType";

    /**
     * Key of milestone media caption.
     */
    public static final String MILESTONE_MEDIA_CAPTION = "milestoneMediaCaption";

    /**
     * Key of milestone link.
     */
    public static final String MILESTONE_LINK = "milestoneLink";

    /**
     * Key of milestone status (0: pending, 1: approved).
     */
    public static final String MILESTONE_STATUS = "milestoneStatus";

    /**
     * Key of milestone author id.
     */
    public static final String MILESTONE_AUTHOR_ID = "milestoneAuthorId";


    /**
     * Key of milestone create time.
     */
    public static final String MILESTONE_CREATE_TIME = "milestoneCreateTime";

    /**
     * Key of milestone update time.
     */
    public static final String MILESTONE_UPDATE_TIME = "milestoneUpdateTime";

    /**
     * Status - 待审核.
     */
    public static final int STATUS_C_PENDING = 1;

    /**
     * Status - 已通过.
     */
    public static final int STATUS_C_APPROVED = 2;

    /**
     * Status - 已拒绝.
     */
    public static final int STATUS_C_REJECTED = 3;
}
