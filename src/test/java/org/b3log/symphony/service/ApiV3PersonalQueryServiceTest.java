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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.http.handler.RouteHandler;
import org.b3log.latke.model.Pagination;
import org.b3log.symphony.model.ArticleDraft;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ApiV3PersonalProcessor;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class ApiV3PersonalQueryServiceTest {
    @Test
    public void draftSummaryDoesNotContainBodiesOrOwnerMetadata() {
        final JSONObject raw = new JSONObject().put(Keys.OBJECT_ID, "1").put(ArticleDraft.USER_ID, "private-owner")
                .put(ArticleDraft.CONTENT, "private-content").put(ArticleDraft.THOUGHT_CONTENT, "private-thought")
                .put(ArticleDraft.REWARD_CONTENT, "private-reward").put("apiKey", "private-key");
        final JSONObject dto = ApiV3PersonalQueryService.draftDto(raw, false);
        Assert.assertFalse(dto.has("content"));
        Assert.assertFalse(dto.has("thoughtContent"));
        Assert.assertFalse(dto.has("rewardContent"));
        Assert.assertFalse(dto.toString().contains("private-"));
    }

    @Test
    public void ownedDraftDetailPreservesThoughtAndEditorContentSeparately() {
        final JSONObject raw = new JSONObject().put(Keys.OBJECT_ID, "1").put(ArticleDraft.USER_ID, "private-owner")
                .put(ArticleDraft.CONTENT, "editor-content").put(ArticleDraft.THOUGHT_CONTENT, "thought-records")
                .put(ArticleDraft.REWARD_CONTENT, "reward-content");
        final JSONObject dto = ApiV3PersonalQueryService.draftDto(raw, true);
        Assert.assertEquals(dto.getString("content"), "editor-content");
        Assert.assertEquals(dto.getString("thoughtContent"), "thought-records");
        Assert.assertEquals(dto.getString("rewardContent"), "reward-content");
        Assert.assertFalse(dto.toString().contains("private-owner"));
        Assert.assertFalse(dto.has(ArticleDraft.USER_ID));
    }

    @Test
    public void memberPointsRespectPrivacyAndProjectOnlyTheTargetLedger() throws Exception {
        final var settings = new Properties();
        settings.setProperty("runtimeDatabase", "NONE");
        settings.setProperty("contextPath", "");
        Latkes.setLatkeProps(settings);
        Latkes.setLocalProps(settings);
        final var query = new ApiV3PersonalQueryService();
        final var target = new JSONObject().put("oId", "10").put(UserExt.USER_POINT_STATUS, 0);
        final int[] reads = {0};
        final boolean[] closeDuringRead = {false};
        ApiV3SecurityTest.inject(query, "userRepository", new UserRepository() {
            @Override
            public List<JSONObject> select(String sql, Object... args) {
                Assert.assertTrue(sql.startsWith("SELECT oId,userPointStatus"));
                return "Alice".equals(args[0]) ? List.of(new JSONObject(target.toString())) : List.of();
            }
        });
        ApiV3SecurityTest.inject(query, "pointtransferQueryService", new PointtransferQueryService() {
            @Override
            public JSONObject getUserPoints(String userId, int page, int size, boolean includeDescription) {
                Assert.assertEquals(userId, "10");
                Assert.assertEquals(page, 2);
                Assert.assertEquals(size, 20);
                Assert.assertFalse(includeDescription);
                reads[0]++;
                if (closeDuringRead[0]) target.put(UserExt.USER_POINT_STATUS, 1);
                final var entry = new JSONObject().put(Pointtransfer.TIME, 1700000000000L).put(Pointtransfer.SUM, 64)
                        .put(Pointtransfer.TYPE, 59).put(Common.OPERATION, "-").put(Common.BALANCE, 120)
                        .put(Common.DISPLAY_TYPE, "宠物咖啡").put(Common.DESCRIPTION, "fixture description")
                        .put(Pointtransfer.MEMO, "fixture memo").put(Pointtransfer.TO_BALANCE, 900);
                return new JSONObject().put(Keys.RESULTS, new JSONArray().put(entry)).put(Pagination.PAGINATION_RECORD_COUNT, 21);
            }
        });

        final var ordinary = identity("11", "defaultRole", ApiV3TokenMgmtService.WEBSITE_CLIENT);
        final var owner = identity("10", "defaultRole", ApiV3TokenMgmtService.WEBSITE_CLIENT);
        final var admin = identity("12", Role.ROLE_ID_C_ADMIN, ApiV3TokenMgmtService.WEBSITE_CLIENT);
        final var op = identity("13", "opRole", ApiV3TokenMgmtService.WEBSITE_CLIENT);
        final var delegatedAdmin = identity("12", Role.ROLE_ID_C_ADMIN, "third-party");
        final var delegatedOwner = identity("10", "defaultRole", "third-party");

        final var publicPage = query.memberPoints("Alice", null, 2, 20);
        final var row = publicPage.getJSONArray("list").getJSONObject(0);
        Assert.assertEquals(row.keySet(), Set.of("time", "amount", "type", "operation", "balance", "displayType"));
        Assert.assertEquals(row.getInt("balance"), 120);
        Assert.assertEquals(publicPage.getJSONObject("pagination").getInt("total"), 21);
        Assert.assertTrue(publicPage.similar(query.memberPoints("Alice", ordinary, 2, 20)));
        Assert.assertTrue(publicPage.similar(query.memberPoints("Alice", delegatedAdmin, 2, 20)));

        target.put(UserExt.USER_POINT_STATUS, 1);
        final int before = reads[0];
        for (JSONObject identity : new JSONObject[]{null, ordinary, op, delegatedAdmin, delegatedOwner}) {
            final var denied = Assert.expectThrows(ApiV3Exception.class, () -> query.memberPoints("Alice", identity, 2, 20));
            Assert.assertEquals(denied.status, 403);
            Assert.assertEquals(denied.code, "point_history_private");
        }
        Assert.assertEquals(reads[0], before, "无权查看时不查询流水");
        Assert.assertTrue(publicPage.similar(query.memberPoints("Alice", owner, 2, 20)));
        Assert.assertTrue(publicPage.similar(query.memberPoints("Alice", admin, 2, 20)));

        final var processor = new ApiV3PersonalProcessor();
        ApiV3SecurityTest.inject(processor, "personal", query);
        final var auth = new ApiV3AuthMidware();
        final var readers = Map.of("ordinary", ordinary, "owner", owner, "admin", admin, "delegated", delegatedAdmin);
        ApiV3SecurityTest.inject(auth, "apiV3TokenMgmtService", new ApiV3TokenMgmtService() {
            @Override
            public JSONObject authenticate(String token) {
                final var actor = readers.get(token);
                return actor == null ? null : new JSONObject(actor.toString()).put("scope", "profile.read");
            }
        });
        ApiV3PersonalProcessor.registerMemberPoints(processor, auth, new AnonymousViewCheckMidware() {
            @Override
            public void handle(RequestContext context) { context.handle(); }
        });
        Dispatcher.mapping();
        Assert.assertEquals(memberPointStatus(processor, null), 403);
        Assert.assertEquals(memberPointStatus(processor, "ordinary"), 403);
        Assert.assertEquals(memberPointStatus(processor, "owner"), 200);
        Assert.assertEquals(memberPointStatus(processor, "admin"), 200);
        Assert.assertEquals(memberPointStatus(processor, "delegated"), 403);
        Assert.assertEquals(memberPointStatus(processor, "invalid"), 401);

        target.remove(UserExt.USER_POINT_STATUS);
        Assert.assertEquals(Assert.expectThrows(ApiV3Exception.class,
                () -> query.memberPoints("Alice", null, 2, 20)).status, 403);
        Assert.assertEquals(Assert.expectThrows(ApiV3Exception.class,
                () -> query.memberPoints("Missing", admin, 2, 20)).status, 404);
        target.put(UserExt.USER_POINT_STATUS, 0);
        closeDuringRead[0] = true;
        Assert.assertEquals(Assert.expectThrows(ApiV3Exception.class,
                () -> query.memberPoints("Alice", ordinary, 2, 20)).code, "point_history_private");
    }

    private static int memberPointStatus(ApiV3PersonalProcessor processor, String token) throws Exception {
        final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v3/users/Alice/points");
        try {
            if (token != null) raw.headers().set("Authorization", "Bearer " + token);
            final var request = new Request(null, raw);
            request.setParams(Map.of("p", "2", "size", "20"));
            final var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            final var context = new RequestContext(request, new Response(null, response));
            context.addHandler(new RouteHandler());
            context.addHandler(processor::memberPoints);
            context.handle();
            return response.status().code();
        } finally {
            raw.release();
        }
    }

    private static JSONObject identity(String id, String role, String client) {
        return new JSONObject().put("clientId", client).put("user",
                new JSONObject().put("oId", id).put(UserExt.USER_ROLE, role));
    }
}
