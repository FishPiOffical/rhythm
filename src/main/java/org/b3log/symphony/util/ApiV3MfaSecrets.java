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

import org.apache.commons.codec.binary.Base32;
import java.security.SecureRandom;
import java.util.Arrays;

/** 独立生成 MFA 密钥，不依赖旧版带种子的生成器或外部二维码服务。 */
public final class ApiV3MfaSecrets {
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiV3MfaSecrets() { }

    public static String generate() {
        final byte[] raw = new byte[20];
        RANDOM.nextBytes(raw);
        try {
            return new Base32().encodeAsString(raw);
        } finally {
            // 编码完成后清零临时的随机字节缓冲区。
            Arrays.fill(raw, (byte) 0);
        }
    }

    public static boolean check(String secret, String code, long time) {
        if (secret == null || !secret.matches("[A-Z2-7]{32}") || code == null || !code.matches("[0-9]{6}"))
            return false;
        return MultiFactorAuthenticator.checkCode(secret, Long.parseLong(code), time);
    }
    public static String uri(String actor, String secret) {
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}") || secret == null || !secret.matches("[A-Z2-7]{32}"))
            throw ApiV3WriteInputs.invalid();
        return "otpauth://totp/FishPi%3A" + actor + "?secret=" + secret + "&issuer=FishPi&algorithm=SHA1&digits=6&period=30";
    }
}
