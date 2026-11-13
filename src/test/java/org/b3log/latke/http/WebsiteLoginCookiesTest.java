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
package org.b3log.latke.http;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.model.User;
import org.b3log.latke.util.Crypts;
import org.b3log.symphony.util.AccountCredentialVersions;
import org.b3log.symphony.util.ApiV3RefreshCookies;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.Symphonys;
import org.b3log.symphony.util.WebsiteLoginCookies;
import org.json.JSONObject;

import java.util.Properties;

/** 使用真实会话缓存和退出入口回归，不连接数据库或启动服务。 */
public final class WebsiteLoginCookiesTest {
    private static final String ACTOR = "10001";
    private static final String OTHER = "10002";
    private static int checks;

    public static void main(final String[] args) throws Exception {
        Latkes.setLocalProps(new Properties());
        Latkes.setLatkeProps(new Properties());
        Latkes.setLatkeProperty("contextPath", "");
        Latkes.setServerScheme("https");
        Latkes.setEnabledSession(true);
        try {
            trustedOrigins();
            final String legacy = Crypts.encryptByAES(new JSONObject().put(Keys.OBJECT_ID, ACTOR)
                    .put(Keys.TOKEN, "test-password:random").put("credentialEpoch", 0).toString(), Symphonys.COOKIE_SECRET);
            check(legacy != null, "旧 Cookie 夹具必须使用真实加密");
            logout(legacy, true);
            logout("invalid-cookie", false);
            logout(null, false);
            System.out.println("website-login-cookies: " + checks + " checks passed");
        } finally {
            Sessions.clearThreadLocalData();
        }
    }

    private static void trustedOrigins() {
        Latkes.setServerHost("fishpi.cn");
        Latkes.setServerPort("443");
        origin("https://new.fishpi.cn", null, "same-site", false, true);
        origin("https://new.fishpi.cn:443", null, "same-site", false, true);
        origin(null, "https://new.fishpi.cn/article/123", "same-site", false, true);
        origin("https://fishpi.cn", null, "same-origin", false, true);
        origin("https://yui.fishpi.cn", null, "same-site", false, false);
        origin(null, "https://yui.fishpi.cn/", "same-site", false, false);
        origin("http://new.fishpi.cn", null, "same-site", false, false);
        origin("https://new.fishpi.cn:444", null, "same-site", false, false);
        origin("https://new.fishpi.cn.evil.example", null, "same-site", false, false);
        origin("https://other.fishpi.cn", null, "same-site", false, false);
        origin("https://new.fishpi.cn@evil.example", null, "same-site", false, false);
        origin("https://evil.example@new.fishpi.cn", null, "same-site", false, false);
        origin("null", "https://new.fishpi.cn/", "same-site", false, false);
        origin("https://evil.example", "https://new.fishpi.cn/", "same-site", false, false);
        origin("https://new.fishpi.cn", null, "cross-site", false, false);
        origin(null, null, null, false, false);
        origin(null, null, null, true, true);

        // 本地规则保持独立，不能因新增生产来源而放行本地站点的跨域请求。
        Latkes.setServerHost("localhost");
        Latkes.setServerPort("8081");
        Latkes.setServerScheme("http");
        origin("http://localhost:5173", null, "same-site", false, true);
        origin("http://localhost:8081", null, "same-origin", false, true);
        origin("http://localhost:5174", null, "same-site", false, false);
        origin("https://new.fishpi.cn", null, null, false, false);
        Latkes.setServerScheme("https");
        Latkes.setServerHost("fishpi.cn");
        Latkes.setServerPort("443");
    }

    private static void origin(final String source, final String referer, final String fetchSite,
                               final boolean navigation, final boolean expected) {
        final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/v3/auth/session");
        try {
            if (source != null) raw.headers().set("Origin", source);
            if (referer != null) raw.headers().set("Referer", referer);
            if (fetchSite != null) raw.headers().set("Sec-Fetch-Site", fetchSite);
            final var request = new Request(null, raw);
            final var response = new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            check(WebsiteLoginCookies.trustedOrigin(new RequestContext(request, response), navigation) == expected,
                    "来源校验：" + source + ", referer=" + referer + ", fetchSite=" + fetchSite);
        } finally {
            raw.release();
        }
    }

    private static void logout(final String cookieValue, final boolean authenticated) throws Exception {
        Sessions.clearThreadLocalData();
        AccountCredentialVersions.beginHttp();
        final JSONObject user = new JSONObject().put(Keys.OBJECT_ID, ACTOR).put(User.USER_PASSWORD, "test-password");
        if (authenticated) {
            Sessions.setUser(user);
        }
        Sessions.put(ACTOR, user);
        Sessions.put(OTHER, new JSONObject().put(Keys.OBJECT_ID, OTHER));
        final Session state = org.b3log.latke.http.Sessions.add();
        final Session otherState = org.b3log.latke.http.Sessions.add();
        state.setAttribute(User.USER, user.toString());
        state.setAttribute("accountCredentialEpoch", "0");
        state.setAttribute("unrelated", "preserve");
        otherState.setAttribute(User.USER, user.toString());
        final WebSocketSession socket = new WebSocketSession(null);
        socket.session = state;
        final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/logout");
        try {
            final Request request = new Request(null, raw);
            request.setSession(state);
            if (cookieValue != null) {
                request.getCookies().add(new Cookie(Sessions.COOKIE_NAME, cookieValue));
            }
            check(Sessions.websiteSession(request) == null, "覆盖未绑定分支");
            final Response response = new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            final RequestContext context = new RequestContext(request, response);
            WebsiteLoginCookies.logout(context);
            check(state.getAttribute(User.USER) == null, "退出后清理服务端用户");
            check(state.getAttribute("accountCredentialEpoch") == null, "退出后清理会话凭据版本");
            check(Sessions.currentSocketUser(socket) == null, "已有 WebSocket 不能继续认证");
            check("preserve".equals(state.getAttribute("unrelated")), "保留无关会话属性");
            check(otherState.getAttribute(User.USER) != null, "不清理其他浏览器会话");
            check(Sessions.get(OTHER) != null, "不清理其他用户缓存");
            if (authenticated) {
                check(Sessions.get(ACTOR) == null, "保留旧退出的当前用户缓存失效");
            }
            cleared(response, Sessions.COOKIE_NAME, "/");
            cleared(response, ApiV3RefreshCookies.NAME, "/api/v3/auth");
            WebsiteLoginCookies.logout(context);
            check(state.getAttribute(User.USER) == null, "重复退出保持幂等");
        } finally {
            raw.release();
            org.b3log.latke.http.Sessions.CACHE.remove(state.getId());
            org.b3log.latke.http.Sessions.CACHE.remove(otherState.getId());
            Sessions.invalidate(ACTOR);
            Sessions.invalidate(OTHER);
            Sessions.clearThreadLocalData();
        }
    }

    private static void cleared(final Response response, final String name, final String path) {
        final Cookie cookie = response.getCookies().stream().filter(value -> name.equals(value.getName()))
                .findFirst().orElseThrow();
        check(cookie.getValue().isEmpty() && cookie.getMaxAge() == 0 && path.equals(cookie.getPath()),
                "正确清理 Cookie：" + name);
    }

    private static void check(final boolean condition, final String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
