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

import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.util.Set;

/**
 * 新版勋章接口鉴权。
 */
@Singleton
public class MedalApiAuth {

    private static final Set<String> READ_ONLY_APIS = Set.of(
            "/api/medal/admin/list",
            "/api/medal/admin/search",
            "/api/medal/admin/detail",
            "/api/medal/admin/owners",
            "/api/medal/admin/user-medals",
            "/api/medal/admin/holds");

    @Inject
    private UserRepository userRepository;

    JSONObject requireAdmin(final RequestContext context) {
        final JSONObject currentUser = getCurrentUser(context);
        if (null == currentUser) {
            context.sendError(401);
            context.abort();
            return null;
        }
        if (!Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE))) {
            context.sendError(403);
            context.abort();
            return null;
        }
        return currentUser;
    }

    JSONObject requireLogin(final RequestContext context) {
        final JSONObject currentUser = getCurrentUser(context);
        if (null == currentUser) {
            context.sendError(401);
            context.abort();
            return null;
        }
        return currentUser;
    }

    void handleAdmin(final RequestContext context) {
        final JSONObject currentUser = requireAdmin(context);
        if (null == currentUser) {
            return;
        }
        context.attr(User.USER, currentUser);
        context.handle();
    }

    private JSONObject getCurrentUser(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        currentUser = userByApiKey(context.param("apiKey"), currentUser);
        final JSONObject request = requestJSON(context);
        currentUser = userByApiKey(request.optString("apiKey"), currentUser);
        return userByGoldFinger(context.requestURI(), request.optString("goldFingerKey"), currentUser);
    }

    private JSONObject userByApiKey(final String apiKey, final JSONObject currentUser) {
        if (null == apiKey || apiKey.isBlank()) {
            return currentUser;
        }
        try {
            return ApiProcessor.getUserByKey(apiKey);
        } catch (final NullPointerException e) {
            return currentUser;
        }
    }

    private JSONObject userByGoldFinger(final String requestURI, final String key,
                                        final JSONObject currentUser) {
        if (key.isBlank()) {
            return currentUser;
        }
        final String configKey = READ_ONLY_APIS.contains(requestURI)
                ? "gold.finger.medal-admin-read" : "gold.finger.medal-admin-write";
        if (!key.equals(Symphonys.get(configKey))) {
            return currentUser;
        }
        try {
            return userRepository.getByName("admin");
        } catch (final RepositoryException e) {
            return currentUser;
        }
    }

    private JSONObject requestJSON(final RequestContext context) {
        final JSONObject request = context.requestJSON();
        return null == request ? new JSONObject() : request;
    }
}
