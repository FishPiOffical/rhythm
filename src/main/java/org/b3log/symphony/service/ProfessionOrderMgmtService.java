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
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.repository.ProfessionRepository;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 原子保存职业展示顺序。 */
@Service
public class ProfessionOrderMgmtService {

    @Inject private ProfessionRepository professionRepository;

    public void reorder(final List<String> ids) throws RepositoryException {
        final Transaction transaction = professionRepository.beginTransaction();
        try {
            final Map<String, JSONObject> professions = professions();
            validate(ids, professions.keySet());
            final long now = System.currentTimeMillis();
            for (int index = 0; index < ids.size(); index++) {
                final JSONObject profession = professions.get(ids.get(index));
                profession.put(Profession.SORT_ORDER, index).put(Profession.UPDATED_AT, now);
                professionRepository.update(ids.get(index), profession);
            }
            transaction.commit();
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw e instanceof RepositoryException error ? error : new RepositoryException(e);
        }
    }

    private Map<String, JSONObject> professions() throws RepositoryException {
        final Map<String, JSONObject> result = new HashMap<>();
        for (final JSONObject profession : professionRepository.getList(new Query())) {
            result.put(profession.getString(Keys.OBJECT_ID), profession);
        }
        return result;
    }

    private void validate(final List<String> ids, final Set<String> professionIds) {
        if (ids.size() != professionIds.size() || new HashSet<>(ids).size() != ids.size()
                || !new HashSet<>(ids).equals(professionIds)) {
            throw new IllegalArgumentException("职业顺序与当前职业库不一致，请刷新后重试");
        }
    }
}
