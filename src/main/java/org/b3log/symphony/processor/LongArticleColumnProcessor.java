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
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Sessions;
import org.json.JSONArray;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 专栏管理处理器。
 */
@Singleton
public class LongArticleColumnProcessor {

    private static final int MANAGE_FETCH_SIZE = 100;

    @Inject
    private DataModelService dataModelService;

    @Inject
    private LongArticleColumnQueryService longArticleColumnQueryService;

    @Inject
    private ColumnCoverMgmtService columnCoverMgmtService;

    @Inject
    private LongArticleColumnMgmtService longArticleColumnMgmtService;

    @Inject
    private UserQueryService userQueryService;

    /**
     * Public column list/detail API rate limit per IP: max 60 accesses in 60 seconds.
     */
    public static final SimpleCurrentLimiter PUBLIC_COLUMNS_LIMITER = new SimpleCurrentLimiter(60, 60);

    private static final int PUBLIC_LIST_DEFAULT_SIZE = 12;
    private static final int PUBLIC_LIST_MAX_SIZE = 12;
    private static final int MINE_DEFAULT_SIZE = 100;
    private static final int MINE_MAX_SIZE = 100;
    private static final int COLUMN_ARTICLES_DEFAULT_SIZE = 20;
    private static final int COLUMN_ARTICLES_MAX_SIZE = 100;

    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LongArticleColumnProcessor processor = beanManager.getReference(LongArticleColumnProcessor.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final AnonymousViewCheckMidware anonymousViewCheckMidware = beanManager.getReference(AnonymousViewCheckMidware.class);

        Dispatcher.post("/api/columns/{columnId}/cover", processor::updateCover, loginCheck::handle);
        Dispatcher.post("/api/columns/{columnId}/rename", processor::renameColumn, loginCheck::handle);
        Dispatcher.post("/api/columns/order", processor::orderColumns, loginCheck::handle);
        Dispatcher.post("/api/columns/{columnId}/remove", processor::removeColumn, loginCheck::handle);
        Dispatcher.get("/api/columns/latest", processor::latestColumnsApi, anonymousViewCheckMidware::handle);
        Dispatcher.get("/api/columns/hot", processor::hotColumnsApi, anonymousViewCheckMidware::handle);
        Dispatcher.get("/api/columns/mine", processor::myColumnsApi, loginCheck::handle);
        Dispatcher.get("/api/columns/{columnId}", processor::columnDetailApi, anonymousViewCheckMidware::handle);
        Dispatcher.get("/api/columns/{columnId}/articles", processor::columnArticlesApi, anonymousViewCheckMidware::handle);
    }

    public void showManage(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/column-manage.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final JSONObject currentUser = getCurrentUser(context);
        if (null == currentUser) {
            context.sendError(401);
            return;
        }

        final String userId = currentUser.optString(Keys.OBJECT_ID);
        final String selectedColumnId = context.param("columnId");
        final List<JSONObject> columns = longArticleColumnQueryService.getManageableColumns(userId, MANAGE_FETCH_SIZE);
        dataModel.put("manageableColumns", filterSelectedColumn(columns, selectedColumnId));
        dataModel.put("selectedColumnId", null == selectedColumnId ? "" : selectedColumnId);
        dataModelService.fillHeaderAndFooter(context, dataModel);
        fillSideModules(dataModel);
        dataModel.put(Common.SELECTED, "column");
    }

