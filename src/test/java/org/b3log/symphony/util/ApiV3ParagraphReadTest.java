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
import org.b3log.symphony.model.Comment;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 在隔离环境中执行服务实际使用的查询片段。 */
public class ApiV3ParagraphReadTest {
    private static final String A = "a".repeat(64), B = "b".repeat(64);
    public static void main(final String[] args) throws Exception {
        try (final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (final var statement = db.createStatement()) {
                statement.execute("CREATE TABLE comment(oId TEXT PRIMARY KEY,commentOnArticleId TEXT,commentAuthorId TEXT,"
                        + "commentOriginalCommentId TEXT,commentContent TEXT,commentCreateTime BIGINT,commentAnonymous INT,"
                        + "commentVisible INT,commentStatus INT,commentType INT,commentParagraphId TEXT,commentParagraphKind TEXT,"
                        + "commentParagraphIndex INT,commentParagraphSnapshot TEXT,commentParagraphStatus INT,commentScore DOUBLE,commentIP TEXT,commentUA TEXT)");
            }
            for (int i = 1; i <= 130; i++) insert(db, String.format("%03d", i), "10", A, 1, 0, 0, i % 5);
            for (int i = 131; i <= 133; i++) insert(db, String.valueOf(i), "10", A, 1, 0, 1, 0);
            for (int i = 134; i <= 136; i++) insert(db, String.valueOf(i), "10", B, 1, 0, 0, 0);
            insert(db, "137", "11", A, 1, 0, 0, 0);
            insert(db, "138", "10", A, 0, 0, 0, 0);
            insert(db, "139", "10", A, 1, 1, 0, 0);
            insert(db, "140", "10", A, 1, 0, 99, 0);

            final var selection = ApiV3ParagraphQueries.selection("comment", "10", A, "active");
            Assert.assertEquals(count(db, selection), 130L);
            final Set<String> ids = new HashSet<>();
            for (int p = 1; p <= 4; p++) {
                final var rows = rows(db, ApiV3ParagraphQueries.page(selection, p, 50, "oldest"));
                Assert.assertEquals(rows.size(), p < 3 ? 50 : p == 3 ? 30 : 0);
                for (final var row : rows) {
                    Assert.assertTrue(ids.add(row.getString(Keys.OBJECT_ID)));
                    Assert.assertFalse(row.has(Comment.COMMENT_IP)); Assert.assertFalse(row.has(Comment.COMMENT_UA));
                    Assert.assertFalse(row.has(Comment.COMMENT_PARAGRAPH_SNAPSHOT));
                    Assert.assertEquals(row.getString(Comment.COMMENT_ON_ARTICLE_ID), "10");
                    Assert.assertEquals(row.getInt(Comment.COMMENT_TYPE), 1);
                }
            }
            Assert.assertEquals(ids.size(), 130);
            System.out.println("PASS actual paragraph SQL paginates beyond 100 with identical count/row predicates and no IP/UA projection");

            Assert.assertEquals(count(db, ApiV3ParagraphQueries.selection("comment", "10", A, "orphaned")), 3L);
            Assert.assertEquals(count(db, ApiV3ParagraphQueries.selection("comment", "10", A, "all")), 133L);
            Assert.assertEquals(count(db, ApiV3ParagraphQueries.selection("comment", "10", "", "all")), 136L);
            Assert.assertEquals(count(db, ApiV3ParagraphQueries.selection("comment", "11", A, "all")), 1L);
            Assert.assertEquals(count(db, ApiV3ParagraphQueries.selection("comment", "10' OR 1=1 --", A, "all")), 0L);
            System.out.println("PASS article/anchor/state isolation excludes ordinary, invalid and unknown-state rows; values stay bound");

            final var latest = rows(db, ApiV3ParagraphQueries.page(selection, 1, 2, "latest"));
            Assert.assertEquals(latest.getFirst().getString(Keys.OBJECT_ID), "130");
            final var hot = rows(db, ApiV3ParagraphQueries.page(selection, 1, 2, "hot"));
            Assert.assertEquals(hot.getFirst().getString(Keys.OBJECT_ID), "129");
            Assert.assertEquals(hot.getLast().getString(Keys.OBJECT_ID), "124");
            System.out.println("PASS latest and hot orders use deterministic ID tie breaks");

            final var counts = rows(db, ApiV3ParagraphQueries.counts("comment", "10", List.of(A, B)));
            Assert.assertEquals(counts.size(), 2);
            for (final var row : counts) Assert.assertEquals(row.getLong("commentCount"), A.equals(row.getString(Comment.COMMENT_PARAGRAPH_ID)) ? 130L : 3L);
            reject(() -> ApiV3ParagraphQueries.counts("comment", "10", List.of()));
            reject(() -> ApiV3ParagraphQueries.counts("comment", "10", java.util.Collections.nCopies(51, A)));
            System.out.println("PASS grouped counts bind only page anchors and exclude removed/orphaned/other-article rows");

            final var aliases = ApiV3ParagraphQueries.selection("comment", "10", List.of(A, B), "active");
            Assert.assertEquals(count(db, aliases), 133L);
            Assert.assertEquals(rows(db, ApiV3ParagraphQueries.page(aliases, 3, 50, "oldest")).size(), 33);
            Assert.assertEquals(rows(db, ApiV3ParagraphQueries.aliasCounts("comment", "10", List.of(A, B))).size(), 2);
            reject(() -> ApiV3ParagraphQueries.selection("comment", "10", List.of(A, B, "c".repeat(64)), "active"));
            reject(() -> ApiV3ParagraphQueries.aliasCounts("comment", "10", java.util.Collections.nCopies(101, A)));
            reject(() -> ApiV3ParagraphQueries.selection("comment", "10", List.of(A, "' OR 1=1"), "active"));
            System.out.println("PASS legacy/provisional anchor alternatives share count and pagination, with bounded parameterized aliases");

            reject(() -> ApiV3ParagraphQueries.selection("comment", "10", "' OR 1=1", "all"));
            reject(() -> ApiV3ParagraphQueries.selection("comment", "10", A, "active OR 1=1"));
            reject(() -> ApiV3ParagraphQueries.page(selection, 0, 20, "oldest"));
            reject(() -> ApiV3ParagraphQueries.page(selection, 1, 51, "oldest"));
            reject(() -> ApiV3ParagraphQueries.page(selection, Integer.MAX_VALUE / 50 + 1, 50, "oldest"));
            reject(() -> ApiV3ParagraphQueries.page(selection, 1, 20, "oId;DROP TABLE comment"));
            final var edge = ApiV3ParagraphQueries.page(selection, Integer.MAX_VALUE / 50, 50, "oldest");
            Assert.assertEquals(edge.args().get(edge.args().size() - 2), (long) (Integer.MAX_VALUE / 50 - 1) * 50);
            Assert.assertTrue(rows(db, edge).isEmpty());
            System.out.println("PASS bounded pagination/anchor/state/sort reject injection and maximum-page offset does not overflow");
        }

        final var source = new JSONObject().put(Keys.OBJECT_ID, "20").put(Comment.COMMENT_ON_ARTICLE_ID, "10")
                .put(Comment.COMMENT_TYPE, 1).put(Comment.COMMENT_PARAGRAPH_ID, A).put(Comment.COMMENT_PARAGRAPH_KIND, "paragraph")
                .put(Comment.COMMENT_PARAGRAPH_INDEX, 2).put(Comment.COMMENT_PARAGRAPH_SNAPSHOT, "SECRET-restricted-title-snapshot")
                .put(Comment.COMMENT_PARAGRAPH_STATUS, 1).put(Comment.COMMENT_AUTHOR_ID, "SECRET-real-author")
                .put(Comment.COMMENT_CONTENT, "SECRET-private-body").put(Comment.COMMENT_IP, "SECRET-ip")
                .put(Comment.COMMENT_UA, "SECRET-ua").put(Comment.COMMENT_SHARP_URL, "javascript:SECRET-url");
        final var safe = new JSONObject().put("id", "20").put("content", "").put("contentHtml", "").put("contentVisible", false)
                .put("author", ApiV3ContentUtil.authorDto(new JSONObject(), true));
        final var dto = ApiV3ParagraphDto.comment(safe, source);
        Assert.assertFalse(dto.toString().contains("SECRET-"));
        Assert.assertFalse(dto.getBoolean("contentVisible"));
        Assert.assertEquals(dto.getJSONObject("paragraph").keySet(), Set.of("id", "kind", "index", "state"));
        Assert.assertEquals(dto.getJSONObject("paragraph").getString("state"), "orphaned");
        Assert.assertEquals(dto.getString("permalink"), "/article/10?paragraph=" + A + "#20");
        Assert.assertFalse(safe.has("paragraph"));
        final var ordinary = ApiV3ParagraphDto.comment(safe, new JSONObject(source.toString()).put(Comment.COMMENT_TYPE, 0));
        Assert.assertTrue(ordinary.isNull("paragraph")); Assert.assertEquals(ordinary.getString("permalink"), "/article/10#20");
        System.out.println("PASS metadata enrichment preserves hidden/anonymous DTO, ignores internal body/identity/URL and never mutates base");

        final var visibility = new ApiV3ContentUtilTest();
        for (final var method : ApiV3ContentUtilTest.class.getDeclaredMethods()) if (method.isAnnotationPresent(Test.class)) {
            method.invoke(visibility); System.out.println("PASS shared read visibility: " + method.getName());
        }
    }
    private static long count(final Connection db, final ApiV3ParagraphQueries.Selection selection) throws Exception {
        return rows(db, new ApiV3ParagraphQueries.Selection("SELECT COUNT(1) AS total" + selection.sql(), selection.args())).getFirst().getLong("total");
    }
    private static List<JSONObject> rows(final Connection db, final ApiV3ParagraphQueries.Selection query) throws Exception {
        try (final var statement = db.prepareStatement(query.sql())) {
            for (int i = 0; i < query.args().size(); i++) statement.setObject(i + 1, query.args().get(i));
            final var rows = new ArrayList<JSONObject>();
            try (final var result = statement.executeQuery()) {
                while (result.next()) {
                    final var row = new JSONObject();
                    for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) row.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                    rows.add(row);
                }
            }
            return rows;
        }
    }
    private static void insert(final Connection db, final String id, final String article, final String anchor,
                               final int type, final int status, final int paragraphStatus, final int score) throws Exception {
        try (final var statement = db.prepareStatement("INSERT INTO comment VALUES(?,?,?,'001','body',0,0,1,?,?,?,'paragraph',0,'snapshot',?,?,'SECRET-ip','SECRET-ua')")) {
            statement.setString(1, id); statement.setString(2, article); statement.setString(3, "actor"); statement.setInt(4, status);
            statement.setInt(5, type); statement.setString(6, anchor); statement.setInt(7, paragraphStatus); statement.setInt(8, score); statement.executeUpdate();
        }
    }
    private static void reject(final Runnable action) {
        try { action.run(); Assert.fail("Expected invalid input"); }
        catch (final ApiV3Exception expected) { Assert.assertEquals(expected.status, 422); }
    }
}
