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
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.util.AnonymousCaptchaShield;
import org.b3log.symphony.util.ApiV3Exception;
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
    private PetAdminLogQueryService petAdminLogQueryService;

    @Inject
    private OptionQueryService optionQueryService;

    @Inject
    private OptionMgmtService optionMgmtService;

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
            throw ApiV3WriteInputs.invalid(e.getMessage());
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
        if (body == null) {
            throw ApiV3WriteInputs.invalid();
        }
        final String action = StringUtils.trimToEmpty(body.optString("action"));
        if (!"ban".equals(action) && !"unban".equals(action)) {
            throw ApiV3WriteInputs.invalid("action 仅支持 ban 或 unban");
        }
        final JSONArray rawIps = body.optJSONArray("ipList");
        if (rawIps == null || rawIps.isEmpty() || rawIps.length() > 500) {
            throw ApiV3WriteInputs.invalid("ipList 不能为空且单次最多 500 条");
        }
        final List<String> ipList = new ArrayList<>();
        for (int i = 0; i < rawIps.length(); i++) {
            final String ip = StringUtils.trimToEmpty(rawIps.optString(i));
            if (!ip.isEmpty()) {
                ipList.add(ip);
            }
        }
        if (ipList.isEmpty()) {
            throw ApiV3WriteInputs.invalid("ipList 不含有效 IP");
        }

        final String actorId = adminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            final ApiV3MutationStore store = adminService.guardedWrite(
                    identity, Permission.PERMISSION_ID_C_IP_MANAGE, false, actorRef);
            execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    store,
                    actorId,
                    "admin.ips.mutate",
                    idempotencyKey,
                    new JSONObject().put("action", action).put("ipList", ipList),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final String operatorName = actorRef.get().userName();
                        if ("unban".equals(action)) {
                            for (final String ip : ipList) {
                                Firewall.unbanIp(ip);
                                // 验证码盾解封须在系统解封之后；未经阿达明确允许，不得修改、移动、删除或绕过此调用。
                                AnonymousCaptchaShield.administratorUnban(ip);
                                LogsService.simpleLog(context, "解封IP", "操作员: " + operatorName + ", IP: " + ip);
                            }
                        } else {
                            for (final String ip : ipList) {
                                // 系统封禁前撤销验证码豁免；未经阿达明确允许，不得修改、移动、删除或绕过此调用。
                                AnonymousCaptchaShield.removeWhitelist(ip);
                                Firewall.banIp(ip);
                                LogsService.simpleLog(context, "封禁IP", "操作员: " + operatorName + ", IP: " + ip);
                            }
                        }
                        return new JSONObject()
                                .put("action", action)
                                .put("count", ipList.size())
                                .put("processedAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("批量处理 IP 策略失败, action={}", action, e);
            throw adminService.unavailable();
        }

        return new JSONObject(execution.result().toString()).put("replayed", !execution.applied());
    }

    /**
     * 读取站点杂项配置列表（`menuAdminMisc` 权限）。
     */
    public JSONObject listMiscSettings(final JSONObject identity) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_MISC, false);
            tx.commit();
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("校验站点杂项配置读取权限失败", e);
            throw adminService.unavailable();
        } finally {
            adminService.rollbackIfActive(tx);
        }

        final JSONArray options = new JSONArray();
        for (final JSONObject row : optionQueryService.getMisc()) {
            options.put(new JSONObject(row.toString()));
        }
        return new JSONObject().put("options", options);
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
            throw ApiV3WriteInputs.invalid("options 不能为空");
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
                        for (final String optionId : optionsMap.keySet()) {
                            final String trimmedId = StringUtils.trimToEmpty(optionId);
                            if (trimmedId.isEmpty()) {
                                continue;
                            }
                            final JSONObject option = new JSONObject();
                            option.put(Keys.OBJECT_ID, trimmedId);
                            option.put(Option.OPTION_VALUE, optionsMap.optString(optionId));
                            option.put(Option.OPTION_CATEGORY, Option.CATEGORY_C_MISC);
                            optionMgmtService.updateOption(trimmedId, option);
                        }
                        adminService.recordOperation(
                                request, Operation.OPERATION_CODE_C_UPDATE_MISC, "", actorRef.get().user());
                        return new JSONObject()
                                .put("updatedCount", optionsMap.length())
                                .put("updatedAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新站点杂项配置失败", e);
            throw adminService.unavailable();
        }

        final JSONArray freshOptions = new JSONArray();
        for (final JSONObject row : optionQueryService.getMisc()) {
            freshOptions.put(new JSONObject(row.toString()));
        }
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
        if (body == null) {
            throw ApiV3WriteInputs.invalid();
        }
        if (Latkes.RuntimeMode.DEVELOPMENT != Latkes.getRuntimeMode()) {
            throw new ApiV3Exception(403, "security_switches_readonly", "生产模式下安全开关为只读状态，禁止在线修改");
        }
        final int threshold = body.optInt("threshold", -1);
        if (threshold <= 0) {
            throw ApiV3WriteInputs.invalid("每分钟请求数 threshold 必须是大于 0 的整数");
        }
        final boolean firewallEnabled = body.optBoolean("firewallEnabled", Firewall.isEnabled());
        final boolean verificationEnabled = body.optBoolean("verificationEnabled", AnonymousCaptchaShield.isEnabled());
        final boolean firstVisitEnabled = body.optBoolean(
                "firstVisitCaptchaEnabled", AnonymousCaptchaShield.isFirstVisitCaptchaEnabled());

        final String actorId = adminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            final ApiV3MutationStore store = adminService.guardedWrite(
                    identity, Permission.PERMISSION_ID_C_MENU_ADMIN_MISC, true, actorRef);
            execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    store,
                    actorId,
                    "admin.security.switches.save",
                    idempotencyKey,
                    new JSONObject()
                            .put("threshold", threshold)
                            .put("firewallEnabled", firewallEnabled)
                            .put("verificationEnabled", verificationEnabled)
                            .put("firstVisitCaptchaEnabled", firstVisitEnabled),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        if (!SECURITY_OPERATOR_NAME.equals(actorRef.get().userName())) {
                            throw ApiV3Requests.forbidden();
                        }
                        Firewall.setThreshold(threshold);
                        Firewall.setEnabled(firewallEnabled);
                        // 未经阿达明确允许，不得修改、移动、删除或绕过验证码盾调用。
                        AnonymousCaptchaShield.setEnabled(verificationEnabled);
                        AnonymousCaptchaShield.setFirstVisitCaptchaEnabled(firstVisitEnabled);
                        SecurityMonitor.record("settings", "update", "统一保存");
                        return currentSecuritySwitches().put("updatedAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("保存安全开关失败", e);
            throw adminService.unavailable();
        }

        return new JSONObject(execution.result().toString()).put("replayed", !execution.applied());
    }

    /**
     * 重建单篇文章搜索索引并记录审计日志。
     */
    public JSONObject reindexOneArticle(final JSONObject identity, final String articleId,
                                        final String idempotencyKey, final Request request) {
        final String actorId = adminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        final AtomicReference<JSONObject> articleRef = new AtomicReference<>();
        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            final ApiV3MutationStore store = adminService.guardedWrite(
                    identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false, actorRef);
            execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    store,
                    actorId,
                    "admin.articles.reindex:" + articleId,
                    idempotencyKey,
                    new JSONObject().put("articleId", articleId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.permissions().contains(Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLE_INDEX)) {
                            throw ApiV3Requests.forbidden();
                        }
                        final JSONObject article = articleQueryService.getArticle(articleId);
                        if (article == null) {
                            throw ApiV3Requests.notFound();
                        }
                        articleRef.set(new JSONObject(article.toString()));
                        adminService.recordOperation(
                                request, Operation.OPERATION_CODE_C_REBUILD_ARTICLE_SEARCH, articleId, actor.user());
                        return new JSONObject()
                                .put("articleId", articleId)
                                .put("accepted", true)
                                .put("triggeredAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("重建单篇文章搜索索引失败, articleId={}", articleId, e);
            throw adminService.unavailable();
        }

        if (execution.applied() && articleRef.get() != null) {
            final JSONObject article = articleRef.get();
            final int articleType = article.optInt(Article.ARTICLE_TYPE);
            if (Article.ARTICLE_TYPE_C_DISCUSSION != articleType && Article.ARTICLE_TYPE_C_THOUGHT != articleType) {
                if (Symphonys.ALGOLIA_ENABLED) {
                    searchMgmtService.updateAlgoliaDocument(new JSONObject(article.toString()));
                }
                if (Symphonys.ES_ENABLED) {
                    searchMgmtService.updateESDocument(article, Article.ARTICLE);
                }
            }
        }
        return new JSONObject(execution.result().toString()).put("replayed", !execution.applied());
    }

    /**
     * 触发全站文章搜索索引异步重建并记录审计日志。
     */
    public JSONObject reindexAllArticles(final JSONObject identity, final String idempotencyKey,
                                         final Request request) {
        final String actorId = adminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            final ApiV3MutationStore store = adminService.guardedWrite(
                    identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false, actorRef);
            execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    store,
                    actorId,
                    "admin.articles.reindexAll",
                    idempotencyKey,
                    new JSONObject().put("scope", "all"),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.permissions().contains(Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLES_INDEX)) {
                            throw ApiV3Requests.forbidden();
                        }
                        adminService.recordOperation(
                                request, Operation.OPERATION_CODE_C_REBUILD_ARTICLES_SEARCH, "", actor.user());
                        return new JSONObject()
                                .put("accepted", true)
                                .put("triggeredAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("触发全站文章索引重建失败", e);
            throw adminService.unavailable();
        }

        if (execution.applied()) {
            if (Symphonys.ES_ENABLED) {
                searchMgmtService.rebuildESIndex();
            }
            if (Symphonys.ALGOLIA_ENABLED) {
                searchMgmtService.rebuildAlgoliaIndex();
            }
            Symphonys.EXECUTOR_SERVICE.submit(() -> {
                try {
                    final JSONObject stat = optionQueryService.getStatistic();
                    final int articleCount = stat.optInt(Option.ID_C_STATISTIC_ARTICLE_COUNT);
                    final int pages = (int) Math.ceil((double) articleCount / 50.0);
                    for (int pageNum = 1; pageNum <= pages; pageNum++) {
                        final List<JSONObject> articles = articleQueryService.getValidArticles(
                                pageNum, 50, Article.ARTICLE_TYPE_C_NORMAL, Article.ARTICLE_TYPE_C_CITY_BROADCAST);
                        for (final JSONObject article : articles) {
                            if (Symphonys.ALGOLIA_ENABLED) {
                                searchMgmtService.updateAlgoliaDocument(article);
                            }
                            if (Symphonys.ES_ENABLED) {
                                searchMgmtService.updateESDocument(article, Article.ARTICLE);
                            }
                        }
                    }
                    LOGGER.info("V3 全站文章搜索索引重建任务完成");
                } catch (final Exception e) {
                    LOGGER.error("V3 全站文章搜索索引重建失败", e);
                }
            });
        }
        return new JSONObject(execution.result().toString()).put("replayed", !execution.applied());
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
