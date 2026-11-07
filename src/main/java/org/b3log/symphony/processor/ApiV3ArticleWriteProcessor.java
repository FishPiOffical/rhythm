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
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ArticleMgmtService;
import org.b3log.symphony.util.ApiV3ArticleInputs;
import org.b3log.symphony.util.ApiV3ArticleEditInputs;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import java.util.Set;

@Singleton
public class ApiV3ArticleWriteProcessor {
    @Inject private ApiV3ArticleMgmtService management;
    public static void register() {
        final var beans = BeanManager.getInstance();
        final var processor = beans.getReference(ApiV3ArticleWriteProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/articles", processor::create, auth::contentWrite, writes::handle);
        Dispatcher.put("/api/v3/articles/{articleId}", processor::update, auth::contentWrite, writes::handle);
        Dispatcher.delete("/api/v3/articles/{articleId}", processor::remove, auth::contentWrite, writes::handle);
    }
    public void create(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final var body = ApiV3WriteInputs.body(context, ApiV3ArticleInputs.FIELDS, false);
            final String ua = context.header("User-Agent");
            return management.create(ApiV3Requests.viewer(context), body, ApiV3WriteInputs.idempotencyKey(context),
                    Requests.getRemoteAddr(context.getRequest()), null == ua ? "" : ua);
        });
    }
    public void update(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final var body = ApiV3WriteInputs.body(context, ApiV3ArticleEditInputs.FIELDS, false);
            final String ua = context.header("User-Agent");
            return management.update(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "articleId"), body,
                    ApiV3WriteInputs.idempotencyKey(context), Requests.getRemoteAddr(context.getRequest()), null == ua ? "" : ua);
        });
    }
    public void remove(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true);
            return management.remove(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "articleId"), ApiV3WriteInputs.idempotencyKey(context));
        });
    }

}
