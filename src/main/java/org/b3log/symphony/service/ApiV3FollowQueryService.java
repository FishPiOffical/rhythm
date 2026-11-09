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

/**
 * 本人关注列表以占位保留不可访问的关系，隐藏目标资料。
 */
@Service
public class ApiV3FollowQueryService {
    @Inject
    private FollowRepository follows;
    @Inject
    private UserRepository users;
    @Inject
    private TagRepository tags;
    @Inject
    private ApiV3ContentQueryService content;
    @Inject
    private ApiV3ContentDtoService dto;

    /**
     * 在同一个 SQL 快照中判断当前有效账号之间的关注关系。
     */
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

    /**
     * 按所有者及类型精确匹配关系，仅返回关系状态。
     */
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
