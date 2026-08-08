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
package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.ProfessionAdminQueryService;
import org.b3log.symphony.service.ProfessionAutomationDraftRequest;
import org.b3log.symphony.service.ProfessionAutomationMgmtService;
import org.b3log.symphony.service.ProfessionAutomationRegistry;
import org.b3log.symphony.service.ProfessionAutomationTestService;
import org.b3log.symphony.service.ProfessionConfigExportService;
import org.b3log.symphony.service.ProfessionConfigImportService;
import org.b3log.symphony.service.ProfessionConfigImportPreviewService;
import org.b3log.symphony.service.ProfessionDefinitionMgmtService;
import org.b3log.symphony.service.ProfessionDefinitionRegistry;
import org.b3log.symphony.service.ProfessionLevelSchemeMgmtService;
import org.b3log.symphony.service.ProfessionOrderMgmtService;
import org.b3log.symphony.service.ProfessionSchemeImpactService;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;

import java.util.Map;

import static org.b3log.symphony.processor.ProfessionAdminParameterMapper.catalogQuery;
import static org.b3log.symphony.processor.ProfessionAdminParameterMapper.id;
import static org.b3log.symphony.processor.ProfessionAdminParameterMapper.idList;
import static org.b3log.symphony.processor.ProfessionAdminParameterMapper.text;

/** 管理员职业定义、等级方案与自动化编排接口。 */
@Singleton
public class ProfessionAdminProcessor {

    @Inject private DataModelService dataModelService;
    @Inject private ProfessionAdminQueryService queryService;
    @Inject private ProfessionDefinitionMgmtService definitionService;
    @Inject private ProfessionDefinitionRegistry definitionRegistry;
    @Inject private ProfessionLevelSchemeMgmtService schemeService;
    @Inject private ProfessionSchemeImpactService schemeImpactService;
    @Inject private ProfessionAutomationMgmtService automationService;
    @Inject private ProfessionAutomationTestService automationTestService;
    @Inject private ProfessionAutomationRegistry automationRegistry;
    @Inject private ProfessionConfigExportService configExportService;
    @Inject private ProfessionConfigImportService configImportService;
    @Inject private ProfessionConfigImportPreviewService configPreviewService;
    @Inject private ProfessionOrderMgmtService orderService;

    public static void register() {
        final BeanManager manager = BeanManager.getInstance();
        final ProfessionAdminProcessor processor = manager.getReference(ProfessionAdminProcessor.class);
        final LoginCheckMidware login = manager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrf = manager.getReference(CSRFMidware.class);
        Dispatcher.get("/admin/profession", processor::show, login::handle);
        Dispatcher.get("/admin/profession/help", processor::showHelp, login::handle);
        Dispatcher.get("/api/profession/admin/catalog", processor::catalog, login::handle);
        Dispatcher.get("/api/profession/admin/catalog-summary", processor::catalogSummary, login::handle);
        Dispatcher.get("/api/profession/admin/catalog-detail/{professionId}", processor::catalogDetail, login::handle);
        Dispatcher.get("/api/profession/admin/scheme-impact/{professionId}/{schemeId}", processor::schemeImpact, login::handle);
        Dispatcher.get("/api/profession/admin/config/export", processor::exportConfig, login::handle);
        Dispatcher.post("/api/profession/admin/config/precheck", processor::previewConfig, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/config/import", processor::importConfig, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/catalog/order", processor::reorderCatalog, login::handle, csrf::check);
        Dispatcher.get("/api/profession/admin/definition/metadata", processor::definitionMetadata, login::handle);
        Dispatcher.get("/api/profession/admin/automation/metadata", processor::automationMetadata, login::handle);
        registerDefinitionRoutes(processor, login, csrf);
        registerSchemeRoutes(processor, login, csrf);
        registerAutomationRoutes(processor, login, csrf);
    }

    private static void registerDefinitionRoutes(final ProfessionAdminProcessor processor, final LoginCheckMidware login,
                                                 final CSRFMidware csrf) {
        Dispatcher.post("/api/profession/admin/definition/draft", processor::createDefinition, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/definition/publish", processor::publishDefinition, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/definition/retire", processor::retireDefinition, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/definition/copy", processor::copyDefinition, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/definition/rollback", processor::rollbackDefinition, login::handle, csrf::check);
    }

    private static void registerSchemeRoutes(final ProfessionAdminProcessor processor, final LoginCheckMidware login,
                                             final CSRFMidware csrf) {
        Dispatcher.post("/api/profession/admin/scheme/draft", processor::createScheme, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/scheme/publish", processor::publishScheme, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/scheme/retire", processor::retireScheme, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/scheme/copy", processor::copyScheme, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/scheme/rollback", processor::rollbackScheme, login::handle, csrf::check);
    }

    private static void registerAutomationRoutes(final ProfessionAdminProcessor processor, final LoginCheckMidware login,
                                                 final CSRFMidware csrf) {
        Dispatcher.post("/api/profession/admin/automation/draft", processor::createAutomation, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/automation/publish", processor::publishAutomation, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/automation/retire", processor::retireAutomation, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/automation/copy", processor::copyAutomation, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/automation/rollback", processor::rollbackAutomation, login::handle, csrf::check);
        Dispatcher.post("/api/profession/admin/automation/test", processor::testAutomation, login::handle, csrf::check);
    }

    public void show(final RequestContext context) {
        render(context, "admin/profession.ftl");
    }

    public void showHelp(final RequestContext context) {
        render(context, "admin/profession-help.ftl");
    }

    private void render(final RequestContext context, final String template) {
        if (null == operator(context)) return;
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, template);
        context.setRenderer(renderer);
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    public void catalog(final RequestContext context) {
        execute(context, () -> queryService.catalog());
    }

