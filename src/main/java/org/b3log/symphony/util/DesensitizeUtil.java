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

import org.b3log.latke.model.User;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.UserExt;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.stream.Collectors;

public class DesensitizeUtil {

    private static final String[] USER_PRIVATE_FIELDS = {
            UserExt.USER_LATEST_LOGIN_IP, User.USER_PASSWORD, "userPhone", UserExt.USER_QQ,
            UserExt.USER_CITY, UserExt.USER_COUNTRY, User.USER_EMAIL, "secret2fa",
            "apiKey", "token", "accessToken", "refreshToken"
    };

    private static final String[] USER_ACTIVITY_FIELDS = {
            UserExt.USER_LONGEST_CHECKIN_STREAK_START, UserExt.USER_CHECKIN_TIME,
            UserExt.USER_CURRENT_CHECKIN_STREAK_END, UserExt.USER_CURRENT_CHECKIN_STREAK,
            UserExt.USER_LATEST_CMT_TIME, UserExt.USER_LONGEST_CHECKIN_STREAK_END,
            UserExt.USER_LATEST_LOGIN_TIME, UserExt.USER_UPDATE_TIME,
            UserExt.USER_SUB_MAIL_SEND_TIME, UserExt.USER_LONGEST_CHECKIN_STREAK,
            UserExt.USER_CURRENT_CHECKIN_STREAK_START
    };

    public static List<JSONObject> articlesDesensitize(final List<JSONObject> articles) {
        return articles.stream().peek(article -> {
            removeArticlePrivateFields(article, true);
            desensitizeUser(article.optJSONObject(Article.ARTICLE_T_AUTHOR));
        }).collect(Collectors.toList());
    }

    public static JSONObject articleDesensitize(final JSONObject article) {
        if (null == article) {
            return null;
        }
        removeArticlePrivateFields(article, false);
        desensitizeUser(article.optJSONObject(Article.ARTICLE_T_AUTHOR));
        desensitizeCommentValue(article.opt(Article.ARTICLE_T_OFFERED_COMMENT));
        desensitizeCommentValue(article.opt(Article.ARTICLE_T_COMMENTS));
        desensitizeCommentValue(article.opt(Article.ARTICLE_T_NICE_COMMENTS));
        return article;
    }

    public static JSONObject commentDesensitize(final JSONObject comment) {
        if (null == comment) {
            return null;
        }
        comment.remove(Comment.COMMENT_IP);
        comment.remove(Comment.COMMENT_UA);
        desensitizeUser(comment.optJSONObject(Comment.COMMENT_T_COMMENTER));
        return comment;
    }

    private static void removeArticlePrivateFields(final JSONObject article, final boolean removeContent) {
        if (null == article) {
            return;
        }
        article.remove(Article.ARTICLE_UA);
        article.remove(Article.ARTICLE_IP);
        if (!removeContent) {
            return;
        }
        article.remove(Article.ARTICLE_T_ORIGINAL_CONTENT);
        article.remove(Article.ARTICLE_CONTENT);
    }

    private static void desensitizeUser(final JSONObject user) {
        if (null == user) {
            return;
        }
        for (final String field : USER_PRIVATE_FIELDS) {
            user.remove(field);
        }
        for (final String field : USER_ACTIVITY_FIELDS) {
            user.remove(field);
        }
    }

    private static void desensitizeCommentValue(final Object value) {
        if (value instanceof JSONObject) {
            commentDesensitize((JSONObject) value);
            return;
        }
        if (value instanceof JSONArray) {
            final JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                desensitizeCommentValue(array.opt(i));
            }
            return;
        }
        if (value instanceof List) {
            ((List<?>) value).forEach(DesensitizeUtil::desensitizeCommentValue);
        }
    }
}
