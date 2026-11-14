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

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.*;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.service.ApiV3ArticleStickService;
import org.b3log.symphony.util.*;

import java.util.Set;

@Singleton
public class ApiV3ArticleStickProcessor {
    @Inject
    private ApiV3ArticleStickService sticks;

    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3ArticleStickProcessor.class), beans.getReference(ApiV3AuthMidware.class), beans.getReference(ApiV3WriteMidware.class));
    }

    public static void registerRoutes(ApiV3ArticleStickProcessor processor, ApiV3AuthMidware auth, ApiV3WriteMidware writes) {
        Dispatcher.post("/api/v3/articles/{articleId}/stick", processor::stick, auth::contentWrite, auth::activityWrite, writes::handle);
    }

    public void stick(RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true);
            return sticks.stick(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "articleId"), ApiV3WriteInputs.idempotencyKey(context));
        });
    }
}
