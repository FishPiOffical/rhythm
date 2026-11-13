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

import org.b3log.latke.Latkes;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.DriverManager;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.b3log.symphony.service.ApiV3PetTest.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 仅隔离 SQLite 和验证码供应商夹具，不连接真实账号、极验或正在运行的测试库。 */
public class ApiV3PetRiskTest {
    static void expect(final String code, final CallableAction action) throws Exception {
        try {
            action.run();
            Assert.fail("应拒绝操作：" + code);
        } catch (ApiV3Exception error) {
            Assert.assertEquals(error.code, code);
        }
    }

    static JSONObject riskState(final Fixture fixture) throws Exception {
        return new JSONObject(rows(fixture.db, "SELECT stateJson FROM pet_risk_state WHERE oId='1'").getFirst().getString("stateJson"));
    }

    static void writeRisk(final Fixture fixture, final JSONObject state) throws Exception {
        sql(fixture.db, "UPDATE pet_risk_state SET stateJson=? WHERE oId='1'", state.toString());
    }

    static String proof(final Fixture fixture, final String lot) {
        return new JSONObject().put("lot_number", lot).put("captcha_output", "fixture-output")
                .put("pass_token", "fixture-pass").put("gen_time", String.valueOf(fixture.clock / 1000)).toString();
    }

    static String trip(final Fixture fixture) throws Exception {
        fixture.enforceRisk = true;
        final String id = fixture.adopt();
        fixture.clock += 60_000;
        for (int index = 0; index < 8; index++) {
            fixture.clock += 1000;
            final int key = index;
            expect("already_full", () -> fixture.pets.action(identity(), action(id, "water"), "denial-" + key));
        }
        Assert.assertTrue(fixture.risk.state(identity()).getBoolean("verificationRequired"));
        expect("pet_verification_required", () -> fixture.pets.purchase(identity(), purchase(id, 1), "frozen-buy"));
        return id;
    }

    static void rateAndRecovery() throws Exception {
        try (var f = new Fixture()) {
            f.enforceRisk = true;
            final String id = f.adopt();
            final JSONObject original = f.pets.purchase(identity(), purchase(id, 1), "paid");
            f.clock += 60_000;
            for (int i = 0; i < 12; i++) f.risk.before(identity(), "name", "n-" + i, new JSONObject().put("name", "豚"));
            expect("pet_rate_limited", () -> f.risk.before(identity(), "name", "n-12", new JSONObject().put("name", "豚")));
            Assert.assertEquals(f.risk.state(identity()).getLong("retryAt"), f.clock + 60_000);
            Assert.assertTrue(f.pets.purchase(identity(), purchase(id, 1), "paid").similar(original));
            Assert.assertEquals(f.coins(), 95);
            Assert.assertEquals(f.count("pet_ledger"), 2);
            // 相同键、不同内容记录一次摘要冲突；不会再次扣款，也不误当普通恢复。
            expect("idempotency_conflict", () -> f.pets.purchase(identity(), purchase(id, 2), "paid"));
            expect("idempotency_conflict", () -> f.pets.purchase(identity(), purchase(id, 2), "paid"));
            Assert.assertEquals(rows(f.db, "SELECT * FROM pet_risk_audit WHERE reason='idempotency_conflict'").size(), 1);
            Assert.assertFalse(f.risk.state(ApiV3IdentityApplicationTest.reviewer()).getBoolean("verificationRequired"));
            f.risk.before(ApiV3IdentityApplicationTest.reviewer(), "name", "isolated", new JSONObject());
            f.clock += 60_001;
            f.risk.before(identity(), "name", "later", new JSONObject());
            final JSONObject state = riskState(f).put("verificationRequired", true).put("retryAt", f.clock + 60_000);
            writeRisk(f, state);
            Assert.assertTrue(f.pets.purchase(identity(), purchase(id, 1), "paid").similar(original));
            // 同键回放也受很高的传输保护，但不增加作弊评分。
            writeRisk(f, riskState(f).put("transportStarted", f.clock).put("transportCount", 239));
            f.pets.purchase(identity(), purchase(id, 1), "paid");
            expect("pet_rate_limited", () -> f.pets.purchase(identity(), purchase(id, 1), "paid"));
        }
        try (var f = new Fixture()) {
            f.enforceRisk = true;
            // 每十秒错开突发额度，独立覆盖每分钟的三十次上限。
            for (int i = 0; i < 30; i++) {
                if (i > 0 && i % 10 == 0) f.clock += 10_001;
                f.risk.before(identity(), "name", "m-" + i, new JSONObject());
            }
            expect("pet_rate_limited", () -> f.risk.before(identity(), "name", "m-30", new JSONObject()));
        }
        System.out.println("PASS 账号突发/分钟限流、同键弱网恢复、提交回执优先、摘要冲突去重、账号隔离与传输上限");
    }

