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

import org.b3log.symphony.util.BagMutation;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.Filter;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.model.Pagination;
import org.b3log.symphony.model.ArticleDraft;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ArticleDraftRepository;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.LongArticleChapterRepository;
import org.b3log.symphony.util.ApiV3ArticleEditorDto;
import org.b3log.symphony.repository.LongArticleColumnRepository;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3DraftDto;
import org.b3log.symphony.util.Markdowns;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 读取个人资源，处理器传入已认证的本人身份。
 */
@Service
public class ApiV3PersonalQueryService {
    @Inject
    private ArticleDraftRepository articleDraftRepository;
    @Inject
    private ArticleRepository articleRepository;
    @Inject
    private LongArticleChapterRepository chapterRepository;
    @Inject
    private LongArticleColumnRepository columnRepository;
    @Inject
    private ActivityQueryService activityQueryService;
    @Inject
    private LivenessQueryService livenessQueryService;
    @Inject
    private CloudService cloudService;
    @Inject
    private PointtransferQueryService pointtransferQueryService;
    @Inject
    private UserRepository userRepository;

    public JSONObject settings(final JSONObject user) {
        final JSONObject privacy = new JSONObject();
        privacy.put("articles", visible(user, UserExt.USER_ARTICLE_STATUS))
                .put("comments", visible(user, UserExt.USER_COMMENT_STATUS))
                .put("breezemoons", visible(user, UserExt.USER_BREEZEMOON_STATUS))
                .put("points", visible(user, UserExt.USER_POINT_STATUS))
                .put("followers", visible(user, UserExt.USER_FOLLOWER_STATUS))
                .put("followingUsers", visible(user, UserExt.USER_FOLLOWING_USER_STATUS))
                .put("followingTags", visible(user, UserExt.USER_FOLLOWING_TAG_STATUS))
                .put("followingArticles", visible(user, UserExt.USER_FOLLOWING_ARTICLE_STATUS));
        return new JSONObject().put("profile", new JSONObject().put("userNickname", user.optString(UserExt.USER_NICKNAME))
                        .put("userIntro", user.optString(UserExt.USER_INTRO)).put("userURL", user.optString(User.USER_URL)))
                .put("privacy", privacy).put("display", new JSONObject().put("commentViewMode", user.optInt(UserExt.USER_COMMENT_VIEW_MODE))
                        .put("avatarViewMode", user.optInt(UserExt.USER_AVATAR_VIEW_MODE))
                        .put("skin", user.optString(UserExt.USER_SKIN)).put("mobileSkin", user.optString(UserExt.USER_MOBILE_SKIN)));
    }

    public JSONObject checkin(final JSONObject user) {
        final String id = user.optString(Keys.OBJECT_ID);
        final int point = livenessQueryService.getCurrentLivenessPoint(id);
        final double liveness = Math.round(point * 10000D / Math.max(1, Symphonys.ACTIVITY_YESTERDAY_REWARD_MAX)) / 100D;
        return new JSONObject().put("checkedIn", activityQueryService.isCheckedinToday(id)).put("liveness", liveness)
                .put("threshold", Symphonys.ACTIVITY_AUTO_CHECKIN_LIVENESS_THRESHOLD)
                .put("streak", user.optInt(UserExt.USER_CURRENT_CHECKIN_STREAK));
    }

    public JSONObject bag(final String userId) {
        return BagMutation.dto(new JSONObject(cloudService.getBag(userId)));
    }

    public JSONObject memberPoints(final String name, final JSONObject identity, final int p, final int size) throws RepositoryException {
        if (!name.matches("[A-Za-z0-9_-]{1,64}")) throw ApiV3Requests.invalid();
        final JSONObject target = memberPointOwner(name);
        final String ownerId = target.getString(Keys.OBJECT_ID);
        requireMemberPointAccess(target, identity);
        final JSONObject result = pointPage(pointtransferQueryService.getUserPoints(ownerId, p, size, false), p, size, false);
        // 查询流水后再次读取可见性，避免设置变更或账号替换时返回旧权限下的记录。
        final JSONObject current = memberPointOwner(name);
        if (!ownerId.equals(current.getString(Keys.OBJECT_ID))) throw ApiV3Requests.notFound();
        requireMemberPointAccess(current, identity);
        return result;
    }

    private JSONObject memberPointOwner(final String name) throws RepositoryException {
        final var rows = userRepository.select("SELECT oId,userPointStatus FROM `" + userRepository.getName()
                        + "` WHERE userName=? AND userStatus IN (?,?) LIMIT 2",
                name, UserExt.USER_STATUS_C_VALID, UserExt.USER_STATUS_C_INVALID_LOGIN);
        if (rows.size() != 1) throw ApiV3Requests.notFound();
        return rows.getFirst();
    }

    private static void requireMemberPointAccess(final JSONObject target, final JSONObject identity) {
        if (UserExt.USER_XXX_STATUS_C_ENABLED == target.optInt(UserExt.USER_POINT_STATUS, -1)) return;
        final JSONObject viewer = identity == null ? null : identity.optJSONObject("user");
        final boolean firstParty = identity != null && ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(identity.optString("clientId"));
        if (viewer != null && firstParty && (target.getString(Keys.OBJECT_ID).equals(viewer.optString(Keys.OBJECT_ID))
                || Role.ROLE_ID_C_ADMIN.equals(viewer.optString(UserExt.USER_ROLE)))) return;
        throw new ApiV3Requests.ApiException(403, "point_history_private", "积分记录未公开");
    }

