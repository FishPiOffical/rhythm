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
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.UserProfession;
import org.json.JSONObject;

import java.util.List;

/** 用户职业汇总存储。 */
@Repository
public class UserProfessionRepository extends AbstractRepository {

    public UserProfessionRepository() {
        super(UserProfession.USER_PROFESSION);
    }

    public JSONObject getForUpdate(final String userId, final String professionId) throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `userId`=? AND `professionId`=? FOR UPDATE";
        final var values = select(sql, userId, professionId);
        return values.isEmpty() ? null : values.getFirst();
    }

    public JSONObject getByUserProfession(final String userId, final String professionId) throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `userId`=? AND `professionId`=?";
        final List<JSONObject> values = select(sql, userId, professionId);
        return values.isEmpty() ? null : values.getFirst();
    }

    public List<JSONObject> getByProfessionForUpdate(final String professionId) throws RepositoryException {
        return select("SELECT `userId` FROM `" + getName() + "` WHERE `professionId`=? FOR UPDATE", professionId);
    }

    public JSONObject previewSchemeImpact(final String professionId, final String candidateSchemeId)
            throws RepositoryException {
        final String levelTable = getName().replace("user_profession", "profession_level");
        final String sql = "SELECT COALESCE(SUM(`candidateRank`>`currentRank`),0) AS `upgradeCount`,"
                + "COALESCE(SUM(`candidateRank`<`currentRank`),0) AS `downgradeCount`,"
                + "COALESCE(SUM(`candidateRank`=`currentRank`),0) AS `unchangedCount`,COUNT(*) AS `totalCount` FROM ("
                + "SELECT (SELECT COUNT(*) FROM `" + levelTable + "` WHERE `schemeId`=? "
                + "AND `requiredTotalExperience`<=up.`totalExperience`) `candidateRank`,"
                + "(SELECT COUNT(*) FROM `" + levelTable + "` WHERE `schemeId`=up.`levelSchemeId` "
                + "AND `requiredTotalExperience`<=up.`totalExperience`) `currentRank` FROM `" + getName()
                + "` up WHERE up.`professionId`=?) impact";
        final var values = select(sql, candidateSchemeId, professionId);
        return values.isEmpty() ? new JSONObject() : values.getFirst();
    }

    public List<JSONObject> getMigrationBatch(final String professionId, final String targetSchemeId,
                                              final int limit) throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `professionId`=? AND `levelSchemeId`<>? "
                + "ORDER BY `oId` LIMIT ? FOR UPDATE";
        return select(sql, professionId, targetSchemeId, limit);
    }

    public List<JSONObject> getRanking(final ProfessionRankingQuery query) throws RepositoryException {
        final String baseSql = "SELECT * FROM `" + getName() + "` WHERE `professionId`=? ";
        if (query.beforeUserId().isBlank()) {
            return select(baseSql + "ORDER BY `totalExperience` DESC,`userId` DESC LIMIT ?",
                    query.professionId(), query.limit());
        }
        final String cursorSql = "AND (`totalExperience`<? OR (`totalExperience`=? AND `userId`<?)) "
                + "ORDER BY `totalExperience` DESC,`userId` DESC LIMIT ?";
        return select(baseSql + cursorSql, query.professionId(), query.beforeExperience(),
                query.beforeExperience(), query.beforeUserId(), query.limit());
    }

    public JSONObject getRarityStats(final String professionId, final long experience) throws RepositoryException {
        final String sql = "SELECT COUNT(*) AS `totalUsers`,COALESCE(SUM(CASE WHEN `totalExperience`>? THEN 1 ELSE 0 END),0) "
                + "AS `greaterUsers`,COALESCE(SUM(CASE WHEN `totalExperience`<? THEN 1 ELSE 0 END),0) AS `lowerUsers` "
                + "FROM `" + getName() + "` WHERE `professionId`=?";
        final List<JSONObject> values = select(sql, experience, experience, professionId);
        return values.isEmpty() ? new JSONObject() : values.getFirst();
    }
}
