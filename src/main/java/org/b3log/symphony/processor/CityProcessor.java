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
import org.apache.commons.lang.time.DateUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.util.Paginator;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * City processor.
 * <ul>
 * <li>Shows city articles (/city/{city}), GET</li>
 * <li>Show city users (/city/{city}/users), GET </li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://ld246.com/member/ZephyrJung">Zephyr</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 1.3.0
 */
@Singleton
public class CityProcessor {

    /**
     * Article query service.
     */
    @Inject
    private ArticleQueryService articleQueryService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

    /**
     * Avatar query service.
     */
    @Inject
    private AvatarQueryService avatarQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langService;

    /**
     * City users API rate limit per IP: max 60 accesses in 60 seconds.
     */
    private static final SimpleCurrentLimiter CITY_API_LIMITER = new SimpleCurrentLimiter(60, 60);

    private static final int CITY_USERS_API_DEFAULT_SIZE = 20;
    private static final int CITY_USERS_API_MAX_SIZE = 50;

    /**
     * City users list API.
     *
     * @param context the specified context
     */
    public void cityUsersApi(final RequestContext context) {
        final JsonRenderer renderer = new JsonRenderer();
        renderer.setJSONObject(new JSONObject());
        context.setRenderer(renderer);

        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (!CITY_API_LIMITER.access(ip)) {
            context.renderCodeMsg(StatusCodes.ERR, "操作过于频繁，请稍后重试");
            return;
        }

        final String city = StringUtils.trimToEmpty(context.pathVar("cityName"));
        if (StringUtils.isBlank(city) || city.length() > 64) {
            context.renderCodeMsg(StatusCodes.ERR, "城市名不合法");
            return;
        }

        final int pageNum = parsePage(context.param("p"));
        final int pageSize = parseSize(context.param("size"));

        final JSONObject requestJSONObject = new JSONObject();
        requestJSONObject.put(Keys.OBJECT_ID, "");
        requestJSONObject.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        requestJSONObject.put(Pagination.PAGINATION_PAGE_SIZE, pageSize);
        requestJSONObject.put(Pagination.PAGINATION_WINDOW_SIZE, Symphonys.CITY_USERS_WIN_SIZE);
        requestJSONObject.put(UserExt.USER_LATEST_LOGIN_TIME, 0L);
        requestJSONObject.put(UserExt.USER_CITY, city);
        final JSONObject result = userQueryService.getUsersByCity(requestJSONObject);
        if (null == result) {
            context.renderCodeMsg(StatusCodes.ERR, "查询失败，请稍后重试");
            return;
        }

        final List<JSONObject> users = (List<JSONObject>) result.opt(User.USERS);
        final List<JSONObject> safeUsers = new ArrayList<>();
        if (null != users) {
            for (final JSONObject user : users) {
                final JSONObject item = new JSONObject();
                item.put(Keys.OBJECT_ID, user.optString(Keys.OBJECT_ID));
                item.put(User.USER_NAME, user.optString(User.USER_NAME));
                item.put(UserExt.USER_NICKNAME, user.optString(UserExt.USER_NICKNAME));
                item.put(UserExt.USER_AVATAR_URL, user.optString(UserExt.USER_AVATAR_URL));
                item.put(UserExt.USER_NO, user.optString(UserExt.USER_NO));
                item.put(UserExt.USER_INTRO, user.optString(UserExt.USER_INTRO));
                item.put(UserExt.USER_ONLINE_FLAG, user.optBoolean(UserExt.USER_ONLINE_FLAG));
                safeUsers.add(item);
            }
        }

        final JSONObject srcPagination = result.optJSONObject(Pagination.PAGINATION);
        final JSONObject pagination = new JSONObject();
        pagination.put(Pagination.PAGINATION_PAGE_COUNT,
                null == srcPagination ? 0 : srcPagination.optInt(Pagination.PAGINATION_PAGE_COUNT));
        pagination.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        pagination.put(Pagination.PAGINATION_PAGE_SIZE, pageSize);

        final JSONObject data = new JSONObject();
        data.put(Common.CITY, city);
        data.put(User.USERS, safeUsers);
        data.put(Pagination.PAGINATION, pagination);
        context.renderData(data).renderCode(StatusCodes.SUCC).renderMsg("");
    }

    private static int parsePage(final String value) {
        if (StringUtils.isBlank(value)) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (final NumberFormatException e) {
            return 1;
        }
    }

    private static int parseSize(final String value) {
        if (StringUtils.isBlank(value)) {
            return CITY_USERS_API_DEFAULT_SIZE;
        }
        try {
            final int parsed = Integer.parseInt(value.trim());
            if (parsed < 1) {
                return CITY_USERS_API_DEFAULT_SIZE;
            }
            return Math.min(parsed, CITY_USERS_API_MAX_SIZE);
        } catch (final NumberFormatException e) {
            return CITY_USERS_API_DEFAULT_SIZE;
        }
    }

