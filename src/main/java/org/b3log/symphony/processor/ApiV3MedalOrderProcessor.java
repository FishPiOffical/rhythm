package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3MedalOrderService;
import org.b3log.symphony.util.*;
import java.util.Set;

@Singleton
public class ApiV3MedalOrderProcessor {
    @Inject private ApiV3MedalOrderService management;
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3MedalOrderProcessor p = beans.getReference(ApiV3MedalOrderProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/me/medals/{medalId}/move", p::move, auth::settingsWrite, writes::handle);
    }
    public void move(final RequestContext c) {
        ApiV3Requests.respond(c, () -> management.move(ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID),
                ApiV3MedalDisplay.medalId(c.pathVar("medalId")),
                ApiV3MedalOrderInputs.direction(ApiV3WriteInputs.body(c, Set.of("direction"), false)), ApiV3WriteInputs.idempotencyKey(c)));
    }
}
