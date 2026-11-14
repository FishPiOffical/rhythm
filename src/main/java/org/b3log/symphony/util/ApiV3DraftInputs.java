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
import org.b3log.symphony.model.ArticleDraft;
import org.b3log.symphony.model.LongArticleColumn;
import org.json.JSONObject;

import java.util.Set;

/**
 * 草稿请求字段，未完成的草稿沿用编辑器的内容规则。
 */
public final class ApiV3DraftInputs {
    private ApiV3DraftInputs() {
    }

    public static final Set<String> FIELDS = Set.of("title", "content", "thoughtContent", "tags", "type", "columnId", "columnTitle",
            "chapterNo", "rewardContent", "rewardPoint", "qnaOfferPoint", "commentable", "anonymous", "notifyFollowers", "showInList", "statement");

    public static JSONObject normalize(final JSONObject body) {
        if (!FIELDS.containsAll(body.keySet()))
            throw ApiV3WriteInputs.invalid();
        final int type = ApiV3WriteInputs.integer(body, "type", Article.ARTICLE_TYPE_C_NORMAL, 0, Article.ARTICLE_TYPE_C_LONG);
        if (Article.isInvalidArticleType(type))
            throw ApiV3WriteInputs.invalid();
        final String columnId = ApiV3WriteInputs.string(body, "columnId", ArticleDraft.MAX_COLUMN_ID_LENGTH, true);
        if (!columnId.isEmpty() && !columnId.matches("[0-9]{1,19}"))
            throw ApiV3WriteInputs.invalid();
        return new JSONObject().put(Article.ARTICLE_TITLE, ApiV3WriteInputs.string(body, "title", ArticleDraft.MAX_TITLE_LENGTH, true))
                .put(Article.ARTICLE_CONTENT, ApiV3WriteInputs.string(body, "content", ArticleDraft.MAX_CONTENT_LENGTH, false))
                .put(ArticleDraft.THOUGHT_CONTENT, ApiV3WriteInputs.string(body, "thoughtContent", ArticleDraft.MAX_THOUGHT_CONTENT_LENGTH, false))
                .put(Article.ARTICLE_TAGS, ApiV3WriteInputs.string(body, "tags", ArticleDraft.MAX_TAGS_LENGTH, true))
                .put(Article.ARTICLE_TYPE, type).put(LongArticleColumn.COLUMN_ID, columnId)
                .put(LongArticleColumn.COLUMN_TITLE, ApiV3WriteInputs.string(body, "columnTitle", LongArticleColumn.MAX_COLUMN_TITLE_LENGTH, true))
                .put(LongArticleColumn.CHAPTER_NO, ApiV3WriteInputs.string(body, "chapterNo", ArticleDraft.MAX_CHAPTER_NO_LENGTH, true))
                .put(Article.ARTICLE_REWARD_CONTENT, ApiV3WriteInputs.string(body, "rewardContent", ArticleDraft.MAX_REWARD_CONTENT_LENGTH, false))
                .put(Article.ARTICLE_REWARD_POINT, ApiV3WriteInputs.integer(body, "rewardPoint", 0, 0, Integer.MAX_VALUE))
                .put(Article.ARTICLE_QNA_OFFER_POINT, ApiV3WriteInputs.integer(body, "qnaOfferPoint", 0, 0, Integer.MAX_VALUE))
                .put(Article.ARTICLE_COMMENTABLE, ApiV3WriteInputs.bool(body, "commentable", true))
                .put(Article.ARTICLE_ANONYMOUS, ApiV3WriteInputs.bool(body, "anonymous", false))
                .put(Article.ARTICLE_T_NOTIFY_FOLLOWERS, ApiV3WriteInputs.bool(body, "notifyFollowers", false))
                .put(Article.ARTICLE_SHOW_IN_LIST, ApiV3WriteInputs.integer(body, "showInList", Article.ARTICLE_SHOW_IN_LIST_C_YES, 0, 1))
                .put(Article.ARTICLE_STATEMENT, ApiV3WriteInputs.integer(body, "statement", 0, 0, 3));
    }
}
