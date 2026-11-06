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
import org.b3log.symphony.repository.MilestoneRepository;
import org.b3log.symphony.util.ApiV3MilestoneDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;

@Service
public class ApiV3MilestoneQueryService {
    @Inject private MilestoneRepository milestones;
    public JSONObject list(final String actor, final int status, final int page, final int size) throws RepositoryException {
        final JSONArray list = new JSONArray();
        for (final JSONObject row : milestones.pageOwned(actor, status, page, size)) list.put(ApiV3MilestoneDto.owned(row));
        return ApiV3Requests.pagination(list, page, size, milestones.countOwned(actor, status));
    }
    public JSONObject detail(final String actor, final String id) throws RepositoryException {
        final JSONObject row = milestones.getOwned(actor, id);
        if (null == row) throw ApiV3Requests.notFound();
        return ApiV3MilestoneDto.owned(row);
    }
}
