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
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;

import java.util.*;

import static org.b3log.symphony.service.ApiV3SocketTicketServiceTest.*;

/**
 * 使用实际实时连接注册表、授权核心及 SQLite 状态，传输和 IP 检查由夹具控制。
 */
public class ApiV3RealtimeSessionsTest {
    static class Transport implements ApiV3RealtimeSessions.Transport {
        final List<JSONObject> sent = new ArrayList<>();
        boolean closed, failSend;

        public void send(String text) {
            if (failSend) throw new IllegalStateException("slow transport");
            sent.add(new JSONObject(text));
        }

        public void close() {
            closed = true;
        }
    }

    static class Live extends Fixture {
        boolean allowed = true, slowConsume;
        int consumes;
        final ApiV3RealtimeSessions registry;

        Live() throws Exception {
            registry = new ApiV3RealtimeSessions(new ApiV3RealtimeSessions.Authority() {
                public ApiV3SocketTicketService.Grant consume(String ticket) throws Exception {
                    consumes++;
                    var result = core.consume(ticket);
                    if (slowConsume) clock += 5001;
                    return result;
                }

                public boolean authorized(ApiV3SocketTicketService.Grant grant) throws Exception {
                    return core.authorized(grant);
                }
            }, (ip, record) -> allowed, () -> clock);
        }

        Transport open(Object key, String ip) {
            var out = new Transport();
            registry.open(key, out, ip);
            return out;
        }

        Transport authenticate(Object key, String actor, String parent) throws Exception {
            var out = open(key, "192.0.2." + (registry.size() + 1));
            registry.receive(key, new JSONObject().put("type", "authenticate").put("ticket", core.issue(actor, parent).ticket()).toString());
            return out;
        }

        Transport article(Object key, String articleId) throws Exception {
            var out = open(key, "192.0.2." + (registry.size() + 1));
            registry.receive(key, new JSONObject().put("type", "authenticate").put("ticket", core.issueArticle("123", "456", articleId).ticket()).toString());
            return out;
        }

        Transport messages(Object key, String actor, String parent) throws Exception {
            var out = open(key, "192.0.2." + (registry.size() + 1));
            registry.receive(key, new JSONObject().put("type", "authenticate").put("ticket", core.issueMessages(actor, parent).ticket()).toString());
            return out;
        }

        Transport chat(Object key) throws Exception {
            var out = open(key, "192.0.2." + (registry.size() + 1));
            registry.receive(key, new JSONObject().put("type", "authenticate").put("ticket", core.issueChat("123", "456").ticket()).toString());
            return out;
        }
    }

    private static void verifyChatReactionSelection() {
        final var registry = new ApiV3RealtimeSessions(new ApiV3RealtimeSessions.Authority() {
            public ApiV3SocketTicketService.Grant consume(String ticket) {
                return new ApiV3SocketTicketService.Grant(ticket.startsWith("A") ? "123" : "321", "123", 10_000, "chat", "");
            }

            public boolean authorized(ApiV3SocketTicketService.Grant grant) {
                return true;
            }
        }, (ip, record) -> true, () -> 0);
        final var own = new Transport();
        final var other = new Transport();
        registry.open("own", own, "192.0.2.1");
        registry.open("other", other, "192.0.2.2");
        registry.receive("own", new JSONObject().put("type", "authenticate").put("ticket", "A".repeat(43)).toString());
        registry.receive("other", new JSONObject().put("type", "authenticate").put("ticket", "B".repeat(43)).toString());
        final var reaction = new JSONObject().put("value", "heart").put("count", 1).put("selected", false);
        final var snapshot = new JSONObject().put("id", "789").put("reactions",
                new JSONObject().put("currentUserReaction", "").put("summary", new org.json.JSONArray().put(reaction)));
        registry.chatChanged("reactions", "789", 1, snapshot, Map.of("123", "heart"));
        final var ownState = own.sent.getLast().getJSONObject("data").getJSONObject("reactions");
        final var otherState = other.sent.getLast().getJSONObject("data").getJSONObject("reactions");
        Assert.assertEquals(ownState.getString("currentUserReaction"), "heart");
        Assert.assertTrue(ownState.getJSONArray("summary").getJSONObject(0).getBoolean("selected"));
        Assert.assertEquals(otherState.getString("currentUserReaction"), "");
        Assert.assertFalse(otherState.getJSONArray("summary").getJSONObject(0).getBoolean("selected"));
        Assert.assertFalse(other.sent.getLast().getJSONObject("data").has("message"));
        Assert.assertFalse(reaction.getBoolean("selected"));
        registry.closeChat();
        System.out.println("PASS 两个聊天室连接只收到各自的表态选择");
    }

