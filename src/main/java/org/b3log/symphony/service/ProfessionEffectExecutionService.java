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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ProfessionEffectFailureState;
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.json.JSONObject;

import java.util.List;

/** 管理职业效果的领取所有权和失败重试。 */
@Service
public class ProfessionEffectExecutionService {

    private static final long CLAIM_TIMEOUT_MILLIS = 5 * 60_000L;
    private static final long RETRY_DELAY_MILLIS = 60_000L;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    @Inject
    private ProfessionEventEffectRepository repository;

    public List<JSONObject> getPending(final int limit) throws RepositoryException {
        final long now = System.currentTimeMillis();
        return repository.getPending(now, now - CLAIM_TIMEOUT_MILLIS, limit);
    }

    public Claim claim(final String effectId) {
        final Transaction transaction = repository.beginTransaction();
        try {
            final long now = System.currentTimeMillis();
            final boolean claimed = repository.claimForApply(effectId, now, now - CLAIM_TIMEOUT_MILLIS);
            transaction.commit();
            return claimed ? new Claim(effectId, now) : null;
        } catch (final Exception e) {
            rollback(transaction);
            throw new IllegalStateException("职业效果领取失败", e);
        }
    }

    public JSONObject lockClaimed(final Claim claim) throws RepositoryException {
        final JSONObject effect = repository.getForUpdate(claim.effectId());
        if (null == effect || !"APPLYING".equals(effect.optString(ProfessionEventEffect.STATUS))
                || claim.claimedAt() != effect.optLong(ProfessionEventEffect.LOCKED_AT)) {
            return null;
        }
        return effect;
    }

    public void recordFailure(final Claim claim, final Exception exception) {
        final Transaction transaction = repository.beginTransaction();
        try {
            final ProfessionEffectFailureState state = failureState(claim, exception);
            if (!repository.recordApplyFailure(state)) {
                throw new IllegalStateException("职业效果领取已失效");
            }
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            throw new IllegalStateException("职业效果失败状态写入失败", e);
        }
    }

    private ProfessionEffectFailureState failureState(final Claim claim, final Exception exception) {
        final long now = System.currentTimeMillis();
        final String message = String.valueOf(exception.getMessage());
        final String summary = message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH));
        return new ProfessionEffectFailureState(claim.effectId(), now + RETRY_DELAY_MILLIS,
                exception.getClass().getSimpleName(), summary, claim.claimedAt(), now);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    public record Claim(String effectId, long claimedAt) {
    }
}
