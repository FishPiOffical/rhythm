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
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在隔离环境中验证实际排行响应协议。
 */
public class ApiV3RankingDtoTest {
    private static JSONObject profile() {
        return new JSONObject().put("id", "12").put("userName", "public").put("userNickname", "Public").put("userAvatarURL", "/avatar.png")
                .put("userEmail", "SECRET-email").put("userPassword", "SECRET-password").put("token", "SECRET-token");
    }

    @Test
    public void closedTypesAndSubTypesRejectArbitraryFieldsAndUrls() {
        Assert.assertEquals(ApiV3RankingDto.TYPES.size(), 15);
        Assert.assertEquals(ApiV3RankingDto.subType("evolve", null), "achievement");
        Assert.assertEquals(ApiV3RankingDto.subType("xiaoice", null), "0");
        for (final String type : ApiV3RankingDto.TYPES) {
            Assert.assertNotNull(ApiV3RankingDto.subType(type, null));
            for (final String input : new String[]{"https://evil.example/", "family", "userPassword", "-1", "4", "days;DROP TABLE user", "\n"}) {
                try {
                    ApiV3RankingDto.subType(type, input);
                    if (!input.isBlank() || Set.of("evolve", "xiaoice").contains(type))
                        Assert.fail("Invalid subtype accepted");
                } catch (final ApiV3Exception expected) {
                    Assert.assertEquals(expected.status, 422);
                }
            }
        }
        try {
            ApiV3RankingDto.subType("missing", null);
            Assert.fail();
        } catch (final ApiV3Exception expected) {
            Assert.assertEquals(expected.status, 422);
        }
        for (final String sub : List.of("achievement", "know", "reset", "days"))
            Assert.assertEquals(ApiV3RankingDto.subType("evolve", sub), sub);
        for (final String sub : List.of("0", "1", "2", "3"))
            Assert.assertEquals(ApiV3RankingDto.subType("xiaoice", sub), sub);
    }

    @Test
    public void allUserBoardsHaveNamedNumericScoresAndStrictNestedWhitelist() {
        final Map<String, String> fields = Map.ofEntries(Map.entry("balance", "userPoint"), Map.entry("consumption", "userUsedPoint"),
                Map.entry("checkin", "userCurrentCheckinStreak"), Map.entry("online", "onlineMinute"), Map.entry("invite", "c"),
                Map.entry("perfect", "count"), Map.entry("donate", "total"), Map.entry("adr", "point"), Map.entry("mofish", "point"),
                Map.entry("smallmofish", "point"), Map.entry("emoji", "point"));
        for (final var pair : fields.entrySet()) {
            final JSONObject source = new JSONObject().put(pair.getValue(), 12345678901L).put("userPassword", "SECRET-raw").put("passTime", "SECRET-time");
            final JSONObject dto = ApiV3RankingDto.entry(pair.getKey(), "", 29, source, profile());
            Assert.assertEquals(dto.getInt("rank"), 29);
            Assert.assertEquals(dto.getLong("score"), 12345678901L);
            Assert.assertEquals(dto.keySet(), Set.of("rank", "score", "metrics", "user"));
            Assert.assertEquals(dto.getJSONObject("user").keySet(), Set.of("id", "userName", "userNickname", "userAvatarURL"));
            Assert.assertFalse(dto.toString().contains("SECRET-"));
        }
    }

