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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.symphony.processor.ApiV3PetProcessor;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.json.JSONObject;
import org.testng.Assert;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.b3log.symphony.service.ApiV3PetTest.action;
import static org.b3log.symphony.service.ApiV3PetTest.adoption;
import static org.b3log.symphony.service.ApiV3SecurityTest.sql;

/** 真实路由和中间件的隔离验证，不启动服务器，也不使用现网身份或验证码服务。 */
public class ApiV3PetRiskHttpTest {
    private record Endpoint(String method, String path, String body) { }

    private static JSONObject proof(final ApiV3PetTest.Fixture fixture) {
        return new JSONObject().put("lot_number", "http-risk-proof").put("captcha_output", "fixture-output")
                .put("pass_token", "fixture-pass").put("gen_time", Long.toString(fixture.clock / 1000));
    }

    private static List<Endpoint> endpoints(final ApiV3PetTest.Fixture fixture) {
        return List.of(new Endpoint("GET", "/api/v3/me/pet/risk", null),
                new Endpoint("POST", "/api/v3/me/pet/risk/challenge",
                        new JSONObject().put("operation", "action").put("idempotencyKey", "risk-http").toString()),
                new Endpoint("POST", "/api/v3/me/pet/risk/verify", new JSONObject()
                        .put("challengeId", "00000000-0000-4000-8000-000000000000").put("captcha", proof(fixture).toString()).toString()));
    }

    private static void identityBoundaries(final ApiV3PetTest.Fixture fixture) throws Exception {
        for (Endpoint endpoint : endpoints(fixture)) {
            // httpPet 总是带旧 Cookie 和 apiKey；没有 Bearer 时仍然必须拒绝。
            Assert.assertEquals(fixture.httpPet(endpoint.method(), endpoint.path(), null, null, endpoint.body()).attr("test.status"), 401);
            Assert.assertEquals(fixture.httpPet(endpoint.method(), endpoint.path(), "legacy", null, endpoint.body()).attr("test.status"), 401);
            Assert.assertEquals(fixture.httpPet(endpoint.method(), endpoint.path(), "scope", null, endpoint.body()).attr("test.status"), 403);
        }
        // 保留有效账号与 settings.write，把当前令牌改为第三方签发，确保不是仅靠 scope 放行。
        sql(fixture.db, "UPDATE tokens SET clientId='third' WHERE oId='100'");
        for (Endpoint endpoint : endpoints(fixture)) {
            Assert.assertEquals(fixture.httpPet(endpoint.method(), endpoint.path(), "owner", null, endpoint.body()).attr("test.status"), 403);
        }
        sql(fixture.db, "UPDATE tokens SET clientId='website_v3' WHERE oId='100'");
        Assert.assertEquals(fixture.count("pet_risk_state"), 0);
        Assert.assertEquals(fixture.count("pet_risk_audit"), 0);
        Assert.assertEquals(fixture.captchaCalls, 0);
        System.out.println("PASS 三个宠物保护端点拒绝匿名、旧 Cookie/apiKey、缺少 scope 和第三方令牌");
    }

    private static void readQuota(final ApiV3PetTest.Fixture fixture) throws Exception {
        for (int index = 0; index < 60; index++) {
            Assert.assertEquals(fixture.httpPet("GET", "/api/v3/me/pet/risk", "owner", null, null).attr("test.status"), 200);
        }
        final RequestContext limited = fixture.httpPet("GET", "/api/v3/me/pet/risk", "owner", null, null);
        Assert.assertEquals(limited.attr("test.status"), 429);
        Assert.assertEquals(limited.attr("test.code"), "pet_rate_limited");
        Assert.assertEquals(limited.getResponse().getHeader("Retry-After"), "60");
        Assert.assertTrue(limited.getResponse().getHeader("Access-Control-Expose-Headers").contains("Retry-After"),
                "浏览器跨域访问时也必须能够读取冷却时间");
        Assert.assertEquals(fixture.count("pet_risk_audit"), 0, "传输超频不能被记作作弊事件");
        fixture.clock += 61_000;
        final JSONObject recovered = (JSONObject) fixture.httpPet("GET", "/api/v3/me/pet/risk", "owner", null, null).attr("test.data");
        Assert.assertFalse(recovered.getBoolean("verificationRequired"));
        System.out.println("PASS 保护状态读取超限返回 429/Retry-After，到期可恢复且不升级作弊结论");
    }

