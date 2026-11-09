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
import java.math.BigDecimal;

/** 公开置顶状态不直接输出用于标记永久置顶的 Long.MAX_VALUE。 */
public final class ApiV3ArticleStickState {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private ApiV3ArticleStickState() { }
    public static long threshold(long now, long duration) {
        if (now <= 0 || duration <= 0) throw unavailable();
        return duration >= now ? 1 : now - duration;
    }
    public static JSONObject metadata(Object stored, long now, long duration) {
        final long timestamp;
        try {
            if (stored == null || stored == JSONObject.NULL) timestamp = 0;
            else if (stored instanceof Number number) timestamp = new BigDecimal(number.toString()).longValueExact();
            else throw unavailable();
            if (timestamp < 0) throw unavailable();
            if (timestamp == 0) return new JSONObject().put("active", false).put("permanent", false).put("expiresAt", 0);
            if (timestamp == Long.MAX_VALUE) return new JSONObject().put("active", true).put("permanent", true).put("expiresAt", JSONObject.NULL);
            threshold(now, duration);
            final long expiry = Math.addExact(timestamp, duration);
            if (expiry > MAX_SAFE_INTEGER) throw unavailable();
            return new JSONObject().put("active", expiry >= now).put("permanent", false).put("expiresAt", expiry);
        } catch (ArithmeticException | NumberFormatException error) { throw unavailable(); }
    }
    private static ApiV3Exception unavailable() { return new ApiV3Exception(503, "temporarily_unavailable", "文章置顶状态暂不可用"); }
}
