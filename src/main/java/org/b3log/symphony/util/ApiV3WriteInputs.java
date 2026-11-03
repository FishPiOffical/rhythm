package org.b3log.symphony.util;

import org.b3log.latke.http.ApiV3GameSaveRawBody;
import org.b3log.latke.http.RequestContext;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** Typed v3 writes. Unknown fields, coercion and ambiguous JSON are rejected. */
public final class ApiV3WriteInputs {
    private ApiV3WriteInputs() { }

    public static JSONObject body(final RequestContext context, final Set<String> allowed, final boolean emptyAllowed) {
        return body(context, allowed, emptyAllowed, ApiV3Json.MAX_BODY_BYTES);
    }

    public static JSONObject body(final RequestContext context, final Set<String> allowed, final boolean emptyAllowed, final int maximumBytes) {
        if (maximumBytes < 0 || maximumBytes > ApiV3Json.MAX_BODY_BYTES) throw new IllegalArgumentException("body limit");
        final byte[] bytes;
        try {
            bytes = ApiV3GameSaveRawBody.copy(context.getRequest(), maximumBytes);
        } catch (final ApiV3GameSaveRawBody.TooLarge e) {
            throw new ApiV3Exception(413, "body_too_large", "请求体过大");
        }
        if (emptyAllowed && bytes.length == 0) return new JSONObject();
        final String type = context.header("Content-Type");
        if (null == type || !"application/json".equals(type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))) {
            throw new ApiV3Exception(415, "unsupported_media_type", "请求体必须使用 application/json");
        }
        final JSONObject body;
        try { body = ApiV3Json.parse(bytes); }
        catch (final IllegalArgumentException e) { throw new ApiV3Exception(400, "invalid_json", "JSON 请求体不合法"); }
        if (!allowed.containsAll(body.keySet())) throw invalid();
        return body;
    }

    public static String idempotencyKey(final RequestContext context) {
        final String key = context.header("Idempotency-Key");
        if (null == key || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw invalid();
        return key;
    }

    public static String string(final JSONObject body, final String key, final int max, final boolean trim) {
        if (!body.has(key)) return "";
        if (!(body.opt(key) instanceof String text)) throw invalid();
        final String result = trim ? text.trim() : text;
        if (result.length() > max || result.indexOf(0) >= 0) throw invalid();
        return result;
    }

    public static int integer(final JSONObject body, final String key, final int fallback, final int min, final int max) {
        if (!body.has(key)) return fallback;
        if (!(body.opt(key) instanceof Number number)) throw invalid();
        try {
            final int result = new BigDecimal(number.toString()).intValueExact();
            if (result < min || result > max) throw invalid();
            return result;
        } catch (final ArithmeticException | NumberFormatException e) { throw invalid(); }
    }

    public static boolean bool(final JSONObject body, final String key, final boolean fallback) {
        if (!body.has(key)) return fallback;
        if (!(body.opt(key) instanceof Boolean value)) throw invalid();
        return value;
    }

    public static ApiV3Exception invalid() { return new ApiV3Exception(422, "invalid_request", "请求字段不合法"); }
}
