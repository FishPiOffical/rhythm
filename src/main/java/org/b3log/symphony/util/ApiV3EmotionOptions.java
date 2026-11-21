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
package org.b3log.symphony.util;

import org.b3log.symphony.model.Emotion;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 返回有序的表情选项分组，顺序与 PC 和移动端设置页一致。
 */
public final class ApiV3EmotionOptions {
    private ApiV3EmotionOptions() {
    }

    public static final String DEFAULTS = "smile,flushed,joy,sob,yum,trollface,tada,heart,+1,ok_hand,pray";

    public static String[][] legacyGroups() {
        final String[][] copy = new String[GROUPS.length][];
        for (int i = 0; i < GROUPS.length; i++) copy[i] = GROUPS[i].clone();
        return copy;
    }

    public static JSONObject options() {
        final JSONArray groups = new JSONArray();
        for (final String[] group : GROUPS) {
            final JSONArray aliases = new JSONArray();
            for (final String alias : group) if (!Emotion.EOF_EMOJI.equals(alias)) aliases.put(alias);
            if (!aliases.isEmpty()) groups.put(aliases);
        }
        return new JSONObject().put("groups", groups).put("defaults", new JSONArray(DEFAULTS.split(",")));
    }

    private static final String[][] GROUPS = {{
            "smile",
            "laughing",
            "smirk",
            "heart_eyes",
            "kissing_heart",
            "flushed",
            "grin",
            "stuck_out_tongue_closed_eyes",
            "kissing",
            "sleeping",
            "anguished",
            "open_mouth",
            "expressionless",
            "unamused",
            "sweat_smile",
            "weary",
            "sob",
            "joy",
            "astonished",
            "scream"
    }, {
            "tired_face",
            "rage",
            "triumph",
            "yum",
            "mask",
            "sunglasses",
            "dizzy_face",
            "imp",
            "smiling_imp",
            "innocent",
            "alien",
            "yellow_heart",
            "blue_heart",
            "purple_heart",
            "heart",
            "green_heart",
            "broken_heart",
            "dizzy",
            "anger",
            "exclamation"
    }, {
            "question",
            "zzz",
            "notes",
            "poop",
            "+1",
            "-1",
            "ok_hand",
            "punch",
            "v",
            "hand",
            "point_up",
            "point_down",
            "pray",
            "clap",
            "muscle",
            "ok_woman",
            "no_good",
            "raising_hand",
            "massage",
            "haircut"
    }, {
            "nail_care",
            "see_no_evil",
            "feet",
            "kiss",
            "eyes",
            "trollface",
            "snowman",
            "zap",
            "cat",
            "dog",
            "mouse",
            "hamster",
            "rabbit",
            "frog",
            "koala",
            "pig",
            "monkey",
            "racehorse",
            "camel",
            "sheep"
    }, {
            "elephant",
            "panda_face",
            "snake",
            "hatched_chick",
            "hatching_chick",
            "turtle",
            "bug",
            "honeybee",
            "beetle",
            "snail",
            "octopus",
            "whale",
            "dolphin",
            "dragon",
            "goat",
            "paw_prints",
            "tulip",
            "four_leaf_clover",
            "rose",
            "mushroom"
    }, {
            "seedling",
            "shell",
            "crescent_moon",
            "partly_sunny",
            "octocat",
            "jack_o_lantern",
            "ghost",
            "santa",
            "tada",
            "camera",
            "loudspeaker",
            "hourglass",
            "lock",
            "key",
            "bulb",
            "hammer",
            "moneybag",
            "smoking",
            "bomb",
            "gun"
    }, {
            "hocho",
            "pill",
            "syringe",
            "scissors",
            "swimmer",
            "black_joker",
            "coffee",
            "tea",
            "sake",
            "beer",
            "wine_glass",
            "pizza",
            "hamburger",
            "poultry_leg",
            "meat_on_bone",
            "dango",
            "doughnut",
            "icecream",
            "shaved_ice",
            "cake"
    }, {
            "cookie",
            "lollipop",
            "apple",
            "green_apple",
            "tangerine",
            "lemon",
            "cherries",
            "grapes",
            "watermelon",
            "strawberry",
            "peach",
            "melon",
            "banana",
            "pear",
            "pineapple",
            "sweet_potato",
            "eggplant",
            "tomato",
            Emotion.EOF_EMOJI // 标记结束以便在function.ftl中处理
    }};
}
