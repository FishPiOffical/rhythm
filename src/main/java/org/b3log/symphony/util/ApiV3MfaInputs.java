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

public final class ApiV3MfaInputs {
    private ApiV3MfaInputs() {
    }

    public record Proof(String currentPassword, String currentTotp, String preparationId, String newTotp) {
    }

    public static Proof parse(JSONObject body, boolean enable) {
        final Set<String> allowed = enable ? Set.of("currentPassword", "currentTotp", "preparationId", "newTotp")
                : Set.of("currentPassword", "currentTotp");
        if (body == null || !allowed.containsAll(body.keySet()))
            throw ApiV3WriteInputs.invalid();
        final String password = ApiV3SecurityInputs.digest(body.opt("currentPassword"));
        final String current = ApiV3SecurityInputs.totp(body.opt("currentTotp"));
        if (!enable)
            return new Proof(password, current, "", "");

        // 启用时还需提交准备记录的句柄和新密钥生成的验证码。
        if (!(body.opt("preparationId") instanceof String handle) || !handle.matches("[A-Za-z0-9_-]{43}"))
            throw ApiV3WriteInputs.invalid();
        final String next = ApiV3SecurityInputs.totp(body.opt("newTotp"));
        if (next.isEmpty())
            throw ApiV3WriteInputs.invalid();
        return new Proof(password, current, handle, next);
    }
}
