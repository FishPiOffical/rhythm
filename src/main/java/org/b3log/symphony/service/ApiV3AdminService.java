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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ApiV3Token;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.model.Report;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3IdentityApplicationRepository;
import org.b3log.symphony.repository.ApiV3IdentityReviewerRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.ApiV3TokenRepository;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.EmotionRepository;
import org.b3log.symphony.repository.LivenessRepository;
import org.b3log.symphony.repository.NotificationRepository;
import org.b3log.symphony.repository.OperationRepository;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.repository.ReportRepository;
import org.b3log.symphony.repository.RoleRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.VisitRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * V3 管理后台业务服务，统一提供权限能力聚合、工作台统计、举报审核、复读机移除及用户管理接口。
 */
@Service
public class ApiV3AdminService {

    private static final Logger LOGGER = LogManager.getLogger(ApiV3AdminService.class);
    private static final String SECURITY_OPERATOR_NAME = "adlered";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /**
     * 管理后台逐项映射的核心权限集合，用于稳定返回布尔能力字典。
     */
    private static final List<String> TRACKED_PERMISSIONS = List.of(
            Permission.PERMISSION_ID_C_MENU_ADMIN,
            Permission.PERMISSION_ID_C_MENU_ADMIN_USERS,
            Permission.PERMISSION_ID_C_MENU_ADMIN_REPORTS,
            Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES,
            Permission.PERMISSION_ID_C_MENU_ADMIN_COMMENTS,
            Permission.PERMISSION_ID_C_MENU_ADMIN_BREEZEMOONS,
            Permission.PERMISSION_ID_C_MENU_ADMIN_DOMAINS,
            Permission.PERMISSION_ID_C_MENU_ADMIN_TAGS,
            Permission.PERMISSION_ID_C_MENU_ADMIN_RWS,
            Permission.PERMISSION_ID_C_MENU_ADMIN_ICS,
            Permission.PERMISSION_ID_C_MENU_ADMIN_AD,
            Permission.PERMISSION_ID_C_MENU_ADMIN_ROLES,
            Permission.PERMISSION_ID_C_MENU_ADMIN_MISC,
            Permission.PERMISSION_ID_C_MENU_MANAGE_MILESTONE,
            Permission.PERMISSION_ID_C_IP_MANAGE,
            Permission.PERMISSION_ID_C_USER_UPDATE_USER_BASIC,
            Permission.PERMISSION_ID_C_USER_UPDATE_USER_ADVANCED,
            Permission.PERMISSION_ID_C_USER_REMOVE_MFA,
            Permission.PERMISSION_ID_C_USER_ADJUST_BAG,
            Permission.PERMISSION_ID_C_USER_GIVE_METAL,
            Permission.PERMISSION_ID_C_USER_REMOVE_METAL,
            Permission.PERMISSION_ID_C_USER_ADD_POINT,
            Permission.PERMISSION_ID_C_USER_EXCHANGE_POINT,
            Permission.PERMISSION_ID_C_USER_DEDUCT_POINT
    );

    public static final Set<String> USER_UPDATE_ALLOWED_FIELDS = Set.of(
            "userNickname",
            "userURL",
            "userIntro",
            "userTags",
            "userEmail",
            "userStatus",
            "userRole",
            "userCommentStatus",
            "userPointStatus"
    );

    @Inject
    private ApiV3RequestRepository requests;
    @Inject
    private ApiV3TokenRepository tokens;
    @Inject
    private ApiV3SecurityProofService proofs;
    @Inject
    private ApiV3IdentityReviewerRepository reviewers;
    @Inject
    private ApiV3IdentityApplicationRepository identityApplications;
    @Inject
    private UserRepository users;
    @Inject
    private RoleRepository roles;
    @Inject
    private RoleQueryService roleQueryService;
    @Inject
    private OptionQueryService optionQueryService;
    @Inject
    private ReportRepository reports;
    @Inject
    private ReportQueryService reportQueryService;
    @Inject
    private ArticleRepository articles;
    @Inject
    private CommentRepository comments;
    @Inject
    private RepeaterContentRepository repeaters;
    @Inject
    private ProfessionSourceEventCaptureService professionSourceEventCaptureService;
    @Inject
    private NotificationRepository notifications;
    @Inject
    private LivenessRepository livenesses;
    @Inject
    private VisitRepository visits;
    @Inject
    private EmotionRepository emotions;
    @Inject
    private OperationRepository operations;
    @Inject
    private NotificationMgmtService notificationMgmtService;
    @Inject
    private AvatarQueryService avatarQueryService;
    @Inject
    private CloudService cloudService;
    @Inject
    private ChatRoomService chatRoomService;
    @Inject
    private LangPropsService langPropsService;

    private record AuthorizedActor(
            JSONObject user,
            String userId,
            String userName,
            String roleId,
            Set<String> permissions,
            boolean isAdmin,
            boolean isIdentityReviewer
    ) {
    }

    /**
     * 获取当前第一方登录用户的管理后台能力矩阵、九大一级入口与动作授权。
     */
    public JSONObject capabilities(final JSONObject identity) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(identity, "profile.read", null, false);
            final JSONObject result = buildCapabilitiesPayload(actor);
            tx.commit();
            return result;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("读取管理后台能力失败", e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    /**
     * 读取管理后台工作台真实待办数量、社区基础统计及最近管理操作日志。
     */
    public JSONObject dashboard(final JSONObject identity) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(identity, "profile.read", null, false);
            final boolean canAccessAdmin = actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN);
            if (!canAccessAdmin && !actor.isIdentityReviewer()) {
                throw ApiV3Requests.forbidden();
            }

