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
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.b3log.symphony.service.ApiV3PetTest.*;
import static org.b3log.symphony.service.ApiV3PetActivitiesTest.*;
import static org.b3log.symphony.service.ApiV3PetWorkTest.*;
import static org.b3log.symphony.service.ApiV3PetExplorationTest.item;
import static org.b3log.symphony.service.ApiV3PetExplorationTest.log;
import static org.b3log.symphony.service.ApiV3PetExplorationTest.latest;
import static org.b3log.symphony.service.ApiV3PetExplorationTest.crafting;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 第五批隔离持久化测试：顺序事件链只推进真实成功操作，换宠不重置账号奖励额度。 */
public class ApiV3PetProgressionTest {
    static JSONObject replacement(final String id) {
        return new JSONObject().put("petId", id).put("speciesId", "paixiaoyu").put("name", "新伙伴")
                .put("catalogVersion", 3).put("resetAccepted", true);
    }

    static JSONObject reward(final String id, final String story) {
        return new JSONObject().put("petId", id).put("storyId", story).put("catalogVersion", 3);
    }

    static JSONObject story(final Fixture f, final String id) {
        for (Object value : f.state().getJSONArray("stories")) {
            final JSONObject state = (JSONObject) value;
            if (id.equals(state.getString("id"))) return state;
        }
        throw new AssertionError("缺少事件链投影：" + id);
    }

    static void careReady(final Fixture f, final String id) throws Exception {
        sql(f.db, "UPDATE user_pet SET hunger=50,water=50 WHERE oId=?", id);
        f.pets.action(identity(), action(id, "feed"), "feed");
        f.pets.action(identity(), action(id, "water"), "water");
        f.pets.action(identity(), action(id, "play"), "play");
    }

    static String complete(final Fixture f, final String id, final String kind, final String entry, final String key) throws Exception {
        final String activity = f.pets.startActivity(identity(), start(id, kind, entry), key + "-start").getString("activityId");
        finish(f, id, activity, key + "-claim");
        return activity;
    }

