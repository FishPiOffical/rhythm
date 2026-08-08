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
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.model.ProfessionLevelScheme;
import org.b3log.symphony.repository.ProfessionLevelSchemeRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 发布与停用职业等级方案。 */
@Service
public class ProfessionLevelSchemeMgmtService {

    @Inject private ProfessionRepository professionRepository;
    @Inject private ProfessionLevelSchemeRepository schemeRepository;
    @Inject private ProfessionDefinitionMgmtService definitionMgmtService;
    @Inject private ProfessionDefinitionQueryService definitionQueryService;
    @Inject private ProfessionLevelDraftValidator levelDraftValidator;

    public String copyDraft(final String sourceSchemeId, final String operatorUserId) throws RepositoryException {
        validateId(sourceSchemeId, "等级方案");
        validateId(operatorUserId, "操作用户");
        final JSONObject source = schemeRepository.get(sourceSchemeId);
        if (null == source) {
            throw new RepositoryException("等级方案不存在");
        }
        return definitionMgmtService.createLevelSchemeDraft(snapshotRequest(source, operatorUserId));
    }

    public String rollback(final String professionId, final String sourceSchemeId, final String operatorUserId)
            throws RepositoryException {
        validateId(professionId, "职业");
        final JSONObject source = schemeRepository.get(sourceSchemeId);
        if (null == source || !professionId.equals(source.optString(ProfessionLevelScheme.PROFESSION_ID))) {
            throw new RepositoryException("目标等级方案不匹配");
        }
        final String draftId = copyDraft(sourceSchemeId, operatorUserId);
        publish(draftId, operatorUserId);
        return draftId;
    }

    public void publish(final String schemeId, final String operatorUserId) throws RepositoryException {
        validateId(schemeId, "等级方案");
        validateId(operatorUserId, "操作用户");
        final Transaction transaction = schemeRepository.beginTransaction();
        try {
            final long now = System.currentTimeMillis();
            final JSONObject scheme = requireDraft(schemeRepository.get(schemeId));
            levelDraftValidator.validate(snapshotRequest(scheme, operatorUserId));
            final JSONObject profession = requireProfession(scheme);
            requireNoScheduledScheme(scheme);
            if ("SCHEDULED_SWITCH".equals(scheme.getString(ProfessionLevelScheme.MIGRATION_POLICY))) {
                scheduleScheme(scheme, operatorUserId, now);
                transaction.commit();
                return;
            }
            retireCurrent(profession.optString(Profession.CURRENT_LEVEL_SCHEME_ID), now);
            publishScheme(scheme, operatorUserId, now);
            profession.put(Profession.CURRENT_LEVEL_SCHEME_ID, schemeId);
            profession.put(Profession.UPDATED_AT, now);
            professionRepository.update(profession.getString(Keys.OBJECT_ID), profession);
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            throw exception(e);
        }
    }

