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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Transactions;

@Service
public class AdminSalaryService {
    @Inject private UserRepository users;
    @Inject private PointtransferMgmtService points;
    @Inject private NotificationMgmtService notifications;

    public String pay(final String recipient, final String expectedName, final int amount, final String memo) throws Exception {
        return points.executeWithTransferLock(Pointtransfer.ID_C_SYS, recipient, () -> {
            final var tx = ApiV3Transactions.strict(users.beginTransaction());
            try {
                if (users.select("SELECT oId FROM `" + users.getName()
                        + "` WHERE oId=? AND userName=? FOR UPDATE", recipient, expectedName).isEmpty()) {
                    throw new org.b3log.symphony.util.ApiV3Exception(409, "salary_target_changed", "目标账号已变化");
                }
                final String transferId = payInCurrentTransaction(recipient, amount, memo);
                tx.commit();
                afterCommit(recipient, transferId);
                return transferId;
            } finally {
                if (tx.isActive()) tx.rollback();
            }
        });
    }

    public String payInCurrentTransaction(final String recipient, final int amount, final String memo)
            throws RepositoryException {
        final String transferId = points.transferAdminInCurrentTransaction(Pointtransfer.ID_C_SYS, recipient,
                Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT, amount, recipient, System.currentTimeMillis(), memo);
        if (StringUtils.isBlank(transferId)) throw new IllegalStateException("工资流水生成失败");
        notifications.addPointTransferNotificationInCurrentTransaction(recipient, transferId);
        return transferId;
    }

    public void afterCommit(final String recipient, final String transferId) {
        try {
            users.evictSettingsCaches(recipient);
            points.indexTransferAsync(Pointtransfer.ID_C_SYS, recipient, transferId);
            notifications.publishRefreshNotification(recipient);
        } catch (final RuntimeException e) {
            LogManager.getLogger(AdminSalaryService.class).warn("工资已入账，提交后发布失败，transferId={}", transferId, e);
        }
    }
}
