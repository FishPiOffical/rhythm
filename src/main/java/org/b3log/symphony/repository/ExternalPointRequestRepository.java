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
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ExternalPointRequest;
import org.json.JSONObject;

import java.util.List;

/**
 * External point adjustment request repository.
 */
@Repository
public class ExternalPointRequestRepository extends AbstractRepository {

    public ExternalPointRequestRepository() {
        super(ExternalPointRequest.EXTERNAL_POINT_REQUEST);
    }

    /**
     * Gets a request by the specified source application and request id.
     *
     * @param sourceAppId source application id
     * @param requestId request id
     * @return request, returns {@code null} if not found
     * @throws RepositoryException repository exception
     */
    public JSONObject getBySourceAppIdAndRequestId(final String sourceAppId, final String requestId)
            throws RepositoryException {
        final Query query = new Query().setPage(1, 1).setFilter(
                CompositeFilterOperator.and(
                        new PropertyFilter(ExternalPointRequest.SOURCE_APP_ID, FilterOperator.EQUAL, sourceAppId),
                        new PropertyFilter(ExternalPointRequest.REQUEST_ID, FilterOperator.EQUAL, requestId)));
        final List<JSONObject> requests = getList(query);
        return requests.isEmpty() ? null : requests.get(0);
    }
}
