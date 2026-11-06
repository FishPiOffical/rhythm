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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.repository.NotificationRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;

/** Notification read/delete state only. Caller owns the transaction and post-commit refresh. */
@Service
public class NotificationStateMgmtService {
    @Inject private NotificationRepository notificationRepository;

    public long markReadInCurrentTransaction(final String userId, final String id) throws RepositoryException {
        requireTransaction();
        final JSONObject row = owned(userId, id);
        if (row.optBoolean(Notification.NOTIFICATION_HAS_READ)) return 0;
        row.put(Notification.NOTIFICATION_HAS_READ, true);
        notificationRepository.update(id, row, Notification.NOTIFICATION_HAS_READ);
        return 1;
    }

    /** Reply publication marks only this actor's notifications about the original comment. No refresh here. */
    public long markDataReadInCurrentTransaction(final String userId, final String dataId) throws RepositoryException {
        requireTransaction();
        final String cutoff = notificationRepository.unreadDataCutoffForUpdate(userId, dataId);
        if (null == cutoff) return 0;
        long changed = 0;
        while (true) {
            final var rows = notificationRepository.unreadDataBatchForUpdate(userId, dataId, cutoff);
            if (rows.isEmpty()) return changed;
            for (final var row : rows) {
                row.put(Notification.NOTIFICATION_HAS_READ, true);
                notificationRepository.update(row.optString("oId"), row, Notification.NOTIFICATION_HAS_READ);
                changed++;
            }
        }
    }

    public long removeInCurrentTransaction(final String userId, final String id) throws RepositoryException {
        requireTransaction();
        owned(userId, id);
        notificationRepository.remove(id);
        return 1;
    }

    public long bulkInCurrentTransaction(final String userId, final String category, final boolean remove) throws RepositoryException {
        requireTransaction();
        final String cutoff = notificationRepository.getV3Cutoff(userId, category, !remove);
        if (null == cutoff) return 0;
        long changed = 0;
        while (true) {
            final var rows = notificationRepository.getV3BatchForUpdate(userId, category, !remove, cutoff);
            if (rows.isEmpty()) return changed;
            for (final JSONObject row : rows) {
                final String id = row.optString(Keys.OBJECT_ID);
                if (remove) notificationRepository.remove(id);
                else {
                    row.put(Notification.NOTIFICATION_HAS_READ, true);
                    notificationRepository.update(id, row, Notification.NOTIFICATION_HAS_READ);
                }
                changed++;
            }
        }
    }

    private JSONObject owned(final String userId, final String id) throws RepositoryException {
        final JSONObject row = notificationRepository.getOwnedForUpdate(userId, id);
        if (null == row) throw new ApiV3Exception(404, "not_found", "通知不存在或不可访问");
        return row;
    }
    private void requireTransaction() {
        if (!notificationRepository.hasTransactionBegun()) throw new IllegalStateException("notification transaction required");
    }
}
