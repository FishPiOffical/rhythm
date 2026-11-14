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
package org.b3log.symphony.util;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.model.User;
import org.b3log.symphony.processor.ApiProcessor;
import org.json.JSONObject;

/** 页面写入要求同源；经验证的 apiKey 客户端沿用原调用方式。 */
public final class RepeaterWriteSecurity {
    private RepeaterWriteSecurity() { }

    public static boolean allowed(final RequestContext context) {
        final Object current = context.attr(User.USER);
        if (!(current instanceof JSONObject actor)) return false;
        final JSONObject body = context.requestJSON();
        final String[] keys = {context.param("apiKey"), body == null ? "" : body.optString("apiKey")};
        for (final String key : keys) {
            if (key == null || key.isBlank()) continue;
            try {
                final JSONObject user = ApiProcessor.getUserByKey(key);
                if (user != null && actor.getString("oId").equals(user.optString("oId"))) return true;
            } catch (final NullPointerException ignored) {
                // 无效密钥不能绕过页面来源校验。
            }
        }
        return ApiV3ConsentSecurity.sameOrigin(Latkes.getServePath(), context.header("Origin"),
                context.header("Referer"), context.header("Sec-Fetch-Site"));
    }
}
