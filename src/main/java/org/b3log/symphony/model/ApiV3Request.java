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

/** Committed idempotent v3 mutation results. Keys and bearer credentials are not stored. */
public final class ApiV3Request {
    private ApiV3Request() { }
    public static final String API_V3_REQUEST = "api_v3_request";
    public static final String USER_ID = "userId";
    public static final String OPERATION = "operation";
    public static final String REQUEST_HASH = "requestHash";
    public static final String RESPONSE_JSON = "responseJson";
    public static final String CREATED_AT = "createdAt";
}
