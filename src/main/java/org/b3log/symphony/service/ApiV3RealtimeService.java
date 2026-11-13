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

import org.b3log.latke.Keys;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.model.User;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.ApiV3ChatEvent;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.b3log.symphony.util.ApiV3AppearanceCatalog;
import org.jsoup.Jsoup;
import org.b3log.symphony.util.AnonymousCaptchaShield;
import org.b3log.symphony.util.ApiV3RealtimeSessions;
import org.b3log.symphony.util.Firewall;
import org.b3log.symphony.util.ApiV3SocketHandshake;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.repository.ApiV3NotificationRevisionRepository;
import org.b3log.symphony.repository.ApiV3PrivateRevisionRepository;
import org.b3log.symphony.repository.ApiV3ArticleRevisionRepository;
import org.b3log.symphony.repository.ApiV3ChatEventRepository;
import org.b3log.symphony.util.ApiV3ChatStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实时传输适配和有界广播，业务写入与旧消息转发由其他链路处理。
 */
@Service
public class ApiV3RealtimeService {
    @Inject
    private ApiV3SocketTicketService tickets;
    @Inject
    private UserRepository users;
    @Inject
    private CloudRepository cloud;
    @Inject
    private MembershipQueryService memberships;
    @Inject
    private ApiV3AppearanceShopService appearanceShop;

