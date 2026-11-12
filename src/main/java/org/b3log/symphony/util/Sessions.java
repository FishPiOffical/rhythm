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

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.cache.Cache;
import org.b3log.latke.cache.CacheFactory;
import org.b3log.latke.http.*;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.model.User;
import org.b3log.latke.util.Crypts;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.service.UserMgmtService;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.b3log.symphony.service.UserQueryService;
import org.json.JSONObject;

import java.util.Objects;
import java.util.Set;

/**
 * Session utilities.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.1.2.0, May 31, 2020
 */
public final class Sessions {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(Sessions.class);

    /**
     * Session cache.
     */
    private static final Cache SESSION_CACHE = CacheFactory.getCache("sessions");

    /**
     * Cookie name.
     */
    public static final String COOKIE_NAME = "sym-ce";

    /**
     * Cookie value separator.
     */
    public static final String COOKIE_ITEM_SEPARATOR = ":";

    /**
     * Cookie expiry: 7 days.
     */
    private static final int COOKIE_EXPIRY = 60 * 60 * 24 * 7;

    /**
     * Thread local data
     */
    private static final ThreadLocal<JSONObject> THREAD_LOCAL_DATA = new ThreadLocal<>();

    /**
     * Checks whether is bot.
     *
     * @return {@code true} if it is bot, returns {@code false} otherwise
     */
    public static boolean isBot() {
        final JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            return false;
        }

