package org.b3log.symphony.processor;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.ApiV3DonationPage;
import org.b3log.symphony.util.ApiV3Requests;

@Singleton
public class ApiV3DonationProcessor {
    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3DonationProcessor.class), beans.getReference(ApiV3AuthMidware.class));
    }

    public static void registerRoutes(final ApiV3DonationProcessor processor, final ApiV3AuthMidware auth) {
        Dispatcher.get("/api/v3/payments/donation-page", processor::page, auth::activityRead);
    }

    public void page(final RequestContext context) {
        ApiV3Requests.read(context, () -> ApiV3DonationPage.link(Latkes.getServePath(),
                Latkes.RuntimeMode.DEVELOPMENT == Latkes.getRuntimeMode()));
    }
}
