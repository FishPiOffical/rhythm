package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3EmojiGroupService;
import org.b3log.symphony.util.*;
import java.util.Set;

@Singleton
public class ApiV3EmojiGroupProcessor {
    @Inject private ApiV3EmojiGroupService service;
    public static void register() {
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3EmojiGroupProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/me/settings/emoji-groups", p::create, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/settings/emoji-groups/{groupId}", p::update, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/me/settings/emoji-groups/{groupId}", p::delete, auth::settingsWrite, writes::handle);
    }
    public void create(final RequestContext context) {
        ApiV3Requests.respond(context, () -> service.create(actor(context), body(context, false), ApiV3WriteInputs.idempotencyKey(context)));
    }
    public void update(final RequestContext context) {
        ApiV3Requests.respond(context, () -> service.update(actor(context), ApiV3Requests.id(context, "groupId"), body(context, false), ApiV3WriteInputs.idempotencyKey(context)));
    }
    public void delete(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            body(context, true);
            return service.delete(actor(context), ApiV3Requests.id(context, "groupId"), ApiV3WriteInputs.idempotencyKey(context));
        });
    }
    private static String actor(final RequestContext context) { return ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID); }
    private static org.json.JSONObject body(final RequestContext context, final boolean empty) {
        final byte[] bytes = context.getRequest().getBytes();
        if (bytes != null && bytes.length > 16384) throw new ApiV3Exception(413, "body_too_large", "请求体过大");
        return ApiV3WriteInputs.body(context, empty ? Set.of() : Set.of("name", "sort"), empty);
    }
}
