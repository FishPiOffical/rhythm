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
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.repository.LongArticleChapterRepository;
import org.b3log.symphony.repository.LongArticleColumnRepository;
import org.json.JSONObject;

/**
 * Long article column management service.
 *
 * @author Zephyr
 * @since 3.8.0
 */
@Service
public class LongArticleColumnMgmtService {

    private static final Logger LOGGER = LogManager.getLogger(LongArticleColumnMgmtService.class);

    @Inject
    private LongArticleColumnRepository longArticleColumnRepository;

    @Inject
    private LongArticleChapterRepository longArticleChapterRepository;

    /**
     * Syncs chapter relation in current transaction.
     *
     * @param article           article data
     * @param requestJSONObject request data
     * @throws ServiceException service exception
     */
    public void syncChapterInCurrentTransaction(final JSONObject article, final JSONObject requestJSONObject) throws ServiceException {
        final String articleId = article.optString(Keys.OBJECT_ID);
        if (StringUtils.isBlank(articleId)) {
            return;
        }

        final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);
        final int articleType = article.optInt(Article.ARTICLE_TYPE);
        final long now = System.currentTimeMillis();

        try {
            final JSONObject existingChapter = getChapterByArticleId(articleId);
            final String previousColumnId = null == existingChapter ? "" : existingChapter.optString(LongArticleColumn.COLUMN_ID);

            if (Article.ARTICLE_TYPE_C_LONG != articleType) {
                if (null != existingChapter) {
                    longArticleChapterRepository.remove(existingChapter.optString(Keys.OBJECT_ID));
                    syncColumnArticleCount(previousColumnId, now);
                }
                return;
            }

            String columnId = StringUtils.trim(requestJSONObject.optString(LongArticleColumn.COLUMN_ID));
            if ("__NEW__".equals(columnId)) {
                columnId = "";
            }

            final String columnTitle = StringUtils.trim(requestJSONObject.optString(LongArticleColumn.COLUMN_TITLE));
            final String chapterNoStr = StringUtils.trim(requestJSONObject.optString(LongArticleColumn.CHAPTER_NO));
            int chapterNo = 0;
            if (StringUtils.isNotBlank(chapterNoStr)) {
                if (!chapterNoStr.matches("^[1-9]\\d*$")) {
                    throw new ServiceException("章节号必须是正整数");
                }
                chapterNo = Integer.parseInt(chapterNoStr);
            }

            if (StringUtils.isBlank(columnId) && StringUtils.isBlank(columnTitle)) {
                if (null != existingChapter) {
                    longArticleChapterRepository.remove(existingChapter.optString(Keys.OBJECT_ID));
                    syncColumnArticleCount(previousColumnId, now);
                }
                return;
            }

            if (StringUtils.isBlank(columnId)) {
                columnId = findOrCreateColumnId(authorId, columnTitle, now);
            } else {
                validateColumnOwner(columnId, authorId);
            }

            if (chapterNo <= 0) {
                if (null != existingChapter && StringUtils.equals(columnId, existingChapter.optString(LongArticleColumn.COLUMN_ID))) {
                    chapterNo = existingChapter.optInt(LongArticleColumn.CHAPTER_NO, 1);
                } else {
                    chapterNo = getNextChapterNo(columnId);
                }
            }

            ensureChapterNoAvailable(columnId, chapterNo, articleId);

            if (null == existingChapter) {
                final JSONObject chapter = new JSONObject();
                chapter.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
                chapter.put(LongArticleColumn.ARTICLE_ID, articleId);
                chapter.put(LongArticleColumn.COLUMN_ID, columnId);
                chapter.put(LongArticleColumn.CHAPTER_NO, chapterNo);
                chapter.put(LongArticleColumn.CHAPTER_CREATE_TIME, now);
                chapter.put(LongArticleColumn.CHAPTER_UPDATE_TIME, now);
                longArticleChapterRepository.add(chapter);
            } else {
                existingChapter.put(LongArticleColumn.COLUMN_ID, columnId);
                existingChapter.put(LongArticleColumn.CHAPTER_NO, chapterNo);
                existingChapter.put(LongArticleColumn.CHAPTER_UPDATE_TIME, now);
                longArticleChapterRepository.update(existingChapter.optString(Keys.OBJECT_ID), existingChapter);
            }

            if (StringUtils.isNotBlank(previousColumnId) && !StringUtils.equals(previousColumnId, columnId)) {
                syncColumnArticleCount(previousColumnId, now);
            }
            syncColumnArticleCount(columnId, now);
        } catch (final ServiceException e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("Sync long article chapter failed [articleId={}]", articleId, e);
            throw new ServiceException(e);
        }
    }

    /**
     * Removes chapter relation in current transaction.
     *
     * @param articleId article id
     * @throws ServiceException service exception
     */
    public void removeChapterInCurrentTransaction(final String articleId) throws ServiceException {
        if (StringUtils.isBlank(articleId)) {
            return;
        }

        try {
            final JSONObject existingChapter = getChapterByArticleId(articleId);
            if (null == existingChapter) {
                return;
            }

            final String previousColumnId = existingChapter.optString(LongArticleColumn.COLUMN_ID);
            longArticleChapterRepository.remove(existingChapter.optString(Keys.OBJECT_ID));
            syncColumnArticleCount(previousColumnId, System.currentTimeMillis());
        } catch (final Exception e) {
            LOGGER.error("Remove long article chapter failed [articleId={}]", articleId, e);
            throw new ServiceException(e);
        }
    }

    private JSONObject getChapterByArticleId(final String articleId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(LongArticleColumn.ARTICLE_ID,
                FilterOperator.EQUAL, articleId)).setPageCount(1);
        return longArticleChapterRepository.getFirst(query);
    }

    private String findOrCreateColumnId(final String authorId, final String columnTitle, final long now) throws RepositoryException, ServiceException {
        if (StringUtils.isBlank(columnTitle)) {
            throw new ServiceException("请填写专栏名称或选择已有专栏");
        }
        if (columnTitle.length() > LongArticleColumn.MAX_COLUMN_TITLE_LENGTH) {
            throw new ServiceException("专栏名称长度不能超过 " + LongArticleColumn.MAX_COLUMN_TITLE_LENGTH + " 个字符");
        }

        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(LongArticleColumn.COLUMN_AUTHOR_ID, FilterOperator.EQUAL, authorId),
                new PropertyFilter(LongArticleColumn.COLUMN_TITLE, FilterOperator.EQUAL, columnTitle),
                new PropertyFilter(LongArticleColumn.COLUMN_STATUS, FilterOperator.EQUAL, LongArticleColumn.COLUMN_STATUS_C_VALID)))
                .setPageCount(1);
        JSONObject column = longArticleColumnRepository.getFirst(query);
        if (null != column) {
            return column.optString(Keys.OBJECT_ID);
        }

        column = new JSONObject();
        final String columnId = Ids.genTimeMillisId();
        column.put(Keys.OBJECT_ID, columnId);
        column.put(LongArticleColumn.COLUMN_TITLE, columnTitle);
        column.put(LongArticleColumn.COLUMN_AUTHOR_ID, authorId);
        column.put(LongArticleColumn.COLUMN_ARTICLE_COUNT, 0);
        column.put(LongArticleColumn.COLUMN_STATUS, LongArticleColumn.COLUMN_STATUS_C_VALID);
        column.put(LongArticleColumn.COLUMN_CREATE_TIME, now);
        column.put(LongArticleColumn.COLUMN_UPDATE_TIME, now);
        longArticleColumnRepository.add(column);
        return columnId;
    }

    private void validateColumnOwner(final String columnId, final String authorId) throws RepositoryException, ServiceException {
        final JSONObject column = longArticleColumnRepository.get(columnId);
        if (null == column || LongArticleColumn.COLUMN_STATUS_C_VALID != column.optInt(LongArticleColumn.COLUMN_STATUS)
                || !StringUtils.equals(authorId, column.optString(LongArticleColumn.COLUMN_AUTHOR_ID))) {
            throw new ServiceException("专栏不存在或无权限操作");
        }
    }

    private int getNextChapterNo(final String columnId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(LongArticleColumn.COLUMN_ID,
                FilterOperator.EQUAL, columnId)).addSort(LongArticleColumn.CHAPTER_NO,
                SortDirection.DESCENDING).setPage(1, 1).setPageCount(1);
        final JSONObject maxChapter = longArticleChapterRepository.getFirst(query);
        if (null == maxChapter) {
            return 1;
        }
        return Math.max(1, maxChapter.optInt(LongArticleColumn.CHAPTER_NO) + 1);
    }

    private void ensureChapterNoAvailable(final String columnId, final int chapterNo, final String articleId)
            throws RepositoryException, ServiceException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(LongArticleColumn.COLUMN_ID, FilterOperator.EQUAL, columnId),
                new PropertyFilter(LongArticleColumn.CHAPTER_NO, FilterOperator.EQUAL, chapterNo))).setPageCount(1);
        final JSONObject chapter = longArticleChapterRepository.getFirst(query);
        if (null != chapter && !StringUtils.equals(articleId, chapter.optString(LongArticleColumn.ARTICLE_ID))) {
            throw new ServiceException("该章节号已被占用，请更换章节号");
        }
    }

    private void syncColumnArticleCount(final String columnId, final long now) throws RepositoryException {
        if (StringUtils.isBlank(columnId)) {
            return;
        }

        final JSONObject column = longArticleColumnRepository.get(columnId);
        if (null == column) {
            return;
        }

        final Query query = new Query().setFilter(new PropertyFilter(LongArticleColumn.COLUMN_ID,
                FilterOperator.EQUAL, columnId));
        final int articleCount = (int) longArticleChapterRepository.count(query);
        column.put(LongArticleColumn.COLUMN_ARTICLE_COUNT, articleCount);
        column.put(LongArticleColumn.COLUMN_UPDATE_TIME, now);
        longArticleColumnRepository.update(columnId, column);
    }
}
