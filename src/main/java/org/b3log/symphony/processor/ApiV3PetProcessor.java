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
import org.b3log.symphony.service.ApiV3PetService;
import org.b3log.symphony.service.ApiV3PetRiskService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

import java.util.Set;
import java.util.Locale;

/** 第一批入口仅第一方本人可用，不放开匿名盾、Cookie 或旧 apiKey。 */
@Singleton
public class ApiV3PetProcessor {
    @Inject private ApiV3PetService pets;
    @Inject private ApiV3PetRiskService risk;

    public static void register() {
        final var beans = BeanManager.getInstance();
        registerRoutes(beans.getReference(ApiV3PetProcessor.class), beans.getReference(ApiV3AuthMidware.class),
                beans.getReference(ApiV3WriteMidware.class));
    }

    public static void registerRoutes(final ApiV3PetProcessor processor, final ApiV3AuthMidware auth,
                                      final ApiV3WriteMidware writes) {
        // 宠物写入使用持久化账号额度；已成功原键回执先恢复，不受共享写额度误拦。
        Dispatcher.get("/api/v3/me/pet/risk", processor::riskState, auth::settingsWrite);
        Dispatcher.post("/api/v3/me/pet/risk/challenge", processor::challenge, auth::settingsWrite);
        Dispatcher.post("/api/v3/me/pet/risk/verify", processor::verify, auth::settingsWrite);
        Dispatcher.get("/api/v3/pets/catalog", processor::catalog, auth::profileRead);
        Dispatcher.get("/api/v3/me/pet", processor::read, auth::profileRead);
        Dispatcher.post("/api/v3/me/pet/adoption", processor::adopt, auth::settingsWrite);
        Dispatcher.post("/api/v3/me/pet/readoption", processor::readopt, auth::settingsWrite);
        Dispatcher.post("/api/v3/me/pet/stories/claim", processor::claimStory, auth::settingsWrite);
        Dispatcher.put("/api/v3/me/pet/name", processor::rename, auth::settingsWrite);
        Dispatcher.post("/api/v3/me/pet/actions", processor::action, auth::settingsWrite);
        Dispatcher.post("/api/v3/me/pet/supplies", processor::supplies, auth::settingsWrite);
        Dispatcher.post("/api/v3/me/pet/purchases", processor::purchase, auth::settingsWrite);
        Dispatcher.post("/api/v3/me/pet/crafts", processor::craft, auth::settingsWrite);
        Dispatcher.get("/api/v3/me/pet/journal", processor::journal, auth::profileRead);
        Dispatcher.get("/api/v3/me/pet/activities", processor::history, auth::profileRead);
        Dispatcher.post("/api/v3/me/pet/activities", processor::startActivity, auth::settingsWrite);
        Dispatcher.post("/api/v3/me/pet/activities/claim", processor::claimActivity, auth::settingsWrite);
    }

    public void catalog(final RequestContext context) {
        ApiV3Requests.read(context, () -> pets.catalog(identity(context)));
    }

    public void read(final RequestContext context) {
        ApiV3Requests.read(context, () -> pets.read(identity(context)));
    }

    public void adopt(final RequestContext context) {
        respond(context, () -> pets.adopt(identity(context),
                body(context, Set.of("speciesId", "name")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void readopt(final RequestContext context) {
        respond(context, () -> pets.readopt(identity(context),
                body(context, Set.of("petId", "speciesId", "name", "catalogVersion", "resetAccepted")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void claimStory(final RequestContext context) {
        respond(context, () -> pets.claimStory(identity(context),
                body(context, Set.of("petId", "storyId", "catalogVersion")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void rename(final RequestContext context) {
        respond(context, () -> pets.rename(identity(context),
                body(context, Set.of("petId", "name")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void action(final RequestContext context) {
        respond(context, () -> pets.action(identity(context),
                body(context, Set.of("petId", "action", "itemId")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void supplies(final RequestContext context) {
        respond(context, () -> pets.supplies(identity(context),
                body(context, Set.of("expectedDate")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void purchase(final RequestContext context) {
        respond(context, () -> pets.purchase(identity(context),
                body(context, Set.of("petId", "itemId", "quantity", "catalogVersion")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void craft(final RequestContext context) {
        respond(context, () -> pets.craft(identity(context),
                body(context, Set.of("petId", "recipeId", "catalogVersion")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void journal(final RequestContext context) {
        ApiV3Requests.read(context, () -> pets.journal(identity(context),
                ApiV3Requests.choice(context, "kind", "all", Set.of("all", "outing", "course", "work", "care", "craft", "purchase", "story", "readoption")),
                ApiV3Requests.number(context, "p", 1, 10_000), ApiV3Requests.number(context, "size", 10, 50)));
    }

    public void history(final RequestContext context) {
        ApiV3Requests.read(context, () -> pets.history(identity(context),
                ApiV3Requests.choice(context, "kind", "all", Set.of("all", "outing", "course", "work")),
                ApiV3Requests.number(context, "p", 1, 10_000), ApiV3Requests.number(context, "size", 10, 50)));
    }

    public void startActivity(final RequestContext context) {
        respond(context, () -> pets.startActivity(identity(context),
                body(context, Set.of("petId", "kind", "entryId", "activityVersion")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void claimActivity(final RequestContext context) {
        respond(context, () -> pets.claimActivity(identity(context),
                body(context, Set.of("petId", "activityId")), ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void riskState(final RequestContext context) {
        respond(context, () -> risk.state(identity(context)));
    }

    public void challenge(final RequestContext context) {
        respond(context, () -> {
            final JSONObject input = body(context, Set.of("operation", "idempotencyKey"));
            return risk.challenge(identity(context), ApiV3WriteInputs.string(input, "operation", 32, false),
                    ApiV3WriteInputs.string(input, "idempotencyKey", 128, false));
        });
    }

    public void verify(final RequestContext context) {
        respond(context, () -> {
            final JSONObject input = ApiV3WriteInputs.body(context, Set.of("challengeId", "captcha"), false, 8192);
            return risk.verify(identity(context), ApiV3WriteInputs.string(input, "challengeId", 36, false),
                    ApiV3WriteInputs.string(input, "captcha", 4096, false));
        });
    }

    private static void respond(final RequestContext context, final ApiV3Requests.ReadOperation operation) {
        ApiV3Requests.respond(context, () -> {
            try {
                return operation.run();
            } catch (ApiV3Exception error) {
                if ("pet_rate_limited".equals(error.code)) {
                    context.getResponse().setHeader("Retry-After", "60");
                    // 只扩展宠物限流响应的可读头，保留全站已有的请求关联头。
                    final String exposed = context.getResponse().getHeader("Access-Control-Expose-Headers");
                    if (exposed == null || exposed.isBlank()) context.getResponse().setHeader("Access-Control-Expose-Headers", "Retry-After");
                    else if (!exposed.toLowerCase(Locale.ROOT).contains("retry-after"))
                        context.getResponse().setHeader("Access-Control-Expose-Headers", exposed + ", Retry-After");
                }
                throw error;
            }
        });
    }

    private static JSONObject body(final RequestContext context, final Set<String> fields) {
        return ApiV3WriteInputs.body(context, fields, false, 2048);
    }

    private static JSONObject identity(final RequestContext context) {
        return (JSONObject) context.attr(ApiV3Processor.IDENTITY);
    }
}
