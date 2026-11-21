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
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.ArticleDraft;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ApiV3DraftInputsTest {
    @Test
    public void unfinishedDraftsPreserveSourceAndNormalizeDefaults() {
        final String source = "  <b>正文</b>\n- 未完成\t";
        final JSONObject request = ApiV3DraftInputs.normalize(new JSONObject().put("title", "  标题  ")
                .put("content", source).put("thoughtContent", "[思绪记录]").put("tags", " java, "));
        Assert.assertEquals(request.getString(Article.ARTICLE_TITLE), "标题");
        Assert.assertEquals(request.getString(Article.ARTICLE_CONTENT), source);
        Assert.assertEquals(request.getString(ArticleDraft.THOUGHT_CONTENT), "[思绪记录]");
        Assert.assertTrue(request.getBoolean(Article.ARTICLE_COMMENTABLE));
        Assert.assertFalse(request.getBoolean(Article.ARTICLE_ANONYMOUS));
    }

    @Test
    public void invalidTypesLengthsAndOwnershipFieldsFail() {
        for (final JSONObject body : new JSONObject[]{
                new JSONObject().put("userId", "other"), new JSONObject().put("id", "other"),
                new JSONObject().put("title", "x".repeat(ArticleDraft.MAX_TITLE_LENGTH + 1)),
                new JSONObject().put("content", "x".repeat(ArticleDraft.MAX_CONTENT_LENGTH + 1)),
                new JSONObject().put("commentable", 1), new JSONObject().put("type", 7),
                new JSONObject().put("rewardPoint", -1), new JSONObject().put("showInList", 2),
                new JSONObject().put("statement", 4), new JSONObject().put("columnId", "../other")}) {
            try { ApiV3DraftInputs.normalize(body); Assert.fail("Invalid draft was accepted"); }
            catch (final ApiV3Exception expected) { Assert.assertEquals(expected.status, 422); }
        }
    }

    @Test
    public void responseSummaryNeverContainsBodiesOrOwnerMetadata() {
        final JSONObject raw = new JSONObject().put(Keys.OBJECT_ID, "1").put(ArticleDraft.USER_ID, "private-owner")
                .put(ArticleDraft.CONTENT, "private-content").put(ArticleDraft.THOUGHT_CONTENT, "private-thought")
                .put(ArticleDraft.REWARD_CONTENT, "private-reward").put("apiKey", "private-key");
        final JSONObject summary = ApiV3DraftDto.from(raw, false);
        Assert.assertFalse(summary.toString().contains("private-"));
        final JSONObject detail = ApiV3DraftDto.from(raw, true);
        Assert.assertEquals(detail.getString("content"), "private-content");
        Assert.assertEquals(detail.getString("thoughtContent"), "private-thought");
        Assert.assertFalse(detail.toString().contains("private-owner"));
    }

    public static void main(final String[] args) {
        final var test = new ApiV3DraftInputsTest();
        test.unfinishedDraftsPreserveSourceAndNormalizeDefaults();
        test.invalidTypesLengthsAndOwnershipFieldsFail();
        test.responseSummaryNeverContainsBodiesOrOwnerMetadata();
        System.out.println("PASS: 3 ApiV3DraftInputs/DTO tests");
    }
}
