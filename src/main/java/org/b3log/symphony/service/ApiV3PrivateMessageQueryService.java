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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ChatInfoRepository;
import org.b3log.symphony.repository.ChatUnreadRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.*;

import java.util.*;

/**
 * 仅按实际参与者读取本人私信，读取过程不创建会话或改变已读状态。
 */
@Service
public class ApiV3PrivateMessageQueryService {
    @Inject
    private ChatInfoRepository messages;
    @Inject
    private ChatUnreadRepository unread;
    @Inject
    private UserRepository users;
    @Inject
    private AvatarQueryService avatars;
    private static final String VALID = " AND m.oId REGEXP '^[1-9][0-9]{0,18}$' AND m.fromId REGEXP '^[1-9][0-9]{0,18}$' AND m.toId REGEXP '^[1-9][0-9]{0,18}$' AND m.fromId<>m.toId";
    private static final String COLUMNS = "m.oId,m.fromId,m.toId,m.time,m.content";
    private static final String PAIR = "((m.fromId=? AND m.toId=?) OR (m.fromId=? AND m.toId=?))";
    private static final String OWNER = "(m.fromId=? OR m.toId=?)";

    @FunctionalInterface
    private interface Read {
        JSONObject run() throws Exception;
    }

    private JSONObject safe(Read operation) throws Exception {
        try {
            return operation.run();
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            LogManager.getLogger(getClass()).warn("Private message read unavailable");
            throw new ApiV3Exception(503, "temporarily_unavailable", "私信暂不可用");
        }
    }

    private String actor(JSONObject viewer) throws Exception {
        final String id = viewer == null ? "" : viewer.optString(Keys.OBJECT_ID);
        if (!ApiV3PrivateMessages.validId(id))
            throw ApiV3Requests.notFound();
        final var rows = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=?", id);
        if (rows.size() != 1 || rows.getFirst().optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID)
            throw ApiV3Requests.notFound();
        return id;
    }

    private Map<String, JSONObject> peers(Collection<String> ids) throws Exception {
        final var result = new LinkedHashMap<String, JSONObject>();
        final var stored = new ArrayList<String>();
        for (String id : new LinkedHashSet<>(ids)) {
            final var dto = new JSONObject().put("id", id).put("userName", "").put("userNickname", "").put("userAvatarURL", "").put("available", false).put("builtin", false);
            if (ApiV3PrivateMessages.FILE_TRANSFER_ID.equals(id))
                dto.put("userName", "文件传输助手").put("userAvatarURL", ApiV3PrivateMessages.FILE_TRANSFER_AVATAR).put("available", true).put("builtin", true);
            else
                stored.add(id);
            result.put(id, dto);
        }
        if (!stored.isEmpty())
            for (var row : users.select("SELECT oId,userName,userNickname,userAvatarURL,userAvatarType,userStatus FROM `" + users.getName() + "` WHERE oId IN (" + String.join(",", Collections.nCopies(stored.size(), "?")) + ")", stored.toArray())) {
                final var dto = result.get(row.optString(Keys.OBJECT_ID));
                if (dto == null || row.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID)
                    continue;
                dto.put("userName", row.optString(User.USER_NAME)).put("userNickname", row.optString(UserExt.USER_NICKNAME))
                        .put("userAvatarURL", ApiV3CommunityDto.safeUrl(avatars.getAvatarURLByUser(row, "48"))).put("available", true);
            }
        return result;
    }

    private JSONObject peer(String id) throws Exception {
        return peers(List.of(id)).get(id);
    }

    protected String render(String content) {
        return Markdowns.toPrivateHTML(content);
    }

    private JSONObject message(JSONObject row, String actor) {
        return ApiV3PrivateMessages.message(row, actor, this::render);
    }

    private String table() {
        return "`" + messages.getName() + "` m";
    }

    private Object[] pair(String actor, String peer) {
        return new Object[]{actor, peer, peer, actor};
    }

    private List<JSONObject> latest(String actor, String peer) throws Exception {
        return messages.select("SELECT " + COLUMNS + " FROM " + table() + " WHERE " + PAIR + VALID + " ORDER BY m.oId DESC LIMIT 1", pair(actor, peer));
    }

    private long count(String sql, Object... args) throws Exception {
        final var rows = messages.select(sql, args);
        if (rows.size() != 1)
            throw new IllegalStateException("private count missing");
        final long result = rows.getFirst().getLong("total");
        if (result < 0)
            throw new IllegalStateException("private count invalid");
        return result;
    }

    private long unread(String actor, String peer) throws Exception {
        final var values = new ArrayList<Object>();
        values.add(actor);
        if (peer != null)
            values.add(peer);
        final var rows = unread.select("SELECT COUNT(*) AS total FROM `" + unread.getName() + "` u WHERE u.toId=?" + (peer == null ? "" : " AND u.fromId=?")
                + " AND u.fromId REGEXP '^[1-9][0-9]{0,18}$' AND u.toId REGEXP '^[1-9][0-9]{0,18}$' AND u.fromId<>u.toId", values.toArray());
        if (rows.size() != 1 || rows.getFirst().getLong("total") < 0)
            throw new IllegalStateException("private unread count invalid");
        return rows.getFirst().getLong("total");
    }

    private Map<String, Long> unreadForPeers(String actor, List<String> peers) throws Exception {
        final var result = new HashMap<String, Long>();
        if (peers.isEmpty())
            return result;
        final var args = new ArrayList<Object>();
        args.add(actor);
        args.addAll(peers);
        for (var row : unread.select("SELECT u.fromId,COUNT(*) AS total FROM `" + unread.getName() + "` u WHERE u.toId=? AND u.fromId IN (" + String.join(",", Collections.nCopies(peers.size(), "?")) + ") GROUP BY u.fromId", args.toArray())) {
            final long total = row.getLong("total");
            if (total < 0)
                throw new IllegalStateException("private unread count invalid");
            result.put(row.getString("fromId"), total);
        }
        return result;
    }

