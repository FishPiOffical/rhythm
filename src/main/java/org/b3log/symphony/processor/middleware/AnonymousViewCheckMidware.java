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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
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
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.processor.ApiV3Processor;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.service.OptionQueryService;
import org.b3log.symphony.util.AnonymousCaptchaShield;
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

    private static final String ARTICLE_RANDOM_PATH = "random";

    /**
     * Article repository.
     */
    @Inject
    private ArticleRepository articleRepository;

    /**
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

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

    /**
     * 输出匿名访问风控决策日志，统一前缀便于检索：{@code [SHIELD]}
     *
     * @param ip      客户端 IP
     * @param uri     请求 URI
     * @param verdict 结果：放行 / 验证码 / 拒绝 / 跳转 / 封禁
     * @param reason  原因说明
     * @param extra   额外信息，可为 {@code null}
     */
    private static void logShield(final String ip, final String uri, final String verdict,
                                  final String reason, final String extra) {
        LOGGER.log(Level.INFO, "[SHIELD] ip={} uri={} 结果={} 原因={}{}",
                ip, uri, verdict, reason, null == extra ? "" : " " + extra);
    }

    public void handle(final RequestContext context) {
        // 验证码盾开关须在身份解析前取快照；未经阿达明确允许，不得修改、移动或删除此读取。
        final boolean shieldEnabled = AnonymousCaptchaShield.isEnabled();

        final Request request = context.getRequest();
        final String requestURI = context.requestURI();
        final String articleId = getArticleId(requestURI);
        // V3 resource identity must never inherit a page session or a legacy apiKey.
        final boolean v3 = requestURI.startsWith(Latkes.getContextPath() + "/api/v3/");
        final JSONObject v3Identity = (JSONObject) context.attr(ApiV3Processor.IDENTITY);
        JSONObject currentUser = v3 ? (null == v3Identity ? null : v3Identity.optJSONObject("user")) : Sessions.getUser();
        if (!v3) {
            try {
                currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
            } catch (NullPointerException ignored) {
            }
        }
        // 验证码盾保护入口：须在下方登录要求、viewSkips 和阅读限制之前执行。
        // 未经阿达明确允许，不得修改、移动、删除此调用或增加绕过分支。
        if (!AnonymousCaptchaShield.checkAnonymous(context, currentUser, articleId, shieldEnabled)) {
            return;
        }

        if (requestURI.startsWith(Latkes.getContextPath() + "/member/") || requestURI.startsWith(Latkes.getContextPath() + "/article/1636516552191")) {
            if (null == currentUser) {
                logShield(Requests.getRemoteAddr(context.getRequest()), requestURI, "拒绝", "需要登录", null);
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
                    logShield(Requests.getRemoteAddr(context.getRequest()), requestURI, "拒绝", "文章不允许匿名查看", null);
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
                        logShield(Requests.getRemoteAddr(context.getRequest()), requestURI, "拒绝", "匿名访问页数超限",
                                "页数=" + uris.length() + " 上限=" + Symphonys.ANONYMOUS_VIEW_URIS);
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
