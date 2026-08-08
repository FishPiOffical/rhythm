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
import org.json.JSONArray;
import org.json.JSONObject;

/** 导出不含用户数据的职业配置文件。 */
@Service
public class ProfessionConfigExportService {

    static final String FORMAT = "fishpi.profession-config";
    static final int FORMAT_VERSION = 1;

    @Inject private ProfessionAdminQueryService adminQueryService;

    public JSONObject exportAll() throws RepositoryException {
        return document("ALL", adminQueryService.catalog());
    }

    public JSONObject exportOne(final String professionId) throws RepositoryException {
        if (null == professionId || !professionId.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException("职业标识不合法");
        }
        final JSONArray matched = new JSONArray();
        for (final Object item : adminQueryService.catalog()) {
            final JSONObject profession = (JSONObject) item;
            if (professionId.equals(profession.optString(Keys.OBJECT_ID))) matched.put(profession);
        }
        if (matched.isEmpty()) throw new IllegalArgumentException("职业不存在");
        return document("SINGLE", matched);
    }

    private JSONObject document(final String scope, final JSONArray values) {
        final JSONObject result = new JSONObject();
        result.put("format", FORMAT);
        result.put("formatVersion", FORMAT_VERSION);
        result.put("exportedAt", System.currentTimeMillis());
        result.put("scope", scope);
        final JSONArray professions = new JSONArray();
        for (final Object item : values) professions.put(profession((JSONObject) item));
        return result.put("professions", professions);
    }

    private JSONObject profession(final JSONObject source) {
        final JSONObject result = new JSONObject();
        result.put("professionCode", source.getString("professionCode"));
        result.put("status", source.getString("status"));
        result.put("sortOrder", source.optInt("sortOrder"));
        result.put("revisions", revisions(source.getJSONArray("revisions")));
        result.put("schemes", schemes(source.getJSONArray("schemes"), source.getJSONArray("revisions")));
        result.put("automations", automations(source.getJSONArray("automations")));
        putReference(result, "currentRevisionNo", source.optString("currentRevisionId"), source.getJSONArray("revisions"));
        putReference(result, "currentSchemeRevisionNo", source.optString("currentLevelSchemeId"), source.getJSONArray("schemes"));
        return result;
    }

    private JSONArray revisions(final JSONArray values) {
        final JSONArray result = new JSONArray();
        for (final Object item : values) {
            final JSONObject source = (JSONObject) item;
            result.put(new JSONObject().put("revisionNo", source.getInt("revisionNo"))
                    .put("schemaVersion", source.getInt("schemaVersion"))
                    .put("status", source.getString("status"))
                    .put("displayName", source.getString("displayName"))
                    .put("shortName", source.getString("shortName"))
                    .put("description", source.getString("description"))
                    .put("defaultPresentationJson", source.getString("defaultPresentationJson")));
        }
        return result;
    }

    private JSONArray schemes(final JSONArray values, final JSONArray revisions) {
        final JSONArray result = new JSONArray();
        for (final Object item : values) {
            final JSONObject source = (JSONObject) item;
            final JSONObject value = new JSONObject().put("revisionNo", source.getInt("revisionNo"))
                    .put("schemaVersion", source.getInt("schemaVersion")).put("status", source.getString("status"))
                    .put("professionRevisionNo", referenceNo(source.optString("professionRevisionId"), revisions))
                    .put("migrationPolicy", source.getString("migrationPolicy"))
                    .put("rewardMigrationPolicy", source.getString("rewardMigrationPolicy"))
                    .put("migrationConfigJson", source.getString("migrationConfigJson"));
            value.put("levels", levels(source.getJSONArray("levels")));
            result.put(value);
        }
        return result;
    }

    private JSONArray levels(final JSONArray values) {
        final JSONArray result = new JSONArray();
        for (final Object item : values) {
            final JSONObject source = (JSONObject) item;
            final JSONObject value = new JSONObject().put("levelCode", source.getString("levelCode"))
                    .put("sortOrder", source.getInt("sortOrder"))
                    .put("requiredTotalExperience", source.getLong("requiredTotalExperience"))
                    .put("displayName", source.getString("displayName")).put("shortName", source.getString("shortName"))
                    .put("description", source.getString("description"))
                    .put("achievementDescription", source.getString("achievementDescription"))
                    .put("isTopLevel", source.getBoolean("isTopLevel"));
            value.put("presentations", presentation(source.getJSONArray("presentations")));
            value.put("rewards", rewards(source.getJSONArray("rewards")));
            result.put(value);
        }
        return result;
    }

    private JSONArray presentation(final JSONArray values) {
        final JSONArray result = new JSONArray();
        for (final Object item : values) {
            final JSONObject source = (JSONObject) item;
            result.put(new JSONObject().put("positionCode", source.getString("positionCode"))
                    .put("lightConfigJson", source.getString("lightConfigJson"))
                    .put("darkConfigJson", source.getString("darkConfigJson")));
        }
        return result;
    }

    private JSONArray rewards(final JSONArray values) {
        final JSONArray result = new JSONArray();
        for (final Object item : values) {
            final JSONObject source = (JSONObject) item;
            result.put(new JSONObject().put("rewardCode", source.getString("rewardCode"))
                    .put("rewardType", source.getString("rewardType"))
                    .put("rewardConfigJson", source.getString("rewardConfigJson"))
                    .put("grantPolicy", source.getString("grantPolicy"))
                    .put("downgradePolicy", source.getString("downgradePolicy"))
                    .put("sortOrder", source.getInt("sortOrder")));
        }
        return result;
    }

    private JSONArray automations(final JSONArray values) {
        final JSONArray result = new JSONArray();
        for (final Object item : values) {
            final JSONObject source = (JSONObject) item;
            final JSONObject value = new JSONObject().put("automationCode", source.getString("automationCode"))
                    .put("status", source.getString("status")).put("sortOrder", source.optInt("sortOrder"))
                    .put("revisions", automationRevisions(source.getJSONArray("revisions")));
            putReference(value, "currentRevisionNo", source.optString("currentRevisionId"), source.getJSONArray("revisions"));
            result.put(value);
        }
        return result;
    }

    private JSONArray automationRevisions(final JSONArray values) {
        final JSONArray result = new JSONArray();
        for (final Object item : values) {
            final JSONObject source = (JSONObject) item;
            result.put(new JSONObject().put("revisionNo", source.getInt("revisionNo"))
                    .put("schemaVersion", source.getInt("schemaVersion"))
                    .put("status", source.getString("status"))
                    .put("configurationJson", source.getString("configurationJson")));
        }
        return result;
    }

    private void putReference(final JSONObject target, final String key, final String sourceId, final JSONArray values) {
        final int number = referenceNo(sourceId, values);
        if (number > 0) target.put(key, number);
    }

    private int referenceNo(final String sourceId, final JSONArray values) {
        for (final Object item : values) {
            final JSONObject value = (JSONObject) item;
            if (sourceId.equals(value.optString(Keys.OBJECT_ID))) return value.optInt("revisionNo");
        }
        return 0;
    }
}
