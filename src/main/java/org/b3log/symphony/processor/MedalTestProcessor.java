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
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.repository.MedalRepository;
import org.b3log.symphony.repository.UserMedalRepository;
import org.b3log.symphony.service.CloudService;
import org.b3log.symphony.service.MedalService;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 勋章测试接口，仅用于本地调试新勋章系统.
 *
 * 所有接口均为测试专用:
 * - 清空/初始化勋章数据
 * - 发勋章、开关展示、调整顺序
 * - 查询用户勋章
 * - 触发从 Cloud 迁移到新表
 */
@Singleton
public class MedalTestProcessor {

    @Inject
    private MedalService medalService;

    @Inject
    private MedalRepository medalRepository;

    @Inject
    private UserMedalRepository userMedalRepository;

    @Inject
    private CloudService cloudService;

    /**
     * 注册测试路由.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final MedalTestProcessor medalTestProcessor = beanManager.getReference(MedalTestProcessor.class);

        Dispatcher.post("/api/test/medal/clear", medalTestProcessor::clearAll);
        Dispatcher.post("/api/test/medal/init", medalTestProcessor::initMedals);
        Dispatcher.post("/api/test/medal/grant", medalTestProcessor::grantMedal);
        Dispatcher.post("/api/test/medal/display", medalTestProcessor::setDisplay);
        Dispatcher.post("/api/test/medal/order", medalTestProcessor::updateOrder);
        Dispatcher.post("/api/test/medal/order/single", medalTestProcessor::updateSingleOrder);
        Dispatcher.post("/api/test/medal/query/all", medalTestProcessor::queryAllForUser);
        Dispatcher.post("/api/test/medal/query/valid", medalTestProcessor::queryValidForUser);
        Dispatcher.post("/api/test/medal/query/displayedValid", medalTestProcessor::queryDisplayedValidForUser);
        Dispatcher.post("/api/test/medal/query/id-order", medalTestProcessor::queryIdAndOrderForUser);
        Dispatcher.post("/api/test/medal/migrate", medalTestProcessor::migrateFromCloud);
    }

    /**
     * 清空 medal / user_medal 两张表.
     *
     * 请求: POST /api/test/medal/clear
     * 请求体: {}
     */
    public void clearAll(final RequestContext context) {
        final Transaction tx = medalRepository.beginTransaction();
        try {
            medalRepository.remove(new Query());
            userMedalRepository.remove(new Query());
            tx.commit();
            context.renderJSON(StatusCodes.SUCC).renderMsg("cleared medal and user_medal");
        } catch (RepositoryException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to clear: " + e.getMessage());
        }
    }

    /**
     * 初始化几条勋章定义数据.
     *
     * 请求: POST /api/test/medal/init
     * 请求体示例:
     * {
     *   "items": [
     *     {"id":"0","name":"测试勋章A","type":"test","description":"descA","attr":"a=1"},
     *     {"id":"1","name":"测试勋章B","type":"test","description":"descB","attr":"b=2"}
     *   ]
     * }
     * 如不传 items，则默认初始化两条.
     */
    public void initMedals(final RequestContext context) {
        final Transaction tx = medalRepository.beginTransaction();
        try {
            final JSONObject req = context.requestJSON();
            JSONArray items = req.optJSONArray("items");
            if (items == null || items.length() == 0) {
                items = new JSONArray();
                items.put(new JSONObject()
                        .put("name", "测试勋章A")
                        .put("type", "test")
                        .put("description", "测试勋章A描述")
                        .put("attr", "color=red"));
                items.put(new JSONObject()
                        .put("name", "测试勋章B")
                        .put("type", "test")
                        .put("description", "测试勋章B描述")
                        .put("attr", "color=blue"));
            }
            for (int i = 0; i < items.length(); i++) {
                final JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                final String name = item.optString("name");
                final String type = item.optString("type", "test");
                final String desc = item.optString("description", "");
                final String attr = item.optString("attr", "");
                medalService.addMedal(name, type, desc, attr);
            }
            tx.commit();
            context.renderJSON(StatusCodes.SUCC).renderMsg("init medals done");
        } catch (final Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to init medals: " + e.getMessage());
        }
    }

    /**
     * 给用户发勋章.
     *
     * 请求: POST /api/test/medal/grant
     * 请求体示例:
     * {
     *   "userId": "123",
     *   "medalId": "0",
     *   "expireTime": 0
     * }
     */
    public void grantMedal(final RequestContext context) {
        try {
            final JSONObject req = context.requestJSON();
            final String userId = req.optString("userId");
            final String medalId = req.optString("medalId");
            final long expireTime = req.optLong("expireTime", 0L);
            medalService.grantMedalToUser(userId, medalId, expireTime, "");
            context.renderJSON(StatusCodes.SUCC).renderMsg("granted");
        } catch (final Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to grant: " + e.getMessage());
        }
    }

