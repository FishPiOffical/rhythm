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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

/**
 * 复读内容、职业来源事件和重放回执在同一个严格根事务中提交。
 */
@Service
public class ApiV3RepeaterPublicationService {
    @Inject
    private UserRepository users;
    @Inject
    private RepeaterContentRepository contents;
    @Inject
    private ApiV3RequestRepository requests;
    @Inject
    private ProfessionSourceEventCaptureService capture;
    @Inject
    private RepeaterContentModerationService moderationService;

    public JSONObject publish(final String actor, final JSONObject body, final String key) throws Exception {
        RepeaterLikeMutationService.id(actor);
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw ApiV3WriteInputs.invalid();
        if (requests.hasTransactionBegun()) throw new IllegalStateException("v3 transaction already active");
        final JSONObject normalized = ApiV3RepeaterPublicationInputs.normalize(body);
        final JSONObject receiptBody = new JSONObject(normalized.toString()).put("textVersion", 2);
        // 审核不持有事务；已成功的回执仍可在审核服务故障时回放。
        ApiV3Exception failure = null;
        try {
            moderationService.requireApproved(normalized.getString("content"));
        } catch (final ApiV3Exception e) {
            failure = e;
        }
        final ApiV3Exception moderationFailure = failure;
        final String legacyHash = ApiV3TokenUtil.hash("request-body\0"
                + ApiV3Json.canonical(ApiV3RepeaterPublicationInputs.legacyReceipt(body)));
        final String currentHash = ApiV3TokenUtil.hash("request-body\0" + ApiV3Json.canonical(receiptBody));
        try {
            final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                public Transaction beginTransaction() {
                    return ApiV3Transactions.strict(requests.beginTransaction());
                }

                public boolean hasTransactionBegun() {
                    return requests.hasTransactionBegun();
                }

                public JSONObject getForUpdate(final String id) throws RepositoryException {
                    RepeaterLikeMutationService.owner(users, actor);
                    final JSONObject receipt = requests.getForUpdate(id);
                    if (receipt != null && legacyHash.equals(receipt.optString("requestHash"))) {
                        // 只适配旧哈希的读取，不修改原始回执。
                        return new JSONObject(receipt.toString()).put("requestHash", currentHash);
                    }
                    return receipt;
                }

                public String add(final JSONObject row) throws RepositoryException {
                    return requests.add(row);
                }

                public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
                    requests.update(id, row, fields);
                }
            };
            return result(ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "repeater.publish", key, receiptBody,
                    () -> {
                        if (moderationFailure != null) throw moderationFailure;
                        return publishInCurrentTransaction(actor, normalized);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            throw RepeaterLikeMutationService.unavailable();
        }
    }

    private JSONObject publishInCurrentTransaction(final String actor, final JSONObject normalized) throws Exception {
        RepeaterLikeMutationService.id(actor);
        if (!requests.hasTransactionBegun())
            throw new IllegalStateException("repeater publication transaction required");
        final String id = Ids.genTimeMillisId();
        final long now = System.currentTimeMillis();
        final JSONObject row = new JSONObject().put("oId", id)
                .put(RepeaterContent.TYPE, normalized.getString("type")).put(RepeaterContent.CONTENT, normalized.getString("content"))
                .put(RepeaterContent.AUTHOR_ID, actor).put(RepeaterContent.SOURCE, RepeaterContent.SOURCE_USER)
                .put(RepeaterContent.STATUS, RepeaterContent.STATUS_C_VALID).put(RepeaterContent.LIKE_COUNT, 0)
                .put(RepeaterContent.CREATED_TIME, now).put(RepeaterContent.UPDATED_TIME, now);
        contents.add(row);
        capture.repeaterPublished(row);
        return new JSONObject().put("id", id).put("type", normalized.getString("type")).put("createdTime", now);
    }

    private static JSONObject result(final JSONObject stored) {
        final String id = stored.getString("id"), type = stored.getString("type");
        if (!id.matches("[1-9][0-9]{0,18}") || !RepeaterContent.isSupportedType(type) || stored.getLong("createdTime") < 0)
            throw RepeaterLikeMutationService.unavailable();
        return new JSONObject().put("id", id).put("type", type).put("createdTime", stored.getLong("createdTime"));
    }
}
