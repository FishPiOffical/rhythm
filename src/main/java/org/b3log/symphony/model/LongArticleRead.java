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
 * Long article read statistics keys.
 *
 * @author Zephyr
 * @since 3.7.0
 */
public final class LongArticleRead {

    private LongArticleRead() {
    }

    public static final String STAT = "article_long_read_stat";
    public static final String HISTORY = "article_long_read_history";
    public static final String USER = "article_long_read_user";
    public static final String ANON = "article_long_read_anon";

    public static final String ARTICLE_ID = "articleId";
    public static final String WINDOW_START = "windowStart";
    public static final String REGISTERED_UNSETTLED = "registeredUnsettledCnt";
    public static final String ANON_UNSETTLED = "anonymousUnsettledCnt";
    public static final String REGISTERED_TOTAL = "registeredTotalCnt";
    public static final String ANON_TOTAL = "anonymousTotalCnt";
    public static final String LAST_SETTLED_AT = "lastSettledAt";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    public static final String WINDOW_END = "windowEnd";
    public static final String REGISTERED_CNT = "registeredCnt";
    public static final String ANON_CNT = "anonymousCnt";
    public static final String ANON_CAPPED_CNT = "anonymousCappedCnt";
    public static final String REWARD_POINT = "rewardPoint";
    public static final String SETTLED_AT = "settledAt";

    public static final String USER_ID = "userId";
    public static final String FIRST_READ_AT = "firstReadAt";
    public static final String READER_HASH = "readerHash";
}
