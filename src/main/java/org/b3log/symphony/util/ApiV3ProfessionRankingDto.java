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
import java.math.BigDecimal;
import java.util.Set;

/** 排行条目仅公开身份字段与历史职业展示信息，经验值按可见性投影。 */
public final class ApiV3ProfessionRankingDto {
    private ApiV3ProfessionRankingDto() { }
    private static final Set<String> MODULES = Set.of("primaryProfession", "allProfessions", "experience", "rarity",
            "majorContributions", "contributionStats", "activityFeed", "levelHistory", "rewards", "ranking");
    private static final Set<String> SCOPES = Set.of("PUBLIC", "LOGGED_IN", "SELF", "HIDDEN");
    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "职业排行暂不可用");
    }
    public static String text(final JSONObject row, final String key, final int max, final boolean empty) {
        if (!(row.opt(key) instanceof String value) || value.length() > max || (!empty && value.isBlank())
                || value.codePoints().anyMatch(Character::isISOControl)) throw unavailable();
        try {
            ApiV3Json.canonical(value);
        } catch (final IllegalArgumentException e) {
            throw unavailable();
        }
        return value;
    }
    public static long number(final JSONObject row, final String key) {
        if (!(row.opt(key) instanceof Number value)) throw unavailable();
        try {
            final long result = new BigDecimal(value.toString()).longValueExact();
            if (result < 0) throw unavailable();
            return result;
        } catch (final ArithmeticException | NumberFormatException e) {
            throw unavailable();
        }
    }
    public static JSONObject visibility(final JSONObject row) {
        final Object stored = row.opt("privacyId");
        if (stored == null || stored == JSONObject.NULL) {
            final JSONObject result = new JSONObject();
            for (final String module : MODULES) result.put(module, "PUBLIC");
            return result;
        }
        text(row, "privacyId", 19, false);
        if (!(row.opt("moduleVisibilityJson") instanceof String json) || json.isBlank() || json.length() > 4096) throw unavailable();
        try {
            ApiV3Json.canonical(json);
        } catch (final IllegalArgumentException e) {
            throw unavailable();
        }
        final JSONObject result = new JSONObject(json);
        if (!result.keySet().equals(MODULES)) throw unavailable();
        for (final String module : MODULES) {
            if (!(result.opt(module) instanceof String scope) || !SCOPES.contains(scope)) throw unavailable();
        }
        return result;
    }
    public static boolean visible(final JSONObject visibility, final String module, final String owner, final String viewer) {
        return switch (visibility.getString(module)) {
            case "PUBLIC" -> true;
            case "LOGGED_IN" -> viewer != null && !viewer.isBlank();
            case "SELF" -> owner.equals(viewer);
            default -> false;
        };
    }
    public static JSONObject entry(final JSONObject row, final long rank, final boolean experienceVisible) {
        for (final String status : Set.of("revisionStatus", "schemeStatus")) {
            if (!Set.of("PUBLISHED", "RETIRED").contains(row.optString(status))) throw unavailable();
        }
        final String name = text(row, "userName", 255, false);
        final String nickname = text(row, "userNickname", 255, true);
        final JSONObject result = new JSONObject().put("rank", rank).put("userName", name).put("userNickname", nickname.isBlank() ? name : nickname)
                .put("userAvatarURL", text(row, "userAvatarURL", 2048, true))
                .put("professionId", text(row, "professionId", 19, false))
                .put("professionCode", text(row, "professionCode", 64, false))
                .put("professionName", text(row, "professionName", 64, false))
                .put("levelCode", text(row, "levelCode", 64, false)).put("levelName", text(row, "levelName", 64, false));
        if (experienceVisible) result.put("totalExperience", number(row, "totalExperience"));
        return result;
    }
}
