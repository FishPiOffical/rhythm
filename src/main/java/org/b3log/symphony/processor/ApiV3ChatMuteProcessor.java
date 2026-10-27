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
import org.b3log.symphony.service.ApiV3ChatMuteQueryService;
import org.b3log.symphony.util.*;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ApiV3ChatMuteProcessor {
    @Inject private ApiV3ChatMuteQueryService query;
    private static final Cache<String, AtomicInteger> COUNTS = Caffeine.newBuilder().maximumSize(100000).expireAfterWrite(1, TimeUnit.MINUTES).build();
    private static final Semaphore QUERIES = new Semaphore(4);
    public static void register() { final var b=BeanManager.getInstance();registerRoutes(b.getReference(ApiV3ChatMuteProcessor.class),b.getReference(ApiV3AuthMidware.class)); }
    public static void registerRoutes(ApiV3ChatMuteProcessor p,ApiV3AuthMidware auth) { Dispatcher.get("/api/v3/chat/mutes",p::list,auth::contentRead); }
    public void list(RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final var user=ApiV3Requests.viewer(context);
            if(user==null)throw new ApiV3Exception(401,"unauthorized","登录凭据无效");
            final String raw=context.getRequest().getQueryString();
            if(raw!=null&&raw.length()>1024)throw ApiV3Requests.invalid();
            final java.util.Map<String,String> values;
            try { values=raw==null||raw.isEmpty()?java.util.Map.of():ApiV3OAuthForms.parse(raw,Set.of("p","size")); }
            catch(ApiV3Exception error){throw ApiV3Requests.invalid();}
            final int page=number(values.get("p"),1,Integer.MAX_VALUE/50), size=number(values.get("size"),20,50);
            final int owner=COUNTS.get("user:"+user.getString("oId"),k->new AtomicInteger()).incrementAndGet();
            final int ip=COUNTS.get("ip:"+Requests.getRemoteAddr(context.getRequest()),k->new AtomicInteger()).incrementAndGet();
            if(owner>120||ip>600){context.getResponse().setHeader("Retry-After","60");throw new ApiV3Exception(429,"too_many_requests","请求过于频繁，请稍后再试");}
            if(!QUERIES.tryAcquire())throw new ApiV3Exception(503,"temporarily_unavailable","思过崖暂不可用");
            try{return query.list(page,size);}finally{QUERIES.release();}
        });
    }
    private static int number(String raw,int fallback,int max) {
        if(raw==null)return fallback;
        if(!raw.matches("[0-9]{1,10}"))throw ApiV3Requests.invalid();
        try{final int n=Integer.parseInt(raw);if(n<1||n>max)throw ApiV3Requests.invalid();return n;}
        catch(NumberFormatException e){throw ApiV3Requests.invalid();}
    }
}
