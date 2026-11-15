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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.util;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.RequestContext;

/** 业务响应与前置验证码拒绝共用同一跨域响应策略；不授予业务访问权限。 */
public final class ApiV3Cors {
    private ApiV3Cors() {
    }

    public static boolean apply(final RequestContext context) {
        final String path = context.requestURI();
        final String contextPath = Latkes.getContextPath();
        final String origin = context.header("Origin");
        if (origin == null || !(path.startsWith(contextPath + "/api/v3/")
                || path.equals(contextPath + "/validateCaptcha"))) {
            return false;
        }
        context.setHeader("Access-Control-Allow-Origin", origin);
        context.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        context.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Content-Length, X-Request-Id, If-None-Match, If-Match, Idempotency-Key");
        context.setHeader("Access-Control-Allow-Credentials", "true");
        context.setHeader("Access-Control-Expose-Headers", "X-Request-Id");
        context.setHeader("Access-Control-Max-Age", "86400");
        context.setHeader("Vary", "Origin");
        return true;
    }
}
