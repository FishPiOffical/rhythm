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

import org.apache.commons.codec.digest.DigestUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.model.ProfessionAutomation;
import org.b3log.symphony.model.ProfessionAutomationRevision;
import org.b3log.symphony.model.ProfessionLevelScheme;
import org.b3log.symphony.model.ProfessionRevision;
import org.b3log.symphony.repository.ProfessionAutomationRepository;
import org.b3log.symphony.repository.ProfessionAutomationRevisionRepository;
import org.b3log.symphony.repository.ProfessionLevelSchemeRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** 在事务中导入职业定义、等级方案和自动化配置。 */
@Service
public class ProfessionConfigImportService {

    private static final int MAX_DOCUMENT_LENGTH = 16_000_000;

    @Inject private ProfessionConfigDocumentValidator documentValidator;
    @Inject private ProfessionLevelDraftStorageService levelStorageService;
    @Inject private ProfessionRepository professionRepository;
    @Inject private ProfessionRevisionRepository revisionRepository;
    @Inject private ProfessionLevelSchemeRepository schemeRepository;
    @Inject private ProfessionAutomationRepository automationRepository;
    @Inject private ProfessionAutomationRevisionRepository automationRevisionRepository;

    public JSONObject importConfig(final String configJson, final String operatorUserId) throws RepositoryException {
        if (null == configJson || configJson.isBlank() || configJson.length() > MAX_DOCUMENT_LENGTH) {
            throw new IllegalArgumentException("配置文件长度不合法");
        }
        final JSONObject document = new JSONObject(configJson);
        documentValidator.validate(document);
        validateConflicts(document.getJSONArray("professions"));
        final Transaction transaction = professionRepository.beginTransaction();
        try {
            final ImportSession session = new ImportSession(operatorUserId, System.currentTimeMillis());
            for (final Object item : document.getJSONArray("professions")) importProfession((JSONObject) item, session);
            transaction.commit();
            return new JSONObject().put("importedCount", document.getJSONArray("professions").length());
        } catch (final Exception e) {
            rollback(transaction);
            throw repositoryException(e);
        }
    }

    private void validateConflicts(final JSONArray professions) throws RepositoryException {
        for (final Object item : professions) {
            final String code = ((JSONObject) item).getString(Profession.CODE);
            if (null != findProfession(code)) throw new IllegalArgumentException("职业代号已存在：" + code);
        }
    }

    private void importProfession(final JSONObject source, final ImportSession session) throws RepositoryException {
        if (null != findProfession(source.getString(Profession.CODE))) {
            throw new IllegalArgumentException("职业代号已存在：" + source.getString(Profession.CODE));
        }
        final JSONObject profession = newProfession(source, session);
        professionRepository.add(profession);
        final Map<Integer, String> revisions = importRevisions(source, profession, session);
        final ProfessionTarget revisionTarget = new ProfessionTarget(profession.getString(Keys.OBJECT_ID), revisions, Map.of());
        final Map<Integer, String> schemes = importSchemes(source, revisionTarget, session);
        final ProfessionTarget target = new ProfessionTarget(profession.getString(Keys.OBJECT_ID), revisions, schemes);
        importAutomations(source, target, session);
        updateProfession(source, profession, target, session);
    }

    private JSONObject newProfession(final JSONObject source, final ImportSession session) {
        final JSONObject value = new JSONObject();
        value.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        value.put(Profession.CODE, source.getString(Profession.CODE));
        value.put(Profession.CURRENT_REVISION_ID, "");
        value.put(Profession.CURRENT_LEVEL_SCHEME_ID, "");
        value.put(Profession.STATUS, source.getString(Profession.STATUS));
        value.put(Profession.SORT_ORDER, source.optInt(Profession.SORT_ORDER));
        value.put(Profession.CREATED_AT, session.now());
        value.put(Profession.UPDATED_AT, session.now());
        return value;
    }

