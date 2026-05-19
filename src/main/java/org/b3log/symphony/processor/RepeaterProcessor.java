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
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.RepeaterMgmtService;
import org.b3log.symphony.service.RepeaterQueryService;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Sessions;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 复读机转录站接口。
 */
@Singleton
public class RepeaterProcessor {

    private static final int HOME_FETCH_SIZE = 12;

    @Inject
    private RepeaterQueryService repeaterQueryService;

    @Inject
    private RepeaterMgmtService repeaterMgmtService;

    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final RepeaterProcessor processor = beanManager.getReference(RepeaterProcessor.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final AnonymousViewCheckMidware anonymousView = beanManager.getReference(AnonymousViewCheckMidware.class);

        Dispatcher.get("/api/repeater/items", processor::list, anonymousView::handle);
        Dispatcher.get("/api/repeater/next", processor::next, anonymousView::handle);
        Dispatcher.post("/api/repeater", processor::create, loginCheck::handle);
        Dispatcher.post("/api/repeater/{id}/like", processor::like, loginCheck::handle);
    }

    public void list(final RequestContext context) {
        try {
            final List<JSONObject> items = repeaterQueryService.list(
                    context.param(RepeaterContent.TYPE), getUserIdOrBlank(context), HOME_FETCH_SIZE);
            renderSuccess(context, new JSONObject().put("items", new JSONArray(items)));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void next(final RequestContext context) {
        try {
            final JSONObject item = repeaterQueryService.next(
                    context.param(RepeaterContent.TYPE), getUserIdOrBlank(context), context.param("excludeId"));
            renderSuccess(context, new JSONObject().put("item", null == item ? new JSONObject() : item));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void create(final RequestContext context) {
        try {
            final JSONObject item = repeaterMgmtService.createUserContent(getUserId(context), context.requestJSON());
            renderSuccess(context, new JSONObject().put("item", item));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void like(final RequestContext context) {
        try {
            final JSONObject result = repeaterMgmtService.toggleLike(getUserId(context), context.pathVar("id"));
            renderSuccess(context, result);
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    private String getUserId(final RequestContext context) throws ServiceException {
        final JSONObject currentUser = (JSONObject) context.attr(User.USER);
        if (null == currentUser) {
            throw new ServiceException("未登录");
        }
        return currentUser.optString(Keys.OBJECT_ID);
    }

    private String getUserIdOrBlank(final RequestContext context) {
        final JSONObject currentUser = (JSONObject) context.attr(User.USER);
        if (null != currentUser) {
            return currentUser.optString(Keys.OBJECT_ID);
        }

        final JSONObject sessionUser = Sessions.getUser();
        if (null != sessionUser) {
            return sessionUser.optString(Keys.OBJECT_ID);
        }

        try {
            return ApiProcessor.getUserByKey(context.param("apiKey")).optString(Keys.OBJECT_ID);
        } catch (final NullPointerException e) {
            return "";
        }
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
