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

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.model.RepeaterLike;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.repository.RepeaterLikeRepository;
import org.b3log.symphony.repository.UserRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 复读机转录站查询服务。
 */
@Service
public class RepeaterQueryService {

    private static final int MAX_FETCH_SIZE = 50;

    @Inject
    private RepeaterContentRepository repeaterContentRepository;

    @Inject
    private RepeaterLikeRepository repeaterLikeRepository;

    @Inject
    private UserRepository userRepository;

    public List<JSONObject> list(final String type, final String userId, final int fetchSize)
            throws ServiceException {
        final int safeSize = Math.min(Math.max(fetchSize, 1), MAX_FETCH_SIZE);
        try {
            final List<JSONObject> items = repeaterContentRepository.getList(buildListQuery(type, safeSize));
            for (final JSONObject item : items) {
                fillViewFields(item, userId);
            }
            return items;
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    public JSONObject next(final String type, final String userId, final List<String> excludedIds)
            throws ServiceException {
        try {
            final List<String> ids = new ArrayList<>();
            if (null != excludedIds) {
                for (final String id : excludedIds) {
                    if (id == null || !id.matches("[0-9]{1,32}")) throw new ServiceException("请求参数不合法");
                    if (!ids.contains(id)) ids.add(id);
                }
            }
            if (ids.size() > 250) throw new ServiceException("本轮浏览内容过多，请重新开始");
            final String where = " WHERE c.repeaterContentStatus=?"
                    + (RepeaterContent.isSupportedType(type) ? " AND c.repeaterContentType=?" : "")
                    + (ids.isEmpty() ? "" : " AND c.oId NOT IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")");
            final List<Object> args = new ArrayList<>();
            args.add(RepeaterContent.STATUS_C_VALID);
            if (RepeaterContent.isSupportedType(type)) args.add(type);
            args.addAll(ids);
            final String from = " FROM `" + repeaterContentRepository.getName() + "` c" + where;
            final long count = repeaterContentRepository.select("SELECT COUNT(*) AS total" + from,
                    args.toArray()).getFirst().getLong("total");
            if (count == 0) return null;
            // 按现有类型/状态索引计数后抽取偏移，不限制热门前 50 条，也不对正文随机排序。
            final List<Object> queryArgs = new ArrayList<>(args);
            queryArgs.add(java.util.concurrent.ThreadLocalRandom.current().nextLong(count));
            final List<JSONObject> item = repeaterContentRepository.select("SELECT c.oId,c.repeaterContentType,"
                    + "c.repeaterContent,c.repeaterContentAuthorId,c.repeaterContentLikeCount,c.repeaterContentCreatedTime,"
                    + "c.repeaterContentSource,c.repeaterContentStatus,c.repeaterContentUpdatedTime"
                    + from + " ORDER BY c.repeaterContentLikeCount,c.oId LIMIT 1 OFFSET ?", queryArgs.toArray());
            if (item.isEmpty()) return null;
            fillViewFields(item.getFirst(), userId);
            return item.getFirst();
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    public JSONObject next(final String type, final String userId, final String excludedId)
            throws ServiceException {
        return next(type, userId, StringUtils.isBlank(excludedId) ? List.of() : List.of(excludedId));
    }

    public JSONObject leaderboard() throws ServiceException {
        try {
            final String contentTable = "`" + repeaterContentRepository.getName() + "`";
            final String userTable = "`" + userRepository.getName() + "`";
            final List<JSONObject> authors = repeaterContentRepository.select("SELECT c.repeaterContentAuthorId AS authorId,"
                    + "u.userName AS authorName,SUM(c.repeaterContentLikeCount) AS likeCount FROM " + contentTable
                    + " c JOIN " + userTable + " u ON u.oId=c.repeaterContentAuthorId WHERE c.repeaterContentStatus=?"
                    + " AND c.repeaterContentAuthorId<>'' AND u.userStatus=0 GROUP BY c.repeaterContentAuthorId,u.userName"
                    + " ORDER BY likeCount DESC,c.repeaterContentAuthorId LIMIT 20", RepeaterContent.STATUS_C_VALID);
            final List<JSONObject> items = repeaterContentRepository.select("SELECT c.oId AS id,c.repeaterContent AS content,"
                    + "c.repeaterContentLikeCount AS likeCount,CASE WHEN u.userStatus=0 THEN u.oId ELSE '' END AS authorId,"
                    + "CASE WHEN u.userStatus=0 THEN u.userName ELSE '' END AS authorName"
                    + " FROM " + contentTable + " c LEFT JOIN " + userTable + " u ON u.oId=c.repeaterContentAuthorId"
                    + " WHERE c.repeaterContentStatus=? ORDER BY c.repeaterContentLikeCount DESC,c.oId DESC LIMIT 20",
                    RepeaterContent.STATUS_C_VALID);
            return new JSONObject().put("authors", new JSONArray(authors)).put("items", new JSONArray(items));
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    public JSONObject getValidContent(final String contentId) throws ServiceException {
        try {
            final JSONObject content = repeaterContentRepository.get(contentId);
            if (null == content || RepeaterContent.STATUS_C_VALID != content.optInt(RepeaterContent.STATUS)) {
                throw new ServiceException("内容不存在");
            }
            return content;
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    private Query buildListQuery(final String type, final int fetchSize) {
        Query query = new Query().setFilter(new PropertyFilter(RepeaterContent.STATUS,
                FilterOperator.EQUAL, RepeaterContent.STATUS_C_VALID));
        if (RepeaterContent.isSupportedType(type)) {
            query = new Query().setFilter(CompositeFilterOperator.and(
                    new PropertyFilter(RepeaterContent.STATUS, FilterOperator.EQUAL, RepeaterContent.STATUS_C_VALID),
                    new PropertyFilter(RepeaterContent.TYPE, FilterOperator.EQUAL, type)));
        }
        return query.addSort(RepeaterContent.LIKE_COUNT, SortDirection.DESCENDING)
                .addSort(RepeaterContent.CREATED_TIME, SortDirection.DESCENDING)
                .setPage(1, fetchSize)
                .setPageCount(1);
    }

    public void fillViewFields(final JSONObject item, final String userId) throws ServiceException {
        try {
            fillViewFieldsInternal(item, userId);
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    private void fillViewFieldsInternal(final JSONObject item, final String userId) throws RepositoryException {
        item.put(RepeaterContent.CONTENT, item.optString(RepeaterContent.CONTENT));
        item.put("repeaterContentTypeLabel", getTypeLabel(item.optString(RepeaterContent.TYPE)));
        item.put("repeaterContentLiked", isLiked(userId, item.optString(Keys.OBJECT_ID)));
        final String authorId = item.optString(RepeaterContent.AUTHOR_ID);
        if (StringUtils.isNotBlank(authorId)) {
            final List<JSONObject> authors = userRepository.select("SELECT userName FROM `" + userRepository.getName()
                    + "` WHERE oId=? AND userStatus=0", authorId);
            if (authors.size() == 1) {
                item.put("repeaterContentAuthorName", authors.getFirst().getString(User.USER_NAME));
            }
        }
    }

    private boolean isLiked(final String userId, final String contentId) throws RepositoryException {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(contentId)) {
            return false;
        }
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(RepeaterLike.USER_ID, FilterOperator.EQUAL, userId),
                new PropertyFilter(RepeaterLike.CONTENT_ID, FilterOperator.EQUAL, contentId))).setPageCount(1);
        return null != repeaterLikeRepository.getFirst(query);
    }

    private String getTypeLabel(final String type) {
        if (RepeaterContent.TYPE_KFC.equals(type)) {
            return "疯狂星期四";
        }
        if (RepeaterContent.TYPE_FISH.equals(type)) {
            return "鱼类科普";
        }
        return "段子";
    }
}
