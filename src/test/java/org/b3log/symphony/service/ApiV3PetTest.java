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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.processor.ApiV3PetProcessor;
import org.b3log.symphony.repository.PetAccountRepository;
import org.b3log.symphony.repository.PetRiskStateRepository;
import org.b3log.symphony.repository.PetRiskAuditRepository;
import org.b3log.symphony.repository.PetRiskProofRepository;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.repository.PetActivityRepository;
import org.b3log.symphony.repository.PetSkillRepository;
import org.b3log.symphony.repository.PetJobProgressRepository;
import org.b3log.symphony.repository.PetIncomeAccountRepository;
import org.b3log.symphony.repository.PetActivityResultRepository;
import org.b3log.symphony.repository.PetCollectionRepository;
import org.b3log.symphony.repository.PetJournalRepository;
import org.b3log.symphony.repository.PetStoryProgressRepository;
import org.b3log.symphony.repository.PetArchiveRepository;
import org.b3log.symphony.repository.PetInventoryRepository;
import org.b3log.symphony.repository.PetLedgerRepository;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3PetRules;
import org.json.JSONObject;
import org.testng.Assert;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 隔离 SQLite 运行真实服务、幂等事务和身份校验，绝不读取或修改本地真实账号。 */
public class ApiV3PetTest {
    static class Fixture extends ApiV3IdentityApplicationTest.Fixture {
        long clock = NOW;
        // 业务专项使用加速时钟，不把集中执行的测试操作误认为真实点击；保护专项启用正式额度。
        boolean enforceRisk;
        String riskCommitFault = "";
        boolean captchaValid = true;
        boolean captchaUnavailable;
        int captchaCalls;
        final ApiV3PetRiskService risk = new ApiV3PetRiskService() {
            protected long now() { return clock; }
            protected int minuteLimit() { return enforceRisk ? super.minuteLimit() : 100_000; }
            protected int burstLimit() { return enforceRisk ? super.burstLimit() : 100_000; }
            protected int denialLimit() { return enforceRisk ? super.denialLimit() : 100_000; }
            protected int transportLimit() { return enforceRisk ? super.transportLimit() : 100_000; }
        };
        boolean rejectName;
        boolean rejectActivityCatalog;
        final ApiV3PetService pets = new ApiV3PetService() {
            protected long now() { return clock; }
            protected JSONObject activityEntry(final String kind, final String id) {
                if (rejectActivityCatalog) throw new ApiV3Exception(503, "temporarily_unavailable", "测试目录已变更");
                return super.activityEntry(kind, id);
            }
            protected void checkName(final String name) {
                if (rejectName) throw new ApiV3Exception(422, "content_rejected", "测试审核拒绝");
            }
        };
        final ApiV3PetProcessor endpointPet = new ApiV3PetProcessor();

