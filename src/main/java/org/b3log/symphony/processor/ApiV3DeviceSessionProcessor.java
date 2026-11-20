package org.b3log.symphony.processor;

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3DeviceSessionService;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONObject;

@Singleton
public class ApiV3DeviceSessionProcessor {
    @Inject private ApiV3DeviceSessionService devices;

    public static void register() {
        final var beans = BeanManager.getInstance();
        final var processor = beans.getReference(ApiV3DeviceSessionProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.get("/api/v3/me/security/devices", processor::list, auth::profileRead);
        Dispatcher.delete("/api/v3/me/security/devices/{deviceId}", processor::revoke, auth::settingsWrite, writes::handle);
    }

    public void list(final RequestContext c) {
        ApiV3Requests.read(c, () -> devices.list((JSONObject) c.attr(ApiV3Processor.IDENTITY)));
    }

    public void revoke(final RequestContext c) {
        ApiV3Requests.respond(c, () -> devices.revoke((JSONObject) c.attr(ApiV3Processor.IDENTITY), c.pathVar("deviceId")));
    }
}
