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
import org.b3log.symphony.model.UserProfession;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.json.JSONObject;

/** 核对职业效果流水与用户职业汇总，不自动修复差异。 */
@Service
public class ProfessionProgressReconciliationService {

    @Inject private ProfessionEventEffectRepository effectRepository;
    @Inject private UserProfessionRepository userProfessionRepository;

    public JSONObject audit(final String userId, final String professionId) throws RepositoryException {
        validateId(userId, "用户");
        validateId(professionId, "职业");
        final JSONObject summary = userProfessionRepository.getByUserProfession(userId, professionId);
        final long summaryExperience = null == summary ? 0L : summary.getLong(UserProfession.TOTAL_EXPERIENCE);
        final long effectExperience = effectRepository.sumAppliedExperience(userId, professionId);
        return new JSONObject()
                .put("userId", userId)
                .put("professionId", professionId)
                .put("summaryExists", null != summary)
                .put("summaryExperience", summaryExperience)
                .put("effectExperience", effectExperience)
                .put("difference", summaryExperience - effectExperience)
                .put("consistent", summaryExperience == effectExperience);
    }

    private void validateId(final String value, final String name) {
        if (null == value || value.isBlank() || value.length() > 19) {
            throw new IllegalArgumentException(name + "标识不合法");
        }
    }
}
