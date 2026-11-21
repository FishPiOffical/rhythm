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

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.ioc.Inject;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.annotation.Transactional;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Milestone;
import org.b3log.symphony.repository.MilestoneRepository;
import org.b3log.symphony.cache.MilestoneCache;
import org.b3log.latke.service.ServiceException;
import org.json.JSONObject;
import org.b3log.latke.Keys;
import org.b3log.symphony.util.MilestoneSubmission;

/**
 * Milestone management service.
 *
 * @author rhythm
 * @version 1.0.0.0, Jan 15, 2026
 * @since 3.7.0
 */
@Service
public class MilestoneMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(MilestoneMgmtService.class);

    @Inject
    private MilestoneRepository milestoneRepository;

    @Inject
    private MilestoneCache milestoneCache;

    /**
     * Adds a milestone.
     *
     * @param milestone the specified milestone
     * @throws ServiceException service exception
     */
    public void addMilestone(final JSONObject milestone) throws ServiceException {
        final Transaction tx = milestoneRepository.beginTransaction();
        try {
            addMilestoneInCurrentTransaction(milestone.optString(Milestone.MILESTONE_AUTHOR_ID), milestone);
            tx.commit();
        } catch (final Exception e) {
            if (tx.isActive()) tx.rollback();
            LOGGER.error("Adds milestone failed", e);
            throw new ServiceException(e);
        }
        clearTimelineAfterCommit();
    }

    public JSONObject addMilestoneInCurrentTransaction(final String actor, final JSONObject input) throws RepositoryException {
        if (!milestoneRepository.hasTransactionBegun()) throw new IllegalStateException("milestone transaction required");
        final JSONObject row = MilestoneSubmission.pending(actor, input, System.currentTimeMillis());
        final String id = milestoneRepository.add(row);
        return row.put(Keys.OBJECT_ID, id);
    }

    public void clearTimelineAfterCommit() {
        try { milestoneCache.clearTimelineEventsCache(); }
        catch (final Exception e) { LOGGER.error("Clears committed milestone cache failed", e); }
    }

    /**
     * Updates a milestone.
     *
     * @param milestoneId the specified milestone id
     * @param milestone the specified milestone
     * @throws ServiceException service exception
     */
    @Transactional
    public void updateMilestone(final String milestoneId, final JSONObject milestone) throws ServiceException {

        try {
            milestone.put(Milestone.MILESTONE_UPDATE_TIME, System.currentTimeMillis());
            milestoneRepository.update(milestoneId, milestone);
            milestoneCache.clearTimelineEventsCache();
        } catch (final RepositoryException e) {

            LOGGER.log(Level.ERROR, "Updates milestone failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * Deletes a milestone.
     *
     * @param milestoneId the specified milestone id
     * @throws ServiceException service exception
     */
    @Transactional
    public void removeMilestone(final String milestoneId) throws ServiceException {
        try {
            milestoneRepository.remove(milestoneId);
            milestoneCache.clearTimelineEventsCache();
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Removes milestone failed", e);
            throw new ServiceException(e);
        }
    }

}
