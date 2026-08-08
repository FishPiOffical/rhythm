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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 基于不可变等级方案缓存执行等级计算。 */
@Service
public class ProfessionLevelResolver {

    private final ConcurrentMap<String, List<LevelSnapshot>> cache = new ConcurrentHashMap<>();

    @Inject
    private ProfessionDefinitionQueryService definitionQueryService;

    public JSONObject resolve(final String schemeId, final long experience) throws RepositoryException {
        final List<LevelSnapshot> levels = levels(schemeId);
        int low = 0;
        int high = levels.size() - 1;
        LevelSnapshot result = levels.getFirst();
        while (low <= high) {
            final int middle = (low + high) >>> 1;
            final LevelSnapshot candidate = levels.get(middle);
            if (candidate.requiredExperience() <= experience) {
                result = candidate;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return result.value();
    }

    public List<JSONObject> crossedLevels(final String schemeId, final long before, final long after)
            throws RepositoryException {
        if (after <= before) {
            return List.of();
        }
        final List<JSONObject> result = new ArrayList<>();
        for (final LevelSnapshot level : levels(schemeId)) {
            final long threshold = level.requiredExperience();
            if (threshold > before && threshold <= after) {
                result.add(level.value());
            }
        }
        return List.copyOf(result);
    }

    public JSONObject findByCode(final String schemeId, final String levelCode) throws RepositoryException {
        for (final LevelSnapshot level : levels(schemeId)) {
            if (levelCode.equals(level.levelCode())) {
                return level.value();
            }
        }
        return null;
    }

    private List<LevelSnapshot> levels(final String schemeId) throws RepositoryException {
        final List<LevelSnapshot> cached = cache.get(schemeId);
        if (null != cached) {
            return cached;
        }
        final JSONArray definitions = definitionQueryService.getLevels(schemeId);
        if (definitions.isEmpty()) {
            throw new IllegalStateException("等级方案没有等级");
        }
        final List<LevelSnapshot> loaded = new ArrayList<>();
        for (final Object item : definitions) {
            final JSONObject level = (JSONObject) item;
            loaded.add(new LevelSnapshot(level.getString("levelCode"),
                    level.getLong("requiredTotalExperience"), level.toString()));
        }
        loaded.sort(Comparator.comparingLong(LevelSnapshot::requiredExperience));
        final List<LevelSnapshot> immutable = List.copyOf(loaded);
        final List<LevelSnapshot> existing = cache.putIfAbsent(schemeId, immutable);
        return null == existing ? immutable : existing;
    }

    private record LevelSnapshot(String levelCode, long requiredExperience, String json) {
        private JSONObject value() {
            return new JSONObject(json);
        }
    }
}
