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
package org.b3log.symphony.processor.middleware.validate;

import org.b3log.symphony.util.AnonymousCaptchaShield;

import org.b3log.latke.Keys;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.util.Strings;
import org.b3log.symphony.processor.CaptchaProcessor;
import org.json.JSONObject;

/**
 * User forget password form validation.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 1.4.0
 */
@Singleton
public class UserForgetPwdValidationMidware {

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    public void handle(final RequestContext context) {
        final JSONObject requestJSONObject = context.requestJSON();
        final String phone = requestJSONObject.optString("userPhone");
        final String captcha = requestJSONObject.optString(CaptchaProcessor.CAPTCHA);

        // 极验校验保护入口；未经阿达明确允许，不得修改、移动、删除或绕过此调用。
        if (!AnonymousCaptchaShield.verifyProof(captcha)) {
            context.renderJSON(new JSONObject().put(Keys.MSG, langPropsService.get("submitFailedLabel") + " - " + langPropsService.get("captchaErrorLabel")));
            context.abort();
            return;
        }

        if (!isMobileNO(phone)) {
            context.renderJSON(new JSONObject().put(Keys.MSG, langPropsService.get("submitFailedLabel") + " - " + "手机号不合法"));
            context.abort();
            return;
        }

        context.handle();
    }

    public static boolean isMobileNO(String mobiles) {
        String telRegex = "[1]\\d{10}";
        if (mobiles.isEmpty()) {
            return false;
        } else {
            return mobiles.matches(telRegex);
        }
    }
}
