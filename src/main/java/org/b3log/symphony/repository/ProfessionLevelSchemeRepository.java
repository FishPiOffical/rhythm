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
package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ProfessionLevelScheme;
import org.json.JSONObject;

import java.util.List;

/** 职业等级方案存储。 */
@Repository
public class ProfessionLevelSchemeRepository extends AbstractRepository {

    public ProfessionLevelSchemeRepository() {
        super(ProfessionLevelScheme.PROFESSION_LEVEL_SCHEME);
    }

    public JSONObject getForUpdate(final String schemeId) throws RepositoryException {
        final List<JSONObject> values = select("SELECT * FROM `" + getName() + "` WHERE `oId`=? FOR UPDATE", schemeId);
        return values.isEmpty() ? null : values.getFirst();
    }
}
