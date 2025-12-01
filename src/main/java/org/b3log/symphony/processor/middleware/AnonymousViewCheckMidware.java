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
package org.b3log.symphony.processor.middleware;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Cookie;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.util.AntPathMatcher;
import org.b3log.latke.util.Execs;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.URLs;
import org.b3log.symphony.cache.UserCache;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.service.OptionQueryService;
import org.b3log.symphony.service.UserMgmtService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.Headers;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Anonymous view check.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.1, May 31, 2020
 * @since 1.6.0
 */
@Singleton
public class AnonymousViewCheckMidware {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(AnonymousViewCheckMidware.class);

    /**
     * Article repository.
     */
    @Inject
    private ArticleRepository articleRepository;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

    /**
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

    // 计数缓存，5分钟过期
    public static final Cache<String, Integer> ipVisitCountCache = Caffeine.newBuilder()
            .expireAfterWrite(5, java.util.concurrent.TimeUnit.MINUTES)
            .maximumSize(100000)
            .build();
    // 黑名单缓存，无过期，手动移除
    public static final Cache<String, Boolean> ipBlacklistCache = Caffeine.newBuilder()
            .maximumSize(100000)
            .build();
    // String类型的白名单
    public static final Set<String> whiteList = new HashSet<>();

    private static Cookie getCookie(final Request request, final String name) {
        final Set<Cookie> cookies = request.getCookies();
        if (cookies.isEmpty()) {
            return null;
        }

        for (final Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return cookie;
            }
        }

        return null;
    }

    private static void addCookie(final Response response, final String name, final String value) {
        final Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24);
        cookie.setHttpOnly(true);
        cookie.setSecure(StringUtils.equalsIgnoreCase(Latkes.getServerScheme(), "https"));

        response.addCookie(cookie);
    }

    public void handle(final RequestContext context) {
        final Request request = context.getRequest();
        final String requestURI = context.requestURI();
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        if (null == currentUser) {
            final String ip = Requests.getRemoteAddr(context.getRequest());
            if (!whiteList.contains(ip)) {
                if (UserCache.hasUserByIP(ip)) {
                    whiteList.add(ip);
                } else {
                    final String ua = Headers.getHeader(request, Common.USER_AGENT, "");
                    if (!isSearchEngineBot(ua)) {
                        // 计数逻辑
                        Integer count = ipVisitCountCache.getIfPresent(ip);
                        if (count == null) count = 0;
                        count++;
                        ipVisitCountCache.put(ip, count);
                        System.out.println(ip + " 访问计数：" + count + " " + context.requestURI());
                        if (count >= 300) {
                            String result = Execs.exec(new String[]{"sh", "-c", "ipset add fishpi " + ip}, 1000 * 3);
                            System.out.println(ip + " 已封禁");
                        }
                        if (count >= 5) {
                            // 进入黑名单
                            ipBlacklistCache.put(ip, true);
                            // 跳转到验证码页面
                            context.sendRedirect("/test");
                            System.out.println(ip + " 进入黑名单");
                            return;
                        }
                    }
                }
            }
        }

        if (requestURI.startsWith(Latkes.getContextPath() + "/member/") || requestURI.startsWith(Latkes.getContextPath() + "/article/1636516552191")) {
            if (null == currentUser) {
                context.sendError(401);
                context.abort();
                return;
            }
        }

        final String[] skips = Symphonys.ANONYMOUS_VIEW_SKIPS.split(",");
        for (final String skip : skips) {
            if (AntPathMatcher.match(Latkes.getContextPath() + skip, requestURI)) {
                return;
            }
        }

        if (requestURI.startsWith(Latkes.getContextPath() + "/article/")) {
            final String articleId = StringUtils.substringAfter(requestURI, Latkes.getContextPath() + "/article/");

            try {
                final JSONObject article = articleRepository.get(articleId);
                if (null == article) {
                    context.sendError(404);
                    context.abort();
                    return;
                }

                if (Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW == article.optInt(Article.ARTICLE_ANONYMOUS_VIEW) && !Sessions.isLoggedIn()) {
                    context.sendError(401);
                    context.abort();
                    return;
                } else if (Article.ARTICLE_ANONYMOUS_VIEW_C_ALLOW == article.optInt(Article.ARTICLE_ANONYMOUS_VIEW)) {
                    context.handle();
                    return;
                }
            } catch (final RepositoryException e) {
                context.sendError(500);
                context.abort();
                return;
            }
        }


        // Check if admin allow to anonymous view
        final JSONObject option = optionQueryService.getOption(Option.ID_C_MISC_ALLOW_ANONYMOUS_VIEW);
        if (!"0".equals(option.optString(Option.OPTION_VALUE))) {
            // https://github.com/b3log/symphony/issues/373
            final String cookieNameVisits = "anonymous-visits";
            final Cookie visitsCookie = getCookie(request, cookieNameVisits);

            if (null == currentUser) {
                if (null != visitsCookie) {
                    final JSONArray uris = new JSONArray(URLs.decode(visitsCookie.getValue()));
                    for (int i = 0; i < uris.length(); i++) {
                        final String uri = uris.getString(i);
                        if (uri.equals(requestURI)) {
                            return;
                        }
                    }

                    uris.put(requestURI);
                    if (uris.length() > Symphonys.ANONYMOUS_VIEW_URIS) {
                        context.sendError(401);
                        context.abort();
                        return;
                    }

                    addCookie(context.getResponse(), cookieNameVisits, URLs.encode(uris.toString()));
                    context.handle();
                    return;
                } else {
                    final JSONArray uris = new JSONArray();
                    uris.put(requestURI);
                    addCookie(context.getResponse(), cookieNameVisits, URLs.encode(uris.toString()));
                    context.handle();
                    return;
                }
            } else { // logged in
                if (null != visitsCookie) {
                    final Cookie cookie = new Cookie(cookieNameVisits, "");
                    cookie.setMaxAge(0);
                    cookie.setPath("/");

                    context.getResponse().addCookie(cookie);
                    context.handle();
                    return;
                }
            }
        }

        context.handle();
    }

    private static final List<String> SEARCH_ENGINE_BOTS = Arrays.asList(
            "Googlebot", "Bingbot", "Baiduspider", "Sogou", "360Spider", "YandexBot", "DuckDuckBot"
    );

    public static boolean isSearchEngineBot(String ua) {
        if (ua == null) return false;
        for (String bot : SEARCH_ENGINE_BOTS) {
            if (ua.contains(bot)) return true;
        }
        return false;
    }

}
