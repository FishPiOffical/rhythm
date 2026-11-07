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
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Follow;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

/** Actual public profile query fragments on disposable SQLite, no application data. */
public class ApiV3ProfileQueriesTest {
    @Test
    public void followingSourcesAreDistinctDeduplicatedAndOwnerBound() throws Exception {
        try (final Connection db = schema()) {
            user(db, "author", 0); user(db, "other", 0);
            article(db, "1", "author", false); article(db, "2", "author", true); article(db, "3", "other", false);
            article(db, "4", "other", false); article(db, "5", "other", false);
            exec(db, "INSERT INTO f VALUES(?,?,?)", "owner", "author", Follow.FOLLOWING_TYPE_C_USER);
            exec(db, "INSERT INTO f VALUES(?,?,?)", "owner", "tag", Follow.FOLLOWING_TYPE_C_TAG);
            exec(db, "INSERT INTO f VALUES(?,?,?)", "owner", "4", Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH);
            exec(db, "INSERT INTO f VALUES(?,?,?)", "owner", "5", Follow.FOLLOWING_TYPE_C_ARTICLE); // collection is not watch
            exec(db, "INSERT INTO f VALUES(?,?,?)", "someone-else", "other", Follow.FOLLOWING_TYPE_C_USER);
            exec(db, "INSERT INTO t VALUES(?,?)", "tag", 0);
            for (final String id : List.of("1", "3", "3")) exec(db, "INSERT INTO ta VALUES(?,?)", "tag", id);
            Assert.assertEquals(watching(db, "owner", "users"), List.of("1")); // no anonymous identity inference
            Assert.assertEquals(watching(db, "owner", "tags"), List.of("1", "3"));
            Assert.assertEquals(watching(db, "owner", "all"), List.of("1", "3"));
            Assert.assertEquals(watching(db, "owner", "articles"), List.of("4"));
            Assert.assertTrue(watching(db, "owner' OR 1=1 --", "all").isEmpty());
            exec(db, "UPDATE t SET tagStatus=?", 1);
            Assert.assertEquals(watching(db, "owner", "all"), List.of("1"));
            try { ApiV3ProfileQueries.following("f", "ta", "t", "owner", "all UNION SELECT *", 0); Assert.fail(); }
            catch (final IllegalArgumentException expected) { }
        }
    }
    @Test
    public void commentCountAndPagesUseTheSameVisibilityBeforePagination() throws Exception {
        try (final Connection db = schema()) {
            user(db, "author", 0); user(db, "blocked", 1); user(db, "deactivated", 4);
            for (int i = 1; i <= 11; i++) { article(db, String.valueOf(i), "author", false); comment(db, String.valueOf(i), "owner", String.valueOf(i)); }
            exec(db, "UPDATE a SET articleStatus=? WHERE oId='2'", Article.ARTICLE_STATUS_C_INVALID);
            exec(db, "UPDATE a SET articleType=? WHERE oId='3'", Article.ARTICLE_TYPE_C_DISCUSSION);
            exec(db, "UPDATE a SET articleAnonymousView=? WHERE oId='4'", Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW);
            exec(db, "UPDATE a SET articleShowInList=? WHERE oId='5'", Article.ARTICLE_SHOW_IN_LIST_C_NOT);
            exec(db, "UPDATE a SET articleTags=? WHERE oId='6'", "Sandbox");
            exec(db, "UPDATE a SET articleAuthorId='blocked' WHERE oId='7'");
            exec(db, "UPDATE c SET commentAnonymous=? WHERE oId='8'", Comment.COMMENT_ANONYMOUS_C_ANONYMOUS);
            exec(db, "UPDATE c SET commentStatus=? WHERE oId='9'", Comment.COMMENT_STATUS_C_INVALID);
            exec(db, "UPDATE a SET articleAuthorId='deactivated' WHERE oId='10'");
            exec(db, "UPDATE a SET articleTags='B3log-test' WHERE oId='11'");
            // Paragraph comments and private comments stay in the feed; rendering hides private content separately.
            article(db, "12", "author", false); comment(db, "12", "owner", "12");
            exec(db, "UPDATE c SET commentType=?,commentVisible=? WHERE oId='12'", Comment.COMMENT_TYPE_C_PARAGRAPH, Comment.COMMENT_VISIBLE_C_AUTHOR);
            final var anonymous = ApiV3ProfileQueries.comments("c", "a", "u", "owner", true, 1, 4, "Sandbox");
            Assert.assertEquals(ids(db, "SELECT c.oId" + anonymous.sql() + " ORDER BY c.oId", anonymous.args()), List.of("1", "12"));
            Assert.assertEquals(ids(db, "SELECT COUNT(1)" + anonymous.sql(), anonymous.args()), List.of("2"));
            final var logged = ApiV3ProfileQueries.comments("c", "a", "u", "owner", false, 1, 4, "Sandbox");
            Assert.assertEquals(ids(db, "SELECT COUNT(1)" + logged.sql(), logged.args()), List.of("3"));
            final List<Object> args = new ArrayList<>(logged.args()); args.add(1); args.add(1);
            Assert.assertEquals(ids(db, "SELECT c.oId" + logged.sql() + " ORDER BY c.oId LIMIT ?,?", args), List.of("12"));
            final var injected = ApiV3ProfileQueries.comments("c", "a", "u", "owner' OR 1=1 --", false, 1, 4, "Sandbox");
            Assert.assertTrue(ids(db, "SELECT c.oId" + injected.sql(), injected.args()).isEmpty());
            comment(db, "missing", "owner", "missing-article");
            Assert.assertEquals(ids(db, "SELECT COUNT(1)" + logged.sql(), logged.args()), List.of("3"));
        }
    }
    private static Connection schema() throws Exception {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        exec(db, "CREATE TABLE a(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleAnonymous INTEGER,articleStatus INTEGER,articleType INTEGER,articleAnonymousView INTEGER,articleShowInList INTEGER,articleTags TEXT)");
        exec(db, "CREATE TABLE c(oId TEXT PRIMARY KEY,commentAuthorId TEXT,commentOnArticleId TEXT,commentAnonymous INTEGER,commentStatus INTEGER,commentType INTEGER DEFAULT 0,commentVisible INTEGER DEFAULT 0)");
        exec(db, "CREATE TABLE u(oId TEXT PRIMARY KEY,userStatus INTEGER)");
        exec(db, "CREATE TABLE f(followerId TEXT,followingId TEXT,followingType INTEGER)");
        exec(db, "CREATE TABLE t(oId TEXT PRIMARY KEY,tagStatus INTEGER)");
        exec(db, "CREATE TABLE ta(tag_oId TEXT,article_oId TEXT)"); return db;
    }
    private static void user(final Connection db, final String id, final int status) throws Exception { exec(db, "INSERT INTO u VALUES(?,?)", id, status); }
    private static void article(final Connection db, final String id, final String author, final boolean anonymous) throws Exception {
        exec(db, "INSERT INTO a VALUES(?,?,?,?,?,?,?,?)", id, author, anonymous ? Article.ARTICLE_ANONYMOUS_C_ANONYMOUS : Article.ARTICLE_ANONYMOUS_C_PUBLIC,
                Article.ARTICLE_STATUS_C_VALID, Article.ARTICLE_TYPE_C_NORMAL, Article.ARTICLE_ANONYMOUS_VIEW_C_ALLOW, Article.ARTICLE_SHOW_IN_LIST_C_YES, "test");
    }
    private static void comment(final Connection db, final String id, final String owner, final String article) throws Exception {
        exec(db, "INSERT INTO c(oId,commentAuthorId,commentOnArticleId,commentAnonymous,commentStatus) VALUES(?,?,?,?,?)", id, owner, article, Comment.COMMENT_ANONYMOUS_C_PUBLIC, Comment.COMMENT_STATUS_C_VALID);
    }
    private static List<String> watching(final Connection db, final String owner, final String source) throws Exception {
        final var fragment = ApiV3ProfileQueries.following("f", "ta", "t", owner, source, 0);
        return ids(db, "SELECT a.oId FROM a a WHERE 1=1" + fragment.sql() + " ORDER BY a.oId", fragment.args());
    }
    private static void exec(final Connection db, final String sql, final Object... args) throws Exception {
        try (final var statement = db.prepareStatement(sql)) { for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); statement.executeUpdate(); }
    }
    private static List<String> ids(final Connection db, final String sql, final List<Object> args) throws Exception {
        final List<String> values = new ArrayList<>();
        try (final var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) statement.setObject(i + 1, args.get(i));
            try (final var result = statement.executeQuery()) { while (result.next()) values.add(result.getString(1)); }
        }
        return values;
    }
    public static void main(final String[] args) throws Exception {
        final var tests = new ApiV3ProfileQueriesTest();
        tests.followingSourcesAreDistinctDeduplicatedAndOwnerBound(); System.out.println("PASS following sources and owner binding");
        tests.commentCountAndPagesUseTheSameVisibilityBeforePagination(); System.out.println("PASS comment visibility, totals and pagination");
    }
}
