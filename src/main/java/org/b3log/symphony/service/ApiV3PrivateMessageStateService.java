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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ChatProcessor;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

@Service
public class ApiV3PrivateMessageStateService {
    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(ApiV3PrivateMessageStateService.class);
    @Inject
    private PrivateMessageReadStateService state;
    @Inject
    private ApiV3IdempotencyMgmtService idempotency;
    @Inject
    private ApiV3PrivateMessageQueryService query;

    public JSONObject mark(JSONObject viewer, String peer, String key) throws Exception {
        final String actor = viewer == null ? "" : viewer.optString(Keys.OBJECT_ID);
        ApiV3PrivateMessages.requireId(actor);
        if (peer != null) {
            ApiV3PrivateMessages.requireId(peer);
            if (actor.equals(peer))
                throw ApiV3WriteInputs.invalid();
        }
        try {
            final var result = idempotency.execute(actor, peer == null ? "private.read-all" : "private.read:" + peer, key, new JSONObject(),
                    () -> new JSONObject().put("changed", state.markInCurrentTransaction(actor, peer)));
            // 回放也可以清缓存并重新查询当前未读总数。
            try {
                afterMarked(actor);
            } catch (Exception e) {
                LOGGER.warn("Private read state refresh unavailable");
            }
            return result;
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Private read state unavailable");
            throw new ApiV3Exception(503, "temporarily_unavailable", "私信暂不可用");
        }
    }

    protected void afterMarked(String actor) throws Exception {
        ChatProcessor.invalidateChatListCache(actor, actor);
        final long count = query.unread(new JSONObject().put(Keys.OBJECT_ID, actor)).getLong("count");
        UserChannel.sendCmd(new JSONObject().put(UserExt.USER_T_ID, actor).put(Common.COMMAND, "chatUnreadCountRefresh").put("count", count));
    }
}
