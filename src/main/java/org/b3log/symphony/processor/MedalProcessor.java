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
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Crypts;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.repository.MedalRepository;
import org.b3log.symphony.repository.UserMedalRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.MedalService;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

@Singleton
public class MedalProcessor {

    @Inject
    private MedalService medalService;

    @Inject
    private MedalRepository medalRepository;

    @Inject
    private UserMedalRepository userMedalRepository;

    @Inject
    private UserRepository userRepository;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * 注册路由.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final MedalProcessor medalProcessor = beanManager.getReference(MedalProcessor.class);

        // 管理侧
        Dispatcher.post("/api/medal/admin/list", medalProcessor::adminListMedals);
        Dispatcher.post("/api/medal/admin/detail", medalProcessor::adminGetMedalDetail);
        Dispatcher.post("/api/medal/admin/delete", medalProcessor::adminDeleteMedal);
        Dispatcher.post("/api/medal/admin/edit", medalProcessor::adminEditMedal);
        Dispatcher.post("/api/medal/admin/create", medalProcessor::adminCreateMedal);
        Dispatcher.post("/api/medal/admin/grant", medalProcessor::adminGrantMedalToUser);
        Dispatcher.post("/api/medal/admin/revoke", medalProcessor::adminRevokeMedalFromUser);
        Dispatcher.post("/api/medal/admin/owners", medalProcessor::adminGetMedalOwners);

        // 用户侧
        Dispatcher.post("/api/medal/my/list", medalProcessor::myListMedals);
        Dispatcher.post("/api/medal/my/order", medalProcessor::myUpdateMedalOrder);
        Dispatcher.post("/api/medal/my/display", medalProcessor::mySetMedalDisplay);

