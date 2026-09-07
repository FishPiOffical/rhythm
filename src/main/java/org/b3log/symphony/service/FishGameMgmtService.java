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

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.FishGame;
import org.b3log.symphony.model.FishGameComment;
import org.b3log.symphony.model.FishGameVote;
import org.b3log.symphony.repository.FishGameCommentRepository;
import org.b3log.symphony.repository.FishGameRepository;
import org.b3log.symphony.repository.FishGameVoteRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.b3log.latke.ioc.Inject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import java.util.HashSet;
import java.util.Set;
/** 鱼游投稿与互动管理服务。 */
@Service
public class FishGameMgmtService {
    private static final int MAX_COMMENT_LENGTH = 500;

    @Inject
    private FishGameRepository fishGameRepository;

    @Inject
    private FishGameVoteRepository fishGameVoteRepository;

    @Inject
    private FishGameCommentRepository fishGameCommentRepository;
    @Inject
    private FishGameValidationService fishGameValidationService;
    public JSONObject submit(final String authorId, final JSONObject request) throws ServiceException {
        final JSONObject game = fishGameValidationService.build(request, authorId, FishGame.STATUS_PENDING);
        ensureUrlAvailable(game.optString(FishGame.URL), null);
        game.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        final Transaction tx = fishGameRepository.beginTransaction();
        try {
            fishGameRepository.add(game);
            tx.commit();
            return game;
        } catch (final RepositoryException e) {
            rollback(tx);
            throw new ServiceException(e);
        }
    }
    public void requestEdit(final String authorId, final String gameId, final JSONObject request) throws ServiceException {
        final JSONObject game = getApprovedRequired(gameId);
        if (!authorId.equals(game.optString(FishGame.AUTHOR_ID))) {
            throw new ServiceException("只能编辑自己投稿的鱼游");
        }
        if (game.optInt(FishGame.EDIT_PENDING) == 1) {
            throw new ServiceException("已有待审核的修改申请");
        }
        final JSONObject pending = fishGameValidationService.build(request, authorId, FishGame.STATUS_PENDING);
        ensureUrlAvailable(pending.optString(FishGame.URL), gameId);
        game.put(FishGame.EDIT_PENDING, 1);
        game.put(FishGame.PENDING_NAME, pending.optString(FishGame.NAME));
        game.put(FishGame.PENDING_DESCRIPTION, pending.optString(FishGame.DESCRIPTION));
        game.put(FishGame.PENDING_URL, pending.optString(FishGame.URL));
        game.put(FishGame.PENDING_ICON_URL, pending.optString(FishGame.ICON_URL));
        game.put(FishGame.UPDATED_TIME, System.currentTimeMillis());
        updateWithTransaction(game);
    }
    public JSONObject review(final String gameId, final int status, final JSONObject request) throws ServiceException {
        final JSONObject game = getRequired(gameId);
        final int currentStatus = game.optInt(FishGame.STATUS);
        if (status != FishGame.STATUS_APPROVED && status != FishGame.STATUS_REJECTED && status != FishGame.STATUS_DISABLED) {
            throw new ServiceException("审核状态不合法");
        }
        final boolean hasPending = game.optInt(FishGame.EDIT_PENDING) == 1;
        if (hasPending && status == FishGame.STATUS_APPROVED) {
            ensureUrlAvailable(game.optString(FishGame.PENDING_URL), gameId);
            game.put(FishGame.NAME, game.optString(FishGame.PENDING_NAME));
            game.put(FishGame.DESCRIPTION, game.optString(FishGame.PENDING_DESCRIPTION));
            game.put(FishGame.URL, game.optString(FishGame.PENDING_URL));
            game.put(FishGame.ICON_URL, game.optString(FishGame.PENDING_ICON_URL));
            clearPending(game);
        }
        if (hasPending && status != FishGame.STATUS_APPROVED) {
            clearPending(game);
        }
        if (!hasPending && request != null && request.has(FishGame.NAME)) {
            final JSONObject manual = fishGameValidationService.build(request, game.optString(FishGame.AUTHOR_ID), status);
            ensureUrlAvailable(manual.optString(FishGame.URL), gameId);
            game.put(FishGame.NAME, manual.optString(FishGame.NAME));
            game.put(FishGame.DESCRIPTION, manual.optString(FishGame.DESCRIPTION));
            game.put(FishGame.URL, manual.optString(FishGame.URL));
            game.put(FishGame.ICON_URL, manual.optString(FishGame.ICON_URL));
        }
        final int finalStatus = hasPending && status == FishGame.STATUS_REJECTED
                && currentStatus == FishGame.STATUS_APPROVED ? currentStatus : status;
        game.put(FishGame.STATUS, finalStatus);
        game.put(FishGame.UPDATED_TIME, System.currentTimeMillis());
        updateWithTransaction(game);
        return game;
    }
    public JSONObject updateByAdmin(final String gameId, final JSONObject request) throws ServiceException {
        final JSONObject game = getRequired(gameId);
        final JSONObject normalized = fishGameValidationService.build(request, game.optString(FishGame.AUTHOR_ID), game.optInt(FishGame.STATUS));
        ensureUrlAvailable(normalized.optString(FishGame.URL), gameId);
        game.put(FishGame.NAME, normalized.optString(FishGame.NAME));
        game.put(FishGame.DESCRIPTION, normalized.optString(FishGame.DESCRIPTION));
        game.put(FishGame.URL, normalized.optString(FishGame.URL));
        game.put(FishGame.ICON_URL, normalized.optString(FishGame.ICON_URL));
        game.put(FishGame.UPDATED_TIME, System.currentTimeMillis());
        updateWithTransaction(game);
        return game;
    }
    public JSONObject addManual(final String adminId, final JSONObject request) throws ServiceException {
        final JSONObject game = fishGameValidationService.build(request, adminId, FishGame.STATUS_APPROVED);
        ensureUrlAvailable(game.optString(FishGame.URL), null);
        game.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        final Transaction tx = fishGameRepository.beginTransaction();
        try {
            fishGameRepository.add(game);
            tx.commit();
            return game;
        } catch (final RepositoryException e) {
            rollback(tx);
            throw new ServiceException(e);
        }
    }
    public JSONObject importGames(final String adminId, final JSONArray games) throws ServiceException {
        if (games.length() > 200) {
            throw new ServiceException("单次最多导入 200 个鱼游");
        }
        final Transaction tx = fishGameRepository.beginTransaction();
        int imported = 0;
        int skipped = 0;
        final Set<String> urls = new HashSet<>();
        try {
            for (int i = 0; i < games.length(); i++) {
                final JSONObject game = games.optJSONObject(i);
                if (game == null) {
                    throw new ServiceException("导入数据格式不合法");
                }
                final JSONObject normalized = fishGameValidationService.build(game, adminId, FishGame.STATUS_APPROVED);
                final String url = normalized.optString(FishGame.URL);
                if (!urls.add(url) || fishGameRepository.getByUrl(url) != null) {
                    skipped++;
                    continue;
                }
                normalized.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
                fishGameRepository.add(normalized);
                imported++;
            }
            tx.commit();
            return new JSONObject().put("imported", imported).put("skipped", skipped);
        } catch (final ServiceException e) {
            rollback(tx);
            throw e;
        } catch (final RepositoryException e) {
            rollback(tx);
            throw new ServiceException(e);
        }
    }
    public JSONObject vote(final String userId, final String gameId, final String value) throws ServiceException {
        if (!FishGameVote.VALUE_LIKE.equals(value) && !FishGameVote.VALUE_DISLIKE.equals(value)) {
            throw new ServiceException("投票值不合法");
        }
        final Transaction tx = fishGameRepository.beginTransaction();
        try {
            final JSONObject game = fishGameRepository.getForUpdate(gameId);
            if (game == null) {
                throw new ServiceException("鱼游不存在");
            }
            if (game.optInt(FishGame.STATUS) != FishGame.STATUS_APPROVED) {
                throw new ServiceException("鱼游暂未公开");
            }
            final JSONObject old = fishGameVoteRepository.getByUserAndGame(userId, gameId);
            String userVote = value;
            if (old == null) {
                final JSONObject vote = new JSONObject().put(Keys.OBJECT_ID, Ids.genTimeMillisId())
                        .put(FishGameVote.GAME_ID, gameId).put(FishGameVote.USER_ID, userId)
                        .put(FishGameVote.VALUE, value).put(FishGameVote.CREATED_TIME, System.currentTimeMillis());
                fishGameVoteRepository.add(vote);
            } else if (value.equals(old.optString(FishGameVote.VALUE))) {
                fishGameVoteRepository.remove(old.optString(Keys.OBJECT_ID));
                userVote = "";
            } else {
                old.put(FishGameVote.VALUE, value);
                fishGameVoteRepository.update(old.optString(Keys.OBJECT_ID), old);
            }
            final int likes = count(gameId, FishGameVote.VALUE_LIKE);
            final int dislikes = count(gameId, FishGameVote.VALUE_DISLIKE);
            game.put(FishGame.LIKE_COUNT, likes);
            game.put(FishGame.DISLIKE_COUNT, dislikes);
            game.put(FishGame.UPDATED_TIME, System.currentTimeMillis());
            fishGameRepository.update(gameId, game);
            tx.commit();
            return new JSONObject().put(FishGame.LIKE_COUNT, likes).put(FishGame.DISLIKE_COUNT, dislikes)
                    .put("fishGameUserVote", userVote);
        } catch (final ServiceException e) {
            rollback(tx);
            throw e;
        } catch (final RepositoryException e) {
            rollback(tx);
            throw new ServiceException(e);
        }
    }
    public JSONObject addComment(final String userId, final String gameId, final String content) throws ServiceException {
        getApprovedRequired(gameId);
        final String normalized = Jsoup.clean(StringUtils.trimToEmpty(content), Whitelist.none()).trim();
        if (normalized.isEmpty() || normalized.length() > MAX_COMMENT_LENGTH) {
            throw new ServiceException("评论不能为空且不能超过 " + MAX_COMMENT_LENGTH + " 个字符");
        }
        final JSONObject comment = new JSONObject().put(Keys.OBJECT_ID, Ids.genTimeMillisId())
                .put(FishGameComment.GAME_ID, gameId).put(FishGameComment.AUTHOR_ID, userId)
                .put(FishGameComment.CONTENT, normalized).put(FishGameComment.CREATED_TIME, System.currentTimeMillis());
        final Transaction tx = fishGameCommentRepository.beginTransaction();
        try {
            fishGameCommentRepository.add(comment);
            tx.commit();
            return comment;
        } catch (final RepositoryException e) {
            rollback(tx);
            throw new ServiceException(e);
        }
    }
    private JSONObject getRequired(final String gameId) throws ServiceException {
        try {
            final JSONObject game = fishGameRepository.get(gameId);
            if (game == null) {
                throw new ServiceException("鱼游不存在");
            }
            return game;
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    private JSONObject getApprovedRequired(final String gameId) throws ServiceException {
        final JSONObject game = getRequired(gameId);
        if (game.optInt(FishGame.STATUS) != FishGame.STATUS_APPROVED) {
            throw new ServiceException("鱼游暂未公开");
        }
        return game;
    }

    private void ensureUrlAvailable(final String url, final String excludedGameId) throws ServiceException {
        try {
            final JSONObject existing = fishGameRepository.getByUrl(url);
            if (existing != null && !existing.optString(Keys.OBJECT_ID).equals(excludedGameId)) {
                throw new ServiceException("目标网址已存在");
            }
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    private void updateWithTransaction(final JSONObject game) throws ServiceException {
        final Transaction tx = fishGameRepository.beginTransaction();
        try {
            fishGameRepository.update(game.optString(Keys.OBJECT_ID), game);
            tx.commit();
        } catch (final RepositoryException e) {
            rollback(tx);
            throw new ServiceException(e);
        }
    }

    private int count(final String gameId, final String value) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(FishGameVote.GAME_ID, FilterOperator.EQUAL, gameId),
                new PropertyFilter(FishGameVote.VALUE, FilterOperator.EQUAL, value)));
        return (int) fishGameVoteRepository.count(query);
    }

    private void clearPending(final JSONObject game) {
        game.put(FishGame.EDIT_PENDING, 0).put(FishGame.PENDING_NAME, "").put(FishGame.PENDING_DESCRIPTION, "")
                .put(FishGame.PENDING_URL, "").put(FishGame.PENDING_ICON_URL, "");
    }

    private void rollback(final Transaction tx) {
        if (tx != null && tx.isActive()) {
            tx.rollback();
        }
    }
}
