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

/** 用户职业隐私与选择状态字段。 */
public final class UserProfessionPrivacy {

    public static final String USER_PROFESSION_PRIVACY = "user_profession_privacy";
    public static final String USER_ID = "userId";
    public static final String ONBOARDING_STATE = "onboardingState";
    public static final String PRIMARY_PROFESSION_ID = "primaryProfessionId";
    public static final String PRIVACY_PRESET = "privacyPreset";
    public static final String MODULE_VISIBILITY_JSON = "moduleVisibilityJson";
    public static final String USER_PROGRESS_VERSION = "userProgressVersion";
    public static final String USER_PRIVACY_VERSION = "userPrivacyVersion";
    public static final String PUBLIC_STATS_VERSION = "publicStatsVersion";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    private UserProfessionPrivacy() {
    }
}
