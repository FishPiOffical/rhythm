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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** RFC 7636 S256 摘要，直接使用 verifier 的 ASCII 字节，不添加应用前缀或盐。 */
public final class ApiV3Pkce {
    private ApiV3Pkce() { }

    public static boolean validVerifier(final String value) {
        return value != null && value.matches("[A-Za-z0-9._~-]{43,128}");
    }

    public static boolean validChallenge(final String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}"))
            return false;
        try {
            final byte[] bytes = Base64.getUrlDecoder().decode(value);
            // 重新编码可排除解码后等价、但文本不符合规范的 challenge。
            return bytes.length == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static void requireS256(final String challenge, final String method) {
        if (!"S256".equals(method) || !validChallenge(challenge))
            throw new IllegalArgumentException("S256 PKCE required");
    }

    public static String challenge(final String verifier) {
        if (!validVerifier(verifier))
            throw new IllegalArgumentException("invalid PKCE verifier");
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean matches(final String challenge, final String verifier) {
        return validChallenge(challenge) && validVerifier(verifier)
                && MessageDigest.isEqual(challenge.getBytes(StandardCharsets.US_ASCII),
                challenge(verifier).getBytes(StandardCharsets.US_ASCII));
    }
}
