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
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3PrivateMessageQueryService;
import org.b3log.symphony.service.ApiV3PrivateMessageStateService;
import org.b3log.symphony.service.ApiV3PrivateMessagePublicationService;
import org.b3log.symphony.service.ApiV3PrivateMessageWithdrawalService;
import org.b3log.symphony.util.*;

@Singleton
public class ApiV3PrivateMessageProcessor {
    @Inject
    private ApiV3PrivateMessageQueryService query;
    @Inject
    private ApiV3PrivateMessageStateService state;
    @Inject
    private ApiV3PrivateMessagePublicationService publication;
    @Inject
    private ApiV3PrivateMessageWithdrawalService withdrawal;

    public static void register() {
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3PrivateMessageProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.get("/api/v3/me/conversations",p::list,auth::messagesRead);
        Dispatcher.get("/api/v3/me/conversations/{peerId}",p::conversation,auth::messagesRead);
        Dispatcher.get("/api/v3/me/conversations/{peerId}/messages",p::history,auth::messagesRead);
        Dispatcher.get("/api/v3/me/messages/unread-count",p::unread,auth::messagesRead);
        Dispatcher.get("/api/v3/me/messages/{messageId}",p::message,auth::messagesRead);
        Dispatcher.post("/api/v3/me/conversations/{peerId}/messages",p::send,auth::messagesWrite,writes::handle);
        Dispatcher.post("/api/v3/me/conversations/{peerId}/read",p::markRead,auth::messagesWrite,writes::handle);
        Dispatcher.post("/api/v3/me/conversations/read",p::markAllRead,auth::messagesWrite,writes::handle);
        Dispatcher.delete("/api/v3/me/messages/{messageId}",p::withdraw,auth::messagesWrite,writes::handle);
    }

    public void list(RequestContext c) {
        ApiV3Requests.read(c, () -> query.conversations(ApiV3Requests.viewer(c), ApiV3Requests.page(c), ApiV3Requests.size(c)));
    }

    public void conversation(RequestContext c) {
        ApiV3Requests.read(c, () -> query.conversation(ApiV3Requests.viewer(c), c.pathVar("peerId")));
    }

    public void history(RequestContext c) {
        ApiV3Requests.read(c, () -> query.history(ApiV3Requests.viewer(c), c.pathVar("peerId"),
                c.param("before"), c.param("after"), ApiV3Requests.size(c)));
    }

    public void unread(RequestContext c) {
        ApiV3Requests.read(c, () -> query.unread(ApiV3Requests.viewer(c)));
    }

    public void message(RequestContext c) {
        // Latke 可能通过动态消息路径匹配同层的未读数路由。
        if ("unread-count".equals(c.pathVar("messageId"))) {
            unread(c);
            return;
        }
        ApiV3Requests.read(c, () -> query.get(ApiV3Requests.viewer(c), c.pathVar("messageId")));
    }

    public void send(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final String query = c.getRequest().getQueryString();
            if (query != null && !query.isEmpty()) throw ApiV3WriteInputs.invalid();
            final var bytes = c.getRequest().getBytes();
            if (bytes != null && bytes.length > 65536)
                throw new ApiV3Exception(413, "body_too_large", "请求体过大");
            final var body = ApiV3WriteInputs.body(c, java.util.Set.of("content"), false);
            return publication.send(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "peerId"),
                    ApiV3WriteInputs.string(body, "content", 10240, false), ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    public void withdraw(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final String query = c.getRequest().getQueryString();
            if (query != null && !query.isEmpty()) throw ApiV3WriteInputs.invalid();
            final var bytes = c.getRequest().getBytes();
            if (bytes != null && bytes.length > 1024)
                throw new ApiV3Exception(413, "body_too_large", "请求体过大");
            ApiV3WriteInputs.body(c, java.util.Set.of(), true);
            return withdrawal.remove(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "messageId"), ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    public void markRead(RequestContext c) {
        mark(c, false);
    }

    public void markAllRead(RequestContext c) {
        mark(c, true);
    }

    private void mark(RequestContext c, boolean all) {
        ApiV3Requests.respond(c, () -> {
            final String query = c.getRequest().getQueryString();
            if (query != null && !query.isEmpty()) throw ApiV3WriteInputs.invalid();
            final var bytes = c.getRequest().getBytes();
            if (bytes != null && bytes.length > 1024)
                throw new ApiV3Exception(413, "body_too_large", "请求体过大");
            ApiV3WriteInputs.body(c, java.util.Set.of(), true);
            return state.mark(ApiV3Requests.viewer(c), all ? null : ApiV3Requests.id(c, "peerId"), ApiV3WriteInputs.idempotencyKey(c));
        });
    }
}