    @SuppressWarnings("unchecked")
    private static void receiptBeforeSharedQuota(final ApiV3PetTest.Fixture fixture) throws Exception {
        final var field = ApiV3WriteMidware.class.getDeclaredField("COUNTS");
        field.setAccessible(true);
        final Cache<String, AtomicInteger> shared = (Cache<String, AtomicInteger>) field.get(null);
        shared.invalidateAll();
        try {
            // 直接模拟其他站点写操作已耗尽共享用户/IP额度，宠物自身保护额度与它独立。
            shared.put("user:1", new AtomicInteger(1000));
            shared.put("ip:127.0.0.1", new AtomicInteger(1000));
            final JSONObject input = adoption("HTTP测试豚");
            final RequestContext created = fixture.httpPet("POST", "/api/v3/me/pet/adoption", "owner", "http-adopt", input.toString());
            Assert.assertEquals(created.attr("test.status"), 200);
            final JSONObject receipt = (JSONObject) created.attr("test.data");
            final String petId = receipt.getString("petId");
            for (int index = 0; index < 11; index++) {
                final JSONObject rename = new JSONObject().put("petId", petId).put("name", "测试豚" + index);
                Assert.assertEquals(fixture.httpPet("PUT", "/api/v3/me/pet/name", "owner", "rename-" + index, rename.toString()).attr("test.status"), 200);
            }
            final JSONObject rename = new JSONObject().put("petId", petId).put("name", "超频测试豚");
            final RequestContext blocked = fixture.httpPet("PUT", "/api/v3/me/pet/name", "owner", "rename-blocked", rename.toString());
            Assert.assertEquals(blocked.attr("test.status"), 429);
            Assert.assertEquals(blocked.attr("test.code"), "pet_rate_limited");
            Assert.assertEquals(blocked.getResponse().getHeader("Retry-After"), "60");
            Assert.assertTrue(blocked.getResponse().getHeader("Access-Control-Expose-Headers").contains("Retry-After"));
            final int ledger = fixture.count("pet_ledger");
            final RequestContext replayed = fixture.httpPet("POST", "/api/v3/me/pet/adoption", "owner", "http-adopt", input.toString());
            Assert.assertEquals(replayed.attr("test.status"), 200, "成功原键回执不能被宠物业务冷却或共享写额度挡住");
            Assert.assertTrue(((JSONObject) replayed.attr("test.data")).similar(receipt));
            Assert.assertEquals(fixture.count("user_pet"), 1);
            Assert.assertEquals(fixture.count("pet_ledger"), ledger);
            Assert.assertEquals(shared.getIfPresent("user:1").get(), 1000);
            Assert.assertEquals(shared.getIfPresent("ip:127.0.0.1").get(), 1000);
            final RequestContext conflict = fixture.httpPet("POST", "/api/v3/me/pet/adoption", "owner", "http-adopt", adoption("另一只豚").toString());
            Assert.assertEquals(conflict.attr("test.status"), 409);
            Assert.assertEquals(conflict.attr("test.code"), "idempotency_conflict");
        } finally {
            shared.invalidateAll();
        }
        System.out.println("PASS 宠物自身限流保留 429，同时成功同键回执绕过共享写额度并保持内容冲突校验");
    }

    private static void challengeAndVerify(final ApiV3PetTest.Fixture fixture) throws Exception {
        fixture.clock += 61_000;
        final String petId = fixture.state().getJSONObject("pet").getString("id");
        // 八个不同的已知业务拒绝才触发验证，避免用修改风险表的方式绕过真实判断。
        final String water = action(petId, "water").toString();
        for (int index = 0; index < 8; index++) {
            final RequestContext denied = fixture.httpPet("POST", "/api/v3/me/pet/actions", "owner", "full-water-" + index, water);
            Assert.assertEquals(denied.attr("test.status"), 409);
            Assert.assertEquals(denied.attr("test.code"), "already_full");
        }
        final RequestContext required = fixture.httpPet("POST", "/api/v3/me/pet/actions", "owner", "risk-http", water);
        Assert.assertEquals(required.attr("test.status"), 403);
        Assert.assertEquals(required.attr("test.code"), "pet_verification_required");
        final String command = new JSONObject().put("operation", "action").put("idempotencyKey", "risk-http").toString();
        final RequestContext issued = fixture.httpPet("POST", "/api/v3/me/pet/risk/challenge", "owner", null, command);
        Assert.assertEquals(issued.attr("test.status"), 200);
        final JSONObject challenge = (JSONObject) issued.attr("test.data");
        Assert.assertEquals(challenge.getString("operation"), "action");
        Assert.assertEquals(challenge.getString("idempotencyKey"), "risk-http");
        Assert.assertEquals(challenge.getString("provider"), "geetest4");
        Assert.assertFalse(challenge.has("bodyHash"));
        final String payload = new JSONObject().put("challengeId", challenge.getString("challengeId"))
                .put("captcha", proof(fixture).toString()).toString();
        Assert.assertEquals(fixture.httpPet("POST", "/api/v3/me/pet/risk/verify", "reviewer", null, payload).attr("test.status"), 409,
                "别的账号不能使用当前挑战");
        Assert.assertEquals(fixture.captchaCalls, 0);
        final RequestContext verified = fixture.httpPet("POST", "/api/v3/me/pet/risk/verify", "owner", null, payload);
        Assert.assertEquals(verified.attr("test.status"), 200);
        final JSONObject granted = (JSONObject) verified.attr("test.data");
        Assert.assertTrue(granted.getBoolean("verified"));
        Assert.assertEquals(granted.getString("idempotencyKey"), "risk-http");
        Assert.assertEquals(fixture.httpPet("POST", "/api/v3/me/pet/risk/verify", "owner", null, payload).attr("test.status"), 200);
        Assert.assertEquals(fixture.captchaCalls, 1, "验证响应丢失后恢复不再消费证明或请求供应商");
        Assert.assertEquals(fixture.count("pet_risk_proof"), 1);
        System.out.println("PASS 真实 HTTP 挑战签发、账号绑定、验证及同挑战恢复，不暴露内部摘要");
    }

    public static void main(final String[] args) throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        try (var fixture = new ApiV3PetTest.Fixture()) {
            fixture.enforceRisk = true;
            ApiV3PetProcessor.registerRoutes(fixture.endpointPet, fixture.auth, fixture.writes);
            Dispatcher.mapping();
            identityBoundaries(fixture);
            readQuota(fixture);
            receiptBeforeSharedQuota(fixture);
            challengeAndVerify(fixture);
        }
        System.out.println("PASS 宠物账号保护 HTTP 边界专项完成");
    }
}
