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

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3AdminPetService;
import org.b3log.symphony.service.ApiV3AdminProfessionService;
import org.b3log.symphony.service.ApiV3AdminSystemService;
import org.b3log.symphony.service.ProfessionAdminQueryService;
import org.b3log.symphony.service.ProfessionAutomationMgmtService;
import org.b3log.symphony.service.ProfessionAutomationRegistry;
import org.b3log.symphony.service.ProfessionAutomationTestService;
import org.b3log.symphony.service.ProfessionConfigExportService;
import org.b3log.symphony.service.ProfessionConfigImportPreviewService;
import org.b3log.symphony.service.ProfessionConfigImportService;
import org.b3log.symphony.service.ProfessionDefinitionMgmtService;
import org.b3log.symphony.service.ProfessionDefinitionRegistry;
import org.b3log.symphony.service.ProfessionLevelSchemeMgmtService;
import org.b3log.symphony.service.ProfessionOrderMgmtService;
import org.b3log.symphony.service.ProfessionSchemeImpactService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * API v3 管理后台四期处理器（宠物风控日志、IP 管理、站点设置、安全监控、索引重建与职业配置工作台）。
 */
@Singleton
public class ApiV3AdminStage4Processor {

    private static final int MAX_WRITE_BODY_BYTES = 64 * 1024;

    private static final Set<String> IP_MUTATE_FIELDS = Set.of("action", "ipList");
    private static final Set<String> MISC_SETTINGS_FIELDS = Set.of("options");
    private static final Set<String> SECURITY_SWITCH_FIELDS = Set.of(
            "firewallEnabled", "threshold", "verificationEnabled", "firstVisitCaptchaEnabled");

    @Inject
    private ApiV3AdminSystemService systemService;

    @Inject
    private ApiV3AdminProfessionService professionAdminService;

    @Inject
    private ProfessionAdminQueryService professionQueryService;

    @Inject
    private ProfessionSchemeImpactService schemeImpactService;

    @Inject
    private ProfessionConfigExportService configExportService;

    @Inject
    private ProfessionConfigImportPreviewService configPreviewService;

    @Inject
    private ProfessionConfigImportService configImportService;

    @Inject
    private ProfessionOrderMgmtService orderService;

    @Inject
    private ProfessionDefinitionRegistry definitionRegistry;

    @Inject
    private ProfessionAutomationRegistry automationRegistry;

    @Inject
    private ProfessionDefinitionMgmtService definitionService;

    @Inject
    private ProfessionLevelSchemeMgmtService schemeService;

    @Inject
    private ProfessionAutomationMgmtService automationService;

    @Inject
    private ProfessionAutomationTestService automationTestService;

    @Inject
    private ApiV3AdminPetService petService;

