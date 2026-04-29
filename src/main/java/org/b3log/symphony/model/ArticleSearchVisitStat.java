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
package org.b3log.symphony.model;

/**
 * Article search engine visit statistics keys.
 *
 * @author Zephyr
 * @since 3.9.0
 */
public final class ArticleSearchVisitStat {

    private ArticleSearchVisitStat() {
    }

    public static final String ARTICLE_SEARCH_VISIT_STAT = "article_search_visit_stat";
    public static final String ARTICLE_SEARCH_VISIT_STATS = "articleSearchVisitStats";
    public static final String ARTICLE_VISIT_SOURCE_STATS = "articleVisitSourceStats";

    public static final String ARTICLE_ID = "articleId";
    public static final String SEARCH_ENGINE = "searchEngine";
    public static final String SEARCH_ENGINE_NAME = "searchEngineName";
    public static final String SOURCE_KEY = "sourceKey";
    public static final String SOURCE_NAME = "sourceName";
    public static final String SOURCE_ICON = "sourceIcon";
    public static final String SOURCE_CSS = "sourceCss";
    public static final String SOURCE_TYPE = "sourceType";
    public static final String VISIT_CNT = "visitCount";
    public static final String CRAWLER_VISIT_CNT = "crawlerVisitCount";
    public static final String REFERER_VISIT_CNT = "refererVisitCount";
    public static final String UPDATED_AT = "updatedAt";
}
