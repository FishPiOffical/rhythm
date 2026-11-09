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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.LivenessRewardClaim;
import org.json.JSONObject;

@Service
public class ApiV3ActivityMgmtService {
    @Inject private LivenessRewardMgmtService rewards;
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    public JSONObject status(final String actor) throws Exception {
        return rewards.status(actor);
    }
    public JSONObject claim(final String actor, final String key) throws Exception {
        return claim(actor, key, null);
    }
    public JSONObject claim(final String actor, final String key, final String expectedDate) throws Exception {
        if (expectedDate != null && !LivenessRewardClaim.validExpectedDate(expectedDate)) throw new ApiV3Exception(422, "invalid_request", "奖励日期不合法");
        final JSONObject normalized = new JSONObject();
        if (expectedDate != null) normalized.put("expectedDate", expectedDate);
        return rewards.withTransferLock(actor, () -> {
            final LivenessRewardClaim.Result[] committed = new LivenessRewardClaim.Result[1];
            final var execution = idempotency.executeWithOutcome(actor, "activity.yesterday-reward", key, normalized, () -> {
                try {
                    committed[0] = rewards.claimInCurrentTransaction(actor, expectedDate);
                    return committed[0].dto();
                } catch (final LivenessRewardClaim.DateChanged e) {
                    throw new ApiV3Exception(409, "reward_date_changed", "奖励日期已变化，请重新读取后决定");
                } catch (final LivenessRewardClaim.Unavailable e) {
                    throw new ApiV3Exception(409, "reward_unavailable", "当前没有可领取的昨日活跃度奖励");
                }
            });
            // 仅本次实际领取成功才执行提交后操作，重放回执不重复执行。
            if (execution.applied())
                rewards.afterCommit(actor, committed[0]);
            return execution.result();
        });
    }
}