    static void challengeAndBinding() throws Exception {
        try (var f = new Fixture()) {
            final String id = trip(f);
            final JSONObject challenge = f.risk.challenge(identity(), "purchase", "frozen-buy");
            Assert.assertEquals(challenge.getLong("serverNow"), f.clock);
            expect("pet_challenge_unavailable", () -> f.risk.challenge(identity(), "purchase", "never-blocked"));
            expect("pet_challenge_expired", () -> f.risk.verify(ApiV3IdentityApplicationTest.reviewer(), challenge.getString("challengeId"), proof(f, "wrong-actor")));
            expect("idempotency_conflict", () -> f.pets.purchase(identity(), purchase(id, 2), "frozen-buy"));
            final JSONObject verified = f.risk.verify(identity(), challenge.getString("challengeId"), proof(f, "unique-lot"));
            Assert.assertTrue(verified.getBoolean("verified"));
            Assert.assertEquals(f.coins(), 100);
            Assert.assertTrue(f.risk.state(identity()).getBoolean("verificationRequired"));
            expect("pet_verification_required", () -> f.pets.purchase(identity(), purchase(id, 1), "another-command"));
            final JSONObject lostAcknowledgement = f.risk.challenge(identity(), "purchase", "frozen-buy");
            Assert.assertTrue(lostAcknowledgement.getBoolean("verified"));
            Assert.assertTrue(f.risk.verify(identity(), challenge.getString("challengeId"), proof(f, "unique-lot")).getBoolean("verified"));
            Assert.assertEquals(f.captchaCalls, 1);
            f.pets.purchase(identity(), purchase(id, 1), "frozen-buy");
            Assert.assertFalse(f.risk.state(identity()).getBoolean("verificationRequired"));
            Assert.assertEquals(f.coins(), 95);
            f.clock += 16 * 60_000;
            f.pets.purchase(identity(), purchase(id, 1), "frozen-buy");
            Assert.assertEquals(f.coins(), 95);
            // 已消费的极验批次不可给其他账号的另一挑战使用。
            final JSONObject other = ApiV3IdentityApplicationTest.reviewer();
            for (int i = 0; i < 8; i++) {
                f.risk.before(other, "action", "reject-" + i, new JSONObject());
                f.risk.denied(other, "action", "reject-" + i, new JSONObject(), new ApiV3Exception(409, "already_full", "已饱"));
            }
            expect("pet_verification_required", () -> f.risk.before(other, "purchase", "other", new JSONObject()));
            final String another = f.risk.challenge(other, "purchase", "other").getString("challengeId");
            expect("invalid_captcha", () -> f.risk.verify(other, another, proof(f, "unique-lot")));
        }
        System.out.println("PASS 验证绑定用户/命令/摘要、验证不消费、丢回执恢复、原命令提交解锁、全局证明单用");
    }

