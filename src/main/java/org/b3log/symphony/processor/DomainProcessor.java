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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.util.Paginator;
import org.b3log.symphony.model.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Domain processor.
 * <ul>
 * <li>Shows domains (/domains), GET</li>
 * <li>Shows domain article (/domain/{domainURI}), GET</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 1.4.0
 */
@Singleton
public class DomainProcessor {

    private static final Logger LOGGER = LogManager.getLogger(ActivityMgmtService.class);

    /**
     * Article query service.
     */
    @Inject
    private ArticleQueryService articleQueryService;

    /**
     * Domain query service.
     */
    @Inject
    private DomainQueryService domainQueryService;

    /**
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

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
     * Shows domain articles.
     *
     * @param context the specified context
     */
    public void showDomainArticles(final RequestContext context) {
        final String domainURI = context.pathVar("domainURI");
        final Request request = context.getRequest();
        final String tag = request.getParameter(Tag.TAG);


        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "domain-articles.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final int pageNum = Paginator.getPage(request);
        int pageSize = Symphonys.ARTICLE_LIST_CNT;

        final JSONObject user = Sessions.getUser();
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);

            if (!UserExt.finishedGuide(user)) {
                context.sendRedirect(Latkes.getServePath() + "/guide");
                return;
            }
        }

        final JSONObject domain = domainQueryService.getByURI(domainURI);
        if (null == domain) {
            context.sendError(404);
            return;
        }

        final List<JSONObject> tags = domainQueryService.getTags(domain.optString(Keys.OBJECT_ID));
        domain.put(Domain.DOMAIN_T_TAGS, (Object) tags);

        JSONObject selectTag = tags.stream().filter(tagObj -> tagObj.optString(Tag.TAG_TITLE).equals(tag)).findFirst().orElse(null);

        dataModel.put(Domain.DOMAIN, domain);
        dataModel.put(Common.SELECTED, domain.optString(Domain.DOMAIN_URI));
        if(StringUtils.isNotBlank(tag)){
            dataModel.put(Domain.DOMAIN_CURRENT_TAG,tag);
        }else {
            dataModel.put(Domain.DOMAIN_CURRENT_TAG,"");
        }


        final String domainId = domain.optString(Keys.OBJECT_ID);

        final JSONObject result = articleQueryService.getDomainArticles(domainId, selectTag,pageNum, pageSize);
        final List<JSONObject> latestArticles = (List<JSONObject>) result.opt(Article.ARTICLES);
        dataModel.put(Common.LATEST_ARTICLES, latestArticles);

        final JSONObject pagination = result.optJSONObject(Pagination.PAGINATION);
        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);

        final List<Integer> pageNums = (List<Integer>) pagination.opt(Pagination.PAGINATION_PAGE_NUMS);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        final List<JSONObject> domains = domainQueryService.getAllDomains();
//        dataModel.put(Common.ALL_DOMAINS, domains);;

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
        dataModel.put(Common.SELECTED, Common.DOMAINS);
    }

    /**
     * Shows domains.
     *
     * @param context the specified context
     */
    public void showDomains(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "domains.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        final JSONObject statistic = optionQueryService.getStatistic();
        final int tagCnt = statistic.optInt(Option.ID_C_STATISTIC_TAG_COUNT);
        dataModel.put(Tag.TAG_T_COUNT, tagCnt);

        final int domainCnt = statistic.optInt(Option.ID_C_STATISTIC_DOMAIN_COUNT);
        dataModel.put(Domain.DOMAIN_T_COUNT, domainCnt);

        final List<JSONObject> domains = domainQueryService.getAllDomains();
        dataModel.put(Common.ALL_DOMAINS, domains);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModel.put(Common.SELECTED, Common.DOMAINS);
    }

    /**
     * Anonymous domains directory API.
     *
     * @param context the specified context
     */
    public void getDomainsApi(final RequestContext context) {
        final List<JSONObject> allDomains = domainQueryService.getAllDomains();
        final List<JSONObject> domains = new ArrayList<>();
        for (final JSONObject domain : allDomains) {
            final JSONObject item = new JSONObject();
            item.put(Domain.DOMAIN_URI, domain.optString(Domain.DOMAIN_URI));
            item.put(Domain.DOMAIN_TITLE, domain.optString(Domain.DOMAIN_TITLE));
            item.put(Domain.DOMAIN_DESCRIPTION, domain.optString(Domain.DOMAIN_DESCRIPTION));
            item.put(Domain.DOMAIN_ICON_PATH, domain.optString(Domain.DOMAIN_ICON_PATH));
            item.put(Domain.DOMAIN_TAG_COUNT, domain.optInt(Domain.DOMAIN_TAG_COUNT));

            // 公开帖数：复用领域文章查询的过滤口径，取分页总页数（pageSize=1 时即总帖数）
            final JSONObject result = articleQueryService.getDomainArticles(domain.optString(Keys.OBJECT_ID), null, 1, 1);
            final JSONObject pagination = null == result ? null : result.optJSONObject(Pagination.PAGINATION);
            item.put("articleCnt", null == pagination ? 0 : pagination.optInt(Pagination.PAGINATION_PAGE_COUNT));

            domains.add(item);
        }

        context.renderJSON(new JSONObject().put("data", new JSONObject().put("domains", (Object) domains)))
                .renderCode(StatusCodes.SUCC).renderMsg("");
    }
}
