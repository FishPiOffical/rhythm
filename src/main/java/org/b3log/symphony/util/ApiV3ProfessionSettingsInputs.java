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

import org.json.JSONObject;

import java.util.Set;

/**
 * 严格校验职业预设和完整 CUSTOM 映射的 JSON 类型。
 */
public final class ApiV3ProfessionSettingsInputs {
    private ApiV3ProfessionSettingsInputs() {
    }

    private static final Set<String> PRESETS = Set.of("ALL_PUBLIC", "LEVEL_ONLY", "PRIMARY_ONLY", "SELF_ONLY", "FULLY_HIDDEN", "CUSTOM");
    private static final Set<String> MODULES = Set.of("primaryProfession", "allProfessions", "experience", "rarity",
            "majorContributions", "contributionStats", "activityFeed", "levelHistory", "rewards", "ranking");
    private static final Set<String> SCOPES = Set.of("PUBLIC", "LOGGED_IN", "SELF", "HIDDEN");

    public static void onboarding(final JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("onboardingState"))
                || !"SKIPPED".equals(body.opt("onboardingState"))) throw ApiV3WriteInputs.invalid();
    }

    public static JSONObject privacy(final JSONObject body) {
        if (body == null || !Set.of("preset", "moduleVisibility").containsAll(body.keySet())
                || !(body.opt("preset") instanceof String preset) || !PRESETS.contains(preset))
            throw ApiV3WriteInputs.invalid();
        JSONObject supplied = null;
        if (body.has("moduleVisibility")) {
            if (!(body.opt("moduleVisibility") instanceof JSONObject value) || value.toString().length() > 4096
                    || !MODULES.containsAll(value.keySet())) throw ApiV3WriteInputs.invalid();
            for (final String module : value.keySet()) {
                if (!(value.opt(module) instanceof String visibility) || !SCOPES.contains(visibility))
                    throw ApiV3WriteInputs.invalid();
            }
            supplied = value;
        }
        final JSONObject visibility;
        if ("CUSTOM".equals(preset)) {
            if (supplied == null || !supplied.keySet().equals(MODULES)) throw ApiV3WriteInputs.invalid();
            visibility = new JSONObject(supplied.toString());
        } else {
            // 预设允许携带可选映射，但仅校验结构，不持久化其值。
            visibility = presetVisibility(preset);
        }
        return new JSONObject().put("preset", preset).put("moduleVisibility", visibility);
    }

    public static JSONObject presetVisibility(final String preset) {
        if (!PRESETS.contains(preset) || "CUSTOM".equals(preset)) throw ApiV3WriteInputs.invalid();
        final JSONObject result = new JSONObject();
        for (final String module : MODULES) {
            final String visibility = switch (preset) {
                case "ALL_PUBLIC" -> "PUBLIC";
                case "LEVEL_ONLY", "PRIMARY_ONLY" -> "primaryProfession".equals(module) ? "PUBLIC" : "HIDDEN";
                case "SELF_ONLY" -> "SELF";
                case "FULLY_HIDDEN" -> "HIDDEN";
                default -> throw ApiV3WriteInputs.invalid();
            };
            result.put(module, visibility);
        }
        return result;
    }
}
