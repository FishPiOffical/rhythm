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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Domain;
import org.b3log.symphony.model.FishGame;
import org.b3log.symphony.model.Milestone;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.repository.AdminOperationTaskRepository;
import org.b3log.symphony.repository.DomainRepository;
import org.b3log.symphony.repository.DomainTagRepository;
import org.b3log.symphony.repository.FishGameRepository;
import org.b3log.symphony.repository.MilestoneRepository;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.repository.TagRepository;
import org.b3log.symphony.repository.UploadRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.symphony.util.ReservedWords;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理后台 v3 社区运营、内容与图片审核服务。
 */
@Service
public class ApiV3AdminOpsService {

    private static final Logger LOGGER = LogManager.getLogger(ApiV3AdminOpsService.class);

    @Inject
    private ApiV3AdminService adminService;

    @Inject
    private FishGameRepository fishGameRepository;

    @Inject
    private FishGameMgmtService fishGameMgmtService;

    @Inject
    private MilestoneRepository milestoneRepository;

    @Inject
    private MilestoneMgmtService milestoneMgmtService;

    @Inject
    private UploadRepository uploadRepository;

    @Inject
    private AdminImageReviewService imageReviewService;

    @Inject
    private AdminOperationTaskRepository operationTasks;

    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    @Inject
    private UserRepository users;

    @Inject
    private DomainRepository domainRepository;

    @Inject
    private DomainTagRepository domainTagRepository;

    @Inject
    private DomainMgmtService domainMgmtService;

    @Inject
    private TagRepository tagRepository;

    @Inject
    private TagMgmtService tagMgmtService;

    @Inject
    private OptionRepository optionRepository;

    @Inject
    private OptionMgmtService optionMgmtService;

    // ==================== 鱼游审核与日常维护 ====================

    /**
     * 分页查询鱼游投稿、待审核修改与日常列表。
     */
    public JSONObject listFishGames(final JSONObject identity, final int page, final int pageSize,
                                    final Integer status, final String keyword, final boolean editPendingOnly) {
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor = adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", null, false);
            if (!actor.isAdmin() && !actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN_MISC)) {
                throw ApiV3Requests.forbidden();
            }
            final int safePage = Math.max(1, page);
            final int safeSize = Math.min(Math.max(1, pageSize), 100);
            final int offset = (safePage - 1) * safeSize;

            final StringBuilder where = new StringBuilder(" WHERE 1=1");
            final List<Object> params = new ArrayList<>();
            if (status != null) {
                if (status < FishGame.STATUS_PENDING || status > FishGame.STATUS_DISABLED) {
                    throw ApiV3WriteInputs.invalid();
                }
                where.append(" AND ").append(FishGame.STATUS).append("=?");
                params.add(status);
            }
            if (editPendingOnly) {
                where.append(" AND ").append(FishGame.EDIT_PENDING).append("=?");
                params.add(1);
            }
            if (StringUtils.isNotBlank(keyword)) {
                final String like = "%" + keyword.trim() + "%";
                where.append(" AND (").append(FishGame.NAME).append(" LIKE ? OR ")
                        .append(FishGame.DESCRIPTION).append(" LIKE ?)");
                params.add(like);
                params.add(like);
            }

            final String table = fishGameRepository.getName();
            final List<JSONObject> totalRows = fishGameRepository.select(
                    "SELECT COUNT(*) AS totalCount FROM `" + table + "`" + where, params.toArray());
            final int total = totalRows.isEmpty() ? 0 : totalRows.getFirst().optInt("totalCount", 0);

            final List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(safeSize);
            queryParams.add(offset);
            final List<JSONObject> rows = fishGameRepository.select(
                    "SELECT * FROM `" + table + "`" + where + " ORDER BY oId DESC LIMIT ? OFFSET ?",
                    queryParams.toArray());

