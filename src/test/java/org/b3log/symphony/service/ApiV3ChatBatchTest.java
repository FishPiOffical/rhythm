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

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.repository.ReactionRepository;
import org.b3log.symphony.repository.ApiV3ChatEventRepository;
import org.b3log.symphony.repository.ApiV3SocketTicketRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3RealtimeSessions;
import org.b3log.symphony.util.UserOnlineTracker;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.*;
import java.util.*;

/** 真实 JDBC 执行批量 SQL，并通过实际连接注册表检查个人选择和单帧协议。 */
public class ApiV3ChatBatchTest {
    static class Fixture implements AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:h2:mem:chatBatch;MODE=MySQL;DATABASE_TO_UPPER=FALSE");
        final ApiV3ChatQueryService service = new ApiV3ChatQueryService() {
            protected String render(String content) { return "<p>" + content + "</p>"; }
        };
        int messageQueries, reactionQueries;
        boolean failMessages, failReactions;
        List<JSONObject> query(String sql, Object... args) throws RepositoryException {
            try (var statement = db.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                try (var rows = statement.executeQuery()) {
                    final var result = new ArrayList<JSONObject>();
                    final var metadata = rows.getMetaData();
                    while (rows.next()) {
                        final var row = new JSONObject();
                        for (int i = 1; i <= metadata.getColumnCount(); i++) {
                            final Object value = rows.getObject(i);
                            row.put(metadata.getColumnLabel(i), value instanceof Clob ? rows.getString(i) : value);
                        }
                        result.add(row);
                    }
                    return result;
                }
            } catch (SQLException failure) { throw new RepositoryException(failure); }
        }
        Fixture() throws Exception {
            db.createStatement().execute("CREATE TABLE chat_room(oId VARCHAR(32) PRIMARY KEY,content TEXT)");
            db.createStatement().execute("CREATE TABLE reaction(oId BIGINT PRIMARY KEY,reactionTargetId VARCHAR(32),reactionTargetType VARCHAR(32),reactionGroup VARCHAR(32),reactionUserId VARCHAR(32),reactionValue VARCHAR(32))");
            ApiV3TokenActivityTest.inject(service, ApiV3ChatQueryService.class, "messages", new ChatRoomRepository() {
                public String getName() { return "chat_room"; }
                public List<JSONObject> select(String sql, Object... args) throws RepositoryException {
                    messageQueries++;
                    if (failMessages) throw new RepositoryException("test message read failure");
                    return query(sql, args);
                }
            });
            ApiV3TokenActivityTest.inject(service, ApiV3ChatQueryService.class, "reactions", new ReactionRepository() {
                public String getName() { return "reaction"; }
                public List<JSONObject> select(String sql, Object... args) throws RepositoryException {
                    reactionQueries++;
                    if (failReactions) throw new RepositoryException("test reaction read failure");
                    return query(sql, args);
                }
            });
        }
        void message(String id, String content) throws Exception {
            try (var statement = db.prepareStatement("INSERT INTO chat_room VALUES(?,?)")) {
                statement.setString(1, id); statement.setString(2, content); statement.executeUpdate();
            }
        }
        void reaction(long id, String target, String actor, String value) throws Exception {
            try (var statement = db.prepareStatement("INSERT INTO reaction VALUES(?,?,?,?,?,?)")) {
                statement.setLong(1, id); statement.setString(2, target);
                statement.setString(3, Reaction.TARGET_TYPE_CHAT); statement.setString(4, Reaction.GROUP_EMOJI);
                statement.setString(5, actor); statement.setString(6, value); statement.executeUpdate();
            }
        }
        public void close() throws SQLException { db.close(); }
    }

    private static final class Transport implements ApiV3RealtimeSessions.Transport {
        final List<JSONObject> frames = new ArrayList<>();
        boolean closed;
        public void send(String text) { frames.add(new JSONObject(text)); }
        public void close() { closed = true; }
    }

    static void personalized(ApiV3ChatQueryService.RealtimeSnapshot snapshot, Fixture fixture) throws Exception {
        final var denied = new HashSet<String>();
        final var sessions = new ApiV3RealtimeSessions(new ApiV3RealtimeSessions.Authority() {
            public ApiV3SocketTicketService.Grant consume(String ticket) {
                return new ApiV3SocketTicketService.Grant(ticket.startsWith("A") ? "123" : "456", "789", 10_000, "chat", "");
            }
            public boolean authorized(ApiV3SocketTicketService.Grant grant) { return !denied.contains(grant.userId()); }
        }, (ip, record) -> true, () -> 0, new UserOnlineTracker(() -> 0, () -> 0));
        final var own = new Transport(); final var other = new Transport();
        sessions.open("own", own, "192.0.2.1"); sessions.open("other", other, "192.0.2.2");
        sessions.receive("own", new JSONObject().put("type", "authenticate").put("ticket", "A".repeat(43)).toString());
        sessions.receive("other", new JSONObject().put("type", "authenticate").put("ticket", "B".repeat(43)).toString());
        own.frames.clear(); other.frames.clear();
        sessions.chatChanged("reactions", "1", 1, snapshot.message(), snapshot.selections());
        Assert.assertEquals(own.frames.size(), 1); Assert.assertEquals(other.frames.size(), 1);
        final var ownData = own.frames.getFirst().getJSONObject("data");
        final var otherData = other.frames.getFirst().getJSONObject("data");
        Assert.assertEquals(ownData.getJSONObject("reactions").getString("currentUserReaction"), "laugh");
        Assert.assertEquals(otherData.getJSONObject("reactions").getString("currentUserReaction"), "");
        Assert.assertFalse(ownData.toString().contains("reactionUserId"));
        Assert.assertEquals(snapshot.message().getJSONObject("reactions").getString("currentUserReaction"), "");
        denied.add("123");
        sessions.chatChanged("updated", "1", 2, snapshot.message(), snapshot.selections());
        Assert.assertTrue(own.closed); Assert.assertEquals(own.frames.size(), 1);
        Assert.assertEquals(other.frames.size(), 2);
        sessions.receive("other", new JSONObject().put("type", "chat.presence").put("active", false).toString());
        final var realtime = new ApiV3RealtimeService();
        final var tickets = new ApiV3SocketTicketService();
        ApiV3TokenActivityTest.inject(tickets, ApiV3SocketTicketService.class, "tickets", new ApiV3SocketTicketRepository() {
            public boolean hasTransactionBegun() { return false; }
        });
        final var events = new ArrayList<JSONObject>();
        final var head = new long[]{0};
        ApiV3TokenActivityTest.inject(realtime, ApiV3RealtimeService.class, "sessions", sessions);
        ApiV3TokenActivityTest.inject(realtime, ApiV3RealtimeService.class, "tickets", tickets);
        ApiV3TokenActivityTest.inject(realtime, ApiV3RealtimeService.class, "chatQuery", fixture.service);
        ApiV3TokenActivityTest.inject(realtime, ApiV3RealtimeService.class, "chatEvents", new ApiV3ChatEventRepository() {
            public long head() { return head[0]; }
            public List<JSONObject> after(long revision, long through, int limit) {
                return events.stream().filter(row -> row.getLong("revision") > revision && row.getLong("revision") <= through).limit(limit).toList();
            }
        });
        realtime.pollChat(); other.frames.clear();
        final int reads = fixture.messageQueries;
        final int reactionReads = fixture.reactionQueries;
        for (int i = 1; i <= 3; i++) events.add(new JSONObject().put("revision", i).put("messageId", "1")
                .put("kind", i == 2 ? "reactions" : "updated"));
        head[0] = 3; realtime.pollChat();
        Assert.assertEquals(fixture.messageQueries, reads + 1);
        Assert.assertEquals(fixture.reactionQueries, reactionReads + 1);
        Assert.assertEquals(other.frames.size(), 3);
        for (int i = 0; i < 3; i++) Assert.assertEquals(other.frames.get(i).getJSONObject("data").getString("revision"), "" + (i + 1));
        fixture.db.createStatement().executeUpdate("DELETE FROM reaction WHERE reactionUserId='123'");
        events.add(new JSONObject().put("revision", 4).put("messageId", "1").put("kind", "reactions"));
        head[0] = 4; realtime.pollChat();
        Assert.assertEquals(fixture.messageQueries, reads + 2);
        final var summary = other.frames.getLast().getJSONObject("data").getJSONObject("reactions").getJSONArray("summary");
        Assert.assertEquals(summary.getJSONObject(1).getLong("count"), 1L);
        fixture.reaction(2, "1", "123", "laugh");
        sessions.closeAll();
    }

    public static void main(String[] args) throws Exception {
        final var props = new Properties(); props.setProperty("runtimeDatabase", "NONE");
        Latkes.setLatkeProps(props); Latkes.setLocalProps(props);
        final String stored = new JSONObject().put("content", "hello").put("userOId", "123").put("userName", "Reader")
                .put("time", 1_700_000_000_000L).put("userEmail", "private@example.invalid").toString();
        try (final var fixture = new Fixture()) {
            for (int i = 1; i <= 41; i++) fixture.message("" + i, stored);
            fixture.message("42", "malformed");
            fixture.reaction(2, "1", "123", "laugh"); fixture.reaction(1, "1", "321", "heart");
            fixture.reaction(3, "1", "222", "laugh"); fixture.reaction(4, "1", "333", "invalid");
            final var snapshots = fixture.service.realtimeSnapshots(List.of("1", "1", "2", "42", "999"));
            Assert.assertEquals(fixture.messageQueries, 1); Assert.assertEquals(fixture.reactionQueries, 1);
            Assert.assertEquals(snapshots.size(), 3);
            final var snapshot = snapshots.get("1");
            Assert.assertFalse(snapshot.message().toString().contains("private@example.invalid"));
            Assert.assertEquals(snapshot.selections().get("123"), "laugh");
            final var counts = snapshot.message().getJSONObject("reactions").getJSONArray("summary");
            Assert.assertEquals(counts.getJSONObject(0).getString("value"), "heart");
            Assert.assertEquals(counts.getJSONObject(1).getLong("count"), 2L);
            Assert.assertEquals(snapshots.get("2").message().getJSONObject("reactions").getJSONArray("summary").length(), 0);
            Assert.assertFalse(snapshots.get("42").message().has("reactions"));
            personalized(snapshot, fixture);
            fixture.messageQueries = 0; fixture.reactionQueries = 0;
            final var ids = new ArrayList<String>(); for (int i = 1; i <= 41; i++) ids.add("" + i);
            Assert.assertEquals(fixture.service.realtimeSnapshots(ids).size(), 41);
            Assert.assertEquals(fixture.messageQueries, 3); Assert.assertEquals(fixture.reactionQueries, 3);
            fixture.messageQueries = 0; fixture.reactionQueries = 0;
            fixture.service.realtimeSnapshots(List.of());
            Assert.assertEquals(fixture.messageQueries, 0); Assert.assertEquals(fixture.reactionQueries, 0);
            for (int i = 0; i < 2001; i++) fixture.reaction(10 + i, "2", "" + (1000 + i), "laugh");
            final var popular = fixture.service.realtimeSnapshots(List.of("2", "1"));
            Assert.assertFalse(popular.get("2").message().has("reactions"));
            Assert.assertEquals(popular.get("1").selections().get("123"), "laugh");
            fixture.failReactions = true;
            final var unavailable = fixture.service.realtimeSnapshots(List.of("1", "2"));
            Assert.assertTrue(unavailable.get("1").message().getBoolean("reactionsUnavailable"));
            Assert.assertTrue(unavailable.get("1").selections().isEmpty());
            fixture.failMessages = true;
            try { fixture.service.realtimeSnapshots(List.of("1")); Assert.fail(); } catch (RepositoryException expected) { }
            try { fixture.service.realtimeSnapshot(null); Assert.fail(); } catch (ApiV3Exception expected) { Assert.assertEquals(expected.status, 422); }
        }
        System.out.println("PASS real JDBC batch/dedup/chunks/order/caps/failure, DTO privacy and per-connection authorization/selection/single frames");
    }
}
