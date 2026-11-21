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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 事件链只响应服务器已成功的照顾和成果领取，进度及奖励按账号保留。 */
public final class ApiV3PetStories {
    private static final int VERSION = 1;
    public static final List<Story> STORIES = List.of(
            new Story("care-companion", "陪伴的日常", "从一顿饭、一杯水和一次玩耍，认识彼此。",
                    List.of(care("feed", "给宠物喂一次食物", "feed"), care("water", "给宠物饮水一次", "water"),
                            care("play", "陪宠物玩耍一次", "play")), List.of(new Reward("food", 2))),
            new Story("pond-keepsake", "荷塘见闻", "从观察课堂走向荷塘，再把发现带回课堂。",
                    List.of(activity("nature-class", "完成并领取自然观察课", "course", List.of("nature-basics")),
                            activity("lotus-trip", "完成并领取荷塘采集", "outing", List.of("lotus-pond")),
                            activity("nature-research", "完成并领取自然研究课", "course", List.of("nature-advanced"))),
                    List.of(new Reward("fruit", 2), new Reward("reed", 2))),
            new Story("first-career", "第一次小小工作", "学一点本领，试一次工作，再继续进修。",
                    List.of(activity("first-class", "完成并领取任意一门课程", "course", List.of()),
                            activity("first-work", "完成并领取任意见习工作", "work", List.of()),
                            activity("advanced-class", "完成并领取任意一门进阶课程", "course",
                                    ApiV3PetAdvancedCourses.COURSES.stream().map(ApiV3PetActivities.Entry::id).toList())),
                    List.of(new Reward("spring-water", 2), new Reward("sea-shell", 2))));

    private ApiV3PetStories() { }

    public record Reward(String itemId, int quantity) {
        public JSONObject json() {
            return new JSONObject().put("itemId", itemId).put("quantity", quantity);
        }
    }

    public record Step(String id, String description, String trigger, String action, String kind, List<String> entryIds) {
        public JSONObject json() {
            return new JSONObject().put("id", id).put("description", description).put("trigger", trigger)
                    .put("action", action).put("kind", kind).put("entryIds", new JSONArray(entryIds));
        }
    }

    public record Story(String id, String name, String description, List<Step> steps, List<Reward> rewards) {
        public JSONObject json() {
            final JSONArray sequence = new JSONArray(), items = new JSONArray();
            for (Step step : steps) sequence.put(step.json());
            for (Reward reward : rewards) items.put(reward.json());
            return new JSONObject().put("id", id).put("name", name).put("description", description).put("steps", sequence)
                    .put("rewards", new JSONObject().put("items", items));
        }
    }

    private static Step care(final String id, final String description, final String action) {
        return new Step(id, description, "care", action, "", List.of());
    }

    private static Step activity(final String id, final String description, final String kind, final List<String> entries) {
        return new Step(id, description, "activity", "", kind, entries);
    }

    public static boolean known(final String id) {
        return STORIES.stream().anyMatch(story -> story.id().equals(id));
    }

    public static Story definition(final String id) {
        return STORIES.stream().filter(story -> story.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiV3PetRules.invalid("这段事件暂未开放"));
    }

    public static JSONArray catalog() {
        final JSONArray catalog = new JSONArray();
        for (Story story : STORIES) {
            final JSONObject item = story.json();
            final JSONArray steps = item.getJSONArray("steps");
            // 公开目录只给阅读所需字段，实际触发条件由服务器处理，不接收客户端上报进度。
            for (int index = 0; index < steps.length(); index++) {
                final JSONObject step = steps.getJSONObject(index);
                for (String field : Set.of("trigger", "action", "kind", "entryIds")) step.remove(field);
            }
            catalog.put(item);
        }
        return catalog;
    }

    public static String freeze(final Story story) {
        return new JSONObject().put("version", VERSION).put("story", story.json()).toString();
    }

