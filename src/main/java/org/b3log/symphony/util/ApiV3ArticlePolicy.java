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

import org.b3log.symphony.model.Article;
import org.json.JSONObject;

/**
 * 读取当前文章策略，遇到无法识别的历史值时维持未知状态。
 */
public final class ApiV3ArticlePolicy {
    public static final String RAW_COMMENTABLE = "v3ContentCommentable";

    private ApiV3ArticlePolicy() {
    }

    /**
     * 用查询别名绕开 Latke 的布尔转换，保留原始值和共享 Repository 的字段映射。
     */
    public static String commentableColumn(final String prefix) {
        if (!prefix.matches("[A-Za-z0-9_]*"))
            throw new IllegalArgumentException("internal alias");
        return (prefix.isEmpty() ? "" : prefix + ".") + Article.ARTICLE_COMMENTABLE + " AS " + RAW_COMMENTABLE;
    }

    public static String lockSql(final String table) {
        if (!table.matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("internal table");
        return "SELECT oId," + commentableColumn("") + " FROM `" + table + "` WHERE oId=? FOR UPDATE";
    }

    public static Boolean commentable(final JSONObject article) {
        final boolean raw = article.has(RAW_COMMENTABLE);
        final Object value = article.opt(raw ? RAW_COMMENTABLE : Article.ARTICLE_COMMENTABLE);
        if (raw && value instanceof String text) {
            if ("0".equals(text))
                return false;
            if ("1".equals(text))
                return true;
            return null;
        }
        if (value instanceof Boolean flag)
            return flag;
        if (value instanceof Number number && number.doubleValue() == number.longValue()) {
            if (number.longValue() == 0)
                return false;
            if (number.longValue() == 1)
                return true;
        }
        return null;
    }

    public static Integer statement(final JSONObject article) {
        final Object value = article.opt(Article.ARTICLE_STATEMENT);
        if (value instanceof Number number && number.doubleValue() == number.longValue()
                && number.longValue() >= 0 && number.longValue() <= 3)
            return number.intValue();
        return null;
    }

    public static JSONObject metadata(final JSONObject article) {
        final Boolean commentable = commentable(article);
        final Integer statement = statement(article);
        return new JSONObject().put("commentable", commentable == null ? JSONObject.NULL : commentable)
                .put("statement", statement == null ? JSONObject.NULL : statement);
    }

    public static void requireCommenting(final JSONObject article) {
        if (!Boolean.TRUE.equals(commentable(article))) {
            throw new ApiV3Exception(403, "comments_closed", "文章当前不可发布或编辑评论");
        }
    }
}
