package org.b3log.symphony.processor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.b3log.latke.Keys;
import org.b3log.latke.http.ApiV3GameSaveRawBody;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Json;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Read-only rendering of caller-supplied text. No target lookup, publication or shared Markdown cache. */
@Singleton
public class ApiV3MarkdownProcessor {
    public static final int MAX_CONTENT_LENGTH = 1024000;
    private static final Cache<String, AtomicInteger> COUNTS = Caffeine.newBuilder()
            .maximumSize(100000).expireAfterWrite(1, TimeUnit.MINUTES).build();
    private static final Semaphore RENDERS = new Semaphore(4);

    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3MarkdownProcessor.class), beans.getReference(ApiV3AuthMidware.class));
    }
    public static void registerRoutes(final ApiV3MarkdownProcessor processor, final ApiV3AuthMidware auth) {
        Dispatcher.post("/api/v3/markdown/preview", processor::preview, auth::contentRead);
    }
    public void preview(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final var viewer = ApiV3Requests.viewer(context);
            if (viewer == null) throw new ApiV3Exception(401, "unauthorized", "登录凭据无效");
            // Preview has its own budget; typing must not consume publication/write allowance.
            final int user = COUNTS.get("user:" + viewer.optString(Keys.OBJECT_ID), key -> new AtomicInteger()).incrementAndGet();
            final int ip = COUNTS.get("ip:" + Requests.getRemoteAddr(context.getRequest()), key -> new AtomicInteger()).incrementAndGet();
            if (user > 120 || ip > 600) {
                context.getResponse().setHeader("Retry-After", "60");
                throw new ApiV3Exception(429, "too_many_requests", "请求过于频繁，请稍后再试");
            }
            final String content = input(context);
            if (!RENDERS.tryAcquire()) throw new ApiV3Exception(503, "render_busy", "预览暂不可用，请稍后重试");
            try { return new JSONObject().put("html", Markdowns.toPrivateHTML(content)); }
            finally { RENDERS.release(); }
        });
    }
    public static String input(final RequestContext context) {
        final String type = context.header("Content-Type");
        if (type == null || !"application/json".equals(type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT)))
            throw new ApiV3Exception(415, "unsupported_media_type", "请求体必须使用 application/json");
        final byte[] bytes;
        try { bytes = ApiV3GameSaveRawBody.copy(context.getRequest(), ApiV3Json.MAX_BODY_BYTES); }
        catch (final ApiV3GameSaveRawBody.TooLarge e) { throw new ApiV3Exception(413, "body_too_large", "请求体过大"); }
        final JSONObject body;
        try { body = ApiV3Json.parse(bytes); }
        catch (final IllegalArgumentException e) { throw new ApiV3Exception(400, "invalid_json", "JSON 请求体不合法"); }
        if (!body.keySet().equals(Set.of("content")) || !(body.opt("content") instanceof String content)
                || content.length() > MAX_CONTENT_LENGTH) throw ApiV3WriteInputs.invalid();
        for (int i = 0; i < content.length(); i++) {
            final char c = content.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') throw ApiV3WriteInputs.invalid();
        }
        return content;
    }
}