    /** 首次推进便冻结步骤与奖励，后续目录调整不能改变已开始事件的待领成果。 */
    public static JSONObject snapshot(final JSONObject row, final String actor) {
        try {
            if (!actor.equals(row.getString("userId")) || !known(row.getString("storyId"))) throw unavailable();
            final JSONObject snapshot = new JSONObject(row.getString("snapshotJson"));
            keys(snapshot, Set.of("version", "story"));
            integer(snapshot, "version", VERSION, VERSION);
            final JSONObject story = snapshot.getJSONObject("story");
            keys(story, Set.of("id", "name", "description", "steps", "rewards"));
            if (!row.getString("storyId").equals(story.getString("id"))) throw unavailable();
            text(story, "name", 80);
            text(story, "description", 512);
            final JSONArray steps = story.getJSONArray("steps");
            if (steps.length() != 3) throw unavailable();
            final Set<String> ids = new HashSet<>();
            for (int index = 0; index < steps.length(); index++) {
                final JSONObject step = steps.getJSONObject(index);
                keys(step, Set.of("id", "description", "trigger", "action", "kind", "entryIds"));
                final String id = step.getString("id"), trigger = step.getString("trigger");
                if (!id.matches("[a-z][a-z0-9-]{0,47}") || !ids.add(id)) throw unavailable();
                text(step, "description", 512);
                final JSONArray entries = step.getJSONArray("entryIds");
                if (entries.length() > 32) throw unavailable();
                final Set<String> seen = new HashSet<>();
                for (int entry = 0; entry < entries.length(); entry++) {
                    final String value = entries.getString(entry);
                    if (!value.matches("[a-z][a-z0-9-]{0,47}") || !seen.add(value)) throw unavailable();
                }
                if ("care".equals(trigger)) {
                    if (!ApiV3PetRules.ACTIONS.contains(step.getString("action"))
                            || !step.getString("kind").isEmpty() || !entries.isEmpty()) throw unavailable();
                } else if (!"activity".equals(trigger) || !step.getString("action").isEmpty()
                        || !Set.of("outing", "course", "work").contains(step.getString("kind"))) throw unavailable();
            }
            final JSONObject rewards = story.getJSONObject("rewards");
            keys(rewards, Set.of("items"));
            if (rewards.getJSONArray("items").isEmpty()) throw unavailable();
            ApiV3PetActivities.validateItems(rewards.getJSONArray("items"));
            integer(row, "completedSteps", 1, steps.length());
            integer(row, "completedAt", 0, Long.MAX_VALUE);
            integer(row, "claimedAt", 0, Long.MAX_VALUE);
            integer(row, "updatedAt", 0, Long.MAX_VALUE);
            if ((row.getInt("completedSteps") == steps.length()) != (row.getLong("completedAt") > 0)
                    || (row.getLong("claimedAt") > 0 && row.getLong("completedAt") == 0)
                    || (row.getLong("claimedAt") > 0 && row.getLong("claimedAt") < row.getLong("completedAt"))
                    || row.getLong("updatedAt") < Math.max(row.getLong("completedAt"), row.getLong("claimedAt"))) throw unavailable();
            return story;
        } catch (ApiV3Exception error) {
            throw error;
        } catch (RuntimeException error) {
            throw unavailable();
        }
    }

    public static boolean matches(final JSONObject step, final String trigger, final String action, final String kind, final String entryId) {
        if (!trigger.equals(step.getString("trigger"))) return false;
        if ("care".equals(trigger)) return action.equals(step.getString("action"));
        if (!kind.equals(step.getString("kind"))) return false;
        final JSONArray entries = step.getJSONArray("entryIds");
        if (entries.isEmpty()) return true;
        for (int index = 0; index < entries.length(); index++) if (entryId.equals(entries.getString(index))) return true;
        return false;
    }

    public static JSONObject state(final Story definition, final JSONObject row) {
        final int completed = row == null ? 0 : row.getInt("completedSteps");
        final long completedAt = row == null ? 0 : row.getLong("completedAt"), claimedAt = row == null ? 0 : row.getLong("claimedAt");
        return new JSONObject().put("id", definition.id()).put("completedSteps", completed)
                .put("status", claimedAt > 0 ? "claimed" : completedAt > 0 ? "claimable" : "active")
                .put("completedAt", completedAt > 0 ? completedAt : JSONObject.NULL)
                .put("claimedAt", claimedAt > 0 ? claimedAt : JSONObject.NULL);
    }

    private static void keys(final JSONObject value, final Set<String> fields) {
        if (!value.keySet().equals(fields)) throw unavailable();
    }

    private static void text(final JSONObject value, final String field, final int limit) {
        final String text = value.getString(field);
        if (text.isBlank() || text.length() > limit || text.codePoints().anyMatch(Character::isISOControl)) throw unavailable();
    }

    private static void integer(final JSONObject value, final String field, final long minimum, final long maximum) {
        if (!(value.opt(field) instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.longValue() < minimum || number.longValue() > maximum
                || number.doubleValue() != number.longValue()) throw unavailable();
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "宠物事件暂不可用");
    }
}
