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
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.symphony.cache.UserCache;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.processor.ApiV3Processor;
import org.b3log.symphony.processor.BeforeRequestHandler;
import org.b3log.symphony.processor.CaptchaProcessor;
import org.b3log.symphony.processor.SkinRenderer;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.service.OptionQueryService;
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
        for (final String name : List.of("ipVisitCountCache", "ipBlacklistCache",
                "ipFirstVisitTimeCache", "ipLastCaptchaPassTimeCache")) cache(name).invalidateAll();
        whitelist().clear();
        AnonymousCaptchaShield.setEnabled(true);
        AnonymousCaptchaShield.setFirstVisitCaptchaEnabled(true);
        Firewall.setEnabled(true);
        Sessions.clearThreadLocalData();
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
    public void firstVisitPageAndApiHaveDifferentConsequences() throws Exception {
        try (final Exchange exchange = new Exchange("/")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.response.getHeader("Location"), "/test");
            Assert.assertFalse(exchange.reached);
            Assert.assertTrue(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
            Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 1);
        }
        reset();
        try (final Exchange exchange = new Exchange("/api/search")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 401);
            Assert.assertFalse(exchange.reached);
            Assert.assertFalse(AnonymousCaptchaShield.isAwaitingCaptcha(IP));
        }
    }

    @Test
    public void fifthVisitAndRecentPassKeepOriginalIntervals() throws Exception {
        AnonymousCaptchaShield.setFirstVisitCaptchaEnabled(false);
        for (int i = 0; i < 4; i++) allowed("/shield-test-page");
        try (final Exchange exchange = new Exchange("/shield-test-page")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.response.getHeader("Location"), "/test");
            Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 5);
        }
        reset();
        cache("ipLastCaptchaPassTimeCache").put(IP, System.currentTimeMillis());
        for (int i = 0; i < 4; i++) allowed("/shield-test-page");
        try (final Exchange exchange = new Exchange("/shield-test-page")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 302);
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
    public void whitelistAndKnownUserIpStillExempt() throws Exception {
        AnonymousCaptchaShield.administratorUnban(IP);
        for (int i = 0; i < 6; i++) allowed("/shield-test-page");
        Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
        reset();
        @SuppressWarnings("unchecked") final Map<String, String> known = (Map<String, String>) field(UserCache.class, "IP_CACHE");
        final String previous = known.put(IP, "fixture-user");
        try {
            allowed("/shield-test-page");
            Assert.assertTrue(whitelist().contains(IP));
            known.remove(IP);
            allowed("/shield-test-page");
            Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
        } finally {
            if (previous != null) known.put(IP, previous); else known.remove(IP);
        }
    }

    @Test
    public void verifiedCrawlerExemptionUsesVerifierResultNotOnlyUa() throws Exception {
        @SuppressWarnings("unchecked") final Cache<String, SearchEngines.Engine> verified =
                (Cache<String, SearchEngines.Engine>) field(SearchEngines.class, "VERIFIED_CRAWLERS");
        verified.put("google:" + IP, SearchEngines.Engine.GOOGLE);
        try (final Exchange exchange = new Exchange("/", "GET", null, "Googlebot")) {
            exchange.run(middleware::handle);
            Assert.assertTrue(exchange.reached);
            Assert.assertNull(cache("ipVisitCountCache").getIfPresent(IP));
        } finally { verified.invalidate("google:" + IP); }
        @SuppressWarnings("unchecked") final Cache<String, Boolean> rejected =
                (Cache<String, Boolean>) field(SearchEngines.class, "REJECTED_CRAWLERS");
        rejected.put("google:" + IP, true);
        try (final Exchange exchange = new Exchange("/", "GET", null, "Googlebot")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 302);
        } finally { rejected.invalidate("google:" + IP); }
    }

    @Test
    public void globalBlacklistPrecedesCorsAndDoesNotDependOnPageAuth() throws Exception {
        cache("ipBlacklistCache").put(IP, true);
        Firewall.setEnabled(false);
        try (final Exchange exchange = new Exchange("/api/v3/me", "OPTIONS", null, UA)) {
            exchange.raw.headers().set("Origin", "https://example.invalid");
            exchange.run(new BeforeRequestHandler());
            Assert.assertEquals(exchange.response.getHeader("Location"), "/test");
            Assert.assertNull(exchange.response.getHeader("Access-Control-Allow-Origin"));
            Assert.assertFalse(exchange.reached);
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
            Assert.assertEquals(exchange.status, 401);
        }
        try (final Exchange exchange = new Exchange("/api/v3/columns")) {
            exchange.context.attr(ApiV3Processor.IDENTITY, new JSONObject().put("user", new JSONObject().put("oId", "v3-user")));
            exchange.run(middleware::handle);
            Assert.assertTrue(exchange.reached);
            Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 1);
        }
    }

    @Test
    public void captchaSuccessAndAdministratorUnbanHaveDistinctStateTransitions() throws Exception {
        try (final ProofProvider provider = new ProofProvider()) {
            cache("ipBlacklistCache").put(IP, true);
            cache("ipVisitCountCache").put(IP, 5);
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
            Assert.assertNotNull(cache("ipLastCaptchaPassTimeCache").getIfPresent(IP));
            Assert.assertEquals(cache("ipFirstVisitTimeCache").getIfPresent(IP), 123L);
            Assert.assertFalse(whitelist().contains(IP));
            AnonymousCaptchaShield.administratorUnban(IP);
            Assert.assertTrue(whitelist().contains(IP));
            Assert.assertNull(cache("ipFirstVisitTimeCache").getIfPresent(IP));
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
                Assert.assertNull(cache("ipLastCaptchaPassTimeCache").getIfPresent(IP));
            }
            Assert.assertFalse(AnonymousCaptchaShield.verifyProof("invalid JSON"));
            Assert.assertFalse(AnonymousCaptchaShield.verifyProof(null));
        }
    }

    @Test
    public void allowListDoesNotPrecedeCaptchaAndApiNeverEntersCumulativeBan() throws Exception {
        Assert.assertTrue(List.of(Symphonys.ANONYMOUS_VIEW_SKIPS.split(",")).contains("/recent"));
        try (final Exchange exchange = new Exchange("/recent")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 302);
        }
        reset();
        cache("ipVisitCountCache").put(IP, 299);
        @SuppressWarnings("unchecked") final Cache<String, Boolean> banned = (Cache<String, Boolean>) field(Firewall.class, "BANNED");
        Assert.assertNull(banned.getIfPresent(IP));
        try (final Exchange exchange = new Exchange("/api/search")) {
            exchange.run(middleware::handle);
            Assert.assertEquals(exchange.status, 401);
            Assert.assertEquals(cache("ipVisitCountCache").getIfPresent(IP), 300);
            Assert.assertNull(banned.getIfPresent(IP));
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
