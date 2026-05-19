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
import org.b3log.symphony.util.Escapes;
import org.json.JSONObject;

import java.util.List;
import java.util.Random;

/**
 * 复读机转录站查询服务。
 */
@Service
public class RepeaterQueryService {

    private static final int MAX_FETCH_SIZE = 50;
    private static final Random RANDOM = new Random();

    @Inject
    private RepeaterContentRepository repeaterContentRepository;

    @Inject
    private RepeaterLikeRepository repeaterLikeRepository;

    @Inject
    private UserQueryService userQueryService;

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

    public JSONObject next(final String type, final String userId, final String excludedId)
            throws ServiceException {
        final List<JSONObject> items = list(type, userId, MAX_FETCH_SIZE);
        items.removeIf(item -> StringUtils.equals(item.optString(Keys.OBJECT_ID), excludedId));
        if (items.isEmpty()) {
            return null;
        }
        return items.get(RANDOM.nextInt(items.size()));
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
            final JSONObject author = userQueryService.getUser(authorId);
            if (null != author) {
                item.put("repeaterContentAuthorName", Escapes.escapeHTML(author.optString(User.USER_NAME)));
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