    private Map<Integer, String> importRevisions(final JSONObject source, final JSONObject profession,
                                                  final ImportSession session) throws RepositoryException {
        final Map<Integer, String> result = new HashMap<>();
        for (final Object item : source.getJSONArray("revisions")) {
            final JSONObject revision = (JSONObject) item;
            final String id = Ids.genTimeMillisId();
            revisionRepository.add(revision(revision, profession.getString(Keys.OBJECT_ID), id, session));
            result.put(revision.getInt("revisionNo"), id);
        }
        return result;
    }

    private JSONObject revision(final JSONObject source, final String professionId, final String revisionId,
                                final ImportSession session) {
        final JSONObject value = versionBase(source, revisionId, session);
        value.put(ProfessionRevision.PROFESSION_ID, professionId);
        value.put(ProfessionRevision.REVISION_NO, source.getInt("revisionNo"));
        value.put(ProfessionRevision.SCHEMA_VERSION, source.getInt("schemaVersion"));
        value.put(ProfessionRevision.DISPLAY_NAME, source.getString("displayName"));
        value.put(ProfessionRevision.SHORT_NAME, source.getString("shortName"));
        value.put(ProfessionRevision.DESCRIPTION, source.getString("description"));
        value.put(ProfessionRevision.DEFAULT_PRESENTATION_JSON, source.getString("defaultPresentationJson"));
        return value;
    }

    private Map<Integer, String> importSchemes(final JSONObject source, final ProfessionTarget target,
                                                final ImportSession session) throws RepositoryException {
        final Map<Integer, String> result = new HashMap<>();
        for (final Object item : source.getJSONArray("schemes")) {
            final JSONObject scheme = (JSONObject) item;
            final String schemeId = Ids.genTimeMillisId();
            schemeRepository.add(scheme(scheme, target, schemeId, session));
            final String revisionId = target.revisionIds().get(scheme.getInt("professionRevisionNo"));
            levelStorageService.store(schemeId, documentValidator.levelRequest(scheme, target.professionId(), revisionId,
                    session.operatorUserId()), session.now());
            result.put(scheme.getInt("revisionNo"), schemeId);
        }
        return result;
    }

    private JSONObject scheme(final JSONObject source, final ProfessionTarget target, final String schemeId,
                              final ImportSession session) {
        final JSONObject value = versionBase(source, schemeId, session);
        value.put(ProfessionLevelScheme.PROFESSION_ID, target.professionId());
        value.put(ProfessionLevelScheme.PROFESSION_REVISION_ID, target.revisionIds().get(source.getInt("professionRevisionNo")));
        value.put(ProfessionLevelScheme.REVISION_NO, source.getInt("revisionNo"));
        value.put(ProfessionLevelScheme.SCHEMA_VERSION, source.getInt("schemaVersion"));
        value.put(ProfessionLevelScheme.MIGRATION_POLICY, source.getString("migrationPolicy"));
        value.put(ProfessionLevelScheme.REWARD_MIGRATION_POLICY, source.getString("rewardMigrationPolicy"));
        value.put(ProfessionLevelScheme.MIGRATION_CONFIG_JSON, source.getString("migrationConfigJson"));
        return value;
    }

    private void importAutomations(final JSONObject source, final ProfessionTarget target, final ImportSession session)
            throws RepositoryException {
        for (final Object item : source.getJSONArray("automations")) {
            final JSONObject automation = (JSONObject) item;
            final JSONObject stored = automation(automation, target, session);
            automationRepository.add(stored);
            final Map<Integer, String> revisions = importAutomationRevisions(automation, stored, target, session);
            stored.put(ProfessionAutomation.CURRENT_REVISION_ID, reference(automation, "currentRevisionNo", revisions));
            automationRepository.update(stored.getString(Keys.OBJECT_ID), stored);
        }
    }

    private JSONObject automation(final JSONObject source, final ProfessionTarget target, final ImportSession session) {
        final JSONObject value = new JSONObject();
        value.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        value.put(ProfessionAutomation.PROFESSION_ID, target.professionId());
        value.put(ProfessionAutomation.CODE, source.getString("automationCode"));
        value.put(ProfessionAutomation.CURRENT_REVISION_ID, "");
        value.put(ProfessionAutomation.STATUS, source.getString("status"));
        value.put(ProfessionAutomation.SORT_ORDER, source.optInt("sortOrder"));
        value.put(ProfessionAutomation.CREATED_AT, session.now());
        value.put(ProfessionAutomation.UPDATED_AT, session.now());
        return value;
    }

