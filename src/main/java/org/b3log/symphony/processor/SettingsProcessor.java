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
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.Strings;
import org.b3log.latke.util.TimeZones;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.validate.PointTransferValidationMidware;
import org.b3log.symphony.processor.middleware.validate.UpdatePasswordValidationMidware;
import org.b3log.symphony.processor.middleware.validate.UpdateProfilesValidationMidware;
import org.b3log.symphony.processor.middleware.validate.UserRegisterValidationMidware;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.ApiV3EmotionOptions;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.math.BigDecimal;
import java.util.*;

/**
 * Settings processor.
 * <ul>
 * <li>Shows settings (/settings), GET</li>
 * <li>Shows settings pages (/settings/{page}), GET</li>
 * <li>Updates profiles (/settings/profiles), POST</li>
 * <li>Updates user avatar (/settings/avatar), POST</li>
 * <li>Geo status (/settings/geo/status), POST</li>
 * <li>Privacy (/settings/privacy), POST</li>
 * <li>Function (/settings/function), POST</li>
 * <li>Transfer point (/point/transfer), POST</li>
 * <li>Queries invitecode state (/invitecode/state), GET</li>
 * <li>Point buy invitecode (/point/buy-invitecode), POST</li>
 * <li>Exports posts(article/comment) to a file (/export/posts), POST</li>
 * <li>Updates emotions (/settings/emotionList), POST</li>
 * <li>Password (/settings/password), POST</li>
 * <li>Updates i18n (/settings/i18n), POST</li>
 * <li>Sends email verify code (/settings/email/vc), POST</li>
 * <li>Updates email (/settings/email), POST</li>
 * <li>Updates username (/settings/username), POST</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.1.0, Aug 17, 2020
 * @since 2.4.0
 */
@Singleton
public class SettingsProcessor {

    @Inject
    private UserNameMgmtService userNames;

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(SettingsProcessor.class);

    /**
     * Post export service.
     */
    @Inject
    private PostExportService postExportService;

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
     * Notification management service.
     */
    @Inject
    private NotificationMgmtService notificationMgmtService;

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
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Emotion query service.
     */
    @Inject
    private EmotionQueryService emotionQueryService;

    /**
     * Emotion management service.
     */
    @Inject
    private EmotionMgmtService emotionMgmtService;

    @Inject
    private EmojiQueryService emojiQueryService;

    @Inject
    private EmojiMgmtService emojiMgmtService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Avatar query service.
     */
    @Inject
    private AvatarQueryService avatarQueryService;

    /**
     * Role query service.
     */
    @Inject
    private RoleQueryService roleQueryService;

    /**
     * Verifycode query service.
     */
    @Inject
    private VerifycodeQueryService verifycodeQueryService;

    /**
     * Verifycode management service.
     */
    @Inject
    private VerifycodeMgmtService verifycodeMgmtService;

    /**
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * System Settings service.
     */
    @Inject
    private SystemSettingsService settingsService;

    /**
     * CLoud service.
     */
    @Inject
    private CloudService cloudService;

    @Inject
    private BagMgmtService bagMgmtService;

    @Inject
    private UserSettingsMgmtService userSettingsMgmtService;

    /**
     * Activity management service.
     */
    @Inject
    private ActivityMgmtService activityMgmtService;

    /**
     * User repository.
     */
    @Inject
    private UserRepository userRepository;

    @Inject
    private MembershipQueryService membershipQueryService;

