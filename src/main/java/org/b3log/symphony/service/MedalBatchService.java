/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Copyright (C) 2012-present, b3log.org
 */
package org.b3log.symphony.service;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.MedalRepository;
import org.b3log.symphony.repository.UserMedalRepository;
import org.b3log.symphony.repository.UserRepository;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 勋章批量查询与发放服务。
 */
@Service
public class MedalBatchService {

    private static final Logger LOGGER = LogManager.getLogger(MedalBatchService.class);

    @Inject
    private MedalRepository medalRepository;

    @Inject
    private UserMedalRepository userMedalRepository;

    @Inject
    private UserRepository userRepository;

    public List<JSONObject> getHoldStatuses(final List<String> userIds, final String medalId)
            throws ServiceException {
        validateTargets(userIds, medalId);
        final long now = System.currentTimeMillis();
        final List<JSONObject> result = new ArrayList<>(userIds.size());
        try {
            for (final String userId : userIds) {
                result.add(toHoldStatus(userId, findUserMedal(userId, medalId), now));
            }
            return result;
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to query medal hold statuses", e);
            throw new ServiceException("查询勋章持有状态失败");
        }
    }

    public void grantBatch(final GrantRequest request) throws ServiceException {
        validateTargets(request.userIds(), request.grant().medalId());
        final Transaction transaction = userMedalRepository.beginTransaction();
        try {
            for (final String userId : request.userIds()) {
                grantOne(userId, request);
            }
            transaction.commit();
        } catch (final RepositoryException | RuntimeException e) {
            rollback(transaction);
            LOGGER.log(Level.ERROR, "Failed to grant medal batch", e);
            throw new ServiceException("批量发放勋章失败");
        }
    }

    private void validateTargets(final List<String> userIds, final String medalId) throws ServiceException {
        try {
            if (null == findMedal(medalId)) {
                throw new ServiceException("勋章不存在");
            }
            for (final String userId : userIds) {
                if (null == userRepository.get(userId)) {
                    throw new ServiceException("用户不存在：" + userId);
                }
            }
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to validate medal batch targets", e);
            throw new ServiceException("校验勋章或用户失败");
        }
    }

    private JSONObject findMedal(final String medalId) throws RepositoryException {
        final Query query = new Query()
                .setFilter(new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId));
        return medalRepository.getFirst(query);
    }

    private JSONObject findUserMedal(final String userId, final String medalId) throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter("user_id", FilterOperator.EQUAL, userId),
                new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId)));
        return userMedalRepository.getFirst(query);
    }

    private JSONObject toHoldStatus(final String userId, final JSONObject userMedal, final long now) {
        final JSONObject status = new JSONObject()
                .put("userId", userId)
                .put("held", false)
                .put("expired", false)
                .put("expireTime", 0L)
                .put("permanent", false)
                .put("data", "");
        if (null == userMedal || userMedal.length() == 0) {
            return status;
        }
        final long expireTime = userMedal.optLong("expire_time", 0L);
        if (expireTime > 0L && expireTime <= now) {
            return status.put("expired", true).put("expireTime", expireTime);
        }
        return status.put("held", true)
                .put("expireTime", expireTime)
                .put("permanent", expireTime == 0L)
                .put("data", userMedal.optString("data", ""));
    }

    private void grantOne(final String userId, final GrantRequest request) throws RepositoryException {
        final MedalGrant grant = request.grant();
        final JSONObject existing = findUserMedal(userId, grant.medalId());
        if (null != existing && existing.length() > 0) {
            existing.put("expire_time", grant.expireTime());
            existing.put("data", grant.data());
            userMedalRepository.update(existing.optString("oId"), existing);
            return;
        }
        userMedalRepository.add(new JSONObject()
                .put("user_id", userId)
                .put("medal_id", grant.medalId())
                .put("data", grant.data())
                .put("expire_time", grant.expireTime())
                .put("display", true)
                .put("display_order", nextDisplayOrder(userId)));
    }

    private int nextDisplayOrder(final String userId) throws RepositoryException {
        final Query query = new Query()
                .setFilter(new PropertyFilter("user_id", FilterOperator.EQUAL, userId));
        int maxOrder = 0;
        for (final JSONObject userMedal : userMedalRepository.getList(query)) {
            maxOrder = Math.max(maxOrder, userMedal.optInt("display_order", 0));
        }
        return maxOrder + 1;
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    public record GrantRequest(List<String> userIds, MedalGrant grant) {
    }

    public record MedalGrant(String medalId, long expireTime, String data) {
    }
}
