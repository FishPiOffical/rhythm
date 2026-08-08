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
import org.b3log.latke.model.User;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.model.ProfessionAutomation;
import org.b3log.symphony.model.ProfessionAutomationRevision;
import org.b3log.symphony.model.ProfessionLevelScheme;
import org.b3log.symphony.model.ProfessionRevision;
import org.b3log.symphony.repository.ProfessionAutomationRepository;
import org.b3log.symphony.repository.ProfessionAutomationRevisionRepository;
import org.b3log.symphony.repository.ProfessionLevelRepository;
import org.b3log.symphony.repository.ProfessionLevelSchemeRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.b3log.latke.repository.Repository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 管理端读取职业定义、方案和自动化版本。 */
@Service
public class ProfessionAdminQueryService {

    @Inject private ProfessionRepository professionRepository;
    @Inject private ProfessionRevisionRepository revisionRepository;
    @Inject private ProfessionLevelSchemeRepository schemeRepository;
    @Inject private ProfessionAutomationRepository automationRepository;
    @Inject private ProfessionAutomationRevisionRepository automationRevisionRepository;
    @Inject private ProfessionLevelRepository levelRepository;
    @Inject private ProfessionDefinitionQueryService definitionQueryService;
    @Inject private UserQueryService userQueryService;

    public JSONArray catalog() throws RepositoryException {
        final Query query = new Query().addSort(Profession.SORT_ORDER, SortDirection.ASCENDING);
        final JSONArray result = new JSONArray();
        for (final JSONObject profession : professionRepository.getList(query)) {
            result.put(professionView(profession));
        }
        return result;
    }

    /** 只读取职业库所需摘要，明细由单职业接口按需读取。 */
    public JSONObject catalogSummary(final ProfessionCatalogSummaryQuery request) throws RepositoryException {
        final List<Object> parameters = summaryParameters(request);
        final String sql = summarySelect() + summaryWhere(request) + " ORDER BY " + summarySort(request.sort())
                + " LIMIT ? OFFSET ?";
        parameters.add(request.pageSize());
        parameters.add((request.page() - 1) * request.pageSize());
        final JSONArray items = array(professionRepository.select(sql, parameters.toArray()));
        return new JSONObject().put("items", items).put("total", summaryCount(request))
                .put("page", request.page()).put("pageSize", request.pageSize());
    }

    /** 读取单个职业的完整版本树，供编辑器和历史面板使用。 */
    public JSONObject detail(final String professionId) throws RepositoryException {
        final JSONObject profession = professionRepository.get(professionId);
        if (null == profession) {
            throw new IllegalArgumentException("职业不存在");
        }
        final JSONObject result = professionView(profession);
        result.put("operatorNames", operatorNames(result));
        return result;
    }

    private JSONObject professionView(final JSONObject profession) throws RepositoryException {
        final String professionId = profession.getString(Keys.OBJECT_ID);
        final JSONObject result = new JSONObject(profession.toString());
        result.put("revisions", queryBy(ProfessionRevision.PROFESSION_ID, professionId, revisionRepository));
        result.put("schemes", schemes(professionId));
        result.put("automations", automations(professionId));
        return result;
    }

    private JSONArray schemes(final String professionId) throws RepositoryException {
        final JSONArray values = queryBy(ProfessionLevelScheme.PROFESSION_ID, professionId, schemeRepository);
        for (final Object item : values) {
            final JSONObject scheme = (JSONObject) item;
            scheme.put("levels", definitionQueryService.getLevels(scheme.getString(Keys.OBJECT_ID)));
        }
        return values;
    }

    private JSONArray automations(final String professionId) throws RepositoryException {
        final JSONArray values = queryBy(ProfessionAutomation.PROFESSION_ID, professionId, automationRepository);
        for (final Object item : values) {
            final JSONObject automation = (JSONObject) item;
            automation.put("revisions", queryBy(ProfessionAutomationRevision.AUTOMATION_ID,
                    automation.getString(Keys.OBJECT_ID), automationRevisionRepository));
        }
        return values;
    }

    private JSONObject operatorNames(final JSONObject profession) {
        final Set<String> ids = new HashSet<>();
        collectOperators(profession.getJSONArray("revisions"), ids);
        collectOperators(profession.getJSONArray("schemes"), ids);
        for (final Object item : profession.getJSONArray("automations")) {
            collectOperators(((JSONObject) item).getJSONArray("revisions"), ids);
        }
        final JSONObject result = new JSONObject();
        for (final String id : ids) {
            final JSONObject user = userQueryService.getUser(id);
            result.put(id, null == user ? id : user.optString(User.USER_NAME, id));
        }
        return result;
    }

