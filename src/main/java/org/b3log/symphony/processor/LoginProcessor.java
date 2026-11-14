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
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.validate.UserForgetPwdValidationMidware;
import org.b3log.symphony.processor.middleware.validate.UserRegister2ValidationMidware;
import org.b3log.symphony.processor.middleware.validate.UserRegisterValidationMidware;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.WebsiteLoginCookies;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.AccountCredentialVersions;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;
import org.b3log.symphony.util.PasswordResetQuota;
import org.b3log.symphony.util.AccountSmsQuota;
import org.b3log.symphony.util.RegistrationVerificationQuota;
import org.b3log.symphony.util.ApiV3Exception;

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
    @Inject private AccountPasswordResetService passwordResets;
    @Inject private AccountRegistrationService registrations;
    @Inject private AccountRegistrationRequestService registrationRequests;

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
     * Role query service.
     */
    @Inject
    private RoleQueryService roleQueryService;

    /**
     * Tag query service.
     */
    @Inject
    private TagQueryService tagQueryService;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final AnonymousViewCheckMidware anonymous = beanManager.getReference(AnonymousViewCheckMidware.class);
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
        Dispatcher.post("/reset-pwd", loginProcessor::resetPwd, anonymous::handle);
        Dispatcher.get("/api/reset-pwd/meta", loginProcessor::resetPwdMeta);
        Dispatcher.get("/register", loginProcessor::showRegister);
        Dispatcher.get("/verify", loginProcessor::verify);
        Dispatcher.post("/register", loginProcessor::register, anonymous::handle, userRegisterValidationMidware::handle);
        Dispatcher.post("/register2", loginProcessor::register2, anonymous::handle, userRegister2ValidationMidware::handle);
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
            if (AccountSmsQuota.access(ip, name, phone)) {
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
    /**
     * Reset password meta API rate limit per IP: max 10 accesses in 60 seconds.
     */
    public static SimpleCurrentLimiter resetPwdMetaLimiter = new SimpleCurrentLimiter(60, 10);
    public void showResetPwd(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, null);
        context.setRenderer(renderer);
        final Map<String, Object> dataModel = renderer.getDataModel();

        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (PasswordResetQuota.access(ip)) {
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
     * Gets reset password metadata via JSON for SPA clients. Exchanges a valid reset code for {@code userId}.
     * This is a dedicated API endpoint; the HTML page flow is not touched.
     *
     * @param context the specified context
     */
    public void resetPwdMeta(final RequestContext context) {
        final JsonRenderer renderer = new JsonRenderer();
        renderer.setJSONObject(new JSONObject());
        context.setRenderer(renderer);

        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (!resetPwdMetaLimiter.access(ip)) {
            context.renderCodeMsg(StatusCodes.ERR, "操作过于频繁，请稍后重试");
            return;
        }

        final String code = StringUtils.trim(context.param("code"));
        if (StringUtils.isBlank(code)) {
            context.renderCodeMsg(StatusCodes.ERR, "校验码不能为空");
            return;
        }

        final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
        if (null == verifycode
                || Verifycode.BIZ_TYPE_C_RESET_PWD != verifycode.optInt(Verifycode.BIZ_TYPE)
                || verifycode.optLong(Verifycode.EXPIRED) < System.currentTimeMillis()) {
            context.renderCodeMsg(StatusCodes.ERR, langPropsService.get("verifycodeExpiredLabel"));
            return;
        }

        final JSONObject data = new JSONObject();
        data.put(Verifycode.USER_ID, verifycode.optString(Verifycode.USER_ID));
        data.put(Verifycode.CODE, code);
        context.renderData(data).renderCode(StatusCodes.SUCC).renderMsg("");
    }

    /**
     * Resets password.
     *
     * @param context the specified context
     */
    public void resetPwd(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        final String ip=Requests.getRemoteAddr(context.getRequest());
        if(!PasswordResetQuota.access(ip)){
            context.renderMsg("验证码尝试次数过快，请稍候重试！");return;
        }
        final JSONObject body=context.requestJSON();
        try{
            final String id=passwordResets.completeForUserId(body.optString(UserExt.USER_T_ID),body.optString(Keys.CODE),body.optString(User.USER_PASSWORD));
            Sessions.login(context.getResponse(),id,true);
            context.renderJSON(StatusCodes.SUCC);
            LOGGER.info("Account password reset completed");
        }catch(ApiV3Exception e){
            context.renderMsg(e.getMessage());
        }catch(Exception e){
            LOGGER.warn("Account password reset unavailable");context.renderMsg("重置密码暂不可用，请稍后重试");
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
        if (RegistrationVerificationQuota.access(ip)) {
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
            if (RegistrationVerificationQuota.access(ip)) {
                final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
                if (null == verifycode) {
                    dataModel.put(Keys.MSG, langPropsService.get("verifycodeExpiredLabel"));
                    renderer.setTemplateName("error/custom.ftl");
                } else {
                    renderer.setTemplateName("verify/register2.ftl");
                    dataModel.put(Keys.CODE, code.matches("[0-9]{6}") ? code : "");

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
    public static SimpleCurrentLimiter verifySMSCodeLimiterOfIPLong = new SimpleCurrentLimiter(60 * 60 * 24, 10);
    public static SimpleCurrentLimiter verifySMSCodeLimiterOfIP = new SimpleCurrentLimiter(600, 5);
    public static SimpleCurrentLimiter verifySMSCodeLimiterOfName = new SimpleCurrentLimiter(600, 5);
    public static SimpleCurrentLimiter verifySMSCodeLimiterOfPhone = new SimpleCurrentLimiter(600, 5);
    public void register(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        final String ip = Requests.getRemoteAddr(context.getRequest());
        final JSONObject requestJSONObject = context.getRequest().getJSON();
        final String name = requestJSONObject.optString(User.USER_NAME);
        final String userPhone = requestJSONObject.optString("userPhone");
        if (AccountSmsQuota.access(ip, name, userPhone)) {
            // 敏感词检测
            CensorResult censorResult = CensorFactory.getTextCensor().censor(name);
            if (censorResult.isBlocked()) {
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
                LOGGER.info("Generated a registration verification code");

                final String allowRegister = optionQueryService.getAllowRegister();
                if ("2".equals(allowRegister) && StringUtils.isNotBlank(invitecode)) {
                    registrationRequests.reserveLegacy(newUserId,userPhone,invitecode);
                }

                if (!verifycodeMgmtService.sendVerifyCodeSMS(userPhone, code)) {
                    context.renderMsg("验证码发送失败，请稍候重试");
                    return;
                }

                context.renderJSON(StatusCodes.SUCC).renderMsg(langPropsService.get("verifycodeSentLabel"));
            } catch (final ApiV3Exception e) {
                context.renderMsg(e.getMessage());
            } catch (final ServiceException e) {
                final String msg = langPropsService.get("registerFailLabel") + " - " + e.getMessage();
                LOGGER.log(Level.ERROR, msg + "[name={}, phone={}]", name, userPhone);
                context.renderMsg(msg);
            } catch (final Exception e) {
                LOGGER.warn("Registration request unavailable");
                context.renderMsg("注册暂不可用，请稍后重试");
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
        context.renderJSON(StatusCodes.ERR);
        final String ip=Requests.getRemoteAddr(context.getRequest());
        if(!RegistrationVerificationQuota.access(ip)){
            LOGGER.warn("Registration attempt limit reached");context.renderMsg("频率过快，请稍候重试");return;
        }
        try{
            final JSONObject body=context.requestJSON();
            final String referral=context.param("r");
            final String id=registrations.completeForUserId(body.optString(UserExt.USER_T_ID),body.optString(Keys.CODE),
                    body.optString(User.USER_PASSWORD),body.optInt(UserExt.USER_APP_ROLE),body.optString("mbti"),referral==null?"":referral);
            AccountCredentialVersions.remember(new AccountCredentialVersions.Proof(id,0,body.optString(User.USER_PASSWORD)));
            Sessions.login(context.getResponse(),id,false);
            context.renderJSON(StatusCodes.SUCC);LOGGER.info("Account registration completed");
        }catch(ApiV3Exception e){context.renderMsg(e.getMessage());}
        catch(Exception e){LOGGER.warn("Account registration unavailable");context.renderMsg("注册暂不可用，请稍后重试");}
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
        try {
            final LoginResult result = authenticate(requestJSONObject.optString("nameOrEmail"),
                    requestJSONObject.optString(User.USER_PASSWORD), requestJSONObject.optString("mfaCode"),
                    Requests.getRemoteAddr(request));
            if (result.success()) {
                final String userId = result.user().optString(Keys.OBJECT_ID);
                final String token = WebsiteLoginCookies.login(response, userId, requestJSONObject.optBoolean(Common.REMEMBER_LOGIN));
                context.renderCodeMsg(StatusCodes.SUCC, "");
                context.renderJSONValue(Keys.TOKEN, token);
                return;
            }
            context.renderMsg(result.message());
        } catch (final ServiceException e) {
            context.renderMsg(langPropsService.get("loginFailLabel"));
        }
    }

    /**
     * Shared credential verification for browser login and API v3. It deliberately owns
     * the failure counters so a new authentication surface cannot bypass account locks.
     */
    public LoginResult authenticate(final String nameOrEmail, final String password, final String mfaCode,
                                    final String ip) throws ServiceException {
        AccountCredentialVersions.clear();
        final Long unlockAt = LOGIN_IP_LOCK.get(ip);
        if (null != unlockAt) {
            if (System.currentTimeMillis() < unlockAt) return LoginResult.failure("too_many_attempts", "尝试次数过多，请稍后再试");
            LOGIN_IP_LOCK.remove(ip);
        }

        JSONObject user = userQueryService.getUserByName(nameOrEmail);
        if (null == user) user = userQueryService.getUserByEmail(nameOrEmail);
        if (null == user) user = userQueryService.getUserByPhone(nameOrEmail);
        final boolean userFound = null != user;
        if (!userFound) {
            // Keep password processing and failure accounting indistinguishable from an unknown account.
            user = new JSONObject().put(Keys.OBJECT_ID, "DUMMY").put(User.USER_PASSWORD, "")
                    .put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
        }

        if (UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS))
            return LoginResult.failure("account_blocked", langPropsService.get("userBlockLabel"));
        if (UserExt.USER_STATUS_C_NOT_VERIFIED == user.optInt(UserExt.USER_STATUS))
            return LoginResult.failure("account_unverified", langPropsService.get("notVerifiedLabel"));
        if (UserExt.USER_STATUS_C_INVALID_LOGIN == user.optInt(UserExt.USER_STATUS)
                || UserExt.USER_STATUS_C_DEACTIVATED == user.optInt(UserExt.USER_STATUS))
            return LoginResult.failure("account_unavailable", langPropsService.get("invalidLoginLabel"));

        final String userId = user.optString(Keys.OBJECT_ID);
        final var credentialProof = userFound ? AccountCredentialVersions.current(userId) : null;
        final boolean passwordMatches = credentialProof != null && credentialProof.password().equals(password);
        if (passwordMatches) {
            final long code;
            try {
                code = StringUtils.isBlank(mfaCode) ? 0L : Long.parseLong(mfaCode);
            } catch (final NumberFormatException e) {
                return LoginResult.failure("invalid_mfa", "两步验证码错误");
            }
            if (!MFAProcessor.verifyLogin(userId, code)) {
                return LoginResult.failure(StringUtils.isBlank(mfaCode) ? "mfa_required" : "invalid_mfa",
                        StringUtils.isBlank(mfaCode) ? "需要两步验证码" : "两步验证码错误");
            }
            AccountCredentialVersions.remember(credentialProof);
            if (AccountCredentialVersions.issuance(userId, null) == null) {
                AccountCredentialVersions.clear();
                return LoginResult.failure("invalid_credentials", langPropsService.get("wrongPwdLabel"));
            }
            WRONG_PWD_TRIES.remove(userId);
            LOGIN_IP_LOCK.remove(ip);
            return LoginResult.success(user);
        }

        JSONObject wrong = WRONG_PWD_TRIES.get(userId);
        if (null == wrong) wrong = new JSONObject();
        final int newWrongCount = wrong.optInt(Common.WRON_COUNT) + 1;
        wrong.put(Common.WRON_COUNT, newWrongCount);
        WRONG_PWD_TRIES.put(userId, wrong);
        if (newWrongCount > 2) {
            LOGIN_IP_LOCK.put(ip, System.currentTimeMillis() + 5 * 60 * 1000);
            return LoginResult.failure("too_many_attempts", "尝试次数过多，请稍后再试");
        }
        return LoginResult.failure("invalid_credentials", langPropsService.get("wrongPwdLabel"));
    }

    public static final class LoginResult {
        private final JSONObject user;
        private final String code;
        private final String message;

        private LoginResult(final JSONObject user, final String code, final String message) {
            this.user = user;
            this.code = code;
            this.message = message;
        }

        public static LoginResult success(final JSONObject user) { return new LoginResult(user, "", ""); }
        public static LoginResult failure(final String code, final String message) { return new LoginResult(null, code, message); }
        public boolean success() { return null != user; }
        public JSONObject user() { return user; }
        public String code() { return code; }
        public String message() { return message; }
    }

    /**
     * Logout.
     *
     * @param context the specified context
     */
    public void logout(final RequestContext context) {
        // 旧版 GET 退出仍保留，但拒绝明确的跨站触发，避免注销 CSRF。
        if (!WebsiteLoginCookies.trustedOrigin(context, true)) {
            context.sendStatus(403);
            return;
        }
        try {
            WebsiteLoginCookies.logout(context);
        } catch (final ServiceException error) {
            context.setStatus(503);
            context.getResponse().setHeader("Content-Type", "text/plain; charset=UTF-8");
            context.sendString("退出暂未完成，请返回后重试");
            return;
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
