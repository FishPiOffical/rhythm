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

import org.b3log.latke.util.Strings;
import org.json.JSONObject;
import org.jsoup.parser.Parser;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.TimeZone;

/**
 * API-to-storage whitelist. New subresources leave the existing aggregate read contract unchanged.
 */
public final class ApiV3Settings {
    private ApiV3Settings() {
    }

    public static final Map<String, String> PERSONALITY = Map.of("mbti", "mbti");
    public static final Map<String, String> AVATAR = Map.of("avatarURL", "userAvatarURL");
    public static final Map<String, String> PROFILE = Map.of("userNickname", "userNickname", "userIntro", "userIntro", "userURL", "userURL");
    public static final Map<String, String> PRIVACY = Map.ofEntries(
            Map.entry("articles", "userArticleStatus"), Map.entry("comments", "userCommentStatus"), Map.entry("breezemoons", "userBreezemoonStatus"),
            Map.entry("points", "userPointStatus"), Map.entry("followers", "userFollowerStatus"), Map.entry("followingUsers", "userFollowingUserStatus"),
            Map.entry("followingTags", "userFollowingTagStatus"), Map.entry("followingArticles", "userFollowingArticleStatus"), Map.entry("watchingArticles", "userWatchingArticleStatus"),
            Map.entry("online", "userOnlineStatus"), Map.entry("userAgent", "userUAStatus"), Map.entry("joinPointRank", "userJoinPointRank"), Map.entry("joinUsedPointRank", "userJoinUsedPointRank"));
    public static final Map<String, String> DISPLAY = Map.of("commentViewMode", "userCommentViewMode", "avatarViewMode", "userAvatarViewMode", "skin", "userSkin", "mobileSkin", "userMobileSkin");
    public static final Map<String, String> FUNCTIONS = Map.ofEntries(
            Map.entry("listPageSize", "userListPageSize"), Map.entry("listViewMode", "userListViewMode"),
            Map.entry("notifications", "userNotifyStatus"), Map.entry("subscriptionEmail", "userSubMailStatus"),
            Map.entry("keyboardShortcuts", "userKeyboardShortcutsStatus"), Map.entry("autoSubscribeOnReply", "userReplyWatchArticleStatus"),
            Map.entry("externalLinkConfirmation", "userForwardPageStatus"), Map.entry("chatImageBlur", "chatRoomPictureStatus"),
            Map.entry("indexRedirectURL", "userIndexRedirectURL"));
    public static final Set<String> FUNCTION_SWITCHES = Set.of("notifications", "subscriptionEmail", "keyboardShortcuts", "autoSubscribeOnReply", "externalLinkConfirmation", "chatImageBlur");
    public static final Map<String, String> LOCATION = Map.of("publicLocation", "userGeoStatus");
    public static final Map<String, String> LOCALE = Map.of("language", "userLanguage", "timezone", "userTimezone");

    public static Map<String, String> fields(final String group) {
        return switch (group) {
            case "personality" -> PERSONALITY;
            case "avatar" -> AVATAR;
            case "profile" -> PROFILE;
            case "privacy" -> PRIVACY;
            case "display" -> DISPLAY;
            case "functions" -> FUNCTIONS;
            case "location" -> LOCATION;
            case "locale" -> LOCALE;
            default -> throw new IllegalArgumentException("unknown settings group");
        };
    }

    public static JSONObject normalize(final String group, final JSONObject body) {
        return normalize(group, body, "");
    }

