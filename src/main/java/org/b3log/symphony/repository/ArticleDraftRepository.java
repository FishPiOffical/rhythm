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
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ArticleDraft;
import org.json.JSONObject;

import java.util.List;

/**
 * Article draft repository.
 */
@Repository
public class ArticleDraftRepository extends AbstractRepository {

    public ArticleDraftRepository() {
        super(ArticleDraft.ARTICLE_DRAFT);
    }

    public JSONObject getOwnedDraft(final String userId, final String draftId) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(ArticleDraft.USER_ID, FilterOperator.EQUAL, userId),
                new PropertyFilter(Keys.OBJECT_ID, FilterOperator.EQUAL, draftId)))
                .setPageCount(1);
        return getFirst(query);
    }

    public List<JSONObject> getUserDrafts(final String userId, final int fetchSize) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(ArticleDraft.USER_ID,
                FilterOperator.EQUAL, userId))
                .addSort(ArticleDraft.UPDATED_TIME, SortDirection.DESCENDING)
                .setPage(1, fetchSize)
                .setPageCount(1);
        return getList(query);
    }
}
