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

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.Membership;
import org.b3log.symphony.model.MembershipLevel;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.MembershipMgmtService;
import org.b3log.symphony.service.MembershipQueryService;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;
import org.json.JSONArray;
import org.b3log.latke.repository.annotation.Transactional;

import java.util.Map;

/**
 * 会员模块处理器：Admin 管理与开放 API。
 */
@Singleton
public class MembershipProcessor {

    @Inject
    private MembershipMgmtService membershipMgmtService;

    @Inject
    private MembershipQueryService membershipQueryService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);

        final MembershipProcessor processor = beanManager.getReference(MembershipProcessor.class);

        // Admin：会员等级管理
        Dispatcher.post("/admin/membership/level", processor::addLevel, loginCheck::handle);
        Dispatcher.put("/admin/membership/level/{oId}", processor::updateLevel, loginCheck::handle);
        Dispatcher.delete("/admin/membership/level/{oId}", processor::removeLevel, loginCheck::handle);
        Dispatcher.get("/api/membership/levels", processor::listLevels);
        // API：查询会员状态（按 userId 唯一） & 开通会员 & 更新用户配置
        // 一次性查询出所有激活用户的配置（公开，无需登录）
        Dispatcher.get("/api/memberships/configs", processor::listActiveConfigs);
        Dispatcher.get("/api/membership/{userId}", processor::getUserMembershipStatus);
        Dispatcher.post("/api/membership/open", processor::openMembership, loginCheck::handle);
        Dispatcher.put("/api/membership/config", processor::updateUserConfig, loginCheck::handle);
        // Page: 会员页面
        Dispatcher.get("/vips", processor::showVipPage, loginCheck::handle);
        Dispatcher.get("/vips-admin",processor::showVipAdminPage,loginCheck::handle);
    }

    private boolean isAdmin(final JSONObject user) {
        return null != user && Role.ROLE_ID_C_ADMIN.equals(user.optString(User.USER_ROLE));
    }

    public void addLevel(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", "无权限");
            response.put("data", new JSONObject());
            context.renderJSON(response);
            return;
        }
        final JSONObject req = context.requestJSON();
        try {
            final JSONObject level = new JSONObject();
            level.put(MembershipLevel.LV_NAME, req.optString(MembershipLevel.LV_NAME));
            level.put(MembershipLevel.LV_CODE, req.optString(MembershipLevel.LV_CODE));
            level.put(MembershipLevel.PRICE, req.optInt(MembershipLevel.PRICE));
            level.put(MembershipLevel.DURATION_TYPE, req.optString(MembershipLevel.DURATION_TYPE));
            level.put(MembershipLevel.DURATION_VALUE, req.optInt(MembershipLevel.DURATION_VALUE));
            level.put(MembershipLevel.BENEFITS, req.optString(MembershipLevel.BENEFITS));

            final String id = membershipMgmtService.addLevel(level);
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.SUCC);
            response.put("msg", "success");
            response.put("data", new JSONObject().put(Keys.OBJECT_ID, id));
            context.renderJSON(response);
        } catch (ServiceException e) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", e.getMessage());
            response.put("data", new JSONObject());
            context.renderJSON(response);
        }
    }

    public void updateLevel(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", "无权限");
            response.put("data", new JSONObject());
            context.renderJSON(response);
            return;
        }
        final String oId = context.pathVar("oId");
        final JSONObject req = context.requestJSON();
        try {
            membershipMgmtService.updateLevel(oId, req);
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.SUCC);
            response.put("msg", "success");
            response.put("data", new JSONObject());
            context.renderJSON(response);
        } catch (ServiceException e) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", e.getMessage());
            response.put("data", new JSONObject());
            context.renderJSON(response);
        }
    }

    public void removeLevel(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", "无权限");
            response.put("data", new JSONObject());
            context.renderJSON(response);
            return;
        }
        final String oId = context.pathVar("oId");
        try {
            membershipMgmtService.removeLevel(oId);
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.SUCC);
            response.put("msg", "success");
            response.put("data", new JSONObject());
            context.renderJSON(response);
        } catch (ServiceException e) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", e.getMessage());
            response.put("data", new JSONObject());
            context.renderJSON(response);
        }
    }

    /**
     * 列出所有会员等级（公开接口）。
     */
    public void listLevels(final RequestContext context) {
        try {
            final java.util.List<JSONObject> levels = membershipQueryService.listLevels();
            final JSONObject response = new JSONObject();
            response.put("data", levels != null ? new JSONArray(levels) : new JSONArray());
            response.put("code", StatusCodes.SUCC);
            response.put("msg", "success");
            context.renderJSON(response);
        } catch (ServiceException e) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", e.getMessage());
            response.put("data", new JSONArray());
            context.renderJSON(response);
        }
    }

    /**
     * 一次性查询所有激活用户的会员配置（Admin）。
     * 返回数组 data，其中每项为一个会员记录（含 configJson）。
     */
    public void listActiveConfigs(final RequestContext context) {
        try {
            final java.util.List<org.json.JSONObject> memberships = membershipQueryService.listActiveConfigs();
            final org.json.JSONArray data = new org.json.JSONArray();
            for (final org.json.JSONObject m : memberships) {
                final org.json.JSONObject item = new org.json.JSONObject();
                item.put(org.b3log.symphony.model.Membership.USER_ID, m.optString(org.b3log.symphony.model.Membership.USER_ID));
                item.put(org.b3log.symphony.model.Membership.CONFIG_JSON, m.optString(org.b3log.symphony.model.Membership.CONFIG_JSON));
                data.put(item);
            }
            final org.json.JSONObject response = new org.json.JSONObject();
            response.put("code", StatusCodes.SUCC);
            response.put("msg", "success");
            response.put("data", data);
            context.renderJSON(response);
        } catch (ServiceException e) {
            final org.json.JSONObject response = new org.json.JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", e.getMessage());
            response.put("data", new org.json.JSONArray());
            context.renderJSON(response);
        }
    }

    public void getMembershipStatus(final RequestContext context) {
        final String userId = context.pathVar("userId");
        final String lvCode = context.pathVar("lvCode");
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(lvCode)) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", "参数错误");
            response.put("data", new JSONObject());
            context.renderJSON(response);
            return;
        }
        try {
            final JSONObject status = membershipQueryService.getStatus(userId, lvCode);
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.SUCC);
            response.put("msg", "success");
            response.put("data", status);
            context.renderJSON(response);
        } catch (ServiceException e) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", e.getMessage());
            response.put("data", new JSONObject());
            context.renderJSON(response);
        }
    }

    public void getUserMembershipStatus(final RequestContext context) {
        final String userId = context.pathVar("userId");
        if (StringUtils.isBlank(userId)) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", "参数错误");
            response.put("data", new JSONObject());
            context.renderJSON(response);
            return;
        }
        try {
            final org.json.JSONObject status = membershipQueryService.getStatusByUserId(userId);
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.SUCC);
            response.put("msg", "success");
            response.put("data", status);
            context.renderJSON(response);
        } catch (ServiceException e) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", e.getMessage());
            response.put("data", new JSONObject());
            context.renderJSON(response);
        }
    }

    public void openMembership(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (null == user) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", "未登录");
            response.put("data", new JSONObject());
            context.renderJSON(response);
            return;
        }
        final JSONObject req = context.requestJSON();
        final String levelOId = req.optString(Keys.OBJECT_ID);
        final String configJson = req.optString(Membership.CONFIG_JSON);
        // 优惠券
        final String couponCode = req.optString(Membership.COUPON_CODE);

        if (StringUtils.isBlank(levelOId)) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", "参数错误");
            response.put("data", new JSONObject());
            context.renderJSON(response);
            return;
        }

        try {
            final JSONObject result = membershipMgmtService.openMembership(user.optString(Keys.OBJECT_ID), levelOId, configJson, couponCode);
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.SUCC);
            response.put("msg", "success");
            response.put("data", result);
            context.renderJSON(response);
        } catch (ServiceException e) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", e.getCause().getMessage());
            response.put("data", new JSONObject());
            context.renderJSON(response);
        }
    }

    /**
     * 更新当前用户的会员配置 configJson。
     */
    @Transactional
    public void updateUserConfig(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (null == user) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", "未登录");
            response.put("data", new JSONObject());
            context.renderJSON(response);
            return;
        }
        final JSONObject req = context.requestJSON();
        String configJson = req.optString(Membership.CONFIG_JSON);
        // 兼容直接传对象
        if (StringUtils.isBlank(configJson)) {
            final JSONObject cfgObj = req.optJSONObject("config");
            if (null != cfgObj) {
                configJson = cfgObj.toString();
            }
        }
        if (StringUtils.isBlank(configJson)) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", "参数错误：configJson 不能为空");
            response.put("data", new JSONObject());
            context.renderJSON(response);
            return;
        }
        try {
            final JSONObject updated = membershipMgmtService.updateUserConfig(user.optString(Keys.OBJECT_ID), configJson);
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.SUCC);
            response.put("msg", "success");
            response.put("data", new JSONObject().put("membership", updated));
            context.renderJSON(response);
        } catch (ServiceException e) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", e.getMessage());
            response.put("data", new JSONObject());
            context.renderJSON(response);
        }
    }

    public void showVipPage(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "vip/index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final JSONObject user = (JSONObject) context.attr(User.USER);
        dataModel.put(User.USER, user);
        try {
            final org.json.JSONObject status = membershipQueryService.getStatusByUserId(user.optString(Keys.OBJECT_ID));
            dataModel.put("membership", status);
        } catch (final org.b3log.latke.service.ServiceException e) {
            dataModel.put("membership", new JSONObject());
        }
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    public void showVipAdminPage(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            context.sendError(404);
            return;
        }
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "vip/admin.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }


    private JSONObject getUser(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {}
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {}
        return currentUser;
    }
}