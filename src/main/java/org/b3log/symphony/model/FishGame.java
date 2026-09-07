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

/** 鱼游投稿模型。 */
public final class FishGame {
    private FishGame() {
    }

    public static final String FISH_GAME = "fish_game";
    public static final String NAME = "fishGameName";
    public static final String DESCRIPTION = "fishGameDescription";
    public static final String URL = "fishGameUrl";
    public static final String ICON_URL = "fishGameIconUrl";
    public static final String AUTHOR_ID = "fishGameAuthorId";
    public static final String STATUS = "fishGameStatus";
    public static final String EDIT_PENDING = "fishGameEditPending";
    public static final String PENDING_NAME = "fishGamePendingName";
    public static final String PENDING_DESCRIPTION = "fishGamePendingDescription";
    public static final String PENDING_URL = "fishGamePendingUrl";
    public static final String PENDING_ICON_URL = "fishGamePendingIconUrl";
    public static final String LIKE_COUNT = "fishGameLikeCount";
    public static final String DISLIKE_COUNT = "fishGameDislikeCount";
    public static final String CREATED_TIME = "fishGameCreatedTime";
    public static final String UPDATED_TIME = "fishGameUpdatedTime";

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_DISABLED = 3;
}
