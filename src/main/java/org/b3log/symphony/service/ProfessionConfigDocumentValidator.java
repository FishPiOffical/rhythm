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
import org.b3log.latke.service.annotation.Service;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 校验职业配置文件并转换等级快照。 */
@Service
public class ProfessionConfigDocumentValidator {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "RETIRED");
    private static final int MAX_PROFESSIONS = 100;
    private static final int MAX_JSON_LENGTH = 102_400;

    @Inject private ProfessionDefinitionRegistry definitionRegistry;
    @Inject private ProfessionLevelDraftValidator levelDraftValidator;
    @Inject private ProfessionAutomationRegistry automationRegistry;

    public void validate(final JSONObject document) {
        validateHeader(document);
        final Set<String> codes = new HashSet<>();
        for (final Object item : professions(document)) validateProfession((JSONObject) item, codes);
    }

    public ProfessionLevelSchemeDraftRequest levelRequest(final JSONObject scheme, final String professionId,
                                                           final String revisionId, final String operatorUserId) {
        final ProfessionLevelSchemeDraftRequest result = new ProfessionLevelSchemeDraftRequest(professionId, revisionId,
                scheme.getString("migrationPolicy"), scheme.getString("rewardMigrationPolicy"),
                scheme.getString("migrationConfigJson"), levels(scheme.getJSONArray("levels")), operatorUserId);
        levelDraftValidator.validate(validationRequest(result));
        return result;
    }

    private ProfessionLevelSchemeDraftRequest validationRequest(final ProfessionLevelSchemeDraftRequest source) {
        if (!"SCHEDULED_SWITCH".equals(source.migrationPolicy())) return source;
        final JSONObject config = new JSONObject(source.migrationConfigJson());
        if (config.optLong("scheduledSwitchAt") > System.currentTimeMillis()) return source;
        config.put("scheduledSwitchAt", System.currentTimeMillis() + 60_000L);
        return new ProfessionLevelSchemeDraftRequest(source.professionId(), source.professionRevisionId(),
                source.migrationPolicy(), source.rewardMigrationPolicy(), config.toString(), source.levels(), source.operatorUserId());
    }

    private void validateHeader(final JSONObject document) {
        if (!ProfessionConfigExportService.FORMAT.equals(document.optString("format"))
                || ProfessionConfigExportService.FORMAT_VERSION != document.optInt("formatVersion")) {
            throw new IllegalArgumentException("不支持此配置文件版本");
        }
        if (!Set.of("ALL", "SINGLE").contains(document.optString("scope"))) {
            throw new IllegalArgumentException("配置文件范围不合法");
        }
        professions(document);
    }

    private JSONArray professions(final JSONObject document) {
        final JSONArray values = document.optJSONArray("professions");
        if (null == values || values.isEmpty() || values.length() > MAX_PROFESSIONS) {
            throw new IllegalArgumentException("职业数量不合法");
        }
        return values;
    }

    private void validateProfession(final JSONObject value, final Set<String> codes) {
        final String code = value.optString("professionCode");
        if (!CODE.matcher(code).matches() || !codes.add(code)) throw new IllegalArgumentException("职业代号不合法或重复");
        validateStatus(value.optString("status"));
        final Map<Integer, String> revisions = validateRevisions(value.getJSONArray("revisions"));
        final Map<Integer, String> schemes = validateSchemes(value.getJSONArray("schemes"), revisions);
        validateCurrent(value, "currentRevisionNo", revisions, value.getString("status"));
        validateCurrent(value, "currentSchemeRevisionNo", schemes, value.getString("status"));
        validateAutomations(value.getJSONArray("automations"));
    }

    private Map<Integer, String> validateRevisions(final JSONArray values) {
        final Map<Integer, String> result = new HashMap<>();
        for (final Object item : requiredArray(values, "职业历史")) {
            final JSONObject value = (JSONObject) item;
            validateStatus(value.optString("status"));
            requireNumber(value, "revisionNo");
            if (result.put(value.getInt("revisionNo"), value.getString("status")) != null) throw new IllegalArgumentException("职业历史重复");
            requireText(value, "displayName", 64, true);
            requireText(value, "shortName", 32, false);
            requireText(value, "description", 1_024, false);
            final String presentation = requireText(value, "defaultPresentationJson", MAX_JSON_LENGTH, true);
            definitionRegistry.validatePresentation(new ProfessionLevelPresentationDraft("homeProfile", presentation, presentation));
        }
        return result;
    }

    private Map<Integer, String> validateSchemes(final JSONArray values, final Map<Integer, String> revisions) {
        final Map<Integer, String> result = new HashMap<>();
        for (final Object item : values) {
            final JSONObject value = (JSONObject) item;
            validateStatus(value.optString("status"));
            final int revisionNo = requireNumber(value, "revisionNo");
            if (result.put(revisionNo, value.getString("status")) != null) throw new IllegalArgumentException("等级记录重复");
            if (!revisions.containsKey(requireNumber(value, "professionRevisionNo"))) throw new IllegalArgumentException("等级记录关联的职业历史不存在");
            levelRequest(value, "1", "1", "1");
        }
        return result;
    }

    private void validateCurrent(final JSONObject source, final String key, final Map<Integer, String> values,
                                 final String status) {
        final int number = source.optInt(key);
        if (0 == number) {
            if ("PUBLISHED".equals(status) && "currentRevisionNo".equals(key)) throw new IllegalArgumentException("已启用职业缺少当前历史记录");
            return;
        }
        final String targetStatus = values.get(number);
        if (null == targetStatus) throw new IllegalArgumentException("当前历史记录不存在");
        if ("PUBLISHED".equals(status) && !"PUBLISHED".equals(targetStatus)) throw new IllegalArgumentException("当前历史记录未启用");
        if ("RETIRED".equals(status) && !"RETIRED".equals(targetStatus)) throw new IllegalArgumentException("停用职业的历史记录不一致");
    }

    private void validateAutomations(final JSONArray values) {
        final Set<String> codes = new HashSet<>();
        for (final Object item : values) {
            final JSONObject value = (JSONObject) item;
            final String code = value.optString("automationCode");
            if (!CODE.matcher(code).matches() || !codes.add(code)) throw new IllegalArgumentException("经验规则标识不合法或重复");
            validateStatus(value.optString("status"));
            final Map<Integer, String> revisions = validateAutomationRevisions(value.getJSONArray("revisions"));
            validateCurrent(value, "currentRevisionNo", revisions, value.getString("status"));
        }
    }

    private Map<Integer, String> validateAutomationRevisions(final JSONArray values) {
        final Map<Integer, String> result = new HashMap<>();
        for (final Object item : requiredArray(values, "经验规则历史")) {
            final JSONObject value = (JSONObject) item;
            validateStatus(value.optString("status"));
            final int revisionNo = requireNumber(value, "revisionNo");
            if (result.put(revisionNo, value.getString("status")) != null) throw new IllegalArgumentException("经验规则历史重复");
            final String configuration = requireText(value, "configurationJson", MAX_JSON_LENGTH, true);
            automationRegistry.validate(new JSONObject(configuration));
        }
        return result;
    }

    private List<ProfessionLevelDraft> levels(final JSONArray values) {
        final List<ProfessionLevelDraft> result = new ArrayList<>();
        for (final Object item : requiredArray(values, "等级")) result.add(level((JSONObject) item));
        return List.copyOf(result);
    }

    private ProfessionLevelDraft level(final JSONObject value) {
        return new ProfessionLevelDraft(requireText(value, "levelCode", 64, true), requireNumber(value, "sortOrder"),
                value.getLong("requiredTotalExperience"), requireText(value, "displayName", 64, true),
                requireText(value, "shortName", 32, false), requireText(value, "description", 1_024, false),
                requireText(value, "achievementDescription", 1_024, false), value.getBoolean("isTopLevel"),
                presentations(value.getJSONArray("presentations")), rewards(value.getJSONArray("rewards")));
    }

    private List<ProfessionLevelPresentationDraft> presentations(final JSONArray values) {
        final List<ProfessionLevelPresentationDraft> result = new ArrayList<>();
        for (final Object item : values) {
            final JSONObject value = (JSONObject) item;
            result.add(new ProfessionLevelPresentationDraft(requireText(value, "positionCode", 32, true),
                    requireText(value, "lightConfigJson", MAX_JSON_LENGTH, true),
                    requireText(value, "darkConfigJson", MAX_JSON_LENGTH, true)));
        }
        return List.copyOf(result);
    }

    private List<ProfessionLevelRewardDraft> rewards(final JSONArray values) {
        final List<ProfessionLevelRewardDraft> result = new ArrayList<>();
        for (final Object item : values) {
            final JSONObject value = (JSONObject) item;
            result.add(new ProfessionLevelRewardDraft(requireText(value, "rewardCode", 64, true),
                    requireText(value, "rewardType", 32, true), requireText(value, "rewardConfigJson", MAX_JSON_LENGTH, true),
                    requireText(value, "grantPolicy", 32, true), requireText(value, "downgradePolicy", 32, true),
                    requireNumber(value, "sortOrder")));
        }
        return List.copyOf(result);
    }

    private JSONArray requiredArray(final JSONArray values, final String name) {
        if (null == values || values.isEmpty()) throw new IllegalArgumentException(name + "不能为空");
        return values;
    }

    private int requireNumber(final JSONObject value, final String key) {
        final int result = value.optInt(key, Integer.MIN_VALUE);
        if (Integer.MIN_VALUE == result || result < 0) throw new IllegalArgumentException(key + "不合法");
        return result;
    }

    private String requireText(final JSONObject value, final String key, final int max, final boolean required) {
        final String result = value.optString(key, null);
        if (null == result || result.length() > max || (required && result.isBlank()) || (!required && !result.isEmpty() && result.isBlank())) {
            throw new IllegalArgumentException(key + "不合法");
        }
        return result;
    }

    private void validateStatus(final String status) {
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("状态不合法");
    }
}
