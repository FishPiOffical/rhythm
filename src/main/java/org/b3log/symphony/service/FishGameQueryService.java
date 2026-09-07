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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.FishGameCommentRepository;
import org.b3log.symphony.repository.FishGameRepository;
import org.b3log.symphony.repository.FishGameVoteRepository;
import org.b3log.symphony.model.FishGameVote;
import org.json.JSONObject;

import org.b3log.latke.ioc.Inject;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** 鱼游查询服务。 */
@org.b3log.latke.service.annotation.Service
public class FishGameQueryService {
    private static final Logger LOGGER = LogManager.getLogger(FishGameQueryService.class);
    private static final int PAGE_SIZE = 100;

    @Inject
    private FishGameRepository fishGameRepository;

    @Inject
    private FishGameCommentRepository fishGameCommentRepository;

    @Inject
    private FishGameVoteRepository fishGameVoteRepository;

    public List<JSONObject> getApproved() {
        try {
            return fishGameRepository.getApproved(1, PAGE_SIZE);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "查询鱼游失败", e);
            throw new IllegalStateException("查询鱼游失败", e);
        }
    }

    public List<JSONObject> getAll() {
        try {
            return fishGameRepository.getAll(1, PAGE_SIZE);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "查询鱼游失败", e);
            throw new IllegalStateException("查询鱼游失败", e);
        }
    }

    public List<JSONObject> getByAuthor(final String authorId) {
        try {
            return fishGameRepository.getByAuthor(authorId, 1, PAGE_SIZE);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "查询用户投稿失败", e);
            throw new IllegalStateException("查询用户投稿失败", e);
        }
    }

    public JSONObject get(final String gameId) {
        try {
            return fishGameRepository.get(gameId);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "查询鱼游失败", e);
            throw new IllegalStateException("查询鱼游失败", e);
        }
    }

    public List<JSONObject> getComments(final String gameId) {
        try {
            return fishGameCommentRepository.getByGame(gameId, 1, PAGE_SIZE);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "查询鱼游评论失败", e);
            throw new IllegalStateException("查询鱼游评论失败", e);
        }
    }

    public Map<String, String> getUserVotes(final String userId) {
        final Map<String, String> votes = new HashMap<>();
        if (userId == null || userId.isEmpty()) {
            return votes;
        }
        try {
            for (final JSONObject vote : fishGameVoteRepository.getByUser(userId, 1, PAGE_SIZE)) {
                votes.put(vote.optString(FishGameVote.GAME_ID), vote.optString(FishGameVote.VALUE));
            }
            return votes;
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "查询鱼游投票状态失败", e);
            throw new IllegalStateException("查询鱼游投票状态失败", e);
        }
    }

    public String getUserVote(final String userId, final String gameId) {
        if (userId == null || userId.isEmpty()) {
            return "";
        }
        try {
            final JSONObject vote = fishGameVoteRepository.getByUserAndGame(userId, gameId);
            return vote == null ? "" : vote.optString(FishGameVote.VALUE);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "查询鱼游投票状态失败", e);
            throw new IllegalStateException("查询鱼游投票状态失败", e);
        }
    }
}
