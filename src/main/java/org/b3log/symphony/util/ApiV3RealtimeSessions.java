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

import org.b3log.symphony.service.ApiV3SocketTicketService.Grant;
import org.b3log.symphony.model.ApiV3SocketTicket;
import org.b3log.symphony.model.ApiV3ChatEvent;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * 管理按资源绑定的有界 v3 连接，不读取页面会话或发送旧版事件载荷。
 */
public final class ApiV3RealtimeSessions {
    public interface Transport {
        void send(String text);

        void close();
    }

    public interface Authority {
        Grant consume(String ticket) throws Exception;

        boolean authorized(Grant grant) throws Exception;
    }

    public interface Guard {
        boolean allowed(String ip, boolean recordRequest);
    }

    private static final int MAX_CONNECTIONS = 4000, MAX_PER_IP = 32, MAX_PER_USER = 8, MAX_MESSAGES = 60;
    private static final long AUTH_TIMEOUT = 5000, RECHECK_INTERVAL = 15000;
    private final Authority authority;
    private final Guard guard;
    private final LongSupplier clock;
    private final Object admission = new Object();
    private final ConcurrentHashMap<Object, Connection> connections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Connection>> byUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Connection>> byArticle = new ConcurrentHashMap<>();
    private final Set<Connection> chat = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> ips = new HashMap<>(), users = new HashMap<>();
    private Set<String> lastChatPresence = Set.of();
    private final Set<String> announcedChatPresence = new HashSet<>();

    public record ChatPresence(String userId, boolean joined) {
    }

    private static final class Connection {
        final Object key;
        final Transport transport;
        final String ip;
        final long deadline;
        final ReentrantLock lock = new ReentrantLock();
        volatile Grant grant;
        volatile boolean ready;
        // 旧客户端默认参与进出场；新版首页仅订阅消息，不冒充进入聊天室。
        volatile boolean presence = true;
        long nextCheck, window;
        int messages;
        boolean closed;

        Connection(Object key, Transport transport, String ip, long now) {
            this.key = key;
            this.transport = transport;
            this.ip = ip;
            deadline = now + AUTH_TIMEOUT;
            window = now;
        }
    }

    public ApiV3RealtimeSessions(Authority authority, Guard guard, LongSupplier clock) {
        this.authority = authority;
        this.guard = guard;
        this.clock = clock;
    }

    public void open(Object key, Transport transport, String ip) {
        boolean accepted = false;
        final Connection c = new Connection(key, transport, ip, clock.getAsLong());
        c.lock.lock();
        try {
            if (key != null && transport != null && ip != null && guard.allowed(ip, true))
                synchronized (admission) {
                    if (!connections.containsKey(key) && connections.size() < MAX_CONNECTIONS
                            && ips.getOrDefault(ip, 0) < MAX_PER_IP) {
                        connections.put(key, c);
                        ips.merge(ip, 1, Integer::sum);
                        accepted = true;
                    }
                }
            if (accepted)
                c.transport.send(new JSONObject().put("type", "authentication.required").put("data", new JSONObject()).toString());
        } catch (RuntimeException ignored) {
            if (accepted)
                remove(c);
            accepted = false;
        } finally {
            c.lock.unlock();
        }
        if (!accepted && transport != null)
            try {
                transport.close();
            } catch (RuntimeException ignored) {
            }
    }

