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
import org.b3log.symphony.model.ProfessionLevel;
import org.b3log.symphony.model.UserProfession;
import org.b3log.symphony.model.UserProfessionContribution;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/** 为职业本人读取等级路线、贡献汇总和最近九十天记录。 */
@Service
public class ProfessionProfileDetailQueryService {

    @Inject private UserProfessionRepository userProfessionRepository;
    @Inject private UserProfessionQueryService userProfessionQueryService;
    @Inject private ProfessionCatalogQueryService catalogQueryService;
    @Inject private ProfessionDefinitionQueryService definitionQueryService;
    @Inject private ProfessionActivitySourceDescriptionService activitySourceDescriptionService;

    public JSONObject getDetail(final String userId, final String professionId, final boolean darkMode)
            throws RepositoryException {
        final JSONObject progress = progressOrZero(userId, professionId);
        final JSONObject result = new JSONObject();
        result.put("profession", catalogQueryService.getProgressView(progress, "professionPage", darkMode));
        result.put("levels", levels(progress.getString(UserProfession.LEVEL_SCHEME_ID)));
        result.put("rarity", userProfessionQueryService.getRarity(userId, professionId));
        result.put("contributions", contributions(userId, professionId));
        return result;
    }

    public JSONObject getRecords(final RecordQuery request) throws RepositoryException {
        if (null == findProgress(request.userId(), request.professionId())) {
            catalogQueryService.zeroProgress(request.professionId());
            return new JSONObject().put("records", new JSONArray());
        }
        final List<JSONObject> values = userProfessionQueryService.getRecentEffects(
                new UserProfessionQueryService.DetailQuery(request.userId(), request.professionId(),
                        request.beforeOccurredAt(), request.beforeEffectId(), request.limit()));
        final Map<String, String> descriptions = activitySourceDescriptionService.descriptions(values, true);
        final JSONArray records = new JSONArray();
        for (final JSONObject value : values) {
            records.put(record(value, descriptions.get(value.getString(Keys.OBJECT_ID))));
        }
        final JSONObject result = new JSONObject().put("records", records);
        if (values.size() == request.limit()) {
            final JSONObject last = values.getLast();
            result.put("nextCursor", new JSONObject().put("beforeOccurredAt",
                    last.getLong(ProfessionEventEffect.OCCURRED_AT)).put("beforeEffectId", last.getString("oId")));
        }
        return result;
    }

    private JSONObject progressOrZero(final String userId, final String professionId) throws RepositoryException {
        final JSONObject progress = findProgress(userId, professionId);
        return null == progress ? catalogQueryService.zeroProgress(professionId) : progress;
    }

    private JSONObject findProgress(final String userId, final String professionId) throws RepositoryException {
        validateId(userId, "用户");
        validateId(professionId, "职业");
        return userProfessionRepository.getByUserProfession(userId, professionId);
    }

    private void validateId(final String value, final String name) {
        if (null == value || !value.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException(name + "标识不合法");
        }
    }

    private JSONArray levels(final String schemeId) throws RepositoryException {
        final JSONArray result = new JSONArray();
        for (final Object item : definitionQueryService.getLevels(schemeId)) {
            final JSONObject level = (JSONObject) item;
            result.put(new JSONObject().put("levelCode", level.getString(ProfessionLevel.CODE))
                    .put("sortOrder", level.getInt(ProfessionLevel.SORT_ORDER))
                    .put("requiredExperience", level.getLong(ProfessionLevel.REQUIRED_TOTAL_EXPERIENCE))
                    .put("displayName", level.getString(ProfessionLevel.DISPLAY_NAME))
                    .put("shortName", level.optString(ProfessionLevel.SHORT_NAME))
                    .put("description", level.optString(ProfessionLevel.DESCRIPTION))
                    .put("achievementDescription", level.optString(ProfessionLevel.ACHIEVEMENT_DESCRIPTION))
                    .put("topLevel", level.optBoolean(ProfessionLevel.TOP_LEVEL)));
        }
        return result;
    }

    private JSONArray contributions(final String userId, final String professionId) throws RepositoryException {
        final JSONArray result = new JSONArray();
        for (final JSONObject value : userProfessionQueryService.getContributions(userId, professionId)) {
            result.put(new JSONObject().put("actionCode", value.getString(UserProfessionContribution.ACTION_CODE))
                    .put("experienceSum", value.getLong(UserProfessionContribution.EXPERIENCE_SUM))
                    .put("eventCount", value.getLong(UserProfessionContribution.EVENT_COUNT))
                    .put("lastOccurredAt", value.getLong(UserProfessionContribution.LAST_OCCURRED_AT)));
        }
        return result;
    }

    private JSONObject record(final JSONObject value, final String sourceLabel) {
        return new JSONObject().put("actionCode", value.getString(ProfessionEventEffect.ACTION_CODE))
                .put("sourceLabel", sourceLabel)
                .put("experienceDelta", value.getLong(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA))
                .put("beforeExperience", value.getLong(ProfessionEventEffect.BEFORE_EXPERIENCE))
                .put("afterExperience", value.getLong(ProfessionEventEffect.AFTER_EXPERIENCE))
                .put("beforeLevelCode", value.optString(ProfessionEventEffect.BEFORE_LEVEL_CODE))
                .put("afterLevelCode", value.optString(ProfessionEventEffect.AFTER_LEVEL_CODE))
                .put("occurredAt", value.getLong(ProfessionEventEffect.OCCURRED_AT));
    }

    public record RecordQuery(String userId, String professionId, long beforeOccurredAt,
                              String beforeEffectId, int limit) {
    }
}
