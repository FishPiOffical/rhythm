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
import org.b3log.latke.repository.Repository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.model.ProfessionLevelScheme;
import org.b3log.symphony.model.ProfessionRevision;
import org.b3log.symphony.repository.ProfessionLevelSchemeRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.json.JSONObject;

import java.util.regex.Pattern;

/** 管理职业及等级方案的不可变定义。 */
@Service
public class ProfessionDefinitionMgmtService {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");

    @Inject private ProfessionRepository professionRepository;
    @Inject private ProfessionRevisionRepository revisionRepository;
    @Inject private ProfessionLevelSchemeRepository schemeRepository;
    @Inject private ProfessionDefinitionRegistry registry;
    @Inject private ProfessionLevelDraftValidator levelDraftValidator;
    @Inject private ProfessionLevelDraftStorageService levelDraftStorageService;
    @Inject private ProfessionDefinitionStateService definitionStateService;
    @Inject private ProfessionRetirementMgmtService retirementMgmtService;

    public String createProfessionDraft(final ProfessionDefinitionDraftRequest request) throws RepositoryException {
        validateProfessionRequest(request);
        final Transaction transaction = professionRepository.beginTransaction();
        try {
            final long now = System.currentTimeMillis();
            final JSONObject profession = findOrCreateProfession(request.professionCode(), now);
            final String revisionId = addProfessionRevision(profession, request, now);
            transaction.commit();
            return revisionId;
        } catch (final Exception e) {
            rollback(transaction);
            throw repositoryException(e);
        }
    }

    public String createLevelSchemeDraft(final ProfessionLevelSchemeDraftRequest request) throws RepositoryException {
        levelDraftValidator.validate(request);
        final Transaction transaction = schemeRepository.beginTransaction();
        try {
            final long now = System.currentTimeMillis();
            requireProfessionRevision(request);
            final JSONObject scheme = addScheme(request, now);
            levelDraftStorageService.store(scheme.getString(Keys.OBJECT_ID), request, now);
            transaction.commit();
            return scheme.getString(Keys.OBJECT_ID);
        } catch (final Exception e) {
            rollback(transaction);
            throw repositoryException(e);
        }
    }

    public void publishProfession(final String revisionId, final String operatorUserId) throws RepositoryException {
        validateId(revisionId, "职业版本");
        validateId(operatorUserId, "操作用户");
        final Transaction transaction = professionRepository.beginTransaction();
        try {
            final JSONObject revision = requireDraft(revisionRepository.get(revisionId), "职业版本");
            registry.validatePresentation(new ProfessionLevelPresentationDraft("homeProfile",
                    revision.getString(ProfessionRevision.DEFAULT_PRESENTATION_JSON),
                    revision.getString(ProfessionRevision.DEFAULT_PRESENTATION_JSON)));
            final JSONObject profession = professionRepository.get(revision.getString(ProfessionRevision.PROFESSION_ID));
            definitionStateService.retireRevision(profession.optString(Profession.CURRENT_REVISION_ID), System.currentTimeMillis());
            publishRevision(revision, operatorUserId, System.currentTimeMillis());
            profession.put(Profession.CURRENT_REVISION_ID, revisionId);
            profession.put(Profession.STATUS, "PUBLISHED");
            profession.put(Profession.UPDATED_AT, System.currentTimeMillis());
            professionRepository.update(profession.getString(Keys.OBJECT_ID), profession);
            transaction.commit();
        } catch (final Exception e) {
            rollback(transaction);
            throw repositoryException(e);
        }
    }

    public void retireProfession(final String professionId, final String operatorUserId) throws RepositoryException {
        retirementMgmtService.retire(professionId, operatorUserId);
    }

