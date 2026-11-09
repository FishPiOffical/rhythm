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

/** 执行实际策略归一化和写入校验，HTTP 与幂等存储另行验证。 */
public final class ApiV3ArticlePolicyTest {
    public static void main(final String[] args) {
        for (Object input : new Object[]{true, 1, 1L}) {
            var article = new JSONObject().put(Article.ARTICLE_COMMENTABLE, input);
            Assert.assertEquals(ApiV3ArticlePolicy.commentable(article), Boolean.TRUE);
            ApiV3ArticlePolicy.requireCommenting(article);
        }
        for (Object input : new Object[]{false, 0, 0L, 2, -1, 0.5, "true", "1", JSONObject.NULL}) {
            var article = new JSONObject().put(Article.ARTICLE_COMMENTABLE, input);
            Assert.assertNotEquals(ApiV3ArticlePolicy.commentable(article), Boolean.TRUE);
            try {
                ApiV3ArticlePolicy.requireCommenting(article);
                Assert.fail("must reject");
            } catch (ApiV3Exception expected) {
            }
        }
        System.out.println("PASS article policy 1 exact Boolean/JDBC 0/1 only; closed and unknown mutation guards reject");
        for (int n = 0; n <= 3; n++)
            Assert.assertEquals(ApiV3ArticlePolicy.statement(new JSONObject().put(Article.ARTICLE_STATEMENT, n)), Integer.valueOf(n));
        for (Object value : new Object[]{-1, 4, 1.5, "1", true, JSONObject.NULL})
            Assert.assertNull(ApiV3ArticlePolicy.statement(new JSONObject().put(Article.ARTICLE_STATEMENT, value)));
        var unknown = ApiV3ArticlePolicy.metadata(new JSONObject());
        Assert.assertTrue(unknown.isNull("commentable") && unknown.isNull("statement"));
        Assert.assertEquals(unknown.length(), 2);
        // 原始数据库别名存在时按原值判断，即使框架布尔列被映射为 true。
        for (Object raw : new Object[]{"x", "2", "true", "false", " 1", JSONObject.NULL}) {
            var row = new JSONObject().put(ApiV3ArticlePolicy.RAW_COMMENTABLE, raw).put(Article.ARTICLE_COMMENTABLE, true);
            Assert.assertNull(ApiV3ArticlePolicy.commentable(row));
        }
        Assert.assertEquals(ApiV3ArticlePolicy.commentable(new JSONObject().put(ApiV3ArticlePolicy.RAW_COMMENTABLE, "1")), Boolean.TRUE);
        Assert.assertEquals(ApiV3ArticlePolicy.commentable(new JSONObject().put(ApiV3ArticlePolicy.RAW_COMMENTABLE, "0")), Boolean.FALSE);
        System.out.println("PASS article policy 2 exact statement enum and null unknown metadata without extra fields");
    }
}