    public static JSONObject normalize(final String group, final JSONObject body, final String site) {
        final Map<String, String> fields = fields(group);
        if (body.isEmpty() || !fields.keySet().containsAll(body.keySet())) throw ApiV3WriteInputs.invalid();
        final JSONObject changes = new JSONObject();
        for (final String key : body.keySet()) {
            if ("privacy".equals(group) || "location".equals(group) || FUNCTION_SWITCHES.contains(key)) {
                if (!(body.opt(key) instanceof Boolean enabled)) throw ApiV3WriteInputs.invalid();
                changes.put(fields.get(key), enabled ? 0 : 1);
            } else if (Set.of("commentViewMode", "avatarViewMode", "listViewMode").contains(key)) {
                changes.put(fields.get(key), ApiV3WriteInputs.integer(body, key, 0, 0, 1));
            } else if ("listPageSize".equals(key)) {
                changes.put(fields.get(key), ApiV3WriteInputs.integer(body, key, 20, 10, 96));
            } else {
                final int max = switch (key) {
                    case "mbti" -> 6;
                    case "userNickname" -> 20;
                    case "userIntro", "avatarURL" -> 255;
                    case "userURL" -> 100;
                    case "indexRedirectURL" -> 255;
                    case "language", "timezone" -> 64;
                    default -> 32;
                };
                String value = ApiV3WriteInputs.string(body, key, max, true);
                if (value.codePoints().anyMatch(cp -> Character.isISOControl(cp) && !("userIntro".equals(key) && (cp == '\n' || cp == '\r' || cp == '\t'))))
                    throw ApiV3WriteInputs.invalid();
                if (Set.of("skin", "mobileSkin").contains(key) && (!value.matches("[A-Za-z0-9_/-]{1,32}") || value.contains("..") || value.startsWith("/")))
                    throw ApiV3WriteInputs.invalid();
                if ("mbti".equals(key)) value = ApiV3Personality.normalize(value);
                if ("avatarURL".equals(key)) value = avatarURL(value);
                if ("userURL".equals(key) && !value.isEmpty()) {
                    try {
                        final URI uri = new URI(value);
                        if (!Strings.isURL(value) || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || null == uri.getHost() || null != uri.getUserInfo())
                            throw ApiV3WriteInputs.invalid();
                    } catch (final java.net.URISyntaxException e) {
                        throw ApiV3WriteInputs.invalid();
                    }
                }
                if ("indexRedirectURL".equals(key)) {
                    value = SettingsRedirects.normalize(value, site);
                    if (value.length() > 255)
                        throw ApiV3WriteInputs.invalid();
                }
                if ("language".equals(key) && !Languages.getAvailableLanguages().contains(value))
                    throw ApiV3WriteInputs.invalid();
                if ("timezone".equals(key) && !Arrays.asList(TimeZone.getAvailableIDs()).contains(value))
                    throw ApiV3WriteInputs.invalid();
                changes.put(fields.get(key), value);
            }
        }
        return changes;
    }

    /**
     * 保存调用方提供的 HTTPS 地址，保留签名查询参数且不访问该地址。
     */
    public static String avatarURL(final String value) {
        if (null == value || value.isBlank() || value.length() > 255 || value.indexOf('\\') >= 0
                || value.codePoints().anyMatch(cp -> Character.isISOControl(cp) || Character.isWhitespace(cp)))
            throw ApiV3WriteInputs.invalid();
        try {
            final URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || null == uri.getHost() || uri.getHost().isBlank()
                    || null != uri.getRawUserInfo() || uri.getPort() > 65535 || uri.getRawAuthority().endsWith(":"))
                throw ApiV3WriteInputs.invalid();
            return value;
        } catch (final java.net.URISyntaxException e) {
            throw ApiV3WriteInputs.invalid();
        }
    }

    public static JSONObject dto(final String group, final JSONObject row) {
        if ("personality".equals(group)) return ApiV3Personality.dto(row);
        final JSONObject dto = new JSONObject();
        for (final var field : fields(group).entrySet()) {
            if ("privacy".equals(group) || "location".equals(group) || FUNCTION_SWITCHES.contains(field.getKey()))
                dto.put(field.getKey(), row.optInt(field.getValue()) == 0);
            else if (Set.of("commentViewMode", "avatarViewMode", "listViewMode", "listPageSize").contains(field.getKey()))
                dto.put(field.getKey(), row.optInt(field.getValue()));
            else {
                String value = row.optString(field.getValue());
                if ("userURL".equals(field.getKey()) && !value.isEmpty()) {
                    try {
                        final URI uri = new URI(value);
                        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || null == uri.getHost() || null != uri.getUserInfo())
                            value = "";
                    } catch (final java.net.URISyntaxException e) {
                        value = "";
                    }
                }
                dto.put(field.getKey(), "profile".equals(group) && !"userURL".equals(field.getKey()) ? Parser.unescapeEntities(value, false) : value);
            }
        }
        return dto;
    }
}
