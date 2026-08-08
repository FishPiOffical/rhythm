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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.ProfessionAutomation;
import org.b3log.symphony.model.ProfessionAutomationRevision;
import org.b3log.symphony.repository.ProfessionAutomationRepository;
import org.b3log.symphony.repository.ProfessionAutomationRevisionRepository;
import org.json.JSONObject;

import java.util.regex.Pattern;

/** 管理职业自动化不可变版本。 */
@Service
public class ProfessionAutomationMgmtService {

    private static final Pattern AUTOMATION_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");
    private static final int MAX_CONFIGURATION_LENGTH = 16_000_000;

    @Inject
    private ProfessionAutomationRepository automationRepository;

    @Inject
    private ProfessionAutomationRevisionRepository revisionRepository;

    @Inject
    private ProfessionAutomationRegistry registry;

    public String createDraft(final ProfessionAutomationDraftRequest request) throws RepositoryException {
        validateRequest(request);
        final JSONObject configuration = new JSONObject(request.configurationJson());
        registry.validate(configuration);
        final Transaction transaction = automationRepository.beginTransaction();
        try {
            final JSONObject automation = findOrCreateAutomation(request, System.currentTimeMillis());
            final String revisionId = addRevision(automation, request, System.currentTimeMillis());
            transaction.commit();
            return revisionId;
        } catch (final RepositoryException e) {
            rollback(transaction);
            throw e;
        } catch (final Exception e) {
            rollback(transaction);
            throw new RepositoryException(e);
        }
    }

    public void publish(final String revisionId, final String operatorUserId) throws RepositoryException {
        validateOperator(operatorUserId);
        final Transaction transaction = automationRepository.beginTransaction();
        try {
            final JSONObject revision = revisionRepository.get(revisionId);
            requireDraft(revision);
            registry.validate(new JSONObject(revision.optString(ProfessionAutomationRevision.CONFIGURATION_JSON)));
            publishRevision(revision, operatorUserId, System.currentTimeMillis());
            transaction.commit();
        } catch (final RepositoryException e) {
            rollback(transaction);
            throw e;
        } catch (final Exception e) {
            rollback(transaction);
            throw new RepositoryException(e);
        }
    }

    public void retire(final String automationId, final String operatorUserId) throws RepositoryException {
        validateOperator(operatorUserId);
        final Transaction transaction = automationRepository.beginTransaction();
        try {
            final JSONObject automation = automationRepository.get(automationId);
            if (null == automation) {
                throw new RepositoryException("自动化不存在");
            }
            retireCurrentRevision(automation.optString(ProfessionAutomation.CURRENT_REVISION_ID), System.currentTimeMillis());
            automation.put(ProfessionAutomation.STATUS, "RETIRED");
            automation.put(ProfessionAutomation.UPDATED_AT, System.currentTimeMillis());
            automationRepository.update(automationId, automation);
            transaction.commit();
        } catch (final RepositoryException e) {
            rollback(transaction);
            throw e;
        } catch (final Exception e) {
            rollback(transaction);
            throw new RepositoryException(e);
        }
    }

    public String copy(final String revisionId, final String operatorUserId) throws RepositoryException {
        validateOperator(operatorUserId);
        final JSONObject source = revisionRepository.get(revisionId);
        if (null == source) {
            throw new RepositoryException("自动化版本不存在");
        }
        final JSONObject automation = automationRepository.get(source.getString(ProfessionAutomationRevision.AUTOMATION_ID));
        return createDraft(new ProfessionAutomationDraftRequest(source.getString(ProfessionAutomationRevision.PROFESSION_ID),
                automation.getString(ProfessionAutomation.CODE), source.getString(ProfessionAutomationRevision.CONFIGURATION_JSON),
                operatorUserId));
    }

    public String rollback(final String automationId, final String revisionId, final String operatorUserId)
            throws RepositoryException {
        validateOperator(operatorUserId);
        final JSONObject source = revisionRepository.get(revisionId);
        if (null == source || !automationId.equals(source.optString(ProfessionAutomationRevision.AUTOMATION_ID))) {
            throw new RepositoryException("目标版本不属于自动化");
        }
        final String draftId = copy(revisionId, operatorUserId);
        publish(draftId, operatorUserId);
        return draftId;
    }

