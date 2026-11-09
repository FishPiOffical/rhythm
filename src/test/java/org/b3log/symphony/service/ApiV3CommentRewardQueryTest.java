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
import org.b3log.symphony.model.Reward;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.RewardRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.*;
import java.util.*;

public class ApiV3CommentRewardQueryTest {
    interface Work {
        void run() throws Exception;
    }

    static void error(int status, Work work) throws Exception {
        try {
            work.run();
            Assert.fail();
        } catch (ApiV3Exception e) {
            Assert.assertEquals(e.status, status);
        }
    }

    static void inject(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    public static void main(String[] args) throws Exception {
        var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        try (var db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (var s = db.createStatement()) {
                s.execute("CREATE TABLE users(oId TEXT PRIMARY KEY,userStatus INT)");
                s.execute("INSERT INTO users VALUES('1',0),('2',0),('3',0),('4',4)");
                s.execute("CREATE TABLE comments(oId TEXT PRIMARY KEY,commentOnArticleId TEXT,commentAuthorId TEXT,commentStatus INT,commentVisible INT,commentAnonymous INT,commentThankCnt INT,commentQnAOffered INT)");
                s.execute("INSERT INTO comments VALUES('11','21','2',0,0,0,7,0),('12','21','2',0,1,1,0,0),('13','21','4',0,0,0,0,0),('14','21','2',1,0,0,0,0)");
                s.execute("CREATE TABLE rewards(oId TEXT PRIMARY KEY,senderId TEXT,dataId TEXT,type INT)");
                s.execute("INSERT INTO rewards VALUES('31','1','11'," + Reward.TYPE_C_COMMENT + "),('32','1','11'," + Reward.TYPE_C_COMMENT + "),('33','3','12'," + Reward.TYPE_C_ARTICLE + " )");
            }
            final var service = new ApiV3CommentRewardQueryService();
            final boolean[] failed = {false}, hidden = {false};
            final int[] offer = {100}, articleType = {5};
            inject(service, "users", new UserRepository() {
                public String getName() {
                    return "users";
                }
            });
            inject(service, "rewards", new RewardRepository() {
                public String getName() {
                    return "rewards";
                }
            });
            inject(service, "comments", new CommentRepository() {
                public String getName() {
                    return "comments";
                }

                public List<JSONObject> select(String sql, Object... values) throws RepositoryException {
                    if (failed[0]) throw new RepositoryException("fixture failure");
                    Assert.assertTrue(sql.startsWith("SELECT "));
                    try (var s = db.prepareStatement(sql)) {
                        for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
                        try (var r = s.executeQuery()) {
                            var list = new ArrayList<JSONObject>();
                            var m = r.getMetaData();
                            while (r.next()) {
                                var row = new JSONObject();
                                for (int i = 1; i <= m.getColumnCount(); i++)
                                    row.put(m.getColumnLabel(i), r.getObject(i));
                                list.add(row);
                            }
                            return list;
                        }
                    } catch (SQLException e) {
                        throw new RepositoryException(e);
                    }
                }
            });
            inject(service, "visibility", new ApiV3ContentQueryService() {
                public JSONObject viewableArticle(String id, JSONObject viewer) {
                    return hidden[0] ? null : new JSONObject().put("oId", id).put("articleAuthorId", "1")
                            .put("articleQnAOfferPoint", offer[0]).put("articleType", articleType[0]);
                }
            });
            final JSONObject owner = new JSONObject().put("oId", "1"), author = new JSONObject().put("oId", "2"), other = new JSONObject().put("oId", "3");
            final var thanks = service.read(owner, "11", false);
            Assert.assertEquals(thanks.keySet(), Set.of("id", "thanked", "canThank", "thankCount", "thankPoint"));
            Assert.assertTrue(thanks.getBoolean("thanked"));
            Assert.assertFalse(thanks.getBoolean("canThank"));
            Assert.assertEquals(thanks.getInt("thankCount"), 7);
            Assert.assertFalse(service.read(other, "11", false).getBoolean("thanked"));
            Assert.assertTrue(service.read(other, "11", false).getBoolean("canThank"));
            Assert.assertFalse(service.read(author, "11", false).getBoolean("canThank"));
            System.out.println("PASS comment rewards 1 exact actor/type EXISTS, duplicate rewards, self qualifications and whitelist");
            error(404, () -> service.read(other, "12", false));
            Assert.assertEquals(service.read(owner, "12", false).getInt("thankPoint"), 0);
            Assert.assertEquals(service.read(author, "12", true).getInt("offerPoint"), 0);
            hidden[0] = true;
            error(404, () -> service.read(owner, "11", false));
            hidden[0] = false;
            for (String id : List.of("13", "14", "99")) error(404, () -> service.read(owner, id, false));
            error(404, () -> service.read(new JSONObject().put("oId", "4"), "11", false));
            System.out.println("PASS comment rewards 2 private content gate and article visibility, anonymous zero fee, invalid actor/author/comment hidden");
            final var accept = service.read(owner, "11", true);
            Assert.assertEquals(accept.keySet(), Set.of("id", "accepted", "canAccept", "offerPoint"));
            Assert.assertTrue(accept.getBoolean("canAccept"));
            Assert.assertEquals(accept.getInt("offerPoint"), 100);
            Assert.assertFalse(service.read(other, "11", true).getBoolean("canAccept"));
            Assert.assertFalse(service.read(author, "11", true).getBoolean("canAccept"));
            for (int value : List.of(0, 19)) {
                offer[0] = value;
                Assert.assertFalse(service.read(owner, "11", true).getBoolean("canAccept"));
                Assert.assertFalse(service.read(owner, "12", true).getBoolean("canAccept"));
            }
            offer[0] = 20;
            Assert.assertTrue(service.read(owner, "11", true).getBoolean("canAccept"));
            Assert.assertTrue(service.read(owner, "12", true).getBoolean("canAccept"));
            Assert.assertEquals(service.read(owner, "12", true).getInt("offerPoint"), 0);
            offer[0] = 100;
            articleType[0] = 0;
            Assert.assertFalse(service.read(owner, "11", true).getBoolean("canAccept"));
            articleType[0] = 5;

            // 已采纳的匿名回答也会使其他回答不可采纳。
            try (var s = db.createStatement()) {
                s.execute("UPDATE comments SET commentQnAOffered=1 WHERE oId='12'");
            }
            Assert.assertFalse(service.read(owner, "11", true).getBoolean("canAccept"));
            Assert.assertTrue(service.read(owner, "12", true).getBoolean("accepted"));
            System.out.println("PASS comment rewards 3 current article owner/target status, anonymous award fee and other accepted candidate without disclosing its ID");
            error(401, () -> service.read(null, "11", false));
            for (String id : List.of("0", "1 OR 1=1", "11/12", "99999999999999999999"))
                error(422, () -> service.read(owner, id, false));
            failed[0] = true;
            try {
                service.read(owner, "11", false);
                Assert.fail();
            } catch (RepositoryException expected) {
            }
            failed[0] = false;
            System.out.println("PASS comment rewards 4 strict IDs and database failure propagated, never false success");
            var endpoint = new org.b3log.symphony.processor.ApiV3CommentRewardProcessor();
            inject(endpoint, "query", service);
            var auth = new org.b3log.symphony.processor.middleware.ApiV3AuthMidware();
            inject(auth, "apiV3TokenMgmtService", new ApiV3TokenMgmtService() {
                public JSONObject authenticate(String token) {
                    return List.of("owner", "wrong").contains(token)
                            ? new JSONObject().put("user", owner).put("scope", token.equals("owner") ? "content.read" : "comments.write")
                            : null;
                }
            });
            org.b3log.symphony.processor.ApiV3CommentRewardProcessor.registerRoutes(endpoint, auth);
            org.b3log.latke.http.Dispatcher.mapping();
            for (String kind : List.of("thanks", "accept"))
                for (String token : List.of("", "wrong", "owner")) {
                    var raw = new io.netty.handler.codec.http.DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                            io.netty.handler.codec.http.HttpMethod.GET, "/api/v3/comments/11/" + kind);
                    try {
                        raw.headers().set("Cookie", "legacy");
                        if (!token.isEmpty()) raw.headers().set("Authorization", "Bearer " + token);
                        var request = new org.b3log.latke.http.Request(null, raw);
                        request.setBytes(new byte[0]);
                        request.setParams(new HashMap<>(Map.of("apiKey", "legacy")));
                        var c = new org.b3log.latke.http.RequestContext(request, new org.b3log.latke.http.Response(null,
                                new io.netty.handler.codec.http.DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                                        io.netty.handler.codec.http.HttpResponseStatus.OK)));
                        c.addHandler(new org.b3log.latke.http.handler.RouteHandler());
                        c.addHandler(kind.equals("thanks") ? endpoint::thanks : endpoint::accept);
                        c.handle();
                        Assert.assertEquals(c.attr("status"), token.isEmpty() ? 401 : token.equals("wrong") ? 403 : 200);
                        if (token.equals("owner")) {
                            Assert.assertEquals(c.getResponse().getHeader("Cache-Control"), "no-store");
                            Assert.assertEquals(c.getResponse().getHeader("Vary"), "Authorization");
                        }
                    } finally {
                        raw.release();
                    }
                }
            try (var s = db.createStatement().executeQuery("SELECT COUNT(*) FROM rewards")) {
                Assert.assertTrue(s.next());
                Assert.assertEquals(s.getInt(1), 3);
            }
            System.out.println("PASS comment rewards 5 actual Dispatcher/auth/controller two GETs require Bearer/content.read, no-store and leave rewards unchanged");
        }
    }
}