        return data.optBoolean(Keys.HttpRequest.IS_SEARCH_ENGINE_BOT);
    }

    /**
     * Sets the specified bot flag into thread local data.
     *
     * @param isBot the specified bot flag
     */
    public static void setBot(final boolean isBot) {
        JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            data = new JSONObject().put(Keys.HttpRequest.IS_SEARCH_ENGINE_BOT, isBot);
            THREAD_LOCAL_DATA.set(data);
            return;
        }

        data.put(Keys.HttpRequest.IS_SEARCH_ENGINE_BOT, isBot);
    }

    /**
     * Checks whether is mobile.
     *
     * @return {@code true} if it is mobile, returns {@code false} otherwise
     */
    public static boolean isMobile() {
        final JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            return false;
        }

        return data.optBoolean(Common.IS_MOBILE);
    }

    /**
     * Sets the specified mobile flag into thread local data.
     *
     * @param isMobile the specified mobile flag
     */
    public static void setMobile(final boolean isMobile) {
        JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            data = new JSONObject().put(Common.IS_MOBILE, isMobile);
            THREAD_LOCAL_DATA.set(data);
            return;
        }

        data.put(Common.IS_MOBILE, isMobile);
    }

    /**
     * Gets the current avatar view mode from thread local data.
     *
     * @return avatar view mode, returns {@value UserExt#USER_AVATAR_VIEW_MODE_C_ORIGINAL} "original mode" if not found
     */
    public static int getAvatarViewMode() {
        JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            return UserExt.USER_AVATAR_VIEW_MODE_C_ORIGINAL;
        }

        return data.optInt(UserExt.USER_AVATAR_VIEW_MODE);
    }

    /**
     * Sets the specified avatar view mode into thread local data.
     *
     * @param avatarViewMode the specified avatar view mode
     */
    public static void setAvatarViewMode(final int avatarViewMode) {
        JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            data = new JSONObject().put(UserExt.USER_AVATAR_VIEW_MODE, avatarViewMode);
            THREAD_LOCAL_DATA.set(data);
            return;
        }

        data.put(UserExt.USER_AVATAR_VIEW_MODE, avatarViewMode);
    }

    /**
     * Checks whether is logged in.
     *
     * @return {@code true} if logged in, returns {@code false} otherwise
     */
    public static boolean isLoggedIn() {
        final JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            return false;
        }

        return data.optBoolean(Common.IS_LOGGED_IN);
    }

    /**
     * Sets the specified logged in flag into thread local data.
     *
     * @param isLoggedIn the specified logged in flag
     */
    public static void setLoggedIn(final boolean isLoggedIn) {
        JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            data = new JSONObject().put(Common.IS_LOGGED_IN, isLoggedIn);
            THREAD_LOCAL_DATA.set(data);
            return;
        }

        data.put(Common.IS_LOGGED_IN, isLoggedIn);
    }

    /**
     * Gets the current user from thread local data.
     *
     * @return user, returns {@code null} if not found
     */
    public static JSONObject getUser() {
        JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            return null;
        }

        return data.optJSONObject(User.USER);
    }

    /**
     * Sets the specified user into thread local data.
     *
     * @param user the specified user
     */
    public static void setUser(final JSONObject user) {
        JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            data = new JSONObject().put(User.USER, user);
            THREAD_LOCAL_DATA.set(data);
            return;
        }

        data.put(User.USER, user);
    }

    /**
     * Gets the current template dir from thread local data.
     *
     * @return template dir, returns default desktop skin if not found
     */
    public static String getTemplateDir() {
        JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            return Symphonys.SKIN_DIR_NAME;
        }

        return data.optString(Keys.TEMPLATE_DIR_NAME);
    }

    /**
     * Sets the specified template dir into thread local data.
     *
     * @param templateDir the specified template dir
     */
    public static void setTemplateDir(final String templateDir) {
        JSONObject data = THREAD_LOCAL_DATA.get();
        if (null == data) {
            data = new JSONObject().put(Keys.TEMPLATE_DIR_NAME, templateDir);
            THREAD_LOCAL_DATA.set(data);
            return;
        }

        data.put(Keys.TEMPLATE_DIR_NAME, templateDir);
    }

    /**
     * Clears the thread local data.
     */
    public static void clearThreadLocalData() {
        THREAD_LOCAL_DATA.set(null);
        AccountCredentialVersions.endHttp();
    }

    /**
     * Gets CSRF token from the specified request.
     *
     * @param context the specified request context
     * @return CSRF token, returns {@code ""} if not found
     */
    public static String getCSRFToken(final RequestContext context) {
        return getCSRFTokenForUser(Sessions.getUser());
    }

    /** Explicit authenticated principal for Cookie-only consent; no thread-local apiKey identity fallback. */
    public static String getCSRFTokenForUser(final JSONObject user) {
        if (null == user) {
            return "";
        }

        final String userId = user.optString(Keys.OBJECT_ID);
        if (StringUtils.isBlank(userId)) {
            return "";
        }

        JSONObject csrfTokenValue = SESSION_CACHE.get(userId + Common.CSRF_TOKEN);
        if (null == csrfTokenValue) {
            csrfTokenValue = new JSONObject();
            csrfTokenValue.put(Common.DATA, RandomStringUtils.randomAlphanumeric(12));

            SESSION_CACHE.put(userId + Common.CSRF_TOKEN, csrfTokenValue);
        }

        return csrfTokenValue.optString(Common.DATA);
    }

    /**
     * Logins the specified user.
     *
     * @param response      the specified response
     * @param userId        the specified user id, for example,
     * @param rememberLogin remember login or not
     * @return token, returns {@code null} if login failed
     */
    public static String login(final Response response, final String userId, final boolean rememberLogin) {
        return loginWebsiteCookie(response, userId, rememberLogin, null);
    }

    /** 家族来自服务端签发结果，不接受请求参数指定。旧调用保持原有 Cookie 格式。 */
    public static String loginWebsiteCookie(final Response response, final String userId, final boolean rememberLogin, final String family) {
        try {
            final var proof = AccountCredentialVersions.issuance(userId, null);
            if (proof == null) return null;
            final BeanManager beanManager = BeanManager.getInstance();
            final UserRepository userRepository = beanManager.getReference(UserRepository.class);
            final JSONObject user = userRepository.get(userId);
            if (null == user) {
                LOGGER.log(Level.WARN, "Login user [id=" + userId + "] failed");

                return null;
            }

            user.put(User.USER_PASSWORD, proof.password());
            SESSION_CACHE.put(userId, user);

            final JSONObject csrfToken = new JSONObject();
            csrfToken.put(Common.DATA, RandomStringUtils.randomAlphanumeric(12));
            SESSION_CACHE.put(userId + Common.CSRF_TOKEN, csrfToken);

            final JSONObject cookieJSONObject = new JSONObject();
            cookieJSONObject.put(Keys.OBJECT_ID, user.optString(Keys.OBJECT_ID));
            cookieJSONObject.put("credentialEpoch", proof.epoch());
            if (family != null) {
                if (!family.matches("[a-f0-9]{32}")) return null;
                cookieJSONObject.put("websiteFamily", family);
            }

            final String random = RandomStringUtils.randomAlphanumeric(16);
            cookieJSONObject.put(Keys.TOKEN, proof.password() + COOKIE_ITEM_SEPARATOR + random);
            cookieJSONObject.put(Common.REMEMBER_LOGIN, rememberLogin);

            final String ret = Crypts.encryptByAES(cookieJSONObject.toString(), Symphonys.COOKIE_SECRET);
            final Cookie cookie = new Cookie(COOKIE_NAME, ret);
            cookie.setPath("/");
            if (rememberLogin) {
                cookie.setMaxAge(family == null ? COOKIE_EXPIRY : 30 * 24 * 60 * 60);
            }
            cookie.setHttpOnly(true);
            cookie.setSecure(StringUtils.equalsIgnoreCase(Latkes.getServerScheme(), "https"));

            if (family != null) cookie.setSameSite("Lax");
            response.addCookie(cookie);

            return ret;
        } catch (final Exception e) {
            LOGGER.log(Level.WARN, "Login user [id=" + userId + "] failed", e);

            return null;
        }
    }

    /**
     * Logouts the specified user.
     *
     * @param userId   the specified user id
     * @param response the specified response
     */
    public static void logout(final String userId, final Request request, final Response response) {
        if (null != response) {
            final Cookie cookie = new Cookie(COOKIE_NAME, "");
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);
        }

        SESSION_CACHE.remove(userId);

        final Session httpSession = request.getSession();
        httpSession.setAttribute(User.USER, null);

        //final BeanManager beanManager = BeanManager.getInstance();
        //final UserMgmtService userMgmtService = beanManager.getReference(UserMgmtService.class);
    }

    /**
     * Gets the current user with the specified request.
     *
     * @param request the specified request
     * @return the current user, returns {@code null} if not logged in
     */
    public static JSONObject currentUser(final Request request) {
        String apiKey = request.getParameter("apiKey");
        if (null == apiKey || apiKey.isEmpty()) {
            final JSONObject requestJSONObject = request.getJSON();
            apiKey = requestJSONObject.optString("apiKey");
        }
        if (Objects.nonNull(apiKey) && !apiKey.isEmpty()) {
            try {
                JSONObject user = ApiProcessor.getUserByKey(apiKey);
                if (Objects.nonNull(user)) {
                    return user;
                }

            } catch (NullPointerException ignored) {
            }
            return null;
        }

        return currentCookieUser(request);
    }

    /** 只解码用于撤销的绑定标识，不据此授予登录权限，也不依赖数据库可用性。 */
    public static JSONObject websiteSession(final Request request) {
        for (final Cookie cookie : request.getCookies()) {
            if (!COOKIE_NAME.equals(cookie.getName())) continue;
            try {
                if (cookie.getValue().length() > 1024) return null;
                final JSONObject value = new JSONObject(Crypts.decryptByAES(cookie.getValue(), Symphonys.COOKIE_SECRET));
                final String actor = value.optString(Keys.OBJECT_ID);
                final String family = value.optString("websiteFamily");
                if (actor.matches("[1-9][0-9]{0,18}") && family.matches("[a-f0-9]{32}"))
                    return new JSONObject().put(Keys.OBJECT_ID, actor).put("websiteFamily", family)
                            .put(Common.REMEMBER_LOGIN, value.optBoolean(Common.REMEMBER_LOGIN));
            } catch (final Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /** 状态探测只验证 Cookie 凭据；存储异常向上传递，不能被解释成用户已经退出。 */
    public static boolean websiteCredentialValid(final Request request, final String actor) {
        JSONObject payload = null;
        for (final Cookie cookie : request.getCookies()) {
            if (!COOKIE_NAME.equals(cookie.getName())) continue;
            try {
                if (cookie.getValue().length() > 1024) return false;
                payload = new JSONObject(Crypts.decryptByAES(cookie.getValue(), Symphonys.COOKIE_SECRET));
            } catch (final Exception invalid) {
                return false;
            }
            break;
        }
        if (payload == null || !actor.equals(payload.optString(Keys.OBJECT_ID))) return false;
        final String password = StringUtils.substringBeforeLast(payload.optString(Keys.TOKEN), COOKIE_ITEM_SEPARATOR);
        return AccountCredentialVersions.authenticate(actor, payload, password);
    }

    /** Existing Cookie validation branch, callable without the legacy apiKey-first resolver. */
    public static JSONObject currentCookieUser(final Request request) {
        AccountCredentialVersions.clear();
        final Set<Cookie> cookies = request.getCookies();
        if (cookies.isEmpty()) {
            return null;
        }

        try {
            for (final Cookie cookie : cookies) {
                if (!Sessions.COOKIE_NAME.equals(cookie.getName())) {
                    continue;
                }

                if (cookie.getValue().length() < 128 || cookie.getValue().length() > 1024) {
                    return null;
                }

                final String value = Crypts.decryptByAES(cookie.getValue(), Symphonys.COOKIE_SECRET);
                final JSONObject cookieJSONObject = new JSONObject(value);

                final String userId = cookieJSONObject.optString(Keys.OBJECT_ID);
                if (StringUtils.isBlank(userId)) {
                    return null;
                }

                JSONObject ret = SESSION_CACHE.get(userId);
                if (null == ret) {
                    ret = tryLogInWithCookie(cookieJSONObject, request);
                }
                if (null == ret) {
                    return null;
                }

                final String token = cookieJSONObject.optString(Keys.TOKEN);
                final String password = StringUtils.substringBeforeLast(token, COOKIE_ITEM_SEPARATOR);
                final String userPassword = ret.optString(User.USER_PASSWORD);
                if (!AccountCredentialVersions.authenticate(userId, cookieJSONObject, password)) {
                    return null;
                }
                if (cookieJSONObject.has("websiteFamily") && !BeanManager.getInstance().getReference(ApiV3TokenMgmtService.class)
                        .websiteFamilyActive(userId, cookieJSONObject.optString("websiteFamily"))) {
                    AccountCredentialVersions.clear();
                    return null;
                }
                ret.put(User.USER_PASSWORD, password);

                if (UserExt.USER_STATUS_C_INVALID == ret.optInt(UserExt.USER_STATUS)
                        || UserExt.USER_STATUS_C_INVALID_LOGIN == ret.optInt(UserExt.USER_STATUS)
                        || UserExt.USER_STATUS_C_DEACTIVATED == ret.optInt(UserExt.USER_STATUS)) {
                    SESSION_CACHE.remove(userId);
                    AccountCredentialVersions.clear();
                    return null;
                }

                final String ip = Requests.getRemoteAddr(request);
                if (StringUtils.isNotBlank(ip)) {
                    ret.put(UserExt.USER_LATEST_LOGIN_IP, ip);
                    SESSION_CACHE.put(userId, ret);
                }

                final JSONObject result = JSONs.clone(ret);
                if (cookieJSONObject.has("websiteFamily")) result.put("websiteFamily", cookieJSONObject.getString("websiteFamily"));
                return result;
            }
        } catch (final Exception ignored) {
        }

        AccountCredentialVersions.clear();
        return null;
    }

    /**
     * Tries to login with cookie.
     *
     * @param cookieJSONObject the specified cookie json object
     * @param request          the specified request
     * @return returns user if logged in, returns {@code null} otherwise
     */
    private static JSONObject tryLogInWithCookie(final JSONObject cookieJSONObject,
                                                 final Request request) {
        final BeanManager beanManager = BeanManager.getInstance();
        final UserRepository userRepository = beanManager.getReference(UserRepository.class);
        final UserMgmtService userMgmtService = beanManager.getReference(UserMgmtService.class);

        try {
            final String userId = cookieJSONObject.optString(Keys.OBJECT_ID);
            if (StringUtils.isBlank(userId)) {
                return null;
            }

            final JSONObject ret = userRepository.get(userId);
            if (null == ret) {
                return null;
            }

            final String ip = Requests.getRemoteAddr(request);
            if (StringUtils.isNotBlank(ip)) {
                ret.put(UserExt.USER_LATEST_LOGIN_IP, ip);
            }

            final String userPassword = ret.optString(User.USER_PASSWORD);
            final String token = cookieJSONObject.optString(Keys.TOKEN);
            final String password = StringUtils.substringBeforeLast(token, COOKIE_ITEM_SEPARATOR);
            if (AccountCredentialVersions.authenticate(userId, cookieJSONObject, password)) {
                ret.put(User.USER_PASSWORD, password);

                SESSION_CACHE.put(userId, ret);

                return ret;
            }
        } catch (final Exception ignored) {
        }

        return null;
    }

    /** Native sockets retain the credential version captured by the HTTP handshake, never a fresh upgrade. */
    public static JSONObject currentSocketUser(final WebSocketSession session) {
        final boolean http=AccountCredentialVersions.isHttp();
        final boolean release=!http&&!AccountCredentialVersions.hasActiveTransaction();
        final var previous=AccountCredentialVersions.saved();
        try {
            String apiKey=session.getParameter("apiKey");
            if(apiKey!=null&&!apiKey.isEmpty())return ApiProcessor.getUserByKeyForSocket(apiKey);
            final Session state=session.getHttpSession();
            final String raw=state.getAttribute(User.USER);
            if(raw==null)return null;
            final JSONObject user=new JSONObject(raw);
            final String actor=user.optString(Keys.OBJECT_ID);
            final String captured=state.getAttribute("accountCredentialEpoch");
            final long epoch=captured==null?0:Long.parseLong(captured);
            final var current=AccountCredentialVersions.current(actor);
            if(epoch<0||current==null||current.epoch()!=epoch||!current.password().equals(user.optString(User.USER_PASSWORD)))return null;
            // 已建立的旧站 WebSocket 也要遵守该浏览器会话的撤销状态。
            if (user.has("websiteFamily") && !BeanManager.getInstance().getReference(ApiV3TokenMgmtService.class)
                    .websiteFamilyActive(actor, user.optString("websiteFamily"))) return null;
            return user;
        }catch(Exception e){return null;}
        finally {
            AccountCredentialVersions.remember(http?previous:null);
            if(release)ApiV3Transactions.releaseThreadResources();
        }
    }

    /**
     * Gets a value from session cache with the specified key.
     *
     * @param key the specified key
     * @return session, returns {@code null} if not found
     */
    public static JSONObject get(final String key) {
        JSONObject ret = SESSION_CACHE.get(key);
        if (null == ret) {
            return null;
        }

        return JSONs.clone(ret);
    }

    /**
     * Puts a value into session cache with the specified key and value.
     *
     * @param key   the specified key
     * @param value the specified value
     */
    public static void put(final String key, final JSONObject value) {
        SESSION_CACHE.put(key, value);
    }

    /**
     * 使指定用户的服务端会话立即失效。
     *
     * @param userId 用户 ID
     */
    public static void invalidate(final String userId) {
        SESSION_CACHE.remove(userId);
        SESSION_CACHE.remove(userId + Common.CSRF_TOKEN);
    }

    /**
     * Private constructor.
     */
    private Sessions() {
    }
}
