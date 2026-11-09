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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.processor.ChatProcessor;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

@Service
public class ApiV3PrivateMessageWithdrawalService {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3PrivateMessageWithdrawalService.class);
    @Inject
    private PrivateMessageWithdrawalService withdrawal;
    @Inject
    private ApiV3IdempotencyMgmtService idempotency;

    protected void afterRemoved(String actor, String peer) {
        ChatProcessor.invalidateChatListCache(actor, peer);
    }

    public JSONObject remove(JSONObject viewer, String id, String key) throws Exception {
        final String actor = viewer == null ? "" : viewer.optString(Keys.OBJECT_ID);
        ApiV3PrivateMessages.requireId(actor);
        ApiV3PrivateMessages.requireId(id);
        try {
            final var result = idempotency.execute(actor, "private.withdraw:" + id, key, new JSONObject(),
                    () -> withdrawal.removeInCurrentTransaction(actor, id));
            // 成功提交但响应丢失时，回放也可以再次清理会话列表缓存。
            try {
                afterRemoved(actor, result.getString("peerId"));
            } catch (Exception e) {
                LOGGER.warn("Private withdrawal refresh unavailable");
            }
            return result;
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Private withdrawal unavailable");
            throw new ApiV3Exception(503, "temporarily_unavailable", "私信暂不可用");
        }
    }
}
