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
