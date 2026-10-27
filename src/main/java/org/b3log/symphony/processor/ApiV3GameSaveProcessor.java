package org.b3log.symphony.processor;

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.ApiV3GameSaveRawBody;
import org.b3log.latke.ioc.*;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3GameSaveService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3GameSaveProcessor {
    @Inject private ApiV3GameSaveService service;
    public static void register(){
        ApiV3OAuthTransportLogFilter.requireInstalled();final var b=BeanManager.getInstance();
        registerRoutes(b.getReference(ApiV3GameSaveProcessor.class),b.getReference(ApiV3AuthMidware.class),b.getReference(ApiV3WriteMidware.class));
    }
    public static void registerRoutes(ApiV3GameSaveProcessor p,ApiV3AuthMidware auth,ApiV3WriteMidware writes){
        Dispatcher.get("/api/v3/game-save-options",p::options,auth::activityRead);
        Dispatcher.get("/api/v3/me/game-saves/{gameId}",p::get,auth::activityRead);
        Dispatcher.put("/api/v3/me/game-saves/{gameId}",p::save,auth::activityWrite,writes::handle);
        Dispatcher.post("/api/v3/me/game-saves/{gameId}/migrations",p::migrate,auth::activityWrite,writes::handle);
    }
    public void options(RequestContext c){ApiV3Requests.read(c,()->new JSONObject().put("list",ApiV3GameSaveInputs.options()));}
    public void get(RequestContext c){ApiV3Requests.read(c,()->service.get(identity(c),game(c)));}
    public void save(RequestContext c){ApiV3Requests.respond(c,()->{
        return service.save(identity(c),game(c),body(c,Set.of("data","expectedRevision"),false,ApiV3GameSaveInputs.MAX_REQUEST_BYTES),ApiV3WriteInputs.idempotencyKey(c));
    });}
    public void migrate(RequestContext c){ApiV3Requests.respond(c,()->{
        body(c,Set.of(),true,4096);return service.migrate(identity(c),game(c),ApiV3WriteInputs.idempotencyKey(c));
    });}
    private static JSONObject body(RequestContext c,Set<String> allowed,boolean empty,int maximum) {
        final byte[] bytes;
        try{bytes=ApiV3GameSaveRawBody.copy(c.getRequest(),maximum);}catch(ApiV3GameSaveRawBody.TooLarge large){throw new ApiV3Exception(413,"body_too_large","请求体过大");}
        if(empty&&bytes.length==0)return new JSONObject();
        final String type=c.header("Content-Type");if(type==null||!"application/json".equalsIgnoreCase(type.split(";",2)[0].trim()))throw new ApiV3Exception(415,"unsupported_media_type","请求体必须使用 application/json");
        final JSONObject value;
        try{value=ApiV3Json.parse(bytes);}catch(IllegalArgumentException malformed){throw new ApiV3Exception(400,"invalid_json","JSON 请求体不合法");}
        if(!allowed.containsAll(value.keySet()))throw ApiV3WriteInputs.invalid();return value;
    }
    private static JSONObject identity(RequestContext c){return (JSONObject)c.attr(ApiV3Processor.IDENTITY);}
    private static String game(RequestContext c){return ApiV3GameSaveInputs.game(c.pathVar("gameId"));}
}
