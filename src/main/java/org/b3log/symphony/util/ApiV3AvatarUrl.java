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

/** 校验并返回存储的头像 URL，保留签名查询参数，省略网络请求和页面展示转换。 */
public final class ApiV3AvatarUrl {
    private ApiV3AvatarUrl() { }
    public static String stored(final String raw) {
        final String value = raw == null ? "" : raw.trim();
        if (value.length() > 2048 || value.indexOf('\\') >= 0 || value.codePoints().anyMatch(Character::isISOControl))
            return "";
        try {
            final URI uri = new URI(value);
            if (value.startsWith("/") && !value.startsWith("//") && uri.getRawAuthority() == null)
                return value;
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                return "";
            return uri.getRawAuthority() != null && !uri.getRawAuthority().isBlank() && uri.getUserInfo() == null ? value : "";
        } catch (final Exception ignored) {
            return "";
        }
    }
}
