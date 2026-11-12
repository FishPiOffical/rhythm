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
import org.b3log.symphony.processor.ApiV3PetProcessor;
import org.b3log.symphony.util.ApiV3PetActivities;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.b3log.symphony.service.ApiV3PetTest.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 第二批隔离持久化测试：读取无奖励、服务端计时、活动槽互斥、快照结算与故障恢复。 */
public class ApiV3PetActivitiesTest {
    static JSONObject start(final String petId, final String kind, final String entry) {
        return new JSONObject().put("petId", petId).put("kind", kind).put("entryId", entry).put("activityVersion", 5);
    }
    static JSONObject claim(final String petId, final String activityId) {
        return new JSONObject().put("petId", petId).put("activityId", activityId);
    }
    static String begin(final Fixture f, final String id, final String kind, final String entry) {
        return f.pets.startActivity(identity(), start(id, kind, entry), "start").getString("activityId");
    }
    static JSONObject pet(final Fixture f) { return f.state().getJSONObject("pet"); }
    static int skill(final Fixture f, final String name) {
        return pet(f).getJSONArray("skills").toList().stream().map(value -> new JSONObject((java.util.Map<?, ?>) value))
                .filter(row -> name.equals(row.getString("id"))).findFirst().orElseThrow().getInt("experience");
    }
    static JSONObject row(final Fixture f, final String id) throws Exception {
        return rows(f.db, "SELECT * FROM pet_activity WHERE oId=?", id).getFirst();
    }

    static void catalogAndEntries() throws Exception {
        try (var f = new Fixture()) {
            final JSONObject catalog = f.pets.catalog(identity());
            Assert.assertEquals(catalog.getInt("version"), 3);
            Assert.assertEquals(catalog.getInt("activityVersion"), 5);
            Assert.assertEquals(catalog.getJSONArray("skills").length(), 6);
            Assert.assertEquals(catalog.getJSONArray("places").length(), 6);
            Assert.assertEquals(catalog.getJSONArray("courses").length(), 12);
            f.adopt();
            Assert.assertEquals(pet(f).getJSONArray("skills").length(), 6);
            for (var definition : ApiV3PetActivities.SKILLS) Assert.assertEquals(skill(f, definition.id()), 0);
            Assert.assertEquals(f.count("pet_skill"), 0);
        }
        for (var entry : ApiV3PetActivities.ENTRIES) {
            try (var f = new Fixture()) {
                final String id = f.adopt(), activity = begin(f, id, entry.kind(), entry.id());
                Assert.assertEquals(f.coins(), 100 - entry.coins());
                Assert.assertEquals(pet(f).getInt("energy"), 100 - entry.energy());
                Assert.assertEquals(pet(f).getInt("hunger"), 100 - entry.hunger());
                Assert.assertEquals(pet(f).getInt("water"), 100 - entry.water());
                Assert.assertEquals(row(f, activity).getLong("endsAt") - f.clock, entry.minutes() * 60_000L);
                f.clock += entry.minutes() * 60_000L;
                f.pets.claimActivity(identity(), claim(id, activity), "claim");
                Assert.assertEquals(pet(f).getInt("experience"), entry.experience());
                Assert.assertEquals(skill(f, entry.skillId()), entry.skillExperience());
                Assert.assertEquals(f.food(), 6 + entry.food());
                Assert.assertEquals(f.coins(), 100 - entry.coins());
                Assert.assertTrue(pet(f).isNull("activity"));
            }
        }
        System.out.println("PASS 三地点六课程与六技能目录、全部费用和成果、既有宠物零技能投影");
    }

