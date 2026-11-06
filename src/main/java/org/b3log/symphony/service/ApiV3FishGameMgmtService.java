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
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.FishGame;
import org.b3log.symphony.model.FishGameVote;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.FishGameWriteQuota;
import org.json.JSONObject;

@Service
public class ApiV3FishGameMgmtService {
    @Inject private FishGameMgmtService management;
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    @Inject private ApiV3CommunityQueryService community;
    public JSONObject vote(final String actor, final String id, final String value, final String key) throws Exception {
        if (!value.isEmpty() && !FishGameVote.VALUE_LIKE.equals(value) && !FishGameVote.VALUE_DISLIKE.equals(value)) throw ApiV3Requests.invalid();
        return idempotency.execute(actor, "fish-games.vote:" + id, key, new JSONObject().put("value", value), () -> {
            try {
                final JSONObject result = management.setVoteInCurrentTransaction(actor, id, value, () -> quota(actor, FishGameWriteQuota.Kind.VOTE));
                return new JSONObject().put("upCount", result.optLong(FishGame.LIKE_COUNT)).put("downCount", result.optLong(FishGame.DISLIKE_COUNT))
                        .put("userVote", result.optString("fishGameUserVote")).put("changed", result.optBoolean("changed"));
            } catch (final ServiceException e) { throw translate(e); }
        });
    }
    public JSONObject comment(final String actor, final String id, final String content, final String key) throws Exception {
        return idempotency.execute(actor, "fish-games.comment:" + id, key, new JSONObject().put("content", content), () -> {
            try { return community.gameCommentDto(management.addCommentInCurrentTransaction(actor, id, content, () -> quota(actor, FishGameWriteQuota.Kind.COMMENT))); }
            catch (final ServiceException e) { throw translate(e); }
        });
    }
    private static ApiV3Exception translate(final ServiceException error) {
        if ("鱼游不存在".equals(error.getMessage()) || "鱼游暂未公开".equals(error.getMessage())) return ApiV3Requests.notFound();
        return new ApiV3Exception(422, "invalid_request", "请求内容不符合要求");
    }
    private static void quota(final String actor, final FishGameWriteQuota.Kind kind) {
        if (!FishGameWriteQuota.access(kind, actor)) throw new ApiV3Exception(429, "too_many_requests", "操作过于频繁，请稍后再试");
    }
}
