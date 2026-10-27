package org.b3log.symphony.processor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3UserSuggestionQueryService;
import org.b3log.symphony.util.*;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ApiV3UserSuggestionProcessor {
    @Inject private ApiV3UserSuggestionQueryService query;
    private static final Cache<String, AtomicInteger> COUNTS = Caffeine.newBuilder().maximumSize(100000).expireAfterWrite(1, TimeUnit.MINUTES).build();
    private static final Semaphore QUERIES = new Semaphore(4);
    public static void register() { final var b=BeanManager.getInstance();registerRoutes(b.getReference(ApiV3UserSuggestionProcessor.class),b.getReference(ApiV3AuthMidware.class)); }
    public static void registerRoutes(ApiV3UserSuggestionProcessor p,ApiV3AuthMidware auth) { Dispatcher.get("/api/v3/suggestions/users",p::suggestions,auth::profileRead); }
    public void suggestions(RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final var user=ApiV3Requests.viewer(context);
            if(user==null)throw new ApiV3Exception(401,"unauthorized","登录凭据无效");
            final String raw=context.getRequest().getQueryString();
            if(raw!=null&&raw.length()>1024)throw ApiV3Requests.invalid();
            final String prefix;
            try { prefix=raw==null||raw.isEmpty()?"":ApiV3OAuthForms.parse(raw,Set.of("prefix")).getOrDefault("prefix",""); }
            catch(ApiV3Exception error){throw ApiV3Requests.invalid();}
            ApiV3UserSuggestionQueryService.prefix(prefix);
            final int owner=COUNTS.get("user:"+user.getString("oId"),k->new AtomicInteger()).incrementAndGet();
            final int ip=COUNTS.get("ip:"+Requests.getRemoteAddr(context.getRequest()),k->new AtomicInteger()).incrementAndGet();
            if(owner>120||ip>600){context.getResponse().setHeader("Retry-After","60");throw new ApiV3Exception(429,"too_many_requests","请求过于频繁，请稍后再试");}
            if(!QUERIES.tryAcquire())throw new ApiV3Exception(503,"temporarily_unavailable","用户联想暂不可用");
            try{return query.suggestions(user.getString("oId"),prefix);}finally{QUERIES.release();}
        });
    }
}
