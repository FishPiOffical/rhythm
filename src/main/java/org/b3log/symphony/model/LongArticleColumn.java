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
 * Long article column model keys.
 *
 * @author Zephyr
 * @since 3.8.0
 */
public final class LongArticleColumn {

    private LongArticleColumn() {
    }

    /**
     * Long article column collection name.
     */
    public static final String COLUMN = "long_article_column";

    /**
     * Long article chapter collection name.
     */
    public static final String CHAPTER = "long_article_column_chapter";

    /**
     * Column title key.
     */
    public static final String COLUMN_TITLE = "columnTitle";

    /**
     * Column author id key.
     */
    public static final String COLUMN_AUTHOR_ID = "columnAuthorId";

    /**
     * Column chapter count key.
     */
    public static final String COLUMN_ARTICLE_COUNT = "columnArticleCount";

    /**
     * Column create time key.
     */
    public static final String COLUMN_CREATE_TIME = "columnCreateTime";

    /**
     * Column update time key.
     */
    public static final String COLUMN_UPDATE_TIME = "columnUpdateTime";

    /**
     * Column status key.
     */
    public static final String COLUMN_STATUS = "columnStatus";

    /**
     * Column status valid.
     */
    public static final int COLUMN_STATUS_C_VALID = 0;

    /**
     * Column status invalid.
     */
    public static final int COLUMN_STATUS_C_INVALID = 1;

    /**
     * Chapter article id key.
     */
    public static final String ARTICLE_ID = "articleId";

    /**
     * Chapter column id key.
     */
    public static final String COLUMN_ID = "columnId";

    /**
     * Chapter sequence key.
     */
    public static final String CHAPTER_NO = "chapterNo";

    /**
     * Chapter create time key.
     */
    public static final String CHAPTER_CREATE_TIME = "chapterCreateTime";

    /**
     * Chapter update time key.
     */
    public static final String CHAPTER_UPDATE_TIME = "chapterUpdateTime";

    /**
     * Max column title length.
     */
    public static final int MAX_COLUMN_TITLE_LENGTH = 64;
}
