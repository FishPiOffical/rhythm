package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3FishGameSubmissionService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import java.util.Set;

@Singleton
public class ApiV3FishGameSubmissionProcessor {
    @Inject private ApiV3FishGameSubmissionService service;
    public static void register() {
        var beans=BeanManager.getInstance();var p=beans.getReference(ApiV3FishGameSubmissionProcessor.class);
        var auth=beans.getReference(ApiV3AuthMidware.class);var writes=beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/fish-games",p::submit,auth::contentWrite,writes::handle);
        Dispatcher.put("/api/v3/fish-games/{gameId}",p::edit,auth::contentWrite,writes::handle);
    }
    public void submit(RequestContext c){write(c,false);}
    public void edit(RequestContext c){write(c,true);}
    private void write(RequestContext c,boolean edit) {
        ApiV3Requests.respond(c,()-> {
            byte[] bytes=c.getRequest().getBytes();if(bytes!=null&&bytes.length>16384)throw new ApiV3Exception(413,"body_too_large","请求体过大");
            return service.submit(ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID),edit?ApiV3Requests.id(c,"gameId"):null,
                    ApiV3WriteInputs.body(c,Set.of("name","description","url","iconUrl","oauthLoginUrl"),false),ApiV3WriteInputs.idempotencyKey(c));
        });
    }
}
