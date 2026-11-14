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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.b3log.latke.http.Request;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.testng.Assert;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * 使用随包发布的 Latke 解析器与应用 XML 日志插件，覆盖 OAuth 控制器执行前的日志。
 */
public class ApiV3OAuthTransportLogTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("log4j.configurationFile", Path.of("src/main/resources/log4j2.xml").toAbsolutePath().toUri().toString());
        Class.forName("org.b3log.latke.http.Request");
        final var actualLogger = Request.class.getDeclaredField("LOGGER");
        actualLogger.setAccessible(true);
        final var context = ((org.apache.logging.log4j.core.Logger) actualLogger.get(null)).getContext();
        final var config = context.getConfiguration();
        final var logger = config.getLoggerConfig("org.b3log.latke.http.Request");
        ApiV3OAuthTransportLogFilter.requireInstalled();
        Assert.assertEquals(logger.getName(), "org.b3log.latke.http.Request");
        Assert.assertTrue(logger.getFilter() instanceof ApiV3OAuthTransportLogFilter);
        final List<String> seen = new ArrayList<>();
        final var capture = new AbstractAppender("oauth-fixture", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent e) {
                seen.add(e.getMessage().getFormattedMessage());
            }
        };
        capture.start();
        logger.addAppender(capture, Level.ALL, null);
        final var server = config.getLoggerConfig("org.b3log.latke.http.ServerHandler");
        server.addAppender(capture, Level.ALL, null);
        context.updateLoggers();

        // 先移除过滤器复现框架的原始日志，再恢复过滤器验证保护范围。
        final var filter = logger.getFilter();
        logger.removeFilter(filter);
        try {
            ApiV3OAuthTransportLogFilter.requireInstalled();
            Assert.fail("Missing privacy filter must prevent route startup");
        } catch (IllegalStateException expected) {
        }
        malformed("/api/v3/oauth/token", "code_verifier=FIXTURE_PRIVATE_PROOF%");
        Assert.assertTrue(seen.stream().anyMatch(x -> x.contains("FIXTURE_PRIVATE_PROOF")));
        logger.addFilter(filter);
        seen.clear();
        for (String path : List.of("/api/v3/oauth/token", "/api/v3/oauth/revoke", "/api/v3/oauth/consent", "/prefix/api/v3/oauth/authorize", "/api/v3/%6fauth/token", "%2Fapi%2Fv3%2Foauth%2Ftoken", "/api/v3/auth/password-reset/complete", "/api/v3/%61uth/login", "/register2", "/reset-pwd",
                "/api/v3/me/settings/password", "/api/v3/me/settings/phone/verification", "/api/v3/me/settings/email", "/api/v3/me/settings/qq", "%2Fapi%2Fv3%2Fme%2Fsettings%2Fqq", "/api/v3/me/security/mfa/preparations", "%2Fapi%2Fv3%2Fme%2Fsecurity%2Fmfa",
                "/api/v3/me/game-saves/evolve-save", "%2Fapi%2Fv3%2Fme%2Fgame-saves%2Fevolve-save", "/api/cloud/sync", "/api/cloud/get", "%2Fapi%2Fcloud%2Fsync", "/api/v3/me/exports/posts", "%2Fapi%2Fv3%2Fme%2Fexports%2Fposts", "/api/v3/me/%65xports/posts"))
            malformed(path, "refresh_token=FIXTURE_PRIVATE_TOKEN%");
        context.getLogger("org.b3log.latke.http.ServerHandler").error("Handle HTTP request failed", new IllegalArgumentException("unterminated escape in /api/v3/oauth/token?code=FIXTURE_PRIVATE_CODE%"));
        Assert.assertTrue(seen.isEmpty());
        malformed("/existing-path", "public_field=bad%");
        Assert.assertEquals(seen.size(), 1);
        Assert.assertTrue(seen.getFirst().contains("/existing-path"));

        // 让非法 UTF-8 实际经过框架 parseForm，再验证 OAuth 解析仍会拒绝。
        final var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/v3/oauth/token",
                Unpooled.wrappedBuffer(new byte[]{'a', '=', (byte) 0xC0, (byte) 0xAF}));
        try {
            var request = new Request(null, req) {
                @Override
                public String getRemoteAddr() {
                    return "192.0.2.1";
                }
            };
            var parser = Request.class.getDeclaredMethod("parseForm");
            parser.setAccessible(true);
            parser.invoke(request);
            try {
                ApiV3OAuthForms.parse(request.getBytes(), Set.of("a"));
                Assert.fail();
            } catch (ApiV3Exception expected) {
            }
        } finally {
            req.release();
        }
        System.out.println("PASS actual Latke malformed-parser logs would expose the fake proof before handlers, application XML discovers the plugin and suppresses OAuth form/query credential errors while preserving unrelated failures; framework-normalized malformed UTF-8 remains rejected");
    }

    static void malformed(String path, String body) throws Exception {
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, path,
                Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        try {
            var request = new Request(null, req) {
                @Override
                public String getRemoteAddr() {
                    return "192.0.2.1";
                }
            };
            var parser = Request.class.getDeclaredMethod("parseForm");
            parser.setAccessible(true);
            parser.invoke(request);
        } finally {
            req.release();
        }
    }
}
