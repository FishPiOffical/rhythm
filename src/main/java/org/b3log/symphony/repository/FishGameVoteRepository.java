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
import org.b3log.symphony.model.FishGameVote;
import org.json.JSONObject;

import java.util.List;

/** 鱼游投票仓库。 */
@Repository
public class FishGameVoteRepository extends AbstractRepository {
    public FishGameVoteRepository() {
        super(FishGameVote.FISH_GAME_VOTE);
    }

    public JSONObject getByUserAndGame(final String userId, final String gameId) throws RepositoryException {
        final Query query = new Query().setPageCount(1).setFilter(CompositeFilterOperator.and(
                new PropertyFilter(FishGameVote.USER_ID, FilterOperator.EQUAL, userId),
                new PropertyFilter(FishGameVote.GAME_ID, FilterOperator.EQUAL, gameId)));
        return getFirst(query);
    }

    public List<JSONObject> getByUser(final String userId, final int page, final int pageSize) throws RepositoryException {
        final Query query = new Query().setPage(page, pageSize).setPageCount(1)
                .setFilter(new PropertyFilter(FishGameVote.USER_ID, FilterOperator.EQUAL, userId));
        return getList(query);
    }
}