    private void target(String actor, String peer) {
        ApiV3PrivateMessages.requireId(peer);
        if (actor.equals(peer))
            throw ApiV3WriteInputs.invalid();
    }

    public JSONObject unread(JSONObject viewer) throws Exception {
        return safe(() -> new JSONObject().put("count", unread(actor(viewer), null)));
    }

    public JSONObject conversations(JSONObject viewer, int page, int size) throws Exception {
        if (page < 1 || page > Integer.MAX_VALUE / 50 || size < 1 || size > 50) throw ApiV3WriteInputs.invalid();
        return safe(() -> {
            final String actor = actor(viewer);
            final String peer = "CASE WHEN m.fromId=? THEN m.toId ELSE m.fromId END";
            final long total = count("SELECT COUNT(DISTINCT " + peer + ") AS total FROM " + table() + " WHERE " + OWNER + VALID, actor, actor, actor);
            final String grouped = "SELECT CASE WHEN c.fromId=? THEN c.toId ELSE c.fromId END AS peerId,MAX(c.oId) AS lastId FROM `" + messages.getName() + "` c WHERE (c.fromId=? OR c.toId=?)" + VALID.replace("m.", "c.")
                    + " GROUP BY peerId ORDER BY lastId DESC,peerId ASC LIMIT ? OFFSET ?";
            final var rows = messages.select("SELECT " + COLUMNS + ",g.peerId FROM " + table() + " JOIN (" + grouped + ") g ON m.oId=g.lastId"
                            + " WHERE ((m.fromId=? AND m.toId=g.peerId) OR (m.fromId=g.peerId AND m.toId=?))" + VALID + " ORDER BY m.oId DESC,g.peerId ASC",
                    actor, actor, actor, size, (page - 1) * size, actor, actor);
            final var ids = rows.stream().map(row -> row.getString("peerId")).toList();
            final var profiles = peers(ids);
            final var counts = unreadForPeers(actor, ids);
            final var list = new JSONArray();
            for (var row : rows) {
                final String other = row.getString("peerId");
                list.put(new JSONObject().put("peerId", other).put("peer", profiles.get(other)).put("lastMessage", message(row, actor)).put("unreadCount", counts.getOrDefault(other, 0L)));
            }
            return ApiV3Requests.pagination(list, page, size, total);
        });
    }

    public JSONObject conversation(JSONObject viewer, String peerId) throws Exception {
        ApiV3PrivateMessages.requireId(peerId);
        return safe(() -> {
            String actor = actor(viewer);
            target(actor, peerId);
            var rows = latest(actor, peerId);
            if (rows.isEmpty())
                throw ApiV3Requests.notFound();
            return new JSONObject().put("peerId", peerId).put("peer", peer(peerId))
                    .put("lastMessage", message(rows.getFirst(), actor)).put("unreadCount", unread(actor, peerId));
        });
    }

    public JSONObject history(JSONObject viewer, String peerId, String before, String after, int size) throws Exception {
        ApiV3PrivateMessages.requireId(peerId);
        if (size < 1 || size > 50 || before != null && after != null)
            throw ApiV3WriteInputs.invalid();
        if (before != null)
            ApiV3PrivateMessages.requireId(before);
        if (after != null)
            ApiV3PrivateMessages.requireId(after);
        return safe(() -> {
            final String actor = actor(viewer);
            target(actor, peerId);
            final var args = new ArrayList<Object>(Arrays.asList(pair(actor, peerId)));
            if (before != null)
                args.add(before);
            if (after != null)
                args.add(after);
            args.add(size + 1);
            final var rows = messages.select("SELECT " + COLUMNS + " FROM " + table() + " WHERE " + PAIR + VALID + (before != null ? " AND m.oId<?" : after != null ? " AND m.oId>?" : "")
                    + " ORDER BY m.oId " + (after != null ? "ASC" : "DESC") + " LIMIT ?", args.toArray());
            if (rows.isEmpty() && latest(actor, peerId).isEmpty()) throw ApiV3Requests.notFound();
            final boolean more = rows.size() > size;
            final var page = new ArrayList<>(rows.subList(0, Math.min(size, rows.size())));
            // 向前翻页时倒序取最近的记录，再恢复响应中的时间顺序。
            if (after == null)
                Collections.reverse(page);
            final var list = new JSONArray();
            for (var row : page)
                list.put(message(row, actor));
            return new JSONObject().put("peerId", peerId).put("peer", peer(peerId)).put("list", list).put("hasMore", more)
                    .put("nextBefore", page.isEmpty() ? "" : page.getFirst().getString("oId")).put("nextAfter", page.isEmpty() ? "" : page.getLast().getString("oId"));
        });
    }

    public JSONObject get(JSONObject viewer, String messageId) throws Exception {
        ApiV3PrivateMessages.requireId(messageId);
        return safe(() -> {
            final String actor = actor(viewer);
            final var rows = messages.select("SELECT " + COLUMNS + " FROM " + table() + " WHERE m.oId=? AND " + OWNER + VALID + " LIMIT 1",
                    messageId, actor, actor);
            if (rows.isEmpty())
                throw ApiV3Requests.notFound();
            final var row = rows.getFirst();
            final String other = actor.equals(row.optString("fromId")) ? row.optString("toId") : row.optString("fromId");
            // DTO 先鉴权，再转换正文或查询公开资料。
            final var dto = message(row, actor);
            return new JSONObject().put("peerId", other).put("peer", peer(other)).put("message", dto);
        });
    }
}