    static void weakNetworkAndFailure() throws Exception {
        try (var f = new Fixture()) {
            f.enforceRisk = true;
            final String id = f.adopt();
            for (int i = 0; i < 20; i++) expect("already_full", () -> f.pets.action(identity(), action(id, "water"), "same-failure"));
            Assert.assertFalse(f.risk.state(identity()).getBoolean("verificationRequired"));
            Assert.assertEquals(riskState(f).getJSONArray("denied").length(), 1);
        }
        try (var f = new Fixture()) {
            final String id = trip(f);
            final String challenge = f.risk.challenge(identity(), "purchase", "frozen-buy").getString("challengeId");
            f.captchaUnavailable = true;
            expect("temporarily_unavailable", () -> f.risk.verify(identity(), challenge, proof(f, "network-failed")));
            Assert.assertTrue(f.risk.state(identity()).getBoolean("verificationRequired"));
            Assert.assertEquals(f.coins(), 100);
            f.captchaUnavailable = false;
            f.captchaValid = false;
            for (int i = 0; i < 5; i++) {
                final String lot = "bad-proof-" + i;
                expect("invalid_captcha", () -> f.risk.verify(identity(), challenge, proof(f, lot)));
            }
            final String replacement = f.risk.challenge(identity(), "purchase", "frozen-buy").getString("challengeId");
            Assert.assertNotEquals(replacement, challenge);
            expect("pet_challenge_expired", () -> f.risk.verify(identity(), challenge, proof(f, "old-challenge")));
            f.captchaValid = true;
            f.risk.verify(identity(), replacement, proof(f, "replacement-lot"));
            // 验证之后业务仍检查余额，失败不能清掉账号风险或消耗授权。
            sql(f.db, "UPDATE pet_account SET coins=0 WHERE oId='1'");
            expect("not_enough_coins", () -> f.pets.purchase(identity(), purchase(id, 1), "frozen-buy"));
            Assert.assertTrue(f.risk.state(identity()).getBoolean("verificationRequired"));
            sql(f.db, "UPDATE pet_account SET coins=100 WHERE oId='1'");
            f.pets.purchase(identity(), purchase(id, 1), "frozen-buy");
            Assert.assertFalse(f.risk.state(identity()).getBoolean("verificationRequired"));
        }
        try (var f = new Fixture()) {
            trip(f);
            final String challenge = f.risk.challenge(identity(), "purchase", "frozen-buy").getString("challengeId");
            final String stale = new JSONObject(proof(f, "stale-lot")).put("gen_time", String.valueOf((f.clock - 11 * 60_000) / 1000)).toString();
            expect("invalid_captcha", () -> f.risk.verify(identity(), challenge, stale));
            f.clock += 5 * 60_000;
            expect("pet_challenge_expired", () -> f.risk.verify(identity(), challenge, proof(f, "expired")));
            Assert.assertEquals(f.captchaCalls, 0);
        }
        System.out.println("PASS 同键明确失败不累计风险、供应商503保留命令、失败挑战换新、过期证明与授权失败回滚");
    }

    static void cooldownBeforeChallenge() throws Exception {
        try (var f = new Fixture()) {
            final String id = trip(f);
            writeRisk(f, riskState(f).put("retryAt", f.clock + 60_000));
            expect("pet_rate_limited", () -> f.pets.purchase(identity(), purchase(id, 1), "limited-before-challenge"));
            expect("pet_rate_limited", () -> f.risk.challenge(identity(), "purchase", "limited-before-challenge"));
            f.clock += 60_001;
            Assert.assertTrue(f.risk.state(identity()).getBoolean("verificationRequired"));
            final String challenge = f.risk.challenge(identity(), "purchase", "limited-before-challenge").getString("challengeId");
            f.risk.verify(identity(), challenge, proof(f, "after-cooldown"));
            f.pets.purchase(identity(), purchase(id, 1), "limited-before-challenge");
            Assert.assertEquals(f.coins(), 95);
            Assert.assertFalse(f.risk.state(identity()).getBoolean("verificationRequired"));
        }
        System.out.println("PASS 先429后需验证的原命令可在冷却后挑战并恢复，不接受未登记命令");
    }

