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
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ArticleVoteService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Votes;
import org.b3log.symphony.util.ApiV3WriteInputs;

@Singleton
public class ApiV3VoteProcessor {
    @Inject
    private ApiV3ArticleVoteService articles;

    public static void register() {
        final var beans = BeanManager.getInstance();
        final var processor = beans.getReference(ApiV3VoteProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.get("/api/v3/articles/{articleId}/vote", processor::read, auth::contentRead);
        Dispatcher.put("/api/v3/articles/{articleId}/vote", processor::set, auth::contentWrite, writes::handle);
        Dispatcher.delete("/api/v3/articles/{articleId}/vote", processor::clear, auth::contentWrite, writes::handle);
    }

    public void read(final RequestContext context) {
        ApiV3Requests.respond(context, () -> articles.read(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "articleId")));
    }

    public void set(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final var body = ApiV3WriteInputs.body(context, ApiV3Votes.FIELDS, false);
            return articles.set(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "articleId"), ApiV3Votes.choice(body), ApiV3WriteInputs.idempotencyKey(context));
        });
    }

    public void clear(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, java.util.Set.of(), true);
            return articles.set(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "articleId"), -1, ApiV3WriteInputs.idempotencyKey(context));
        });
    }
}
