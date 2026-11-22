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

import org.b3log.latke.http.RequestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.processor.ApiV3Processor;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.SecurityMonitor;
import org.json.JSONObject;

/**
 * API v3 仅从 Authorization 请求头接受 Bearer 凭据作为身份。
 */
@Singleton
public class ApiV3AuthMidware {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3AuthMidware.class);
    @Inject
    private ApiV3TokenMgmtService apiV3TokenMgmtService;

    /**
     * 公开资源允许匿名访问，显式提交的凭据必须通过验证。
     */
    public void optionalContent(final RequestContext context) {
        optional(context, "content.read");
    }

    public void optionalProfile(final RequestContext context) {
        optional(context, "profile.read");
    }

    // [验证码链路指纹开始：optional]
    private void optional(final RequestContext context, final String scope) {
        if (null == context.header("Authorization")) {
            context.handle();
            return;
        }
        final JSONObject identity = resolve(context);
        if (null == identity) return;
        if (!ApiV3TokenMgmtService.hasScope(identity.optString("scope"), scope)) {
            LOGGER.info("api_v3_auth result=insufficient_scope required={} requestId={}", scope, ApiV3Processor.requestId(context));
            ApiV3Processor.error(context, 403, "insufficient_scope", "缺少所需权限");
            context.abort();
            return;
        }
        proceed(context, identity);
    }
    // [验证码链路指纹结束：optional]

    // [验证码链路指纹开始：handle]
    public void handle(final RequestContext context) {
        final JSONObject identity = resolve(context);
        if (identity != null) proceed(context, identity);
    }
    // [验证码链路指纹结束：handle]

    public void messagesRead(final RequestContext context) {
        required(context, "messages.read");
    }

    public void messagesWrite(final RequestContext context) {
        required(context, "messages.write");
    }

    public void notificationsRead(final RequestContext context) {
        required(context, "notifications.read");
    }

    public void notificationsWrite(final RequestContext context) {
        required(context, "notifications.write");
    }

    public void commentsWrite(final RequestContext context) {
        required(context, "comments.write");
    }

    public void reactionsWrite(final RequestContext context) {
        required(context, "reactions.write");
    }

    public void followsWrite(final RequestContext context) {
        required(context, "follows.write");
    }

    public void settingsWrite(final RequestContext context) {
        required(context, "settings.write");
    }

    public void uploadsWrite(final RequestContext context) {
        required(context, "uploads.write");
    }

    public void contentWrite(final RequestContext context) {
        required(context, "content.write");
    }

    public void contentRead(final RequestContext context) {
        required(context, "content.read");
    }

    public void activityWrite(final RequestContext context) {
        required(context, "activity.write");
    }

    public void activityRead(final RequestContext context) {
        required(context, "activity.read");
    }

    public void profileRead(final RequestContext context) {
        required(context, "profile.read");
    }

    public void partnerRead(final RequestContext context) {
        required(context, "profile.read", true);
    }

    public void partnerWrite(final RequestContext context) {
        required(context, "settings.write", true);
    }

    // [验证码链路指纹开始：required]
    private void required(final RequestContext context, final String scope) {
        required(context, scope, false);
    }
    // [验证码链路指纹结束：required]

    // [验证码链路指纹开始：required]
    private void required(final RequestContext context, final String scope, final boolean partner) {
        final JSONObject identity = resolve(context, partner);
        if (null == identity) return;
        if (!ApiV3TokenMgmtService.hasScope(identity.optString("scope"), scope)) {
            LOGGER.info("api_v3_auth result=insufficient_scope required={} requestId={}", scope, ApiV3Processor.requestId(context));
            ApiV3Processor.error(context, 403, "insufficient_scope", "缺少所需权限");
            context.abort();
            return;
        }
        proceed(context, identity);
    }
    // [验证码链路指纹结束：required]

    // [验证码链路指纹开始：resolve]
    private JSONObject resolve(final RequestContext context) {
        return resolve(context, false);
    }
    // [验证码链路指纹结束：resolve]

    // [验证码链路指纹开始：resolve]
    private JSONObject resolve(final RequestContext context, final boolean partner) {
        final String header = context.header("Authorization");
        if (null == header || !header.startsWith("Bearer ") || header.length() > 300) {
            LOGGER.info("api_v3_auth result=unauthorized requestId={}", ApiV3Processor.requestId(context));
            ApiV3Processor.error(context, 401, "unauthorized", "登录凭据无效");
            context.abort();
            return null;
        }
        final String token = header.substring("Bearer ".length()).trim();
        final JSONObject identity;
        try {
            identity = partner ? apiV3TokenMgmtService.authenticatePartner(token) : apiV3TokenMgmtService.authenticate(token);
        } catch (final ApiV3Exception error) {
            ApiV3Processor.error(context, error.status, error.code, error.getMessage());
            context.abort();
            return null;
        }
        if (null == identity) {
            LOGGER.info("api_v3_auth result=unauthorized requestId={}", ApiV3Processor.requestId(context));
            ApiV3Processor.error(context, 401, "unauthorized", "登录凭据无效");
            context.abort();
            return null;
        }
        return identity;
    }
    // [验证码链路指纹结束：resolve]

    // [验证码链路指纹开始：proceed]
    private void proceed(final RequestContext context, final JSONObject identity) {
        final JSONObject user = identity.optJSONObject("user");
        context.attr(User.USER, user);
        context.attr(ApiV3Processor.IDENTITY, identity);
        SecurityMonitor.markAuthenticated(user);
        context.handle();
    }
    // [验证码链路指纹结束：proceed]
}