    public static void main(String[] args) throws Exception {
        verifyChatReactionSelection();
        var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        for (String key : List.of("latkeProps", "localProps")) {
            var field = Latkes.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, props);
        }
        try (var f = new Live()) {
            var a = f.authenticate("a", "123", "456");
            var b = f.authenticate("b", "321", "654");
            var pending = f.open("pending", "192.0.2.99");
            Assert.assertEquals(a.sent.get(0).getString("type"), "authentication.required");
            Assert.assertEquals(a.sent.get(1).getString("type"), "ready");
            Assert.assertEquals(a.sent.get(1).getJSONObject("data").keySet(), Set.of("channel", "expiresAt"));

            // 同时保留其他账号和未认证连接，检查通知只发给当前授权的目标。
            f.registry.notificationsChanged("123");
            Assert.assertEquals(a.sent.size(), 3);
            Assert.assertEquals(a.sent.get(2).keySet(), Set.of("type", "data"));
            Assert.assertEquals(a.sent.get(2).getJSONObject("data").length(), 0);
            Assert.assertEquals(b.sent.size(), 2);
            Assert.assertEquals(pending.sent.size(), 1);
            f.registry.receive("a", "{\"type\":\"ping\"}");
            Assert.assertEquals(a.sent.getLast().getString("type"), "pong");
            write("UPDATE symphony_api_v3_token SET state=2 WHERE oId='456'");
            f.registry.notificationsChanged("123");
            Assert.assertTrue(a.closed);
            Assert.assertFalse(b.closed);
            Assert.assertEquals(f.registry.size(), 2);
        }
        System.out.println("PASS challenge-first authentication, owner-only whitelist invalidation, ping and parent revocation before delivery with real grant state");
        for (String bad : List.of("{}", "{\"type\":\"authenticate\",\"ticket\":\"bad\"}", "{\"type\":\"authenticate\",\"apiKey\":\"PRIVATE\"}", "{\"type\":\"ping\"}", "{\"type\":\"authenticate\",\"type\":\"authenticate\",\"ticket\":\"x\"}", "x".repeat(1025), "中".repeat(400)))
            try (var f = new Live()) {
                var out = f.open("a", "192.0.2.1");
                f.registry.receive("a", bad);
                Assert.assertTrue(out.closed);
                Assert.assertEquals(f.consumes, 0);
                Assert.assertEquals(f.registry.size(), 0);
                Assert.assertEquals(out.sent.size(), 1);
            }
        for (String bad : List.of("{\"type\":\"authenticate\",\"ticket\":\"x\"}", "{\"type\":\"ping\",\"userId\":\"321\"}", "{\"type\":\"send\",\"content\":\"x\"}"))
            try (var f = new Live()) {
                var out = f.authenticate("a", "123", "456");
                f.registry.receive("a", bad);
                Assert.assertTrue(out.closed);
                Assert.assertEquals(out.sent.size(), 2);
            }
        System.out.println("PASS strict first/control-frame whitelist, duplicate JSON and byte/length limits reject without consuming credentials or exposing user data");
        try (var f = new Live()) {
            var out = f.open("a", "192.0.2.1");
            f.clock += 5000;
            f.registry.sweep();
            Assert.assertTrue(out.closed);
            Assert.assertEquals(f.consumes, 0);
        }
        try (var f = new Live()) {
            f.slowConsume = true;
            var out = f.authenticate("a", "123", "456");
            Assert.assertTrue(out.closed);
            Assert.assertEquals(out.sent.size(), 1);
            Assert.assertEquals(f.registry.size(), 0);
        }
        try (var f = new Live()) {
            var out = f.authenticate("a", "123", "456");
            write("UPDATE symphony_user SET userStatus=4 WHERE oId='123'");
            f.clock += 15000;
            f.registry.tick();
            Assert.assertTrue(out.closed);
        }
        try (var f = new Live()) {
            var out = f.authenticate("a", "123", "456");
            f.clock = 125000;
            f.registry.sweep();
            Assert.assertTrue(out.closed);
        }
        System.out.println("PASS unauthenticated timeout, slow authentication deadline, periodic current authorization and original token expiry close stale connections");
        try (var f = new Live()) {
            var a = f.authenticate("a", "123", "456");
            f.allowed = false;
            f.registry.notificationsChanged("123");
            Assert.assertTrue(a.closed);
            var denied = f.open("b", "192.0.2.2");
            Assert.assertTrue(denied.closed);
            Assert.assertEquals(denied.sent.size(), 0);
        }
        try (var f = new Live()) {
            var a = f.authenticate("a", "123", "456");
            a.failSend = true;
            f.registry.notificationsChanged("123");
            Assert.assertTrue(a.closed);
            Assert.assertEquals(f.registry.size(), 0);
        }
        System.out.println("PASS connection/message/event IP guard and transport backpressure failures close without an authorization fallback");
        try (var f = new Live()) {
            for (int i = 0; i < 32; i++)
                Assert.assertFalse(f.open(i, "192.0.2.1").closed);
            Assert.assertTrue(f.open(33, "192.0.2.1").closed);
            f.registry.close(0);
            Assert.assertFalse(f.open(34, "192.0.2.1").closed);
            Assert.assertEquals(f.registry.size(), 32);
        }
        try (var f = new Live()) {
            for (int i = 0; i < 8; i++)
                Assert.assertFalse(f.authenticate(i, "123", "456").closed);
            Assert.assertTrue(f.authenticate(9, "123", "456").closed);
            f.registry.close(0);
            Assert.assertFalse(f.authenticate(10, "123", "456").closed);
            Assert.assertEquals(f.registry.size(), 8);
        }
        try (var f = new Live()) {
            var out = f.authenticate("a", "123", "456");
            for (int i = 0; i < 60; i++)
                f.registry.receive("a", "{\"type\":\"ping\"}");
            Assert.assertTrue(out.closed);
            Assert.assertEquals(f.registry.size(), 0);
        }
        System.out.println("PASS per-IP/per-user connection and message budgets release capacity on close and bound abuse");
        try (var f = new Live()) {
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");
            var article = f.article("article", "789");
            var other = f.article("other", "987");
            var notifications = f.authenticate("notifications", "321", "654");
            Assert.assertEquals(article.sent.get(1).getJSONObject("data").keySet(), Set.of("channel", "expiresAt", "articleId"));
            Assert.assertEquals(article.sent.get(1).getJSONObject("data").getString("channel"), "article");
            Assert.assertEquals(article.sent.get(1).getJSONObject("data").getString("articleId"), "789");
            Assert.assertEquals(f.registry.owners(), Set.of("321"));
            Assert.assertEquals(f.registry.articleIds(), Set.of("789", "987"));
            f.registry.notificationsChanged("123");
            Assert.assertEquals(article.sent.size(), 2);
            Assert.assertEquals(other.sent.size(), 2);
            f.registry.articleChanged("789");
            Assert.assertEquals(article.sent.size(), 3);
            Assert.assertEquals(other.sent.size(), 2);
            Assert.assertEquals(notifications.sent.size(), 2);
            var event = article.sent.getLast();
            Assert.assertEquals(event.keySet(), Set.of("type", "data"));
            Assert.assertEquals(event.getString("type"), "article.changed");
            Assert.assertEquals(event.getJSONObject("data").toString(), new JSONObject().put("articleId", "789").toString());
            Assert.assertFalse(event.toString().contains("PRIVATE"));
            write("UPDATE symphony_article SET articleType=?,articleContent='@SomeoneElse only' WHERE oId='789'", org.b3log.symphony.model.Article.ARTICLE_TYPE_C_DISCUSSION);
            f.registry.articleChanged("789");
            Assert.assertTrue(article.closed);
            Assert.assertEquals(article.sent.size(), 3);
            Assert.assertFalse(other.closed);
            Assert.assertEquals(f.registry.articleIds(), Set.of("987"));
            Assert.assertEquals(f.registry.owners(), Set.of("321"));
            f.registry.closeAll();
            Assert.assertTrue(other.closed);
            Assert.assertTrue(notifications.closed);
            Assert.assertTrue(f.registry.articleIds().isEmpty());
            Assert.assertTrue(f.registry.owners().isEmpty());
        }
        System.out.println("PASS article-bound registry isolates targets/scopes, emits only an article ID and closes on current invitation loss while releasing indexes");
        try (var f = new Live()) {
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");
            var article = f.article("article", "789");
            f.fail = "target.select";
            f.registry.articleChanged("789");
            Assert.assertTrue(article.closed);
            Assert.assertEquals(article.sent.size(), 2);
            Assert.assertTrue(f.registry.articleIds().isEmpty());
            Assert.assertEquals(f.registry.size(), 0);
        }
        System.out.println("PASS article authorization source failure closes before emitting a change and releases the resource subscription");
        try (var f = new Live()) {
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");
            var chat = f.chat("chat");
            var article = f.article("article", "789");
            var notifications = f.authenticate("notifications", "321", "654");
            Assert.assertTrue(f.registry.hasChat());
            Assert.assertEquals(f.registry.chatUserIds(), Set.of("123"));
            var duplicate = f.chat("duplicate");
            var pending = f.open("pending-online", "192.0.2.99");
            Assert.assertEquals(f.registry.chatUserIds(), Set.of("123"));
            Assert.assertEquals(chat.sent.get(1).getJSONObject("data").keySet(), Set.of("channel", "expiresAt"));
            Assert.assertEquals(chat.sent.get(1).getJSONObject("data").getString("channel"), "chat");
            Assert.assertEquals(f.registry.owners(), Set.of("321"));
            Assert.assertEquals(f.registry.articleIds(), Set.of("789"));
            f.registry.chatSyncRequired();
            Assert.assertEquals(chat.sent.getLast().getString("type"), "chat.sync_required");
            Assert.assertEquals(chat.sent.getLast().getJSONObject("data").length(), 0);
            var stored = new JSONObject().put("userOId", "123").put("userName", "sender").put("userNickname", "昵称")
                    .put("userAvatarURL", "https://fishpi.cn/avatar.png").put("content", "hello").put("userPhone", "PRIVATE")
                    .put("appearance", new JSONObject().put("version", 1).put("bubbleId", "candy").put("frameId", "gold"));
            var snapshot = org.b3log.symphony.util.ApiV3ChatMessageDto.from(
                    new JSONObject().put("oId", "789").put("content", stored.toString()), text -> "<p>" + text + "</p>");
            f.registry.chatChanged("created", "789", 1, snapshot);
            var event = chat.sent.getLast();
            Assert.assertEquals(event.getString("type"), "chat.message.created");
            var data = event.getJSONObject("data");
            Assert.assertEquals(data.keySet(), Set.of("messageId", "revision", "message"));
            Assert.assertEquals(data.getString("messageId"), "789");
            Assert.assertEquals(data.getString("revision"), "1");
            Assert.assertTrue(data.getJSONObject("message").similar(snapshot));
            Assert.assertEquals(data.getJSONObject("message").getJSONObject("author").getString("userName"), "sender");
            Assert.assertEquals(data.getJSONObject("message").getString("contentHtml"), "<p>hello</p>");
            Assert.assertEquals(data.getJSONObject("message").getJSONObject("appearance").getString("frameId"), "gold");
            Assert.assertFalse(event.toString().contains("PRIVATE"));
            Assert.assertTrue(duplicate.sent.getLast().similar(event));
            Assert.assertEquals(pending.sent.size(), 1);

            f.registry.chatChanged("deleted", "789", 2, snapshot);
            Assert.assertEquals(chat.sent.getLast().getJSONObject("data").keySet(), Set.of("messageId", "revision"));
            f.registry.chatChanged("updated", "987", 3, snapshot);
            Assert.assertFalse(chat.sent.getLast().getJSONObject("data").has("message"));
            f.registry.chatChanged("updated", "789", 4, new JSONObject(snapshot.toString()).put("content", "鱼".repeat(100_000)));
            Assert.assertFalse(chat.sent.getLast().getJSONObject("data").has("message"));
            Assert.assertEquals(article.sent.size(), 2);
            Assert.assertEquals(notifications.sent.size(), 2);
            int before = chat.sent.size();
            f.registry.notificationsChanged("123");
            f.registry.articleChanged("789");
            Assert.assertEquals(chat.sent.size(), before);
            f.registry.closeChat();
            Assert.assertTrue(chat.closed);
            Assert.assertFalse(article.closed);
            Assert.assertFalse(notifications.closed);
            Assert.assertFalse(f.registry.hasChat());
            var replacement = f.chat("replacement");
            write("UPDATE symphony_api_v3_token SET state=2 WHERE oId='456'");
            f.registry.chatChanged("created", "789", 5, snapshot);
            Assert.assertEquals(replacement.sent.size(), 2);
            Assert.assertTrue(f.registry.chatUserIds().isEmpty());
            Assert.assertTrue(replacement.closed);
            Assert.assertFalse(f.registry.hasChat());
        }
        try (var f = new Live()) {
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");
            var chat = f.chat("barrage-chat");
            var article = f.article("barrage-article", "789");
            var notifications = f.authenticate("barrage-notifications", "321", "654");
            var pending = f.open("barrage-pending", "192.0.2.99");
            var barrage = new ApiV3BarrageEvent("00000000-0000-4000-8000-000000000001", "<img> literal 😀", "#FFFFFF", 1000, "123", "PublicName", "<b>Nickname</b>", "javascript:private");
            f.registry.chatBarrage(barrage);
            var frame = chat.sent.getLast();
            Assert.assertEquals(frame.keySet(), Set.of("type", "data"));
            Assert.assertEquals(frame.getString("type"), "chat.barrager");
            Assert.assertEquals(frame.getJSONObject("data").toString(), barrage.dto().toString());
            Assert.assertEquals(frame.getJSONObject("data").getJSONObject("author").getString("userAvatarURL"), "");
            Assert.assertEquals(article.sent.size(), 2);
            Assert.assertEquals(notifications.sent.size(), 2);
            Assert.assertEquals(pending.sent.size(), 1);
            var copy = barrage.dto();
            copy.getJSONObject("author").put("userPhone", "PRIVATE");
            Assert.assertFalse(barrage.dto().toString().contains("PRIVATE"));
            int before = chat.sent.size();
            write("UPDATE symphony_api_v3_token SET state=2 WHERE oId='456'");
            f.registry.chatBarrage(barrage);
            Assert.assertTrue(chat.closed);
            Assert.assertEquals(chat.sent.size(), before);
        }
        System.out.println("PASS transient barrage whitelist reaches only currently authorized chat subscribers, isolates other channels, sanitizes avatars and closes revoked peers before sending");
        System.out.println("PASS chat registry isolates notification/article scopes, sends ID-only changes and empty sync hints, rechecks authority and releases only chat connections");
        try (var f = new Live()) {
            write("UPDATE symphony_api_v3_token SET scope='messages.read notifications.read content.read' WHERE oId='456'");
            write("UPDATE symphony_api_v3_token SET scope='messages.read notifications.read' WHERE oId='654'");
            var a = f.messages("private-a", "123", "456");
            var a2 = f.messages("private-a2", "123", "456");
            var b = f.messages("private-b", "321", "654");
            var notifications = f.authenticate("notice", "123", "456");
            var article = f.article("article", "789");
            var chat = f.chat("chat");
            var pending = f.open("pending-private", "192.0.2.99");
            Assert.assertEquals(f.registry.messageOwners(), Set.of("123", "321"));
            Assert.assertEquals(f.registry.owners(), Set.of("123"));
            Assert.assertEquals(a.sent.getLast().getJSONObject("data").keySet(), Set.of("channel", "expiresAt"));
            // 同一所有者的两个私信连接都应收到空载荷提示，其他用户和频道不收。
            f.registry.messagesChanged("123");
            Assert.assertEquals(a.sent.size(), 3);
            Assert.assertEquals(a2.sent.size(), 3);
            var hint = a.sent.getLast();
            Assert.assertEquals(hint.keySet(), Set.of("type", "data"));
            Assert.assertEquals(hint.getString("type"), "messages.changed");
            Assert.assertTrue(hint.getJSONObject("data").isEmpty());
            for (var other : List.of(b, notifications, article, chat))
                Assert.assertEquals(other.sent.size(), 2);
            Assert.assertEquals(pending.sent.size(), 1);
            f.registry.notificationsChanged("123");
            Assert.assertEquals(a.sent.size(), 3);
            Assert.assertEquals(notifications.sent.size(), 3);

            // 撤去私信权限后只清理该用户的私信连接，保留通知和其他用户连接。
            write("UPDATE symphony_api_v3_token SET scope='notifications.read content.read' WHERE oId='456'");
            f.registry.messagesChanged("123");
            Assert.assertTrue(a.closed);
            Assert.assertTrue(a2.closed);
            Assert.assertFalse(b.closed);
            Assert.assertFalse(notifications.closed);
            Assert.assertEquals(f.registry.messageOwners(), Set.of("321"));
            b.failSend = true;
            f.registry.messagesChanged("321");
            Assert.assertTrue(b.closed);
            Assert.assertTrue(f.registry.messageOwners().isEmpty());
        }
        System.out.println("PASS private invalidations reach all authorized sockets of only the affected owner, contain no message/peer/unread data, isolate other channels and remove indexes on scope loss or transport failure");

    }
}