    public String copyProfessionDraft(final String sourceRevisionId, final String operatorUserId) throws RepositoryException {
        validateId(sourceRevisionId, "职业版本");
        validateId(operatorUserId, "操作用户");
        final JSONObject source = revisionRepository.get(sourceRevisionId);
        if (null == source) throw new RepositoryException("职业版本不存在");
        final JSONObject profession = professionRepository.get(source.getString(ProfessionRevision.PROFESSION_ID));
        return createProfessionDraft(new ProfessionDefinitionDraftRequest(profession.getString(Profession.CODE),
                source.getString(ProfessionRevision.DISPLAY_NAME), source.getString(ProfessionRevision.SHORT_NAME),
                source.getString(ProfessionRevision.DESCRIPTION), source.getString(ProfessionRevision.DEFAULT_PRESENTATION_JSON),
                operatorUserId));
    }

    public String rollbackProfession(final String professionId, final String sourceRevisionId, final String operatorUserId)
            throws RepositoryException {
        validateId(professionId, "职业");
        final JSONObject source = revisionRepository.get(sourceRevisionId);
        if (null == source || !professionId.equals(source.optString(ProfessionRevision.PROFESSION_ID))) {
            throw new RepositoryException("目标职业版本不匹配");
        }
        final String draftId = copyProfessionDraft(sourceRevisionId, operatorUserId);
        publishProfession(draftId, operatorUserId);
        return draftId;
    }