            tx.commit();
            return new JSONObject()
                    .put("items", new JSONArray(rows))
                    .put("pagination", pagination(safePage, safeSize, total));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询鱼游列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 审核鱼游投稿或待审核修改（仅管理员）。
     */
    public JSONObject reviewFishGame(final JSONObject identity, final String gameId,
                                     final String idempotencyKey, final JSONObject body) {
        if (StringUtils.isBlank(gameId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final int status = ApiV3AdminService.requireExactInt(
                body, "status", FishGame.STATUS_PENDING, FishGame.STATUS_DISABLED);
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.fish_game.review",
                    idempotencyKey,
                    new JSONObject(body.toString()).put("gameId", gameId),
                    () -> ApiV3TransactionSavepoint.run(() ->
                            fishGameMgmtService.review(gameId, status, new JSONObject(body.toString()))));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "fish_game_review_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("审核鱼游失败, gameId={}", gameId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 后台手工新增鱼游条目。
     */
    public JSONObject addFishGame(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, false, actorRef),
                    actorId,
                    "admin.fish_game.add",
                    idempotencyKey,
                    body,
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.isAdmin() && !actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN_MISC)) {
                            throw ApiV3Requests.forbidden();
                        }
                        return fishGameMgmtService.addManual(actorId, fishGameAdminInput(body));
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "fish_game_add_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("新增鱼游失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 后台直接编辑鱼游条目。
     */
    public JSONObject updateFishGame(final JSONObject identity, final String gameId,
                                     final String idempotencyKey, final JSONObject body) {
        if (StringUtils.isBlank(gameId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, false, actorRef),
                    actorId,
                    "admin.fish_game.update",
                    idempotencyKey,
                    new JSONObject(body.toString()).put("gameId", gameId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.isAdmin() && !actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN_MISC)) {
                            throw ApiV3Requests.forbidden();
                        }
                        return fishGameMgmtService.updateByAdmin(gameId, fishGameAdminInput(body));
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "fish_game_update_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("编辑鱼游失败, gameId={}", gameId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    static JSONObject fishGameAdminInput(final JSONObject body) {
        // 仅转换传给表单校验的副本，幂等摘要仍使用原请求。
        final JSONObject input = new JSONObject(body.toString());
        final Object legacyIcon = input.remove(FishGame.LEGACY_ADMIN_ICON_URL);
        if (!input.has(FishGame.ICON_URL) && legacyIcon != null) {
            input.put(FishGame.ICON_URL, legacyIcon);
        }
        return input;
    }

