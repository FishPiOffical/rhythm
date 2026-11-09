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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.repository.ReactionRepository;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONObject;

/**
 * HTTP 请求和论坛实时事件均按具体查看者使用同一可见性校验。
 */
@Service
public class ApiV3ReactionTargetService {
    @Inject
    private ArticleRepository articles;
    @Inject
    private ChatRoomRepository chat;
    @Inject
    private ReactionRepository reactions;
    @Inject
    private ApiV3ContentQueryService content;

    public JSONObject read(final JSONObject viewer, final String type, final String id) throws RepositoryException {
        final JSONObject row = switch (type) {
            case Reaction.TARGET_TYPE_ARTICLE -> content.viewableArticle(id, viewer);
            case Reaction.TARGET_TYPE_COMMENT -> content.currentComment(id);
            case Reaction.TARGET_TYPE_CHAT -> chat.get(id);
            default -> throw ApiV3Requests.invalid();
        };
        return validate(viewer, type, row, false);
    }

    /**
     * 写入时，表态目标行已由共享写入服务锁定。
     */
    public JSONObject validate(final JSONObject viewer, final String type, final JSONObject row, final boolean locked) throws RepositoryException {
        if (null == row) throw ApiV3Requests.notFound();
        if (Reaction.TARGET_TYPE_CHAT.equals(type)) {
            if (null == viewer) throw ApiV3Requests.notFound();
            return row;
        }
        final JSONObject article;
        if (Reaction.TARGET_TYPE_COMMENT.equals(type)) {
            if (Comment.COMMENT_STATUS_C_VALID != row.optInt(Comment.COMMENT_STATUS) || blocked(content.currentAuthor(row.optString(Comment.COMMENT_AUTHOR_ID))))
                throw ApiV3Requests.notFound();
            final String articleId = row.optString(Comment.COMMENT_ON_ARTICLE_ID);
            if (locked) reactions.lockEntity(articles.getName(), articleId);
            article = content.viewableArticle(articleId, viewer);
        } else article = content.viewableArticle(row.optString(Keys.OBJECT_ID), viewer);
        if (null == article) throw ApiV3Requests.notFound();
        if (Reaction.TARGET_TYPE_COMMENT.equals(type) && !ApiV3ContentUtil.canViewCommentContent(row, article, viewer))
            throw ApiV3Requests.notFound();
        return article;
    }

    private static boolean blocked(final JSONObject user) {
        return null == user || UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS) || UserExt.USER_STATUS_C_DEACTIVATED == user.optInt(UserExt.USER_STATUS);
    }
}
