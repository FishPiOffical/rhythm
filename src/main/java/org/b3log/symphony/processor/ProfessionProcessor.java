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
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.ProfessionPrivacyMgmtService;
import org.b3log.symphony.service.ProfessionDefinitionRegistry;
import org.b3log.symphony.service.ProfessionProfileDetailQueryService;
import org.b3log.symphony.service.ProfessionRankingQueryService;
import org.b3log.symphony.service.PublicProfessionProfileQueryService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Sessions;
import org.json.JSONObject;

import java.util.Optional;

/** 职业选择、隐私设置与公开职业资料接口。 */
@Singleton
public class ProfessionProcessor {

    private static final int DEFAULT_RECORD_LIMIT = 30;
    private static final int MAX_RECORD_LIMIT = 100;

    @Inject private ProfessionPrivacyMgmtService privacyMgmtService;
    @Inject private ProfessionDefinitionRegistry definitionRegistry;
    @Inject private ProfessionProfileDetailQueryService profileDetailQueryService;
    @Inject private ProfessionRankingQueryService rankingQueryService;
    @Inject private PublicProfessionProfileQueryService publicProfileQueryService;
    @Inject private UserQueryService userQueryService;

    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final ProfessionProcessor processor = beanManager.getReference(ProfessionProcessor.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrf = beanManager.getReference(CSRFMidware.class);
        final AnonymousViewCheckMidware anonymousView = beanManager.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/profession/me", processor::getMine, loginCheck::handle);
        Dispatcher.get("/api/profession/me/{professionId}/detail", processor::getMineDetail, loginCheck::handle);
        Dispatcher.get("/api/profession/me/{professionId}/records", processor::getMineRecords, loginCheck::handle);
        Dispatcher.get("/api/profession/ranking", processor::getRanking, loginCheck::handle);
        Dispatcher.post("/api/profession/me/skip", processor::skip, loginCheck::handle, csrf::check);
        Dispatcher.post("/api/profession/me/primary", processor::selectPrimary, loginCheck::handle, csrf::check);
        Dispatcher.post("/api/profession/me/privacy", processor::updatePrivacy, loginCheck::handle, csrf::check);
        Dispatcher.get("/api/user/{userName}/profession", processor::getPublic, anonymousView::handle);
    }

    public void getMine(final RequestContext context) {
        try {
            success(context, privacyMgmtService.getProfile(currentUserId(context)));
        } catch (final Exception e) {
            error(context, e.getMessage());
        }
    }

    public void skip(final RequestContext context) {
        try {
            privacyMgmtService.skipOnboarding(currentUserId(context));
            success(context, new JSONObject());
        } catch (final Exception e) {
            error(context, e.getMessage());
        }
    }

    public void getMineDetail(final RequestContext context) {
        try {
            success(context, profileDetailQueryService.getDetail(currentUserId(context), context.pathVar("professionId"),
                    isDark(context)));
        } catch (final Exception e) {
            error(context, e.getMessage());
        }
    }

    public void getMineRecords(final RequestContext context) {
        try {
            success(context, profileDetailQueryService.getRecords(new ProfessionProfileDetailQueryService.RecordQuery(
                    currentUserId(context), context.pathVar("professionId"), longParam(context, "beforeOccurredAt"),
                    effectCursor(context), intParam(context, "limit", DEFAULT_RECORD_LIMIT, MAX_RECORD_LIMIT))));
        } catch (final Exception e) {
            error(context, e.getMessage());
        }
    }

    public void getRanking(final RequestContext context) {
        try {
            final ProfessionRankingQueryService.RankingPage page = rankingQueryService.getPage(
                    context.param("professionId"), currentUserId(context), isDark(context));
            final JSONObject result = new JSONObject().put("selectedProfession", page.selected())
                    .put("professions", page.definitions()).put("entries", page.entries());
            success(context, result);
        } catch (final Exception e) {
            error(context, e.getMessage());
        }
    }

