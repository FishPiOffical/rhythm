package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ActivityMgmtService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import java.util.Set;

@Singleton
public class ApiV3ActivityProcessor {
    @Inject private ApiV3ActivityMgmtService management;
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3ActivityProcessor p = beans.getReference(ApiV3ActivityProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.get("/api/v3/me/activity/yesterday-reward", p::status, auth::activityRead);
        Dispatcher.post("/api/v3/me/activity/yesterday-reward", p::claim, auth::activityWrite, writes::handle);
    }
    public void status(final RequestContext c) { ApiV3Requests.read(c, () -> management.status(actor(c))); }
    public void claim(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final var body = ApiV3WriteInputs.body(c, Set.of("expectedDate"), true);
            final String expectedDate = body.has("expectedDate") ? ApiV3WriteInputs.string(body, "expectedDate", 8, false) : null;
            return management.claim(actor(c), ApiV3WriteInputs.idempotencyKey(c), expectedDate);
        });
    }
    private static String actor(final RequestContext c) { return ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID); }
}
