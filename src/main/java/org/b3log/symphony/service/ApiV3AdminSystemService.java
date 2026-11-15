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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.util.AnonymousCaptchaShield;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.symphony.util.CrawlerMonitor;
import org.b3log.symphony.util.Firewall;
import org.b3log.symphony.util.SecurityMonitor;
import org.b3log.symphony.util.SecuritySelfCheck;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理后台 v3 第四阶段系统、安全、宠物日志与维护工具服务。
 */
@Service
public class ApiV3AdminSystemService {

    private static final Logger LOGGER = LogManager.getLogger(ApiV3AdminSystemService.class);
    private static final String SECURITY_OPERATOR_NAME = "adlered";
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Inject
    private ApiV3AdminService adminService;

    @Inject
    private AdminOperationTaskService tasks;

    @Inject
    private PetAdminLogQueryService petAdminLogQueryService;

    @Inject
    private AdminMiscSettingsService miscSettings;

    @Inject
    private ArticleQueryService articleQueryService;

    @Inject
    private SearchMgmtService searchMgmtService;

    /**
     * 只读查询宠物活动与风控日志（仅管理员可用，不提供改币或重置操作）。
     */
    public JSONObject listPetLogs(final JSONObject identity, final String tab, final String user,
                                  final String from, final String to, final String operation, final String page) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(identity, "profile.read", null, true);
            tx.commit();
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("校验宠物日志查询权限失败", e);
            throw adminService.unavailable();
        } finally {
            adminService.rollbackIfActive(tx);
        }

        final LocalDate today = LocalDate.now(SHANGHAI_ZONE);
        final PetAdminLogQueryService.Filters filters;
        try {
            filters = PetAdminLogQueryService.filters(tab, user, from, to, operation, page, today);
        } catch (final IllegalArgumentException e) {
            throw invalidRequest(e.getMessage());
        }

        final PetAdminLogQueryService.Page result;
        try {
            result = petAdminLogQueryService.list(filters);
        } catch (final RepositoryException e) {
            LOGGER.warn("读取宠物日志失败", e);
            throw new ApiV3Exception(503, "pet_logs_unavailable", "宠物日志暂时无法读取，请检查相关数据表是否已迁移");
        }

        final JSONArray rows = new JSONArray();
        for (final var row : result.rows()) {
            rows.put(new JSONObject(row));
        }
        return new JSONObject()
                .put("tab", filters.tab())
                .put("user", filters.user() == null ? "" : filters.user())
                .put("from", filters.from() == null ? "" : filters.from())
                .put("to", filters.to() == null ? "" : filters.to())
                .put("operation", filters.operation() == null ? "" : filters.operation())
                .put("operations", new JSONObject(PetAdminLogQueryService.operations(filters.tab())))
                .put("page", filters.page())
                .put("hasNext", result.hasNext())
                .put("rows", rows);
    }

    /**
     * 查询防火墙与验证码盾当前运行状态摘要。
     */
    public JSONObject getIpPolicyStatus(final JSONObject identity) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_IP_MANAGE, false);
            tx.commit();
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询 IP 管理状态失败", e);
            throw adminService.unavailable();
        } finally {
            adminService.rollbackIfActive(tx);
        }

        return new JSONObject()
                .put("firewallEnabled", Firewall.isEnabled())
                .put("threshold", Firewall.getThreshold())
                .put("defaultThreshold", Firewall.getDefaultThreshold())
                .put("verificationEnabled", AnonymousCaptchaShield.isEnabled())
                .put("firstVisitCaptchaEnabled", AnonymousCaptchaShield.isFirstVisitCaptchaEnabled());
    }

    /**
     * 批量封禁或解封 IP，严格保留旧版防火墙与验证码盾调用顺序并记录操作日志。
     */
    public JSONObject mutateIps(final JSONObject identity, final JSONObject body,
                                final String idempotencyKey, final RequestContext context) {
        final String action = ApiV3WriteInputs.string(body, "action", 5, true);
        if (!"ban".equals(action) && !"unban".equals(action)) throw ApiV3WriteInputs.invalid();
        final JSONArray raw = body.optJSONArray("ipList");
        if (raw == null || raw.isEmpty() || raw.length() > 500) throw ApiV3WriteInputs.invalid();
        final java.util.Set<String> addresses = new java.util.LinkedHashSet<>();
        for (final Object value : raw) {
            if (!(value instanceof String text)) throw ApiV3WriteInputs.invalid();
            final String ip = text.trim();
            if (ip.length() > 45 || ip.contains("%") || ip.contains("[") || ip.contains("]")
                    || !(io.netty.util.NetUtil.isValidIpV4Address(ip) || io.netty.util.NetUtil.isValidIpV6Address(ip))) {
                throw ApiV3WriteInputs.invalid();
            }
            try {
                addresses.add(java.net.InetAddress.getByAddress(io.netty.util.NetUtil.createByteArrayFromIpAddressString(ip)).getHostAddress());
            } catch (final java.net.UnknownHostException e) { throw ApiV3WriteInputs.invalid(); }
        }
        return enqueue(identity, Permission.PERMISSION_ID_C_IP_MANAGE, false, "admin.ips.mutate", idempotencyKey,
                "IP", new JSONObject().put("action", action).put("ipList", addresses), context.getRequest(), null);
    }

    /**
     * 读取站点杂项配置列表（`menuAdminMisc` 权限）。
     */
    public JSONObject listMiscSettings(final JSONObject identity) {
        final Transaction tx = adminService.begin();
        try {
            final var actor = adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_MISC, false);
            final JSONArray options = miscSettings.list(actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN)
                    ? actor.permissions() : java.util.Set.of());
            tx.commit();
            return new JSONObject().put("options", options);
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("校验站点杂项配置读取权限失败", e);
            throw adminService.unavailable();
        } finally {
            adminService.rollbackIfActive(tx);
        }

    }

    /**
     * 更新站点杂项配置并写入 `Operation` 审计记录。
     */
    public JSONObject updateMiscSettings(final JSONObject identity, final JSONObject body,
                                           final String idempotencyKey, final Request request) {
        if (body == null) {
            throw ApiV3WriteInputs.invalid();
        }
        final JSONObject optionsMap = body.optJSONObject("options");
        if (optionsMap == null || optionsMap.isEmpty()) {
            throw invalidRequest("options 不能为空");
        }

        final String actorId = adminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            final ApiV3MutationStore store = adminService.guardedWrite(
                    identity, Permission.PERMISSION_ID_C_MENU_ADMIN_MISC, false, actorRef);
            execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    store,
                    actorId,
                    "admin.settings.misc.update",
                    idempotencyKey,
                    new JSONObject().put("options", optionsMap),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final int updated = miscSettings.updateInCurrentTransaction(optionsMap, actorRef.get().permissions());
                        adminService.recordOperation(
                                request, Operation.OPERATION_CODE_C_UPDATE_MISC, "", actorRef.get().user());
                        return new JSONObject()
                                .put("updatedCount", updated)
                                .put("updatedAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新站点杂项配置失败", e);
            throw adminService.unavailable();
        }

        if (execution.applied()) miscSettings.afterCommit(optionsMap);
        final JSONArray freshOptions = miscSettings.list(actorRef.get().permissions());
        return new JSONObject(execution.result().toString())
                .put("replayed", !execution.applied())
                .put("options", freshOptions);
    }

    /**
     * 查询安全监控面板快照（仅安全操作员 `adlered` + 管理员可用）。
     */
    public JSONObject getSecurityMonitor(final JSONObject identity, final String crawler) {
        authorizeSecurityOperator(identity, "profile.read");
        return new JSONObject(SecurityMonitor.snapshot())
                .put("crawlers", CrawlerMonitor.snapshot(crawler))
                .put("events", SecurityMonitor.recentEvents())
                .put("switches", currentSecuritySwitches());
    }

    /**
     * 执行安全自检（仅安全操作员 `adlered` + 管理员可用）。
     */
    public JSONObject runSecuritySelfCheck(final JSONObject identity) {
        authorizeSecurityOperator(identity, "profile.read");
        return new JSONObject(SecuritySelfCheck.run());
    }

    /**
     * 在开发模式下原子更新临时安全开关（生产模式严格只读返回 403）。
     */
    public JSONObject saveSecuritySwitches(final JSONObject identity, final JSONObject body,
                                           final String idempotencyKey) {
        authorizeSecurityOperator(identity, "settings.write");
        if (Latkes.RuntimeMode.DEVELOPMENT != Latkes.getRuntimeMode()) {
            throw new ApiV3Exception(403, "security_switches_readonly", "生产模式下安全开关为只读状态");
        }
        // 要求完整快照，幂等摘要不能包含随运行状态变化的默认值。
        if (!body.keySet().equals(java.util.Set.of("threshold", "firewallEnabled", "verificationEnabled", "firstVisitCaptchaEnabled"))) {
            throw ApiV3WriteInputs.invalid();
        }
        final JSONObject input = new JSONObject()
                .put("threshold", ApiV3WriteInputs.integer(body, "threshold", -1, 1, Integer.MAX_VALUE))
                .put("firewallEnabled", ApiV3WriteInputs.bool(body, "firewallEnabled", false))
                .put("verificationEnabled", ApiV3WriteInputs.bool(body, "verificationEnabled", false))
                .put("firstVisitCaptchaEnabled", ApiV3WriteInputs.bool(body, "firstVisitCaptchaEnabled", false));
        return enqueue(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_MISC, true, "admin.security.switches.save",
                idempotencyKey, "SWITCHES", input, null, null);
    }

    /**
     * 重建单篇文章搜索索引并记录审计日志。
     */
    public JSONObject reindexOneArticle(final JSONObject identity, final String articleId,
                                        final String idempotencyKey, final Request request) {
        return enqueue(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false,
                "admin.articles.reindex:" + articleId, idempotencyKey, "REINDEX_ONE",
                new JSONObject().put("articleId", articleId), request,
                Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLE_INDEX);
    }

    /**
     * 触发全站文章搜索索引异步重建并记录审计日志。
     */
    public JSONObject reindexAllArticles(final JSONObject identity, final String idempotencyKey,
                                         final Request request) {
        return enqueue(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false,
                "admin.articles.reindexAll", idempotencyKey, "REINDEX_ALL", new JSONObject().put("scope", "all"),
                request, Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLES_INDEX);
    }

    private JSONObject enqueue(final JSONObject identity, final String permission, final boolean adminOnly,
                               final String operation, final String key, final String kind, final JSONObject input,
                               final Request request, final String extraPermission) {
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            final ApiV3IdempotencyMgmtService.Execution execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    adminService.guardedWrite(identity, permission, adminOnly, actorRef), adminService.actorId(identity),
                    operation, key, input, () -> {
                        final var actor = actorRef.get();
                        if (extraPermission != null && !actor.permissions().contains(extraPermission)) throw ApiV3Requests.forbidden();
                        if ("REINDEX_ONE".equals(kind) && articleQueryService.getArticle(input.getString("articleId")) == null) {
                            throw ApiV3Requests.notFound();
                        }
                        final JSONObject accepted = tasks.createInCurrentTransaction(actor.userId(), kind, input);
                        if (kind.startsWith("REINDEX")) {
                            adminService.recordOperation(request, "REINDEX_ALL".equals(kind)
                                    ? Operation.OPERATION_CODE_C_REBUILD_ARTICLES_SEARCH
                                    : Operation.OPERATION_CODE_C_REBUILD_ARTICLE_SEARCH,
                                    input.optString("articleId"), actor.user());
                        }
                        return accepted;
                    });
            if (extraPermission != null && !actorRef.get().permissions().contains(extraPermission)) {
                throw ApiV3Requests.forbidden();
            }
            if (!execution.result().has("taskId")) {
                return new JSONObject(execution.result().toString()).put("replayed", true).put("status", "LEGACY_UNVERIFIED");
            }
            final String id = execution.result().getString("taskId");
            tasks.dispatch(id);
            // 外部任务结果独立查询，响应只确认已持久受理，不把调度或索引结果冒充已完成。
            return new JSONObject(execution.result().toString()).put("replayed", !execution.applied())
                    .put("statusUrl", "/api/v3/admin/tasks/" + id);
        } catch (final ApiV3Exception e) { throw e; }
        catch (final Exception e) {
            LOGGER.error("后台任务受理失败，kind={}", kind, e);
            throw adminService.unavailable();
        }
    }

    public JSONObject getTask(final JSONObject identity, final String id) {
        final Transaction tx = adminService.begin();
        try {
            final JSONObject task = tasks.read(id);
            if (task == null) throw ApiV3Requests.notFound();
            final String kind = task.getString("kind");
            final String permission = "IP".equals(kind) ? Permission.PERMISSION_ID_C_IP_MANAGE
                    : "SWITCHES".equals(kind) ? Permission.PERMISSION_ID_C_MENU_ADMIN_MISC
                    : Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES;
            final var actor = adminService.authorizeInCurrentTransaction(identity, "profile.read", permission, "SWITCHES".equals(kind));
            if (!actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN)) throw ApiV3Requests.forbidden();
            if ("SWITCHES".equals(kind) && !SECURITY_OPERATOR_NAME.equals(actor.userName())) throw ApiV3Requests.forbidden();
            if (kind.startsWith("REINDEX") && !actor.permissions().contains("REINDEX_ALL".equals(kind)
                    ? Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLES_INDEX
                    : Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLE_INDEX)) throw ApiV3Requests.forbidden();
            tx.commit();
            tasks.wake();
            return task;
        } catch (final ApiV3Exception e) { throw e; }
        catch (final Exception e) { throw adminService.unavailable(); }
        finally { adminService.rollbackIfActive(tx); }
    }

    private void authorizeSecurityOperator(final JSONObject identity, final String scope) {
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor = adminService.authorizeInCurrentTransaction(
                    identity, scope, Permission.PERMISSION_ID_C_MENU_ADMIN_MISC, true);
            if (!SECURITY_OPERATOR_NAME.equals(actor.userName())) {
                throw ApiV3Requests.forbidden();
            }
            tx.commit();
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("校验安全操作员权限失败", e);
            throw adminService.unavailable();
        } finally {
            adminService.rollbackIfActive(tx);
        }
    }

    private static ApiV3Exception invalidRequest(final String message) {
        return new ApiV3Exception(422, "invalid_request", message);
    }

    private static JSONObject currentSecuritySwitches() {
        return new JSONObject()
                .put("firewallEnabled", Firewall.isEnabled())
                .put("threshold", Firewall.getThreshold())
                .put("verificationEnabled", AnonymousCaptchaShield.isEnabled())
                .put("firstVisitCaptchaEnabled", AnonymousCaptchaShield.isFirstVisitCaptchaEnabled())
                .put("runtimeMode", Latkes.getRuntimeMode().name())
                .put("editable", Latkes.RuntimeMode.DEVELOPMENT == Latkes.getRuntimeMode());
    }
}
