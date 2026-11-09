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
import org.b3log.symphony.model.Milestone;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONObject;

@Service
public class ApiV3MilestoneMgmtService {
    @Inject private MilestoneMgmtService management;
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    public JSONObject create(final String actor, final JSONObject normalized, final String key) throws Exception {
        final var execution = idempotency.executeWithOutcome(actor, "milestones.create", key, normalized, () -> {
            // 现有里程碑读取方使用 HTML，在提交入口将 Markdown 转为 HTML。
            final String html = Markdowns.clean(Markdowns.toHTML(normalized.getString(Milestone.MILESTONE_CONTENT)), "");
            if (html.isBlank() || html.length() > 102400) throw new ApiV3Exception(422, "invalid_request", "正文为空或过长");
            final JSONObject input = new JSONObject(normalized.toString()).put(Milestone.MILESTONE_CONTENT, html);
            final JSONObject row = management.addMilestoneInCurrentTransaction(actor, input);
            return new JSONObject().put("id", row.getString(Keys.OBJECT_ID)).put("status", "pending")
                    .put("createdAt", row.getLong(Milestone.MILESTONE_CREATE_TIME)).put("updatedAt", row.getLong(Milestone.MILESTONE_UPDATE_TIME));
        });
        if (execution.applied()) management.clearTimelineAfterCommit();
        return execution.result();
    }
}
