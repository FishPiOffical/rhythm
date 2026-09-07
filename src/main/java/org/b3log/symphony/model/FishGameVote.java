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

/** 鱼游投票模型。 */
public final class FishGameVote {
    private FishGameVote() {
    }

    public static final String FISH_GAME_VOTE = "fish_game_vote";
    public static final String GAME_ID = "fishGameVoteGameId";
    public static final String USER_ID = "fishGameVoteUserId";
    public static final String VALUE = "fishGameVoteValue";
    public static final String CREATED_TIME = "fishGameVoteCreatedTime";
    public static final String VALUE_LIKE = "like";
    public static final String VALUE_DISLIKE = "dislike";
}
