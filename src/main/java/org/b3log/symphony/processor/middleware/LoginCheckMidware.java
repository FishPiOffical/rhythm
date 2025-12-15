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
package org.b3log.symphony.processor.middleware;

import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.util.Sessions;
import org.json.JSONObject;
/**
 * Login check. Gets user from request attribute named "user" if logged in.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 0.2.5
 */
@Singleton
public class LoginCheckMidware {
    public void handle(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        if (null == currentUser) {
            if (context.getRequest().getRequestURI().equals("/gen")) {
                context.getResponse().setContentType("image/svg+xml");
                context.getResponse().setHeader("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate");
                context.getResponse().setHeader("Pragma", "no-cache");
                context.getResponse().setHeader("Expires", "0");
                context.getResponse().sendBytes("<svg width=\"0\" height=\"0\" xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes());
                return;
            }
            context.sendError(401);
            context.abort();
            return;
        }

        final int point = currentUser.optInt(UserExt.USER_POINT);
        final int appRole = currentUser.optInt(UserExt.USER_APP_ROLE);
        if (UserExt.USER_APP_ROLE_C_HACKER == appRole) {
            currentUser.put(UserExt.USER_T_POINT_HEX, Integer.toHexString(point));
        } else {
            currentUser.put(UserExt.USER_T_POINT_CC, UserExt.toCCString(point));
        }

        context.attr(User.USER, currentUser);
        context.handle();
    }
}
