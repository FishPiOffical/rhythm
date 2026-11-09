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

import org.testng.Assert;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 使用 RFC 7636 附录 B 的公开向量验证 PKCE 格式、规范编码及算法降级边界。
 */
public class ApiV3PkceTest {
    public static void main(String[] args) throws Exception {
        final String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        Assert.assertEquals(ApiV3Pkce.challenge(verifier), expected);
        Assert.assertTrue(ApiV3Pkce.matches(expected, verifier));
        Assert.assertFalse(ApiV3Pkce.matches(expected, "x".repeat(43)));
        Assert.assertNotEquals(ApiV3Pkce.challenge(verifier), ApiV3TokenUtil.hash(verifier));
        for (String value : List.of("A".repeat(43), "~._-".repeat(32))) {
            Assert.assertTrue(ApiV3Pkce.validVerifier(value));
            Assert.assertTrue(ApiV3Pkce.matches(ApiV3Pkce.challenge(value), value));
        }
        for (String value : Arrays.asList(null, "", "x".repeat(42), "x".repeat(129), "x".repeat(42) + "=",
                "x".repeat(42) + "+", "x".repeat(42) + "/", "x".repeat(42) + "é", "x".repeat(42) + "\n")) {
            Assert.assertFalse(ApiV3Pkce.validVerifier(value));
            Assert.assertFalse(ApiV3Pkce.matches(expected, value));
            try {
                ApiV3Pkce.challenge(value);
                Assert.fail();
            } catch (IllegalArgumentException e) {
            }
        }
        System.out.println("PASS S256 matches the official RFC vector without the credential-hash prefix; verifier grammar and 43/128 bounds reject Unicode, whitespace, padding and short/long values");
        ApiV3Pkce.requireS256(expected, "S256");
        for (String method : Arrays.asList(null, "", "plain", "s256", "S512"))
            try {
                ApiV3Pkce.requireS256(expected, method);
                Assert.fail();
            } catch (IllegalArgumentException e) {
            }

        // 最后一位改为 B 后仍能解码为同一组字节，但未使用位非零，属于非规范编码。
        final var bytes = new byte[32];
        final String zero = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Assert.assertTrue(ApiV3Pkce.validChallenge(zero));
        Assert.assertFalse(ApiV3Pkce.validChallenge(zero.substring(0, 42) + "B"));
        for (String bad : Arrays.asList(null, "", expected + "=", expected.substring(1), expected + "A",
                "+" + expected.substring(1), "/" + expected.substring(1), expected.substring(0, 42) + "\n")) {
            Assert.assertFalse(ApiV3Pkce.validChallenge(bad));
            Assert.assertFalse(ApiV3Pkce.matches(bad, verifier));
            try {
                ApiV3Pkce.requireS256(bad, "S256");
                Assert.fail();
            } catch (IllegalArgumentException e) {
            }
        }
        System.out.println("PASS challenges require canonical unpadded 32-byte base64url and exact S256, rejecting nonzero padding bits and plain/implicit algorithm downgrades");
    }
}
