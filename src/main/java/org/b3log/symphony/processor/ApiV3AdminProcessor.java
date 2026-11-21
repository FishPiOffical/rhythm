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

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3AdminService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

import java.util.Set;

/**
 * API v3 管理后台核心处理器（能力、工作台、举报审核、用户管理、内容管理及操作审计）。
 */
@Singleton
public class ApiV3AdminProcessor {

    private static final int MAX_EMPTY_BODY_BYTES = 1024;
    private static final int MAX_USER_UPDATE_BODY_BYTES = 8192;
    private static final int MAX_CONTENT_WRITE_BODY_BYTES = 512 * 1024;
    private static final Set<String> REPORT_STATUSES = Set.of("all", "pending", "handled", "ignored");
    private static final Set<String> REPORT_DATA_TYPES = Set.of("all", "0", "1", "2", "3", "4");
    private static final Set<String> USER_STATUSES = Set.of("all", "0", "1", "2", "3", "4");
    private static final Set<String> ARTICLE_STATUSES = Set.of("all", "0", "1", "2");
    private static final Set<String> ARTICLE_TYPES = Set.of("all", "0", "1", "2", "3", "5", "6");
    private static final Set<String> BINARY_CONTENT_STATUSES = Set.of("all", "0", "1");

    @Inject
    private ApiV3AdminService adminService;

    /**
     * 声明管理后台全部 V3 路由映射；保持方法体内无条件或循环语句以符合契约审计规范。
     */
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3AdminProcessor admin = beans.getReference(ApiV3AdminProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);

