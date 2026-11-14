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
import org.b3log.symphony.util.ApiV3PetRules;
import org.json.JSONObject;
import org.testng.Assert;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;

import static org.b3log.symphony.service.ApiV3PetTest.*;
import static org.b3log.symphony.service.ApiV3PetActivitiesTest.*;
import static org.b3log.symphony.service.ApiV3PetWorkTest.*;
import static org.b3log.symphony.service.ApiV3PetProgressionTest.complete;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 第六批以真实服务逐日跑代表路线，核对成长、补给和收支，不伪造在线奖励。 */
public class ApiV3PetBalanceTest {
    static long morning(final Fixture f) {
        return LocalDate.parse(ApiV3PetRules.today(f.clock)).atTime(8, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    }

    static void at(final Fixture f, final long firstMorning, final int day, final int minutes) {
        f.clock = firstMorning + day * 86_400_000L + minutes * 60_000L;
    }

    static void care(final Fixture f, final String id, final String action, final String key) {
        f.pets.action(identity(), action(id, action), key);
    }

    static void supply(final Fixture f, final String key) throws Exception {
        final JSONObject body = new JSONObject().put("expectedDate", ApiV3PetRules.today(f.clock));
        final JSONObject receipt = f.pets.supplies(identity(), body, key);
        final int coins = f.coins(), food = f.food();
        Assert.assertTrue(receipt.similar(f.pets.supplies(identity(), body, key)));
        error(409, () -> f.pets.supplies(identity(), body, key + "-another"));
        Assert.assertEquals(f.coins(), coins);
        Assert.assertEquals(f.food(), food);
    }

    static JSONObject purchase(final String id) {
        return new JSONObject().put("petId", id).put("itemId", "food").put("quantity", 1).put("catalogVersion", 3);
    }

    static void catalogAndGrowth() throws Exception {
        try (var f = new Fixture()) {
            final JSONObject catalog = f.pets.catalog(identity());
            Assert.assertEquals(catalog.getInt("version"), 3);
            Assert.assertEquals(catalog.getInt("activityVersion"), 5);
            Assert.assertTrue(catalog.getJSONObject("growthRules").similar(new JSONObject()
                    .put("experiencePerLevel", 100).put("maxLevel", 30).put("careExperience", 10).put("careFriendship", 2)));
            Assert.assertEquals(catalog.getJSONObject("readoptionRules").getInt("coins"), 512);
            Assert.assertEquals(catalog.getJSONObject("incomeRules").getInt("dailyLimit"), 60);
            final String id = f.adopt();
            final String activity = begin(f, id, "outing", "plaza");
            finish(f, id, activity, "claim");
            Assert.assertEquals(pet(f).getInt("experience"), 5);
            Assert.assertEquals(skill(f, "exploration"), 3);
            Assert.assertEquals(f.coins(), 100);
            for (int index = 0; index < 11; index++) {
                care(f, id, "pet", "pet-" + index);
                f.clock += ApiV3PetRules.CARE_COOLDOWN;
            }
            Assert.assertEquals(pet(f).getInt("experience"), 105);
            Assert.assertEquals(pet(f).getInt("level"), 2);
            Assert.assertEquals(pet(f).getInt("friendship"), 20);
            care(f, id, "rest", "rest");
            f.clock += ApiV3PetRules.REST_DURATION;
            care(f, id, "wake", "wake");
            Assert.assertEquals(pet(f).getInt("experience"), 105);
            Assert.assertEquals(pet(f).getJSONObject("growthToday").getInt("used"), 10);
        }
        System.out.println("PASS 第六批目录成长协议、新散步成果、照顾每日十次/亲密度及休息不发成长");
    }

    static void frozenVersionFour() throws Exception {
        for (String entry : List.of("plaza", "nature-advanced")) {
            try (var f = new Fixture()) {
                final String id = f.adopt();
                final boolean advanced = entry.equals("nature-advanced");
                if (advanced) {
                    complete(f, id, "course", "nature-basics", "base-one");
                    complete(f, id, "course", "nature-basics", "base-two");
                }
                final int before = pet(f).getInt("experience"), skillBefore = skill(f, "exploration");
                final String kind = advanced ? "course" : "outing";
                error(409, () -> f.pets.startActivity(identity(), start(id, kind, entry).put("activityVersion", 4), "old-quote"));
                final String activity = begin(f, id, kind, entry);
                // 存入旧版实际会保存的成果，升级后领取不能重新套用当前目录数值。
                final JSONObject frozen = new JSONObject(row(f, activity).getString("snapshotJson")).put("version", 4);
                final JSONObject rewards = frozen.getJSONObject("entry").getJSONObject("rewards");
                rewards.put("experience", advanced ? 30 : 10);
                if (advanced) rewards.getJSONArray("skills").getJSONObject(0).put("experience", 20);
                sql(f.db, "UPDATE pet_activity SET snapshotJson=? WHERE oId=?", frozen.toString(), activity);
                f.rejectActivityCatalog = true;
                finish(f, id, activity, "old-claim");
                Assert.assertEquals(pet(f).getInt("experience"), before + (advanced ? 30 : 10));
                Assert.assertEquals(skill(f, "exploration"), skillBefore + (advanced ? 20 : 3));
                Assert.assertEquals(history(f, "all", 1, 10).getJSONArray("list").getJSONObject(0)
                        .getJSONObject("actualRewards").getInt("experience"), advanced ? 30 : 10);
                f.pets.claimActivity(identity(), claim(id, activity), "old-claim");
                Assert.assertEquals(pet(f).getInt("experience"), before + (advanced ? 30 : 10));
            }
        }
        System.out.println("PASS 旧v4广场/进阶成果冻结、旧报价拒绝、新读取与原键领取不重复计奖");
    }

    static void sevenLightDays() throws Exception {
        try (var f = new Fixture()) {
            final long start = morning(f);
            at(f, start, 0, 0);
            final String id = f.adopt();
            for (int day = 0; day < 7; day++) {
                final String key = "light-" + day;
                at(f, start, day, 30);
                supply(f, key + "-supply");
                care(f, id, "feed", key + "-breakfast");
                care(f, id, "water", key + "-morning-water");
                care(f, id, "pet", key + "-pet");
                complete(f, id, "course", "nature-basics", key + "-class");
                complete(f, id, "work", "trail-assistant", key + "-work");
                at(f, start, day, 240);
                care(f, id, "water", key + "-noon-water");
                at(f, start, day, 720);
                care(f, id, "feed", key + "-dinner");
                care(f, id, "water", key + "-evening-water");
                final JSONObject current = pet(f);
                Assert.assertEquals(f.coins(), 100 + (day + 1) * 27);
                Assert.assertEquals(f.food(), 6);
                Assert.assertEquals(current.getInt("experience"), (day + 1) * 90);
                Assert.assertEquals(current.getInt("friendship"), Math.min(100, (day + 1) * 12));
                Assert.assertEquals(current.getJSONObject("growthToday").getInt("used"), 6);
                Assert.assertEquals(skill(f, "exploration"), (day + 1) * 10);
                Assert.assertEquals(job(f, "trail-assistant").getInt("experience"), (day + 1) * 10);
                Assert.assertEquals(f.state().getJSONObject("income").getInt("earned"), 12);
                care(f, id, "rest", key + "-rest");
            }
            Assert.assertEquals(pet(f).getInt("level"), 7);
        }
        System.out.println("PASS 连续七日轻度路线：真实照顾/课程/工作/休息，日净27币、90经验、无额外口粮消费");
    }

    static void sevenFrequentDays() throws Exception {
        try (var f = new Fixture()) {
            final long start = morning(f);
            at(f, start, 0, 0);
            final String id = f.adopt();
            for (int day = 0; day < 7; day++) {
                final String key = "frequent-" + day;
                at(f, start, day, 30);
                supply(f, key + "-supply");
                // 每日补买一份而非消耗初始库存，测得可持续净收益；一次性事件奖不计日收入。
                final JSONObject bought = f.pets.purchase(identity(), purchase(id), key + "-food");
                Assert.assertTrue(bought.similar(f.pets.purchase(identity(), purchase(id), key + "-food")));
                care(f, id, "feed", key + "-breakfast");
                care(f, id, "water", key + "-morning-water");
                care(f, id, "pet", key + "-morning-pet");
                complete(f, id, "course", "nature-basics", key + "-class-one");
                complete(f, id, "course", "nature-basics", key + "-class-two");
                complete(f, id, "course", "nature-advanced", key + "-advanced");
                complete(f, id, "outing", "lotus-pond", key + "-outing");
                for (int work = 0; work < 3; work++) complete(f, id, "work", "trail-assistant", key + "-work-" + work);
                care(f, id, "rest", key + "-short-rest");
                f.clock += ApiV3PetRules.HOUR;
                care(f, id, "wake", key + "-short-wake");
                for (int work = 3; work < 5; work++) complete(f, id, "work", "trail-assistant", key + "-work-" + work);
                error(409, () -> f.pets.startActivity(identity(), start(id, "work", "trail-assistant"), key + "-over-cap"));
                care(f, id, "feed", key + "-lunch");
                care(f, id, "water", key + "-noon-water");
                care(f, id, "play", key + "-play");
                care(f, id, "rest", key + "-afternoon-rest");
                at(f, start, day, 600);
                care(f, id, "water", key + "-late-water");
                care(f, id, "pet", key + "-evening-pet");
                at(f, start, day, 720);
                care(f, id, "feed", key + "-dinner");
                care(f, id, "water", key + "-evening-water");
                final JSONObject current = pet(f);
                Assert.assertEquals(f.coins(), 100 + (day + 1) * 50);
                Assert.assertEquals(f.food(), 6);
                Assert.assertEquals(current.getInt("experience"), (day + 1) * 275);
                Assert.assertEquals(current.getInt("friendship"), Math.min(100, (day + 1) * 20));
                Assert.assertEquals(current.getJSONObject("growthToday").getInt("used"), 10);
                Assert.assertEquals(skill(f, "exploration"), (day + 1) * 58);
                Assert.assertEquals(job(f, "trail-assistant").getInt("experience"), (day + 1) * 50);
                Assert.assertEquals(f.state().getJSONObject("income").getInt("earned"), 60);
                Assert.assertEquals(f.state().getJSONObject("income").getInt("remaining"), 0);
                care(f, id, "rest", key + "-night-rest");
            }
            Assert.assertEquals(pet(f).getInt("level"), 20);
        }
        System.out.println("PASS 连续七日高频路线：工作60币共限、补给独立、补买粮原键去重，日净50币/275经验");
    }

    static void purchaseUnknownResult() throws Exception {
        try (var f = new Fixture()) {
            final String id = f.adopt();
            f.fault = "ack";
            error(503, () -> f.pets.purchase(identity(), purchase(id), "unknown-purchase"));
            f.fault = "";
            f.pets.purchase(identity(), purchase(id), "unknown-purchase");
            Assert.assertEquals(f.coins(), 95);
            Assert.assertEquals(f.food(), 7);
            // 后续读取损坏不应倒退已经成功的消费；原键仍只返回回执，不依赖状态读取。
            sql(f.db, "UPDATE user_pet SET hunger=-1");
            error(503, () -> f.state());
            f.pets.purchase(identity(), purchase(id), "unknown-purchase");
            Assert.assertEquals(rows(f.db, "SELECT coins FROM pet_account WHERE oId='1'").getFirst().getInt("coins"), 95);
            Assert.assertEquals(rows(f.db, "SELECT quantity FROM pet_inventory WHERE userId='1' AND itemId='food'")
                    .getFirst().getInt("quantity"), 7);
        }
        System.out.println("PASS 消费回包丢失及后续状态读取失败，原键恢复不重复扣币或发物品");
    }

    public static void main(final String[] args) throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        catalogAndGrowth();
        frozenVersionFour();
        sevenLightDays();
        sevenFrequentDays();
        purchaseUnknownResult();
        System.out.println("PASS 宠物第六批成长/经济专项完成；代表路线不代替真实玩家分布及人工体验评估");
    }
}
