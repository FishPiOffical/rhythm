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

/** 已认证的购买者只能选择已登记的方案，以及可选的精确优惠券值。 */
public final class ApiV3MembershipOpeningInputs {
    private ApiV3MembershipOpeningInputs() { }
    public record Opening(String levelId, String couponCode) {
        public JSONObject normalized() {
            return new JSONObject().put("levelId", levelId).put("couponCode", couponCode);
        }
    }
    public static Opening parse(final JSONObject body) {
        if (body == null || !Set.of("levelId", "couponCode").containsAll(body.keySet())
                || !(body.opt("levelId") instanceof String id) || !id.matches("[1-9][0-9]{0,18}"))
            throw ApiV3WriteInputs.invalid();
        String code = "";
        if (body.has("couponCode")) {
            if (!(body.opt("couponCode") instanceof String value) || value.length() > 128)
                throw ApiV3WriteInputs.invalid();
            if (value.codePoints().anyMatch(Character::isISOControl))
                throw ApiV3WriteInputs.invalid();
            code = value.trim();
            if (!code.isEmpty() && (code.length() < 8 || code.length() > 32))
                throw ApiV3WriteInputs.invalid();
            if (code.codePoints().anyMatch(Character::isISOControl))
                throw ApiV3WriteInputs.invalid();
            try {
                ApiV3Json.canonical(code);
            } catch (IllegalArgumentException e) {
                throw ApiV3WriteInputs.invalid();
            }
        }
        return new Opening(id, code);
    }
}
