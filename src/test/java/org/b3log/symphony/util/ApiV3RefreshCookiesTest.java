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

import org.testng.Assert;

public class ApiV3RefreshCookiesTest {
    public static void main(final String[] args) {
        for (final long seconds : new long[]{0, 60, Long.MAX_VALUE}) {
            final var cookie = ApiV3RefreshCookies.create(seconds == 0 ? "" : "opaque", seconds);
            Assert.assertEquals(cookie.getName(), "fp_v3_refresh");
            Assert.assertEquals(cookie.getValue(), seconds == 0 ? "" : "opaque");
            Assert.assertEquals(cookie.getPath(), "/api/v3/auth");
            Assert.assertTrue(cookie.isHttpOnly()); Assert.assertTrue(cookie.isSecure());
            Assert.assertEquals(cookie.getSameSite(), "Strict"); Assert.assertNull(cookie.getDomain());
            Assert.assertEquals(cookie.getMaxAge(), (long) ApiV3TokenUtil.cookieMaxAgeSeconds(seconds));
        }
        System.out.println("PASS actual Latke refresh-cookie API, issue/clear flags, host-only domain and bounded max age");
    }
}
