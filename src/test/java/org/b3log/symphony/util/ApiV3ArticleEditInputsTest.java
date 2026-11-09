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
import org.b3log.symphony.model.LongArticleColumn;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.List;

public class ApiV3ArticleEditInputsTest {
    public static void main(final String[] args) {
        final var current = current();
        final String before = current.toString();
        final var patch = ApiV3ArticleEditInputs.normalize(new JSONObject().put("title", " new title "));
        Assert.assertEquals(patch.length(), 1);
        final String hashInput = ApiV3Json.canonical(patch);
        final var chapter = new JSONObject().put(LongArticleColumn.COLUMN_ID, "10").put(LongArticleColumn.CHAPTER_NO, 2);
        final var merged = ApiV3ArticleEditInputs.merge(current, chapter, patch);
        Assert.assertEquals(merged.getString(Article.ARTICLE_TITLE), "new title");
        Assert.assertEquals(merged.getString(Article.ARTICLE_CONTENT), "    old markdown\nbody");
        Assert.assertEquals(merged.getInt(Article.ARTICLE_REWARD_POINT), 25);
        Assert.assertFalse(merged.getBoolean(Article.ARTICLE_COMMENTABLE));
        Assert.assertEquals(merged.getString(LongArticleColumn.COLUMN_ID), "10");
        Assert.assertEquals(merged.getString(LongArticleColumn.CHAPTER_NO), "2");
        Assert.assertFalse(merged.has("userPassword"));
        Assert.assertFalse(merged.has(Article.ARTICLE_AUTHOR_ID));
        Assert.assertEquals(current.toString(), before);
        Assert.assertEquals(ApiV3Json.canonical(patch), hashInput);
        System.out.println("PASS partial edit preserves content, numeric boolean flags and chapter while leaving stored/replay inputs unchanged");
        for (final JSONObject bad : List.of(new JSONObject(), new JSONObject().put("anonymous", true), new JSONObject().put("authorId", "other"),
                new JSONObject().put("draftId", "1"), new JSONObject().put("bypassCensor", true), new JSONObject().put("goodPost", true),
                new JSONObject().put("type", 4), new JSONObject().put("commentable", 1), new JSONObject().put("title", " "),
                new JSONObject().put("chapterNo", "2147483648"), new JSONObject().put("columnId", "1 OR 1=1"), new JSONObject().put("content", "abc"))) {
            try {
                ApiV3ArticleEditInputs.normalize(bad);
                Assert.fail(bad.keySet().toString());
            } catch (final ApiV3Exception expected) { }
        }
        System.out.println("PASS immutable/unknown fields, invalid types, IDs, chapter capacity and required text boundaries");
        final var zero = ApiV3ArticleEditInputs.merge(current, chapter, ApiV3ArticleEditInputs.normalize(new JSONObject().put("rewardPoint", 0).put("rewardContent", "ignored new reward")));
        Assert.assertEquals(zero.getInt(Article.ARTICLE_REWARD_POINT), 25);
        Assert.assertEquals(zero.getString(Article.ARTICLE_REWARD_CONTENT), "old paid content");
        try {
            ApiV3ArticleEditInputs.merge(current, chapter, ApiV3ArticleEditInputs.normalize(new JSONObject().put("qnaOfferPoint", 19)));
            Assert.fail();
        } catch (final ApiV3Exception expected) { }
        final var moved = ApiV3ArticleEditInputs.merge(current, chapter, ApiV3ArticleEditInputs.normalize(new JSONObject().put("columnId", "11")));
        Assert.assertEquals(moved.getString(LongArticleColumn.CHAPTER_NO), "");
        final var detached = ApiV3ArticleEditInputs.merge(current, chapter, ApiV3ArticleEditInputs.normalize(new JSONObject().put("columnId", "")));
        Assert.assertEquals(detached.getString(LongArticleColumn.COLUMN_ID), "");
        System.out.println("PASS legacy zero-reward retention, monotonic QnA offer and chapter auto-assignment on column moves");
        final String record = "<文本>\u001f10\u001f0-0\u001f0-0\u001e\u0018\u001f20\u001f0-0\u001f1-0\u001e";
        final var thought = current().put(Article.ARTICLE_TYPE, 3);
        final var changed = ApiV3ArticleEditInputs.merge(thought, null, ApiV3ArticleEditInputs.normalize(new JSONObject().put("content", record)));
        Assert.assertEquals(changed.getString(Article.ARTICLE_CONTENT), record);
        ApiV3ArticleEditInputs.merge(thought, null, ApiV3ArticleEditInputs.normalize(new JSONObject().put("content", "1\u001f0-0\u001f0-0\u001e")));
        try {
            ApiV3ArticleEditInputs.merge(current, null, ApiV3ArticleEditInputs.normalize(new JSONObject().put("type", 3)));
            Assert.fail();
        }
        catch (final ApiV3Exception e) { Assert.assertEquals(e.code, "thought_disabled"); }
        try {
            ApiV3ArticleEditInputs.merge(thought, null, ApiV3ArticleEditInputs.normalize(new JSONObject().put("type", 0)));
            Assert.fail();
        }
        catch (final ApiV3Exception e) { Assert.assertEquals(e.code, "content_required"); }
        System.out.println("PASS historical thought insert/delete/legacy records without restoring creation through conversion");
        for (final String bad : List.of("text only", "x\u001fno-time\u001f0-0\u001f0-0\u001e", "x\u001f9007199254740992\u001f0-0\u001f0-0\u001e",
                "x\u001f1\u001f-1-0\u001f0-0\u001e", "x\u001f1\u001f0-0\u001f0-0")) {
            try {
                ApiV3ArticleEditInputs.merge(thought, null, ApiV3ArticleEditInputs.normalize(new JSONObject().put("content", bad)));
                Assert.fail();
            } catch (final ApiV3Exception expected) { }
        }
        try {
            ApiV3ArticleEditInputs.merge(current, null, ApiV3ArticleEditInputs.normalize(new JSONObject().put("content", record)));
            Assert.fail();
        } catch (final ApiV3Exception expected) { }
        System.out.println("PASS malformed thought protocol, unsafe numeric capacity and separators in ordinary Markdown rejected");
    }
    static JSONObject current() {
        return new JSONObject().put(Article.ARTICLE_AUTHOR_ID, "owner").put(Article.ARTICLE_STATUS, Article.ARTICLE_STATUS_C_VALID)
                .put(Article.ARTICLE_TITLE, "old title").put(Article.ARTICLE_CONTENT, "    old markdown\nbody").put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_LONG)
                .put(Article.ARTICLE_TAGS, "日常").put(Article.ARTICLE_REWARD_POINT, 25).put(Article.ARTICLE_REWARD_CONTENT, "old paid content")
                .put(Article.ARTICLE_QNA_OFFER_POINT, 20).put(Article.ARTICLE_COMMENTABLE, 0).put("userPassword", "private sentinel");
    }
}