    private static final String PRESENCE_GAME_ID = "sys-custom-message";
    private static final int MAX_PRESENCE_TEXT = 512;
    private static final Pattern PRESENCE_VARIABLE = Pattern.compile("\\{(userName|userNickName|userPoint|userLevel|userNo|userCity)}");
    @Inject
    private ApiV3NotificationRevisionRepository revisions;
    @Inject
    private ApiV3ArticleRevisionRepository articleRevisions;
    @Inject
    private ApiV3PrivateRevisionRepository privateRevisions;
    @Inject
    private ApiV3ChatEventRepository chatEvents;
    @Inject
    private ApiV3ChatQueryService chatQuery;
    @Inject
    private ChatTopicDeliveryService topicDelivery;
    private ApiV3ChatStream chatStream;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(256),
            Thread.ofPlatform().daemon().name("v3-realtime-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("v3-realtime-timer").factory());
    private final AtomicBoolean ticking = new AtomicBoolean();
    private final AtomicBoolean cleaning = new AtomicBoolean();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final ConcurrentHashMap<String, String> seen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> seenMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> seenArticles = new ConcurrentHashMap<>();
    private volatile ApiV3RealtimeSessions sessions;

    public void start() {
        registry();
    }

    public Set<String> chatUserIds() {
        final var active = sessions;
        return active == null ? Set.of() : active.chatUserIds();
    }

    public int articleConnectionCount(final String articleId) {
        final var active = sessions;
        return active == null ? 0 : active.articleConnectionCount(articleId);
    }

    private synchronized ApiV3RealtimeSessions registry() {
        if (sessions == null) {
            sessions = new ApiV3RealtimeSessions(new ApiV3RealtimeSessions.Authority() {
                public ApiV3SocketTicketService.Grant consume(String ticket) throws Exception {
                    return tickets.consume(ticket);
                }

                public boolean authorized(ApiV3SocketTicketService.Grant grant) throws Exception {
                    return tickets.authorized(grant);
                }
                // 实时连接也须经过验证码盾；未经阿达明确允许，不得修改、移动、删除或绕过此调用。
            }, (ip, record) -> !AnonymousCaptchaShield.isRestricted(ip)
                    && (!record || Firewall.recordAndMaybeBan(ip)), System::currentTimeMillis);
            timer.scheduleAtFixedRate(() -> {
                sessions.sweep();
                if (polling.compareAndSet(false, true)) try {
                    executeWorker(() -> {
                        try {
                            try {
                                pollNotifications();
                                pollArticles();
                                pollMessages();
                            } catch (Exception ignored) {
                                org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 realtime synchronization unavailable");
                                sessions.closeAll();
                            }
                            try {
                                pollTopic();
                            } catch (Exception ignored) {
                                org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("Committed chat topic projection unavailable");
                            }
                            try {
                                pollChat();
                            } catch (Exception ignored) {
                                org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 chat synchronization unavailable");
                                sessions.closeChat();
                                resetChat();
                            }
                        } finally {
                            polling.set(false);
                        }
                    });
                } catch (RejectedExecutionException ignored) {
                    polling.set(false);
                }
                if (ticking.compareAndSet(false, true)) try {
                    executeWorker(() -> {
                        try {
                            sessions.tick();
                        } finally {
                            ticking.set(false);
                        }
                    });
                } catch (RejectedExecutionException ignored) {
                    ticking.set(false);
                }
            }, 1, 1, TimeUnit.SECONDS);
            timer.scheduleWithFixedDelay(() -> {
                if (cleaning.compareAndSet(false, true)) try {
                    executeWorker(() -> {
                        try {
                            try {
                                for (int batch = 0; batch < 5; batch++) if (tickets.removeExpired() < 200) break;
                            } catch (Exception ignored) {
                                org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 expired socket-ticket cleanup failed");
                            }
                            try {
                                cleanupChatEvents();
                            } catch (Exception ignored) {
                                org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 expired chat-event cleanup failed");
                            }
                        } finally {
                            cleaning.set(false);
                        }
                    });
                } catch (RejectedExecutionException ignored) {
                    cleaning.set(false);
                }
            }, 1, 1, TimeUnit.MINUTES);
        }
        return sessions;
    }

    public void open(WebSocketSession session, String ip) {
        final ApiV3RealtimeSessions active = sessions == null ? registry() : sessions;
        active.open(session, new ApiV3RealtimeSessions.Transport() {
            public void send(String text) {
                if (!ApiV3SocketHandshake.writable(session))
                    throw new IllegalStateException("socket backpressure");
                session.sendText(text);
            }

            public void close() {
                session.close();
            }
        }, ip);
    }

    public void receive(WebSocketSession session, String text) {
        final var registry = sessions;
        if (registry != null)
            registry.receive(session, text);
        else
            session.close();
    }

    public void close(WebSocketSession session) {
        final var registry = sessions;
        if (registry != null)
            registry.close(session);
    }

    private void executeWorker(Runnable work) {
        workers.execute(() -> {
            try {
                work.run();
            } finally {
                try {
                    ApiV3Transactions.releaseThreadResources();
                } catch (RuntimeException ignored) {
                    org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 background JDBC cleanup failed");
                }
            }
        });
    }

    /**
     * 读取已提交的版本标记，包括旧入口或其他后端实例写入的标记。
     */
    public synchronized void pollNotifications() throws Exception {
        if (sessions == null)
            return;
        tickets.requireNoTransaction();
        final Set<String> owners = sessions.owners();
        if (owners.isEmpty()) {
            seen.clear();
            return;
        }
        final var snapshot = revisions.snapshot(owners);
        seen.keySet().retainAll(owners);
        for (final String owner : owners) {
            final String revision = snapshot.getOrDefault(owner, "");
            final String previous = seen.get(owner);
            if (!revision.equals(previous) && (!revision.isEmpty() || previous != null))
                sessions.notificationsChanged(owner);
            seen.put(owner, revision);
        }
    }

    public synchronized void pollArticles() throws Exception {
        if (sessions == null)
            return;
        tickets.requireNoTransaction();
        final Set<String> ids = sessions.articleIds();
        if (ids.isEmpty()) {
            seenArticles.clear();
            return;
        }
        final var snapshot = articleRevisions.snapshot(ids);
        seenArticles.keySet().retainAll(ids);
        for (final String id : ids) {
            final String revision = snapshot.getOrDefault(id, "");
            final String previous = seenArticles.get(id);
            if (!revision.equals(previous) && (!revision.isEmpty() || previous != null))
                sessions.articleChanged(id);
            seenArticles.put(id, revision);
        }
    }

    /**
     * 按所有者合并刷新提示，仅发送变化提示，隐藏私信详情和未读状态。
     */
    public synchronized void pollMessages() throws Exception {
        if (sessions == null)
            return;
        tickets.requireNoTransaction();
        final Set<String> owners = sessions.messageOwners();
        if (owners.isEmpty()) {
            seenMessages.clear();
            return;
        }
        final var snapshot = privateRevisions.snapshot(owners);
        seenMessages.keySet().retainAll(owners);
        for (final String owner : owners) {
            final String revision = snapshot.getOrDefault(owner, "");
            final String previous = seenMessages.get(owner);
            if (!revision.equals(previous) && (!revision.isEmpty() || previous != null))
                sessions.messagesChanged(owner);
            seenMessages.put(owner, revision);
        }
    }

    private synchronized void resetChat() {
        if (chatStream != null)
            chatStream.reset();
    }

    /**
     * 在本实例有界发送即时弹幕，弹幕独立于持久消息游标。
     */
    public void publishBarrage(final org.b3log.symphony.util.ApiV3BarrageEvent event) {
        tickets.requireNoTransaction();
        final var active = sessions;
        if (active == null || !active.hasChat())
            return;
        executeWorker(() -> {
            tickets.requireNoTransaction();
            active.chatBarrage(event);
        });
    }

    /**
     * 领取事务提交后发布即时提示，失败不会改变已确认的领取结果。
     */
    public void publishRedPacketClaim(final String packetId, final String actorId, final String senderId,
                                      final int got, final int count, final long claimedAt) {
        tickets.requireNoTransaction();
        final var active = sessions;
        if (active == null || !active.hasChat()) return;
        executeWorker(() -> {
            try {
                final var participants = chatNoticeUsers(actorId.equals(senderId) ? Set.of(actorId) : Set.of(actorId, senderId));
                final var actor = chatNoticeUser(participants.get(actorId));
                final var sender = chatNoticeUser(participants.get(senderId));
                if (actor == null || sender == null) return;
                active.chatRedPacketClaimed(new JSONObject().put("id", "claim:" + packetId + ":" + got)
                        .put("createdAt", claimedAt).put("actor", actor).put("sender", sender)
                        .put("packetId", packetId).put("got", got).put("count", count));
            } catch (RepositoryException failure) {
                org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 红包领取提示读取失败");
            }
        });
    }

    private void pollChatPresence(final ApiV3RealtimeSessions active) throws RepositoryException {
        final var changes = active.pollChatPresence();
        if (changes.isEmpty() || !active.hasChat()) return;
        final var ids = changes.stream().map(ApiV3RealtimeSessions.ChatPresence::userId).toArray();
        final var parameters = new ArrayList<Object>();
        parameters.add(PRESENCE_GAME_ID);
        Collections.addAll(parameters, ids);
        // 播报独立于在线名单隐私；左连接保留没有管理员文案但已装备座驾的账号。
        final var rows = users.select("SELECT u.oId,u.userName,u.userNickname,u.userStatus,"
                        + "u.userPoint,u.userNo,u.userCity,u.userAvatarURL,c.data FROM `" + users.getName() + "` u LEFT JOIN `" + cloud.getName()
                        + "` c ON c.userId=u.oId AND c.gameId=? WHERE u.oId IN ("
                        + String.join(",", Collections.nCopies(ids.length, "?")) + ")", parameters.toArray());
        final var participants = new HashMap<String, JSONObject>();
        final var duplicates = new java.util.HashSet<String>();
        for (final var row : rows) {
            final var id = row.getString(Keys.OBJECT_ID);
            if (participants.putIfAbsent(id, row) != null) duplicates.add(id);
        }
        duplicates.forEach(participants::remove);
        final long now = System.currentTimeMillis();
        final var notices = new ArrayList<JSONObject>();
        final var mounts = appearanceShop.mounts(participants.keySet(), now);
        for (final var change : changes) {
            final var participant = participants.get(change.userId());
            final var mount = mounts.get(change.userId());
            final var mountNotice = change.joined() ? chatMountNotice(participant, mount, now) : null;
            if (mountNotice != null) {
                notices.add(mountNotice);
                continue;
            }
            final var notice = chatPresenceNotice(participants.get(change.userId()), change.joined(), now,
                    () -> chatPresenceLevel(change.userId()));
            if (notice != null) notices.add(notice);
        }
        active.chatPresenceChanged(notices);
    }

    /** 座驾只替代本次进场文案，不影响管理员配置的退场提示。 */
    static JSONObject chatMountNotice(final JSONObject user, final ApiV3AppearanceCatalog.Product mount, final long now) {
        if (mount == null || user == null) return null;
        final var actor = chatNoticeUser(user);
        if (actor == null) return null;
        final String nickname = actor.optString("userNickname").isEmpty() ? actor.getString("userName") : actor.getString("userNickname");
        return new JSONObject().put("id", UUID.randomUUID().toString()).put("kind", "join").put("createdAt", now)
                .put("actor", actor).put("text", nickname + mount.verb() + mount.name() + "来了")
                .put("mount", new JSONObject().put("id", mount.styleId()).put("version", 1))
                .put("avatarURL", user.optString("userAvatarURL"));
    }

    private String chatPresenceLevel(final String userId) {
        try {
            return memberships.getStatusByUserId(userId).optString("lvCode").split("_", 2)[0];
        } catch (ServiceException failure) {
            // 与经典版一致，会员等级暂时不可读时变量留空。
            return "";
        }
    }

    static JSONObject chatPresenceNotice(final JSONObject user, final boolean joined, final long now,
                                         final java.util.function.Supplier<String> level) {
        // 进出提示按配置播报，不受在线名单的隐私设置影响。
        if (user == null) return null;
        final var actor = chatNoticeUser(user);
        if (actor == null) return null;
        final String[] templates = user.optString("data").split("&&&", 3);
        if (templates.length < 2) return null;
        final String template = templates[joined ? 0 : 1].strip();
        if (template.isEmpty()) return null;

        // 经典版允许写入 HTML。V3 只广播文本，先提取模板中的文字，再以字面量替换变量。
        final String plain = Jsoup.parseBodyFragment(template).text();
        final String membership = plain.contains("{userLevel}") ? level.get() : "";
        final String city = user.optString(UserExt.USER_CITY);
        final String text = PRESENCE_VARIABLE.matcher(plain).replaceAll(match -> Matcher.quoteReplacement(switch (match.group(1)) {
            case "userName" -> user.optString(User.USER_NAME);
            case "userNickName" -> user.optString(UserExt.USER_NICKNAME);
            case "userPoint" -> user.optString(UserExt.USER_POINT);
            case "userNo" -> user.optString(UserExt.USER_NO);
            case "userCity" -> city.isEmpty() ? "神秘角落" : city;
            case "userLevel" -> membership;
            default -> match.group();
        })).replaceAll("[\\p{Cc}]+", " ").strip();
        if (text.isEmpty()) return null;
        final int end = text.offsetByCodePoints(0, Math.min(MAX_PRESENCE_TEXT, text.codePointCount(0, text.length())));
        return new JSONObject().put("id", UUID.randomUUID().toString()).put("kind", joined ? "join" : "leave")
                .put("createdAt", now).put("actor", actor).put("text", text.substring(0, end));
    }

    private Map<String, JSONObject> chatNoticeUsers(final Collection<String> ids) throws RepositoryException {
        final var rows = users.select("SELECT oId,userName,userNickname,userOnlineStatus,userStatus FROM `" + users.getName()
                + "` WHERE oId IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")", ids.toArray());
        final var result = new HashMap<String, JSONObject>();
        for (final var row : rows) result.put(row.getString(Keys.OBJECT_ID), row);
        return result;
    }

    private static JSONObject chatNoticeUser(final JSONObject user) {
        if (user == null || user.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID) return null;
        final String name = user.optString(User.USER_NAME);
        if (!name.matches("[A-Za-z0-9_-]{1,64}")) return null;
        final String nickname = user.optString(UserExt.USER_NICKNAME);
        return new JSONObject().put("id", user.getString(Keys.OBJECT_ID)).put("userName", name)
                .put("userNickname", nickname.length() <= 128 && nickname.codePoints().noneMatch(c -> c < 32 || c == 127) ? nickname : "");
    }

    public void pollTopic() throws Exception {
        tickets.requireNoTransaction();
        topicDelivery.refreshCommitted();
    }

    public synchronized void pollChat() throws Exception {
        if (sessions == null)
            return;
        tickets.requireNoTransaction();
        try {
            pollChatPresence(sessions);
        } catch (RepositoryException failure) {
            org.apache.logging.log4j.LogManager.getLogger(ApiV3RealtimeService.class).warn("V3 聊天室进出场提示读取失败");
        }
        if (!sessions.hasChat()) {
            resetChat();
            return;
        }
        if (chatStream == null)
            chatStream = new ApiV3ChatStream(new ApiV3ChatStream.Source() {
                public long head() throws Exception {
                    return chatEvents.head();
                }

                public java.util.List<org.json.JSONObject> after(long revision, long through, int limit) throws Exception {
                    return chatEvents.after(revision, through, limit);
                }
            }, new ApiV3ChatStream.Sink() {
                public void changed(String kind, String id, long revision) {
                    final var snapshot = chatSnapshot(kind, id);
                    if (snapshot == null) sessions.chatChanged(kind, id, revision, null);
                    else sessions.chatChanged(kind, id, revision, snapshot.message(), snapshot.selections());
                }

                public void syncRequired() {
                    sessions.chatSyncRequired();
                }
            });
        chatStream.poll();
    }

    /**
     * 本节点按 content.read 详情接口的字段白名单，每个已提交事件读取一次消息快照。
     */
    private ApiV3ChatQueryService.RealtimeSnapshot chatSnapshot(final String kind, final String id) {
        if (!ApiV3ChatEvent.CREATED.equals(kind) && !ApiV3ChatEvent.UPDATED.equals(kind) && !"reactions".equals(kind)) return null;
        try {
            return chatQuery.realtimeSnapshot(id);
        } catch (RepositoryException | ApiV3Exception unavailable) {
            // 消息随后被删除或暂时读取失败时，仅保留重新同步提示。
            return null;
        }
    }

    public int cleanupChatEvents() throws Exception {
        tickets.requireNoTransaction();
        int removed = 0;
        final long cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        for (int batch = 0; batch < 5; batch++) {
            final int count = chatEvents.removeExpired(cutoff);
            removed += count;
            if (count < 200)
                break;
        }
        return removed;
    }
}
