package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3RepeaterPublicationService;
import org.b3log.symphony.util.*;
import java.util.Set;

@Singleton
public class ApiV3RepeaterPublicationProcessor {
    @Inject private ApiV3RepeaterPublicationService service;
    public static void register(){
        final var beans=BeanManager.getInstance();final var p=beans.getReference(ApiV3RepeaterPublicationProcessor.class);
        final var auth=beans.getReference(ApiV3AuthMidware.class);final var writes=beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/repeater/items",p::publish,auth::contentWrite,writes::handle);
    }
    public void publish(final RequestContext context){
        ApiV3Requests.respond(context,()->{
            final byte[] bytes=context.getRequest().getBytes();if(bytes!=null&&bytes.length>16384)throw new ApiV3Exception(413,"body_too_large","请求体过大");
            return service.publish(ApiV3Requests.viewer(context).getString(Keys.OBJECT_ID),ApiV3WriteInputs.body(context,Set.of("type","content"),false),ApiV3WriteInputs.idempotencyKey(context));
        });
    }
}
