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
import java.util.Locale;
import java.util.Set;

/** 仅校验回调地址格式，不查询 DNS 或访问地址。调用方还须确认地址与有效注册项完全一致。 */
public final class ApiV3OAuthRedirects {
    private ApiV3OAuthRedirects() { }

    public static boolean valid(final String value, final boolean development) {
        if (value == null || value.isEmpty() || value.length() > 2048
                || value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)))
            return false;
        try {
            ApiV3Json.canonical(value);
            final URI uri = URI.create(value);
            if (uri.toASCIIString().length() > 2048 || !uri.isAbsolute() || uri.getRawFragment() != null
                    || uri.getRawUserInfo() != null || ApiV3OAuthCallbacks.hasResponseParameters(uri))
                return false;
            final String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if ("https".equals(scheme) || "http".equals(scheme)) {
                if (uri.getHost() == null || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getRawAuthority().endsWith(":"))
                    return false;
                return "https".equals(scheme) || development
                        && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost().toLowerCase(Locale.ROOT));
            }
            // 原生应用的私有 scheme 采用反向域名，路径以单个斜杠起始，参见 RFC 8252。
            return scheme.matches("[a-z][a-z0-9-]*(?:\\.[a-z0-9][a-z0-9-]*)+") && !uri.isOpaque() && uri.getRawAuthority() == null
                    && uri.getRawPath() != null && uri.getRawPath().startsWith("/") && !uri.getRawPath().startsWith("//");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
