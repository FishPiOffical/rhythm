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
import org.testng.Assert;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;

/** 验证实际的原文投影和 DTO。SQLite 用例不覆盖 HTTP 中间件及 MySQL。 */
public class ApiV3ArticleEditorDtoTest {
    public static void main(final String[] args) throws Exception {
        final String raw = "<script>alert('raw')</script>\n正文\\\u001f20\u001f0-0\u001f0-0\u001e";
        final var row = new JSONObject().put("oId", "1").put(Article.ARTICLE_CONTENT, raw)
                .put(Article.ARTICLE_REWARD_CONTENT, "私密打赏正文").put(Article.ARTICLE_COMMENTABLE, 0)
                .put(Article.ARTICLE_ANONYMOUS, Article.ARTICLE_ANONYMOUS_C_ANONYMOUS)
                .put("articleAuthorId", "owner").put("articleIP", "secret-ip").put("articleUA", "secret-ua")
                .put("userPassword", "secret").put("apiKey", "secret");
        final var dto = ApiV3ArticleEditorDto.from(row);
        Assert.assertEquals(dto.keySet(), Set.of("id", "title", "content", "tags", "type", "rewardContent", "rewardPoint",
                "qnaOfferPoint", "commentable", "showInList", "statement", "anonymous", "status", "editorType",
                "createdAt", "updatedAt", "permalink", "columnId", "columnTitle", "columnCoverURL", "chapterNo"));
        Assert.assertEquals(dto.getString("content"), raw);
        Assert.assertEquals(dto.getString("rewardContent"), "私密打赏正文");
        Assert.assertFalse(dto.getBoolean("commentable"));
        Assert.assertTrue(dto.getBoolean("anonymous"));
        Assert.assertEquals(row.getString("articleIP"), "secret-ip");
        System.out.println("PASS editor DTO preserves raw source and owned paid content without author/IP/UA/credential fields");
        Assert.assertTrue(ApiV3ArticleEditorDto.from(new JSONObject().put(Article.ARTICLE_COMMENTABLE, 1)).getBoolean("commentable"));
        Assert.assertTrue(ApiV3ArticleEditorDto.from(new JSONObject().put(Article.ARTICLE_COMMENTABLE, true)).getBoolean("commentable"));
        Assert.assertFalse(ApiV3ArticleEditorDto.from(new JSONObject().put(Article.ARTICLE_COMMENTABLE, false)).getBoolean("commentable"));
        Assert.assertEquals(dto.getString("columnId"), "");
        Assert.assertEquals(dto.getString("chapterNo"), "");
        System.out.println("PASS legacy numeric and boolean comment flags normalize with empty chapter defaults");
        try (final var db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (final var s = db.createStatement()) {
                s.execute("CREATE TABLE article(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleTitle TEXT,articleContent TEXT,articleTags TEXT,articleType INT,articleRewardContent TEXT,articleRewardPoint INT,articleQnAOfferPoint INT,articleCommentable INT,articleShowInList INT,articleStatement INT,articleAnonymous INT,articleStatus INT,articleCreateTime BIGINT,articleUpdateTime BIGINT,articlePermalink TEXT,articleEditorType INT,articleIP TEXT)");
                s.execute("CREATE TABLE chapter(articleId TEXT,columnId TEXT,chapterNo INT)");
                s.execute("CREATE TABLE columns(oId TEXT,columnAuthorId TEXT,columnTitle TEXT,columnCoverURL TEXT,columnStatus INT)");
                s.execute("INSERT INTO article VALUES('1','owner','title','raw Markdown','a,b',3,'paid',10,20,0,1,2,1,2,100,200,'/article/1',0,'secret-ip')");
                s.execute("INSERT INTO chapter VALUES('1','c1',7)");
                s.execute("INSERT INTO columns VALUES('c1','owner','own column','/cover.png',0)");
            }
            final var owned = read(db, "1", "owner");
            Assert.assertNotNull(owned);
            Assert.assertEquals(owned.getString("columnId"), "c1");
            Assert.assertEquals(owned.getString("chapterNo"), "7");
            Assert.assertEquals(owned.getString("columnTitle"), "own column");
            Assert.assertEquals(owned.getInt("status"), Article.ARTICLE_STATUS_C_LOCKED);
            Assert.assertTrue(owned.getBoolean("anonymous"));
            Assert.assertEquals(owned.getString("rewardContent"), "paid");
            Assert.assertNull(read(db, "1", "other"));
            Assert.assertNull(read(db, "absent", "owner"));
            Assert.assertNull(read(db, "1' OR 1=1 --", "owner"));
            Assert.assertNull(read(db, "1", "owner' OR 1=1 --"));
            System.out.println("PASS actual SQL owner+ID binding, locked anonymous source, paid fields and own chapter projection");
            try (final var s = db.createStatement()) { s.execute("UPDATE columns SET columnAuthorId='other'"); }
            hidden(read(db, "1", "owner"));
            try (final var s = db.createStatement()) { s.execute("UPDATE columns SET columnAuthorId='owner',columnStatus=1"); }
            hidden(read(db, "1", "owner"));
            try (final var s = db.createStatement()) { s.execute("DELETE FROM columns"); }
            hidden(read(db, "1", "owner"));
            try (final var s = db.createStatement()) { s.execute("DELETE FROM chapter"); }
            hidden(read(db, "1", "owner"));
            System.out.println("PASS foreign, disabled and absent column metadata cannot leak through owned article source");
        }
        for (final String bad : new String[]{"article;DROP TABLE x", "a.b", "`article`", ""}) {
            try {
                ApiV3ArticleEditorDto.sql(bad, "chapter", "columns");
                Assert.fail();
            } catch (final IllegalArgumentException expected) { }
        }
        Assert.assertTrue(ApiV3ArticleEditorDto.sql("symphony_article", "symphony_long_article_chapter", "symphony_long_article_column").contains("`symphony_article`"));
        System.out.println("PASS internal table identifier validation and configured prefix support");
    }
    private static void hidden(final JSONObject dto) {
        Assert.assertNotNull(dto);
        for (final String field : new String[]{"columnId", "columnTitle", "columnCoverURL", "chapterNo"}) Assert.assertEquals(dto.getString(field), "");
    }
    private static JSONObject read(final Connection db, final String id, final String owner) throws Exception {
        try (final var s = db.prepareStatement(ApiV3ArticleEditorDto.sql("article", "chapter", "columns"))) {
            s.setString(1, id); s.setString(2, owner);
            try (final var result = s.executeQuery()) {
                if (!result.next()) return null;
                final var row = new JSONObject();
                for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) row.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                return ApiV3ArticleEditorDto.from(row);
            }
        }
    }
}
