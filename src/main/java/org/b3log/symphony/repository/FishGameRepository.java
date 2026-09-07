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
