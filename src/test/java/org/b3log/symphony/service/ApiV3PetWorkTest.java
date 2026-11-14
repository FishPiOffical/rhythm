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
import org.b3log.symphony.util.ApiV3PetRules;
import org.b3log.symphony.util.ApiV3PetWork;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.b3log.symphony.service.ApiV3PetTest.*;
import static org.b3log.symphony.service.ApiV3PetActivitiesTest.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 第三批持久化回归：技能解锁、账号收入预算、实际成果日志和旧快照兼容。 */
public class ApiV3PetWorkTest {
    static void unlock(final Fixture f, final String id, final String skillId) throws Exception {
        sql(f.db, "INSERT INTO pet_skill VALUES(?,?,?,?,?,?)", id + ":" + skillId, "1", id, skillId, 10, f.clock);
    }
    static void budget(final Fixture f, final int earned) throws Exception {
        sql(f.db, "INSERT INTO pet_income_account VALUES(?,?,?,?)", "1", ApiV3PetRules.today(f.clock), earned, f.clock);
    }
    static JSONObject history(final Fixture f, final String kind, final int page, final int size) {
        return f.pets.history(identity(), kind, page, size);
    }
    static JSONObject actual(final Fixture f) {
        return history(f, "all", 1, 10).getJSONArray("list").getJSONObject(0).getJSONObject("actualRewards");
    }
    static JSONObject job(final Fixture f, final String jobId) {
        for (Object item : pet(f).getJSONArray("jobs")) {
            if (item instanceof JSONObject progress && jobId.equals(progress.getString("id"))) return progress;
        }
        throw new AssertionError("工作进度缺失");
    }
    static void finish(final Fixture f, final String petId, final String activityId, final String key) throws Exception {
        f.clock = row(f, activityId).getLong("endsAt");
        f.pets.claimActivity(identity(), claim(petId, activityId), key);
    }

