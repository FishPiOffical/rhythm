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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.Keys;
import org.b3log.symphony.repository.UserRepository;
import java.util.List;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.cache.TagCache;
import org.b3log.symphony.cache.ArticleCache;
import org.b3log.symphony.util.FollowMutation;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Follow;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.FollowRepository;
import org.b3log.symphony.repository.TagRepository;
import org.json.JSONObject;

/**
 * Follow management service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.3.1.3, Jun 6, 2019
 * @since 0.2.5
 */
@Service
public class FollowMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(FollowMgmtService.class);

    /**
     * Follow repository.
     */
    @Inject
    private FollowRepository followRepository;

    /**
     * Tag repository.
     */
    @Inject
    private TagRepository tagRepository;

    /**
     * Article repository.
     */
    @Inject
    private ArticleRepository articleRepository;

    @Inject
    private UserRepository userRepository;
    @Inject private TagCache tagCache;
    @Inject private ArticleCache articleCache;

    /**
     * The specified follower follows the specified following tag.
     *
     * @param followerId     the specified follower id
     * @param followingTagId the specified following tag id
     */
    public void followTag(final String followerId, final String followingTagId) {
        try {
            follow(followerId, followingTagId, Follow.FOLLOWING_TYPE_C_TAG);
        } catch (final RepositoryException e) {
            final String msg = "User[id=" + followerId + "] follows a tag[id=" + followingTagId + "] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * The specified follower follows the specified following user.
     *
     * @param followerId      the specified follower id
     * @param followingUserId the specified following user id
     */
    public void followUser(final String followerId, final String followingUserId) {
        try {
            follow(followerId, followingUserId, Follow.FOLLOWING_TYPE_C_USER);
        } catch (final RepositoryException e) {
            final String msg = "User[id=" + followerId + "] follows a user[id=" + followingUserId + "] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * The specified follower follows the specified following article.
     *
     * @param followerId         the specified follower id
     * @param followingArticleId the specified following article id
     */
    public void followArticle(final String followerId, final String followingArticleId) {
        try {
            follow(followerId, followingArticleId, Follow.FOLLOWING_TYPE_C_ARTICLE);
        } catch (final RepositoryException e) {
            final String msg = "User[id=" + followerId + "] follows an article[id=" + followingArticleId + "] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * The specified follower watches the specified following article.
     *
     * @param followerId         the specified follower id
     * @param followingArticleId the specified following article id
     */
    public void watchArticle(final String followerId, final String followingArticleId) {
        try {
            follow(followerId, followingArticleId, Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH);
        } catch (final RepositoryException e) {
            final String msg = "User[id=" + followerId + "] watches an article[id=" + followingArticleId + "] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * The specified follower unfollows the specified following tag.
     *
     * @param followerId     the specified follower id
     * @param followingTagId the specified following tag id
     */
    public void unfollowTag(final String followerId, final String followingTagId) {
        try {
            unfollow(followerId, followingTagId, Follow.FOLLOWING_TYPE_C_TAG);
        } catch (final RepositoryException e) {
            final String msg = "User[id=" + followerId + "] unfollows a tag[id=" + followingTagId + "] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * The specified follower unfollows the specified following user.
     *
     * @param followerId      the specified follower id
     * @param followingUserId the specified following user id
     */
    public void unfollowUser(final String followerId, final String followingUserId) {
        try {
            unfollow(followerId, followingUserId, Follow.FOLLOWING_TYPE_C_USER);
        } catch (final RepositoryException e) {
            final String msg = "User[id=" + followerId + "] unfollows a user[id=" + followingUserId + "] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * The specified follower unfollows the specified following article.
     *
     * @param followerId         the specified follower id
     * @param followingArticleId the specified following article id
     */
    public void unfollowArticle(final String followerId, final String followingArticleId) {
        try {
            unfollow(followerId, followingArticleId, Follow.FOLLOWING_TYPE_C_ARTICLE);
        } catch (final RepositoryException e) {
            final String msg = "User[id=" + followerId + "] unfollows an article[id=" + followingArticleId + "] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }


    /**
     * The specified follower unwatches the specified following article.
     *
     * @param followerId         the specified follower id
     * @param followingArticleId the specified following article id
     */
    public void unwatchArticle(final String followerId, final String followingArticleId) {
        try {
            unfollow(followerId, followingArticleId, Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH);
        } catch (final RepositoryException e) {
            final String msg = "User[id=" + followerId + "] unwatches an article[id=" + followingArticleId + "] failed";
            LOGGER.log(Level.ERROR, msg, e);
        }
    }

    /**
     * The specified follower follows the specified following entity with the specified following type.
     *
     * @param followerId    the specified follower id
     * @param followingId   the specified following entity id
     * @param followingType the specified following type
     * @throws RepositoryException repository exception
     */
    private void follow(final String followerId, final String followingId, final int followingType) throws RepositoryException {
        legacySet(followerId, followingId, followingType, true);
    }

    public void unfollow(final String followerId, final String followingId, final int followingType) throws RepositoryException {
        legacySet(followerId, followingId, followingType, false);
    }

    private void legacySet(final String actor, final String target, final int type, final boolean enabled) throws RepositoryException {
        if (followRepository.hasTransactionBegun()) throw new IllegalStateException("legacy follow cannot nest transactions");
        final Transaction transaction = followRepository.beginTransaction();
        try {
            setInCurrentTransaction(actor, target, type, enabled, null);
            transaction.commit();
        } catch (final RepositoryException | RuntimeException e) {
            if (transaction.isActive()) transaction.rollback();
            throw e;
        } finally { invalidateCaches(target, type); }
    }

    @FunctionalInterface
    public interface TargetCheck { void check(JSONObject target) throws RepositoryException; }

    /** Locks the actor across legacy and v3 writes; never begins or commits a transaction. */
    public boolean setInCurrentTransaction(final String followerId, final String followingId, final int type,
                                           final boolean enabled, final TargetCheck check) throws RepositoryException {
        return FollowMutation.set(new FollowMutation.Store() {
            public boolean hasTransaction() { return followRepository.hasTransactionBegun(); }
            public boolean lockActor(final String actor) throws RepositoryException {
                return null != followRepository.lockEntity(userRepository.getName(), actor);
            }
            public JSONObject lockTarget(final String target, final int kind) throws RepositoryException {
                final String table = kind == Follow.FOLLOWING_TYPE_C_USER ? userRepository.getName()
                        : kind == Follow.FOLLOWING_TYPE_C_TAG ? tagRepository.getName() : articleRepository.getName();
                return followRepository.lockEntity(table, target);
            }
            public List<JSONObject> relations(final String actor, final String target, final int kind) throws RepositoryException {
                return followRepository.getRelationsForUpdate(actor, target, kind);
            }
            public void add(final JSONObject relation) throws RepositoryException { followRepository.add(relation); }
            public void remove(final String id) throws RepositoryException { followRepository.remove(id); }
            public void adjust(final JSONObject target, final String id, final int kind, final int delta) throws RepositoryException {
                final String field = kind == Follow.FOLLOWING_TYPE_C_TAG ? Tag.TAG_FOLLOWER_CNT
                        : kind == Follow.FOLLOWING_TYPE_C_ARTICLE ? Article.ARTICLE_COLLECT_CNT : Article.ARTICLE_WATCH_CNT;
                target.put(field, Math.max(0, target.optLong(field) + delta));
                if (kind == Follow.FOLLOWING_TYPE_C_TAG) {
                    target.put(Tag.TAG_RANDOM_DOUBLE, Math.random());
                    tagRepository.update(id, target, field, Tag.TAG_RANDOM_DOUBLE);
                } else articleRepository.update(id, target, field);
            }
        }, followerId, followingId, type, enabled, null == check ? null : check::check);
    }
    public void invalidateCaches(final String target, final int type) {
        if (type == Follow.FOLLOWING_TYPE_C_TAG) tagCache.removeTag(target);
        else if (type == Follow.FOLLOWING_TYPE_C_ARTICLE || type == Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH) articleCache.removeArticle(target);
    }
}