    /**
     * 注册第四阶段管理后台 v3 路由。
     */
    public static void register() {
        final BeanManager manager = BeanManager.getInstance();
        final ApiV3AdminStage4Processor p = manager.getReference(ApiV3AdminStage4Processor.class);
        final ApiV3AuthMidware auth = manager.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware write = manager.getReference(ApiV3WriteMidware.class);

        Dispatcher.get("/api/v3/admin/partners/report", p::partnerReport, auth::profileRead);
        Dispatcher.get("/api/v3/admin/partners/ledger", p::partnerLedger, auth::profileRead);
        Dispatcher.put("/api/v3/admin/partners/rate", p::partnerRate, auth::settingsWrite, write::handle);
        Dispatcher.get("/api/v3/admin/parking/models", p::parkingModels, auth::profileRead);
        Dispatcher.get("/api/v3/admin/parking/metrics", p::parkingMetrics, auth::profileRead);
        Dispatcher.get("/api/v3/admin/parking/ledger", p::parkingLedger, auth::profileRead);
        Dispatcher.put("/api/v3/admin/parking/models/{model}", p::updateParkingModel, auth::settingsWrite, write::handle);

        // 宠物风控日志（严格只读）
        Dispatcher.get("/api/v3/admin/pets/logs", p::listPetLogs, auth::profileRead);
        Dispatcher.get("/api/v3/admin/pets", p::listPets, auth::profileRead);
        Dispatcher.get("/api/v3/admin/pets/coffee", p::getPetCoffee, auth::profileRead);
        Dispatcher.put("/api/v3/admin/pets/coffee", p::updatePetCoffee, auth::settingsWrite, write::handle);
        Dispatcher.put("/api/v3/admin/pets/{petId}/moderate-name", p::moderatePetName, auth::settingsWrite, write::handle);

        // IP 封禁与白名单管理
        Dispatcher.get("/api/v3/admin/ips", p::getIpPolicyStatus, auth::profileRead);
        Dispatcher.post("/api/v3/admin/ips", p::mutateIps, auth::settingsWrite, write::handle);

        // 站点杂项配置
        Dispatcher.get("/api/v3/admin/settings", p::listMiscSettings, auth::profileRead);
        Dispatcher.put("/api/v3/admin/settings", p::updateMiscSettings, auth::settingsWrite, write::handle);

        // 安全监控、自检与开发模式开关
        Dispatcher.get("/api/v3/admin/security/monitor", p::getSecurityMonitor, auth::profileRead);
        Dispatcher.get("/api/v3/admin/security/self-check", p::runSecuritySelfCheck, auth::profileRead);
        Dispatcher.post("/api/v3/admin/security/switches", p::saveSecuritySwitches, auth::settingsWrite, write::handle);

        Dispatcher.get("/api/v3/admin/tasks/{taskId}", p::getTask, auth::profileRead);

        // 搜索索引维护工具
        Dispatcher.post("/api/v3/admin/articles/reindex", p::reindexAllArticles, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/articles/{articleId}/reindex", p::reindexOneArticle, auth::settingsWrite, write::handle);

        // 职业配置工作台
        registerProfessionRoutes(p, auth, write);
    }

    private static void registerProfessionRoutes(final ApiV3AdminStage4Processor p, final ApiV3AuthMidware auth, final ApiV3WriteMidware write) {
        Dispatcher.get("/api/v3/admin/professions/catalog", p::professionCatalog, auth::profileRead);
        Dispatcher.get("/api/v3/admin/professions/catalog-summary", p::professionCatalogSummary, auth::profileRead);
        Dispatcher.get("/api/v3/admin/professions/{professionId}", p::professionDetail, auth::profileRead);
        Dispatcher.get("/api/v3/admin/professions/{professionId}/schemes/{schemeId}/impact", p::professionSchemeImpact, auth::profileRead);
        Dispatcher.get("/api/v3/admin/professions/config/export", p::professionConfigExport, auth::profileRead);
        Dispatcher.get("/api/v3/admin/professions/definition/metadata", p::professionDefinitionMetadata, auth::profileRead);
        Dispatcher.get("/api/v3/admin/professions/automation/metadata", p::professionAutomationMetadata, auth::profileRead);

        Dispatcher.post("/api/v3/admin/professions/config/precheck", p::professionConfigPrecheck, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/config/import", p::professionConfigImport, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/catalog/order", p::professionReorderCatalog, auth::settingsWrite, write::handle);

        Dispatcher.post("/api/v3/admin/professions/definition/draft", p::professionCreateDefinition, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/definition/publish", p::professionPublishDefinition, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/definition/retire", p::professionRetireDefinition, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/definition/copy", p::professionCopyDefinition, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/definition/rollback", p::professionRollbackDefinition, auth::settingsWrite, write::handle);

        Dispatcher.post("/api/v3/admin/professions/scheme/draft", p::professionCreateScheme, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/scheme/publish", p::professionPublishScheme, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/scheme/retire", p::professionRetireScheme, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/scheme/copy", p::professionCopyScheme, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/scheme/rollback", p::professionRollbackScheme, auth::settingsWrite, write::handle);

        Dispatcher.post("/api/v3/admin/professions/automation/draft", p::professionCreateAutomation, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/automation/publish", p::professionPublishAutomation, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/automation/retire", p::professionRetireAutomation, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/automation/copy", p::professionCopyAutomation, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/automation/rollback", p::professionRollbackAutomation, auth::settingsWrite, write::handle);
        Dispatcher.post("/api/v3/admin/professions/automation/test", p::professionTestAutomation, auth::settingsWrite, write::handle);
    }

    public void listPets(final RequestContext context) {
        ApiV3Requests.read(context, () -> petService.list(identity(context),
                ApiV3Requests.text(context.param("q"), 64, false), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    @Inject private org.b3log.symphony.service.PartnerOperationsService partners;

    public void partnerReport(final RequestContext context) {
        ApiV3Requests.read(context, () -> partners.report(identity(context)));
    }

    public void partnerLedger(final RequestContext context) {
        ApiV3Requests.read(context, () -> partners.ledger(identity(context), context.param("userId"), context.param("referenceId"),
                ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void partnerRate(final RequestContext context) {
        ApiV3Requests.respond(context, () -> partners.update(identity(context),
                ApiV3WriteInputs.body(context, Set.of("baseRate","stepRate","version"), false, 512),
                ApiV3WriteInputs.idempotencyKey(context), context.getRequest()));
    }

    @Inject private org.b3log.symphony.service.ParkingCatalogService parkingCatalog;

    public void parkingModels(final RequestContext context) {
        ApiV3Requests.read(context, () -> parkingCatalog.read(identity(context), false, null, 1, 1));
    }

    @Inject private org.b3log.symphony.service.ParkingMetricsService metrics;

    public void parkingMetrics(final RequestContext context) {
        ApiV3Requests.read(context, () -> metrics.report(identity(context)));
    }

    public void parkingLedger(final RequestContext context) {
        ApiV3Requests.read(context, () -> parkingCatalog.read(identity(context), true, context.param("userId"),
                ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void updateParkingModel(final RequestContext context) {
        ApiV3Requests.respond(context, () -> parkingCatalog.update(identity(context), context.pathVar("model"),
                ApiV3WriteInputs.body(context, Set.of("enabled","rate","price","version"), false, 2048),
                ApiV3WriteInputs.idempotencyKey(context), context.getRequest()));
    }

    public void getPetCoffee(final RequestContext context) {
        ApiV3Requests.read(context, () -> petService.coffeeSettings(identity(context)));
    }

    public void updatePetCoffee(final RequestContext context) {
        ApiV3Requests.respond(context, () -> petService.updateCoffee(identity(context),
                ApiV3WriteInputs.body(context, Set.of("enabled", "price", "version"), false, 2048),
                ApiV3WriteInputs.idempotencyKey(context), context.getRequest()));
    }

    public void moderatePetName(final RequestContext context) {
        ApiV3Requests.respond(context, () -> petService.moderateName(identity(context), context.pathVar("petId"),
                ApiV3WriteInputs.body(context, Set.of("expectedName"), false, 2048),
                ApiV3WriteInputs.idempotencyKey(context), context.getRequest()));
    }

    public void listPetLogs(final RequestContext context) {
        ApiV3Requests.read(context, () -> systemService.listPetLogs(
                identity(context), context.param("tab"), context.param("user"),
                context.param("from"), context.param("to"), context.param("operation"), context.param("p")));
    }

    public void getIpPolicyStatus(final RequestContext context) {
        ApiV3Requests.read(context, () -> systemService.getIpPolicyStatus(identity(context)));
    }

    public void mutateIps(final RequestContext context) {
        ApiV3Requests.respond(context, () -> systemService.mutateIps(
                identity(context), body(context, IP_MUTATE_FIELDS, MAX_WRITE_BODY_BYTES),
                ApiV3WriteInputs.idempotencyKey(context), context));
    }

    public void listMiscSettings(final RequestContext context) {
        ApiV3Requests.read(context, () -> systemService.listMiscSettings(identity(context)));
    }

    public void updateMiscSettings(final RequestContext context) {
        ApiV3Requests.respond(context, () -> systemService.updateMiscSettings(
                identity(context), body(context, MISC_SETTINGS_FIELDS, MAX_WRITE_BODY_BYTES),
                ApiV3WriteInputs.idempotencyKey(context), context.getRequest()));
    }

    public void getSecurityMonitor(final RequestContext context) {
        ApiV3Requests.read(context, () -> systemService.getSecurityMonitor(identity(context), context.param("crawler")));
    }

    public void runSecuritySelfCheck(final RequestContext context) {
        ApiV3Requests.read(context, () -> systemService.runSecuritySelfCheck(identity(context)));
    }

    public void saveSecuritySwitches(final RequestContext context) {
        ApiV3Requests.respond(context, () -> systemService.saveSecuritySwitches(
                identity(context), body(context, SECURITY_SWITCH_FIELDS, MAX_WRITE_BODY_BYTES),
                ApiV3WriteInputs.idempotencyKey(context)));
    }

    public void getTask(final RequestContext context) {
        ApiV3Requests.read(context, () -> systemService.getTask(identity(context), ApiV3Requests.id(context, "taskId")));
    }

    public void reindexAllArticles(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, 1024);
            return systemService.reindexAllArticles(identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest());
        });
    }

    public void reindexOneArticle(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, 1024);
            return systemService.reindexOneArticle(identity(context), ApiV3Requests.id(context, "articleId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest());
        });
    }

    public void professionCatalog(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            professionAdminService.requireAdminOperatorId(identity(context), "profile.read");
            return professionAdminService.executeDomainAction(() -> new JSONObject().put("items", professionQueryService.catalog()));
        });
    }

    public void professionCatalogSummary(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            professionAdminService.requireAdminOperatorId(identity(context), "profile.read");
            return professionAdminService.executeDomainAction(
                    () -> professionQueryService.catalogSummary(ProfessionAdminParameterMapper.catalogQuery(context)));
        });
    }

    public void professionDetail(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            professionAdminService.requireAdminOperatorId(identity(context), "profile.read");
            return professionAdminService.executeDomainAction(
                    () -> professionQueryService.detail(ApiV3Requests.id(context, "professionId")));
        });
    }

