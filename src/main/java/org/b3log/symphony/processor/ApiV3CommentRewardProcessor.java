package org.b3log.symphony.processor;

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3CommentRewardQueryService;
import org.b3log.symphony.util.ApiV3Requests;

@Singleton
public class ApiV3CommentRewardProcessor {
    @Inject private ApiV3CommentRewardQueryService query;
    public static void register(){
        final var beans=BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3CommentRewardProcessor.class),beans.getReference(ApiV3AuthMidware.class));
    }
    public static void registerRoutes(final ApiV3CommentRewardProcessor processor,final ApiV3AuthMidware auth){
        Dispatcher.get("/api/v3/comments/{commentId}/thanks",processor::thanks,auth::contentRead);
        Dispatcher.get("/api/v3/comments/{commentId}/accept",processor::accept,auth::contentRead);
    }
    public void thanks(final RequestContext context){read(context,false);}
    public void accept(final RequestContext context){read(context,true);}
    private void read(final RequestContext context,final boolean acceptance){
        ApiV3Requests.respond(context,()->query.read(ApiV3Requests.viewer(context),ApiV3Requests.id(context,"commentId"),acceptance));
    }
}
