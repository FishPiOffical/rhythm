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
package org.b3log.symphony.model;

/**
 * API v3 访问令牌和刷新令牌的存储字段，令牌明文不入库。
 */
public final class ApiV3Token {
    private ApiV3Token() {
    }

    public static final String API_V3_TOKEN = "api_v3_token";
    public static final String TOKEN_HASH = "tokenHash";
    public static final String TOKEN_TYPE = "tokenType";
    public static final String USER_ID = "userId";
    public static final String CLIENT_ID = "clientId";
    public static final String SCOPE = "scope";
    public static final String FAMILY_ID = "familyId";
    public static final String PARENT_ID = "parentId";
    public static final String STATE = "state";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String LAST_USED_AT = "lastUsedAt";
    public static final String EXPIRES_AT = "expiresAt";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final int STATE_ACTIVE = 0;
    public static final int STATE_ROTATED = 1;
    public static final int STATE_REVOKED = 2;
    public static final int STATE_EXPIRED = 3;
}
