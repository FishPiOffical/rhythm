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

import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Set;

/**
 * 管理员读取已保存的注册记录时，也只返回受约束的公开字段。
 */
public final class ApiV3ClientManagementDto {
    private ApiV3ClientManagementDto() {
    }

    public static JSONObject client(final JSONObject row) {
        final String id = string(row, "clientId", 64), name = string(row, "name", 128), scope = string(row, "scope", 255);
        if (!id.matches("[A-Za-z0-9_-]{1,64}") || name.isBlank()) throw unavailable();
        final Set<String> available = Set.copyOf(Arrays.asList(ApiV3TokenMgmtService.DEFAULT_SCOPES.split(" ")));
        for (final String item : scope.split(" ", -1)) if (!available.contains(item)) throw unavailable();
        return new JSONObject().put("clientId", id).put("name", name).put("scope", scope).put("status", status(row));
    }

    public static JSONObject redirect(final JSONObject row) {
        final String uri = string(row, "redirectUri", 2048);
        // 读取已登记的开发环境回环地址，不代表生产环境允许使用该地址。
        if (!ApiV3OAuthRedirects.valid(uri, true)) throw unavailable();
        return new JSONObject().put("uri", uri).put("status", status(row));
    }

    private static String string(final JSONObject row, final String key, final int max) {
        if (!(row.opt(key) instanceof String value) || value.isEmpty() || value.length() > max
                || value.codePoints().anyMatch(Character::isISOControl)) throw unavailable();
        try {
            ApiV3Json.canonical(value);
        } catch (IllegalArgumentException e) {
            throw unavailable();
        }
        return value;
    }

    private static int status(final JSONObject row) {
        if (!(row.opt("status") instanceof Number value) || !("0".equals(value.toString()) || "1".equals(value.toString())))
            throw unavailable();
        return value.intValue();
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "客户端管理暂不可用");
    }
}
