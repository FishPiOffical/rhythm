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
import org.b3log.symphony.util.ApiV3PetExploration;
import org.b3log.symphony.util.ApiV3PetRules;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.b3log.symphony.service.ApiV3PetTest.*;
import static org.b3log.symphony.service.ApiV3PetActivitiesTest.*;
import static org.b3log.symphony.service.ApiV3PetWorkTest.finish;
import static org.b3log.symphony.service.ApiV3PetWorkTest.unlock;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 第四批真实持久化回归：材料、消费、永久收藏与混合日志保持同一事务和账号边界。 */
public class ApiV3PetExplorationTest {
    static JSONObject buy(final String id, final String item, final int quantity) {
        return purchase(id, quantity).put("itemId", item);
    }

    static JSONObject crafting(final String id, final String recipe) {
        return new JSONObject().put("petId", id).put("recipeId", recipe).put("catalogVersion", 3);
    }

    static int item(final Fixture f, final String id) {
        for (Object value : f.state().getJSONArray("inventory")) {
            final JSONObject row = (JSONObject) value;
            if (id.equals(row.getString("itemId"))) return row.getInt("quantity");
        }
        throw new AssertionError("目录物品缺少背包投影：" + id);
    }

    static JSONObject log(final Fixture f, final String kind, final int page, final int size) {
        return f.pets.journal(identity(), kind, page, size);
    }

    static JSONObject latest(final Fixture f, final String kind) {
        return log(f, kind, 1, 1).getJSONArray("list").getJSONObject(0);
    }

    static void prepareCraft(final Fixture f, final String id) throws Exception {
        unlock(f, id, "exploration");
        f.pets.purchase(identity(), buy(id, "reed", 2), "buy-reed");
    }