    private JSONObject findOrCreateProfession(final String code, final long now) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(Profession.CODE, FilterOperator.EQUAL, code));
        final JSONObject existing = professionRepository.getFirst(query);
        if (null != existing) return existing;
        final JSONObject profession = new JSONObject();
        profession.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        profession.put(Profession.CODE, code);
        profession.put(Profession.CURRENT_REVISION_ID, "");
        profession.put(Profession.CURRENT_LEVEL_SCHEME_ID, "");
        profession.put(Profession.STATUS, "DRAFT");
        profession.put(Profession.SORT_ORDER, 0);
        profession.put(Profession.CREATED_AT, now);
        profession.put(Profession.UPDATED_AT, now);
        professionRepository.add(profession);
        return profession;
    }

    private String addProfessionRevision(final JSONObject profession, final ProfessionDefinitionDraftRequest request,
                                         final long now) throws RepositoryException {
        final JSONObject revision = new JSONObject();
        revision.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        revision.put(ProfessionRevision.PROFESSION_ID, profession.getString(Keys.OBJECT_ID));
        revision.put(ProfessionRevision.REVISION_NO, nextRevision(revisionRepository, ProfessionRevision.PROFESSION_ID,
                profession.getString(Keys.OBJECT_ID)));
        revision.put(ProfessionRevision.SCHEMA_VERSION, 1);
        revision.put(ProfessionRevision.STATUS, "DRAFT");
        revision.put(ProfessionRevision.DISPLAY_NAME, request.displayName());
        revision.put(ProfessionRevision.SHORT_NAME, request.shortName());
        revision.put(ProfessionRevision.DESCRIPTION, request.description());
        revision.put(ProfessionRevision.DEFAULT_PRESENTATION_JSON, request.defaultPresentationJson());
        revision.put(ProfessionRevision.EFFECTIVE_FROM, 0L);
        revision.put(ProfessionRevision.EFFECTIVE_TO, 0L);
        revision.put(ProfessionRevision.CREATED_BY, request.operatorUserId());
        revision.put(ProfessionRevision.CREATED_AT, now);
        revision.put(ProfessionRevision.PUBLISHED_BY, "");
        revision.put(ProfessionRevision.PUBLISHED_AT, 0L);
        revisionRepository.add(revision);
        return revision.getString(Keys.OBJECT_ID);
    }

    private JSONObject addScheme(final ProfessionLevelSchemeDraftRequest request, final long now) throws RepositoryException {
        final JSONObject scheme = new JSONObject();
        scheme.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        scheme.put(ProfessionLevelScheme.PROFESSION_ID, request.professionId());
        scheme.put(ProfessionLevelScheme.PROFESSION_REVISION_ID, request.professionRevisionId());
        scheme.put(ProfessionLevelScheme.REVISION_NO, nextRevision(schemeRepository, ProfessionLevelScheme.PROFESSION_ID, request.professionId()));
        scheme.put(ProfessionLevelScheme.SCHEMA_VERSION, 1);
        scheme.put(ProfessionLevelScheme.STATUS, "DRAFT");
        scheme.put(ProfessionLevelScheme.MIGRATION_POLICY, request.migrationPolicy());
        scheme.put(ProfessionLevelScheme.REWARD_MIGRATION_POLICY, request.rewardMigrationPolicy());
        scheme.put(ProfessionLevelScheme.MIGRATION_CONFIG_JSON, request.migrationConfigJson());
        scheme.put(ProfessionLevelScheme.EFFECTIVE_FROM, 0L);
        scheme.put(ProfessionLevelScheme.EFFECTIVE_TO, 0L);
        scheme.put(ProfessionLevelScheme.CREATED_BY, request.operatorUserId());
        scheme.put(ProfessionLevelScheme.CREATED_AT, now);
        scheme.put(ProfessionLevelScheme.PUBLISHED_BY, "");
        scheme.put(ProfessionLevelScheme.PUBLISHED_AT, 0L);
        schemeRepository.add(scheme);
        return scheme;
    }

    private void requireProfessionRevision(final ProfessionLevelSchemeDraftRequest request) throws RepositoryException {
        final JSONObject revision = revisionRepository.get(request.professionRevisionId());
        if (null == revision || !request.professionId().equals(
                revision.optString(ProfessionRevision.PROFESSION_ID))) {
            throw new RepositoryException("职业版本不匹配");
        }
    }

    private void validateProfessionRequest(final ProfessionDefinitionDraftRequest request) {
        if (null == request || !CODE.matcher(request.professionCode()).matches()) {
            throw new IllegalArgumentException("职业编码不合法");
        }
        validateId(request.operatorUserId(), "操作用户");
        validateText(request.displayName(), 64, "职业名称");
        validateOptionalText(request.shortName(), 32, "职业简称");
        validateOptionalText(request.description(), 1_024, "职业说明");
        registry.validatePresentation(new ProfessionLevelPresentationDraft("homeProfile",
                request.defaultPresentationJson(), request.defaultPresentationJson()));
    }

    private int nextRevision(final Repository repository, final String field, final String value)
            throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(field, FilterOperator.EQUAL, value));
        return repository.getList(query).stream()
                .mapToInt(item -> item.optInt("revisionNo"))
                .max().orElse(0) + 1;
    }

    private JSONObject requireDraft(final JSONObject value, final String name) throws RepositoryException {
        if (null == value || !"DRAFT".equals(value.optString("status"))) {
            throw new RepositoryException(name + "不可发布");
        }
        return value;
    }

    private void publishRevision(final JSONObject revision, final String operator, final long now)
            throws RepositoryException {
        revision.put(ProfessionRevision.STATUS, "PUBLISHED");
        revision.put(ProfessionRevision.EFFECTIVE_FROM, now);
        revision.put(ProfessionRevision.PUBLISHED_BY, operator);
        revision.put(ProfessionRevision.PUBLISHED_AT, now);
        revisionRepository.update(revision.getString(Keys.OBJECT_ID), revision);
    }

    private void validateId(final String value, final String name) {
        if (null == value || !value.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException(name + "标识不合法");
        }
    }

    private void validateText(final String value, final int maxLength, final String name) {
        if (!safeText(value, maxLength) || value.isBlank()) {
            throw new IllegalArgumentException(name + "长度不合法");
        }
    }

    private void validateOptionalText(final String value, final int maxLength, final String name) {
        if (!safeText(value, maxLength) || (!value.isEmpty() && value.isBlank())) {
            throw new IllegalArgumentException(name + "长度不合法");
        }
    }

    private boolean safeText(final String value, final int maxLength) {
        return null != value && value.length() <= maxLength && !value.contains("<") && !value.contains(">");
    }

    private RepositoryException repositoryException(final Exception exception) {
        return exception instanceof RepositoryException error ? error : new RepositoryException(exception);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
