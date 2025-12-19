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

        Dispatcher.post("/api/test/medal/migrate", medalTestProcessor::migrateFromCloud);
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
