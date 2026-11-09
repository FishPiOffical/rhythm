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
import org.b3log.symphony.service.ApiV3EmojiTransferService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3EmojiTransferProcessor {
    @Inject
    private ApiV3EmojiTransferService service;

    public static void register() {
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3EmojiTransferProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/me/settings/emoji-groups/{groupId}/shares", p::share, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/me/settings/emoji-group-imports", p::importShare, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/me/settings/emoji-migrations", p::migrate, auth::settingsWrite, writes::handle);
    }

    public void share(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            body(c, Set.of(), true);
            return service.share(actor(c), ApiV3Requests.id(c, "groupId"), ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    public void importShare(RequestContext c) {
        ApiV3Requests.respond(c, () -> service.importShare(actor(c), body(c, Set.of("shareCode"), false),
                ApiV3WriteInputs.idempotencyKey(c)));
    }

    public void migrate(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            body(c, Set.of(), true);
            return service.migrate(actor(c), ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    private static String actor(RequestContext c) {
        return ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID);
    }

    private static JSONObject body(RequestContext c, Set<String> fields, boolean empty) {
        final byte[] bytes = c.getRequest().getBytes();
        if (bytes != null && bytes.length > 4096) throw new ApiV3Exception(413, "body_too_large", "请求体过大");
        return ApiV3WriteInputs.body(c, fields, empty);
    }
}
