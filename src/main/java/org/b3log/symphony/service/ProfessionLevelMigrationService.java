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
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.model.ProfessionLevelScheme;
import org.b3log.symphony.model.UserProfession;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.ProfessionLevelSchemeRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 分批激活和迁移已发布的职业等级方案。 */
@Service
public class ProfessionLevelMigrationService {

    private static final int MIGRATION_BATCH_SIZE = 100;
    private static final Set<String> MIGRATE_ALL_POLICIES = Set.of("MIGRATE_ALL", "SCHEDULED_SWITCH");

    @Inject private ProfessionRepository professionRepository;
    @Inject private ProfessionLevelSchemeRepository schemeRepository;
    @Inject private UserProfessionRepository userProfessionRepository;
    @Inject private ProfessionEventEffectRepository effectRepository;
    @Inject private ProfessionDefinitionQueryService definitionQueryService;
    @Inject private ProfessionLevelResolver levelResolver;
    @Inject private ProfessionRewardGrantService rewardGrantService;
    @Inject private ProfessionAutomationNotificationService notificationService;
    @Inject private ProfessionScheduledSchemeService scheduledSchemeService;

    public void migratePending() throws RepositoryException {
        scheduledSchemeService.activateDue();
        final Query query = new Query().setFilter(new PropertyFilter(
                Profession.STATUS, FilterOperator.EQUAL, "PUBLISHED"));
        for (final JSONObject profession : professionRepository.getList(query)) {
            migrateProfession(profession);
        }
    }

    private void migrateProfession(final JSONObject profession) throws RepositoryException {
        final String schemeId = profession.optString(Profession.CURRENT_LEVEL_SCHEME_ID);
        if (schemeId.isBlank()) {
            return;
        }
        final JSONObject scheme = schemeRepository.get(schemeId);
        if (null == scheme || !MIGRATE_ALL_POLICIES.contains(
                scheme.optString(ProfessionLevelScheme.MIGRATION_POLICY))) {
            return;
        }
        migrateBatch(new MigrationTarget(profession, scheme, System.currentTimeMillis()));
    }

    private void migrateBatch(final MigrationTarget target) throws RepositoryException {
        final Transaction transaction = userProfessionRepository.beginTransaction();
        try {
            final List<JSONObject> summaries = userProfessionRepository.getMigrationBatch(
                    target.profession().getString(Keys.OBJECT_ID), target.scheme().getString(Keys.OBJECT_ID),
                    MIGRATION_BATCH_SIZE);
            final MigrationContext context = context(target);
            for (final JSONObject summary : summaries) {
                migrateSummary(summary, context);
            }
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            throw repositoryException(e);
        }
    }

    private MigrationContext context(final MigrationTarget target) throws RepositoryException {
        final String schemeId = target.scheme().getString(Keys.OBJECT_ID);
        return new MigrationContext(target, definitionQueryService.getLevels(schemeId),
                new JSONObject(target.scheme().getString(ProfessionLevelScheme.MIGRATION_CONFIG_JSON)),
                new HashMap<>());
    }

    private void migrateSummary(final JSONObject summary, final MigrationContext context) throws RepositoryException {
        final String schemeId = context.target().scheme().getString(Keys.OBJECT_ID);
        final long experience = summary.getLong(UserProfession.TOTAL_EXPERIENCE);
        final JSONObject afterLevel = levelResolver.resolve(schemeId, experience);
        final JSONObject effect = migrationEffect(summary, context, afterLevel);
        if (!effectRepository.addIfAbsent(effect)) {
            throw new IllegalStateException("等级迁移效果已经存在但用户汇总尚未迁移");
        }
        final List<JSONObject> newlyReached = newlyReachedLevels(summary, context);
        rewardGrantService.grantCrossed(effect, rewardLevels(summary, context), context.target().now());
        notificationService.sendMigrationNotifications(effect, newlyReached, context.target().now());
        updateSummary(summary, context, afterLevel);
        effect.put(ProfessionEventEffect.STATUS, "APPLIED");
        effectRepository.update(effect.getString(Keys.OBJECT_ID), effect);
    }

