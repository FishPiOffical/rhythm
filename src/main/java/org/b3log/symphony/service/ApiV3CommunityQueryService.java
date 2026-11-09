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
import org.b3log.symphony.model.FishGame;
import org.b3log.symphony.model.FishGameComment;
import org.b3log.symphony.model.FishGameVote;
import org.b3log.symphony.model.Milestone;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.FishGameRepository;
import org.b3log.symphony.repository.FishGameCommentRepository;
import org.b3log.symphony.repository.FishGameVoteRepository;
import org.b3log.symphony.repository.MilestoneRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3CommunityDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;

/** 查询经审核的社区资源和明确公开的地理位置列表。 */
@Service
public class ApiV3CommunityQueryService {
    @Inject private FishGameRepository gameRepository;
    @Inject private FishGameCommentRepository commentRepository;
    @Inject private FishGameVoteRepository voteRepository;
    @Inject private MilestoneRepository milestoneRepository;
    @Inject private UserRepository userRepository;
    @Inject private UserQueryService userQueryService;
    @Inject private ApiV3ContentDtoService contentDtoService;
    @Inject private OptionQueryService optionQueryService;
    @Inject private ApiV3DiscoveryQueryService discoveryQueryService;

    public JSONObject games(final JSONObject viewer, final int p, final int size, final String sort) throws RepositoryException {
        final JSONArray list = new JSONArray();
        for (final JSONObject row : gameRepository.getV3ApprovedPage(p, size, sort)) list.put(gameDto(row, viewer));
        final long total = gameRepository.count(new Query().setFilter(new PropertyFilter(FishGame.STATUS, FilterOperator.EQUAL, FishGame.STATUS_APPROVED)));
        return ApiV3Requests.pagination(list, p, size, total);
    }
    public JSONObject game(final String id, final JSONObject viewer) throws RepositoryException { return gameDto(approvedGame(id), viewer); }
    public JSONObject gameComments(final String id, final int p, final int size) throws RepositoryException {
        approvedGame(id);
        final Filter filter = new PropertyFilter(FishGameComment.GAME_ID, FilterOperator.EQUAL, id);
        final Query query = new Query().setFilter(filter).setPage(p, size).setPageCount(1)
                .addSort(FishGameComment.CREATED_TIME, SortDirection.DESCENDING).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        final JSONArray list = new JSONArray();
        for (final JSONObject row : commentRepository.getList(query)) {
            list.put(gameCommentDto(row));
        }
        return ApiV3Requests.pagination(list, p, size, commentRepository.count(new Query().setFilter(filter)));
    }
    public JSONObject gameCommentDto(final JSONObject row) {
        final JSONObject user = userQueryService.getUser(row.optString(FishGameComment.AUTHOR_ID));
        final boolean available = null != user && UserExt.USER_STATUS_C_INVALID != user.optInt(UserExt.USER_STATUS)
                && UserExt.USER_STATUS_C_DEACTIVATED != user.optInt(UserExt.USER_STATUS);
        return new JSONObject().put("id", row.optString(Keys.OBJECT_ID)).put("gameId", row.optString(FishGameComment.GAME_ID))
                .put("content", available ? row.optString(FishGameComment.CONTENT) : "").put("contentAvailable", available)
                .put("author", contentDtoService.author(available ? user : null, false)).put("createdAt", row.optLong(FishGameComment.CREATED_TIME));
    }
    private JSONObject approvedGame(final String id) throws RepositoryException {
        final JSONObject row = gameRepository.get(id);
        if (null == row || FishGame.STATUS_APPROVED != row.optInt(FishGame.STATUS)) throw ApiV3Requests.notFound();
        return row;
    }
    private JSONObject gameDto(final JSONObject row, final JSONObject viewer) throws RepositoryException {
        final JSONObject user = userQueryService.getUser(row.optString(FishGame.AUTHOR_ID));
        final JSONObject vote = null == viewer ? null : voteRepository.getByUserAndGame(viewer.optString(Keys.OBJECT_ID), row.optString(Keys.OBJECT_ID));
        return ApiV3CommunityDto.game(row, contentDtoService.author(user, false), null == vote ? "" : vote.optString(FishGameVote.VALUE));
    }