    public void selectPrimary(final RequestContext context) {
        try {
            privacyMgmtService.selectPrimary(currentUserId(context), context.requestJSON().optString("professionId"));
            success(context, privacyMgmtService.getProfile(currentUserId(context)));
        } catch (final Exception e) {
            error(context, e.getMessage());
        }
    }

    public void updatePrivacy(final RequestContext context) {
        try {
            final JSONObject request = context.requestJSON();
            final JSONObject visibility = request.optJSONObject("moduleVisibility");
            privacyMgmtService.updatePrivacy(currentUserId(context), request.optString("preset"),
                    null == visibility ? "" : visibility.toString());
            success(context, privacyMgmtService.getProfile(currentUserId(context)));
        } catch (final Exception e) {
            error(context, e.getMessage());
        }
    }

    public void getPublic(final RequestContext context) {
        try {
            final JSONObject target = userQueryService.getUserByName(context.pathVar("userName"));
            if (null == target) {
                context.sendError(404);
                return;
            }
            final Optional<JSONObject> profile = publicProfileQueryService.get(
                    new PublicProfessionProfileQueryService.ViewerRequest(target.getString(Keys.OBJECT_ID),
                            currentViewerId(context), displayPosition(context), isDark(context)));
            if (profile.isEmpty()) {
                context.sendError(404);
                return;
            }
            success(context, profile.get());
        } catch (final Exception e) {
            error(context, e.getMessage());
        }
    }

    private String currentUserId(final RequestContext context) {
        final JSONObject current = (JSONObject) context.attr(User.USER);
        if (null == current || current.optString(Keys.OBJECT_ID).isBlank()) {
            throw new IllegalStateException("未登录");
        }
        return current.getString(Keys.OBJECT_ID);
    }

    private String currentViewerId(final RequestContext context) {
        final JSONObject current = (JSONObject) context.attr(User.USER);
        if (null != current) {
            return current.optString(Keys.OBJECT_ID);
        }
        JSONObject viewer = userQueryService.getCurrentUser(context.getRequest());
        if (null == viewer) viewer = Sessions.getUser();
        if (null == viewer && null != context.param("apiKey") && !context.param("apiKey").isBlank()) {
            viewer = ApiProcessor.getUserByKey(context.param("apiKey"));
        }
        return null == viewer ? "" : viewer.optString(Keys.OBJECT_ID);
    }

    private String displayPosition(final RequestContext context) {
        final String position = context.param("position");
        final String result = null == position || position.isBlank() ? "homeProfile" : position;
        definitionRegistry.validatePositionCode(result);
        return result;
    }

    private boolean isDark(final RequestContext context) {
        return "true".equals(context.param("dark"));
    }

    private long longParam(final RequestContext context, final String key) {
        final String value = context.param(key);
        if (null == value || value.isBlank()) {
            return 0L;
        }
        try {
            final long parsed = Long.parseLong(value);
            if (parsed < 0L) {
                throw new IllegalArgumentException(key + "不合法");
            }
            return parsed;
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(key + "不合法", e);
        }
    }

    private int intParam(final RequestContext context, final String key, final int defaultValue, final int maximum) {
        final String value = context.param(key);
        if (null == value || value.isBlank()) {
            return defaultValue;
        }
        try {
            final int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > maximum) {
                throw new IllegalArgumentException(key + "不合法");
            }
            return parsed;
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(key + "不合法", e);
        }
    }

    private String effectCursor(final RequestContext context) {
        final String value = context.param("beforeEffectId");
        if (null == value || value.isBlank()) {
            return "";
        }
        if (!value.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException("beforeEffectId不合法");
        }
        return value;
    }

    private void success(final RequestContext context, final JSONObject data) {
        final JSONObject response = new JSONObject();
        response.put(Keys.CODE, StatusCodes.SUCC);
        response.put(Common.DATA, data);
        context.renderJSON(response);
    }

    private void error(final RequestContext context, final String message) {
        final JSONObject response = new JSONObject();
        response.put(Keys.CODE, StatusCodes.ERR);
        response.put(Keys.MSG, null == message ? "职业操作失败" : message);
        response.put(Common.DATA, new JSONObject());
        context.renderJSON(response);
    }
}
