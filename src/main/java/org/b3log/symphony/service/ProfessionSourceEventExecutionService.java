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
import org.b3log.symphony.model.ProfessionExecutionState;
import org.b3log.symphony.model.ProfessionSourceEvent;
import org.b3log.symphony.repository.ProfessionSourceEventRepository;
import org.json.JSONObject;

import java.util.List;

/** 管理职业来源事件的领取所有权和失败重试。 */
@Service
public class ProfessionSourceEventExecutionService {

    private static final long CLAIM_TIMEOUT_MILLIS = 5 * 60_000L;
    private static final long RETRY_DELAY_MILLIS = 60_000L;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    @Inject
    private ProfessionSourceEventRepository repository;

    public List<JSONObject> getReady(final int limit) throws RepositoryException {
        final long now = System.currentTimeMillis();
        return repository.getReady(now, now - CLAIM_TIMEOUT_MILLIS, limit);
    }

    public Claim claim(final String eventId) {
        final Transaction transaction = repository.beginTransaction();
        try {
            final long now = System.currentTimeMillis();
            final boolean claimed = repository.claim(eventId, now, now - CLAIM_TIMEOUT_MILLIS);
            transaction.commit();
            return claimed ? new Claim(eventId, now) : null;
        } catch (final Exception e) {
            rollback(transaction);
            throw new IllegalStateException("职业来源事件领取失败", e);
        }
    }

    public JSONObject lockClaimed(final Claim claim) throws RepositoryException {
        final JSONObject event = repository.getForUpdate(claim.eventId());
        if (null == event || !"PROCESSING".equals(event.optString(ProfessionSourceEvent.STATUS))
                || claim.claimedAt() != event.optLong(ProfessionSourceEvent.LOCKED_AT)) {
            return null;
        }
        return event;
    }

    public void updateState(final ProfessionExecutionState state) throws RepositoryException {
        if (!repository.updateExecutionState(state)) {
            throw new IllegalStateException("职业来源事件领取已失效");
        }
    }

    public void recordFailure(final Claim claim, final Exception exception) {
        final Transaction transaction = repository.beginTransaction();
        try {
            final ProfessionExecutionState state = failureState(claim, exception);
            if (!repository.recordRetryableFailure(state)) {
                throw new IllegalStateException("职业来源事件领取已失效");
            }
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            throw new IllegalStateException("职业事件失败状态写入失败", e);
        }
    }

    private ProfessionExecutionState failureState(final Claim claim, final Exception exception) {
        final long now = System.currentTimeMillis();
        final String message = String.valueOf(exception.getMessage());
        final String summary = message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH));
        return new ProfessionExecutionState(claim.eventId(), "RETRYABLE_FAILED", now + RETRY_DELAY_MILLIS,
                exception.getClass().getSimpleName(), summary, claim.claimedAt(), now);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    public record Claim(String eventId, long claimedAt) {
    }
}
