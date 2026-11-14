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

/** 六门进阶课程仍走普通计时活动，门槛和成果统一从本目录冻结。 */
public final class ApiV3PetAdvancedCourses {
    public static final List<ApiV3PetActivities.Entry> COURSES = List.of(
            course("fitness-advanced", "体能训练课", "通过协调与耐力练习提高体能", "vitality"),
            course("nature-advanced", "自然研究课", "记录植物与潮汐，练习独立探索", "exploration"),
            course("reading-advanced", "阅读研习课", "理解故事线索，整理新的知识", "knowledge"),
            course("expression-advanced", "表达进阶课", "把观察到的故事清楚地分享给伙伴", "expression"),
            course("craft-advanced", "创作进阶课", "练习组合材料，完成更精细的小作品", "crafting"),
            course("manners-advanced", "伙伴协作课", "学习协作与照顾伙伴的方法", "kindness"));

    private ApiV3PetAdvancedCourses() { }

    private static ApiV3PetActivities.Entry course(final String id, final String name, final String description, final String skill) {
        // 同样三十分钟与三次入门课成长相当，但节省宠物币和精力，体现进修价值。
        return new ApiV3PetActivities.Entry("course", id, name, description, 30, 10, 14, 5, 7, 45, 0, 5, skill, 30);
    }

    public static JSONObject json(final ApiV3PetActivities.Entry entry) {
        return entry.json().put("requirements", new JSONArray()
                .put(new JSONObject().put("skillId", entry.skillId()).put("experience", 20)));
    }

    public static boolean known(final String id) {
        return COURSES.stream().anyMatch(course -> course.id().equals(id));
    }
}
