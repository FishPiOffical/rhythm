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
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.model.UserProfessionPrivacy;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.UserProfessionPrivacyRepository;
import org.json.JSONObject;

import java.util.Set;

/** 管理职业首次选择、主职业与模块隐私。 */
@Service
public class ProfessionPrivacyMgmtService {

    private static final Set<String> PRESETS = Set.of("ALL_PUBLIC", "LEVEL_ONLY", "PRIMARY_ONLY",
            "SELF_ONLY", "FULLY_HIDDEN", "CUSTOM");
    private static final Set<String> MODULES = Set.of("primaryProfession", "allProfessions", "experience",
            "rarity", "majorContributions", "contributionStats", "activityFeed", "levelHistory", "rewards", "ranking");
    private static final Set<String> VISIBILITIES = Set.of("PUBLIC", "LOGGED_IN", "SELF", "HIDDEN");

    @Inject private UserProfessionPrivacyRepository privacyRepository;
    @Inject private ProfessionRepository professionRepository;
    @Inject private UserProfessionQueryService userProfessionQueryService;
    @Inject private ProfessionCatalogQueryService catalogQueryService;
    @Inject private ProfessionGrowthGuideQueryService growthGuideQueryService;

    public JSONObject get(final String userId) throws RepositoryException {
        validateId(userId, "用户");
        final JSONObject stored = find(userId);
        return null == stored ? defaults(userId) : new JSONObject(stored.toString());
    }

    public JSONObject getProfile(final String userId) throws RepositoryException {
        final JSONObject result = get(userId);
        final org.json.JSONArray progress = new org.json.JSONArray();
        final String primaryProfessionId = result.optString(UserProfessionPrivacy.PRIMARY_PROFESSION_ID);
        boolean containsPrimary = false;
        for (final JSONObject item : userProfessionQueryService.getUserProgress(userId)) {
            progress.put(catalogQueryService.getProgressView(item, "selectionCard", false));
            containsPrimary = primaryProfessionId.equals(item.optString("professionId"));
        }
        final String primaryState = primaryState(primaryProfessionId);
        if ("ACTIVE".equals(primaryState) && !containsPrimary) {
            progress.put(catalogQueryService.getZeroProgressView(primaryProfessionId, "selectionCard", false));
        }
        result.put("availableProfessions", catalogQueryService.getPublished());
        result.put("growthGuides", growthGuideQueryService.publishedTriggers());
        result.put("rewardGuides", growthGuideQueryService.publishedRewards());
        result.put("progress", progress);
        result.put("primaryProfessionState", primaryState);
        return result;
    }

    public void skipOnboarding(final String userId) throws RepositoryException {
        updateState(new StateRequest(userId, "SKIPPED", "", null, null));
    }

    public void selectPrimary(final String userId, final String professionId) throws RepositoryException {
        validateId(professionId, "职业");
        final JSONObject profession = professionRepository.get(professionId);
        if (null == profession || !"PUBLISHED".equals(profession.optString(Profession.STATUS))) {
            throw new IllegalArgumentException("职业不存在或尚未发布");
        }
        updateState(new StateRequest(userId, "SELECTED", professionId, null, null));
    }

    public void updatePrivacy(final String userId, final String preset, final String moduleVisibilityJson)
            throws RepositoryException {
        if (!PRESETS.contains(preset)) {
            throw new IllegalArgumentException("职业隐私预设不合法");
        }
        final JSONObject visibility = "CUSTOM".equals(preset)
                ? validateCustom(moduleVisibilityJson) : presetVisibility(preset);
        updateState(new StateRequest(userId, null, null, preset, visibility));
    }

    public void touchProgressInCurrentTransaction(final String userId, final long now) throws RepositoryException {
        validateId(userId, "用户");
        final JSONObject stored = privacyRepository.getByUserForUpdate(userId);
        final JSONObject value = null == stored ? defaults(userId) : stored;
        value.put(UserProfessionPrivacy.USER_PROGRESS_VERSION,
                value.getLong(UserProfessionPrivacy.USER_PROGRESS_VERSION) + 1L);
        value.put(UserProfessionPrivacy.PUBLIC_STATS_VERSION,
                value.getLong(UserProfessionPrivacy.PUBLIC_STATS_VERSION) + 1L);
        value.put(UserProfessionPrivacy.UPDATED_AT, now);
        if (null == stored) privacyRepository.add(value); else privacyRepository.update(value.getString(Keys.OBJECT_ID), value);
    }

    private void updateState(final StateRequest request) throws RepositoryException {
        validateId(request.userId(), "用户");
        final Transaction transaction = privacyRepository.beginTransaction();
        try {
            final long now = System.currentTimeMillis();
            final JSONObject value = find(request.userId());
            final JSONObject state = null == value ? defaults(request.userId()) : value;
            apply(state, request, now);
            if (null == value) {
                privacyRepository.add(state);
            } else {
                privacyRepository.update(state.getString(Keys.OBJECT_ID), state);
            }
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            throw exception(e);
        }
    }

