package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.Filter;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Follow;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.TagRepository;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.repository.FollowRepository;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONArray;
import org.json.JSONObject;

/** Owned relationships keep inaccessible targets as placeholders, without disclosing target metadata. */
@Service
public class ApiV3FollowQueryService {
    @Inject private FollowRepository follows;
    @Inject private UserRepository users;
    @Inject private TagRepository tags;
    @Inject private ApiV3ContentQueryService content;
    @Inject private ApiV3ContentDtoService dto;
    /** One SQL snapshot of the authenticated owner's relationship to a currently active user. */
    public JSONObject userState(final JSONObject viewer, final String targetId) throws RepositoryException {
        final String actor = viewer.optString(Keys.OBJECT_ID);
        if (!actor.matches("[1-9][0-9]{0,18}") || targetId == null || !targetId.matches("[1-9][0-9]{0,18}")) {
            throw new ApiV3Exception(422, "invalid_input", "用户ID不合法");
        }
        final var rows = follows.select("SELECT t.oId AS targetId, EXISTS (SELECT 1 FROM `" + follows.getName()
                + "` f WHERE f.followerId=? AND f.followingId=t.oId AND f.followingType=?) AS following FROM `"
                + users.getName() + "` a JOIN `" + users.getName() + "` t ON t.oId=? WHERE a.oId=? AND a.userStatus=? AND t.userStatus=?",
                actor, Follow.FOLLOWING_TYPE_C_USER, targetId, actor, UserExt.USER_STATUS_C_VALID, UserExt.USER_STATUS_C_VALID);
        if (rows.size() != 1 || !targetId.equals(rows.getFirst().optString("targetId"))) throw ApiV3Requests.notFound();
        return new JSONObject().put("targetId", targetId).put("following", rows.getFirst().getInt("following") != 0);
    }
    /** Exact owner/type relation; target metadata never enters this response. */
    public JSONObject tagState(final JSONObject viewer, final String targetId) throws RepositoryException {
        final String actor = checkedActor(viewer, targetId);
        return state(follows.select("SELECT t.oId AS targetId, EXISTS (SELECT 1 FROM `" + follows.getName()
                + "` f WHERE f.followerId=? AND f.followingId=t.oId AND f.followingType=?) AS following FROM `"
                + users.getName() + "` a JOIN `" + tags.getName() + "` t ON t.oId=? WHERE a.oId=? AND a.userStatus=? AND t.tagStatus=?",
                actor, Follow.FOLLOWING_TYPE_C_TAG, targetId, actor, UserExt.USER_STATUS_C_VALID, Tag.TAG_STATUS_C_VALID), targetId);
    }
    public JSONObject articleState(final JSONObject viewer, final String targetId, final boolean watch) throws RepositoryException {
        final String actor = checkedActor(viewer, targetId);
        if (content.viewableArticle(targetId, viewer) == null) throw ApiV3Requests.notFound();
        return state(follows.select("SELECT ? AS targetId, EXISTS (SELECT 1 FROM `" + follows.getName()
                + "` f WHERE f.followerId=? AND f.followingId=? AND f.followingType=?) AS following FROM `"
                + users.getName() + "` a WHERE a.oId=? AND a.userStatus=?", targetId, actor, targetId,
                watch ? Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH : Follow.FOLLOWING_TYPE_C_ARTICLE, actor, UserExt.USER_STATUS_C_VALID), targetId);
    }
    private static String checkedActor(final JSONObject viewer, final String targetId) {
        final String actor = viewer == null ? "" : viewer.optString(Keys.OBJECT_ID);
        if (!actor.matches("[1-9][0-9]{0,18}") || targetId == null || !targetId.matches("[1-9][0-9]{0,18}"))
            throw new ApiV3Exception(422, "invalid_input", "目标ID不合法");
        return actor;
    }
    private static JSONObject state(final java.util.List<JSONObject> rows, final String targetId) {
        if (rows.size() != 1 || !targetId.equals(rows.getFirst().optString("targetId"))) throw ApiV3Requests.notFound();
        return new JSONObject().put("targetId", targetId).put("following", rows.getFirst().getInt("following") != 0);
    }
    public JSONObject articles(final JSONObject viewer, final boolean watch, final int p, final int size) throws RepositoryException {
        final Filter filter = CompositeFilterOperator.and(
                new PropertyFilter(Follow.FOLLOWER_ID, FilterOperator.EQUAL, viewer.optString(Keys.OBJECT_ID)),
                new PropertyFilter(Follow.FOLLOWING_TYPE, FilterOperator.EQUAL, watch ? Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH : Follow.FOLLOWING_TYPE_C_ARTICLE));
        final Query query = new Query().setFilter(filter).setPage(p, size).setPageCount(1).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        final JSONArray list = new JSONArray();
        for (final JSONObject relation : follows.getList(query)) {
            final String id = relation.optString(Follow.FOLLOWING_ID);
            final JSONObject article = content.viewableArticle(id, viewer);
            list.put(null == article ? new JSONObject().put("id", id).put("available", false) : dto.summary(article).put("available", true));
        }
        return ApiV3Requests.pagination(list, p, size, follows.count(new Query().setFilter(filter)));
    }
}
