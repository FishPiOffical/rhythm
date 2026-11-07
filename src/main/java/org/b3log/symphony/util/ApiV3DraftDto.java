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
import org.b3log.symphony.model.ArticleDraft;
import org.json.JSONObject;

/** Explicit owned-draft response fields, shared by reads and mutations. */
public final class ApiV3DraftDto {
    private ApiV3DraftDto() { }
    public static JSONObject from(final JSONObject raw, final boolean detail) {
        final JSONObject dto = new JSONObject().put("id", raw.optString(Keys.OBJECT_ID)).put("title", raw.optString(ArticleDraft.TITLE))
                .put("summary", raw.optString(ArticleDraft.SUMMARY)).put("tags", raw.optString(ArticleDraft.TAGS))
                .put("type", raw.optInt(ArticleDraft.ARTICLE_TYPE)).put("columnId", raw.optString(ArticleDraft.COLUMN_ID))
                .put("columnTitle", raw.optString(ArticleDraft.COLUMN_TITLE)).put("chapterNo", raw.optString(ArticleDraft.CHAPTER_NO))
                .put("createdAt", raw.optLong(ArticleDraft.CREATED_TIME)).put("updatedAt", raw.optLong(ArticleDraft.UPDATED_TIME));
        if (detail) dto.put("content", raw.optString(ArticleDraft.CONTENT)).put("thoughtContent", raw.optString(ArticleDraft.THOUGHT_CONTENT))
                .put("rewardContent", raw.optString(ArticleDraft.REWARD_CONTENT)).put("rewardPoint", raw.optInt(ArticleDraft.REWARD_POINT))
                .put("qnaOfferPoint", raw.optInt(ArticleDraft.QNA_OFFER_POINT)).put("commentable", raw.optBoolean(ArticleDraft.COMMENTABLE))
                .put("anonymous", raw.optBoolean(ArticleDraft.ANONYMOUS)).put("notifyFollowers", raw.optBoolean(ArticleDraft.NOTIFY_FOLLOWERS))
                .put("showInList", raw.optInt(ArticleDraft.SHOW_IN_LIST)).put("statement", raw.optInt(ArticleDraft.STATEMENT));
        return dto;
    }

}