    static void timingAndSnapshot() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt(), activity = begin(f, id, "outing", "riverside");
            final long ends = row(f, activity).getLong("endsAt");
            f.clock = ends - 1;
            Assert.assertEquals(pet(f).getJSONObject("activity").getString("status"), "running");
            error(409, () -> f.pets.claimActivity(identity(), claim(id, activity), "too-early"));
            f.clock = ends;
            Assert.assertEquals(pet(f).getJSONObject("activity").getString("status"), "claimable");
            for (int i = 0; i < 20; i++) Assert.assertEquals(pet(f).getInt("experience"), 0);
            Assert.assertEquals(f.food(), 6);
            Assert.assertEquals(row(f, activity).getLong("claimedAt"), 0L);
            Assert.assertEquals(f.count("pet_ledger"), 2);
            // 模拟上线后目录已失效。已经付费的活动领取仍只使用启动快照。
            f.rejectActivityCatalog = true;
            f.clock += 3 * 86_400_000L;
            f.pets.claimActivity(identity(), claim(id, activity), "claim");
            Assert.assertEquals(pet(f).getInt("experience"), 20);
            Assert.assertEquals(pet(f).getInt("mood"), 38);
            Assert.assertEquals(skill(f, "exploration"), 6);
            Assert.assertEquals(f.food(), 7);
            Assert.assertEquals(pet(f).getJSONObject("growthToday").getInt("used"), 0);
            final JSONObject receipt = f.pets.claimActivity(identity(), claim(id, activity), "claim");
            f.clock -= 2 * 86_400_000L;
            Assert.assertTrue(receipt.similar(f.pets.claimActivity(identity(), claim(id, activity), "claim")));
            error(409, () -> f.pets.claimActivity(identity(), claim(id, activity), "different-key"));
            sql(f.db, "DELETE FROM receipt WHERE operation='pet.activity-claim'");
            error(409, () -> f.pets.claimActivity(identity(), claim(id, activity), "claim"));
            Assert.assertEquals(f.food(), 7);
        }
        try (var f = new Fixture()) {
            final String id = f.adopt(), activity = begin(f, id, "outing", "plaza");
            f.clock -= 60_000L;
            Assert.assertEquals(pet(f).getJSONObject("activity").getString("status"), "running");
            error(409, () -> f.pets.claimActivity(identity(), claim(id, activity), "rollback"));
            f.clock = row(f, activity).getLong("endsAt");
            f.pets.claimActivity(identity(), claim(id, activity), "claim");
            f.clock--;
            error(409, () -> f.pets.startActivity(identity(), start(id, "outing", "plaza"), "rollback-start"));
            Assert.assertEquals(f.count("pet_activity"), 1);
        }
        System.out.println("PASS 到期前1毫秒/恰到/多日离线、读取不发奖、冻结目录、跨日重放和时钟回拨");
    }

    static void slotAndIsolation() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            f.pets.action(identity(), action(id, "rest"), "rest");
            error(409, () -> begin(f, id, "outing", "plaza"));
            f.clock += 4 * 3_600_000L;
            final String activity = begin(f, id, "outing", "plaza");
            for (String command : List.of("feed", "water", "pet", "play", "rest", "wake"))
                error(409, () -> f.pets.action(identity(), action(id, command), "busy-" + command));
            error(409, () -> f.pets.startActivity(identity(), start(id, "course", "fitness-basics"), "second"));
            f.clock = row(f, activity).getLong("endsAt");
            error(409, () -> f.pets.action(identity(), action(id, "wake"), "wake-claimable"));
            error(409, () -> f.pets.startActivity(identity(), start(id, "course", "fitness-basics"), "third"));
            f.pets.claimActivity(identity(), claim(id, activity), "claim");
            final String next = f.pets.startActivity(identity(), start(id, "course", "fitness-basics"), "second").getString("activityId");
            error(409, () -> f.pets.claimActivity(identity(), claim(id, activity), "old-activity"));
            Assert.assertEquals(pet(f).getJSONObject("activity").getString("id"), next);
            error(404, () -> f.pets.claimActivity(identity(), claim("99", next), "old-pet"));
            error(404, () -> f.pets.startActivity(identity(), start("99", "outing", "plaza"), "old-start"));
            final JSONObject other = ApiV3IdentityApplicationTest.reviewer();
            final String otherPet = f.pets.adopt(other, adoption("另一个"), "adopt").getString("petId");
            error(404, () -> f.pets.claimActivity(other, claim(otherPet, next), "steal"));
            error(404, () -> f.pets.claimActivity(other, claim(id, next), "steal-pet"));
            Assert.assertEquals(f.pets.read(other).getJSONObject("pet").getJSONArray("skills").getJSONObject(0).getInt("experience"), 0);
            f.clock = row(f, next).getLong("endsAt");
            sql(f.db, "UPDATE tokens SET state=1 WHERE oId='100'");
            error(403, () -> f.pets.claimActivity(identity(), claim(id, next), "revoked"));
        }
        System.out.println("PASS 休息迁移/到期、照顾和醒来不可覆盖活动、待领取占槽、旧活动与换宠物/换号隔离");
    }

    static void validation() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            for (Object invalid : List.of("1", 1.5, 0, -1, true))
                error(422, () -> f.pets.startActivity(identity(), start(id, "outing", "plaza").put("activityVersion", invalid), "invalid-version"));
            error(409, () -> f.pets.startActivity(identity(), start(id, "outing", "plaza").put("activityVersion", 2), "old-version"));
            error(422, () -> f.pets.startActivity(identity(), start(id, "journey", "plaza"), "unknown-kind"));
            error(422, () -> f.pets.startActivity(identity(), start(id, "course", "plaza"), "unknown-course"));
            error(422, () -> f.pets.startActivity(identity(), start(id, "outing", "plaza").put("rewards", new JSONObject()), "forged"));
            error(422, () -> f.pets.claimActivity(identity(), claim(id, "not-an-id"), "bad-id"));
            Assert.assertEquals(f.coins(), 100); Assert.assertEquals(f.count("pet_activity"), 0);
            for (String field : List.of("energy", "hunger", "water")) {
                sql(f.db, "UPDATE user_pet SET " + field + "=0 WHERE oId=?", id);
                error(409, () -> begin(f, id, "outing", "plaza"));
                sql(f.db, "UPDATE user_pet SET " + field + "=100 WHERE oId=?", id);
            }
            sql(f.db, "UPDATE pet_account SET coins=2");
            error(409, () -> begin(f, id, "outing", "riverside"));
            Assert.assertEquals(f.count("pet_activity"), 0);
        }
        for (String corruption : List.of("bad-json", "unknown-skill", "negative-reward", "unknown-kind", "foreign-owner")) {
            try (var f = new Fixture()) {
                final String id = f.adopt(), activity = begin(f, id, "course", "fitness-basics");
                final JSONObject snapshot = new JSONObject(row(f, activity).getString("snapshotJson"));
                if (corruption.equals("unknown-skill")) snapshot.getJSONObject("entry").getJSONObject("rewards").getJSONArray("skills").getJSONObject(0).put("id", "evil");
                if (corruption.equals("negative-reward")) snapshot.getJSONObject("entry").getJSONObject("rewards").put("food", -1);
                sql(f.db, "UPDATE pet_activity SET snapshotJson=? WHERE oId=?", corruption.equals("bad-json") ? "bad" : snapshot.toString(), activity);
                if (corruption.equals("unknown-kind")) sql(f.db, "UPDATE pet_activity SET kind='journey'");
                if (corruption.equals("foreign-owner")) sql(f.db, "UPDATE pet_activity SET petId='99'");
                f.clock += 600_000L;
                error(503, () -> f.state());
                if (corruption.equals("foreign-owner")) error(404, () -> f.pets.claimActivity(identity(), claim(id, activity), "claim"));
                else error(503, () -> f.pets.claimActivity(identity(), claim(id, activity), "claim"));
                Assert.assertEquals(rows(f.db, "SELECT quantity FROM pet_inventory").getFirst().getInt("quantity"), 6);
                Assert.assertEquals(f.count("pet_skill"), 0);
            }
        }
        System.out.println("PASS 严格参数/报价/成本门槛、坏快照和归属异常不被当成空活动继续结算");
    }

    static void atomicity() throws Exception {
        for (String failure : List.of("INSERT INTO pet_activity", "UPDATE user_pet", "UPDATE pet_account", "INSERT INTO pet_ledger", "commit", "receipt")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                if (failure.startsWith("INSERT") || failure.startsWith("UPDATE")) f.writeFault = failure; else f.fault = failure;
                error(503, () -> begin(f, id, "course", "fitness-basics"));
                f.fault = ""; f.writeFault = "";
                Assert.assertEquals(f.coins(), 100); Assert.assertEquals(pet(f).getInt("energy"), 100);
                Assert.assertTrue(pet(f).isNull("activity")); Assert.assertEquals(f.count("pet_activity"), 0);
                Assert.assertEquals(f.count("receipt"), 1); Assert.assertEquals(f.count("pet_ledger"), 1);
                begin(f, id, "course", "fitness-basics");
                Assert.assertEquals(f.coins(), 95);
            }
        }
        for (String failure : List.of("INSERT INTO pet_skill", "UPDATE pet_inventory", "UPDATE user_pet", "UPDATE pet_activity", "INSERT INTO pet_ledger", "commit", "receipt")) {
            try (var f = new Fixture()) {
                final String id = f.adopt(), activity = begin(f, id, "outing", "riverside");
                f.clock += 900_000L;
                if (failure.startsWith("INSERT") || failure.startsWith("UPDATE")) f.writeFault = failure; else f.fault = failure;
                error(503, () -> f.pets.claimActivity(identity(), claim(id, activity), "claim"));
                f.fault = ""; f.writeFault = "";
                Assert.assertEquals(f.food(), 6); Assert.assertEquals(pet(f).getInt("experience"), 0);
                Assert.assertEquals(f.count("pet_skill"), 0); Assert.assertEquals(row(f, activity).getLong("claimedAt"), 0L);
                Assert.assertEquals(f.count("receipt"), 2); Assert.assertEquals(f.count("pet_ledger"), 2);
                f.pets.claimActivity(identity(), claim(id, activity), "claim");
                Assert.assertEquals(f.food(), 7); Assert.assertEquals(skill(f, "exploration"), 6);
            }
        }
        try (var f = new Fixture()) {
            final String id = f.adopt();
            f.fault = "ack";
            error(503, () -> begin(f, id, "outing", "riverside"));
            f.fault = "";
            final String activity = begin(f, id, "outing", "riverside");
            Assert.assertEquals(f.coins(), 97); Assert.assertEquals(f.count("pet_activity"), 1);
            f.clock += 900_000L;
            f.fault = "ack";
            error(503, () -> f.pets.claimActivity(identity(), claim(id, activity), "claim"));
            f.fault = "";
            f.pets.claimActivity(identity(), claim(id, activity), "claim");
            Assert.assertEquals(f.food(), 7); Assert.assertEquals(skill(f, "exploration"), 6);
            Assert.assertEquals(f.count("pet_ledger"), 3);
        }
        System.out.println("PASS 开始/领取各写入点与提交失败整体回滚、回包丢失原键恢复不重复扣费/发奖");
    }

    static void overflowAndProgress() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt(), activity = begin(f, id, "course", "fitness-basics");
            sql(f.db, "INSERT INTO pet_skill VALUES(?,?,?,?,?,?)", id + ":vitality", "1", id, "vitality", 95, f.clock);
            f.clock += 600_000L;
            f.pets.claimActivity(identity(), claim(id, activity), "claim");
            Assert.assertEquals(skill(f, "vitality"), 105);
            Assert.assertEquals(pet(f).getJSONArray("skills").getJSONObject(0).getInt("level"), 2);
            final String next = f.pets.startActivity(identity(), start(id, "course", "fitness-basics"), "next").getString("activityId");
            f.clock += 600_000L;
            sql(f.db, "UPDATE pet_skill SET experience=?", Integer.MAX_VALUE);
            Assert.assertEquals(pet(f).getJSONArray("skills").getJSONObject(0).getInt("level"), 30);
            error(503, () -> f.pets.claimActivity(identity(), claim(id, next), "overflow-skill"));
            Assert.assertEquals(pet(f).getInt("experience"), 15); Assert.assertEquals(row(f, next).getLong("claimedAt"), 0L);
            sql(f.db, "UPDATE pet_skill SET experience=105");
            sql(f.db, "UPDATE user_pet SET experience=?", Integer.MAX_VALUE);
            error(503, () -> f.pets.claimActivity(identity(), claim(id, next), "overflow-pet"));
            sql(f.db, "UPDATE user_pet SET experience=15");
            sql(f.db, "UPDATE pet_skill SET experience=-1");
            error(503, () -> f.state());
        }
        System.out.println("PASS 技能累加/升级/等级上限、整数溢出和坏进度回滚");
    }

    static void concurrency() throws Exception {
        for (boolean sameKey : List.of(false, true)) {
            try (var a = new Fixture(); var b = new Fixture(); var pool = Executors.newFixedThreadPool(2)) {
                b.db.close(); b.url = a.url; b.db = DriverManager.getConnection(a.url);
                final String id = a.adopt();
                final CountDownLatch starts = new CountDownLatch(1);
                final var x = pool.submit(() -> { starts.await(); return outcome(() -> a.pets.startActivity(identity(), start(id, "outing", "riverside"), "a")); });
                final var y = pool.submit(() -> { starts.await(); return outcome(() -> b.pets.startActivity(identity(), start(id, "outing", "riverside"), sameKey ? "a" : "b")); });
                starts.countDown();
                final var startOutcomes = List.of(x.get(), y.get());
                Assert.assertEquals(startOutcomes.stream().filter(code -> code == 200).count(), sameKey ? 2L : 1L);
                if (!sameKey) Assert.assertTrue(startOutcomes.contains(409));
                Assert.assertEquals(a.count("pet_activity"), 1); Assert.assertEquals(a.coins(), 97);
                final String activity = pet(a).getJSONObject("activity").getString("id");
                a.clock += 900_000L; b.clock = a.clock;
                final CountDownLatch claims = new CountDownLatch(1);
                final var first = pool.submit(() -> { claims.await(); return outcome(() -> a.pets.claimActivity(identity(), claim(id, activity), "claim-a")); });
                final var second = pool.submit(() -> { claims.await(); return outcome(() -> b.pets.claimActivity(identity(), claim(id, activity), sameKey ? "claim-a" : "claim-b")); });
                claims.countDown();
                final var outcomes = List.of(first.get(), second.get());
                Assert.assertEquals(outcomes.stream().filter(code -> code == 200).count(), sameKey ? 2L : 1L);
                if (!sameKey) Assert.assertTrue(outcomes.contains(409));
                Assert.assertEquals(a.food(), 7); Assert.assertEquals(skill(a, "exploration"), 6);
                Assert.assertEquals(pet(a).getInt("experience"), 20); Assert.assertEquals(a.count("pet_ledger"), 3);
            }
        }
        System.out.println("PASS 双连接同键/异键并发开始及领取：一次扣费、单槽、一次奖励");
    }

    static void routes() throws Exception {
        try (var f = new Fixture()) {
            ApiV3PetProcessor.registerRoutes(f.endpointPet, f.auth, f.writes);
            org.b3log.latke.http.Dispatcher.mapping();
            for (String path : List.of("activities", "activities/claim")) {
                Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/" + path, null, "anonymous", "{}").attr("test.status"), 401);
                Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/" + path, "scope", "scope", "{}").attr("test.status"), 403);
                Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/" + path, "owner", null, "{}").attr("test.status"), 422);
                Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/" + path, "owner", "bad", "{bad").attr("test.status"), 400);
            }
            final String id = f.adopt();
            final var response = f.httpPet("POST", "/api/v3/me/pet/activities", "owner", "start", start(id, "outing", "plaza").toString());
            Assert.assertEquals(response.attr("test.status"), 200);
            final String activity = ((JSONObject) response.attr("test.data")).getString("activityId");
            f.clock += 300_000L;
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/activities/claim", "owner", "claim", claim(id, activity).toString()).attr("test.status"), 200);
        }
        System.out.println("PASS 两新增路由真实HTTP、必需Bearer/scope/幂等键、JSON和成功回执");
    }

    public static void main(final String[] args) throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE"); props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props); Latkes.setLocalProps(props);
        catalogAndEntries(); timingAndSnapshot(); slotAndIsolation(); validation(); atomicity(); overflowAndProgress(); concurrency(); routes();
        System.out.println("PASS 宠物外出与学校第二批后端专项完成；SQLite不代替真实MySQL锁验收");
    }
}
