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
import org.b3log.symphony.util.ApiV3PetRules;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import static org.b3log.symphony.service.ApiV3PetTest.*;
import static org.b3log.symphony.service.ApiV3PetActivitiesTest.*;
import static org.b3log.symphony.service.ApiV3PetExplorationTest.buy;
import static org.b3log.symphony.service.ApiV3PetExplorationTest.crafting;
import static org.b3log.symphony.service.ApiV3PetProgressionTest.replacement;
import static org.b3log.symphony.service.ApiV3PetProgressionTest.reward;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 可复现的持久化随机序列；仅连接夹具临时 SQLite，不读取真实账号或线上服务。 */
public class ApiV3PetSequenceTest {
    private static final List<String> TABLES = List.of("pet_account", "user_pet", "pet_inventory", "pet_ledger",
            "pet_activity", "pet_skill", "pet_job_progress", "pet_income_account", "pet_activity_result",
            "pet_collection", "pet_journal", "pet_story_progress", "pet_archive");
    private record Command(String operation, JSONObject input, String key, JSONObject receipt) { }
    private final Random random;
    private final JSONObject report;
    private final Map<String, Integer> counts = new HashMap<>();
    private final List<Command> commands = new ArrayList<>();
    private final List<String> retired = new ArrayList<>();
    private final Map<String, Integer> priorStories = new HashMap<>();
    private final Set<String> priorCollections = new HashSet<>();
    private int sequence;
    private int invariants;
    private String currentOperation = "prepare";

    private ApiV3PetSequenceTest(final long seed, final int steps) {
        random = new Random(seed);
        report = new JSONObject().put("seed", seed).put("requestedSteps", steps)
                .put("fixtureProfile", "isolated-funded-experienced");
    }

    private JSONObject snapshot(final Fixture f) throws Exception {
        final JSONObject data = new JSONObject();
        for (String table : TABLES) data.put(table, new JSONArray(rows(f.db, "SELECT * FROM " + table + " ORDER BY oId")));
        return data;
    }

    private JSONObject invoke(final Fixture f, final String operation, final JSONObject input, final String key) {
        return switch (operation) {
            case "care" -> f.pets.action(identity(), input, key);
            case "purchase" -> f.pets.purchase(identity(), input, key);
            case "supply" -> f.pets.supplies(identity(), input, key);
            case "start" -> f.pets.startActivity(identity(), input, key);
            case "claim" -> f.pets.claimActivity(identity(), input, key);
            case "craft" -> f.pets.craft(identity(), input, key);
            case "story" -> f.pets.claimStory(identity(), input, key);
            case "readoption" -> f.pets.readopt(identity(), input, key);
            case "rename" -> f.pets.rename(identity(), input, key);
            default -> throw new AssertionError("未知测试操作 " + operation);
        };
    }

    private JSONObject execute(final Fixture f, final String operation, final JSONObject input, final boolean mustSucceed) throws Exception {
        final String key = "sequence-" + (++sequence);
        currentOperation = operation + ":" + input;
        final JSONObject before = snapshot(f);
        try {
            final JSONObject receipt = invoke(f, operation, input, key);
            counts.merge(operation + ".success", 1, Integer::sum);
            commands.add(new Command(operation, new JSONObject(input.toString()), key, receipt));
            if (operation.equals("readoption")) retired.add(input.getString("petId"));
            // 每次成功立即重放，以实际数据库不变验证，而非只对比返回文案。
            final JSONObject committed = snapshot(f);
            Assert.assertTrue(receipt.similar(invoke(f, operation, input, key)), "同键回执变化");
            Assert.assertTrue(committed.similar(snapshot(f)), "同键重放再次写入业务数据");
            return receipt;
        } catch (ApiV3Exception error) {
            if (mustSucceed || !Set.of(404, 409, 422).contains(error.status)) throw error;
            counts.merge(operation + ".rejected." + error.code, 1, Integer::sum);
            Assert.assertTrue(before.similar(snapshot(f)), "被拒绝的命令改变业务数据：" + error.code);
            return null;
        }
    }

