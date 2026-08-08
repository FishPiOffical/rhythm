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
package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ProfessionRewardGrant;
import org.json.JSONObject;

import java.util.List;

/** 职业等级奖励发放存储。 */
@Repository
public class ProfessionRewardGrantRepository extends AbstractRepository {

    public ProfessionRewardGrantRepository() {
        super(ProfessionRewardGrant.PROFESSION_REWARD_GRANT);
    }

    public boolean addIfAbsent(final JSONObject grant) throws RepositoryException {
        final String sql = "INSERT INTO `" + getName() + "` (`oId`,`userId`,`professionId`,`schemeId`,`levelCode`,"
                + "`rewardCode`,`grantCycle`,`rewardType`,`rewardConfigJson`,`sourceEffectId`,`externalReferenceId`,"
                + "`status`,`grantedAt`,`revokedAt`,`createdAt`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE `oId`=`oId`";
        AtomicRepositorySupport.executeUpdate(sql, grant.getString("oId"),
                grant.getString(ProfessionRewardGrant.USER_ID), grant.getString(ProfessionRewardGrant.PROFESSION_ID),
                grant.getString(ProfessionRewardGrant.SCHEME_ID), grant.getString(ProfessionRewardGrant.LEVEL_CODE),
                grant.getString(ProfessionRewardGrant.REWARD_CODE), grant.getString(ProfessionRewardGrant.GRANT_CYCLE),
                grant.getString(ProfessionRewardGrant.REWARD_TYPE), grant.getString(ProfessionRewardGrant.REWARD_CONFIG_JSON),
                grant.getString(ProfessionRewardGrant.SOURCE_EFFECT_ID), grant.getString(ProfessionRewardGrant.EXTERNAL_REFERENCE_ID),
                grant.getString(ProfessionRewardGrant.STATUS), grant.getLong(ProfessionRewardGrant.GRANTED_AT),
                grant.getLong(ProfessionRewardGrant.REVOKED_AT), grant.getLong(ProfessionRewardGrant.CREATED_AT),
                grant.getLong(ProfessionRewardGrant.UPDATED_AT));
        final JSONObject stored = getByIdentity(grant);
        return null != stored && grant.getString("oId").equals(stored.getString("oId"));
    }

    public JSONObject getByIdentity(final JSONObject grant) throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `userId`=? AND `professionId`=? "
                + "AND `levelCode`=? AND `rewardCode`=? AND `grantCycle`=?";
        final var values = select(sql, grant.getString(ProfessionRewardGrant.USER_ID),
                grant.getString(ProfessionRewardGrant.PROFESSION_ID),
                grant.getString(ProfessionRewardGrant.LEVEL_CODE),
                grant.getString(ProfessionRewardGrant.REWARD_CODE),
                grant.getString(ProfessionRewardGrant.GRANT_CYCLE));
        return values.isEmpty() ? null : values.getFirst();
    }

    public List<JSONObject> getByUser(final String userId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(ProfessionRewardGrant.USER_ID,
                FilterOperator.EQUAL, userId)).addSort(ProfessionRewardGrant.GRANTED_AT, SortDirection.DESCENDING);
        return getList(query);
    }
}
