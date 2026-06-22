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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.*;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


public class OpenIdUtil {

    private static final String SECRET = Symphonys.get("openid.secret");
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern REFRESH_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43,256}");

    public static String generateNonce() {
        // 时间部分
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());

        // 随机部分（你也可以用更短的 UUID 或随机数）
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        return timestamp + random;
    }

    public static String sign(Map<String, String> fields) throws Exception{
        StringBuilder sb = new StringBuilder();
        String[] signedFields = fields.get("openid.signed").split(",");
        for (String field : signedFields) {
            String key = "openid." + field;
            sb.append(key).append(":").append(fields.get(key)).append("\n");
        }

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] hash = sha1.digest((sb.toString() + SECRET).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }


    public static Date extractNonceTimestamp(String nonce) throws ParseException {
        if (nonce.length() < 20) {
            throw new IllegalArgumentException("Invalid nonce format");
        }

        String timestampPart = nonce.substring(0, 20); // "2025-05-14T09:42:18Z"
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.parse(timestampPart);
    }

    public static String generateAccessToken(final String userId, final Collection<String> scopes,
                                             final String realm, final long expiresAt) throws Exception {
        final long now = System.currentTimeMillis();
        final JSONObject payload = new JSONObject()
                .put("userId", userId)
                .put("scope", String.join(" ", scopes))
                .put("realm", realm)
                .put("iat", now)
                .put("exp", expiresAt)
                .put("jti", UUID.randomUUID().toString().replace("-", ""));
        final String encodedPayload = URL_ENCODER.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        final String signature = URL_ENCODER.encodeToString(hmac(encodedPayload));

        return encodedPayload + "." + signature;
    }

    public static JSONObject parseAccessToken(final String token) {
        try {
            final String[] parts = token.split("\\.");
            if (2 != parts.length) {
                return null;
            }

            final byte[] expectedSignature = hmac(parts[0]);
            final byte[] actualSignature = URL_DECODER.decode(parts[1]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                return null;
            }

            final JSONObject payload = new JSONObject(new String(URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8));
            if (payload.optLong("exp") < System.currentTimeMillis()) {
                return null;
            }

            return payload;
        } catch (final Exception e) {
            return null;
        }
    }

    public static String generateRefreshToken() {
        final byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        return URL_ENCODER.encodeToString(random);
    }

    public static boolean isRefreshTokenFormat(final String token) {
        return null != token && REFRESH_TOKEN_PATTERN.matcher(token).matches();
    }

    public static String hashRefreshToken(final String token) throws Exception {
        return URL_ENCODER.encodeToString(hmac("refresh:" + token));
    }

    private static byte[] hmac(final String payload) throws Exception {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }
}
