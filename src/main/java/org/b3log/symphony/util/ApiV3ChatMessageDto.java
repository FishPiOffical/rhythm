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

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Function;

/**
 * 将持久化聊天消息和支持的卡片类型按白名单投影为公开字段。
 */
public final class ApiV3ChatMessageDto {
    private static final int MAX_STORED_LENGTH = 102400;

    private ApiV3ChatMessageDto() {
    }

    public static JSONObject from(final JSONObject row, final Function<String, String> render) {
        final String id = row.optString("oId");
        final JSONObject dto = new JSONObject().put("id", id).put("createdAt", number(id, 0))
                .put("type", "unavailable").put("content", "").put("contentHtml", "").put("card", new JSONObject())
                .put("author", new JSONObject().put("id", "").put("userName", "").put("userNickname", "").put("userAvatarURL", ""))
                .put("client", "").put("appearance", ApiV3ChatAppearance.defaults());
        final JSONObject source;
        try {
            final String stored = row.optString("content");
            if (stored.isEmpty() || stored.length() > MAX_STORED_LENGTH) return dto;
            source = ApiV3Json.parse(stored.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return dto;
        }
        final Object body = source.opt("content");
        if (!(body instanceof String) && !(body instanceof JSONObject)) return dto;
        final String content = body.toString();
        if (content.length() > MAX_STORED_LENGTH) return dto;
        final String authorId = source.optString("userOId");
        dto.put("createdAt", number(source.opt("time"), number(id, 0)))
                .put("author", new JSONObject().put("id", authorId.matches("[1-9][0-9]{0,31}") ? authorId : "")
                        .put("userName", plain(source.opt("userName"), 64)).put("userNickname", plain(source.opt("userNickname"), 64))
                        .put("userAvatarURL", ApiV3CommunityDto.safeUrl(source.optString("userAvatarURL"))))
                .put("client", plain(source.opt("client"), 64))
                .put("appearance", ApiV3ChatAppearance.publicSnapshot(source.opt("appearance")));
        final JSONObject card;
        try {
            card = content.stripLeading().startsWith("{") ? ApiV3Json.parse(content.getBytes(StandardCharsets.UTF_8)) : null;
        } catch (IllegalArgumentException e) {
            return text(dto, source, content, render);
        }
        final String kind = card == null ? "" : card.optString("msgType");
        // 新卡片精确保留已校验的纯文本和签名 URL，历史消息沿用原投影。
        if (card != null && card.optInt("cardVersion") == 3 && Set.of("music", "weather").contains(kind)) {
            try {
                final JSONObject payload = card.getJSONObject("payload");
                final JSONObject normalized = "music".equals(kind) ? ApiV3ChatCardInputs.music(payload) : ApiV3ChatCardInputs.weather(payload);
                final JSONObject projected = new JSONObject(ApiV3ChatCardInputs.content(kind, normalized));
                projected.remove("msgType");
                projected.remove("cardVersion");
                projected.remove("payload");
                return dto.put("type", kind).put("card", projected);
            } catch (RuntimeException invalidCard) {
                return dto;
            }
        }
        switch (kind) {
            case "redPacket" -> {
                final String type = card.optString("type");
                dto.put("type", "redPacket").put("card", new JSONObject()
                        .put("type", Set.of("random", "average", "specify", "heartbeat", "rockPaperScissors", "dice").contains(type) ? type : "unknown")
                        .put("message", plain(card.opt("msg"), 4096)).put("money", number(card.opt("money"), 0))
                        .put("count", number(card.opt("count"), 0)).put("got", number(card.opt("got"), 0)));
            }
            case "weather" -> {
                final var weather = new JSONObject();
                for (final String field : new String[]{"date", "weatherCode", "max", "min", "t", "st"})
                    weather.put(field, plain(card.opt(field), 4096));
                dto.put("type", "weather").put("card", weather);
            }
            case "music" -> {
                final String musicSource = plain(card.opt("source"), 2048);
                dto.put("type", "music").put("card", new JSONObject().put("title", plain(card.opt("title"), 4096))
                        .put("source", musicSource.matches("[0-9]{1,32}") ? musicSource : ApiV3CommunityDto.safeUrl(musicSource))
                        .put("coverURL", ApiV3CommunityDto.safeUrl(card.optString("coverURL"))).put("from", plain(card.opt("from"), 64)));
            }
            default -> text(dto, source, content, render);
        }
        return dto;
    }

    private static JSONObject text(final JSONObject dto, final JSONObject source, final String content,
                                   final Function<String, String> render) {
        final String html = render.apply(content);
        dto.put("type", "text").put("content", content).put("contentHtml", html);
        // 只投影可见普通正文的合法快照；缺失与坏快照不影响原消息读取。
        final JSONObject tail = ApiV3ChatTail.snapshot(source.opt("tail"));
        if (tail != null && ApiV3ChatTail.eligibleHtml(html)) dto.put("tail", tail);
        final JSONObject pet = petStatus(source.opt("petStatus"));
        if (pet != null && !dto.getJSONObject("author").optString("id").isEmpty()) dto.put("petStatus", pet);
        return dto;
    }

    public static JSONObject petStatus(final Object value) {
        if (!(value instanceof JSONObject pet) || !(pet.opt("name") instanceof String name)
                || !(pet.opt("state") instanceof String state) || name.isBlank() || name.length() > 256
                || name.codePoints().anyMatch(cp -> Character.isISOControl(cp) || (Character.getType(cp) == Character.FORMAT && cp != 0x200D))
                || !Set.of("idle", "rest", "outing", "course", "work", "claimable").contains(state)) return null;
        final JSONObject snapshot = new JSONObject().put("name", name).put("state", state);
        if (pet.opt("level") instanceof Number level && level.doubleValue() == level.intValue()
                && level.intValue() >= 1 && level.intValue() <= ApiV3PetRules.MAX_LEVEL) {
            snapshot.put("level", level.intValue());
        }
        return snapshot;
    }

    private static long number(final Object value, final long fallback) {
        if (value == null || !value.toString().matches("[0-9]{1,19}")) return fallback;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String plain(final Object value, final int limit) {
        if (!(value instanceof String) && !(value instanceof Number)) return "";
        final String text = Jsoup.clean(value.toString(), "", Safelist.none()).strip();
        final StringBuilder out = new StringBuilder();
        text.codePoints().filter(c -> !Character.isISOControl(c)).limit(limit).forEach(out::appendCodePoint);
        return out.toString();
    }
}
