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
package org.b3log.symphony.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.regex.Pattern;

/** 第一批试运行数值及目录集中维护，后续课程、地点和工作不伪造可用入口。 */
public final class ApiV3PetRules {
    public static final int VERSION = 3;
    public static final int READOPTION_COINS = 512;
    public static final int NAME_LENGTH = 12;
    public static final int INITIAL_COINS = 100;
    public static final int INITIAL_FOOD = 6;
    public static final int DAILY_COINS = 20;
    public static final int DAILY_FOOD = 2;
    public static final int FOOD_PRICE = 5;
    public static final int FOOD_HUNGER = 35;
    public static final int WATER_GAIN = 40;
    public static final int PET_MOOD = 10;
    public static final int PLAY_MOOD = 20;
    public static final int PLAY_ENERGY = 10;
    public static final int DAILY_GROWTH = 10;
    public static final int EXPERIENCE_PER_LEVEL = 100;
    public static final int MAX_LEVEL = 30;
    public static final int CARE_EXPERIENCE = 10;
    public static final int CARE_FRIENDSHIP = 2;
    public static final long HOUR = 3_600_000L;
    public static final long CARE_COOLDOWN = HOUR / 2;
    public static final long REST_DURATION = 4 * HOUR;
    public static final int REST_ENERGY_PER_HOUR = 20;
    public static final Set<String> SPECIES = Set.of("momota", "hexiaotun", "yubingmiao", "paixiaoyu");
    public static final Set<String> ACTIONS = Set.of("feed", "water", "pet", "play", "rest", "wake");
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private ApiV3PetRules() { }

    public static String today(final long now) {
        return Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate().toString();
    }