    private void apply(final JSONObject value, final StateRequest request, final long now) {
        if (null != request.onboardingState()) {
            value.put(UserProfessionPrivacy.ONBOARDING_STATE, request.onboardingState());
            value.put(UserProfessionPrivacy.PRIMARY_PROFESSION_ID, request.primaryProfessionId());
            value.put(UserProfessionPrivacy.USER_PROGRESS_VERSION,
                    value.getLong(UserProfessionPrivacy.USER_PROGRESS_VERSION) + 1L);
        }
        if (null != request.visibility()) {
            value.put(UserProfessionPrivacy.PRIVACY_PRESET, request.privacyPreset());
            value.put(UserProfessionPrivacy.MODULE_VISIBILITY_JSON, request.visibility().toString());
            value.put(UserProfessionPrivacy.USER_PRIVACY_VERSION,
                    value.getLong(UserProfessionPrivacy.USER_PRIVACY_VERSION) + 1L);
            value.put(UserProfessionPrivacy.PUBLIC_STATS_VERSION,
                    value.getLong(UserProfessionPrivacy.PUBLIC_STATS_VERSION) + 1L);
        }
        value.put(UserProfessionPrivacy.UPDATED_AT, now);
    }

    private JSONObject defaults(final String userId) {
        final long now = System.currentTimeMillis();
        final JSONObject value = new JSONObject();
        value.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        value.put(UserProfessionPrivacy.USER_ID, userId);
        value.put(UserProfessionPrivacy.ONBOARDING_STATE, "UNDECIDED");
        value.put(UserProfessionPrivacy.PRIMARY_PROFESSION_ID, "");
        value.put(UserProfessionPrivacy.PRIVACY_PRESET, "ALL_PUBLIC");
        value.put(UserProfessionPrivacy.MODULE_VISIBILITY_JSON, presetVisibility("ALL_PUBLIC").toString());
        value.put(UserProfessionPrivacy.USER_PROGRESS_VERSION, 0L);
        value.put(UserProfessionPrivacy.USER_PRIVACY_VERSION, 0L);
        value.put(UserProfessionPrivacy.PUBLIC_STATS_VERSION, 0L);
        value.put(UserProfessionPrivacy.CREATED_AT, now);
        value.put(UserProfessionPrivacy.UPDATED_AT, now);
        return value;
    }

    private JSONObject validateCustom(final String json) {
        if (null == json || json.isBlank() || json.length() > 4_096) {
            throw new IllegalArgumentException("职业隐私配置长度不合法");
        }
        final JSONObject value = new JSONObject(json);
        if (!value.keySet().equals(MODULES)) {
            throw new IllegalArgumentException("职业隐私配置字段不完整");
        }
        for (final String module : MODULES) {
            if (!VISIBILITIES.contains(value.optString(module))) {
                throw new IllegalArgumentException("职业隐私范围不合法");
            }
        }
        return value;
    }

    private JSONObject presetVisibility(final String preset) {
        final JSONObject result = new JSONObject();
        for (final String module : MODULES) {
            result.put(module, visibilityFor(preset, module));
        }
        return result;
    }

    private String visibilityFor(final String preset, final String module) {
        return switch (preset) {
            case "ALL_PUBLIC" -> "PUBLIC";
            case "LEVEL_ONLY" -> "primaryProfession".equals(module) ? "PUBLIC" : "HIDDEN";
            case "PRIMARY_ONLY" -> "primaryProfession".equals(module) ? "PUBLIC" : "HIDDEN";
            case "SELF_ONLY" -> "SELF";
            case "FULLY_HIDDEN" -> "HIDDEN";
            default -> throw new IllegalArgumentException("职业隐私预设不合法");
        };
    }

    private JSONObject find(final String userId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(UserProfessionPrivacy.USER_ID,
                FilterOperator.EQUAL, userId));
        return privacyRepository.getFirst(query);
    }

    private String primaryState(final String professionId) throws RepositoryException {
        if (professionId.isBlank()) {
            return "NONE";
        }
        final JSONObject profession = professionRepository.get(professionId);
        if (null == profession) {
            return "DELETED";
        }
        return "PUBLISHED".equals(profession.optString(Profession.STATUS)) ? "ACTIVE" : "RETIRED";
    }

    private void validateId(final String value, final String name) {
        if (null == value || !value.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException(name + "标识不合法");
        }
    }

    private RepositoryException exception(final Exception error) {
        return error instanceof RepositoryException result ? result : new RepositoryException(error);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    private record StateRequest(String userId, String onboardingState, String primaryProfessionId,
                                String privacyPreset, JSONObject visibility) {
    }
}
