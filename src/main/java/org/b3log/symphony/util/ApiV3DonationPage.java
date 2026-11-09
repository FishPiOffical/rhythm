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
import org.json.JSONObject;

/** 返回配置站点的捐助页面链接，不创建支付单或浏览器会话。 */
public final class ApiV3DonationPage {
    private ApiV3DonationPage() { }

    public static JSONObject link(final String configuredSite, final boolean development) {
        try {
            if (configuredSite == null || configuredSite.isEmpty() || configuredSite.length() > 2048
                    || configuredSite.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
                throw new IllegalArgumentException();
            }
            final URI base = URI.create(configuredSite);
            final String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
            if (base.getHost() == null || base.getRawUserInfo() != null || base.getRawQuery() != null
                    || base.getRawFragment() != null || base.getPort() == 0 || base.getPort() > 65535
                    || base.getRawAuthority().endsWith(":") || !base.normalize().equals(base)
                    || base.getRawPath().contains("//")
                    || !(scheme.equals("https") || scheme.equals("http") && development
                    && Set.of("localhost", "127.0.0.1", "[::1]").contains(base.getHost().toLowerCase(Locale.ROOT)))) {
                throw new IllegalArgumentException();
            }
            final String site = configuredSite.endsWith("/") ? configuredSite.substring(0, configuredSite.length() - 1) : configuredSite;
            return new JSONObject().put("url", site + "/charge/point");
        } catch (final IllegalArgumentException e) {
            throw new ApiV3Exception(503, "service_unavailable", "捐助页面暂不可用");
        }
    }
}
