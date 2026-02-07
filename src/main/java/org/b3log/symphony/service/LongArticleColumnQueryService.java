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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.LongArticleChapterRepository;
import org.b3log.symphony.repository.LongArticleColumnRepository;
import org.b3log.symphony.util.Escapes;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Long article column query service.
 *
 * @author Zephyr
 * @since 3.8.0
 */
@Service
public class LongArticleColumnQueryService {

    private static final Logger LOGGER = LogManager.getLogger(LongArticleColumnQueryService.class);

    @Inject
    private LongArticleColumnRepository longArticleColumnRepository;

    @Inject
    private LongArticleChapterRepository longArticleChapterRepository;

    @Inject
    private ArticleRepository articleRepository;

    /**
     * Gets user columns.
     *
     * @param userId     user id
     * @param fetchSize  fetch size
     * @return column list
     */
    public List<JSONObject> getUserColumns(final String userId, final int fetchSize) {
        if (StringUtils.isBlank(userId)) {
            return Collections.emptyList();
        }

        try {
            final Query query = new Query().setFilter(CompositeFilterOperator.and(
                    new PropertyFilter(LongArticleColumn.COLUMN_AUTHOR_ID, FilterOperator.EQUAL, userId),
                    new PropertyFilter(LongArticleColumn.COLUMN_STATUS, FilterOperator.EQUAL, LongArticleColumn.COLUMN_STATUS_C_VALID)))
                    .addSort(LongArticleColumn.COLUMN_UPDATE_TIME, SortDirection.DESCENDING)
                    .setPageCount(1)
                    .setPage(1, fetchSize);
            return longArticleColumnRepository.getList(query);
        } catch (final Exception e) {
            LOGGER.error("Gets user long article columns failed [userId={}]", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Gets chapter meta for specified article.
     *
     * @param articleId article id
     * @return chapter meta
     */
    public JSONObject getArticleChapterMeta(final String articleId) {
        if (StringUtils.isBlank(articleId)) {
            return null;
        }

        try {
            final JSONObject chapter = getChapterByArticleId(articleId);
            if (null == chapter) {
                return null;
            }

            final JSONObject column = longArticleColumnRepository.get(chapter.optString(LongArticleColumn.COLUMN_ID));
            if (null == column || LongArticleColumn.COLUMN_STATUS_C_VALID != column.optInt(LongArticleColumn.COLUMN_STATUS)) {
                return null;
            }

            final JSONObject ret = new JSONObject();
            ret.put(LongArticleColumn.COLUMN_ID, column.optString(Keys.OBJECT_ID));
            ret.put(LongArticleColumn.COLUMN_TITLE, column.optString(LongArticleColumn.COLUMN_TITLE));
            ret.put(LongArticleColumn.CHAPTER_NO, chapter.optInt(LongArticleColumn.CHAPTER_NO));
            ret.put(LongArticleColumn.COLUMN_ARTICLE_COUNT, column.optInt(LongArticleColumn.COLUMN_ARTICLE_COUNT));
            return ret;
        } catch (final Exception e) {
            LOGGER.error("Gets article chapter meta failed [articleId={}]", articleId, e);
            return null;
        }
    }

    /**
     * Gets column view data for specified article.
     *
     * @param articleId article id
     * @return column view data
     */
    public JSONObject getArticleColumnView(final String articleId) {
        if (StringUtils.isBlank(articleId)) {
            return null;
        }

        try {
            final JSONObject chapter = getChapterByArticleId(articleId);
            if (null == chapter) {
                return null;
            }

            final String columnId = chapter.optString(LongArticleColumn.COLUMN_ID);
            final JSONObject column = longArticleColumnRepository.get(columnId);
            if (null == column || LongArticleColumn.COLUMN_STATUS_C_VALID != column.optInt(LongArticleColumn.COLUMN_STATUS)) {
                return null;
            }

            final Query query = new Query().setFilter(new PropertyFilter(LongArticleColumn.COLUMN_ID,
                    FilterOperator.EQUAL, columnId))
                    .addSort(LongArticleColumn.CHAPTER_NO, SortDirection.ASCENDING)
                    .addSort(LongArticleColumn.CHAPTER_CREATE_TIME, SortDirection.ASCENDING)
                    .setPageCount(1);
            final List<JSONObject> relations = longArticleChapterRepository.getList(query);
            if (relations.isEmpty()) {
                return null;
            }

            final List<JSONObject> chapterViews = new ArrayList<>();
            int currentIndex = -1;
            int idx = 0;
            for (final JSONObject relation : relations) {
                final String chapterArticleId = relation.optString(LongArticleColumn.ARTICLE_ID);
                final JSONObject chapterArticle = articleRepository.get(chapterArticleId);
                if (null == chapterArticle || Article.ARTICLE_STATUS_C_VALID != chapterArticle.optInt(Article.ARTICLE_STATUS)) {
                    continue;
                }

                final JSONObject chapterView = buildChapterView(chapterArticle, relation.optInt(LongArticleColumn.CHAPTER_NO));
                chapterViews.add(chapterView);
                if (StringUtils.equals(chapterArticleId, articleId)) {
                    currentIndex = idx;
                }
                idx++;
            }

            if (chapterViews.isEmpty() || currentIndex < 0 || currentIndex >= chapterViews.size()) {
                return null;
            }

            final JSONObject ret = new JSONObject();
            column.put(LongArticleColumn.COLUMN_ARTICLE_COUNT, chapterViews.size());
            ret.put("column", column);
            ret.put("chapters", (Object) chapterViews);
            ret.put(LongArticleColumn.CHAPTER_NO, chapterViews.get(currentIndex).optInt(LongArticleColumn.CHAPTER_NO));
            if (currentIndex > 0) {
                ret.put("previous", chapterViews.get(currentIndex - 1));
            }
            if (currentIndex < chapterViews.size() - 1) {
                ret.put("next", chapterViews.get(currentIndex + 1));
            }
            return ret;
        } catch (final Exception e) {
            LOGGER.error("Gets article column view failed [articleId={}]", articleId, e);
            return null;
        }
    }

    private JSONObject buildChapterView(final JSONObject article, final int chapterNo) {
        final JSONObject chapterView = new JSONObject();
        final String title = article.optString(Article.ARTICLE_TITLE);
        final String safeTitle = Escapes.escapeHTML(title);
        chapterView.put(Article.ARTICLE_T_ID, article.optString(Keys.OBJECT_ID));
        chapterView.put(Article.ARTICLE_PERMALINK, article.optString(Article.ARTICLE_PERMALINK));
        chapterView.put(Article.ARTICLE_TITLE, safeTitle);
        chapterView.put(Article.ARTICLE_T_TITLE_EMOJI, Emotions.convert(safeTitle));
        chapterView.put(LongArticleColumn.CHAPTER_NO, chapterNo);

        String preview = Jsoup.clean(Markdowns.toHTML(article.optString(Article.ARTICLE_CONTENT)), Whitelist.none());
        preview = StringUtils.replace(preview, "\n", " ");
        preview = StringUtils.replace(preview, "\r", " ");
        preview = StringUtils.normalizeSpace(preview);
        if (preview.length() > 120) {
            preview = StringUtils.substring(preview, 0, 120) + "...";
        }
        chapterView.put(Article.ARTICLE_T_PREVIEW_CONTENT, preview);
        return chapterView;
    }

    private JSONObject getChapterByArticleId(final String articleId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(LongArticleColumn.ARTICLE_ID,
                FilterOperator.EQUAL, articleId)).setPageCount(1);
        return longArticleChapterRepository.getFirst(query);
    }
}
