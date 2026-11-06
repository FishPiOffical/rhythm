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

import org.b3log.latke.http.Cookie;
import org.b3log.latke.http.RequestContext;

/** Cookie construction uses the project's actual Latke String-based SameSite API. */
public final class ApiV3RefreshCookies {
    public static final String NAME = "fp_v3_refresh";
    private ApiV3RefreshCookies() { }
    /** Latke prepopulates response cookies from request headers without their original security attributes. */
    public static void discardFrameworkEcho(final RequestContext context) {
        context.getResponse().getCookies().removeIf(cookie -> NAME.equals(cookie.getName()));
    }

    public static Cookie create(final String value, final long seconds) {
        final Cookie cookie = new Cookie(NAME, value);
        cookie.setPath("/api/v3/auth");
        cookie.setMaxAge(ApiV3TokenUtil.cookieMaxAgeSeconds(seconds));
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setSameSite("Strict");
        return cookie;
    }
}
