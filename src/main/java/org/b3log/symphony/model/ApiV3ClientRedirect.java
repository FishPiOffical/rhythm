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

/** 注册回调地址按完整值精确匹配，以服务端保存的记录为准。 */
public final class ApiV3ClientRedirect {
    private ApiV3ClientRedirect(){ }
    public static final String TABLE="api_v3_client_redirect";
    public static final String CLIENT_ID="clientId",REDIRECT_URI="redirectUri",STATUS="status";
    public static final int ACTIVE=0;
}
