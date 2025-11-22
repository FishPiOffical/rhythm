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

public final class YuhuVote {
    public static final String YUHU_VOTE = "yuhu_vote";
    public static final String YUHU_VOTES = "yuhu_votes";
    public static final String YUHU_VOTE_PROFILE_ID = "yuhuVoteProfileId";
    public static final String YUHU_VOTE_TARGET_TYPE = "yuhuVoteTargetType";
    public static final String YUHU_VOTE_TARGET_ID = "yuhuVoteTargetId";
    public static final String YUHU_VOTE_TYPE = "yuhuVoteType";
    public static final String YUHU_VOTE_VALUE = "yuhuVoteValue";
    public static final String YUHU_VOTE_POINTS_COST = "yuhuVotePointsCost";
    public static final String YUHU_VOTE_CREATED = "yuhuVoteCreated";
    public static final int TARGET_TYPE_C_BOOK = 0;
    public static final int TARGET_TYPE_C_CHAPTER = 1;
    public static final int TYPE_C_MONTHLY = 0;
    public static final int TYPE_C_RECOMMEND = 1;
    public static final int TYPE_C_TIP = 2;
    public static final int TYPE_C_THUMB_UP = 3;
    public static final int TYPE_C_THUMB_DOWN = 4;
    public static final int TYPE_C_RATING = 5;
    private YuhuVote() {}
}