    public JSONObject milestones(final int p, final int size) throws RepositoryException {
        final Filter filter = new PropertyFilter(Milestone.MILESTONE_STATUS, FilterOperator.EQUAL, Milestone.STATUS_C_APPROVED);
        final Query query = new Query().setFilter(filter).setPage(p, size).setPageCount(1)
                .addSort(Milestone.MILESTONE_DATE, SortDirection.DESCENDING).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        final JSONArray list = new JSONArray();
        for (final JSONObject row : milestoneRepository.getList(query)) list.put(milestoneDto(row));
        return ApiV3Requests.pagination(list, p, size, milestoneRepository.count(new Query().setFilter(filter)));
    }
    public JSONObject milestone(final String id) throws RepositoryException {
        final JSONObject row = milestoneRepository.get(id);
        if (null == row || Milestone.STATUS_C_APPROVED != row.optInt(Milestone.MILESTONE_STATUS)) throw ApiV3Requests.notFound();
        return milestoneDto(row);
    }
    private JSONObject milestoneDto(final JSONObject row) {
        final String html = Markdowns.clean(row.optString(Milestone.MILESTONE_CONTENT), "");
        return ApiV3CommunityDto.milestone(row, html, Jsoup.parse(html).text());
    }
    public JSONObject statistics() {
        final int online = optionQueryService.getOnlineVisitorCount();
        final int members = optionQueryService.getOnlineMemberCount();
        final JSONObject raw = optionQueryService.getStatistic();
        if (null == raw) throw new ApiV3Requests.ApiException(503, "service_unavailable", "统计服务暂不可用");
        return new JSONObject().put("memberCount", raw.optLong(Option.ID_C_STATISTIC_MEMBER_COUNT))
                .put("articleCount", raw.optLong(Option.ID_C_STATISTIC_ARTICLE_COUNT)).put("commentCount", raw.optLong(Option.ID_C_STATISTIC_CMT_COUNT))
                .put("domainCount", raw.optLong(Option.ID_C_STATISTIC_DOMAIN_COUNT)).put("tagCount", raw.optLong(Option.ID_C_STATISTIC_TAG_COUNT))
                .put("linkCount", raw.optLong(Option.ID_C_STATISTIC_LINK_COUNT))
                .put("peakOnlineVisitorCount", Math.max(online, raw.optLong(Option.ID_C_STATISTIC_MAX_ONLINE_VISITOR_COUNT)))
                .put("onlineVisitorCount", online).put("onlineMemberCount", members);
    }
    public JSONObject cityUsers(final String city, final int p, final int size) throws RepositoryException {
        final Filter filter = CompositeFilterOperator.and(new PropertyFilter(UserExt.USER_CITY, FilterOperator.EQUAL, city),
                new PropertyFilter(UserExt.USER_GEO_STATUS, FilterOperator.EQUAL, UserExt.USER_GEO_STATUS_C_PUBLIC),
                new PropertyFilter(UserExt.USER_STATUS, FilterOperator.EQUAL, UserExt.USER_STATUS_C_VALID));
        final Query query = new Query().setFilter(filter).setPage(p, size).setPageCount(1)
                .addSort(UserExt.USER_LATEST_LOGIN_TIME, SortDirection.DESCENDING).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        final JSONArray list = new JSONArray();
        for (final JSONObject user : userRepository.getList(query)) list.put(contentDtoService.author(user, false).put("userIntro", user.optString(UserExt.USER_INTRO)));
        return ApiV3Requests.pagination(list, p, size, userRepository.count(new Query().setFilter(filter)));
    }
    public JSONObject cityArticles(final String city, final JSONObject viewer, final int p, final int size, final String sort) throws RepositoryException {
        return discoveryQueryService.articles(viewer, p, size, sort, "all", new ApiV3DiscoveryQueryService.Selection(null, null, null, null, null, city));
    }
}
