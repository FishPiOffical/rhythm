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

import java.util.List;

/** 见习工作试运行目录；学习技能负责解锁，工作熟练度单独累计，不代替上课。 */
public final class ApiV3PetWork {
    public static final int DAILY_LIMIT = 60;
    public static final List<Job> JOBS = List.of(
            new Job("delivery-assistant", "配送见习", "帮邻居把小包裹送到家", "vitality"),
            new Job("trail-assistant", "巡游向导", "带伙伴认识熟悉的小路", "exploration"),
            new Job("library-assistant", "图书助理", "整理书架，让好故事更容易被找到", "knowledge"),
            new Job("broadcast-assistant", "播报学徒", "为社区播报今日的小消息", "expression"),
            new Job("craft-assistant", "手作学徒", "协助制作实用的小物件", "crafting"),
            new Job("care-assistant", "照护见习", "帮助照顾社区的小伙伴", "kindness"));

    private ApiV3PetWork() { }

    public record Job(String id, String name, String description, String skillId) {
        public JSONObject json() {
            return new JSONObject().put("id", id).put("name", name).put("description", description).put("durationMs", 1_200_000)
                    .put("cost", new JSONObject().put("coins", 0).put("energy", 12).put("hunger", 4).put("water", 6))
                    .put("rewards", new JSONObject().put("experience", 15).put("food", 0).put("mood", 3)
                            .put("skills", new JSONArray()).put("coins", 12).put("proficiency", 10))
                    .put("requirement", new JSONObject().put("skillId", skillId).put("experience", 10));
        }
    }

    public static boolean known(final String id) {
        return JOBS.stream().anyMatch(job -> job.id().equals(id));
    }

    public static JSONObject find(final String id) {
        return JOBS.stream().filter(job -> job.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiV3PetRules.invalid("这份工作暂未开放")).json();
    }

    public static JSONArray catalog() {
        final JSONArray result = new JSONArray();
        for (Job job : JOBS) result.put(job.json());
        return result;
    }
}
