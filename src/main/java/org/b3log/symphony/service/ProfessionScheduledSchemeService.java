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
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.model.ProfessionLevelScheme;
import org.b3log.symphony.repository.ProfessionLevelSchemeRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.json.JSONObject;

/** 激活到期的职业等级方案。 */
@Service
public class ProfessionScheduledSchemeService {

    @Inject private ProfessionRepository professionRepository;
    @Inject private ProfessionLevelSchemeRepository schemeRepository;

    public void activateDue() throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(
                ProfessionLevelScheme.STATUS, FilterOperator.EQUAL, "SCHEDULED"))
                .addSort(ProfessionLevelScheme.EFFECTIVE_FROM, SortDirection.ASCENDING);
        final long now = System.currentTimeMillis();
        for (final JSONObject scheme : schemeRepository.getList(query)) {
            if (scheme.getLong(ProfessionLevelScheme.EFFECTIVE_FROM) <= now) {
                activate(scheme.getString(Keys.OBJECT_ID), now);
            }
        }
    }

    private void activate(final String schemeId, final long now) throws RepositoryException {
        final Transaction transaction = schemeRepository.beginTransaction();
        try {
            final JSONObject scheme = schemeRepository.getForUpdate(schemeId);
            if (!isDue(scheme, now)) {
                transaction.commit();
                return;
            }
            final JSONObject profession = requireMatchingProfession(scheme);
            retireCurrent(profession.optString(Profession.CURRENT_LEVEL_SCHEME_ID), now);
            scheme.put(ProfessionLevelScheme.STATUS, "PUBLISHED");
            schemeRepository.update(schemeId, scheme);
            profession.put(Profession.CURRENT_LEVEL_SCHEME_ID, schemeId);
            profession.put(Profession.UPDATED_AT, now);
            professionRepository.update(profession.getString(Keys.OBJECT_ID), profession);
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            throw repositoryException(e);
        }
    }

    private boolean isDue(final JSONObject scheme, final long now) {
        return null != scheme && "SCHEDULED".equals(scheme.optString(ProfessionLevelScheme.STATUS))
                && scheme.getLong(ProfessionLevelScheme.EFFECTIVE_FROM) <= now;
    }

    private JSONObject requireMatchingProfession(final JSONObject scheme) throws RepositoryException {
        final JSONObject profession = professionRepository.get(scheme.getString(ProfessionLevelScheme.PROFESSION_ID));
        if (null == profession || !scheme.getString(ProfessionLevelScheme.PROFESSION_REVISION_ID)
                .equals(profession.optString(Profession.CURRENT_REVISION_ID))) {
            throw new RepositoryException("定时等级方案与当前职业版本不匹配");
        }
        return profession;
    }

    private void retireCurrent(final String schemeId, final long now) throws RepositoryException {
        if (schemeId.isBlank()) {
            return;
        }
        final JSONObject current = schemeRepository.getForUpdate(schemeId);
        current.put(ProfessionLevelScheme.STATUS, "RETIRED");
        current.put(ProfessionLevelScheme.EFFECTIVE_TO, now);
        schemeRepository.update(schemeId, current);
    }

    private RepositoryException repositoryException(final Exception exception) {
        return exception instanceof RepositoryException result ? result : new RepositoryException(exception);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
