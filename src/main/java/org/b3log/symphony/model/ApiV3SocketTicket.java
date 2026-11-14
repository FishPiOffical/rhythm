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
 * 一次性 WebSocket 票据，凭据明文不入库。
 */
public final class ApiV3SocketTicket {
    private ApiV3SocketTicket() {
    }

    public static final String REPOSITORY = "api_v3_socket_ticket";
    public static final String USER_ID = "userId";
    public static final String PARENT_TOKEN_ID = "parentTokenId";
    public static final String CHANNEL = "channel";
    public static final String TARGET_ID = "targetId";
    public static final String CHANNEL_NOTIFICATIONS = "notifications";
    public static final String CHANNEL_ARTICLE = "article";
    public static final String CHANNEL_CHAT = "chat";
    public static final String CHANNEL_MESSAGES = "messages";
    public static final String CREATED_AT = "createdAt";
    public static final String EXPIRES_AT = "expiresAt";
    public static final String CONSUMED_AT = "consumedAt";
}
