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

import org.b3log.latke.service.annotation.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.QiniuTextCensor;
import org.json.JSONObject;

/** 复读投稿使用七牛审核，并对待审核或异常结果默认拒绝。 */
@Service
public class RepeaterContentModerationService {
    private static final Logger LOGGER = LogManager.getLogger(RepeaterContentModerationService.class);

    protected JSONObject censor(final String content) {
        return QiniuTextCensor.censor(content);
    }

    public void requireApproved(final String content) {
        final JSONObject result;
        try {
            result = censor(content);
        } catch (final Exception e) {
            LOGGER.warn("Repeater moderation request failed", e);
            throw unavailable();
        }
        if (result == null || result.optString("type").isBlank() || "未知".equals(result.optString("type"))
                || !java.util.Set.of("pass", "review", "block").contains(result.optString("action"))
                || !java.util.Set.of("pass", "block").contains(result.optString("do"))) {
            LOGGER.warn("Repeater moderation returned an unavailable result");
            throw unavailable();
        }
        if (!"pass".equals(result.optString("action")) || !"pass".equals(result.optString("do"))) {
            throw new ApiV3Exception(422, "content_rejected", "内容审核未通过，请修改后重试");
        }
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "内容审核暂不可用，请稍后重试");
    }
}
