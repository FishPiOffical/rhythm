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
import org.b3log.symphony.model.ProfessionLevelScheme;
import org.b3log.symphony.model.ProfessionRevision;
import org.b3log.symphony.repository.ProfessionLevelSchemeRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.json.JSONObject;

/** 统一维护职业版本与等级方案的停用状态。 */
@Service
public class ProfessionDefinitionStateService {

    @Inject private ProfessionRevisionRepository revisionRepository;
    @Inject private ProfessionLevelSchemeRepository schemeRepository;

    public void retireRevision(final String revisionId, final long now) throws RepositoryException {
        if (revisionId.isBlank()) {
            return;
        }
        final JSONObject revision = revisionRepository.get(revisionId);
        if (null == revision) {
            throw new RepositoryException("职业版本不存在");
        }
        revision.put(ProfessionRevision.STATUS, "RETIRED");
        revision.put(ProfessionRevision.EFFECTIVE_TO, now);
        revisionRepository.update(revisionId, revision);
    }

    public void retireScheme(final String schemeId, final long now) throws RepositoryException {
        if (schemeId.isBlank()) {
            return;
        }
        final JSONObject scheme = schemeRepository.get(schemeId);
        if (null == scheme) {
            throw new RepositoryException("职业等级方案不存在");
        }
        scheme.put(ProfessionLevelScheme.STATUS, "RETIRED");
        scheme.put(ProfessionLevelScheme.EFFECTIVE_TO, now);
        schemeRepository.update(schemeId, scheme);
    }
}
