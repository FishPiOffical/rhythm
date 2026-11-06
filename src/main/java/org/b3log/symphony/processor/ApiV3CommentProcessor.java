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
import org.b3log.symphony.service.ApiV3CommentMgmtService;
import org.b3log.symphony.service.ApiV3CommentVoteService;
import org.b3log.symphony.util.ApiV3Votes;
import org.b3log.symphony.util.ApiV3CommentInputs;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;

@Singleton
public class ApiV3CommentProcessor {
    @Inject private ApiV3CommentMgmtService comments;
    @Inject private ApiV3CommentVoteService votes;
    public static void register() {
        final var beans = BeanManager.getInstance();
        final var processor = beans.getReference(ApiV3CommentProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/articles/{articleId}/comments", processor::create, auth::commentsWrite, writes::handle);
        Dispatcher.put("/api/v3/comments/{commentId}", processor::update, auth::commentsWrite, writes::handle);
        Dispatcher.delete("/api/v3/comments/{commentId}", processor::remove, auth::commentsWrite, writes::handle);
        Dispatcher.post("/api/v3/comments/{commentId}/thanks", processor::thank, auth::commentsWrite, writes::handle);
        Dispatcher.post("/api/v3/comments/{commentId}/accept", processor::accept, auth::commentsWrite, writes::handle);
        Dispatcher.get("/api/v3/comments/{commentId}/vote", processor::vote, auth::contentRead);
        Dispatcher.put("/api/v3/comments/{commentId}/vote", processor::setVote, auth::commentsWrite, writes::handle);
        Dispatcher.delete("/api/v3/comments/{commentId}/vote", processor::clearVote, auth::commentsWrite, writes::handle);
    }
    public void vote(final RequestContext context) {
        ApiV3Requests.respond(context, () -> votes.read(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "commentId")));
    }
    public void setVote(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final var body = ApiV3WriteInputs.body(context, ApiV3Votes.FIELDS, false);
            return votes.set(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "commentId"), ApiV3Votes.choice(body),
                    ApiV3WriteInputs.idempotencyKey(context));
        });
    }
    public void clearVote(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, java.util.Set.of(), true);
            return votes.set(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "commentId"), -1,
                    ApiV3WriteInputs.idempotencyKey(context));
        });
    }
    public void remove(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, java.util.Set.of(), true);
            return comments.remove(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "commentId"),
                    ApiV3WriteInputs.idempotencyKey(context));
        });
    }
    public void accept(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, java.util.Set.of(), true);
            return comments.accept(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "commentId"),
                    ApiV3WriteInputs.idempotencyKey(context));
        });
    }
    public void thank(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, java.util.Set.of(), true);
            return comments.thank(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "commentId"),
                    ApiV3WriteInputs.idempotencyKey(context));
        });
    }
    public void create(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final var body = ApiV3WriteInputs.body(context, ApiV3CommentInputs.CREATE_FIELDS, false);
            final String ua = context.header("User-Agent");
            return comments.create(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "articleId"), body,
                    ApiV3WriteInputs.idempotencyKey(context), Requests.getRemoteAddr(context.getRequest()), null == ua ? "" : ua);
        });
    }
    public void update(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final var body = ApiV3WriteInputs.body(context, ApiV3CommentInputs.EDIT_FIELDS, false);
            final String ua = context.header("User-Agent");
            return comments.update(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "commentId"), body,
                    ApiV3WriteInputs.idempotencyKey(context), Requests.getRemoteAddr(context.getRequest()), null == ua ? "" : ua);
        });
    }
}
