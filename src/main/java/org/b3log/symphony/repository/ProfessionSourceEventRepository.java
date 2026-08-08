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
package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ProfessionExecutionState;
import org.b3log.symphony.model.ProfessionSourceEvent;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 职业来源事件存储。 */
@Repository
public class ProfessionSourceEventRepository extends AbstractRepository {

    public ProfessionSourceEventRepository() {
        super(ProfessionSourceEvent.PROFESSION_SOURCE_EVENT);
    }

    public List<JSONObject> getReady(final long now, final long staleBefore, final int limit)
            throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE (`status`='PENDING' OR "
                + "(`status` IN ('RETRYABLE_FAILED','WAITING_ORIGINAL') AND `nextRetryAt`<=?) OR "
                + "(`status`='PROCESSING' AND `lockedAt`<=?)) "
                + "ORDER BY `capturedAt`,`oId` LIMIT ?";
        return select(sql, now, staleBefore, limit);
    }

    public JSONObject getByEventKey(final String eventKey) throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `eventKey`=?";
        final List<JSONObject> result = select(sql, eventKey);
        return result.isEmpty() ? null : result.getFirst();
    }

    public List<JSONObject> getPositiveByTarget(final String targetType, final String targetId)
            throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `targetType`=? AND `targetId`=? "
                + "AND `reversalOfEventKey`=''";
        return select(sql, targetType, targetId);
    }

    public boolean addIfAbsent(final JSONObject event) throws RepositoryException {
        try {
            add(event);
            return true;
        } catch (final RepositoryException duplicate) {
            if (null != getByEventKey(event.getString(ProfessionSourceEvent.EVENT_KEY))) {
                return false;
            }
            throw duplicate;
        }
    }

    public JSONObject getByExternalRequest(final String sourceAppId, final String requestId) throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `sourceAppId`=? AND `externalRequestId`=?";
        final List<JSONObject> result = select(sql, sourceAppId, requestId);
        return result.isEmpty() ? null : result.getFirst();
    }

    public List<JSONObject> getByIds(final Collection<String> eventIds) throws RepositoryException {
        final List<String> ids = eventIds.stream().filter(id -> null != id && !id.isBlank()).toList();
        if (ids.isEmpty()) return List.of();
        final List<String> placeholders = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) placeholders.add("?");
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `oId` IN (" + String.join(",", placeholders) + ")";
        return select(sql, ids.toArray());
    }

    public int countRecentExternalRequestsForUpdate(final String sourceAppId, final long since) throws RepositoryException {
        final String sql = "SELECT `oId` FROM `" + getName() + "` WHERE `sourceAppId`=? AND `capturedAt`>=? FOR UPDATE";
        return select(sql, sourceAppId, since).size();
    }

    public boolean claim(final String eventId, final long now, final long staleBefore) throws RepositoryException {
        final String sql = "UPDATE `" + getName() + "` SET `status`='PROCESSING',`lockedAt`=?,"
                + "`processingAttempt`=`processingAttempt`+1,`updatedAt`=? WHERE `oId`=? AND "
                + "(`status`='PENDING' OR (`status` IN ('RETRYABLE_FAILED','WAITING_ORIGINAL') "
                + "AND `nextRetryAt`<=?) OR (`status`='PROCESSING' AND `lockedAt`<=?))";
        return AtomicRepositorySupport.executeUpdate(sql, now, now, eventId, now, staleBefore) == 1;
    }

    public JSONObject getForUpdate(final String eventId) throws RepositoryException {
        final List<JSONObject> result = select("SELECT * FROM `" + getName() + "` WHERE `oId`=? FOR UPDATE", eventId);
        return result.isEmpty() ? null : result.getFirst();
    }

    public boolean updateExecutionState(final ProfessionExecutionState state) throws RepositoryException {
        final String sql = "UPDATE `" + getName() + "` SET `status`=?,`nextRetryAt`=?,`errorCode`=?,"
                + "`errorMessage`=?,`lockedAt`=0,`updatedAt`=? WHERE `oId`=? AND `status`='PROCESSING' "
                + "AND `lockedAt`=?";
        return AtomicRepositorySupport.executeUpdate(sql, state.status(), state.nextRetryAt(), state.errorCode(),
                state.errorMessage(), state.updatedAt(), state.id(), state.claimedAt()) == 1;
    }

    public boolean recordRetryableFailure(final ProfessionExecutionState state) throws RepositoryException {
        final String sql = "UPDATE `" + getName() + "` SET `status`=?,`nextRetryAt`=?,`errorCode`=?,"
                + "`errorMessage`=?,`lockedAt`=0,`updatedAt`=? WHERE `oId`=? AND `status`='PROCESSING' "
                + "AND `lockedAt`=?";
        return AtomicRepositorySupport.executeUpdate(sql, state.status(), state.nextRetryAt(), state.errorCode(),
                state.errorMessage(), state.updatedAt(), state.id(), state.claimedAt()) == 1;
    }

    public boolean requeue(final String eventId, final long now) throws RepositoryException {
        final String sql = "UPDATE `" + getName() + "` SET `status`='PENDING',`nextRetryAt`=0,`lockedAt`=0,"
                + "`errorCode`='',`errorMessage`='',`updatedAt`=? WHERE `oId`=?";
        return AtomicRepositorySupport.executeUpdate(sql, now, eventId) == 1;
    }
}