    public void catalogSummary(final RequestContext context) {
        execute(context, () -> queryService.catalogSummary(catalogQuery(context)));
    }

    public void catalogDetail(final RequestContext context) {
        execute(context, () -> queryService.detail(id(context.pathVar("professionId"), "professionId")));
    }

    public void schemeImpact(final RequestContext context) {
        execute(context, () -> schemeImpactService.preview(id(context.pathVar("professionId"), "professionId"),
                id(context.pathVar("schemeId"), "schemeId")));
    }

    public void exportConfig(final RequestContext context) {
        execute(context, () -> {
            final String professionId = context.param("professionId");
            return null == professionId || professionId.isBlank() ? configExportService.exportAll()
                    : configExportService.exportOne(id(professionId, "professionId"));
        });
    }

    public void previewConfig(final RequestContext context) {
        execute(context, () -> configPreviewService.preview(text(context, "configJson", 16_000_000)));
    }

    public void importConfig(final RequestContext context) {
        execute(context, () -> configImportService.importConfig(text(context, "configJson", 16_000_000), operator(context)));
    }

    public void reorderCatalog(final RequestContext context) {
        execute(context, () -> {
            orderService.reorder(idList(context.requestJSON(), "professionIds", 1_000));
            return new JSONObject();
        });
    }

    public void automationMetadata(final RequestContext context) {
        execute(context, automationRegistry::editorMetadata);
    }

    public void definitionMetadata(final RequestContext context) {
        execute(context, definitionRegistry::presentationMetadata);
    }

    public void createDefinition(final RequestContext context) {
        execute(context, () -> value(definitionService.createProfessionDraft(
                ProfessionAdminRequestMapper.definition(context.requestJSON(), operator(context)))));
    }

    public void publishDefinition(final RequestContext context) {
        execute(context, () -> { definitionService.publishProfession(id(context, "revisionId"), operator(context)); return new JSONObject(); });
    }

    public void retireDefinition(final RequestContext context) {
        execute(context, () -> { definitionService.retireProfession(id(context, "professionId"), operator(context)); return new JSONObject(); });
    }

    public void copyDefinition(final RequestContext context) {
        execute(context, () -> value(definitionService.copyProfessionDraft(id(context, "revisionId"), operator(context))));
    }

    public void rollbackDefinition(final RequestContext context) {
        execute(context, () -> value(definitionService.rollbackProfession(id(context, "professionId"),
                id(context, "revisionId"), operator(context))));
    }

    public void createScheme(final RequestContext context) {
        execute(context, () -> value(definitionService.createLevelSchemeDraft(
                ProfessionAdminRequestMapper.scheme(context.requestJSON(), operator(context)))));
    }

    public void publishScheme(final RequestContext context) {
        execute(context, () -> { schemeService.publish(id(context, "schemeId"), operator(context)); return new JSONObject(); });
    }

    public void retireScheme(final RequestContext context) {
        execute(context, () -> { schemeService.retire(id(context, "schemeId"), operator(context)); return new JSONObject(); });
    }

    public void copyScheme(final RequestContext context) {
        execute(context, () -> value(schemeService.copyDraft(id(context, "schemeId"), operator(context))));
    }

    public void rollbackScheme(final RequestContext context) {
        execute(context, () -> value(schemeService.rollback(id(context, "professionId"), id(context, "schemeId"), operator(context))));
    }

    public void createAutomation(final RequestContext context) {
        execute(context, () -> value(automationService.createDraft(automationRequest(context, operator(context)))));
    }

    public void publishAutomation(final RequestContext context) {
        execute(context, () -> { automationService.publish(id(context, "revisionId"), operator(context)); return new JSONObject(); });
    }

    public void retireAutomation(final RequestContext context) {
        execute(context, () -> { automationService.retire(id(context, "automationId"), operator(context)); return new JSONObject(); });
    }

    public void copyAutomation(final RequestContext context) {
        execute(context, () -> value(automationService.copy(id(context, "revisionId"), operator(context))));
    }

    public void rollbackAutomation(final RequestContext context) {
        execute(context, () -> value(automationService.rollback(id(context, "automationId"),
                id(context, "revisionId"), operator(context))));
    }

    public void testAutomation(final RequestContext context) {
        execute(context, () -> automationTestService.test(text(context, "configurationJson", 16_000_000),
                text(context, "payloadJson", 16_000_000)));
    }

    private ProfessionAutomationDraftRequest automationRequest(final RequestContext context, final String operatorId) {
        final JSONObject request = context.requestJSON();
        return new ProfessionAutomationDraftRequest(id(request, "professionId"), text(request, "automationCode", 64),
                text(request, "configurationJson", 16_000_000), operatorId);
    }

    private void execute(final RequestContext context, final Action action) {
        if (null == operator(context)) return;
        try { success(context, action.run()); } catch (final Exception e) { error(context, e.getMessage()); }
    }

    private String operator(final RequestContext context) {
        final JSONObject user = (JSONObject) context.attr(User.USER);
        if (null == user || !Role.ROLE_ID_C_ADMIN.equals(user.optString(User.USER_ROLE))) {
            context.sendError(403);
            context.abort();
            return null;
        }
        return user.getString(Keys.OBJECT_ID);
    }

    private JSONObject value(final String id) { return new JSONObject().put("id", id); }
    private void success(final RequestContext context, final Object data) { context.renderJSON(new JSONObject().put(Keys.CODE, StatusCodes.SUCC).put(Common.DATA, data)); }
    private void error(final RequestContext context, final String message) { context.renderJSON(new JSONObject().put(Keys.CODE, StatusCodes.ERR).put(Keys.MSG, message)); }

    @FunctionalInterface private interface Action { Object run() throws Exception; }
}
