package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3MembershipOpeningService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MembershipOpeningInputs;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import java.util.Set;

@Singleton
public class ApiV3MembershipOpeningProcessor {
    @Inject private ApiV3MembershipOpeningService service;
    public static void register() {
        final var beans=BeanManager.getInstance();
        final var p=beans.getReference(ApiV3MembershipOpeningProcessor.class);
        final var auth=beans.getReference(ApiV3AuthMidware.class);
        final var writes=beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/me/membership", p::open, auth::activityWrite, writes::handle);
    }
    public void open(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final byte[] bytes=context.getRequest().getBytes();
            if (bytes!=null && bytes.length>4096) throw new ApiV3Exception(413,"body_too_large","请求体过大");
            return service.open(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID),
                    ApiV3MembershipOpeningInputs.parse(ApiV3WriteInputs.body(context,Set.of("levelId","couponCode"),false)),
                    ApiV3WriteInputs.idempotencyKey(context));
        });
    }
}
