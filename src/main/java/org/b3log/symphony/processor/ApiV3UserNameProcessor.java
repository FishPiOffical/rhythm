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
import org.b3log.latke.Keys;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.service.ApiV3UserNameService;
import org.b3log.symphony.util.*;

import java.util.Set;

@Singleton
public class ApiV3UserNameProcessor {
    @Inject
    private ApiV3UserNameService settings;

    public static void register() {
        final var b = BeanManager.getInstance();
        registerRoutes(b.getReference(ApiV3UserNameProcessor.class),
                b.getReference(ApiV3AuthMidware.class), b.getReference(ApiV3WriteMidware.class));
    }

    public static void registerRoutes(ApiV3UserNameProcessor p, ApiV3AuthMidware auth, ApiV3WriteMidware writes) {
        Dispatcher.put("/api/v3/me/settings/user-name", p::rename, auth::activityWrite, auth::settingsWrite, writes::handle);
    }

    public void rename(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final byte[] raw = c.getRequest().getBytes();
            if (raw != null && raw.length > 4096)
                throw new ApiV3Exception(413, "body_too_large", "请求体过大");
            return settings.rename(ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID),
                    ApiV3WriteInputs.body(c, Set.of("userName"), false), ApiV3WriteInputs.idempotencyKey(c));
        });
    }
}
