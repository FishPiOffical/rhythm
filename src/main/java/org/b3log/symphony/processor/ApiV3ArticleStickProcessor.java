package org.b3log.symphony.processor;

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.*;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.service.ApiV3ArticleStickService;
import org.b3log.symphony.util.*;
import java.util.Set;

@Singleton
public class ApiV3ArticleStickProcessor {
    @Inject private ApiV3ArticleStickService sticks;
    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ArticleStickProcessor.class), beans.getReference(ApiV3AuthMidware.class), beans.getReference(ApiV3WriteMidware.class));
    }
    public static void registerRoutes(ApiV3ArticleStickProcessor processor, ApiV3AuthMidware auth, ApiV3WriteMidware writes) {
        Dispatcher.post("/api/v3/articles/{articleId}/stick", processor::stick, auth::contentWrite, auth::activityWrite, writes::handle);
    }
    public void stick(RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true);
            return sticks.stick(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "articleId"), ApiV3WriteInputs.idempotencyKey(context));
        });
    }
}
