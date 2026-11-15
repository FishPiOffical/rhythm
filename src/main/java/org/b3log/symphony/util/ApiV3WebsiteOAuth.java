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

import java.net.URI;

/** 管理后台的第一方 OAuth 回调，仅允许固定路径和可信网站来源。 */
public final class ApiV3WebsiteOAuth {
    private static final String CALLBACK = "/manage/oauth/callback";
    private static final String PRODUCTION_ORIGIN = "https://new.fishpi.cn";

    private ApiV3WebsiteOAuth() { }

    public static boolean allowedRedirect(final String redirect, final boolean development) {
        if ((PRODUCTION_ORIGIN + CALLBACK).equals(redirect)) return !development;
        if (!development || redirect == null || !redirect.endsWith(CALLBACK)) return false;
        final String origin = redirect.substring(0, redirect.length() - CALLBACK.length());
        if (!origin.matches("http://(?:127[.]0[.]0[.]1|localhost):[1-9][0-9]{0,4}")) return false;
        final int port = URI.create(origin).getPort();
        return port == 5173 || (port <= 65535 && origin.equals(System.getProperty("rhythm.dev.websiteOrigin")));
    }

    public static boolean matchesOrigin(final String redirect, final String origin, final boolean development) {
        return origin != null && allowedRedirect(redirect, development) && redirect.equals(origin + CALLBACK);
    }
}
