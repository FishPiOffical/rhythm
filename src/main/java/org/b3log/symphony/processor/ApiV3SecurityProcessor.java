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

import org.b3log.latke.http.*;
import org.b3log.latke.ioc.*;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.service.ApiV3SecuritySettingsService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3SecurityProcessor {
    @Inject
    private ApiV3SecuritySettingsService settings;

    public static void register() {
        final var b = BeanManager.getInstance();
        registerRoutes(b.getReference(ApiV3SecurityProcessor.class),
                b.getReference(ApiV3AuthMidware.class), b.getReference(ApiV3WriteMidware.class));
    }

    public static void registerRoutes(final ApiV3SecurityProcessor p, final ApiV3AuthMidware auth, final ApiV3WriteMidware writes) {
        Dispatcher.get("/api/v3/me/security/mfa",p::mfa,auth::profileRead);
        Dispatcher.put("/api/v3/me/settings/password",p::password,auth::settingsWrite,writes::handle);
    }

    private static JSONObject identity(final RequestContext c) {
        return (JSONObject) c.attr(ApiV3Processor.IDENTITY);
    }

    public void mfa(final RequestContext c) {
        ApiV3Requests.read(c, () -> settings.mfa(identity(c)));
    }

    public void password(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final byte[] bytes = c.getRequest().getBytes();
            if (bytes != null && bytes.length > 4096)
                throw new ApiV3Exception(413, "body_too_large", "请求体过大");
            return settings.password(identity(c),
                    ApiV3WriteInputs.body(c, Set.of("currentPassword", "newPassword", "currentTotp"), false),
                    Requests.getRemoteAddr(c.getRequest()));
        });
    }
}
