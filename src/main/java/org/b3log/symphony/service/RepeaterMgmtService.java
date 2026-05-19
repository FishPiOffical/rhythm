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
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.model.RepeaterLike;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.repository.RepeaterLikeRepository;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

/**
 * 复读机转录站管理服务。
 */
@Service
public class RepeaterMgmtService {

    @Inject
    private RepeaterContentRepository repeaterContentRepository;

    @Inject
    private RepeaterLikeRepository repeaterLikeRepository;

    @Inject
    private RepeaterQueryService repeaterQueryService;

    public JSONObject createUserContent(final String userId, final JSONObject request) throws ServiceException {
        return createContent(userId, request.optString(RepeaterContent.TYPE),
                request.optString(RepeaterContent.CONTENT), RepeaterContent.SOURCE_USER);
    }

    public JSONObject toggleLike(final String userId, final String contentId) throws ServiceException {
        final Transaction transaction = repeaterContentRepository.beginTransaction();
        try {
            final JSONObject content = repeaterQueryService.getValidContent(contentId);
            final JSONObject existing = getLike(userId, contentId);
            final boolean liked = null == existing;
            if (liked) {
                addLike(userId, contentId);
            } else {
                repeaterLikeRepository.remove(existing.optString(Keys.OBJECT_ID));
            }
            content.put(RepeaterContent.LIKE_COUNT, countLikes(contentId));
            content.put(RepeaterContent.UPDATED_TIME, System.currentTimeMillis());
            repeaterContentRepository.update(contentId, content);
            transaction.commit();
            return new JSONObject().put("liked", liked)
                    .put(RepeaterContent.LIKE_COUNT, content.optInt(RepeaterContent.LIKE_COUNT));
        } catch (final ServiceException e) {
            rollback(transaction);
            throw e;
        } catch (final RepositoryException e) {
            rollback(transaction);
            throw new ServiceException(e);
        }
    }

    private JSONObject createContent(final String userId, final String type, final String content,
                                     final String source) throws ServiceException {
        final JSONObject item = buildContent(userId, type, content, source);
        final Transaction transaction = repeaterContentRepository.beginTransaction();
        try {
            repeaterContentRepository.add(item);
            repeaterQueryService.fillViewFields(item, userId);
            transaction.commit();
            return item;
        } catch (final ServiceException e) {
            rollback(transaction);
            throw e;
        } catch (final RepositoryException e) {
            rollback(transaction);
            throw new ServiceException(e);
        }
    }

    private JSONObject buildContent(final String userId, final String type, final String content,
                                    final String source) throws ServiceException {
        final String normalizedType = normalizeType(type);
        final String normalizedContent = normalizeContent(content);
        final long now = System.currentTimeMillis();
        final JSONObject item = new JSONObject();
        item.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        item.put(RepeaterContent.TYPE, normalizedType);
        item.put(RepeaterContent.CONTENT, normalizedContent);
        item.put(RepeaterContent.AUTHOR_ID, StringUtils.trimToEmpty(userId));
        item.put(RepeaterContent.SOURCE, source);
        item.put(RepeaterContent.STATUS, RepeaterContent.STATUS_C_VALID);
        item.put(RepeaterContent.LIKE_COUNT, 0);
        item.put(RepeaterContent.CREATED_TIME, now);
        item.put(RepeaterContent.UPDATED_TIME, now);
        return item;
    }

    private String normalizeType(final String type) throws ServiceException {
        final String ret = StringUtils.trimToEmpty(type);
        if (!RepeaterContent.isSupportedType(ret)) {
            throw new ServiceException("类型不合法");
        }
        return ret;
    }

    private String normalizeContent(final String content) throws ServiceException {
        final String plain = Jsoup.clean(StringUtils.trimToEmpty(content), Whitelist.none()).replaceAll("\\s+", " ");
        if (plain.length() < RepeaterContent.MIN_CONTENT_LENGTH) {
            throw new ServiceException("内容太短");
        }
        if (plain.length() > RepeaterContent.MAX_CONTENT_LENGTH) {
            throw new ServiceException("内容长度不能超过 " + RepeaterContent.MAX_CONTENT_LENGTH + " 个字符");
        }
        return plain;
    }

    private JSONObject getLike(final String userId, final String contentId) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(RepeaterLike.USER_ID, FilterOperator.EQUAL, userId),
                new PropertyFilter(RepeaterLike.CONTENT_ID, FilterOperator.EQUAL, contentId))).setPageCount(1);
        return repeaterLikeRepository.getFirst(query);
    }

    private void addLike(final String userId, final String contentId) throws RepositoryException {
        final JSONObject like = new JSONObject();
        like.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        like.put(RepeaterLike.USER_ID, userId);
        like.put(RepeaterLike.CONTENT_ID, contentId);
        like.put(RepeaterLike.CREATED_TIME, System.currentTimeMillis());
        repeaterLikeRepository.add(like);
    }

    private int countLikes(final String contentId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(RepeaterLike.CONTENT_ID,
                FilterOperator.EQUAL, contentId));
        return (int) repeaterLikeRepository.count(query);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
