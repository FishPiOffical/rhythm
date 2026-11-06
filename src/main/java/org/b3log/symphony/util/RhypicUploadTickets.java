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
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/** Shared RhyPic wire protocol. No request identity, persistence or external calls. */
public final class RhypicUploadTickets {
    public static final long TTL_SECONDS = 180;
    private RhypicUploadTickets() { }

    public static String sign(final String privateKey, final String actor, final String name,
                              final String nickname, final long nowSeconds) throws Exception {
        final var key = KeyFactory.getInstance("Ed25519").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
        final var payload = new JSONObject().put("iss", "rhythm").put("aud", "rhypic")
                .put("sub", actor).put("scope", "file:upload").put("name", name).put("nickname", nickname)
                .put("iat", nowSeconds).put("exp", Math.addExact(nowSeconds, TTL_SECONDS))
                .put("jti", UUID.randomUUID().toString().replace("-", ""));
        final String input = encode("{\"alg\":\"EdDSA\",\"typ\":\"JWT\"}") + "." + encode(payload.toString());
        final var signature = Signature.getInstance("Ed25519");
        signature.initSign(key);signature.update(input.getBytes(StandardCharsets.UTF_8));
        return input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String encode(final String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
