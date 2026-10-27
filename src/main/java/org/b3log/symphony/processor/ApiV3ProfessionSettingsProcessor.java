package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ProfessionSettingsService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3ProfessionSettingsInputs;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;
import java.util.Set;

/** Explicit skip and privacy replacements; no owner, version or arbitrary onboarding state input. */
@Singleton
public class ApiV3ProfessionSettingsProcessor {
    private static final int MAX_BODY_BYTES = 4096;
    @Inject private ApiV3ProfessionSettingsService service;
    public static void register() {
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3ProfessionSettingsProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.put("/api/v3/me/professions/onboarding", p::onboarding, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/professions/privacy", p::privacy, auth::settingsWrite, writes::handle);
    }
    private static JSONObject body(final RequestContext context, final Set<String> fields) {
        final var bytes = context.getRequest().getBytes();
        if (bytes != null && bytes.length > MAX_BODY_BYTES) throw new ApiV3Exception(413, "body_too_large", "请求体过大");
        return ApiV3WriteInputs.body(context, fields, false);
    }
    public void onboarding(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3ProfessionSettingsInputs.onboarding(body(context, Set.of("onboardingState")));
            return service.skip(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID), ApiV3WriteInputs.idempotencyKey(context));
        });
    }
    public void privacy(final RequestContext context) {
        ApiV3Requests.respond(context, () -> service.privacy(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID),
                body(context, Set.of("preset", "moduleVisibility")), ApiV3WriteInputs.idempotencyKey(context)));
    }
}
