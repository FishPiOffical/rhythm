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

/** 职业等级奖励发放字段。 */
public final class ProfessionRewardGrant {

    public static final String PROFESSION_REWARD_GRANT = "profession_reward_grant";
    public static final String USER_ID = "userId";
    public static final String PROFESSION_ID = "professionId";
    public static final String SCHEME_ID = "schemeId";
    public static final String LEVEL_CODE = "levelCode";
    public static final String REWARD_CODE = "rewardCode";
    public static final String GRANT_CYCLE = "grantCycle";
    public static final String REWARD_TYPE = "rewardType";
    public static final String REWARD_CONFIG_JSON = "rewardConfigJson";
    public static final String SOURCE_EFFECT_ID = "sourceEffectId";
    public static final String EXTERNAL_REFERENCE_ID = "externalReferenceId";
    public static final String STATUS = "status";
    public static final String GRANTED_AT = "grantedAt";
    public static final String REVOKED_AT = "revokedAt";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    private ProfessionRewardGrant() {
    }
}
