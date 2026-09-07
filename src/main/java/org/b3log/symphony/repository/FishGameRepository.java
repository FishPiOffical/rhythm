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
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.FishGame;
import org.json.JSONObject;

import java.util.List;

/** 鱼游投稿仓库。 */
@Repository
public class FishGameRepository extends AbstractRepository {
    public FishGameRepository() {
        super(FishGame.FISH_GAME);
    }

    public List<JSONObject> getApproved(final int page, final int pageSize) throws RepositoryException {
        final String sql = "SELECT * FROM `" + getName() + "` WHERE `" + FishGame.STATUS + "`=? "
                + "ORDER BY (`" + FishGame.LIKE_COUNT + "` + `" + FishGame.DISLIKE_COUNT + "`) DESC, "
                + "`" + FishGame.LIKE_COUNT + "` DESC, `" + FishGame.CREATED_TIME + "` DESC LIMIT ?";
        return select(sql, FishGame.STATUS_APPROVED, pageSize);
    }

    public List<JSONObject> getAll(final int page, final int pageSize) throws RepositoryException {
        final Query query = new Query().setPage(page, pageSize).setPageCount(1)
                .addSort(FishGame.STATUS, SortDirection.ASCENDING)
                .addSort(FishGame.UPDATED_TIME, SortDirection.DESCENDING);
        return getList(query);
    }

    public List<JSONObject> getByAuthor(final String authorId, final int page, final int pageSize)
            throws RepositoryException {
        final Query query = new Query().setPage(page, pageSize).setPageCount(1)
                .setFilter(new PropertyFilter(FishGame.AUTHOR_ID, FilterOperator.EQUAL, authorId))
                .addSort(FishGame.UPDATED_TIME, SortDirection.DESCENDING);
        return getList(query);
    }

    public JSONObject getByUrl(final String url) throws RepositoryException {
        final Query query = new Query().setPageCount(1)
                .setFilter(new PropertyFilter(FishGame.URL, FilterOperator.EQUAL, url));
        return getFirst(query);
    }

    public JSONObject getForUpdate(final String gameId) throws RepositoryException {
        final List<JSONObject> values = select("SELECT * FROM `" + getName() + "` WHERE `oId`=? FOR UPDATE", gameId);
        return values.isEmpty() ? null : values.getFirst();
    }
}
