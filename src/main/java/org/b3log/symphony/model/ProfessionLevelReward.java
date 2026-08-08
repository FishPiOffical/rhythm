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

/** 职业等级奖励定义字段。 */
public final class ProfessionLevelReward {

    public static final String PROFESSION_LEVEL_REWARD = "profession_level_reward";
    public static final String SCHEME_ID = "schemeId";
    public static final String LEVEL_ID = "levelId";
    public static final String LEVEL_CODE = "levelCode";
    public static final String REWARD_CODE = "rewardCode";
    public static final String REWARD_TYPE = "rewardType";
    public static final String REWARD_CONFIG_JSON = "rewardConfigJson";
    public static final String GRANT_POLICY = "grantPolicy";
    public static final String DOWNGRADE_POLICY = "downgradePolicy";
    public static final String SORT_ORDER = "sortOrder";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    private ProfessionLevelReward() {
    }
}
