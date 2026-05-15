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
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.ArticleDraftMgmtService;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Article draft processor.
 */
@Singleton
public class ArticleDraftProcessor {

    @Inject
    private ArticleDraftMgmtService articleDraftMgmtService;

    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final ArticleDraftProcessor processor = beanManager.getReference(ArticleDraftProcessor.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);

        Dispatcher.get("/api/article-drafts", processor::list, loginCheck::handle);
        Dispatcher.post("/api/article-drafts", processor::save, loginCheck::handle);
        Dispatcher.get("/api/article-drafts/{id}", processor::get, loginCheck::handle);
        Dispatcher.delete("/api/article-drafts/{id}", processor::remove, loginCheck::handle);
    }

    public void list(final RequestContext context) {
        try {
            final List<JSONObject> drafts = articleDraftMgmtService.listDrafts(getUserId(context));
            renderSuccess(context, new JSONObject().put("drafts", new JSONArray(drafts)));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void save(final RequestContext context) {
        try {
            final JSONObject draft = articleDraftMgmtService.saveDraft(getUserId(context), context.requestJSON());
            renderSuccess(context, new JSONObject().put("draft", draft));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void get(final RequestContext context) {
        try {
            final JSONObject draft = articleDraftMgmtService.getDraft(getUserId(context), context.pathVar("id"));
            renderSuccess(context, new JSONObject().put("draft", draft));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void remove(final RequestContext context) {
        try {
            final String draftId = context.pathVar("id");
            articleDraftMgmtService.removeDraft(getUserId(context), draftId);
            renderSuccess(context, new JSONObject().put("id", draftId));
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
