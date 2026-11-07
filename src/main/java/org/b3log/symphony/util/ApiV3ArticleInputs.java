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
import org.b3log.symphony.service.ColumnCoverMgmtService;
import org.b3log.latke.service.ServiceException;
import org.json.JSONObject;
import java.util.Set;

/** Typed creation boundary. Semantic/tag/censor/permission checks still belong to the publication service. */
public final class ApiV3ArticleInputs {
    private ApiV3ArticleInputs() { }
    public static final Set<String> FIELDS = Set.of("title", "content", "tags", "type", "rewardContent", "rewardPoint", "qnaOfferPoint",
            "anonymous", "notifyFollowers", "showInList", "commentable", "statement", "columnId", "columnTitle", "chapterNo", "columnCoverURL", "draftId", "goodPost");
    public static JSONObject normalize(final JSONObject body) {
        if (!FIELDS.containsAll(body.keySet())) throw ApiV3WriteInputs.invalid();
        final int type = ApiV3WriteInputs.integer(body, "type", Article.ARTICLE_TYPE_C_NORMAL, 0, Article.ARTICLE_TYPE_C_LONG);
        if (type == Article.ARTICLE_TYPE_C_THOUGHT) throw new ApiV3Exception(403, "thought_disabled", "思绪发布功能已关闭");
        if (!Set.of(Article.ARTICLE_TYPE_C_NORMAL, Article.ARTICLE_TYPE_C_DISCUSSION, Article.ARTICLE_TYPE_C_CITY_BROADCAST,
                Article.ARTICLE_TYPE_C_QNA, Article.ARTICLE_TYPE_C_LONG).contains(type)) throw ApiV3WriteInputs.invalid();
        final String title = text(body, "title", 255, true, false), content = text(body, "content", 1024000, false, true);
        if (title.isBlank() || content.trim().length() < 4) throw ApiV3WriteInputs.invalid();
        final int rewardPoint = ApiV3WriteInputs.integer(body, "rewardPoint", 0, 0, Integer.MAX_VALUE);
        final int offer = ApiV3WriteInputs.integer(body, "qnaOfferPoint", 0, 0, Integer.MAX_VALUE);
        final String reward = text(body, "rewardContent", ArticleDraft.MAX_REWARD_CONTENT_LENGTH, false, true);
        if ((rewardPoint == 0 && !reward.isEmpty()) || (rewardPoint > 0 && (reward.isBlank() || reward.length() < 4))
                || (type == Article.ARTICLE_TYPE_C_QNA && offer < 20)) throw ApiV3WriteInputs.invalid();
        final String columnId = id(body, "columnId"), draftId = id(body, "draftId");
        final String chapter = text(body, "chapterNo", ArticleDraft.MAX_CHAPTER_NO_LENGTH, true, false);
        if (!chapter.isEmpty()) {
            try { if (!chapter.matches("[1-9][0-9]*") || Integer.parseInt(chapter) < 1) throw ApiV3WriteInputs.invalid(); }
            catch (final NumberFormatException e) { throw ApiV3WriteInputs.invalid(); }
        }
        final String cover;
        try { cover = ColumnCoverMgmtService.normalizeCoverURL(text(body, "columnCoverURL", LongArticleColumn.MAX_COLUMN_COVER_URL_LENGTH, true, false)); }
        catch (final ServiceException e) { throw ApiV3WriteInputs.invalid(); }
        return new JSONObject().put(Article.ARTICLE_TITLE, title).put(Article.ARTICLE_CONTENT, content)
                .put(Article.ARTICLE_TAGS, text(body, "tags", 255, true, false)).put(Article.ARTICLE_TYPE, type)
                .put(Article.ARTICLE_REWARD_CONTENT, reward).put(Article.ARTICLE_REWARD_POINT, rewardPoint).put(Article.ARTICLE_QNA_OFFER_POINT, offer)
                .put(Article.ARTICLE_ANONYMOUS, ApiV3WriteInputs.bool(body, "anonymous", false) ? Article.ARTICLE_ANONYMOUS_C_ANONYMOUS : Article.ARTICLE_ANONYMOUS_C_PUBLIC)
                .put(Article.ARTICLE_T_NOTIFY_FOLLOWERS, ApiV3WriteInputs.bool(body, "notifyFollowers", false))
                .put(Article.ARTICLE_SHOW_IN_LIST, ApiV3WriteInputs.integer(body, "showInList", Article.ARTICLE_SHOW_IN_LIST_C_YES, 0, 1))
                .put(Article.ARTICLE_COMMENTABLE, ApiV3WriteInputs.bool(body, "commentable", true))
                .put(Article.ARTICLE_STATEMENT, ApiV3WriteInputs.integer(body, "statement", 0, 0, 3))
                .put(LongArticleColumn.COLUMN_ID, columnId).put(LongArticleColumn.COLUMN_TITLE, text(body, "columnTitle", LongArticleColumn.MAX_COLUMN_TITLE_LENGTH, true, false))
                .put(LongArticleColumn.CHAPTER_NO, chapter).put(LongArticleColumn.COLUMN_COVER_URL, cover)
                .put(ArticleDraft.ARTICLE_DRAFT_ID, draftId).put(Article.ARTICLE_GOOD_POST, ApiV3WriteInputs.bool(body, "goodPost", false) ? "yes" : "");
    }
    static String id(final JSONObject body, final String field) {
        final String id = text(body, field, 19, true, false);
        if (!id.isEmpty() && !id.matches("[0-9]{1,19}")) throw ApiV3WriteInputs.invalid();
        return id;
    }
    /** Matches the editor's explicit introduction-post reward exclusion, after tag normalization. */
    public static void validateGoodPost(final JSONObject request) {
        final String tags = request.optString(Article.ARTICLE_TAGS);
        if ("yes".equals(request.optString(Article.ARTICLE_GOOD_POST)) && (tags.contains("新人报道") || tags.contains("新人报到"))) {
            throw new ApiV3Exception(422, "invalid_good_post", "新人报道帖不能标记为奖励帖");
        }
    }
    static String text(final JSONObject body, final String field, final int max, final boolean trim, final boolean multiline) {
        final String text = ApiV3WriteInputs.string(body, field, max, trim);
        if (text.codePoints().anyMatch(cp -> Character.isISOControl(cp) && !(multiline && (cp == '\n' || cp == '\r' || cp == '\t')))) throw ApiV3WriteInputs.invalid();
        return text;
    }
}