    public void receive(Object key, String text) {
        final Connection c = connections.get(key);
        if (c == null)
            return;
        c.lock.lock();
        try {
            if (c.closed)
                return;
            final long now = clock.getAsLong();
            if (!guard.allowed(c.ip, true) || text == null || text.length() > 1024)
                throw new IllegalArgumentException();
            if (now - c.window >= 60000) {
                c.window = now;
                c.messages = 0;
            }
            if (++c.messages > MAX_MESSAGES)
                throw new IllegalArgumentException();
            final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 1024)
                throw new IllegalArgumentException();
            final JSONObject body = ApiV3Json.parse(bytes);
            if (c.grant == null) {
                if (now >= c.deadline || !body.keySet().equals(Set.of("type", "ticket")) || !"authenticate".equals(body.opt("type"))
                        || !(body.opt("ticket") instanceof String ticket) || !ApiV3TokenUtil.isValidFormat(ticket))
                    throw new IllegalArgumentException();
                // 消费票据后再检查截止时间和当前权限，通过后才加入订阅索引。
                final Grant grant = authority.consume(ticket);
                if (clock.getAsLong() >= c.deadline || !authority.authorized(grant))
                    throw new IllegalArgumentException();
                synchronized (admission) {
                    if (users.getOrDefault(grant.userId(), 0) >= MAX_PER_USER)
                        throw new IllegalArgumentException();
                    c.grant = grant;
                    users.merge(grant.userId(), 1, Integer::sum);
                    byUser.computeIfAbsent(grant.userId(), ignored -> ConcurrentHashMap.newKeySet()).add(c);
                    if (ApiV3SocketTicket.CHANNEL_ARTICLE.equals(grant.channel()))
                        byArticle.computeIfAbsent(grant.targetId(), ignored -> ConcurrentHashMap.newKeySet()).add(c);
                    if (ApiV3SocketTicket.CHANNEL_CHAT.equals(grant.channel()))
                        chat.add(c);
                }
                c.nextCheck = clock.getAsLong() + RECHECK_INTERVAL;
                final var ready = new JSONObject().put("channel", grant.channel()).put("expiresAt", grant.expiresAt());
                if (ApiV3SocketTicket.CHANNEL_CHAT.equals(grant.channel())) ready.put("presenceControl", true);
                if (ApiV3SocketTicket.CHANNEL_ARTICLE.equals(grant.channel()))
                    ready.put("articleId", grant.targetId());
                c.transport.send(new JSONObject().put("type", "ready").put("data", ready).toString());
                c.ready = true;
            } else {
                if (ApiV3SocketTicket.CHANNEL_CHAT.equals(c.grant.channel()) && "chat.presence".equals(body.opt("type"))) {
                    if (!body.keySet().equals(Set.of("type", "active")) || !(body.opt("active") instanceof Boolean active) || !valid(c))
                        throw new IllegalArgumentException();
                    c.presence = active;
                    return;
                }
                if (!body.keySet().equals(Set.of("type")) || !"ping".equals(body.opt("type")) || !valid(c))
                    throw new IllegalArgumentException();
                c.transport.send(new JSONObject().put("type", "pong").put("data", new JSONObject()).toString());
            }
        } catch (Exception e) {
            remove(c);
        } finally {
            c.lock.unlock();
        }
    }

    /**
     * 只向对应用户发送失效信号，载荷省略旧版消息和被引用内容的 ID。
     */
    public void notificationsChanged(String actor) {
        ownerChanged(actor, ApiV3SocketTicket.CHANNEL_NOTIFICATIONS, "notifications.changed");
    }

    public void messagesChanged(String actor) {
        ownerChanged(actor, ApiV3SocketTicket.CHANNEL_MESSAGES, "messages.changed");
    }

    private void ownerChanged(String actor, String channel, String type) {
        if (actor == null || actor.isEmpty())
            return;
        final Set<Connection> recipients = byUser.get(actor);
        if (recipients == null)
            return;
        for (Connection c : recipients) {
            c.lock.lock();
            try {
                if (c.closed || c.grant == null || !actor.equals(c.grant.userId()) || !channel.equals(c.grant.channel()))
                    continue;
                if (!valid(c)) {
                    remove(c);
                    continue;
                }
                c.transport.send(new JSONObject().put("type", type).put("data", new JSONObject()).toString());
            } catch (Exception e) {
                remove(c);
            } finally {
                c.lock.unlock();
            }
        }
    }

    /**
     * 按文章发送失效信号，载荷省略评论、作者及正文。
     */
    public void articleChanged(String articleId) {
        final Set<Connection> recipients = byArticle.get(articleId);
        if (recipients == null)
            return;
        for (Connection c : recipients) {
            c.lock.lock();
            try {
                if (c.closed || c.grant == null || !articleId.equals(c.grant.targetId())
                        || !ApiV3SocketTicket.CHANNEL_ARTICLE.equals(c.grant.channel()))
                    continue;
                if (!valid(c)) {
                    remove(c);
                    continue;
                }
                c.transport.send(new JSONObject().put("type", "article.changed")
                        .put("data", new JSONObject().put("articleId", articleId)).toString());
            } catch (Exception e) {
                remove(c);
            } finally {
                c.lock.unlock();
            }
        }
    }

    public void chatChanged(String kind, String messageId, long revision, JSONObject snapshot) {
        chatChanged(kind, messageId, revision, snapshot, Map.of());
    }

    public void chatChanged(String kind, String messageId, long revision, JSONObject snapshot, Map<String, String> selections) {
        if (!ApiV3ChatEvent.validChange(kind, messageId) || revision < 1)
            throw new IllegalArgumentException("invalid chat change");
        if (ApiV3ChatEvent.TOPIC.equals(kind)) {
            broadcastChat("chat.topic_changed", new JSONObject().put("revision", Long.toString(revision)));
            return;
        }
        final JSONObject data = new JSONObject().put("messageId", messageId).put("revision", Long.toString(revision));
        if (snapshot != null && (ApiV3ChatEvent.CREATED.equals(kind) || ApiV3ChatEvent.UPDATED.equals(kind))
                && messageId.equals(snapshot.optString("id")) && snapshot.toString().getBytes(StandardCharsets.UTF_8).length <= 256 * 1024) {
            data.put("message", snapshot);
        } else if (snapshot != null && "reactions".equals(kind) && messageId.equals(snapshot.optString("id"))) {
            final var summary = snapshot.optJSONObject("reactions");
            if (summary != null) data.put("reactions", summary);
            if (snapshot.optBoolean("reactionsUnavailable")) data.put("reactionsUnavailable", true);
        }
        broadcastChat("chat.message." + kind, data, selections);
    }

    public void chatBarrage(final ApiV3BarrageEvent event) {
        broadcastChat("chat.barrager", event.dto());
    }

    public void chatRedPacketClaimed(final JSONObject data) {
        broadcastChat("chat.red_packet.claimed", data);
    }

    public void chatPresenceChanged(final List<JSONObject> changes) {
        if (changes.isEmpty()) return;
        final var frames = changes.stream()
                .map(data -> new JSONObject().put("type", "chat.presence.changed").put("data", data).toString())
                .toList();
        broadcastChatFrames(frames, Map.of(), false);
    }

    public List<ChatPresence> pollChatPresence() {
        synchronized (admission) {
            final var current = new HashSet<String>();
            for (final Connection c : chat) {
                if (c.ready && c.presence) current.add(c.grant.userId());
            }
            final var changes = new ArrayList<ChatPresence>();
            // 连续两次观测一致才推送，合并短暂重连。同一账号多个标签页只计一次。
            final var departed = announcedChatPresence.iterator();
            while (departed.hasNext() && changes.size() < 16) {
                final String id = departed.next();
                if (!current.contains(id) && !lastChatPresence.contains(id)) {
                    changes.add(new ChatPresence(id, false));
                    departed.remove();
                }
            }
            for (final String id : current) {
                if (changes.size() >= 16) break;
                if (lastChatPresence.contains(id) && announcedChatPresence.add(id))
                    changes.add(new ChatPresence(id, true));
            }
            lastChatPresence = current;
            return changes;
        }
    }

    public void chatSyncRequired() {
        broadcastChat("chat.sync_required", new JSONObject());
    }

    private void broadcastChat(String type, JSONObject data) {
        broadcastChat(type, data, Map.of());
    }

    private void broadcastChat(String type, JSONObject data, Map<String, String> selections) {
        final String frame = new JSONObject().put("type", type).put("data", data).toString();
        broadcastChatFrames(List.of(frame), selections, data.has("message") || data.has("reactions"));
    }

    private void broadcastChatFrames(final List<String> frames, final Map<String, String> selections, final boolean personalized) {
        for (Connection c : chat) {
            c.lock.lock();
            try {
                if (c.closed || c.grant == null || !ApiV3SocketTicket.CHANNEL_CHAT.equals(c.grant.channel()))
                    continue;
                if (!valid(c)) {
                    remove(c);
                    continue;
                }
                final String selected = selections.get(c.grant.userId());
                for (final String frame : frames)
                    c.transport.send(selected == null || !personalized ? frame : personalizedChatFrame(frame, selected));
            } catch (Exception e) {
                remove(c);
            } finally {
                c.lock.unlock();
            }
        }
    }

    private static String personalizedChatFrame(final String frame, final String selected) {
        // 公共帧可以复用，只有本人有选择时才复制并标记该连接的状态。
        final var payload = new JSONObject(frame);
        final var data = payload.getJSONObject("data");
        final var reactions = data.has("message") ? data.getJSONObject("message").getJSONObject("reactions")
                : data.getJSONObject("reactions");
        reactions.put("currentUserReaction", selected);
        final var summary = reactions.getJSONArray("summary");
        for (int i = 0; i < summary.length(); i++) {
            final var reaction = summary.getJSONObject(i);
            reaction.put("selected", selected.equals(reaction.getString("value")));
        }
        return payload.toString();
    }

    public boolean hasChat() {
        return !chat.isEmpty();
    }

    /**
     * 只有已认证的聊天室订阅计入在线名单。
     */
    public Set<String> chatUserIds() {
        final Set<String> ids = new java.util.HashSet<>();
        for (Connection c : chat) {
            c.lock.lock();
            try {
                if (c.closed || c.grant == null || !ApiV3SocketTicket.CHANNEL_CHAT.equals(c.grant.channel()))
                    continue;
                if (!valid(c)) {
                    remove(c);
                    continue;
                }
                ids.add(c.grant.userId());
            } catch (Exception e) {
                remove(c);
            } finally {
                c.lock.unlock();
            }
        }
        return Set.copyOf(ids);
    }

    /**
     * 统计当前实例已认证的文章订阅连接，按连接计数。
     */
    public int articleConnectionCount(final String articleId) {
        final Set<Connection> targets = byArticle.get(articleId);
        if (targets == null) return 0;
        int count = 0;
        for (final Connection c : targets) {
            c.lock.lock();
            try {
                if (c.closed || c.grant == null || !articleId.equals(c.grant.targetId())
                        || !ApiV3SocketTicket.CHANNEL_ARTICLE.equals(c.grant.channel())) continue;
                if (!valid(c)) {
                    remove(c);
                    continue;
                }
                count++;
            } catch (final Exception e) {
                remove(c);
            } finally {
                c.lock.unlock();
            }
        }
        return count;
    }

    public void closeChat() {
        for (Connection c : chat)
            close(c.key);
    }

    /**
     * 在 Netty 事件循环之外执行，忙碌的连接会在激活前自行检查截止时间。
     */
    public void tick() {
        for (Connection c : connections.values()) {
            if (!c.lock.tryLock())
                continue;
            try {
                final long now = clock.getAsLong();
                if (c.closed)
                    continue;
                if (!guard.allowed(c.ip, false) || (c.grant == null ? now >= c.deadline
                        : c.grant.expiresAt() <= now || now >= c.nextCheck && !valid(c)))
                    remove(c);
            } catch (Exception e) {
                remove(c);
            } finally {
                c.lock.unlock();
            }
        }
    }

    /**
     * 快速清理不访问数据库，避免授权检查延迟导致空闲的未认证连接滞留。
     */
    public void sweep() {
        for (Connection c : connections.values()) {
            if (!c.lock.tryLock())
                continue;
            try {
                final long now = clock.getAsLong();
                if (!c.closed && (!guard.allowed(c.ip, false)
                        || (c.grant == null ? now >= c.deadline : c.grant.expiresAt() <= now)))
                    remove(c);
            } catch (RuntimeException e) {
                remove(c);
            } finally {
                c.lock.unlock();
            }
        }
    }

    private boolean valid(Connection c) throws Exception {
        if (c.closed || c.grant == null || c.grant.expiresAt() <= clock.getAsLong()
                || !guard.allowed(c.ip, false) || !authority.authorized(c.grant))
            return false;
        c.nextCheck = clock.getAsLong() + RECHECK_INTERVAL;
        return true;
    }

    public void close(Object key) {
        Connection c = connections.get(key);
        if (c == null)
            return;
        c.lock.lock();
        try {
            remove(c);
        } finally {
            c.lock.unlock();
        }
    }

    public void closeAll() {
        for (Object key : connections.keySet())
            close(key);
    }

    private void remove(Connection c) {
        if (c.closed)
            return;
        c.closed = true;
        // 在同一把登记锁内撤销连接、计数和订阅索引，再关闭底层传输。
        synchronized (admission) {
            connections.remove(c.key, c);
            decrement(ips, c.ip);
            if (c.grant != null) {
                chat.remove(c);
                decrement(users, c.grant.userId());
                final Set<Connection> owned = byUser.get(c.grant.userId());
                if (owned != null) {
                    owned.remove(c);
                    if (owned.isEmpty())
                        byUser.remove(c.grant.userId(), owned);
                }
                if (ApiV3SocketTicket.CHANNEL_ARTICLE.equals(c.grant.channel())) {
                    final Set<Connection> targets = byArticle.get(c.grant.targetId());
                    if (targets != null) {
                        targets.remove(c);
                        if (targets.isEmpty())
                            byArticle.remove(c.grant.targetId(), targets);
                    }
                }
            }
        }
        try {
            c.transport.close();
        } catch (RuntimeException ignored) {
        }
    }

    private static void decrement(Map<String, Integer> counts, String key) {
        counts.computeIfPresent(key, (k, n) -> n <= 1 ? null : n - 1);
    }

    public int size() {
        return connections.size();
    }

    public Set<String> owners() {
        return owners(ApiV3SocketTicket.CHANNEL_NOTIFICATIONS);
    }

    public Set<String> messageOwners() {
        return owners(ApiV3SocketTicket.CHANNEL_MESSAGES);
    }

    private Set<String> owners(String channel) {
        synchronized (admission) {
            final var result = new java.util.HashSet<String>();
            byUser.forEach((owner, connections) -> {
                if (connections.stream().anyMatch(c -> c.grant != null && channel.equals(c.grant.channel())))
                    result.add(owner);
            });
            return Set.copyOf(result);
        }
    }

    public Set<String> articleIds() {
        synchronized (admission) {
            return Set.copyOf(byArticle.keySet());
        }
    }
}
