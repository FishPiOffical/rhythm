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
import java.util.Set;

/** Security proofs are validated but never normalized into ordinary idempotency receipts. */
public final class ApiV3SecurityInputs {
    private ApiV3SecurityInputs() { }

    public record Password(String currentPassword, String newPassword, String currentTotp) { }

    public static Password password(final JSONObject body) {
        if (body == null || !Set.of("currentPassword", "newPassword", "currentTotp").containsAll(body.keySet()))
            throw ApiV3Requests.invalid();
        return new Password(digest(body.opt("currentPassword")), digest(body.opt("newPassword")), totp(body.opt("currentTotp")));
    }

    public static String digest(final Object value) {
        if (!(value instanceof String text) || !text.matches("[a-f0-9]{32}"))
            throw ApiV3Requests.invalid();
        return text;
    }

    public static String totp(final Object value) {
        // 缺省值留给后续的当前账号 MFA 策略判断，提供的值须恰为六位数字。
        if (value == null)
            return "";
        if (!(value instanceof String text) || !text.matches("[0-9]{6}"))
            throw ApiV3Requests.invalid();
        return text;
    }
}