    /**
     * 导出全部鱼游 JSON。
     */
    public JSONObject exportFishGames(final JSONObject identity) {
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor = adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", null, false);
            if (!actor.isAdmin() && !actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN_MISC)) {
                throw ApiV3Requests.forbidden();
            }
            final List<JSONObject> rows = fishGameRepository.select(
                    "SELECT * FROM `" + fishGameRepository.getName() + "` ORDER BY oId DESC");
            tx.commit();
            return new JSONObject().put("items", new JSONArray(rows));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("导出鱼游失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 批量导入鱼游 JSON。
     */
    public JSONObject importFishGames(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final JSONArray games = body.optJSONArray("items");
        if (games == null || games.isEmpty()) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomicWithLegacyReceipt(
                    adminService.guardedWrite(identity, null, false, actorRef, false,
                            actor -> actor.isAdmin() || actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN_MISC)),
                    actorId,
                    "admin.fish_game.import",
                    idempotencyKey,
                    new JSONObject().put("items", games),
                    new JSONObject().put("count", games.length()),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        return fishGameMgmtService.importGamesInCurrentTransaction(actorId, games);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "fish_game_import_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("导入鱼游失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    // ==================== 大事记审核与日常维护 ====================

    /**
     * 分页查询大事记列表（审核中心与日常维护共用同一数据源）。
     */
    public JSONObject listMilestones(final JSONObject identity, final int page, final int pageSize,
                                     final Integer status, final String keyword) {
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor = adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", null, false);
            requireMilestoneAccess(actor);

            final int safePage = Math.max(1, page);
            final int safeSize = Math.min(Math.max(1, pageSize), 100);
            final int offset = (safePage - 1) * safeSize;

            final StringBuilder where = new StringBuilder(" WHERE 1=1");
            final List<Object> params = new ArrayList<>();
            if (status != null) {
                if (status < Milestone.STATUS_C_PENDING || status > Milestone.STATUS_C_REJECTED) {
                    throw ApiV3WriteInputs.invalid();
                }
                where.append(" AND ").append(Milestone.MILESTONE_STATUS).append("=?");
                params.add(status);
            }
            if (StringUtils.isNotBlank(keyword)) {
                final String like = "%" + keyword.trim() + "%";
                where.append(" AND (").append(Milestone.MILESTONE_TITLE).append(" LIKE ? OR ")
                        .append(Milestone.MILESTONE_CONTENT).append(" LIKE ?)");
                params.add(like);
                params.add(like);
            }

            final String table = milestoneRepository.getName();
            final List<JSONObject> totalRows = milestoneRepository.select(
                    "SELECT COUNT(*) AS totalCount FROM `" + table + "`" + where, params.toArray());
            final int total = totalRows.isEmpty() ? 0 : totalRows.getFirst().optInt("totalCount", 0);

            final List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(safeSize);
            queryParams.add(offset);
            final List<JSONObject> rows = milestoneRepository.select(
                    "SELECT * FROM `" + table + "`" + where + " ORDER BY oId DESC LIMIT ? OFFSET ?",
                    queryParams.toArray());

            for (final JSONObject row : rows) {
                final String authorId = row.optString(Milestone.MILESTONE_AUTHOR_ID);
                if (StringUtils.isNotBlank(authorId)) {
                    final JSONObject author = users.get(authorId);
                    row.put("authorName", author == null ? "" : author.optString(User.USER_NAME));
                }
            }

            tx.commit();
            return new JSONObject()
                    .put("items", new JSONArray(rows))
                    .put("pagination", pagination(safePage, safeSize, total));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询大事记列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 审核或编辑大事记状态与内容。
     */
    public JSONObject updateMilestone(final JSONObject identity, final String milestoneId,
                                      final String idempotencyKey, final Request request, final JSONObject body) {
        if (StringUtils.isBlank(milestoneId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, false, actorRef),
                    actorId,
                    "admin.milestone.update",
                    idempotencyKey,
                    new JSONObject(body.toString()).put("milestoneId", milestoneId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        requireMilestoneAccess(actor);

                        final JSONObject existing = milestoneRepository.get(milestoneId);
                        if (existing == null) {
                            throw ApiV3Requests.notFound();
                        }
                        if (body.has(Milestone.MILESTONE_TITLE)) {
                            existing.put(Milestone.MILESTONE_TITLE,
                                    ApiV3WriteInputs.string(body, Milestone.MILESTONE_TITLE, 255, false));
                        }
                        if (body.has(Milestone.MILESTONE_DATE)) {
                            existing.put(Milestone.MILESTONE_DATE,
                                    ApiV3WriteInputs.string(body, Milestone.MILESTONE_DATE, 64, false));
                        }
                        if (body.has(Milestone.MILESTONE_END_DATE)) {
                            existing.put(Milestone.MILESTONE_END_DATE,
                                    ApiV3WriteInputs.string(body, Milestone.MILESTONE_END_DATE, 64, true));
                        }
                        if (body.has(Milestone.MILESTONE_CONTENT)) {
                            existing.put(Milestone.MILESTONE_CONTENT,
                                    ApiV3WriteInputs.string(body, Milestone.MILESTONE_CONTENT, 10000, true));
                        }
                        if (body.has(Milestone.MILESTONE_LINK)) {
                            existing.put(Milestone.MILESTONE_LINK,
                                    ApiV3WriteInputs.string(body, Milestone.MILESTONE_LINK, 512, true));
                        }
                        if (body.has(Milestone.MILESTONE_STATUS)) {
                            existing.put(Milestone.MILESTONE_STATUS, ApiV3AdminService.requireExactInt(
                                    body, Milestone.MILESTONE_STATUS,
                                    Milestone.STATUS_C_PENDING, Milestone.STATUS_C_REJECTED));
                        }
                        existing.put(Milestone.MILESTONE_UPDATE_TIME, System.currentTimeMillis());
                        milestoneMgmtService.updateMilestone(milestoneId, existing);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_UPDATE_MILESTONE,
                                milestoneId, actor.user());
                        return existing;
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "milestone_update_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新大事记失败, milestoneId={}", milestoneId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 删除大事记。
     */
    public JSONObject removeMilestone(final JSONObject identity, final String milestoneId,
                                      final String idempotencyKey, final Request request) {
        if (StringUtils.isBlank(milestoneId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, false, actorRef),
                    actorId,
                    "admin.milestone.remove",
                    idempotencyKey,
                    new JSONObject().put("milestoneId", milestoneId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        requireMilestoneAccess(actor);

                        final JSONObject existing = milestoneRepository.get(milestoneId);
                        if (existing == null) {
                            throw ApiV3Requests.notFound();
                        }
                        milestoneMgmtService.removeMilestone(milestoneId);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_DELETE_MILESTONE,
                                milestoneId, actor.user());
                        return new JSONObject().put("oId", milestoneId).put("removed", true);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "milestone_remove_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("删除大事记失败, milestoneId={}", milestoneId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    // ==================== 图片审核（仅 RHPIC_ENABLED == false 时可用） ====================

    /**
     * 查询 45 天内待审核公开上传图片列表。
     */
    public JSONObject listImages(final JSONObject identity, final int page, final int pageSize) {
        if (Symphonys.RHPIC_ENABLED) {
            throw new ApiV3Exception(403, "image_review_disabled", "已启用外置图床审核，内置图片审核不可用");
        }
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(identity, "profile.read", null, true);
            final int safePage = Math.max(1, page);
            final int safeSize = Math.min(Math.max(1, pageSize), 100);
            final int offset = (safePage - 1) * safeSize;
            final String sinceId = String.valueOf(System.currentTimeMillis() - AdminImageReviewService.WINDOW);
            final String table = uploadRepository.getName();
            final String where = " WHERE `public`=? AND oId>=?";
            final List<JSONObject> totalRows = uploadRepository.select(
                    "SELECT COUNT(*) AS totalCount FROM `" + table + "`" + where, true, sinceId);
            final int total = totalRows.isEmpty() ? 0 : totalRows.getFirst().optInt("totalCount", 0);
            final List<JSONObject> rows = uploadRepository.select(
                    "SELECT u.oId,u.type,u.userName,u.time,u.path,u.`public`,0 AS reviewed,"
                            + " CASE WHEN t.status IN ('IMAGE_DELETING','IMAGE_REFRESHING','IMAGE_UNKNOWN') THEN 'UNKNOWN'"
                            + " WHEN t.status IS NULL THEN 'PENDING' ELSE 'IN_PROGRESS' END AS reviewStatus"
                            + " FROM `" + table + "` u LEFT JOIN `" + operationTasks.getName() + "` t"
                            + " ON t.oId=CONCAT('image-review:',u.oId)"
                            + " WHERE u.`public`=? AND u.oId>=? ORDER BY u.oId DESC LIMIT ? OFFSET ?",
                    true, sinceId, safeSize, offset);

            tx.commit();
            return new JSONObject()
                    .put("items", new JSONArray(rows))
                    .put("pagination", pagination(safePage, safeSize, total));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询待审核图片失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 标记上传图片为正常或违规（RHPIC_ENABLED == true 时强制拒绝）。
     */
    public JSONObject reviewImage(final JSONObject identity, final String imageId,
                                  final String idempotencyKey, final Request request, final JSONObject body) {
        if (Symphonys.RHPIC_ENABLED) {
            throw new ApiV3Exception(403, "image_review_disabled", "已启用外置图床审核，内置图片审核不可用");
        }
        if (StringUtils.isBlank(imageId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String decision = ApiV3WriteInputs.string(body, "decision", 16, false);
        if (!"normal".equals(decision) && !"illegal".equals(decision)) {
            throw ApiV3WriteInputs.invalid();
        }

        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            final JSONObject accepted = ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId, "admin.image.review", idempotencyKey,
                    new JSONObject().put("imageId", imageId).put("decision", decision),
                    () -> imageReviewService.reserveInCurrentTransaction(imageId, decision, request, actorRef.get().user()));
            return imageReviewService.complete(accepted, actorId);
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("图片审核失败, imageId={}", imageId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    // ==================== 领域、导航与标签 ====================

    /**
     * 分页查询领域及已绑定标签。
     */
    public JSONObject listDomains(final JSONObject identity, final int page, final int pageSize) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_DOMAINS, false);
            final int safePage = Math.max(1, page);
            final int safeSize = Math.min(Math.max(1, pageSize), 100);
            final int offset = (safePage - 1) * safeSize;
            final String table = domainRepository.getName();

            final List<JSONObject> totalRows = domainRepository.select(
                    "SELECT COUNT(*) AS totalCount FROM `" + table + "`");
            final int total = totalRows.isEmpty() ? 0 : totalRows.getFirst().optInt("totalCount", 0);

            final List<JSONObject> rows = domainRepository.select(
                    "SELECT * FROM `" + table + "` ORDER BY domainSort ASC, oId ASC LIMIT ? OFFSET ?",
                    safeSize, offset);

            for (final JSONObject domain : rows) {
                final String domainId = domain.optString(Keys.OBJECT_ID);
                final List<JSONObject> binds = domainTagRepository.select(
                        "SELECT dt.oId AS bindId, t.oId AS tagId, t.tagTitle, t.tagURI "
                                + "FROM `" + domainTagRepository.getName() + "` dt "
                                + "JOIN `" + tagRepository.getName() + "` t ON dt.tag_oId = t.oId "
                                + "WHERE dt.domain_oId=?",
                        domainId);
                domain.put("tags", new JSONArray(binds));
            }

            tx.commit();
            return new JSONObject()
                    .put("items", new JSONArray(rows))
                    .put("pagination", pagination(safePage, safeSize, total));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询领域列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 新增领域。
     */
    public JSONObject addDomain(final JSONObject identity, final String idempotencyKey,
                                final Request request, final JSONObject body) {
        final String title = ApiV3WriteInputs.string(body, Domain.DOMAIN_TITLE, 64, false);
        final String uri = ApiV3WriteInputs.string(body, Domain.DOMAIN_URI, 64, false);
        final String description = body.has(Domain.DOMAIN_DESCRIPTION)
                ? ApiV3WriteInputs.string(body, Domain.DOMAIN_DESCRIPTION, 512, true)
                : "";
        final String iconPath = body.has(Domain.DOMAIN_ICON_PATH)
                ? ApiV3WriteInputs.string(body, Domain.DOMAIN_ICON_PATH, 512, true)
                : "";
        final String css = body.has(Domain.DOMAIN_CSS)
                ? ApiV3WriteInputs.string(body, Domain.DOMAIN_CSS, 2048, true)
                : "";
        final int sort = body.has(Domain.DOMAIN_SORT)
                ? ApiV3AdminService.requireExactInt(body, Domain.DOMAIN_SORT, 0, 100000)
                : 10;
        final int status = body.has(Domain.DOMAIN_STATUS)
                ? ApiV3AdminService.requireExactInt(body, Domain.DOMAIN_STATUS, 0, 1)
                : Domain.DOMAIN_STATUS_C_VALID;

        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_DOMAINS, false, actorRef),
                    actorId,
                    "admin.domain.add",
                    idempotencyKey,
                    new JSONObject().put(Domain.DOMAIN_TITLE, title).put(Domain.DOMAIN_URI, uri),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject domain = new JSONObject()
                                .put(Domain.DOMAIN_TITLE, title)
                                .put(Domain.DOMAIN_URI, uri)
                                .put(Domain.DOMAIN_DESCRIPTION, description)
                                .put(Domain.DOMAIN_ICON_PATH, iconPath)
                                .put(Domain.DOMAIN_CSS, css)
                                .put(Domain.DOMAIN_SORT, sort)
                                .put(Domain.DOMAIN_STATUS, status)
                                .put(Domain.DOMAIN_NAV, body.optInt(Domain.DOMAIN_NAV, 0))
                                .put(Domain.DOMAIN_TYPE, body.optString(Domain.DOMAIN_TYPE, ""));
                        final String oId = domainMgmtService.addDomain(domain);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_ADD_DOMAIN,
                                oId, actorRef.get().user());
                        return new JSONObject().put("oId", oId);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "domain_add_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("新增领域失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 更新领域配置。
     */
    public JSONObject updateDomain(final JSONObject identity, final String domainId,
                                   final String idempotencyKey, final Request request, final JSONObject body) {
        if (StringUtils.isBlank(domainId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_DOMAINS, false, actorRef),
                    actorId,
                    "admin.domain.update",
                    idempotencyKey,
                    new JSONObject(body.toString()).put("domainId", domainId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject existing = domainRepository.get(domainId);
                        if (existing == null) {
                            throw ApiV3Requests.notFound();
                        }
                        if (body.has(Domain.DOMAIN_TITLE)) {
                            existing.put(Domain.DOMAIN_TITLE,
                                    ApiV3WriteInputs.string(body, Domain.DOMAIN_TITLE, 64, false));
                        }
                        if (body.has(Domain.DOMAIN_URI)) {
                            existing.put(Domain.DOMAIN_URI,
                                    ApiV3WriteInputs.string(body, Domain.DOMAIN_URI, 64, false));
                        }
                        if (body.has(Domain.DOMAIN_DESCRIPTION)) {
                            existing.put(Domain.DOMAIN_DESCRIPTION,
                                    ApiV3WriteInputs.string(body, Domain.DOMAIN_DESCRIPTION, 512, true));
                        }
                        if (body.has(Domain.DOMAIN_ICON_PATH)) {
                            existing.put(Domain.DOMAIN_ICON_PATH,
                                    ApiV3WriteInputs.string(body, Domain.DOMAIN_ICON_PATH, 512, true));
                        }
                        if (body.has(Domain.DOMAIN_CSS)) {
                            existing.put(Domain.DOMAIN_CSS,
                                    ApiV3WriteInputs.string(body, Domain.DOMAIN_CSS, 2048, true));
                        }
                        if (body.has(Domain.DOMAIN_SORT)) {
                            existing.put(Domain.DOMAIN_SORT,
                                    ApiV3AdminService.requireExactInt(body, Domain.DOMAIN_SORT, 0, 100000));
                        }
                        if (body.has(Domain.DOMAIN_STATUS)) {
                            existing.put(Domain.DOMAIN_STATUS,
                                    ApiV3AdminService.requireExactInt(body, Domain.DOMAIN_STATUS, 0, 1));
                        }
                        if (body.has(Domain.DOMAIN_NAV)) {
                            existing.put(Domain.DOMAIN_NAV,
                                    ApiV3AdminService.requireExactInt(body, Domain.DOMAIN_NAV, 0, 1));
                        }
                        domainMgmtService.updateDomain(domainId, existing);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_UPDATE_DOMAIN,
                                domainId, actorRef.get().user());
                        return existing;
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新领域失败, domainId={}", domainId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 删除领域。
     */
    public JSONObject removeDomain(final JSONObject identity, final String domainId,
                                   final String idempotencyKey, final Request request) {
        if (StringUtils.isBlank(domainId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_DOMAINS, false, actorRef),
                    actorId,
                    "admin.domain.remove",
                    idempotencyKey,
                    new JSONObject().put("domainId", domainId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        if (domainRepository.get(domainId) == null) {
                            throw ApiV3Requests.notFound();
                        }
                        domainMgmtService.removeDomain(domainId);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_REMOVE_DOMAIN,
                                domainId, actorRef.get().user());
                        return new JSONObject().put("oId", domainId).put("removed", true);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("删除领域失败, domainId={}", domainId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 为领域绑定标签。
     */
    public JSONObject bindDomainTag(final JSONObject identity, final String domainId,
                                    final String idempotencyKey, final Request request, final JSONObject body) {
        if (StringUtils.isBlank(domainId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String tagTitle = ApiV3WriteInputs.string(body, Tag.TAG_TITLE, 64, false);
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_DOMAINS, false, actorRef),
                    actorId,
                    "admin.domain.tag.bind",
                    idempotencyKey,
                    new JSONObject().put("domainId", domainId).put(Tag.TAG_TITLE, tagTitle),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        if (domainRepository.get(domainId) == null) {
                            throw ApiV3Requests.notFound();
                        }
                        final JSONObject tag = tagRepository.getByTitle(tagTitle);
                        if (tag == null) {
                            throw new ApiV3Exception(404, "tag_not_found", "未找到标签：" + tagTitle);
                        }
                        final String tagId = tag.optString(Keys.OBJECT_ID);
                        domainMgmtService.addDomainTag(new JSONObject()
                                .put(Domain.DOMAIN + "_" + Keys.OBJECT_ID, domainId)
                                .put(Tag.TAG + "_" + Keys.OBJECT_ID, tagId));
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_ADD_DOMAIN_TAG,
                                domainId + ":" + tagId, actorRef.get().user());
                        return new JSONObject().put("domainId", domainId).put("tagId", tagId).put("tagTitle", tagTitle);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("领域绑定标签失败, domainId={}, tagTitle={}", domainId, tagTitle, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 解绑领域标签。
     */
    public JSONObject unbindDomainTag(final JSONObject identity, final String domainId, final String tagId,
                                      final String idempotencyKey, final Request request) {
        if (StringUtils.isBlank(domainId) || StringUtils.isBlank(tagId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_DOMAINS, false, actorRef),
                    actorId,
                    "admin.domain.tag.unbind",
                    idempotencyKey,
                    new JSONObject().put("domainId", domainId).put("tagId", tagId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        domainMgmtService.removeDomainTag(domainId, tagId);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_REMOVE_DOMAIN_TAG,
                                domainId + ":" + tagId, actorRef.get().user());
                        return new JSONObject().put("domainId", domainId).put("tagId", tagId).put("unbound", true);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("领域解绑标签失败, domainId={}, tagId={}", domainId, tagId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 分页查询标签列表。
     */
    public JSONObject listTags(final JSONObject identity, final int page, final int pageSize, final String keyword) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_TAGS, false);
            final int safePage = Math.max(1, page);
            final int safeSize = Math.min(Math.max(1, pageSize), 100);
            final int offset = (safePage - 1) * safeSize;

            final StringBuilder where = new StringBuilder(" WHERE 1=1");
            final List<Object> params = new ArrayList<>();
            if (StringUtils.isNotBlank(keyword)) {
                final String like = "%" + keyword.trim() + "%";
                where.append(" AND (").append(Tag.TAG_TITLE).append(" LIKE ? OR ")
                        .append(Tag.TAG_URI).append(" LIKE ?)");
                params.add(like);
                params.add(like);
            }

            final String table = tagRepository.getName();
            final List<JSONObject> totalRows = tagRepository.select(
                    "SELECT COUNT(*) AS totalCount FROM `" + table + "`" + where, params.toArray());
            final int total = totalRows.isEmpty() ? 0 : totalRows.getFirst().optInt("totalCount", 0);

            final List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(safeSize);
            queryParams.add(offset);
            final List<JSONObject> rows = tagRepository.select(
                    "SELECT * FROM `" + table + "`" + where + " ORDER BY tagReferenceCount DESC, oId DESC LIMIT ? OFFSET ?",
                    queryParams.toArray());

            tx.commit();
            return new JSONObject()
                    .put("items", new JSONArray(rows))
                    .put("pagination", pagination(safePage, safeSize, total));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询标签列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 新增标签。
     */
    public JSONObject addTag(final JSONObject identity, final String idempotencyKey,
                             final Request request, final JSONObject body) {
        final String tagTitle = ApiV3WriteInputs.string(body, Tag.TAG_TITLE, 64, false);
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_TAGS, false, actorRef),
                    actorId,
                    "admin.tag.add",
                    idempotencyKey,
                    new JSONObject().put(Tag.TAG_TITLE, tagTitle),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final String oId = tagMgmtService.addTag(actorId, tagTitle);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_ADD_TAG,
                                oId, actorRef.get().user());
                        return new JSONObject().put("oId", oId).put(Tag.TAG_TITLE, tagTitle);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "tag_add_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("新增标签失败, tagTitle={}", tagTitle, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 更新标签属性。
     */
    public JSONObject updateTag(final JSONObject identity, final String tagId,
                                final String idempotencyKey, final Request request, final JSONObject body) {
        if (StringUtils.isBlank(tagId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_TAGS, false, actorRef),
                    actorId,
                    "admin.tag.update",
                    idempotencyKey,
                    new JSONObject(body.toString()).put("tagId", tagId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject existing = tagRepository.get(tagId);
                        if (existing == null) {
                            throw ApiV3Requests.notFound();
                        }
                        if (body.has(Tag.TAG_TITLE)) {
                            existing.put(Tag.TAG_TITLE, ApiV3WriteInputs.string(body, Tag.TAG_TITLE, 64, false));
                        }
                        if (body.has(Tag.TAG_URI)) {
                            existing.put(Tag.TAG_URI, ApiV3WriteInputs.string(body, Tag.TAG_URI, 64, false));
                        }
                        if (body.has(Tag.TAG_DESCRIPTION)) {
                            existing.put(Tag.TAG_DESCRIPTION,
                                    ApiV3WriteInputs.string(body, Tag.TAG_DESCRIPTION, 2000, true));
                        }
                        if (body.has(Tag.TAG_ICON_PATH)) {
                            existing.put(Tag.TAG_ICON_PATH,
                                    ApiV3WriteInputs.string(body, Tag.TAG_ICON_PATH, 512, true));
                        }
                        if (body.has(Tag.TAG_STATUS)) {
                            existing.put(Tag.TAG_STATUS,
                                    ApiV3AdminService.requireExactInt(body, Tag.TAG_STATUS, 0, 1));
                        }
                        tagMgmtService.updateTag(tagId, existing);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_UPDATE_TAG,
                                tagId, actorRef.get().user());
                        return existing;
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "tag_update_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新标签失败, tagId={}", tagId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 清理所有未被使用的标签。
     */
    public JSONObject removeUnusedTags(final JSONObject identity, final String idempotencyKey, final Request request) {
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_TAGS, false, actorRef),
                    actorId,
                    "admin.tag.remove_unused",
                    idempotencyKey,
                    new JSONObject(),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        tagMgmtService.removeUnusedTags();
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_REMOVE_UNUSED_TAGS,
                                "unused", actorRef.get().user());
                        return new JSONObject().put("cleaned", true);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("清理未使用标签失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    // ==================== 保留词与广告位 ====================

    /**
     * 查询保留词列表。
     */
    public JSONObject listReservedWords(final JSONObject identity, final String keyword) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_RWS, false);
            final List<JSONObject> all = ReservedWords.getList();
            final JSONArray items = new JSONArray();
            final String q = StringUtils.trimToEmpty(keyword).toLowerCase();
            for (final JSONObject row : all) {
                final String word = row.optString(Option.OPTION_VALUE);
                if (q.isEmpty() || word.toLowerCase().contains(q)) {
                    items.put(new JSONObject()
                            .put("oId", row.optString(Keys.OBJECT_ID))
                            .put("word", word));
                }
            }
            tx.commit();
            return new JSONObject().put("items", items);
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询保留词列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 新增保留词。
     */
    public JSONObject addReservedWord(final JSONObject identity, final String idempotencyKey,
                                      final Request request, final JSONObject body) {
        final String word = ApiV3WriteInputs.string(body, "word", 128, false);
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_RWS, false, actorRef),
                    actorId,
                    "admin.reserved_word.add",
                    idempotencyKey,
                    new JSONObject().put("word", word),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        ReservedWords.add(word);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_ADD_RESERVED_WORD,
                                word, actorRef.get().user());
                        return new JSONObject().put("word", word).put("added", true);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("新增保留词失败, word={}", word, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 删除保留词。
     */
    public JSONObject removeReservedWord(final JSONObject identity, final String idempotencyKey,
                                         final Request request, final JSONObject body) {
        final String word = ApiV3WriteInputs.string(body, "word", 128, false);
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_RWS, false, actorRef),
                    actorId,
                    "admin.reserved_word.remove",
                    idempotencyKey,
                    new JSONObject().put("word", word),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        ReservedWords.remove(word);
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_REMOVE_RESERVED_WORD,
                                word, actorRef.get().user());
                        return new JSONObject().put("word", word).put("removed", true);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("删除保留词失败, word={}", word, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 读取侧边广告与顶栏横幅配置。
     */
    public JSONObject getAds(final JSONObject identity) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_AD, false);
            final JSONObject sideAd = optionRepository.get(Option.ID_C_SIDE_FULL_AD);
            final JSONObject banner = optionRepository.get(Option.ID_C_HEADER_BANNER);
            tx.commit();
            return new JSONObject()
                    .put("sideFullAd", sideAd == null ? "" : sideAd.optString(Option.OPTION_VALUE))
                    .put("headerBanner", banner == null ? "" : banner.optString(Option.OPTION_VALUE));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("读取广告配置失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 更新侧边广告或顶栏横幅配置。
     */
    public JSONObject updateAds(final JSONObject identity, final String idempotencyKey,
                                final Request request, final JSONObject body) {
        final String sideFullAd = body.has("sideFullAd")
                ? ApiV3WriteInputs.string(body, "sideFullAd", 20000, true)
                : null;
        final String headerBanner = body.has("headerBanner")
                ? ApiV3WriteInputs.string(body, "headerBanner", 20000, true)
                : null;
        if (sideFullAd == null && headerBanner == null) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_AD, false, actorRef),
                    actorId,
                    "admin.ad.update",
                    idempotencyKey,
                    new JSONObject()
                            .put("hasSideFullAd", sideFullAd != null)
                            .put("hasHeaderBanner", headerBanner != null),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        if (sideFullAd != null) {
                            upsertAdOption(Option.ID_C_SIDE_FULL_AD, sideFullAd);
                        }
                        if (headerBanner != null) {
                            upsertAdOption(Option.ID_C_HEADER_BANNER, headerBanner);
                        }
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_UPDATE_AD_POS,
                                Option.CATEGORY_C_AD, actorRef.get().user());
                        return new JSONObject().put("updated", true);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新广告配置失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    private void upsertAdOption(final String optionId, final String value) throws Exception {
        final JSONObject existing = optionRepository.get(optionId);
        if (existing == null) {
            optionMgmtService.addOption(new JSONObject()
                    .put(Keys.OBJECT_ID, optionId)
                    .put(Option.OPTION_CATEGORY, Option.CATEGORY_C_AD)
                    .put(Option.OPTION_VALUE, value));
        } else {
            existing.put(Option.OPTION_VALUE, value);
            optionMgmtService.updateOption(optionId, existing);
        }
    }

    private static void requireMilestoneAccess(final ApiV3AdminService.AuthorizedActor actor) {
        if (!actor.isAdmin() && !actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_MANAGE_MILESTONE)) {
            throw ApiV3Requests.forbidden();
        }
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
