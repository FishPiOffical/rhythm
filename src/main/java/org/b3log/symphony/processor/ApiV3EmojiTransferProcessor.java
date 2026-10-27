package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3EmojiTransferService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3EmojiTransferProcessor {
    @Inject private ApiV3EmojiTransferService service;
    public static void register(){
        final var beans=BeanManager.getInstance();final var p=beans.getReference(ApiV3EmojiTransferProcessor.class);
        final var auth=beans.getReference(ApiV3AuthMidware.class);final var writes=beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/me/settings/emoji-groups/{groupId}/shares", p::share, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/me/settings/emoji-group-imports", p::importShare, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/me/settings/emoji-migrations", p::migrate, auth::settingsWrite, writes::handle);
    }
    public void share(RequestContext c){ApiV3Requests.respond(c,()->{body(c,Set.of(),true);return service.share(actor(c),ApiV3Requests.id(c,"groupId"),ApiV3WriteInputs.idempotencyKey(c));});}
    public void importShare(RequestContext c){ApiV3Requests.respond(c,()->service.importShare(actor(c),body(c,Set.of("shareCode"),false),ApiV3WriteInputs.idempotencyKey(c)));}
    public void migrate(RequestContext c){ApiV3Requests.respond(c,()->{body(c,Set.of(),true);return service.migrate(actor(c),ApiV3WriteInputs.idempotencyKey(c));});}
    private static String actor(RequestContext c){return ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID);}
    private static JSONObject body(RequestContext c,Set<String> fields,boolean empty){final byte[] bytes=c.getRequest().getBytes();if(bytes!=null&&bytes.length>4096)throw new ApiV3Exception(413,"body_too_large","请求体过大");return ApiV3WriteInputs.body(c,fields,empty);}
}
