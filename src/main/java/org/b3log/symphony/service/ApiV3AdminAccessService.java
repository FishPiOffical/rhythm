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
import org.b3log.latke.http.Request;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Invitecode;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.repository.InvitecodeRepository;
import org.b3log.symphony.repository.RoleRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理后台 v3 角色权限与邀请码服务。
 */
@Service
public class ApiV3AdminAccessService {

    private static final Logger LOGGER = LogManager.getLogger(ApiV3AdminAccessService.class);

    private static final Set<String> PROTECTED_ROLE_IDS = Set.of(
            Role.ROLE_ID_C_ADMIN,
            Role.ROLE_ID_C_DEFAULT,
            Role.ROLE_ID_C_LEADER,
            Role.ROLE_ID_C_MEMBER,
            Role.ROLE_ID_C_VISITOR,
            Role.ROLE_ID_C_OP
    );

    private static final Set<String> ADMIN_ONLY_PERMISSIONS = Set.of(
            Permission.PERMISSION_ID_C_MENU_ADMIN_ROLES,
            Permission.PERMISSION_ID_C_USER_UPDATE_USER_ADVANCED
    );

    @Inject
    private ApiV3AdminService adminService;

    @Inject
    private RoleQueryService roleQueryService;

    @Inject
    private RoleMgmtService roleMgmtService;

    @Inject
    private RoleRepository roleRepository;

    @Inject
    private InvitecodeRepository invitecodeRepository;

    @Inject
    private InvitecodeMgmtService invitecodeMgmtService;

    @Inject
    private UserRepository users;

    @Inject
    private LangPropsService langPropsService;

    /**
     * 分页查询角色列表及各角色下用户数。
     */
    public JSONObject listRoles(final JSONObject identity, final int page, final int pageSize) {
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor = adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_ROLES, false);
            final int safePage = Math.max(1, page);
            final int safeSize = Math.min(Math.max(1, pageSize), 100);
            final int offset = (safePage - 1) * safeSize;

            final List<JSONObject> totalRows = roleRepository.select(
                    "SELECT COUNT(*) AS totalCount FROM `" + roleRepository.getName() + "`");
            final int total = totalRows.isEmpty() ? 0 : totalRows.getFirst().optInt("totalCount", 0);

            final List<JSONObject> rows = roleRepository.select(
                    "SELECT oId," + Role.ROLE_NAME + "," + Role.ROLE_DESCRIPTION
                            + " FROM `" + roleRepository.getName() + "` ORDER BY oId ASC LIMIT ? OFFSET ?",
                    safeSize, offset);

            final JSONArray items = new JSONArray();
            for (final JSONObject row : rows) {
                final String roleId = row.optString(Keys.OBJECT_ID);
                final int userCount = roleQueryService.countUser(roleId);
                items.put(new JSONObject()
                        .put("oId", roleId)
                        .put("roleName", row.optString(Role.ROLE_NAME))
                        .put("roleDescription", row.optString(Role.ROLE_DESCRIPTION))
                        .put("userCount", userCount)
                        .put("builtIn", PROTECTED_ROLE_IDS.contains(roleId))
                        .put("removable", !PROTECTED_ROLE_IDS.contains(roleId) && userCount == 0));
            }

