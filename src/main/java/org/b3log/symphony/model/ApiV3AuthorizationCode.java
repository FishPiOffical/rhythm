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

/** Hash-only, one-use OAuth authorization code and the grant it approved. */
public final class ApiV3AuthorizationCode {
    private ApiV3AuthorizationCode(){ }
    public static final String TABLE="api_v3_authorization_code";
    public static final String CLIENT_ID="clientId",USER_ID="userId",REDIRECT_URI="redirectUri",SCOPE="scope",
            CHALLENGE="codeChallenge",CREATED_AT="createdAt",EXPIRES_AT="expiresAt",CONSUMED_AT="consumedAt",FAMILY_ID="familyId";
}
