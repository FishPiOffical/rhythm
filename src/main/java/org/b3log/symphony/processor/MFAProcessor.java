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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.UserMgmtService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.MultiFactorAuthenticator;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public class MFAProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(MFAProcessor.class);

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
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);

        final MFAProcessor mfaProcessor = beanManager.getReference(MFAProcessor.class);
        Dispatcher.get("/mfa", mfaProcessor::getMFA, loginCheck::handle);
        Dispatcher.get("/mfa/verify", mfaProcessor::verifyMFA, loginCheck::handle);
        Dispatcher.get("/mfa/remove", mfaProcessor::removeMFA, loginCheck::handle);
        Dispatcher.get("/mfa/enabled", mfaProcessor::enabledMFA, loginCheck::handle);
    }

    private static final Map<String, String> MFA_CACHE = Collections.synchronizedMap(new LinkedHashMap<String, String>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 100;
        }
    });

    public void getMFA(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        JSONObject json = new JSONObject();
        String userId = currentUser.optString(Keys.OBJECT_ID);
        String getSecret = userQueryService.getSecret2fa(userId);
        if (!getSecret.isEmpty()) {
            context.renderJSON(StatusCodes.ERR).renderMsg("已绑定2FA，无法获取新的Secret");
            return;
        }
        if (MFA_CACHE.containsKey(userId)) {
            String secret = MFA_CACHE.get(userId);
            String qrCodeLink = MultiFactorAuthenticator.getQRBarcodeURL(userId, secret);
            json.put("qrCodeLink", qrCodeLink);
            json.put("user", userId);
            json.put("secret", secret);
            context.renderJSON(json.put("code", 0));
        } else {
            String secret = MultiFactorAuthenticator.generateSecretKey();
            String qrCodeLink = MultiFactorAuthenticator.getQRBarcodeURL(userId, secret);
            json.put("qrCodeLink", qrCodeLink);
            json.put("user", userId);
            json.put("secret", secret);
            MFA_CACHE.put(userId, secret);
            context.renderJSON(json.put("code", 0));
        }
    }

    public void verifyMFA(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        String userId = currentUser.optString(Keys.OBJECT_ID);
        try {
            long code = Long.parseLong(context.param("code"));
            if (verify(userId, code)) {
                context.renderJSON(StatusCodes.SUCC).renderMsg("设置成功");
            } else {
                context.renderJSON(StatusCodes.ERR).renderMsg("认证码错误，请重试");
            }
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("认证码不合法");
        }
    }

    public synchronized void removeMFA(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        String userId = currentUser.optString(Keys.OBJECT_ID);
        try {
            final JSONObject user = userQueryService.getUser(userId);
            user.put("secret2fa", "");
            userMgmtService.updateUser(userId, user);
            context.renderJSON(StatusCodes.SUCC).renderMsg("解绑成功");
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("解绑失败");
        }
    }

    public void enabledMFA(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        String userId = currentUser.optString(Keys.OBJECT_ID);
        String secret = userQueryService.getSecret2fa(userId);
        if (!secret.isEmpty()) {
            context.renderJSON(StatusCodes.SUCC).renderMsg("已绑定2FA");
            return;
        }
        context.renderJSON(StatusCodes.ERR).renderMsg("未绑定2FA");
    }

    public static boolean verifyLogin(String userId, long code) throws ServiceException {
        final BeanManager beanManager = BeanManager.getInstance();
        final UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
        final String secret;
        try {
            secret = userQueryService.getSecret2fa(userId);
        } catch (final IllegalStateException e) {
            LOGGER.error("Reads account configuration failed", e);
            throw new ServiceException(e);
        }
        if (secret.isEmpty()) {
            return true;
        }
        return check(secret, code);
    }

    public synchronized static boolean verify(String userId, long code) {
        try {
            final BeanManager beanManager = BeanManager.getInstance();
            final UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
            final UserMgmtService userMgmtService = beanManager.getReference(UserMgmtService.class);
            String secret = userQueryService.getSecret2fa(userId);
            if (secret.isEmpty()) {
                // 初次验证
                if (!MFA_CACHE.containsKey(userId)) {
                    return false;
                }
                // 验证并且写数据库
                if (check(MFA_CACHE.get(userId), code)) {
                    // 验证通过
                    final JSONObject user = userQueryService.getUser(userId);
                    user.put("secret2fa", MFA_CACHE.get(userId));
                    userMgmtService.updateUser(userId, user);
                    MFA_CACHE.remove(userId);
                    return true;
                } else {
                    return false;
                }
            } else {
                // 已经验证过
                return check(secret, code);
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "failed to verify MFA", e);
            return false;
        }
    }

    private static boolean check(String secret, long code) {
        return MultiFactorAuthenticator.checkCode(secret, code, System.currentTimeMillis());
    }
}