    public void professionSchemeImpact(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            professionAdminService.requireAdminOperatorId(identity(context), "profile.read");
            return professionAdminService.executeDomainAction(() -> schemeImpactService.preview(
                    ApiV3Requests.id(context, "professionId"), ApiV3Requests.id(context, "schemeId")));
        });
    }

    public void professionConfigExport(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            professionAdminService.requireAdminOperatorId(identity(context), "profile.read");
            final String professionId = StringUtils.trimToNull(context.param("professionId"));
            return professionAdminService.executeDomainAction(() -> null == professionId
                    ? configExportService.exportAll() : configExportService.exportOne(professionId));
        });
    }

    public void professionDefinitionMetadata(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            professionAdminService.requireAdminOperatorId(identity(context), "profile.read");
            return professionAdminService.executeDomainAction(definitionRegistry::presentationMetadata);
        });
    }

    public void professionAutomationMetadata(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            professionAdminService.requireAdminOperatorId(identity(context), "profile.read");
            return professionAdminService.executeDomainAction(automationRegistry::editorMetadata);
        });
    }

    public void professionConfigPrecheck(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final JSONObject payload = rawBody(context, Set.of("configJson"));
            return professionAdminService.executeDomainAction(() -> configPreviewService.preview(
                    requiredText(payload, "configJson")));
        });
    }

    public void professionConfigImport(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final JSONObject payload = rawBody(context, Set.of("configJson"));
            return professionAdminService.executeDomainAction(() -> configImportService.importConfig(
                    requiredText(payload, "configJson"), operatorId));
        });
    }

    public void professionReorderCatalog(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final JSONObject payload = rawBody(context, Set.of("professionIds"));
            final JSONArray rawIds = payload.optJSONArray("professionIds");
            if (rawIds == null) {
                throw new ApiV3Exception(422, "invalid_request", "professionIds 不能为空");
            }
            final List<String> ids = new ArrayList<>();
            if (rawIds.length() > 1_000) throw ApiV3WriteInputs.invalid();
            for (int i = 0; i < rawIds.length(); i++) {
                if (!(rawIds.opt(i) instanceof String)) throw ApiV3WriteInputs.invalid();
                final String id = StringUtils.trimToEmpty(rawIds.optString(i));
                if (!id.matches("[0-9]{1,19}")) throw ApiV3WriteInputs.invalid();
                ids.add(id);
            }
            return professionAdminService.executeDomainAction(() -> {
                orderService.reorder(ids);
                return new JSONObject().put("reordered", true);
            });
        });
    }

    public void professionCreateDefinition(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final JSONObject payload = rawBody(context, Set.of("professionCode", "displayName", "shortName", "description", "defaultPresentationJson"));
            return professionAdminService.executeDomainAction(() -> new JSONObject().put("id",
                    definitionService.createProfessionDraft(ProfessionAdminRequestMapper.definition(payload, operatorId))));
        });
    }

    public void professionPublishDefinition(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final String revisionId = requiredText(rawBody(context, Set.of("revisionId")), "revisionId");
            return professionAdminService.executeDomainAction(() -> {
                definitionService.publishProfession(revisionId, operatorId);
                return new JSONObject().put("published", true);
            });
        });
    }

    public void professionRetireDefinition(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final String professionId = requiredText(rawBody(context, Set.of("professionId")), "professionId");
            return professionAdminService.executeDomainAction(() -> {
                definitionService.retireProfession(professionId, operatorId);
                return new JSONObject().put("retired", true);
            });
        });
    }

    public void professionCopyDefinition(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final String revisionId = requiredText(rawBody(context, Set.of("revisionId")), "revisionId");
            return professionAdminService.executeDomainAction(() -> new JSONObject().put("id",
                    definitionService.copyProfessionDraft(revisionId, operatorId)));
        });
    }

    public void professionRollbackDefinition(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final JSONObject payload = rawBody(context, Set.of("professionId", "revisionId"));
            return professionAdminService.executeDomainAction(() -> new JSONObject().put("id",
                    definitionService.rollbackProfession(
                            requiredText(payload, "professionId"), requiredText(payload, "revisionId"), operatorId)));
        });
    }

    public void professionCreateScheme(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final JSONObject payload = rawBody(context, Set.of("professionId", "professionRevisionId", "migrationPolicy", "rewardMigrationPolicy", "migrationConfigJson", "levels"));
            return professionAdminService.executeDomainAction(() -> new JSONObject().put("id",
                    definitionService.createLevelSchemeDraft(ProfessionAdminRequestMapper.scheme(payload, operatorId))));
        });
    }

    public void professionPublishScheme(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final String schemeId = requiredText(rawBody(context, Set.of("schemeId")), "schemeId");
            return professionAdminService.executeDomainAction(() -> {
                schemeService.publish(schemeId, operatorId);
                return new JSONObject().put("published", true);
            });
        });
    }

    public void professionRetireScheme(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final String schemeId = requiredText(rawBody(context, Set.of("schemeId")), "schemeId");
            return professionAdminService.executeDomainAction(() -> {
                schemeService.retire(schemeId, operatorId);
                return new JSONObject().put("retired", true);
            });
        });
    }

    public void professionCopyScheme(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final String schemeId = requiredText(rawBody(context, Set.of("schemeId")), "schemeId");
            return professionAdminService.executeDomainAction(() -> new JSONObject().put("id",
                    schemeService.copyDraft(schemeId, operatorId)));
        });
    }

    public void professionRollbackScheme(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final JSONObject payload = rawBody(context, Set.of("professionId", "schemeId"));
            return professionAdminService.executeDomainAction(() -> new JSONObject().put("id",
                    schemeService.rollback(
                            requiredText(payload, "professionId"), requiredText(payload, "schemeId"), operatorId)));
        });
    }

    public void professionCreateAutomation(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final JSONObject payload = rawBody(context, Set.of("professionId", "automationCode", "configurationJson"));
            final var req = new org.b3log.symphony.service.ProfessionAutomationDraftRequest(
                    requiredText(payload, "professionId"),
                    requiredText(payload, "automationCode"),
                    requiredText(payload, "configurationJson"),
                    operatorId);
            return professionAdminService.executeDomainAction(() -> new JSONObject().put("id",
                    automationService.createDraft(req)));
        });
    }

    public void professionPublishAutomation(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final String revisionId = requiredText(rawBody(context, Set.of("revisionId")), "revisionId");
            return professionAdminService.executeDomainAction(() -> {
                automationService.publish(revisionId, operatorId);
                return new JSONObject().put("published", true);
            });
        });
    }

    public void professionRetireAutomation(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final String automationId = requiredText(rawBody(context, Set.of("automationId")), "automationId");
            return professionAdminService.executeDomainAction(() -> {
                automationService.retire(automationId, operatorId);
                return new JSONObject().put("retired", true);
            });
        });
    }

    public void professionCopyAutomation(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final String revisionId = requiredText(rawBody(context, Set.of("revisionId")), "revisionId");
            return professionAdminService.executeDomainAction(() -> new JSONObject().put("id",
                    automationService.copy(revisionId, operatorId)));
        });
    }

    public void professionRollbackAutomation(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String operatorId = professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final JSONObject payload = rawBody(context, Set.of("automationId", "revisionId"));
            return professionAdminService.executeDomainAction(() -> new JSONObject().put("id",
                    automationService.rollback(
                            requiredText(payload, "automationId"), requiredText(payload, "revisionId"), operatorId)));
        });
    }

    public void professionTestAutomation(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            professionAdminService.requireAdminOperatorId(identity(context), "settings.write");
            final JSONObject payload = rawBody(context, Set.of("configurationJson", "payloadJson"));
            return professionAdminService.executeDomainAction(() -> automationTestService.test(
                    requiredText(payload, "configurationJson"), requiredText(payload, "payloadJson")));
        });
    }

    private static JSONObject identity(final RequestContext context) {
        return (JSONObject) context.attr(ApiV3Processor.IDENTITY);
    }

    private static JSONObject body(final RequestContext context, final Set<String> allowedFields, final int maxBytes) {
        return ApiV3WriteInputs.body(context, allowedFields, false, maxBytes);
    }

    private static JSONObject rawBody(final RequestContext context, final Set<String> fields) {
        final JSONObject payload = ApiV3WriteInputs.body(context, fields, false, 16 * 1024 * 1024);
        org.b3log.symphony.util.ApiV3ProfessionInputs.validate(payload);
        return payload;
    }

    private static String requiredText(final JSONObject payload, final String key) {
        final String value = StringUtils.trimToNull(ApiV3WriteInputs.string(payload, key, 16_000_000, true));
        if (value == null || (key.endsWith("Id") && !value.matches("[0-9]{1,19}"))) {
            throw new ApiV3Exception(422, "invalid_request", key + " 不能为空");
        }
        return value;
    }
}
