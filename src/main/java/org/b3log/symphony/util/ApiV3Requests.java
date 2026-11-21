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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.b3log.latke.http.RequestContext;
import org.b3log.symphony.processor.ApiV3Processor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;

/** Shared v3 input parsing and read response boundary. */
public final class ApiV3Requests {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3Requests.class);
    private ApiV3Requests() { }

    @FunctionalInterface
    public interface ReadOperation { Object run() throws Exception; }

    public static final class ApiException extends ApiV3Exception {
        public ApiException(final int status, final String code, final String message) {
            super(status, code, message);
        }
    }

    public static void read(final RequestContext context, final ReadOperation operation) {
        respond(context, operation);
    }

    public static void respond(final RequestContext context, final ReadOperation operation) {
        context.getResponse().setHeader("Cache-Control", "no-store");
        context.getResponse().setHeader("Vary", "Authorization");
        final String previousRequestId = ThreadContext.get("apiV3RequestId");
        ThreadContext.put("apiV3RequestId", ApiV3Processor.requestId(context));
        try {
            ApiV3Processor.success(context, operation.run());
        } catch (final ApiV3Exception e) {
            if (!"GET".equals(context.method())) LOGGER.info("api_v3_write method={} result={} requestId={}",
                    context.method(), e.code, ThreadContext.get("apiV3RequestId"));
            ApiV3Processor.error(context, e.status, e.code, e.getMessage());
        } catch (final Exception e) {
            LOGGER.error("API v3 request failed", e);
            ApiV3Processor.error(context, 500, "internal_error", "服务暂不可用");
        } finally {
            if (null == previousRequestId) ThreadContext.remove("apiV3RequestId");
            else ThreadContext.put("apiV3RequestId", previousRequestId);
        }
    }

    public static JSONObject withCommittedInteraction(final JSONObject receipt, final ReadOperation read) {
        try {
            receipt.put("interaction", read.run());
        } catch (final Exception e) {
            if (e instanceof ApiV3Exception failure && (failure.status == 403 || failure.status == 404)) {
                receipt.put("interaction", JSONObject.NULL);
            } else {
                // 写入已经提交，状态补查失败时保留成功回执，由客户端单独重读。
                LOGGER.warn("已提交的互动状态补查失败 requestId={}", ThreadContext.get("apiV3RequestId"), e);
            }
        }
        return receipt;
    }

    public static int page(final RequestContext context) { return number(context, "p", 1, Integer.MAX_VALUE / 50); }
    public static int size(final RequestContext context) { return number(context, "size", 20, 50); }

    public static int number(final RequestContext context, final String key, final int fallback, final int max) {
        final String raw = context.param(key);
        if (null == raw) return fallback;
        final String value = raw.trim();
        if (!value.matches("[0-9]{1,10}")) throw invalid();
        try {
            final int result = Integer.parseInt(value);
            if (result < 1 || result > max) throw invalid();
            return result;
        } catch (final NumberFormatException e) { throw invalid(); }
    }

    public static String choice(final RequestContext context, final String key, final String fallback, final Set<String> allowed) {
        final String raw = context.param(key);
        if (null == raw) return fallback;
        if (!allowed.contains(raw.trim())) throw invalid();
        return raw.trim();
    }

    public static String id(final RequestContext context, final String key) {
        final String id = StringUtils.trimToEmpty(context.pathVar(key));
        if (!id.matches("[0-9]{1,32}")) throw invalid();
        return id;
    }

    public static String text(final String raw, final int max, final boolean required) {
        final String value = StringUtils.trimToEmpty(raw);
        if ((required && value.isEmpty()) || value.length() > max || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return value;
    }

    public static JSONObject viewer(final RequestContext context) {
        final JSONObject identity = (JSONObject) context.attr(ApiV3Processor.IDENTITY);
        return null == identity ? null : identity.optJSONObject("user");
    }

    public static JSONObject pagination(final JSONArray list, final int p, final int size, final long total) {
        return new JSONObject().put("list", list).put("pagination", new JSONObject().put("p", p).put("size", size)
                .put("total", total).put("pageCount", (total + size - 1) / size));
    }

    public static ApiException invalid() { return new ApiException(422, "invalid_request", "请求参数不合法"); }
    public static ApiException notFound() { return new ApiException(404, "not_found", "资源不存在或不可访问"); }
    public static ApiException forbidden() { return new ApiException(403, "forbidden", "无权访问"); }
}
