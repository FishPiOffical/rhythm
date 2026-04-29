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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ArticleSearchVisitStat;
import org.b3log.symphony.repository.ArticleSearchVisitStatRepository;
import org.b3log.symphony.util.ClientVisitSources;
import org.b3log.symphony.util.SearchEngines;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Article search engine visit statistics query service.
 *
 * @author Zephyr
 * @since 3.9.0
 */
@Service
public class ArticleSearchVisitStatQueryService {

    private static final Logger LOGGER = LogManager.getLogger(ArticleSearchVisitStatQueryService.class);
    private static final int FIRST_PAGE = 1;
    private static final int MAX_STAT_ROWS = 100;
    private static final String SOURCE_TYPE_SEARCH = "search";

    @Inject
    private ArticleSearchVisitStatRepository statRepository;

    public List<JSONObject> getStats(final String articleId) {
        final List<JSONObject> ret = new ArrayList<>();
        try {
            final Query query = new Query().setFilter(new PropertyFilter(
                    ArticleSearchVisitStat.ARTICLE_ID, FilterOperator.EQUAL, articleId))
                    .setPage(FIRST_PAGE, MAX_STAT_ROWS);
            final List<JSONObject> stats = statRepository.getList(query);
            if (null == stats) {
                return ret;
            }
            for (final SearchEngines.Engine engine : SearchEngines.Engine.values()) {
                appendStat(ret, stats, engine);
            }
            for (final ClientVisitSources.Source source : ClientVisitSources.Source.values()) {
                appendStat(ret, stats, source);
            }
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Gets article search visit statistics failed", e);
        }
        return ret;
    }

    private void appendStat(final List<JSONObject> ret, final List<JSONObject> stats,
                            final SearchEngines.Engine engine) {
        for (final JSONObject stat : stats) {
            if (!engine.key().equals(stat.optString(ArticleSearchVisitStat.SEARCH_ENGINE))) {
                continue;
            }

            final int crawlerCnt = stat.optInt(ArticleSearchVisitStat.CRAWLER_VISIT_CNT);
            final int refererCnt = stat.optInt(ArticleSearchVisitStat.REFERER_VISIT_CNT);
            if (crawlerCnt == 0 && refererCnt == 0) {
                return;
            }

            ret.add(toSourceStat(engine, crawlerCnt, refererCnt));
            return;
        }
    }

    private void appendStat(final List<JSONObject> ret, final List<JSONObject> stats,
                            final ClientVisitSources.Source source) {
        for (final JSONObject stat : stats) {
            if (!source.key().equals(stat.optString(ArticleSearchVisitStat.SEARCH_ENGINE))) {
                continue;
            }

            final int visitCnt = stat.optInt(ArticleSearchVisitStat.REFERER_VISIT_CNT);
            if (visitCnt == 0) {
                return;
            }
            ret.add(toSourceStat(source, visitCnt));
            return;
        }
    }

    private JSONObject toSourceStat(final SearchEngines.Engine engine, final int crawlerCnt, final int refererCnt) {
        final JSONObject ret = new JSONObject();
        ret.put(ArticleSearchVisitStat.SOURCE_KEY, SOURCE_TYPE_SEARCH + ':' + engine.key());
        ret.put(ArticleSearchVisitStat.SOURCE_NAME, sourceName(engine, crawlerCnt, refererCnt));
        ret.put(ArticleSearchVisitStat.SOURCE_ICON, engineIcon(engine));
        ret.put(ArticleSearchVisitStat.SOURCE_CSS, engine.key());
        ret.put(ArticleSearchVisitStat.SOURCE_TYPE, SOURCE_TYPE_SEARCH);
        ret.put(ArticleSearchVisitStat.VISIT_CNT, crawlerCnt + refererCnt);
        return ret;
    }

    private JSONObject toSourceStat(final ClientVisitSources.Source source, final int visitCnt) {
        final JSONObject ret = new JSONObject();
        ret.put(ArticleSearchVisitStat.SOURCE_KEY, source.key());
        ret.put(ArticleSearchVisitStat.SOURCE_NAME, source.displayName());
        ret.put(ArticleSearchVisitStat.SOURCE_ICON, source.icon());
        ret.put(ArticleSearchVisitStat.SOURCE_CSS, source.css());
        ret.put(ArticleSearchVisitStat.SOURCE_TYPE, source.type());
        ret.put(ArticleSearchVisitStat.VISIT_CNT, visitCnt);
        return ret;
    }

    private String sourceName(final SearchEngines.Engine engine, final int crawlerCnt, final int refererCnt) {
        final List<String> parts = new ArrayList<>();
        if (0 < crawlerCnt) {
            parts.add("抓取 " + crawlerCnt);
        }
        if (0 < refererCnt) {
            parts.add("来源 " + refererCnt);
        }
        return engine.displayName() + "：" + String.join("，", parts);
    }

    private String engineIcon(final SearchEngines.Engine engine) {
        switch (engine) {
            case GOOGLE:
                return "visitSourceGoogle";
            case BAIDU:
                return "visitSourceBaidu";
            case BING:
                return "visitSourceBing";
            case SOGOU:
                return "visitSourceSogou";
            case YANDEX:
                return "visitSourceYandex";
            case SO360:
                return "visitSourceSo360";
            case DUCKDUCKGO:
                return "visitSourceDuckDuckGo";
            default:
                return "visitSourceSearch";
        }
    }
}
