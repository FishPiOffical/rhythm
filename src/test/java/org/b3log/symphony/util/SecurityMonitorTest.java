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
import org.b3log.latke.Keys;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.symphony.processor.SkinRenderer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Path;
import freemarker.template.Configuration;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;

@Test(singleThreaded = true, timeOut = 60_000)
public class SecurityMonitorTest {
    private static final String IP = "192.0.2.240";
    private Properties oldProps;
    private Properties oldLocal;

    private static Field field(final Class<?> owner, final String name) throws Exception {
        final Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    @BeforeMethod
    public void prepare() throws Exception {
        oldProps = (Properties) field(Latkes.class, "latkeProps").get(null);
        oldLocal = (Properties) field(Latkes.class, "localProps").get(null);
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        ((Deque<?>) field(SecurityMonitor.class, "OBSERVATIONS").get(null)).clear();
        ((Deque<?>) field(SecurityMonitor.class, "EVENTS").get(null)).clear();
        SecurityMonitor.clearRequest();
        field(SecurityMonitor.class, "discardedObservations").setLong(null, 0L);
        Sessions.clearThreadLocalData();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() throws Exception {
        ((Deque<?>) field(SecurityMonitor.class, "OBSERVATIONS").get(null)).clear();
        ((Deque<?>) field(SecurityMonitor.class, "EVENTS").get(null)).clear();
        SecurityMonitor.clearRequest();
        ((Cache<?, ?>) field(AnonymousCaptchaShield.class, "ipBlacklistCache").get(null)).invalidateAll();
        Sessions.clearThreadLocalData();
        Latkes.setLatkeProps(oldProps);
        Latkes.setLocalProps(oldLocal);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> observations() {
        return (List<Map<String, Object>>) SecurityMonitor.snapshot().get("observations");
    }

    private static Map<String, Object> latest() { return observations().getFirst(); }

    private static void challenge() {
        SecurityMonitor.record(IP, "GET", "/", "test", "captcha", "challenge", "首次访问");
    }

    private static RequestContext context(final String uri) {
        final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri, Unpooled.EMPTY_BUFFER);
        final Request request = new Request(null, raw) {
            @Override public String getRemoteAddr() { return IP; }
        };
        request.setParams(new HashMap<>());
        return new RequestContext(request,
                new Response(null, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
    }

    public void monitorPollingKeepsStatisticsWithoutDisplacingRecentVisits() throws Exception {
        SecurityMonitor.record(IP, "GET", "/article/123", "test", "cc", "allow", "阈值内");
        final long before = ((Number) SecurityMonitor.snapshot().get("total")).longValue();
        for (int i = 0; i < 110; i++) {
            SecurityMonitor.record(IP, "GET", "/admin/security/monitor", "test", "cc", "allow", "阈值内");
        }
        Assert.assertEquals(((Number) SecurityMonitor.snapshot().get("total")).longValue(), before + 110);
        Assert.assertEquals(SecurityMonitor.recentEvents().size(), 1);
        Assert.assertEquals(SecurityMonitor.recentEvents().getFirst().get("uri"), "/article/123");
        Assert.assertNull(((ThreadLocal<?>) field(SecurityMonitor.class, "CURRENT_EVENT").get(null)).get());

        SecurityMonitor.record(IP, "POST", "/admin/security/monitor", "test", "cc", "allow", "阈值内");
        SecurityMonitor.record(IP, "GET", "/admin/security/self-check", "test", "cc", "allow", "阈值内");
        Assert.assertEquals(SecurityMonitor.recentEvents().size(), 3);
    }

    public void monitorPollingStillRecordsRecoveryFailures() {
        challenge();
        SecurityMonitor.captchaValidated(IP, true, true);
        SecurityMonitor.record(IP, "GET", "/admin/security/monitor", "test", "cc", "deny", "已封禁");
        Assert.assertEquals(latest().get("failure"), "验证码已通过，仍被 CC 拦截");
        Assert.assertEquals(SecurityMonitor.recentEvents().size(), 1);
    }

    public void actualArrivalAndRecoveryAreSeparateFromRedirect() {
        challenge();
        Assert.assertEquals(latest().get("state"), "待到达");
        SecurityMonitor.challengePageReached(IP);
        Assert.assertEquals(latest().get("state"), "已到达");
        SecurityMonitor.pageRendered(context("/test"), "captcha.ftl", true);
        Assert.assertEquals(latest().get("state"), "待验证");
        SecurityMonitor.captchaValidated(IP, true, true);
        Assert.assertEquals(latest().get("state"), "待恢复");
        SecurityMonitor.pageRendered(context("/admin/misc"), "admin/misc.ftl", true);
        Assert.assertEquals(latest().get("state"), "待恢复");
        final var adminFailure = context("/admin/misc");
        adminFailure.attr(RequestContext.ERROR_CODE, 403);
        SecurityMonitor.requestFinished(adminFailure);
        Assert.assertEquals(latest().get("state"), "待恢复");
        SecurityMonitor.pageRendered(context("/article/123"), "article.ftl", true);
        Assert.assertEquals(latest().get("state"), "已恢复");
        challenge();
        Assert.assertEquals(observations().size(), 2);
        Assert.assertEquals(latest().get("state"), "待到达");
        Assert.assertEquals(observations().get(1).get("state"), "已恢复");
    }

    public void failuresRemainVisibleAfterRecovery() {
        challenge();
        SecurityMonitor.challengePageReached(IP);
        SecurityMonitor.pageRendered(context("/test"), "captcha.ftl", false);
        Assert.assertEquals(latest().get("failure"), "验证页服务端渲染失败");
        SecurityMonitor.captchaValidated(IP, false, false);
        Assert.assertEquals(latest().get("state"), "未通过");
        SecurityMonitor.captchaValidated(IP, true, false);
        SecurityMonitor.record(IP, "GET", "/", "test", "cc", "deny", "已封禁");
        Assert.assertEquals(latest().get("state"), "异常");
        SecurityMonitor.pageRendered(context("/"), "index.ftl", true);
        Assert.assertEquals(latest().get("state"), "已恢复");
        Assert.assertEquals(latest().get("failure"), "验证页服务端渲染失败");
    }

    @SuppressWarnings("unchecked")
    public void restrictionsAndErrorPagesNeverCountAsRecovery() throws Exception {
        challenge();
        SecurityMonitor.captchaValidated(IP, true, true);
        ((Cache<String, Boolean>) field(AnonymousCaptchaShield.class, "ipBlacklistCache").get(null)).put(IP, true);
        SecurityMonitor.pageRendered(context("/"), "index.ftl", true);
        Assert.assertEquals(latest().get("state"), "待恢复");
        SecurityMonitor.record(IP, "GET", "/", "test", "captcha", "redirect", "黑名单跳转");
        Assert.assertEquals(latest().get("state"), "异常");
        final RequestContext error = context("/article/missing");
        error.attr(RequestContext.ERROR_CODE, 404);
        SecurityMonitor.requestFinished(error);
        Assert.assertEquals(latest().get("detail"), "验证通过后请求处理失败");
    }

    public void ipsetExitAndMembershipMustBothMatch() {
        final AtomicInteger calls = new AtomicInteger();
        Firewall.runIpset("add", IP, (operation, ip) -> {
            calls.incrementAndGet();
            return new Firewall.CommandResult(0, false, "");
        });
        Assert.assertEquals(calls.get(), 2);
        Assert.assertEquals(latest().get("state"), "已核实");
        Firewall.runIpset("del", IP, (operation, ip) -> new Firewall.CommandResult(
                operation.equals("test") ? 1 : 0, false, "Element " + IP + " is NOT in set fishpi.\n"));
        Assert.assertEquals(latest().get("state"), "已核实");
        for (String failure : List.of("permission", "missing", "timeout", "wrong-member", "command-failed")) {
            Firewall.runIpset("del", IP, (operation, ip) -> new Firewall.CommandResult(
                    failure.equals("command-failed") || operation.equals("test") ? 1 : 0,
                    failure.equals("timeout"), failure));
            Assert.assertEquals(latest().get("state"), "执行异常", failure);
        }
        Firewall.runIpset("add", IP, (operation, ip) -> { throw new java.io.IOException("private material"); });
        Assert.assertEquals(latest().get("state"), "执行异常");
        Assert.assertFalse(latest().toString().contains("private material"));
    }

    public void observationsAreBoundedAndExpireWithoutTraffic() throws Exception {
        for (int i = 0; i < 205; i++) SecurityMonitor.ipsetResult("192.0.2." + i, "封禁", false, "诊断");
        Assert.assertEquals(observations().size(), 200);
        Assert.assertEquals(SecurityMonitor.snapshot().get("observationDiscarded"), 5L);
        final Deque<?> rows = (Deque<?>) field(SecurityMonitor.class, "OBSERVATIONS").get(null);
        final Object oldest = rows.peekLast();
        field(oldest.getClass(), "started").setLong(oldest, System.currentTimeMillis() - 7_200_001L);
        Assert.assertEquals(observations().size(), 199);
        Assert.assertEquals(SecurityMonitor.snapshot().get("observationDiscarded"), 6L);
    }

    public void bothObservationTablesRenderEscapedValuesAndEmptyState() throws Exception {
        final var cfg = new Configuration(Configuration.VERSION_2_3_30);
        CrawlerMonitor.record(new SearchEngines.Verification(SearchEngines.Engine.GOOGLE, false, "测试校验", "", "", 123L, false), "192.0.2.241", "/<script>unsafe</script>");
        for (String skin : List.of("pc", "mobile")) {
            final String source = java.nio.file.Files.readString(Path.of(
                    "src/main/resources/skins/classic", skin, "admin/misc.ftl"));
            final int start = source.indexOf("            <#if currentUser.userName == \"adlered\">");
            final int end = source.indexOf("            <div class=\"security-self-check\"", start);
            final var template = new freemarker.template.Template("observations",
                    new java.io.StringReader(source.substring(start, end)), cfg);
            SecurityMonitor.ipsetResult("<img src=x>", "封禁", false, "<script>unsafe</script>");
            final var output = new java.io.StringWriter();
            template.process(Map.of("currentUser", Map.of("userName", "adlered"),
                    "securityStats", SecurityMonitor.snapshot(), "crawlerStats", CrawlerMonitor.snapshot(null)), output);
            Assert.assertTrue(output.toString().contains("&lt;script&gt;"));
            Assert.assertFalse(output.toString().contains("<script>unsafe"));
            final var hidden = new java.io.StringWriter();
            template.process(Map.of("currentUser", Map.of("userName", "other-admin"),
                    "securityStats", SecurityMonitor.snapshot()), hidden);
            Assert.assertFalse(hidden.toString().contains("链路观测"));
            Assert.assertFalse(hidden.toString().contains("爬虫访问"));
            final var empty = new java.io.StringWriter();
            template.process(Map.of("currentUser", Map.of("userName", "adlered"),
                    "securityStats", Map.of("observations", List.of(), "observationDiscarded", 0),
                    "crawlerStats", CrawlerMonitor.snapshot(null)), empty);
            Assert.assertTrue(empty.toString().contains("暂无观测"));
        }
    }

    public void realSkinRenderingAndBothAdminTemplatesParse() throws Exception {
        final Configuration cfg = (Configuration) field(Templates.class, "TEMPLATE_CFG").get(null);
        final var loader = cfg.getTemplateLoader();
        try {
            final var fixture = new StringTemplateLoader();
            for (String skin : List.of("pc", "mobile")) {
                fixture.putTemplate("classic/" + skin + "/fixture-business.ftl", "<html>业务页面</html>");
            }
            cfg.setTemplateLoader(new MultiTemplateLoader(new TemplateLoader[]{fixture,
                    new FileTemplateLoader(Path.of("src/main/resources/skins").toFile())}));
            for (String skin : List.of("pc", "mobile")) {
                cfg.getTemplate("classic/" + skin + "/admin/misc.ftl");
                Sessions.setTemplateDir("classic/" + skin);
                Sessions.setMobile(skin.equals("mobile"));
                challenge();
                SecurityMonitor.challengePageReached(IP);
                final var page = context("/test");
                page.attr(Keys.HttpRequest.START_TIME_MILLIS, System.currentTimeMillis());
                new SkinRenderer(page, "captcha.ftl").render(page);
                Assert.assertEquals(latest().get("state"), "待验证");
                Assert.assertTrue(page.getResponse().getString().contains("访客"));
                SecurityMonitor.captchaValidated(IP, true, true);
                final var missing = context("/after-pass");
                new SkinRenderer(missing, "missing-fixture.ftl").render(missing);
                Assert.assertEquals(latest().get("state"), "异常");
                Assert.assertFalse(latest().get("state").equals("已恢复"));
                final var recovered = context("/article/123");
                recovered.attr(Keys.HttpRequest.START_TIME_MILLIS, System.currentTimeMillis());
                new SkinRenderer(recovered, "fixture-business.ftl").render(recovered);
                Assert.assertEquals(latest().get("state"), "已恢复");
                Assert.assertEquals(latest().get("failure"), "验证通过后页面处理失败");
            }
        } finally {
            cfg.setTemplateLoader(loader);
            cfg.clearTemplateCache();
        }
    }
}