    /**
     * Show city articles.
     *
     * @param context the specified context
     */
    public void showCityArticles(final RequestContext context) {
        final String city = context.pathVar("city");
        final Request request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "city.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);

        dataModel.put(Common.CURRENT, "");

        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        List<JSONObject> articles = new ArrayList<>();
        dataModel.put(Article.ARTICLES, articles); // an empty list to avoid null check in template
        dataModel.put(Common.SELECTED, Common.CITY);

        final JSONObject user = Sessions.getUser();
        if (!UserExt.finishedGuide(user)) {
            context.sendRedirect(Latkes.getServePath() + "/guide");
            return;
        }

        dataModel.put(UserExt.USER_GEO_STATUS, true);
        dataModel.put(Common.CITY_FOUND, true);
        dataModel.put(Common.CITY, langService.get("sameCityLabel"));

        if (UserExt.USER_GEO_STATUS_C_PUBLIC != user.optInt(UserExt.USER_GEO_STATUS)) {
            dataModel.put(UserExt.USER_GEO_STATUS, false);
            return;
        }

        final String userCity = user.optString(UserExt.USER_CITY);

        String queryCity = city;
        if ("my".equals(city)) {
            dataModel.put(Common.CITY, userCity);
            queryCity = userCity;
        } else {
            dataModel.put(Common.CITY, city);
        }

        if (StringUtils.isBlank(userCity)) {
            dataModel.put(Common.CITY_FOUND, false);
            return;
        }

        final int pageNum = Paginator.getPage(request);
        final int pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);
        final int windowSize = Symphonys.ARTICLE_LIST_WIN_SIZE;

        final JSONObject statistic = optionQueryService.getOption(queryCity + "-ArticleCount");
        if (null != statistic) {
            articles = articleQueryService.getArticlesByCity(queryCity, pageNum, pageSize);
            dataModel.put(Article.ARTICLES, articles);
        }

        final int articleCnt = null == statistic ? 0 : statistic.optInt(Option.OPTION_VALUE);
        final int pageCount = (int) Math.ceil(articleCnt / (double) pageSize);

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
    }

    /**
     * Show city users.
     *
     * @param context the specified context
     */
    public void showCityUsers(final RequestContext context) {
        final String city = context.pathVar("city");
        final Request request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "city.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);

        dataModel.put(Common.CURRENT, "/users");

        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        List<JSONObject> users = new ArrayList<>();
        dataModel.put(User.USERS, users);
        dataModel.put(Common.SELECTED, Common.CITY);

        final JSONObject user = Sessions.getUser();
        if (!UserExt.finishedGuide(user)) {
            context.sendRedirect(Latkes.getServePath() + "/guide");
            return;
        }

        dataModel.put(UserExt.USER_GEO_STATUS, true);
        dataModel.put(Common.CITY_FOUND, true);
        dataModel.put(Common.CITY, langService.get("sameCityLabel"));
        if (UserExt.USER_GEO_STATUS_C_PUBLIC != user.optInt(UserExt.USER_GEO_STATUS)) {
            dataModel.put(UserExt.USER_GEO_STATUS, false);
            return;
        }

        final String userCity = user.optString(UserExt.USER_CITY);

        String queryCity = city;
        if ("my".equals(city)) {
            dataModel.put(Common.CITY, userCity);
            queryCity = userCity;
        } else {
            dataModel.put(Common.CITY, city);
        }

        if (StringUtils.isBlank(userCity)) {
            dataModel.put(Common.CITY_FOUND, false);
            return;
        }

        final int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.CITY_USERS_CNT;
        final int windowSize = Symphonys.CITY_USERS_WIN_SIZE;

        final JSONObject requestJSONObject = new JSONObject();
        requestJSONObject.put(Keys.OBJECT_ID, user.optString(Keys.OBJECT_ID));
        requestJSONObject.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        requestJSONObject.put(Pagination.PAGINATION_PAGE_SIZE, pageSize);
        requestJSONObject.put(Pagination.PAGINATION_WINDOW_SIZE, windowSize);
        final long latestLoginTime = DateUtils.addDays(new Date(), Integer.MIN_VALUE).getTime(); // all users
        requestJSONObject.put(UserExt.USER_LATEST_LOGIN_TIME, latestLoginTime);
        requestJSONObject.put(UserExt.USER_CITY, queryCity);
        final JSONObject result = userQueryService.getUsersByCity(requestJSONObject);
        final List<JSONObject> cityUsers = (List<JSONObject>) result.opt(User.USERS);
        final JSONObject pagination = result.optJSONObject(Pagination.PAGINATION);
        if (!cityUsers.isEmpty()) {
            users.addAll(cityUsers);
            dataModel.put(User.USERS, users);
        }

        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
    }
}
