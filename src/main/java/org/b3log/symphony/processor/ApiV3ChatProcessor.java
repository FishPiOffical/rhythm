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
import org.b3log.latke.Keys;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ChatPublicationService;
import org.b3log.symphony.service.ApiV3ChatQueryService;
import org.b3log.symphony.service.ApiV3RankingQueryService;
import org.b3log.symphony.service.ApiV3ChatOnlineQueryService;
import org.b3log.symphony.service.ChatMessageWithdrawalMgmtService;
import org.b3log.symphony.service.ApiV3ChatTopicService;
import org.b3log.symphony.service.ApiV3ChatBarrageService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;

import java.util.Set;

@Singleton
public class ApiV3ChatProcessor {
    @Inject
    private ApiV3ChatQueryService query;
    @Inject
    private ApiV3RankingQueryService rankings;
    @Inject
    private ApiV3ChatOnlineQueryService onlineQuery;
    @Inject
    private ApiV3ChatPublicationService publication;
    @Inject
    private ChatMessageWithdrawalMgmtService withdrawals;
    @Inject
    private ApiV3ChatTopicService topics;
    @Inject
    private ApiV3ChatBarrageService barrages;

    public static void register() {
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3ChatProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/chat/messages", p::create, auth::contentWrite, writes::handle);
        Dispatcher.post("/api/v3/chat/music", p::sendMusic, auth::contentWrite, writes::handle);
        Dispatcher.post("/api/v3/chat/weather", p::sendWeather, auth::contentWrite, writes::handle);
        Dispatcher.delete("/api/v3/chat/messages/{messageId}", p::withdraw, auth::contentWrite, writes::handle);
        Dispatcher.get("/api/v3/chat/messages", p::list, auth::contentRead);
        Dispatcher.get("/api/v3/chat/online-users", p::online, auth::contentRead);
        Dispatcher.get("/api/v3/chat/topic", p::topic, auth::contentRead);
        Dispatcher.put("/api/v3/chat/topic", p::setTopic, auth::contentWrite, writes::handle);
        Dispatcher.post("/api/v3/chat/barrager", p::sendBarrage, auth::contentWrite, writes::handle);
        Dispatcher.get("/api/v3/chat/barrager/price", p::barragerPrice, auth::contentRead);
        Dispatcher.get("/api/v3/chat/messages/{messageId}", p::detail, auth::contentRead);
        Dispatcher.get("/api/v3/chat/messages/{messageId}/pet", p::pet, auth::contentRead);
        Dispatcher.get("/api/v3/chat/messages/{messageId}/context", p::context, auth::contentRead);
    }

    public void list(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.list(cursor(c, "before"), cursor(c, "after"), ApiV3Requests.size(c),
                ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID)));
    }

    public void create(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final var body = ApiV3WriteInputs.body(c, Set.of("content", "client"), false);
            return publication.publish(ApiV3Requests.viewer(c), ApiV3WriteInputs.string(body, "content", 4096, false),
                    ApiV3WriteInputs.string(body, "client", 128, false), ApiV3WriteInputs.idempotencyKey(c), c.getRequest());
        });
    }

    public void sendMusic(final RequestContext c) {
        ApiV3Requests.respond(c, () -> publication.publishCard(ApiV3Requests.viewer(c), "music",
                ApiV3WriteInputs.body(c, Set.of("title", "from", "source", "coverURL"), false),
                ApiV3WriteInputs.idempotencyKey(c)));
    }

    public void sendWeather(final RequestContext c) {
        ApiV3Requests.respond(c, () -> publication.publishCard(ApiV3Requests.viewer(c), "weather",
                ApiV3WriteInputs.body(c, Set.of("city", "description", "days"), false),
                ApiV3WriteInputs.idempotencyKey(c)));
    }

    public void online(final RequestContext c) {
        ApiV3Requests.read(c, () -> onlineQuery.list(ApiV3Requests.viewer(c), ApiV3Requests.page(c), ApiV3Requests.size(c)));
    }

    public void withdraw(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            ApiV3WriteInputs.body(c, Set.of(), true);
            return withdrawals.withdraw(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "messageId"), ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    public void topic(final RequestContext c) {
        ApiV3Requests.read(c, query::topic);
    }

    public void setTopic(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final var body = ApiV3WriteInputs.body(c, Set.of("text"), false);
            return topics.set(ApiV3Requests.viewer(c), ApiV3WriteInputs.string(body, "text", 64, false),
                    ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    public void sendBarrage(final RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final var body = ApiV3WriteInputs.body(c, Set.of("content", "color"), false);
            return barrages.send(ApiV3Requests.viewer(c), ApiV3WriteInputs.string(body, "content", 4096, false),
                    ApiV3WriteInputs.string(body, "color", 7, false), ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    public void barragerPrice(final RequestContext c) {
        ApiV3Requests.read(c, query::barragerPrice);
    }

    public void detail(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.detail(ApiV3Requests.id(c, "messageId"), ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID)));
    }

    public void pet(final RequestContext c) {
        ApiV3Requests.read(c, () -> rankings.chatPetProfile(query.detail(ApiV3Requests.id(c, "messageId"))));
    }

    public void context(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.context(ApiV3Requests.id(c, "messageId"), ApiV3Requests.number(c, "radius", 10, 25)));
    }

    private static String cursor(final RequestContext c, final String key) {
        final String value = c.param(key);
        if (value == null) return null;
        if (!value.matches("[0-9]{1,32}")) throw ApiV3Requests.invalid();
        return value;
    }
}
