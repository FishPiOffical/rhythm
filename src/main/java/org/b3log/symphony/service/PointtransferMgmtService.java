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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.PointtransferRedcRepository;
import org.b3log.symphony.repository.PointtransferRepository;
import org.b3log.symphony.repository.UserRepository;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pointtransfer management service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.2.1.7, Jun 6, 2019
 * @since 1.3.0
 */
@Service
public class PointtransferMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(PointtransferMgmtService.class);

    /**
     * Per-pair locks keyed by sorted fromId+toId to serialize transfers between the same pair of users.
     */
    private static final ConcurrentHashMap<String, ReentrantLock> TRANSFER_LOCKS = new ConcurrentHashMap<>();

    /**
     * Builds a lock key for a pair of user ids, ensuring A->B and B->A share the same lock.
     *
     * @param fromId from user id
     * @param toId   to user id
     * @return lock key
     */
    private static String buildLockKey(final String fromId, final String toId) {
        if (fromId == null || toId == null) {
            // Fall back to a simple key if null, though normal flow should avoid null ids
            return String.valueOf(fromId) + "->" + String.valueOf(toId);
        }
        if (fromId.compareTo(toId) <= 0) {
            return fromId + "->" + toId;
        } else {
            return toId + "->" + fromId;
        }
    }

    /**
     * Gets (or creates) a lock for the given pair of user ids.
     *
     * @param fromId from user id
     * @param toId   to user id
     * @return the lock associated with this user pair
     */
    private static ReentrantLock getTransferLock(final String fromId, final String toId) {
        final String key = buildLockKey(fromId, toId);
        return TRANSFER_LOCKS.computeIfAbsent(key, k -> new ReentrantLock());
    }

    /**
     * Pointtransfer repository.
     */
    @Inject
    private PointtransferRepository pointtransferRepository;

    /**
     * User repository.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * Transfers point from the specified from id to the specified to id with type, sum, data id and time.
     *
     * @param fromId the specified from id, may be system "sys"
     * @param toId   the specified to id, may be system "sys"
     * @param type   the specified type
     * @param sum    the specified sum
     * @param dataId the specified data id
     * @param time   the specified time
     * @param memo   the specified memo
     * @return transfer record id, returns {@code null} if transfer failed
     */
    public String transfer(final String fromId, final String toId, final int type, final int sum,
                           final String dataId, final long time, final String memo) {
        if (StringUtils.equals(fromId, toId)) {
            LOGGER.log(Level.WARN, "The from id is equal to the to id [" + fromId + "]");
            return null;
        }

        // 按 fromId+toId 维度加锁
        final ReentrantLock lock = getTransferLock(fromId, toId);
        lock.lock();

        final Transaction transaction = pointtransferRepository.beginTransaction();

        try {
            int fromBalance = 0;
            if (!Pointtransfer.ID_C_SYS.equals(fromId)) {
                final JSONObject fromUser = userRepository.get(fromId);
                fromBalance = fromUser.optInt(UserExt.USER_POINT) - sum;
                if (type != Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT) {
                    if (fromBalance < 0) {
                        throw new Exception("Insufficient balance");
                    }
                }

                List<Integer> canIncludeArray = new ArrayList<>();
                Collections.addAll(canIncludeArray, 1, 2, 3, 15, 19, 20, 22, 23, 24, 26, 30, 32, 34, 36, 37, 45, 48, 49, 50);
                if (canIncludeArray.contains(type)) {
                    fromUser.put(UserExt.USER_USED_POINT, fromUser.optInt(UserExt.USER_USED_POINT) + sum);
                }
                fromUser.put(UserExt.USER_POINT, fromBalance);
                userRepository.update(fromId, fromUser, UserExt.USER_POINT, UserExt.USER_USED_POINT);
            }

            int toBalance = 0;
            if (!Pointtransfer.ID_C_SYS.equals(toId)) {
                final JSONObject toUser = userRepository.get(toId);
                toBalance = toUser.optInt(UserExt.USER_POINT) + sum;
                toUser.put(UserExt.USER_POINT, toBalance);
                userRepository.update(toId, toUser, UserExt.USER_POINT);
            }

            final JSONObject pointtransfer = new JSONObject();
            pointtransfer.put(Pointtransfer.FROM_ID, fromId);
            pointtransfer.put(Pointtransfer.TO_ID, toId);
            pointtransfer.put(Pointtransfer.SUM, sum);
            pointtransfer.put(Pointtransfer.FROM_BALANCE, fromBalance);
            pointtransfer.put(Pointtransfer.TO_BALANCE, toBalance);
            pointtransfer.put(Pointtransfer.TIME, time);
            pointtransfer.put(Pointtransfer.TYPE, type);
            pointtransfer.put(Pointtransfer.DATA_ID, dataId);
            pointtransfer.put(Pointtransfer.MEMO, memo);

            final String ret = pointtransferRepository.add(pointtransfer);

            PointtransferRedcRepository.addRecordAsync(fromId, ret);
            PointtransferRedcRepository.addRecordAsync(toId, ret);

            transaction.commit();

            return ret;
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.ERROR, "Transfer [fromId=" + fromId + ", toId=" + toId + ", sum=" + sum +
                    ", type=" + type + ", dataId=" + dataId + ", memo=" + memo + "] failed", e);

            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Transfers point without starting/committing a transaction, so it can be used within an outer transaction.
     *
     * @param fromId the specified from id, may be system "sys"
     * @param toId   the specified to id, may be system "sys"
     * @param type   the specified type
     * @param sum    the specified sum
     * @param dataId the specified data id
     * @param time   the specified time
     * @param memo   the specified memo
     * @return transfer record id, returns {@code null} if transfer failed
     */
    public String transferInCurrentTransaction(final String fromId, final String toId, final int type, final int sum,
                                               final String dataId, final long time, final String memo) {
        if (StringUtils.equals(fromId, toId)) {
            LOGGER.log(Level.WARN, "The from id is equal to the to id [" + fromId + "]");
            return null;
        }

        final ReentrantLock lock = getTransferLock(fromId, toId);
        lock.lock();
        try {
            int fromBalance = 0;
            if (!Pointtransfer.ID_C_SYS.equals(fromId)) {
                final JSONObject fromUser = userRepository.get(fromId);
                fromBalance = fromUser.optInt(UserExt.USER_POINT) - sum;
                if (fromBalance < 0) {
                    throw new Exception("Insufficient balance");
                }

                List<Integer> canIncludeArray = new ArrayList<>();
                Collections.addAll(canIncludeArray, 1, 2, 3, 15, 19, 20, 22, 23, 24, 26, 30, 32, 34, 36, 37, 45, 48, 49, 50);
                if (canIncludeArray.contains(type)) {
                    fromUser.put(UserExt.USER_USED_POINT, fromUser.optInt(UserExt.USER_USED_POINT) + sum);
                }
                fromUser.put(UserExt.USER_POINT, fromBalance);
                userRepository.update(fromId, fromUser, UserExt.USER_POINT, UserExt.USER_USED_POINT);
            }

            int toBalance = 0;
            if (!Pointtransfer.ID_C_SYS.equals(toId)) {
                final JSONObject toUser = userRepository.get(toId);
                toBalance = toUser.optInt(UserExt.USER_POINT) + sum;
                toUser.put(UserExt.USER_POINT, toBalance);
                userRepository.update(toId, toUser, UserExt.USER_POINT);
            }

            final JSONObject pointtransfer = new JSONObject();
            pointtransfer.put(Pointtransfer.FROM_ID, fromId);
            pointtransfer.put(Pointtransfer.TO_ID, toId);
            pointtransfer.put(Pointtransfer.SUM, sum);
            pointtransfer.put(Pointtransfer.FROM_BALANCE, fromBalance);
            pointtransfer.put(Pointtransfer.TO_BALANCE, toBalance);
            pointtransfer.put(Pointtransfer.TIME, time);
            pointtransfer.put(Pointtransfer.TYPE, type);
            pointtransfer.put(Pointtransfer.DATA_ID, dataId);
            pointtransfer.put(Pointtransfer.MEMO, memo);

            final String ret = pointtransferRepository.add(pointtransfer);

            PointtransferRedcRepository.addRecordAsync(fromId, ret);
            PointtransferRedcRepository.addRecordAsync(toId, ret);

            return ret;
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Transfer (in current tx) [fromId=" + fromId + ", toId=" + toId + ", sum=" + sum +
                    ", type=" + type + ", dataId=" + dataId + ", memo=" + memo + "] failed", e);
            return null;
        } finally {
            lock.unlock();
        }
    }
}
