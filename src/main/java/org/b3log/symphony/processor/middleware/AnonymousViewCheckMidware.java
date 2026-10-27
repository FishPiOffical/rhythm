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
import org.b3log.symphony.util.Firewall;
import org.b3log.symphony.util.Headers;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.SearchEngines;
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

    private static final String ARTICLE_RANDOM_PATH = "random";

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

    // 访问计数缓存，2 小时过期；窗口必须足够长，否则慢速抓取（间隔超过 5 分钟）会让计数反复归零从而绕过验证码
    public static final Cache<String, Integer> ipVisitCountCache = Caffeine.newBuilder()
            .expireAfterWrite(2, java.util.concurrent.TimeUnit.HOURS)
            .maximumSize(100000)
            .build();
    // 黑名单缓存，无过期，手动移除
    public static final Cache<String, Boolean> ipBlacklistCache = Caffeine.newBuilder()
            .maximumSize(100000)
            .build();
    // String类型的白名单（管理员在 /admin/ip 解封后加入，永久跳过验证码盾）
    public static final Set<String> whiteList = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * IP 首次访问时间缓存（用于 2 小时内首次访问需要验证码）
     */
    public static final Cache<String, Long> ipFirstVisitTimeCache = Caffeine.newBuilder()
            .expireAfterWrite(2, java.util.concurrent.TimeUnit.HOURS)
            .maximumSize(100000)
            .build();

    /**
     * IP 最近一次通过验证码的时间（通过后重置计数逻辑时使用）
     */
    public static final Cache<String, Long> ipLastCaptchaPassTimeCache = Caffeine.newBuilder()
            .expireAfterWrite(2, java.util.concurrent.TimeUnit.HOURS)
            .maximumSize(100000)
            .build();

    /**
     * Runtime enable flag for captcha checks (temporary, resets on restart).
     */
    private static volatile boolean enabled = true;

    /**
     * Whether to force captcha on the first visit within 2 hours (temporary, resets on restart).
     */
    private static volatile boolean firstVisitCaptchaEnabled = true;

    // 计数窗口为 2 小时，阈值需相应放大，避免正常用户 2 小时内浏览 100 个页面被误封
    private static final int AUTO_BAN_VISIT_THRESHOLD = 300;

    private static final int CAPTCHA_VISIT_INTERVAL = 5;

    private static final long FIRST_VISIT_CAPTCHA_WINDOW_MILLIS = 2L * 60L * 60L * 1000L;

    public static void setEnabled(final boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setFirstVisitCaptchaEnabled(final boolean value) {
        firstVisitCaptchaEnabled = value;
    }

    public static boolean isFirstVisitCaptchaEnabled() {
        return firstVisitCaptchaEnabled;
    }

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

    private static boolean isPublicInfoPage(final String requestURI) {
        final String contextPath = Latkes.getContextPath();
        return requestURI.equals(contextPath + "/about")
                || requestURI.equals(contextPath + "/download")
                || requestURI.equals(contextPath + "/privacy")
                || requestURI.equals(contextPath + "/agreement");
    }

    public void handle(final RequestContext context) {
        final boolean shieldEnabled = enabled;

        final Request request = context.getRequest();
        final String requestURI = context.requestURI();
        final String articleId = getArticleId(requestURI);
        final boolean firstVisitArticle = StringUtils.isNotBlank(articleId);
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        if (null == currentUser) {
            final String ip = Requests.getRemoteAddr(context.getRequest());
            final String ua = Headers.getHeader(request, Common.USER_AGENT, "");
            final boolean verifiedCrawler = null != SearchEngines.verifiedCrawler(ip, ua);
            // 匿名非蜘蛛：白名单直接放行，其余按「首访 / 每 5 次」判断；通过验证码只豁免首访规则，不豁免次数规则
            if (shieldEnabled && !verifiedCrawler && !isPublicInfoPage(requestURI)
                    && !whiteList.contains(ip)) {
                if (UserCache.hasUserByIP(ip)) {
                    // 该 IP 上有用户登录过，按既定策略整台机器免验证
                    whiteList.add(ip);
                } else {
                    final long now = System.currentTimeMillis();
                    Long firstVisitTime = ipFirstVisitTimeCache.getIfPresent(ip);
                    if (null == firstVisitTime) {
                        firstVisitTime = now;
                        ipFirstVisitTimeCache.put(ip, firstVisitTime);
                    }

                    final Integer visits = ipVisitCountCache.getIfPresent(ip);
                    final int count = null == visits ? 1 : visits + 1;
                    ipVisitCountCache.put(ip, count);

                    // API 调用方无法通过验证码，不做累计封禁，交给 Firewall 的速率阈值处理
                    if (!requestURI.contains("/api/")
                            && count >= AUTO_BAN_VISIT_THRESHOLD && Firewall.banIpIfAbsent(ip, "Anonymous captcha threshold")) {
                        System.out.println(ip + " 已封禁");
                    }

                    // 2 小时内已通过验证码时，不再按「首访」弹窗，但每 5 次访问仍然需要验证
                    final boolean passedRecently = null != ipLastCaptchaPassTimeCache.getIfPresent(ip);

                    boolean needCaptcha = false;
                    // 非文章页：2 小时内首次访问就需要验证码（可后台开关）；文章页豁免首访验证，便于搜索引擎落地用户直接阅读
                    if (!passedRecently && firstVisitCaptchaEnabled && !firstVisitArticle && count == 1
                            && (now - firstVisitTime) <= FIRST_VISIT_CAPTCHA_WINDOW_MILLIS) {
                        needCaptcha = true;
                    }
                    // 之后每访问 5 次需要一次验证码
                    if (!needCaptcha && count % CAPTCHA_VISIT_INTERVAL == 0) {
                        needCaptcha = true;
                    }

                    if (needCaptcha) {
                        if (requestURI.contains("/api/")) {
                            // API 调用方无法完成验证码，直接拒绝且不拉黑，避免把服务端客户端永久卡死
                            context.sendError(401);
                        } else {
                            ipBlacklistCache.put(ip, true);
                            context.sendRedirect("/test");
                            System.out.println(ip + " 触发验证码，进入黑名单");
                        }
                        context.abort();
                        return;
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

        if (StringUtils.isNotBlank(articleId)) {
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

    private static String getArticleId(final String requestURI) {
        final String articlePrefix = Latkes.getContextPath() + "/article/";
        if (!requestURI.startsWith(articlePrefix)) {
            return "";
        }

        final String articlePath = StringUtils.substringAfter(requestURI, articlePrefix);
        final String articleId = StringUtils.substringBefore(articlePath, "/");
        if (StringUtils.isBlank(articleId) || ARTICLE_RANDOM_PATH.equals(articleId)) {
            return "";
        }

        return articleId;
    }

}
