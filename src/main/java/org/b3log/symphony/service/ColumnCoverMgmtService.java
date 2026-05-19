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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.repository.LongArticleColumnRepository;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 专栏封面管理服务。
 */
@Service
public class ColumnCoverMgmtService {

    private static final String UNSAFE_URL_CHARS = ".*[\\s'\"\\\\<>&#();{}].*";

    @Inject
    private LongArticleColumnRepository longArticleColumnRepository;

    public JSONObject updateCoverURL(final String userId, final String columnId,
                                     final String coverURL) throws ServiceException {
        final String normalizedCoverURL = normalizeCoverURL(coverURL);
        final Transaction transaction = longArticleColumnRepository.beginTransaction();
        try {
            final JSONObject column = getOwnedColumn(userId, columnId);
            column.put(LongArticleColumn.COLUMN_COVER_URL, normalizedCoverURL);
            column.put(LongArticleColumn.COLUMN_UPDATE_TIME, System.currentTimeMillis());
            longArticleColumnRepository.update(columnId, column);
            transaction.commit();
            return column;
        } catch (final ServiceException e) {
            rollback(transaction);
            throw e;
        } catch (final RepositoryException e) {
            rollback(transaction);
            throw new ServiceException(e);
        }
    }

    private JSONObject getOwnedColumn(final String userId, final String columnId)
            throws RepositoryException, ServiceException {
        final JSONObject column = longArticleColumnRepository.get(columnId);
        if (null == column || LongArticleColumn.COLUMN_STATUS_C_VALID != column.optInt(LongArticleColumn.COLUMN_STATUS)
                || !StringUtils.equals(userId, column.optString(LongArticleColumn.COLUMN_AUTHOR_ID))) {
            throw new ServiceException("专栏不存在或无权限操作");
        }
        return column;
    }

    public static String normalizeCoverURL(final String coverURL) throws ServiceException {
        final String ret = StringUtils.trimToEmpty(coverURL);
        if (StringUtils.isBlank(ret)) {
            return "";
        }
        if (ret.length() > LongArticleColumn.MAX_COLUMN_COVER_URL_LENGTH) {
            throw new ServiceException("封面地址长度不能超过 "
                    + LongArticleColumn.MAX_COLUMN_COVER_URL_LENGTH + " 个字符");
        }
        if (ret.matches(UNSAFE_URL_CHARS)) {
            throw new ServiceException("封面地址格式不合法");
        }
        if (ret.startsWith("/")) {
            validateSitePath(ret);
            return ret;
        }
        validateRemoteURL(ret);
        return ret;
    }

    private static void validateSitePath(final String path) throws ServiceException {
        if (path.startsWith("//")) {
            throw new ServiceException("封面地址格式不合法");
        }
    }

    private static void validateRemoteURL(final String url) throws ServiceException {
        try {
            final URI uri = new URI(url);
            final String scheme = StringUtils.lowerCase(uri.getScheme());
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new ServiceException("封面地址仅支持 HTTP/HTTPS 或站内路径");
            }
            if (StringUtils.isBlank(uri.getHost())) {
                throw new ServiceException("封面地址格式不合法");
            }
        } catch (final URISyntaxException e) {
            throw new ServiceException("封面地址格式不合法");
        }
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
