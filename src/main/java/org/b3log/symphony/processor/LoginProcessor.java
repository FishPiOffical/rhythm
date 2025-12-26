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

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.URLs;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.validate.UserForgetPwdValidationMidware;
import org.b3log.symphony.processor.middleware.validate.UserRegister2ValidationMidware;
import org.b3log.symphony.processor.middleware.validate.UserRegisterValidationMidware;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.QiniuTextCensor;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login/Register processor.
 * <ul>
 * <li>Registration (/register), GET/POST</li>
 * <li>Login (/login), GET/POST</li>
 * <li>Logout (/logout), GET</li>
 * <li>Reset password (/reset-pwd), GET/POST</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @version 2.0.0.1, May 31, 2020
 * @since 0.2.0
 */
@Singleton
public class LoginProcessor {

    /**
     * Wrong password tries.
     * <p>
     * &lt;userId, {"wrongCount": int, "captcha": ""}&gt;
     * </p>
     */
    public static final Map<String, JSONObject> WRONG_PWD_TRIES = new ConcurrentHashMap<>();
    /**
     * Login lock by IP: <ip, unlockAtMillis>.
     */
    private static final Map<String, Long> LOGIN_IP_LOCK = new ConcurrentHashMap<>();

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(LoginProcessor.class);

    /**
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

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
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Verifycode management service.
     */
    @Inject
    private VerifycodeMgmtService verifycodeMgmtService;

    /**
     * Verifycode query service.
     */
    @Inject
    private VerifycodeQueryService verifycodeQueryService;

    /**
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

    /**
     * Invitecode query service.
     */
    @Inject
    private InvitecodeQueryService invitecodeQueryService;

    /**
     * Invitecode management service.
     */
    @Inject
    private InvitecodeMgmtService invitecodeMgmtService;

    /**
     * Invitecode management service.
     */
    @Inject
    private NotificationMgmtService notificationMgmtService;

    /**
     * Role query service.
     */
    @Inject
    private RoleQueryService roleQueryService;

    /**
     * Tag query service.
     */
    @Inject
    private TagQueryService tagQueryService;

