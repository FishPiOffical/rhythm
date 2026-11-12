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

import org.b3log.latke.http.Response;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.Keys;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.json.JSONObject;

/** 第一方网页的双 Cookie 交付；令牌仅留在 HttpOnly Cookie 或既有 V3 响应。 */
public final class WebsiteLoginCookies {
    private WebsiteLoginCookies() { }

    /** 仅信任本站和明确的新站；旧版直接打开退出地址时允许没有来源头。 */
    public static boolean trustedOrigin(final RequestContext context, final boolean navigation) {
        if ("cross-site".equalsIgnoreCase(context.header("Sec-Fetch-Site"))) return false;
        String source = context.header("Origin");
        if (source == null || source.isBlank()) source = context.header("Referer");
        if (source == null || source.isBlank()) return navigation;
        try {
            final var uri = java.net.URI.create(source);
            if (uri.getRawUserInfo() != null || uri.getHost() == null) return false;
            final String expected = org.b3log.latke.Latkes.getServePath();
            final var site = java.net.URI.create(expected);
            final boolean own = uri.getScheme().equalsIgnoreCase(site.getScheme())
                    && uri.getHost().equalsIgnoreCase(site.getHost()) && uri.getPort() == site.getPort();
            final boolean newer = "https".equalsIgnoreCase(uri.getScheme())
                    && "yui.fishpi.cn".equalsIgnoreCase(uri.getHost()) && (uri.getPort() == -1 || uri.getPort() == 443)
                    && "fishpi.cn".equalsIgnoreCase(site.getHost());
            final boolean local = ("localhost".equals(site.getHost()) || "127.0.0.1".equals(site.getHost()))
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
                    && "http".equals(uri.getScheme()) && (uri.getPort() == 5173 || uri.getPort() == site.getPort());
            return own || newer || local;
        } catch (final IllegalArgumentException error) {
            return false;
        }
    }

    public static String login(final Response response, final String actor, final boolean remember) throws ServiceException {
        final var service = BeanManager.getInstance().getReference(ApiV3TokenMgmtService.class);
        final var login = service.issueWebsite(actor);
        return attach(response, login, remember);
    }

    public static String attach(final Response response, final ApiV3TokenMgmtService.WebsiteLogin login,
                                final boolean remember) throws ServiceException {
        final String cookie = Sessions.loginWebsiteCookie(response, login.actor(), remember, login.family());
        if (cookie == null) {
            BeanManager.getInstance().getReference(ApiV3TokenMgmtService.class).revokeWebsiteFamily(login.actor(), login.family());
            throw new ServiceException("unauthorized");
        }
        // 旧版没有勾选记住登录时，两种 Cookie 都随浏览器会话结束。
        final var refresh = remember
                ? ApiV3RefreshCookies.create(login.tokens().getString("refreshToken"), login.tokens().getLong("refreshExpiresIn"))
                : ApiV3RefreshCookies.createSession(login.tokens().getString("refreshToken"));
        response.addCookie(refresh);
        return cookie;
    }

    /** 轮换后延续同一旧 Cookie，不重建 CSRF 值或重新获取账号凭据版本。 */
    public static void renew(final RequestContext context, final JSONObject binding, final JSONObject tokens) {
        final boolean remember = binding.optBoolean("rememberLogin");
        for (final var original : context.getRequest().getCookies()) {
            if (!Sessions.COOKIE_NAME.equals(original.getName())) continue;
            final var cookie = new org.b3log.latke.http.Cookie(Sessions.COOKIE_NAME, original.getValue());
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setSecure("https".equalsIgnoreCase(org.b3log.latke.Latkes.getServerScheme()));
            cookie.setSameSite("Lax");
            if (remember) cookie.setMaxAge(ApiV3TokenUtil.cookieMaxAgeSeconds(tokens.getLong("refreshExpiresIn")));
            context.getResponse().addCookie(cookie);
        }
        final var refresh = remember
                ? ApiV3RefreshCookies.create(tokens.getString("refreshToken"), tokens.getLong("refreshExpiresIn"))
                : ApiV3RefreshCookies.createSession(tokens.getString("refreshToken"));
        context.getResponse().addCookie(refresh);
    }

    public static void logout(final RequestContext context) throws ServiceException {
        ApiV3RefreshCookies.discardFrameworkEcho(context);
        final JSONObject user = Sessions.websiteSession(context.getRequest());
        final String actor;
        if (user != null) {
            actor = user.getString(Keys.OBJECT_ID);
            final String family = user.getString("websiteFamily");
            if (family != null) BeanManager.getInstance().getReference(ApiV3TokenMgmtService.class).revokeWebsiteFamily(actor, family);
        } else {
            final JSONObject current = Sessions.getUser();
            actor = current == null ? null : current.optString(Keys.OBJECT_ID);
        }
        // 未绑定旧会话和失效 Cookie 也必须清理服务端身份，阻止已有 WebSocket 继续认证。
        Sessions.logout(actor, context.getRequest(), context.getResponse());
        context.getResponse().addCookie(ApiV3RefreshCookies.create("", 0));
    }
}