        // HTML
        Dispatcher.get("/admin/medal", medalProcessor::showAdminMedal);
    }

    public void showAdminMedal(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "admin/medal.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final JSONObject currentUser = Sessions.getUser();
        if (null == currentUser) {
            context.sendError(403);
            return;
        }
        // 放 ApiKey
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        final String userPassword = currentUser.optString(User.USER_PASSWORD);
        final JSONObject cookieJSONObject = new JSONObject();
        cookieJSONObject.put(Keys.OBJECT_ID, userId);
        final String random = RandomStringUtils.randomAlphanumeric(16);
        cookieJSONObject.put(Keys.TOKEN, userPassword + ApiProcessor.COOKIE_ITEM_SEPARATOR + random);
        final String key = Crypts.encryptByAES(cookieJSONObject.toString(), Symphonys.COOKIE_SECRET_API);
        dataModel.put("apiKey", key);

        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    /**
     * 获取当前用户（支持 apiKey），未登录返回 null.
     */
    private JSONObject getCurrentUser(final RequestContext context) {
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

    /**
     * 管理员鉴权，非管理员返回 null 并写 403.
     */
    private JSONObject requireAdmin(final RequestContext context) {
        final JSONObject currentUser = getCurrentUser(context);
        if (null == currentUser) {
            context.sendError(401);
            context.abort();
            return null;
        }
        if (!currentUser.optString(User.USER_ROLE).equals(Role.ROLE_ID_C_ADMIN)) {
            context.sendError(403);
            context.abort();
            return null;
        }
        return currentUser;
    }

    /**
     * 登录鉴权，未登录返回 null 并写 401.
     */
    private JSONObject requireLogin(final RequestContext context) {
        final JSONObject currentUser = getCurrentUser(context);
        if (null == currentUser) {
            context.sendError(401);
            context.abort();
            return null;
        }
        return currentUser;
    }

    /* ========== 管理侧接口 ========== */

    /**
     * 管理侧：分页读取全部勋章列表.
     *
     * 请求: POST /api/medal/admin/list
     * 请求体: {"page":1,"pageSize":20}
     */
    public void adminListMedals(final RequestContext context) {
        final JSONObject currentUser = requireAdmin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final JSONObject req = context.requestJSON();
            final int page = req.optInt("page", 1);
            final int pageSize = req.optInt("pageSize", 20);
            final List<JSONObject> list = medalService.getAllMedalsPaged(page, pageSize);
            final JSONObject ret = new JSONObject();
            ret.put(Keys.CODE, StatusCodes.SUCC);
            ret.put(Keys.MSG, "");
            ret.put(Keys.DATA, new JSONArray(list));
            context.renderJSON(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /**
     * 管理侧：读取指定勋章详细信息.
     *
     * 请求: POST /api/medal/admin/detail
     * 请求体: {"medalId":"0"}
     */
    public void adminGetMedalDetail(final RequestContext context) {
        final JSONObject currentUser = requireAdmin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final JSONObject req = context.requestJSON();
            final String medalId = req.optString("medalId");
            final JSONObject medal = medalService.getMedalById(medalId);
            if (medal == null) {
                context.renderJSON(StatusCodes.ERR);
                return;
            }
            final JSONObject ret = new JSONObject();
            ret.put(Keys.CODE, StatusCodes.SUCC);
            ret.put(Keys.MSG, "");
            ret.put(Keys.DATA, medal);
            context.renderJSON(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /**
     * 管理侧：删除指定勋章.
     *
     * 请求: POST /api/medal/admin/delete
     * 请求体: {"medalId":"0"}
     */
    public void adminDeleteMedal(final RequestContext context) {
        final JSONObject currentUser = requireAdmin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final JSONObject req = context.requestJSON();
            final String medalId = req.optString("medalId");
            medalService.deleteMedal(medalId);
            context.renderJSON(StatusCodes.SUCC);
        } catch (ServiceException e) {
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /**
     * 管理侧：编辑指定勋章（不允许修改 medalId）.
     *
     * 请求: POST /api/medal/admin/edit
     * 请求体: {"medalId":"0","name":"xxx","type":"xxx","description":"...","attr":"..."}
     */
    public void adminEditMedal(final RequestContext context) {
        final JSONObject currentUser = requireAdmin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final JSONObject req = context.requestJSON();
            final String medalId = req.optString("medalId");
            final String name = req.optString("name");
            final String type = req.optString("type", "default");
            final String desc = req.optString("description", "");
            final String attr = req.optString("attr", "");
            medalService.updateMedal(medalId, name, type, desc, attr);
            context.renderJSON(StatusCodes.SUCC);
        } catch (ServiceException e) {
            e.printStackTrace();
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /**
     * 管理侧：新建勋章.
     *
     * 请求: POST /api/medal/admin/create
     * 请求体: {"name":"xxx","type":"xxx","description":"...","attr":"..."}
     */
    public void adminCreateMedal(final RequestContext context) {
        final JSONObject currentUser = requireAdmin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final JSONObject req = context.requestJSON();
            final String name = req.optString("name");
            final String type = req.optString("type", "default");
            final String desc = req.optString("description", "");
            final String attr = req.optString("attr", "");
            final String oId = medalService.addMedal(name, type, desc, attr);
            final JSONObject ret = new JSONObject();
            ret.put(Keys.CODE, StatusCodes.SUCC);
            ret.put(Keys.MSG, "created");
            ret.put(Keys.DATA, new JSONObject().put("oId", oId));
            context.renderJSON(ret);
        } catch (ServiceException e) {
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /**
     * 管理侧：给指定用户发指定勋章.
     *
     * 请求: POST /api/medal/admin/grant
     * 请求体: {"userId":"123","medalId":"0","expireTime":0,"data":""}
     */
    public void adminGrantMedalToUser(final RequestContext context) {
        final JSONObject currentUser = requireAdmin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final JSONObject req = context.requestJSON();
            final String userId = req.optString("userId");
            final String medalId = req.optString("medalId");
            final long expireTime = req.optLong("expireTime", 0L);
            final String data = req.optString("data", "");
            medalService.grantMedalToUser(userId, medalId, expireTime, data);
            context.renderJSON(StatusCodes.SUCC);
        } catch (ServiceException e) {
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /**
     * 管理侧：给指定用户移除指定勋章.
     *
     * 请求: POST /api/medal/admin/revoke
     * 请求体: {"userId":"123","medalId":"0"}
     */
    public void adminRevokeMedalFromUser(final RequestContext context) {
        final JSONObject currentUser = requireAdmin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final JSONObject req = context.requestJSON();
            final String userId = req.optString("userId");
            final String medalId = req.optString("medalId");
            medalService.revokeMedalFromUser(userId, medalId);
            context.renderJSON(StatusCodes.SUCC);
        } catch (ServiceException e) {
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /**
     * 管理侧：读取指定勋章拥有的用户和拥有总数（分页）.
     *
     * 请求: POST /api/medal/admin/owners
     * 请求体: {"medalId":"0","page":1,"pageSize":20}
     */
    public void adminGetMedalOwners(final RequestContext context) {
        final JSONObject currentUser = requireAdmin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final JSONObject req = context.requestJSON();
            final String medalId = req.optString("medalId");
            final int page = req.optInt("page", 1);
            final int pageSize = req.optInt("pageSize", 20);

            long now = System.currentTimeMillis();
            // 统计总数（同时清理已过期记录）
            final Query countQuery = new Query()
                    .setFilter(new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId));
            final List<JSONObject> all = userMedalRepository.getList(countQuery);
            int total = 0;
            for (final JSONObject um : all) {
                long expireTime = um.optLong("expire_time", 0L);
                if (expireTime > 0L && expireTime <= now) {
                    // 过期则删除
                    String oId = um.optString("oId");
                    if (oId != null && !oId.isEmpty()) {
                        try {
                            Transaction tx = userMedalRepository.beginTransaction();
                            userMedalRepository.remove(oId);
                            tx.commit();
                        } catch (RepositoryException ignored) {
                        }
                    }
                    continue;
                }
                total++;
            }

            // 分页查询（再次过滤过期记录，避免并发）
            final Query pageQuery = new Query()
                    .setFilter(new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId))
                    .setCurrentPageNum(page)
                    .setPageSize(pageSize)
                    .setPageCount(1);
            final List<JSONObject> ownersRaw = userMedalRepository.getList(pageQuery);
            final List<JSONObject> owners = new java.util.ArrayList<>();
            for (final JSONObject um : ownersRaw) {
                long expireTime = um.optLong("expire_time", 0L);
                if (expireTime > 0L && expireTime <= now) {
                    String oId = um.optString("oId");
                    if (oId != null && !oId.isEmpty()) {
                        try {
                            Transaction tx = userMedalRepository.beginTransaction();
                            userMedalRepository.remove(oId);
                            tx.commit();
                        } catch (RepositoryException ignored) {
                        }
                    }
                    continue;
                }
                owners.add(um);
            }

            // 附带用户基本信息
            final JSONArray dataArray = new JSONArray();
            for (final JSONObject um : owners) {
                final JSONObject item = new JSONObject(um.toString());
                final String userId = um.optString("user_id");
                try {
                    final JSONObject user = userRepository.get(userId);
                    if (user != null) {
                        item.put("userName", user.optString(User.USER_NAME));
                    }
                } catch (RepositoryException ignored) {
                }
                dataArray.put(item);
            }

            final JSONObject data = new JSONObject();
            data.put("total", total);
            data.put("items", dataArray);

            final JSONObject ret = new JSONObject();
            ret.put(Keys.CODE, StatusCodes.SUCC);
            ret.put(Keys.MSG, "");
            ret.put(Keys.DATA, data);
            context.renderJSON(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /* ========== 用户侧接口 ========== */

    /**
     * 用户侧：读取当前登录用户的所有勋章列表.
     *
     * 请求: POST /api/medal/my/list
     * 请求体: {}
     */
    public void myListMedals(final RequestContext context) {
        final JSONObject currentUser = requireLogin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            final List<JSONObject> list = medalService.getUserMedals(userId);
            final JSONObject ret = new JSONObject();
            ret.put(Keys.CODE, StatusCodes.SUCC);
            ret.put(Keys.MSG, "");
            ret.put(Keys.DATA, new JSONArray(list));
            context.renderJSON(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /**
     * 用户侧：调整当前登录用户的单个勋章顺序.
     *
     * 请求: POST /api/medal/my/order
     * 请求体: {"medalId":"0","order":5}
     */
    public void myUpdateMedalOrder(final RequestContext context) {
        final JSONObject currentUser = requireLogin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final JSONObject req = context.requestJSON();
            final String medalId = req.optString("medalId");
            final int order = req.optInt("order", 0);
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            medalService.updateUserMedalOrderSingle(userId, medalId, order);
            context.renderJSON(StatusCodes.SUCC);
        } catch (ServiceException e) {
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /**
     * 用户侧：调整当前登录用户隐藏/显示指定勋章.
     *
     * 请求: POST /api/medal/my/display
     * 请求体: {"medalId":"0","display":true}
     */
    public void mySetMedalDisplay(final RequestContext context) {
        final JSONObject currentUser = requireLogin(context);
        if (currentUser == null) {
            return;
        }
        try {
            final JSONObject req = context.requestJSON();
            final String medalId = req.optString("medalId");
            final boolean display = req.optBoolean("display", true);
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            medalService.setUserMedalDisplay(userId, medalId, display);
            context.renderJSON(StatusCodes.SUCC);
        } catch (ServiceException e) {
            context.renderJSON(StatusCodes.ERR);
        }
    }
}
