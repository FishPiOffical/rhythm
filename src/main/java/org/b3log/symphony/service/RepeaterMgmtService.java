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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.repository.OperationRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;
import org.b3log.symphony.util.ApiV3RepeaterPublicationInputs;

/**
 * 复读机转录站管理服务。
 */
@Service
public class RepeaterMgmtService {
    private static final Logger LOGGER = LogManager.getLogger(RepeaterMgmtService.class);

    @Inject
    private RepeaterContentRepository repeaterContentRepository;

    @Inject
    private RepeaterQueryService repeaterQueryService;

    @Inject
    private ProfessionSourceEventCaptureService professionSourceEventCaptureService;

    @Inject
    private RepeaterContentModerationService moderationService;

    @Inject
    private OperationRepository operationRepository;

    public JSONObject createUserContent(final String userId, final JSONObject request) throws ServiceException {
        if (request == null || !(request.opt(RepeaterContent.TYPE) instanceof String type)
                || !(request.opt(RepeaterContent.CONTENT) instanceof String content) || type.length() > 32) {
            throw new ServiceException("内容格式不合法");
        }
        return createContent(userId, type, content, RepeaterContent.SOURCE_USER);
    }

    @Inject
    private RepeaterLikeMutationService repeaterLikeMutationService;

    public JSONObject toggleLike(final String userId, final String contentId) throws ServiceException {
        // This endpoint owns its root. Never commit an unrelated caller transaction.
        if (repeaterContentRepository.hasTransactionBegun()) throw new ServiceException("复读机操作暂不可用");
        final Transaction transaction = ApiV3Transactions.strict(repeaterContentRepository.beginTransaction());
        try {
            final JSONObject result = repeaterLikeMutationService.setInCurrentTransaction(userId, contentId, null);
            transaction.commit();
            return new JSONObject().put("liked", result.getBoolean("liked"))
                    .put(RepeaterContent.LIKE_COUNT, result.getLong("likeCount"));
        } catch (final Exception e) {
            rollback(transaction);
            if (e instanceof ApiV3Exception api) throw new ServiceException(api.getMessage());
            throw new ServiceException("复读机操作暂不可用");
        }
    }

    public void removeContent(final String contentId, final JSONObject operation) throws ServiceException {
        if (contentId == null || !contentId.matches("[1-9][0-9]{0,18}")
                || repeaterContentRepository.hasTransactionBegun()) {
            throw new ServiceException("复读内容删除暂不可用");
        }
        final Transaction transaction = ApiV3Transactions.strict(repeaterContentRepository.beginTransaction());
        try {
            final var rows = repeaterContentRepository.select("SELECT oId,repeaterContentStatus FROM `"
                    + repeaterContentRepository.getName() + "` WHERE oId=? FOR UPDATE", contentId);
            if (rows.size() != 1 || RepeaterContent.STATUS_C_VALID != rows.getFirst().optInt(RepeaterContent.STATUS)) {
                throw new ServiceException("内容不存在或已删除");
            }
            repeaterContentRepository.update(contentId, new JSONObject()
                    .put(RepeaterContent.STATUS, RepeaterContent.STATUS_C_REMOVED)
                    .put(RepeaterContent.UPDATED_TIME, System.currentTimeMillis()),
                    RepeaterContent.STATUS, RepeaterContent.UPDATED_TIME);
            professionSourceEventCaptureService.targetRemoved("repeater", contentId);
            operationRepository.add(operation);
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            if (e instanceof ServiceException serviceException) throw serviceException;
            LOGGER.error("Repeater deletion failed, contentId={}", contentId, e);
            throw new ServiceException("复读内容删除暂不可用");
        }
    }

    private JSONObject createContent(final String userId, final String type, final String content,
                                     final String source) throws ServiceException {
        final JSONObject item = buildContent(userId, type, content, source);
        if (RepeaterContent.SOURCE_USER.equals(source)) {
            try {
                moderationService.requireApproved(item.getString(RepeaterContent.CONTENT));
            } catch (final ApiV3Exception e) {
                throw new ServiceException(e.getMessage());
            }
        }
        if (repeaterContentRepository.hasTransactionBegun()) throw new ServiceException("复读机操作暂不可用");
        final Transaction transaction = ApiV3Transactions.strict(repeaterContentRepository.beginTransaction());
        try {
            repeaterContentRepository.add(item);
            professionSourceEventCaptureService.repeaterPublished(item);
            repeaterQueryService.fillViewFields(item, userId);
            transaction.commit();
            return item;
        } catch (final Exception e) {
            rollback(transaction);
            if (e instanceof ServiceException failure) throw failure;
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
        final String plain;
        try {
            plain = ApiV3RepeaterPublicationInputs.plainText(content);
        } catch (final ApiV3Exception e) {
            throw new ServiceException("内容格式不合法");
        }
        if (plain.length() < RepeaterContent.MIN_CONTENT_LENGTH) {
            throw new ServiceException("内容太短");
        }
        if (plain.length() > RepeaterContent.MAX_CONTENT_LENGTH) {
            throw new ServiceException("内容长度不能超过 " + RepeaterContent.MAX_CONTENT_LENGTH + " 个字符");
        }
        return plain;
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