    @Inject
    private LivenessQueryService livenessQueryService;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);
        final UserForgetPwdValidationMidware userForgetPwdValidationMidware = beanManager.getReference(UserForgetPwdValidationMidware.class);
        final UserRegisterValidationMidware userRegisterValidationMidware = beanManager.getReference(UserRegisterValidationMidware.class);
        final UserRegister2ValidationMidware userRegister2ValidationMidware = beanManager.getReference(UserRegister2ValidationMidware.class);

        final LoginProcessor loginProcessor = beanManager.getReference(LoginProcessor.class);
        Dispatcher.post("/guide/next", loginProcessor::nextGuideStep, loginCheck::handle);
        Dispatcher.get("/guide", loginProcessor::showGuide, loginCheck::handle, csrfMidware::fill);
        Dispatcher.get("/login", loginProcessor::showLogin);
        Dispatcher.get("/forget-pwd", loginProcessor::showForgetPwd);
        Dispatcher.post("/forget-pwd", loginProcessor::forgetPwd, userForgetPwdValidationMidware::handle);
        Dispatcher.get("/reset-pwd", loginProcessor::showResetPwd);
        Dispatcher.post("/reset-pwd", loginProcessor::resetPwd);
        Dispatcher.get("/register", loginProcessor::showRegister);
        Dispatcher.get("/verify", loginProcessor::verify);
        Dispatcher.post("/register", loginProcessor::register, userRegisterValidationMidware::handle);
        Dispatcher.post("/register2", loginProcessor::register2, userRegister2ValidationMidware::handle);
        Dispatcher.post("/login", loginProcessor::login);
        Dispatcher.get("/logout", loginProcessor::logout);
    }

    /**
     * Next guide step.
     *
     * @param context the specified context
     */
    public void nextGuideStep(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        JSONObject requestJSONObject;
        try {
            requestJSONObject = context.requestJSON();
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage());
            return;
        }

        JSONObject user = Sessions.getUser();
        final String userId = user.optString(Keys.OBJECT_ID);

        int step = requestJSONObject.optInt(UserExt.USER_GUIDE_STEP);

        if (UserExt.USER_GUIDE_STEP_STAR_PROJECT < step || UserExt.USER_GUIDE_STEP_FIN >= step) {
            step = UserExt.USER_GUIDE_STEP_FIN;
        }

        try {
            user = userQueryService.getUser(userId);
            user.put(UserExt.USER_GUIDE_STEP, step);
            userMgmtService.updateUser(userId, user);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Guide next step [" + step + "] failed", e);
            return;
        }

        context.renderJSON(StatusCodes.SUCC);
    }

    /**
     * Shows guide page.
     *
     * @param context the specified context
     */
    public void showGuide(final RequestContext context) {
        final JSONObject currentUser = Sessions.getUser();
        final int step = currentUser.optInt(UserExt.USER_GUIDE_STEP);
        if (UserExt.USER_GUIDE_STEP_FIN == step) {
            context.sendRedirect(Latkes.getServePath());
            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "verify/guide.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModel.put(Common.CURRENT_USER, currentUser);

        final List<JSONObject> tags = tagQueryService.getTags(32);
        dataModel.put(Tag.TAGS, tags);

        final List<JSONObject> users = userQueryService.getNiceUsers(6);
        final Iterator<JSONObject> iterator = users.iterator();
        while (iterator.hasNext()) {
            final JSONObject user = iterator.next();
            if (user.optString(Keys.OBJECT_ID).equals(currentUser.optString(Keys.OBJECT_ID))) {
                iterator.remove();
                break;
            }
        }
        dataModel.put(User.USERS, users);

        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    /**
     * Shows login page.
     *
     * @param context the specified context
     */
    public void showLogin(final RequestContext context) {
        if (Sessions.isLoggedIn()) {
            context.sendRedirect(Latkes.getServePath());
            return;
        }

        String referer = context.param(Common.GOTO);
        if (StringUtils.isBlank(referer)) {
            referer = context.header("referer");
        }

        if (!StringUtils.startsWith(referer, Latkes.getServePath())) {
            referer = Latkes.getServePath();
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "verify/login.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModel.put(Common.GOTO, URLs.encode(referer));

        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    /**
     * Shows forget password page.
     *
     * @param context the specified context
     */
    public void showForgetPwd(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "verify/forget-pwd.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    /**
     * Forget password.
     *
     * @param context the specified context
     */
    public void forgetPwd(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final JSONObject requestJSONObject = context.requestJSON();
        final String phone = requestJSONObject.optString("userPhone");

        try {
            final JSONObject user = userQueryService.getUserByPhone(phone);
            if (null == user || UserExt.USER_STATUS_C_VALID != user.optInt(UserExt.USER_STATUS)) {
                context.renderMsg(langPropsService.get("notFoundUserLabel"));
                return;
            }

            final String userId = user.optString(Keys.OBJECT_ID);
            final String ip = Requests.getRemoteAddr(context.getRequest());
            final String name = user.optString(User.USER_NAME);
            if (verifySMSCodeLimiterOfIPLong.access(ip) && verifySMSCodeLimiterOfIP.access(ip) && verifySMSCodeLimiterOfName.access(name) && verifySMSCodeLimiterOfPhone.access(phone)) {
                final String code = RandomStringUtils.randomNumeric(6);
                if (!verifycodeMgmtService.sendVerifyCodeSMS(phone, code)) {
                    context.renderMsg("验证码发送失败，请稍候重试");
                    return;
                }

                final JSONObject verifycode = new JSONObject();
                verifycode.put(Verifycode.BIZ_TYPE, Verifycode.BIZ_TYPE_C_RESET_PWD);
                verifycode.put(Verifycode.CODE, code);
                verifycode.put(Verifycode.EXPIRED, DateUtils.addDays(new Date(), 1).getTime());
                verifycode.put(Verifycode.RECEIVER, phone);
                verifycode.put(Verifycode.STATUS, Verifycode.STATUS_C_UNSENT);
                verifycode.put(Verifycode.TYPE, Verifycode.TYPE_C_PHONE);
                verifycode.put(Verifycode.USER_ID, userId);
                verifycodeMgmtService.addVerifycode(verifycode);

                context.renderJSON(StatusCodes.SUCC).renderMsg("验证码已通过短信的形式发送至您的手机，请查收。");
            } else {
                context.renderMsg("验证码发送频率过快，请稍候重试");
            }
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("resetPwdLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg + "[phone=" + phone + "]");
            context.renderMsg(msg);
        }
    }

    /**
     * Shows reset password page.
     *
     * @param context the specified context
     */
    public static SimpleCurrentLimiter resetCodeLimiter = new SimpleCurrentLimiter(60, 4);
    public void showResetPwd(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, null);
        context.setRenderer(renderer);
        final Map<String, Object> dataModel = renderer.getDataModel();

        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (resetCodeLimiter.access(ip)) {
            final String code = context.param("code");
            final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
            if (null == verifycode) {
                dataModel.put(Keys.MSG, langPropsService.get("verifycodeExpiredLabel"));
                renderer.setTemplateName("error/custom.ftl");
            } else {
                renderer.setTemplateName("verify/reset-pwd.ftl");

                final String userId = verifycode.optString(Verifycode.USER_ID);
                final JSONObject user = userQueryService.getUser(userId);
                dataModel.put(User.USER, user);
                dataModel.put(Keys.CODE, code);
            }
        } else {
            dataModel.put(Keys.MSG, "验证码尝试次数过快，请稍候重试！");
            renderer.setTemplateName("error/custom.ftl");
        }

        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    /**
     * Resets password.
     *
     * @param context the specified context
     */
    public void resetPwd(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (resetCodeLimiter.access(ip)) {
            final Response response = context.getResponse();
            final JSONObject requestJSONObject = context.requestJSON();
            final String password = requestJSONObject.optString(User.USER_PASSWORD); // Hashed
            final String userId = requestJSONObject.optString(UserExt.USER_T_ID);
            final String code = requestJSONObject.optString(Keys.CODE);
            final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
            if (null == verifycode || !verifycode.optString(Verifycode.USER_ID).equals(userId)) {
                context.renderMsg(langPropsService.get("verifycodeExpiredLabel"));
                return;
            }

            String name = null;
            String phone = null;
            try {
                final JSONObject user = userQueryService.getUser(userId);
                if (null == user || UserExt.USER_STATUS_C_VALID != user.optInt(UserExt.USER_STATUS)) {
                    context.renderMsg(langPropsService.get("resetPwdLabel") + " - " + "User Not Found");
                    return;
                }

                name = user.optString(User.USER_NAME);
                phone = user.optString("userPhone");

                user.put(User.USER_PASSWORD, password);
                userMgmtService.updatePassword(user);
                verifycodeMgmtService.removeByCode(code);
                context.renderJSON(StatusCodes.SUCC);
                LOGGER.info("User [phone=" + phone + "] reseted password");
                Sessions.login(response, userId, true);
            } catch (final ServiceException e) {
                final String msg = langPropsService.get("resetPwdLabel") + " - " + e.getMessage();
                LOGGER.log(Level.ERROR, msg + "[name={}, phone={}]", name, phone);
                context.renderMsg(msg);
            }
        } else {
            context.renderMsg("验证码尝试次数过快，请稍候重试！");
        }
    }

    /**
     * verify SMS code
     *
     * @param context the specified context
     */
    public void verify(final RequestContext context) {
        JsonRenderer renderer = new JsonRenderer();
        renderer.setJSONObject(new JSONObject());
        context.setRenderer(renderer);
        final String code = context.param("code");
        if (StringUtils.isBlank(code)) {
            context.renderCodeMsg(-1, "短信验证码为空");
            return;
        }
        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (verifyCodeLimiter.access(ip)) {
            final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
            if (null == verifycode) {
                context.renderCodeMsg(-1, "短信验证码不正确");
            } else {
                final String userId = verifycode.optString(Verifycode.USER_ID);
                final JSONObject user = userQueryService.getUser(userId);
                if (UserExt.USER_STATUS_C_VALID == user.optInt(UserExt.USER_STATUS)
                        || UserExt.NULL_USER_NAME.equals(user.optString(User.USER_NAME))) {
                    context.renderCodeMsg(-1, langPropsService.get("userExistLabel"));
                    return;
                }
                context.renderJSON(new JSONObject().put("userId", userId)).renderCode(StatusCodes.SUCC).renderMsg("");
            }
        } else {
            context.renderCodeMsg(-1, "验证码尝试次数过快，请稍候重试！");
        }

    }

    /**
     * Shows registration page.
     *
     * @param context the specified context
     */
    public static SimpleCurrentLimiter verifyCodeLimiter = new SimpleCurrentLimiter(60, 4);
    public void showRegister(final RequestContext context) {
        if (Sessions.isLoggedIn()) {
            context.sendRedirect(Latkes.getServePath());
            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, null);
        final Map<String, Object> dataModel = renderer.getDataModel();

        boolean useInvitationLink = false;

        String referral = context.param("r");
        if (!UserRegisterValidationMidware.invalidUserName(referral)) {
            final JSONObject referralUser = userQueryService.getUserByName(referral);
            if (null != referralUser) {
                final Map<String, JSONObject> permissions =
                        roleQueryService.getUserPermissionsGrantMap(referralUser.optString(Keys.OBJECT_ID));
                final JSONObject useILPermission =
                        permissions.get(Permission.PERMISSION_ID_C_COMMON_USE_INVITATION_LINK);
                useInvitationLink = useILPermission.optBoolean(Permission.PERMISSION_T_GRANT);
            }
        }

        final String code = context.param("code");
        if (StringUtils.isBlank(code)) { // Register Step 1
            renderer.setTemplateName("verify/register.ftl");
        } else { // Register Step 2
            final String ip = Requests.getRemoteAddr(context.getRequest());
            if (verifyCodeLimiter.access(ip)) {
                final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
                if (null == verifycode) {
                    dataModel.put(Keys.MSG, langPropsService.get("verifycodeExpiredLabel"));
                    renderer.setTemplateName("error/custom.ftl");
                } else {
                    renderer.setTemplateName("verify/register2.ftl");

                    final String userId = verifycode.optString(Verifycode.USER_ID);
                    final JSONObject user = userQueryService.getUser(userId);
                    dataModel.put(User.USER, user);

                    if (UserExt.USER_STATUS_C_VALID == user.optInt(UserExt.USER_STATUS)
                            || UserExt.NULL_USER_NAME.equals(user.optString(User.USER_NAME))) {
                        dataModel.put(Keys.MSG, langPropsService.get("userExistLabel"));
                        renderer.setTemplateName("error/custom.ftl");
                    }
                }
            } else {
                dataModel.put(Keys.MSG, "验证码尝试次数过快，请稍候重试！");
                renderer.setTemplateName("error/custom.ftl");
            }
        }

        final String allowRegister = optionQueryService.getAllowRegister();
        dataModel.put(Option.ID_C_MISC_ALLOW_REGISTER, allowRegister);
        if (useInvitationLink && "2".equals(allowRegister)) {
            dataModel.put(Option.ID_C_MISC_ALLOW_REGISTER, "1");
        }

        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    /**
     * Register Step 1.
     *
     * @param context the specified context
     */
    public static SimpleCurrentLimiter verifySMSCodeLimiterOfIPLong = new SimpleCurrentLimiter(60 * 60 * 24, 3);
    public static SimpleCurrentLimiter verifySMSCodeLimiterOfIP = new SimpleCurrentLimiter(600, 2);
    public static SimpleCurrentLimiter verifySMSCodeLimiterOfName = new SimpleCurrentLimiter(600, 2);
    public static SimpleCurrentLimiter verifySMSCodeLimiterOfPhone = new SimpleCurrentLimiter(600, 2);
    public void register(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        final String ip = Requests.getRemoteAddr(context.getRequest());
        final JSONObject requestJSONObject = context.getRequest().getJSON();
        final String name = requestJSONObject.optString(User.USER_NAME);
        final String userPhone = requestJSONObject.optString("userPhone");
        if (verifySMSCodeLimiterOfIPLong.access(ip) && verifySMSCodeLimiterOfIP.access(ip) && verifySMSCodeLimiterOfName.access(name) && verifySMSCodeLimiterOfPhone.access(userPhone)) {
            // 敏感词检测
            JSONObject censorResult = QiniuTextCensor.censor(name);
            if (censorResult.optString("do").equals("block")) {
                context.renderMsg("用户名含违规信息，请修改后重试。");
                return;
            }

            final String invitecode = requestJSONObject.optString(Invitecode.INVITECODE);

            final JSONObject user = new JSONObject();
            user.put(User.USER_NAME, name);
            user.put("userPhone", userPhone);
            user.put(User.USER_PASSWORD, "");
            final Locale locale = Locales.getLocale();
            user.put(UserExt.USER_LANGUAGE, locale.getLanguage() + "_" + locale.getCountry());

            try {
                final String newUserId = userMgmtService.addUser(user);
                final String code = RandomStringUtils.randomNumeric(6);

                final JSONObject verifycode = new JSONObject();
                verifycode.put(Verifycode.BIZ_TYPE, Verifycode.BIZ_TYPE_C_REGISTER);
                verifycode.put(Verifycode.CODE, code);
                verifycode.put(Verifycode.EXPIRED, DateUtils.addDays(new Date(), 1).getTime());
                verifycode.put(Verifycode.RECEIVER, userPhone);
                verifycode.put(Verifycode.STATUS, Verifycode.STATUS_C_UNSENT);
                verifycode.put(Verifycode.TYPE, Verifycode.TYPE_C_PHONE);
                verifycode.put(Verifycode.USER_ID, newUserId);
                verifycodeMgmtService.addVerifycode(verifycode);
                LOGGER.log(Level.INFO, "Generated a verify code for registering [userName={}, phone={}, code={}]", name, userPhone, code);

                final String allowRegister = optionQueryService.getAllowRegister();
                if ("2".equals(allowRegister) && StringUtils.isNotBlank(invitecode)) {
                    final JSONObject ic = invitecodeQueryService.getInvitecode(invitecode);
                    ic.put(Invitecode.USER_ID, newUserId);
                    ic.put(Invitecode.USE_TIME, System.currentTimeMillis());
                    final String icId = ic.optString(Keys.OBJECT_ID);

                    invitecodeMgmtService.updateInvitecode(icId, ic);
                }

                if (!verifycodeMgmtService.sendVerifyCodeSMS(userPhone, code)) {
                    context.renderMsg("验证码发送失败，请稍候重试");
                    return;
                }

                context.renderJSON(StatusCodes.SUCC).renderMsg(langPropsService.get("verifycodeSentLabel"));
            } catch (final ServiceException e) {
                final String msg = langPropsService.get("registerFailLabel") + " - " + e.getMessage();
                LOGGER.log(Level.ERROR, msg + "[name={}, phone={}]", name, userPhone);
                context.renderMsg(msg);
            }
        } else {
            context.renderMsg("验证码发送频率过快，请稍候重试");
        }
    }

    /**
     * Register Step 2.
     *
     * @param context the specified context
     */
    public void register2(final RequestContext context) {
        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (verifyCodeLimiter.access(ip)) {
            context.renderJSON(StatusCodes.ERR);

            final Request request = context.getRequest();
            final Response response = context.getResponse();
            final JSONObject requestJSONObject = context.getRequest().getJSON();

            final String password = requestJSONObject.optString(User.USER_PASSWORD); // Hashed
            final int appRole = requestJSONObject.optInt(UserExt.USER_APP_ROLE);
            String referral = context.param("r");
            if (referral == null) {
                referral = "";
            }
            final String userId = requestJSONObject.optString(UserExt.USER_T_ID);

            String name = null;
            String phone = null;
            try {
                final JSONObject user = userQueryService.getUser(userId);
                if (null == user) {
                    context.renderMsg(langPropsService.get("registerFailLabel") + " - " + "User Not Found").renderCode(-1);
                    return;
                }

                name = user.optString(User.USER_NAME);
                phone = user.optString("userPhone");

                user.put(UserExt.USER_APP_ROLE, appRole);
                user.put(User.USER_PASSWORD, password);
                user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
                user.put("mbti", requestJSONObject.optString("mbti"));

                userMgmtService.addUser(user);

                Sessions.login(response, userId, false);

                //final String ip = Requests.getRemoteAddr(request);
                //userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), ip, true, true);

                if (StringUtils.isNotBlank(referral) && !UserRegisterValidationMidware.invalidUserName(referral)) {
                    final JSONObject referralUser = userQueryService.getUserByName(referral);
                    if (null != referralUser) {
                        final String referralId = referralUser.optString(Keys.OBJECT_ID);
                        pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, userId,
                                Pointtransfer.TRANSFER_TYPE_C_INVITED_REGISTER,
                                Pointtransfer.TRANSFER_SUM_C_INVITE_REGISTER, referralId, System.currentTimeMillis(), "");
                        pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, referralId,
                                Pointtransfer.TRANSFER_TYPE_C_INVITE_REGISTER,
                                Pointtransfer.TRANSFER_SUM_C_INVITE_REGISTER, userId, System.currentTimeMillis(), "");

                        final JSONObject notification = new JSONObject();
                        notification.put(Notification.NOTIFICATION_USER_ID, referralId);
                        notification.put(Notification.NOTIFICATION_DATA_ID, userId);
                        notificationMgmtService.addInvitationLinkUsedNotification(notification);
                    }
                }

                final JSONObject ic = invitecodeQueryService.getInvitecodeByUserId(userId);
                if (null != ic && Invitecode.STATUS_C_UNUSED == ic.optInt(Invitecode.STATUS)) {
                    ic.put(Invitecode.STATUS, Invitecode.STATUS_C_USED);
                    ic.put(Invitecode.USER_ID, userId);
                    ic.put(Invitecode.USE_TIME, System.currentTimeMillis());
                    final String icId = ic.optString(Keys.OBJECT_ID);

                    invitecodeMgmtService.updateInvitecode(icId, ic);

                    final String icGeneratorId = ic.optString(Invitecode.GENERATOR_ID);
                    if (StringUtils.isNotBlank(icGeneratorId) && !Pointtransfer.ID_C_SYS.equals(icGeneratorId)) {
                        pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, icGeneratorId,
                                Pointtransfer.TRANSFER_TYPE_C_INVITECODE_USED,
                                Pointtransfer.TRANSFER_SUM_C_INVITECODE_USED, userId, System.currentTimeMillis(), "");

                        final JSONObject notification = new JSONObject();
                        notification.put(Notification.NOTIFICATION_USER_ID, icGeneratorId);
                        notification.put(Notification.NOTIFICATION_DATA_ID, userId);

                        notificationMgmtService.addInvitecodeUsedNotification(notification);
                    }
                }

                // 天降红包
                String userName = user.optString(User.USER_NAME);
                Thread.startVirtualThread(() -> {
                    LOGGER.log(Level.INFO, "Red packet for joining matching...");
                    for (final String uId : UserChannel.SESSIONS.keySet()) {
                        // 获取活跃度
                        try {
                            Thread.sleep(500);
                        } catch (Exception ignored) {
                        }
                        final JSONObject yesterdayLiveness = livenessQueryService.getYesterdayLiveness(uId);
                        if (null != yesterdayLiveness) {
                            final int currentLiveness = Liveness.calcPoint(yesterdayLiveness);
                            final int livenessMax = Symphonys.ACTIVITY_YESTERDAY_REWARD_MAX;
                            float liveness = (float) (Math.round((float) currentLiveness / livenessMax * 100 * 100)) / 100;
                            if (liveness == 100) {
                                // 满活跃，发放奖励
                                // 范围 1-24
                                int random = new Random().nextInt(24) + 1;
                                LOGGER.log(Level.INFO, "Gave [from={}, for={}]", userName, uId);
                                pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, uId,
                                        Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_REDPACKET_FROM_SKY, random,
                                        userName, System.currentTimeMillis(), "");
                                // 发通知
                                try {
                                    final JSONObject notification = new JSONObject();
                                    notification.put(Notification.NOTIFICATION_USER_ID, uId);
                                    notification.put(Notification.NOTIFICATION_DATA_ID, userName + ":" + random);
                                    notificationMgmtService.addRedPacketFromSkyNotification(notification);
                                } catch (Exception e) {
                                    LOGGER.log(Level.ERROR, "Cannot add red packet from sky notification", e);
                                }
                            }
                        }
                    }
                    LOGGER.log(Level.INFO, "Red packet for joining match done.");
                });

                context.renderJSON(StatusCodes.SUCC);
                LOGGER.log(Level.INFO, "Registered a user [name={}, phone={}]", name, phone);
            } catch (final ServiceException e) {
                final String msg = langPropsService.get("registerFailLabel") + " - " + e.getMessage();
                LOGGER.log(Level.ERROR, msg + " [name={}, phone={}]", name, phone);
                context.renderMsg(msg).renderCode(-1);
            }
        } else {
            final JSONObject requestJSONObject = context.getRequest().getJSON();
            final String userId = requestJSONObject.optString(UserExt.USER_T_ID);
            final String password = requestJSONObject.optString(User.USER_PASSWORD); // Hashed
            LOGGER.log(Level.WARN, "Detected a user registration attack [ip={}, userId={}, password={}]", ip, userId, password);
            context.renderMsg("频率过快，请稍候重试").renderCode(-1);
        }
    }

    /**
     * Logins user.
     *
     * @param context the specified context
     */
    public void login(final RequestContext context) {
        final Request request = context.getRequest();
        final Response response = context.getResponse();
        context.renderJSON(StatusCodes.ERR).renderMsg(langPropsService.get("loginFailLabel"));
        final JSONObject requestJSONObject = context.requestJSON();
        final String nameOrEmail = requestJSONObject.optString("nameOrEmail");
        final String ip = Requests.getRemoteAddr(request);

        // IP-level lock: 5 minutes after too many failures
        final Long unlockAt = LOGIN_IP_LOCK.get(ip);
        if (unlockAt != null) {
            if (System.currentTimeMillis() < unlockAt) {
                context.renderMsg("尝试次数过多，请 5 分钟后再试");
                return;
            } else {
                LOGIN_IP_LOCK.remove(ip);
            }
        }

        try {
            JSONObject user = userQueryService.getUserByName(nameOrEmail);
            if (null == user) {
                user = userQueryService.getUserByEmail(nameOrEmail);
            }

            if (null == user) {
                user = userQueryService.getUserByPhone(nameOrEmail);
            }

            boolean userFound = true;
            if (null == user) {
                userFound = false;
                // fabricate a dummy user object to unify flow and avoid timing leaks
                user = new JSONObject();
                user.put(Keys.OBJECT_ID, "DUMMY");
                user.put(User.USER_PASSWORD, "");
                user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
            }

            if (UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS)) {
                //userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false, true);
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

            final String userId = user.optString(Keys.OBJECT_ID);
            JSONObject wrong = WRONG_PWD_TRIES.get(userId);
            if (null == wrong) {
                wrong = new JSONObject();
            }

            final int wrongCount = wrong.optInt(Common.WRON_COUNT);

            final String userPassword = user.optString(User.USER_PASSWORD);
            if (userFound && userPassword.equals(requestJSONObject.optString(User.USER_PASSWORD))) {
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

                final String token = Sessions.login(response, userId, requestJSONObject.optBoolean(Common.REMEMBER_LOGIN));

                //userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), ip, true, true);

                context.renderCodeMsg(StatusCodes.SUCC, "");
                context.renderJSONValue(Keys.TOKEN, token);

                WRONG_PWD_TRIES.remove(userId);
                LOGIN_IP_LOCK.remove(ip);
                return;
            }

            final int newWrongCount = wrongCount + 1;
            wrong.put(Common.WRON_COUNT, newWrongCount);
            WRONG_PWD_TRIES.put(userId, wrong);

            if (newWrongCount > 2) {
                LOGIN_IP_LOCK.put(ip, System.currentTimeMillis() + 5 * 60 * 1000);
                context.renderMsg("尝试次数过多，已暂时锁定 5 分钟");
            } else {
                context.renderMsg(langPropsService.get("wrongPwdLabel"));
            }
        } catch (final ServiceException e) {
            context.renderMsg(langPropsService.get("loginFailLabel"));
        }
    }

    /**
     * Logout.
     *
     * @param context the specified context
     */
    public void logout(final RequestContext context) {
        final JSONObject user = Sessions.getUser();
        if (null != user) {
            Sessions.logout(user.optString(Keys.OBJECT_ID), context.getRequest(), context.getResponse());
        }

        String destinationURL = context.param(Common.GOTO);
        if (StringUtils.isBlank(destinationURL)) {
            destinationURL = context.header("referer");
        }

        if (!StringUtils.startsWith(destinationURL, Latkes.getServePath())) {
            destinationURL = Latkes.getServePath();
        }

        context.sendRedirect(destinationURL);
    }
}
