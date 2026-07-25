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
 * External point adjustment request model keys.
 */
public final class ExternalPointRequest {

    public static final String EXTERNAL_POINT_REQUEST = "external_point_request";
    public static final String SOURCE_APP_ID = "sourceAppId";
    public static final String REQUEST_ID = "requestId";
    public static final String REQUEST_HASH = "requestHash";
    public static final String TRANSFER_ID = "transferId";
    public static final String CREATE_TIME = "createTime";

    private ExternalPointRequest() {
    }
}