    public static String name(final Object value) {
        if (!(value instanceof String raw) || raw.length() > 256) throw invalid("宠物名字最多 12 个字");
        // 保留组合 Emoji 的 ZWJ，拒绝隐藏控制、换行与双向重排；不解析 HTML。
        for (int i = 0; i < raw.length(); ) {
            final int cp = raw.codePointAt(i);
            if (Character.isISOControl(cp) || cp == '<' || cp == '>' || cp == 0x2028 || cp == 0x2029
                    || (Character.getType(cp) == Character.FORMAT && cp != 0x200D)
                    || (cp >= 0xD800 && cp <= 0xDFFF)) throw invalid("名字不能包含换行、控制字符或 HTML");
            i += Character.charCount(cp);
        }
        final String name = raw.strip();
        if (name.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp)
                || Character.getType(cp) == Character.FORMAT || Character.getType(cp) == Character.NON_SPACING_MARK))
            throw invalid("宠物名字不能只有空白或隐藏字符");
        final var matcher = GRAPHEME.matcher(name);
        int count = 0;
        while (matcher.find()) count++;
        if (count < 1 || count > NAME_LENGTH) throw invalid("宠物名字需为 1～12 个字");
        return name;
    }

    public static JSONObject catalog() {
        final JSONArray species = new JSONArray()
                .put(species("momota", "摸摸獭", "喜欢水边和小鱼的温柔伙伴"))
                .put(species("hexiaotun", "荷小豚", "戴着荷叶帽，慢慢长大的小伙伴"))
                .put(species("yubingmiao", "鱼饼喵", "好奇又贪吃，陪你一起摸鱼"))
                .put(species("paixiaoyu", "派小鱼", "熟悉的派小鱼，今天也一起玩吧"));
        return new JSONObject().put("version", VERSION).put("species", species)
                .put("growthRules", new JSONObject().put("experiencePerLevel", EXPERIENCE_PER_LEVEL).put("maxLevel", MAX_LEVEL)
                        .put("careExperience", CARE_EXPERIENCE).put("careFriendship", CARE_FRIENDSHIP))
                .put("readoptionRules", new JSONObject().put("coins", READOPTION_COINS))
                .put("stories", ApiV3PetStories.catalog())
                .put("items", new JSONArray().put(new JSONObject().put("id", "food").put("name", "基础口粮")
                        .put("price", FOOD_PRICE).put("hunger", FOOD_HUNGER)))
                .put("rules", new JSONObject().put("maxNameLength", NAME_LENGTH).put("initialCoins", INITIAL_COINS)
                        .put("initialFood", INITIAL_FOOD).put("dailyCoins", DAILY_COINS).put("dailyFood", DAILY_FOOD)
                        .put("restDurationMs", REST_DURATION).put("restEnergyPerHour", REST_ENERGY_PER_HOUR)
                        .put("growthDailyLimit", DAILY_GROWTH).put("careCooldownMs", CARE_COOLDOWN)
                        .put("waterGain", WATER_GAIN).put("petMood", PET_MOOD).put("playMood", PLAY_MOOD)
                        .put("playEnergy", PLAY_ENERGY));
    }

    private static JSONObject species(final String id, final String name, final String description) {
        return new JSONObject().put("id", id).put("name", name).put("description", description);
    }

    /** 读取与动作使用同一离线投影；保留小数余量，不因频繁操作丢失未满一小时的衰减。 */
    public static JSONObject settle(final JSONObject stored, final long clock) {
        final JSONObject pet = new JSONObject(stored.toString());
        final long last = pet.getLong("lastCalculatedAt");
        final long current = Math.max(clock, last);
        final double hours = (current - last) / (double) HOUR;
        pet.put("hunger", Math.max(0, pet.getDouble("hunger") - hours * 2));
        pet.put("water", Math.max(0, pet.getDouble("water") - hours * 3));
        pet.put("mood", Math.max(0, pet.getDouble("mood") - hours));
        if ("rest".equals(pet.getString("activityKind"))) {
            final long elapsed = Math.max(0, Math.min(current, pet.getLong("activityEndsAt"))
                    - Math.max(last, pet.getLong("activityStartedAt")));
            pet.put("energy", Math.min(100, pet.getDouble("energy") + elapsed / (double) HOUR * REST_ENERGY_PER_HOUR));
            if (current >= pet.getLong("activityEndsAt")) clearActivity(pet);
        }
        pet.put("lastCalculatedAt", current);
        return pet;
    }

    public static void clearActivity(final JSONObject pet) {
        pet.put("activityKind", "").put("activityStartedAt", 0L).put("activityEndsAt", 0L);
    }

    public static JSONObject publicPet(final JSONObject pet, final JSONObject account, final String today) {
        final JSONObject activity = "rest".equals(pet.getString("activityKind"))
                ? new JSONObject().put("kind", "rest").put("startedAt", pet.getLong("activityStartedAt"))
                        .put("endsAt", pet.getLong("activityEndsAt")) : null;
        return new JSONObject().put("id", pet.getString("oId")).put("speciesId", pet.getString("speciesId"))
                .put("name", pet.getString("name")).put("hunger", (int) Math.ceil(pet.getDouble("hunger") - 1e-9))
                .put("water", (int) Math.ceil(pet.getDouble("water") - 1e-9)).put("mood", (int) Math.ceil(pet.getDouble("mood") - 1e-9))
                .put("energy", (int) Math.floor(pet.getDouble("energy") + 1e-9)).put("experience", pet.getInt("experience"))
                .put("level", Math.min(MAX_LEVEL, 1 + pet.getInt("experience") / EXPERIENCE_PER_LEVEL)).put("friendship", pet.getInt("friendship"))
                .put("adoptedAt", pet.getLong("adoptedAt")).put("activity", activity == null ? JSONObject.NULL : activity)
                .put("cooldowns", new JSONObject().put("pet", pet.getLong("petAvailableAt")).put("play", pet.getLong("playAvailableAt")))
                .put("careAvailableAt", new JSONObject().put("feed", careAvailableAt(pet, "hunger", 2))
                        .put("water", careAvailableAt(pet, "water", 3)))
                .put("growthToday", new JSONObject().put("used", today.compareTo(account.getString("growthDate")) <= 0
                        ? account.getInt("growthCount") : 0).put("limit", DAILY_GROWTH));
    }

    /** 普通口粮与免费饮水达到下一次有效操作阈值的时刻，客户端只到点核对，不自行放行。 */
    private static long careAvailableAt(final JSONObject pet, final String field, final int decayPerHour) {
        final double missing = Math.max(0, pet.getDouble(field) - 99 - 1e-9);
        return pet.getLong("lastCalculatedAt") + (long) Math.ceil(missing / decayPerHour * HOUR);
    }

    public static ApiV3Exception invalid(final String message) {
        return new ApiV3Exception(422, "invalid_request", message);
    }

    public static ApiV3Exception conflict(final String code, final String message) {
        return new ApiV3Exception(409, code, message);
    }
}