    /**
     * 设置用户某个勋章是否展示.
     *
     * 请求: POST /api/test/medal/display
     * 请求体示例:
     * {
     *   "userId": "123",
     *   "medalId": "0",
     *   "display": true
     * }
     */
    public void setDisplay(final RequestContext context) {
        try {
            final JSONObject req = context.requestJSON();
            final String userId = req.optString("userId");
            final String medalId = req.optString("medalId");
            final boolean display = req.optBoolean("display", true);
            medalService.setUserMedalDisplay(userId, medalId, display);
            context.renderJSON(StatusCodes.SUCC).renderMsg("display updated");
        } catch (final Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to set display: " + e.getMessage());
        }
    }

    /**
     * 批量更新用户勋章顺序.
     *
     * 请求: POST /api/test/medal/order
     * 请求体示例:
     * {
     *   "userId": "123",
     *   "medalOrders": [
     *     {"medalId":"0","order":1},
     *     {"medalId":"1","order":2}
     *   ]
     * }
     */
    public void updateOrder(final RequestContext context) {
        try {
            final JSONObject req = context.requestJSON();
            final String userId = req.optString("userId");
            final JSONArray medalOrders = req.optJSONArray("medalOrders");
            final JSONObject sortConfig = new JSONObject();
            sortConfig.put("medalOrders", medalOrders);
            medalService.updateUserMedalOrder(userId, sortConfig);
            context.renderJSON(StatusCodes.SUCC).renderMsg("order updated");
        } catch (final Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to update order: " + e.getMessage());
        }
    }

    /**
     * 单个更新用户勋章顺序.
     *
     * 请求: POST /api/test/medal/order/single
     * 请求体示例:
     * {
     *   "userId": "123",
     *   "medalId": "0",
     *   "order": 10
     * }
     */
    public void updateSingleOrder(final RequestContext context) {
        try {
            final JSONObject req = context.requestJSON();
            final String userId = req.optString("userId");
            final String medalId = req.optString("medalId");
            final int order = req.optInt("order", 0);

            medalService.updateUserMedalOrderSingle(userId, medalId, order);
            context.renderJSON(StatusCodes.SUCC).renderMsg("single order updated");
        } catch (final Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to update single order: " + e.getMessage());
        }
    }

    /**
     * 查询用户所有勋章（含过期、未展示的）.
     *
     * 请求: POST /api/test/medal/query/all
     * 请求体: {"userId":"123"}
     */
    public void queryAllForUser(final RequestContext context) {
        try {
            final JSONObject req = context.requestJSON();
            final String userId = req.optString("userId");
            final List<JSONObject> list = medalService.getUserMedals(userId);
            context.renderJSON(StatusCodes.SUCC).renderData(new JSONArray(list).toString());
        } catch (final Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to query all: " + e.getMessage());
        }
    }

    /**
     * 查询用户未过期的全部勋章（不判断 display）.
     *
     * 请求: POST /api/test/medal/query/valid
     * 请求体: {"userId":"123"}
     */
    public void queryValidForUser(final RequestContext context) {
        try {
            final JSONObject req = context.requestJSON();
            final String userId = req.optString("userId");
            final List<JSONObject> list = medalService.getUserValidMedals(userId);
            context.renderJSON(StatusCodes.SUCC).renderData(new JSONArray(list).toString());
        } catch (final Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to query valid: " + e.getMessage());
        }
    }

    /**
     * 查询用户有效且已开启展示的勋章（按 display_order 排序）.
     *
     * 请求: POST /api/test/medal/query/displayedValid
     * 请求体: {"userId":"123"}
     */
    public void queryDisplayedValidForUser(final RequestContext context) {
        try {
            final JSONObject req = context.requestJSON();
            final String userId = req.optString("userId");
            final List<JSONObject> list = medalService.getUserDisplayedValidMedals(userId);
            context.renderJSON(StatusCodes.SUCC).renderData(new JSONArray(list).toString());
        } catch (final Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to query displayed valid: " + e.getMessage());
        }
    }

    /**
     * 查询用户勋章的 ID 与顺序.
     *
     * 请求: POST /api/test/medal/query/id-order
     * 请求体: {"userId":"123"}
     */
    public void queryIdAndOrderForUser(final RequestContext context) {
        try {
            final JSONObject req = context.requestJSON();
            final String userId = req.optString("userId");
            final List<JSONObject> list = medalService.getUserMedalIdAndOrder(userId);
            context.renderJSON(StatusCodes.SUCC).renderData(new JSONArray(list).toString());
        } catch (final Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to query id and order: " + e.getMessage());
        }
    }

    /**
     * 触发从 Cloud 迁移勋章到新表.
     *
     * 请求: POST /api/test/medal/migrate
     * 请求体: {}
     *
     * 注意：运行前请确保已清空 medal/user_medal 或者你明确知道迁移规则.
     */
    public void migrateFromCloud(final RequestContext context) {
        try {
            medalService.migrateCloudMedals();
            context.renderJSON(StatusCodes.SUCC).renderMsg("migrate from cloud done, check console logs for progress");
        } catch (final Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("failed to migrate from cloud: " + e.getMessage());
        }
    }
}
