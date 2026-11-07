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

import org.b3log.latke.Keys;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.Vote;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Vote repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.1.0.0, Sep 15, 2018
 * @since 1.3.0
 */
@Repository
public class VoteRepository extends AbstractRepository {

    /**
     * Removes votes for one typed target inside the caller's transaction.
     *
     * @param dataId the specified data id
     * @param dataType the target kind from Vote.DATA_TYPE_C_*
     * @throws RepositoryException repository exception
     */
    public void removeByDataId(final String dataId, final int dataType) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("vote removal requires transaction");
        if (dataType != Vote.DATA_TYPE_C_ARTICLE && dataType != Vote.DATA_TYPE_C_COMMENT
                && dataType != Vote.DATA_TYPE_C_USER && dataType != Vote.DATA_TYPE_C_TAG) {
            throw new IllegalArgumentException("unknown vote target type");
        }
        final var rows = select("SELECT oId FROM `" + getName()
                + "` WHERE dataId=? AND dataType=? ORDER BY oId FOR UPDATE", dataId, dataType);
        for (final JSONObject row : rows) remove(row.getString(Keys.OBJECT_ID));
    }

    /**
     * Removes vote if it exists.
     *
     * @param userId   the specified user id
     * @param dataId   the specified data entity id
     * @param dataType the specified data type
     * @return the removed vote type, returns {@code -1} if removed nothing
     * @throws RepositoryException repository exception
     */
    public int removeIfExists(final String userId, final String dataId, final int dataType) throws RepositoryException {
        final List<Filter> filters = new ArrayList<>();
        filters.add(new PropertyFilter(Vote.USER_ID, FilterOperator.EQUAL, userId));
        filters.add(new PropertyFilter(Vote.DATA_ID, FilterOperator.EQUAL, dataId));
        filters.add(new PropertyFilter(Vote.DATA_TYPE, FilterOperator.EQUAL, dataType));
        final Query query = new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters));
        final JSONObject voteToRemove = getFirst(query);
        if (null == voteToRemove) {
            return -1;
        }
        remove(voteToRemove.optString(Keys.OBJECT_ID));
        return voteToRemove.optInt(Vote.TYPE);
    }

    /**
     * Public constructor.
     */
    public VoteRepository() {
        super(Vote.VOTE);
    }
}
