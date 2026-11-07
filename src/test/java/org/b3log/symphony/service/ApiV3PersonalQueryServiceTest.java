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
package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.symphony.model.ArticleDraft;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ApiV3PersonalQueryServiceTest {
    @Test
    public void draftSummaryDoesNotContainBodiesOrOwnerMetadata() {
        final JSONObject raw = new JSONObject().put(Keys.OBJECT_ID, "1").put(ArticleDraft.USER_ID, "private-owner")
                .put(ArticleDraft.CONTENT, "private-content").put(ArticleDraft.THOUGHT_CONTENT, "private-thought")
                .put(ArticleDraft.REWARD_CONTENT, "private-reward").put("apiKey", "private-key");
        final JSONObject dto = ApiV3PersonalQueryService.draftDto(raw, false);
        Assert.assertFalse(dto.has("content"));
        Assert.assertFalse(dto.has("thoughtContent"));
        Assert.assertFalse(dto.has("rewardContent"));
        Assert.assertFalse(dto.toString().contains("private-"));
    }

    @Test
    public void ownedDraftDetailPreservesThoughtAndEditorContentSeparately() {
        final JSONObject raw = new JSONObject().put(Keys.OBJECT_ID, "1").put(ArticleDraft.USER_ID, "private-owner")
                .put(ArticleDraft.CONTENT, "editor-content").put(ArticleDraft.THOUGHT_CONTENT, "thought-records")
                .put(ArticleDraft.REWARD_CONTENT, "reward-content");
        final JSONObject dto = ApiV3PersonalQueryService.draftDto(raw, true);
        Assert.assertEquals(dto.getString("content"), "editor-content");
        Assert.assertEquals(dto.getString("thoughtContent"), "thought-records");
        Assert.assertEquals(dto.getString("rewardContent"), "reward-content");
        Assert.assertFalse(dto.toString().contains("private-owner"));
        Assert.assertFalse(dto.has(ArticleDraft.USER_ID));
    }
}
