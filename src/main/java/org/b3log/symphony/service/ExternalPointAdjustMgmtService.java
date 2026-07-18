/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.b3log.symphony.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ExternalPointRequest;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.repository.ExternalPointRequestRepository;
import org.json.JSONObject;

import java.util.concurrent.locks.ReentrantLock;

/**
 * External application point adjustment service.
 */
@Service
public class ExternalPointAdjustMgmtService {

    private static final Logger LOGGER = LogManager.getLogger(ExternalPointAdjustMgmtService.class);
    private static final int LOCK_STRIPE_COUNT = 256;
    private static final ReentrantLock[] REQUEST_LOCKS = new ReentrantLock[LOCK_STRIPE_COUNT];

    static {
        for (int i = 0; i < REQUEST_LOCKS.length; i++) {
            REQUEST_LOCKS[i] = new ReentrantLock();
        }
    }

    @Inject
    private ExternalPointRequestRepository externalPointRequestRepository;

    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    @Inject
    private NotificationMgmtService notificationMgmtService;

    @Inject
    private LogsService logsService;

    /**
     * Adjusts points once for the specified external request.
     *
     * @return point transfer id
     * @throws ServiceException service exception
     */
    public String adjust(final RequestContext context, final String userId, final String userName,
                         final int point, final String memo, final PointtransferSource source)
            throws ServiceException {
        final String requestHash = requestHash(userId, point, memo, source);
        final ReentrantLock requestLock = REQUEST_LOCKS[Math.floorMod(source.requestId().hashCode(), LOCK_STRIPE_COUNT)];
        requestLock.lock();
        try {
            final JSONObject existing = getExisting(source.requestId());
            if (null != existing) {
                return resolveExisting(existing, requestHash);
            }

            final boolean income = point > 0;
            final String fromId = income ? Pointtransfer.ID_C_SYS : userId;
            final String toId = income ? userId : Pointtransfer.ID_C_SYS;
            try {
                return pointtransferMgmtService.executeWithTransferLock(fromId, toId,
                        () -> execute(context, userId, userName, point, memo, source,
                                requestHash, fromId, toId, income));
            } catch (final ServiceException e) {
                throw e;
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Unable to execute locked external point adjustment", e);
                throw new ServiceException("无法完成交易。");
            }
        } finally {
            requestLock.unlock();
        }
    }

    private String execute(final RequestContext context, final String userId, final String userName,
                           final int point, final String memo, final PointtransferSource source,
                           final String requestHash, final String fromId, final String toId,
                           final boolean income) throws ServiceException {
        final Transaction transaction = externalPointRequestRepository.beginTransaction();
        String transferId = null;
        JSONObject publicLog = null;
        try {
            final JSONObject request = new JSONObject();
            request.put(ExternalPointRequest.REQUEST_ID, source.requestId());
            request.put(ExternalPointRequest.REQUEST_HASH, requestHash);
            request.put(ExternalPointRequest.TRANSFER_ID, "");
            request.put(ExternalPointRequest.CREATE_TIME, System.currentTimeMillis());
            final String externalRequestId = externalPointRequestRepository.add(request);

            final int sum = Math.abs(point);
            transferId = pointtransferMgmtService.transferInCurrentTransaction(
                    fromId, toId, Pointtransfer.TRANSFER_TYPE_C_APP_ADJUST, sum, "",
                    System.currentTimeMillis(), memo, source, false);
            if (null == transferId) {
                throw new ServiceException("无法完成交易。");
            }

            notificationMgmtService.addAppPointAdjustNotificationInCurrentTransaction(userId, transferId);
            publicLog = logsService.addAppPointLogInCurrentTransaction(
                    context, income, source.appName(), source.scene(), userName, sum, memo);
            externalPointRequestRepository.update(externalRequestId,
                    new JSONObject().put(ExternalPointRequest.TRANSFER_ID, transferId));
            transaction.commit();
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            final JSONObject existing = getExisting(source.requestId());
            if (null != existing) {
                return resolveExisting(existing, requestHash);
            }

            LOGGER.log(Level.ERROR, "External point adjustment failed [requestId=" + source.requestId()
                    + ", appId=" + source.appId() + ", userId=" + userId + "]", e);
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            }
            throw new ServiceException("无法完成交易。");
        }

        publishPostCommitActions(fromId, toId, transferId, userId);
        LogsService.publishPublicLog(publicLog);
        return transferId;
    }

    private void publishPostCommitActions(final String fromId, final String toId,
                                          final String transferId, final String userId) {
        try {
            pointtransferMgmtService.indexTransferAsync(fromId, toId, transferId);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Unable to index committed point transfer [transferId=" + transferId + "]", e);
        }
        try {
            notificationMgmtService.publishRefreshNotification(userId);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Unable to publish committed point notification [userId=" + userId + "]", e);
        }
    }

    private JSONObject getExisting(final String requestId) throws ServiceException {
        try {
            return externalPointRequestRepository.getByRequestId(requestId);
        } catch (final RepositoryException e) {
            throw new ServiceException("请求记录查询失败。");
        }
    }

    private String resolveExisting(final JSONObject existing, final String requestHash) throws ServiceException {
        if (!requestHash.equals(existing.optString(ExternalPointRequest.REQUEST_HASH))) {
            throw new ServiceException("请求编号已使用。");
        }
        final String transferId = existing.optString(ExternalPointRequest.TRANSFER_ID);
        if (transferId.isEmpty()) {
            throw new ServiceException("请求处理中，请稍后重试。");
        }
        return transferId;
    }

    private String requestHash(final String userId, final int point, final String memo,
                               final PointtransferSource source) {
        final StringBuilder canonical = new StringBuilder();
        appendCanonical(canonical, userId);
        appendCanonical(canonical, String.valueOf(point));
        appendCanonical(canonical, memo);
        appendCanonical(canonical, source.appId());
        appendCanonical(canonical, source.appName());
        appendCanonical(canonical, source.scene());
        return DigestUtils.sha256Hex(canonical.toString());
    }

    private void appendCanonical(final StringBuilder canonical, final String value) {
        canonical.append(value.length()).append(':').append(value);
    }
}
