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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.LongArticleRead;
import org.b3log.symphony.repository.LongArticleReadStatRepository;
import org.b3log.symphony.repository.LongArticleReadWindowRepository;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/** 查询当前长篇阅读窗口统计。 */
@Service
public class LongArticleReadQueryService {

    private static final long WINDOW_MILLIS = TimeUnit.HOURS.toMillis(6);

    @Inject
    private LongArticleReadWindowRepository windowRepository;

    @Inject
    private LongArticleReadStatRepository legacyStatRepository;

    public JSONObject getStat(final String articleId) {
        final JSONObject result = emptyStat();
        try {
            final long windowStart = System.currentTimeMillis() / WINDOW_MILLIS * WINDOW_MILLIS;
            final var window = windowRepository.getFirst(new Query().setFilter(CompositeFilterOperator.and(
                    new PropertyFilter(LongArticleRead.ARTICLE_ID, FilterOperator.EQUAL, articleId),
                    new PropertyFilter(LongArticleRead.WINDOW_START, FilterOperator.EQUAL, windowStart))));
            if (null != window) {
                return copyWindow(window, result);
            }
            return copyLegacy(articleId, windowStart, result);
        } catch (final Exception e) {
            throw new IllegalStateException("读取长篇阅读统计失败", e);
        }
    }

    private JSONObject copyWindow(final JSONObject window, final JSONObject result) {
        result.put(LongArticleRead.REGISTERED_UNSETTLED, window.optInt(LongArticleRead.REGISTERED_CNT));
        result.put(LongArticleRead.ANON_UNSETTLED, window.optInt(LongArticleRead.ANON_CNT));
        result.put(LongArticleRead.REGISTERED_TOTAL, window.optInt(LongArticleRead.REGISTERED_CNT));
        result.put(LongArticleRead.ANON_TOTAL, window.optInt(LongArticleRead.ANON_CNT));
        return result;
    }

    private JSONObject copyLegacy(final String articleId, final long windowStart, final JSONObject result) throws Exception {
        final var legacy = legacyStatRepository.getFirst(new Query().setFilter(
                new PropertyFilter(LongArticleRead.ARTICLE_ID, FilterOperator.EQUAL, articleId)));
        if (null == legacy || legacy.optLong(LongArticleRead.WINDOW_START) != windowStart) {
            return result;
        }
        result.put(LongArticleRead.REGISTERED_UNSETTLED, legacy.optInt(LongArticleRead.REGISTERED_UNSETTLED));
        result.put(LongArticleRead.ANON_UNSETTLED, legacy.optInt(LongArticleRead.ANON_UNSETTLED));
        result.put(LongArticleRead.REGISTERED_TOTAL, legacy.optInt(LongArticleRead.REGISTERED_TOTAL));
        result.put(LongArticleRead.ANON_TOTAL, legacy.optInt(LongArticleRead.ANON_TOTAL));
        return result;
    }

    private JSONObject emptyStat() {
        final JSONObject result = new JSONObject();
        result.put(LongArticleRead.REGISTERED_UNSETTLED, 0);
        result.put(LongArticleRead.ANON_UNSETTLED, 0);
        result.put(LongArticleRead.REGISTERED_TOTAL, 0);
        result.put(LongArticleRead.ANON_TOTAL, 0);
        return result;
    }
}