    public void updateCover(final RequestContext context) {
        try {
            final JSONObject currentUser = getCurrentUser(context);
            if (null == currentUser) {
                context.sendError(401);
                return;
            }

            final JSONObject column = columnCoverMgmtService.updateCoverURL(
                    currentUser.optString(Keys.OBJECT_ID),
                    context.pathVar("columnId"),
                    context.requestJSON().optString(LongArticleColumn.COLUMN_COVER_URL));
            longArticleColumnQueryService.fillCoverFields(column);
            renderSuccess(context, new JSONObject().put("column", column));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    /**
     * Renames a column owned by the current user.
     *
     * @param context the specified context
     */
    public void renameColumn(final RequestContext context) {
        try {
            final JSONObject currentUser = getCurrentUser(context);
            if (null == currentUser) {
                context.sendError(401);
                return;
            }

            final JSONObject requestBody = context.requestJSON();
            final String columnTitle = null == requestBody ? "" : requestBody.optString(LongArticleColumn.COLUMN_TITLE);
            final JSONObject column = longArticleColumnMgmtService.renameColumn(
                    currentUser.optString(Keys.OBJECT_ID),
                    context.pathVar("columnId"), columnTitle);
            longArticleColumnQueryService.fillCoverFields(column);
            renderSuccess(context, new JSONObject().put("column", column));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    /**
     * Updates the display order of the current user's columns; the index in the given id list becomes the new order.
     *
     * @param context the specified context
     */
    public void orderColumns(final RequestContext context) {
        try {
            final JSONObject currentUser = getCurrentUser(context);
            if (null == currentUser) {
                context.sendError(401);
                return;
            }

            final JSONObject requestBody = context.requestJSON();
            final JSONArray columnIdsArray = null == requestBody ? null : requestBody.optJSONArray("columnIds");
            if (null == columnIdsArray) {
                renderError(context, "请提供专栏排序");
                return;
            }
            final List<String> columnIds = new ArrayList<>();
            for (int i = 0; i < columnIdsArray.length(); i++) {
                final String columnId = columnIdsArray.optString(i);
                if (StringUtils.isNotBlank(columnId)) {
                    columnIds.add(columnId);
                }
            }

            longArticleColumnMgmtService.updateColumnOrder(currentUser.optString(Keys.OBJECT_ID), columnIds);
            renderSuccess(context, new JSONObject());
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    /**
     * Removes (marks invalid) a column owned by the current user; refuses when the column still has chapters.
     *
     * @param context the specified context
     */
    public void removeColumn(final RequestContext context) {
        try {
            final JSONObject currentUser = getCurrentUser(context);
            if (null == currentUser) {
                context.sendError(401);
                return;
            }

            longArticleColumnMgmtService.removeColumn(
                    currentUser.optString(Keys.OBJECT_ID), context.pathVar("columnId"));
            renderSuccess(context, new JSONObject());
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    /**
     * Gets the latest columns for the home page column shelf.
     *
     * @param context the specified context
     */
    public void latestColumnsApi(final RequestContext context) {
        initJson(context);
        if (!rateLimited(context)) {
            return;
        }

        final int size = parseSize(context.param("size"), PUBLIC_LIST_DEFAULT_SIZE, PUBLIC_LIST_MAX_SIZE);
        context.renderData(longArticleColumnQueryService.getLatestColumns(size))
                .renderCode(StatusCodes.SUCC).renderMsg("");
    }

    /**
     * Gets the hot columns for the home page column shelf.
     *
     * @param context the specified context
     */
    public void hotColumnsApi(final RequestContext context) {
        initJson(context);
        if (!rateLimited(context)) {
            return;
        }

        final int size = parseSize(context.param("size"), PUBLIC_LIST_DEFAULT_SIZE, PUBLIC_LIST_MAX_SIZE);
        context.renderData(longArticleColumnQueryService.getHotColumns(size))
                .renderCode(StatusCodes.SUCC).renderMsg("");
    }

    /**
     * Gets the current user's own columns for the long article post page and column management.
     *
     * @param context the specified context
     */
    public void myColumnsApi(final RequestContext context) {
        initJson(context);

        final JSONObject currentUser = (JSONObject) context.attr(User.USER);
        final int size = parseSize(context.param("size"), MINE_DEFAULT_SIZE, MINE_MAX_SIZE);
        context.renderData(longArticleColumnQueryService.getUserColumns(
                        currentUser.optString(Keys.OBJECT_ID), size))
                .renderCode(StatusCodes.SUCC).renderMsg("");
    }

    /**
     * Gets a column detail: title, description, article count and author profile.
     *
     * @param context the specified context
     */
    public void columnDetailApi(final RequestContext context) {
        initJson(context);
        if (!rateLimited(context)) {
            return;
        }

        final String columnId = context.pathVar("columnId");
        final JSONObject view = longArticleColumnQueryService.getColumnViewById(columnId);
        if (null == view) {
            context.renderCodeMsg(StatusCodes.ERR, "专栏不存在");
            return;
        }

        final JSONObject column = view.optJSONObject("column");
        final JSONObject author = userQueryService.getUser(column.optString(LongArticleColumn.COLUMN_AUTHOR_ID));
        final JSONObject authorView = new JSONObject();
        if (null != author) {
            authorView.put(User.USER_NAME, author.optString(User.USER_NAME));
            authorView.put(UserExt.USER_NICKNAME, author.optString(UserExt.USER_NICKNAME));
            authorView.put(UserExt.USER_AVATAR_URL, author.optString(UserExt.USER_AVATAR_URL));
        }

        final JSONObject data = new JSONObject();
        data.put(LongArticleColumn.COLUMN_ID, column.optString(Keys.OBJECT_ID, columnId));
        data.put(LongArticleColumn.COLUMN_TITLE, column.optString(LongArticleColumn.COLUMN_TITLE));
        // Columns currently do not store a description; keep the field stable for future use.
        data.put("columnDescription", "");
        data.put(LongArticleColumn.COLUMN_ARTICLE_COUNT, column.optInt(LongArticleColumn.COLUMN_ARTICLE_COUNT));
        data.put("author", authorView);
        context.renderData(data).renderCode(StatusCodes.SUCC).renderMsg("");
    }

    /**
     * Gets paginated chapter articles of a column.
     *
     * @param context the specified context
     */
    public void columnArticlesApi(final RequestContext context) {
        initJson(context);
        if (!rateLimited(context)) {
            return;
        }

        final String columnId = context.pathVar("columnId");
        final JSONObject view = longArticleColumnQueryService.getColumnViewById(columnId);
        if (null == view) {
            context.renderCodeMsg(StatusCodes.ERR, "专栏不存在");
            return;
        }

        final JSONArray chapterArray = view.optJSONArray("chapters");
        final List<JSONObject> chapters = new ArrayList<>();
        if (null != chapterArray) {
            for (int i = 0; i < chapterArray.length(); i++) {
                chapters.add(chapterArray.getJSONObject(i));
            }
        }

        final int page = parsePage(context.param("p"));
        final int size = parseSize(context.param("size"), COLUMN_ARTICLES_DEFAULT_SIZE, COLUMN_ARTICLES_MAX_SIZE);
        final int recordCount = chapters.size();
        final int pageCount = Math.max(1, (int) Math.ceil((double) recordCount / size));

        final List<JSONObject> pageArticles = new ArrayList<>();
        if (page <= pageCount) {
            final int fromIndex = (page - 1) * size;
            final int toIndex = Math.min(fromIndex + size, recordCount);
            for (final JSONObject chapter : chapters.subList(fromIndex, toIndex)) {
                final JSONObject item = new JSONObject();
                item.put(Article.ARTICLE_T_ID, chapter.optString(Article.ARTICLE_T_ID));
                item.put(Article.ARTICLE_PERMALINK, chapter.optString(Article.ARTICLE_PERMALINK));
                item.put(LongArticleColumn.CHAPTER_NO, chapter.optInt(LongArticleColumn.CHAPTER_NO));
                item.put(Article.ARTICLE_TITLE, chapter.optString(Article.ARTICLE_TITLE));
                item.put(Article.ARTICLE_CREATE_TIME, chapter.optString(Article.ARTICLE_CREATE_TIME));
                pageArticles.add(item);
            }
        }

        final JSONObject pagination = new JSONObject();
        pagination.put("paginationPageCount", pageCount);
        pagination.put("paginationRecordCount", recordCount);
        final JSONObject data = new JSONObject();
        data.put("articles", pageArticles);
        data.put("pagination", pagination);
        context.renderData(data).renderCode(StatusCodes.SUCC).renderMsg("");
    }

    private static void initJson(final RequestContext context) {
        final JsonRenderer renderer = new JsonRenderer();
        renderer.setJSONObject(new JSONObject());
        context.setRenderer(renderer);
    }

    private boolean rateLimited(final RequestContext context) {
        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (!PUBLIC_COLUMNS_LIMITER.access(ip)) {
            context.renderCodeMsg(StatusCodes.ERR, "操作过于频繁，请稍后重试");
            return false;
        }
        return true;
    }

    private static int parsePage(final String value) {
        if (null == value || value.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (final NumberFormatException e) {
            return 1;
        }
    }

    private static int parseSize(final String value, final int defaultSize, final int maxSize) {
        if (null == value || value.isBlank()) {
            return defaultSize;
        }
        try {
            final int parsed = Integer.parseInt(value.trim());
            if (parsed < 1) {
                return defaultSize;
            }
            return Math.min(parsed, maxSize);
        } catch (final NumberFormatException e) {
            return defaultSize;
        }
    }

    private JSONObject getCurrentUser(final RequestContext context) {
        final JSONObject currentUser = (JSONObject) context.attr(User.USER);
        if (null != currentUser) {
            return currentUser;
        }

        return Sessions.getUser();
    }

    private void fillSideModules(final Map<String, Object> dataModel) {
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    private List<JSONObject> filterSelectedColumn(final List<JSONObject> columns, final String selectedColumnId) {
        if (null == selectedColumnId || selectedColumnId.isEmpty()) {
            return columns;
        }

        final List<JSONObject> ret = new ArrayList<>();
        for (final JSONObject column : columns) {
            final String columnId = column.optString(LongArticleColumn.COLUMN_ID, column.optString(Keys.OBJECT_ID));
            if (selectedColumnId.equals(columnId)) {
                ret.add(column);
            }
        }
        return ret;
    }

    private void renderSuccess(final RequestContext context, final JSONObject data) {
        final JSONObject response = new JSONObject();
        response.put(Keys.CODE, StatusCodes.SUCC);
        response.put(Common.DATA, data);
        context.renderJSON(response);
    }

    private void renderError(final RequestContext context, final String msg) {
        final JSONObject response = new JSONObject();
        response.put(Keys.CODE, StatusCodes.ERR);
        response.put(Keys.MSG, msg);
        response.put(Common.DATA, new JSONObject());
        context.renderJSON(response);
    }
}
