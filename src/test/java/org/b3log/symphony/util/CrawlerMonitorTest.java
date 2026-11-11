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

import com.github.benmanes.caffeine.cache.Cache;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.symphony.processor.BeforeRequestHandler;
import org.b3log.symphony.processor.AdminProcessor;
import org.b3log.latke.model.User;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Test(singleThreaded = true, timeOut = 60_000)
public class CrawlerMonitorTest {
    private static final String IP = "192.0.2.241";
    private Properties oldProps;
    private Properties oldLocal;

    private static Object field(final Class<?> owner, final String name) throws Exception {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    @BeforeMethod
    public void prepare() throws Exception {
        oldProps = (Properties) field(Latkes.class, "latkeProps");
        oldLocal = (Properties) field(Latkes.class, "localProps");
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        reset();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() throws Exception {
        reset();
        Latkes.setLatkeProps(oldProps);
        Latkes.setLocalProps(oldLocal);
    }

    private void reset() throws Exception {
        ((Map<?, ?>) field(CrawlerMonitor.class, "GROUPS")).clear();
        ((Deque<?>) field(SecurityMonitor.class, "EVENTS")).clear();
        ((Cache<?, ?>) field(SearchEngines.class, "VERIFIED_CRAWLERS")).invalidateAll();
        ((Cache<?, ?>) field(SearchEngines.class, "REJECTED_CRAWLERS")).invalidateAll();
        SecurityMonitor.clearRequest();
        Sessions.clearThreadLocalData();
        AccountCredentialVersions.endHttp();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(final Map<String, Object> snapshot, final String name) {
        return (List<Map<String, Object>>) snapshot.get(name);
    }

    public void separatesIdentityAndStripsQueryWithoutLosingTotals() {
        CrawlerMonitor.record(SearchEngines.Engine.GOOGLE, true, "/article/1?apiKey=private#fragment");
        CrawlerMonitor.record(SearchEngines.Engine.GOOGLE, true, "/article/1?other=2");
        CrawlerMonitor.record(SearchEngines.Engine.GOOGLE, false, "/article/2");
        CrawlerMonitor.record(null, false, "/ignored");
        final var result = CrawlerMonitor.snapshot("google-verified");
        Assert.assertEquals(rows(result, "groups").size(), 2);
        Assert.assertEquals(rows(result, "paths").getFirst().get("count"), 2L);
        Assert.assertEquals(rows(result, "paths").getFirst().get("path"), "/article/1");
        Assert.assertFalse(result.toString().contains("private"));
        Assert.assertTrue(rows(CrawlerMonitor.snapshot("invalid"), "paths").isEmpty());
        Assert.assertTrue(rows(CrawlerMonitor.snapshot(null), "paths").isEmpty());
        Assert.assertEquals(rows(CrawlerMonitor.snapshot("google-unverified"), "paths").size(), 1);
    }

    public void boundedPathsKeepExactTotalAndExposeOmissions() {
        for (int i = 0; i < 1005; i++) CrawlerMonitor.record(SearchEngines.Engine.BING, true, "/article/" + i);
        CrawlerMonitor.record(SearchEngines.Engine.BING, true, "/article/0");
        CrawlerMonitor.record(SearchEngines.Engine.BING, true, "/" + "x".repeat(2048));
        final var result = CrawlerMonitor.snapshot("bing-verified");
        final var group = rows(result, "groups").getFirst();
        Assert.assertEquals(group.get("count"), 1007L);
        Assert.assertEquals(group.get("omitted"), 6L);
        Assert.assertEquals(rows(result, "paths").size(), 1000);
        Assert.assertEquals(rows(result, "paths").stream().filter(row -> row.get("path").equals("/article/0"))
                .findFirst().orElseThrow().get("count"), 2L);
    }

    public void concurrentRequestsCountExactlyOnceEach() throws Exception {
        try (var pool = Executors.newFixedThreadPool(4)) {
            final List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) tasks.add(pool.submit(() -> {
                for (int j = 0; j < 100; j++) CrawlerMonitor.record(SearchEngines.Engine.BAIDU, true, "/");
            }));
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        final var result = CrawlerMonitor.snapshot("baidu-verified");
        Assert.assertEquals(rows(result, "groups").getFirst().get("count"), 400L);
        Assert.assertEquals(rows(result, "paths").getFirst().get("count"), 400L);
    }

    @SuppressWarnings("unchecked")
    public void entryRecordsVerifiedAndUnverifiedWithoutChangingDecision() throws Exception {
        final var verify = BeforeRequestHandler.class.getDeclaredMethod("fillBotAttrs", RequestContext.class);
        verify.setAccessible(true);
        final Cache<String, SearchEngines.Engine> verified =
                (Cache<String, SearchEngines.Engine>) field(SearchEngines.class, "VERIFIED_CRAWLERS");
        final Cache<String, Boolean> rejected = (Cache<String, Boolean>) field(SearchEngines.class, "REJECTED_CRAWLERS");
        verified.put("google:" + IP, SearchEngines.Engine.GOOGLE);
        Assert.assertEquals(verify.invoke(null, context("Googlebot")), true);
        Assert.assertTrue(Sessions.isBot());
        Assert.assertEquals(SecurityMonitor.recentEvents().getFirst().get("type"), "crawler");
        verified.invalidateAll();
        rejected.put("google:" + IP, true);
        Assert.assertEquals(verify.invoke(null, context("Googlebot")), false);
        Assert.assertFalse(Sessions.isBot());
        Assert.assertEquals(rows(CrawlerMonitor.snapshot(null), "groups").size(), 2);
    }

    public void missingUaIsStillRejectedAndNowVisible() {
        final var context = context(null);
        new BeforeRequestHandler().handle(context);
        Assert.assertEquals(context.attr("testStatus"), 418);
        Assert.assertEquals(SecurityMonitor.recentEvents().getFirst().get("reason"), "缺少UA");
    }

    public void crawlerDataRequiresExistingSecurityOperator() {
        CrawlerMonitor.record(SearchEngines.Engine.GOOGLE, false, "/private-admin-observation");
        final var processor = new AdminProcessor();
        for (final String name : List.of("", "other-admin")) {
            Sessions.setUser(name.isEmpty() ? null : new JSONObject().put(User.USER_NAME, name));
            final var denied = context("Mozilla/5.0");
            processor.securityMonitor(denied);
            Assert.assertEquals(denied.attr("testStatus"), 403);
            Assert.assertNull(denied.getRenderer());
        }
        Sessions.setUser(new JSONObject().put(User.USER_NAME, "adlered"));
        final var allowed = context("Mozilla/5.0");
        processor.securityMonitor(allowed);
        final var data = ((org.b3log.latke.http.renderer.JsonRenderer) allowed.getRenderer()).getJSONObject();
        Assert.assertTrue(data.getJSONObject("data").getJSONObject("crawlers").has("groups"));
    }

    private static RequestContext context(final String ua) {
        final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);
        if (ua != null) raw.headers().set("User-Agent", ua);
        final Request request = new Request(null, raw) {
            @Override public String getRemoteAddr() { return IP; }
        };
        request.setParams(new HashMap<>());
        final RequestContext[] holder = new RequestContext[1];
        final Response response = new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)) {
            @Override public void send() { }
            @Override public void sendError(final int status) { setStatus(status); }
            @Override public void setStatus(final int status) {
                holder[0].attr("testStatus", status);
                super.setStatus(status);
            }
        };
        final var context = new RequestContext(request, response);
        holder[0] = context;
        return context;
    }
}
