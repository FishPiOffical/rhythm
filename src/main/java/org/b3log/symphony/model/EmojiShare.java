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
 * Emoji share snapshot model constants.
 */
public final class EmojiShare {

    private EmojiShare() {
    }

    public static final String EMOJI_SHARE = "emoji_share";

    public static final String SHARE_CODE = "emojiShareCode";
    public static final String OWNER_USER_ID = "emojiShareOwnerUserId";
    public static final String GROUP_ID = "emojiShareGroupId";
    public static final String GROUP_NAME = "emojiShareGroupName";
    public static final String SNAPSHOT = "emojiShareSnapshot";
    public static final String EMOJI_COUNT = "emojiShareEmojiCount";
    public static final String IMPORTED_COUNT = "emojiShareImportedCount";
    public static final String CREATED_TIME = "emojiShareCreatedTime";
}
