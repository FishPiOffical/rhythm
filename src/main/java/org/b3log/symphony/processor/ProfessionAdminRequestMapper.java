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
package org.b3log.symphony.processor;

import org.b3log.symphony.service.ProfessionDefinitionDraftRequest;
import org.b3log.symphony.service.ProfessionLevelDraft;
import org.b3log.symphony.service.ProfessionLevelPresentationDraft;
import org.b3log.symphony.service.ProfessionLevelRewardDraft;
import org.b3log.symphony.service.ProfessionLevelSchemeDraftRequest;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 将管理端 JSON 转换为经过服务层校验的不可变草稿请求。 */
final class ProfessionAdminRequestMapper {

    private ProfessionAdminRequestMapper() {
    }

    static ProfessionDefinitionDraftRequest definition(final JSONObject request, final String operatorId) {
        return new ProfessionDefinitionDraftRequest(request.optString("professionCode"), request.optString("displayName"),
                request.optString("shortName"), request.optString("description"),
                request.optString("defaultPresentationJson", "{}"), operatorId);
    }

    static ProfessionLevelSchemeDraftRequest scheme(final JSONObject request, final String operatorId) {
        return new ProfessionLevelSchemeDraftRequest(request.optString("professionId"), request.optString("professionRevisionId"),
                request.optString("migrationPolicy", "MIGRATE_ALL"), request.optString("rewardMigrationPolicy", "NO_GRANT"),
                request.optString("migrationConfigJson", "{}"), levels(request.optJSONArray("levels")), operatorId);
    }

    private static List<ProfessionLevelDraft> levels(final JSONArray values) {
        if (null == values) {
            throw new IllegalArgumentException("等级不能为空");
        }
        final List<ProfessionLevelDraft> result = new ArrayList<>();
        for (final Object value : values) {
            result.add(level((JSONObject) value));
        }
        return List.copyOf(result);
    }

    private static ProfessionLevelDraft level(final JSONObject value) {
        return new ProfessionLevelDraft(value.optString("levelCode"), value.optInt("sortOrder"),
                value.optLong("requiredTotalExperience"), value.optString("displayName"), value.optString("shortName"),
                value.optString("description"), value.optString("achievementDescription"), value.optBoolean("isTopLevel"),
                presentations(value.optJSONArray("presentations")), rewards(value.optJSONArray("rewards")));
    }

    private static List<ProfessionLevelPresentationDraft> presentations(final JSONArray values) {
        final List<ProfessionLevelPresentationDraft> result = new ArrayList<>();
        if (null == values) return result;
        for (final Object value : values) {
            final JSONObject item = (JSONObject) value;
            result.add(new ProfessionLevelPresentationDraft(item.optString("positionCode"),
                    item.optString("lightConfigJson", "{}"), item.optString("darkConfigJson", "{}")));
        }
        return List.copyOf(result);
    }

    private static List<ProfessionLevelRewardDraft> rewards(final JSONArray values) {
        final List<ProfessionLevelRewardDraft> result = new ArrayList<>();
        if (null == values) return result;
        for (final Object value : values) {
            final JSONObject item = (JSONObject) value;
            result.add(new ProfessionLevelRewardDraft(item.optString("rewardCode"), item.optString("rewardType"),
                    item.optString("rewardConfigJson", "{}"), item.optString("grantPolicy", "FIRST_REACH"),
                    item.optString("downgradePolicy", "KEEP"), item.optInt("sortOrder")));
        }
        return List.copyOf(result);
    }
}
