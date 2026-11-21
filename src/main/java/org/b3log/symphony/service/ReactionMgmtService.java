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
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.repository.ApiV3ArticleRevisionRepository;
import org.b3log.symphony.repository.ApiV3ChatEventRepository;
import org.b3log.symphony.util.ApiV3ChatTransaction;
import org.b3log.symphony.repository.ReactionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.util.ReactionMutation;
import java.util.List;
import org.json.JSONObject;

/**
 * Reaction management service.
 */
@Service
public class ReactionMgmtService {

    private static final Logger LOGGER = LogManager.getLogger(ReactionMgmtService.class);

    @Inject
    private ReactionRepository reactionRepository;
    @Inject private UserRepository userRepository;
    @Inject private ArticleRepository articleRepository;
    @Inject private CommentRepository commentRepository;
    @Inject private ChatRoomRepository chatRoomRepository;
    @Inject private ApiV3ArticleRevisionRepository realtimeRevisions;
    @Inject private ApiV3ChatEventRepository chatEvents;

    public void toggleReaction(final String userId, final String targetType,
                               final String targetId, final String groupType,
                               final String value) throws ServiceException {
        validateArgs(userId, targetType, targetId, groupType, value);

        final Transaction transaction = reactionRepository.beginTransaction();
        try {
            changeInCurrentTransaction(userId, targetType, targetId, groupType, value, true, null);
            transaction.commit();
        } catch (final RepositoryException | RuntimeException e) {
            rollback(transaction);
            LOGGER.log(Level.ERROR, "Toggle reaction failed", e);
            throw new ServiceException(e);
        }
    }

    public boolean setInCurrentTransaction(final String actor, final String type, final String id, final String value,
                                           final ReactionMutation.TargetCheck check) throws RepositoryException {
        return changeInCurrentTransaction(actor, type, id, Reaction.GROUP_EMOJI, value, false, check);
    }

    private boolean changeInCurrentTransaction(final String actor, final String type, final String id, final String group,
                                               final String value, final boolean toggle, final ReactionMutation.TargetCheck check) throws RepositoryException {
        if(Reaction.TARGET_TYPE_CHAT.equals(type))return ApiV3ChatTransaction.write(()->{
            final boolean changed=changeRowsInCurrentTransaction(actor,type,id,group,value,toggle,check);
            if(changed)chatEvents.appendInCurrentTransaction(id,"reactions");
            return changed;
        });
        return changeRowsInCurrentTransaction(actor,type,id,group,value,toggle,check);
    }
    private boolean changeRowsInCurrentTransaction(final String actor, final String type, final String id, final String group,
                                               final String value, final boolean toggle, final ReactionMutation.TargetCheck check) throws RepositoryException {
        final String[] articleId = {""};
        final boolean changed = ReactionMutation.change(new ReactionMutation.Store() {
            public boolean active() { return reactionRepository.hasTransactionBegun(); }
            public boolean lockActor(final String userId) throws RepositoryException { return null != reactionRepository.lockEntity(userRepository.getName(), userId); }
            public JSONObject lockTarget(final String targetType, final String targetId) throws RepositoryException {
                final String table = Reaction.TARGET_TYPE_ARTICLE.equals(targetType) ? articleRepository.getName()
                        : Reaction.TARGET_TYPE_COMMENT.equals(targetType) ? commentRepository.getName() : chatRoomRepository.getName();
                final JSONObject target = reactionRepository.lockEntity(table, targetId);
                if (target != null) articleId[0] = Reaction.TARGET_TYPE_ARTICLE.equals(targetType) ? targetId
                        : Reaction.TARGET_TYPE_COMMENT.equals(targetType) ? target.optString(Comment.COMMENT_ON_ARTICLE_ID) : "";
                return target;
            }
            public List<JSONObject> existing(final String userId, final String targetType, final String targetId, final String groupType) throws RepositoryException {
                return reactionRepository.getOwnedForUpdate(userId, targetType, targetId, groupType);
            }
            public void add(final JSONObject row) throws RepositoryException { reactionRepository.add(row); }
            public void update(final String rowId, final JSONObject row) throws RepositoryException { reactionRepository.update(rowId, row); }
            public void remove(final String rowId) throws RepositoryException { reactionRepository.remove(rowId); }
        }, actor, type, id, group, value, toggle, check);
        if (changed && !articleId[0].isEmpty()) realtimeRevisions.touchInCurrentTransaction(articleId[0]);
        return changed;
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
