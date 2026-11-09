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
import org.b3log.symphony.model.UserExt;
import org.json.JSONObject;

/** 从当前查询快照同时读取授权字段和正文，避免由实体缓存恢复可见性或内容。 */
public final class ApiV3ContentSnapshots {
    private static final String STATUS = "v3ContentAuthorStatus";
    private static final String NAME = "v3ContentAuthorName";
    private static final String NICKNAME = "v3ContentAuthorNickname";
    private static final String AVATAR = "v3ContentAuthorAvatar";
    private static final String QNA_ACCEPTED = "v3ContentQnaAccepted";
    private ApiV3ContentSnapshots() { }

    private static String table(final String value) {
        if (value == null || !value.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("table");
        return "`" + value + "`";
    }
    public static String authorColumns() {
        return "u.userStatus AS " + STATUS + ",u.userName AS " + NAME
                + ",u.userNickname AS " + NICKNAME + ",u.userAvatarURL AS " + AVATAR;
    }
    public static String article(final String articles, final String users, final String comments) {
        return "SELECT a.oId,a.articleAuthorId,a.articleStatus,a.articleType,a.articleAnonymousView,"
                + "a.articleContent,a.articleTitle,a.articleAnonymous,a.articleRewardPoint,a.articleRewardContent,"
                + "a.articleQnAOfferPoint,a.articlePerfect,a.articleStatement,a.articleTags,a.articlePermalink,"
                + "a.articleCreateTime,a.articleUpdateTime,a.articleCommentCount,a.articleViewCount,a.articleStick,"
                + "a.articleShowInList," + ApiV3ArticlePolicy.commentableColumn("a") + "," + authorColumns()
                + ",CASE WHEN a.articleType=5 THEN EXISTS(SELECT 1 FROM " + table(comments)
                + " ac WHERE ac.commentOnArticleId=a.oId AND ac.commentQnAOffered=1) ELSE 0 END AS " + QNA_ACCEPTED
                + " FROM " + table(articles) + " a LEFT JOIN " + table(users)
                + " u ON u.oId=a.articleAuthorId WHERE a.oId=? AND u.userStatus NOT IN (?,?) LIMIT 2";
    }
    public static boolean questionAccepted(final JSONObject row) {
        return row.getInt(QNA_ACCEPTED) != 0;
    }
    public static String comment(final String comments, final String users) {
        return "SELECT c.*," + authorColumns() + " FROM " + table(comments) + " c LEFT JOIN " + table(users)
                + " u ON u.oId=c.commentAuthorId WHERE c.oId=? LIMIT 2";
    }
    public static String commentPage(final String comments, final String users, final String sort) {
        if (!java.util.Set.of("oldest", "latest", "hot").contains(sort)) throw new IllegalArgumentException("sort");
        final String order = "hot".equals(sort) ? "c.commentScore DESC,c.oId DESC" : "c.oId " + ("oldest".equals(sort) ? "ASC" : "DESC");
        return "SELECT c.*," + authorColumns() + " FROM " + table(comments) + " c LEFT JOIN " + table(users)
                + " u ON u.oId=c.commentAuthorId WHERE c.commentOnArticleId=? AND c.commentType=? AND c.commentStatus=? ORDER BY " + order + " LIMIT ?,?";
    }

    public static String author(final String users) {
        return "SELECT oId,userStatus,userName,userNickname,userAvatarURL FROM " + table(users) + " WHERE oId=? LIMIT 2";
    }
    public static String viewer(final String users) {
        return "SELECT oId,userStatus,userName,userRole FROM " + table(users) + " WHERE oId=? LIMIT 2";
    }
    public static boolean hasAuthor(final JSONObject row) {
        return row.has(STATUS);
    }
    public static JSONObject author(final JSONObject row, final String authorField) {
        if (row == null || row.isNull(STATUS)) return null;
        return new JSONObject().put(Keys.OBJECT_ID, row.optString(authorField))
                .put(UserExt.USER_STATUS, row.getInt(STATUS)).put("userName", row.optString(NAME))
                .put(UserExt.USER_NICKNAME, row.optString(NICKNAME)).put(UserExt.USER_AVATAR_URL, row.optString(AVATAR));
    }
}
