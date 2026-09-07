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
