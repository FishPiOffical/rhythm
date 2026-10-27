package org.b3log.symphony.processor;

import org.b3log.latke.http.*;
import org.b3log.latke.ioc.*;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3MembershipStyleQueryService;
import org.b3log.symphony.util.*;
import java.util.Set;

@Singleton
public class ApiV3MembershipStyleProcessor {
    @Inject private ApiV3MembershipStyleQueryService query;
    public static void register(){final var b=BeanManager.getInstance();registerRoutes(b.getReference(ApiV3MembershipStyleProcessor.class),b.getReference(ApiV3AuthMidware.class));}
    public static void registerRoutes(ApiV3MembershipStyleProcessor p,ApiV3AuthMidware auth){Dispatcher.get("/api/v3/membership/styles",p::styles,auth::profileRead);}
    public void styles(RequestContext context){ApiV3Requests.read(context,()->{
        final String raw=context.getRequest().getQueryString();
        if(raw==null||raw.length()>4096)throw ApiV3Requests.invalid();
        final String ids;
        try{ids=ApiV3OAuthForms.parse(raw,Set.of("userIds")).get("userIds");}
        catch(ApiV3Exception error){throw ApiV3Requests.invalid();}
        return query.styles(ApiV3Requests.viewer(context).getString("oId"),ApiV3MembershipStyleQueryService.ids(ids));
    });}
}
