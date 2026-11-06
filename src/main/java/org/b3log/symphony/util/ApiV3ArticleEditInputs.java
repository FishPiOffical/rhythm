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
import java.util.Map;
import java.util.Set;

/** Typed partial edit; only supplied values enter the replay hash. Current state is merged under row locks. */
public final class ApiV3ArticleEditInputs {
    private ApiV3ArticleEditInputs() { }
    private static final Map<String, String> KEYS = Map.ofEntries(
            Map.entry("title", Article.ARTICLE_TITLE), Map.entry("content", Article.ARTICLE_CONTENT), Map.entry("tags", Article.ARTICLE_TAGS),
            Map.entry("type", Article.ARTICLE_TYPE), Map.entry("rewardContent", Article.ARTICLE_REWARD_CONTENT), Map.entry("rewardPoint", Article.ARTICLE_REWARD_POINT),
            Map.entry("qnaOfferPoint", Article.ARTICLE_QNA_OFFER_POINT), Map.entry("notifyFollowers", Article.ARTICLE_T_NOTIFY_FOLLOWERS),
            Map.entry("showInList", Article.ARTICLE_SHOW_IN_LIST), Map.entry("commentable", Article.ARTICLE_COMMENTABLE), Map.entry("statement", Article.ARTICLE_STATEMENT),
            Map.entry("columnId", LongArticleColumn.COLUMN_ID), Map.entry("columnTitle", LongArticleColumn.COLUMN_TITLE),
            Map.entry("chapterNo", LongArticleColumn.CHAPTER_NO), Map.entry("columnCoverURL", LongArticleColumn.COLUMN_COVER_URL));
    public static final Set<String> FIELDS = KEYS.keySet();
    public static JSONObject normalize(final JSONObject body) {
        if (body.isEmpty() || !FIELDS.containsAll(body.keySet())) throw ApiV3WriteInputs.invalid();
        final var patch = new JSONObject();
        for (final String key : body.keySet()) {
            final Object value = switch (key) {
                case "type" -> {
                    final int type = ApiV3WriteInputs.integer(body, key, 0, 0, 6);
                    if (!Set.of(0, 1, 2, 3, 5, 6).contains(type)) throw ApiV3WriteInputs.invalid();
                    yield type;
                }
                case "rewardPoint", "qnaOfferPoint" -> ApiV3WriteInputs.integer(body, key, 0, 0, Integer.MAX_VALUE);
                case "showInList" -> ApiV3WriteInputs.integer(body, key, 1, 0, 1);
                case "statement" -> ApiV3WriteInputs.integer(body, key, 0, 0, 3);
                case "commentable", "notifyFollowers" -> ApiV3WriteInputs.bool(body, key, false);
                case "columnId" -> ApiV3ArticleInputs.id(body, key);
                case "chapterNo" -> {
                    final String chapter = ApiV3ArticleInputs.text(body, key, ArticleDraft.MAX_CHAPTER_NO_LENGTH, true, false);
                    if (!chapter.isEmpty()) {
                        try { if (!chapter.matches("[1-9][0-9]*") || Integer.parseInt(chapter) < 1) throw ApiV3WriteInputs.invalid(); }
                        catch (final NumberFormatException e) { throw ApiV3WriteInputs.invalid(); }
                    }
                    yield chapter;
                }
                case "columnCoverURL" -> {
                    try { yield ColumnCoverMgmtService.normalizeCoverURL(ApiV3ArticleInputs.text(body, key, 1024, true, false)); }
                    catch (final ServiceException e) { throw ApiV3WriteInputs.invalid(); }
                }
                case "content" -> {
                    final String content = ApiV3WriteInputs.string(body, key, 1024000, false);
                    if (content.isBlank() || content.trim().length() < 4 || content.codePoints().anyMatch(cp -> Character.isISOControl(cp)
                            && cp != '\n' && cp != '\r' && cp != '\t' && cp != 24 && cp != 30 && cp != 31)) throw ApiV3WriteInputs.invalid();
                    yield content;
                }
                case "rewardContent" -> ApiV3ArticleInputs.text(body, key, ArticleDraft.MAX_REWARD_CONTENT_LENGTH, false, true);
                case "columnTitle" -> ApiV3ArticleInputs.text(body, key, LongArticleColumn.MAX_COLUMN_TITLE_LENGTH, true, false);
                default -> {
                    final String text = ApiV3ArticleInputs.text(body, key, 255, true, false);
                    if ("title".equals(key) && text.isEmpty()) throw ApiV3WriteInputs.invalid();
                    yield text;
                }
            };
            patch.put(KEYS.get(key), value);
        }
        return patch;
    }
    public static JSONObject merge(final JSONObject current, final JSONObject chapter, final JSONObject patch) {
        final var request = new JSONObject();
        for (final String field : KEYS.values()) if (current.has(field)) request.put(field, current.opt(field));
        final Object commentable = current.opt(Article.ARTICLE_COMMENTABLE);
        request.put(Article.ARTICLE_COMMENTABLE, commentable instanceof Number number ? number.intValue() != 0 : current.optBoolean(Article.ARTICLE_COMMENTABLE, true));
        request.put(Article.ARTICLE_T_NOTIFY_FOLLOWERS, false);
        request.put(LongArticleColumn.COLUMN_ID, null == chapter ? "" : chapter.optString(LongArticleColumn.COLUMN_ID));
        request.put(LongArticleColumn.CHAPTER_NO, null == chapter ? "" : chapter.optString(LongArticleColumn.CHAPTER_NO));
        request.put(LongArticleColumn.COLUMN_TITLE, "").put(LongArticleColumn.COLUMN_COVER_URL, "");
        if (patch.has(LongArticleColumn.COLUMN_ID) || patch.has(LongArticleColumn.COLUMN_TITLE)) request.put(LongArticleColumn.CHAPTER_NO, "");
        for (final String key : patch.keySet()) request.put(key, patch.opt(key));
        final int oldType = current.optInt(Article.ARTICLE_TYPE), type = request.optInt(Article.ARTICLE_TYPE);
        if (type == Article.ARTICLE_TYPE_C_THOUGHT && oldType != Article.ARTICLE_TYPE_C_THOUGHT) {
            throw new ApiV3Exception(403, "thought_disabled", "不能通过编辑新增思绪内容");
        }
        if (patch.has(Article.ARTICLE_CONTENT)) {
            final String content = request.getString(Article.ARTICLE_CONTENT);
            if (type == Article.ARTICLE_TYPE_C_THOUGHT) thought(content);
            else if (content.codePoints().anyMatch(cp -> Character.isISOControl(cp) && cp != '\n' && cp != '\r' && cp != '\t')) throw ApiV3WriteInputs.invalid();
        } else if (oldType == Article.ARTICLE_TYPE_C_THOUGHT && type != oldType) {
            throw new ApiV3Exception(422, "content_required", "转换思绪类型时需要提供正文");
        }
        if (request.optInt(Article.ARTICLE_QNA_OFFER_POINT) < current.optInt(Article.ARTICLE_QNA_OFFER_POINT)
                || (type == Article.ARTICLE_TYPE_C_QNA && request.optInt(Article.ARTICLE_QNA_OFFER_POINT) < 20)) throw ApiV3WriteInputs.invalid();
        // Legacy editing treats zero as retaining the enabled reward, including its original content.
        if (patch.has(Article.ARTICLE_REWARD_POINT) && patch.optInt(Article.ARTICLE_REWARD_POINT) == 0 && current.optInt(Article.ARTICLE_REWARD_POINT) > 0) {
            request.put(Article.ARTICLE_REWARD_POINT, current.optInt(Article.ARTICLE_REWARD_POINT))
                    .put(Article.ARTICLE_REWARD_CONTENT, current.optString(Article.ARTICLE_REWARD_CONTENT));
        }
        final int reward = request.optInt(Article.ARTICLE_REWARD_POINT);
        final String rewardContent = request.optString(Article.ARTICLE_REWARD_CONTENT);
        if ((reward > 0 && (rewardContent.isBlank() || rewardContent.length() < 4)) || (reward == 0 && !rewardContent.isBlank())) throw ApiV3WriteInputs.invalid();
        return request;
    }
    /** Existing editor format: payload US elapsedMillis US ch-line US ch-line RS. CAN is deletion. */
    private static void thought(final String content) {
        if (!content.endsWith("\u001e")) throw ApiV3WriteInputs.invalid();
        final String[] records = content.split("\u001e", -1);
        int count = 0;
        for (int i = 0; i < records.length; i++) {
            if (i == records.length - 1 && records[i].isEmpty()) continue;
            final String[] raw = records[i].split("\u001f", -1);
            final String[] units = raw.length == 3 ? new String[]{"", raw[0], raw[1], raw[2]} : raw;
            if (units.length != 4) throw ApiV3WriteInputs.invalid();
            if (!"\u0018".equals(units[0]) && units[0].codePoints().anyMatch(cp -> Character.isISOControl(cp) && cp != '\n' && cp != '\r' && cp != '\t')) throw ApiV3WriteInputs.invalid();
            number(units[1]);
            for (int j = 2; j <= 3; j++) {
                final String[] position = units[j].split("-", -1);
                if (position.length != 2) throw ApiV3WriteInputs.invalid();
                number(position[0]); number(position[1]);
            }
            count++;
        }
        if (count == 0) throw ApiV3WriteInputs.invalid();
    }
    private static void number(final String value) {
        try { if (!value.matches("[0-9]{1,16}") || Long.parseLong(value) > 9_007_199_254_740_991L) throw ApiV3WriteInputs.invalid(); }
        catch (final NumberFormatException e) { throw ApiV3WriteInputs.invalid(); }
    }
}
