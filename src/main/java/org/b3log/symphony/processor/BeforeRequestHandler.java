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
package org.b3log.symphony.processor;

import eu.bitwalker.useragentutils.BrowserType;
import eu.bitwalker.useragentutils.UserAgent;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Session;
import org.b3log.latke.http.function.Handler;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.model.User;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.Stopwatchs;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.Firewall;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Before request handler.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.1, Nov 11, 2019
 * @since 3.6.0
 */
public class BeforeRequestHandler implements Handler {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(BeforeRequestHandler.class);

    /*SimpleCurrentLimiter antiCCLimiter = new SimpleCurrentLimiter(15, 15);

    private static final List<String> whiteList = new ArrayList<String>() {{
            add("103.239.101.147");
            add("127.0.0.1");
            add("[0:0:0:0:0:0:0:1]");
            add("43.155.63.117");
        }};*/

    @Override
    public void handle(final RequestContext context) {
        Stopwatchs.start("Request initialized [" + context.requestURI() + "]");

        if (context.header(Common.USER_AGENT) == null) {
            context.sendStatus(418);
            return;
        }

        final String ip = Requests.getRemoteAddr(context.getRequest());
        Firewall.recordAndMaybeBan(ip);
        // 黑名单判断
        if (AnonymousViewCheckMidware.isEnabled()
                && AnonymousViewCheckMidware.ipBlacklistCache.getIfPresent(ip) != null
                && !context.requestURI().equals("/test")
                && !context.requestURI().equals("/validateCaptcha")) {
            // 已经在黑名单，强制跳转到验证码页面
            context.sendRedirect("/test");
            System.out.println(ip + " 已经在黑名单中");
            return;
        }

        Locales.setLocale(Latkes.getLocale());

        Sessions.setTemplateDir(Symphonys.SKIN_DIR_NAME);
        Sessions.setMobile(false);
        Sessions.setAvatarViewMode(UserExt.USER_AVATAR_VIEW_MODE_C_ORIGINAL);

        fillBotAttrs(context);
        resolveSkinDir(context);
    }

    /**
     * Resolve skin (template) for the specified HTTP request.
     *
     * @param context the specified HTTP request context
     */
    private void resolveSkinDir(final RequestContext context) {
        if (Sessions.isBot() || context.getRequest().isStaticResource()) {
            return;
        }

        Stopwatchs.start("Resolve skin");

        final String templateDirName = Sessions.isMobile() ? "mobile" : "classic";
        Sessions.setTemplateDir(templateDirName);

        final Request request = context.getRequest();
        final Session httpSession = request.getSession();
        httpSession.setAttribute(Keys.TEMPLATE_DIR_NAME, templateDirName);

        try {
            final BeanManager beanManager = BeanManager.getInstance();
            final UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
            final OptionRepository optionRepository = beanManager.getReference(OptionRepository.class);

            final JSONObject optionLang = optionRepository.get(Option.ID_C_MISC_LANGUAGE);
            final String optionLangValue = optionLang.optString(Option.OPTION_VALUE);
            if ("0".equals(optionLangValue)) {
                Locales.setLocale(Locales.getLocale(request));
            } else {
                Locales.setLocale(Locales.getLocale(optionLangValue));
            }

            httpSession.setAttribute(Common.IP, getIpAddr(request));

            JSONObject user = userQueryService.getCurrentUser(request);
            if (null == user) {
                return;
            }

            httpSession.setAttribute(User.USER, user.toString());

            final String skin = Sessions.isMobile() ? user.optString(UserExt.USER_MOBILE_SKIN) : user.optString(UserExt.USER_SKIN);
            httpSession.setAttribute(Keys.TEMPLATE_DIR_NAME, skin);
            Sessions.setTemplateDir(skin);
            Sessions.setAvatarViewMode(user.optInt(UserExt.USER_AVATAR_VIEW_MODE));
            Sessions.setUser(user);
            Sessions.setLoggedIn(true);

            final Locale locale = Locales.getLocale(user.optString(UserExt.USER_LANGUAGE));
            Locales.setLocale(locale);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Resolves skin failed", e);
        } finally {
            Stopwatchs.end();
        }
    }

    private static void fillBotAttrs(final RequestContext context) {
        final String userAgentStr = context.header(Common.USER_AGENT);
        final UserAgent userAgent = UserAgent.parseUserAgentString(userAgentStr);
        BrowserType browserType = userAgent.getBrowser().getBrowserType();
        if (StringUtils.containsIgnoreCase(userAgentStr, "mobile")
                || StringUtils.containsIgnoreCase(userAgentStr, "MQQBrowser")
                || StringUtils.containsIgnoreCase(userAgentStr, "iphone")
                || StringUtils.containsIgnoreCase(userAgentStr, "MicroMessenger")
                || StringUtils.containsIgnoreCase(userAgentStr, "CFNetwork")
                || StringUtils.containsIgnoreCase(userAgentStr, "Android")) {
            browserType = BrowserType.MOBILE_BROWSER;
        } else if (StringUtils.containsIgnoreCase(userAgentStr, "Iframely")
                || StringUtils.containsIgnoreCase(userAgentStr, "Google")
                || StringUtils.containsIgnoreCase(userAgentStr, "BUbiNG")
                || StringUtils.containsIgnoreCase(userAgentStr, "ltx71")) {
            browserType = BrowserType.ROBOT;
        } else if (BrowserType.UNKNOWN == browserType) {
            if (!StringUtils.containsIgnoreCase(userAgentStr, "Java")
                    && !StringUtils.containsIgnoreCase(userAgentStr, "MetaURI")
                    && !StringUtils.containsIgnoreCase(userAgentStr, "Feed")
                    && !StringUtils.containsIgnoreCase(userAgentStr, "okhttp")
                    && !StringUtils.containsIgnoreCase(userAgentStr, "Sym")
                    && !StringUtils.containsIgnoreCase(userAgentStr, "axios")
                    && !StringUtils.containsIgnoreCase(userAgentStr, "node")
                    && !StringUtils.containsIgnoreCase(userAgentStr, "RSS")
                    && !StringUtils.containsIgnoreCase(userAgentStr, "apifox")) {
                LOGGER.log(Level.WARN, "Unknown client [UA=" + userAgentStr + ", remoteAddr="
                        + Requests.getRemoteAddr(context.getRequest()) + ", URI=" + context.requestURI() + "]");
            }
        }

        if (BrowserType.ROBOT == browserType) {
            LOGGER.log(Level.DEBUG, "Request made from a search engine [User-Agent={}]", context.header(Common.USER_AGENT));
            Sessions.setBot(true);
            return;
        }

        Sessions.setBot(false);
        Sessions.setMobile(BrowserType.MOBILE_BROWSER == browserType);
    }

    /**
     * 获取 IP地址
     * 使用 Nginx等反向代理软件， 则不能通过 request.getRemoteAddr()获取 IP地址
     * 如果使用了多级反向代理的话，X-Forwarded-For的值并不止一个，而是一串IP地址，
     * X-Forwarded-For中第一个非 unknown的有效IP字符串，则为真实IP地址
     */
    public static String getIpAddr(Request request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0) {
            ip = request.getRemoteAddr();
        }
        return "[0:0:0:0:0:0:0:1]".equals(ip) ? "127.0.0.1" : ip;
    }
}
