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
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.ProfessionLevel;
import org.b3log.symphony.model.ProfessionLevelPresentation;
import org.b3log.symphony.model.ProfessionLevelReward;
import org.b3log.symphony.repository.ProfessionLevelPresentationRepository;
import org.b3log.symphony.repository.ProfessionLevelRepository;
import org.b3log.symphony.repository.ProfessionLevelRewardRepository;
import org.json.JSONObject;

/** 将已校验等级草稿写入不可变方案快照。 */
@Service
public class ProfessionLevelDraftStorageService {

    @Inject private ProfessionLevelRepository levelRepository;
    @Inject private ProfessionLevelPresentationRepository presentationRepository;
    @Inject private ProfessionLevelRewardRepository rewardRepository;

    public void store(final String schemeId, final ProfessionLevelSchemeDraftRequest request, final long now)
            throws RepositoryException {
        for (final ProfessionLevelDraft draft : request.levels()) {
            final JSONObject level = level(schemeId, draft, now);
            levelRepository.add(level);
            storePresentations(level.getString(Keys.OBJECT_ID), draft, now);
            storeRewards(new RewardStorageRequest(schemeId, level.getString(Keys.OBJECT_ID), draft, now));
        }
    }

    private JSONObject level(final String schemeId, final ProfessionLevelDraft draft, final long now) {
        final JSONObject level = new JSONObject();
        level.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        level.put(ProfessionLevel.SCHEME_ID, schemeId);
        level.put(ProfessionLevel.CODE, draft.levelCode());
        level.put(ProfessionLevel.SORT_ORDER, draft.sortOrder());
        level.put(ProfessionLevel.REQUIRED_TOTAL_EXPERIENCE, draft.requiredTotalExperience());
        level.put(ProfessionLevel.DISPLAY_NAME, draft.displayName());
        level.put(ProfessionLevel.SHORT_NAME, draft.shortName());
        level.put(ProfessionLevel.DESCRIPTION, draft.description());
        level.put(ProfessionLevel.ACHIEVEMENT_DESCRIPTION, draft.achievementDescription());
        level.put(ProfessionLevel.TOP_LEVEL, draft.topLevel());
        level.put(ProfessionLevel.CREATED_AT, now);
        level.put(ProfessionLevel.UPDATED_AT, now);
        return level;
    }

    private void storePresentations(final String levelId, final ProfessionLevelDraft draft, final long now)
            throws RepositoryException {
        for (final ProfessionLevelPresentationDraft item : draft.presentations()) {
            final JSONObject value = new JSONObject();
            value.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
            value.put(ProfessionLevelPresentation.LEVEL_ID, levelId);
            value.put(ProfessionLevelPresentation.POSITION_CODE, item.positionCode());
            value.put(ProfessionLevelPresentation.LIGHT_CONFIG_JSON, item.lightConfigJson());
            value.put(ProfessionLevelPresentation.DARK_CONFIG_JSON, item.darkConfigJson());
            value.put(ProfessionLevelPresentation.CREATED_AT, now);
            value.put(ProfessionLevelPresentation.UPDATED_AT, now);
            presentationRepository.add(value);
        }
    }

    private void storeRewards(final RewardStorageRequest request) throws RepositoryException {
        for (final ProfessionLevelRewardDraft item : request.draft().rewards()) {
            final JSONObject value = new JSONObject();
            value.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
            value.put(ProfessionLevelReward.SCHEME_ID, request.schemeId());
            value.put(ProfessionLevelReward.LEVEL_ID, request.levelId());
            value.put(ProfessionLevelReward.LEVEL_CODE, request.draft().levelCode());
            value.put(ProfessionLevelReward.REWARD_CODE, item.rewardCode());
            value.put(ProfessionLevelReward.REWARD_TYPE, item.rewardType());
            value.put(ProfessionLevelReward.REWARD_CONFIG_JSON, item.rewardConfigJson());
            value.put(ProfessionLevelReward.GRANT_POLICY, item.grantPolicy());
            value.put(ProfessionLevelReward.DOWNGRADE_POLICY, item.downgradePolicy());
            value.put(ProfessionLevelReward.SORT_ORDER, item.sortOrder());
            value.put(ProfessionLevelReward.CREATED_AT, request.now());
            value.put(ProfessionLevelReward.UPDATED_AT, request.now());
            rewardRepository.add(value);
        }
    }

    private record RewardStorageRequest(String schemeId, String levelId, ProfessionLevelDraft draft, long now) {
    }
}