    public JSONObject points(final String userId, final int p, final int size) {
        return pointPage(pointtransferQueryService.getUserPoints(userId, p, size), p, size, true);
    }

    private static JSONObject pointPage(final JSONObject result, final int p, final int size, final boolean detailed) {
        if (null == result) throw new ApiV3Requests.ApiException(503, "service_unavailable", "积分服务暂不可用");
        final JSONArray raw = result.optJSONArray(Keys.RESULTS);
        final JSONArray list = new JSONArray();
        if (null != raw) for (int i = 0; i < raw.length(); i++) {
            final JSONObject row = raw.getJSONObject(i);
            // 会员页只投影该用户的账面变动，说明中的转账备注及来源详情只在本人接口展示。
            final JSONObject item = new JSONObject().put("time", row.optLong(Pointtransfer.TIME)).put("amount", row.optLong(Pointtransfer.SUM))
                    .put("type", row.optInt(Pointtransfer.TYPE)).put("operation", row.optString(Common.OPERATION))
                    .put("balance", row.optLong(Common.BALANCE)).put("displayType", row.optString(Common.DISPLAY_TYPE));
            if (detailed) item.put("descriptionHtml", Markdowns.clean(row.optString(Common.DESCRIPTION), ""));
            list.put(item);
        }
        return ApiV3Requests.pagination(list, p, size, result.optLong(Pagination.PAGINATION_RECORD_COUNT));
    }

    public JSONObject drafts(final String userId, final int p, final int size) throws RepositoryException {
        final Filter filter = new PropertyFilter(ArticleDraft.USER_ID, FilterOperator.EQUAL, userId);
        final Query query = new Query().setFilter(filter).setPage(p, size).setPageCount(1)
                .select(Keys.OBJECT_ID, ArticleDraft.TITLE, ArticleDraft.SUMMARY, ArticleDraft.TAGS, ArticleDraft.ARTICLE_TYPE,
                        ArticleDraft.COLUMN_ID, ArticleDraft.COLUMN_TITLE, ArticleDraft.CHAPTER_NO, ArticleDraft.CREATED_TIME, ArticleDraft.UPDATED_TIME)
                .addSort(ArticleDraft.UPDATED_TIME, SortDirection.DESCENDING).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        final JSONArray list = new JSONArray();
        for (final JSONObject row : articleDraftRepository.getList(query)) list.put(draftDto(row, false));
        return ApiV3Requests.pagination(list, p, size, articleDraftRepository.count(new Query().setFilter(filter)));
    }

    public JSONObject draft(final String userId, final String id) throws RepositoryException {
        final JSONObject raw = articleDraftRepository.getOwnedDraft(userId, id);
        if (null == raw) throw ApiV3Requests.notFound();
        return draftDto(raw, true);
    }

    public JSONObject articleSource(final String userId, final String id) throws RepositoryException {
        final var row = articleRepository.getOwnedEditorSource(id, userId, chapterRepository.getName(), columnRepository.getName());
        if (null == row) throw ApiV3Requests.notFound();
        return ApiV3ArticleEditorDto.from(row);
    }

    public JSONObject columns(final String userId, final int p, final int size) throws RepositoryException {
        final Filter filter = CompositeFilterOperator.and(
                new PropertyFilter(LongArticleColumn.COLUMN_AUTHOR_ID, FilterOperator.EQUAL, userId),
                new PropertyFilter(LongArticleColumn.COLUMN_STATUS, FilterOperator.EQUAL, LongArticleColumn.COLUMN_STATUS_C_VALID));
        final Query query = new Query().setFilter(filter).setPage(p, size).setPageCount(1)
                .addSort(LongArticleColumn.COLUMN_ORDER, SortDirection.ASCENDING)
                .addSort(LongArticleColumn.COLUMN_UPDATE_TIME, SortDirection.DESCENDING).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        final JSONArray list = new JSONArray();
        for (final JSONObject row : columnRepository.getList(query)) {
            list.put(new JSONObject().put("id", row.optString(Keys.OBJECT_ID)).put("title", row.optString(LongArticleColumn.COLUMN_TITLE))
                    .put("order", row.optInt(LongArticleColumn.COLUMN_ORDER))
                    .put("coverURL", row.optString(LongArticleColumn.COLUMN_COVER_URL)).put("articleCount", row.optInt(LongArticleColumn.COLUMN_ARTICLE_COUNT))
                    .put("createdAt", row.optLong(LongArticleColumn.COLUMN_CREATE_TIME)).put("updatedAt", row.optLong(LongArticleColumn.COLUMN_UPDATE_TIME)));
        }
        return ApiV3Requests.pagination(list, p, size, columnRepository.count(new Query().setFilter(filter)));
    }

    static JSONObject draftDto(final JSONObject raw, final boolean detail) {
        return ApiV3DraftDto.from(raw, detail);
    }

    private static boolean visible(final JSONObject user, final String field) {
        return UserExt.USER_XXX_STATUS_C_ENABLED == user.optInt(field);
    }
}
