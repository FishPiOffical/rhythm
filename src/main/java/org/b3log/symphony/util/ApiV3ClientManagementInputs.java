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
import java.util.ArrayList;
import java.util.List;

/** 只校验传输结构，权限范围、名称和回调策略由客户端注册逻辑校验。 */
public final class ApiV3ClientManagementInputs {
    private ApiV3ClientManagementInputs() { }
    // 这里只限制单个客户端的回调数量，URI 策略由注册逻辑处理。
    public static final int MAX_REDIRECTS = 20;

    public record Registration(String name, String scope, List<String> redirectUris) {
        public Registration { redirectUris = List.copyOf(redirectUris); }
        public JSONObject normalized() {
            return new JSONObject().put("name", name).put("scope", scope).put("redirectUris", new JSONArray(redirectUris));
        }
    }

    public static Registration registration(final JSONObject body) {
        final String name = ApiV3WriteInputs.string(body, "name", 128, false);
        final String scope = ApiV3WriteInputs.string(body, "scope", 255, false);
        if (!(body.opt("redirectUris") instanceof JSONArray uris) || uris.isEmpty()
                || uris.length() > MAX_REDIRECTS) throw ApiV3WriteInputs.invalid();
        final List<String> redirects = new ArrayList<>();
        for (final Object value : uris) {
            if (!(value instanceof String uri) || uri.length() > 2048 || uri.isEmpty()) throw ApiV3WriteInputs.invalid();
            redirects.add(uri);
        }
        return new Registration(name, scope, redirects);
    }

    public static String clientId(final String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) throw ApiV3WriteInputs.invalid();
        return value;
    }
}
