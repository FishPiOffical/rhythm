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
import java.util.Set;

/** 宠物瞬时事件的严格快照；坏记录明确报错，不能把消费或奖励伪装为零。 */
public final class ApiV3PetJournal {
    private static final Set<String> STATS = Set.of("hunger", "water", "mood", "energy");
    private static final Set<String> EFFECTS = Set.of("hunger", "water", "mood", "energy", "experience", "friendship");
    private static final Set<String> FIELDS = Set.of("recordType", "id", "petId", "kind", "action", "name",
            "occurredAt", "itemChanges", "coinsDelta", "effects", "collectionId");

    private ApiV3PetJournal() { }

    public static String actionName(final String action) {
        return switch (action) {
            case "feed" -> "喂食";
            case "water" -> "饮水";
            case "pet" -> "抚摸";
            case "play" -> "玩耍";
            case "rest" -> "休息";
            case "wake" -> "醒来";
            default -> throw unavailable();
        };
    }

    public static JSONObject zeroEffects() {
        final JSONObject result = new JSONObject();
        for (String field : EFFECTS) result.put(field, 0);
        return result;
    }

    public static JSONObject effects(final JSONObject before, final JSONObject after) {
        final JSONObject result = new JSONObject();
        for (String field : STATS) result.put(field, after.getDouble(field) - before.getDouble(field));
        for (String field : Set.of("experience", "friendship"))
            result.put(field, after.getInt(field) - before.getInt(field));
        return result;
    }

    public static void validate(final JSONObject event) {
        try {
            if (!event.keySet().equals(FIELDS) || !"event".equals(event.getString("recordType"))) throw unavailable();
            for (String field : Set.of("id", "petId"))
                if (!event.getString(field).matches("[0-9]{1,19}")) throw unavailable();
            final String kind = event.getString("kind"), action = event.getString("action");
            if ("care".equals(kind)) {
                if (!ApiV3PetRules.ACTIONS.contains(action)) throw unavailable();
            } else if ("story".equals(kind)) {
                if (!"story-claim".equals(action)) throw unavailable();
            } else if (!Set.of("craft", "purchase", "readoption").contains(kind) || !kind.equals(action)) throw unavailable();
            final String name = event.getString("name");
            if (name.isBlank() || name.length() > 80 || name.codePoints().anyMatch(Character::isISOControl)) throw unavailable();
            integer(event, "occurredAt", 0, Long.MAX_VALUE);
            integer(event, "coinsDelta", Integer.MIN_VALUE, 0);
            if (Set.of("care", "story").contains(kind) && event.getInt("coinsDelta") != 0) throw unavailable();
            final JSONObject effects = event.getJSONObject("effects");
            if (!effects.keySet().equals(EFFECTS)) throw unavailable();
            for (String field : STATS) {
                if (!(effects.opt(field) instanceof Number number) || !Double.isFinite(number.doubleValue())
                        || number.doubleValue() < -100 || number.doubleValue() > 100) throw unavailable();
            }
            integer(effects, "experience", 0, Integer.MAX_VALUE);
            integer(effects, "friendship", 0, 100);
            // 制作和购买不改变宠物数值；历史记录不能携带额外的隐含奖励。
            if (!"care".equals(kind)) {
                for (String field : EFFECTS) if (effects.getDouble(field) != 0) throw unavailable();
            }
            final JSONArray items = event.getJSONArray("itemChanges");
            if (items.length() > ApiV3PetExploration.ITEMS.size()) throw unavailable();
            final Set<String> seen = new HashSet<>();
            for (int index = 0; index < items.length(); index++) {
                final JSONObject item = items.getJSONObject(index);
                if (!item.keySet().equals(Set.of("itemId", "quantity"))) throw unavailable();
                final String itemId = item.getString("itemId");
                if (!ApiV3PetExploration.knownItem(itemId) || !seen.add(itemId)) throw unavailable();
                integer(item, "quantity", Integer.MIN_VALUE, Integer.MAX_VALUE);
                final int quantity = item.getInt("quantity");
                if (quantity == 0 || (Set.of("purchase", "story").contains(kind) ? quantity < 0 : quantity > 0)) throw unavailable();
            }
            if ("story".equals(kind) && items.isEmpty()) throw unavailable();
            if ("readoption".equals(kind) && (!items.isEmpty() || event.getInt("coinsDelta") >= 0)) throw unavailable();
            if ("craft".equals(kind)) {
                if (!ApiV3PetExploration.knownCollection(event.getString("collectionId")) || items.isEmpty()) throw unavailable();
            } else if (!event.isNull("collectionId")) throw unavailable();
        } catch (ApiV3Exception error) {
            throw error;
        } catch (RuntimeException error) {
            throw unavailable();
        }
    }

    /** 元数据和快照双向核对，避免脏行或联查错误把其他账号的日志投影出去。 */
    public static JSONObject read(final JSONObject row, final String actor) {
        try {
            final JSONObject event = new JSONObject(row.getString("eventJson"));
            validate(event);
            if (!actor.equals(row.getString("eventUserId")) || !event.getString("id").equals(row.getString("eventId"))
                    || !event.getString("petId").equals(row.getString("eventPetId"))
                    || !event.getString("kind").equals(row.getString("eventKind"))
                    || !event.getString("action").equals(row.getString("eventAction"))
                    || event.getLong("occurredAt") != row.getLong("eventOccurredAt")) throw unavailable();
            return event;
        } catch (ApiV3Exception error) {
            throw error;
        } catch (RuntimeException error) {
            throw unavailable();
        }
    }

    private static void integer(final JSONObject value, final String field, final long minimum, final long maximum) {
        if (!(value.opt(field) instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.longValue() < minimum || number.longValue() > maximum
                || number.doubleValue() != number.longValue()) throw unavailable();
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "宠物记录暂不可用");
    }
}
