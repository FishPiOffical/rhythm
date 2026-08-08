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

import java.util.List;

/** 一个等级及其展示和奖励快照。 */
public record ProfessionLevelDraft(String levelCode, int sortOrder, long requiredTotalExperience,
                                   String displayName, String shortName, String description,
                                   String achievementDescription, boolean topLevel,
                                   List<ProfessionLevelPresentationDraft> presentations,
                                   List<ProfessionLevelRewardDraft> rewards) {

    public ProfessionLevelDraft {
        presentations = null == presentations ? null : List.copyOf(presentations);
        rewards = null == rewards ? null : List.copyOf(rewards);
    }
}
