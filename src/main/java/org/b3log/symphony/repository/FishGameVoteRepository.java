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