    private void collectOperators(final JSONArray values, final Set<String> ids) {
        for (final Object item : values) {
            final JSONObject value = (JSONObject) item;
            collect(value.optString("createdBy"), ids);
            collect(value.optString("publishedBy"), ids);
        }
    }

    private void collect(final String id, final Set<String> ids) {
        if (!id.isBlank()) {
            ids.add(id);
        }
    }

    private long summaryCount(final ProfessionCatalogSummaryQuery request) throws RepositoryException {
        final List<JSONObject> values = professionRepository.select("SELECT COUNT(*) AS `total`" + catalogFrom()
                + summaryWhere(request), summaryParameters(request).toArray());
        return values.isEmpty() ? 0L : values.getFirst().optLong("total");
    }

    private String summarySelect() {
        return "SELECT p.`oId`,p.`professionCode`,p.`currentRevisionId`,p.`currentLevelSchemeId`,p.`status`,"
                + "p.`sortOrder`,p.`createdAt`,p.`updatedAt`,COALESCE(r.`revisionNo`,latest.`revisionNo`) AS `revisionNo`,"
                + "COALESCE(r.`status`,latest.`status`) AS `revisionStatus`,COALESCE(r.`displayName`,latest.`displayName`) AS `displayName`,"
                + "COALESCE(r.`shortName`,latest.`shortName`) AS `shortName`,COALESCE(r.`description`,latest.`description`) AS `description`,"
                + "COALESCE(r.`defaultPresentationJson`,latest.`defaultPresentationJson`) AS `defaultPresentationJson`,"
                + "COALESCE(levels.`levelCount`,0) AS `levelCount`,COALESCE(automations.`automationCount`,0) "
                + "AS `automationCount`" + catalogFrom();
    }

    private String catalogFrom() {
        return " FROM `" + professionRepository.getName() + "` p LEFT JOIN `" + revisionRepository.getName()
                + "` r ON r.`oId`=p.`currentRevisionId` LEFT JOIN (SELECT newest.* FROM `" + revisionRepository.getName()
                + "` newest INNER JOIN (SELECT `professionId`,MAX(`revisionNo`) AS `revisionNo` FROM `" + revisionRepository.getName()
                + "` GROUP BY `professionId`) latestNo ON latestNo.`professionId`=newest.`professionId` AND latestNo.`revisionNo`=newest.`revisionNo`) latest ON latest.`professionId`=p.`oId` LEFT JOIN (SELECT `schemeId`,COUNT(*) AS `levelCount` FROM `"
                + levelRepository.getName() + "` GROUP BY `schemeId`) levels ON levels.`schemeId`=p.`currentLevelSchemeId` "
                + "LEFT JOIN (SELECT `professionId`,COUNT(*) AS `automationCount` FROM `" + automationRepository.getName()
                + "` GROUP BY `professionId`) automations ON automations.`professionId`=p.`oId`";
    }

    private String summaryWhere(final ProfessionCatalogSummaryQuery request) {
        final List<String> conditions = new ArrayList<>();
        if (!request.status().isBlank()) {
            conditions.add("p.`status`=?");
        }
        if (!request.keyword().isBlank()) {
            conditions.add("(LOWER(p.`professionCode`) LIKE ? OR LOWER(COALESCE(r.`displayName`,latest.`displayName`,'')) LIKE ?)");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private List<Object> summaryParameters(final ProfessionCatalogSummaryQuery request) {
        final List<Object> parameters = new ArrayList<>();
        if (!request.status().isBlank()) {
            parameters.add(request.status());
        }
        if (!request.keyword().isBlank()) {
            final String keyword = "%" + request.keyword().toLowerCase(Locale.ROOT) + "%";
            parameters.add(keyword);
            parameters.add(keyword);
        }
        return parameters;
    }

    private String summarySort(final ProfessionCatalogSummaryQuery.Sort sort) {
        return switch (sort) {
            case MANUAL -> "p.`sortOrder` ASC,p.`oId` ASC";
            case NAME -> "r.`displayName` ASC,p.`professionCode` ASC";
            case RECENT -> "p.`updatedAt` DESC,p.`oId` DESC";
        };
    }

    private JSONArray queryBy(final String field, final String value, final Repository repository)
            throws RepositoryException {
        final JSONArray result = new JSONArray();
        final Query query = new Query().setFilter(new PropertyFilter(field, FilterOperator.EQUAL, value));
        for (final JSONObject item : repository.getList(query)) {
            result.put(new JSONObject(item.toString()));
        }
        return result;
    }

    private JSONArray array(final List<JSONObject> values) {
        final JSONArray result = new JSONArray();
        values.forEach(value -> result.put(new JSONObject(value.toString())));
        return result;
    }
}
