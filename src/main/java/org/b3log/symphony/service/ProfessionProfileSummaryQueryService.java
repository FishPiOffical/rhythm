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
package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.model.ProfessionRewardGrant;
import org.b3log.symphony.model.UserProfession;
import org.b3log.symphony.model.UserProfessionContribution;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.ProfessionRewardGrantRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 组装公开职业资料中不含来源内容的统计摘要。 */
@Service
public class ProfessionProfileSummaryQueryService {

    private static final int ACTIVITY_LIMIT = 30;
    private static final long DETAIL_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(90);

    @Inject private UserProfessionQueryService userProfessionQueryService;
    @Inject private ProfessionEventEffectRepository effectRepository;
    @Inject private ProfessionRewardGrantRepository rewardGrantRepository;
    @Inject private ProfessionProfileActivityVisibilityService activityVisibilityService;
    @Inject private ProfessionActivitySourceDescriptionService activitySourceDescriptionService;

    public JSONArray contributionStats(final String userId) throws RepositoryException {
        final JSONArray result = new JSONArray();
        for (final JSONObject progress : userProfessionQueryService.getUserProgress(userId)) {
            result.put(contributionStat(userId, progress.getString(UserProfession.PROFESSION_ID)));
        }
        return result;
    }

    public JSONArray activityFeed(final String userId, final boolean owner) throws RepositoryException {
        final JSONArray result = new JSONArray();
        final List<JSONObject> effects = activityVisibilityService.filter(recentEffects(userId), owner);
        final Map<String, String> descriptions = activitySourceDescriptionService.descriptions(effects, owner);
        for (final JSONObject effect : effects) {
            result.put(activity(effect, descriptions.get(effect.getString(Keys.OBJECT_ID))));
        }
        return result;
    }

    public JSONArray levelHistory(final String userId, final boolean owner) throws RepositoryException {
        final JSONArray result = new JSONArray();
        for (final JSONObject effect : activityVisibilityService.filter(recentEffects(userId), owner)) {
            if (!effect.getString(ProfessionEventEffect.BEFORE_LEVEL_CODE)
                    .equals(effect.getString(ProfessionEventEffect.AFTER_LEVEL_CODE))) {
                result.put(levelChange(effect));
            }
        }
        return result;
    }

    public JSONArray rewards(final String userId) throws RepositoryException {
        final JSONArray result = new JSONArray();
        for (final JSONObject grant : rewardGrantRepository.getByUser(userId)) {
            result.put(new JSONObject()
                    .put("professionId", grant.getString(ProfessionRewardGrant.PROFESSION_ID))
                    .put("levelCode", grant.getString(ProfessionRewardGrant.LEVEL_CODE))
                    .put("rewardCode", grant.getString(ProfessionRewardGrant.REWARD_CODE))
                    .put("rewardType", grant.getString(ProfessionRewardGrant.REWARD_TYPE))
                    .put("status", grant.getString(ProfessionRewardGrant.STATUS))
                    .put("grantedAt", grant.getLong(ProfessionRewardGrant.GRANTED_AT)));
        }
        return result;
    }

    public JSONArray ranking(final String userId) throws RepositoryException {
        final JSONArray result = new JSONArray();
        for (final JSONObject progress : userProfessionQueryService.getUserProgress(userId)) {
            final String professionId = progress.getString(UserProfession.PROFESSION_ID);
            final JSONObject rarity = userProfessionQueryService.getRarity(professionId,
                    progress.getLong(UserProfession.TOTAL_EXPERIENCE));
            result.put(new JSONObject().put("professionId", professionId)
                    .put("rank", rarity.getLong("rank"))
                    .put("surpassedPercent", rarity.getDouble("surpassedPercent"))
                    .put("totalUsers", rarity.getLong("totalUsers")));
        }
        return result;
    }

    private JSONObject contributionStat(final String userId, final String professionId) throws RepositoryException {
        long experience = 0L;
        long events = 0L;
        long lastOccurredAt = 0L;
        for (final JSONObject contribution : userProfessionQueryService.getContributions(userId, professionId)) {
            experience += contribution.getLong(UserProfessionContribution.EXPERIENCE_SUM);
            events += contribution.getLong(UserProfessionContribution.EVENT_COUNT);
            lastOccurredAt = Math.max(lastOccurredAt, contribution.getLong(UserProfessionContribution.LAST_OCCURRED_AT));
        }
        return new JSONObject().put("professionId", professionId).put("experienceSum", experience)
                .put("eventCount", events).put("lastOccurredAt", lastOccurredAt);
    }

    private List<JSONObject> recentEffects(final String userId) throws RepositoryException {
        return effectRepository.getRecentAppliedByUser(userId,
                System.currentTimeMillis() - DETAIL_RETENTION_MILLIS, ACTIVITY_LIMIT);
    }

    private JSONObject activity(final JSONObject effect, final String sourceLabel) {
        return new JSONObject().put("professionId", effect.getString(ProfessionEventEffect.PROFESSION_ID))
                .put("actionCode", effect.getString(ProfessionEventEffect.ACTION_CODE))
                .put("sourceLabel", sourceLabel)
                .put("experienceDelta", effect.getLong(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA))
                .put("occurredAt", effect.getLong(ProfessionEventEffect.OCCURRED_AT));
    }

    private JSONObject levelChange(final JSONObject effect) {
        return new JSONObject().put("professionId", effect.getString(ProfessionEventEffect.PROFESSION_ID))
                .put("beforeLevelCode", effect.getString(ProfessionEventEffect.BEFORE_LEVEL_CODE))
                .put("afterLevelCode", effect.getString(ProfessionEventEffect.AFTER_LEVEL_CODE))
                .put("occurredAt", effect.getLong(ProfessionEventEffect.OCCURRED_AT));
    }
}
