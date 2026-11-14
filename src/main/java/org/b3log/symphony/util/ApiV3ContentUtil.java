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
package org.b3log.symphony.util;

import org.b3log.latke.Keys;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.json.JSONObject;

import java.util.Set;

/** Viewer-explicit visibility rules and public DTOs; never uses the page session. */
public final class ApiV3ContentUtil {
    private ApiV3ContentUtil() { }

    public static boolean canViewArticle(final JSONObject article, final JSONObject viewer,
                                         final Set<String> invitedNames) {
        if (null == article || Article.ARTICLE_STATUS_C_INVALID == article.optInt(Article.ARTICLE_STATUS)) return false;
        if (null == viewer && Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW == article.optInt(Article.ARTICLE_ANONYMOUS_VIEW)) return false;
        if (Article.ARTICLE_TYPE_C_DISCUSSION != article.optInt(Article.ARTICLE_TYPE)) return true;
        if (null == viewer) return false;
        final String viewerId = viewer.optString(Keys.OBJECT_ID);
        return (!viewerId.isEmpty() && viewerId.equals(article.optString(Article.ARTICLE_AUTHOR_ID)))
                || Role.ROLE_ID_C_ADMIN.equals(viewer.optString(User.USER_ROLE))
                || (!viewer.optString(User.USER_NAME).isEmpty() && invitedNames.contains(viewer.optString(User.USER_NAME)));
    }

    public static boolean canViewCommentContent(final JSONObject comment, final JSONObject article,
                                                final JSONObject viewer) {
        if (Comment.COMMENT_VISIBLE_C_AUTHOR != comment.optInt(Comment.COMMENT_VISIBLE)) return true;
        if (null == viewer) return false;
        final String id = viewer.optString(Keys.OBJECT_ID);
        return !id.isEmpty() && (id.equals(comment.optString(Comment.COMMENT_AUTHOR_ID))
                || id.equals(article.optString(Article.ARTICLE_AUTHOR_ID)));
    }

    public static boolean canViewUserList(final JSONObject target, final JSONObject viewer, final String field) {
        if (UserExt.USER_XXX_STATUS_C_ENABLED == target.optInt(field)) return true;
        if (null == viewer) return false;
        final String id = viewer.optString(Keys.OBJECT_ID);
        return (!id.isEmpty() && id.equals(target.optString(Keys.OBJECT_ID)))
                || Role.ROLE_ID_C_ADMIN.equals(viewer.optString(User.USER_ROLE));
    }

    public static JSONObject authorDto(final JSONObject author, final boolean anonymous) {
        if (anonymous || null == author) {
            return new JSONObject().put("id", "").put("userName", "匿名用户")
                    .put("userNickname", "匿名用户").put("userAvatarURL", "");
        }
        return new JSONObject().put("id", author.optString(Keys.OBJECT_ID))
                .put("userName", author.optString(User.USER_NAME))
                .put("userNickname", author.optString(UserExt.USER_NICKNAME))
                .put("userAvatarURL", author.optString(UserExt.USER_AVATAR_URL));
    }
}