    private String id(final Fixture f) { return pet(f).getString("id"); }

    private void settleActive(final Fixture f) throws Exception {
        final JSONObject p = pet(f), activity = p.optJSONObject("activity");
        if (activity == null) return;
        f.clock = Math.max(f.clock, activity.getLong("endsAt"));
        if (!activity.getString("kind").equals("rest"))
            execute(f, "claim", claim(p.getString("id"), activity.getString("id")), true);
    }

    private void prepare(final Fixture f) throws Exception {
        settleActive(f);
        execute(f, "care", action(id(f), "rest"), true);
        f.clock += ApiV3PetRules.REST_DURATION;
        while (pet(f).getInt("hunger") <= 99) execute(f, "care", action(id(f), "feed"), true);
        while (pet(f).getInt("water") <= 99) execute(f, "care", action(id(f), "water"), true);
    }

    private void complete(final Fixture f, final String kind, final String entry) throws Exception {
        prepare(f);
        final JSONObject started = execute(f, "start", start(id(f), kind, entry), true);
        f.clock = row(f, started.getString("activityId")).getLong("endsAt");
        execute(f, "claim", claim(id(f), started.getString("activityId")), true);
    }

    private void warmup(final Fixture f, final JSONObject catalog) throws Exception {
        final String initial = f.adopt();
        // 种子序列从可访问全部玩法的测试前置开始，不把这笔夹具资金算作生产收益。
        sql(f.db, "UPDATE pet_account SET coins=10000");
        for (Object value : catalog.getJSONArray("skills")) {
            final String skill = ((JSONObject) value).getString("id");
            sql(f.db, "INSERT INTO pet_skill VALUES(?,?,?,?,?,?)", initial + ":" + skill, "1", initial, skill, 20, f.clock);
        }
        execute(f, "purchase", buy(initial, "food", 20), true);
        execute(f, "purchase", buy(initial, "food", 20), true);
        execute(f, "purchase", buy(initial, "fruit", 20), true);
        execute(f, "purchase", buy(initial, "spring-water", 20), true);
        execute(f, "rename", new JSONObject().put("petId", initial).put("name", "种子伙伴"), true);
        prepare(f);
        execute(f, "care", action(initial, "play"), true);
        execute(f, "story", reward(initial, "care-companion"), true);
        complete(f, "course", "nature-basics");
        complete(f, "outing", "lotus-pond");
        complete(f, "course", "nature-advanced");
        execute(f, "story", reward(initial, "pond-keepsake"), true);
        // 每个目录条目至少走一遍真实开始和领取，后续随机序列再覆盖交错顺序与错误分支。
        for (String group : List.of("courses", "places", "jobs")) {
            final List<JSONObject> entries = new ArrayList<>();
            for (Object entry : catalog.getJSONArray(group)) entries.add((JSONObject) entry);
            Collections.shuffle(entries, random);
            for (JSONObject entry : entries) complete(f, group.equals("jobs") ? "work" : group.equals("places") ? "outing" : "course", entry.getString("id"));
        }
        complete(f, "course", "nature-advanced");
        execute(f, "story", reward(initial, "first-career"), true);
        for (Object value : catalog.getJSONArray("recipes")) execute(f, "craft", crafting(initial, ((JSONObject) value).getString("id")), true);
        check(f);
    }

    private void addItems(final Map<String, Long> expected, final JSONArray items) {
        for (Object value : items) {
            final JSONObject item = (JSONObject) value;
            expected.merge(item.getString("itemId"), item.getLong("quantity"), Long::sum);
        }
    }

