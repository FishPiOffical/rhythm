package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3ReportQueryService;
import org.b3log.symphony.util.*;
import org.b3log.symphony.service.ApiV3ReportPublicationService;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import java.util.Set;

@Singleton
public class ApiV3ReportProcessor {
    @Inject private ApiV3ReportQueryService query;
    @Inject private ApiV3ReportPublicationService publication;
    public static void register() {
        final var beans=BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ReportProcessor.class),beans.getReference(ApiV3AuthMidware.class),beans.getReference(ApiV3WriteMidware.class));
    }
    static void registerRoutes(final ApiV3ReportProcessor p, final ApiV3AuthMidware auth,final ApiV3WriteMidware writes) {
        Dispatcher.get("/api/v3/report/options",p::options,auth::contentRead);
        Dispatcher.post("/api/v3/reports",p::publish,auth::contentWrite,writes::handle);
    }
    public void publish(final RequestContext context) {
        ApiV3Requests.respond(context,()->{
            final byte[] bytes=context.getRequest().getBytes();
            if(bytes!=null&&bytes.length>65536)throw new ApiV3Exception(413,"body_too_large","请求体过大");
            return publication.publish(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID),
                    ApiV3WriteInputs.body(context,Set.of("targetType","targetId","reason","memo"),false),
                    ApiV3WriteInputs.idempotencyKey(context));
        });
    }
    public void options(final RequestContext context) {
        ApiV3Requests.read(context,()->query.options(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID)));
    }
}
