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
import org.b3log.latke.Keys;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.Reaction;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Reaction repository.
 */
@Repository
public class ReactionRepository extends AbstractRepository {

    private static final int QUERY_PAGE_SIZE = 512;

    public ReactionRepository() {
        super(Reaction.REACTION);
    }

    /** The caller must hold the target lock and commit content removal with this cleanup. */
    public void removeTargetInCurrentTransaction(final String targetType, final String targetId) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("reaction removal requires transaction");
        if (!Reaction.isValidTargetType(targetType)) throw new IllegalArgumentException("invalid reaction target type");
        for (final JSONObject row : select("SELECT oId FROM `" + getName()
                + "` WHERE reactionTargetType=? AND reactionTargetId=? ORDER BY oId FOR UPDATE", targetType, targetId)) {
            remove(row.getString(Keys.OBJECT_ID));
        }
    }

    public JSONObject lockEntity(final String table, final String id) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("reaction transaction required");
        final List<JSONObject> rows = select("SELECT * FROM " + table + " WHERE oId=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<JSONObject> getOwnedForUpdate(final String actor, final String type, final String id, final String group) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("reaction transaction required");
        return select("SELECT * FROM " + getName() + " WHERE reactionUserId=? AND reactionTargetType=? AND reactionTargetId=? AND reactionGroup=?"
                + " ORDER BY reactionUpdatedTime DESC,oId DESC FOR UPDATE", actor, type, id, group);
    }

    public JSONObject getByUserTargetGroup(final String userId, final String targetType,
                                           final String targetId, final String groupType) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(Reaction.USER_ID, FilterOperator.EQUAL, userId),
                new PropertyFilter(Reaction.TARGET_TYPE, FilterOperator.EQUAL, targetType),
                new PropertyFilter(Reaction.TARGET_ID, FilterOperator.EQUAL, targetId),
                new PropertyFilter(Reaction.GROUP, FilterOperator.EQUAL, groupType)
        ));
        return getFirst(query);
    }

    public List<JSONObject> getByTargetsGroup(final String targetType, final Collection<String> targetIds,
                                              final String groupType) throws RepositoryException {
        if (targetIds == null || targetIds.isEmpty()) {
            return Collections.emptyList();
        }
        final List<JSONObject> results = new ArrayList<>();
        int page = 1;
        while (true) {
            final Query query = new Query().setPage(page, QUERY_PAGE_SIZE).setPageCount(1)
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter(Reaction.TARGET_TYPE, FilterOperator.EQUAL, targetType),
                            new PropertyFilter(Reaction.TARGET_ID, FilterOperator.IN, targetIds),
                            new PropertyFilter(Reaction.GROUP, FilterOperator.EQUAL, groupType)
                    ))
                    .addSort(Reaction.TARGET_ID, SortDirection.ASCENDING)
                    .addSort(Reaction.UPDATED_TIME, SortDirection.DESCENDING);
            final List<JSONObject> currentPage = getList(query);
            results.addAll(currentPage);
            if (currentPage.size() < QUERY_PAGE_SIZE) {
                return results;
            }
            page++;
        }
    }
}
