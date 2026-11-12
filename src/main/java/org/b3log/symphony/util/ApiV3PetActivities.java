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

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 外出与入门课程的试运行目录；费用和成果均在出发时冻结，领取不重新计算目录。 */
public final class ApiV3PetActivities {
    public static final int VERSION = 1;
    public static final List<Skill> SKILLS = List.of(
            new Skill("vitality", "体能", "散步、运动和远足积累的好体力"),
            new Skill("exploration", "探索", "观察身边的变化，发现新事物"),
            new Skill("knowledge", "知识", "读书和课堂里学到的小知识"),
            new Skill("expression", "表达", "练习把想法好好说出来"),
            new Skill("crafting", "手作", "动手制作的小小成就"),
            new Skill("kindness", "亲和", "学会礼貌相处，结交新伙伴"));
    public static final List<Entry> ENTRIES = List.of(
            new Entry("outing", "plaza", "广场散步", "绕广场走一走，认识附近的小伙伴", 5, 0, 5, 2, 3, 10, 0, 8, "exploration", 3),
            new Entry("outing", "riverside", "河岸探索", "沿着河岸找找新发现，也带一点口粮回来", 15, 3, 10, 4, 6, 20, 1, 10, "exploration", 6),
            new Entry("outing", "forest", "林间远足", "穿过林间小径，锻炼体力并收集口粮", 30, 5, 15, 6, 10, 30, 1, 12, "vitality", 8),
            course("fitness-basics", "活力运动课", "认识热身与基础运动", "vitality"),
            course("nature-basics", "自然观察课", "观察植物和小动物", "exploration"),
            course("reading-basics", "阅读启蒙课", "读一段故事，记住一个新知识", "knowledge"),
            course("expression-basics", "表达练习课", "试着向伙伴介绍自己的想法", "expression"),
            course("craft-basics", "创意手作课", "用简单材料完成一个小作品", "crafting"),
            course("manners-basics", "友善礼仪课", "学习倾听、分享与互相帮助", "kindness"));

    private ApiV3PetActivities() { }

    public record Skill(String id, String name, String description) {
        public JSONObject json() {
            return new JSONObject().put("id", id).put("name", name).put("description", description);
        }
    }

    public record Entry(String kind, String id, String name, String description, int minutes, int coins, int energy,
                        int hunger, int water, int experience, int food, int mood, String skillId, int skillExperience) {
        public JSONObject json() {
            return new JSONObject().put("id", id).put("name", name).put("description", description).put("durationMs", minutes * 60_000L)
                    .put("cost", new JSONObject().put("coins", coins).put("energy", energy).put("hunger", hunger).put("water", water))
                    .put("rewards", new JSONObject().put("experience", experience).put("food", food).put("mood", mood)
                            .put("skills", new JSONArray().put(new JSONObject().put("id", skillId).put("experience", skillExperience))));
        }
    }

    private static Entry course(final String id, final String name, final String description, final String skillId) {
        return new Entry("course", id, name, description, 10, 5, 8, 3, 4, 15, 0, 3, skillId, 10);
    }

    public static JSONObject extendCatalog(final JSONObject catalog) {
        final JSONArray places = new JSONArray(), courses = new JSONArray(), skills = new JSONArray();
        for (Skill skill : SKILLS) skills.put(skill.json());
        for (Entry entry : ENTRIES) (entry.kind().equals("outing") ? places : courses).put(entry.json());
        return catalog.put("activityVersion", VERSION).put("skills", skills).put("places", places).put("courses", courses);
    }

    public static JSONObject find(final String kind, final String id) {
        return ENTRIES.stream().filter(entry -> entry.kind().equals(kind) && entry.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiV3PetRules.invalid("这个活动暂未开放")).json();
    }

    public static boolean skill(final String id) {
        return SKILLS.stream().anyMatch(skill -> skill.id().equals(id));
    }

    public static int level(final int experience) {
        return Math.min(30, 1 + experience / 100);
    }

    /** 存储快照也做白名单与有界验证，坏快照不能被当成零费用或空奖励继续结算。 */
    public static JSONObject snapshot(final JSONObject row) {
        try {
            final JSONObject snapshot = new JSONObject(row.getString("snapshotJson"));
            keys(snapshot, Set.of("version", "entry"));
            integer(snapshot, "version", 1, Integer.MAX_VALUE);
            final JSONObject entry = snapshot.getJSONObject("entry");
            keys(entry, Set.of("id", "name", "description", "durationMs", "cost", "rewards"));
            if (!entry.getString("id").equals(row.getString("entryId"))
                    || !entry.getString("id").matches("[a-z][a-z0-9-]{0,47}")) throw unavailable();
            text(entry, "name", 64);
            text(entry, "description", 256);
            final int duration = integer(entry, "durationMs", 1, 86_400_000);
            final JSONObject cost = entry.getJSONObject("cost"), rewards = entry.getJSONObject("rewards");
            keys(cost, Set.of("coins", "energy", "hunger", "water"));
            integer(cost, "coins", 0, 1_000_000);
            for (String field : Set.of("energy", "hunger", "water")) integer(cost, field, 0, 100);
            keys(rewards, Set.of("experience", "food", "mood", "skills"));
            integer(rewards, "experience", 0, 10_000);
            integer(rewards, "food", 0, 100);
            integer(rewards, "mood", 0, 100);
            final JSONArray skills = rewards.getJSONArray("skills");
            if (skills.length() < 1 || skills.length() > SKILLS.size()) throw unavailable();
            final Set<String> seen = new HashSet<>();
            for (int i = 0; i < skills.length(); i++) {
                final JSONObject reward = skills.getJSONObject(i);
                keys(reward, Set.of("id", "experience"));
                final String id = reward.getString("id");
                if (!skill(id) || !seen.add(id)) throw unavailable();
                integer(reward, "experience", 1, 10_000);
            }
            final long started = row.getLong("startedAt"), ended = row.getLong("endsAt");
            if (started < 0 || ended < started || ended - started != duration
                    || !Set.of("outing", "course").contains(row.getString("kind")) || row.getLong("claimedAt") < 0) throw unavailable();
            return entry;
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }

    public static JSONObject publicActivity(final JSONObject row, final long current) {
        final JSONObject entry = snapshot(row);
        return new JSONObject().put("id", row.getString("oId")).put("kind", row.getString("kind"))
                .put("entryId", row.getString("entryId")).put("name", entry.getString("name"))
                .put("startedAt", row.getLong("startedAt")).put("endsAt", row.getLong("endsAt"))
                .put("status", current >= row.getLong("endsAt") ? "claimable" : "running")
                .put("cost", entry.getJSONObject("cost")).put("rewards", entry.getJSONObject("rewards"));
    }

    private static void keys(final JSONObject object, final Set<String> fields) {
        if (!object.keySet().equals(fields)) throw unavailable();
    }

    private static void text(final JSONObject object, final String field, final int maximum) {
        if (!(object.opt(field) instanceof String text) || text.isEmpty() || text.length() > maximum
                || text.codePoints().anyMatch(Character::isISOControl)) throw unavailable();
    }

    private static int integer(final JSONObject object, final String field, final int minimum, final int maximum) {
        if (!(object.opt(field) instanceof Number number)) throw unavailable();
        final int value = new BigDecimal(number.toString()).intValueExact();
        if (value < minimum || value > maximum) throw unavailable();
        return value;
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "宠物活动暂不可用，请稍后再试");
    }
}
