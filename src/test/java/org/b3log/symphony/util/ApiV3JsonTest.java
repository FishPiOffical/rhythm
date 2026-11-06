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
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

public class ApiV3JsonTest {
    @Test
    public void strictJsonRejectsJavaScriptExtensionsAndDuplicateKeys() {
        for (final String input : new String[]{"{'a':1}", "{a:1}", "{\"a\":1,}", "{\"a\":01}", "{\"a\":+1}",
                "{\"a\":NaN}", "{\"a\":undefined}", "{\"a\":1e}", "{\"a\":1.}", "{\"a\":1e99999}",
                "{\"a\":1} trailing", "[]", "null", "{\"a\":1,\"a\":2}", "{\"x\":1,\"\\u0078\":2}",
                "{\"a\":/*comment*/1}", "{\"a\":[1,,2]}", "{\"a\":\"line\nbreak\"}", "{\"a\":\"\\uＦＦＦＦ\"}"}) {
            reject(input.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void boundedDepthSizeAndUtf8() {
        reject(new byte[]{(byte) 0xc3, 0x28});
        reject(new byte[ApiV3Json.MAX_BODY_BYTES + 1]);
        reject(("{\"x\":".repeat(40) + "0" + "}".repeat(40)).getBytes(StandardCharsets.UTF_8));
        reject("{\"a\":\"\\uD800\"}".getBytes(StandardCharsets.UTF_8));
        reject("{\"a\":\"\\uDC00\"}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void validEscapesUnicodeAndNumbersSurvive() {
        final String input = "{\"text\":\"中文😀\\n\\t\\r\\b\\f\\\\\\\"\\/\",\"n\":-1.25e+2,\"b\":true,\"empty\":null}";
        final JSONObject body = ApiV3Json.parse(input.getBytes(StandardCharsets.UTF_8));
        Assert.assertTrue(body.getString("text").startsWith("中文😀"));
        Assert.assertEquals(body.getDouble("n"), -125D);
        Assert.assertTrue(body.getBoolean("b"));
    }

    @Test
    public void canonicalObjectOrderDoesNotChangeFingerprintButArrayOrderDoes() {
        final JSONObject first = new JSONObject("{\"b\":[2,1],\"a\":{\"d\":4,\"c\":3}}");
        final JSONObject second = new JSONObject("{\"a\":{\"c\":3,\"d\":4},\"b\":[2,1]}");
        Assert.assertEquals(ApiV3Json.canonical(first), ApiV3Json.canonical(second));
        second.put("b", new JSONArray().put(1).put(2));
        Assert.assertNotEquals(ApiV3Json.canonical(first), ApiV3Json.canonical(second));
    }

    @Test
    public void roundTripGeneratedJson() {
        final Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            final JSONObject original = new JSONObject().put("value", value(random, 0));
            final JSONObject parsed = ApiV3Json.parse(original.toString().getBytes(StandardCharsets.UTF_8));
            Assert.assertEquals(ApiV3Json.canonical(parsed), ApiV3Json.canonical(original));
        }
    }

    private Object value(final Random random, final int depth) {
        final int kind = random.nextInt(depth > 4 ? 4 : 6);
        return switch (kind) {
            case 0 -> "中文😀\"\\\n\t" + random.nextInt(1000);
            case 1 -> random.nextLong();
            case 2 -> random.nextBoolean();
            case 3 -> JSONObject.NULL;
            case 4 -> new JSONArray().put(value(random, depth + 1)).put(value(random, depth + 1));
            default -> new JSONObject().put("b", value(random, depth + 1)).put("a", value(random, depth + 1));
        };
    }

    private void reject(final byte[] bytes) {
        try { ApiV3Json.parse(bytes); Assert.fail("Invalid JSON was accepted"); }
        catch (final IllegalArgumentException expected) { }
    }

    public static void main(final String[] args) {
        final ApiV3JsonTest test = new ApiV3JsonTest();
        test.strictJsonRejectsJavaScriptExtensionsAndDuplicateKeys();
        test.boundedDepthSizeAndUtf8();
        test.validEscapesUnicodeAndNumbersSurvive();
        test.canonicalObjectOrderDoesNotChangeFingerprintButArrayOrderDoes();
        test.roundTripGeneratedJson();
        System.out.println("PASS: 5 ApiV3Json tests (including 1000 generated JSON round trips)");
    }
}
