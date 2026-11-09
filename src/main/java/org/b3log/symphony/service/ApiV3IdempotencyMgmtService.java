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
import org.apache.logging.log4j.ThreadContext;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ApiV3Request;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.util.ApiV3Json;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.json.JSONObject;

/**
 * 业务写入和重放回执同事务提交。回调必须加入当前事务，且不得产生外部副作用。
 */
@Service
public class ApiV3IdempotencyMgmtService {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3IdempotencyMgmtService.class);
    private static final int MAX_ATTEMPTS = 3;
    @Inject
    private ApiV3RequestRepository requestRepository;

    @FunctionalInterface
    public interface Mutation {
        JSONObject apply() throws Exception;
    }

    public JSONObject execute(final String userId, final String operation, final String key,
                              final JSONObject normalizedRequest, final Mutation mutation) throws Exception {
        return runAtomic(requestRepository, userId, operation, key, normalizedRequest, mutation);
    }

    public record Execution(JSONObject result, boolean applied) {
    }

    public Execution executeWithOutcome(final String userId, final String operation, final String key,
                                        final JSONObject normalizedRequest, final Mutation mutation) throws Exception {
        return runAtomicWithOutcome(requestRepository, userId, operation, key, normalizedRequest, mutation);
    }

    static JSONObject runAtomic(final ApiV3MutationStore store, final String userId, final String operation,
                                final String key, final JSONObject normalizedRequest, final Mutation mutation) throws Exception {
        return runAtomicWithOutcome(store, userId, operation, key, normalizedRequest, mutation).result();
    }

    static Execution runAtomicWithOutcome(final ApiV3MutationStore store, final String userId, final String operation,
                                          final String key, final JSONObject normalizedRequest, final Mutation mutation) throws Exception {
        if (null == key || !key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw new ApiV3Exception(422, "invalid_request", "幂等键格式不合法");
        if (store.hasTransactionBegun()) throw new IllegalStateException("v3 transaction already active");
        final String id = ApiV3TokenUtil.hash("request-key\0" + userId + "\0" + operation + "\0" + key);
        final String hash = ApiV3TokenUtil.hash("request-body\0" + ApiV3Json.canonical(normalizedRequest));
        for (int attempt = 1; ; attempt++) {
            final Transaction transaction = store.beginTransaction();
            try {
                final JSONObject existing = store.getForUpdate(id);
                if (null != existing) {
                    if (!hash.equals(existing.optString(ApiV3Request.REQUEST_HASH))) {
                        throw new ApiV3Exception(409, "idempotency_conflict", "相同幂等键不能用于不同请求内容");
                    }
                    final JSONObject result = new JSONObject(existing.getString(ApiV3Request.RESPONSE_JSON));
                    transaction.commit();
                    LOGGER.info("api_v3_write operation={} userId={} result=replayed requestId={}", operation, userId, ThreadContext.get("apiV3RequestId"));
                    return new Execution(result, false);
                }
                final JSONObject row = new JSONObject().put(Keys.OBJECT_ID, id).put(ApiV3Request.USER_ID, userId)
                        .put(ApiV3Request.OPERATION, operation).put(ApiV3Request.REQUEST_HASH, hash)
                        .put(ApiV3Request.RESPONSE_JSON, "{}").put(ApiV3Request.CREATED_AT, System.currentTimeMillis());
                // 通过插入确定性的主键，在应用实例之间串行处理同一幂等请求。
                store.add(row);
                final JSONObject result = mutation.apply();
                final String response = result.toString();
                if (response.length() > 65536) throw new IllegalStateException("v3 mutation response too large");
                row.put(ApiV3Request.RESPONSE_JSON, response);
                store.update(id, row);
                transaction.commit();
                LOGGER.info("api_v3_write operation={} userId={} result=committed requestId={}", operation, userId, ThreadContext.get("apiV3RequestId"));
                return new Execution(result, true);
            } catch (final Exception e) {
                if (transaction.isActive()) transaction.rollback();
                // 并发插入或死锁可能使首次尝试失败，随后用新事务读取已提交回执。
                if (attempt < MAX_ATTEMPTS && storageFailure(e)) continue;
                LOGGER.info("api_v3_write operation={} userId={} result=failed requestId={}", operation, userId, ThreadContext.get("apiV3RequestId"));
                throw e;
            }
        }
    }

    private static boolean storageFailure(final Throwable error) {
        for (Throwable cause = error; null != cause; cause = cause.getCause()) {
            if (cause instanceof RepositoryException) return true;
        }
        return false;
    }
}
