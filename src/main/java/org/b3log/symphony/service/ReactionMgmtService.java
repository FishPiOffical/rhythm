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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.repository.ReactionRepository;
import org.json.JSONObject;

/**
 * Reaction management service.
 */
@Service
public class ReactionMgmtService {

    private static final Logger LOGGER = LogManager.getLogger(ReactionMgmtService.class);

    @Inject
    private ReactionRepository reactionRepository;

    public void toggleReaction(final String userId, final String targetType,
                               final String targetId, final String groupType,
                               final String value) throws ServiceException {
        validateArgs(userId, targetType, targetId, groupType, value);

        final Transaction transaction = reactionRepository.beginTransaction();
        try {
            final JSONObject existing = reactionRepository.getByUserTargetGroup(userId, targetType, targetId, groupType);
            applyChange(existing, userId, targetType, targetId, groupType, value);
            transaction.commit();
        } catch (final RepositoryException e) {
            rollback(transaction);
            LOGGER.log(Level.ERROR, "Toggle reaction failed", e);
            throw new ServiceException(e);
        }
    }

    private void applyChange(final JSONObject existing, final String userId,
                             final String targetType, final String targetId,
                             final String groupType, final String value) throws RepositoryException {
        if (existing == null) {
            addReaction(userId, targetType, targetId, groupType, value);
            return;
        }
        if (value.equals(existing.optString(Reaction.VALUE))) {
            reactionRepository.remove(existing.optString(Keys.OBJECT_ID));
            return;
        }
        updateReaction(existing, value);
    }

    private void addReaction(final String userId, final String targetType,
                             final String targetId, final String groupType,
                             final String value) throws RepositoryException {
        final long now = System.currentTimeMillis();
        final JSONObject reaction = new JSONObject();
        reaction.put(Reaction.USER_ID, userId);
        reaction.put(Reaction.TARGET_TYPE, targetType);
        reaction.put(Reaction.TARGET_ID, targetId);
        reaction.put(Reaction.GROUP, groupType);
        reaction.put(Reaction.VALUE, value);
        reaction.put(Reaction.CREATED_TIME, now);
        reaction.put(Reaction.UPDATED_TIME, now);
        reactionRepository.add(reaction);
    }

    private void updateReaction(final JSONObject reaction, final String value) throws RepositoryException {
        reaction.put(Reaction.VALUE, value);
        reaction.put(Reaction.UPDATED_TIME, System.currentTimeMillis());
        reactionRepository.update(reaction.optString(Keys.OBJECT_ID), reaction);
    }

    private void validateArgs(final String userId, final String targetType,
                              final String targetId, final String groupType,
                              final String value) throws ServiceException {
        if (userId == null || userId.isEmpty()) {
            throw new ServiceException("用户未登录");
        }
        if (!Reaction.isValidTargetType(targetType)) {
            throw new ServiceException("暂不支持该 reaction 目标");
        }
        if (!Reaction.isValidGroup(groupType)) {
            throw new ServiceException("暂不支持该 reaction 分组");
        }
        if (targetId == null || targetId.isEmpty()) {
            throw new ServiceException("目标不存在");
        }
        if (!Reaction.isValidEmojiValue(value)) {
            throw new ServiceException("暂不支持该 emoji");
        }
    }

    private void rollback(final Transaction transaction) {
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();
        }
    }
}
