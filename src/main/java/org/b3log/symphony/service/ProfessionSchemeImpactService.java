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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** 预览等级方案发布对现有用户汇总的影响。 */
@Service
public class ProfessionSchemeImpactService {

    @Inject private ProfessionRepository professionRepository;
    @Inject private UserProfessionRepository userProfessionRepository;
    @Inject private ProfessionDefinitionQueryService definitionQueryService;

    public JSONObject preview(final String professionId, final String candidateSchemeId) throws RepositoryException {
        validateId(professionId);
        validateId(candidateSchemeId);
        final JSONObject result = userProfessionRepository.previewSchemeImpact(professionId, candidateSchemeId);
        final JSONObject profession = professionRepository.get(professionId);
        if (null == profession) {
            throw new RepositoryException("职业不存在");
        }
        final Set<String> current = levelCodes(profession.optString(Profession.CURRENT_LEVEL_SCHEME_ID));
        final Set<String> candidate = levelCodes(candidateSchemeId);
        final Set<String> currentRewards = rewardCodes(profession.optString(Profession.CURRENT_LEVEL_SCHEME_ID));
        final Set<String> candidateRewards = rewardCodes(candidateSchemeId);
        result.put("addedLevelCount", differenceSize(candidate, current));
        result.put("removedLevelCount", differenceSize(current, candidate));
        result.put("addedRewardCount", differenceSize(candidateRewards, currentRewards));
        result.put("removedRewardCount", differenceSize(currentRewards, candidateRewards));
        result.put("rankingRebuildRequired", result.optLong("totalCount") > 0L);
        result.put("candidateSchemeId", candidateSchemeId);
        return result;
    }

    private Set<String> rewardCodes(final String schemeId) throws RepositoryException {
        final Set<String> result = new HashSet<>();
        if (schemeId.isBlank()) {
            return result;
        }
        for (final Object item : definitionQueryService.getLevels(schemeId)) {
            final JSONArray rewards = ((JSONObject) item).getJSONArray("rewards");
            for (final Object reward : rewards) {
                result.add(((JSONObject) reward).getString("rewardCode"));
            }
        }
        return result;
    }

    private Set<String> levelCodes(final String schemeId) throws RepositoryException {
        final Set<String> result = new HashSet<>();
        if (schemeId.isBlank()) {
            return result;
        }
        final JSONArray levels = definitionQueryService.getLevels(schemeId);
        for (final Object item : levels) {
            result.add(((JSONObject) item).getString("levelCode"));
        }
        return result;
    }

    private int differenceSize(final Set<String> left, final Set<String> right) {
        final Set<String> difference = new HashSet<>(left);
        difference.removeAll(right);
        return difference.size();
    }

    private void validateId(final String value) {
        if (null == value || !value.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException("职业或等级方案标识不合法");
        }
    }
}