    static void catalogAndExpeditions() throws Exception {
        try (var f = new Fixture()) {
            final JSONObject catalog = f.pets.catalog(identity());
            Assert.assertEquals(catalog.getInt("version"), 3);
            Assert.assertEquals(catalog.getInt("activityVersion"), 4);
            Assert.assertEquals(catalog.getJSONArray("items").length(), 6);
            Assert.assertEquals(catalog.getJSONArray("recipes").length(), 3);
            Assert.assertEquals(catalog.getJSONArray("collections").length(), 3);
            Assert.assertEquals(catalog.getJSONArray("places").length(), 6);
            for (String group : List.of("places", "courses", "jobs")) {
                for (Object value : catalog.getJSONArray(group)) {
                    final JSONObject entry = (JSONObject) value;
                    Assert.assertTrue(entry.get("requirements") instanceof JSONArray);
                    Assert.assertTrue(entry.getJSONObject("rewards").get("items") instanceof JSONArray);
                }
            }
            final String id = f.adopt();
            Assert.assertEquals(f.state().getJSONArray("inventory").length(), 6);
            Assert.assertEquals(f.state().getJSONArray("collections").length(), 0);
            Assert.assertEquals(item(f, "fruit"), 0);
            error(409, () -> begin(f, id, "outing", "lotus-pond"));
            final String course = begin(f, id, "course", "nature-basics");
            finish(f, id, course, "finish-course");
            Assert.assertEquals(skill(f, "exploration"), 10);
            final String trip = f.pets.startActivity(identity(), start(id, "outing", "lotus-pond"), "after-course").getString("activityId");
            finish(f, id, trip, "finish-trip");
            Assert.assertEquals(item(f, "reed"), 2);
        }
        for (var place : ApiV3PetExploration.PLACES) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                unlock(f, id, place.skillId());
                sql(f.db, "UPDATE pet_skill SET experience=9");
                error(409, () -> begin(f, id, "outing", place.id()));
                sql(f.db, "UPDATE pet_skill SET experience=10");
                final String trip = begin(f, id, "outing", place.id());
                Assert.assertEquals(f.coins(), 95);
                Assert.assertEquals(row(f, trip).getLong("endsAt") - f.clock, 20 * 60_000L);
                f.clock = row(f, trip).getLong("endsAt");
                Assert.assertEquals(item(f, place.itemId()), 0);
                Assert.assertEquals(pet(f).getJSONObject("activity").getString("status"), "claimable");
                // 出发后目录变化不能修改已支付活动的奖励快照。
                f.rejectActivityCatalog = true;
                final JSONObject receipt = f.pets.claimActivity(identity(), claim(id, trip), "claim");
                Assert.assertEquals(item(f, place.itemId()), 2);
                Assert.assertEquals(pet(f).getInt("experience"), 25);
                Assert.assertEquals(skill(f, place.skillId()), 18);
                Assert.assertTrue(receipt.similar(f.pets.claimActivity(identity(), claim(id, trip), "claim")));
                error(409, () -> f.pets.claimActivity(identity(), claim(id, trip), "claim-again"));
                Assert.assertEquals(item(f, place.itemId()), 2);
                final JSONObject actual = latest(f, "outing").getJSONObject("actualRewards");
                Assert.assertEquals(actual.getJSONArray("items").getJSONObject(0).getString("itemId"), place.itemId());
                Assert.assertEquals(actual.getJSONArray("items").getJSONObject(0).getInt("quantity"), 2);
            }
        }
        System.out.println("PASS 三个进阶地点、入门课程解锁、技能边界、冻结材料奖励和领取去重");
    }

    static void shopAndCare() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            f.pets.purchase(identity(), buy(id, "fruit", 2), "fruit");
            f.pets.purchase(identity(), buy(id, "spring-water", 2), "drink");
            f.pets.purchase(identity(), buy(id, "reed", 2), "reed");
            Assert.assertEquals(f.coins(), 66);
            Assert.assertEquals(item(f, "fruit"), 2);
            Assert.assertEquals(item(f, "spring-water"), 2);
            for (String unavailable : List.of("forest-seed", "sea-shell", "unknown"))
                error(422, () -> f.pets.purchase(identity(), buy(id, unavailable, 1), "bad-" + unavailable));
            error(422, () -> f.pets.purchase(identity(), buy(id, "fruit", 21), "quantity"));
            error(409, () -> f.pets.purchase(identity(), buy(id, "fruit", 1).put("catalogVersion", 1), "old-price"));
            error(422, () -> f.pets.action(identity(), action(id, "feed").put("itemId", "reed"), "eat-material"));
            error(422, () -> f.pets.action(identity(), action(id, "water").put("itemId", "fruit"), "drink-fruit"));
            error(422, () -> f.pets.action(identity(), action(id, "pet").put("itemId", "fruit"), "pet-fruit"));
            final int before = f.count("pet_journal");
            error(409, () -> f.pets.action(identity(), action(id, "feed").put("itemId", "fruit"), "full-fruit"));
            error(409, () -> f.pets.action(identity(), action(id, "water").put("itemId", "spring-water"), "full-drink"));
            Assert.assertEquals(f.count("pet_journal"), before);
            Assert.assertEquals(item(f, "fruit"), 2);
            // 一个主要状态已满，但次要效果仍可生效，不应误判为浪费。
            sql(f.db, "UPDATE user_pet SET mood=95.5,energy=98 WHERE oId=?", id);
            f.pets.action(identity(), action(id, "feed").put("itemId", "fruit"), "eat-fruit");
            JSONObject effect = latest(f, "care").getJSONObject("effects");
            Assert.assertEquals(effect.getDouble("hunger"), 0.0);
            Assert.assertEquals(effect.getDouble("mood"), 4.5);
            Assert.assertEquals(item(f, "fruit"), 1);
            f.pets.action(identity(), action(id, "water").put("itemId", "spring-water"), "drink-water");
            effect = latest(f, "care").getJSONObject("effects");
            Assert.assertEquals(effect.getDouble("water"), 0.0);
            Assert.assertEquals(effect.getDouble("energy"), 2.0);
            Assert.assertEquals(item(f, "spring-water"), 1);
            // 离线结算损失不混入操作日志，免费水不会消费收费饮料。
            f.clock += ApiV3PetRules.HOUR;
            final double waterBefore = pet(f).getDouble("water");
            f.pets.action(identity(), action(id, "water"), "free-water");
            final JSONObject water = latest(f, "care");
            Assert.assertEquals(water.getJSONArray("itemChanges").length(), 0);
            Assert.assertEquals(water.getJSONObject("effects").getDouble("water"), Math.min(40, 100 - waterBefore), 0.00001);
            Assert.assertEquals(water.getJSONObject("effects").getDouble("hunger"), 0.0);
            Assert.assertEquals(item(f, "spring-water"), 1);
            f.pets.action(identity(), action(id, "feed"), "old-food");
            Assert.assertEquals(item(f, "food"), 5);
        }
        System.out.println("PASS 多物品报价/数量、类型限制、完整状态防浪费、封顶净效果、免费饮水及旧口粮兼容");
    }

    static void recipesAndPermanentCollections() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            error(409, () -> f.pets.craft(identity(), crafting(id, "reed-boat"), "locked"));
            unlock(f, id, "exploration");
            error(409, () -> f.pets.craft(identity(), crafting(id, "reed-boat"), "missing-items"));
            f.pets.purchase(identity(), buy(id, "reed", 2), "materials");
            f.pets.action(identity(), action(id, "rest"), "rest");
            error(409, () -> f.pets.craft(identity(), crafting(id, "reed-boat"), "resting"));
            f.pets.action(identity(), action(id, "wake"), "wake");
            final String trip = begin(f, id, "outing", "plaza");
            error(409, () -> f.pets.craft(identity(), crafting(id, "reed-boat"), "travelling"));
            finish(f, id, trip, "return");
            error(409, () -> f.pets.craft(identity(), crafting(id, "reed-boat").put("catalogVersion", 1), "old-craft"));
            error(422, () -> f.pets.craft(identity(), crafting(id, "unknown"), "bad-craft"));
            error(422, () -> f.pets.craft(identity(), crafting(id, "reed-boat").put("coins", 0), "forged-craft"));
            sql(f.db, "UPDATE pet_account SET coins=1");
            error(409, () -> f.pets.craft(identity(), crafting(id, "reed-boat"), "broke"));
            Assert.assertEquals(item(f, "reed"), 2);
            sql(f.db, "UPDATE pet_account SET coins=88");
            final JSONObject receipt = f.pets.craft(identity(), crafting(id, "reed-boat"), "craft");
            Assert.assertEquals(f.coins(), 86);
            Assert.assertEquals(item(f, "reed"), 0);
            Assert.assertEquals(f.state().getJSONArray("collections").length(), 1);
            Assert.assertEquals(f.state().getJSONArray("collections").getJSONObject(0).getString("id"), "reed-boat");
            Assert.assertTrue(receipt.similar(f.pets.craft(identity(), crafting(id, "reed-boat"), "craft")));
            error(409, () -> f.pets.craft(identity(), crafting(id, "reed-boat"), "craft-new-key"));
            Assert.assertEquals(f.count("pet_collection"), 1);
            Assert.assertEquals(log(f, "craft", 1, 10).getJSONObject("pagination").getInt("total"), 1);
            final JSONObject event = latest(f, "craft");
            Assert.assertEquals(event.getString("collectionId"), "reed-boat");
            Assert.assertEquals(event.getInt("coinsDelta"), -2);
            Assert.assertEquals(event.getJSONArray("itemChanges").getJSONObject(0).getInt("quantity"), -2);
            Assert.assertEquals(f.pets.read(ApiV3IdentityApplicationTest.reviewer()).getJSONArray("collections").length(), 0);
            error(404, () -> f.pets.craft(identity(), crafting("99", "reed-boat"), "wrong-pet"));
        }
        for (var recipe : ApiV3PetExploration.RECIPES) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                unlock(f, id, recipe.skillId());
                sql(f.db, "INSERT INTO pet_inventory VALUES(?,?,?,?,?)", "1:" + recipe.itemId(), "1", recipe.itemId(), 2, f.clock);
                f.pets.craft(identity(), crafting(id, recipe.id()), "craft");
                Assert.assertEquals(item(f, recipe.itemId()), 0);
                Assert.assertEquals(f.coins(), 98);
                Assert.assertEquals(f.state().getJSONArray("collections").getJSONObject(0).getString("id"), recipe.id());
                // 收藏以账号归属而非宠物归属存储；未来重新领养不能再次获取首次收藏。
                Assert.assertEquals(rows(f.db, "SELECT userId,collectionId FROM pet_collection").getFirst().getString("userId"), "1");
            }
        }
        System.out.println("PASS 三配方、技能/材料/余额/活动槽约束、账号永久收藏、原键重放与新键去重");
    }

    static void mixedJournal() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            prepareCraft(f, id);
            f.pets.craft(identity(), crafting(id, "reed-boat"), "craft");
            sql(f.db, "UPDATE user_pet SET hunger=50,water=50,mood=50 WHERE oId=?", id);
            for (String command : List.of("feed", "water", "pet", "play", "rest", "wake"))
                f.pets.action(identity(), action(id, command), command);
            final JSONObject care = log(f, "care", 1, 20);
            Assert.assertEquals(care.getJSONObject("pagination").getInt("total"), 6);
            final Set<String> actions = new HashSet<>();
            for (Object value : care.getJSONArray("list")) {
                final JSONObject event = (JSONObject) value;
                actions.add(event.getString("action"));
                Assert.assertEquals(event.getString("recordType"), "event");
                Assert.assertTrue(event.isNull("collectionId"));
                Assert.assertEquals(event.getInt("coinsDelta"), 0);
                if ("play".equals(event.getString("action")))
                    Assert.assertEquals(event.getJSONObject("effects").getInt("energy"), -ApiV3PetRules.PLAY_ENERGY);
            }
            Assert.assertEquals(actions, Set.of("feed", "water", "pet", "play", "rest", "wake"));
            final String activity = begin(f, id, "outing", "plaza");
            final int rowsBefore = f.count("pet_journal");
            // 同一时刻混合活动与照顾记录，必须在数据库联合分页而不是各取一页再拼接。
            final Set<String> seen = new HashSet<>();
            for (int page = 1; page <= 3; page++) {
                final JSONObject result = log(f, "all", page, 3);
                Assert.assertEquals(result.getJSONObject("pagination").getInt("total"), 9);
                Assert.assertEquals(result.getJSONObject("pagination").getInt("pageCount"), 3);
                Assert.assertEquals(result.getJSONArray("list").length(), 3);
                for (Object value : result.getJSONArray("list")) {
                    final JSONObject event = (JSONObject) value;
                    Assert.assertTrue(seen.add(event.getString("recordType") + ":" + event.getString("id")));
                }
                Assert.assertTrue(result.getJSONArray("list").similar(log(f, "all", page, 3).getJSONArray("list")));
            }
            Assert.assertEquals(seen.size(), 9);
            Assert.assertEquals(log(f, "all", 4, 3).getJSONArray("list").length(), 0);
            Assert.assertEquals(log(f, "outing", 1, 10).getJSONObject("pagination").getInt("total"), 1);
            Assert.assertEquals(log(f, "course", 1, 10).getJSONObject("pagination").getInt("total"), 0);
            Assert.assertEquals(log(f, "work", 1, 10).getJSONObject("pagination").getInt("total"), 0);
            Assert.assertEquals(log(f, "purchase", 1, 10).getJSONObject("pagination").getInt("total"), 1);
            Assert.assertEquals(f.pets.journal(ApiV3IdentityApplicationTest.reviewer(), "all", 1, 10).getJSONArray("list").length(), 0);
            Assert.assertEquals(f.count("pet_journal"), rowsBefore);
            Assert.assertEquals(row(f, activity).getLong("claimedAt"), 0L);
            finish(f, id, activity, "claim");
            Assert.assertEquals(log(f, "all", 1, 10).getJSONObject("pagination").getInt("total"), 9);
            Assert.assertEquals(latest(f, "outing").getString("status"), "claimed");
            // 旧流水不臆造日常事件；清空新日志后只剩真实计时活动。
            sql(f.db, "DELETE FROM pet_journal");
            Assert.assertEquals(log(f, "all", 1, 10).getJSONObject("pagination").getInt("total"), 1);
        }
        System.out.println("PASS 六种日常操作净效果、混合日志真分页/稳定排序、分类和换号隔离、读取不发奖或伪造旧日志");
    }

    static void legacyAndCorruption() throws Exception {
        for (int version : List.of(1, 2)) {
            try (var f = new Fixture()) {
                final String id = f.adopt(), trip = begin(f, id, "outing", "riverside");
                final JSONObject snapshot = new JSONObject(row(f, trip).getString("snapshotJson"));
                snapshot.put("version", version);
                final JSONObject entry = snapshot.getJSONObject("entry");
                entry.remove("requirements");
                final JSONObject rewards = entry.getJSONObject("rewards");
                rewards.remove("items");
                if (version == 1) { rewards.remove("coins"); rewards.remove("proficiency"); }
                sql(f.db, "UPDATE pet_activity SET snapshotJson=? WHERE oId=?", snapshot.toString(), trip);
                finish(f, id, trip, "old-claim");
                Assert.assertEquals(item(f, "food"), 7);
                if (version == 1) sql(f.db, "DELETE FROM pet_activity_result WHERE oId=?", trip);
                else {
                    final JSONObject actual = new JSONObject(rows(f.db, "SELECT actualJson FROM pet_activity_result WHERE oId=?", trip).getFirst().getString("actualJson"));
                    actual.remove("items");
                    sql(f.db, "UPDATE pet_activity_result SET actualJson=? WHERE oId=?", actual.toString(), trip);
                }
                final JSONObject record = latest(f, "outing");
                Assert.assertEquals(record.getJSONObject("rewards").getJSONArray("items").length(), 0);
                if (version == 1) Assert.assertTrue(record.isNull("actualRewards"));
                else Assert.assertEquals(record.getJSONObject("actualRewards").getJSONArray("items").length(), 0);
            }
        }
        for (String corruption : List.of("json", "negative-time", "unknown-action", "wrong-pet", "nonfinite", "zero-item", "unknown-item", "false-collection")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                f.pets.purchase(identity(), buy(id, "fruit", 1), "fruit");
                final JSONObject row = rows(f.db, "SELECT * FROM pet_journal").getFirst();
                final JSONObject event = new JSONObject(row.getString("eventJson"));
                if (corruption.equals("negative-time")) event.put("occurredAt", -1);
                if (corruption.equals("unknown-action")) event.put("action", "evil");
                if (corruption.equals("wrong-pet")) event.put("petId", "99");
                if (corruption.equals("nonfinite")) event.getJSONObject("effects").put("mood", "Infinity");
                if (corruption.equals("zero-item")) event.getJSONArray("itemChanges").getJSONObject(0).put("quantity", 0);
                if (corruption.equals("unknown-item")) event.getJSONArray("itemChanges").getJSONObject(0).put("itemId", "unknown");
                if (corruption.equals("false-collection")) event.put("collectionId", "reed-boat");
                sql(f.db, "UPDATE pet_journal SET eventJson=?", corruption.equals("json") ? "broken" : event.toString());
                error(503, () -> log(f, "all", 1, 10));
                Assert.assertEquals(f.coins(), 94);
            }
        }
        for (String corruption : List.of("unknown-item", "duplicate-item", "negative-quantity", "missing-items", "unknown-requirement")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                unlock(f, id, "exploration");
                final String trip = begin(f, id, "outing", "lotus-pond");
                final JSONObject snapshot = new JSONObject(row(f, trip).getString("snapshotJson"));
                final JSONObject entry = snapshot.getJSONObject("entry"), rewards = entry.getJSONObject("rewards");
                final JSONArray items = rewards.getJSONArray("items");
                if (corruption.equals("unknown-item")) items.getJSONObject(0).put("itemId", "unknown");
                if (corruption.equals("duplicate-item")) items.put(new JSONObject(items.getJSONObject(0).toString()));
                if (corruption.equals("negative-quantity")) items.getJSONObject(0).put("quantity", -1);
                if (corruption.equals("missing-items")) rewards.remove("items");
                if (corruption.equals("unknown-requirement")) entry.getJSONArray("requirements").getJSONObject(0).put("skillId", "unknown");
                sql(f.db, "UPDATE pet_activity SET snapshotJson=? WHERE oId=?", snapshot.toString(), trip);
                f.clock = row(f, trip).getLong("endsAt");
                error(503, () -> f.pets.claimActivity(identity(), claim(id, trip), "claim"));
                error(503, () -> log(f, "all", 1, 10));
                Assert.assertEquals(f.count("pet_activity_result"), 0);
                Assert.assertEquals(rows(f.db, "SELECT COUNT(*) AS n FROM pet_inventory WHERE itemId='reed'").getFirst().getInt("n"), 0);
            }
        }
        System.out.println("PASS v1/v2冻结成果兼容、旧实得未知、坏日志/材料/技能快照明确失败且不误发奖");
    }

    static void atomicity() throws Exception {
        for (String failure : List.of("UPDATE pet_inventory", "UPDATE pet_account", "INSERT INTO pet_collection", "INSERT INTO pet_journal", "INSERT INTO pet_ledger", "receipt", "commit")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                prepareCraft(f, id);
                final int ledger = f.count("pet_ledger"), journal = f.count("pet_journal");
                if (failure.contains("INTO") || failure.startsWith("UPDATE")) f.writeFault = failure;
                else f.fault = failure;
                error(503, () -> f.pets.craft(identity(), crafting(id, "reed-boat"), "craft"));
                f.writeFault = ""; f.fault = "";
                Assert.assertEquals(f.coins(), 88);
                Assert.assertEquals(item(f, "reed"), 2);
                Assert.assertEquals(f.count("pet_collection"), 0);
                Assert.assertEquals(f.count("pet_journal"), journal);
                Assert.assertEquals(f.count("pet_ledger"), ledger);
                f.pets.craft(identity(), crafting(id, "reed-boat"), "craft");
                Assert.assertEquals(f.count("pet_collection"), 1);
                Assert.assertEquals(f.coins(), 86);
            }
        }
        for (String operation : List.of("purchase", "care", "claim")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                if (operation.equals("purchase")) {
                    f.writeFault = "INSERT INTO pet_journal";
                    error(503, () -> f.pets.purchase(identity(), buy(id, "fruit", 2), "buy"));
                    f.writeFault = "";
                    Assert.assertEquals(item(f, "fruit"), 0);
                    Assert.assertEquals(f.coins(), 100);
                    Assert.assertEquals(f.count("pet_journal"), 0);
                } else if (operation.equals("care")) {
                    sql(f.db, "UPDATE user_pet SET hunger=50");
                    f.writeFault = "INSERT INTO pet_journal";
                    error(503, () -> f.pets.action(identity(), action(id, "feed"), "feed"));
                    f.writeFault = "";
                    Assert.assertEquals(item(f, "food"), 6);
                    Assert.assertEquals(pet(f).getDouble("hunger"), 50.0);
                    Assert.assertEquals(pet(f).getInt("experience"), 0);
                } else {
                    unlock(f, id, "exploration");
                    final String trip = begin(f, id, "outing", "lotus-pond");
                    f.clock = row(f, trip).getLong("endsAt");
                    f.writeFault = "INSERT INTO pet_activity_result";
                    error(503, () -> f.pets.claimActivity(identity(), claim(id, trip), "claim"));
                    f.writeFault = "";
                    Assert.assertEquals(item(f, "reed"), 0);
                    Assert.assertEquals(skill(f, "exploration"), 10);
                    Assert.assertEquals(row(f, trip).getLong("claimedAt"), 0L);
                    f.pets.claimActivity(identity(), claim(id, trip), "claim");
                    Assert.assertEquals(item(f, "reed"), 2);
                }
            }
        }
        try (var f = new Fixture()) {
            final String id = f.adopt();
            prepareCraft(f, id);
            f.fault = "ack";
            error(503, () -> f.pets.craft(identity(), crafting(id, "reed-boat"), "lost-ack"));
            f.fault = "";
            Assert.assertEquals(f.count("pet_collection"), 1);
            f.pets.craft(identity(), crafting(id, "reed-boat"), "lost-ack");
            Assert.assertEquals(f.coins(), 86);
            Assert.assertEquals(f.count("pet_collection"), 1);
            Assert.assertEquals(log(f, "craft", 1, 10).getJSONObject("pagination").getInt("total"), 1);
        }
        System.out.println("PASS 制作各写入点/回执/提交回滚、消费日志故障原子性、材料领取回滚和未知结果原键恢复");
    }

    static void concurrencyAndOverflow() throws Exception {
        for (boolean sameKey : List.of(true, false)) {
            try (var first = new Fixture(); var second = new Fixture(); var pool = Executors.newFixedThreadPool(2)) {
                second.db.close(); second.url = first.url; second.db = DriverManager.getConnection(first.url);
                final String id = first.adopt();
                prepareCraft(first, id);
                final CountDownLatch start = new CountDownLatch(1);
                final var a = pool.submit(() -> { start.await(); return outcome(() -> first.pets.craft(identity(), crafting(id, "reed-boat"), "craft")); });
                final var b = pool.submit(() -> { start.await(); return outcome(() -> second.pets.craft(identity(), crafting(id, "reed-boat"), sameKey ? "craft" : "other")); });
                start.countDown();
                final List<Integer> result = List.of(a.get(), b.get());
                Assert.assertEquals(result.stream().filter(code -> code == 200).count(), sameKey ? 2L : 1L);
                if (!sameKey) Assert.assertTrue(result.contains(409));
                Assert.assertEquals(first.coins(), 86);
                Assert.assertEquals(item(first, "reed"), 0);
                Assert.assertEquals(first.count("pet_collection"), 1);
                Assert.assertEquals(log(first, "craft", 1, 10).getJSONObject("pagination").getInt("total"), 1);
            }
        }
        try (var first = new Fixture(); var second = new Fixture(); var pool = Executors.newFixedThreadPool(2)) {
            second.db.close(); second.url = first.url; second.db = DriverManager.getConnection(first.url);
            final String id = first.adopt();
            sql(first.db, "UPDATE pet_account SET coins=6");
            final CountDownLatch start = new CountDownLatch(1);
            final var a = pool.submit(() -> { start.await(); return outcome(() -> first.pets.purchase(identity(), buy(id, "fruit", 1), "first")); });
            final var b = pool.submit(() -> { start.await(); return outcome(() -> second.pets.purchase(identity(), buy(id, "reed", 1), "second")); });
            start.countDown();
            final List<Integer> result = List.of(a.get(), b.get());
            Assert.assertTrue(result.contains(200));
            Assert.assertTrue(result.contains(409));
            Assert.assertEquals(first.coins(), 0);
            Assert.assertEquals(item(first, "fruit") + item(first, "reed"), 1);
            Assert.assertEquals(first.count("pet_journal"), 1);
        }
        try (var f = new Fixture()) {
            final String id = f.adopt();
            sql(f.db, "INSERT INTO pet_inventory VALUES(?,?,?,?,?)", "1:reed", "1", "reed", Integer.MAX_VALUE, f.clock);
            error(503, () -> f.pets.purchase(identity(), buy(id, "reed", 1), "overflow"));
            Assert.assertEquals(item(f, "reed"), Integer.MAX_VALUE);
            Assert.assertEquals(f.coins(), 100);
            Assert.assertEquals(f.count("pet_journal"), 0);
        }
        System.out.println("PASS 两独立连接制作同键/异键去重、不同物品并发消费共享余额、材料溢出完整回滚");
    }

    static void routes() throws Exception {
        try (var f = new Fixture()) {
            ApiV3PetProcessor.registerRoutes(f.endpointPet, f.auth, f.writes);
            org.b3log.latke.http.Dispatcher.mapping();
            for (String path : List.of("/api/v3/me/pet/journal", "/api/v3/me/pet/crafts")) {
                final String method = path.endsWith("journal") ? "GET" : "POST";
                Assert.assertEquals(f.httpPet(method, path, null, "key", "{}").attr("test.status"), 401);
                Assert.assertEquals(f.httpPet(method, path, "scope", "key", "{}").attr("test.status"), 403);
            }
            for (String query : List.of("kind=evil", "p=0", "p=10001", "size=51", "size=1.5"))
                Assert.assertEquals(f.httpPet("GET", "/api/v3/me/pet/journal?" + query, "owner", null, null).attr("test.status"), 422);
            Assert.assertEquals(f.httpPet("GET", "/api/v3/me/pet/journal?kind=care&p=2&size=3", "owner", null, null).attr("test.status"), 200);
            final String id = f.adopt();
            prepareCraft(f, id);
            Assert.assertEquals(f.httpPet("POST", "/api/v3/me/pet/crafts", "owner", "route-craft", crafting(id, "reed-boat").toString()).attr("test.status"), 200);
            Assert.assertEquals(f.httpPet("GET", "/api/v3/me/pet/journal?kind=craft", "owner", null, null).attr("test.status"), 200);
            final JSONObject response = (JSONObject) f.httpPet("GET", "/api/v3/me/pet/journal?kind=craft", "owner", null, null).attr("test.data");
            Assert.assertEquals(response.getJSONObject("pagination").getInt("total"), 1);
            sql(f.db, "UPDATE tokens SET state=1 WHERE oId='100'");
            error(403, () -> f.pets.journal(identity(), "all", 1, 10));
            error(403, () -> f.pets.craft(identity(), crafting(id, "reed-boat"), "revoked"));
        }
        System.out.println("PASS 制作/混合日志真实路由、匿名/权限/撤销凭据、严格分页和写入协议");
    }

    public static void main(final String[] args) throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        catalogAndExpeditions();
        shopAndCare();
        recipesAndPermanentCollections();
        mixedJournal();
        legacyAndCorruption();
        atomicity();
        concurrencyAndOverflow();
        routes();
        System.out.println("PASS 宠物进阶探索、配方收藏和日常日志第四批后端专项完成；MySQL运行态验收另行记录");
    }
}