    @Inject
    private SkinQueryService skinQueryService;



    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);
        final UpdateProfilesValidationMidware updateProfilesValidationMidware = beanManager.getReference(UpdateProfilesValidationMidware.class);
        final UpdatePasswordValidationMidware updatePasswordValidationMidware = beanManager.getReference(UpdatePasswordValidationMidware.class);
        final PointTransferValidationMidware pointTransferValidationMidware = beanManager.getReference(PointTransferValidationMidware.class);

        final SettingsProcessor settingsProcessor = beanManager.getReference(SettingsProcessor.class);
        Dispatcher.post("/settings/username", settingsProcessor::updateUserName, loginCheck::handle);
        Dispatcher.post("/settings/email/vc", settingsProcessor::sendEmailVC, loginCheck::handle);
        Dispatcher.post("/settings/phone/vc", settingsProcessor::sendPhoneVC, loginCheck::handle);
        Dispatcher.post("/settings/email", settingsProcessor::updateEmail, loginCheck::handle);
        Dispatcher.post("/settings/phone", settingsProcessor::updatePhone, loginCheck::handle);
        Dispatcher.post("/settings/i18n", settingsProcessor::updateI18n, loginCheck::handle, csrfMidware::check);
        Dispatcher.group().middlewares(loginCheck::handle, csrfMidware::fill).router().get().uris(new String[]{"/settings", "/settings/{page}"}).handler(settingsProcessor::showSettings);
        Dispatcher.post("/settings/geo/status", settingsProcessor::updateGeoStatus, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/settings/privacy", settingsProcessor::updatePrivacy, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/settings/function", settingsProcessor::updateFunction, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/settings/system", settingsProcessor::updateSystem, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/settings/skin", settingsProcessor::updateSkin, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/settings/profiles", settingsProcessor::updateProfiles, loginCheck::handle, csrfMidware::check, updateProfilesValidationMidware::handle);
        Dispatcher.post("/settings/avatar", settingsProcessor::updateAvatar, loginCheck::handle, csrfMidware::check, updateProfilesValidationMidware::handle);
        Dispatcher.post("/settings/password", settingsProcessor::updatePassword, loginCheck::handle, csrfMidware::check, updatePasswordValidationMidware::handle);
        Dispatcher.post("/settings/emotionList", settingsProcessor::updateEmoji, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/invitecode/state", settingsProcessor::queryInvitecode, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/point/buy-invitecode", settingsProcessor::pointBuy, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/export/posts", settingsProcessor::exportPosts, loginCheck::handle);
        Dispatcher.post("/point/transfer", settingsProcessor::pointTransfer, loginCheck::handle, pointTransferValidationMidware::handle);
        Dispatcher.get("/bag/1dayCheckin", settingsProcessor::use1dayCheckinCard, loginCheck::handle, csrfMidware::check);
        Dispatcher.get("/bag/2dayCheckin", settingsProcessor::use2dayCheckinCard, loginCheck::handle, csrfMidware::check);
        Dispatcher.get("/bag/patchCheckin", settingsProcessor::usePatchCheckinCard, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/bag/nameCard", settingsProcessor::useNameCard, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/api/settings/profiles", settingsProcessor::updateProfiles, loginCheck::handle, updateProfilesValidationMidware::handle);
        Dispatcher.post("/api/settings/avatar", settingsProcessor::updateAvatar, loginCheck::handle, updateProfilesValidationMidware::handle);
        Dispatcher.get("/api/user/bag", settingsProcessor::getUserBagApi, loginCheck::handle);
        Dispatcher.get("/api/bag/1dayCheckin", settingsProcessor::use1dayCheckinCardApi, loginCheck::handle);
        Dispatcher.get("/api/bag/2dayCheckin", settingsProcessor::use2dayCheckinCardApi, loginCheck::handle);
        Dispatcher.get("/api/bag/patchCheckin", settingsProcessor::usePatchCheckinCardApi, loginCheck::handle);
        Dispatcher.post("/api/bag/nameCard", settingsProcessor::useNameCardApi, loginCheck::handle);
    }

    /**
     * 使用改名卡
     */
    public void useNameCard(final RequestContext context) {
        useNameCardAtomic(context, false);
    }

    /**
     * 使用补签卡
     */
    public void usePatchCheckinCard(final RequestContext context) {
        final JSONObject user = (JSONObject) context.attr(User.USER);
        final String userId = user.optString(Keys.OBJECT_ID);
        int result = activityMgmtService.patchCheckin(userId);
        if ( result == 0) {
            context.renderJSON(StatusCodes.SUCC);
            context.renderMsg("补签卡使用成功！");
        } else {
            context.renderJSON(StatusCodes.ERR);
            switch (result){
                case -1:
                    context.renderMsg("没有补签卡，怎么补嘛~");
                    break;
                case -2:
                    context.renderMsg("当前签到是最长签到，不可以补签哟");
                    break;
                case -3:
                    context.renderMsg("今天还没签到，不可以补签哟");
                    break;
                default:
                    context.renderMsg("补签卡使用失败，卡片与补签记录未消耗。");
                    break;
            }
        }
    }

    /**
     * 使用单日免签卡
     */
    public void use1dayCheckinCard(final RequestContext context) {
        final JSONObject user = (JSONObject) context.attr(User.USER);
        final String actor = user.optString(Keys.OBJECT_ID);
        try {
            final JSONObject bag = bagMgmtService.useExemption(actor, "checkin1day");
            context.renderJSON(StatusCodes.SUCC);
            context.renderMsg("免签卡使用成功！免签天数已累积。");
        } catch (final BagMutation.Unavailable e) {
            context.renderCodeMsg(StatusCodes.ERR, "您没有可用的免签卡，或剩余天数已达上限。");
        } catch (final Exception e) {
            context.renderCodeMsg(StatusCodes.ERR, "免签卡使用失败，请稍后重试。");
        }
    }

    /**
     * 使用两天免签卡
     */
    public void use2dayCheckinCard(final RequestContext context) {
        final JSONObject user = (JSONObject) context.attr(User.USER);
        final String actor = user.optString(Keys.OBJECT_ID);
        try {
            final JSONObject bag = bagMgmtService.useExemption(actor, "checkin2days");
            context.renderJSON(StatusCodes.SUCC);
            context.renderMsg("免签卡使用成功！免签天数已累积。");
        } catch (final BagMutation.Unavailable e) {
            context.renderCodeMsg(StatusCodes.ERR, "您没有可用的免签卡，或剩余天数已达上限。");
        } catch (final Exception e) {
            context.renderCodeMsg(StatusCodes.ERR, "免签卡使用失败，请稍后重试。");
        }
    }

    /**
     * Initializes a JSON renderer with the unified envelope for API endpoints.
     *
     * @param context the specified context
     */
    private static void initJson(final RequestContext context) {
        final JsonRenderer renderer = new JsonRenderer();
        renderer.setJSONObject(new JSONObject());
        context.setRenderer(renderer);
    }

    /**
     * Gets the current user's bag via JSON.
     *
     * @param context the specified context
     */
    public void getUserBagApi(final RequestContext context) {
        initJson(context);

        final JSONObject currentUser = (JSONObject) context.attr(User.USER);
        final JSONObject bag = new JSONObject(cloudService.getBag(currentUser.optString(Keys.OBJECT_ID)));
        context.renderData(bag).renderCode(StatusCodes.SUCC).renderMsg("");
    }

    /**
     * Uses a 1-day check-in exemption card via JSON (apiKey compatible).
     *
     * @param context the specified context
     */
    public void use1dayCheckinCardApi(final RequestContext context) {
        initJson(context);
        final JSONObject user = (JSONObject) context.attr(User.USER);
        final String actor = user.optString(Keys.OBJECT_ID);
        try {
            final JSONObject bag = bagMgmtService.useExemption(actor, "checkin1day");
            context.renderData(bag).renderCode(StatusCodes.SUCC).renderMsg("免签卡使用成功！免签天数已累积。");
        } catch (final BagMutation.Unavailable e) {
            context.renderCodeMsg(StatusCodes.ERR, "您没有可用的免签卡，或剩余天数已达上限。");
        } catch (final Exception e) {
            context.renderCodeMsg(StatusCodes.ERR, "免签卡使用失败，请稍后重试。");
        }
    }

    /**
     * Uses a 2-day check-in exemption card via JSON (apiKey compatible).
     *
     * @param context the specified context
     */
    public void use2dayCheckinCardApi(final RequestContext context) {
        initJson(context);
        final JSONObject user = (JSONObject) context.attr(User.USER);
        final String actor = user.optString(Keys.OBJECT_ID);
        try {
            final JSONObject bag = bagMgmtService.useExemption(actor, "checkin2days");
            context.renderData(bag).renderCode(StatusCodes.SUCC).renderMsg("免签卡使用成功！免签天数已累积。");
        } catch (final BagMutation.Unavailable e) {
            context.renderCodeMsg(StatusCodes.ERR, "您没有可用的免签卡，或剩余天数已达上限。");
        } catch (final Exception e) {
            context.renderCodeMsg(StatusCodes.ERR, "免签卡使用失败，请稍后重试。");
        }
    }

    /**
     * Uses a patch check-in card via JSON (apiKey compatible).
     *
     * @param context the specified context
     */
    public void usePatchCheckinCardApi(final RequestContext context) {
        initJson(context);

        final JSONObject currentUser = (JSONObject) context.attr(User.USER);
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        final int result = activityMgmtService.patchCheckin(userId);
        if (0 == result) {
            renderBagResult(context, userId, "补签卡使用成功！");
            return;
        }

        String msg;
        switch (result) {
            case -1:
                msg = "没有补签卡，怎么补嘛~";
                break;
            case -2:
                msg = "当前签到是最长签到，不可以补签哟";
                break;
            case -3:
                msg = "今天还没签到，不可以补签哟";
                break;
            default:
                msg = "补签卡使用失败，卡片与补签记录未消耗。";
                break;
        }
        context.renderCodeMsg(StatusCodes.ERR, msg);
    }

    /**
     * Uses a name card via JSON (apiKey compatible). The new user name must match the same rule as registration.
     *
     * @param context the specified context
     */
    public void useNameCardApi(final RequestContext context) {
        useNameCardAtomic(context, true);
    }

    private void useNameCardAtomic(final RequestContext context, final boolean api) {
        if (api) initJson(context);
        else context.renderJSON(StatusCodes.ERR);
        final JSONObject user = (JSONObject) context.attr(User.USER);
        if (null == user) { context.sendError(401); return; }
        final String actor = user.optString(Keys.OBJECT_ID);
        final String input = context.requestJSON().optString(User.USER_NAME);
        final String name = api ? StringUtils.trim(input) : input;
        try {
            userNames.useCardLegacy(actor, name);
            final String msg = "您的用户名已成功修改为：" + name;
            if (api) renderBagResult(context, actor, msg);
            else context.renderJSON(StatusCodes.SUCC).renderMsg(msg);
        } catch (final UserNameMgmtService.Rejected e) {
            context.renderCodeMsg(StatusCodes.ERR, switch (e.code) {
                case "invalid_name" -> "用户名不合法，仅支持 1-64 位字母、数字和 -";
                case "name_taken" -> "用户名已被使用，请更换后重试。";
                case "item_unavailable" -> "您没有改名卡。";
                case "deactivated" -> "永久停用账号不能修改用户名";
                default -> "用户不存在或不可用";
            });
        } catch (final Exception e) {
            context.renderCodeMsg(StatusCodes.ERR, "操作结果暂未确认，请刷新背包和资料后重试。");
        }
    }

    /**
     * Renders a successful bag operation with the latest bag snapshot.
     *
     * @param context the specified context
     * @param userId  the current user id
     * @param msg     the success message
     */
    private void renderBagResult(final RequestContext context, final String userId, final String msg) {
        final JSONObject bag = new JSONObject(cloudService.getBag(userId));
        context.renderData(bag).renderCode(StatusCodes.SUCC).renderMsg(msg);
    }

    /**
     * Updates username.
     *
     * @param context the specified context
     */
    public void updateUserName(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final JSONObject requestJSONObject = context.requestJSON();
        final JSONObject currentUser = Sessions.getUser();
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        try {
            if (currentUser.optInt(UserExt.USER_POINT) < Pointtransfer.TRANSFER_SUM_C_CHANGE_USERNAME) {
                throw new ServiceException(langPropsService.get("insufficientBalanceLabel"));
            }

            final JSONObject user = userQueryService.getUser(userId);
            final String oldName = user.optString(User.USER_NAME);
            final String newName = requestJSONObject.optString(User.USER_NAME);
            user.put(User.USER_NAME, newName);

            if (!ProfileWriteQuota.access(user.optString(Keys.OBJECT_ID))) {
                context.renderMsg("操作频繁，请稍候重试。");
                context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
                return;
            }

            // 敏感词检测
            CensorResult censorResult = CensorFactory.getTextCensor().censor(newName);
            if (censorResult.isBlocked()) {
                context.renderMsg("用户名含违规信息，请修改后重试。");
                context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
                return;
            }

            userMgmtService.updateUserName(userId, user);

            pointtransferMgmtService.transfer(userId, Pointtransfer.ID_C_SYS,
                    Pointtransfer.TRANSFER_TYPE_C_CHANGE_USERNAME, Pointtransfer.TRANSFER_SUM_C_CHANGE_USERNAME,
                    oldName + "-" + newName, System.currentTimeMillis(), "");

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }

    /**
     * Sends phone verify code.
     *
     * @param context the specified context
     */
    public void sendPhoneVC(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final JSONObject requestJSONObject = context.requestJSON();
        final String userPhone = requestJSONObject.optString("userPhone");
        if (!UserRegisterValidationMidware.isMobileNO(userPhone)) {
            final String msg = langPropsService.get("sendFailedLabel") + " - 手机号不合法";
            context.renderMsg(msg);
            return;
        }

        final String captcha = requestJSONObject.optString(CaptchaProcessor.CAPTCHA);
        if (!CaptchaProcessor.jiyan(captcha)) {
            final String msg = langPropsService.get("sendFailedLabel") + " - " + langPropsService.get("captchaErrorLabel");
            context.renderMsg(msg);
            return;
        }

        final JSONObject user = Sessions.getUser();

        if (userPhone.equals(user.optString("userPhone"))) {
            final String msg = langPropsService.get("sendFailedLabel") + " - 该手机号与当前绑定手机号相同";
            context.renderMsg(msg);
            return;
        }

        final String userId = user.optString(Keys.OBJECT_ID);
        try {
            JSONObject verifycode = verifycodeQueryService.getVerifycodeByUserId(Verifycode.TYPE_C_PHONE, Verifycode.BIZ_TYPE_C_BIND_PHONE, userId);
            if (null != verifycode) {
                context.renderJSON(StatusCodes.SUCC).renderMsg(langPropsService.get("vcSentLabel"));
                return;
            }

            if (null != userQueryService.getUserByPhone(userPhone)) {
                context.renderMsg("该手机号已绑定其他账号");
                return;
            }

            final String name = user.optString(User.USER_NAME);
            final String ip = Requests.getRemoteAddr(context.getRequest());

            if (AccountSmsQuota.access(ip, name, userPhone)) {
                final String code = RandomStringUtils.randomNumeric(6);
                if (!verifycodeMgmtService.sendVerifyCodeSMS(userPhone, code)) {
                    context.renderMsg("验证码发送失败，请稍候重试");
                    return;
                }

                verifycode = new JSONObject();
                verifycode.put(Verifycode.USER_ID, userId);
                verifycode.put(Verifycode.BIZ_TYPE, Verifycode.BIZ_TYPE_C_BIND_PHONE);
                verifycode.put(Verifycode.TYPE, Verifycode.TYPE_C_PHONE);
                verifycode.put(Verifycode.CODE, code);
                verifycode.put(Verifycode.STATUS, Verifycode.STATUS_C_UNSENT);
                verifycode.put(Verifycode.EXPIRED, DateUtils.addMinutes(new Date(), 10).getTime());
                verifycode.put(Verifycode.RECEIVER, userPhone);
                verifycodeMgmtService.addVerifycode(verifycode);
                LOGGER.log(Level.INFO, "Generated a verify code for binding [userName={}, phone={}, code={}]", name, userPhone, code);

                context.renderJSON(StatusCodes.SUCC).renderMsg(langPropsService.get("verifycodeSentLabel"));
            } else {
                context.renderMsg("验证码发送频率过快，请稍候重试");
            }
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }

    /**
     * Sends email verify code.
     *
     * @param context the specified context
     */
    public void sendEmailVC(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final JSONObject requestJSONObject = context.requestJSON();
        final String email = StringUtils.lowerCase(StringUtils.trim(requestJSONObject.optString(User.USER_EMAIL)));
        if (!Strings.isEmail(email)) {
            final String msg = langPropsService.get("sendFailedLabel") + " - " + langPropsService.get("invalidEmailLabel");
            context.renderMsg(msg);
            return;
        }

        final String captcha = requestJSONObject.optString(CaptchaProcessor.CAPTCHA);
        if (!CaptchaProcessor.jiyan(captcha)) {
            final String msg = langPropsService.get("sendFailedLabel") + " - " + langPropsService.get("captchaErrorLabel");
            context.renderMsg(msg);
            return;
        }

        final JSONObject user = Sessions.getUser();
        if (email.equalsIgnoreCase(user.optString(User.USER_EMAIL))) {
            final String msg = langPropsService.get("sendFailedLabel") + " - " + langPropsService.get("bindedLabel");
            context.renderMsg(msg);
            return;
        }

        final String userId = user.optString(Keys.OBJECT_ID);
        try {
            JSONObject verifycode = verifycodeQueryService.getVerifycodeByUserId(Verifycode.TYPE_C_EMAIL, Verifycode.BIZ_TYPE_C_BIND_EMAIL, userId);
            if (null != verifycode) {
                context.renderJSON(StatusCodes.SUCC).renderMsg(langPropsService.get("vcSentLabel"));
                return;
            }

            if (null != userQueryService.getUserByEmail(email)) {
                context.renderMsg(langPropsService.get("duplicatedEmailLabel"));
                return;
            }

            final String code = RandomStringUtils.randomNumeric(6);
            verifycode = new JSONObject();
            verifycode.put(Verifycode.USER_ID, userId);
            verifycode.put(Verifycode.BIZ_TYPE, Verifycode.BIZ_TYPE_C_BIND_EMAIL);
            verifycode.put(Verifycode.TYPE, Verifycode.TYPE_C_EMAIL);
            verifycode.put(Verifycode.CODE, code);
            verifycode.put(Verifycode.STATUS, Verifycode.STATUS_C_UNSENT);
            verifycode.put(Verifycode.EXPIRED, DateUtils.addMinutes(new Date(), 10).getTime());
            verifycode.put(Verifycode.RECEIVER, email);
            verifycodeMgmtService.addVerifycode(verifycode);

            context.renderJSON(StatusCodes.SUCC).renderMsg(langPropsService.get("verifycodeSentLabel"));
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }

    /**
     * Updates phone.
     *
     * @param context the specified context
     */
    public void updatePhone(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final Request request = context.getRequest();
        final JSONObject requestJSONObject = context.requestJSON();
        final String captcha = requestJSONObject.optString(CaptchaProcessor.CAPTCHA);
        final JSONObject currentUser = Sessions.getUser();
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        try {
            final JSONObject verifycode = verifycodeQueryService.getVerifycodeByUserId(Verifycode.TYPE_C_PHONE, Verifycode.BIZ_TYPE_C_BIND_PHONE, userId);
            if (null == verifycode) {
                final String msg = langPropsService.get("updateFailLabel") + " - " + langPropsService.get("captchaErrorLabel");
                context.renderMsg(msg);
                context.renderJSONValue(Keys.CODE, 2);
                return;
            }

            if (!StringUtils.equals(verifycode.optString(Verifycode.CODE), captcha)) {
                final String msg = langPropsService.get("updateFailLabel") + " - " + langPropsService.get("captchaErrorLabel");
                context.renderMsg(msg);
                context.renderJSONValue(Keys.CODE, 2);
                return;
            }

            final JSONObject user = userQueryService.getUser(userId);
            final String userPhone = verifycode.optString(Verifycode.RECEIVER);
            user.put("userPhone", userPhone);
            userMgmtService.updateUserPhone(userId, user);
            verifycodeMgmtService.removeByCode(captcha);

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }


    /**
     * Updates email.
     *
     * @param context the specified context
     */
    public void updateEmail(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final Request request = context.getRequest();
        final JSONObject requestJSONObject = context.requestJSON();
        final String captcha = requestJSONObject.optString(CaptchaProcessor.CAPTCHA);
        final JSONObject currentUser = Sessions.getUser();
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        try {
            final JSONObject verifycode = verifycodeQueryService.getVerifycodeByUserId(Verifycode.TYPE_C_EMAIL, Verifycode.BIZ_TYPE_C_BIND_EMAIL, userId);
            if (null == verifycode) {
                final String msg = langPropsService.get("updateFailLabel") + " - " + langPropsService.get("captchaErrorLabel");
                context.renderMsg(msg);
                context.renderJSONValue(Keys.CODE, 2);
                return;
            }

            if (!StringUtils.equals(verifycode.optString(Verifycode.CODE), captcha)) {
                final String msg = langPropsService.get("updateFailLabel") + " - " + langPropsService.get("captchaErrorLabel");
                context.renderMsg(msg);
                context.renderJSONValue(Keys.CODE, 2);
                return;
            }

            final JSONObject user = userQueryService.getUser(userId);
            final String email = verifycode.optString(Verifycode.RECEIVER);
            user.put(User.USER_EMAIL, email);
            userMgmtService.updateUserEmail(userId, user);
            verifycodeMgmtService.removeByCode(captcha);

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }

    /**
     * Updates user i18n.
     *
     * @param context the specified context
     */
    public void updateI18n(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final Request request = context.getRequest();
        JSONObject requestJSONObject;
        try {
            requestJSONObject = context.requestJSON();
            request.setAttribute(Keys.REQUEST, requestJSONObject);
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage());

            requestJSONObject = new JSONObject();
        }

        String userLanguage = requestJSONObject.optString(UserExt.USER_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toString());
        if (!Languages.getAvailableLanguages().contains(userLanguage)) {
            userLanguage = Locale.US.toString();
        }

        String userTimezone = requestJSONObject.optString(UserExt.USER_TIMEZONE, TimeZone.getDefault().getID());
        if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(userTimezone)) {
            userTimezone = TimeZone.getDefault().getID();
        }

        try {
            userSettingsMgmtService.updateLegacy(((JSONObject) context.attr(User.USER)).optString(Keys.OBJECT_ID), "locale", new JSONObject().put(UserExt.USER_LANGUAGE, userLanguage).put(UserExt.USER_TIMEZONE, userTimezone));
            context.renderJSON(StatusCodes.SUCC);
        } catch (final Exception e) { LOGGER.error("Update locale failed", e); context.renderMsg("更新失败，请稍后重试"); }
    }

    /**
     * Shows settings pages.
     *
     * @param context the specified context
     */
    public void showSettings(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, null);
        context.setRenderer(renderer);
        String page = context.pathVar("page");
        if (StringUtils.isBlank(page)) {
            page = "profile";
        }
        page += ".ftl";
        renderer.setTemplateName("home/settings/" + page);
        final Map<String, Object> dataModel = renderer.getDataModel();

        final JSONObject user = Sessions.getUser();
        UserProcessor.fillHomeUser(dataModel, user, roleQueryService);

        avatarQueryService.fillUserAvatarURL(user);

        final String userId = user.optString(Keys.OBJECT_ID);
        dataModel.put("desktopSkins", skinQueryService.getSkins(SkinQueryService.DEVICE_PC));
        dataModel.put("mobileSkins", skinQueryService.getSkins(SkinQueryService.DEVICE_MOBILE));
        dataModel.put("currentDesktopSkin",
                skinQueryService.normalizeSkin(user.optString(UserExt.USER_SKIN), SkinQueryService.DEVICE_PC));
        dataModel.put("currentMobileSkin",
                skinQueryService.normalizeSkin(user.optString(UserExt.USER_MOBILE_SKIN), SkinQueryService.DEVICE_MOBILE));

        final int invitedUserCount = userQueryService.getInvitedUserCount(userId);
        dataModel.put(Common.INVITED_USER_COUNT, invitedUserCount);

        dataModelService.fillHeaderAndFooter(context, dataModel);

        String inviteTipLabel = (String) dataModel.get("inviteTipLabel");
        inviteTipLabel = inviteTipLabel.replace("{point}", String.valueOf(Pointtransfer.TRANSFER_SUM_C_INVITE_REGISTER));
        dataModel.put("inviteTipLabel", inviteTipLabel);

        String pointTransferTipLabel = (String) dataModel.get("pointTransferTipLabel");
        pointTransferTipLabel = pointTransferTipLabel.replace("{point}", Symphonys.POINT_TRANSER_MIN + "");
        dataModel.put("pointTransferTipLabel", pointTransferTipLabel);

        String dataExportTipLabel = (String) dataModel.get("dataExportTipLabel");
        dataExportTipLabel = dataExportTipLabel.replace("{point}",
                String.valueOf(Pointtransfer.TRANSFER_SUM_C_DATA_EXPORT));
        dataModel.put("dataExportTipLabel", dataExportTipLabel);

        final String allowRegister = optionQueryService.getAllowRegister();
        dataModel.put("allowRegister", allowRegister);

        String buyInvitecodeLabel = langPropsService.get("buyInvitecodeLabel");
        buyInvitecodeLabel = buyInvitecodeLabel.replace("${point}",
                String.valueOf(Pointtransfer.TRANSFER_SUM_C_BUY_INVITECODE));
        buyInvitecodeLabel = buyInvitecodeLabel.replace("${point2}",
                String.valueOf(Pointtransfer.TRANSFER_SUM_C_INVITECODE_USED));
        dataModel.put("buyInvitecodeLabel", buyInvitecodeLabel);

        String updateNameTipLabel = (String) dataModel.get("updateNameTipLabel");
        updateNameTipLabel = updateNameTipLabel.replace("{point}", Symphonys.POINT_CHANGE_USERNAME + "");
        dataModel.put("updateNameTipLabel", updateNameTipLabel);

        final List<JSONObject> invitecodes = invitecodeQueryService.getValidInvitecodes(userId);
        for (final JSONObject invitecode : invitecodes) {
            String msg = langPropsService.get("expireTipLabel");
            msg = msg.replace("${time}", DateFormatUtils.format(invitecode.optLong(Keys.OBJECT_ID)
                    + Symphonys.INVITECODE_EXPIRED, "yyyy-MM-dd HH:mm"));
            invitecode.put(Common.MEMO, msg);
        }

        dataModel.put(Invitecode.INVITECODES, invitecodes);

        final String requestURI = context.requestURI();
        if (requestURI.contains("function")) {
            dataModel.put(Emotion.EMOTIONS, emotionQueryService.getEmojis(userId));
            dataModel.put(Emotion.SHORT_T_LIST, emojiLists);
        }

        if (requestURI.contains("i18n")) {
            dataModel.put(Common.LANGUAGES, Languages.getAvailableLanguages());

            final List<JSONObject> timezones = new ArrayList<>();
            final List<TimeZones.TimeZoneWithDisplayNames> timeZones = TimeZones.getInstance().getTimeZones();
            for (final TimeZones.TimeZoneWithDisplayNames timeZone : timeZones) {
                final JSONObject timezone = new JSONObject();

                timezone.put(Common.ID, timeZone.getTimeZone().getID());
                timezone.put(Common.NAME, timeZone.getDisplayName());

                timezones.add(timezone);
            }
            dataModel.put(Common.TIMEZONES, timezones);
        }

        dataModel.put(Common.TYPE, "settings");
        dataModel.put("sysBag", cloudService.getBag(userId));
        dataModel.put("sysMetal", cloudService.getMedal(userId));

        // “感谢加入”系统通知已读置位 https://github.com/b3log/symphony/issues/907
        notificationMgmtService.makeRead(userId, Notification.DATA_TYPE_C_SYS_ANNOUNCE_NEW_USER);

        // 根据param中的to查用户是否存在
        String to = context.param("to");
        if (to == null) {
            dataModel.put("pointTransferToUser", "");
        } else {
            JSONObject pointTransferToUser = userQueryService.getUserByName(to);
            if (pointTransferToUser == null) {
                dataModel.put("pointTransferToUser", "");
            } else {
                dataModel.put("pointTransferToUser", pointTransferToUser.optString(User.USER_NAME));
            }
        }
    }

    /**
     * Updates user geo status.
     *
     * @param context the specified context
     */
    public void updateGeoStatus(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final Request request = context.getRequest();
        JSONObject requestJSONObject;
        try {
            requestJSONObject = context.requestJSON();
            request.setAttribute(Keys.REQUEST, requestJSONObject);
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage());

            requestJSONObject = new JSONObject();
        }

        int geoStatus = requestJSONObject.optInt(UserExt.USER_GEO_STATUS);
        if (UserExt.USER_GEO_STATUS_C_PRIVATE != geoStatus && UserExt.USER_GEO_STATUS_C_PUBLIC != geoStatus) {
            geoStatus = UserExt.USER_GEO_STATUS_C_PUBLIC;
        }

        try {
            userSettingsMgmtService.updateLegacy(((JSONObject) context.attr(User.USER)).optString(Keys.OBJECT_ID), "location", new JSONObject().put(UserExt.USER_GEO_STATUS, geoStatus));
            context.renderJSON(StatusCodes.SUCC);
        } catch (final Exception e) { LOGGER.error("Update location failed", e); context.renderMsg("更新失败，请稍后重试"); }
    }

    /**
     * Updates user privacy.
     *
     * @param context the specified context
     */
    public void updatePrivacy(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        JSONObject input;
        try { input = context.requestJSON(); } catch (final Exception e) { input = new JSONObject(); }
        final JSONObject changes = new JSONObject();
        for (final String field : ApiV3Settings.PRIVACY.values()) changes.put(field,
                input.optBoolean(field) ? UserExt.USER_XXX_STATUS_C_ENABLED : UserExt.USER_XXX_STATUS_C_DISABLED);
        try {
            userSettingsMgmtService.updateLegacy(((JSONObject) context.attr(User.USER)).optString(Keys.OBJECT_ID), "privacy", changes);
            context.renderJSON(StatusCodes.SUCC);
        } catch (final Exception e) { LOGGER.error("Update privacy failed", e); context.renderMsg("更新失败，请稍后重试"); }
    }

    /**
     * Updates user system settings.
     *
     * @param context the specified context
     */
    public void updateSystem(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        final Request request = context.getRequest();
        JSONObject requestJSONObject;
        try {
            requestJSONObject = context.requestJSON();
            request.setAttribute(Keys.REQUEST, requestJSONObject);
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage());
            requestJSONObject = new JSONObject();
        }
        String systemTitle = requestJSONObject.optString(SystemSettings.SYSTEM_TITLE);
        String cardBg = requestJSONObject.optString("cardBg");
        String iconURL = requestJSONObject.optString("iconURL");
        String onlineTimeUnit = requestJSONObject.optString(SystemSettings.ONLINE_TIME_UNIT);
        boolean showSideAd = requestJSONObject.optBoolean("showSideAd");
        boolean showTopAd = requestJSONObject.optBoolean("showTopAd");
        final JSONObject settings = new JSONObject();
        settings.put(SystemSettings.SYSTEM_TITLE, systemTitle);
        settings.put("cardBg", cardBg);
        settings.put("iconURL", iconURL);
        settings.put(SystemSettings.ONLINE_TIME_UNIT, onlineTimeUnit);
        settings.put("showSideAd", showSideAd);
        settings.put("showTopAd", showTopAd);
        try {
            settingsService.setSystemSettings(settings);
            context.renderJSON(StatusCodes.SUCC);
        } catch (Exception e) {
            context.renderMsg(e.getMessage());
        }
    }

    /**
     * Updates user skins.
     *
     * @param context the specified context
     */
    public void updateSkin(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        JSONObject input;
        try { input = context.requestJSON(); } catch (final Exception e) { input = new JSONObject(); }
        final JSONObject changes = new JSONObject().put(UserExt.USER_SKIN, input.optString(UserExt.USER_SKIN))
                .put(UserExt.USER_MOBILE_SKIN, input.optString(UserExt.USER_MOBILE_SKIN));
        try {
            userSettingsMgmtService.updateLegacy(((JSONObject) context.attr(User.USER)).optString(Keys.OBJECT_ID), "display", changes);
            context.renderJSON(StatusCodes.SUCC);
        } catch (final ApiV3Exception e) { context.renderMsg(e.getMessage()); }
        catch (final Exception e) { LOGGER.error("Update skins failed", e); context.renderMsg("更新失败，请稍后重试"); }
    }

    /**
     * Updates user function.
     *
     * @param context the specified context
     */
    public void updateFunction(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final Request request = context.getRequest();
        JSONObject requestJSONObject;
        try {
            requestJSONObject = context.requestJSON();
            request.setAttribute(Keys.REQUEST, requestJSONObject);
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage());

            requestJSONObject = new JSONObject();
        }

        String userListPageSizeStr = requestJSONObject.optString(UserExt.USER_LIST_PAGE_SIZE);
        final int userCommentViewMode = requestJSONObject.optInt(UserExt.USER_COMMENT_VIEW_MODE);
        final int userAvatarViewMode = requestJSONObject.optInt(UserExt.USER_AVATAR_VIEW_MODE);
        final int userListViewMode = requestJSONObject.optInt(UserExt.USER_LIST_VIEW_MODE);
        final boolean notifyStatus = requestJSONObject.optBoolean(UserExt.USER_NOTIFY_STATUS);
        final boolean subMailStatus = requestJSONObject.optBoolean(UserExt.USER_SUB_MAIL_STATUS);
        final boolean keyboardShortcutsStatus = requestJSONObject.optBoolean(UserExt.USER_KEYBOARD_SHORTCUTS_STATUS);
        final boolean userReplyWatchArticleStatus = requestJSONObject.optBoolean(UserExt.USER_REPLY_WATCH_ARTICLE_STATUS);
        final boolean forwardStatus = requestJSONObject.optBoolean(UserExt.USER_FORWARD_PAGE_STATUS);
        final boolean chatRoomPictureStatus = requestJSONObject.optBoolean(UserExt.CHAT_ROOM_PICTURE_STATUS);
        String indexRedirectURL = requestJSONObject.optString(UserExt.USER_INDEX_REDIRECT_URL);
        if (!Strings.isURL(indexRedirectURL)) {
            indexRedirectURL = "";
        }
        try {
            indexRedirectURL = SettingsRedirects.normalize(indexRedirectURL, Latkes.getServePath());
        } catch (final ApiV3Exception e) {
            context.renderMsg(langPropsService.get("onlyInternalURLLabel"));
            return;
        }

        int userListPageSize;
        try {
            userListPageSize = Integer.valueOf(userListPageSizeStr);
            if (10 > userListPageSize) {
                userListPageSize = 10;
            }
            if (userListPageSize > 96) {
                userListPageSize = 96;
            }
        } catch (final Exception e) {
            userListPageSize = Symphonys.ARTICLE_LIST_CNT;
        }

        final JSONObject changes = new JSONObject().put(UserExt.USER_LIST_PAGE_SIZE, userListPageSize)
                .put(UserExt.USER_LIST_VIEW_MODE, userListViewMode).put(UserExt.USER_INDEX_REDIRECT_URL, indexRedirectURL)
                .put(UserExt.USER_NOTIFY_STATUS, notifyStatus ? 0 : 1).put(UserExt.USER_SUB_MAIL_STATUS, subMailStatus ? 0 : 1)
                .put(UserExt.USER_KEYBOARD_SHORTCUTS_STATUS, keyboardShortcutsStatus ? 0 : 1)
                .put(UserExt.USER_REPLY_WATCH_ARTICLE_STATUS, userReplyWatchArticleStatus ? 0 : 1)
                .put(UserExt.USER_FORWARD_PAGE_STATUS, forwardStatus ? 0 : 1).put(UserExt.CHAT_ROOM_PICTURE_STATUS, chatRoomPictureStatus ? 0 : 1);
        final JSONObject display = new JSONObject().put(UserExt.USER_COMMENT_VIEW_MODE, userCommentViewMode).put(UserExt.USER_AVATAR_VIEW_MODE, userAvatarViewMode);
        try {
            userSettingsMgmtService.updateLegacy(((JSONObject) context.attr(User.USER)).optString(Keys.OBJECT_ID), Map.of("functions", changes, "display", display));
            context.renderJSON(StatusCodes.SUCC);
        } catch (final Exception e) { LOGGER.error("Update functions failed", e); context.renderMsg("更新失败，请稍后重试"); }
    }

    /**
     * Updates user profiles.
     *
     * @param context the specified context
     */
    public void updateProfiles(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        final JSONObject requestJSONObject = context.requestJSON();
        final String userTags = requestJSONObject.optString(UserExt.USER_TAGS);
        final String userURL = requestJSONObject.optString(User.USER_URL);
        final String userQQ = requestJSONObject.optString(UserExt.USER_QQ);
        String userIntro = StringUtils.trim(requestJSONObject.optString(UserExt.USER_INTRO));
        userIntro = Escapes.escapeHTML(userIntro);
        String userNickname = StringUtils.trim(requestJSONObject.optString(UserExt.USER_NICKNAME));
        userNickname = Escapes.escapeHTML(userNickname);
        String mbti = checkMBTI(requestJSONObject.optString("mbti"));

        final JSONObject user = (JSONObject) context.attr(User.USER);
        user.put("mbti", mbti);
        user.put(UserExt.USER_TAGS, userTags);
        user.put(User.USER_URL, userURL);
        user.put(UserExt.USER_QQ, userQQ);
        user.put(UserExt.USER_INTRO, userIntro);
        user.put(UserExt.USER_NICKNAME, userNickname);
        user.put(UserExt.USER_AVATAR_TYPE, UserExt.USER_AVATAR_TYPE_C_UPLOAD);

        if (!ProfileWriteQuota.access(user.optString(Keys.OBJECT_ID))) {
            context.renderMsg("操作频繁，请稍候重试。");
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
            return;
        }

        // 敏感词检测
        String content = userNickname + "\n" + userIntro + "\n" + userTags;
        CensorResult censorResult = CensorFactory.getTextCensor().censor(content);
        CensorResult censor2Result = CensorFactory.getTextCensor().censor(userURL);
        if (censorResult.isBlocked() || censor2Result.isBlocked()) {
            context.renderMsg("个人信息中含违规信息，请修改后重试。");
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
            return;
        }

        try {
            userMgmtService.updateProfiles(user);

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }

    public static String checkMBTI(String mbti) {
        List<String> mbtiLeftArray = Arrays.asList("INTJ", "INTP", "ENTJ", "ENTP", "INFJ", "INFP", "ENFJ", "ENFP", "ISTJ", "ISFJ", "ESTJ", "ESFJ", "ISTP", "ISFP", "ESTP", "ESFP");
        List<String> mbtiRightArray = Arrays.asList("A", "T");
        mbti = mbti.toUpperCase();
        String new_mbti = "";
        try {
            if (!mbti.isEmpty()) {
                if (mbti.contains("-")) {
                    String temp_left, temp_right = "";
                    temp_left = mbti.split("-")[0];
                    temp_right = mbti.split("-")[1];
                    if (mbtiLeftArray.contains(temp_left) && mbtiRightArray.contains(temp_right)) {
                        new_mbti = temp_left + "-" + temp_right;
                    }
                } else {
                    if (mbtiLeftArray.contains(mbti)) {
                        new_mbti = mbti;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new_mbti;
    }

    /**
     * Updates user avatar.
     *
     * @param context the specified context
     */
    public void updateAvatar(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final JSONObject requestJSONObject = context.requestJSON();
        final String userAvatarURL = requestJSONObject.optString(UserExt.USER_AVATAR_URL);

        JSONObject user = Sessions.getUser();
        final String userId = user.optString(Keys.OBJECT_ID);
        user = userQueryService.getUser(userId);
        user.put(UserExt.USER_AVATAR_TYPE, UserExt.USER_AVATAR_TYPE_C_UPLOAD);
        user.put(UserExt.USER_UPDATE_TIME, System.currentTimeMillis());

        if (Strings.contains(userAvatarURL, new String[]{"<", ">", "\"", "'"})) {
            user.put(UserExt.USER_AVATAR_URL, AvatarQueryService.DEFAULT_AVATAR_URL);
        } else {
            if (Symphonys.QN_ENABLED) {
                final String qiniuDomain = Symphonys.UPLOAD_QINIU_DOMAIN;

                if (!StringUtils.startsWith(userAvatarURL, qiniuDomain)) {
                    user.put(UserExt.USER_AVATAR_URL, AvatarQueryService.DEFAULT_AVATAR_URL);
                } else {
                    user.put(UserExt.USER_AVATAR_URL, userAvatarURL);
                }
            } else {
                user.put(UserExt.USER_AVATAR_URL, userAvatarURL);
            }
        }

        try {
            userMgmtService.updateUser(user.optString(Keys.OBJECT_ID), user);

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
        }
    }

    /**
     * Updates user password.
     *
     * @param context the specified context
     */
    public void updatePassword(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final JSONObject requestJSONObject = context.requestJSON();
        final String password = requestJSONObject.optString(User.USER_PASSWORD);
        final String newPassword = requestJSONObject.optString(User.USER_NEW_PASSWORD);

        final JSONObject user = Sessions.getUser();
        if (!password.equals(user.optString(User.USER_PASSWORD))) {
            context.renderMsg(langPropsService.get("invalidOldPwdLabel"));
            return;
        }

        user.put(User.USER_PASSWORD, newPassword);

        try {
            userMgmtService.updatePassword(user);
            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("updateFailLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg, e);

            context.renderMsg(msg);
        }
    }

    /**
     * Updates user emotions.
     *
     * @param context the specified context
     */
    public void updateEmoji(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final JSONObject requestJSONObject = context.requestJSON();
        final String emotionList = requestJSONObject.optString(Emotion.EMOTIONS);

        final JSONObject user = Sessions.getUser();
        try {
            emotionMgmtService.setEmotionList(user.optString(Keys.OBJECT_ID), emotionList);

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("updateFailLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg, e);

            context.renderMsg(msg);
        }
    }

    /**
     * Point transfer.
     *
     * @param context the specified context
     */
    public void pointTransfer(final RequestContext context) {
        final JSONObject ret = new JSONObject().put(Keys.CODE, StatusCodes.ERR);
        context.renderJSON(ret);

        final JSONObject requestJSONObject = context.requestJSON();
        final JSONObject toUser = (JSONObject) context.attr(Common.TO_USER);
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        String memo = (String) context.attr(Pointtransfer.MEMO);
        if (StringUtils.isBlank(memo)) {
            memo = "";
        }

        String fromId = currentUser.optString(Keys.OBJECT_ID);
        // 手续费
        BigDecimal feeRate = new BigDecimal("0.01");
        int amountInt = requestJSONObject.optInt(Common.AMOUNT);
        // 是否是尊贵的会员
        boolean notSVIP = true;
        try {
            // 获取用户会员状态
            JSONObject memberShip = membershipQueryService.getStatusByUserId(fromId);
            if (Objects.nonNull(memberShip) && memberShip.getInt(Membership.STATE) != 0) {
                if (memberShip.optString(Membership.LV_CODE).contains("VIP4")) {
                    // 免税
                    notSVIP = false;
                }
            }
        } catch (final ServiceException e) {
        }
        // 手续费减免
        int fee = notSVIP ? (amountInt < 100 ? 1 : BigDecimal.valueOf(amountInt).multiply(feeRate).intValue()) : 0;
        // 最后金额
        final int amount = amountInt - fee;

        String fromUsername = currentUser.optString(User.USER_NAME);
        if (fromUsername.equals("admin")) {
            fromId = Pointtransfer.ID_C_SYS;
        }
        final String toId = toUser.optString(Keys.OBJECT_ID);

        final String transferId = pointtransferMgmtService.transfer(fromId, toId,
                Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT, amount, toId, System.currentTimeMillis(), memo);
        if (notSVIP) {
            // 手续费给admin
            pointtransferMgmtService.transfer(fromId, userQueryService.getUserByName("admin").optString(Keys.OBJECT_ID),
                    Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT, fee, fromId, System.currentTimeMillis(),
                    "转账手续费，纳税人：" + fromUsername);
        }
        
        final boolean succ = null != transferId;
        if (succ) {
            ret.put(Keys.CODE, StatusCodes.SUCC);
        }
        if (!succ) {
            ret.put(Keys.MSG, langPropsService.get("transferFailLabel"));
        } else {
            final JSONObject notification = new JSONObject();
            notification.put(Notification.NOTIFICATION_USER_ID, toId);
            notification.put(Notification.NOTIFICATION_DATA_ID, transferId);

            notificationMgmtService.addPointTransferNotification(notification);
        }
    }

    /**
     * Queries invitecode state.
     *
     * @param context the specified context
     */
    public void queryInvitecode(final RequestContext context) {
        final JSONObject ret = new JSONObject().put(Keys.CODE, StatusCodes.ERR);
        context.renderJSON(ret);

        final JSONObject requestJSONObject = context.requestJSON();
        String invitecode = requestJSONObject.optString(Invitecode.INVITECODE);
        if (StringUtils.isBlank(invitecode)) {
            ret.put(Keys.CODE, -1);
            ret.put(Keys.MSG, invitecode + " " + langPropsService.get("notFoundInvitecodeLabel"));
            return;
        }

        invitecode = invitecode.trim();

        final JSONObject result = invitecodeQueryService.getInvitecode(invitecode);

        if (null == result) {
            ret.put(Keys.CODE, -1);
            ret.put(Keys.MSG, langPropsService.get("notFoundInvitecodeLabel"));
        } else {
            final int status = result.optInt(Invitecode.STATUS);
            ret.put(Keys.CODE, status);

            switch (status) {
                case Invitecode.STATUS_C_USED:
                    ret.put(Keys.MSG, langPropsService.get("invitecodeUsedLabel"));
                    break;
                case Invitecode.STATUS_C_UNUSED:
                    String msg = langPropsService.get("invitecodeOkLabel");
                    msg = msg.replace("${time}", DateFormatUtils.format(result.optLong(Keys.OBJECT_ID)
                            + Symphonys.INVITECODE_EXPIRED, "yyyy-MM-dd HH:mm"));

                    ret.put(Keys.MSG, msg);
                    break;
                case Invitecode.STATUS_C_STOPUSE:
                    ret.put(Keys.MSG, langPropsService.get("invitecodeStopLabel"));
                    break;
                default:
                    ret.put(Keys.MSG, langPropsService.get("notFoundInvitecodeLabel"));
            }
        }
    }

    /**
     * Point buy invitecode.
     *
     * @param context the specified context
     */
    public void pointBuy(final RequestContext context) {
        final JSONObject ret = new JSONObject().put(Keys.CODE, StatusCodes.ERR);
        context.renderJSON(ret);

        final String allowRegister = optionQueryService.getAllowRegister();
        if (!"2".equals(allowRegister)) {
            return;
        }

        final JSONObject currentUser = Sessions.getUser();
        final String fromId = currentUser.optString(Keys.OBJECT_ID);
        final String userName = currentUser.optString(User.USER_NAME);

        // 故意先生成后返回校验，所以即使积分不够也是可以兑换成功的
        // 这是为了让积分不够的用户可以通过这个后门兑换、分发邀请码以实现积分“自充”
        // 后期可能会关掉这个【特性】
        final String invitecode = invitecodeMgmtService.userGenInvitecode(fromId, userName);

        final String transferId = pointtransferMgmtService.transfer(fromId, Pointtransfer.ID_C_SYS,
                Pointtransfer.TRANSFER_TYPE_C_BUY_INVITECODE, Pointtransfer.TRANSFER_SUM_C_BUY_INVITECODE,
                invitecode, System.currentTimeMillis(), "");
        final boolean succ = null != transferId;
        if (succ) {
            ret.put(Keys.CODE, StatusCodes.SUCC);
        }
        if (!succ) {
            ret.put(Keys.MSG, langPropsService.get("exchangeFailedLabel"));
        } else {
            String msg = langPropsService.get("expireTipLabel");
            msg = msg.replace("${time}", DateFormatUtils.format(System.currentTimeMillis()
                    + Symphonys.INVITECODE_EXPIRED, "yyyy-MM-dd HH:mm"));
            ret.put(Keys.MSG, invitecode + " " + msg);
        }
    }

    /**
     * Exports posts(article/comment) to a file.
     *
     * @param context the specified context
     */
    public void exportPosts(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final JSONObject user = Sessions.getUser();
        final String userId = user.optString(Keys.OBJECT_ID);

        final String downloadURL = postExportService.exportPosts(userId);
        if ("-1".equals(downloadURL)) {
            context.renderJSONValue(Keys.MSG, langPropsService.get("insufficientBalanceLabel"));

        } else if (StringUtils.isBlank(downloadURL)) {
            return;
        }

        context.renderJSON(StatusCodes.SUCC).renderJSONValue("url", downloadURL);
    }

    private static final String[][] emojiLists = ApiV3EmotionOptions.legacyGroups();
}
