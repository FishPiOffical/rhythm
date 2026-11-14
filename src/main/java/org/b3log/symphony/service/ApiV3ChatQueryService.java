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
package org.b3log.symphony.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.repository.ReactionRepository;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.util.ApiV3ChatMessageDto;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * 通过 Bearer 身份查询聊天室历史，使用受限的 ID 游标分页。
 */
@Service
public class ApiV3ChatQueryService {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3ChatQueryService.class);
    @Inject
    private ChatRoomRepository messages;
    @Inject
    private ChatTopicMgmtService topics;
    @Inject
    private ReactionRepository reactions;

    public JSONObject topic() throws RepositoryException {
        final var current = topics.readCommitted();
        return new JSONObject().put("text", current == null ? ChatroomChannel.discussing : current.text());
    }

    public JSONObject barragerPrice() {
        final var price = ChatroomProcessor.getBarragerPrice();
        return new JSONObject().put("amount", price.amount()).put("unit", price.unit());
    }

    protected String render(final String content) {
        return Markdowns.cleanChat(Markdowns.toHTML(Emotions.convert(content)), "");
    }

    private JSONObject dto(final JSONObject row) {
        return ApiV3ChatMessageDto.from(row, this::render);
    }

    private static void id(final String value) {
        if (value == null || !value.matches("[0-9]{1,32}")) throw invalid();
    }

    private static ApiV3Exception invalid() {
        return new ApiV3Exception(422, "invalid_parameter", "请求参数不合法");
    }

    private List<JSONObject> side(final String anchor, final boolean after, final int limit) throws RepositoryException {
        return messages.select("SELECT oId,content FROM `" + messages.getName() + "` WHERE oId"
                + (after ? ">" : "<") + "? ORDER BY oId " + (after ? "ASC" : "DESC") + " LIMIT ?", anchor, limit);
    }

    public JSONObject list(final String before, final String after, final int size) throws RepositoryException {
        return list(before, after, size, null);
    }

    public JSONObject list(final String before, final String after, final int size, final String viewerId) throws RepositoryException {
        if (size < 1 || size > 50 || before != null && after != null) throw invalid();
        if (before != null) id(before);
        if (after != null) id(after);
        final List<JSONObject> rows = before != null ? side(before, false, size + 1) : after != null ? side(after, true, size + 1)
                : messages.select("SELECT oId,content FROM `" + messages.getName() + "` ORDER BY oId DESC LIMIT ?", size + 1);
        final boolean more = rows.size() > size;
        final var page = new ArrayList<>(rows.subList(0, Math.min(size, rows.size())));
        // 倒序查询先取离游标最近的消息，返回时统一为正序。
        if (after == null) Collections.reverse(page);
        final var list = new JSONArray();
        for (final var row : page) list.put(dto(row));
        if (viewerId != null) fillReactions(list, viewerId);
        return new JSONObject().put("list", list).put("hasMore", more)
                .put("nextBefore", page.isEmpty() ? "" : page.getFirst().getString("oId"))
                .put("nextAfter", page.isEmpty() ? "" : page.getLast().getString("oId"));
    }

    private JSONObject row(final String messageId) throws RepositoryException {
        id(messageId);
        final var rows = messages.select("SELECT oId,content FROM `" + messages.getName() + "` WHERE oId=? LIMIT 1", messageId);
        if (rows.isEmpty()) throw new ApiV3Exception(404, "not_found", "消息不存在或已撤回");
        return rows.getFirst();
    }

    public JSONObject detail(final String messageId) throws RepositoryException {
        return dto(row(messageId));
    }

    public JSONObject detail(final String messageId, final String viewerId) throws RepositoryException {
        final var message = detail(messageId);
        fillReactions(new JSONArray().put(message), viewerId);
        return message;
    }

    private static boolean hasVisibleContent(final JSONObject message) {
        return !"unavailable".equals(message.getString("type"));
    }

    private static JSONObject reactionSummary(final String id, final JSONArray summary, final String selected) {
        return new JSONObject().put("targetId", id).put("targetType", Reaction.TARGET_TYPE_CHAT)
                .put("groupType", Reaction.GROUP_EMOJI).put("currentUserReaction", selected).put("summary", summary);
    }

    private static JSONObject reactionCount(final String value, final long count, final boolean selected) {
        return new JSONObject().put("value", value).put("emoji", Reaction.emojiOf(value)).put("count", count)
                .put("selected", selected).put("users", new JSONArray());
    }

    private void fillReactions(final JSONArray list, final String viewerId) {
        try {
            readReactions(list, viewerId);
        } catch (final RepositoryException | RuntimeException failure) {
            LOGGER.warn("聊天室表态查询失败", failure);
            for (int i = 0; i < list.length(); i++) {
                final var message = list.getJSONObject(i);
                if (hasVisibleContent(message)) markReactionsUnavailable(message);
            }
        }
    }

    private static void markReactionsUnavailable(final JSONObject message) {
        // 保留消息正文，用独立标记区分查询失败与空表态。
        message.remove("reactions");
        message.put("reactionsUnavailable", true);
    }

    private void readReactions(final JSONArray list, final String viewerId) throws RepositoryException {
        final Map<String, JSONObject> visible = new LinkedHashMap<>();
        for (int i = 0; i < list.length(); i++) {
            final var message = list.getJSONObject(i);
            if (hasVisibleContent(message)) visible.put(message.getString("id"), message);
        }
        if (visible.isEmpty()) return;
        // 对本页可读消息执行一次聚合查询，只返回计数和当前读者的选择。
        final var args = new ArrayList<Object>(List.of(viewerId, Reaction.TARGET_TYPE_CHAT, Reaction.GROUP_EMOJI));
        args.addAll(visible.keySet());
        final String placeholders = String.join(",", Collections.nCopies(visible.size(), "?"));
        final var rows = reactions.select("SELECT reactionTargetId,reactionValue,COUNT(*) AS total,"
                + "MAX(CASE WHEN reactionUserId=? THEN 1 ELSE 0 END) AS ownSelection FROM `" + reactions.getName()
                + "` WHERE reactionTargetType=? AND reactionGroup=? AND reactionTargetId IN (" + placeholders
                + ") GROUP BY reactionTargetId,reactionValue ORDER BY reactionTargetId,MIN(oId)", args.toArray());
        final Map<String, JSONArray> summaries = new HashMap<>();
        final Map<String, String> selections = new HashMap<>();
        for (final var row : rows) {
            final String id = row.getString("reactionTargetId"), value = row.getString("reactionValue");
            if (!Reaction.isValidEmojiValue(value)) continue;
            final boolean selected = row.getInt("ownSelection") != 0;
            if (selected) selections.put(id, value);
            summaries.computeIfAbsent(id, ignored -> new JSONArray()).put(reactionCount(value, row.getLong("total"), selected));
        }
        visible.forEach((id, message) -> message.put("reactions", reactionSummary(id,
                summaries.getOrDefault(id, new JSONArray()), selections.getOrDefault(id, ""))));
    }

    public record RealtimeSnapshot(JSONObject message, Map<String, String> selections) {}

    public RealtimeSnapshot realtimeSnapshot(final String id) throws RepositoryException {
        final var message = detail(id);
        if (!hasVisibleContent(message)) return new RealtimeSnapshot(message, Map.of());
        try {
            return readRealtimeReactions(id, message);
        } catch (final RepositoryException | RuntimeException failure) {
            LOGGER.warn("聊天室实时表态查询失败", failure);
            markReactionsUnavailable(message);
            return new RealtimeSnapshot(message, Map.of());
        }
    }

    private RealtimeSnapshot readRealtimeReactions(final String id, final JSONObject message) throws RepositoryException {
        // 读取一次供本节点广播使用。大规模表态改由客户端按需读取单条详情。
        final var rows = reactions.select("SELECT reactionUserId,reactionValue FROM `" + reactions.getName()
                + "` WHERE reactionTargetType=? AND reactionGroup=? AND reactionTargetId=? ORDER BY oId LIMIT 2001",
                Reaction.TARGET_TYPE_CHAT, Reaction.GROUP_EMOJI, id);
        if (rows.size() > 2000) return new RealtimeSnapshot(message, Map.of());
        final Map<String, Long> counts = new LinkedHashMap<>();
        final Map<String, String> selections = new HashMap<>();
        for (final var row : rows) {
            final String value = row.getString("reactionValue");
            if (!Reaction.isValidEmojiValue(value)) continue;
            counts.merge(value, 1L, Long::sum);
            selections.put(row.getString("reactionUserId"), value);
        }
        final var summary = new JSONArray();
        counts.forEach((value, count) -> summary.put(reactionCount(value, count, false)));
        message.put("reactions", reactionSummary(id, summary, ""));
        return new RealtimeSnapshot(message, selections);
    }

    public JSONObject context(final String messageId, final int radius) throws RepositoryException {
        if (radius < 1 || radius > 25) throw invalid();
        final JSONObject anchor = row(messageId);
        final var before = side(messageId, false, radius + 1);
        final var after = side(messageId, true, radius + 1);
        final var left = new ArrayList<>(before.subList(0, Math.min(radius, before.size())));
        Collections.reverse(left);
        final var right = after.subList(0, Math.min(radius, after.size()));
        final var list = new JSONArray();
        for (final var item : left) list.put(dto(item));
        list.put(dto(anchor));
        for (final var item : right) list.put(dto(item));
        return new JSONObject().put("anchorId", messageId).put("list", list)
                .put("hasMoreBefore", before.size() > radius).put("hasMoreAfter", after.size() > radius)
                .put("nextBefore", left.isEmpty() ? messageId : left.getFirst().getString("oId"))
                .put("nextAfter", right.isEmpty() ? messageId : right.getLast().getString("oId"));
    }
}
