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
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3ReactionService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3ReactionDto;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3ReactionProcessor {
    @Inject
    private ApiV3ReactionService reactions;
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3ReactionProcessor p = beans.getReference(ApiV3ReactionProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);
        final AnonymousViewCheckMidware anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/reactions/emojis", p::emojis, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/articles/{targetId}/reactions", p::article, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/comments/{targetId}/reactions", p::comment, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/chat/messages/{targetId}/reactions", p::chat, auth::contentRead);
        Dispatcher.put("/api/v3/articles/{targetId}/reactions", p::setArticle, auth::reactionsWrite, writes::handle);
        Dispatcher.delete("/api/v3/articles/{targetId}/reactions", p::clearArticle, auth::reactionsWrite, writes::handle);
        Dispatcher.put("/api/v3/comments/{targetId}/reactions", p::setComment, auth::reactionsWrite, writes::handle);
        Dispatcher.delete("/api/v3/comments/{targetId}/reactions", p::clearComment, auth::reactionsWrite, writes::handle);
        Dispatcher.put("/api/v3/chat/messages/{targetId}/reactions", p::setChat, auth::reactionsWrite, writes::handle);
        Dispatcher.delete("/api/v3/chat/messages/{targetId}/reactions", p::clearChat, auth::reactionsWrite, writes::handle);
    }
    public void emojis(final RequestContext c) {
        ApiV3Requests.read(c, () -> new JSONObject().put("list", ApiV3ReactionDto.emojis()));
    }

    public void article(final RequestContext c) {
        read(c, Reaction.TARGET_TYPE_ARTICLE);
    }

    public void comment(final RequestContext c) {
        read(c, Reaction.TARGET_TYPE_COMMENT);
    }

    public void chat(final RequestContext c) {
        read(c, Reaction.TARGET_TYPE_CHAT);
    }
    private void read(final RequestContext c, final String type) {
        ApiV3Requests.read(c, () -> reactions.read(ApiV3Requests.viewer(c), type, ApiV3Requests.id(c, "targetId")));
    }
    public void setArticle(final RequestContext c) {
        set(c, Reaction.TARGET_TYPE_ARTICLE, false);
    }

    public void clearArticle(final RequestContext c) {
        set(c, Reaction.TARGET_TYPE_ARTICLE, true);
    }

    public void setComment(final RequestContext c) {
        set(c, Reaction.TARGET_TYPE_COMMENT, false);
    }

    public void clearComment(final RequestContext c) {
        set(c, Reaction.TARGET_TYPE_COMMENT, true);
    }

    public void setChat(final RequestContext c) {
        set(c, Reaction.TARGET_TYPE_CHAT, false);
    }

    public void clearChat(final RequestContext c) {
        set(c, Reaction.TARGET_TYPE_CHAT, true);
    }
    private void set(final RequestContext c, final String type, final boolean clear) {
        ApiV3Requests.respond(c, () -> {
            final JSONObject body = ApiV3WriteInputs.body(c, clear ? Set.of() : Set.of("value"), clear);
            return reactions.set(ApiV3Requests.viewer(c), type, ApiV3Requests.id(c, "targetId"), clear ? "" : ApiV3ReactionDto.value(body), ApiV3WriteInputs.idempotencyKey(c));
        });
    }
}
