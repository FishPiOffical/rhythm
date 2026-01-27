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
 * This class defines emoji model relevant keys.
 *
 * @author <a href="https://github.com/yourname">Your Name</a>
 * @version 1.0.0.0, Jan 26, 2026
 * @since 3.7.0
 */
public final class Emoji {

    /**
     * Emoji.
     */
    public static final String EMOJI = "emoji";

    /**
     * Emojis.
     */
    public static final String EMOJIS = "emojis";

    /**
     * Key of emoji URL.
     */
    public static final String EMOJI_URL = "emojiUrl";

    /**
     * Key of create time.
     */
    public static final String EMOJI_CREATE_TIME = "emojiCreateTime";

    /**
     * Key of uploader id.
     */
    public static final String EMOJI_UPLOADER_ID = "emojiUploaderId";

    /**
     * Key of emoji status (0: normal, 1: deleted).
     */
    public static final String EMOJI_STATUS = "emojiStatus";

    private Emoji() {
    }
}
