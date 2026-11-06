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

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/** Same published content length and HTML stripping rules as the legacy POST. */
public final class ApiV3BreezemoonInputs {
    private ApiV3BreezemoonInputs() { }
    public static String content(final JSONObject body) {
        final String value = ApiV3WriteInputs.string(body, "content", 512, true);
        if (value.isEmpty() || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) throw ApiV3WriteInputs.invalid();
        return value;
    }
    /** Legacy PUT keeps source markup; only the rendered HTML may be inserted into a page. */
    public static String editable(final String filtered) {
        return content(new JSONObject().put("content", filtered));
    }
    public static String publishable(final String filtered) {
        if (null == filtered || filtered.trim().isEmpty() || filtered.trim().length() > 512) throw ApiV3WriteInputs.invalid();
        final String value = Jsoup.clean(filtered, Safelist.none()).trim();
        if (value.isEmpty()) throw ApiV3WriteInputs.invalid();
        return value;
    }
}
