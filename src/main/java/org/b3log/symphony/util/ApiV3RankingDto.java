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

import org.b3log.latke.Keys;
import org.b3log.symphony.model.Link;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 排行榜仅返回明确列出的分数和展示字段，省略任意存档及用户字段。
 */
public final class ApiV3RankingDto {
    public static final Set<String> TYPES = Set.of("links", "balance", "consumption", "checkin", "online", "adr", "mofish",
            "smallmofish", "lifeRestart", "evolve", "emoji", "xiaoice", "invite", "donate", "perfect");

    private ApiV3RankingDto() {
    }

    public static String subType(final String type, final String raw) {
        if (!TYPES.contains(type)) throw invalid();
        final String value = null == raw ? ("evolve".equals(type) ? "achievement" : "xiaoice".equals(type) ? "0" : "") : raw.trim();
        final Set<String> allowed = switch (type) {
            case "evolve" -> Set.of("achievement", "know", "reset", "days");
            case "xiaoice" -> Set.of("0", "1", "2", "3");
            default -> Set.of("");
        };
        if (!allowed.contains(value)) throw invalid();
        return value;
    }

    /**
     * org.json may wrap put(Collection) as JSONArray; legacy handlers must accept both.
     */
    public static List<JSONObject> rows(final Object value) {
        final List<JSONObject> rows = new ArrayList<>();
        if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) if (array.opt(i) instanceof JSONObject row) rows.add(row);
        } else if (value instanceof Iterable<?> iterable) {
            for (final Object item : iterable) if (item instanceof JSONObject row) rows.add(row);
        }
        return rows;
    }

    public static JSONObject entry(final String type, final String subType, final int rank, final JSONObject row, final JSONObject user) {
        subType(type, subType);
        final JSONObject metrics = new JSONObject();
        final String scoreField;
        switch (type) {
            case "balance" -> {
                scoreField = "points";
                metrics.put(scoreField, row.optLong("userPoint"));
            }
            case "consumption" -> {
                scoreField = "points";
                metrics.put(scoreField, row.optLong("userUsedPoint"));
            }
            case "checkin" -> {
                scoreField = "streak";
                metrics.put(scoreField, row.optLong("userCurrentCheckinStreak"));
            }
            case "online" -> {
                scoreField = "minutes";
                metrics.put(scoreField, row.optLong("onlineMinute"));
            }
            case "invite" -> {
                scoreField = "count";
                metrics.put(scoreField, row.optLong("c"));
            }
            case "perfect" -> {
                scoreField = "count";
                metrics.put(scoreField, row.optLong("count"));
            }
            case "donate" -> {
                scoreField = "amount";
                metrics.put(scoreField, decimal(row, "total")).put("count", row.optLong("totalCount"));
            }
            case "links" -> {
                scoreField = "clickCount";
                metrics.put(scoreField, row.optLong(Link.LINK_CLICK_CNT));
            }
            case "lifeRestart" -> {
                scoreField = "times";
                final JSONObject data = row.optJSONObject("data");
                metrics.put(scoreField, null == data ? 0 : data.optLong("times")).put("achievement", row.optInt("achievement"));
            }
            case "evolve" -> {
                scoreField = subType;
                final JSONObject data = row.optJSONObject("data");
                final JSONObject top = null == data ? null : data.optJSONObject("top");
                final JSONObject stats = null == data ? null : data.optJSONObject("stats");
                for (final String key : new String[]{"achievement", "know", "reset", "days"})
                    metrics.put(key, null == top ? 0 : top.optLong(key));
                metrics.put("currentKnow", null == stats ? 0 : stats.optLong("know")).put("currentDays", null == stats ? 0 : stats.optLong("days"));
            }
            case "xiaoice" -> {
                scoreField = switch (subType) {
                    case "1" -> "dieTimes";
                    case "2" -> "allExTimes";
                    case "3" -> "allBsTimes";
                    default -> "exp";
                };
                metrics.put(scoreField, row.optLong(scoreField));
                if ("0".equals(subType))
                    metrics.put("ancestry", row.optString("ancestry")).put("gongfa", row.optString("gongfa"));
            }
            default -> {
                scoreField = "points";
                metrics.put(scoreField, row.optLong("point"));
            }
        }
        final JSONObject result = new JSONObject().put("rank", rank).put("score", metrics.get(scoreField)).put("metrics", metrics);
        if ("links".equals(type)) return result.put("link", new JSONObject().put("id", row.optString(Keys.OBJECT_ID))
                .put("title", row.optString(Link.LINK_TITLE)).put("url", ApiV3CommunityDto.safeUrl(row.optString(Link.LINK_ADDR))));
        final JSONObject profile = new JSONObject();
        for (final String key : new String[]{"id", "userName", "userNickname", "userAvatarURL"})
            profile.put(key, null == user ? "" : user.optString(key));
        return result.put("user", profile);
    }

    public static JSONObject summary(final JSONObject raw) {
        return new JSONObject().put("amount", null == raw ? 0 : decimal(raw, "totalAmount"))
                .put("supportedDays", null == raw ? 0 : decimal(raw, "donateMakeDays"));
    }

    private static double decimal(final JSONObject row, final String key) {
        final double value = row.optDouble(key, 0);
        return Double.isFinite(value) ? value : 0;
    }

    private static ApiV3Exception invalid() {
        return new ApiV3Exception(422, "invalid_request", "排行榜参数不合法");
    }
}
