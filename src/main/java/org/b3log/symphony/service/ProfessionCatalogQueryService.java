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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.model.ProfessionLevel;
import org.b3log.symphony.model.ProfessionRevision;
import org.b3log.symphony.model.UserProfession;
import org.b3log.symphony.repository.ProfessionLevelRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

/** 对外输出已发布职业和用户职业的安全展示快照。 */
@Service
public class ProfessionCatalogQueryService {

    @Inject private ProfessionRepository professionRepository;
    @Inject private ProfessionRevisionRepository revisionRepository;
    @Inject private ProfessionLevelRepository levelRepository;
    @Inject private ProfessionDefinitionQueryService definitionQueryService;

    public JSONArray getPublished() throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(Profession.STATUS, FilterOperator.EQUAL, "PUBLISHED"))
                .addSort(Profession.SORT_ORDER, SortDirection.ASCENDING);
        final JSONArray result = new JSONArray();
        for (final JSONObject profession : professionRepository.getList(query)) {
            final JSONObject view = definition(profession, profession.optString(Profession.CURRENT_REVISION_ID));
            if (null != view) {
                result.put(view);
            }
        }
        return result;
    }

    public JSONObject getProgressView(final JSONObject progress) throws RepositoryException {
        return getProgressView(progress, "professionPage", false);
    }

    public JSONObject getProgressView(final JSONObject progress, final String positionCode, final boolean darkMode)
            throws RepositoryException {
        final JSONObject profession = professionRepository.get(progress.getString(UserProfession.PROFESSION_ID));
        final JSONObject result = definition(profession, progress.getString(UserProfession.PROFESSION_REVISION_ID));
        if (null == result) {
            throw new RepositoryException("职业展示版本不存在");
        }
        result.put("levelCode", progress.optString(UserProfession.CURRENT_LEVEL_CODE));
        result.put("totalExperience", progress.optLong(UserProfession.TOTAL_EXPERIENCE));
        appendLevel(result, progress, positionCode, darkMode);
        return result;
    }

    /** 返回已发布职业在零经验时的初始等级快照。 */
    public JSONObject getZeroProgressView(final String professionId, final String positionCode, final boolean darkMode)
            throws RepositoryException {
        return getProgressView(zeroProgress(professionId), positionCode, darkMode);
    }

    /** 返回已发布职业的零经验汇总，用于尚未产生职业效果的已选主职业。 */
    public JSONObject zeroProgress(final String professionId) throws RepositoryException {
        final JSONObject profession = requirePublished(professionId);
        final String schemeId = profession.getString(Profession.CURRENT_LEVEL_SCHEME_ID);
        final JSONObject initialLevel = initialLevel(schemeId);
        if (null == initialLevel) {
            throw new RepositoryException("职业等级方案为空");
        }
        return new JSONObject().put(UserProfession.PROFESSION_ID, professionId)
                .put(UserProfession.PROFESSION_REVISION_ID, profession.getString(Profession.CURRENT_REVISION_ID))
                .put(UserProfession.LEVEL_SCHEME_ID, schemeId)
                .put(UserProfession.CURRENT_LEVEL_CODE, initialLevel.getString(ProfessionLevel.CODE))
                .put(UserProfession.TOTAL_EXPERIENCE, 0L);
    }

    public JSONArray getPublishedLevels(final String professionId) throws RepositoryException {
        final JSONObject profession = requirePublished(professionId);
        return definitionQueryService.getLevels(profession.getString(Profession.CURRENT_LEVEL_SCHEME_ID));
    }

    public boolean isPublished(final String professionId) throws RepositoryException {
        final JSONObject profession = professionRepository.get(professionId);
        return null != profession && "PUBLISHED".equals(profession.optString(Profession.STATUS));
    }

    private JSONObject definition(final JSONObject profession, final String revisionId) throws RepositoryException {
        if (null == profession || revisionId.isBlank()) {
            return null;
        }
        final JSONObject revision = revisionRepository.get(revisionId);
        if (null == revision || !profession.getString(Keys.OBJECT_ID).equals(revision.optString(ProfessionRevision.PROFESSION_ID))) {
            return null;
        }
        return new JSONObject()
                .put("professionId", profession.getString(Keys.OBJECT_ID))
                .put("professionCode", profession.getString(Profession.CODE))
                .put("displayName", revision.getString(ProfessionRevision.DISPLAY_NAME))
                .put("shortName", revision.optString(ProfessionRevision.SHORT_NAME))
                .put("description", revision.optString(ProfessionRevision.DESCRIPTION))
                .put("presentation", new JSONObject(revision.getString(ProfessionRevision.DEFAULT_PRESENTATION_JSON)));
    }

    private JSONObject requirePublished(final String professionId) throws RepositoryException {
        final JSONObject profession = professionRepository.get(professionId);
        if (null == profession || !"PUBLISHED".equals(profession.optString(Profession.STATUS))
                || profession.optString(Profession.CURRENT_REVISION_ID).isBlank()
                || profession.optString(Profession.CURRENT_LEVEL_SCHEME_ID).isBlank()) {
            throw new IllegalArgumentException("职业不存在或已停用");
        }
        return profession;
    }

    private JSONObject initialLevel(final String schemeId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(ProfessionLevel.SCHEME_ID,
                FilterOperator.EQUAL, schemeId)).addSort(ProfessionLevel.SORT_ORDER, SortDirection.ASCENDING);
        return levelRepository.getFirst(query);
    }

    private void appendLevel(final JSONObject result, final JSONObject progress, final String positionCode,
                             final boolean darkMode) throws RepositoryException {
        final String code = progress.optString(UserProfession.CURRENT_LEVEL_CODE);
        if (code.isBlank()) {
            return;
        }
        final Query query = new Query().setFilter(new PropertyFilter(ProfessionLevel.SCHEME_ID, FilterOperator.EQUAL,
                progress.getString(UserProfession.LEVEL_SCHEME_ID)));
        for (final JSONObject level : levelRepository.getList(query)) {
            if (code.equals(level.optString(ProfessionLevel.CODE))) {
                result.put("levelName", level.getString(ProfessionLevel.DISPLAY_NAME));
                result.put("levelDescription", level.optString(ProfessionLevel.DESCRIPTION));
                result.put("requiredExperience", level.getLong(ProfessionLevel.REQUIRED_TOTAL_EXPERIENCE));
                result.put("presentation", definitionQueryService.getResolvedPresentation(
                        new ProfessionDefinitionQueryService.PresentationQuery(
                                progress.getString(UserProfession.PROFESSION_REVISION_ID),
                                progress.getString(UserProfession.LEVEL_SCHEME_ID), code, positionCode, darkMode)));
                return;
            }
        }
    }
}
