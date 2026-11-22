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
import com.sun.net.httpserver.HttpServer;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.Keys;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ApiV3Processor;
import org.b3log.symphony.processor.AdminProcessor;
import org.b3log.symphony.processor.BeforeRequestHandler;
import org.b3log.symphony.processor.CaptchaProcessor;
import org.b3log.symphony.processor.SkinRenderer;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.b3log.symphony.service.OptionQueryService;
import org.b3log.symphony.service.UserIpLeaseService;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Real shield/middleware/Latke pipeline. Only persistence and the external proof provider are isolated. */
@Test(singleThreaded = true, timeOut = 60_000)
public class AnonymousCaptchaShieldTest {
    private static final String IP = "198.51.100.211";
    private static final String UA = "Mozilla/5.0";
    private final AnonymousViewCheckMidware middleware = new AnonymousViewCheckMidware();

    private static Object field(final Class<?> owner, final String name) throws Exception {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static <V> Cache<String, V> cache(final String name) throws Exception {
        return (Cache<String, V>) field(AnonymousCaptchaShield.class, name);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> whitelist() throws Exception {
        return (Set<String>) field(AnonymousCaptchaShield.class, "whiteList");
    }

    private static void inject(final Object target, final String name, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeMethod
    public void prepare() throws Exception {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);
        reset();
        inject(middleware, "optionQueryService", new OptionQueryService() {
            @Override public JSONObject getOption(final String key) {
                return new JSONObject().put("optionValue", "0");
            }
        });
        inject(middleware, "articleRepository", new ArticleRepository() {
            @Override public JSONObject get(final String id) {
                if ("missing".equals(id)) return null;
                return new JSONObject().put(Article.ARTICLE_ANONYMOUS_VIEW,
                        "private".equals(id) ? Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW
                                : Article.ARTICLE_ANONYMOUS_VIEW_C_ALLOW);
            }
        });
    }

    @AfterMethod(alwaysRun = true)
    public void reset() throws Exception {
        for (final String name : List.of("ipVisitCountCache", "ipV3VisitCountCache", "ipBlacklistCache",
                "ipFirstVisitTimeCache", "ipLastCaptchaPassTimeCache")) cache(name).invalidateAll();
        whitelist().clear();
        AnonymousCaptchaShield.setEnabled(true);
        AnonymousCaptchaShield.setFirstVisitCaptchaEnabled(true);
        Firewall.setEnabled(true);
        Sessions.clearThreadLocalData();
        SecurityMonitor.clearRequest();
    }

    private static final class Exchange implements AutoCloseable {
        final DefaultFullHttpRequest raw;
        final RequestContext context;
        final Response response;
        int status = 200;
        boolean reached;

        Exchange(final String path) { this(path, "GET", null, UA); }

        Exchange(final String path, final String method, final JSONObject body, final String ua) {
            raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), path,
                    Unpooled.copiedBuffer(body == null ? "" : body.toString(), StandardCharsets.UTF_8));
            if (ua != null) raw.headers().set("User-Agent", ua);
            raw.headers().set("Content-Type", "application/json");
            final Request request = new Request(null, raw) {
                @Override public String getRemoteAddr() { return IP; }
            };
            request.setParams(new HashMap<>());
            response = new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)) {
                @Override public void setStatus(final int value) { status = value; super.setStatus(value); }
                @Override public void sendRedirect(final String uri) { setStatus(302); setHeader("Location", uri); }
                @Override public void sendError(final int value) { setStatus(value); }
                @Override public void send() { }
            };
            context = new RequestContext(request, response);
        }

        void run(final org.b3log.latke.http.function.Handler first) {
            context.addHandler(first);
            context.addHandler(c -> reached = true);
            context.handle();
        }

        @Override public void close() { raw.release(); }
    }

    private void allowed(final String path) {
        try (final Exchange exchange = new Exchange(path)) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 200);
            Assert.assertTrue(exchange.reached);
        }
    }

    @Test
    public void freshLegacyApiTrafficDoesNotConsumeFirstPageVisitAndRemainsSubjectToChallenge() throws Exception {
        for (int index = 0; index < 21; index++) allowed("/api/repeater/next");
        Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
        Assert.assertNull(cache("ipV3VisitCountCache").getIfPresent(IP));
        Assert.assertNull(cache("ipFirstVisitTimeCache").getIfPresent(IP));
        Assert.assertFalse(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
        try (final Exchange exchange = new Exchange("/")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.response.getHeader("Location"), "/test");
            Assert.assertFalse(exchange.reached);
            Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
            Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 1);
        }
        Firewall.setEnabled(false);
        for (int index = 0; index < 2; index++) {
            try (final Exchange exchange = new Exchange("/api/repeater/next")) {
                exchange.run(new BeforeRequestHandler());
                Assert.assertEquals(exchange.status, 302);
                Assert.assertEquals(exchange.response.getHeader("Location"), "/test");
                Assert.assertFalse(exchange.reached);
            }
        }
        Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 1);
        Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
        try (final ProofProvider provider = new ProofProvider();
             final Exchange exchange = new Exchange("/validateCaptcha", "POST", null, UA)) {
            exchange.context.getRequest().setJSON(new JSONObject().put("captcha", provider.proof()));
            new CaptchaProcessor().validateCaptcha(exchange.context);
            Assert.assertEquals(((JsonRenderer) exchange.context.getRenderer()).getJSONObject().getInt("code"), StatusCodes.SUCC);
        }
        try (final Exchange exchange = new Exchange("/api/repeater/next")) {
            Assert.assertFalse(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false));
            exchange.run(middleware::handle);
            Assert.assertTrue(exchange.reached);
        }
        Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
    }

    @Test
    public void tenthLegacyVisitChallengesEvenAfterRecentPass() throws Exception {
        AnonymousCaptchaShield.setFirstVisitCaptchaEnabled(false);
        for (int index = 0; index < 9; index++) allowed("/shield-test-page");
        try (final Exchange exchange = new Exchange("/shield-test-page")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.response.getHeader("Location"), "/test");
            Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 10);
        }
        reset();
        cache("ipLastCaptchaPassTimeCache").put(IP, System.currentTimeMillis());
        for (int index = 0; index < 9; index++) allowed("/shield-test-page");
        try (final Exchange exchange = new Exchange("/shield-test-page")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 302);
        }
    }

    @Test
    public void legacyApiNeverConsumesTheTenthPageVisit() throws Exception {
        cache("ipLastCaptchaPassTimeCache").put(IP, System.currentTimeMillis());
        for (int index = 0; index < 9; index++) {
            allowed("/");
            allowed("/api/repeater/next");
        }
        allowed("/api/repeater/next");
        Assert.assertFalse(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
        Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 9);
        try (final Exchange page = new Exchange("/")) {
            page.run(middleware::handle);
            Assert.assertEquals(page.status, 302);
            Assert.assertEquals(page.response.getHeader("Location"), "/test");
            Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 10);
        }
    }

    @Test
    public void legacyApiExclusionRequiresExactPathPrefix() throws Exception {
        for (final String path : List.of("/page/api/example", "/apiary/example")) {
            reset();
            try (final Exchange exchange = new Exchange(path)) {
                exchange.run(middleware::handle);
                Assert.assertEquals(exchange.status, 302);
                Assert.assertEquals(exchange.response.getHeader("Location"), "/test");
            }
        }
    }

    @Test
    public void v3UsesIndependentThirtyRequestInterval() throws Exception {
        try (final Exchange exchange = new Exchange("/api/v3/articles")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 403);
        }
        try (final ProofProvider provider = new ProofProvider();
             final Exchange exchange = new Exchange("/validateCaptcha", "POST", null, UA)) {
            exchange.context.getRequest().setJSON(new JSONObject().put("captcha", provider.proof()));
            new CaptchaProcessor().validateCaptcha(exchange.context);
            Assert.assertEquals(((JsonRenderer) exchange.context.getRenderer()).getJSONObject().getInt("code"), StatusCodes.SUCC);
        }
        Assert.assertNull(cache("ipV3VisitCountCache").getIfPresent(IP));
        for (int index = 0; index < 9; index++) allowed("/shield-test-page");
        for (int i = 0; i < 29; i++) allowed("/api/v3/articles");
        Assert.assertEquals(cache("ipV3VisitCountCache").getIfPresent(IP), 29);
        Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 9);
        try (final Exchange exchange = new Exchange("/api/v3/articles")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 403);
            Assert.assertEquals(cache("ipV3VisitCountCache").getIfPresent(IP), 30);
            Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
        }
        try (final Exchange exchange = new Exchange("/shield-test-page")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 302);
            Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 10);
            Assert.assertEquals(cache("ipV3VisitCountCache").getIfPresent(IP), 30);
        }
    }

    @Test
    public void articleFirstVisitAndPublicInformationExemptionsRemain() throws Exception {
        allowed("/article/123");
        Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 1);
        reset();
        for (final String path : List.of("/about", "/download", "/privacy", "/agreement")) allowed(path);
        Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
        try (final Exchange exchange = new Exchange("/article/random/1")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 302);
        }
    }

    @Test
    public void leasedIpNeverEntersPermanentWhitelist() throws Exception {
        final JSONObject user = new JSONObject().put(Keys.OBJECT_ID, "lease-fixture")
                .put(User.USER_NAME, "fixture-owner")
                .put(User.USER_ROLE, Role.ROLE_ID_C_DEFAULT).put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
        try {
            Sessions.setUser(user);
            allowed("/shield-test-page");
            Sessions.clearThreadLocalData();
            allowed("/shield-test-page");
            Assert.assertFalse(whitelist().contains(IP));
            Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
            SecurityMonitor.record(IP, "GET", "/shield-test-page", "fixture", "cc", "allow", "阈值内");
            Assert.assertEquals(SecurityMonitor.recentEvents().getFirst().get("userStatus"), "访客+有效租约IP");
            final var event = SecurityMonitor.recentEvents(true).getFirst();
            Assert.assertEquals(event.get("ip"), IP);
            Assert.assertEquals(event.get("userStatus"), "访客+有效租约IP（匹配账号：fixture-owner）");
            SecurityMonitor.markAuthenticated(user);
            Assert.assertEquals(SecurityMonitor.recentEvents(true).getFirst().get("userStatus"), "本人登录（用户：fixture-owner）");
            moveLease(user);
            try (final Exchange exchange = new Exchange("/shield-test-page")) {
                exchange.run(middleware::handle);
                Assert.assertEquals(exchange.response.getHeader("Location"), "/test");
            }
        } finally {
            moveLease(user);
        }
    }

    private static void moveLease(final JSONObject user) {
        try (final Exchange exchange = new Exchange("/shield-test-page")) {
            exchange.raw.headers().set("X-Waf-Ip", "198.51.100.212");
            UserIpLeaseService.renew(user, exchange.context);
        }
    }

    @Test
    public void verifiedCrawlerExemptionUsesVerifierResultNotOnlyUa() throws Exception {
        @SuppressWarnings("unchecked") final Cache<String, SearchEngines.Verification> verified =
                (Cache<String, SearchEngines.Verification>) field(SearchEngines.class, "VERIFIED_CRAWLERS");
        verified.put("google:" + IP, new SearchEngines.Verification(SearchEngines.Engine.GOOGLE, true, "双向 DNS 校验通过", "crawl.googlebot.com", IP, 123L, false));
        try (final Exchange exchange = new Exchange("/", "GET", null, "Googlebot")) {
            exchange.run(middleware::handle);
            Assert.assertTrue(exchange.reached);
            Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
        } finally { verified.invalidate("google:" + IP); }
        @SuppressWarnings("unchecked") final Cache<String, SearchEngines.Verification> rejected =
                (Cache<String, SearchEngines.Verification>) field(SearchEngines.class, "REJECTED_CRAWLERS");
        rejected.put("google:" + IP, new SearchEngines.Verification(SearchEngines.Engine.GOOGLE, false, "反向解析无记录", "", "", 123L, false));
        try (final Exchange exchange = new Exchange("/", "GET", null, "Googlebot")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 302);
        } finally { rejected.invalidate("google:" + IP); }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void newSearchRolesReuseVerifiedExemptionWithoutWhitelistingOrdinaryTraffic() throws Exception {
        final Cache<String, SearchEngines.Verification> verified =
                (Cache<String, SearchEngines.Verification>) field(SearchEngines.class, "VERIFIED_CRAWLERS");
        for (final var role : Map.of(SearchEngines.Engine.OAI_SEARCH, "OAI-SearchBot",
                SearchEngines.Engine.CLAUDE_SEARCH, "Claude-SearchBot",
                SearchEngines.Engine.PERPLEXITY, "PerplexityBot", SearchEngines.Engine.APPLE, "Applebot",
                SearchEngines.Engine.MISTRAL_INDEX, "MistralAI-Index").entrySet()) {
            final String key = role.getKey().key() + ':' + IP;
            // 隔离来源校验结果，验证真实中间件不因角色扩充而把 IP 加入永久白名单。
            verified.put(key, new SearchEngines.Verification(role.getKey(), true, "测试校验", "", "", 123L, false));
            try {
                try (final Exchange exchange = new Exchange("/", "GET", null, role.getValue() + "/1.0")) {
                    exchange.run(middleware::handle);
                    Assert.assertTrue(exchange.reached, role.getValue());
                    Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
                    Assert.assertFalse(whitelist().contains(IP));
                }
                try (final Exchange exchange = new Exchange("/", "GET", null, UA)) {
                    exchange.run(middleware::handle);
                    Assert.assertEquals(exchange.status, 302);
                }
            } finally {
                verified.invalidate(key);
                reset();
            }
        }
    }

    @Test
    public void globalBlacklistPrecedesBusinessAndAllowsOnlyCorsNegotiation() throws Exception {
        cache("ipBlacklistCache").put(IP, true);
        Firewall.setEnabled(false);
        try (final Exchange exchange = new Exchange("/api/v3/me", "OPTIONS", null, UA)) {
            exchange.raw.headers().set("Origin", "https://example.invalid");
            exchange.run(new BeforeRequestHandler());
            Assert.assertEquals(exchange.status, 204);
            Assert.assertEquals(exchange.response.getHeader("Access-Control-Allow-Origin"), "*");
            Assert.assertNull(exchange.response.getHeader("Access-Control-Allow-Credentials"));
            Assert.assertFalse(exchange.reached);
        }
        try (final Exchange exchange = new Exchange("/api/v3/me")) {
            exchange.raw.headers().set("Origin", "https://example.invalid");
            exchange.raw.headers().set("Authorization", "Bearer test-only");
            exchange.run(new BeforeRequestHandler());
            Assert.assertEquals(exchange.status, 403);
            Assert.assertEquals(((JsonRenderer) exchange.context.getRenderer()).getJSONObject().getString("code"), "captcha_required");
            Assert.assertEquals(exchange.response.getHeader("Access-Control-Allow-Origin"), "*");
            Assert.assertNull(exchange.response.getHeader("Access-Control-Allow-Credentials"));
            Assert.assertFalse(exchange.reached);
        }
        try (final Exchange exchange = new Exchange("/validateCaptcha", "OPTIONS", null, UA)) {
            exchange.raw.headers().set("Origin", "https://example.invalid");
            exchange.run(new BeforeRequestHandler());
            Assert.assertEquals(exchange.status, 204);
            Assert.assertEquals(exchange.response.getHeader("Access-Control-Allow-Origin"), "*");
            Assert.assertNull(exchange.response.getHeader("Access-Control-Allow-Credentials"));
            Assert.assertTrue(AnonymousCaptchaShield.isRestricted(IP));
            Assert.assertFalse(exchange.reached);
        }
        try (final Exchange exchange = new Exchange("/api/v3/me", "OPTIONS", null, null)) {
            exchange.raw.headers().set("Origin", "https://example.invalid");
            exchange.run(new BeforeRequestHandler());
            Assert.assertEquals(exchange.status, 418);
            Assert.assertNull(exchange.response.getHeader("Access-Control-Allow-Origin"));
            Assert.assertFalse(exchange.reached);
        }
        Firewall.setEnabled(true);
        final Cache<String, Boolean> banned = cacheForFirewall();
        final Boolean priorBan = banned.getIfPresent(IP);
        banned.put(IP, true);
        try (final Exchange exchange = new Exchange("/api/v3/me", "OPTIONS", null, UA)) {
            exchange.raw.headers().set("Origin", "https://example.invalid");
            exchange.run(new BeforeRequestHandler());
            Assert.assertEquals(exchange.status, 429);
            Assert.assertNull(exchange.response.getHeader("Access-Control-Allow-Origin"));
            Assert.assertFalse(exchange.reached);
        } finally {
            if (priorBan == null) banned.invalidate(IP); else banned.put(IP, priorBan);
        }
        for (final String path : List.of("/", "/unregistered-path", "/api/search")) {
            try (final Exchange exchange = new Exchange(path)) {
                Assert.assertTrue(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false));
                Assert.assertEquals(exchange.status, 302);
            }
        }
        for (final String path : List.of("/test", "/validateCaptcha")) {
            try (final Exchange exchange = new Exchange(path)) {
                Assert.assertFalse(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false));
            }
        }
        try (final Exchange exchange = new Exchange("/")) {
            Assert.assertFalse(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, true));
        }
    }

    @Test
    public void websiteRefreshReachesCredentialValidationWithShieldEnabled() throws Exception {
        final String refreshPath = "/api/v3/auth/refresh";
        final ApiV3Processor processor = new ApiV3Processor();
        inject(processor, "apiV3TokenMgmtService", new ApiV3TokenMgmtService());
        Firewall.setEnabled(false);
        for (final boolean pending : List.of(false, true)) {
            if (pending) cache("ipBlacklistCache").put(IP, true);
            try (final Exchange exchange = new Exchange(refreshPath, "POST", null, UA)) {
                exchange.raw.headers().set("Origin", Latkes.getServer());
                exchange.run(middleware::handle);
                Assert.assertTrue(exchange.reached);
                Assert.assertEquals(AnonymousCaptchaShield.isAwaitingCaptcha(IP), pending);
                Assert.assertNull(cache("ipV3VisitCountCache").getIfPresent(IP));
                Assert.assertFalse(whitelist().contains(IP));
            }
            if (pending) {
                try (final Exchange exchange = new Exchange(refreshPath, "POST", null, UA)) {
                    exchange.raw.headers().set("Origin", Latkes.getServer());
                    processor.refresh(exchange.context);
                    Assert.assertEquals(exchange.status, 401);
                    Assert.assertEquals(((JsonRenderer) exchange.context.getRenderer()).getJSONObject()
                            .getString("code"), "unauthorized");
                    Assert.assertEquals(AnonymousCaptchaShield.isAwaitingCaptcha(IP), pending);
                    Assert.assertNull(cache("ipV3VisitCountCache").getIfPresent(IP));
                    Assert.assertFalse(whitelist().contains(IP));
                }
            }
        }
        try (final Exchange exchange = new Exchange(refreshPath, "POST", null, UA)) {
            exchange.raw.headers().set("Origin", "https://example.invalid");
            exchange.run(middleware::handle);
            Assert.assertTrue(exchange.reached);
            processor.refresh(exchange.context);
            Assert.assertEquals(exchange.status, 403);
            Assert.assertEquals(((JsonRenderer) exchange.context.getRenderer()).getJSONObject()
                    .getString("code"), "forbidden");
        }
        for (final var route : Map.of(refreshPath + "/extra", "POST", "/api/v3/auth/session", "GET").entrySet()) {
            try (final Exchange exchange = new Exchange(route.getKey(), route.getValue(), null, UA)) {
                Assert.assertTrue(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false));
                Assert.assertEquals(exchange.status, 403);
                Assert.assertEquals(((JsonRenderer) exchange.context.getRenderer()).getJSONObject()
                        .getString("code"), "captcha_required");
                Assert.assertFalse(exchange.reached);
            }
        }
        Firewall.setEnabled(true);
        final Cache<String, Boolean> banned = cacheForFirewall();
        final Boolean priorBan = banned.getIfPresent(IP);
        banned.put(IP, true);
        try (final Exchange exchange = new Exchange(refreshPath, "POST", null, UA)) {
            exchange.run(new BeforeRequestHandler());
            Assert.assertEquals(exchange.status, 429);
            Assert.assertFalse(exchange.reached);
        } finally {
            if (priorBan == null) banned.invalidate(IP); else banned.put(IP, priorBan);
        }
    }

    @Test
    public void websiteSessionProbeBypassesCaptchaOnlyForPost() throws Exception {
        final String sessionPath = "/api/v3/auth/session";
        Firewall.setEnabled(false);
        for (final boolean pending : List.of(false, true)) {
            if (pending) {
                cache("ipBlacklistCache").put(IP, true);
                cache("ipV3VisitCountCache").put(IP, 29);
            }
            try (final Exchange exchange = new Exchange(sessionPath, "POST", null, UA)) {
                exchange.raw.headers().set("Origin", Latkes.getServer());
                Assert.assertFalse(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false));
                exchange.run(middleware::handle);
                Assert.assertTrue(exchange.reached);
                Assert.assertEquals(AnonymousCaptchaShield.isAwaitingCaptcha(IP), pending);
                Assert.assertEquals(cache("ipV3VisitCountCache").getIfPresent(IP), pending ? 29 : null);
                Assert.assertFalse(whitelist().contains(IP));
            }
            if (pending) {
                assertV3CaptchaRequired(sessionPath, "GET");
                assertV3CaptchaRequired(sessionPath + "/extra", "POST");
            }
        }
        Firewall.setEnabled(true);
        final Cache<String, Boolean> banned = cacheForFirewall();
        final Boolean priorBan = banned.getIfPresent(IP);
        banned.put(IP, true);
        try (final Exchange exchange = new Exchange(sessionPath, "POST", null, UA)) {
            exchange.run(new BeforeRequestHandler());
            Assert.assertEquals(exchange.status, 429);
            Assert.assertFalse(exchange.reached);
        } finally {
            if (priorBan == null) banned.invalidate(IP); else banned.put(IP, priorBan);
        }
    }

    @Test
    public void oauthExemptionIsExactAndLeavesCaptchaStateIntact() throws Exception {
        final var routes = Map.of("/api/v3/oauth/authorize", "GET", "/api/v3/oauth/consent", "POST",
                "/api/v3/oauth/token", "POST", "/api/v3/oauth/revoke", "POST");
        for (final boolean pending : List.of(false, true)) {
            if (pending) {
                cache("ipBlacklistCache").put(IP, true);
                cache("ipV3VisitCountCache").put(IP, 29);
            }
            for (final var route : routes.entrySet()) {
                final String path = route.getKey();
                final String method = route.getValue();
                try (final Exchange exchange = new Exchange(path, method, null, UA)) {
                    Assert.assertFalse(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false), path);
                    exchange.run(middleware::handle);
                    Assert.assertTrue(exchange.reached, path);
                }
                if (pending) {
                    final String wrongMethod = "GET".equals(method) ? "POST" : "GET";
                    assertV3CaptchaRequired(path, wrongMethod);
                    assertV3CaptchaRequired(path + "/extra", method);
                }
            }
            Assert.assertEquals(AnonymousCaptchaShield.isAwaitingCaptcha(IP), pending);
            Assert.assertEquals(cache("ipV3VisitCountCache").getIfPresent(IP), pending ? 29 : null);
            Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
            Assert.assertNull(cache("ipFirstVisitTimeCache").getIfPresent(IP));
            Assert.assertNull(cache("ipLastCaptchaPassTimeCache").getIfPresent(IP));
            Assert.assertFalse(whitelist().contains(IP));
        }
        assertV3CaptchaRequired("/api/v3/articles", "GET");
        assertV3CaptchaRequired("/api/v3/admin/oauth/clients", "GET");
    }

    @Test
    public void legacyOpenIdProtocolExemptionIsExactAndLeavesCaptchaStateIntact() throws Exception {
        final var routes = Map.of("/openid/login", "GET", "/openid/confirm", "POST",
                "/openid/verify", "POST", "/openid/token", "POST");
        for (final boolean pending : List.of(false, true)) {
            if (pending) {
                cache("ipBlacklistCache").put(IP, true);
                cache("ipV3VisitCountCache").put(IP, 29);
            }
            for (final var route : routes.entrySet()) {
                final String path = route.getKey();
                final String method = route.getValue();
                try (final Exchange exchange = new Exchange(path, method, null, UA)) {
                    Assert.assertFalse(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false), path);
                    exchange.run(middleware::handle);
                    Assert.assertTrue(exchange.reached, path);
                }
                if (pending) {
                    final String wrongMethod = "GET".equals(method) ? "POST" : "GET";
                    assertPageNotCaptchaExempt(path, wrongMethod);
                    assertPageNotCaptchaExempt(path + "/extra", method);
                }
            }
            Assert.assertEquals(AnonymousCaptchaShield.isAwaitingCaptcha(IP), pending);
            Assert.assertEquals(cache("ipV3VisitCountCache").getIfPresent(IP), pending ? 29 : null);
            Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
            Assert.assertNull(cache("ipFirstVisitTimeCache").getIfPresent(IP));
            Assert.assertNull(cache("ipLastCaptchaPassTimeCache").getIfPresent(IP));
            Assert.assertFalse(whitelist().contains(IP));
        }
        for (final String resource : List.of("profile", "detail", "points", "articles", "membership")) {
            final String path = "/openid/user/" + resource;
            try (final Exchange exchange = new Exchange(path)) {
                Assert.assertFalse(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false), path);
            }
            assertPageNotCaptchaExempt(path, "POST");
            assertPageNotCaptchaExempt(path + "/extra", "GET");
        }
        assertPageNotCaptchaExempt("/openid/user/unknown", "GET");
    }

    private static void assertPageNotCaptchaExempt(final String path, final String method) {
        try (final Exchange exchange = new Exchange(path, method, null, UA)) {
            Assert.assertTrue(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false), path);
            Assert.assertEquals(exchange.status, 302);
        }
    }

    private static void assertV3CaptchaRequired(final String path, final String method) {
        try (final Exchange exchange = new Exchange(path, method, null, UA)) {
            Assert.assertTrue(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false), path);
            Assert.assertEquals(exchange.status, 403);
            Assert.assertEquals(((JsonRenderer) exchange.context.getRenderer()).getJSONObject()
                    .getString("code"), "captcha_required");
        }
    }

    @Test
    public void oauthPreflightStillNegotiatesCorsAndFirewallStillDenies() throws Exception {
        final String endpoint = "/api/v3/oauth/token";
        cache("ipBlacklistCache").put(IP, true);
        Firewall.setEnabled(false);
        try (final Exchange exchange = new Exchange(endpoint, "OPTIONS", null, UA)) {
            exchange.raw.headers().set("Origin", "https://example.invalid");
            exchange.raw.headers().set("Access-Control-Request-Method", "POST");
            exchange.run(new BeforeRequestHandler());
            Assert.assertEquals(exchange.status, 204);
            Assert.assertEquals(exchange.response.getHeader("Access-Control-Allow-Origin"), "*");
            Assert.assertNull(exchange.response.getHeader("Access-Control-Allow-Credentials"));
            Assert.assertFalse(exchange.reached);
        }
        Firewall.setEnabled(true);
        final Cache<String, Boolean> banned = cacheForFirewall();
        final Boolean priorBan = banned.getIfPresent(IP);
        banned.put(IP, true);
        try (final Exchange exchange = new Exchange(endpoint, "POST", null, UA)) {
            exchange.run(new BeforeRequestHandler());
            Assert.assertEquals(exchange.status, 429);
            Assert.assertFalse(exchange.reached);
            Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
        } finally {
            if (priorBan == null) banned.invalidate(IP); else banned.put(IP, priorBan);
        }
    }

    @SuppressWarnings("unchecked")
    private static Cache<String, Boolean> cacheForFirewall() throws Exception {
        return (Cache<String, Boolean>) field(Firewall.class, "BANNED");
    }

    @Test
    public void disablingShieldDoesNotClearPendingChallengeOrReadingRules() throws Exception {
        cache("ipBlacklistCache").put(IP, true);
        AnonymousCaptchaShield.setEnabled(false);
        Assert.assertFalse(AnonymousCaptchaShield.isRestricted(IP));
        Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
        try (final Exchange exchange = new Exchange("/")) {
            Assert.assertFalse(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false));
        }
        allowed("/");
        try (final Exchange exchange = new Exchange("/member/Someone")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 401);
        }
        try (final Exchange exchange = new Exchange("/article/private")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 401);
        }
        try (final Exchange exchange = new Exchange("/article/missing")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 404);
        }
        AnonymousCaptchaShield.setEnabled(true);
        Assert.assertTrue(AnonymousCaptchaShield.isRestricted(IP));
    }

    @Test
    public void challengeControllerRetainsRawBlacklistPageBehavior() throws Exception {
        final CaptchaProcessor processor = new CaptchaProcessor();
        try (final Exchange exchange = new Exchange("/test")) {
            processor.captcha(exchange.context);
            Assert.assertEquals(exchange.response.getHeader("Location"), "/");
        }
        cache("ipBlacklistCache").put(IP, true);
        AnonymousCaptchaShield.setEnabled(false);
        try (final Exchange exchange = new Exchange("/test")) {
            processor.captcha(exchange.context);
            Assert.assertTrue(exchange.context.getRenderer() instanceof SkinRenderer);
            Assert.assertEquals(((SkinRenderer) exchange.context.getRenderer()).getTemplateName(), "captcha.ftl");
        }
    }

    @Test
    public void v3DoesNotInheritLegacyIdentityWhilePageSessionStillWorks() throws Exception {
        Sessions.setUser(new JSONObject().put("oId", "legacy-user"));
        Sessions.setLoggedIn(true);
        allowed("/shield-test-page");
        Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
        try (final Exchange exchange = new Exchange("/api/v3/columns")) {
            exchange.context.getRequest().setParameter("apiKey", "legacy");
            exchange.context.attr("user", new JSONObject().put("oId", "legacy-user"));
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 403);
        }
        try (final Exchange exchange = new Exchange("/api/v3/columns")) {
            exchange.context.attr(ApiV3Processor.IDENTITY, new JSONObject().put("user", new JSONObject().put("oId", "v3-user")));
            exchange.run(middleware::handle);
            Assert.assertTrue(exchange.reached);
            Assert.assertEquals(cache("ipV3VisitCountCache").getIfPresent(IP), 1);
        }
    }

    @Test
    public void v3MonitorUpdatesOnlyAfterBearerAndScopeSucceed() throws Exception {
        final String path = "/api/v3/columns";
        final var auth = new ApiV3AuthMidware();
        final var user = new JSONObject().put(User.USER_NAME, "bearer-fixture");
        final var identity = new JSONObject().put("user", user).put("scope", "profile.read");
        inject(auth, "apiV3TokenMgmtService", new ApiV3TokenMgmtService() {
            @Override public JSONObject authenticate(final String token) {
                return "fixture-token".equals(token) ? identity : null;
            }
        });
        Sessions.setUser(new JSONObject().put(User.USER_NAME, "legacy-fixture"));
        for (final String scope : List.of("profile.read", "content.read")) {
            identity.put("scope", scope);
            try (final Exchange exchange = new Exchange(path)) {
                SecurityMonitor.record(IP, "GET", path, "fixture", "cc", "allow", "阈值内");
                exchange.raw.headers().set("Authorization", "Bearer fixture-token");
                exchange.run(auth::optionalContent);
                final var event = SecurityMonitor.recentEvents(true).getFirst();
                if ("content.read".equals(scope)) {
                    Assert.assertTrue(exchange.reached);
                    Assert.assertEquals(event.get("userStatus"), "本人登录（用户：bearer-fixture）");
                } else {
                    Assert.assertEquals(exchange.status, 403);
                    Assert.assertTrue(event.get("userStatus").toString().startsWith("访客"));
                }
            }
        }
    }

    @Test
    public void v3ChallengeTextLogsMatchResponseRequestIdsAndOmitQuery() throws Exception {
        final var logger = (org.apache.logging.log4j.core.Logger) field(AnonymousCaptchaShield.class, "LOGGER");
        final var lines = new ArrayList<String>();
        final var capture = new AbstractAppender("shield-fixture", null, null, false, Property.EMPTY_ARRAY) {
            @Override public void append(final LogEvent event) { lines.add(event.getMessage().getFormattedMessage()); }
        };
        capture.start();
        logger.addAppender(capture);
        try {
            final String path = "/api/v3/articles?fixture=not-for-logs";
            try (final Exchange exchange = new Exchange(path)) {
                exchange.run(middleware::handle);
                Assert.assertEquals(exchange.status, 403);
                Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
                Assert.assertTrue(lines.getLast().contains("结果=验证码 原因=首次访问 次数=1 requestId="
                        + exchange.response.getHeader("X-Request-Id")));
            }
            try (final Exchange exchange = new Exchange(path)) {
                Assert.assertTrue(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false));
                Assert.assertEquals(exchange.status, 403);
                Assert.assertTrue(lines.getLast().contains("结果=等待验证码 原因=已在验证黑名单 次数=- requestId="
                        + exchange.response.getHeader("X-Request-Id")));
            }
            Assert.assertTrue(lines.stream().noneMatch(line -> line.contains("not-for-logs")));
        } finally {
            logger.removeAppender(capture);
            capture.stop();
        }
    }

    @Test
    public void v3FirstAndPeriodicChallengesPersistUntilProviderVerification() throws Exception {
        Firewall.setEnabled(false);
        try (final ProofProvider provider = new ProofProvider()) {
            for (final boolean periodic : List.of(false, true)) {
                reset();
                Firewall.setEnabled(false);
                if (periodic) {
                    AnonymousCaptchaShield.setFirstVisitCaptchaEnabled(false);
                    cache("ipV3VisitCountCache").put(IP, 29);
                }
                try (final Exchange exchange = new Exchange("/api/v3/articles")) {
                    exchange.run(middleware::handle);
                    Assert.assertEquals(exchange.status, 403);
                    Assert.assertEquals(((JsonRenderer) exchange.context.getRenderer()).getJSONObject().getString("code"), "captcha_required");
                    Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
                    Assert.assertFalse(exchange.reached);
                }
                final Object count = cache("ipV3VisitCountCache").getIfPresent(IP);
                for (int i = 0; i < 3; i++) {
                    try (final Exchange exchange = new Exchange("/api/v3/articles")) {
                        exchange.run(new BeforeRequestHandler());
                        Assert.assertEquals(exchange.status, 403);
                        Assert.assertFalse(exchange.reached);
                        Assert.assertEquals(cache("ipV3VisitCountCache").getIfPresent(IP), count);
                    }
                }
                provider.mode.set("rejected");
                try (final Exchange exchange = new Exchange("/validateCaptcha", "POST", null, UA)) {
                    Assert.assertFalse(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false));
                    exchange.context.getRequest().setJSON(new JSONObject().put("captcha", provider.proof()));
                    new CaptchaProcessor().validateCaptcha(exchange.context);
                    Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
                }
                provider.mode.set("success");
                try (final Exchange exchange = new Exchange("/validateCaptcha", "POST", null, UA)) {
                    exchange.context.getRequest().setJSON(new JSONObject().put("captcha", provider.proof()));
                    new CaptchaProcessor().validateCaptcha(exchange.context);
                }
                Assert.assertFalse(AnonymousCaptchaShield.isRestricted(IP));
                Assert.assertNull(cache("ipV3VisitCountCache").getIfPresent(IP));
                allowed("/api/v3/articles");
                Assert.assertEquals(cache("ipV3VisitCountCache").getIfPresent(IP), 1);
                try (final Exchange exchange = new Exchange("/")) {
                    Assert.assertFalse(AnonymousCaptchaShield.redirectIfChallenged(exchange.context, false));
                }
            }
        }
    }

    @Test
    public void captchaSuccessAndAdministratorUnbanHaveDistinctStateTransitions() throws Exception {
        try (final ProofProvider provider = new ProofProvider()) {
            cache("ipBlacklistCache").put(IP, true);
            cache("ipVisitCountCache").put(IP, 5);
            cache("ipV3VisitCountCache").put(IP, 30);
            cache("ipFirstVisitTimeCache").put(IP, 123L);
            final JSONObject body = new JSONObject().put("captcha", provider.proof());
            try (final Exchange exchange = new Exchange("/validateCaptcha", "POST", body, UA)) {
                final var parse = Request.class.getDeclaredMethod("parseForm");
                parse.setAccessible(true);
                parse.invoke(exchange.context.getRequest());
                new CaptchaProcessor().validateCaptcha(exchange.context);
                Assert.assertEquals(((JsonRenderer) exchange.context.getRenderer()).getJSONObject().getInt("code"), StatusCodes.SUCC);
            }
            Assert.assertFalse(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
            Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
            Assert.assertNull(cache("ipV3VisitCountCache").getIfPresent(IP));
            Assert.assertNotNull(cache("ipLastCaptchaPassTimeCache").getIfPresent(IP));
            Assert.assertEquals(cache("ipFirstVisitTimeCache").getIfPresent(IP), 123L);
            Assert.assertFalse(whitelist().contains(IP));
            cache("ipV3VisitCountCache").put(IP, 29);
            AnonymousCaptchaShield.administratorUnban(IP);
            Assert.assertTrue(whitelist().contains(IP));
            Assert.assertNull(cache("ipFirstVisitTimeCache").getIfPresent(IP));
            Assert.assertNull(cache("ipV3VisitCountCache").getIfPresent(IP));
            AnonymousCaptchaShield.removeWhitelist(IP);
            Assert.assertFalse(whitelist().contains(IP));
            Assert.assertNotNull(cache("ipLastCaptchaPassTimeCache").getIfPresent(IP));
            Assert.assertEquals(provider.received.get().get("sign_token"),
                    new HmacUtils(HmacAlgorithms.HMAC_SHA_256, "local-fixture-key").hmacHex("lot"));
            Assert.assertEquals(provider.received.get().get("method"), "POST");
            Assert.assertEquals(provider.received.get().get("query"), "captcha_id=local-fixture-id");
            Assert.assertEquals(provider.received.get().get("captcha_output"), "output");
            Assert.assertEquals(provider.received.get().get("pass_token"), "pass");
            Assert.assertEquals(provider.received.get().get("gen_time"), "123");
        }
    }

    @Test
    public void invalidProofAndProviderErrorsNeverClearBlacklist() throws Exception {
        try (final ProofProvider provider = new ProofProvider()) {
            cache("ipBlacklistCache").put(IP, true);
            cache("ipVisitCountCache").put(IP, 5);
            cache("ipV3VisitCountCache").put(IP, 100);
            for (final String mode : List.of("rejected", "malformed", "missing", "http-error")) {
                provider.mode.set(mode);
                Assert.assertFalse(AnonymousCaptchaShield.verifyProof(provider.proof().toString()));
                try (final Exchange exchange = new Exchange("/validateCaptcha")) {
                    exchange.context.getRequest().setJSON(new JSONObject().put("captcha", provider.proof()));
                    new CaptchaProcessor().validateCaptcha(exchange.context);
                    Assert.assertEquals(((JsonRenderer) exchange.context.getRenderer()).getJSONObject().getInt("code"), StatusCodes.ERR);
                }
                Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
                Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 5);
                Assert.assertEquals(cache("ipV3VisitCountCache").getIfPresent(IP), 100);
                Assert.assertNull(cache("ipLastCaptchaPassTimeCache").getIfPresent(IP));
            }
            Assert.assertFalse(AnonymousCaptchaShield.verifyProof("invalid JSON"));
            Assert.assertFalse(AnonymousCaptchaShield.verifyProof(null));
        }
    }

    @Test
    public void allowListDoesNotPrecedeCaptcha() throws Exception {
        Assert.assertTrue(List.of(Symphonys.ANONYMOUS_VIEW_SKIPS.split(",")).contains("/recent"));
        try (final Exchange exchange = new Exchange("/recent")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 302);
        }
    }

    @Test
    public void legacyPageAtThreeHundredStillChallengesWithoutFirewallBan() throws Exception {
        final String source = "192.0.2.144";
        AnonymousCaptchaShield.setFirstVisitCaptchaEnabled(false);
        cache("ipVisitCountCache").put(source, 299);
        @SuppressWarnings("unchecked") final Cache<String, Boolean> banned = (Cache<String, Boolean>) field(Firewall.class, "BANNED");
        Assert.assertNull(banned.getIfPresent(source));
        try (final Exchange exchange = new Exchange("/shield-test-page")) {
            exchange.raw.headers().set("X-Waf-Ip", source);
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 302);
            Assert.assertEquals(exchange.response.getHeader("Location"), "/test");
            Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(source), 300);
            Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(source));
            Assert.assertNull(banned.getIfPresent(source));
        } finally {
            banned.invalidate(source);
        }
    }

    @Test
    public void legacySecurityMonitorRequiresValidAdminSessionAndProductionSwitchesStayReadOnly() {
        final var processor = new AdminProcessor();
        final var session = new JSONObject().put(User.USER_NAME, "another-admin")
                .put(User.USER_ROLE, Role.ROLE_ID_C_OP).put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
        try (final Exchange exchange = new Exchange("/admin/security/monitor")) {
            processor.securityMonitor(exchange.context);
            Assert.assertEquals(exchange.status, 403);
        }
        Sessions.setUser(session);
        try (final Exchange exchange = new Exchange("/admin/security/monitor")) {
            processor.securityMonitor(exchange.context);
            Assert.assertEquals(exchange.status, 403);
        }
        session.put(User.USER_ROLE, Role.ROLE_ID_C_ADMIN);
        try (final Exchange exchange = new Exchange("/admin/security/monitor")) {
            processor.securityMonitor(exchange.context);
            Assert.assertEquals(exchange.status, 200);
            Assert.assertEquals(exchange.response.getHeader("Cache-Control"), "private, no-store");
        }
        try (final Exchange exchange = new Exchange("/admin/security/save", "POST", null, UA)) {
            processor.saveSecuritySwitches(exchange.context);
            Assert.assertEquals(exchange.status, 403);
        }
        session.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_INVALID);
        try (final Exchange exchange = new Exchange("/admin/security/self-check")) {
            processor.securitySelfCheck(exchange.context);
            Assert.assertEquals(exchange.status, 403);
        }
    }

    private static final class ProofProvider implements AutoCloseable {
        final HttpServer server;
        final AtomicReference<String> mode = new AtomicReference<>("success");
        final AtomicReference<Map<String, String>> received = new AtomicReference<>();
        final Properties cfg;
        final Map<String, String> original = new HashMap<>();

        ProofProvider() throws Exception {
            cfg = (Properties) field(Symphonys.class, "CFG");
            for (final String key : List.of("jiyan.id", "jiyan.key", "jiyan.domain")) original.put(key, cfg.getProperty(key));
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/validate", exchange -> {
                final Map<String, String> values = new HashMap<>();
                for (final String pair : new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
                    final String[] parts = pair.split("=", 2);
                    values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                            parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
                }
                values.put("query", exchange.getRequestURI().getRawQuery());
                values.put("method", exchange.getRequestMethod());
                received.set(values);
                final String body = switch (mode.get()) {
                    case "success" -> "{\"result\":\"success\"}";
                    case "malformed" -> "invalid JSON";
                    case "missing" -> "{}";
                    default -> "{\"result\":\"fail\"}";
                };
                final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders("http-error".equals(mode.get()) ? 503 : 200, bytes.length);
                try (final var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
            cfg.setProperty("jiyan.id", "local-fixture-id");
            cfg.setProperty("jiyan.key", "local-fixture-key");
            cfg.setProperty("jiyan.domain", "http://127.0.0.1:" + server.getAddress().getPort());
        }

        JSONObject proof() {
            return new JSONObject().put("lot_number", "lot").put("captcha_output", "output")
                    .put("pass_token", "pass").put("gen_time", "123");
        }

        @Override public void close() {
            server.stop(0);
            for (final var entry : original.entrySet()) {
                if (entry.getValue() == null) cfg.remove(entry.getKey()); else cfg.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}
