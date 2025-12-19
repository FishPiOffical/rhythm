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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.qiniu.cdn.CdnManager;
import com.qiniu.cdn.CdnResult;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.util.Auth;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Crypts;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.bot.ChatRoomBot;
import org.b3log.symphony.processor.channel.ChatChannel;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.repository.UploadRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 专业团队，专业的 API 接口
 */
@Singleton
public class ApiProcessor {
    /**
     * Cookie value separator.
     */
    public static final String COOKIE_ITEM_SEPARATOR = ":";
    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ApiProcessor.class);

    /**
     * 存储用户的Key
     */
    /**
     * 通过用户名和密码进行登录，然后发放Key通行证
     */
    SimpleCurrentLimiter loginCurrentLimiter = new SimpleCurrentLimiter(10 * 60, 5);
    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;
    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;
    /**
     * Follow query service.
     */
    @Inject
    private FollowQueryService followQueryService;
    /**
     * Role query service.
     */
    @Inject
    private RoleQueryService roleQueryService;
    /**
     * System settings service.
     */
    @Inject
    private SystemSettingsService systemSettingsService;
    /**
     * Cloud service.
     */
    @Inject
    private CloudService cloudService;

    @Inject
    private UploadRepository uploadRepository;

    @Inject
    private ChatChannel chatChannel;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);

        final ApiProcessor apiProcessor = beanManager.getReference(ApiProcessor.class);
        Dispatcher.get("/api/user/exists/{user}", apiProcessor::userExists);
        Dispatcher.post("/api/getKey", apiProcessor::getKey);
        Dispatcher.get("/api/user", apiProcessor::getUser);
        Dispatcher.get("/api/user/getInfoById", apiProcessor::getUserInfoById);
        Dispatcher.get("/api/user/recentReg", apiProcessor::getRecentReg);

        final RewardQueryService rewardQueryService = beanManager.getReference(RewardQueryService.class);
        Dispatcher.get("/api/article/reward/senders/{aId}", rewardQueryService::rewardedSenders);
        Dispatcher.post(Symphonys.get("callback.url"), apiProcessor::callbackFromQiNiu);

        Dispatcher.get("/loginWebInApiKey", apiProcessor::loginWebInApiKey);
        Dispatcher.get("/getApiKeyInWeb", apiProcessor::getApiKeyInWeb, loginCheck::handle);

        Dispatcher.get("/ping", apiProcessor::ping, loginCheck::handle);
    }

    public void ping(final RequestContext context) {
        context.getResponse().sendString("pong");;
    }

    public void getApiKeyInWeb(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        String userId = currentUser.optString(Keys.OBJECT_ID);
        final String userPassword = currentUser.optString(User.USER_PASSWORD);

        final JSONObject cookieJSONObject = new JSONObject();
        cookieJSONObject.put(Keys.OBJECT_ID, userId);

        final String random = RandomStringUtils.randomAlphanumeric(16);
        cookieJSONObject.put(Keys.TOKEN, userPassword + COOKIE_ITEM_SEPARATOR + random);
        final String key = Crypts.encryptByAES(cookieJSONObject.toString(), Symphonys.COOKIE_SECRET_API);

        context.renderJSON(StatusCodes.SUCC).renderJSON(new JSONObject().put("apiKey", key));
    }

    public void loginWebInApiKey(final RequestContext context) {
        JSONObject currentUser;
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
            context.renderJSON(StatusCodes.ERR).renderMsg("ApiKey 错误。");
            return;
        }

        if (null != currentUser) {
            final Response response = context.getResponse();
            String r = context.param("r");
            if (null == r) {
                r = "/";
            }
            Sessions.login(response, currentUser.optString(Keys.OBJECT_ID), true);
            response.sendRedirect(Latkes.getServePath() + r);
        } else {
            context.renderJSON(StatusCodes.ERR).renderMsg("ApiKey 错误。");
        }
    }

    public void callbackFromQiNiu(final RequestContext context) {
        Auth auth = Auth.create(Symphonys.UPLOAD_QINIU_AK, Symphonys.UPLOAD_QINIU_SK);

        JSONObject jsonObject = context.requestJSON();
        LOGGER.log(Level.INFO, jsonObject.toString());

        String fileURL = Symphonys.get("callback.base.url") + jsonObject.optString("inputKey");
        String userName = "";
        final Query query = new Query().setFilter(new PropertyFilter("path", FilterOperator.EQUAL, fileURL));
        try {
            JSONObject uploadJSON = uploadRepository.getFirst(query);
            userName = uploadJSON.optString("userName");
        } catch (Exception e) {
            String suggestion = jsonObject.optJSONArray("items").optJSONObject(0).optJSONObject("result").optJSONObject("result").optString("suggestion");
            if (suggestion.equals("block") || suggestion.equals("review")) {
                try {
                    String[] urls = new String[]{fileURL};
                    CdnManager c = new CdnManager(auth);
                    CdnResult.RefreshResult result = c.refreshUrls(urls);
                    LOGGER.log(Level.INFO, "CDN Refresh result: " + result.code);
                    LOGGER.log(Level.WARN, "Avatar file " + fileURL);
                } catch (Exception f) {
                    LOGGER.error(f);
                }
            } else {
                LOGGER.log(Level.INFO, "Normal avatar file " + fileURL);
            }
        }

        if (!userName.isEmpty()) {
            String suggestion = jsonObject.optJSONArray("items").optJSONObject(0).optJSONObject("result").optJSONObject("result").optString("suggestion");
            String userId = userQueryService.getUserByName(userName).optString(Keys.OBJECT_ID);
            switch (suggestion) {
                case "block":
                    try {
                        String[] urls = new String[]{fileURL};
                        CdnManager c = new CdnManager(auth);
                        CdnResult.RefreshResult result = c.refreshUrls(urls);
                        LOGGER.log(Level.INFO, "CDN Refresh result: " + result.code);
                        LOGGER.log(Level.WARN, "Block file " + fileURL);
                        ChatChannel.sendAdminMsg(userName, "【AI审查】您由于上传违规文件/图片，被处以 100 积分的处罚，请引以为戒。\n如误报请在此处回复我，审核后找回积分并获得补偿！\n文件URL：" + fileURL);
                        ChatRoomBot.abusePoint(userId, 100, "[AI审查] [如有误报请联系管理员追回积分] 机器人罚单-上传违规文件");
                        LogsService.censorLog(context, userName, "用户：" + userName + " 上传违规图片：" + fileURL);
                    } catch (Exception e) {
                        LOGGER.error(e);
                    }
                    break;
                case "review":
                    try {
                        String[] urls = new String[]{fileURL};
                        CdnManager c = new CdnManager(auth);
                        CdnResult.RefreshResult result = c.refreshUrls(urls);
                        LOGGER.log(Level.INFO, "CDN Refresh result: " + result.code);
                        LOGGER.log(Level.WARN, "Review file " + fileURL);
                        ChatChannel.sendAdminMsg(userName, "【AI审查】您疑似上传违规文件/图片，被处以 1 积分的处罚，请引以为戒。\n如误报请在此处回复我，审核后找回积分并获得补偿！\n文件URL：" + fileURL);
                        ChatRoomBot.abusePoint(userId, 1, "[AI审查] [如有误报请联系管理员追回积分] 机器人罚单-上传疑似违规文件");
                        LogsService.censorLog(context, userName, "用户：" + userName + " 上传疑似违规图片：" + fileURL);
                    } catch (Exception e) {
                        LOGGER.error(e);
                    }
                    break;
                 default:
                    LOGGER.log(Level.INFO, "Normal file " + fileURL);
                    break;
            }
        }

        context.renderJSON(StatusCodes.SUCC);
    }

    /**
     * @param apiKey
     * @return userInfo
     * @throws NullPointerException if apiKey is null or not found in keymaps
     */
    private static final Cache<String, JSONObject> apiKeyUserCache = Caffeine.newBuilder()
            .expireAfterWrite(60, java.util.concurrent.TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    public static JSONObject getUserByKey(String apiKey) {
        if (apiKey != null && apiKey.length() == 192) {
            // 先查缓存
            JSONObject user = apiKeyUserCache.getIfPresent(apiKey);
            if (user != null) {
                return user;
            }
            // 缓存没有，解密+查库
            user = tryLogInWithApiKey(apiKey);
            if (user != null) {
                apiKeyUserCache.put(apiKey, user);
                return user;
            }
        }
        throw new NullPointerException();
    }

    /**
     * Tries to login with Api Key.
     *
     * @param apiKey the specified apikey
     * @return returns user if logged in, returns {@code null} otherwise
     */
    private static JSONObject tryLogInWithApiKey(String apiKey) {
        try {
            final String value = Crypts.decryptByAES(apiKey, Symphonys.COOKIE_SECRET_API);
            final JSONObject cookieJSONObject = new JSONObject(value);
            final BeanManager beanManager = BeanManager.getInstance();
            final UserRepository userRepository = beanManager.getReference(UserRepository.class);

            final String userId = cookieJSONObject.optString(Keys.OBJECT_ID);
            if (StringUtils.isBlank(userId)) {
                return null;
            }

            final JSONObject ret = userRepository.get(userId);
            if (null == ret) {
                return null;
            }

            if (ret.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) {
                return null;
            }

            final String userPassword = ret.optString(User.USER_PASSWORD);
            final String token = cookieJSONObject.optString(Keys.TOKEN);
            final String password = StringUtils.substringBeforeLast(token, COOKIE_ITEM_SEPARATOR);
            if (userPassword.equals(password)) {
                return ret;
            }
        } catch (final Exception e) {
            LOGGER.log(Level.DEBUG, "Parses apikey failed, clears apikey");
        }
        return null;
    }

    public void getKey(final RequestContext context) {
        final JSONObject requestJSONObject = context.requestJSON();
        final String nameOrEmail = requestJSONObject.optString("nameOrEmail");

        if (!loginCurrentLimiter.access(nameOrEmail)) {
            context.renderJSON(StatusCodes.ERR).renderMsg("登录频率过快，请5分钟后重试。");
            return;
        }

        context.renderJSON(StatusCodes.ERR).renderMsg(langPropsService.get("loginFailLabel"));

        try {
            JSONObject user = userQueryService.getUserByName(nameOrEmail);
            if (null == user) {
                user = userQueryService.getUserByEmail(nameOrEmail);
            }

            if (null == user) {
                user = userQueryService.getUserByPhone(nameOrEmail);
            }

            if (null == user) {
                context.renderMsg(langPropsService.get("notFoundUserLabel"));
                return;
            }

            if (UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS)) {
                context.renderMsg(langPropsService.get("userBlockLabel"));
                return;
            }

            if (UserExt.USER_STATUS_C_NOT_VERIFIED == user.optInt(UserExt.USER_STATUS)) {
                context.renderMsg(langPropsService.get("notVerifiedLabel"));
                return;
            }

            if (UserExt.USER_STATUS_C_INVALID_LOGIN == user.optInt(UserExt.USER_STATUS)
                    || UserExt.USER_STATUS_C_DEACTIVATED == user.optInt(UserExt.USER_STATUS)) {
                context.renderMsg(langPropsService.get("invalidLoginLabel"));
                return;
            }

            final String userPassword = user.optString(User.USER_PASSWORD);
            if (userPassword.equals(requestJSONObject.optString(User.USER_PASSWORD))) {
                final String userId = user.optString(Keys.OBJECT_ID);
                long code;
                try {
                    code = requestJSONObject.optLong("mfaCode");
                } catch (Exception e) {
                    code = 0;
                }
                if (!MFAProcessor.verifyLogin(userId, code)) {
                    context.renderMsg("两步验证失败，请填写正确的一次性密码");
                    return;
                }

                final JSONObject cookieJSONObject = new JSONObject();
                cookieJSONObject.put(Keys.OBJECT_ID, userId);

                final String random = RandomStringUtils.randomAlphanumeric(16);
                cookieJSONObject.put(Keys.TOKEN, userPassword + COOKIE_ITEM_SEPARATOR + random);
                final String key = Crypts.encryptByAES(cookieJSONObject.toString(), Symphonys.COOKIE_SECRET_API);

                context.renderCodeMsg(StatusCodes.SUCC, "");
                context.renderJSONValue("Key", key);

                return;
            }

            context.renderMsg(langPropsService.get("wrongPwdLabel"));
        } catch (final ServiceException e) {
            context.renderMsg(langPropsService.get("loginFailLabel"));
        }
    }

    /**
     * 查询指定apiKey的用户信息
     */
    public void getUser(final RequestContext context) {
        JSONObject ret = new JSONObject();
        try {
            JSONObject user = ApiProcessor.getUserByKey(context.param("apiKey"));
            final JSONObject filteredUserProfile = new JSONObject();
            filteredUserProfile.put(User.USER_NAME, user.optString(User.USER_NAME));
            filteredUserProfile.put(UserExt.USER_ONLINE_FLAG, user.optBoolean(UserExt.USER_ONLINE_FLAG));
            filteredUserProfile.put(UserExt.ONLINE_MINUTE, user.optInt(UserExt.ONLINE_MINUTE));
            filteredUserProfile.put(User.USER_URL, user.optString(User.USER_URL));
            filteredUserProfile.put(UserExt.USER_NICKNAME, user.optString(UserExt.USER_NICKNAME));
            try {
                if (user.optInt(UserExt.USER_GEO_STATUS) == UserExt.USER_GEO_STATUS_C_PUBLIC) {
                    filteredUserProfile.put(UserExt.USER_CITY, user.optString(UserExt.USER_CITY));
                } else {
                    filteredUserProfile.put(UserExt.USER_CITY, "");
                }
            } catch (Exception ignored) {
                filteredUserProfile.put(UserExt.USER_CITY, "");
            }
            filteredUserProfile.put(UserExt.USER_AVATAR_URL, user.optString(UserExt.USER_AVATAR_URL));
            filteredUserProfile.put(UserExt.USER_POINT, user.optInt(UserExt.USER_POINT));
            filteredUserProfile.put(UserExt.USER_INTRO, user.optString(UserExt.USER_INTRO));
            filteredUserProfile.put(Keys.OBJECT_ID, user.optString(Keys.OBJECT_ID));
            filteredUserProfile.put(UserExt.USER_NO, user.optString(UserExt.USER_NO));
            filteredUserProfile.put(UserExt.USER_APP_ROLE, user.optString(UserExt.USER_APP_ROLE));
            filteredUserProfile.put("sysMetal", cloudService.getEnabledMedal(user.optString(Keys.OBJECT_ID)));
            final String userId = user.optString(Keys.OBJECT_ID);
            final long followerCnt = followQueryService.getFollowerCount(userId, Follow.FOLLOWING_TYPE_C_USER);
            filteredUserProfile.put("followerCount", followerCnt);
            final long followingUserCnt = followQueryService.getFollowingCount(userId, Follow.FOLLOWING_TYPE_C_USER);
            filteredUserProfile.put("followingUserCount", followingUserCnt);
            final String userRoleId = user.optString(User.USER_ROLE);
            final JSONObject role = roleQueryService.getRole(userRoleId);
            final String roleName = role.optString(Role.ROLE_NAME);
            filteredUserProfile.put(User.USER_ROLE, roleName);
            // 获取用户个性化设定
            final JSONObject systemSettings = systemSettingsService.getByUsrId(user.optString(Keys.OBJECT_ID));
            if (Objects.isNull(systemSettings)) {
                filteredUserProfile.put("cardBg", "");
            } else {
                final String settingsJson = systemSettings.optString(SystemSettings.SETTINGS);
                final JSONObject settings = new JSONObject(settingsJson);
                final String cardBg = settings.optString("cardBg");
                if (StringUtils.isBlank(cardBg)) {
                    filteredUserProfile.put("cardBg", "");
                } else {
                    filteredUserProfile.put("cardBg", cardBg);
                }
            }
            ret.put(Keys.CODE, StatusCodes.SUCC);
            ret.put(Keys.MSG, "");
            ret.put(Keys.DATA, filteredUserProfile);
            context.renderJSON(ret);
        } catch (Exception e) {
            ret.put(Keys.CODE, StatusCodes.ERR);
            ret.put(Keys.MSG, "Invalid Api Key.");
            context.renderJSON(ret);
        }
    }


    /**
     * 根据用户id查询信息
     */
    public void getUserInfoById(final RequestContext context) {
        JSONObject ret = new JSONObject();
        try {
            JSONObject user = userQueryService.getUser(context.param("userId"));
            final JSONObject filteredUserProfile = new JSONObject();
            filteredUserProfile.put(User.USER_NAME, user.optString(User.USER_NAME));
            filteredUserProfile.put(UserExt.USER_NICKNAME, user.optString(UserExt.USER_NICKNAME));
            filteredUserProfile.put(UserExt.USER_AVATAR_URL, user.optString(UserExt.USER_AVATAR_URL));

            ret.put(Keys.CODE, StatusCodes.SUCC);
            ret.put(Keys.MSG, "");
            ret.put(Keys.DATA, filteredUserProfile);
            context.renderJSON(ret);
        } catch (Exception e) {
            ret.put(Keys.CODE, StatusCodes.ERR);
            ret.put(Keys.MSG, "Query user error");
            context.renderJSON(ret);
        }
    }



    /**
     * 获取最近注册的20个鱼油  只需要用户名和昵称吧
     * @param context
     */
    public void getRecentReg(final RequestContext context) {
        JSONObject ret = new JSONObject();
        try {
            ret.put(Keys.CODE, StatusCodes.SUCC);
            ret.put(Keys.MSG, "");
            List<JSONObject> users = userQueryService.getRecentRegisteredUsers(20);
            ret.put(Keys.DATA, users.stream().map(
                    x -> {
                        JSONObject user = new JSONObject();
                        user.put(User.USER_NAME, x.optString(User.USER_NAME));
                        user.put(UserExt.USER_NICKNAME, x.optString(UserExt.USER_NICKNAME));
                        return user;
                    }
            ).collect(Collectors.toList()));
            context.renderJSON(ret);
        } catch (Exception e) {
            ret.put(Keys.CODE, StatusCodes.ERR);
            ret.put(Keys.MSG, "Invalid Api Key.");
            context.renderJSON(ret);
        }
    }
    public void userExists(final RequestContext context) {
        String user = context.pathVar("user");
        JSONObject userJSON = userQueryService.getUserByName(user);
        if (userJSON == null) {
            context.renderJSON(StatusCodes.ERR);
            return;
        }
        context.renderJSON(StatusCodes.SUCC);
    }

}