            tx.commit();
            return new JSONObject()
                    .put("items", items)
                    .put("canManageAdminPerms", actor.isAdmin())
                    .put("pagination", pagination(safePage, safeSize, total));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询角色列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 新增角色。
     */
    public JSONObject addRole(final JSONObject identity, final String idempotencyKey,
                              final Request request, final JSONObject body) {
        final String name = ApiV3WriteInputs.string(body, Role.ROLE_NAME, 64, false);
        final String description = body.has(Role.ROLE_DESCRIPTION)
                ? ApiV3WriteInputs.string(body, Role.ROLE_DESCRIPTION, 255, true)
                : "";
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ROLES, false, actorRef),
                    actorId,
                    "admin.role.add",
                    idempotencyKey,
                    new JSONObject().put(Role.ROLE_NAME, name).put(Role.ROLE_DESCRIPTION, description),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject role = new JSONObject()
                                .put(Role.ROLE_NAME, name)
                                .put(Role.ROLE_DESCRIPTION, description);
                        roleMgmtService.addRole(role);
                        return new JSONObject()
                                .put("roleName", name)
                                .put("roleDescription", description)
                                .put("createdAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("新增角色失败, name={}", name, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 删除角色（仅允许删除无绑定用户的非内置角色）。
     */
    public JSONObject removeRole(final JSONObject identity, final String roleId,
                                 final String idempotencyKey, final Request request) {
        if (StringUtils.isBlank(roleId)) {
            throw ApiV3WriteInputs.invalid();
        }
        if (PROTECTED_ROLE_IDS.contains(roleId)) {
            throw new ApiV3Exception(409, "protected_role", "内置系统角色不可删除");
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ROLES, false, actorRef),
                    actorId,
                    "admin.role.remove",
                    idempotencyKey,
                    new JSONObject().put("roleId", roleId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject existing = roleRepository.get(roleId);
                        if (existing == null) {
                            throw ApiV3Requests.notFound();
                        }
                        if (roleQueryService.countUser(roleId) > 0) {
                            throw new ApiV3Exception(409, "role_in_use", "该角色下仍有用户，不可删除");
                        }
                        roleMgmtService.removeRole(roleId);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_REMOVE_ROLE,
                                roleId, actorRef.get().user());
                        return new JSONObject()
                                .put("roleId", roleId)
                                .put("removed", true);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("删除角色失败, roleId={}", roleId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 查询指定角色的权限矩阵与授权状态。
     */
    public JSONObject getRolePermissions(final JSONObject identity, final String roleId) {
        if (StringUtils.isBlank(roleId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor = adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_ROLES, false);
            final JSONObject role = roleRepository.get(roleId);
            if (role == null) {
                throw ApiV3Requests.notFound();
            }
            final List<JSONObject> permissions = roleQueryService.getPermissionsGrant(roleId);
            final JSONArray items = new JSONArray();
            for (final JSONObject perm : permissions) {
                final String permId = perm.optString(Keys.OBJECT_ID);
                final int category = perm.optInt(Permission.PERMISSION_CATEGORY, 0);
                final String labelKey = "permission" + StringUtils.capitalize(permId) + "Label";
                String label = permId;
                try {
                    final String localized = langPropsService.get(labelKey);
                    if (StringUtils.isNotBlank(localized)) {
                        label = localized;
                    }
                } catch (final Exception ignored) {
                    // 忽略缺失的国际化键，回退到权限标识
                }
                items.put(new JSONObject()
                        .put("oId", permId)
                        .put("permissionCategory", category)
                        .put("categoryLabel", permissionCategoryLabel(category))
                        .put("permissionLabel", label)
                        .put("permissionGrant", perm.optBoolean(Permission.PERMISSION_T_GRANT, false))
                        .put("adminOnly", ADMIN_ONLY_PERMISSIONS.contains(permId)));
            }
            tx.commit();
            return new JSONObject()
                    .put("role", new JSONObject()
                            .put("oId", role.optString(Keys.OBJECT_ID))
                            .put("roleName", role.optString(Role.ROLE_NAME))
                            .put("roleDescription", role.optString(Role.ROLE_DESCRIPTION))
                            .put("isAdminRole", Role.ROLE_ID_C_ADMIN.equals(roleId)))
                    .put("canManageAdminPerms", actor.isAdmin())
                    .put("items", items);
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询角色权限矩阵失败, roleId={}", roleId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 更新角色权限矩阵，并拦截非管理员越权授予高危管理权限。
     */
    public JSONObject updateRolePermissions(final JSONObject identity, final String roleId,
                                            final String idempotencyKey, final Request request,
                                            final JSONObject body) {
        if (StringUtils.isBlank(roleId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final JSONArray rawPerms = body.optJSONArray("permissionIds");
        if (rawPerms == null) {
            throw ApiV3WriteInputs.invalid();
        }
        final Set<String> requestedPerms = new LinkedHashSet<>();
        for (int i = 0; i < rawPerms.length(); i++) {
            final String permId = StringUtils.trimToEmpty(rawPerms.optString(i));
            if (StringUtils.isNotBlank(permId)) {
                requestedPerms.add(permId);
            }
        }

        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ROLES, false, actorRef),
                    actorId,
                    "admin.role.permissions.update",
                    idempotencyKey,
                    new JSONObject().put("roleId", roleId).put("permissionIds", new JSONArray(requestedPerms)),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject role = roleRepository.get(roleId);
                        if (role == null) {
                            throw ApiV3Requests.notFound();
                        }
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.isAdmin()) {
                            if (Role.ROLE_ID_C_ADMIN.equals(roleId) || Role.ROLE_ID_C_OP.equals(roleId)) {
                                throw ApiV3Requests.forbidden();
                            }
                            for (final String permId : requestedPerms) {
                                if (ADMIN_ONLY_PERMISSIONS.contains(permId)) {
                                    throw ApiV3Requests.forbidden();
                                }
                            }
                        }
                        roleMgmtService.updateRolePermissions(roleId, requestedPerms);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_UPDATE_ROLE_PERMS,
                                roleId, actor.user());
                        return new JSONObject()
                                .put("roleId", roleId)
                                .put("grantedCount", requestedPerms.size())
                                .put("updatedAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新角色权限失败, roleId={}", roleId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 分页查询邀请码列表（支持状态与关键字筛选）。
     */
    public JSONObject listInvitecodes(final JSONObject identity, final int page, final int pageSize,
                                      final String keyword, final Integer status) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_ICS, false);
            final int safePage = Math.max(1, page);
            final int safeSize = Math.min(Math.max(1, pageSize), 100);
            final int offset = (safePage - 1) * safeSize;

            final StringBuilder where = new StringBuilder(" WHERE 1=1");
            final List<Object> params = new ArrayList<>();
            if (status != null) {
                if (status < Invitecode.STATUS_C_USED || status > Invitecode.STATUS_C_STOPUSE) {
                    throw ApiV3WriteInputs.invalid();
                }
                where.append(" AND ").append(Invitecode.STATUS).append("=?");
                params.add(status);
            }
            if (StringUtils.isNotBlank(keyword)) {
                final String like = "%" + keyword.trim() + "%";
                where.append(" AND (").append(Invitecode.CODE).append(" LIKE ? OR ")
                        .append(Invitecode.MEMO).append(" LIKE ?)");
                params.add(like);
                params.add(like);
            }

            final String table = invitecodeRepository.getName();
            final List<JSONObject> totalRows = invitecodeRepository.select(
                    "SELECT COUNT(*) AS totalCount FROM `" + table + "`" + where, params.toArray());
            final int total = totalRows.isEmpty() ? 0 : totalRows.getFirst().optInt("totalCount", 0);

            final List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(safeSize);
            queryParams.add(offset);
            final List<JSONObject> rows = invitecodeRepository.select(
                    "SELECT oId," + Invitecode.CODE + "," + Invitecode.MEMO + "," + Invitecode.STATUS + ","
                            + Invitecode.GENERATOR_ID + "," + Invitecode.USER_ID + ","
                            + "time," + Invitecode.USE_TIME
                            + " FROM `" + table + "`" + where + " ORDER BY oId DESC LIMIT ? OFFSET ?",
                    queryParams.toArray());

            final JSONArray items = new JSONArray();
            for (final JSONObject row : rows) {
                final String genId = row.optString(Invitecode.GENERATOR_ID);
                final String userId = row.optString(Invitecode.USER_ID);
                final int st = row.optInt(Invitecode.STATUS, Invitecode.STATUS_C_UNUSED);
                items.put(new JSONObject()
                        .put("oId", row.optString(Keys.OBJECT_ID))
                        .put("code", row.optString(Invitecode.CODE))
                        .put("memo", row.optString(Invitecode.MEMO))
                        .put("status", st)
                        .put("statusLabel", invitecodeStatusLabel(st))
                        .put("generatorId", genId)
                        .put("generatorName", resolveUserName(genId))
                        .put("userId", userId)
                        .put("userName", resolveUserName(userId))
                        .put("time", row.optLong("time", 0L))
                        .put("useTime", row.optLong(Invitecode.USE_TIME, 0L)));
            }

            tx.commit();
            return new JSONObject()
                    .put("items", items)
                    .put("pagination", pagination(safePage, safeSize, total));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询邀请码列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 批量生成邀请码。
     */
    public JSONObject generateInvitecodes(final JSONObject identity, final String idempotencyKey,
                                          final Request request, final JSONObject body) {
        final int quantity = ApiV3AdminService.requireExactInt(body, "quantity", 1, 100);
        final String memo = body.has(Invitecode.MEMO)
                ? ApiV3WriteInputs.string(body, Invitecode.MEMO, 255, true)
                : "";
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ICS, false, actorRef),
                    actorId,
                    "admin.invitecode.generate",
                    idempotencyKey,
                    new JSONObject().put("quantity", quantity).put(Invitecode.MEMO, memo),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        invitecodeMgmtService.adminGenInvitecodes(quantity, memo);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_GENERATE_INVITECODES,
                                String.valueOf(quantity), actorRef.get().user());
                        return new JSONObject()
                                .put("quantity", quantity)
                                .put("memo", memo)
                                .put("generatedAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("批量生成邀请码失败, quantity={}", quantity, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 更新邀请码状态（0 已使用 / 1 未使用 / 2 停用）与备注。
     */
    public JSONObject updateInvitecode(final JSONObject identity, final String invitecodeId,
                                       final String idempotencyKey, final Request request,
                                       final JSONObject body) {
        if (StringUtils.isBlank(invitecodeId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final int status = ApiV3AdminService.requireExactInt(
                body, Invitecode.STATUS, Invitecode.STATUS_C_USED, Invitecode.STATUS_C_STOPUSE);
        final String memo = body.has(Invitecode.MEMO)
                ? ApiV3WriteInputs.string(body, Invitecode.MEMO, 255, true)
                : null;
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ICS, false, actorRef),
                    actorId,
                    "admin.invitecode.update",
                    idempotencyKey,
                    new JSONObject().put("invitecodeId", invitecodeId).put(Invitecode.STATUS, status)
                            .put(Invitecode.MEMO, memo == null ? "" : memo),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject existing = invitecodeRepository.get(invitecodeId);
                        if (existing == null) {
                            throw ApiV3Requests.notFound();
                        }
                        final JSONObject patch = new JSONObject()
                                .put(Keys.OBJECT_ID, invitecodeId)
                                .put(Invitecode.STATUS, status)
                                .put(Invitecode.MEMO, memo == null ? existing.optString(Invitecode.MEMO) : memo)
                                .put(Invitecode.CODE, existing.optString(Invitecode.CODE))
                                .put(Invitecode.GENERATOR_ID, existing.optString(Invitecode.GENERATOR_ID))
                                .put(Invitecode.USER_ID, existing.optString(Invitecode.USER_ID))
                                .put("time", existing.optLong("time", 0L))
                                .put(Invitecode.USE_TIME, existing.optLong(Invitecode.USE_TIME, 0L));
                        invitecodeMgmtService.updateInvitecode(invitecodeId, patch);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_UPDATE_INVITECODE,
                                invitecodeId, actorRef.get().user());
                        return new JSONObject()
                                .put("oId", invitecodeId)
                                .put("status", status)
                                .put("statusLabel", invitecodeStatusLabel(status))
                                .put("memo", patch.optString(Invitecode.MEMO));
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "invalid_request");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新邀请码失败, invitecodeId={}", invitecodeId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    private String resolveUserName(final String userId) throws RepositoryException {
        if (StringUtils.isBlank(userId) || "0".equals(userId)) {
            return "";
        }
        final JSONObject user = users.get(userId);
        return user == null ? userId : user.optString(User.USER_NAME, userId);
    }

    private static String invitecodeStatusLabel(final int status) {
        return switch (status) {
            case Invitecode.STATUS_C_USED -> "已使用";
            case Invitecode.STATUS_C_UNUSED -> "未使用";
            case Invitecode.STATUS_C_STOPUSE -> "已停用";
            default -> "未知状态";
        };
    }

    private static String permissionCategoryLabel(final int category) {
        return switch (category) {
            case Permission.PERMISSION_CATEGORY_C_COMMON -> "通用";
            case Permission.PERMISSION_CATEGORY_C_USER -> "用户";
            case Permission.PERMISSION_CATEGORY_C_ARTICLE -> "帖子";
            case Permission.PERMISSION_CATEGORY_C_COMMENT -> "回帖";
            case Permission.PERMISSION_CATEGORY_C_DOMAIN -> "领域";
            case Permission.PERMISSION_CATEGORY_C_TAG -> "标签";
            case Permission.PERMISSION_CATEGORY_C_RESERVED_WORD -> "保留词";
            case Permission.PERMISSION_CATEGORY_C_IC -> "邀请码";
            case Permission.PERMISSION_CATEGORY_C_AD -> "广告";
            case Permission.PERMISSION_CATEGORY_C_MISC -> "杂项";
            case Permission.PERMISSION_CATEGORY_C_MENU -> "菜单";
            case Permission.PERMISSION_CATEGORY_C_BREEZEMOON -> "清风明月";
            default -> "其他";
        };
    }

    private static JSONObject pagination(final int page, final int pageSize, final int total) {
        final int totalPages = total <= 0 ? 1 : (int) Math.ceil((double) total / pageSize);
        return new JSONObject()
                .put("page", page)
                .put("pageSize", pageSize)
                .put("total", total)
                .put("totalPages", totalPages);
    }
}
