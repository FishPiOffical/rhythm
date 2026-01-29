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
 * This class defines emoji group model relevant keys.
 *
 * @author <a href="https://github.com/yourname">Your Name</a>
 * @version 1.0.0.0, Jan 26, 2026
 * @since 3.7.0
 */
public final class EmojiGroup {

    /**
     * EmojiGroup.
     */
    public static final String EMOJI_GROUP = "emoji_group";

    /**
     * EmojiGroups.
     */
    public static final String EMOJI_GROUPS = "emojiGroups";

    /**
     * Key of group name.
     */
    public static final String EMOJI_GROUP_ID = "emojiGroupId";

    /**
     * Key of user id.
     */
    public static final String EMOJI_GROUP_USER_ID = "emojiGroupUserId";

    /**
     * Key of group name.
     */
    public static final String EMOJI_GROUP_NAME = "emojiGroupName";

    /**
     * Key of group sort order.
     */
    public static final String EMOJI_GROUP_SORT = "emojiGroupSort";

    /**
     * Key of create time.
     */
    public static final String EMOJI_GROUP_CREATE_TIME = "emojiGroupCreateTime";

    /**
     * Key of update time.
     */
    public static final String EMOJI_GROUP_UPDATE_TIME = "emojiGroupUpdateTime";

    /**
     * Key of group type (0: custom group, 1: default "all" group).
     */
    public static final String EMOJI_GROUP_TYPE = "emojiGroupType";

    /**
     * Group type - Default "all" group.
     */
    public static final int EMOJI_GROUP_TYPE_ALL = 1;

    /**
     * Group type - Custom group.
     */
    public static final int EMOJI_GROUP_TYPE_CUSTOM = 0;

    private EmojiGroup() {
    }
}
