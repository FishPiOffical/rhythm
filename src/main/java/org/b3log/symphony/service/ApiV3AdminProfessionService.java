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
package org.b3log.symphony.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;

/**
 * 管理后台 v3 职业配置工作台代理服务（复用既有 {@link ProfessionAdminQueryService} 等领域服务，仅管理员可调用）。
 */
@Service
public class ApiV3AdminProfessionService {

    private static final Logger LOGGER = LogManager.getLogger(ApiV3AdminProfessionService.class);

    @Inject
    private ApiV3AdminService adminService;

    /**
     * 校验当前请求者具备有效第一方管理员身份后，返回操作员用户 ID。
     *
     * @param identity 当前认证身份
     * @param scope    所需 OAuth 权限范围（{@code profile.read} 或 {@code settings.write}）
     * @return 管理员用户 ID
     */
    public String requireAdminOperatorId(final JSONObject identity, final String scope) {
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor =
                    adminService.authorizeInCurrentTransaction(identity, scope, null, true);
            tx.commit();
            return actor.userId();
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("校验职业管理权限失败", e);
            throw adminService.unavailable();
        } finally {
            adminService.rollbackIfActive(tx);
        }
    }

    /**
     * 执行职业领域服务动作并将业务校验异常统一映射为 V3 错误码。
     *
     * @param action 业务回调
     * @param <T>    返回类型
     * @return 业务执行结果
     */
    public <T> T executeDomainAction(final DomainAction<T> action) {
        try {
            return action.run();
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final IllegalArgumentException e) {
            throw new ApiV3Exception(422, "invalid_request", e.getMessage());
        } catch (final IllegalStateException e) {
            throw new ApiV3Exception(409, "profession_state_conflict", e.getMessage());
        } catch (final Exception e) {
            LOGGER.error("执行职业配置管理操作失败", e);
            throw new ApiV3Exception(500, "profession_operation_failed",
                    e.getMessage() == null ? "职业配置操作失败" : e.getMessage());
        }
    }

    @FunctionalInterface
    public interface DomainAction<T> {
        T run() throws Exception;
    }
}
