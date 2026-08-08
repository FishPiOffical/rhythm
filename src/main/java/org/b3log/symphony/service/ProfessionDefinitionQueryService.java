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
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ProfessionLevel;
import org.b3log.symphony.model.ProfessionLevelPresentation;
import org.b3log.symphony.model.ProfessionLevelReward;
import org.b3log.symphony.model.ProfessionRevision;
import org.b3log.symphony.repository.ProfessionLevelPresentationRepository;
import org.b3log.symphony.repository.ProfessionLevelRepository;
import org.b3log.symphony.repository.ProfessionLevelRewardRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

/** 按不可变等级方案读取等级、展示和奖励快照。 */
@Service
public class ProfessionDefinitionQueryService {

    @Inject private ProfessionLevelRepository levelRepository;
    @Inject private ProfessionLevelPresentationRepository presentationRepository;
    @Inject private ProfessionLevelRewardRepository rewardRepository;
    @Inject private ProfessionRevisionRepository revisionRepository;
    @Inject private ProfessionDefinitionRegistry registry;

    public JSONArray getLevels(final String schemeId) throws RepositoryException {
        validateId(schemeId, "等级方案");
        final Query query = new Query().setFilter(new PropertyFilter(ProfessionLevel.SCHEME_ID, FilterOperator.EQUAL, schemeId))
                .addSort(ProfessionLevel.SORT_ORDER, SortDirection.ASCENDING);
        final JSONArray result = new JSONArray();
        for (final JSONObject level : levelRepository.getList(query)) result.put(levelSnapshot(level));
        return result;
    }

    public JSONObject getResolvedPresentation(final PresentationQuery request) throws RepositoryException {
        if (null == request) {
            throw new IllegalArgumentException("展示查询不能为空");
        }
        validateId(request.professionRevisionId(), "职业版本");
        validateId(request.schemeId(), "等级方案");
        registry.validatePositionCode(request.positionCode());
        final JSONObject revision = revisionRepository.get(request.professionRevisionId());
        final JSONObject level = getLevel(request.schemeId(), request.levelCode());
        if (null == revision || null == level) {
            throw new RepositoryException("职业版本或等级不存在");
        }
        final JSONObject result = new JSONObject(revision.getString(ProfessionRevision.DEFAULT_PRESENTATION_JSON));
        merge(result, presentation(level.getString("oId"), "default", request.darkMode()));
        merge(result, presentation(level.getString("oId"), request.positionCode(), request.darkMode()));
        return result;
    }

    private JSONObject getLevel(final String schemeId, final String levelCode) throws RepositoryException {
        if (null == levelCode || levelCode.isBlank() || levelCode.length() > 64) {
            throw new IllegalArgumentException("等级编码不合法");
        }
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(ProfessionLevel.SCHEME_ID, FilterOperator.EQUAL, schemeId),
                new PropertyFilter(ProfessionLevel.CODE, FilterOperator.EQUAL, levelCode)));
        return levelRepository.getFirst(query);
    }

    private JSONObject presentation(final String levelId, final String positionCode, final boolean darkMode)
            throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(ProfessionLevelPresentation.LEVEL_ID, FilterOperator.EQUAL, levelId),
                new PropertyFilter(ProfessionLevelPresentation.POSITION_CODE, FilterOperator.EQUAL, positionCode)));
        final JSONObject value = presentationRepository.getFirst(query);
        if (null == value) {
            return null;
        }
        final String field = darkMode ? ProfessionLevelPresentation.DARK_CONFIG_JSON
                : ProfessionLevelPresentation.LIGHT_CONFIG_JSON;
        return new JSONObject(value.getString(field));
    }

    private void merge(final JSONObject target, final JSONObject source) {
        if (null == source) {
            return;
        }
        for (final String key : source.keySet()) {
            target.put(key, source.get(key));
        }
    }

    private JSONObject levelSnapshot(final JSONObject level) throws RepositoryException {
        final JSONObject result = new JSONObject(level.toString());
        result.put("presentations", presentations(level.getString("oId")));
        result.put("rewards", rewards(level.getString("oId")));
        return result;
    }

    private JSONArray presentations(final String levelId) throws RepositoryException {
        return array(presentationRepository.getList(new Query().setFilter(new PropertyFilter(
                ProfessionLevelPresentation.LEVEL_ID, FilterOperator.EQUAL, levelId))));
    }

    private JSONArray rewards(final String levelId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(ProfessionLevelReward.LEVEL_ID, FilterOperator.EQUAL, levelId))
                .addSort(ProfessionLevelReward.SORT_ORDER, SortDirection.ASCENDING);
        return array(rewardRepository.getList(query));
    }

    private JSONArray array(final Iterable<JSONObject> values) {
        final JSONArray result = new JSONArray();
        for (final JSONObject value : values) result.put(value);
        return result;
    }

    private void validateId(final String value, final String name) {
        if (null == value || !value.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException(name + "标识不合法");
        }
    }

    public record PresentationQuery(String professionRevisionId, String schemeId, String levelCode,
                                    String positionCode, boolean darkMode) {
    }
}
