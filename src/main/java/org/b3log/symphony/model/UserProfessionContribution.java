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

/** 用户职业贡献聚合字段。 */
public final class UserProfessionContribution {

    public static final String USER_PROFESSION_CONTRIBUTION = "user_profession_contribution";
    public static final String USER_ID = "userId";
    public static final String PROFESSION_ID = "professionId";
    public static final String ACTION_CODE = "actionCode";
    public static final String EXPERIENCE_SUM = "experienceSum";
    public static final String EVENT_COUNT = "eventCount";
    public static final String LAST_OCCURRED_AT = "lastOccurredAt";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    private UserProfessionContribution() {
    }
}