    private void check(final Fixture f) throws Exception {
        final JSONObject before = snapshot(f), state = f.state(), p = state.getJSONObject("pet");
        Assert.assertTrue(before.similar(snapshot(f)), "读取产生了业务写入");
        Assert.assertEquals(f.count("user_pet"), 1);
        Assert.assertEquals(f.count("pet_account"), 1);
        long money = 9900;
        final Map<String, Long> items = new HashMap<>();
        for (JSONObject ledger : rows(f.db, "SELECT * FROM pet_ledger")) {
            money += ledger.getLong("coinDelta");
            if (Set.of("adoption", "supplies").contains(ledger.getString("operation"))) items.merge("food", ledger.getLong("foodDelta"), Long::sum);
        }
        Assert.assertEquals((long) state.getJSONObject("wallet").getInt("coins"), money, "钱包与已提交流水不守恒");
        Assert.assertTrue(money >= 0);
        long experience = 0, friendship = 0;
        for (JSONObject row : rows(f.db, "SELECT * FROM pet_journal")) {
            final JSONObject event = new JSONObject(row.getString("eventJson"));
            addItems(items, event.getJSONArray("itemChanges"));
            if (row.getString("petId").equals(p.getString("id"))) {
                experience += event.getJSONObject("effects").getLong("experience");
                friendship += event.getJSONObject("effects").getLong("friendship");
            }
        }
        for (JSONObject row : rows(f.db, "SELECT * FROM pet_activity_result")) {
            final JSONObject result = new JSONObject(row.getString("actualJson"));
            items.merge("food", result.getLong("food"), Long::sum);
            addItems(items, result.getJSONArray("items"));
            if (row.getString("petId").equals(p.getString("id"))) experience += result.getLong("experience");
        }
        Assert.assertEquals((long) p.getInt("experience"), experience, "成长与已提交成果不守恒");
        Assert.assertEquals((long) p.getInt("friendship"), friendship);
        final Set<String> inventoryIds = new HashSet<>();
        for (Object value : state.getJSONArray("inventory")) {
            final JSONObject item = (JSONObject) value;
            Assert.assertTrue(inventoryIds.add(item.getString("itemId")), "重复物品投影");
            Assert.assertEquals((long) item.getInt("quantity"), items.getOrDefault(item.getString("itemId"), 0L).longValue(), "物品不守恒：" + item.getString("itemId"));
            Assert.assertTrue(item.getInt("quantity") >= 0);
        }
        for (Map.Entry<String, Long> item : items.entrySet()) {
            Assert.assertTrue(item.getValue() >= 0, "物品记录累计为负：" + item.getKey());
            if (item.getValue() > 0) Assert.assertTrue(inventoryIds.contains(item.getKey()), "正余额物品丢失：" + item.getKey());
        }
        for (String stat : List.of("hunger", "water", "mood", "energy")) Assert.assertTrue(p.getInt(stat) >= 0 && p.getInt(stat) <= 100, stat);
        Assert.assertTrue(p.getInt("friendship") <= 100);
        Assert.assertTrue(p.getJSONObject("growthToday").getInt("used") <= 10);
        Assert.assertTrue(state.getJSONObject("income").getInt("earned") <= 60);
        Assert.assertTrue(state.getJSONObject("income").getInt("earned") >= 0);
        Assert.assertEquals(state.getJSONObject("income").getInt("earned") + state.getJSONObject("income").getInt("remaining"), 60);
        final Set<String> currentCollections = new HashSet<>();
        for (JSONObject collection : rows(f.db, "SELECT * FROM pet_collection")) currentCollections.add(collection.getString("collectionId"));
        Assert.assertTrue(currentCollections.containsAll(priorCollections), "换宠或重放删除了永久收藏");
        priorCollections.addAll(currentCollections);
        final var slots = rows(f.db, "SELECT * FROM pet_activity WHERE slotKey LIKE 'active:%'");
        Assert.assertTrue(slots.size() <= 1);
        Assert.assertEquals(f.count("pet_activity_result"), rows(f.db, "SELECT * FROM pet_activity WHERE claimedAt>0").size());
        for (Object value : state.getJSONArray("stories")) {
            final JSONObject story = (JSONObject) value;
            final int progress = story.getInt("completedSteps");
            Assert.assertTrue(progress >= priorStories.getOrDefault(story.getString("id"), 0) && progress <= 3);
            priorStories.put(story.getString("id"), progress);
        }
        Assert.assertEquals(f.count("pet_archive"), retired.size());
        invariants++;
    }