    static void jobsAndLearning() throws Exception {
        try (var f = new Fixture()) {
            Assert.assertEquals(f.pets.catalog(identity()).getJSONArray("jobs").length(), 6);
            Assert.assertEquals(f.state().getJSONObject("income").getInt("remaining"), 60);
            Assert.assertEquals(history(f, "all", 1, 10).getJSONArray("list").length(), 0);
            Assert.assertEquals(history(f, "all", 1, 10).getJSONObject("pagination").getInt("pageCount"), 1);
            Assert.assertEquals(f.count("pet_income_account"), 0);
            final String id = f.adopt();
            Assert.assertEquals(pet(f).getJSONArray("jobs").length(), 6);
            // 明确失败后改成另一种活动属于新命令，不能复用被冻结的操作键。
            error(409, () -> f.pets.startActivity(identity(), start(id, "work", "delivery-assistant"), "locked-work"));
            final String course = begin(f, id, "course", "fitness-basics");
            finish(f, id, course, "learn");
            Assert.assertEquals(skill(f, "vitality"), 10);
            final String work = f.pets.startActivity(identity(), start(id, "work", "delivery-assistant"), "first-work").getString("activityId");
            finish(f, id, work, "work-claim");
            Assert.assertEquals(skill(f, "vitality"), 10);
            Assert.assertEquals(job(f, "delivery-assistant").getInt("experience"), 10);
            Assert.assertEquals(job(f, "delivery-assistant").getInt("completed"), 1);
            Assert.assertEquals(job(f, "library-assistant").getInt("experience"), 0);
            Assert.assertEquals(f.coins(), 107);
        }
        for (var definition : ApiV3PetWork.JOBS) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                unlock(f, id, definition.skillId());
                final String work = begin(f, id, "work", definition.id());
                Assert.assertEquals(pet(f).getInt("energy"), 88);
                Assert.assertEquals(pet(f).getInt("hunger"), 96);
                Assert.assertEquals(pet(f).getInt("water"), 94);
                Assert.assertEquals(f.coins(), 100);
                Assert.assertEquals(row(f, work).getLong("endsAt") - f.clock, 1_200_000L);
                finish(f, id, work, "claim");
                Assert.assertEquals(f.coins(), 112);
                Assert.assertEquals(pet(f).getInt("experience"), 15);
                Assert.assertEquals(job(f, definition.id()).getInt("experience"), 10);
                Assert.assertEquals(actual(f).getInt("coins"), 12);
                Assert.assertEquals(actual(f).getInt("proficiency"), 10);
                Assert.assertEquals(actual(f).getJSONArray("skills").length(), 0);
            }
        }
        System.out.println("PASS 六类工作真实课程解锁、独立熟练度、费用/成果与无产币读取");
    }

    static void sharedCapAndDates() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            unlock(f, id, "vitality"); unlock(f, id, "knowledge");
            budget(f, 55);
            final String work = begin(f, id, "work", "delivery-assistant");
            finish(f, id, work, "claim");
            Assert.assertEquals(f.coins(), 105);
            Assert.assertEquals(actual(f).getInt("coins"), 5);
            Assert.assertEquals(history(f, "work", 1, 10).getJSONArray("list").getJSONObject(0).getJSONObject("rewards").getInt("coins"), 12);
            Assert.assertEquals(f.state().getJSONObject("income").getInt("remaining"), 0);
            error(409, () -> f.pets.startActivity(identity(), start(id, "work", "library-assistant"), "full-cap"));
            Assert.assertEquals(begin(f, id, "work", "delivery-assistant"), work);
            f.pets.supplies(identity(), new JSONObject().put("expectedDate", ApiV3PetRules.today(f.clock)), "daily");
            Assert.assertEquals(f.coins(), 125); Assert.assertEquals(f.state().getJSONObject("income").getInt("earned"), 60);
            f.pets.claimActivity(identity(), claim(id, work), "claim");
            Assert.assertEquals(f.coins(), 125);
            sql(f.db, "DELETE FROM receipt WHERE operation='pet.activity-claim'");
            error(409, () -> f.pets.claimActivity(identity(), claim(id, work), "claim"));
            // 模拟未来更换宠物：熟练度跟随宠物，产币额度仍属于原账号。
            sql(f.db, "UPDATE user_pet SET oId='99'");
            Assert.assertEquals(job(f, "delivery-assistant").getInt("experience"), 0);
            Assert.assertEquals(f.state().getJSONObject("income").getInt("remaining"), 0);
            error(409, () -> f.pets.startActivity(identity(), start("99", "work", "delivery-assistant"), "replacement"));
        }
        try (var f = new Fixture()) {
            final String id = f.adopt(); unlock(f, id, "vitality");
            final String work = begin(f, id, "work", "delivery-assistant");
            budget(f, 60);
            finish(f, id, work, "claim");
            Assert.assertEquals(actual(f).getInt("coins"), 0); Assert.assertEquals(f.coins(), 100);
            Assert.assertEquals(job(f, "delivery-assistant").getInt("experience"), 10); Assert.assertTrue(pet(f).isNull("activity"));
        }
        try (var f = new Fixture()) {
            final String id = f.adopt(); unlock(f, id, "vitality");
            f.clock = LocalDate.parse(ApiV3PetRules.today(f.clock)).atTime(23, 50).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
            budget(f, 55);
            final String work = begin(f, id, "work", "delivery-assistant");
            finish(f, id, work, "claim");
            Assert.assertEquals(f.coins(), 112); Assert.assertEquals(f.state().getJSONObject("income").getInt("earned"), 12);
            Assert.assertEquals(f.state().getJSONObject("income").getString("date"), ApiV3PetRules.today(f.clock));
            f.clock -= 2 * 86_400_000L;
            // 即使换宠物重置计算时间，也不能重置账号级最高结算日。
            sql(f.db, "UPDATE user_pet SET lastCalculatedAt=?", f.clock);
            error(409, () -> f.pets.startActivity(identity(), start(id, "work", "delivery-assistant"), "rollback"));
            Assert.assertEquals(f.state().getJSONObject("income").getInt("earned"), 12);
            Assert.assertEquals(f.coins(), 112);
        }
        System.out.println("PASS 账号共享60上限、余5入5/满额已有工作入0、补给独立、跨日/回拨/换宠防重");
    }

    static void historyAndLegacy() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            for (int i = 0; i < 5; i++) {
                final String kind = i % 2 == 0 ? "outing" : "course";
                final String entry = kind.equals("outing") ? "plaza" : "fitness-basics";
                final String activity = f.pets.startActivity(identity(), start(id, kind, entry), "start-" + i).getString("activityId");
                finish(f, id, activity, "claim-" + i);
            }
            final JSONObject first = history(f, "all", 1, 2), second = history(f, "all", 2, 2), last = history(f, "all", 3, 2);
            Assert.assertEquals(first.getJSONObject("pagination").getInt("total"), 5);
            Assert.assertEquals(first.getJSONObject("pagination").getInt("pageCount"), 3);
            Assert.assertEquals(first.getJSONArray("list").length(), 2); Assert.assertEquals(last.getJSONArray("list").length(), 1);
            Assert.assertTrue(first.getJSONArray("list").getJSONObject(1).getLong("startedAt") > second.getJSONArray("list").getJSONObject(0).getLong("startedAt"));
            Assert.assertEquals(history(f, "course", 1, 10).getJSONObject("pagination").getInt("total"), 2);
            Assert.assertEquals(history(f, "outing", 1, 10).getJSONObject("pagination").getInt("total"), 3);
            Assert.assertEquals(history(f, "work", 1, 10).getJSONObject("pagination").getInt("total"), 0);
            Assert.assertEquals(history(f, "all", 9, 2).getJSONArray("list").length(), 0);
            final JSONObject other = f.pets.history(ApiV3IdentityApplicationTest.reviewer(), "all", 1, 10);
            Assert.assertEquals(other.getJSONObject("pagination").getInt("total"), 0);
            // 费用不同、事件时间恰好相同时仍用ID提供确定次序。
            for (JSONObject row : rows(f.db, "SELECT * FROM pet_activity")) {
                final long duration = row.getLong("endsAt") - row.getLong("startedAt");
                sql(f.db, "UPDATE pet_activity SET startedAt=?,endsAt=? WHERE oId=?", NOW, NOW + duration, row.getString("oId"));
            }
            final var items = history(f, "all", 1, 10).getJSONArray("list");
            for (int i = 1; i < items.length(); i++) Assert.assertTrue(items.getJSONObject(i - 1).getString("id").compareTo(items.getJSONObject(i).getString("id")) > 0);
            for (var item : items) Assert.assertEquals(((JSONObject) item).getString("status"), "claimed");
        }
        try (var f = new Fixture()) {
            final String id = f.adopt(), activity = begin(f, id, "outing", "plaza");
            final JSONObject snapshot = new JSONObject(row(f, activity).getString("snapshotJson"));
            snapshot.put("version", 1);
            snapshot.getJSONObject("entry").remove("requirements");
            snapshot.getJSONObject("entry").getJSONObject("rewards").remove("items");
            snapshot.getJSONObject("entry").getJSONObject("rewards").remove("coins");
            snapshot.getJSONObject("entry").getJSONObject("rewards").remove("proficiency");
            sql(f.db, "UPDATE pet_activity SET snapshotJson=? WHERE oId=?", snapshot.toString(), activity);
            final JSONObject running = history(f, "all", 1, 10).getJSONArray("list").getJSONObject(0);
            Assert.assertTrue(running.isNull("actualRewards")); Assert.assertEquals(running.getJSONObject("rewards").getInt("coins"), 0);
            finish(f, id, activity, "claim");
            final double mood = actual(f).getDouble("mood");
            Assert.assertTrue(mood > 0 && mood < 1, "实际心情只回复自然消耗的少量数值");
            Assert.assertEquals(f.count("pet_activity_result"), 1);
            // 旧版真实已领记录没有净增量，不能把承诺8心情伪装成实际8心情。
            sql(f.db, "DELETE FROM pet_activity_result");
            Assert.assertTrue(history(f, "all", 1, 10).getJSONArray("list").getJSONObject(0).isNull("actualRewards"));
            sql(f.db, "UPDATE pet_activity SET snapshotJson=?", snapshot.put("version", 2).toString());
            error(503, () -> history(f, "all", 1, 10));
        }
        for (String fault : List.of("missing", "bad-json", "wrong-owner", "bad-value")) {
            try (var f = new Fixture()) {
                final String id = f.adopt(); unlock(f, id, "vitality");
                final String work = begin(f, id, "work", "delivery-assistant"); finish(f, id, work, "claim");
                if (fault.equals("missing")) sql(f.db, "DELETE FROM pet_activity_result");
                if (fault.equals("bad-json")) sql(f.db, "UPDATE pet_activity_result SET actualJson='bad'");
                if (fault.equals("wrong-owner")) sql(f.db, "UPDATE pet_activity_result SET userId='2'");
                if (fault.equals("bad-value")) {
                    final JSONObject actual = actual(f).put("coins", 13);
                    sql(f.db, "UPDATE pet_activity_result SET actualJson=?", actual.toString());
                }
                error(503, () -> history(f, "all", 1, 10));
            }
        }
        System.out.println("PASS 日志实际分页/分类/归属/稳定排序、v1旧结果未知、净心情小数、新结果丢失不吞错");
    }

    static void atomicity() throws Exception {
        for (String failure : List.of("INSERT INTO pet_income_account", "UPDATE pet_account", "INSERT INTO pet_job_progress", "UPDATE pet_inventory", "UPDATE user_pet", "UPDATE pet_activity", "INSERT INTO pet_activity_result", "INSERT INTO pet_ledger", "commit", "receipt")) {
            try (var f = new Fixture()) {
                final String id = f.adopt(); unlock(f, id, "vitality");
                final String work = begin(f, id, "work", "delivery-assistant");
                f.clock += 1_200_000L;
                if (failure.startsWith("INSERT") || failure.startsWith("UPDATE")) f.writeFault = failure; else f.fault = failure;
                error(503, () -> f.pets.claimActivity(identity(), claim(id, work), "claim"));
                f.fault = ""; f.writeFault = "";
                Assert.assertEquals(f.coins(), 100); Assert.assertEquals(f.count("pet_income_account"), 0);
                Assert.assertEquals(f.count("pet_job_progress"), 0); Assert.assertEquals(f.count("pet_activity_result"), 0);
                Assert.assertEquals(pet(f).getInt("experience"), 0); Assert.assertEquals(row(f, work).getLong("claimedAt"), 0L);
                f.pets.claimActivity(identity(), claim(id, work), "claim");
                Assert.assertEquals(f.coins(), 112); Assert.assertEquals(job(f, "delivery-assistant").getInt("completed"), 1);
            }
        }
        for (String failure : List.of("UPDATE pet_income_account", "UPDATE pet_job_progress")) {
            try (var f = new Fixture()) {
                final String id = f.adopt(); unlock(f, id, "vitality"); budget(f, 12);
                sql(f.db, "INSERT INTO pet_job_progress VALUES(?,?,?,?,?,?,?)", id + ":delivery-assistant", "1", id, "delivery-assistant", 10, 1, f.clock);
                final String work = begin(f, id, "work", "delivery-assistant"); f.clock += 1_200_000L;
                f.writeFault = failure;
                error(503, () -> f.pets.claimActivity(identity(), claim(id, work), "claim"));
                f.writeFault = "";
                Assert.assertEquals(f.state().getJSONObject("income").getInt("earned"), 12);
                Assert.assertEquals(job(f, "delivery-assistant").getInt("completed"), 1); Assert.assertEquals(f.coins(), 100);
            }
        }
        try (var f = new Fixture()) {
            final String id = f.adopt(); unlock(f, id, "vitality");
            final String work = begin(f, id, "work", "delivery-assistant"); f.clock += 1_200_000L;
            f.fault = "ack";
            error(503, () -> f.pets.claimActivity(identity(), claim(id, work), "claim"));
            f.fault = "";
            f.pets.claimActivity(identity(), claim(id, work), "claim");
            Assert.assertEquals(f.coins(), 112); Assert.assertEquals(f.count("pet_activity_result"), 1);
            Assert.assertEquals(job(f, "delivery-assistant").getInt("completed"), 1);
        }
        System.out.println("PASS 收入/钱包/熟练度/成果各写入点失败同事务回滚，断流原键恢复不重复产币");
    }

    static void concurrencyAndOverflow() throws Exception {
        for (boolean sameKey : List.of(false, true)) {
            try (var a = new Fixture(); var b = new Fixture(); var pool = Executors.newFixedThreadPool(2)) {
                b.db.close(); b.url = a.url; b.db = DriverManager.getConnection(a.url);
                final String id = a.adopt(); unlock(a, id, "vitality"); budget(a, 55);
                final String work = begin(a, id, "work", "delivery-assistant");
                a.clock += 1_200_000L; b.clock = a.clock;
                final CountDownLatch latch = new CountDownLatch(1);
                final var x = pool.submit(() -> { latch.await(); return outcome(() -> a.pets.claimActivity(identity(), claim(id, work), "claim")); });
                final var y = pool.submit(() -> { latch.await(); return outcome(() -> b.pets.claimActivity(identity(), claim(id, work), sameKey ? "claim" : "claim-other")); });
                latch.countDown();
                final var outcomes = List.of(x.get(), y.get());
                Assert.assertEquals(outcomes.stream().filter(code -> code == 200).count(), sameKey ? 2L : 1L);
                if (!sameKey) Assert.assertTrue(outcomes.contains(409));
                Assert.assertEquals(a.coins(), 105); Assert.assertEquals(a.state().getJSONObject("income").getInt("earned"), 60);
                Assert.assertEquals(job(a, "delivery-assistant").getInt("completed"), 1); Assert.assertEquals(a.count("pet_activity_result"), 1);
            }
        }
        for (String field : List.of("experience", "completed")) {
            try (var f = new Fixture()) {
                final String id = f.adopt(); unlock(f, id, "vitality");
                sql(f.db, "INSERT INTO pet_job_progress VALUES(?,?,?,?,?,?,?)", id + ":delivery-assistant", "1", id, "delivery-assistant", 10, 1, f.clock);
                sql(f.db, "UPDATE pet_job_progress SET " + field + "=?", Integer.MAX_VALUE);
                final String work = begin(f, id, "work", "delivery-assistant"); f.clock += 1_200_000L;
                error(503, () -> f.pets.claimActivity(identity(), claim(id, work), "claim"));
                Assert.assertEquals(f.coins(), 100); Assert.assertEquals(f.count("pet_income_account"), 0);
                Assert.assertEquals(row(f, work).getLong("claimedAt"), 0L);
            }
        }
        System.out.println("PASS 同/异键双连接抢领部分额度只发一次、熟练度/履历计数溢出整体回滚");
    }

    static void routes() throws Exception {
        try (var f = new Fixture()) {
            ApiV3PetProcessor.registerRoutes(f.endpointPet, f.auth, f.writes);
            org.b3log.latke.http.Dispatcher.mapping();
            Assert.assertEquals(f.httpPet("GET", "/api/v3/me/pet/activities", null, null, null).attr("test.status"), 401);
            Assert.assertEquals(f.httpPet("GET", "/api/v3/me/pet/activities", "scope", null, null).attr("test.status"), 403);
            for (String query : List.of("kind=evil", "p=0", "p=10001", "size=51", "size=1.5"))
                Assert.assertEquals(f.httpPet("GET", "/api/v3/me/pet/activities?" + query, "owner", null, null).attr("test.status"), 422);
            final var response = f.httpPet("GET", "/api/v3/me/pet/activities?kind=work&p=2&size=3", "owner", null, null);
            Assert.assertEquals(response.attr("test.status"), 200);
            final var data = (JSONObject) response.attr("test.data");
            Assert.assertEquals(data.getJSONObject("pagination").getInt("page"), 2);
            Assert.assertEquals(data.getJSONObject("pagination").getInt("pageSize"), 3);
            Assert.assertEquals(f.count("pet_income_account"), 0);
        }
        System.out.println("PASS 日志GET真实路由鉴权、分类/分页约束、读取无副作用");
    }

    public static void main(final String[] args) throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE"); props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props); Latkes.setLocalProps(props);
        jobsAndLearning(); sharedCapAndDates(); historyAndLegacy(); atomicity(); concurrencyAndOverflow(); routes();
        System.out.println("PASS 宠物见习工作与日志第三批后端专项完成；MySQL运行态验收另行记录");
    }
}
