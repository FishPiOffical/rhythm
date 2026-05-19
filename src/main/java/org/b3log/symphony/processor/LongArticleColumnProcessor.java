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

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.ColumnCoverMgmtService;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.LongArticleColumnQueryService;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Sessions;
import org.json.JSONObject;

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

    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LongArticleColumnProcessor processor = beanManager.getReference(LongArticleColumnProcessor.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);

        Dispatcher.post("/api/columns/{columnId}/cover", processor::updateCover, loginCheck::handle);
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