    private void randomStep(final Fixture f, final JSONObject catalog, final int step) throws Exception {
        final String current = id(f);
        final int choice = random.nextInt(19);
        switch (choice) {
            case 0, 1, 2 -> {
                final String care = List.of("feed", "water", "pet", "play", "rest", "wake").get(random.nextInt(6));
                final JSONObject input = action(current, care);
                if (care.equals("feed") && random.nextBoolean()) input.put("itemId", "fruit");
                if (care.equals("water") && random.nextBoolean()) input.put("itemId", "spring-water");
                execute(f, "care", input, false);
            }
            case 3 -> execute(f, "purchase", buy(current, List.of("food", "fruit", "spring-water", "reed").get(random.nextInt(4)), 1 + random.nextInt(4)), false);
            case 4 -> execute(f, "supply", new JSONObject().put("expectedDate", ApiV3PetRules.today(f.clock)), false);
            case 5, 6 -> {
                final String group = List.of("places", "courses", "jobs").get(random.nextInt(3));
                final JSONArray entries = catalog.getJSONArray(group);
                final String entry = entries.getJSONObject(random.nextInt(entries.length())).getString("id");
                execute(f, "start", start(current, group.equals("jobs") ? "work" : group.equals("places") ? "outing" : "course", entry), false);
            }
            case 7 -> {
                final JSONObject active = pet(f).optJSONObject("activity");
                if (active != null && !active.getString("kind").equals("rest")) execute(f, "claim", claim(current, active.getString("id")), false);
            }
            case 8 -> {
                f.clock += List.of(1L, 60_000L, 1_800_000L, 14_400_000L, 86_400_000L, 259_200_000L).get(random.nextInt(6));
                counts.merge("clock.advance", 1, Integer::sum);
            }
            case 9 -> {
                settleActive(f);
                if (random.nextBoolean() && f.food() > 4) prepare(f);
            }
            case 10 -> execute(f, "craft", crafting(current, catalog.getJSONArray("recipes").getJSONObject(random.nextInt(3)).getString("id")), false);
            case 11 -> execute(f, "story", reward(current, catalog.getJSONArray("stories").getJSONObject(random.nextInt(3)).getString("id")), false);
            case 12 -> {
                final Command command = commands.get(random.nextInt(commands.size()));
                final JSONObject before = snapshot(f);
                Assert.assertTrue(command.receipt.similar(invoke(f, command.operation, command.input, command.key)), "历史回执改变");
                Assert.assertTrue(before.similar(snapshot(f)), "旧键重放影响当前宠物");
                counts.merge("historicalReplay", 1, Integer::sum);
            }
            case 13 -> {
                execute(f, "readoption", replacement(current).put("speciesId", catalog.getJSONArray("species").getJSONObject(random.nextInt(4)).getString("id")), false);
            }
            case 14 -> {
                final JSONObject input = buy(current, "food", 0);
                final JSONObject before = snapshot(f);
                error(422, () -> f.pets.purchase(identity(), input, "invalid-" + step));
                Assert.assertTrue(before.similar(snapshot(f)));
                counts.merge("invalid.quantity", 1, Integer::sum);
            }
            case 15 -> {
                if (!retired.isEmpty()) {
                    final JSONObject before = snapshot(f);
                    error(404, () -> f.pets.rename(identity(), new JSONObject().put("petId", retired.get(random.nextInt(retired.size()))).put("name", "迟到命令"), "stale-" + step));
                    Assert.assertTrue(before.similar(snapshot(f)));
                    counts.merge("stalePetId", 1, Integer::sum);
                }
            }
            case 16 -> execute(f, "rename", new JSONObject().put("petId", current).put("name", "伙伴" + random.nextInt(1000)), false);
            case 17 -> {
                final JSONObject before = snapshot(f);
                final JSONObject all = f.pets.journal(identity(), "all", 1, 20);
                Assert.assertEquals(all.getJSONObject("pagination").getInt("total"), f.count("pet_activity") + f.count("pet_journal"));
                final Set<String> seen = new HashSet<>();
                for (int page = 1; page <= Math.min(3, all.getJSONObject("pagination").getInt("pageCount")); page++) {
                    for (Object value : f.pets.journal(identity(), "all", page, 20).getJSONArray("list")) {
                        final JSONObject entry = (JSONObject) value;
                        Assert.assertTrue(seen.add(entry.getString("recordType") + ":" + entry.getString("id")), "日志分页重复");
                    }
                }
                f.pets.history(identity(), "work", 1, 20);
                Assert.assertTrue(before.similar(snapshot(f)), "日志读取改变业务数据");
                counts.merge("history.read", 1, Integer::sum);
            }
            case 18 -> {
                if (f.coins() >= 5) {
                    currentOperation = "purchase.unknown-result";
                    final int beforeCoins = f.coins();
                    final JSONObject input = buy(current, "food", 1);
                    final String key = "unknown-" + (++sequence);
                    f.fault = "ack";
                    try { error(503, () -> f.pets.purchase(identity(), input, key)); }
                    finally { f.fault = ""; }
                    final JSONObject receipt = f.pets.purchase(identity(), input, key);
                    Assert.assertEquals(f.coins(), beforeCoins - 5);
                    final JSONObject committed = snapshot(f);
                    Assert.assertTrue(receipt.similar(f.pets.purchase(identity(), input, key)));
                    Assert.assertTrue(committed.similar(snapshot(f)), "未知消费原键恢复重复入账");
                    commands.add(new Command("purchase", input, key, receipt));
                    counts.merge("purchase.unknownRecovered", 1, Integer::sum);
                }
            }
            default -> throw new AssertionError("随机分支遗漏");
        }
    }