        Fixture() throws Exception {
            // IMMEDIATE 使并发测试有真正的写事务排队；生产按客户端、用户、回执、宠物顺序持行锁。
            url += "?busy_timeout=10000&transaction_mode=IMMEDIATE";
            sql(db, "CREATE TABLE pet_account(oId TEXT PRIMARY KEY,coins INT,supplyDate TEXT,growthDate TEXT,growthCount INT,createdAt BIGINT,updatedAt BIGINT)");
            sql(db, "CREATE TABLE user_pet(oId TEXT PRIMARY KEY,userId TEXT UNIQUE,speciesId TEXT,name TEXT,hunger DOUBLE,water DOUBLE,mood DOUBLE,energy DOUBLE,experience INT,friendship INT,lastCalculatedAt BIGINT,activityKind TEXT,activityStartedAt BIGINT,activityEndsAt BIGINT,petAvailableAt BIGINT,playAvailableAt BIGINT,adoptedAt BIGINT,updatedAt BIGINT)");
            sql(db, "CREATE TABLE pet_inventory(oId TEXT PRIMARY KEY,userId TEXT,itemId TEXT,quantity INT,updatedAt BIGINT,UNIQUE(userId,itemId))");
            sql(db, "CREATE TABLE pet_ledger(oId TEXT PRIMARY KEY,userId TEXT,petId TEXT,operation TEXT,coinDelta INT,foodDelta INT,createdAt BIGINT)");
            sql(db, "CREATE TABLE pet_activity(oId TEXT PRIMARY KEY,userId TEXT,petId TEXT,kind TEXT,entryId TEXT,slotKey TEXT UNIQUE,startedAt BIGINT,endsAt BIGINT,claimedAt BIGINT,snapshotJson TEXT)");
            sql(db, "CREATE TABLE pet_skill(oId TEXT PRIMARY KEY,userId TEXT,petId TEXT,skillId TEXT,experience INT,updatedAt BIGINT,UNIQUE(petId,skillId))");
            sql(db, "CREATE TABLE pet_job_progress(oId TEXT PRIMARY KEY,userId TEXT,petId TEXT,jobId TEXT,experience INT,completed INT,updatedAt BIGINT,UNIQUE(petId,jobId))");
            sql(db, "CREATE TABLE pet_income_account(oId TEXT PRIMARY KEY,date TEXT,earned INT,lastSettlementAt BIGINT)");
            sql(db, "CREATE TABLE pet_activity_result(oId TEXT PRIMARY KEY,userId TEXT,petId TEXT,actualJson TEXT)");
            sql(db, "CREATE TABLE pet_collection(oId TEXT PRIMARY KEY,userId TEXT,collectionId TEXT,unlockedAt BIGINT,UNIQUE(userId,collectionId))");
            sql(db, "CREATE TABLE pet_journal(oId TEXT PRIMARY KEY,userId TEXT,petId TEXT,kind TEXT,action TEXT,occurredAt BIGINT,eventJson TEXT)");
            sql(db, "CREATE TABLE pet_story_progress(oId TEXT PRIMARY KEY,userId TEXT,storyId TEXT,completedSteps INT,completedAt BIGINT,claimedAt BIGINT,snapshotJson TEXT,updatedAt BIGINT,UNIQUE(userId,storyId))");
            sql(db, "CREATE TABLE pet_archive(oId TEXT PRIMARY KEY,userId TEXT,retiredAt BIGINT,snapshotJson TEXT)");
            sql(db, "CREATE TABLE pet_risk_state(oId TEXT PRIMARY KEY,stateJson TEXT,updatedAt BIGINT)");
            sql(db, "CREATE TABLE pet_risk_audit(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,commandHash TEXT,bodyHash TEXT,reason TEXT,occurredAt BIGINT)");
            sql(db, "CREATE TABLE pet_risk_proof(oId TEXT PRIMARY KEY,userId TEXT,challengeId TEXT,expiresAt BIGINT)");
            inject(risk, "states", new PetRiskStateRepository() {
                public String getName() { return "pet_risk_state"; }
                public Transaction beginTransaction() {
                    final Transaction root = org.b3log.symphony.util.ApiV3Transactions.strict(begin());
                    return new Transaction() {
                        public String getId() { return root.getId(); }
                        public boolean isActive() { return root.isActive(); }
                        public void rollback() { root.rollback(); }
                        public void commit() {
                            // 风险短事务与业务提交分开注入故障，避免丢回包用例提前失败而漏测实际扣款恢复。
                            final String businessFault = fault;
                            fault = riskCommitFault;
                            try { root.commit(); }
                            finally { fault = businessFault; }
                        }
                    };
                }
                public boolean hasTransactionBegun() { return active(); }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_risk_state", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { modify("pet_risk_state", id, row, fields); }
            });
            inject(risk, "audits", new PetRiskAuditRepository() {
                public String getName() { return "pet_risk_audit"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_risk_audit", row); }
                public void remove(final String id) throws RepositoryException { sql(writer, "DELETE FROM pet_risk_audit WHERE oId=?", id); }
            });
            inject(risk, "proofUses", new PetRiskProofRepository() {
                public String getName() { return "pet_risk_proof"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_risk_proof", row); }
                public void remove(final String id) throws RepositoryException { sql(writer, "DELETE FROM pet_risk_proof WHERE oId=?", id); }
            });
            inject(risk, "requests", ApiV3QqSettingsTest.field(service, ApiV3IdentityApplicationService.class, "requests"));
            inject(risk, "proofs", proof);
            inject(risk, "options", new AccountCaptchaService() {
                public JSONObject options() { return new JSONObject().put("provider", "geetest4").put("captchaId", "fixture"); }
            });
            inject(risk, "captcha", new ApiV3PetCaptchaService() {
                public boolean verify(final String value) {
                    captchaCalls++;
                    if (captchaUnavailable) throw new ApiV3Exception(503, "temporarily_unavailable", "隔离供应商故障");
                    return captchaValid;
                }
            });
            inject(pets, "risk", risk);
            inject(endpointPet, "risk", risk);
            inject(pets, "storyProgress", new PetStoryProgressRepository() {
                public String getName() { return "pet_story_progress"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_story_progress", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { modify("pet_story_progress", id, row, fields); }
            });
            inject(pets, "archives", new PetArchiveRepository() {
                public String getName() { return "pet_archive"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_archive", row); }
            });
            inject(pets, "collections", new PetCollectionRepository() {
                public String getName() { return "pet_collection"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_collection", row); }
            });
            inject(pets, "journal", new PetJournalRepository() {
                public String getName() { return "pet_journal"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_journal", row); }
            });
            inject(pets, "jobs", new PetJobProgressRepository() {
                public String getName() { return "pet_job_progress"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_job_progress", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { modify("pet_job_progress", id, row, fields); }
            });
            inject(pets, "income", new PetIncomeAccountRepository() {
                public String getName() { return "pet_income_account"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_income_account", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { modify("pet_income_account", id, row, fields); }
            });
            inject(pets, "results", new PetActivityResultRepository() {
                public String getName() { return "pet_activity_result"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_activity_result", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { modify("pet_activity_result", id, row, fields); }
            });
            inject(pets, "activities", new PetActivityRepository() {
                public String getName() { return "pet_activity"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_activity", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { modify("pet_activity", id, row, fields); }
            });
            inject(pets, "skills", new PetSkillRepository() {
                public String getName() { return "pet_skill"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_skill", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { modify("pet_skill", id, row, fields); }
            });
            inject(pets, "accounts", new PetAccountRepository() {
                public String getName() { return "pet_account"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_account", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { modify("pet_account", id, row, fields); }
            });
            inject(pets, "pets", new UserPetRepository() {
                public String getName() { return "user_pet"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("user_pet", row); }
                public void remove(final String id) throws RepositoryException {
                    Assert.assertTrue(active());
                    sql(writer, "DELETE FROM user_pet WHERE oId=?", id);
                }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { modify("user_pet", id, row, fields); }
            });
            inject(pets, "inventory", new PetInventoryRepository() {
                public String getName() { return "pet_inventory"; }
                public List<JSONObject> select(final String q, final Object... a) throws RepositoryException { return selectSQL(q, a); }
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_inventory", row); }
                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException { modify("pet_inventory", id, row, fields); }
            });
            inject(pets, "ledger", new PetLedgerRepository() {
                public String add(final JSONObject row) throws RepositoryException { return insert("pet_ledger", row); }
            });
            inject(pets, "requests", ApiV3QqSettingsTest.field(service, ApiV3IdentityApplicationService.class, "requests"));
            inject(pets, "proofs", proof);
            inject(endpointPet, "pets", pets);
        }

        String insert(final String table, final JSONObject row) throws RepositoryException {
            Assert.assertTrue(active());
            final List<String> keys = new ArrayList<>(row.keySet());
            sql(writer, "INSERT INTO " + table + "(" + String.join(",", keys) + ") VALUES("
                    + String.join(",", Collections.nCopies(keys.size(), "?")) + ")", keys.stream().map(row::get).toArray());
            return row.getString("oId");
        }

        void modify(final String table, final String id, final JSONObject row, final String... fields) throws RepositoryException {
            Assert.assertTrue(active());
            final List<Object> values = new ArrayList<>();
            for (String field : fields) values.add(row.get(field));
            values.add(id);
            sql(writer, "UPDATE " + table + " SET " + String.join(",", Arrays.stream(fields).map(field -> field + "=?").toList())
                    + " WHERE oId=?", values.toArray());
        }

        int count(final String table) throws Exception { return rows(db, "SELECT COUNT(*) AS n FROM " + table).getFirst().getInt("n"); }
        JSONObject state() { return pets.read(identity()); }
        String adopt() { return pets.adopt(identity(), adoption("荷小豚"), "adopt").getString("petId"); }
        int coins() { return state().getJSONObject("wallet").getInt("coins"); }
        int food() {
            for (var value : state().getJSONArray("inventory")) {
                final JSONObject row = (JSONObject) value;
                if ("food".equals(row.getString("itemId"))) return row.getInt("quantity");
            }
            throw new AssertionError("口粮库存缺失");
        }

        RequestContext httpPet(final String method, final String path, final String token, final String key, final String body) throws Exception {
            final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), path);
            try {
                if (token != null) raw.headers().set("Authorization", "Bearer " + token);
                if (key != null) raw.headers().set("Idempotency-Key", key);
                raw.headers().set("Content-Type", "application/json");
                raw.headers().set("Cookie", "legacy");
                raw.headers().set("X-Real-IP", "127.0.0.1");
                final byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
                raw.content().writeBytes(bytes);
                final Request request = new Request(null, raw) {
                    public String getRemoteAddr() { return "127.0.0.1"; }
                };
                final var parse = Request.class.getDeclaredMethod("parseForm");
                parse.setAccessible(true);
                parse.invoke(request);
                request.setParams(new HashMap<>(Map.of("apiKey", "legacy")));
                new io.netty.handler.codec.http.QueryStringDecoder(path).parameters().forEach((name, values) -> request.setParameter(name, values.getFirst()));
                final var context = new RequestContext(request, new Response(null,
                        new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
                context.addHandler(new org.b3log.latke.http.handler.RouteHandler());
                context.addHandler(current -> {
                    final var match = (org.b3log.latke.http.handler.RouteResolution) current.attr("MATCH_RESULT");
                    switch (match.getContextHandlerMeta().getInvokeHolder().getName()) {
                        case "riskState" -> endpointPet.riskState(current);
                        case "challenge" -> endpointPet.challenge(current);
                        case "verify" -> endpointPet.verify(current);
                        case "catalog" -> endpointPet.catalog(current);
                        case "read" -> endpointPet.read(current);
                        case "adopt" -> endpointPet.adopt(current);
                        case "rename" -> endpointPet.rename(current);
                        case "action" -> endpointPet.action(current);
                        case "supplies" -> endpointPet.supplies(current);
                        case "purchase" -> endpointPet.purchase(current);
                        case "startActivity" -> endpointPet.startActivity(current);
                        case "claimActivity" -> endpointPet.claimActivity(current);
                        case "history" -> endpointPet.history(current);
                        case "craft" -> endpointPet.craft(current);
                        case "journal" -> endpointPet.journal(current);
                        case "claimStory" -> endpointPet.claimStory(current);
                        case "readopt" -> endpointPet.readopt(current);
                        default -> throw new AssertionError("未知宠物路由");
                    }
                });
                context.handle();
                return context;
            } finally {
                raw.release();
            }
        }
    }

    static JSONObject adoption(final String name) { return new JSONObject().put("speciesId", "hexiaotun").put("name", name); }
    static JSONObject action(final String id, final String action) {
        final JSONObject input = new JSONObject().put("petId", id).put("action", action);
        if (action.equals("feed")) input.put("itemId", "food");
        return input;
    }
    static JSONObject purchase(final String id, final int quantity) {
        return new JSONObject().put("petId", id).put("itemId", "food").put("quantity", quantity).put("catalogVersion", 3);
    }

    static void names() throws Exception {
        Assert.assertEquals(ApiV3PetRules.name("  豚豚  "), "豚豚");
        for (String token : List.of("豚", "👨‍👩‍👧‍👦", "👍🏽", "🇨🇳", "e\u0301")) {
            Assert.assertEquals(ApiV3PetRules.name(token.repeat(12)), token.repeat(12));
            error(422, () -> ApiV3PetRules.name(token.repeat(13)));
        }
        for (String name : List.of("", "   ", "\u00A0", "\u200D", "\u0301", "<img>", "\n豚", "a\u0000", "a\u202E", "a\u2066", "\uD800", "a" + "\u0301".repeat(256)))
            error(422, () -> ApiV3PetRules.name(name));
        System.out.println("PASS 名称字素、组合 Emoji、规范化及控制字符拒绝");
    }

    static void adoptionAndIsolation() throws Exception {
        try (var f = new Fixture()) {
            Assert.assertTrue(f.state().isNull("pet"));
            Assert.assertEquals(f.count("pet_account"), 0);
            Assert.assertEquals(f.pets.catalog(identity()).getJSONArray("species").length(), 4);
            final String id = f.adopt();
            Assert.assertEquals(f.coins(), 100); Assert.assertEquals(f.food(), 6);
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("energy"), 100);
            Assert.assertEquals(f.pets.adopt(identity(), adoption("荷小豚"), "adopt").getString("petId"), id);
            Assert.assertEquals(f.count("pet_ledger"), 1);
            error(409, () -> f.pets.adopt(identity(), adoption("另一只"), "adopt-again"));
            error(409, () -> f.pets.adopt(identity(), adoption("另一只"), "adopt"));
            error(422, () -> f.pets.adopt(identity(), adoption("小豚").put("userId", "2"), "forged"));
            Assert.assertTrue(f.pets.read(ApiV3IdentityApplicationTest.reviewer()).isNull("pet"));
            f.pets.adopt(ApiV3IdentityApplicationTest.reviewer(), adoption("第二只"), "second-owner");
            error(404, () -> f.pets.action(ApiV3IdentityApplicationTest.reviewer(), action(id, "pet"), "foreign-pet"));
            error(404, () -> f.pets.rename(identity(), new JSONObject().put("petId", "99").put("name", "旧宠物"), "stale-pet"));
            final JSONObject renamed = f.pets.rename(identity(), new JSONObject().put("petId", id).put("name", "  豚豚  "), "name");
            Assert.assertEquals(renamed.length(), 3);
            Assert.assertEquals(f.state().getJSONObject("pet").getString("name"), "豚豚");
            sql(f.db, "UPDATE tokens SET state=1 WHERE oId='100'");
            error(403, () -> f.pets.adopt(identity(), adoption("荷小豚"), "adopt"));
            error(403, () -> f.state());
        }
        try (var f = new Fixture()) {
            f.rejectName = true;
            error(422, () -> f.adopt());
            Assert.assertEquals(f.count("pet_account"), 0);
            Assert.assertEquals(f.count("receipt"), 0);
            error(403, () -> f.pets.catalog(null));
            final JSONObject third = new JSONObject().put("tokenId", "101").put("user", new JSONObject().put("oId", "1"));
            sql(f.db, "UPDATE tokens SET tokenType='access',scope='profile.read settings.write' WHERE oId='101'");
            error(403, () -> f.pets.adopt(third, adoption("豚"), "third"));
            sql(f.db, "UPDATE u SET userStatus=1 WHERE oId='1'");
            error(403, () -> f.adopt());
        }
        System.out.println("PASS 一账号一宠物、首奖仅一次、换号归属、撤销凭据、禁用账号与第三方拒绝");
    }

    static void economy() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            final JSONObject bought = f.pets.purchase(identity(), purchase(id, 2), "buy");
            Assert.assertEquals(f.coins(), 90); Assert.assertEquals(f.food(), 8);
            Assert.assertTrue(bought.similar(f.pets.purchase(identity(), purchase(id, 2), "buy")));
            Assert.assertEquals(f.coins(), 90);
            error(409, () -> f.pets.purchase(identity(), purchase(id, 20), "poor"));
            error(409, () -> f.pets.purchase(identity(), purchase(id, 1).put("catalogVersion", 1), "old-price"));
            error(422, () -> f.pets.purchase(identity(), purchase(id, 0), "zero"));
            error(422, () -> f.pets.purchase(identity(), purchase(id, 1).put("quantity", 1.5), "fraction"));
            error(422, () -> f.pets.purchase(identity(), purchase(id, 1).put("quantity", "1"), "string"));
            final JSONObject date = new JSONObject().put("expectedDate", ApiV3PetRules.today(f.clock));
            final JSONObject receipt = f.pets.supplies(identity(), date, "daily");
            Assert.assertEquals(f.coins(), 110); Assert.assertEquals(f.food(), 10);
            error(409, () -> f.pets.supplies(identity(), date, "second-daily"));
            f.clock += 86_400_000L;
            Assert.assertFalse(f.state().getJSONObject("dailySupply").getBoolean("claimed"));
            Assert.assertTrue(receipt.similar(f.pets.supplies(identity(), date, "daily")));
            Assert.assertEquals(f.coins(), 110);
            error(409, () -> f.pets.supplies(identity(), date, "yesterday-new-key"));
            f.pets.supplies(identity(), new JSONObject().put("expectedDate", ApiV3PetRules.today(f.clock)), "next-daily");
            Assert.assertEquals(f.coins(), 130); Assert.assertEquals(f.food(), 12);
            f.clock -= 86_400_000L;
            error(409, () -> f.pets.supplies(identity(), date, "clock-rollback"));
            Assert.assertEquals(f.coins(), 130);
        }
        for (String fault : List.of("INSERT INTO pet_ledger", "UPDATE pet_account")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                f.writeFault = fault;
                error(503, () -> f.pets.purchase(identity(), purchase(id, 2), "fault"));
                Assert.assertEquals(f.coins(), 100); Assert.assertEquals(f.food(), 6);
                Assert.assertEquals(f.count("receipt"), 1); Assert.assertEquals(f.count("pet_ledger"), 1);
                f.writeFault = "";
                f.pets.purchase(identity(), purchase(id, 2), "fault");
                Assert.assertEquals(f.coins(), 90); Assert.assertEquals(f.food(), 8);
            }
        }
        try (var f = new Fixture()) {
            final String id = f.adopt();
            f.fault = "ack";
            error(503, () -> f.pets.purchase(identity(), purchase(id, 2), "lost-ack"));
            f.fault = "";
            f.pets.purchase(identity(), purchase(id, 2), "lost-ack");
            Assert.assertEquals(f.coins(), 90); Assert.assertEquals(f.food(), 8);
        }
        System.out.println("PASS 购买去重/余额/报价、上海跨日补给、失败回滚、提交回包丢失同键恢复");
    }

    static void care() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            error(409, () -> f.pets.action(identity(), action(id, "feed"), "full-food"));
            error(409, () -> f.pets.action(identity(), action(id, "water"), "full-water"));
            f.pets.action(identity(), action(id, "pet"), "pet");
            error(409, () -> f.pets.action(identity(), action(id, "pet"), "pet-again"));
            f.pets.action(identity(), action(id, "play"), "play");
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("energy"), 90);
            // 密集读取不写回时间或小数；之后一个小时仍完整衰减 2/3/1 点。
            for (int i = 1; i <= 12; i++) { f.clock = NOW + i * 300_000L; f.state(); }
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("hunger"), 98);
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("water"), 97);
            Assert.assertEquals(rows(f.db, "SELECT lastCalculatedAt FROM user_pet").getFirst().getLong("lastCalculatedAt"), NOW);
            f.pets.action(identity(), action(id, "feed"), "feed");
            Assert.assertEquals(f.food(), 5);
            f.pets.action(identity(), action(id, "water"), "water");
            f.pets.action(identity(), action(id, "rest"), "rest");
            error(409, () -> f.pets.action(identity(), action(id, "feed"), "rest-feed"));
            error(409, () -> f.pets.action(identity(), action(id, "play"), "rest-play"));
            f.clock += 15 * 60_000L;
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("energy"), 95);
            f.pets.action(identity(), action(id, "wake"), "wake");
            Assert.assertTrue(f.state().getJSONObject("pet").isNull("activity"));
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("energy"), 95);
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("experience"), 40);
            for (int i = 0; i < 8; i++) {
                f.clock += ApiV3PetRules.CARE_COOLDOWN;
                f.pets.action(identity(), action(id, "pet"), "grow-" + i);
            }
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("experience"), 100);
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("friendship"), 20);
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("level"), 2);
            f.pets.action(identity(), action(id, "rest"), "auto-rest");
            f.clock += 5 * ApiV3PetRules.HOUR;
            Assert.assertTrue(f.state().getJSONObject("pet").isNull("activity"));
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("energy"), 100);
            f.clock += 30 * 86_400_000L;
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("hunger"), 0);
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("water"), 0);
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("energy"), 100);
            Assert.assertEquals(f.state().getJSONObject("pet").getJSONObject("growthToday").getInt("used"), 0);
            f.pets.action(identity(), action(id, "pet"), "new-day");
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("experience"), 110);
        }
        try (var f = new Fixture()) {
            final String id = f.adopt();
            f.clock += 19 * 60_000L;
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("water"), 100);
            error(409, () -> f.pets.action(identity(), action(id, "water"), "too-soon"));
            f.clock += 60_000L;
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("water"), 99);
            f.pets.action(identity(), action(id, "water"), "one-water-point");
            f.clock += 1;
            error(409, () -> f.pets.action(identity(), action(id, "water"), "tiny-water-decay"));
            error(409, () -> f.pets.action(identity(), action(id, "feed"), "tiny-food-decay"));
            f.clock = NOW + 30 * 60_000L;
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("hunger"), 99);
            f.pets.action(identity(), action(id, "feed"), "one-food-point");
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("experience"), 20);
            Assert.assertEquals(f.food(), 5);
        }
        // 连续短时写入也保留小数，避免每次不足一小时就把衰减抹掉。
        try (var f = new Fixture()) {
            final String id = f.adopt();
            for (int i = 1; i <= 12; i++) {
                f.clock = NOW + i * 300_000L;
                f.pets.action(identity(), action(id, "wake"), "settle-" + i);
            }
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("hunger"), 98);
            Assert.assertEquals(f.state().getJSONObject("pet").getInt("water"), 97);
            final double exact = rows(f.db, "SELECT hunger FROM user_pet").getFirst().getDouble("hunger");
            Assert.assertEquals(exact, 98.0, 1e-8);
        }
        System.out.println("PASS 离线/高频读写/小数余量、照顾冷却、休息中限制与自动醒来、每日成长上限");
    }

    static int outcome(final CallableAction operation) {
        try { operation.run(); return 200; }
        catch (ApiV3Exception error) { return error.status; }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    interface CallableAction { void run() throws Exception; }

    static void concurrent() throws Exception {
        try (var first = new Fixture(); var second = new Fixture(); var pool = Executors.newFixedThreadPool(2)) {
            second.db.close();
            second.url = first.url;
            second.db = DriverManager.getConnection(first.url);
            final CountDownLatch start = new CountDownLatch(1);
            final var a = pool.submit(() -> { start.await(); return outcome(() -> first.pets.adopt(identity(), adoption("甲"), "a")); });
            final var b = pool.submit(() -> { start.await(); return outcome(() -> second.pets.adopt(identity(), adoption("乙"), "b")); });
            start.countDown();
            Assert.assertEquals(Set.of(a.get(), b.get()), Set.of(200, 409));
            Assert.assertEquals(first.count("user_pet"), 1); Assert.assertEquals(first.coins(), 100);
            final String id = first.state().getJSONObject("pet").getString("id");
            final CountDownLatch buy = new CountDownLatch(1);
            final var p = pool.submit(() -> { buy.await(); return outcome(() -> first.pets.purchase(identity(), purchase(id, 20), "buy-a")); });
            final var q = pool.submit(() -> { buy.await(); return outcome(() -> second.pets.purchase(identity(), purchase(id, 20), "buy-b")); });
            buy.countDown();
            Assert.assertEquals(Set.of(p.get(), q.get()), Set.of(200, 409));
            Assert.assertEquals(first.coins(), 0); Assert.assertEquals(first.food(), 26);
            final CountDownLatch supply = new CountDownLatch(1);
            final JSONObject date = new JSONObject().put("expectedDate", ApiV3PetRules.today(first.clock));
            final var x = pool.submit(() -> { supply.await(); return outcome(() -> first.pets.supplies(identity(), date, "supply-a")); });
            final var y = pool.submit(() -> { supply.await(); return outcome(() -> second.pets.supplies(identity(), date, "supply-b")); });
            supply.countDown();
            Assert.assertEquals(Set.of(x.get(), y.get()), Set.of(200, 409));
            Assert.assertEquals(first.coins(), 20); Assert.assertEquals(first.food(), 28);
        }
        System.out.println("PASS 双连接并发领养、不同幂等键消费与同日补给唯一约束");
    }

    static void http() throws Exception {
        try (var f = new Fixture()) {
            ApiV3PetProcessor.registerRoutes(f.endpointPet, f.auth, f.writes);
            org.b3log.latke.http.Dispatcher.mapping();
            for (String path : List.of("adoption", "name", "actions", "supplies", "purchases")) {
                final String method = path.equals("name") ? "PUT" : "POST";
                Assert.assertEquals(f.httpPet(method, "/api/v3/me/pet/" + path, null, "anonymous", "{}").attr("test.status"), 401);
                Assert.assertEquals(f.httpPet(method, "/api/v3/me/pet/" + path, "scope", "scope", "{}").attr("test.status"), 403);
            }
            Assert.assertEquals(f.httpPet("GET", "/api/v3/me/pet", null, null, null).attr("test.status"), 401);
            Assert.assertEquals(f.httpPet("GET", "/api/v3/pets/catalog", null, null, null).attr("test.status"), 401);
            Assert.assertEquals(f.httpPet("GET", "/api/v3/me/pet", "scope", null, null).attr("test.status"), 403);
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/adoption", "owner", null, adoption("小豚").toString()).attr("test.status"), 422);
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/adoption", "owner", "http", "{bad-json").attr("test.status"), 400);
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/adoption", "owner", "http", adoption("小豚").put("userId", "2").toString()).attr("test.status"), 422);
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/adoption", "owner", "http", adoption("小豚").toString()).attr("test.status"), 200);
            Assert.assertEquals(f.count("user_pet"), 1);
            final String id = f.state().getJSONObject("pet").getString("id");
            Assert.assertEquals(f.httpPet("GET", "/api/v3/pets/catalog", "owner", null, null).attr("test.status"), 200);
            Assert.assertEquals(f.httpPet("PUT", "/api/v3/me/pet/name", "owner", "http-name",
                    new JSONObject().put("petId", id).put("name", "改名豚").toString()).attr("test.status"), 200);
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/actions", "owner", "http-care", action(id, "pet").toString()).attr("test.status"), 200);
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/purchases", "owner", "http-buy", purchase(id, 1).toString()).attr("test.status"), 200);
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/supplies", "owner", "http-supply",
                    new JSONObject().put("expectedDate", ApiV3PetRules.today(f.clock)).toString()).attr("test.status"), 200);
            Assert.assertEquals(f.coins(), 115);
            Assert.assertEquals(f.food(), 9);
            Assert.assertEquals(f.httpPet("GET", "/api/v3/me/pet", "reviewer", null, null).attr("test.status"), 200);
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/adoption", "owner", "oversize", " ".repeat(2049)).attr("test.status"), 413);
        }
        System.out.println("PASS 七路由显式鉴权链、旧Cookie/apiKey拒绝、严格JSON/字段/请求体/幂等键");
    }

    public static void main(final String[] args) throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE"); props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props); Latkes.setLocalProps(props);
        names(); adoptionAndIsolation(); economy(); care(); concurrent(); http();
        System.out.println("PASS 宠物第一批后端专项完成；真实MySQL与浏览器人工验收另行执行");
    }
}
