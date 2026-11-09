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
import org.b3log.latke.http.ApiV3GameSaveRawBody;
import org.b3log.latke.ioc.*;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3GameSaveService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3GameSaveProcessor {
    @Inject
    private ApiV3GameSaveService service;

    public static void register() {
        ApiV3OAuthTransportLogFilter.requireInstalled();
        final var b = BeanManager.getInstance();
        registerRoutes(b.getReference(ApiV3GameSaveProcessor.class),b.getReference(ApiV3AuthMidware.class),b.getReference(ApiV3WriteMidware.class));
    }
    public static void registerRoutes(ApiV3GameSaveProcessor p, ApiV3AuthMidware auth, ApiV3WriteMidware writes) {
        Dispatcher.get("/api/v3/game-save-options",p::options,auth::activityRead);
        Dispatcher.get("/api/v3/me/game-saves/{gameId}",p::get,auth::activityRead);
        Dispatcher.put("/api/v3/me/game-saves/{gameId}",p::save,auth::activityWrite,writes::handle);
        Dispatcher.post("/api/v3/me/game-saves/{gameId}/migrations",p::migrate,auth::activityWrite,writes::handle);
    }
    public void options(RequestContext c) {
        ApiV3Requests.read(c, () -> new JSONObject().put("list", ApiV3GameSaveInputs.options()));
    }

    public void get(RequestContext c) {
        ApiV3Requests.read(c, () -> service.get(identity(c), game(c)));
    }

    public void save(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            return service.save(identity(c), game(c),
                    body(c, Set.of("data", "expectedRevision"), false, ApiV3GameSaveInputs.MAX_REQUEST_BYTES),
                    ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    public void migrate(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            body(c, Set.of(), true, 4096);
            return service.migrate(identity(c), game(c), ApiV3WriteInputs.idempotencyKey(c));
        });
    }

    private static JSONObject body(RequestContext c, Set<String> allowed, boolean empty, int maximum) {
        // 直接读取原始请求字节，避免 Latke 预处理改写存档内容或把非法 JSON 变成空体。
        final byte[] bytes;
        try {
            bytes = ApiV3GameSaveRawBody.copy(c.getRequest(), maximum);
        } catch (ApiV3GameSaveRawBody.TooLarge large) {
            throw new ApiV3Exception(413, "body_too_large", "请求体过大");
        }
        if (empty && bytes.length == 0) return new JSONObject();
        final String type = c.header("Content-Type");
        if (type == null || !"application/json".equalsIgnoreCase(type.split(";", 2)[0].trim()))
            throw new ApiV3Exception(415, "unsupported_media_type", "请求体必须使用 application/json");
        final JSONObject value;
        try {
            value = ApiV3Json.parse(bytes);
        } catch (IllegalArgumentException malformed) {
            throw new ApiV3Exception(400, "invalid_json", "JSON 请求体不合法");
        }
        if (!allowed.containsAll(value.keySet())) throw ApiV3WriteInputs.invalid();
        return value;
    }

    private static JSONObject identity(RequestContext c) {
        return (JSONObject) c.attr(ApiV3Processor.IDENTITY);
    }

    private static String game(RequestContext c) {
        return ApiV3GameSaveInputs.game(c.pathVar("gameId"));
    }
}