    private void run(final int steps) throws Exception {
        try (var f = new Fixture()) {
            final JSONObject catalog = f.pets.catalog(identity());
            warmup(f, catalog);
            for (int step = 0; step < steps; step++) {
                report.put("lastStep", step);
                randomStep(f, catalog, step);
                check(f);
            }
            settleActive(f);
            if (f.coins() >= 512) execute(f, "readoption", replacement(id(f)), true);
            check(f);
            // 并发用两个独立连接和真实事务，不以顺序调用模拟竞争。
            currentOperation = "concurrency";
            ApiV3PetProgressionTest.concurrencyAndLegacy();
            report.put("concurrencyScenarios", 5).put("legacyCompatibilityChecks", 1)
                    .put("retiredPets", retired.size()).put("status", "passed");
        } finally {
            report.put("operations", counts).put("invariantChecks", invariants).put("lastOperation", currentOperation);
        }
    }

    public static void main(final String[] args) throws Exception {
        final long seed = args.length > 0 ? Long.parseLong(args[0]) : 20261113L;
        final int steps = args.length > 1 ? Integer.parseInt(args[1]) : 80;
        if (steps < 1 || steps > 2000) throw new IllegalArgumentException("步数需为 1..2000");
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        final var test = new ApiV3PetSequenceTest(seed, steps);
        try {
            test.run(steps);
            System.out.println("PASS 宠物种子序列与账本/物品/成长不变量，seed=" + seed + "，steps=" + steps);
        } catch (Throwable error) {
            test.report.put("status", "failed").put("error", error.toString());
            throw error;
        } finally {
            if (args.length > 2) Files.writeString(Path.of(args[2]), test.report.toString(2));
        }
    }
}