    private JSONObject migrationEffect(final JSONObject summary, final MigrationContext context,
                                       final JSONObject afterLevel) {
        final JSONObject effect = new JSONObject();
        final String schemeId = context.target().scheme().getString(Keys.OBJECT_ID);
        final long now = context.target().now();
        effect.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        effect.put(ProfessionEventEffect.EFFECT_KEY, "level-migration:" + schemeId + ':'
                + summary.getString(UserProfession.USER_ID));
        effect.put(ProfessionEventEffect.SOURCE_EVENT_ID, "");
        effect.put(ProfessionEventEffect.EVENT_KEY, "");
        effect.put(ProfessionEventEffect.BUCKET_ID, "");
        effect.put(ProfessionEventEffect.BUCKET_KEY, "");
        effect.put(ProfessionEventEffect.AUTOMATION_REVISION_ID, "");
        effect.put(ProfessionEventEffect.ACTION_CODE, "level.migration");
        effect.put(ProfessionEventEffect.USER_ID, summary.getString(UserProfession.USER_ID));
        effect.put(ProfessionEventEffect.PROFESSION_ID, summary.getString(UserProfession.PROFESSION_ID));
        effect.put(ProfessionEventEffect.PROFESSION_REVISION_ID,
                context.target().scheme().getString(ProfessionLevelScheme.PROFESSION_REVISION_ID));
        effect.put(ProfessionEventEffect.LEVEL_SCHEME_ID, schemeId);
        effect.put(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA, 0L);
        effect.put(ProfessionEventEffect.BEFORE_EXPERIENCE, summary.getLong(UserProfession.TOTAL_EXPERIENCE));
        effect.put(ProfessionEventEffect.AFTER_EXPERIENCE, summary.getLong(UserProfession.TOTAL_EXPERIENCE));
        effect.put(ProfessionEventEffect.BEFORE_LEVEL_CODE, summary.getString(UserProfession.CURRENT_LEVEL_CODE));
        effect.put(ProfessionEventEffect.AFTER_LEVEL_CODE, afterLevel.getString("levelCode"));
        effect.put(ProfessionEventEffect.STATUS, "APPLYING");
        effect.put(ProfessionEventEffect.REVERSES_EFFECT_ID, "");
        effect.put(ProfessionEventEffect.REVERSED_BY_EFFECT_ID, "");
        effect.put(ProfessionEventEffect.OCCURRED_AT, now);
        effect.put(ProfessionEventEffect.CREATED_AT, now);
        effect.put(ProfessionEventEffect.UPDATED_AT, now);
        return effect;
    }

    private List<JSONObject> rewardLevels(final JSONObject summary, final MigrationContext context)
            throws RepositoryException {
        final String policy = context.target().scheme().getString(ProfessionLevelScheme.REWARD_MIGRATION_POLICY);
        if ("NO_GRANT".equals(policy)) {
            return List.of();
        }
        final Set<String> oldCodes = "GRANT_NEW_LEVELS".equals(policy)
                ? oldReachedCodes(summary, context) : Set.of();
        final Set<String> selected = selectedRewardCodes(context, policy);
        final List<JSONObject> result = new ArrayList<>();
        for (final Object item : context.targetLevels()) {
            final JSONObject level = (JSONObject) item;
            if (level.getLong("requiredTotalExperience") <= summary.getLong(UserProfession.TOTAL_EXPERIENCE)
                    && !oldCodes.contains(level.getString("levelCode"))) {
                result.add(filteredLevel(level, selected));
            }
        }
        return List.copyOf(result);
    }

    private List<JSONObject> newlyReachedLevels(final JSONObject summary, final MigrationContext context)
            throws RepositoryException {
        final Set<String> oldCodes = oldReachedCodes(summary, context);
        final List<JSONObject> result = new ArrayList<>();
        for (final Object item : context.targetLevels()) {
            final JSONObject level = (JSONObject) item;
            if (level.getLong("requiredTotalExperience") <= summary.getLong(UserProfession.TOTAL_EXPERIENCE)
                    && !oldCodes.contains(level.getString("levelCode"))) {
                result.add(new JSONObject(level.toString()));
            }
        }
        return List.copyOf(result);
    }

