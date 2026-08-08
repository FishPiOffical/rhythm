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

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.service.ProfessionExternalExperienceMgmtService;
import org.b3log.symphony.service.PublicProfessionProfileQueryService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.util.Optional;

/** 面向第三方的职业经验金手指与隐私受限查询接口。 */
@Singleton
public class ProfessionGoldFingerProcessor {

    @Inject private ProfessionExternalExperienceMgmtService externalExperienceService;
    @Inject private PublicProfessionProfileQueryService publicProfileQueryService;
    @Inject private UserQueryService userQueryService;

    public static void register() {
        final ProfessionGoldFingerProcessor processor = BeanManager.getInstance().getReference(ProfessionGoldFingerProcessor.class);
        Dispatcher.post("/api/gold-finger/profession/experience", processor::grantExperience);
        Dispatcher.post("/api/gold-finger/profession/query", processor::queryProfile);
    }

    public void grantExperience(final RequestContext context) {
        execute(context, request -> externalExperienceService.grant(new ProfessionExternalExperienceMgmtService.GrantRequest(
                text(request, "sourceAppId", 64), text(request, "requestId", 128), request.optString("sourceScene"),
                text(request, "userName", 20), text(request, "professionId", 19), request.optLong("experienceDelta"))));
    }

    public void queryProfile(final RequestContext context) {
        execute(context, request -> {
            final JSONObject user = userQueryService.getUserByName(text(request, "userName", 20));
            if (null == user) throw new IllegalArgumentException("用户不存在");
            final Optional<JSONObject> profile = publicProfileQueryService.get(new PublicProfessionProfileQueryService.ViewerRequest(
                    user.getString(Keys.OBJECT_ID), "", "professionPage", false));
            if (profile.isEmpty()) throw new IllegalArgumentException("该用户未公开职业资料");
            return profile.get();
        });
    }

    private void execute(final RequestContext context, final Action action) {
        try {
            final JSONObject request = context.requestJSON();
            authenticate(request);
            success(context, action.run(request));
        } catch (final Exception e) {
            error(context, e.getMessage());
        }
    }

    private void authenticate(final JSONObject request) {
        final String configured = Symphonys.get("gold.finger.profession");
        if (configured == null || !configured.equals(request.optString("goldFingerKey"))) {
            throw new SecurityException("金手指密钥无效");
        }
    }

    private String text(final JSONObject request, final String key, final int max) {
        final String value = request.optString(key);
        if (value.isBlank() || value.length() > max) throw new IllegalArgumentException(key + "不合法");
        return value;
    }

    private void success(final RequestContext context, final JSONObject data) { context.renderJSON(new JSONObject().put(Keys.CODE, StatusCodes.SUCC).put(Common.DATA, data)); }
    private void error(final RequestContext context, final String message) { context.renderJSON(new JSONObject().put(Keys.CODE, StatusCodes.ERR).put(Keys.MSG, message).put(Common.DATA, new JSONObject())); }
    @FunctionalInterface private interface Action { JSONObject run(JSONObject request) throws Exception; }
}
