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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.ApiV3ReactionDto;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;

@Service
public class ApiV3ReactionService {
    @Inject
    private ApiV3ReactionTargetService targets;
    @Inject
    private ReactionQueryService query;
    @Inject
    private ReactionMgmtService management;
    @Inject
    private ApiV3IdempotencyMgmtService idempotency;
    @Inject
    private ApiV3ReactionEventService events;

    public JSONObject read(final JSONObject viewer, final String type, final String id) throws Exception {
        targets.read(viewer, type, id);
        return ApiV3ReactionDto.from(query.buildOperationResultOrThrow(type, id, null == viewer ? "" : viewer.optString(Keys.OBJECT_ID)));
    }

    public JSONObject set(final JSONObject viewer, final String type, final String id, final String value, final String key) throws Exception {
        final String actor = viewer.optString(Keys.OBJECT_ID);
        // 写入时校验锁定的目标，并在同一事务中构造回执。
        final ApiV3IdempotencyMgmtService.Execution execution = idempotency.executeWithOutcome(actor, "reactions.set:" + type + ":" + id,
                key, new JSONObject().put("value", value), () -> {
                    final boolean changed = management.setInCurrentTransaction(actor, type, id, value,
                            target -> targets.validate(viewer, type, target, true));
                    return ApiV3ReactionDto.from(query.buildOperationResultOrThrow(type, id, actor)).put("changed", changed);
                });
        // 只有本次提交实际改变表态时才发实时事件。
        if (execution.applied() && execution.result().optBoolean("changed")) events.publish(viewer, type, id);
        final JSONObject receipt = execution.result();
        if (Reaction.TARGET_TYPE_COMMENT.equals(type) || Reaction.TARGET_TYPE_ARTICLE.equals(type)) {
            // 重放的原表态可能已被其他会话修改，页面使用当前可见状态。
            try {
                final JSONObject state = execution.applied() ? new JSONObject(receipt.toString()) : read(viewer, type, id);
                receipt.put("interaction", state);
            } catch (final ApiV3Exception e) {
                if (e.status != 403 && e.status != 404) {
                    throw e;
                }
                receipt.put("interaction", JSONObject.NULL);
            }
        }
        return receipt;
    }
}
