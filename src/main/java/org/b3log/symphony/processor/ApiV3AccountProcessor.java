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

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.service.ApiV3RegistrationPolicyService;
import org.b3log.symphony.service.AccountPasswordResetService;
import org.b3log.symphony.service.AccountRegistrationService;
import org.b3log.symphony.service.AccountPasswordResetRequestService;
import org.b3log.symphony.service.AccountCaptchaService;
import org.b3log.symphony.service.AccountRegistrationRequestService;
import org.b3log.symphony.util.RegistrationVerificationQuota;
import org.b3log.symphony.util.UserNameRules;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.symphony.util.PasswordResetQuota;
import org.b3log.symphony.util.ApiV3OAuthTransportLogFilter;
import org.b3log.latke.util.Requests;
import java.util.Set;

/** 匿名账号入口的读取请求不会用 Cookie 或 apiKey 中的用户身份授予访问权限。 */
@Singleton
public class ApiV3AccountProcessor {
    @Inject
    private ApiV3RegistrationPolicyService registration;
    @Inject
    private AccountPasswordResetService passwords;
    @Inject
    private AccountRegistrationService registrations;
    @Inject
    private AccountPasswordResetRequestService resetRequests;
    @Inject
    private AccountCaptchaService captchas;
    @Inject
    private AccountRegistrationRequestService registrationRequests;

    public static void register(){
        ApiV3OAuthTransportLogFilter.requireInstalled();
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3AccountProcessor.class);
        final var anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/auth/registration-options",p::registrationOptions,anonymous::handle);
        Dispatcher.get("/api/v3/auth/captcha-options",p::captchaOptions,anonymous::handle);
        Dispatcher.post("/api/v3/auth/password-reset/complete",p::completePasswordReset,anonymous::handle);
        Dispatcher.post("/api/v3/auth/password-reset/request",p::requestPasswordReset,anonymous::handle);
        Dispatcher.post("/api/v3/auth/registration/request",p::requestRegistration,anonymous::handle);
        Dispatcher.post("/api/v3/auth/registration/complete",p::completeRegistration,anonymous::handle);
    }

    public void captchaOptions(RequestContext c) {
        ApiV3Requests.read(c, () -> captchas.options());
    }

    public void registrationOptions(RequestContext c) {
        ApiV3Requests.read(c, () -> registration.options(c.param("referral")));
    }

    protected boolean resetAllowed(String ip) {
        return PasswordResetQuota.access(ip);
    }

    public void completePasswordReset(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            // 先检查验证码尝试额度，再读取并校验仅允许从正文提交的凭据。
            if (!resetAllowed(Requests.getRemoteAddr(c.getRequest()))) {
                c.setHeader("Retry-After", "60");
                throw new ApiV3Exception(429, "rate_limited", "验证码尝试过于频繁，请稍后重试");
            }
            final String query = c.getRequest().getQueryString();
            if (query != null && !query.isEmpty()) throw ApiV3WriteInputs.invalid();
            final var bytes = c.getRequest().getBytes();
            if (bytes != null && bytes.length > 4096)
                throw new ApiV3Exception(413, "body_too_large", "请求体过大");
            final var body = ApiV3WriteInputs.body(c, Set.of("userPhone", "code", "userPassword"), false);
            return passwords.complete(ApiV3WriteInputs.string(body, "userPhone", 11, false),
                    ApiV3WriteInputs.string(body, "code", 6, false),
                    ApiV3WriteInputs.string(body, "userPassword", 32, false));
        });
    }

    protected boolean registrationAllowed(String ip) {
        return RegistrationVerificationQuota.access(ip);
    }

    public void completeRegistration(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            if (!registrationAllowed(Requests.getRemoteAddr(c.getRequest()))) {
                c.setHeader("Retry-After", "60");
                throw new ApiV3Exception(429, "rate_limited", "验证码尝试过于频繁，请稍后重试");
            }
            final String query = c.getRequest().getQueryString();
            if (query != null && !query.isEmpty()) throw ApiV3WriteInputs.invalid();
            final var bytes = c.getRequest().getBytes();
            if (bytes != null && bytes.length > 4096)
                throw new ApiV3Exception(413, "body_too_large", "请求体过大");
            final var body = ApiV3WriteInputs.body(c, Set.of("userPhone", "code", "userPassword", "userAppRole", "mbti", "referral"), false);
            final String referral = ApiV3WriteInputs.string(body, "referral", 64, true);
            if (!referral.isEmpty() && UserNameRules.invalid(referral)) throw ApiV3WriteInputs.invalid();
            return registrations.complete(ApiV3WriteInputs.string(body, "userPhone", 11, false),
                    ApiV3WriteInputs.string(body, "code", 6, false),
                    ApiV3WriteInputs.string(body, "userPassword", 32, false),
                    ApiV3WriteInputs.integer(body, "userAppRole", 0, 0, 1),
                    ApiV3WriteInputs.string(body, "mbti", 32, false), referral);
        });
    }

    /** 发起注册，校验人机凭据和当前邀请规则，并绑定短信验证凭据。 */
    public void requestRegistration(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final String query = c.getRequest().getQueryString();
            if (query != null && !query.isEmpty()) throw ApiV3WriteInputs.invalid();
            final var bytes = c.getRequest().getBytes();
            if (bytes != null && bytes.length > 8192)
                throw new ApiV3Exception(413, "body_too_large", "请求体过大");
            final var body = ApiV3WriteInputs.body(c, Set.of("userName", "userPhone", "captcha", "invitecode", "referral"), false);
            return registrationRequests.request(ApiV3WriteInputs.string(body, "userName", 64, false),
                    ApiV3WriteInputs.string(body, "userPhone", 11, false),
                    ApiV3WriteInputs.string(body, "captcha", 4096, false),
                    ApiV3WriteInputs.string(body, "invitecode", 16, false),
                    ApiV3WriteInputs.string(body, "referral", 64, false), "", Requests.getRemoteAddr(c.getRequest()));
        });
    }

    public void requestPasswordReset(RequestContext c) {
        ApiV3Requests.respond(c, () -> {
            final String query = c.getRequest().getQueryString();
            if (query != null && !query.isEmpty()) throw ApiV3WriteInputs.invalid();
            final var bytes = c.getRequest().getBytes();
            if (bytes != null && bytes.length > 8192)
                throw new ApiV3Exception(413, "body_too_large", "请求体过大");
            final var body = ApiV3WriteInputs.body(c, Set.of("userPhone", "captcha"), false);
            return resetRequests.request(ApiV3WriteInputs.string(body, "userPhone", 11, false),
                    ApiV3WriteInputs.string(body, "captcha", 4096, false), Requests.getRemoteAddr(c.getRequest()));
        });
    }

}
