package org.b3log.symphony.util;

import org.json.JSONObject;
import java.math.BigDecimal;

/** Public state never serializes the permanent-stick Long.MAX_VALUE sentinel. */
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
