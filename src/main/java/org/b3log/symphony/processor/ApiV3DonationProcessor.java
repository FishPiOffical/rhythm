/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