    static void advancedCourses() throws Exception {
        final Map<String, String> courses = Map.of("fitness-advanced", "vitality", "nature-advanced", "exploration",
                "reading-advanced", "knowledge", "expression-advanced", "expression", "craft-advanced", "crafting", "manners-advanced", "kindness");
        try (var f = new Fixture()) {
            final JSONObject catalog = f.pets.catalog(identity());
            Assert.assertEquals(catalog.getInt("version"), 3);
            Assert.assertEquals(catalog.getInt("activityVersion"), 5);
            Assert.assertEquals(catalog.getJSONArray("courses").length(), 12);
            Assert.assertEquals(catalog.getJSONArray("stories").length(), 3);
            Assert.assertEquals(catalog.getJSONObject("readoptionRules").getInt("coins"), 512);
            Assert.assertEquals(f.state().getJSONArray("stories").length(), 3);
            Assert.assertEquals(f.count("pet_story_progress"), 0);
        }
        for (var course : courses.entrySet()) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                unlock(f, id, course.getValue());
                sql(f.db, "UPDATE pet_skill SET experience=19");
                error(409, () -> begin(f, id, "course", course.getKey()));
                sql(f.db, "UPDATE pet_skill SET experience=20");
                final String activity = begin(f, id, "course", course.getKey());
                Assert.assertEquals(f.coins(), 90);
                Assert.assertEquals(pet(f).getInt("energy"), 86);
                Assert.assertEquals(pet(f).getInt("hunger"), 95);
                Assert.assertEquals(pet(f).getInt("water"), 93);
                f.clock = row(f, activity).getLong("endsAt") - 1;
                error(409, () -> f.pets.claimActivity(identity(), claim(id, activity), "early"));
                f.clock++;
                Assert.assertEquals(story(f, "first-career").getInt("completedSteps"), 0);
                f.rejectActivityCatalog = true;
                f.pets.claimActivity(identity(), claim(id, activity), "claim");
                Assert.assertEquals(skill(f, course.getValue()), 50);
                Assert.assertEquals(pet(f).getInt("experience"), 45);
                Assert.assertEquals(item(f, "food"), 6);
                Assert.assertEquals(story(f, "first-career").getInt("completedSteps"), 1);
                f.pets.claimActivity(identity(), claim(id, activity), "claim");
                Assert.assertEquals(story(f, "first-career").getInt("completedSteps"), 1);
            }
        }
        System.out.println("PASS 六进阶课程门槛/费用/时间/冻结成果、到时读取不推进、领取重放不跳步骤");
    }

    static void orderedCareStory() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            sql(f.db, "UPDATE user_pet SET hunger=50,water=60");
            f.pets.action(identity(), action(id, "water"), "out-of-order-water");
            Assert.assertEquals(story(f, "care-companion").getInt("completedSteps"), 0);
            f.pets.action(identity(), action(id, "feed"), "first-feed");
            Assert.assertEquals(story(f, "care-companion").getInt("completedSteps"), 1);
            f.pets.action(identity(), action(id, "feed"), "first-feed");
            Assert.assertEquals(story(f, "care-companion").getInt("completedSteps"), 1);
            error(409, () -> f.pets.action(identity(), action(id, "water"), "full-water"));
            Assert.assertEquals(story(f, "care-companion").getInt("completedSteps"), 1);
            f.clock += ApiV3PetRules.HOUR;
            f.pets.action(identity(), action(id, "water"), "second-water");
            f.pets.action(identity(), action(id, "play"), "third-play");
            final JSONObject ready = story(f, "care-companion");
            Assert.assertEquals(ready.getInt("completedSteps"), 3);
            Assert.assertEquals(ready.getString("status"), "claimable");
            Assert.assertFalse(ready.isNull("completedAt"));
            Assert.assertTrue(ready.isNull("claimedAt"));
            Assert.assertEquals(item(f, "food"), 5);
            final JSONObject receipt = f.pets.claimStory(identity(), reward(id, "care-companion"), "reward");
            Assert.assertEquals(item(f, "food"), 7);
            Assert.assertEquals(f.coins(), 100);
            Assert.assertEquals(story(f, "care-companion").getString("status"), "claimed");
            Assert.assertTrue(receipt.similar(f.pets.claimStory(identity(), reward(id, "care-companion"), "reward")));
            error(409, () -> f.pets.claimStory(identity(), reward(id, "care-companion"), "other-key"));
            Assert.assertEquals(item(f, "food"), 7);
            Assert.assertEquals(latest(f, "story").getString("action"), "story-claim");
            Assert.assertEquals(latest(f, "story").getInt("coinsDelta"), 0);
        }
        try (var f = new Fixture()) {
            final String id = f.adopt();
            careReady(f, id);
            // 旧记录不回填，原键只读旧回执也不能成为新的事件。
            sql(f.db, "DELETE FROM pet_story_progress");
            Assert.assertEquals(story(f, "care-companion").getInt("completedSteps"), 0);
            f.pets.action(identity(), action(id, "feed"), "feed");
            Assert.assertEquals(story(f, "care-companion").getInt("completedSteps"), 0);
            Assert.assertEquals(f.count("pet_story_progress"), 0);
        }
        try (var f = new Fixture()) {
            final String id = f.adopt();
            careReady(f, id);
            final JSONObject snapshot = new JSONObject(rows(f.db, "SELECT snapshotJson FROM pet_story_progress WHERE storyId='care-companion'")
                    .getFirst().getString("snapshotJson"));
            // 模拟先前已冻结为另一档数量的合法旧目录，领奖不得用当前目录重新计奖。
            snapshot.getJSONObject("story").getJSONObject("rewards").getJSONArray("items").getJSONObject(0).put("quantity", 1);
            sql(f.db, "UPDATE pet_story_progress SET snapshotJson=? WHERE storyId='care-companion'", snapshot.toString());
            f.pets.claimStory(identity(), reward(id, "care-companion"), "frozen-reward");
            Assert.assertEquals(item(f, "food"), 6);
        }
        System.out.println("PASS 日常链顺序/失败/重放/不回填、完成只待领取、原键奖励唯一及真实日志");
    }

    static void activityStories() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            complete(f, id, "course", "nature-basics", "nature");
            Assert.assertEquals(story(f, "pond-keepsake").getInt("completedSteps"), 1);
            Assert.assertEquals(story(f, "first-career").getInt("completedSteps"), 1);
            complete(f, id, "outing", "lotus-pond", "pond");
            Assert.assertEquals(story(f, "pond-keepsake").getInt("completedSteps"), 2);
            // 补一节入门课达到20门槛，不会代替当前需要的进阶课。
            complete(f, id, "course", "nature-basics", "nature-again");
            Assert.assertEquals(story(f, "pond-keepsake").getInt("completedSteps"), 2);
            complete(f, id, "course", "nature-advanced", "advanced");
            Assert.assertEquals(story(f, "pond-keepsake").getString("status"), "claimable");
            Assert.assertEquals(story(f, "first-career").getInt("completedSteps"), 1);
            final int coins = f.coins();
            f.pets.claimStory(identity(), reward(id, "pond-keepsake"), "pond-reward");
            Assert.assertEquals(item(f, "fruit"), 2);
            Assert.assertEquals(item(f, "reed"), 4);
            Assert.assertEquals(f.coins(), coins);
        }
        try (var f = new Fixture()) {
            final String id = f.adopt();
            complete(f, id, "course", "fitness-basics", "fitness");
            complete(f, id, "work", "delivery-assistant", "work");
            Assert.assertEquals(story(f, "first-career").getInt("completedSteps"), 2);
            complete(f, id, "course", "fitness-basics", "fitness-again");
            complete(f, id, "course", "fitness-advanced", "advanced");
            final int earned = f.state().getJSONObject("income").getInt("earned");
            final int coins = f.coins();
            f.pets.claimStory(identity(), reward(id, "first-career"), "career-reward");
            Assert.assertEquals(item(f, "spring-water"), 2);
            Assert.assertEquals(item(f, "sea-shell"), 2);
            Assert.assertEquals(f.state().getJSONObject("income").getInt("earned"), earned);
            Assert.assertEquals(f.coins(), coins);
        }
        System.out.println("PASS 荷塘纪念与见习旅程三阶段真实活动链、材料奖励、奖励不占用或刷新产币额度");
    }

    static void readoptionPreservesAccount() throws Exception {
        try (var f = new Fixture()) {
            final String oldId = f.adopt();
            careReady(f, oldId);
            f.pets.claimStory(identity(), reward(oldId, "care-companion"), "care-reward");
            complete(f, oldId, "course", "fitness-basics", "learn");
            complete(f, oldId, "work", "delivery-assistant", "job");
            budgetReset(f, 60);
            sql(f.db, "UPDATE pet_account SET coins=1024,supplyDate=?,growthDate=?,growthCount=10", ApiV3PetRules.today(f.clock), ApiV3PetRules.today(f.clock));
            sql(f.db, "UPDATE user_pet SET petAvailableAt=?,playAvailableAt=?", f.clock + 300_000L, f.clock + 300_000L);
            sql(f.db, "INSERT INTO pet_collection VALUES(?,?,?,?)", "1:reed-boat", "1", "reed-boat", f.clock);
            final int food = item(f, "food"), historyCount = f.count("pet_activity"), journalCount = f.count("pet_journal");
            final JSONObject before = rows(f.db, "SELECT * FROM pet_account").getFirst();
            final JSONObject receipt = f.pets.readopt(identity(), replacement(oldId), "replace");
            final String id = receipt.getString("petId");
            Assert.assertNotEquals(id, oldId);
            Assert.assertEquals(receipt.getString("previousPetId"), oldId);
            Assert.assertEquals(f.count("user_pet"), 1);
            Assert.assertEquals(f.count("pet_archive"), 1);
            Assert.assertEquals(rows(f.db, "SELECT oId,userId FROM pet_archive").getFirst().getString("oId"), oldId);
            Assert.assertEquals(f.coins(), 512);
            Assert.assertEquals(item(f, "food"), food);
            Assert.assertEquals(f.state().getJSONArray("collections").length(), 1);
            for (String field : List.of("supplyDate", "growthDate", "growthCount", "createdAt"))
                Assert.assertEquals(rows(f.db, "SELECT * FROM pet_account").getFirst().get(field), before.get(field));
            Assert.assertEquals(f.state().getJSONObject("income").getInt("earned"), 60);
            Assert.assertEquals(story(f, "care-companion").getString("status"), "claimed");
            Assert.assertEquals(story(f, "first-career").getInt("completedSteps"), 2);
            Assert.assertEquals(pet(f).getInt("experience"), 0);
            Assert.assertEquals(pet(f).getInt("friendship"), 0);
            Assert.assertEquals(skill(f, "vitality"), 0);
            Assert.assertEquals(job(f, "delivery-assistant").getInt("experience"), 0);
            Assert.assertEquals(rows(f.db, "SELECT * FROM pet_job_progress WHERE petId=?", oldId).size(), 1);
            Assert.assertEquals(f.count("pet_activity"), historyCount);
            Assert.assertEquals(f.count("pet_journal"), journalCount + 1);
            Assert.assertEquals(history(f, "all", 1, 50).getJSONObject("pagination").getInt("total"), historyCount);
            Assert.assertEquals(latest(f, "readoption").getInt("coinsDelta"), -512);
            Assert.assertEquals(latest(f, "readoption").getJSONArray("itemChanges").length(), 0);
            error(409, () -> f.pets.adopt(identity(), adoption("再领首奖"), "new-adopt"));
            Assert.assertEquals(f.pets.adopt(identity(), adoption("荷小豚"), "adopt").getString("petId"), oldId);
            error(409, () -> f.pets.supplies(identity(), new JSONObject().put("expectedDate", ApiV3PetRules.today(f.clock)), "duplicate-supply"));
            error(409, () -> f.pets.claimStory(identity(), reward(id, "care-companion"), "duplicate-story"));
            error(409, () -> f.pets.action(identity(), action(id, "pet"), "cooldown"));
            sql(f.db, "UPDATE user_pet SET hunger=50 WHERE oId=?", id);
            f.pets.action(identity(), action(id, "feed"), "new-feed");
            Assert.assertEquals(pet(f).getInt("experience"), 0);
            Assert.assertEquals(pet(f).getInt("friendship"), 0);
            unlock(f, id, "vitality");
            error(409, () -> f.pets.startActivity(identity(), start(id, "work", "delivery-assistant"), "budget"));
            Assert.assertTrue(receipt.similar(f.pets.readopt(identity(), replacement(oldId), "replace")));
            Assert.assertEquals(f.coins(), 512);
            Assert.assertEquals(f.count("pet_archive"), 1);
        }
        System.out.println("PASS 换宠新ID/初始状态与旧宠归档、成长技能职业重置、钱包背包收藏和账号额度/履历保留");
    }

    static void budgetReset(final Fixture f, final int earned) throws Exception {
        sql(f.db, "UPDATE pet_income_account SET earned=?,date=?,lastSettlementAt=?", earned, ApiV3PetRules.today(f.clock), f.clock);
    }

    static void staleCommandsAndGates() throws Exception {
        try (var f = new Fixture()) {
            final String old = f.adopt();
            sql(f.db, "UPDATE pet_account SET coins=1024");
            error(422, () -> f.pets.readopt(identity(), replacement(old).put("resetAccepted", false), "not-accepted"));
            error(409, () -> f.pets.readopt(identity(), replacement(old).put("catalogVersion", 2), "old-price"));
            error(422, () -> f.pets.readopt(identity(), replacement(old).put("speciesId", "unknown"), "bad-species"));
            f.rejectName = true;
            error(422, () -> f.pets.readopt(identity(), replacement(old), "moderation"));
            f.rejectName = false;
            f.pets.action(identity(), action(old, "rest"), "rest");
            error(409, () -> f.pets.readopt(identity(), replacement(old), "busy-rest"));
            f.pets.action(identity(), action(old, "wake"), "wake");
            final String activity = begin(f, old, "outing", "plaza");
            error(409, () -> f.pets.readopt(identity(), replacement(old), "busy-running"));
            f.clock = row(f, activity).getLong("endsAt");
            error(409, () -> f.pets.readopt(identity(), replacement(old), "busy-claimable"));
            f.pets.claimActivity(identity(), claim(old, activity), "claim");
            sql(f.db, "UPDATE pet_account SET coins=511");
            error(409, () -> f.pets.readopt(identity(), replacement(old), "not-enough"));
            sql(f.db, "UPDATE pet_account SET coins=1024");
            final String id = f.pets.readopt(identity(), replacement(old), "replace").getString("petId");
            error(404, () -> f.pets.rename(identity(), new JSONObject().put("petId", old).put("name", "迟到改名"), "stale-name"));
            error(404, () -> f.pets.action(identity(), action(old, "water"), "stale-care"));
            error(404, () -> f.pets.purchase(identity(), purchase(old, 1), "stale-buy"));
            error(404, () -> f.pets.startActivity(identity(), start(old, "outing", "plaza"), "stale-start"));
            error(404, () -> f.pets.claimActivity(identity(), claim(old, activity), "stale-claim"));
            error(404, () -> f.pets.craft(identity(), crafting(old, "reed-boat"), "stale-craft"));
            error(404, () -> f.pets.claimStory(identity(), reward(old, "care-companion"), "stale-story"));
            error(404, () -> f.pets.readopt(identity(), replacement(old), "stale-replace"));
            // 已成功命令只恢复原回执，不重新授权其旧目标，也不会写到新宠物。
            f.pets.claimActivity(identity(), claim(old, activity), "claim");
            Assert.assertEquals(pet(f).getString("id"), id);
            Assert.assertEquals(pet(f).getInt("experience"), 0);
            Assert.assertEquals(f.coins(), 512);
        }
        try (var f = new Fixture()) {
            final String id = f.adopt();
            error(409, () -> f.pets.claimStory(identity(), reward(id, "care-companion"), "too-early"));
            careReady(f, id);
            f.pets.action(identity(), action(id, "rest"), "rest");
            error(409, () -> f.pets.claimStory(identity(), reward(id, "care-companion"), "busy"));
            f.pets.action(identity(), action(id, "wake"), "wake");
            final String activity = begin(f, id, "outing", "plaza");
            error(409, () -> f.pets.claimStory(identity(), reward(id, "care-companion"), "travelling"));
            finish(f, id, activity, "return");
            f.pets.claimStory(identity(), reward(id, "care-companion"), "reward");
        }
        System.out.println("PASS 明确重置确认/审核/报价/余额/活动槽、所有旧petId新命令拒绝、旧成功回执不污染新宠");
    }

    static void atomicityAndUnknownResults() throws Exception {
        for (String failure : List.of("INSERT INTO pet_archive", "DELETE FROM user_pet", "INSERT INTO user_pet", "UPDATE pet_account", "INSERT INTO pet_journal", "INSERT INTO pet_ledger", "receipt", "commit")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                sql(f.db, "UPDATE pet_account SET coins=1024");
                if (failure.contains("INTO") || failure.startsWith("DELETE") || failure.startsWith("UPDATE")) f.writeFault = failure;
                else f.fault = failure;
                error(503, () -> f.pets.readopt(identity(), replacement(id), "replace"));
                f.writeFault = ""; f.fault = "";
                Assert.assertEquals(pet(f).getString("id"), id);
                Assert.assertEquals(f.coins(), 1024);
                Assert.assertEquals(f.count("pet_archive"), 0);
                Assert.assertEquals(f.count("pet_journal"), 0);
                Assert.assertEquals(f.count("pet_ledger"), 1);
                f.pets.readopt(identity(), replacement(id), "replace");
                Assert.assertEquals(f.coins(), 512);
                Assert.assertEquals(f.count("pet_archive"), 1);
            }
        }
        for (String failure : List.of("UPDATE pet_inventory", "UPDATE pet_story_progress", "INSERT INTO pet_journal", "INSERT INTO pet_ledger", "receipt", "commit")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                careReady(f, id);
                final int journal = f.count("pet_journal"), ledger = f.count("pet_ledger");
                if (failure.contains("INTO") || failure.startsWith("UPDATE")) f.writeFault = failure;
                else f.fault = failure;
                error(503, () -> f.pets.claimStory(identity(), reward(id, "care-companion"), "reward"));
                f.writeFault = ""; f.fault = "";
                Assert.assertEquals(item(f, "food"), 5);
                Assert.assertEquals(story(f, "care-companion").getString("status"), "claimable");
                Assert.assertEquals(f.count("pet_journal"), journal);
                Assert.assertEquals(f.count("pet_ledger"), ledger);
                f.pets.claimStory(identity(), reward(id, "care-companion"), "reward");
                Assert.assertEquals(item(f, "food"), 7);
            }
        }
        try (var f = new Fixture()) {
            final String id = f.adopt();
            sql(f.db, "UPDATE user_pet SET hunger=50");
            f.writeFault = "INSERT INTO pet_story_progress";
            error(503, () -> f.pets.action(identity(), action(id, "feed"), "feed"));
            f.writeFault = "";
            Assert.assertEquals(item(f, "food"), 6);
            Assert.assertEquals(pet(f).getDouble("hunger"), 50.0);
            Assert.assertEquals(f.count("pet_journal"), 0);
            f.pets.action(identity(), action(id, "feed"), "feed");
            Assert.assertEquals(story(f, "care-companion").getInt("completedSteps"), 1);
        }
        for (String operation : List.of("readoption", "story")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                sql(f.db, "UPDATE pet_account SET coins=1024");
                if (operation.equals("story")) careReady(f, id);
                f.fault = "ack";
                if (operation.equals("story")) error(503, () -> f.pets.claimStory(identity(), reward(id, "care-companion"), "unknown"));
                else error(503, () -> f.pets.readopt(identity(), replacement(id), "unknown"));
                f.fault = "";
                if (operation.equals("story")) {
                    f.pets.claimStory(identity(), reward(id, "care-companion"), "unknown");
                    Assert.assertEquals(item(f, "food"), 7);
                    Assert.assertEquals(log(f, "story", 1, 10).getJSONObject("pagination").getInt("total"), 1);
                } else {
                    f.pets.readopt(identity(), replacement(id), "unknown");
                    Assert.assertEquals(f.coins(), 512);
                    Assert.assertEquals(f.count("pet_archive"), 1);
                }
            }
        }
        System.out.println("PASS 重新领养/链领奖各写入点与回执/提交故障回滚、推进失败撤销原照顾、未知原键恢复");
    }

    static void concurrencyAndLegacy() throws Exception {
        for (String operation : List.of("readoption", "story")) {
            for (boolean sameKey : List.of(true, false)) {
                try (var first = new Fixture(); var second = new Fixture(); var pool = Executors.newFixedThreadPool(2)) {
                    second.db.close(); second.url = first.url; second.db = DriverManager.getConnection(first.url);
                    final String id = first.adopt();
                    sql(first.db, "UPDATE pet_account SET coins=1024");
                    if (operation.equals("story")) careReady(first, id);
                    final CountDownLatch latch = new CountDownLatch(1);
                    final var a = pool.submit(() -> { latch.await(); return outcome(() -> {
                        if (operation.equals("story")) first.pets.claimStory(identity(), reward(id, "care-companion"), "one");
                        else first.pets.readopt(identity(), replacement(id), "one");
                    }); });
                    final var b = pool.submit(() -> { latch.await(); return outcome(() -> {
                        if (operation.equals("story")) second.pets.claimStory(identity(), reward(id, "care-companion"), sameKey ? "one" : "two");
                        else second.pets.readopt(identity(), replacement(id), sameKey ? "one" : "two");
                    }); });
                    latch.countDown();
                    final List<Integer> responses = List.of(a.get(), b.get());
                    Assert.assertEquals(responses.stream().filter(code -> code == 200).count(), sameKey ? 2L : 1L);
                    if (!sameKey) Assert.assertTrue(responses.contains(operation.equals("story") ? 409 : 404));
                    if (operation.equals("story")) Assert.assertEquals(item(first, "food"), 7);
                    else {
                        Assert.assertEquals(first.coins(), 512);
                        Assert.assertEquals(first.count("pet_archive"), 1);
                        Assert.assertEquals(first.count("user_pet"), 1);
                    }
                }
            }
        }
        try (var first = new Fixture(); var second = new Fixture(); var pool = Executors.newFixedThreadPool(2)) {
            second.db.close(); second.url = first.url; second.db = DriverManager.getConnection(first.url);
            final String id = first.adopt();
            sql(first.db, "UPDATE pet_account SET coins=1024");
            final CountDownLatch latch = new CountDownLatch(1);
            final var a = pool.submit(() -> { latch.await(); return outcome(() -> first.pets.readopt(identity(), replacement(id), "replace")); });
            final var b = pool.submit(() -> { latch.await(); return outcome(() -> second.pets.startActivity(identity(), start(id, "outing", "plaza"), "start")); });
            latch.countDown();
            final List<Integer> result = List.of(a.get(), b.get());
            Assert.assertEquals(result.stream().filter(code -> code == 200).count(), 1L);
            Assert.assertEquals(first.count("pet_archive") + first.count("pet_activity"), 1);
        }
        try (var f = new Fixture()) {
            final String id = f.adopt(), activity = begin(f, id, "course", "nature-basics");
            final JSONObject snapshot = new JSONObject(row(f, activity).getString("snapshotJson"));
            snapshot.put("version", 3);
            sql(f.db, "UPDATE pet_activity SET snapshotJson=? WHERE oId=?", snapshot.toString(), activity);
            finish(f, id, activity, "v3-claim");
            Assert.assertEquals(skill(f, "exploration"), 10);
            Assert.assertEquals(story(f, "pond-keepsake").getInt("completedSteps"), 1);
            // 老活动此次真实领取可以推进；之后回执重放不会再推进。
            f.pets.claimActivity(identity(), claim(id, activity), "v3-claim");
            Assert.assertEquals(story(f, "pond-keepsake").getInt("completedSteps"), 1);
            Assert.assertEquals(history(f, "all", 1, 10).getJSONObject("pagination").getInt("total"), 1);
        }
        System.out.println("PASS 双连接重领养/链领奖同异键、换宠与出发竞争、v3未领旧活动真实领取兼容");
    }

    static void routesAndCorruption() throws Exception {
        try (var f = new Fixture()) {
            ApiV3PetProcessor.registerRoutes(f.endpointPet, f.auth, f.writes);
            org.b3log.latke.http.Dispatcher.mapping();
            for (String path : List.of("/api/v3/me/pet/readoption", "/api/v3/me/pet/stories/claim")) {
                Assert.assertEquals(f.httpPet("POST", path, null, "key", "{}").attr("test.status"), 401);
                Assert.assertEquals(f.httpPet("POST", path, "scope", "key", "{}").attr("test.status"), 403);
            }
            final String id = f.adopt();
            careReady(f, id);
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/stories/claim", "owner", "reward", reward(id, "care-companion").toString()).attr("test.status"), 200);
            sql(f.db, "UPDATE pet_account SET coins=1024");
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/readoption", "owner", "replace", replacement(id).toString()).attr("test.status"), 200);
            final JSONObject other = ApiV3IdentityApplicationTest.reviewer();
            f.pets.adopt(other, adoption("另一位伙伴"), "other-adopt");
            Assert.assertEquals(f.pets.read(other).getJSONArray("stories").getJSONObject(0).getInt("completedSteps"), 0);
            error(404, () -> f.pets.claimStory(other, reward(id, "care-companion"), "steal"));
            error(404, () -> f.pets.readopt(other, replacement(id), "steal"));
        }
        for (String corruption : List.of("json", "negative", "over-complete", "early-claim", "unknown-story")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                sql(f.db, "UPDATE user_pet SET hunger=50");
                f.pets.action(identity(), action(id, "feed"), "feed");
                if (corruption.equals("json")) sql(f.db, "UPDATE pet_story_progress SET snapshotJson='bad'");
                if (corruption.equals("negative")) sql(f.db, "UPDATE pet_story_progress SET completedSteps=-1");
                if (corruption.equals("over-complete")) sql(f.db, "UPDATE pet_story_progress SET completedSteps=4");
                if (corruption.equals("early-claim")) sql(f.db, "UPDATE pet_story_progress SET claimedAt=?", f.clock);
                if (corruption.equals("unknown-story")) sql(f.db, "UPDATE pet_story_progress SET storyId='unknown'");
                error(503, () -> f.state());
                error(503, () -> f.pets.claimStory(identity(), reward(id, "care-companion"), "bad-reward"));
                Assert.assertEquals(rows(f.db, "SELECT quantity FROM pet_inventory WHERE itemId='food'").getFirst().getInt("quantity"), 5);
            }
        }
        System.out.println("PASS 两新增路由鉴权与换号、坏链快照/进度/奖励状态拒绝，未误发物品");
    }

    public static void main(final String[] args) throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        advancedCourses();
        orderedCareStory();
        activityStories();
        readoptionPreservesAccount();
        staleCommandsAndGates();
        atomicityAndUnknownResults();
        concurrencyAndLegacy();
        routesAndCorruption();
        System.out.println("PASS 宠物进阶课程、事件链与重新领养第五批专项完成；MySQL与浏览器人工验收仍需另行确认");
    }
}
