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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reaction model constants.
 */
public final class Reaction {

    public static final String REACTION = "reaction";

    public static final String USER_ID = "reactionUserId";
    public static final String TARGET_TYPE = "reactionTargetType";
    public static final String TARGET_ID = "reactionTargetId";
    public static final String GROUP = "reactionGroup";
    public static final String VALUE = "reactionValue";
    public static final String CREATED_TIME = "reactionCreatedTime";
    public static final String UPDATED_TIME = "reactionUpdatedTime";

    public static final String TARGET_TYPE_COMMENT = "comment";
    public static final String TARGET_TYPE_CHAT = "chat";
    public static final String TARGET_TYPE_ARTICLE = "article";

    public static final String GROUP_EMOJI = "emoji";

    public static final String VALUE_THUMBS_UP = "thumbsup";
    public static final String VALUE_PLUS = "plus";
    public static final String VALUE_THUMBS_DOWN = "thumbsdown";
    public static final String VALUE_CHECK = "check";
    public static final String VALUE_CROSS = "cross";
    public static final String VALUE_STAR = "star";
    public static final String VALUE_HEART = "heart";
    public static final String VALUE_FIRE = "fire";
    public static final String VALUE_PARTY = "party";
    public static final String VALUE_LAUGH = "laugh";
    public static final String VALUE_WOW = "wow";
    public static final String VALUE_CLAP = "clap";
    public static final String VALUE_EYES = "eyes";
    public static final String VALUE_THINKING = "thinking";
    public static final String VALUE_CRY = "cry";
    public static final String VALUE_ANGRY = "angry";
    public static final String VALUE_BROKEN_HEART = "brokenheart";
    public static final String VALUE_HEART_ON_FIRE = "heartonfire";
    public static final String VALUE_HUNDRED = "hundred";
    public static final String VALUE_ROCKET = "rocket";
    public static final String VALUE_SALUTE = "salute";
    public static final String VALUE_HANDSHAKE = "handshake";
    public static final String VALUE_RAISED_HANDS = "raisedhands";
    public static final String VALUE_MIND_BLOWN = "mindblown";
    public static final String VALUE_PRAY = "pray";
    public static final String VALUE_SKULL = "skull";
    public static final String VALUE_CLOWN = "clown";
    public static final String VALUE_POOP = "poop";

    public static final String FIELD_SUMMARY = "reactionSummary";
    public static final String FIELD_CURRENT_USER_REACTION = "currentUserReaction";

    public static final String RESPONSE_TARGET_ID = "targetId";
    public static final String RESPONSE_TARGET_TYPE = "targetType";
    public static final String RESPONSE_GROUP_TYPE = "groupType";
    public static final String RESPONSE_SUMMARY = "summary";
    public static final String RESPONSE_ACTOR_USER_ID = "actorUserId";
    public static final String RESPONSE_ACTOR_REACTION = "actorReaction";

    private static final Map<String, String> EMOJI_VALUES;

    static {
        final Map<String, String> values = new LinkedHashMap<>();
        values.put(VALUE_THUMBS_UP, "👍");
        values.put(VALUE_PLUS, "➕1️⃣");
        values.put(VALUE_THUMBS_DOWN, "👎");
        values.put(VALUE_CHECK, "✅");
        values.put(VALUE_CROSS, "❌");
        values.put(VALUE_STAR, "⭐");
        values.put(VALUE_HEART, "❤️");
        values.put(VALUE_FIRE, "🔥");
        values.put(VALUE_PARTY, "🎉");
        values.put(VALUE_LAUGH, "😂");
        values.put(VALUE_WOW, "😮");
        values.put(VALUE_CLAP, "👏");
        values.put(VALUE_EYES, "👀");
        values.put(VALUE_THINKING, "🤔");
        values.put(VALUE_CRY, "😢");
        values.put(VALUE_ANGRY, "😡");
        values.put(VALUE_BROKEN_HEART, "💔");
        values.put(VALUE_HEART_ON_FIRE, "❤️‍🔥");
        values.put(VALUE_HUNDRED, "💯");
        values.put(VALUE_ROCKET, "🚀");
        values.put(VALUE_SALUTE, "🖖");
        values.put(VALUE_HANDSHAKE, "🤝");
        values.put(VALUE_RAISED_HANDS, "🙌");
        values.put(VALUE_MIND_BLOWN, "🤯");
        values.put(VALUE_PRAY, "🙏");
        values.put(VALUE_SKULL, "💀");
        values.put(VALUE_CLOWN, "🤡");
        values.put(VALUE_POOP, "💩");
        EMOJI_VALUES = Collections.unmodifiableMap(values);
    }

    private Reaction() {
    }

    public static boolean isValidTargetType(final String targetType) {
        return TARGET_TYPE_COMMENT.equals(targetType)
                || TARGET_TYPE_CHAT.equals(targetType)
                || TARGET_TYPE_ARTICLE.equals(targetType);
    }

    public static boolean isValidGroup(final String groupType) {
        return GROUP_EMOJI.equals(groupType);
    }

    public static boolean isValidEmojiValue(final String value) {
        return EMOJI_VALUES.containsKey(value);
    }

    public static String emojiOf(final String value) {
        return EMOJI_VALUES.get(value);
    }

    public static Set<String> emojiValues() {
        return EMOJI_VALUES.keySet();
    }
}
