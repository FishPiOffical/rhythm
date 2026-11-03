package org.b3log.symphony.processor;

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.ApiV3GameSaveRawBody;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.LongArticleReadCaptureService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Json;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;

/** Explicit post-display registration; all identity, window and counts are server-owned. */
@Singleton
public class ApiV3ArticleReadProcessor {
    @Inject private LongArticleReadCaptureService reads;
    public static void register() {
        final var beans = BeanManager.getInstance();
        final var processor = beans.getReference(ApiV3ArticleReadProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        registerRoutes(processor, auth, writes);
    }
    public static void registerRoutes(final ApiV3ArticleReadProcessor processor, final ApiV3AuthMidware auth, final ApiV3WriteMidware writes) {
        Dispatcher.post("/api/v3/articles/{articleId}/reads", processor::record, auth::contentRead, auth::activityWrite, writes::handle);
    }
    private static void validateBody(final RequestContext context) {
        final byte[] bytes;
        try { bytes = ApiV3GameSaveRawBody.copy(context.getRequest(), 128); }
        catch (final ApiV3GameSaveRawBody.TooLarge oversized) { throw ApiV3Requests.invalid(); }
        if (bytes.length == 0) return;
        final String type = context.header("Content-Type");
        if (type == null || !"application/json".equalsIgnoreCase(type.split(";", 2)[0].trim()))
            throw new ApiV3Exception(415, "unsupported_media_type", "请求体必须使用 application/json");
        final JSONObject body;
        try { body = ApiV3Json.parse(bytes); }
        catch (final IllegalArgumentException malformed) { throw new ApiV3Exception(400, "invalid_json", "JSON 请求体不合法"); }
        if (!body.isEmpty()) throw ApiV3Requests.invalid();
    }

    public void record(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            validateBody(context);
            final String id = ApiV3Requests.id(context, "articleId");
            final boolean recorded = reads.recordCurrent(id, ApiV3Requests.viewer(context).optString("oId"));
            return new JSONObject().put("articleId", id).put("recorded", recorded);
        });
    }
}
