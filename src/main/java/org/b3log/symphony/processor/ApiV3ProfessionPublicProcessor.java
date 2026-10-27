package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.service.ApiV3ProfessionPublicQueryService;
import org.b3log.symphony.util.ApiV3Requests;

/** Viewer-explicit public profession metadata; never derives identity from page credentials. */
@Singleton
public class ApiV3ProfessionPublicProcessor {
    @Inject private ApiV3ProfessionPublicQueryService query;
    public static void register() {
        final BeanManager beans=BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ProfessionPublicProcessor.class), beans.getReference(ApiV3AuthMidware.class),
                beans.getReference(AnonymousViewCheckMidware.class));
    }
    public static void registerRoutes(final ApiV3ProfessionPublicProcessor p, final ApiV3AuthMidware auth,
                                      final AnonymousViewCheckMidware anonymous) {
        Dispatcher.get("/api/v3/users/{userName}/professions",p::profile,auth::optionalProfile,anonymous::handle);
    }
    public void profile(final RequestContext c) {
        ApiV3Requests.read(c,()->query.profile(ApiV3Requests.text(c.pathVar("userName"),64,true),
                ApiV3Requests.viewer(c)==null?"":ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID)));
    }
}