    private Map<Integer, String> importAutomationRevisions(final JSONObject source, final JSONObject automation,
                                                            final ProfessionTarget target, final ImportSession session)
            throws RepositoryException {
        final Map<Integer, String> result = new HashMap<>();
        for (final Object item : source.getJSONArray("revisions")) {
            final JSONObject revision = (JSONObject) item;
            final String id = Ids.genTimeMillisId();
            automationRevisionRepository.add(automationRevision(revision, automation.getString(Keys.OBJECT_ID), target, id, session));
            result.put(revision.getInt("revisionNo"), id);
        }
        return result;
    }

    private JSONObject automationRevision(final JSONObject source, final String automationId, final ProfessionTarget target,
                                          final String revisionId, final ImportSession session) {
        final JSONObject configuration = new JSONObject(source.getString("configurationJson"));
        final JSONObject trigger = configuration.getJSONObject("trigger");
        final JSONObject value = versionBase(source, revisionId, session);
        value.put(ProfessionAutomationRevision.AUTOMATION_ID, automationId);
        value.put(ProfessionAutomationRevision.PROFESSION_ID, target.professionId());
        value.put(ProfessionAutomationRevision.REVISION_NO, source.getInt("revisionNo"));
        value.put(ProfessionAutomationRevision.SCHEMA_VERSION, source.getInt("schemaVersion"));
        value.put(ProfessionAutomationRevision.TRIGGER_TYPE, trigger.getString("triggerType"));
        value.put(ProfessionAutomationRevision.TRIGGER_SCHEMA_VERSION, trigger.getInt("schemaVersion"));
        value.put(ProfessionAutomationRevision.CONFIGURATION_JSON, source.getString("configurationJson"));
        value.put(ProfessionAutomationRevision.CONFIGURATION_HASH, DigestUtils.sha256Hex(source.getString("configurationJson")));
        return value;
    }

    private JSONObject versionBase(final JSONObject source, final String objectId, final ImportSession session) {
        final boolean published = "PUBLISHED".equals(source.getString("status"));
        final boolean retired = "RETIRED".equals(source.getString("status"));
        final JSONObject value = new JSONObject();
        value.put(Keys.OBJECT_ID, objectId);
        value.put("status", source.getString("status"));
        value.put("effectiveFrom", published ? session.now() : 0L);
        value.put("effectiveTo", retired ? session.now() : 0L);
        value.put("createdBy", session.operatorUserId());
        value.put("createdAt", session.now());
        value.put("publishedBy", published ? session.operatorUserId() : "");
        value.put("publishedAt", published ? session.now() : 0L);
        return value;
    }

    private void updateProfession(final JSONObject source, final JSONObject profession, final ProfessionTarget target,
                                  final ImportSession session) throws RepositoryException {
        profession.put(Profession.CURRENT_REVISION_ID, reference(source, "currentRevisionNo", target.revisionIds()));
        profession.put(Profession.CURRENT_LEVEL_SCHEME_ID, reference(source, "currentSchemeRevisionNo", target.schemeIds()));
        profession.put(Profession.UPDATED_AT, session.now());
        professionRepository.update(profession.getString(Keys.OBJECT_ID), profession);
    }

    private String reference(final JSONObject source, final String key, final Map<Integer, String> ids) {
        final int number = source.optInt(key);
        return 0 == number ? "" : ids.get(number);
    }

    private JSONObject findProfession(final String code) throws RepositoryException {
        return professionRepository.getFirst(new Query().setFilter(new PropertyFilter(Profession.CODE, FilterOperator.EQUAL, code)));
    }

    private RepositoryException repositoryException(final Exception exception) {
        return exception instanceof RepositoryException error ? error : new RepositoryException(exception);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) transaction.rollback();
    }

    private record ImportSession(String operatorUserId, long now) {
    }

    private record ProfessionTarget(String professionId, Map<Integer, String> revisionIds, Map<Integer, String> schemeIds) {
    }
}
