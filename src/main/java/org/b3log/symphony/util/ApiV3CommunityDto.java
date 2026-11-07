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

import org.b3log.latke.Keys;
import org.b3log.symphony.model.FishGame;
import org.b3log.symphony.model.FishGameVote;
import org.b3log.symphony.model.Milestone;
import org.json.JSONObject;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Public community metadata, with no review queue or user contact fields. */
public final class ApiV3CommunityDto {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault());
    private ApiV3CommunityDto() { }
    public static JSONObject game(final JSONObject row, final JSONObject author, final String userVote) {
        final JSONObject publicAuthor = new JSONObject();
        for (final String key : new String[]{"id", "userName", "userNickname", "userAvatarURL"}) publicAuthor.put(key, author.optString(key));
        final String vote = FishGameVote.VALUE_LIKE.equals(userVote) || FishGameVote.VALUE_DISLIKE.equals(userVote) ? userVote : "";
        return new JSONObject().put("id", row.optString(Keys.OBJECT_ID)).put("name", row.optString(FishGame.NAME))
                .put("description", row.optString(FishGame.DESCRIPTION)).put("url", safeUrl(row.optString(FishGame.URL)))
                .put("iconURL", safeUrl(row.optString(FishGame.ICON_URL))).put("author", publicAuthor)
                .put("upCount", row.optLong(FishGame.LIKE_COUNT)).put("downCount", row.optLong(FishGame.DISLIKE_COUNT))
                .put("createdAt", row.optLong(FishGame.CREATED_TIME)).put("updatedAt", row.optLong(FishGame.UPDATED_TIME))
                .put("userVote", vote);
    }
    public static JSONObject milestone(final JSONObject row, final String html, final String plain) {
        final long end = row.optLong(Milestone.MILESTONE_END_DATE);
        final String mediaType = row.optString(Milestone.MILESTONE_MEDIA_TYPE);
        return new JSONObject().put("id", row.optString(Keys.OBJECT_ID)).put("title", row.optString(Milestone.MILESTONE_TITLE))
                .put("date", DATE.format(Instant.ofEpochMilli(row.optLong(Milestone.MILESTONE_DATE))))
                .put("endDate", end > 0 ? DATE.format(Instant.ofEpochMilli(end)) : "")
                .put("content", plain).put("contentHtml", html).put("link", safeUrl(row.optString(Milestone.MILESTONE_LINK)))
                .put("media", new JSONObject().put("url", safeUrl(row.optString(Milestone.MILESTONE_MEDIA_URL)))
                        .put("type", "image".equals(mediaType) || "video".equals(mediaType) ? mediaType : "")
                        .put("caption", row.optString(Milestone.MILESTONE_MEDIA_CAPTION)));
    }
    public static String safeUrl(final String raw) {
        final String url = null == raw ? "" : raw.trim();
        if (url.length() > 2048 || url.indexOf('\\') >= 0 || url.codePoints().anyMatch(Character::isISOControl)) return "";
        try {
            final URI uri = new URI(url);
            if (url.startsWith("/") && !url.startsWith("//") && null == uri.getRawAuthority()) return url;
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) return "";
            return null != uri.getRawAuthority() && !uri.getRawAuthority().isBlank() && null == uri.getUserInfo() ? url : "";
        } catch (final Exception e) { return ""; }
    }
}
