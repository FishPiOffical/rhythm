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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 校验等级方案草稿的发布不变量。 */
@Service
public class ProfessionLevelDraftValidator {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");
    private static final Set<String> MIGRATION_POLICIES = Set.of(
            "MIGRATE_ALL", "NEW_USERS_ONLY", "KEEP_EXISTING", "SCHEDULED_SWITCH");
    private static final Set<String> REWARD_POLICIES = Set.of(
            "NO_GRANT", "GRANT_NEW_LEVELS", "GRANT_SELECTED");
    private static final int MAX_MIGRATION_CONFIG_LENGTH = 4_096;
    private static final int MAX_LEVELS = 100;
    private static final Set<String> MIGRATION_CONFIG_KEYS = Set.of("scheduledSwitchAt", "selectedRewardCodes");

    @Inject
    private ProfessionDefinitionRegistry registry;

    public void validate(final ProfessionLevelSchemeDraftRequest request) {
        validateRequest(request);
        ValidationState state = new ValidationState(-1L, new HashSet<>(), new HashSet<>(), 0);
        for (final ProfessionLevelDraft level : request.levels()) {
            state = validateLevel(level, state);
        }
        if (0L != request.levels().getFirst().requiredTotalExperience()) {
            throw new IllegalArgumentException("首级经验门槛必须为零");
        }
        if (1 != state.topLevelCount() || !request.levels().getLast().topLevel()) {
            throw new IllegalArgumentException("最高等级必须且只能标记一次");
        }
        validateSelectedRewardsExist(request);
    }

    private void validateRequest(final ProfessionLevelSchemeDraftRequest request) {
        if (null == request || null == request.levels() || request.levels().isEmpty()) {
            throw new IllegalArgumentException("等级方案不能为空");
        }
        if (request.levels().size() > MAX_LEVELS) {
            throw new IllegalArgumentException("等级数量不能超过" + MAX_LEVELS);
        }
        if (!MIGRATION_POLICIES.contains(request.migrationPolicy())
                || !REWARD_POLICIES.contains(request.rewardMigrationPolicy())) {
            throw new IllegalArgumentException("等级方案迁移策略不合法");
        }
        validateId(request.professionId());
        validateId(request.professionRevisionId());
        validateId(request.operatorUserId());
        validateMigrationConfig(request);
    }

    private void validateMigrationConfig(final ProfessionLevelSchemeDraftRequest request) {
        final String json = request.migrationConfigJson();
        if (null == json || json.isBlank() || json.length() > MAX_MIGRATION_CONFIG_LENGTH) {
            throw new IllegalArgumentException("等级迁移配置长度不合法");
        }
        final JSONObject config = new JSONObject(json);
        for (final String key : config.keySet()) {
            if (!MIGRATION_CONFIG_KEYS.contains(key)) {
                throw new IllegalArgumentException("等级迁移配置包含未注册字段");
            }
        }
        if ("SCHEDULED_SWITCH".equals(request.migrationPolicy())
                && config.optLong("scheduledSwitchAt") <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("等级方案切换时间必须晚于当前时间");
        }
        if (!List.of("MIGRATE_ALL", "SCHEDULED_SWITCH").contains(request.migrationPolicy())
                && !"NO_GRANT".equals(request.rewardMigrationPolicy())) {
            throw new IllegalArgumentException("保留旧方案时不能补发迁移奖励");
        }
        if ("GRANT_SELECTED".equals(request.rewardMigrationPolicy())) {
            validateSelectedRewards(config.optJSONArray("selectedRewardCodes"));
        }
    }

    private void validateSelectedRewardsExist(final ProfessionLevelSchemeDraftRequest request) {
        if (!"GRANT_SELECTED".equals(request.rewardMigrationPolicy())) {
            return;
        }
        final Set<String> defined = new HashSet<>();
        for (final ProfessionLevelDraft level : request.levels()) {
            for (final ProfessionLevelRewardDraft reward : level.rewards()) {
                defined.add(reward.rewardCode());
            }
        }
        final JSONArray selected = new JSONObject(request.migrationConfigJson()).getJSONArray("selectedRewardCodes");
        for (final Object item : selected) {
            if (!defined.contains(String.valueOf(item))) {
                throw new IllegalArgumentException("补发奖励编码不属于当前等级方案");
            }
        }
    }

    private void validateSelectedRewards(final JSONArray rewardCodes) {
        if (null == rewardCodes || rewardCodes.isEmpty()) {
            throw new IllegalArgumentException("选择补发奖励时必须指定奖励编码");
        }
        final Set<String> unique = new HashSet<>();
        for (final Object item : rewardCodes) {
            final String code = String.valueOf(item);
            if (!CODE.matcher(code).matches() || !unique.add(code)) {
                throw new IllegalArgumentException("补发奖励编码不合法或重复");
            }
        }
    }

    private ValidationState validateLevel(final ProfessionLevelDraft level, final ValidationState state) {
        if (null == level || !CODE.matcher(level.levelCode()).matches()
                || level.sortOrder() < 0
                || level.requiredTotalExperience() <= state.threshold()
                || !state.levelCodes().add(level.levelCode()) || !state.sortOrders().add(level.sortOrder())) {
            throw new IllegalArgumentException("等级门槛、编码或排序不合法");
        }
        validateRequiredText(level.displayName(), 64);
        validateOptionalText(level.shortName(), 32);
        validateOptionalText(level.description(), 1_024);
        validateOptionalText(level.achievementDescription(), 1_024);
        validateChildren(level);
        return new ValidationState(level.requiredTotalExperience(), state.levelCodes(), state.sortOrders(),
                state.topLevelCount() + (level.topLevel() ? 1 : 0));
    }

    private void validateChildren(final ProfessionLevelDraft level) {
        if (null == level.presentations() || null == level.rewards()) {
            throw new IllegalArgumentException("等级展示或奖励不能为空");
        }
        final Set<String> positions = new HashSet<>();
        for (final ProfessionLevelPresentationDraft item : level.presentations()) {
            registry.validatePresentation(item);
            if (!positions.add(item.positionCode())) {
                throw new IllegalArgumentException("等级展示位置重复");
            }
        }
        final Set<String> rewards = new HashSet<>();
        for (final ProfessionLevelRewardDraft item : level.rewards()) {
            registry.validateReward(item);
            if (!rewards.add(item.rewardCode())) {
                throw new IllegalArgumentException("等级奖励编码重复");
            }
        }
    }

    private void validateId(final String value) {
        if (null == value || !value.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException("等级方案标识不合法");
        }
    }

    private void validateRequiredText(final String value, final int maxLength) {
        if (!safeText(value, maxLength) || value.isBlank()) {
            throw new IllegalArgumentException("等级文字长度不合法");
        }
    }

    private void validateOptionalText(final String value, final int maxLength) {
        if (!safeText(value, maxLength) || (!value.isEmpty() && value.isBlank())) {
            throw new IllegalArgumentException("等级文字长度不合法");
        }
    }

    private boolean safeText(final String value, final int maxLength) {
        return null != value && value.length() <= maxLength && !value.contains("<") && !value.contains(">");
    }

    private record ValidationState(long threshold, Set<String> levelCodes, Set<Integer> sortOrders,
                                   int topLevelCount) {
    }
}
