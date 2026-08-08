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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.model.ProfessionEffectFailureState;
import org.json.JSONObject;

import java.util.List;

/** 职业经验效果明细存储。 */
@Repository
public class ProfessionEventEffectRepository extends AbstractRepository {

    public ProfessionEventEffectRepository() {
        super(ProfessionEventEffect.PROFESSION_EVENT_EFFECT);
    }

    public boolean addIfAbsent(final JSONObject effect) throws RepositoryException {
        final String sql = "INSERT INTO `" + getName() + "` (`oId`,`effectKey`,`sourceEventId`,`eventKey`,`bucketId`,"
                + "`bucketKey`,`automationRevisionId`,`actionCode`,`userId`,`professionId`,`professionRevisionId`,"
                + "`levelSchemeId`,`actualExperienceDelta`,`beforeExperience`,`afterExperience`,`beforeLevelCode`,"
                + "`afterLevelCode`,`status`,`reversesEffectId`,`reversedByEffectId`,`occurredAt`,`createdAt`,`updatedAt`) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE `oId`=`oId`";
        AtomicRepositorySupport.executeUpdate(sql, effect.optString("oId"), effect.optString(ProfessionEventEffect.EFFECT_KEY),
                effect.optString(ProfessionEventEffect.SOURCE_EVENT_ID), effect.optString(ProfessionEventEffect.EVENT_KEY),
                effect.optString(ProfessionEventEffect.BUCKET_ID), effect.optString(ProfessionEventEffect.BUCKET_KEY),
                effect.optString(ProfessionEventEffect.AUTOMATION_REVISION_ID), effect.optString(ProfessionEventEffect.ACTION_CODE),
                effect.optString(ProfessionEventEffect.USER_ID), effect.optString(ProfessionEventEffect.PROFESSION_ID),
                effect.optString(ProfessionEventEffect.PROFESSION_REVISION_ID), effect.optString(ProfessionEventEffect.LEVEL_SCHEME_ID),
                effect.optLong(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA), effect.optLong(ProfessionEventEffect.BEFORE_EXPERIENCE),
                effect.optLong(ProfessionEventEffect.AFTER_EXPERIENCE), effect.optString(ProfessionEventEffect.BEFORE_LEVEL_CODE),
                effect.optString(ProfessionEventEffect.AFTER_LEVEL_CODE), effect.optString(ProfessionEventEffect.STATUS),
                effect.optString(ProfessionEventEffect.REVERSES_EFFECT_ID), effect.optString(ProfessionEventEffect.REVERSED_BY_EFFECT_ID),
                effect.optLong(ProfessionEventEffect.OCCURRED_AT), effect.optLong(ProfessionEventEffect.CREATED_AT),
                effect.optLong(ProfessionEventEffect.UPDATED_AT));
        final List<JSONObject> stored = select("SELECT `oId` FROM `" + getName() + "` WHERE `effectKey`=?",
                effect.getString(ProfessionEventEffect.EFFECT_KEY));
        return !stored.isEmpty() && effect.getString("oId").equals(stored.getFirst().getString("oId"));
    }

