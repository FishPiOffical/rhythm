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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.ArticleSearchVisitStat;
import org.b3log.symphony.repository.ArticleSearchVisitStatRepository;
import org.b3log.symphony.util.ClientVisitSources;
import org.b3log.symphony.util.SearchEngines;
import org.json.JSONObject;

/**
 * Article search engine visit statistics management service.
 *
 * @author Zephyr
 * @since 3.9.0
 */
@Service
public class ArticleSearchVisitStatMgmtService {

    private static final Logger LOGGER = LogManager.getLogger(ArticleSearchVisitStatMgmtService.class);
    private static final int NO_INCREMENT = 0;
    private static final int VISIT_INCREMENT = 1;
    private static final int SINGLE_ROW_PAGE_COUNT = 1;

    @Inject
    private ArticleSearchVisitStatRepository statRepository;

    public void recordCrawler(final String articleId, final String ip, final String ua) {
        final SearchEngines.Engine engine = SearchEngines.verifiedCrawler(ip, ua);
        if (null == engine) {
            return;
        }
        increment(articleId, engine.key(), VISIT_INCREMENT, NO_INCREMENT);
    }

    public void recordReferer(final String articleId, final String referer) {
        final SearchEngines.Engine engine = SearchEngines.detectReferer(referer);
        if (null == engine) {
            return;
        }
        increment(articleId, engine.key(), NO_INCREMENT, VISIT_INCREMENT);
    }

    public void recordClient(final String articleId, final String ua) {
        for (final ClientVisitSources.Source source : ClientVisitSources.detect(ua)) {
            increment(articleId, source.key(), NO_INCREMENT, VISIT_INCREMENT);
        }
    }

    private synchronized void increment(final String articleId, final String sourceKey,
                                        final int crawlerInc, final int refererInc) {
        if (StringUtils.isBlank(articleId) || StringUtils.isBlank(sourceKey)) {
            return;
        }

        final Transaction tx = statRepository.beginTransaction();
        try {
            final JSONObject stat = getOrCreate(articleId, sourceKey);
            stat.put(ArticleSearchVisitStat.CRAWLER_VISIT_CNT,
                    stat.optInt(ArticleSearchVisitStat.CRAWLER_VISIT_CNT) + crawlerInc);
            stat.put(ArticleSearchVisitStat.REFERER_VISIT_CNT,
                    stat.optInt(ArticleSearchVisitStat.REFERER_VISIT_CNT) + refererInc);
            stat.put(ArticleSearchVisitStat.UPDATED_AT, System.currentTimeMillis());
            statRepository.update(stat.optString(Keys.OBJECT_ID), stat);
            tx.commit();
        } catch (final Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.log(Level.ERROR, "Records article search visit statistics failed", e);
        }
    }

    private JSONObject getOrCreate(final String articleId, final String sourceKey) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(ArticleSearchVisitStat.ARTICLE_ID, FilterOperator.EQUAL, articleId),
                new PropertyFilter(ArticleSearchVisitStat.SEARCH_ENGINE, FilterOperator.EQUAL, sourceKey)))
                .setPageCount(SINGLE_ROW_PAGE_COUNT);
        JSONObject stat = statRepository.getFirst(query);
        if (null != stat) {
            return stat;
        }

        stat = new JSONObject();
        stat.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        stat.put(ArticleSearchVisitStat.ARTICLE_ID, articleId);
        stat.put(ArticleSearchVisitStat.SEARCH_ENGINE, sourceKey);
        stat.put(ArticleSearchVisitStat.CRAWLER_VISIT_CNT, 0);
        stat.put(ArticleSearchVisitStat.REFERER_VISIT_CNT, 0);
        stat.put(ArticleSearchVisitStat.UPDATED_AT, System.currentTimeMillis());
        statRepository.add(stat);
        return stat;
    }
}
