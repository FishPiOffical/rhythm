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
package org.b3log.symphony.service;

import io.netty.handler.codec.http.*;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.http.handler.RouteHandler;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.processor.ApiV3SettingsProcessor;
import org.b3log.symphony.repository.SystemSettingsRepository;
import org.json.JSONObject;
import org.testng.Assert;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static org.b3log.symphony.service.ApiV3QqSettingsTest.field;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

public class ApiV3CardBackgroundTest {

    static final String PATH = "/api/v3/me/settings/card-background";
    static final String LEGACY = "/upload/legacy.png";
    static final String UPLOADED = "https://cdn.example/card.webp?sig=abc%2Bdef&size=800";

    record Result(int status, JSONObject body, String cacheControl) { }

    static final class Fixture extends ApiV3IdentityApplicationTest.Fixture {
        final ApiV3SettingsMgmtService backgrounds = new ApiV3SettingsMgmtService();
        final ApiV3SettingsProcessor endpoint = new ApiV3SettingsProcessor();
        final SystemSettingsRepository legacy = new SystemSettingsRepository() {
            public String getName() {
                return "system_settings";
            }

            public List<JSONObject> select(final String query, final Object... args) throws RepositoryException {
                return selectSQL(query, args);
            }

            public String add(final JSONObject row) throws RepositoryException {
                Assert.assertTrue(active());
                sql(writer, "INSERT INTO system_settings VALUES('new',?,?)", row.getString("userId"), row.getString("settings"));
                return "new";
            }

            public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
                Assert.assertTrue(active());
                Assert.assertEquals(fields, new String[]{"settings"});
                sql(writer, "UPDATE system_settings SET settings=? WHERE oId=?", row.getString("settings"), id);
            }
        };

        Fixture() throws Exception {
            sql(db, "CREATE TABLE system_settings(oId TEXT PRIMARY KEY,userId TEXT,settings TEXT)");
            sql(db, "INSERT INTO system_settings VALUES('old','1',?)",
                    new JSONObject().put("cardBg", LEGACY).put("theme", "dark").toString());
            final var idempotency = new ApiV3IdempotencyMgmtService();
            inject(idempotency, "requestRepository", field(service, ApiV3IdentityApplicationService.class, "requests"));
            inject(backgrounds, "idempotency", idempotency);
            inject(backgrounds, "users", field(proof, ApiV3SecurityProofService.class, "users"));
            inject(backgrounds, "systemSettings", legacy);
            inject(endpoint, "management", backgrounds);
            ApiV3SettingsProcessor.registerRoutes(endpoint, auth, writes);
            Dispatcher.mapping();
        }

        JSONObject stored(final String actor) throws Exception {
            return new JSONObject(rows(db, "SELECT settings FROM system_settings WHERE userId=?", actor).getFirst().getString("settings"));
        }

        Result cardRequest(final String token, final String key, final String body) throws Exception {
            final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, body == null ? HttpMethod.GET : HttpMethod.PUT, PATH);
            try {
                if (token != null) raw.headers().set("Authorization", "Bearer " + token);
                if (key != null) raw.headers().set("Idempotency-Key", key);
                raw.headers().set("Cookie", "legacy");
                raw.headers().set("X-Real-IP", "127.0.0.1");
                raw.headers().set("Content-Type", "application/json");
                final byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
                raw.content().writeBytes(bytes);
                final var request = new Request(null, raw);
                request.setBytes(bytes);
                request.setParams(new HashMap<>());
                final var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                final var context = new RequestContext(request, new Response(null, response));
                context.addHandler(new RouteHandler());
                context.addHandler(c -> {
                    if (body == null) endpoint.cardBackground(c);
                    else endpoint.updateCardBackground(c);
                });
                context.handle();
                return new Result(response.status().code(), ((JsonRenderer) context.getRenderer()).getJSONObject(),
                        context.getResponse().getHeader("Cache-Control"));
            } finally {
                raw.release();
            }
        }
    }

    static JSONObject input(final String url) {
        return new JSONObject().put("cardBg", url);
    }

    static void init() {
        final var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
    }

    public static void main(final String[] args) throws Exception {
        init();
        try (var f = new Fixture()) {
            Assert.assertEquals(f.cardRequest(null, null, null).status(), 401);
            Assert.assertEquals(f.cardRequest("scope", null, null).status(), 403);
            final var read = f.cardRequest("owner", null, null);
            Assert.assertEquals(read.status(), 200);
            Assert.assertEquals(read.cacheControl(), "no-store");
            Assert.assertTrue(read.body().getJSONObject("data").similar(input(LEGACY)));
            Assert.assertEquals(f.cardRequest("owner", null, input(UPLOADED).toString()).status(), 422);
            Assert.assertEquals(f.cardRequest("owner", "empty", "{}").status(), 422);
            Assert.assertEquals(f.cardRequest("owner", "wrong-owner", input(UPLOADED).put("userId", "2").toString()).status(), 422);
            for (String bad : List.of("http://cdn.example/card.png", "//cdn.example/card.png", " ", "https://user@cdn.example/card.png",
                    "https://cdn.example/evil'url", "https://cdn.example:65536/card.png")) {
                error(422, () -> f.backgrounds.updateCardBackground("1", input(bad), "invalid"));
            }

            Assert.assertEquals(f.cardRequest("owner", "save", input(UPLOADED).toString()).status(), 200);
            Assert.assertEquals(f.stored("1").getString("cardBg"), UPLOADED);
            Assert.assertEquals(f.stored("1").getString("theme"), "dark");
            Assert.assertEquals(ApiV3SettingsMgmtService.cardBackground(f.legacy, "1"), UPLOADED);
            Assert.assertEquals(f.cardRequest("owner", "clear", input("").toString()).status(), 200);
            Assert.assertEquals(f.stored("1").getString("cardBg"), "");
            Assert.assertEquals(f.stored("1").getString("theme"), "dark");
            Assert.assertEquals(f.cardRequest("owner", "save", input(UPLOADED).toString()).status(), 200);
            Assert.assertEquals(f.stored("1").getString("cardBg"), "");
            Assert.assertEquals(f.cardRequest("owner", "save", input("").toString()).status(), 409);
            Assert.assertEquals(f.backgrounds.readCardBackground("2").getString("cardBg"), "");
            f.backgrounds.updateCardBackground("2", input(UPLOADED), "save");
            Assert.assertEquals(f.stored("2").getString("cardBg"), UPLOADED);
            Assert.assertEquals(f.stored("1").getString("cardBg"), "");
        }
        System.out.println("PASS 旧背景读取、权限、格式校验、首次保存、恢复默认、设置保留及幂等重放");
    }
}
