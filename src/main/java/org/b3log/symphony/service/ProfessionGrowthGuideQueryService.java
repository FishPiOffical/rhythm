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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.ProfessionAutomationRepository;
import org.b3log.symphony.repository.ProfessionAutomationRevisionRepository;
import org.b3log.symphony.repository.ProfessionLevelRewardRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** 为职业选择界面提供已发布自动化的升级来源。 */
@Service
public class ProfessionGrowthGuideQueryService {

    private static final String PUBLISHED = "PUBLISHED";

    @Inject private ProfessionAutomationRepository automationRepository;
    @Inject private ProfessionAutomationRevisionRepository revisionRepository;
    @Inject private ProfessionRepository professionRepository;
    @Inject private ProfessionLevelRewardRepository rewardRepository;

    public JSONObject publishedTriggers() throws RepositoryException {
        final String sql = "SELECT a.`professionId`,r.`triggerType` FROM `" + automationRepository.getName() + "` a "
                + "INNER JOIN `" + revisionRepository.getName() + "` r ON r.`oId`=a.`currentRevisionId` "
                + "WHERE a.`status`=? AND r.`status`=? ORDER BY a.`professionId`,a.`sortOrder`,a.`oId`";
        final List<JSONObject> rows = automationRepository.select(sql, new Object[]{PUBLISHED, PUBLISHED});
        final JSONObject result = new JSONObject();
        for (final JSONObject row : rows) {
            result.append(row.getString("professionId"), row.getString("triggerType"));
        }
        return result;
    }

    public JSONObject publishedRewards() throws RepositoryException {
        final String sql = "SELECT p.`oId` AS `professionId`,r.`rewardType`,r.`rewardConfigJson` FROM `"
                + professionRepository.getName() + "` p INNER JOIN `" + rewardRepository.getName() + "` r "
                + "ON r.`schemeId`=p.`currentLevelSchemeId` WHERE p.`status`=? "
                + "ORDER BY p.`sortOrder`,r.`sortOrder`,r.`oId`";
        final List<JSONObject> rows = professionRepository.select(sql, new Object[]{PUBLISHED});
        final JSONObject result = new JSONObject();
        for (final JSONObject row : rows) {
            final JSONObject reward = new JSONObject().put("rewardType", row.getString("rewardType"));
            if ("POINT".equals(row.optString("rewardType"))) {
                reward.put("amount", new JSONObject(row.getString("rewardConfigJson")).optLong("amount"));
            }
            result.append(row.getString("professionId"), reward);
        }
        return result;
    }
}