    private JSONObject findOrCreateAutomation(final ProfessionAutomationDraftRequest request, final long now)
            throws RepositoryException {
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(ProfessionAutomation.PROFESSION_ID, FilterOperator.EQUAL, request.professionId()),
                new PropertyFilter(ProfessionAutomation.CODE, FilterOperator.EQUAL, request.automationCode())));
        final JSONObject existing = automationRepository.getFirst(query);
        if (null != existing) {
            return existing;
        }
        final JSONObject automation = new JSONObject();
        automation.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        automation.put(ProfessionAutomation.PROFESSION_ID, request.professionId());
        automation.put(ProfessionAutomation.CODE, request.automationCode());
        automation.put(ProfessionAutomation.CURRENT_REVISION_ID, "");
        automation.put(ProfessionAutomation.STATUS, "DRAFT");
        automation.put(ProfessionAutomation.SORT_ORDER, 0);
        automation.put(ProfessionAutomation.CREATED_AT, now);
        automation.put(ProfessionAutomation.UPDATED_AT, now);
        automationRepository.add(automation);
        return automation;
    }

    private String addRevision(final JSONObject automation, final ProfessionAutomationDraftRequest request, final long now)
            throws RepositoryException {
        final String automationId = automation.optString(Keys.OBJECT_ID);
        final JSONObject revision = new JSONObject();
        revision.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        revision.put(ProfessionAutomationRevision.AUTOMATION_ID, automationId);
        revision.put(ProfessionAutomationRevision.PROFESSION_ID, request.professionId());
        revision.put(ProfessionAutomationRevision.REVISION_NO, nextRevisionNo(automationId));
        revision.put(ProfessionAutomationRevision.SCHEMA_VERSION, 1);
        revision.put(ProfessionAutomationRevision.STATUS, "DRAFT");
        revision.put(ProfessionAutomationRevision.TRIGGER_TYPE, new JSONObject(request.configurationJson())
                .getJSONObject("trigger").getString("triggerType"));
        revision.put(ProfessionAutomationRevision.TRIGGER_SCHEMA_VERSION, new JSONObject(request.configurationJson())
                .getJSONObject("trigger").getInt("schemaVersion"));
        revision.put(ProfessionAutomationRevision.CONFIGURATION_JSON, request.configurationJson());
        revision.put(ProfessionAutomationRevision.CONFIGURATION_HASH, DigestUtils.sha256Hex(request.configurationJson()));
        revision.put(ProfessionAutomationRevision.EFFECTIVE_FROM, 0L);
        revision.put(ProfessionAutomationRevision.EFFECTIVE_TO, 0L);
        revision.put(ProfessionAutomationRevision.CREATED_BY, request.operatorUserId());
        revision.put(ProfessionAutomationRevision.CREATED_AT, now);
        revision.put(ProfessionAutomationRevision.PUBLISHED_BY, "");
        revision.put(ProfessionAutomationRevision.PUBLISHED_AT, 0L);
        revisionRepository.add(revision);
        return revision.optString(Keys.OBJECT_ID);
    }

    private int nextRevisionNo(final String automationId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(ProfessionAutomationRevision.AUTOMATION_ID,
                FilterOperator.EQUAL, automationId));
        return revisionRepository.getList(query).stream()
                .mapToInt(item -> item.optInt(ProfessionAutomationRevision.REVISION_NO)).max().orElse(0) + 1;
    }

    private void requireDraft(final JSONObject revision) throws RepositoryException {
        if (null == revision || !StringUtils.equals("DRAFT", revision.optString(ProfessionAutomationRevision.STATUS))) {
            throw new RepositoryException("自动化版本不可发布");
        }
    }

    private void validateRequest(final ProfessionAutomationDraftRequest request) throws RepositoryException {
        if (null == request || null == request.configurationJson() || request.configurationJson().isBlank()
                || request.configurationJson().length() > MAX_CONFIGURATION_LENGTH) {
            throw new RepositoryException("自动化配置长度不合法");
        }
        if (null == request.professionId() || request.professionId().isBlank() || request.professionId().length() > 19
                || null == request.automationCode() || !AUTOMATION_CODE_PATTERN.matcher(request.automationCode()).matches()) {
            throw new RepositoryException("职业或自动化编码不合法");
        }
        validateOperator(request.operatorUserId());
    }

    private void validateOperator(final String operatorUserId) throws RepositoryException {
        if (null == operatorUserId || operatorUserId.isBlank() || operatorUserId.length() > 19) {
            throw new RepositoryException("操作用户不合法");
        }
    }

    private void publishRevision(final JSONObject revision, final String operatorUserId, final long now)
            throws RepositoryException {
        final JSONObject automation = automationRepository.get(revision.optString(ProfessionAutomationRevision.AUTOMATION_ID));
        retireCurrentRevision(automation.optString(ProfessionAutomation.CURRENT_REVISION_ID), now);
        revision.put(ProfessionAutomationRevision.STATUS, "PUBLISHED");
        revision.put(ProfessionAutomationRevision.EFFECTIVE_FROM, now);
        revision.put(ProfessionAutomationRevision.PUBLISHED_BY, operatorUserId);
        revision.put(ProfessionAutomationRevision.PUBLISHED_AT, now);
        revisionRepository.update(revision.optString(Keys.OBJECT_ID), revision);
        automation.put(ProfessionAutomation.CURRENT_REVISION_ID, revision.optString(Keys.OBJECT_ID));
        automation.put(ProfessionAutomation.STATUS, "PUBLISHED");
        automation.put(ProfessionAutomation.UPDATED_AT, now);
        automationRepository.update(automation.optString(Keys.OBJECT_ID), automation);
    }

    private void retireCurrentRevision(final String revisionId, final long now) throws RepositoryException {
        if (StringUtils.isBlank(revisionId)) {
            return;
        }
        final JSONObject current = revisionRepository.get(revisionId);
        current.put(ProfessionAutomationRevision.STATUS, "RETIRED");
        current.put(ProfessionAutomationRevision.EFFECTIVE_TO, now);
        revisionRepository.update(revisionId, current);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