    @Test
    public void gameBoardsOnlyExposePublicRankingMetrics() {
        final JSONObject top = new JSONObject().put("achievement", 11).put("know", 22).put("reset", 33).put("days", 44).put("key", "SECRET-top");
        final JSONObject save = new JSONObject().put("top", top).put("stats", new JSONObject().put("know", 55).put("days", 66).put("token", "SECRET-stats"))
                .put("times", "123").put("ACHV", "SECRET-achievements").put("password", "SECRET-save");
        final JSONObject row = new JSONObject().put("data", save).put("achievement", 7).put("userId", "SECRET-owner");
        for (final String sub : List.of("achievement", "know", "reset", "days")) {
            final JSONObject dto = ApiV3RankingDto.entry("evolve", sub, 1, row, profile());
            Assert.assertEquals(dto.getLong("score"), top.getLong(sub));
            Assert.assertEquals(dto.getJSONObject("metrics").keySet(), Set.of("achievement", "know", "reset", "days", "currentKnow", "currentDays"));
            Assert.assertEquals(dto.getJSONObject("metrics").getLong("currentKnow"), 55);
            Assert.assertFalse(dto.toString().contains("SECRET-"));
        }
        final JSONObject life = ApiV3RankingDto.entry("lifeRestart", "", 2, row, profile());
        Assert.assertEquals(life.getLong("score"), 123);
        Assert.assertEquals(life.getJSONObject("metrics").getInt("achievement"), 7);
        Assert.assertFalse(life.toString().contains("SECRET-"));
        for (final var pair : Map.of("0", "exp", "1", "dieTimes", "2", "allExTimes", "3", "allBsTimes").entrySet()) {
            final JSONObject dto = ApiV3RankingDto.entry("xiaoice", pair.getKey(), 1, new JSONObject().put(pair.getValue(), 900)
                    .put("family", "SECRET-family").put("ancestry", "黄阶低级").put("gongfa", "黄阶中级"), profile());
            Assert.assertEquals(dto.getLong("score"), 900);
            Assert.assertFalse(dto.toString().contains("SECRET-"));
            Assert.assertEquals(dto.getJSONObject("metrics").length(), "0".equals(pair.getKey()) ? 3 : 1);
        }
    }

    @Test
    public void linkAndSummaryDoNotExposeInternalFieldsOrUnsafeUrls() {
        final JSONObject row = new JSONObject().put(Keys.OBJECT_ID, "3").put(Link.LINK_ADDR, "javascript:alert(1)")
                .put(Link.LINK_TITLE, "Public").put(Link.LINK_CLICK_CNT, 9).put(Link.LINK_CARD_HTML, "SECRET-html");
        final JSONObject dto = ApiV3RankingDto.entry("links", "", 1, row, profile());
        Assert.assertEquals(dto.getLong("score"), 9);
        Assert.assertEquals(dto.getJSONObject("link").getString("url"), "");
        Assert.assertFalse(dto.has("user"));
        Assert.assertFalse(dto.toString().contains("SECRET-"));
        final JSONObject summary = ApiV3RankingDto.summary(new JSONObject().put("totalAmount", 12.5).put("donateMakeDays", 3).put("userId", "SECRET-id"));
        Assert.assertEquals(summary.keySet(), Set.of("amount", "supportedDays"));
        Assert.assertEquals(summary.getDouble("amount"), 12.5);
        Assert.assertEquals(ApiV3RankingDto.summary(new JSONObject()).getDouble("amount"), 0.0);
    }

    @Test
    public void collectionConversionHandlesBothRealJsonPutOverloads() {
        final JSONObject row = new JSONObject().put("count", 12);
        final List<JSONObject> list = List.of(row);
        final JSONObject collectionOverload = new JSONObject().put("data", list);
        final JSONObject objectOverload = new JSONObject().put("data", (Object) list);
        Assert.assertTrue(collectionOverload.opt("data") instanceof JSONArray);
        Assert.assertEquals(ApiV3RankingDto.rows(collectionOverload.opt("data")).getFirst().getInt("count"), 12);
        Assert.assertEquals(ApiV3RankingDto.rows(objectOverload.opt("data")).size(), 1);
        Assert.assertEquals(ApiV3RankingDto.rows(new JSONArray().put(row).put("invalid")).size(), 1);
        Assert.assertTrue(ApiV3RankingDto.rows(null).isEmpty());
    }

    public static void main(final String[] args) throws Exception {
        int count = 0;
        final var instance = new ApiV3RankingDtoTest();
        for (final var method : ApiV3RankingDtoTest.class.getDeclaredMethods())
            if (method.isAnnotationPresent(Test.class)) {
                try {
                    method.invoke(instance);
                    System.out.println("PASS " + method.getName());
                    count++;
                } catch (final java.lang.reflect.InvocationTargetException e) {
                    throw new AssertionError(method.getName(), e.getCause());
                }
            }
        System.out.println("Ranking protocol tests passed: " + count);
    }
}