    public List<JSONObject> getByEventKey(final String eventKey) throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `eventKey`=? AND `reversesEffectId`=''";
        return select(sql, eventKey);
    }

    public boolean markReversed(final String effectId, final String reversalEffectId, final long now)
            throws RepositoryException {
        final String sql = "UPDATE `" + getName() + "` SET `reversedByEffectId`=?,`updatedAt`=? WHERE `oId`=? "
                + "AND (`reversedByEffectId`='' OR `reversedByEffectId`=?)";
        return AtomicRepositorySupport.executeUpdate(sql, reversalEffectId, now, effectId, reversalEffectId) == 1;
    }

    public List<JSONObject> getPending(final long now, final long staleBefore, final int limit)
            throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE (`status`='PENDING_APPLY' OR "
                + "(`status`='APPLY_RETRY' AND `nextRetryAt`<=?) OR "
                + "(`status`='APPLYING' AND `lockedAt`<=?)) ORDER BY `createdAt`,`oId` LIMIT ?";
        return select(sql, now, staleBefore, limit);
    }

    public boolean claimForApply(final String effectId, final long now, final long staleBefore)
            throws RepositoryException {
        final String retrySql = "UPDATE `" + getName() + "` SET `status`='APPLYING',`lockedAt`=?,"
                + "`processingAttempt`=`processingAttempt`+1,`updatedAt`=? WHERE `oId`=? AND "
                + "(`status`='PENDING_APPLY' OR (`status`='APPLY_RETRY' AND `nextRetryAt`<=?) OR "
                + "(`status`='APPLYING' AND `lockedAt`<=?))";
        return AtomicRepositorySupport.executeUpdate(retrySql, now, now, effectId, now, staleBefore) == 1;
    }

    public JSONObject getForUpdate(final String effectId) throws RepositoryException {
        final List<JSONObject> result = select("SELECT * FROM `" + getName() + "` WHERE `oId`=? FOR UPDATE", effectId);
        return result.isEmpty() ? null : result.getFirst();
    }

    public boolean recordApplyFailure(final ProfessionEffectFailureState state) throws RepositoryException {
        final String sql = "UPDATE `" + getName() + "` SET `status`='APPLY_RETRY',`nextRetryAt`=?,"
                + "`errorCode`=?,`errorMessage`=?,`lockedAt`=0,`updatedAt`=? WHERE `oId`=? AND `status`='APPLYING' "
                + "AND `lockedAt`=?";
        return AtomicRepositorySupport.executeUpdate(sql, state.nextRetryAt(), state.errorCode(), state.errorMessage(),
                state.updatedAt(), state.effectId(), state.claimedAt()) == 1;
    }

    public boolean requeueApply(final String effectId, final long now) throws RepositoryException {
        final String sql = "UPDATE `" + getName() + "` SET `status`='PENDING_APPLY',`nextRetryAt`=0,"
                + "`lockedAt`=0,`errorCode`='',`errorMessage`='',`updatedAt`=? WHERE `oId`=?";
        return AtomicRepositorySupport.executeUpdate(sql, now, effectId) == 1;
    }

    public long sumAppliedExperience(final String userId, final String professionId) throws RepositoryException {
        final String sql = "SELECT COALESCE(SUM(`actualExperienceDelta`),0) AS `experienceSum` FROM `"
                + getName() + "` WHERE `userId`=? AND `professionId`=? AND `status`='APPLIED'";
        final List<JSONObject> values = select(sql, userId, professionId);
        return values.isEmpty() ? 0L : values.getFirst().optLong("experienceSum");
    }

    public List<JSONObject> getRecentApplied(final ProfessionEffectDetailQuery query) throws RepositoryException {
        final String baseSql = "SELECT * FROM `" + getName() + "` WHERE `userId`=? AND `professionId`=? "
                + "AND `status`='APPLIED' AND `actualExperienceDelta`<>0 AND `occurredAt`>=? ";
        if (query.beforeOccurredAt() <= 0L) {
            return select(baseSql + "ORDER BY `occurredAt` DESC,`oId` DESC LIMIT ?",
                    query.userId(), query.professionId(), query.cutoff(), query.limit());
        }
        final String cursorSql = "AND (`occurredAt`<? OR (`occurredAt`=? AND `oId`<?)) "
                + "ORDER BY `occurredAt` DESC,`oId` DESC LIMIT ?";
        return select(baseSql + cursorSql, query.userId(), query.professionId(), query.cutoff(),
                query.beforeOccurredAt(), query.beforeOccurredAt(), query.beforeEffectId(), query.limit());
    }

    public List<JSONObject> getRecentAppliedByUser(final String userId, final long cutoff, final int limit)
            throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `userId`=? AND `status`='APPLIED' "
                + "AND `actualExperienceDelta`<>0 AND `occurredAt`>=? ORDER BY `occurredAt` DESC,`oId` DESC LIMIT ?";
        return select(sql, userId, cutoff, limit);
    }
}
