package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3ProfessionGuideQueryService;
import org.b3log.symphony.util.ApiV3Requests;

@Singleton
public class ApiV3ProfessionGuideProcessor {
    @Inject private ApiV3ProfessionGuideQueryService query;
    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ProfessionGuideProcessor.class), beans.getReference(ApiV3AuthMidware.class));
    }
    public static void registerRoutes(final ApiV3ProfessionGuideProcessor p, final ApiV3AuthMidware auth) {
        Dispatcher.get("/api/v3/professions/{professionId}/guides", p::guides, auth::profileRead);
    }
    public void guides(final RequestContext context) {
        ApiV3Requests.read(context, () -> query.guides(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID), context.pathVar("professionId")));
    }
}