    private void requireNoScheduledScheme(final JSONObject scheme) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(ProfessionLevelScheme.PROFESSION_ID, FilterOperator.EQUAL,
                        scheme.getString(ProfessionLevelScheme.PROFESSION_ID)),
                new PropertyFilter(ProfessionLevelScheme.STATUS, FilterOperator.EQUAL, "SCHEDULED")));
        if (null != schemeRepository.getFirst(query)) {
            throw new RepositoryException("该职业已有待生效的等级方案");
        }
    }

    public void retire(final String schemeId, final String operatorUserId) throws RepositoryException {
        validateId(schemeId, "等级方案");
        validateId(operatorUserId, "操作用户");
        final Transaction transaction = schemeRepository.beginTransaction();
        try {
            final long now = System.currentTimeMillis();
            final JSONObject scheme = schemeRepository.get(schemeId);
            if (null == scheme) throw new RepositoryException("等级方案不存在");
            scheme.put(ProfessionLevelScheme.STATUS, "RETIRED");
            scheme.put(ProfessionLevelScheme.EFFECTIVE_TO, now);
            schemeRepository.update(schemeId, scheme);
            clearCurrentScheme(scheme, now);
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            throw exception(e);
        }
    }

    private JSONObject requireDraft(final JSONObject scheme) throws RepositoryException {
        if (null == scheme || !"DRAFT".equals(scheme.optString(ProfessionLevelScheme.STATUS))) {
            throw new RepositoryException("等级方案不可发布");
        }
        return scheme;
    }

    private JSONObject requireProfession(final JSONObject scheme) throws RepositoryException {
        final JSONObject profession = professionRepository.get(scheme.getString(ProfessionLevelScheme.PROFESSION_ID));
        if (null == profession || !"PUBLISHED".equals(profession.optString(Profession.STATUS))) {
            throw new RepositoryException("职业尚未发布");
        }
        if (!scheme.getString(ProfessionLevelScheme.PROFESSION_REVISION_ID)
                .equals(profession.optString(Profession.CURRENT_REVISION_ID))) {
            throw new RepositoryException("等级方案不属于当前职业版本");
        }
        return profession;
    }

    private void scheduleScheme(final JSONObject scheme, final String operatorUserId, final long now)
            throws RepositoryException {
        final long scheduledAt = new JSONObject(scheme.getString(ProfessionLevelScheme.MIGRATION_CONFIG_JSON))
                .getLong("scheduledSwitchAt");
        scheme.put(ProfessionLevelScheme.STATUS, "SCHEDULED");
        scheme.put(ProfessionLevelScheme.EFFECTIVE_FROM, scheduledAt);
        scheme.put(ProfessionLevelScheme.PUBLISHED_BY, operatorUserId);
        scheme.put(ProfessionLevelScheme.PUBLISHED_AT, now);
        schemeRepository.update(scheme.getString(Keys.OBJECT_ID), scheme);
    }

    private void retireCurrent(final String schemeId, final long now) throws RepositoryException {
        if (schemeId.isBlank()) return;
        final JSONObject current = schemeRepository.get(schemeId);
        current.put(ProfessionLevelScheme.STATUS, "RETIRED");
        current.put(ProfessionLevelScheme.EFFECTIVE_TO, now);
        schemeRepository.update(schemeId, current);
    }

    private void publishScheme(final JSONObject scheme, final String operatorUserId, final long now) throws RepositoryException {
        scheme.put(ProfessionLevelScheme.STATUS, "PUBLISHED");
        scheme.put(ProfessionLevelScheme.EFFECTIVE_FROM, now);
        scheme.put(ProfessionLevelScheme.PUBLISHED_BY, operatorUserId);
        scheme.put(ProfessionLevelScheme.PUBLISHED_AT, now);
        schemeRepository.update(scheme.getString(Keys.OBJECT_ID), scheme);
    }

    private void clearCurrentScheme(final JSONObject scheme, final long now) throws RepositoryException {
        final JSONObject profession = professionRepository.get(scheme.getString(ProfessionLevelScheme.PROFESSION_ID));
        if (null == profession) {
            throw new RepositoryException("职业不存在");
        }
        if (!scheme.getString(Keys.OBJECT_ID).equals(profession.optString(Profession.CURRENT_LEVEL_SCHEME_ID))) return;
        profession.put(Profession.CURRENT_LEVEL_SCHEME_ID, "");
        profession.put(Profession.UPDATED_AT, now);
        professionRepository.update(profession.getString(Keys.OBJECT_ID), profession);
    }

    private List<ProfessionLevelDraft> levelDrafts(final JSONArray levels) {
        final List<ProfessionLevelDraft> result = new ArrayList<>();
        for (final Object item : levels) {
            final JSONObject level = (JSONObject) item;
            result.add(new ProfessionLevelDraft(level.getString("levelCode"), level.getInt("sortOrder"),
                    level.getLong("requiredTotalExperience"), level.getString("displayName"),
                    level.optString("shortName"), level.optString("description"),
                    level.optString("achievementDescription"), level.optBoolean("isTopLevel"),
                    presentationDrafts(level.getJSONArray("presentations")),
                    rewardDrafts(level.getJSONArray("rewards"))));
        }
        return List.copyOf(result);
    }

    private ProfessionLevelSchemeDraftRequest snapshotRequest(final JSONObject source, final String operatorUserId)
            throws RepositoryException {
        return new ProfessionLevelSchemeDraftRequest(source.getString(ProfessionLevelScheme.PROFESSION_ID),
                source.getString(ProfessionLevelScheme.PROFESSION_REVISION_ID),
                source.getString(ProfessionLevelScheme.MIGRATION_POLICY),
                source.getString(ProfessionLevelScheme.REWARD_MIGRATION_POLICY),
                source.getString(ProfessionLevelScheme.MIGRATION_CONFIG_JSON),
                levelDrafts(definitionQueryService.getLevels(source.getString(Keys.OBJECT_ID))), operatorUserId);
    }

    private List<ProfessionLevelPresentationDraft> presentationDrafts(final JSONArray values) {
        final List<ProfessionLevelPresentationDraft> result = new ArrayList<>();
        for (final Object item : values) {
            final JSONObject value = (JSONObject) item;
            result.add(new ProfessionLevelPresentationDraft(value.getString("positionCode"),
                    value.getString("lightConfigJson"), value.getString("darkConfigJson")));
        }
        return List.copyOf(result);
    }

    private List<ProfessionLevelRewardDraft> rewardDrafts(final JSONArray values) {
        final List<ProfessionLevelRewardDraft> result = new ArrayList<>();
        for (final Object item : values) {
            final JSONObject value = (JSONObject) item;
            result.add(new ProfessionLevelRewardDraft(value.getString("rewardCode"),
                    value.getString("rewardType"), value.getString("rewardConfigJson"),
                    value.getString("grantPolicy"), value.getString("downgradePolicy"),
                    value.getInt("sortOrder")));
        }
        return List.copyOf(result);
    }

    private void validateId(final String value, final String name) {
        if (null == value || !value.matches("^[0-9]{1,19}$")) throw new IllegalArgumentException(name + "标识不合法");
    }

    private RepositoryException exception(final Exception error) {
        return error instanceof RepositoryException result ? result : new RepositoryException(error);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) transaction.rollback();
    }
}