        Dispatcher.get("/api/v3/admin/capabilities", admin::getCapabilities, auth::profileRead);
        Dispatcher.get("/api/v3/admin/dashboard", admin::getDashboard, auth::profileRead);
        Dispatcher.get("/api/v3/admin/reports", admin::getReports, auth::profileRead);
        Dispatcher.post("/api/v3/admin/reports/{reportId}/handle", admin::handleReport, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/reports/{reportId}/ignore", admin::ignoreReport, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/repeater/{id}/remove", admin::removeRepeater, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/users", admin::getUsers, auth::profileRead);
        Dispatcher.get("/api/v3/admin/users/{userId}", admin::getUser, auth::profileRead);
        Dispatcher.put("/api/v3/admin/users/{userId}", admin::updateUser, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/users/{userId}/deactivate", admin::deactivateUser, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/users/{userId}/remove-mfa", admin::removeMfa, auth::settingsWrite, writes::handle);

        Dispatcher.get("/api/v3/admin/articles", admin::getArticles, auth::profileRead);
        Dispatcher.post("/api/v3/admin/articles", admin::addArticle, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/articles/{articleId}", admin::getArticle, auth::profileRead);
        Dispatcher.put("/api/v3/admin/articles/{articleId}", admin::updateArticle, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/articles/{articleId}/stick", admin::stickArticle, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/articles/{articleId}/cancel-stick", admin::cancelStickArticle, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/admin/articles/{articleId}", admin::removeArticle, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/comments", admin::getComments, auth::profileRead);
        Dispatcher.get("/api/v3/admin/comments/{commentId}", admin::getComment, auth::profileRead);
        Dispatcher.put("/api/v3/admin/comments/{commentId}", admin::updateComment, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/admin/comments/{commentId}", admin::removeComment, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/breezemoons", admin::getBreezemoons, auth::profileRead);
        Dispatcher.get("/api/v3/admin/breezemoons/{breezemoonId}", admin::getBreezemoon, auth::profileRead);
        Dispatcher.put("/api/v3/admin/breezemoons/{breezemoonId}", admin::updateBreezemoon, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/admin/breezemoons/{breezemoonId}", admin::removeBreezemoon, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/audit-logs", admin::getAuditLogs, auth::profileRead);
    }

    /**
     * 返回当前第一方登录用户在管理后台的权限矩阵与页面菜单能力。
     */
    public void getCapabilities(final RequestContext context) {
        ApiV3Requests.read(context, () -> adminService.capabilities(identity(context)));
    }

    /**
     * 返回管理后台工作台聚合待办、社区基础统计及最近操作日志。
     */
    public void getDashboard(final RequestContext context) {
        ApiV3Requests.read(context, () -> adminService.dashboard(identity(context)));
    }

    /**
     * 分页查询全站举报列表及上下文。
     */
    public void getReports(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final int p = ApiV3Requests.page(context);
            final int size = ApiV3Requests.size(context);
            final String status = ApiV3Requests.choice(context, "status", "pending", REPORT_STATUSES);
            final String dataType = ApiV3Requests.choice(context, "dataType", "all", REPORT_DATA_TYPES);
            return adminService.getReports(identity(context), p, size, status, dataType);
        });
    }

    /**
     * 标记举报为已处理（携带 Idempotency-Key）。
     */
    public void handleReport(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            checkEmptyWriteBody(context);
            return adminService.handleReport(
                    identity(context),
                    ApiV3Requests.id(context, "reportId"),
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 标记举报为已忽略（携带 Idempotency-Key）。
     */
    public void ignoreReport(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            checkEmptyWriteBody(context);
            return adminService.ignoreReport(
                    identity(context),
                    ApiV3Requests.id(context, "reportId"),
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 移除被举报或违规的复读机内容（携带 Idempotency-Key）。
     */
    public void removeRepeater(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            checkEmptyWriteBody(context);
            return adminService.removeRepeater(
                    identity(context),
                    ApiV3Requests.id(context, "id"),
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 多条件分页查询管理后台用户列表。
     */
    public void getUsers(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final int p = ApiV3Requests.page(context);
            final int size = ApiV3Requests.size(context);
            final String keyword = context.param("keyword");
            final String status = ApiV3Requests.choice(context, "status", "all", USER_STATUSES);
            final String roleId = context.param("roleId");
            return adminService.getUsers(identity(context), p, size, keyword, status, roleId);
        });
    }

    /**
     * 查询单个管理用户的详细资料与操作审计记录。
     */
    public void getUser(final RequestContext context) {
        ApiV3Requests.read(context, () -> adminService.getUserDetail(
                identity(context),
                ApiV3Requests.id(context, "userId")));
    }

    /**
     * 编辑用户基础资料或高级状态/角色字段（携带 Idempotency-Key）。
     */
    public void updateUser(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final byte[] bytes = context.getRequest().getBytes();
            if (bytes != null && bytes.length > MAX_USER_UPDATE_BODY_BYTES) {
                throw new ApiV3Exception(413, "body_too_large", "请求体过大");
            }
            final JSONObject body = ApiV3WriteInputs.body(context, ApiV3AdminService.USER_UPDATE_ALLOWED_FIELDS, false);
            return adminService.updateUser(
                    identity(context),
                    ApiV3Requests.id(context, "userId"),
                    body,
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 永久停用并匿名化用户账号（仅管理员可用，携带 Idempotency-Key）。
     */
    public void deactivateUser(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            checkEmptyWriteBody(context);
            return adminService.deactivateUser(
                    identity(context),
                    ApiV3Requests.id(context, "userId"),
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 移除用户两步验证 MFA 绑定（携带 Idempotency-Key）。
     */
    public void removeMfa(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            checkEmptyWriteBody(context);
            return adminService.removeMfa(
                    identity(context),
                    ApiV3Requests.id(context, "userId"),
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 多条件分页查询管理后台文章列表。
     */
    public void getArticles(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final int p = ApiV3Requests.page(context);
            final int size = ApiV3Requests.size(context);
            final String keyword = context.param("keyword");
            final String status = ApiV3Requests.choice(context, "status", "all", ARTICLE_STATUSES);
            final String type = ApiV3Requests.choice(context, "type", "all", ARTICLE_TYPES);
            return adminService.getArticles(identity(context), p, size, keyword, status, type);
        });
    }

    /**
     * 管理员在后台新建文章并同步长文专栏（携带 Idempotency-Key）。
     */
    public void addArticle(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = checkedBody(context, ApiV3AdminService.ARTICLE_CREATE_ALLOWED_FIELDS, false, MAX_CONTENT_WRITE_BODY_BYTES);
            return adminService.addArticle(
                    identity(context),
                    body,
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 查询单篇文章管理详情与最近审计记录。
     */
    public void getArticle(final RequestContext context) {
        ApiV3Requests.read(context, () -> adminService.getArticleDetail(
                identity(context),
                ApiV3Requests.id(context, "articleId")));
    }

    /**
     * 管理员编辑文章字段并可选同步长文专栏章节与封面（携带 Idempotency-Key）。
     */
    public void updateArticle(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = checkedBody(context, ApiV3AdminService.ARTICLE_UPDATE_ALLOWED_FIELDS, false, MAX_CONTENT_WRITE_BODY_BYTES);
            return adminService.updateArticle(
                    identity(context),
                    ApiV3Requests.id(context, "articleId"),
                    body,
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 置顶指定文章（携带 Idempotency-Key）。
     */
    public void stickArticle(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            checkEmptyWriteBody(context);
            return adminService.stickArticle(
                    identity(context),
                    ApiV3Requests.id(context, "articleId"),
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 取消置顶指定文章（携带 Idempotency-Key）。
     */
    public void cancelStickArticle(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            checkEmptyWriteBody(context);
            return adminService.cancelStickArticle(
                    identity(context),
                    ApiV3Requests.id(context, "articleId"),
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 管理员强制移除指定文章（携带 Idempotency-Key）。
     */
    public void removeArticle(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            checkEmptyWriteBody(context);
            return adminService.removeArticle(
                    identity(context),
                    ApiV3Requests.id(context, "articleId"),
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 多条件分页查询管理后台评论列表。
     */
    public void getComments(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final int p = ApiV3Requests.page(context);
            final int size = ApiV3Requests.size(context);
            final String keyword = context.param("keyword");
            final String status = ApiV3Requests.choice(context, "status", "all", BINARY_CONTENT_STATUSES);
            final String articleId = context.param("articleId");
            return adminService.getComments(identity(context), p, size, keyword, status, articleId);
        });
    }

    /**
     * 查询单条评论详情与最近审计记录。
     */
    public void getComment(final RequestContext context) {
        ApiV3Requests.read(context, () -> adminService.getCommentDetail(
                identity(context),
                ApiV3Requests.id(context, "commentId")));
    }

    /**
     * 管理员编辑评论内容与状态（携带 Idempotency-Key）。
     */
    public void updateComment(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = checkedBody(context, ApiV3AdminService.COMMENT_UPDATE_ALLOWED_FIELDS, false, MAX_CONTENT_WRITE_BODY_BYTES);
            return adminService.updateComment(
                    identity(context),
                    ApiV3Requests.id(context, "commentId"),
                    body,
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 管理员移除指定评论（携带 Idempotency-Key）。
     */
    public void removeComment(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            checkEmptyWriteBody(context);
            return adminService.removeComment(
                    identity(context),
                    ApiV3Requests.id(context, "commentId"),
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 多条件分页查询管理后台清风明月列表。
     */
    public void getBreezemoons(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final int p = ApiV3Requests.page(context);
            final int size = ApiV3Requests.size(context);
            final String keyword = context.param("keyword");
            final String status = ApiV3Requests.choice(context, "status", "all", BINARY_CONTENT_STATUSES);
            final String authorId = context.param("authorId");
            return adminService.getBreezemoons(identity(context), p, size, keyword, status, authorId);
        });
    }

    /**
     * 查询单条清风明月详情与最近审计记录。
     */
    public void getBreezemoon(final RequestContext context) {
        ApiV3Requests.read(context, () -> adminService.getBreezemoonDetail(
                identity(context),
                ApiV3Requests.id(context, "breezemoonId")));
    }

    /**
     * 管理员编辑清风明月内容或状态（携带 Idempotency-Key）。
     */
    public void updateBreezemoon(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = checkedBody(context, ApiV3AdminService.BREEZEMOON_UPDATE_ALLOWED_FIELDS, false, MAX_CONTENT_WRITE_BODY_BYTES);
            return adminService.updateBreezemoon(
                    identity(context),
                    ApiV3Requests.id(context, "breezemoonId"),
                    body,
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 管理员永久移除指定清风明月内容（携带 Idempotency-Key）。
     */
    public void removeBreezemoon(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            checkEmptyWriteBody(context);
            return adminService.removeBreezemoon(
                    identity(context),
                    ApiV3Requests.id(context, "breezemoonId"),
                    ApiV3WriteInputs.idempotencyKey(context),
                    context.getRequest());
        });
    }

    /**
     * 多条件分页检索全站管理操作审计日志。
     */
    public void getAuditLogs(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final int p = ApiV3Requests.page(context);
            final int size = ApiV3Requests.size(context);
            final String operator = context.param("operator");
            final String rawCode = context.param("code");
            final String code;
            if (rawCode == null || rawCode.trim().isEmpty() || "all".equals(rawCode.trim())) {
                code = "all";
            } else {
                final String trimmedCode = rawCode.trim();
                if (!trimmedCode.matches("[0-9]{1,3}")) {
                    throw ApiV3Requests.invalid();
                }
                code = trimmedCode;
            }
            final String dataId = context.param("dataId");
            return adminService.getAuditLogs(identity(context), p, size, operator, code, dataId);
        });
    }

    private static JSONObject checkedBody(final RequestContext context, final Set<String> allowed,
                                          final boolean emptyAllowed, final int maxBytes) {
        final byte[] bytes = context.getRequest().getBytes();
        if (bytes != null && bytes.length > maxBytes) {
            throw new ApiV3Exception(413, "body_too_large", "请求体过大");
        }
        return ApiV3WriteInputs.body(context, allowed, emptyAllowed, maxBytes);
    }

    private static void checkEmptyWriteBody(final RequestContext context) {
        checkedBody(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
    }

    private static JSONObject identity(final RequestContext context) {
        return (JSONObject) context.attr(ApiV3Processor.IDENTITY);
    }
}
