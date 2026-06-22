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
 * OpenID refresh token model constants.
 */
public final class OpenIdRefreshToken {

    private OpenIdRefreshToken() {
    }

    public static final String OPENID_REFRESH_TOKEN = "openid_refresh_token";

    public static final String TOKEN_HASH = "tokenHash";
    public static final String USER_ID = "userId";
    public static final String REALM = "realm";
    public static final String SCOPE = "scope";
    public static final String FAMILY_ID = "familyId";
    public static final String PARENT_ID = "parentId";
    public static final String STATE = "state";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String LAST_USED_AT = "lastUsedAt";
    public static final String IDLE_EXPIRES_AT = "idleExpiresAt";
    public static final String MAX_EXPIRES_AT = "maxExpiresAt";

    public static final int STATE_ACTIVE = 0;
    public static final int STATE_ROTATED = 1;
    public static final int STATE_REVOKED = 2;
    public static final int STATE_EXPIRED = 3;
}