    static void parallelAndRetention() throws Exception {
        try (var first = new Fixture(); var second = new Fixture(); var pool = Executors.newFixedThreadPool(2)) {
            first.enforceRisk = true; second.enforceRisk = true;
            second.db.close(); second.url = first.url; second.db = DriverManager.getConnection(first.url);
            final CountDownLatch start = new CountDownLatch(1);
            final var a = pool.submit(() -> { start.await(); int accepted = 0;
                for (int i = 0; i < 10; i++) if (outcome(() -> first.risk.before(identity(), "name", "a-" + System.nanoTime(), new JSONObject())) == 200) accepted++;
                return accepted;
            });
            final var b = pool.submit(() -> { start.await(); int accepted = 0;
                for (int i = 0; i < 10; i++) if (outcome(() -> second.risk.before(identity(), "name", "b-" + System.nanoTime(), new JSONObject())) == 200) accepted++;
                return accepted;
            });
            start.countDown();
            Assert.assertEquals(a.get() + b.get(), 12);
            Assert.assertTrue(second.risk.state(identity()).getLong("retryAt") > second.clock);
        }
        try (var f = new Fixture()) {
            f.adopt();
            for (int i = 0; i < 220; i++) {
                final int value = i;
                f.clock += 1000;
                expect("idempotency_conflict", () -> f.risk.before(identity(), "adoption", "adopt", adoption("不同内容" + value)));
            }
            Assert.assertEquals(f.count("pet_risk_audit"), 200);
            Assert.assertEquals(riskState(f).getJSONArray("conflicts").length(), 32);
            Assert.assertFalse(rows(f.db, "SELECT * FROM pet_risk_audit").toString().contains("不同内容"));
        }
        System.out.println("PASS 双连接共享额度与审计有界留存；日志不保存请求正文");
    }

    static void guardCommitRecovery() throws Exception {
        for (String fault : new String[]{"commit", "ack"}) {
            try (var f = new Fixture()) {
                f.enforceRisk = true;
                final String id = f.adopt();
                final int commands = riskState(f).getJSONArray("commands").length();
                f.riskCommitFault = fault;
                expect("temporarily_unavailable", () -> f.pets.purchase(identity(), purchase(id, 1), "guard-fault"));
                f.riskCommitFault = "";
                // 风险写入失败或回包丢失都不能先扣款；重试后只产生一笔真实消费。
                Assert.assertEquals(f.coins(), 100);
                Assert.assertEquals(f.food(), 6);
                Assert.assertEquals(riskState(f).getJSONArray("commands").length(), commands + (fault.equals("ack") ? 1 : 0));
                final JSONObject receipt = f.pets.purchase(identity(), purchase(id, 1), "guard-fault");
                Assert.assertTrue(receipt.similar(f.pets.purchase(identity(), purchase(id, 1), "guard-fault")));
                Assert.assertEquals(f.coins(), 95);
                Assert.assertEquals(f.food(), 7);
                Assert.assertEquals(riskState(f).getJSONArray("commands").length(), commands + 1);
            }
        }
        System.out.println("PASS 风险事务提交失败/已提交丢回包均可原键恢复，只有业务成功才扣款一次");
    }

    static void careAvailability() throws Exception {
        try (var f = new Fixture()) {
            f.adopt();
            JSONObject times = f.state().getJSONObject("pet").getJSONObject("careAvailableAt");
            Assert.assertEquals(times.getLong("feed"), f.clock + 30 * 60_000);
            Assert.assertEquals(times.getLong("water"), f.clock + 20 * 60_000);
            f.clock += 20 * 60_000;
            times = f.state().getJSONObject("pet").getJSONObject("careAvailableAt");
            Assert.assertEquals(times.getLong("feed"), f.clock + 10 * 60_000);
            Assert.assertEquals(times.getLong("water"), f.clock);
        }
        System.out.println("PASS 服务端小数状态给出真实喂食/饮水可用时间");
    }

    public static void main(final String[] args) throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE"); props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props); Latkes.setLocalProps(props);
        rateAndRecovery(); challengeAndBinding(); weakNetworkAndFailure(); cooldownBeforeChallenge(); parallelAndRetention(); guardCommitRecovery(); careAvailability();
        System.out.println("PASS 宠物账号保护隔离专项完成，真实极验及MySQL另行人工验收");
    }
}