    private Set<String> oldReachedCodes(final JSONObject summary, final MigrationContext context)
            throws RepositoryException {
        final String oldSchemeId = summary.getString(UserProfession.LEVEL_SCHEME_ID);
        final JSONArray levels = context.oldLevels().computeIfAbsent(oldSchemeId, key -> loadLevels(key));
        final long highestExperience = Math.max(summary.optLong(UserProfession.HIGHEST_REACHED_EXPERIENCE),
                summary.getLong(UserProfession.TOTAL_EXPERIENCE));
        final Set<String> result = new HashSet<>();
        for (final Object item : levels) {
            final JSONObject level = (JSONObject) item;
            if (level.getLong("requiredTotalExperience") <= highestExperience) {
                result.add(level.getString("levelCode"));
            }
        }
        result.add(summary.getString(UserProfession.CURRENT_LEVEL_CODE));
        return result;
    }

    private JSONArray loadLevels(final String schemeId) {
        try {
            return definitionQueryService.getLevels(schemeId);
        } catch (final RepositoryException e) {
            throw new IllegalStateException("读取旧等级方案失败", e);
        }
    }

    private Set<String> selectedRewardCodes(final MigrationContext context, final String policy) {
        if (!"GRANT_SELECTED".equals(policy)) {
            return Set.of();
        }
        final Set<String> result = new HashSet<>();
        for (final Object item : context.config().getJSONArray("selectedRewardCodes")) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private JSONObject filteredLevel(final JSONObject level, final Set<String> selected) {
        if (selected.isEmpty()) {
            return new JSONObject(level.toString());
        }
        final JSONObject result = new JSONObject(level.toString());
        final JSONArray rewards = new JSONArray();
        for (final Object item : level.getJSONArray("rewards")) {
            final JSONObject reward = (JSONObject) item;
            if (selected.contains(reward.getString("rewardCode"))) {
                rewards.put(new JSONObject(reward.toString()));
            }
        }
        result.put("rewards", rewards);
        return result;
    }

    private void updateSummary(final JSONObject summary, final MigrationContext context,
                               final JSONObject afterLevel) throws RepositoryException {
        final long now = context.target().now();
        final long total = summary.getLong(UserProfession.TOTAL_EXPERIENCE);
        final long highestExperience = Math.max(summary.optLong(UserProfession.HIGHEST_REACHED_EXPERIENCE), total);
        final String schemeId = context.target().scheme().getString(Keys.OBJECT_ID);
        final JSONObject highestLevel = levelResolver.resolve(schemeId, highestExperience);
        if (!afterLevel.getString("levelCode").equals(summary.getString(UserProfession.CURRENT_LEVEL_CODE))) {
            summary.put(UserProfession.CURRENT_LEVEL_REACHED_AT, now);
        }
        summary.put(UserProfession.PROFESSION_REVISION_ID,
                context.target().scheme().getString(ProfessionLevelScheme.PROFESSION_REVISION_ID));
        summary.put(UserProfession.LEVEL_SCHEME_ID, schemeId);
        summary.put(UserProfession.CURRENT_LEVEL_CODE, afterLevel.getString("levelCode"));
        summary.put(UserProfession.HIGHEST_REACHED_EXPERIENCE, highestExperience);
        summary.put(UserProfession.HIGHEST_REACHED_LEVEL_CODE, highestLevel.getString("levelCode"));
        summary.put(UserProfession.DATA_VERSION, summary.getLong(UserProfession.DATA_VERSION) + 1L);
        summary.put(UserProfession.UPDATED_AT, now);
        userProfessionRepository.update(summary.getString(Keys.OBJECT_ID), summary);
    }

    private RepositoryException repositoryException(final Exception exception) {
        return exception instanceof RepositoryException result ? result : new RepositoryException(exception);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    private record MigrationTarget(JSONObject profession, JSONObject scheme, long now) {
    }

    private record MigrationContext(MigrationTarget target, JSONArray targetLevels, JSONObject config,
                                    Map<String, JSONArray> oldLevels) {
    }
}
