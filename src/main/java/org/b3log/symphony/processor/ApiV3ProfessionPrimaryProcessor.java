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

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ProfessionPrimaryService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import java.util.Set;

/** 通过 Bearer 身份显式选择主职业，跳过引导和隐私设置由其他入口处理。 */
@Singleton
public class ApiV3ProfessionPrimaryProcessor {
    @Inject
    private ApiV3ProfessionPrimaryService service;
    public static void register() {
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3ProfessionPrimaryProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.put("/api/v3/me/professions/primary", p::set, auth::settingsWrite, writes::handle);
    }
    public void set(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final var body = ApiV3WriteInputs.body(c, Set.of("professionId"), false);
            return service.set(ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID),
                    ApiV3WriteInputs.string(body, "professionId", 19, false), ApiV3WriteInputs.idempotencyKey(c));
        });
    }
}