            final JSONObject todos = new JSONObject();
            if (canAccessAdmin && actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN_REPORTS)) {
                todos.put("pendingReports", countQuery(
                        "SELECT COUNT(oId) AS cnt FROM `" + reports.getName() + "` WHERE " + Report.REPORT_HANDLED + "=?",
                        Report.REPORT_HANDLED_C_NOT));
            } else {
                todos.put("pendingReports", JSONObject.NULL);
            }

            if (actor.isIdentityReviewer()) {
                todos.put("pendingIdentityApplications", countQuery(
                        "SELECT COUNT(oId) AS cnt FROM `" + identityApplications.getName() + "` WHERE status=?",
                        "pending"));
            } else {
                todos.put("pendingIdentityApplications", JSONObject.NULL);
            }

            final JSONObject communityStats = new JSONObject();
            if (canAccessAdmin) {
                communityStats.put("memberCount", readOptionNumericValue(Option.ID_C_STATISTIC_MEMBER_COUNT));
                communityStats.put("articleCount", readOptionNumericValue(Option.ID_C_STATISTIC_ARTICLE_COUNT));
                communityStats.put("commentCount", readOptionNumericValue(Option.ID_C_STATISTIC_CMT_COUNT));
                communityStats.put("domainCount", readOptionNumericValue(Option.ID_C_STATISTIC_DOMAIN_COUNT));
                communityStats.put("tagCount", readOptionNumericValue(Option.ID_C_STATISTIC_TAG_COUNT));
                communityStats.put("linkCount", readOptionNumericValue(Option.ID_C_STATISTIC_LINK_COUNT));
                communityStats.put("maxOnlineVisitorCount", readOptionNumericValue(Option.ID_C_STATISTIC_MAX_ONLINE_VISITOR_COUNT));
            } else {
                communityStats.put("memberCount", JSONObject.NULL);
                communityStats.put("articleCount", JSONObject.NULL);
                communityStats.put("commentCount", JSONObject.NULL);
                communityStats.put("domainCount", JSONObject.NULL);
                communityStats.put("tagCount", JSONObject.NULL);
                communityStats.put("linkCount", JSONObject.NULL);
                communityStats.put("maxOnlineVisitorCount", JSONObject.NULL);
            }

            final JSONArray recentOps = new JSONArray();
            if (canAccessAdmin) {
                final List<JSONObject> rows = operations.select(
                        "SELECT oId,operationUserId,operationCode,operationDataId,operationCreated,operationIP "
                                + "FROM `" + operations.getName() + "` ORDER BY operationCreated DESC, oId DESC LIMIT 10");
                for (final JSONObject row : rows) {
                    recentOps.put(formatOperationRow(row));
                }
            }

            final JSONObject meta = new JSONObject()
                    .put("fetchedAt", System.currentTimeMillis())
                    .put("source", "Rhythm 实时数据库")
                    .put("statsNote", "缺失数据源的统计项显示为未知；历史趋势与并发去重需后续建设持久化指标后接入。");

            final JSONObject result = new JSONObject()
                    .put("capabilities", buildCapabilitiesPayload(actor))
                    .put("todos", todos)
                    .put("communityStats", communityStats)
                    .put("recentOperations", recentOps)
                    .put("meta", meta);
            tx.commit();
            return result;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("读取管理工作台失败", e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    /**
     * 分页检索举报记录，并附带结构化安全清洗后的对象上下文，不直接输出未转义富文本脚本。
     */
    public JSONObject getReports(final JSONObject identity, final int p, final int size,
                                 final String status, final String dataType) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_REPORTS, false);
            requireMenuAdmin(actor);

            final List<String> conditions = new ArrayList<>();
            final List<Object> args = new ArrayList<>();
            switch (status) {
                case "pending" -> {
                    conditions.add(Report.REPORT_HANDLED + "=?");
                    args.add(Report.REPORT_HANDLED_C_NOT);
                }
                case "handled" -> {
                    conditions.add(Report.REPORT_HANDLED + "=?");
                    args.add(Report.REPORT_HANDLED_C_YES);
                }
                case "ignored" -> {
                    conditions.add(Report.REPORT_HANDLED + "=?");
                    args.add(Report.REPORT_HANDLED_C_IGNORED);
                }
                default -> {
                    // all 状态不额外过滤处理进度
                }
            }
            if (!"all".equals(dataType)) {
                conditions.add(Report.REPORT_DATA_TYPE + "=?");
                args.add(Integer.parseInt(dataType));
            }

            final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
            final long total = countQuery("SELECT COUNT(oId) AS cnt FROM `" + reports.getName() + "`" + where, args.toArray());

            final long offset = (long) (p - 1) * size;
            final List<Object> listArgs = new ArrayList<>(args);
            listArgs.add(size);
            listArgs.add(offset);
            final List<JSONObject> rows = reports.select(
                    "SELECT * FROM `" + reports.getName() + "`" + where + " ORDER BY oId DESC LIMIT ? OFFSET ?",
                    listArgs.toArray());

            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) {
                list.put(buildReportDto(row, actor.userId()));
            }
            final JSONObject result = ApiV3Requests.pagination(list, p, size, total);
            tx.commit();
            return result;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询举报列表失败", e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    /**
     * 标记举报为已处理（与“忽略”“删除目标”动作严格分离，且禁止处理本人提交的举报）。
     */
    public JSONObject handleReport(final JSONObject identity, final String reportId,
                                   final String idempotencyKey, final Request request) {
        return mutateReportStatus(identity, reportId, idempotencyKey, request,
                "admin.reports.handle", Report.REPORT_HANDLED_C_YES, Operation.OPERATION_CODE_C_MAKE_REPORT_HANDLED);
    }

    /**
     * 标记举报为已忽略（与“已处理”“删除目标”严格分离，且禁止操作本人提交的举报）。
     */
    public JSONObject ignoreReport(final JSONObject identity, final String reportId,
                                   final String idempotencyKey, final Request request) {
        return mutateReportStatus(identity, reportId, idempotencyKey, request,
                "admin.reports.ignore", Report.REPORT_HANDLED_C_IGNORED, Operation.OPERATION_CODE_C_MAKE_REPORT_IGNORED);
    }

    /**
     * 移除复读机转录站目标内容（独立于举报状态变更，需显式幂等键并在严格事务内写入审计与事件）。
     */
    public JSONObject removeRepeater(final JSONObject identity, final String repeaterId,
                                     final String idempotencyKey, final Request request) {
        if (!repeaterId.matches("[1-9][0-9]{0,18}")) {
            throw ApiV3Requests.invalid();
        }
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();
        final String actorId = actorId(identity);
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_REPORTS, false, actorRef),
                    actorId,
                    "admin.repeater.remove",
                    idempotencyKey,
                    new JSONObject().put("repeaterId", repeaterId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final var rows = repeaters.select(
                                "SELECT oId," + RepeaterContent.STATUS + " FROM `" + repeaters.getName()
                                        + "` WHERE oId=? FOR UPDATE",
                                repeaterId);
                        if (rows.isEmpty()) {
                            throw ApiV3Requests.notFound();
                        }
                        if (RepeaterContent.STATUS_C_VALID != rows.getFirst().optInt(RepeaterContent.STATUS, -1)) {
                            throw new ApiV3Exception(409, "repeater_already_removed", "该复读机内容已被移除，请刷新核对最新状态");
                        }
                        final long now = System.currentTimeMillis();
                        final int updated = ApiV3TransactionStatements.execute(
                                "UPDATE `" + repeaters.getName() + "` SET " + RepeaterContent.STATUS + "=?,"
                                        + RepeaterContent.UPDATED_TIME + "=? WHERE oId=?",
                                RepeaterContent.STATUS_C_REMOVED, now, repeaterId);
                        if (updated != 1) {
                            throw unavailable();
                        }
                        professionSourceEventCaptureService.targetRemoved("repeater", repeaterId);
                        operations.add(Operation.newOperation(
                                request, Operation.OPERATION_CODE_C_REMOVE_REPEATER, repeaterId, actorRef.get().user()));
                        return new JSONObject()
                                .put("repeaterId", repeaterId)
                                .put("status", RepeaterContent.STATUS_C_REMOVED)
                                .put("removedAt", now);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("移除复读机内容失败, repeaterId={}", repeaterId, e);
            throw unavailable();
        }
    }

    /**
     * 多条件分页查询管理后台用户列表，仅返回脱敏后的展示与管理必要字段。
     */
    public JSONObject getUsers(final JSONObject identity, final int p, final int size,
                               final String keyword, final String status, final String roleId) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_USERS, false);
            requireMenuAdmin(actor);

            final List<String> conditions = new ArrayList<>();
            final List<Object> args = new ArrayList<>();
            final String q = keyword == null ? "" : keyword.trim();
            if (q.length() > 64) {
                throw ApiV3Requests.invalid();
            }
            if (!q.isEmpty()) {
                final String escaped = escapeLike(q);
                final String pattern = "%" + escaped + "%";
                conditions.add("(" + User.USER_NAME + " LIKE ? ESCAPE '!' OR "
                        + UserExt.USER_NICKNAME + " LIKE ? ESCAPE '!' OR "
                        + Keys.OBJECT_ID + "=? OR "
                        + UserExt.USER_NO + "=? OR "
                        + User.USER_EMAIL + "=? OR "
                        + UserExt.USER_LATEST_LOGIN_IP + "=?)");
                args.add(pattern);
                args.add(pattern);
                args.add(q);
                args.add(q);
                args.add(q);
                args.add(q);
            }
            if (!"all".equals(status)) {
                conditions.add(UserExt.USER_STATUS + "=?");
                args.add(Integer.parseInt(status));
            }
            if (roleId != null && !roleId.isBlank() && !"all".equals(roleId)) {
                conditions.add(User.USER_ROLE + "=?");
                args.add(roleId.trim());
            }

            final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
            final long total = countQuery("SELECT COUNT(oId) AS cnt FROM `" + users.getName() + "`" + where, args.toArray());

            final long offset = (long) (p - 1) * size;
            final List<Object> listArgs = new ArrayList<>(args);
            listArgs.add(size);
            listArgs.add(offset);
            final List<JSONObject> rows = users.select(
                    "SELECT * FROM `" + users.getName() + "`" + where + " ORDER BY oId DESC LIMIT ? OFFSET ?",
                    listArgs.toArray());

            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) {
                list.put(toSanitizedUserSummary(row, actor.userId()));
            }
            final JSONObject result = ApiV3Requests.pagination(list, p, size, total);
            result.put("roleOptions", listRoleOptions());
            tx.commit();
            return result;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询后台用户列表失败", e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    /**
     * 查询单个用户管理详情、勋章/背包摘要及最近操作审计记录（绝不返回密码、令牌或 MFA 秘钥）。
     */
    public JSONObject getUserDetail(final JSONObject identity, final String userId) {
        final AuthorizedActor actor;
        final JSONObject detailPayload;
        final Transaction tx = begin();
        try {
            actor = authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_USERS, false);
            requireMenuAdmin(actor);
            detailPayload = loadSanitizedUserDetailInTransaction(userId, actor.userId());
            tx.commit();
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询用户详情失败, userId={}", userId, e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }

        // 在只读事务外补充云端背包与勋章展示摘要，避免跨服务锁嵌套
        detailPayload.put("bagItems", parseBagItems(cloudService.getBag(userId)));
        detailPayload.put("medals", parseMedalList(cloudService.getMedal(userId)));
        return detailPayload;
    }

    /**
     * 更新用户基础或高级资料。基础资料与高级状态/角色严格按操作者权限校验，永久停用账号禁止常规编辑。
     */
    public JSONObject updateUser(final JSONObject identity, final String userId,
                                 final JSONObject body, final String idempotencyKey, final Request request) {
        if (body == null || body.isEmpty() || !USER_UPDATE_ALLOWED_FIELDS.containsAll(body.keySet())) {
            throw ApiV3WriteInputs.invalid();
        }
        final JSONObject normalized = normalizeUserUpdateInput(body);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();
        final AtomicBoolean roleChangedRef = new AtomicBoolean(false);
        final AtomicReference<String> roleDiffRef = new AtomicReference<>("");
        final String actorId = actorId(identity);

        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            final ApiV3MutationStore store = guardedWrite(
                    identity, Permission.PERMISSION_ID_C_MENU_ADMIN_USERS, false, actorRef);
            execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    store,
                    actorId,
                    "admin.users.update",
                    idempotencyKey,
                    new JSONObject().put("userId", userId).put("patch", normalized),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final AuthorizedActor actor = actorRef.get();
                        final JSONObject target = users.getForUpdate(userId);
                        if (target == null) {
                            throw ApiV3Requests.notFound();
                        }
                        if (UserExt.USER_STATUS_C_DEACTIVATED == target.optInt(UserExt.USER_STATUS, -1)) {
                            throw new ApiV3Exception(422, "user_deactivated", "已永久停用账号不可通过常规资料编辑修改或恢复");
                        }

                        final JSONObject changes = validateAndBuildUserChanges(actor, target, normalized);
                        final String oldRole = target.optString(User.USER_ROLE);
                        final String newRole = changes.optString(User.USER_ROLE, oldRole);
                        final boolean emailChanged = changes.has(User.USER_EMAIL)
                                && !changes.getString(User.USER_EMAIL).equals(target.optString(User.USER_EMAIL));
                        final boolean roleChanged = changes.has(User.USER_ROLE) && !oldRole.equals(newRole);

                        if (!changes.isEmpty()) {
                            changes.put(UserExt.USER_UPDATE_TIME, System.currentTimeMillis());
                            users.updateColumnsInCurrentTransaction(userId, changes);

                            // 若封禁或置为登录失效，同步吊销该用户的 V3 令牌确保即时失效
                            if (changes.has(UserExt.USER_STATUS)) {
                                final int nextStatus = changes.getInt(UserExt.USER_STATUS);
                                if (nextStatus == UserExt.USER_STATUS_C_INVALID
                                        || nextStatus == UserExt.USER_STATUS_C_INVALID_LOGIN) {
                                    revokeUserTokensInCurrentTransaction(userId);
                                }
                            }
                        }

                        operations.add(Operation.newOperation(
                                request, Operation.OPERATION_CODE_C_UPDATE_USER, userId, actor.user()));
                        if (emailChanged) {
                            operations.add(Operation.newOperation(
                                    request, Operation.OPERATION_CODE_C_UPDATE_USER_EMAIL, userId, actor.user()));
                        }

                        if (roleChanged) {
                            roleChangedRef.set(true);
                            roleDiffRef.set(oldRole + "-" + newRole);
                        }

                        return new JSONObject()
                                .put("userId", userId)
                                .put("updatedFields", new JSONArray(changes.keySet()))
                                .put("updatedAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新用户资料失败, userId={}", userId, e);
            throw unavailable();
        }

        if (execution.applied()) {
            try {
                users.evictSettingsCaches(userId);
                users.refreshSettingsCache(userId);
            } catch (final Exception e) {
                LOGGER.warn("刷新用户缓存失败, userId={}", userId, e);
            }
            if (roleChangedRef.get()) {
                try {
                    final JSONObject notification = new JSONObject()
                            .put(Notification.NOTIFICATION_USER_ID, userId)
                            .put(Notification.NOTIFICATION_DATA_ID, roleDiffRef.get());
                    notificationMgmtService.addSysAnnounceRoleChangedNotification(notification);
                } catch (final Exception e) {
                    LOGGER.warn("发送角色变更系统通知失败, userId={}", userId, e);
                }
            }
        }

        // 提交后重读真实最新用户详情返回给调用方，避免前端仅依赖旧请求载荷回显
        final JSONObject freshDetail = getUserDetail(identity, userId);
        return new JSONObject(execution.result().toString())
                .put("replayed", !execution.applied())
                .put("detail", freshDetail);
    }

    /**
     * 永久停用并匿名化指定账号。仅限管理员角色执行，严格禁止停用操作者本人，且与普通状态编辑隔离。
     */
    public JSONObject deactivateUser(final JSONObject identity, final String userId,
                                     final String idempotencyKey, final Request request) {
        final String actorId = actorId(identity);
        if (actorId.equals(userId)) {
            throw new ApiV3Exception(422, "cannot_deactivate_self", "禁止永久停用当前登录管理员本人");
        }
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();
        final AtomicReference<String> oldUserNameRef = new AtomicReference<>("");

        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            final ApiV3MutationStore store = guardedWrite(
                    identity, Permission.PERMISSION_ID_C_MENU_ADMIN_USERS, true, actorRef);
            execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    store,
                    actorId,
                    "admin.users.deactivate",
                    idempotencyKey,
                    new JSONObject().put("userId", userId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject target = users.getForUpdate(userId);
                        if (target == null) {
                            throw ApiV3Requests.notFound();
                        }
                        if (UserExt.USER_STATUS_C_DEACTIVATED == target.optInt(UserExt.USER_STATUS, -1)) {
                            throw new ApiV3Exception(409, "user_already_deactivated", "该账号已处于永久停用状态");
                        }
                        if (Role.ROLE_ID_C_ADMIN.equals(target.optString(User.USER_ROLE))) {
                            throw new ApiV3Exception(422, "cannot_deactivate_admin", "请先调整管理员角色后再执行永久停用");
                        }

                        final String oldUserName = target.optString(User.USER_NAME);
                        oldUserNameRef.set(oldUserName);
                        final String newName = UserExt.ANONYMOUS_USER_NAME + target.optString(UserExt.USER_NO);
                        final long now = System.currentTimeMillis();
                        final String randomPwd = DigestUtils.md5Hex(now + ":" + UUID.randomUUID());

                        final JSONObject changes = new JSONObject()
                                .put(User.USER_NAME, newName)
                                .put(User.USER_EMAIL, newName + UserExt.USER_BUILTIN_EMAIL_SUFFIX)
                                .put("userPhone", UserExt.NULL_USER_PHONE)
                                .put(User.USER_PASSWORD, randomPwd)
                                .put(UserExt.USER_NICKNAME, "")
                                .put(UserExt.USER_TAGS, "")
                                .put(User.USER_URL, "")
                                .put(UserExt.USER_INTRO, "")
                                .put(UserExt.USER_QQ, "")
                                .put(UserExt.USER_AVATAR_TYPE, UserExt.USER_AVATAR_TYPE_C_UPLOAD)
                                .put(UserExt.USER_AVATAR_URL, AvatarQueryService.DEFAULT_AVATAR_URL)
                                .put(UserExt.USER_CITY, "")
                                .put(UserExt.USER_PROVINCE, "")
                                .put(UserExt.USER_COUNTRY, "")
                                .put(UserExt.USER_LATEST_LOGIN_IP, "")
                                .put(UserExt.USER_INDEX_REDIRECT_URL, "")
                                .put("secret2fa", "")
                                .put("mbti", "")
                                .put(User.USER_ROLE, Role.ROLE_ID_C_DEFAULT)
                                .put(UserExt.USER_ONLINE_FLAG, false)
                                .put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_DEACTIVATED)
                                .put(UserExt.USER_UPDATE_TIME, now);

                        users.updateColumnsInCurrentTransaction(userId, changes);
                        revokeUserTokensInCurrentTransaction(userId);
                        notifications.removeByUserId(userId);
                        livenesses.removeByUserId(userId);
                        visits.removeByUserId(userId);
                        emotions.removeByUserId(userId);
                        operations.add(Operation.newOperation(
                                request, Operation.OPERATION_CODE_C_DEACTIVATE_USER, userId, actorRef.get().user()));

                        return new JSONObject()
                                .put("userId", userId)
                                .put("userStatus", UserExt.USER_STATUS_C_DEACTIVATED)
                                .put("userName", newName)
                                .put("deactivatedAt", now);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("永久停用账号失败, userId={}", userId, e);
            throw unavailable();
        }

        if (execution.applied()) {
            try {
                if (!oldUserNameRef.get().isEmpty()) {
                    users.evictUserNameCaches(userId, oldUserNameRef.get());
                }
                users.evictSettingsCaches(userId);
                users.refreshSettingsCache(userId);
            } catch (final Exception e) {
                LOGGER.warn("停用账号后清理缓存失败, userId={}", userId, e);
            }
        }

        final JSONObject freshDetail = getUserDetail(identity, userId);
        return new JSONObject(execution.result().toString())
                .put("replayed", !execution.applied())
                .put("detail", freshDetail);
    }

    /**
     * 清除目标用户的两步验证（MFA）绑定。需具备 userRemoveMFA 权限且不可操作永久停用账号。
     */
    public JSONObject removeMfa(final JSONObject identity, final String userId,
                                final String idempotencyKey, final Request request) {
        final String actorId = actorId(identity);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();

        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            final ApiV3MutationStore store = guardedWrite(
                    identity, Permission.PERMISSION_ID_C_USER_REMOVE_MFA, false, actorRef);
            execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    store,
                    actorId,
                    "admin.users.remove-mfa",
                    idempotencyKey,
                    new JSONObject().put("userId", userId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject target = users.getForUpdate(userId);
                        if (target == null) {
                            throw ApiV3Requests.notFound();
                        }
                        if (UserExt.USER_STATUS_C_DEACTIVATED == target.optInt(UserExt.USER_STATUS, -1)) {
                            throw new ApiV3Exception(422, "user_deactivated", "已永久停用账号无需且不可执行 MFA 清除");
                        }
                        if (StringUtils.isBlank(target.optString("secret2fa"))) {
                            throw new ApiV3Exception(409, "mfa_not_bound", "该用户当前未绑定 MFA 两步验证");
                        }
                        final long now = System.currentTimeMillis();
                        users.updateColumnsInCurrentTransaction(userId, new JSONObject()
                                .put("secret2fa", "")
                                .put(UserExt.USER_UPDATE_TIME, now));
                        operations.add(Operation.newOperation(
                                request, Operation.OPERATION_CODE_C_UPDATE_USER, userId, actorRef.get().user()));
                        return new JSONObject()
                                .put("userId", userId)
                                .put("hasMfa", false)
                                .put("updatedAt", now);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("移除用户 MFA 失败, userId={}", userId, e);
            throw unavailable();
        }

        if (execution.applied()) {
            try {
                users.evictSettingsCaches(userId);
                users.refreshSettingsCache(userId);
            } catch (final Exception e) {
                LOGGER.warn("移除 MFA 后刷新缓存失败, userId={}", userId, e);
            }
        }

        final JSONObject freshDetail = getUserDetail(identity, userId);
        return new JSONObject(execution.result().toString())
                .put("replayed", !execution.applied())
                .put("detail", freshDetail);
    }

    private JSONObject mutateReportStatus(final JSONObject identity, final String reportId,
                                          final String idempotencyKey, final Request request,
                                          final String operationName, final int targetStatus, final int operationCode) {
        final String actorId = actorId(identity);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    guardedWrite(identity, Permission.PERMISSION_ID_C_MENU_ADMIN_REPORTS, false, actorRef),
                    actorId,
                    operationName,
                    idempotencyKey,
                    new JSONObject().put("reportId", reportId).put("targetStatus", targetStatus),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final var rows = reports.select(
                                "SELECT oId," + Report.REPORT_USER_ID + "," + Report.REPORT_HANDLED
                                        + " FROM `" + reports.getName() + "` WHERE oId=? FOR UPDATE",
                                reportId);
                        if (rows.isEmpty()) {
                            throw ApiV3Requests.notFound();
                        }
                        final JSONObject report = rows.getFirst();
                        if (actorId.equals(report.optString(Report.REPORT_USER_ID))) {
                            throw new ApiV3Exception(422, "self_report_forbidden", "不能处理自己提交的举报");
                        }
                        if (Report.REPORT_HANDLED_C_NOT != report.optInt(Report.REPORT_HANDLED, -1)) {
                            throw new ApiV3Exception(409, "report_state_conflict", "该举报已被处理或忽略，请刷新后核对最新状态");
                        }
                        final int updated = ApiV3TransactionStatements.execute(
                                "UPDATE `" + reports.getName() + "` SET " + Report.REPORT_HANDLED + "=? WHERE oId=?",
                                targetStatus, reportId);
                        if (updated != 1) {
                            throw unavailable();
                        }
                        operations.add(Operation.newOperation(request, operationCode, reportId, actorRef.get().user()));
                        return new JSONObject()
                                .put("reportId", reportId)
                                .put("reportHandled", targetStatus)
                                .put("updatedAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("变更举报状态失败, reportId={}, targetStatus={}", reportId, targetStatus, e);
            throw unavailable();
        }
    }

    /**
     * 在当前事务中核验第一方网站登录身份、活跃状态与角色权限。
     */
    private AuthorizedActor authorizeInCurrentTransaction(final JSONObject identity, final String scope,
                                                        final String requiredPermission, final boolean requireAdminRole)
            throws RepositoryException {
        final String expectedActorId = actorId(identity);
        final JSONObject actorUser = proofs.authorizeFirstPartyInCurrentTransaction(identity, scope);
        if (actorUser == null || !expectedActorId.equals(actorUser.optString(Keys.OBJECT_ID))) {
            throw ApiV3Requests.forbidden();
        }
        final String userName = actorUser.optString(User.USER_NAME);
        final String roleId = actorUser.optString(User.USER_ROLE);
        final Set<String> permissions = roleQueryService.getPermissions(roleId);
        final boolean isAdmin = Role.ROLE_ID_C_ADMIN.equals(roleId);
        final boolean isReviewer = isIdentityReviewer(expectedActorId);

        if (requireAdminRole && !isAdmin) {
            throw ApiV3Requests.forbidden();
        }
        if (requiredPermission != null && !permissions.contains(requiredPermission)) {
            throw ApiV3Requests.forbidden();
        }
        return new AuthorizedActor(actorUser, expectedActorId, userName, roleId, permissions, isAdmin, isReviewer);
    }

    /**
     * 构造受权限与幂等表加锁保护的写存储适配。
     */
    private ApiV3MutationStore guardedWrite(final JSONObject identity, final String requiredPermission,
                                             final boolean requireAdminRole,
                                             final AtomicReference<AuthorizedActor> actorRef) {
        return new ApiV3MutationStore() {
            @Override
            public Transaction beginTransaction() {
                return begin();
            }

            @Override
            public boolean hasTransactionBegun() {
                return requests.hasTransactionBegun();
            }

            @Override
            public JSONObject getForUpdate(final String id) throws RepositoryException {
                final AuthorizedActor actor = authorizeInCurrentTransaction(
                        identity, "settings.write", requiredPermission, requireAdminRole);
                requireMenuAdmin(actor);
                actorRef.set(actor);
                return requests.getForUpdate(id);
            }

            @Override
            public String add(final JSONObject row) throws RepositoryException {
                return requests.add(row);
            }

            @Override
            public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
                requests.update(id, row, fields);
            }
        };
    }

    private void requireMenuAdmin(final AuthorizedActor actor) {
        if (!actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN)) {
            throw ApiV3Requests.forbidden();
        }
    }

    private boolean isIdentityReviewer(final String userId) throws RepositoryException {
        return !reviewers.select(
                "SELECT oId FROM `" + reviewers.getName() + "` WHERE oId=? AND userId=? LIMIT 1",
                "enterprise", userId).isEmpty();
    }

    private JSONObject buildCapabilitiesPayload(final AuthorizedActor actor) {
        final Set<String> perms = actor.permissions();
        final boolean canAccessAdmin = perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN);
        final boolean isAdmin = actor.isAdmin();
        final boolean isReviewer = actor.isIdentityReviewer();
        final boolean isSecurityOperator = isAdmin && SECURITY_OPERATOR_NAME.equals(actor.userName());

        final JSONObject permFlags = new JSONObject();
        for (final String tracked : TRACKED_PERMISSIONS) {
            permFlags.put(tracked, perms.contains(tracked));
        }

        final JSONObject menus = new JSONObject()
                .put("workbench", canAccessAdmin || isReviewer)
                .put("review", isReviewer || (canAccessAdmin && (hasAny(perms,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_REPORTS,
                        Permission.PERMISSION_ID_C_MENU_MANAGE_MILESTONE) || isAdmin)))
                .put("content", canAccessAdmin && hasAny(perms,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_COMMENTS,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_BREEZEMOONS))
                .put("users", canAccessAdmin && hasAny(perms,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_USERS,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_ROLES,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_ICS))
                .put("membershipAssets", canAccessAdmin && (isAdmin || hasAny(perms,
                        Permission.PERMISSION_ID_C_USER_ADD_POINT,
                        Permission.PERMISSION_ID_C_USER_DEDUCT_POINT,
                        Permission.PERMISSION_ID_C_USER_EXCHANGE_POINT,
                        Permission.PERMISSION_ID_C_USER_GIVE_METAL,
                        Permission.PERMISSION_ID_C_USER_REMOVE_METAL,
                        Permission.PERMISSION_ID_C_USER_ADJUST_BAG)))
                .put("communityOps", canAccessAdmin && hasAny(perms,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_DOMAINS,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_TAGS,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_RWS,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_AD,
                        Permission.PERMISSION_ID_C_MENU_MANAGE_MILESTONE,
                        Permission.PERMISSION_ID_C_MENU_ADMIN_MISC))
                .put("professionsPets", canAccessAdmin && isAdmin)
                .put("openPlatform", canAccessAdmin && isAdmin)
                .put("systemSecurity", canAccessAdmin);

        final JSONObject actions = new JSONObject()
                .put("reportHandle", canAccessAdmin && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_REPORTS))
                .put("reportIgnore", canAccessAdmin && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_REPORTS))
                .put("repeaterRemove", canAccessAdmin && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_REPORTS))
                .put("identityReview", isReviewer)
                .put("userEditBasic", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_USERS)
                        && perms.contains(Permission.PERMISSION_ID_C_USER_UPDATE_USER_BASIC))
                .put("userEditAdvanced", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_USERS)
                        && perms.contains(Permission.PERMISSION_ID_C_USER_UPDATE_USER_ADVANCED))
                .put("userEditRole", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_USERS)
                        && perms.contains(Permission.PERMISSION_ID_C_USER_UPDATE_USER_ADVANCED)
                        && (isAdmin || Role.ROLE_ID_C_OP.equals(actor.roleId())))
                .put("userDeactivate", canAccessAdmin && isAdmin)
                .put("userRemoveMfa", canAccessAdmin && perms.contains(Permission.PERMISSION_ID_C_USER_REMOVE_MFA))
                .put("oauthManage", canAccessAdmin && isAdmin);

        final JSONObject userCopy = new JSONObject(actor.user().toString());
        avatarQueryService.fillUserAvatarURL(userCopy);
        final JSONObject role = roleQueryService.getRole(actor.roleId());
        final String roleName = role == null ? actor.roleId() : role.optString(Role.ROLE_NAME, actor.roleId());

        return new JSONObject()
                .put("canAccessAdmin", canAccessAdmin)
                .put("canEnterConsole", canAccessAdmin || isReviewer)
                .put("userId", actor.userId())
                .put("userName", actor.userName())
                .put("userNickname", actor.user().optString(UserExt.USER_NICKNAME))
                .put("avatarUrl", userCopy.optString(UserExt.USER_AVATAR_URL))
                .put("roleId", actor.roleId())
                .put("roleName", roleName)
                .put("isAdmin", isAdmin)
                .put("isSecurityOperator", isSecurityOperator)
                .put("isIdentityReviewer", isReviewer)
                .put("rhypicEnabled", Symphonys.RHPIC_ENABLED)
                .put("permissions", permFlags)
                .put("menus", menus)
                .put("actions", actions)
                .put("fetchedAt", System.currentTimeMillis());
    }

    private JSONObject buildReportDto(final JSONObject record, final String actorId) throws RepositoryException {
        final String id = record.optString(Keys.OBJECT_ID);
        final String reporterId = record.optString(Report.REPORT_USER_ID);
        final JSONObject reporter = users.get(reporterId);
        final String reporterName = reporter == null ? "未知用户" : reporter.optString(User.USER_NAME);
        final String reporterNickname = reporter == null ? "" : reporter.optString(UserExt.USER_NICKNAME);

        final String dataId = record.optString(Report.REPORT_DATA_ID);
        final int dataType = record.optInt(Report.REPORT_DATA_TYPE);
        final JSONObject target = buildReportTargetContext(dataType, dataId);
        final int reportType = record.optInt(Report.REPORT_TYPE);

        return new JSONObject()
                .put("oId", id)
                .put("createdAt", safeLong(id))
                .put("reportUserId", reporterId)
                .put("reporterName", reporterName)
                .put("reporterNickname", reporterNickname)
                .put("isOwnReport", actorId.equals(reporterId))
                .put("reportDataId", dataId)
                .put("reportDataType", dataType)
                .put("reportDataTypeLabel", reportDataTypeLabel(dataType))
                .put("reportType", reportType)
                .put("reportTypeLabel", reportTypeLabel(reportType))
                .put("reportMemo", stripTagsAndTrim(record.optString(Report.REPORT_MEMO), 600))
                .put("reportHandled", record.optInt(Report.REPORT_HANDLED))
                .put("target", target);
    }

    private JSONObject buildReportTargetContext(final int dataType, final String dataId) throws RepositoryException {
        final JSONObject target = new JSONObject()
                .put("id", dataId)
                .put("dataType", dataType)
                .put("exists", false)
                .put("title", "")
                .put("snippet", "")
                .put("url", "")
                .put("status", -1)
                .put("statusLabel", "对象不存在或不可读取")
                .put("removable", false)
                .put("revoked", false);

        switch (dataType) {
            case Report.REPORT_DATA_TYPE_C_ARTICLE -> {
                final JSONObject article = articles.get(dataId);
                if (article != null) {
                    final int st = article.optInt(Article.ARTICLE_STATUS, -1);
                    target.put("exists", true)
                            .put("title", article.optString(Article.ARTICLE_TITLE))
                            .put("url", "/article/" + article.optString(Keys.OBJECT_ID))
                            .put("status", st)
                            .put("statusLabel", switch (st) {
                                case Article.ARTICLE_STATUS_C_VALID -> "正常";
                                case Article.ARTICLE_STATUS_C_INVALID -> "已封禁/无效";
                                case Article.ARTICLE_STATUS_C_LOCKED -> "已锁定";
                                default -> "未知状态";
                            });
                }
            }
            case Report.REPORT_DATA_TYPE_C_COMMENT -> {
                final JSONObject comment = comments.get(dataId);
                if (comment != null) {
                    final String articleId = comment.optString(Comment.COMMENT_ON_ARTICLE_ID);
                    final JSONObject article = StringUtils.isBlank(articleId) ? null : articles.get(articleId);
                    final int st = comment.optInt(Comment.COMMENT_STATUS, -1);
                    target.put("exists", true)
                            .put("title", article == null ? "所属文章 #" + articleId : article.optString(Article.ARTICLE_TITLE))
                            .put("snippet", stripTagsAndTrim(comment.optString(Comment.COMMENT_CONTENT), 240))
                            .put("url", StringUtils.isBlank(articleId) ? "" : "/article/" + articleId + "#comment-" + dataId)
                            .put("status", st)
                            .put("statusLabel", st == Comment.COMMENT_STATUS_C_VALID ? "正常" : "已移除/无效");
                }
            }
            case Report.REPORT_DATA_TYPE_C_USER -> {
                final JSONObject reported = users.get(dataId);
                if (reported != null) {
                    final String uname = reported.optString(User.USER_NAME);
                    final int st = reported.optInt(UserExt.USER_STATUS, -1);
                    target.put("exists", true)
                            .put("title", uname)
                            .put("snippet", reported.optString(UserExt.USER_NICKNAME))
                            .put("url", "/member/" + uname)
                            .put("status", st)
                            .put("statusLabel", userStatusLabel(st));
                }
            }
            case Report.REPORT_DATA_TYPE_C_CHAT -> {
                target.put("url", "/cr?oId=" + dataId);
                final JSONObject chatMsg = chatRoomService.getChatMsg(dataId);
                if (chatMsg != null) {
                    final JSONObject content = safeParseJson(chatMsg.optString(Common.CONTENT));
                    final String author = content.optString(User.USER_NAME);
                    final String text = stripTagsAndTrim(content.optString(Common.CONTENT), 400);
                    target.put("exists", true)
                            .put("title", author.isEmpty() ? "聊天室消息" : "发言人：" + author)
                            .put("snippet", text.isEmpty() ? "[富媒体或空内容]" : text)
                            .put("status", 0)
                            .put("statusLabel", "在线可查看");
                } else {
                    final JSONObject log = reportQueryService.findChatRevokeLog(dataId);
                    if (log != null) {
                        final JSONObject logData = safeParseJson(log.optString("data"));
                        final JSONObject content = safeParseJson(logData.optString(Common.CONTENT));
                        final String author = content.optString(User.USER_NAME);
                        final String text = stripTagsAndTrim(content.optString(Common.CONTENT), 400);
                        target.put("exists", true)
                                .put("revoked", true)
                                .put("title", author.isEmpty() ? "已撤回聊天消息" : "原发言人：" + author)
                                .put("snippet", "[已撤回/删除快照] " + (text.isEmpty() ? "[非纯文本内容]" : text))
                                .put("status", 1)
                                .put("statusLabel", "已撤回（保留审计快照）");
                    } else {
                        target.put("snippet", "内容已被撤回或删除，且审计日志无该记录。原消息 oId：" + dataId)
                                .put("statusLabel", "已撤回且无快照");
                    }
                }
            }
            case Report.REPORT_DATA_TYPE_C_REPEATER -> {
                final JSONObject repeater = repeaters.get(dataId);
                if (repeater != null) {
                    final int st = repeater.optInt(RepeaterContent.STATUS, -1);
                    final boolean valid = RepeaterContent.STATUS_C_VALID == st && dataId.matches("[1-9][0-9]{0,18}");
                    target.put("exists", true)
                            .put("title", "复读内容 #" + dataId + "（" + repeater.optString(RepeaterContent.TYPE) + "）")
                            .put("snippet", stripTagsAndTrim(repeater.optString(RepeaterContent.CONTENT), 500))
                            .put("status", st)
                            .put("statusLabel", RepeaterContent.STATUS_C_VALID == st ? "正常可见" : "已移除")
                            .put("removable", valid);
                }
            }
            default -> {
                // 未知举报目标类型保持默认提示
            }
        }
        return target;
    }

    private JSONObject loadSanitizedUserDetailInTransaction(final String userId, final String actorId)
            throws RepositoryException {
        final List<JSONObject> rows = users.select(
                "SELECT * FROM `" + users.getName() + "` WHERE oId=? LIMIT 1", userId);
        if (rows.isEmpty()) {
            throw ApiV3Requests.notFound();
        }
        final JSONObject row = rows.getFirst();
        final JSONObject profile = toSanitizedUserSummary(row, actorId);
        profile.put("userURL", row.optString(User.USER_URL));
        profile.put("userIntro", row.optString(UserExt.USER_INTRO));
        profile.put("userTags", row.optString(UserExt.USER_TAGS));
        profile.put("userArticleCount", row.optInt(UserExt.USER_ARTICLE_COUNT));
        profile.put("userCommentCount", row.optInt(UserExt.USER_COMMENT_COUNT));
        profile.put("userTagCount", row.optInt(UserExt.USER_TAG_COUNT));
        profile.put("onlineMinute", row.optInt(UserExt.ONLINE_MINUTE));
        profile.put("userOnlineFlag", row.optBoolean(UserExt.USER_ONLINE_FLAG));
        profile.put("userCommentStatus", row.optInt(UserExt.USER_COMMENT_STATUS));
        profile.put("userPointStatus", row.optInt(UserExt.USER_POINT_STATUS));
        profile.put("userFollowerStatus", row.optInt(UserExt.USER_FOLLOWER_STATUS));
        profile.put("userFollowingUserStatus", row.optInt(UserExt.USER_FOLLOWING_USER_STATUS));

        final List<JSONObject> opRows = operations.select(
                "SELECT oId,operationUserId,operationCode,operationDataId,operationCreated,operationIP "
                        + "FROM `" + operations.getName()
                        + "` WHERE operationDataId=? ORDER BY operationCreated DESC, oId DESC LIMIT 15",
                userId);
        final JSONArray recentOps = new JSONArray();
        for (final JSONObject opRow : opRows) {
            recentOps.put(formatOperationRow(opRow));
        }

        return new JSONObject()
                .put("user", profile)
                .put("recentOperations", recentOps)
                .put("roleOptions", listRoleOptions());
    }

    private JSONObject toSanitizedUserSummary(final JSONObject row, final String actorId) {
        final JSONObject copy = new JSONObject();
        final String id = row.optString(Keys.OBJECT_ID);
        final String roleId = row.optString(User.USER_ROLE);
        final JSONObject role = roleQueryService.getRole(roleId);
        final String roleName = role == null ? roleId : role.optString(Role.ROLE_NAME, roleId);

        final JSONObject avatarProbe = new JSONObject()
                .put(Keys.OBJECT_ID, id)
                .put(UserExt.USER_AVATAR_TYPE, row.optString(UserExt.USER_AVATAR_TYPE))
                .put(UserExt.USER_AVATAR_URL, row.optString(UserExt.USER_AVATAR_URL))
                .put(User.USER_EMAIL, row.optString(User.USER_EMAIL));
        avatarQueryService.fillUserAvatarURL(avatarProbe);

        copy.put("oId", id);
        copy.put("userNo", row.optString(UserExt.USER_NO));
        copy.put("userName", row.optString(User.USER_NAME));
        copy.put("userNickname", row.optString(UserExt.USER_NICKNAME));
        copy.put("userEmail", row.optString(User.USER_EMAIL));
        copy.put("userPhoneMasked", maskPhone(row.optString("userPhone")));
        copy.put("userAvatarURL", avatarProbe.optString(UserExt.USER_AVATAR_URL));
        copy.put("userRole", roleId);
        copy.put("roleName", roleName);
        copy.put("userStatus", row.optInt(UserExt.USER_STATUS));
        copy.put("userPoint", row.optInt(UserExt.USER_POINT));
        copy.put("userUsedPoint", row.optInt(UserExt.USER_USED_POINT));
        copy.put("userLatestLoginTime", row.optLong(UserExt.USER_LATEST_LOGIN_TIME));
        copy.put("userLatestLoginIP", row.optString(UserExt.USER_LATEST_LOGIN_IP));
        copy.put("createdAt", safeLong(id));
        copy.put("hasMfa", StringUtils.isNotBlank(row.optString("secret2fa")));
        copy.put("isSelf", actorId.equals(id));
        return copy;
    }

    private JSONObject normalizeUserUpdateInput(final JSONObject body) {
        final JSONObject normalized = new JSONObject();
        if (body.has("userNickname")) {
            normalized.put("userNickname", ApiV3WriteInputs.string(body, "userNickname", 32, true));
        }
        if (body.has("userURL")) {
            final String url = ApiV3WriteInputs.string(body, "userURL", 255, true);
            if (!url.isEmpty() && !isValidHttpUrl(url)) {
                throw new ApiV3Exception(422, "invalid_request", "个人主页链接必须为 http 或 https 协议");
            }
            normalized.put("userURL", url);
        }
        if (body.has("userIntro")) {
            normalized.put("userIntro", ApiV3WriteInputs.string(body, "userIntro", 255, true));
        }
        if (body.has("userTags")) {
            normalized.put("userTags", ApiV3WriteInputs.string(body, "userTags", 255, true));
        }
        if (body.has("userEmail")) {
            final String email = ApiV3WriteInputs.string(body, "userEmail", 128, true).toLowerCase(Locale.ROOT);
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new ApiV3Exception(422, "invalid_request", "邮箱格式不正确");
            }
            normalized.put("userEmail", email);
        }
        if (body.has("userStatus")) {
            final int status = requireExactInt(body, "userStatus", 0, 3);
            normalized.put("userStatus", status);
        }
        if (body.has("userCommentStatus")) {
            normalized.put("userCommentStatus", requireExactInt(body, "userCommentStatus", 0, 1));
        }
        if (body.has("userPointStatus")) {
            normalized.put("userPointStatus", requireExactInt(body, "userPointStatus", 0, 1));
        }
        if (body.has("userRole")) {
            final String roleId = ApiV3WriteInputs.string(body, "userRole", 64, true);
            if (roleId.isEmpty()) {
                throw ApiV3WriteInputs.invalid();
            }
            normalized.put("userRole", roleId);
        }
        if (normalized.isEmpty()) {
            throw ApiV3WriteInputs.invalid();
        }
        return normalized;
    }

    private JSONObject validateAndBuildUserChanges(final AuthorizedActor actor, final JSONObject target,
                                                   final JSONObject normalized)
            throws RepositoryException {
        final JSONObject changes = new JSONObject();
        final Set<String> perms = actor.permissions();
        final String targetId = target.optString(Keys.OBJECT_ID);
        final boolean isSelf = actor.userId().equals(targetId);

        final boolean touchesBasic = normalized.has("userNickname")
                || normalized.has("userURL")
                || normalized.has("userIntro")
                || normalized.has("userTags")
                || normalized.has("userEmail");
        if (touchesBasic && !perms.contains(Permission.PERMISSION_ID_C_USER_UPDATE_USER_BASIC)) {
            throw ApiV3Requests.forbidden();
        }

        final boolean touchesAdvanced = normalized.has("userStatus")
                || normalized.has("userCommentStatus")
                || normalized.has("userPointStatus")
                || normalized.has("userRole");
        if (touchesAdvanced && !perms.contains(Permission.PERMISSION_ID_C_USER_UPDATE_USER_ADVANCED)) {
            throw ApiV3Requests.forbidden();
        }

        if (normalized.has("userNickname")) {
            changes.put(UserExt.USER_NICKNAME, normalized.getString("userNickname"));
        }
        if (normalized.has("userURL")) {
            changes.put(User.USER_URL, normalized.getString("userURL"));
        }
        if (normalized.has("userIntro")) {
            changes.put(UserExt.USER_INTRO, normalized.getString("userIntro"));
        }
        if (normalized.has("userTags")) {
            changes.put(UserExt.USER_TAGS, normalized.getString("userTags"));
        }
        if (normalized.has("userEmail")) {
            final String nextEmail = normalized.getString("userEmail");
            final String currentEmail = target.optString(User.USER_EMAIL);
            if (!nextEmail.equalsIgnoreCase(currentEmail)) {
                final var dup = users.select(
                        "SELECT oId FROM `" + users.getName() + "` WHERE " + User.USER_EMAIL + "=? AND oId<>? LIMIT 1",
                        nextEmail, targetId);
                if (!dup.isEmpty()) {
                    throw new ApiV3Exception(409, "email_exists", "该邮箱已被其他账号使用");
                }
                changes.put(User.USER_EMAIL, nextEmail);
            }
        }
        if (normalized.has("userCommentStatus")) {
            changes.put(UserExt.USER_COMMENT_STATUS, normalized.getInt("userCommentStatus"));
        }
        if (normalized.has("userPointStatus")) {
            changes.put(UserExt.USER_POINT_STATUS, normalized.getInt("userPointStatus"));
        }
        if (normalized.has("userStatus")) {
            final int nextStatus = normalized.getInt("userStatus");
            if (isSelf && nextStatus != UserExt.USER_STATUS_C_VALID) {
                throw new ApiV3Exception(422, "invalid_request", "禁止将当前登录管理员自身状态设置为非正常状态");
            }
            changes.put(UserExt.USER_STATUS, nextStatus);
        }
        if (normalized.has("userRole")) {
            final String oldRole = target.optString(User.USER_ROLE);
            final String nextRole = normalized.getString("userRole");
            if (!nextRole.equals(oldRole)) {
                if (isSelf) {
                    throw new ApiV3Exception(422, "invalid_request", "禁止修改当前登录管理员自身角色");
                }
                final boolean canAssignRole = actor.isAdmin() || Role.ROLE_ID_C_OP.equals(actor.roleId());
                if (!canAssignRole) {
                    throw ApiV3Requests.forbidden();
                }
                if ((Role.ROLE_ID_C_ADMIN.equals(oldRole) || Role.ROLE_ID_C_ADMIN.equals(nextRole)) && !actor.isAdmin()) {
                    throw ApiV3Requests.forbidden();
                }
                if (roles.get(nextRole) == null) {
                    throw new ApiV3Exception(422, "invalid_request", "指定角色不存在");
                }
                changes.put(User.USER_ROLE, nextRole);
            }
        }
        return changes;
    }

    private void revokeUserTokensInCurrentTransaction(final String userId) throws RepositoryException {
        ApiV3TransactionStatements.execute(
                "UPDATE `" + tokens.getName() + "` SET state=?,updatedAt=? WHERE userId=? AND (state<>? OR state IS NULL)",
                ApiV3Token.STATE_REVOKED, System.currentTimeMillis(), userId, ApiV3Token.STATE_REVOKED);
    }

    private JSONArray listRoleOptions() throws RepositoryException {
        final List<JSONObject> roleRows = roles.select("SELECT oId,roleName FROM `" + roles.getName() + "` ORDER BY oId ASC");
        final JSONArray options = new JSONArray();
        for (final JSONObject row : roleRows) {
            final String roleId = row.optString(Keys.OBJECT_ID);
            final JSONObject resolved = roleQueryService.getRole(roleId);
            final String roleName = resolved == null ? row.optString(Role.ROLE_NAME, roleId)
                    : resolved.optString(Role.ROLE_NAME, roleId);
            options.put(new JSONObject().put("oId", roleId).put("roleName", roleName));
        }
        return options;
    }

    private JSONObject formatOperationRow(final JSONObject row) throws RepositoryException {
        final String operatorId = row.optString(Operation.OPERATION_USER_ID);
        final JSONObject operator = StringUtils.isBlank(operatorId) ? null : users.get(operatorId);
        final int code = row.optInt(Operation.OPERATION_CODE, -1);
        return new JSONObject()
                .put("oId", row.optString(Keys.OBJECT_ID))
                .put("operationUserId", operatorId)
                .put("operationUserName", operator == null ? "系统/未知" : operator.optString(User.USER_NAME))
                .put("operationCode", code)
                .put("operationCodeLabel", operationCodeLabel(code))
                .put("operationDataId", row.optString(Operation.OPERATION_DATA_ID))
                .put("operationCreated", row.optLong(Operation.OPERATION_CREATED))
                .put("operationIP", row.optString(Operation.OPERATION_IP));
    }

    private Object readOptionNumericValue(final String optionId) {
        final JSONObject option = optionQueryService.getOption(optionId);
        if (option == null) {
            return JSONObject.NULL;
        }
        final String raw = option.optString(Option.OPTION_VALUE, "").trim();
        if (raw.isEmpty()) {
            return JSONObject.NULL;
        }
        try {
            return Long.parseLong(raw);
        } catch (final NumberFormatException ignored) {
            return JSONObject.NULL;
        }
    }

    private long countQuery(final String sql, final Object... args) throws RepositoryException {
        final List<JSONObject> rows = reports.select(sql, args);
        return rows.isEmpty() ? 0L : rows.getFirst().optLong("cnt", 0L);
    }

    private JSONArray parseBagItems(final String rawJson) {
        final JSONArray items = new JSONArray();
        final JSONObject bag = safeParseJson(rawJson);
        for (final String key : new LinkedHashSet<>(bag.keySet())) {
            final int count = bag.optInt(key, 0);
            if (count > 0) {
                items.put(new JSONObject().put("name", key).put("count", count));
            }
        }
        return items;
    }

    private JSONArray parseMedalList(final String rawJson) {
        final JSONObject parsed = safeParseJson(rawJson);
        final JSONArray rawList = parsed.optJSONArray("list");
        return rawList == null ? new JSONArray() : rawList;
    }

    private Transaction begin() {
        if (requests.hasTransactionBegun()) {
            throw new IllegalStateException("admin v3 root transaction required");
        }
        return ApiV3Transactions.strict(requests.beginTransaction());
    }

    private static void rollbackIfActive(final Transaction tx) {
        if (tx != null && tx.isActive()) {
            tx.rollback();
        }
    }

    private static String actorId(final JSONObject identity) {
        final JSONObject user = identity == null ? null : identity.optJSONObject("user");
        final String actor = user == null ? "" : user.optString(Keys.OBJECT_ID);
        if (!actor.matches("[1-9][0-9]{0,18}")) {
            throw ApiV3Requests.forbidden();
        }
        return actor;
    }

    private static boolean hasAny(final Set<String> granted, final String... candidates) {
        for (final String candidate : candidates) {
            if (granted.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static int requireExactInt(final JSONObject body, final String key, final int min, final int max) {
        final Object raw = body.opt(key);
        if (!(raw instanceof Number number) || raw instanceof Double || raw instanceof Float) {
            throw ApiV3WriteInputs.invalid();
        }
        final long value = number.longValue();
        if (value < min || value > max) {
            throw ApiV3WriteInputs.invalid();
        }
        return (int) value;
    }

    private static boolean isValidHttpUrl(final String value) {
        try {
            final URI uri = URI.create(value);
            final String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && StringUtils.isNotBlank(uri.getHost());
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String escapeLike(final String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String maskPhone(final String phone) {
        if (StringUtils.isBlank(phone) || UserExt.NULL_USER_PHONE.equals(phone)) {
            return "";
        }
        if (phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return "***";
    }

    private static long safeLong(final String raw) {
        try {
            return Long.parseLong(raw);
        } catch (final Exception ignored) {
            return 0L;
        }
    }

    private static JSONObject safeParseJson(final String raw) {
        if (StringUtils.isBlank(raw)) {
            return new JSONObject();
        }
        try {
            return new JSONObject(raw);
        } catch (final Exception ignored) {
            return new JSONObject();
        }
    }

    private static String stripTagsAndTrim(final String raw, final int maxLen) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        final String stripped = HTML_TAG_PATTERN.matcher(raw).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return stripped.length() > maxLen ? stripped.substring(0, maxLen) + "..." : stripped;
    }

    private static String reportDataTypeLabel(final int dataType) {
        return switch (dataType) {
            case Report.REPORT_DATA_TYPE_C_ARTICLE -> "文章";
            case Report.REPORT_DATA_TYPE_C_COMMENT -> "评论";
            case Report.REPORT_DATA_TYPE_C_USER -> "用户";
            case Report.REPORT_DATA_TYPE_C_CHAT -> "聊天消息";
            case Report.REPORT_DATA_TYPE_C_REPEATER -> "复读机内容";
            default -> "其他类型";
        };
    }

    private String reportTypeLabel(final int reportType) {
        return switch (reportType) {
            case Report.REPORT_TYPE_C_SPAM_AD -> langOrFallback("spamADLabel", "垃圾广告");
            case Report.REPORT_TYPE_C_PORNOGRAPHIC -> langOrFallback("pornographicLabel", "色情低俗");
            case Report.REPORT_TYPE_C_VIOLATION_OF_REGULATIONS -> langOrFallback("violationOfRegulationsLabel", "违法违规");
            case Report.REPORT_TYPE_C_ALLEGEDLY_INFRINGING -> langOrFallback("allegedlyInfringingLabel", "涉嫌侵权");
            case Report.REPORT_TYPE_C_PERSONAL_ATTACKS -> langOrFallback("personalAttacksLabel", "人身攻击");
            case Report.REPORT_TYPE_C_POSING_ACCOUNT -> langOrFallback("posingAccountLabel", "冒充他人");
            case Report.REPORT_TYPE_C_SPAM_AD_ACCOUNT -> langOrFallback("spamADAccountLabel", "广告账号");
            case Report.REPORT_TYPE_C_PERSONAL_INFO_VIOLATION -> langOrFallback("personalInfoViolationLabel", "泄露隐私");
            case Report.REPORT_TYPE_C_OTHER -> langOrFallback("miscLabel", "其他原因");
            default -> "其他原因";
        };
    }

    private String langOrFallback(final String key, final String fallback) {
        final String value = langPropsService.get(key);
        return StringUtils.isBlank(value) ? fallback : value;
    }

    private static String userStatusLabel(final int status) {
        return switch (status) {
            case UserExt.USER_STATUS_C_VALID -> "正常";
            case UserExt.USER_STATUS_C_INVALID -> "封禁";
            case UserExt.USER_STATUS_C_NOT_VERIFIED -> "未验证";
            case UserExt.USER_STATUS_C_INVALID_LOGIN -> "登录失效";
            case UserExt.USER_STATUS_C_DEACTIVATED -> "永久停用";
            default -> "未知状态";
        };
    }

    private static String operationCodeLabel(final int code) {
        return switch (code) {
            case Operation.OPERATION_CODE_C_MAKE_REPORT_IGNORED -> "忽略举报";
            case Operation.OPERATION_CODE_C_MAKE_REPORT_HANDLED -> "标记举报已处理";
            case Operation.OPERATION_CODE_C_UPDATE_USER -> "编辑用户信息/安全项";
            case Operation.OPERATION_CODE_C_UPDATE_USER_EMAIL -> "修改用户邮箱";
            case Operation.OPERATION_CODE_C_DEACTIVATE_USER -> "永久停用用户";
            case Operation.OPERATION_CODE_C_REMOVE_REPEATER -> "移除复读机内容";
            case Operation.OPERATION_CODE_C_REMOVE_ARTICLE -> "移除文章";
            case Operation.OPERATION_CODE_C_UPDATE_ARTICLE -> "更新文章";
            case Operation.OPERATION_CODE_C_REMOVE_COMMENT -> "移除评论";
            case Operation.OPERATION_CODE_C_UPDATE_COMMENT -> "更新评论";
            case Operation.OPERATION_CODE_C_UPDATE_ROLE_PERMS -> "更新角色权限";
            case Operation.OPERATION_CODE_C_UPDATE_MISC -> "修改系统杂项配置";
            default -> "管理操作 #" + code;
        };
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "管理后台服务暂不可用");
    }
}
