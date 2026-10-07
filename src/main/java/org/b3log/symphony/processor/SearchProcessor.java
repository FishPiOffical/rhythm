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
package org.b3log.symphony.processor;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.util.Paginator;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.service.ArticleQueryService;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.SearchQueryService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.DesensitizeUtil;
import org.b3log.symphony.util.Escapes;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Search processor.
 * <ul>
 * <li>Searches keyword (/search), GET</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 1.4.0
 */
@Singleton
public class SearchProcessor {

    /**
     * Search query service.
     */
    @Inject
    private SearchQueryService searchQueryService;

    /**
     * Article query service.
     */
    @Inject
    private ArticleQueryService articleQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Searches.
     *
     * @param context the specified context
     */
    public void search(final RequestContext context) {
        final Request request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "search-articles.ftl");

        if (!Symphonys.ES_ENABLED && !Symphonys.ALGOLIA_ENABLED) {
            context.sendError(404);
            return;
        }

        final Map<String, Object> dataModel = renderer.getDataModel();
        String keyword = context.param("key");
        if (StringUtils.isBlank(keyword)) {
            keyword = "";
        }
        dataModel.put(Common.KEY, Escapes.escapeHTML(keyword));

        final int pageNum = Paginator.getPage(request);
        int pageSize = Symphonys.ARTICLE_LIST_CNT;
        final JSONObject user = Sessions.getUser();
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);
        }
        final List<JSONObject> articles = new ArrayList<>();
        int total = 0;

        if (Symphonys.ES_ENABLED) {
            final JSONObject result = searchQueryService.searchElasticsearch(Article.ARTICLE, keyword, pageNum, pageSize);
            if (null == result || 0 != result.optInt("status")) {
                context.sendError(404);
                return;
            }

            final JSONObject hitsResult = result.optJSONObject("hits");
            final JSONArray hits = hitsResult.optJSONArray("hits");

            for (int i = 0; i < hits.length(); i++) {
                final JSONObject article = hits.optJSONObject(i).optJSONObject("_source");
                articles.add(article);
            }

            total = result.optInt("total");
        }

        if (Symphonys.ALGOLIA_ENABLED) {
            final JSONObject result = searchQueryService.searchAlgolia(keyword, pageNum, pageSize);
            if (null == result) {
                context.sendError(404);
                return;
            }

            final JSONArray hits = result.optJSONArray("hits");

            for (int i = 0; i < hits.length(); i++) {
                final JSONObject article = hits.optJSONObject(i);
                articles.add(article);
            }

            total = result.optInt("nbHits");
            if (total > 1000) {
                total = 1000; // Algolia limits the maximum number of search results to 1000
            }
        }

        articleQueryService.organizeArticles(articles);
        final Integer participantsCnt = Symphonys.ARTICLE_LIST_PARTICIPANTS_CNT;
        articleQueryService.genParticipants(articles, participantsCnt);

        dataModel.put(Article.ARTICLES, articles);

        final int pageCount = (int) Math.ceil(total / (double) pageSize);
        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, Symphonys.ARTICLE_LIST_WIN_SIZE);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        String searchEmptyLabel = langPropsService.get("searchEmptyLabel");
        searchEmptyLabel = searchEmptyLabel.replace("${key}", keyword);
        dataModel.put("searchEmptyLabel", searchEmptyLabel);
    }

    /**
     * 匿名搜索 API 限流：单 IP 每 2 秒允许一次（搜索查询开销较大）。
     */
    private static final long SEARCH_API_INTERVAL_MILLIS = 2000L;
    private static final ConcurrentMap<String, Long> SEARCH_API_RATE_LIMIT = new ConcurrentHashMap<>();

    /**
     * 搜索 API 返回字段白名单。
     */
    private static final String[] SEARCH_API_FIELDS = {
            Keys.OBJECT_ID,
            Article.ARTICLE_TITLE,
            Article.ARTICLE_TAGS,
            Article.ARTICLE_PERMALINK,
            Article.ARTICLE_CREATE_TIME,
            Article.ARTICLE_UPDATE_TIME,
            Article.ARTICLE_VIEW_CNT,
            Article.ARTICLE_COMMENT_CNT,
            Article.ARTICLE_TYPE,
            Article.ARTICLE_PERFECT,
            Article.ARTICLE_T_AUTHOR,
            Article.ARTICLE_T_PREVIEW_CONTENT,
            Article.ARTICLE_T_THUMBNAIL_URL,
            Article.ARTICLE_T_TAG_OBJS,
            Article.ARTICLE_T_HEAT,
            Article.ARTICLE_T_TITLE_EMOJI,
            Article.ARTICLE_T_TITLE_EMOJI_UNICODE,
            Article.ARTICLE_T_VIEW_CNT_DISPLAY_FORMAT,
            Common.TIME_AGO,
            Common.CMT_TIME_AGO,
    };

    /**
     * Anonymous searchable API.
     *
     * @param context the specified context
     */
    public void searchApi(final RequestContext context) {
        final Request request = context.getRequest();

        // 单 IP 简易限流
        final String ip = Requests.getRemoteAddr(request);
        final long now = System.currentTimeMillis();
        final Long last = SEARCH_API_RATE_LIMIT.get(ip);
        if (null != last && now - last < SEARCH_API_INTERVAL_MILLIS) {
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求过于频繁，请稍后再试");
            return;
        }
        SEARCH_API_RATE_LIMIT.put(ip, now);
        if (SEARCH_API_RATE_LIMIT.size() > 10000) {
            final long cutoff = now - 60_000;
            SEARCH_API_RATE_LIMIT.entrySet().removeIf(e -> e.getValue() < cutoff);
        }

        String keyword = context.param("q");
        if (StringUtils.isBlank(keyword)) {
            keyword = context.param("key");
        }
        if (StringUtils.isBlank(keyword)) {
            keyword = "";
        }
        keyword = keyword.trim();
        final int maxLength = 100;
        if (keyword.length() > maxLength) {
            keyword = keyword.substring(0, maxLength);
        }

        final int pageNum = Paginator.getPage(request);
        final String size = context.param("size");
        int pageSize = 0;
        if (StringUtils.isNotBlank(size)) {
            try {
                pageSize = Integer.parseInt(size.trim());
            } catch (final NumberFormatException ignored) {
            }
        }
        if (pageSize <= 0) {
            pageSize = 20;
        }
        pageSize = Math.min(pageSize, 50);

        final JSONObject data = new JSONObject();
        final List<JSONObject> articles = new ArrayList<>();
        int total = 0;

        if (StringUtils.isNotBlank(keyword) && (Symphonys.ES_ENABLED || Symphonys.ALGOLIA_ENABLED)) {
            final List<JSONObject> hits = new ArrayList<>();
            if (Symphonys.ES_ENABLED) {
                final JSONObject result = searchQueryService.searchElasticsearch(Article.ARTICLE, keyword, pageNum, pageSize);
                if (null == result || 0 != result.optInt("status")) {
                    context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("搜索服务不可用");
                    return;
                }
                final JSONObject hitsResult = result.optJSONObject("hits");
                final JSONArray hitArray = null == hitsResult ? null : hitsResult.optJSONArray("hits");
                if (null != hitArray) {
                    for (int i = 0; i < hitArray.length(); i++) {
                        final JSONObject source = hitArray.optJSONObject(i).optJSONObject("_source");
                        if (null != source) {
                            hits.add(source);
                        }
                    }
                }
                total = result.optInt("total");
            }

            if (Symphonys.ALGOLIA_ENABLED) {
                final JSONObject result = searchQueryService.searchAlgolia(keyword, pageNum, pageSize);
                if (null == result) {
                    context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("搜索服务不可用");
                    return;
                }
                final JSONArray hitArray = result.optJSONArray("hits");
                if (null != hitArray) {
                    for (int i = 0; i < hitArray.length(); i++) {
                        final JSONObject hit = hitArray.optJSONObject(i);
                        if (null != hit) {
                            hits.add(hit);
                        }
                    }
                }
                total = result.optInt("nbHits");
                if (total > 1000) {
                    total = 1000; // Algolia limits the maximum number of search results to 1000
                }
            }

            // 与页面搜索一致：补齐展示字段
            articleQueryService.organizeArticles(hits);
            articleQueryService.genParticipants(hits, Symphonys.ARTICLE_LIST_PARTICIPANTS_CNT);

            // 响应白名单 + 去敏
            for (final JSONObject hit : hits) {
                final JSONObject filtered = new JSONObject();
                for (final String field : SEARCH_API_FIELDS) {
                    final Object value = hit.opt(field);
                    if (null != value) {
                        filtered.put(field, value);
                    }
                }
                articles.add(filtered);
            }
            DesensitizeUtil.articlesDesensitize(articles);
        }

        data.put(Article.ARTICLES, (Object) articles);
        data.put("total", total);

        final int pageCount = (int) Math.ceil(total / (double) pageSize);
        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, Symphonys.ARTICLE_LIST_WIN_SIZE);
        final JSONObject pagination = new JSONObject();
        pagination.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        pagination.put(Pagination.PAGINATION_PAGE_NUMS, (Object) pageNums);
        data.put(Pagination.PAGINATION, pagination);

        data.put(Common.KEY, Escapes.escapeHTML(keyword));

        context.renderJSON(new JSONObject().put("data", data)).renderCode(StatusCodes.SUCC).renderMsg("");
    }
}
