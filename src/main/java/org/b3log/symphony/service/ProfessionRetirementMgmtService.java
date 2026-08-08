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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.model.ProfessionRevision;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.b3log.symphony.repository.UserProfessionPrivacyRepository;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 停用职业并向受影响用户发送可追踪的系统通知。 */
@Service
public class ProfessionRetirementMgmtService {

    @Inject private ProfessionRepository professionRepository;
    @Inject private ProfessionRevisionRepository revisionRepository;
    @Inject private UserProfessionRepository userProfessionRepository;
    @Inject private UserProfessionPrivacyRepository privacyRepository;
    @Inject private ProfessionDefinitionStateService definitionStateService;
    @Inject private NotificationMgmtService notificationMgmtService;

    public void retire(final String professionId, final String operatorUserId) throws RepositoryException {
        validateId(professionId, "职业");
        validateId(operatorUserId, "操作用户");
        final Transaction transaction = professionRepository.beginTransaction();
        final List<String> userIds;
        try {
            final JSONObject profession = professionRepository.getForUpdate(professionId);
            if (null == profession) {
                throw new RepositoryException("职业不存在");
            }
            if ("RETIRED".equals(profession.optString(Profession.STATUS))) {
                transaction.commit();
                return;
            }
            userIds = affectedUsers(professionId);
            retire(profession, userIds, System.currentTimeMillis());
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            throw exception(e);
        }
        userIds.forEach(notificationMgmtService::publishRefreshNotification);
    }

    private void retire(final JSONObject profession, final List<String> userIds, final long now) throws Exception {
        addRetirementNotifications(userIds, professionName(profession));
        definitionStateService.retireRevision(profession.optString(Profession.CURRENT_REVISION_ID), now);
        definitionStateService.retireScheme(profession.optString(Profession.CURRENT_LEVEL_SCHEME_ID), now);
        profession.put(Profession.STATUS, "RETIRED");
        profession.put(Profession.UPDATED_AT, now);
        professionRepository.update(profession.getString(Keys.OBJECT_ID), profession);
    }

    private List<String> affectedUsers(final String professionId) throws RepositoryException {
        final Set<String> userIds = new LinkedHashSet<>();
        userProfessionRepository.getByProfessionForUpdate(professionId)
                .forEach(item -> userIds.add(item.getString("userId")));
        privacyRepository.getByPrimaryProfessionForUpdate(professionId)
                .forEach(item -> userIds.add(item.getString("userId")));
        return List.copyOf(userIds);
    }

    private void addRetirementNotifications(final List<String> userIds, final String professionName)
            throws Exception {
        final String content = "职业「" + professionName + "」已停用。历史经验与贡献记录会保留；可在职业设置重新选择主职业。";
        for (final String userId : userIds) {
            notificationMgmtService.addSysAnnounceCustomNotificationInCurrentTransaction(content, userId);
        }
    }

    private String professionName(final JSONObject profession) throws RepositoryException {
        final String revisionId = profession.optString(Profession.CURRENT_REVISION_ID);
        if (revisionId.isBlank()) {
            return profession.getString(Profession.CODE);
        }
        final JSONObject revision = revisionRepository.get(revisionId);
        return null == revision ? profession.getString(Profession.CODE)
                : revision.getString(ProfessionRevision.DISPLAY_NAME);
    }

    private void validateId(final String value, final String name) {
        if (null == value || !value.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException(name + "标识不合法");
        }
    }

    private RepositoryException exception(final Exception error) {
        return error instanceof RepositoryException result ? result : new RepositoryException(error);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
