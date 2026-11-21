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

import org.b3log.symphony.model.Comment;

/** 普通评论线程只遍历同文章、同类型的有效评论，正文继续走 V3 投影。 */
public final class ApiV3CommentThreads {
    public static final int PAGE_SIZE = ApiV3CommentLocationQueries.PAGE_SIZE;
    public static final int PREVIEW_SIZE = 3;

    private ApiV3CommentThreads() { }

    private static String table(final String name) {
        if (!name.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("table");
        return "`" + name + "`";
    }

    private static String ordinary(final String alias) {
        return alias + ".commentType=" + Comment.COMMENT_TYPE_C_ARTICLE
                + " AND " + alias + ".commentStatus=" + Comment.COMMENT_STATUS_C_VALID;
    }

    private static String parent(final String comments) {
        return " LEFT JOIN " + table(comments) + " parent ON parent.oId=c.commentOriginalCommentId"
                + " AND parent.commentOnArticleId=c.commentOnArticleId AND " + ordinary("parent");
    }

    private static String roots(final String comments) {
        return " FROM " + table(comments) + " c" + parent(comments)
                + " WHERE c.commentOnArticleId=? AND " + ordinary("c") + " AND parent.oId IS NULL";
    }

    public static String rootCount(final String comments) {
        return "SELECT COUNT(*) AS total" + roots(comments);
    }

    // 每行只带遍历所需的索引字段，最后才读取有界窗口的正文。
    private static String descendants(final String comments) {
        return ", members AS (SELECT oId AS id,oId AS rootId,articleId FROM roots UNION ALL"
                + " SELECT c.oId,m.rootId,m.articleId FROM members m JOIN " + table(comments)
                + " c ON c.commentOriginalCommentId=m.id AND c.commentOnArticleId=m.articleId"
                + " AND " + ordinary("c") + ")";
    }

    private static String projection(final String comments, final String users) {
        return " SELECT c.*," + ApiV3ContentSnapshots.authorColumns() + ",r.rootId AS threadRoot,r.total AS threadTotal"
                + " FROM ranked r JOIN " + table(comments) + " c ON c.oId=r.id LEFT JOIN "
                + table(users) + " u ON u.oId=c.commentAuthorId";
    }

    private static String ranked() {
        return ", ranked AS (SELECT id,rootId,COUNT(*) OVER(PARTITION BY rootId)-1 AS total,"
                + " ROW_NUMBER() OVER(PARTITION BY rootId ORDER BY CASE WHEN id=rootId THEN 0 ELSE 1 END,id)-1 AS slot FROM members)";
    }

    public static String rootPage(final String comments, final String users) {
        return "WITH RECURSIVE roots AS (SELECT c.oId,c.commentOnArticleId AS articleId" + roots(comments)
                + " ORDER BY c.oId LIMIT ?,?)" + descendants(comments) + ranked()
                + projection(comments, users) + " WHERE r.slot<=" + PREVIEW_SIZE + " ORDER BY r.rootId,r.slot";
    }

    public static String replyPage(final String comments, final String users) {
        return "WITH RECURSIVE roots AS (SELECT c.oId,c.commentOnArticleId AS articleId" + roots(comments)
                + " AND c.oId=?)" + descendants(comments) + ranked()
                + projection(comments, users) + " WHERE r.slot=0 OR (r.slot>? AND r.slot<=?) ORDER BY r.slot";
    }

    public static String position(final String comments) {
        final String source = table(comments);
        return "WITH RECURSIVE ancestors AS (SELECT c.oId,c.commentOnArticleId AS articleId,c.commentOriginalCommentId AS parentId"
                + " FROM " + source + " c WHERE c.commentOnArticleId=? AND c.oId=? AND " + ordinary("c")
                + " UNION DISTINCT SELECT p.oId,a.articleId,p.commentOriginalCommentId FROM ancestors a JOIN "
                + source + " p ON p.oId=a.parentId AND p.commentOnArticleId=a.articleId AND " + ordinary("p") + ")"
                + ", roots AS (SELECT a.oId,a.articleId FROM ancestors a WHERE NOT EXISTS"
                + " (SELECT 1 FROM " + source + " p WHERE p.oId=a.parentId AND p.commentOnArticleId=a.articleId AND " + ordinary("p") + "))"
                + descendants(comments)
                + " SELECT root.oId AS rootId,(SELECT COUNT(*)" + roots(comments) + " AND c.oId<=root.oId) AS rootPosition,"
                + " (SELECT COUNT(*) FROM members WHERE id<>root.oId AND id<=?) AS replyPosition"
                + " FROM roots root";
    }

    public static long offset(final int page) {
        return (long) (page - 1) * PAGE_SIZE;
    }
}
