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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.model.UserProfession;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.ProfessionContributionIncrement;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.b3log.symphony.repository.UserProfessionContributionRepository;
import org.json.JSONObject;

import java.util.List;

/** 将职业效果、汇总、等级变化和奖励放在同一事务内。 */
@Service
public class ProfessionEffectApplyService {

    private static final int BATCH_SIZE = 100;
    @Inject private ProfessionEventEffectRepository effectRepository;
    @Inject private ProfessionEffectExecutionService effectExecutionService;
    @Inject private UserProfessionRepository userProfessionRepository;
    @Inject private ProfessionLevelResolver levelResolver;
    @Inject private ProfessionRewardGrantService rewardGrantService;
    @Inject private UserProfessionContributionRepository contributionRepository;
    @Inject private ProfessionPrivacyMgmtService privacyMgmtService;
    @Inject private ProfessionAutomationNotificationService notificationService;

    public void applyPending() {
        try {
            for (final JSONObject effect : effectExecutionService.getPending(BATCH_SIZE)) {
                apply(effect.getString(Keys.OBJECT_ID));
            }
        } catch (final RepositoryException e) {
            throw new IllegalStateException("读取待应用职业效果失败", e);
        }
    }

    public void replay(final String effectId) {
        if (null == effectId || effectId.isBlank() || effectId.length() > 19) {
            throw new IllegalArgumentException("职业效果标识不合法");
        }
        final Transaction transaction = effectRepository.beginTransaction();
        try {
            if (!effectRepository.requeueApply(effectId, System.currentTimeMillis())) {
                throw new IllegalArgumentException("职业效果不存在");
            }
            transaction.commit();
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new IllegalStateException("职业效果重新入队失败", e);
        }
    }

    private void apply(final String effectId) {
        final ProfessionEffectExecutionService.Claim claim = effectExecutionService.claim(effectId);
        if (null == claim) {
            return;
        }
        final Transaction transaction = effectRepository.beginTransaction();
        try {
            final long now = System.currentTimeMillis();
            final JSONObject effect = effectExecutionService.lockClaimed(claim);
            if (null == effect) {
                transaction.commit();
                return;
            }
            final boolean notificationEffect = notificationService.isNotificationEffect(effect);
            final JSONObject summary = summaryForUpdate(effect, now);
            final ApplyContext context = applyContext(effect, summary, now);
            rewardGrantService.grantCrossed(effect, context.crossedLevels(), now);
            updateSummary(context);
            if (notificationEffect) {
                updateEffect(context);
                notificationService.send(effect);
            } else {
                updateContribution(context);
                updateEffect(context);
            }
            privacyMgmtService.touchProgressInCurrentTransaction(
                    effect.getString(ProfessionEventEffect.USER_ID), now);
            transaction.commit();
        } catch (final Exception e) {
            retry(claim, transaction, e);
        }
    }

    private ApplyContext applyContext(final JSONObject effect, final JSONObject summary, final long now)
            throws RepositoryException {
        final String schemeId = summary.getString(UserProfession.LEVEL_SCHEME_ID);
        if (schemeId.isBlank()) {
            throw new IllegalStateException("职业效果缺少等级方案快照");
        }
        final long before = summary.getLong(UserProfession.TOTAL_EXPERIENCE);
        final long delta = resolveDelta(effect);
        final long desired = Math.addExact(before, delta);
        final long after = Math.max(0L, desired);
        final JSONObject beforeLevel = levelResolver.resolve(schemeId, before);
        final JSONObject afterLevel = levelResolver.resolve(schemeId, after);
        final long rewardStart = summary.optString(UserProfession.CURRENT_LEVEL_CODE).isBlank() ? -1L : before;
        final List<JSONObject> crossed = levelResolver.crossedLevels(schemeId, rewardStart, after);
        return new ApplyContext(effect, summary, beforeLevel, afterLevel, crossed, before, after, now);
    }

    private long resolveDelta(final JSONObject effect) throws RepositoryException {
        final String originalId = effect.optString(ProfessionEventEffect.REVERSES_EFFECT_ID);
        if (originalId.isBlank()) {
            return effect.getLong(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA);
        }
        final JSONObject original = effectRepository.get(originalId);
        if (null == original || !"APPLIED".equals(original.optString(ProfessionEventEffect.STATUS))) {
            throw new IllegalStateException("被撤销的职业效果尚未完成");
        }
        return Math.negateExact(original.getLong(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA));
    }

    private JSONObject summaryForUpdate(final JSONObject effect, final long now) throws RepositoryException {
        final String userId = effect.getString(ProfessionEventEffect.USER_ID);
        final String professionId = effect.getString(ProfessionEventEffect.PROFESSION_ID);
        final JSONObject existing = userProfessionRepository.getForUpdate(userId, professionId);
        if (null != existing) {
            return existing;
        }
        final JSONObject result = new JSONObject();
        result.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        result.put(UserProfession.USER_ID, userId);
        result.put(UserProfession.PROFESSION_ID, professionId);
        result.put(UserProfession.PROFESSION_REVISION_ID, effect.getString(ProfessionEventEffect.PROFESSION_REVISION_ID));
        result.put(UserProfession.LEVEL_SCHEME_ID, effect.getString(ProfessionEventEffect.LEVEL_SCHEME_ID));
        result.put(UserProfession.TOTAL_EXPERIENCE, 0L);
        result.put(UserProfession.CURRENT_LEVEL_CODE, "");
        result.put(UserProfession.HIGHEST_REACHED_LEVEL_CODE, "");
        result.put(UserProfession.HIGHEST_REACHED_EXPERIENCE, 0L);
        result.put(UserProfession.CURRENT_LEVEL_REACHED_AT, 0L);
        result.put(UserProfession.HIGHEST_LEVEL_REACHED_AT, 0L);
        result.put(UserProfession.LAST_EFFECT_AT, 0L);
        result.put(UserProfession.DATA_VERSION, 0L);
        result.put(UserProfession.CREATED_AT, now);
        result.put(UserProfession.UPDATED_AT, now);
        userProfessionRepository.add(result);
        return result;
    }

