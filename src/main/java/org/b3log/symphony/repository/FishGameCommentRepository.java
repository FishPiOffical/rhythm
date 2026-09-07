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
import org.b3log.symphony.model.FishGameComment;
import org.json.JSONObject;

import java.util.List;

/** 鱼游评论仓库。 */
@Repository
public class FishGameCommentRepository extends AbstractRepository {
    public FishGameCommentRepository() {
        super(FishGameComment.FISH_GAME_COMMENT);
    }

    public List<JSONObject> getByGame(final String gameId, final int page, final int pageSize) throws RepositoryException {
        final Query query = new Query().setPage(page, pageSize).setPageCount(1)
                .setFilter(new PropertyFilter(FishGameComment.GAME_ID, FilterOperator.EQUAL, gameId))
                .addSort(FishGameComment.CREATED_TIME, SortDirection.DESCENDING);
        return getList(query);
    }
}
