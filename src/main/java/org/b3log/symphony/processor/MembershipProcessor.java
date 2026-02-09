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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Transactional;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.Membership;
import org.b3log.symphony.model.MembershipLevel;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.MembershipMgmtService;
import org.b3log.symphony.service.MembershipQueryService;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONArray;
import org.json.JSONObject;

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

    @Inject
    private UserRepository userRepository;

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

        // Admin：VIP 管理页与管理接口
        Dispatcher.get("/admin/vip", processor::showAdminVipManagePage, loginCheck::handle);
        Dispatcher.post("/api/admin/vip/list", processor::adminListMemberships, loginCheck::handle);
        Dispatcher.post("/api/admin/vip/add", processor::adminAddMembershipNoCost, loginCheck::handle);
        Dispatcher.post("/api/admin/vip/update", processor::adminUpdateMembership, loginCheck::handle);
        Dispatcher.post("/api/admin/vip/refund", processor::adminRefundMembershipByDays, loginCheck::handle);
        Dispatcher.post("/api/admin/vip/extend", processor::adminExtendMembershipDays, loginCheck::handle);

        // API：查询会员状态（按 userId 唯一） & 开通会员 & 更新用户配置
        Dispatcher.get("/api/membership/levels", processor::listLevels);
        // 一次性查询出所有激活用户的配置（公开，无需登录）
        Dispatcher.get("/api/memberships/configs", processor::listActiveConfigs);
        Dispatcher.get("/api/membership/{userId}", processor::getUserMembershipStatus);
        Dispatcher.post("/api/membership/open", processor::openMembership, loginCheck::handle);
        Dispatcher.put("/api/membership/config", processor::updateUserConfig, loginCheck::handle);

        // Page: 会员页面
        Dispatcher.get("/vips", processor::showVipPage, loginCheck::handle);
        Dispatcher.get("/vips-admin", processor::showVipAdminPage, loginCheck::handle);
    }

    private boolean isAdmin(final JSONObject user) {
        return null != user && Role.ROLE_ID_C_ADMIN.equals(user.optString(User.USER_ROLE));
    }

    public void addLevel(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            renderError(context, "无权限");
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
            renderSuccess(context, new JSONObject().put(Keys.OBJECT_ID, id));
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void updateLevel(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            renderError(context, "无权限");
            return;
        }
        final String oId = context.pathVar("oId");
        final JSONObject req = context.requestJSON();
        try {
            membershipMgmtService.updateLevel(oId, req);
            renderSuccess(context, new JSONObject());
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void removeLevel(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            renderError(context, "无权限");
            return;
        }
        final String oId = context.pathVar("oId");
        try {
            membershipMgmtService.removeLevel(oId);
            renderSuccess(context, new JSONObject());
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
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
            final java.util.List<JSONObject> memberships = membershipQueryService.listActiveConfigs();
            final JSONArray data = new JSONArray();
            for (final JSONObject m : memberships) {
                final JSONObject item = new JSONObject();
                item.put(Membership.USER_ID, m.optString(Membership.USER_ID));
                item.put(Membership.CONFIG_JSON, m.optString(Membership.CONFIG_JSON));
                data.put(item);
            }
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.SUCC);
            response.put("msg", "success");
            response.put("data", data);
            context.renderJSON(response);
        } catch (ServiceException e) {
            final JSONObject response = new JSONObject();
            response.put("code", StatusCodes.ERR);
            response.put("msg", e.getMessage());
            response.put("data", new JSONArray());
            context.renderJSON(response);
        }
    }

    public void getMembershipStatus(final RequestContext context) {
        final String userId = context.pathVar("userId");
        final String lvCode = context.pathVar("lvCode");
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(lvCode)) {
            renderError(context, "参数错误");
            return;
        }
        try {
            final JSONObject status = membershipQueryService.getStatus(userId, lvCode);
            renderSuccess(context, status);
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void getUserMembershipStatus(final RequestContext context) {
        final String userId = context.pathVar("userId");
        if (StringUtils.isBlank(userId)) {
            renderError(context, "参数错误");
            return;
        }
        try {
            final JSONObject status = membershipQueryService.getStatusByUserId(userId);
            renderSuccess(context, status);
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void openMembership(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (null == user) {
            renderError(context, "未登录");
            return;
        }
        final JSONObject req = context.requestJSON();
        final String levelOId = req.optString(Keys.OBJECT_ID);
        final String configJson = req.optString(Membership.CONFIG_JSON);
        final String couponCode = req.optString(Membership.COUPON_CODE);

        if (StringUtils.isBlank(levelOId)) {
            renderError(context, "参数错误");
            return;
        }

        try {
            final JSONObject result = membershipMgmtService.openMembership(
                    user.optString(Keys.OBJECT_ID), levelOId, configJson, couponCode);
            renderSuccess(context, result);
        } catch (ServiceException e) {
            final Throwable cause = e.getCause();
            renderError(context, null == cause ? e.getMessage() : cause.getMessage());
        }
    }

    /**
     * 更新当前用户的会员配置 configJson。
     */
    @Transactional
    public void updateUserConfig(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (null == user) {
            renderError(context, "未登录");
            return;
        }

        final JSONObject req = context.requestJSON();
        String configJson = req.optString(Membership.CONFIG_JSON);
        if (StringUtils.isBlank(configJson)) {
            final JSONObject cfgObj = req.optJSONObject("config");
            if (null != cfgObj) {
                configJson = cfgObj.toString();
            }
        }

        if (StringUtils.isBlank(configJson)) {
            renderError(context, "参数错误：configJson 不能为空");
            return;
        }

        try {
            final JSONObject updated = membershipMgmtService.updateUserConfig(user.optString(Keys.OBJECT_ID), configJson);
            renderSuccess(context, new JSONObject().put("membership", updated));
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    /**
     * Admin：分页查询 VIP。
     */
    public void adminListMemberships(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            renderError(context, "无权限");
            return;
        }

        try {
            final JSONObject req = context.requestJSON();
            final int page = req.optInt("page", 1);
            final int pageSize = req.optInt("pageSize", 20);
            final String userName = StringUtils.trimToEmpty(req.optString(User.USER_NAME));
            final String lvCode = StringUtils.trimToEmpty(req.optString(Membership.LV_CODE));
            final Integer state = parseNullableInteger(req, Membership.STATE);

            final JSONObject data = membershipMgmtService.adminListMemberships(page, pageSize, userName, lvCode, state);
            renderSuccess(context, data);
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    /**
     * Admin：新增免费 VIP。
     */
    public void adminAddMembershipNoCost(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            renderError(context, "无权限");
            return;
        }

        try {
            final JSONObject req = context.requestJSON();
            final String userIdOrName = firstNotBlank(
                    req.optString("userId"),
                    req.optString(User.USER_NAME),
                    req.optString("userIdOrName")
            );
            if (StringUtils.isBlank(userIdOrName)) {
                throw new ServiceException("参数错误：userId 或 userName 不能为空");
            }

            final String levelOId = firstNotBlank(req.optString("levelOId"), req.optString(Keys.OBJECT_ID));
            if (StringUtils.isBlank(levelOId)) {
                throw new ServiceException("参数错误：levelOId 不能为空");
            }

            String configJson = "";
            if (req.has(Membership.CONFIG_JSON) && !req.isNull(Membership.CONFIG_JSON)) {
                configJson = req.optString(Membership.CONFIG_JSON);
            }
            if (StringUtils.isBlank(configJson)) {
                final JSONObject configObj = req.optJSONObject("config");
                if (null != configObj) {
                    configJson = configObj.toString();
                }
            }

            final String userId = resolveUserId(userIdOrName);
            final JSONObject data = membershipMgmtService.adminAddMembershipNoCost(userId, levelOId, StringUtils.defaultString(configJson));
            renderSuccess(context, data);
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    /**
     * Admin：更新 VIP 信息。
     */
    public void adminUpdateMembership(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            renderError(context, "无权限");
            return;
        }

        try {
            final JSONObject req = context.requestJSON();
            final String userIdOrName = firstNotBlank(
                    req.optString("userId"),
                    req.optString(User.USER_NAME),
                    req.optString("userIdOrName")
            );
            if (StringUtils.isBlank(userIdOrName)) {
                throw new ServiceException("参数错误：userId 或 userName 不能为空");
            }

            final String userId = resolveUserId(userIdOrName);

            String lvCode = null;
            if (req.has(Membership.LV_CODE) && !req.isNull(Membership.LV_CODE)) {
                lvCode = req.optString(Membership.LV_CODE);
                if (StringUtils.isBlank(lvCode)) {
                    lvCode = null;
                } else {
                    lvCode = lvCode.trim();
                }
            }

            final Integer state = parseNullableInteger(req, Membership.STATE);
            final Long expiresAt = parseNullableLong(req, Membership.EXPIRES_AT);

            String configJson = null;
            if (req.has(Membership.CONFIG_JSON) && !req.isNull(Membership.CONFIG_JSON)) {
                configJson = req.optString(Membership.CONFIG_JSON);
            } else {
                final JSONObject cfgObj = req.optJSONObject("config");
                if (null != cfgObj) {
                    configJson = cfgObj.toString();
                }
            }

            final JSONObject data = membershipMgmtService.adminUpdateMembership(userId, lvCode, state, expiresAt, configJson);
            renderSuccess(context, data);
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    /**
     * Admin：按天退款（按现有汇率）并使 VIP 失效。
     */
    public void adminRefundMembershipByDays(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            renderError(context, "无权限");
            return;
        }

        try {
            final JSONObject req = context.requestJSON();
            final String userIdOrName = firstNotBlank(
                    req.optString("userId"),
                    req.optString(User.USER_NAME),
                    req.optString("userIdOrName")
            );
            if (StringUtils.isBlank(userIdOrName)) {
                throw new ServiceException("参数错误：userId 或 userName 不能为空");
            }

            final String userId = resolveUserId(userIdOrName);
            final JSONObject data = membershipMgmtService.adminRefundMembershipByDays(userId);
            renderSuccess(context, data);
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    /**
     * Admin：一键延长 VIP 到期时间（按天）。
     */
    public void adminExtendMembershipDays(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            renderError(context, "无权限");
            return;
        }

        try {
            final JSONObject req = context.requestJSON();
            final String userIdOrName = firstNotBlank(
                    req.optString("userId"),
                    req.optString(User.USER_NAME),
                    req.optString("userIdOrName")
            );
            if (StringUtils.isBlank(userIdOrName)) {
                throw new ServiceException("参数错误：userId 或 userName 不能为空");
            }

            final Integer days = parseNullableInteger(req, "days");
            if (null == days || days <= 0) {
                throw new ServiceException("参数错误：days 必须大于 0");
            }

            final String userId = resolveUserId(userIdOrName);
            final JSONObject data = membershipMgmtService.adminExtendMembershipDays(userId, days);
            renderSuccess(context, data);
        } catch (ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void showVipPage(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "vip/index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final JSONObject user = (JSONObject) context.attr(User.USER);
        dataModel.put(User.USER, user);
        try {
            final JSONObject status = membershipQueryService.getStatusByUserId(user.optString(Keys.OBJECT_ID));
            dataModel.put("membership", status);
        } catch (ServiceException e) {
            dataModel.put("membership", new JSONObject());
        }
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    /**
     * 旧版 VIP 配置页（保留兼容）。
     */
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

    /**
     * 后台管理页：放在勋章管理下。
     */
    public void showAdminVipManagePage(final RequestContext context) {
        final JSONObject user = getUser(context);
        if (!isAdmin(user)) {
            context.sendError(404);
            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "admin/vip.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    private void renderSuccess(final RequestContext context, final Object data) {
        final JSONObject response = new JSONObject();
        response.put("code", StatusCodes.SUCC);
        response.put("msg", "success");
        response.put("data", null == data ? new JSONObject() : data);
        context.renderJSON(response);
    }

    private void renderError(final RequestContext context, final String message) {
        final JSONObject response = new JSONObject();
        response.put("code", StatusCodes.ERR);
        response.put("msg", StringUtils.defaultIfBlank(message, "操作失败"));
        response.put("data", new JSONObject());
        context.renderJSON(response);
    }

    private Integer parseNullableInteger(final JSONObject req, final String key) throws ServiceException {
        if (!req.has(key) || req.isNull(key)) {
            return null;
        }

        final Object raw = req.opt(key);
        if (null == raw) {
            return null;
        }

        final String value = StringUtils.trimToEmpty(String.valueOf(raw));
        if (StringUtils.isBlank(value)) {
            return null;
        }

        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ServiceException(key + " 参数格式错误");
        }
    }

    private Long parseNullableLong(final JSONObject req, final String key) throws ServiceException {
        if (!req.has(key) || req.isNull(key)) {
            return null;
        }

        final Object raw = req.opt(key);
        if (null == raw) {
            return null;
        }

        final String value = StringUtils.trimToEmpty(String.valueOf(raw));
        if (StringUtils.isBlank(value)) {
            return null;
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ServiceException(key + " 参数格式错误");
        }
    }

    private String firstNotBlank(final String... candidates) {
        if (null == candidates) {
            return null;
        }

        for (final String candidate : candidates) {
            if (StringUtils.isNotBlank(candidate)) {
                return candidate.trim();
            }
        }

        return null;
    }

    private String resolveUserId(final String userIdOrName) throws ServiceException {
        if (StringUtils.isBlank(userIdOrName)) {
            throw new ServiceException("参数错误：用户标识不能为空");
        }

        try {
            JSONObject user = userRepository.get(userIdOrName);
            if (null == user) {
                user = userRepository.getByName(userIdOrName);
            }

            if (null == user) {
                throw new ServiceException("用户不存在");
            }

            return user.optString(Keys.OBJECT_ID);
        } catch (RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    private JSONObject getUser(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        return currentUser;
    }
}