    private void updateSummary(final ApplyContext context) throws RepositoryException {
        final JSONObject summary = context.summary();
        final JSONObject effect = context.effect();
        final String beforeCode = context.beforeLevel().getString("levelCode");
        final String afterCode = context.afterLevel().getString("levelCode");
        summary.put(UserProfession.TOTAL_EXPERIENCE, context.afterExperience());
        summary.put(UserProfession.CURRENT_LEVEL_CODE, afterCode);
        if (!beforeCode.equals(afterCode)) {
            summary.put(UserProfession.CURRENT_LEVEL_REACHED_AT, context.now());
        }
        updateHighest(summary, context.afterLevel(), context.now());
        summary.put(UserProfession.LAST_EFFECT_AT, effect.getLong(ProfessionEventEffect.OCCURRED_AT));
        summary.put(UserProfession.DATA_VERSION, summary.getLong(UserProfession.DATA_VERSION) + 1L);
        summary.put(UserProfession.UPDATED_AT, context.now());
        userProfessionRepository.update(summary.getString(Keys.OBJECT_ID), summary);
    }

    private void updateHighest(final JSONObject summary, final JSONObject afterLevel, final long now)
            throws RepositoryException {
        final long afterExperience = summary.getLong(UserProfession.TOTAL_EXPERIENCE);
        final long highestExperience = summary.optLong(UserProfession.HIGHEST_REACHED_EXPERIENCE);
        if (highestExperience >= afterExperience) {
            return;
        }
        summary.put(UserProfession.HIGHEST_REACHED_EXPERIENCE, afterExperience);
        summary.put(UserProfession.HIGHEST_REACHED_LEVEL_CODE, afterLevel.getString("levelCode"));
        summary.put(UserProfession.HIGHEST_LEVEL_REACHED_AT, now);
    }

    private void updateEffect(final ApplyContext context) throws RepositoryException {
        final JSONObject effect = context.effect();
        effect.put(ProfessionEventEffect.ACTUAL_EXPERIENCE_DELTA,
                context.afterExperience() - context.beforeExperience());
        effect.put(ProfessionEventEffect.PROFESSION_REVISION_ID,
                context.summary().getString(UserProfession.PROFESSION_REVISION_ID));
        effect.put(ProfessionEventEffect.LEVEL_SCHEME_ID,
                context.summary().getString(UserProfession.LEVEL_SCHEME_ID));
        effect.put(ProfessionEventEffect.BEFORE_EXPERIENCE, context.beforeExperience());
        effect.put(ProfessionEventEffect.AFTER_EXPERIENCE, context.afterExperience());
        effect.put(ProfessionEventEffect.BEFORE_LEVEL_CODE, context.beforeLevel().getString("levelCode"));
        effect.put(ProfessionEventEffect.AFTER_LEVEL_CODE, context.afterLevel().getString("levelCode"));
        effect.put(ProfessionEventEffect.STATUS, "APPLIED");
        effect.put(ProfessionEventEffect.NEXT_RETRY_AT, 0L);
        effect.put(ProfessionEventEffect.LOCKED_AT, 0L);
        effect.put(ProfessionEventEffect.ERROR_CODE, "");
        effect.put(ProfessionEventEffect.ERROR_MESSAGE, "");
        effect.put(ProfessionEventEffect.UPDATED_AT, context.now());
        effectRepository.update(effect.getString(Keys.OBJECT_ID), effect);
    }

    private void updateContribution(final ApplyContext context) throws RepositoryException {
        final JSONObject effect = context.effect();
        contributionRepository.increment(new ProfessionContributionIncrement(Ids.genTimeMillisId(),
                effect.getString(ProfessionEventEffect.USER_ID),
                effect.getString(ProfessionEventEffect.PROFESSION_ID),
                effect.getString(ProfessionEventEffect.ACTION_CODE),
                context.afterExperience() - context.beforeExperience(),
                effect.optString(ProfessionEventEffect.REVERSES_EFFECT_ID).isBlank() ? 1L : -1L,
                effect.getLong(ProfessionEventEffect.OCCURRED_AT), context.now()));
    }

    private void retry(final ProfessionEffectExecutionService.Claim claim, final Transaction transaction,
                       final Exception exception) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
        effectExecutionService.recordFailure(claim, exception);
    }

    private record ApplyContext(JSONObject effect, JSONObject summary, JSONObject beforeLevel, JSONObject afterLevel,
                                List<JSONObject> crossedLevels, long beforeExperience, long afterExperience, long now) {
    }
}
