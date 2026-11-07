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

import org.b3log.symphony.model.Notification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One category map for listing, unread counts and bulk state mutations. */
public final class ApiV3NotificationTypes {
    private ApiV3NotificationTypes() { }
    public static final Map<String, List<Integer>> CATEGORIES;
    public static final List<Integer> KNOWN_TYPES;
    public static final Set<String> FILTERS;
    static {
        final Map<String, List<Integer>> categories = new LinkedHashMap<>();
        categories.put("commented", List.of(Notification.DATA_TYPE_C_COMMENTED, Notification.DATA_TYPE_C_COMMENT));
        categories.put("reply", List.of(Notification.DATA_TYPE_C_REPLY));
        categories.put("at", List.of(Notification.DATA_TYPE_C_AT, Notification.DATA_TYPE_C_NEW_FOLLOWER,
                Notification.DATA_TYPE_C_CHAT_ROOM_AT, Notification.DATA_TYPE_C_RED_PACKET,
                Notification.DATA_TYPE_C_ARTICLE_NEW_FOLLOWER, Notification.DATA_TYPE_C_ARTICLE_NEW_WATCHER,
                Notification.DATA_TYPE_C_COMMENT_VOTE_UP, Notification.DATA_TYPE_C_COMMENT_VOTE_DOWN,
                Notification.DATA_TYPE_C_ARTICLE_VOTE_UP, Notification.DATA_TYPE_C_ARTICLE_VOTE_DOWN));
        categories.put("following", List.of(Notification.DATA_TYPE_C_FOLLOWING_USER,
                Notification.DATA_TYPE_C_FOLLOWING_ARTICLE_UPDATE, Notification.DATA_TYPE_C_FOLLOWING_ARTICLE_COMMENT));
        categories.put("point", List.of(Notification.DATA_TYPE_C_POINT_CHARGE, Notification.DATA_TYPE_C_POINT_TRANSFER,
                Notification.DATA_TYPE_C_POINT_ARTICLE_REWARD, Notification.DATA_TYPE_C_POINT_COMMENT_THANK,
                Notification.DATA_TYPE_C_POINT_EXCHANGE, Notification.DATA_TYPE_C_ABUSE_POINT_DEDUCT,
                Notification.DATA_TYPE_C_POINT_ARTICLE_THANK, Notification.DATA_TYPE_C_INVITECODE_USED,
                Notification.DATA_TYPE_C_INVITATION_LINK_USED, Notification.DATA_TYPE_C_POINT_PERFECT_ARTICLE,
                Notification.DATA_TYPE_C_POINT_COMMENT_ACCEPT, Notification.DATA_TYPE_C_POINT_REPORT_HANDLED,
                Notification.DATA_TYPE_C_RED_PACKET_FROM_SKY, Notification.DATA_TYPE_C_APP_POINT_ADJUST));
        categories.put("broadcast", List.of(Notification.DATA_TYPE_C_BROADCAST, Notification.DATA_TYPE_C_ARTICLE));
        categories.put("sys-announce", List.of(Notification.DATA_TYPE_C_SYS_ANNOUNCE_ARTICLE,
                Notification.DATA_TYPE_C_SYS_ANNOUNCE_NEW_USER, Notification.DATA_TYPE_C_SYS_ANNOUNCE_ROLE_CHANGED,
                Notification.DATA_TYPE_C_CUSTOM_SYS));
        CATEGORIES = java.util.Collections.unmodifiableMap(categories);
        KNOWN_TYPES = categories.values().stream().flatMap(List::stream).distinct().sorted().toList();
        final Set<String> filters = new java.util.HashSet<>(categories.keySet());
        filters.add("all"); filters.add("other");
        FILTERS = Set.copyOf(filters);
    }

    public static String category(final int type) {
        for (final var entry : CATEGORIES.entrySet()) if (entry.getValue().contains(type)) return entry.getKey();
        return "other";
    }

    public static String label(final int type) {
        return switch (type) {
            case Notification.DATA_TYPE_C_ARTICLE, Notification.DATA_TYPE_C_BROADCAST -> "文章广播";
            case Notification.DATA_TYPE_C_COMMENT, Notification.DATA_TYPE_C_COMMENTED -> "收到评论";
            case Notification.DATA_TYPE_C_REPLY -> "收到回复";
            case Notification.DATA_TYPE_C_AT -> "有人提到了你";
            case Notification.DATA_TYPE_C_FOLLOWING_USER -> "关注用户发布了文章";
            case Notification.DATA_TYPE_C_FOLLOWING_ARTICLE_UPDATE -> "关注的文章已更新";
            case Notification.DATA_TYPE_C_FOLLOWING_ARTICLE_COMMENT -> "关注的文章收到评论";
            case Notification.DATA_TYPE_C_NEW_FOLLOWER -> "新增关注者";
            case Notification.DATA_TYPE_C_ARTICLE_NEW_FOLLOWER -> "文章被收藏";
            case Notification.DATA_TYPE_C_ARTICLE_NEW_WATCHER -> "文章被关注";
            case Notification.DATA_TYPE_C_COMMENT_VOTE_UP -> "评论收到赞同";
            case Notification.DATA_TYPE_C_COMMENT_VOTE_DOWN -> "评论收到反对";
            case Notification.DATA_TYPE_C_ARTICLE_VOTE_UP -> "文章收到赞同";
            case Notification.DATA_TYPE_C_ARTICLE_VOTE_DOWN -> "文章收到反对";
            case Notification.DATA_TYPE_C_CHAT_ROOM_AT -> "聊天室有人提到了你";
            case Notification.DATA_TYPE_C_RED_PACKET -> "聊天室红包通知";
            default -> switch (category(type)) {
                case "point" -> "积分通知";
                case "sys-announce" -> "系统通知";
                default -> "通知";
            };
        };
    }
}
