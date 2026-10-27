package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3ProfessionDetailQueryService;
import org.b3log.symphony.util.ApiV3Requests;

@Singleton
public class ApiV3ProfessionDetailProcessor {
    @Inject private ApiV3ProfessionDetailQueryService query;
    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ProfessionDetailProcessor.class), beans.getReference(ApiV3AuthMidware.class));
    }
    public static void registerRoutes(final ApiV3ProfessionDetailProcessor p, final ApiV3AuthMidware auth) {
        Dispatcher.get("/api/v3/me/professions/{professionId}", p::detail, auth::profileRead);
        Dispatcher.get("/api/v3/me/professions/{professionId}/records", p::records, auth::profileRead);
    }
    public void detail(final RequestContext context) {
        ApiV3Requests.read(context, () -> query.detail(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID), context.pathVar("professionId")));
    }
    public void records(final RequestContext context) {
        ApiV3Requests.read(context, () -> query.records(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID), context.pathVar("professionId"),
                ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }
}
