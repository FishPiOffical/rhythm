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
 * This class defines emoji group item model relevant keys.
 *
 * @author <a href="https://github.com/yourname">Your Name</a>
 * @version 1.0.0.0, Jan 26, 2026
 * @since 3.7.0
 */
public final class EmojiGroupItem {

    /**
     * EmojiGroupItem.
     */
    public static final String EMOJI_GROUP_ITEM = "emoji_group_item";

    /**
     * EmojiGroupItems.
     */
    public static final String EMOJI_GROUP_ITEMS = "emojiGroupItems";


    /**
     * Key of group id.
     */
    public static final String EMOJI_GROUP_ITEM_GROUP_ID = "emojiGroupItemGroupId";

    /**
     * Key of emoji id.
     */
    public static final String EMOJI_GROUP_ITEM_EMOJI_ID = "emojiGroupItemEmojiId";

    /**
     * Key of sort order within group.
     */
    public static final String EMOJI_GROUP_ITEM_SORT = "emojiGroupItemSort";


    /**
     * Key of emoji group item name
     */
    public static final String EMOJI_GROUP_ITEM_NAME = "emojiGroupItemName";

    /**
     * Key of create time.
     */
    public static final String EMOJI_GROUP_ITEM_CREATE_TIME = "emojiGroupItemCreateTime";

    private EmojiGroupItem() {
    }
}
