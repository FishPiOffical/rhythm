package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.*;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3RevisionQueryService;
import org.b3log.symphony.util.ApiV3Requests;

@Singleton
public class ApiV3RevisionProcessor {
    @Inject private ApiV3RevisionQueryService query;
    public static void register(){final var b=BeanManager.getInstance();registerRoutes(b.getReference(ApiV3RevisionProcessor.class),b.getReference(ApiV3AuthMidware.class));}
    public static void registerRoutes(final ApiV3RevisionProcessor p,final ApiV3AuthMidware auth){
        Dispatcher.get("/api/v3/articles/{articleId}/revisions",p::articles,auth::contentRead);
        Dispatcher.get("/api/v3/articles/{articleId}/revisions/{revisionId}",p::article,auth::contentRead);
        Dispatcher.get("/api/v3/comments/{commentId}/revisions",p::comments,auth::contentRead);
        Dispatcher.get("/api/v3/comments/{commentId}/revisions/{revisionId}",p::comment,auth::contentRead);
    }
    public void articles(final RequestContext c){list(c,0,"articleId");}
    public void comments(final RequestContext c){list(c,1,"commentId");}
    public void article(final RequestContext c){detail(c,0,"articleId");}
    public void comment(final RequestContext c){detail(c,1,"commentId");}
    private void list(final RequestContext c,final int type,final String field){ApiV3Requests.read(c,()->query.list(ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID),type,c.pathVar(field),ApiV3Requests.page(c),ApiV3Requests.size(c)));}
    private void detail(final RequestContext c,final int type,final String field){ApiV3Requests.read(c,()->query.detail(ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID),type,c.pathVar(field),c.pathVar("revisionId")));}
}
