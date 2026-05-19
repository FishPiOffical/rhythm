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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 复读机转录站内容模型。
 */
public final class RepeaterContent {

    private RepeaterContent() {
    }

    public static final String REPEATER_CONTENT = "repeater_content";

    public static final String TYPE = "repeaterContentType";
    public static final String CONTENT = "repeaterContent";
    public static final String AUTHOR_ID = "repeaterContentAuthorId";
    public static final String SOURCE = "repeaterContentSource";
    public static final String STATUS = "repeaterContentStatus";
    public static final String LIKE_COUNT = "repeaterContentLikeCount";
    public static final String CREATED_TIME = "repeaterContentCreatedTime";
    public static final String UPDATED_TIME = "repeaterContentUpdatedTime";

    public static final String TYPE_JOKE = "joke";
    public static final String TYPE_KFC = "kfc";
    public static final String TYPE_FISH = "fish";

    public static final String SOURCE_USER = "user";
    public static final String SOURCE_SEED = "seed";

    public static final int STATUS_C_VALID = 0;
    public static final int STATUS_C_REMOVED = 1;
    public static final int MAX_CONTENT_LENGTH = 500;
    public static final int MIN_CONTENT_LENGTH = 2;

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(TYPE_JOKE, TYPE_KFC, TYPE_FISH));

    public static boolean isSupportedType(final String type) {
        return TYPES.contains(type);
    }
}
