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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.UserOnlineStatus;
import org.b3log.symphony.util.UserOnlineSynchronizer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 定时发现内存状态变化；数据库慢查询只占用该用户的同步任务。 */
@Service
public class UserOnlineService {
    private static final Logger LOGGER = LogManager.getLogger(UserOnlineService.class);
    @Inject
    private UserMgmtService users;

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("user-online-timer").factory());
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private UserOnlineSynchronizer synchronizer;

    public synchronized void start() {
        if (synchronizer != null) {
            return;
        }
        synchronizer = new UserOnlineSynchronizer(UserOnlineStatus.tracker(), users::persistOnlineState);
        timer.scheduleWithFixedDelay(() -> {
            try {
                flush();
            } catch (final Exception e) {
                LOGGER.error("Online synchronization scheduling failed", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void flush() {
        for (final var snapshot : UserOnlineStatus.tracker().pending()) {
            final String userId = snapshot.userId();
            if (!running.add(userId)) {
                continue;
            }
            try {
                workers.execute(() -> {
                    try {
                        synchronizer.synchronize(userId);
                    } catch (final Exception e) {
                        LOGGER.warn("Online state/time synchronization failed [userId={}]", userId, e);
                    } finally {
                        try {
                            releaseResources();
                        } finally {
                            running.remove(userId);
                        }
                    }
                });
            } catch (final RuntimeException e) {
                running.remove(userId);
                throw e;
            }
        }
    }

    public void stop() {
        if (synchronizer == null) {
            return;
        }
        timer.shutdown();
        try {
            while (!timer.awaitTermination(1, TimeUnit.SECONDS)) {
                // 先等待调度结束，避免停机刷新与调度投递竞争。
            }
            UserOnlineStatus.tracker().stop();
            flush();
            workers.shutdown();
            while (!workers.awaitTermination(1, TimeUnit.SECONDS)) {
                // 数据库关闭前，等待正在提交的每用户任务。
            }
            // 原任务可能在停机前取得快照，完成后再同步冻结产生的最终离线状态。
            for (final var snapshot : UserOnlineStatus.tracker().pending()) {
                try {
                    synchronizer.synchronize(snapshot.userId());
                } catch (final Exception e) {
                    LOGGER.error("Final online settlement failed [userId={}]", snapshot.userId(), e);
                } finally {
                    releaseResources();
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while settling online time", e);
        }
    }

    private void releaseResources() {
        try {
            ApiV3Transactions.releaseThreadResources();
        } catch (final RuntimeException e) {
            LOGGER.error("Online synchronization resource release failed", e);
        }
    }
}
