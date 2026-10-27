package org.b3log.symphony.processor;

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.*;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ContactSettingsService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3ContactSettingsProcessor {
    @Inject private ApiV3ContactSettingsService settings;
    public static void register(){final var b=BeanManager.getInstance();registerRoutes(b.getReference(ApiV3ContactSettingsProcessor.class),b.getReference(ApiV3AuthMidware.class),b.getReference(ApiV3WriteMidware.class));}
    public static void registerRoutes(ApiV3ContactSettingsProcessor p,ApiV3AuthMidware auth,ApiV3WriteMidware writes) {
        Dispatcher.post("/api/v3/me/settings/phone/verification",p::phoneCode,auth::settingsWrite,writes::handle);
        Dispatcher.post("/api/v3/me/settings/email/verification",p::emailCode,auth::settingsWrite,writes::handle);
        Dispatcher.put("/api/v3/me/settings/phone",p::phone,auth::settingsWrite,writes::handle);
        Dispatcher.put("/api/v3/me/settings/email",p::email,auth::settingsWrite,writes::handle);
    }
    public void phoneCode(RequestContext c){handle(c,true,true);}
    public void emailCode(RequestContext c){handle(c,false,true);}
    public void phone(RequestContext c){handle(c,true,false);}
    public void email(RequestContext c){handle(c,false,false);}
    private void handle(RequestContext c,boolean phone,boolean sending) {
        ApiV3Requests.respond(c,()->{
            final byte[] bytes=c.getRequest().getBytes();if(bytes!=null&&bytes.length>8192)throw new ApiV3Exception(413,"body_too_large","请求体过大");
            final Set<String> fields=sending?Set.of("target","currentPassword","currentTotp","captcha"):Set.of("target","currentPassword","currentTotp","code");
            final var input=ApiV3ContactInputs.parse(ApiV3WriteInputs.body(c,fields,false),phone,sending);
            final JSONObject identity=(JSONObject)c.attr(ApiV3Processor.IDENTITY);
            final String ip=Requests.getRemoteAddr(c.getRequest());
            return sending?settings.request(identity,input,phone,ip):settings.change(identity,input,phone,ip);
        });
    }
}
