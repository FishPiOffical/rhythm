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
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.ApiV3Token;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Breezemoon;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.model.Report;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3IdentityApplicationRepository;
import org.b3log.symphony.repository.ApiV3IdentityReviewerRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.ApiV3TokenRepository;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.BreezemoonRepository;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.EmotionRepository;
import org.b3log.symphony.repository.LivenessRepository;
import org.b3log.symphony.repository.LongArticleChapterRepository;
import org.b3log.symphony.repository.LongArticleColumnRepository;
import org.b3log.symphony.repository.NotificationRepository;
import org.b3log.symphony.repository.OperationRepository;
import org.b3log.symphony.repository.OptionRepository;
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
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.Headers;
import org.b3log.symphony.util.ReservedWords;
import org.b3log.symphony.util.Runes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

    private record OperationCodeMeta(int code, String label, String targetType) {
    }

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
            Permission.PERMISSION_ID_C_USER_DEDUCT_POINT,
            Permission.PERMISSION_ID_C_ARTICLE_ADD_ARTICLE,
            Permission.PERMISSION_ID_C_ARTICLE_UPDATE_ARTICLE_BASIC,
            Permission.PERMISSION_ID_C_ARTICLE_STICK_ARTICLE,
            Permission.PERMISSION_ID_C_ARTICLE_CANCEL_STICK_ARTICLE,
            Permission.PERMISSION_ID_C_ARTICLE_REMOVE_ARTICLE,
            Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLE_INDEX,
            Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLES_INDEX,
            Permission.PERMISSION_ID_C_COMMENT_UPDATE_COMMENT_BASIC,
            Permission.PERMISSION_ID_C_COMMENT_REMOVE_COMMENT,
            Permission.PERMISSION_ID_C_BREEZEMOON_UPDATE_BREEZEMOON,
            Permission.PERMISSION_ID_C_BREEZEMOON_REMOVE_BREEZEMOON
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

    public static final Set<String> ARTICLE_CREATE_ALLOWED_FIELDS = Set.of(
            "userName",
            "articleTitle",
            "articleContent",
            "articleRewardContent",
            "articleTags",
            "articleRewardPoint",
            "articleQnAOfferPoint",
            "articleType",
            "articleShowInList",
            "articleStatement",
            "articleCommentable",
            "columnId",
            "columnTitle",
            "columnCoverURL",
            "chapterNo",
            "time"
    );

    public static final Set<String> ARTICLE_UPDATE_ALLOWED_FIELDS = Set.of(
            "articleTitle",
            "articleContent",
            "articleRewardContent",
            "articleTags",
            "articleRewardPoint",
            "articleQnAOfferPoint",
            "articlePushOrder",
            "articleViewCount",
            "articleGoodCnt",
            "articleBadCnt",
            "articleCollectCnt",
            "articleWatchCnt",
            "articleCommentable",
            "articleType",
            "articleStatus",
            "articlePerfect",
            "articleStatement",
            "articleShowInList",
            "columnId",
            "columnTitle",
            "columnCoverURL",
            "chapterNo"
    );

    public static final Set<String> COMMENT_UPDATE_ALLOWED_FIELDS = Set.of(
            "commentContent",
            "commentStatus",
            "commentGoodCnt",
            "commentBadCnt",
            "commentVisible"
    );

    public static final Set<String> BREEZEMOON_UPDATE_ALLOWED_FIELDS = Set.of(
            "breezemoonContent",
            "breezemoonStatus"
    );

    private static final List<OperationCodeMeta> OPERATION_CODE_META_LIST = List.of(
            new OperationCodeMeta(Operation.OPERATION_CODE_C_MAKE_REPORT_IGNORED, "忽略举报", "report"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_MAKE_REPORT_HANDLED, "标记举报已处理", "report"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_USER_ADDR, "更新用户地址", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REMOVE_ROLE, "删除角色", "role"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_CHANGE_ARTICLE_EMAIL_PUSH_ORDER, "调整文章邮件推送排序", "article"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_BREEZEMOON, "更新清风明月", "breezemoon"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REMOVE_BREEZEMOON, "移除清风明月", "breezemoon"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_PUSH_TELEGRAM, "推送至 Telegram", "article"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_WITHDRAW_B3T, "提现 B3T", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REMOVE_UNUSED_TAGS, "清理未使用标签", "tag"),
            new OperationCodeMeta(Operation.OPERAIONT_CODE_C_ADD_ROLE, "新增角色", "role"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_ROLE_PERMS, "更新角色权限", "role"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_ADD_AD_POS, "新增广告位", "ad"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_AD_POS, "更新广告位", "ad"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_ADD_TAG, "新增标签", "tag"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_STICK_ARTICLE, "置顶文章", "article"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_CANCEL_STICK_ARTICLE, "取消置顶文章", "article"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_GENERATE_INVITECODES, "生成邀请码", "invitecode"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_INVITECODE, "更新邀请码", "invitecode"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_ADD_ARTICLE, "后台发布文章", "article"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_ADD_RESERVED_WORD, "新增保留词", "reserved_word"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_RESERVED_WORD, "更新保留词", "reserved_word"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REMOVE_RESERVED_WORD, "移除保留词", "reserved_word"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REMOVE_COMMENT, "移除评论", "comment"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REMOVE_ARTICLE, "移除文章", "article"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_ADD_USER, "新增用户", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_USER, "编辑用户信息/安全项", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_USER_EMAIL, "修改用户邮箱", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_USER_NAME, "修改用户名", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_CHARGE_POINT, "充值积分", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_DEDUCT_POINT, "扣除积分", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_INIT_POINT, "初始化积分", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_EXCHANGE_POINT, "兑换积分", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_ARTICLE, "更新文章", "article"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_COMMENT, "更新评论", "comment"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_MISC, "修改系统杂项配置", "misc"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_TAG, "更新标签", "tag"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_DOMAIN, "更新领域", "domain"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_ADD_DOMAIN, "新增领域", "domain"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REMOVE_DOMAIN, "移除领域", "domain"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_ADD_DOMAIN_TAG, "绑定领域标签", "domain"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REMOVE_DOMAIN_TAG, "解绑领域标签", "domain"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REBUILD_ALGOLIA_TAG, "重建标签 Algolia 索引", "tag"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REBUILD_ALGOLIA_USER, "重建用户 Algolia 索引", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REBUILD_ARTICLES_SEARCH, "重建全站文章搜索索引", "system"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REBUILD_ARTICLE_SEARCH, "重建单篇文章搜索索引", "article"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_DELETE_PICTURE, "删除图片", "picture"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_UPDATE_MILESTONE, "更新里程碑", "milestone"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_DELETE_MILESTONE, "删除里程碑", "milestone"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_DEACTIVATE_USER, "永久停用用户", "user"),
            new OperationCodeMeta(Operation.OPERATION_CODE_C_REMOVE_REPEATER, "移除复读机内容", "repeater")
    );

    private static final Map<Integer, OperationCodeMeta> OPERATION_CODE_META_MAP = buildOperationCodeMetaMap();

    private static Map<Integer, OperationCodeMeta> buildOperationCodeMetaMap() {
        final Map<Integer, OperationCodeMeta> map = new LinkedHashMap<>();
        for (final OperationCodeMeta meta : OPERATION_CODE_META_LIST) {
            map.put(meta.code(), meta);
        }
        return Map.copyOf(map);
    }

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
    private OptionRepository options;
    @Inject
    private ReportRepository reports;
    @Inject
    private ReportQueryService reportQueryService;
    @Inject
    private ArticleRepository articles;
    @Inject
    private ArticleMgmtService articleMgmtService;
    @Inject
    private CommentRepository comments;
    @Inject
    private CommentMgmtService commentMgmtService;
    @Inject
    private BreezemoonRepository breezemoons;
    @Inject
    private BreezemoonMgmtService breezemoonMgmtService;
    @Inject
    private UserMgmtService userMgmtService;
    @Inject
    private LongArticleColumnRepository longArticleColumns;
    @Inject
    private LongArticleChapterRepository longArticleChapters;
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

    record AuthorizedActor(
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
                        "SELECT oId,operationUserId,operationCode,operationDataId,operationCreated,operationIP,operationUA "
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
            final boolean canViewAuditLog = actor.permissions().contains(Permission.PERMISSION_ID_C_MENU_ADMIN);
            detailPayload = loadSanitizedUserDetailInTransaction(userId, actor.userId(), canViewAuditLog);
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
                        final long now = System.currentTimeMillis();
                        final String newName = userMgmtService.deactivateUserInCurrentTransaction(userId);
                        revokeUserTokensInCurrentTransaction(userId);
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
     * 在当前事务中核验第一方网站登录身份、活跃状态与角色权限，并加载非敏感操作者信息。
     */
    AuthorizedActor authorizeInCurrentTransaction(final JSONObject identity, final String scope,
                                                  final String requiredPermission, final boolean requireAdminRole)
            throws RepositoryException {
        final String expectedActorId = actorId(identity);
        final JSONObject proofUser = proofs.authorizeFirstPartyInCurrentTransaction(identity, scope);
        if (proofUser == null || !expectedActorId.equals(proofUser.optString(Keys.OBJECT_ID))) {
            throw ApiV3Requests.forbidden();
        }
        // proofs 仅返回主键，此处加载操作者非敏感基础资料与角色信息以供权限校验与审计展示
        final List<JSONObject> actorRows = users.select(
                "SELECT oId,userName,userNickname,userRole,userEmail,userStatus,userAvatarType,userAvatarURL "
                        + "FROM `" + users.getName() + "` WHERE oId=? LIMIT 1",
                expectedActorId);
        if (actorRows.isEmpty()) {
            throw ApiV3Requests.forbidden();
        }
        final JSONObject actorUser = actorRows.getFirst();
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
    ApiV3MutationStore guardedWrite(final JSONObject identity, final String requiredPermission,
                                     final boolean requireAdminRole,
                                     final AtomicReference<AuthorizedActor> actorRef) {
        return guardedWrite(identity, requiredPermission, requireAdminRole, actorRef, false);
    }

    /**
     * 构造受权限与幂等表加锁保护的写存储适配，必要时在获取用户锁前先锁定全局文章统计行以保持全局加锁序一致。
     */
    ApiV3MutationStore guardedWrite(final JSONObject identity, final String requiredPermission,
                                     final boolean requireAdminRole,
                                     final AtomicReference<AuthorizedActor> actorRef,
                                     final boolean lockStatisticOption) {
        return guardedWrite(identity, requiredPermission, requireAdminRole, actorRef, lockStatisticOption, actor -> true);
    }

    ApiV3MutationStore guardedWrite(final JSONObject identity, final String requiredPermission,
                                     final boolean requireAdminRole,
                                     final AtomicReference<AuthorizedActor> actorRef,
                                     final boolean lockStatisticOption,
                                     final java.util.function.Predicate<AuthorizedActor> actionAllowed) {
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
                final JSONObject existingRequest = requests.getForUpdate(id);
                if (lockStatisticOption) {
                    // 按全局统一顺序先锁定全站文章统计配置行，再进入用户与文章行锁
                    if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) {
                        throw new IllegalStateException("缺少全站文章统计配置项");
                    }
                }
                final AuthorizedActor actor = authorizeInCurrentTransaction(
                        identity, "settings.write", requiredPermission, requireAdminRole);
                requireMenuAdmin(actor);
                if (!actionAllowed.test(actor)) throw ApiV3Requests.forbidden();
                actorRef.set(actor);
                return existingRequest;
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
                .put("oauthManage", canAccessAdmin && isAdmin)
                .put("articleCreate", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES)
                        && perms.contains(Permission.PERMISSION_ID_C_ARTICLE_ADD_ARTICLE))
                .put("articleEdit", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES)
                        && perms.contains(Permission.PERMISSION_ID_C_ARTICLE_UPDATE_ARTICLE_BASIC))
                .put("articleStick", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES)
                        && perms.contains(Permission.PERMISSION_ID_C_ARTICLE_STICK_ARTICLE))
                .put("articleCancelStick", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES)
                        && perms.contains(Permission.PERMISSION_ID_C_ARTICLE_CANCEL_STICK_ARTICLE))
                .put("articleRemove", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES)
                        && perms.contains(Permission.PERMISSION_ID_C_ARTICLE_REMOVE_ARTICLE))
                .put("commentEdit", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_COMMENTS)
                        && perms.contains(Permission.PERMISSION_ID_C_COMMENT_UPDATE_COMMENT_BASIC))
                .put("commentRemove", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_COMMENTS)
                        && perms.contains(Permission.PERMISSION_ID_C_COMMENT_REMOVE_COMMENT))
                .put("breezemoonEdit", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_BREEZEMOONS)
                        && perms.contains(Permission.PERMISSION_ID_C_BREEZEMOON_UPDATE_BREEZEMOON))
                .put("breezemoonRemove", canAccessAdmin
                        && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_BREEZEMOONS)
                        && perms.contains(Permission.PERMISSION_ID_C_BREEZEMOON_REMOVE_BREEZEMOON))
                .put("auditLogView", canAccessAdmin)
                .put("fishGameReview", canAccessAdmin && isAdmin)
                .put("milestoneReview", canAccessAdmin
                        && (isAdmin || perms.contains(Permission.PERMISSION_ID_C_MENU_MANAGE_MILESTONE)))
                .put("imageReview", canAccessAdmin && isAdmin && !Symphonys.RHPIC_ENABLED)
                .put("roleManage", canAccessAdmin && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_ROLES))
                .put("invitecodeManage", canAccessAdmin && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_ICS))
                .put("vipManage", canAccessAdmin && isAdmin)
                .put("medalDefine", canAccessAdmin && isAdmin)
                .put("medalGrant", canAccessAdmin
                        && (isAdmin || perms.contains(Permission.PERMISSION_ID_C_USER_GIVE_METAL)))
                .put("medalRevoke", canAccessAdmin
                        && (isAdmin || perms.contains(Permission.PERMISSION_ID_C_USER_REMOVE_METAL)))
                .put("medalManage", canAccessAdmin && (isAdmin || hasAny(perms,
                        Permission.PERMISSION_ID_C_USER_GIVE_METAL,
                        Permission.PERMISSION_ID_C_USER_REMOVE_METAL)))
                .put("pointAdd", canAccessAdmin
                        && (isAdmin || perms.contains(Permission.PERMISSION_ID_C_USER_ADD_POINT)))
                .put("pointDeduct", canAccessAdmin
                        && (isAdmin || perms.contains(Permission.PERMISSION_ID_C_USER_DEDUCT_POINT)))
                .put("pointExchange", canAccessAdmin
                        && (isAdmin || perms.contains(Permission.PERMISSION_ID_C_USER_EXCHANGE_POINT)))
                .put("bagAdjust", canAccessAdmin
                        && (isAdmin || perms.contains(Permission.PERMISSION_ID_C_USER_ADJUST_BAG)))
                .put("salaryPay", canAccessAdmin && isAdmin)
                .put("domainManage", canAccessAdmin && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_DOMAINS))
                .put("tagManage", canAccessAdmin && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_TAGS))
                .put("reservedWordManage", canAccessAdmin && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_RWS))
                .put("adManage", canAccessAdmin && perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_AD))
                .put("fishGameManage", canAccessAdmin
                        && (isAdmin || perms.contains(Permission.PERMISSION_ID_C_MENU_ADMIN_MISC)))
                .put("milestoneManage", canAccessAdmin
                        && (isAdmin || perms.contains(Permission.PERMISSION_ID_C_MENU_MANAGE_MILESTONE)));

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

    private JSONObject loadSanitizedUserDetailInTransaction(final String userId,
                                                            final String actorId,
                                                            final boolean includeRecentOperations)
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

        final JSONArray recentOps = new JSONArray();
        if (includeRecentOperations) {
            final List<JSONObject> opRows = operations.select(
                    "SELECT oId,operationUserId,operationCode,operationDataId,operationCreated,operationIP,operationUA "
                            + "FROM `" + operations.getName()
                            + "` WHERE operationDataId=? ORDER BY operationCreated DESC, oId DESC LIMIT 15",
                    userId);
            for (final JSONObject opRow : opRows) {
                recentOps.put(formatOperationRow(opRow));
            }
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
                .put("operationUserNickname", operator == null ? "" : operator.optString(UserExt.USER_NICKNAME))
                .put("operationCode", code)
                .put("operationCodeLabel", operationCodeLabel(code))
                .put("targetType", classifyOperationTargetType(code))
                .put("operationDataId", row.optString(Operation.OPERATION_DATA_ID))
                .put("operationCreated", row.optLong(Operation.OPERATION_CREATED))
                .put("operationIP", row.optString(Operation.OPERATION_IP))
                .put("operationUA", row.optString(Operation.OPERATION_UA));
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

    Transaction begin() {
        if (requests.hasTransactionBegun()) {
            throw new IllegalStateException("admin v3 root transaction required");
        }
        return ApiV3Transactions.strict(requests.beginTransaction());
    }

    static void rollbackIfActive(final Transaction tx) {
        if (tx != null && tx.isActive()) {
            tx.rollback();
        }
    }

    void recordOperation(final Request request, final int code, final String targetId, final JSONObject actorUser)
            throws RepositoryException {
        operations.add(Operation.newOperation(request, code, targetId, actorUser));
    }

    static String actorId(final JSONObject identity) {
        final JSONObject user = identity == null ? null : identity.optJSONObject("user");
        final String actor = user == null ? "" : user.optString(Keys.OBJECT_ID);
        if (!actor.matches("[1-9][0-9]{0,18}")) {
            throw ApiV3Requests.forbidden();
        }
        return actor;
    }

    static boolean hasAny(final Set<String> granted, final String... candidates) {
        for (final String candidate : candidates) {
            if (granted.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    static int requireExactInt(final JSONObject body, final String key, final int min, final int max) {
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
        final OperationCodeMeta meta = OPERATION_CODE_META_MAP.get(code);
        return meta != null ? meta.label() : "管理操作 #" + code;
    }

    private static String classifyOperationTargetType(final int code) {
        final OperationCodeMeta meta = OPERATION_CODE_META_MAP.get(code);
        return meta != null ? meta.targetType() : "other";
    }

    private static JSONArray buildOperationCodeOptions() {
        final JSONArray options = new JSONArray();
        for (final OperationCodeMeta meta : OPERATION_CODE_META_LIST) {
            options.put(new JSONObject()
                    .put("code", meta.code())
                    .put("label", meta.label())
                    .put("targetType", meta.targetType()));
        }
        return options;
    }

    // ==================== 第二阶段：文章、评论、清风明月管理与全站操作审计 ====================

    /**
     * 多条件分页查询管理后台文章列表，并附带作者信息及长文专栏绑定摘要。
     */
    public JSONObject getArticles(final JSONObject identity, final int p, final int size,
                                  final String keyword, final String status, final String type) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false);
            requireMenuAdmin(actor);

            final List<String> conditions = new ArrayList<>();
            final List<Object> args = new ArrayList<>();
            if (!"all".equals(status)) {
                conditions.add("a." + Article.ARTICLE_STATUS + "=?");
                args.add(Integer.parseInt(status));
            }
            if (!"all".equals(type)) {
                conditions.add("a." + Article.ARTICLE_TYPE + "=?");
                args.add(Integer.parseInt(type));
            }
            final String trimmedKeyword = StringUtils.trimToEmpty(keyword);
            if (!trimmedKeyword.isEmpty()) {
                final String pattern = "%" + escapeLike(trimmedKeyword) + "%";
                conditions.add("(a.oId=? OR a." + Article.ARTICLE_TITLE + " LIKE ? ESCAPE '!' "
                        + "OR a." + Article.ARTICLE_TAGS + " LIKE ? ESCAPE '!' "
                        + "OR u." + User.USER_NAME + " LIKE ? ESCAPE '!' "
                        + "OR u." + UserExt.USER_NICKNAME + " LIKE ? ESCAPE '!')");
                args.add(trimmedKeyword);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
            }

            final String fromClause = " FROM `" + articles.getName() + "` a LEFT JOIN `" + users.getName()
                    + "` u ON u.oId=a." + Article.ARTICLE_AUTHOR_ID;
            final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
            final long total = countQuery("SELECT COUNT(a.oId) AS cnt" + fromClause + where, args.toArray());

            final long offset = (long) (p - 1) * size;
            final List<Object> listArgs = new ArrayList<>(args);
            listArgs.add(size);
            listArgs.add(offset);
            final List<JSONObject> rows = articles.select(
                    "SELECT a.*" + fromClause + where
                            + " ORDER BY a." + Article.ARTICLE_STICK + " DESC, a.oId DESC LIMIT ? OFFSET ?",
                    listArgs.toArray());

            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) {
                list.put(buildArticleSummaryDto(row));
            }
            final JSONObject result = ApiV3Requests.pagination(list, p, size, total);
            tx.commit();
            return result;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询后台文章列表失败", e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    /**
     * 查询单篇文章管理详情（含正文、打赏区内容、作者、长文专栏选项与最近操作审计）。
     */
    public JSONObject getArticleDetail(final JSONObject identity, final String articleId) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false);
            requireMenuAdmin(actor);
            final JSONObject detail = loadArticleDetailInTransaction(articleId);
            tx.commit();
            return detail;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询后台文章详情失败, articleId={}", articleId, e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    /**
     * 管理员在后台新建文章并支持同步绑定长文专栏章节，操作通过发布锁、幂等表与审计表保障。
     */
    public JSONObject addArticle(final JSONObject identity, final JSONObject body,
                                 final String idempotencyKey, final Request request) {
        if (body == null || body.isEmpty() || !ARTICLE_CREATE_ALLOWED_FIELDS.containsAll(body.keySet())) {
            throw ApiV3WriteInputs.invalid();
        }
        final JSONObject normalized = normalizeAdminArticleCreateInput(body);
        final String targetUserName = normalized.optString(User.USER_NAME);
        final String targetAuthorId = preReadUserIdByUserName(
                identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES,
                Permission.PERMISSION_ID_C_ARTICLE_ADD_ARTICLE, targetUserName);

        final String actorId = actorId(identity);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();
        final AtomicReference<JSONObject> createdArticleRef = new AtomicReference<>();
        final String articleIP = Requests.getRemoteAddr(request);
        final String articleUA = Headers.getHeader(request, Common.USER_AGENT, "");

        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            execution = articleMgmtService.withPublicationLock(targetAuthorId, () -> {
                final ApiV3MutationStore store = guardedWrite(
                        identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false, actorRef, true);
                return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                        store,
                        actorId,
                        "admin.articles.add",
                        idempotencyKey,
                        normalized,
                        () -> ApiV3TransactionSavepoint.run(() -> {
                            final AuthorizedActor actor = actorRef.get();
                            if (!actor.permissions().contains(Permission.PERMISSION_ID_C_ARTICLE_ADD_ARTICLE)) {
                                throw ApiV3Requests.forbidden();
                            }
                            final JSONObject adminReq = new JSONObject(normalized.toString())
                                    .put(Article.ARTICLE_IP, articleIP)
                                    .put(Article.ARTICLE_UA, articleUA);
                            final JSONObject created;
                            try {
                                created = articleMgmtService.addArticleByAdminInCurrentTransaction(adminReq);
                            } catch (final ServiceException e) {
                                throw mapAdminServiceException(e, 422, "article_create_rejected");
                            }
                            createdArticleRef.set(created);
                            final String articleId = created.optString(Keys.OBJECT_ID);
                            operations.add(Operation.newOperation(
                                    request, Operation.OPERATION_CODE_C_ADD_ARTICLE, articleId, actor.user()));
                            return new JSONObject()
                                    .put("oId", articleId)
                                    .put("articleId", articleId)
                                    .put("articlePermalink", created.optString(Article.ARTICLE_PERMALINK))
                                    .put("createdAt", created.optLong(Article.ARTICLE_CREATE_TIME));
                        }));
            }, exec -> {
                if (exec.applied() && createdArticleRef.get() != null) {
                    articleMgmtService.afterAdminArticleAdded(createdArticleRef.get());
                }
            });
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("后台新建文章失败, targetUserName={}", targetUserName, e);
            throw unavailable();
        }

        final String createdId = execution.result().optString("articleId");
        final JSONObject freshDetail = getArticleDetail(identity, createdId);
        return new JSONObject(execution.result().toString())
                .put("replayed", !execution.applied())
                .put("detail", freshDetail);
    }

    /**
     * 管理员编辑文章字段，并可选同步长文专栏章节绑定与专栏封面。
     */
    public JSONObject updateArticle(final JSONObject identity, final String articleId,
                                    final JSONObject body, final String idempotencyKey, final Request request) {
        if (body == null || body.isEmpty() || !ARTICLE_UPDATE_ALLOWED_FIELDS.containsAll(body.keySet())) {
            throw ApiV3WriteInputs.invalid();
        }
        final ArticleUpdateInput updateInput = normalizeAdminArticleUpdateInput(body);
        final String targetAuthorId = preReadArticleAuthorId(
                identity, Permission.PERMISSION_ID_C_ARTICLE_UPDATE_ARTICLE_BASIC, articleId);

        final String actorId = actorId(identity);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();
        final AtomicReference<JSONObject> outcomeRef = new AtomicReference<>();

        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            execution = articleMgmtService.withPublicationLock(targetAuthorId, () -> {
                final ApiV3MutationStore store = guardedWrite(
                        identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false, actorRef, true);
                return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                        store,
                        actorId,
                        "admin.articles.update:" + articleId,
                        idempotencyKey,
                        updateInput.canonicalRequest(),
                        () -> ApiV3TransactionSavepoint.run(() -> {
                            final AuthorizedActor actor = actorRef.get();
                            if (!actor.permissions().contains(Permission.PERMISSION_ID_C_ARTICLE_UPDATE_ARTICLE_BASIC)) {
                                throw ApiV3Requests.forbidden();
                            }
                            final JSONObject outcome;
                            try {
                                outcome = articleMgmtService.updateArticleByAdminInCurrentTransaction(
                                        articleId, updateInput.patch(), updateInput.longArticleColumnRequest());
                            } catch (final ServiceException e) {
                                if ("文章不存在".equals(e.getMessage())) {
                                    throw ApiV3Requests.notFound();
                                }
                                throw mapAdminServiceException(e, 422, "article_update_rejected");
                            }
                            outcomeRef.set(outcome);
                            operations.add(Operation.newOperation(
                                    request, Operation.OPERATION_CODE_C_UPDATE_ARTICLE, articleId, actor.user()));
                            return new JSONObject()
                                    .put("oId", articleId)
                                    .put("articleId", articleId)
                                    .put("updatedAt", System.currentTimeMillis());
                        }));
            }, exec -> {
                if (exec.applied() && outcomeRef.get() != null) {
                    articleMgmtService.afterAdminArticleUpdated(outcomeRef.get());
                }
            });
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("后台编辑文章失败, articleId={}", articleId, e);
            throw unavailable();
        }

        final JSONObject freshDetail = getArticleDetail(identity, articleId);
        return new JSONObject(execution.result().toString())
                .put("replayed", !execution.applied())
                .put("detail", freshDetail);
    }

    /**
     * 置顶指定文章（沿用旧后台永久置顶规则）。
     */
    public JSONObject stickArticle(final JSONObject identity, final String articleId,
                                   final String idempotencyKey, final Request request) {
        return mutateArticleStick(
                identity, articleId, idempotencyKey, request, true,
                "admin.articles.stick:" + articleId,
                Permission.PERMISSION_ID_C_ARTICLE_STICK_ARTICLE,
                Operation.OPERATION_CODE_C_STICK_ARTICLE);
    }

    /**
     * 取消指定文章置顶（将置顶时间戳清零）。
     */
    public JSONObject cancelStickArticle(final JSONObject identity, final String articleId,
                                         final String idempotencyKey, final Request request) {
        return mutateArticleStick(
                identity, articleId, idempotencyKey, request, false,
                "admin.articles.cancelStick:" + articleId,
                Permission.PERMISSION_ID_C_ARTICLE_CANCEL_STICK_ARTICLE,
                Operation.OPERATION_CODE_C_CANCEL_STICK_ARTICLE);
    }

    /**
     * 管理员强制移除指定文章（支持移除发布未满 24 小时的内容，清理关联标签、章节与索引）。
     */
    public JSONObject removeArticle(final JSONObject identity, final String articleId,
                                    final String idempotencyKey, final Request request) {
        final String targetAuthorId = preReadArticleAuthorId(
                identity, Permission.PERMISSION_ID_C_ARTICLE_REMOVE_ARTICLE, articleId);
        final String actorId = actorId(identity);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();
        final AtomicReference<ArticleMgmtService.Removal> removalRef = new AtomicReference<>();

        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            execution = articleMgmtService.withPublicationLock(targetAuthorId, () -> {
                final ApiV3MutationStore store = guardedWrite(
                        identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false, actorRef, true);
                return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                        store,
                        actorId,
                        "admin.articles.remove:" + articleId,
                        idempotencyKey,
                        new JSONObject().put("articleId", articleId),
                        () -> ApiV3TransactionSavepoint.run(() -> {
                            final AuthorizedActor actor = actorRef.get();
                            if (!actor.permissions().contains(Permission.PERMISSION_ID_C_ARTICLE_REMOVE_ARTICLE)) {
                                throw ApiV3Requests.forbidden();
                            }
                            final ArticleMgmtService.Removal removal;
                            try {
                                removal = articleMgmtService.removeArticleInCurrentTransaction(articleId, true);
                            } catch (final ServiceException e) {
                                throw mapAdminServiceException(e, 422, "article_remove_rejected");
                            }
                            if (removal == null) {
                                throw ApiV3Requests.notFound();
                            }
                            removalRef.set(removal);
                            operations.add(Operation.newOperation(
                                    request, Operation.OPERATION_CODE_C_REMOVE_ARTICLE, articleId, actor.user()));
                            return new JSONObject()
                                    .put("oId", articleId)
                                    .put("articleId", articleId)
                                    .put("deleted", true)
                                    .put("removedAt", System.currentTimeMillis());
                        }));
            }, exec -> {
                if (exec.applied() && removalRef.get() != null) {
                    articleMgmtService.afterArticleRemoved(removalRef.get());
                }
            });
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("后台移除文章失败, articleId={}", articleId, e);
            throw unavailable();
        }

        return new JSONObject(execution.result().toString()).put("replayed", !execution.applied());
    }

    /**
     * 多条件分页查询管理后台评论列表，并附带所属文章标题/永久链接及评论者资料。
     */
    public JSONObject getComments(final JSONObject identity, final int p, final int size,
                                  final String keyword, final String status, final String articleId) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_COMMENTS, false);
            requireMenuAdmin(actor);

            final List<String> conditions = new ArrayList<>();
            final List<Object> args = new ArrayList<>();
            if (!"all".equals(status)) {
                conditions.add("c." + Comment.COMMENT_STATUS + "=?");
                args.add(Integer.parseInt(status));
            }
            final String trimmedArticleId = StringUtils.trimToEmpty(articleId);
            if (!trimmedArticleId.isEmpty()) {
                conditions.add("c." + Comment.COMMENT_ON_ARTICLE_ID + "=?");
                args.add(trimmedArticleId);
            }
            final String trimmedKeyword = StringUtils.trimToEmpty(keyword);
            if (!trimmedKeyword.isEmpty()) {
                final String pattern = "%" + escapeLike(trimmedKeyword) + "%";
                conditions.add("(c.oId=? OR c." + Comment.COMMENT_ON_ARTICLE_ID + "=? "
                        + "OR c." + Comment.COMMENT_CONTENT + " LIKE ? ESCAPE '!' "
                        + "OR u." + User.USER_NAME + " LIKE ? ESCAPE '!' "
                        + "OR u." + UserExt.USER_NICKNAME + " LIKE ? ESCAPE '!' "
                        + "OR a." + Article.ARTICLE_TITLE + " LIKE ? ESCAPE '!')");
                args.add(trimmedKeyword);
                args.add(trimmedKeyword);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
            }

            final String fromClause = " FROM `" + comments.getName() + "` c"
                    + " LEFT JOIN `" + users.getName() + "` u ON u.oId=c." + Comment.COMMENT_AUTHOR_ID
                    + " LEFT JOIN `" + articles.getName() + "` a ON a.oId=c." + Comment.COMMENT_ON_ARTICLE_ID;
            final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
            final long total = countQuery("SELECT COUNT(c.oId) AS cnt" + fromClause + where, args.toArray());

            final long offset = (long) (p - 1) * size;
            final List<Object> listArgs = new ArrayList<>(args);
            listArgs.add(size);
            listArgs.add(offset);
            final List<JSONObject> rows = comments.select(
                    "SELECT c.*" + fromClause + where + " ORDER BY c.oId DESC LIMIT ? OFFSET ?",
                    listArgs.toArray());

            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) {
                list.put(buildCommentDto(row, false));
            }
            final JSONObject result = ApiV3Requests.pagination(list, p, size, total);
            tx.commit();
            return result;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询后台评论列表失败", e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    /**
     * 查询单条评论详情与最近操作审计记录。
     */
    public JSONObject getCommentDetail(final JSONObject identity, final String commentId) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_COMMENTS, false);
            requireMenuAdmin(actor);
            final JSONObject detail = loadCommentDetailInTransaction(commentId);
            tx.commit();
            return detail;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询后台评论详情失败, commentId={}", commentId, e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    /**
     * 管理员编辑评论内容、状态、可见性及赞踩计数（在发布锁与缓存作用域内更新）。
     */
    public JSONObject updateComment(final JSONObject identity, final String commentId,
                                    final JSONObject body, final String idempotencyKey, final Request request) {
        if (body == null || body.isEmpty() || !COMMENT_UPDATE_ALLOWED_FIELDS.containsAll(body.keySet())) {
            throw ApiV3WriteInputs.invalid();
        }
        final JSONObject normalized = normalizeAdminCommentUpdateInput(body);
        final CommentLockTarget target = preReadCommentTarget(
                identity, Permission.PERMISSION_ID_C_COMMENT_UPDATE_COMMENT_BASIC, commentId);

        final String actorId = actorId(identity);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();
        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            execution = articleMgmtService.withPublicationLock(target.commentAuthorId(), () -> {
                final ApiV3MutationStore store = guardedWrite(
                        identity, Permission.PERMISSION_ID_C_MENU_ADMIN_COMMENTS, false, actorRef, true);
                return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                        store,
                        actorId,
                        "admin.comments.update:" + commentId,
                        idempotencyKey,
                        new JSONObject().put("commentId", commentId).put("patch", normalized),
                        () -> ApiV3TransactionSavepoint.run(() -> {
                            final AuthorizedActor actor = actorRef.get();
                            if (!actor.permissions().contains(Permission.PERMISSION_ID_C_COMMENT_UPDATE_COMMENT_BASIC)) {
                                throw ApiV3Requests.forbidden();
                            }
                            if (StringUtils.isNotBlank(target.commentAuthorId())) {
                                users.getForUpdate(target.commentAuthorId());
                            }
                            if (StringUtils.isNotBlank(target.articleId())) {
                                articles.getForUpdate(target.articleId());
                            }
                            if (!commentMgmtService.updateCommentByAdminInCurrentTransaction(commentId, normalized)) {
                                throw ApiV3Requests.notFound();
                            }
                            operations.add(Operation.newOperation(
                                    request, Operation.OPERATION_CODE_C_UPDATE_COMMENT, commentId, actor.user()));
                            return new JSONObject()
                                    .put("oId", commentId)
                                    .put("commentId", commentId)
                                    .put("updatedAt", System.currentTimeMillis());
                        }));
            });
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("后台更新评论失败, commentId={}", commentId, e);
            throw unavailable();
        }

        final JSONObject freshDetail = getCommentDetail(identity, commentId);
        return new JSONObject(execution.result().toString())
                .put("replayed", !execution.applied())
                .put("detail", freshDetail);
    }

    /**
     * 管理员移除指定评论，清理评论计数值、通知与索引事件源。
     */
    public JSONObject removeComment(final JSONObject identity, final String commentId,
                                    final String idempotencyKey, final Request request) {
        final CommentLockTarget target = preReadCommentTarget(
                identity, Permission.PERMISSION_ID_C_COMMENT_REMOVE_COMMENT, commentId);
        final List<String> destinations = new ArrayList<>();
        if (StringUtils.isNotBlank(target.articleAuthorId())
                && !target.articleAuthorId().equals(target.commentAuthorId())) {
            destinations.add(target.articleAuthorId());
        }
        destinations.add(Pointtransfer.ID_C_SYS);

        final String actorId = actorId(identity);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();
        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            execution = articleMgmtService.withPublicationLocks(
                    target.commentAuthorId(),
                    destinations,
                    () -> {
                        final ApiV3MutationStore store = guardedWrite(
                                identity, Permission.PERMISSION_ID_C_MENU_ADMIN_COMMENTS, false, actorRef, true);
                        return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                                store,
                                actorId,
                                "admin.comments.remove:" + commentId,
                                idempotencyKey,
                                new JSONObject().put("commentId", commentId),
                                () -> ApiV3TransactionSavepoint.run(() -> {
                                    final AuthorizedActor actor = actorRef.get();
                                    if (!actor.permissions().contains(Permission.PERMISSION_ID_C_COMMENT_REMOVE_COMMENT)) {
                                        throw ApiV3Requests.forbidden();
                                    }
                                    final TreeSet<String> sortedUsers = new TreeSet<>();
                                    if (StringUtils.isNotBlank(target.commentAuthorId())) {
                                        sortedUsers.add(target.commentAuthorId());
                                    }
                                    if (StringUtils.isNotBlank(target.articleAuthorId())) {
                                        sortedUsers.add(target.articleAuthorId());
                                    }
                                    for (final String uid : sortedUsers) {
                                        users.getForUpdate(uid);
                                    }
                                    if (StringUtils.isNotBlank(target.articleId())) {
                                        articles.getForUpdate(target.articleId());
                                    }
                                    if (!commentMgmtService.removeCommentByAdminInCurrentTransaction(commentId)) {
                                        throw ApiV3Requests.notFound();
                                    }
                                    operations.add(Operation.newOperation(
                                            request, Operation.OPERATION_CODE_C_REMOVE_COMMENT, commentId, actor.user()));
                                    return new JSONObject()
                                            .put("oId", commentId)
                                            .put("commentId", commentId)
                                            .put("deleted", true)
                                            .put("removedAt", System.currentTimeMillis());
                                }));
                    },
                    ignored -> {
                    });
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("后台移除评论失败, commentId={}", commentId, e);
            throw unavailable();
        }

        return new JSONObject(execution.result().toString()).put("replayed", !execution.applied());
    }

    /**
     * 多条件分页查询管理后台清风明月列表。
     */
    public JSONObject getBreezemoons(final JSONObject identity, final int p, final int size,
                                     final String keyword, final String status, final String authorId) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_BREEZEMOONS, false);
            requireMenuAdmin(actor);

            final List<String> conditions = new ArrayList<>();
            final List<Object> args = new ArrayList<>();
            if (!"all".equals(status)) {
                conditions.add("b." + Breezemoon.BREEZEMOON_STATUS + "=?");
                args.add(Integer.parseInt(status));
            }
            final String trimmedAuthorId = StringUtils.trimToEmpty(authorId);
            if (!trimmedAuthorId.isEmpty()) {
                conditions.add("b." + Breezemoon.BREEZEMOON_AUTHOR_ID + "=?");
                args.add(trimmedAuthorId);
            }
            final String trimmedKeyword = StringUtils.trimToEmpty(keyword);
            if (!trimmedKeyword.isEmpty()) {
                final String pattern = "%" + escapeLike(trimmedKeyword) + "%";
                conditions.add("(b.oId=? OR b." + Breezemoon.BREEZEMOON_CONTENT + " LIKE ? ESCAPE '!' "
                        + "OR u." + User.USER_NAME + " LIKE ? ESCAPE '!' "
                        + "OR u." + UserExt.USER_NICKNAME + " LIKE ? ESCAPE '!')");
                args.add(trimmedKeyword);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
            }

            final String fromClause = " FROM `" + breezemoons.getName() + "` b LEFT JOIN `" + users.getName()
                    + "` u ON u.oId=b." + Breezemoon.BREEZEMOON_AUTHOR_ID;
            final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
            final long total = countQuery("SELECT COUNT(b.oId) AS cnt" + fromClause + where, args.toArray());

            final long offset = (long) (p - 1) * size;
            final List<Object> listArgs = new ArrayList<>(args);
            listArgs.add(size);
            listArgs.add(offset);
            final List<JSONObject> rows = breezemoons.select(
                    "SELECT b.*" + fromClause + where + " ORDER BY b.oId DESC LIMIT ? OFFSET ?",
                    listArgs.toArray());

            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) {
                list.put(buildBreezemoonDto(row, false));
            }
            final JSONObject result = ApiV3Requests.pagination(list, p, size, total);
            tx.commit();
            return result;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询后台清风明月列表失败", e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    /**
     * 查询单条清风明月详情与最近操作审计记录。
     */
    public JSONObject getBreezemoonDetail(final JSONObject identity, final String breezemoonId) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(
                    identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN_BREEZEMOONS, false);
            requireMenuAdmin(actor);
            final JSONObject detail = loadBreezemoonDetailInTransaction(breezemoonId);
            tx.commit();
            return detail;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询清风明月详情失败, breezemoonId={}", breezemoonId, e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    /**
     * 管理员更新清风明月正文或有效状态，并写入管理审计记录。
     */
    public JSONObject updateBreezemoon(final JSONObject identity, final String breezemoonId,
                                       final JSONObject body, final String idempotencyKey, final Request request) {
        if (body == null || body.isEmpty() || !BREEZEMOON_UPDATE_ALLOWED_FIELDS.containsAll(body.keySet())) {
            throw ApiV3WriteInputs.invalid();
        }
        final JSONObject normalized = normalizeAdminBreezemoonUpdateInput(body);
        final String actorId = actorId(identity);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();

        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            final ApiV3MutationStore store = guardedWrite(
                    identity, Permission.PERMISSION_ID_C_MENU_ADMIN_BREEZEMOONS, false, actorRef);
            execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    store,
                    actorId,
                    "admin.breezemoons.update:" + breezemoonId,
                    idempotencyKey,
                    new JSONObject().put("breezemoonId", breezemoonId).put("patch", normalized),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final AuthorizedActor actor = actorRef.get();
                        if (!actor.permissions().contains(Permission.PERMISSION_ID_C_BREEZEMOON_UPDATE_BREEZEMOON)) {
                            throw ApiV3Requests.forbidden();
                        }
                        final JSONObject updated = breezemoonMgmtService.updateBreezemoonByAdminInCurrentTransaction(
                                breezemoonId, normalized);
                        if (updated == null) {
                            throw ApiV3Requests.notFound();
                        }
                        final long now = updated.optLong(Breezemoon.BREEZEMOON_UPDATED, System.currentTimeMillis());
                        operations.add(Operation.newOperation(
                                request, Operation.OPERATION_CODE_C_UPDATE_BREEZEMOON, breezemoonId, actor.user()));
                        return new JSONObject()
                                .put("oId", breezemoonId)
                                .put("breezemoonId", breezemoonId)
                                .put("updatedAt", now);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("后台更新清风明月失败, breezemoonId={}", breezemoonId, e);
            throw unavailable();
        }

        final JSONObject freshDetail = getBreezemoonDetail(identity, breezemoonId);
        return new JSONObject(execution.result().toString())
                .put("replayed", !execution.applied())
                .put("detail", freshDetail);
    }

    /**
     * 管理员永久移除指定清风明月内容。
     */
    public JSONObject removeBreezemoon(final JSONObject identity, final String breezemoonId,
                                       final String idempotencyKey, final Request request) {
        final String actorId = actorId(identity);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();

        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            final ApiV3MutationStore store = guardedWrite(
                    identity, Permission.PERMISSION_ID_C_MENU_ADMIN_BREEZEMOONS, false, actorRef);
            execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                    store,
                    actorId,
                    "admin.breezemoons.remove:" + breezemoonId,
                    idempotencyKey,
                    new JSONObject().put("breezemoonId", breezemoonId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final AuthorizedActor actor = actorRef.get();
                        if (!actor.permissions().contains(Permission.PERMISSION_ID_C_BREEZEMOON_REMOVE_BREEZEMOON)) {
                            throw ApiV3Requests.forbidden();
                        }
                        if (!breezemoonMgmtService.removeBreezemoonInCurrentTransaction(breezemoonId)) {
                            throw ApiV3Requests.notFound();
                        }
                        operations.add(Operation.newOperation(
                                request, Operation.OPERATION_CODE_C_REMOVE_BREEZEMOON, breezemoonId, actor.user()));
                        return new JSONObject()
                                .put("oId", breezemoonId)
                                .put("breezemoonId", breezemoonId)
                                .put("deleted", true)
                                .put("removedAt", System.currentTimeMillis());
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("后台移除清风明月失败, breezemoonId={}", breezemoonId, e);
            throw unavailable();
        }

        return new JSONObject(execution.result().toString()).put("replayed", !execution.applied());
    }

    /**
     * 多条件分页检索全站管理操作审计日志（仅展示 operation 表真实字段，不伪造字段级前后快照）。
     */
    public JSONObject getAuditLogs(final JSONObject identity, final int p, final int size,
                                   final String operator, final String code, final String dataId) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(identity, "profile.read", null, false);
            requireMenuAdmin(actor);

            final List<String> conditions = new ArrayList<>();
            final List<Object> args = new ArrayList<>();
            final String trimmedOperator = StringUtils.trimToEmpty(operator);
            if (!trimmedOperator.isEmpty()) {
                final String pattern = "%" + escapeLike(trimmedOperator) + "%";
                conditions.add("(op." + Operation.OPERATION_USER_ID + "=? "
                        + "OR u." + User.USER_NAME + " LIKE ? ESCAPE '!' "
                        + "OR u." + UserExt.USER_NICKNAME + " LIKE ? ESCAPE '!')");
                args.add(trimmedOperator);
                args.add(pattern);
                args.add(pattern);
            }
            if (!"all".equals(code)) {
                conditions.add("op." + Operation.OPERATION_CODE + "=?");
                args.add(Integer.parseInt(code));
            }
            final String trimmedDataId = StringUtils.trimToEmpty(dataId);
            if (!trimmedDataId.isEmpty()) {
                final String pattern = "%" + escapeLike(trimmedDataId) + "%";
                conditions.add("(op." + Operation.OPERATION_DATA_ID + "=? OR op." + Operation.OPERATION_DATA_ID + " LIKE ? ESCAPE '!')");
                args.add(trimmedDataId);
                args.add(pattern);
            }

            final String fromClause = " FROM `" + operations.getName() + "` op LEFT JOIN `" + users.getName()
                    + "` u ON u.oId=op." + Operation.OPERATION_USER_ID;
            final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
            final long total = countQuery("SELECT COUNT(op.oId) AS cnt" + fromClause + where, args.toArray());

            final long offset = (long) (p - 1) * size;
            final List<Object> listArgs = new ArrayList<>(args);
            listArgs.add(size);
            listArgs.add(offset);
            final List<JSONObject> rows = operations.select(
                    "SELECT op.oId,op." + Operation.OPERATION_USER_ID + ",op." + Operation.OPERATION_CODE
                            + ",op." + Operation.OPERATION_DATA_ID + ",op." + Operation.OPERATION_CREATED
                            + ",op." + Operation.OPERATION_IP + ",op." + Operation.OPERATION_UA
                            + fromClause + where
                            + " ORDER BY op." + Operation.OPERATION_CREATED + " DESC, op.oId DESC LIMIT ? OFFSET ?",
                    listArgs.toArray());

            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) {
                list.put(formatOperationRow(row));
            }
            final JSONObject result = ApiV3Requests.pagination(list, p, size, total);
            result.put("codeOptions", buildOperationCodeOptions());
            result.put("auditNote", "当前审计日志来自系统真实 operation 操作表，仅呈现操作者、动作编码、对象 ID、时间、IP 与 UA，不伪造未采集的变更前后字段快照。");
            tx.commit();
            return result;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("查询全站操作审计日志失败", e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    private record CommentLockTarget(String commentAuthorId, String articleId, String articleAuthorId) {
    }

    private record ArticleUpdateInput(JSONObject patch, JSONObject longArticleColumnRequest, JSONObject canonicalRequest) {
    }

    private JSONObject mutateArticleStick(final JSONObject identity, final String articleId,
                                          final String idempotencyKey, final Request request,
                                          final boolean stick, final String operationName,
                                          final String actionPermission, final int operationCode) {
        final String targetAuthorId = preReadArticleAuthorId(identity, actionPermission, articleId);
        final String actorId = actorId(identity);
        final AtomicReference<AuthorizedActor> actorRef = new AtomicReference<>();

        final ApiV3IdempotencyMgmtService.Execution execution;
        try {
            execution = articleMgmtService.withPublicationLock(targetAuthorId, () -> {
                final ApiV3MutationStore store = guardedWrite(
                        identity, Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false, actorRef, true);
                return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(
                        store,
                        actorId,
                        operationName,
                        idempotencyKey,
                        new JSONObject().put("articleId", articleId).put("stick", stick),
                        () -> ApiV3TransactionSavepoint.run(() -> {
                            final AuthorizedActor actor = actorRef.get();
                            if (!actor.permissions().contains(actionPermission)) {
                                throw ApiV3Requests.forbidden();
                            }
                            final Long stickTime = articleMgmtService.setArticleStickInCurrentTransaction(
                                    articleId, targetAuthorId, stick);
                            if (stickTime == null) {
                                throw ApiV3Requests.notFound();
                            }
                            operations.add(Operation.newOperation(request, operationCode, articleId, actor.user()));
                            return new JSONObject()
                                    .put("oId", articleId)
                                    .put("articleId", articleId)
                                    .put("articleStick", stickTime)
                                    .put("updatedAt", System.currentTimeMillis());
                        }));
            });
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("调整文章置顶状态失败, articleId={}, stick={}", articleId, stick, e);
            throw unavailable();
        }

        final JSONObject freshDetail = getArticleDetail(identity, articleId);
        return new JSONObject(execution.result().toString())
                .put("replayed", !execution.applied())
                .put("detail", freshDetail);
    }

    private String preReadUserIdByUserName(final JSONObject identity, final String menuPermission,
                                           final String actionPermission, final String userName) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(identity, "settings.write", menuPermission, false);
            requireMenuAdmin(actor);
            if (!actor.permissions().contains(actionPermission)) {
                throw ApiV3Requests.forbidden();
            }
            final List<JSONObject> rows = users.select(
                    "SELECT oId FROM `" + users.getName() + "` WHERE " + User.USER_NAME + "=? LIMIT 1",
                    userName);
            if (rows.isEmpty()) {
                throw new ApiV3Exception(404, "not_found", "指定作者账号不存在");
            }
            final String userId = rows.getFirst().optString(Keys.OBJECT_ID);
            tx.commit();
            return userId;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("预读取作者账号失败, userName={}", userName, e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    private String preReadArticleAuthorId(final JSONObject identity, final String actionPermission, final String articleId) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(
                    identity, "settings.write", Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES, false);
            requireMenuAdmin(actor);
            if (!actor.permissions().contains(actionPermission)) {
                throw ApiV3Requests.forbidden();
            }
            final List<JSONObject> rows = articles.select(
                    "SELECT " + Article.ARTICLE_AUTHOR_ID + " FROM `" + articles.getName() + "` WHERE oId=? LIMIT 1",
                    articleId);
            if (rows.isEmpty()) {
                throw ApiV3Requests.notFound();
            }
            final String authorId = rows.getFirst().optString(Article.ARTICLE_AUTHOR_ID);
            tx.commit();
            return authorId;
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("预读取文章作者失败, articleId={}", articleId, e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    private CommentLockTarget preReadCommentTarget(final JSONObject identity, final String actionPermission,
                                                   final String commentId) {
        final Transaction tx = begin();
        try {
            final AuthorizedActor actor = authorizeInCurrentTransaction(
                    identity, "settings.write", Permission.PERMISSION_ID_C_MENU_ADMIN_COMMENTS, false);
            requireMenuAdmin(actor);
            if (!actor.permissions().contains(actionPermission)) {
                throw ApiV3Requests.forbidden();
            }
            final List<JSONObject> commentRows = comments.select(
                    "SELECT " + Comment.COMMENT_AUTHOR_ID + "," + Comment.COMMENT_ON_ARTICLE_ID
                            + " FROM `" + comments.getName() + "` WHERE oId=? LIMIT 1",
                    commentId);
            if (commentRows.isEmpty()) {
                throw ApiV3Requests.notFound();
            }
            final JSONObject cRow = commentRows.getFirst();
            final String commentAuthorId = cRow.optString(Comment.COMMENT_AUTHOR_ID);
            final String articleId = cRow.optString(Comment.COMMENT_ON_ARTICLE_ID);
            String articleAuthorId = "";
            if (StringUtils.isNotBlank(articleId)) {
                final List<JSONObject> articleRows = articles.select(
                        "SELECT " + Article.ARTICLE_AUTHOR_ID + " FROM `" + articles.getName() + "` WHERE oId=? LIMIT 1",
                        articleId);
                if (!articleRows.isEmpty()) {
                    articleAuthorId = articleRows.getFirst().optString(Article.ARTICLE_AUTHOR_ID);
                }
            }
            tx.commit();
            return new CommentLockTarget(commentAuthorId, articleId, articleAuthorId);
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("预读取评论关联作者失败, commentId={}", commentId, e);
            throw unavailable();
        } finally {
            rollbackIfActive(tx);
        }
    }

    private JSONObject loadArticleDetailInTransaction(final String articleId) throws RepositoryException {
        final List<JSONObject> rows = articles.select(
                "SELECT * FROM `" + articles.getName() + "` WHERE oId=? LIMIT 1", articleId);
        if (rows.isEmpty()) {
            throw ApiV3Requests.notFound();
        }
        final JSONObject row = rows.getFirst();
        final JSONObject dto = buildArticleSummaryDto(row);
        dto.put("articleContent", row.optString(Article.ARTICLE_CONTENT));
        dto.put("articleRewardContent", row.optString(Article.ARTICLE_REWARD_CONTENT));
        dto.put("articleAnonymous", row.optInt(Article.ARTICLE_ANONYMOUS));
        dto.put("articleAnonymousView", row.optInt(Article.ARTICLE_ANONYMOUS_VIEW));
        dto.put("articleCommentable", row.optBoolean(Article.ARTICLE_COMMENTABLE, true));
        dto.put("articleStatement", row.optInt(Article.ARTICLE_STATEMENT));
        dto.put("articleRewardPoint", row.optInt(Article.ARTICLE_REWARD_POINT));
        dto.put("articleQnAOfferPoint", row.optInt(Article.ARTICLE_QNA_OFFER_POINT));
        dto.put("articlePushOrder", row.optInt(Article.ARTICLE_PUSH_ORDER));
        dto.put("articleThankCnt", row.optInt(Article.ARTICLE_THANK_CNT));
        dto.put("articleIP", row.optString(Article.ARTICLE_IP));
        dto.put("articleUA", row.optString(Article.ARTICLE_UA));
        dto.put("authorColumns", loadAuthorRawColumns(row.optString(Article.ARTICLE_AUTHOR_ID)));
        dto.put("recentOperations", loadRecentOperationsByDataId(articleId));
        return dto;
    }

    private JSONObject loadCommentDetailInTransaction(final String commentId) throws RepositoryException {
        final List<JSONObject> rows = comments.select(
                "SELECT * FROM `" + comments.getName() + "` WHERE oId=? LIMIT 1", commentId);
        if (rows.isEmpty()) {
            throw ApiV3Requests.notFound();
        }
        final JSONObject dto = buildCommentDto(rows.getFirst(), true);
        dto.put("recentOperations", loadRecentOperationsByDataId(commentId));
        return dto;
    }

    private JSONObject loadBreezemoonDetailInTransaction(final String breezemoonId) throws RepositoryException {
        final List<JSONObject> rows = breezemoons.select(
                "SELECT * FROM `" + breezemoons.getName() + "` WHERE oId=? LIMIT 1", breezemoonId);
        if (rows.isEmpty()) {
            throw ApiV3Requests.notFound();
        }
        final JSONObject dto = buildBreezemoonDto(rows.getFirst(), true);
        dto.put("recentOperations", loadRecentOperationsByDataId(breezemoonId));
        return dto;
    }

    private JSONObject buildArticleSummaryDto(final JSONObject row) throws RepositoryException {
        final String articleId = row.optString(Keys.OBJECT_ID);
        final String authorId = row.optString(Article.ARTICLE_AUTHOR_ID);
        final JSONObject authorBrief = loadUserBrief(authorId);
        final int status = row.optInt(Article.ARTICLE_STATUS);
        final int type = row.optInt(Article.ARTICLE_TYPE);

        final JSONObject dto = new JSONObject();
        dto.put("oId", articleId);
        dto.put("articleTitle", row.optString(Article.ARTICLE_TITLE));
        dto.put("articleTags", row.optString(Article.ARTICLE_TAGS));
        dto.put("articleAuthorId", authorId);
        dto.put("articleAuthorName", authorBrief.optString("userName"));
        dto.put("articleAuthorNickname", authorBrief.optString("userNickname"));
        dto.put("articleAuthorAvatarURL", authorBrief.optString("userAvatarURL"));
        dto.put("articleStatus", status);
        dto.put("articleStatusLabel", articleStatusLabel(status));
        dto.put("articleType", type);
        dto.put("articleTypeLabel", articleTypeLabel(type));
        dto.put("articlePerfect", row.optInt(Article.ARTICLE_PERFECT));
        dto.put("articleStick", row.optLong(Article.ARTICLE_STICK));
        dto.put("articleCommentCount", row.optInt(Article.ARTICLE_COMMENT_CNT));
        dto.put("articleViewCount", row.optInt(Article.ARTICLE_VIEW_CNT));
        dto.put("articleGoodCnt", row.optInt(Article.ARTICLE_GOOD_CNT));
        dto.put("articleBadCnt", row.optInt(Article.ARTICLE_BAD_CNT));
        dto.put("articleCollectCnt", row.optInt(Article.ARTICLE_COLLECT_CNT));
        dto.put("articleWatchCnt", row.optInt(Article.ARTICLE_WATCH_CNT));
        dto.put("articlePermalink", row.optString(Article.ARTICLE_PERMALINK));
        dto.put("articleShowInList", row.optInt(Article.ARTICLE_SHOW_IN_LIST, Article.ARTICLE_SHOW_IN_LIST_C_YES));
        dto.put("articleCreateTime", row.optLong(Article.ARTICLE_CREATE_TIME));
        dto.put("articleUpdateTime", row.optLong(Article.ARTICLE_UPDATE_TIME));
        dto.put("articleLatestCmtTime", row.optLong(Article.ARTICLE_LATEST_CMT_TIME));

        final Object chapter = Article.ARTICLE_TYPE_C_LONG == type
                ? loadRawArticleChapterSummary(articleId)
                : JSONObject.NULL;
        dto.put("longArticleChapter", chapter);
        return dto;
    }

    private JSONObject buildCommentDto(final JSONObject row, final boolean includeFullMeta) throws RepositoryException {
        final String commentId = row.optString(Keys.OBJECT_ID);
        final String authorId = row.optString(Comment.COMMENT_AUTHOR_ID);
        final JSONObject authorBrief = loadUserBrief(authorId);
        final String articleId = row.optString(Comment.COMMENT_ON_ARTICLE_ID);
        final JSONObject article = StringUtils.isBlank(articleId) ? null : articles.get(articleId);
        final int status = row.optInt(Comment.COMMENT_STATUS);
        final String content = row.optString(Comment.COMMENT_CONTENT);

        final JSONObject dto = new JSONObject();
        dto.put("oId", commentId);
        dto.put("commentContent", content);
        dto.put("commentContentPreview", stripTagsAndTrim(content, 240));
        dto.put("commentAuthorId", authorId);
        dto.put("commentAuthorName", authorBrief.optString("userName"));
        dto.put("commentAuthorNickname", authorBrief.optString("userNickname"));
        dto.put("commentAuthorAvatarURL", authorBrief.optString("userAvatarURL"));
        dto.put("commentOnArticleId", articleId);
        dto.put("commentArticleTitle", article == null ? "关联文章已删除" : article.optString(Article.ARTICLE_TITLE));
        dto.put("commentArticlePermalink", article == null ? "" : article.optString(Article.ARTICLE_PERMALINK));
        dto.put("commentOriginalCommentId", row.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID));
        dto.put("commentStatus", status);
        dto.put("commentStatusLabel", commentStatusLabel(status));
        dto.put("commentVisible", row.optInt(Comment.COMMENT_VISIBLE));
        dto.put("commentType", row.optInt(Comment.COMMENT_TYPE));
        dto.put("commentGoodCnt", row.optInt(Comment.COMMENT_GOOD_CNT));
        dto.put("commentBadCnt", row.optInt(Comment.COMMENT_BAD_CNT));
        dto.put("commentReplyCnt", row.optInt(Comment.COMMENT_REPLY_CNT));
        dto.put("commentThankCnt", row.optInt(Comment.COMMENT_THANK_CNT));
        dto.put("commentCreateTime", row.optLong(Comment.COMMENT_CREATE_TIME));
        dto.put("commentSharpURL", row.optString(Comment.COMMENT_SHARP_URL));
        dto.put("commentIP", row.optString(Comment.COMMENT_IP));
        if (includeFullMeta) {
            dto.put("commentUA", row.optString(Comment.COMMENT_UA));
            dto.put("commentAnonymous", row.optInt(Comment.COMMENT_ANONYMOUS));
            dto.put("commentQnAOffered", row.optInt(Comment.COMMENT_QNA_OFFERED));
            dto.put("commentParagraphId", row.optString(Comment.COMMENT_PARAGRAPH_ID));
            dto.put("commentParagraphIndex", row.optInt(Comment.COMMENT_PARAGRAPH_INDEX));
            dto.put("commentParagraphSnapshot", row.optString(Comment.COMMENT_PARAGRAPH_SNAPSHOT));
            dto.put("commentParagraphStatus", row.optInt(Comment.COMMENT_PARAGRAPH_STATUS));
        }
        return dto;
    }

    private JSONObject buildBreezemoonDto(final JSONObject row, final boolean includeFullMeta) throws RepositoryException {
        final String authorId = row.optString(Breezemoon.BREEZEMOON_AUTHOR_ID);
        final JSONObject authorBrief = loadUserBrief(authorId);
        final int status = row.optInt(Breezemoon.BREEZEMOON_STATUS);

        final JSONObject dto = new JSONObject();
        dto.put("oId", row.optString(Keys.OBJECT_ID));
        dto.put("breezemoonContent", row.optString(Breezemoon.BREEZEMOON_CONTENT));
        dto.put("breezemoonAuthorId", authorId);
        dto.put("breezemoonAuthorName", authorBrief.optString("userName"));
        dto.put("breezemoonAuthorNickname", authorBrief.optString("userNickname"));
        dto.put("breezemoonAuthorAvatarURL", authorBrief.optString("userAvatarURL"));
        dto.put("breezemoonStatus", status);
        dto.put("breezemoonStatusLabel", breezemoonStatusLabel(status));
        dto.put("breezemoonCity", row.optString(Breezemoon.BREEZEMOON_CITY));
        dto.put("breezemoonCreated", row.optLong(Breezemoon.BREEZEMOON_CREATED));
        dto.put("breezemoonUpdated", row.optLong(Breezemoon.BREEZEMOON_UPDATED));
        dto.put("breezemoonIP", row.optString(Breezemoon.BREEZEMOON_IP));
        if (includeFullMeta) {
            dto.put("breezemoonUA", row.optString(Breezemoon.BREEZEMOON_UA));
        }
        return dto;
    }

    /**
     * 直接读取原始未 HTML 转义的长文专栏与章节绑定关系，确保编辑回显与持久化一致。
     */
    private Object loadRawArticleChapterSummary(final String articleId) throws RepositoryException {
        final List<JSONObject> chapterRows = longArticleChapters.select(
                "SELECT c." + LongArticleColumn.COLUMN_ID + ",c." + LongArticleColumn.CHAPTER_NO
                        + ",col." + LongArticleColumn.COLUMN_TITLE + ",col." + LongArticleColumn.COLUMN_COVER_URL
                        + " FROM `" + longArticleChapters.getName() + "` c"
                        + " LEFT JOIN `" + longArticleColumns.getName() + "` col ON col.oId=c." + LongArticleColumn.COLUMN_ID
                        + " WHERE c." + LongArticleColumn.ARTICLE_ID + "=? LIMIT 1",
                articleId);
        if (chapterRows.isEmpty()) {
            return JSONObject.NULL;
        }
        final JSONObject cRow = chapterRows.getFirst();
        return new JSONObject()
                .put("columnId", cRow.optString(LongArticleColumn.COLUMN_ID))
                .put("columnTitle", cRow.optString(LongArticleColumn.COLUMN_TITLE))
                .put("columnCoverURL", cRow.optString(LongArticleColumn.COLUMN_COVER_URL))
                .put("chapterNo", cRow.optInt(LongArticleColumn.CHAPTER_NO, 1));
    }

    private JSONArray loadAuthorRawColumns(final String authorId) throws RepositoryException {
        final JSONArray list = new JSONArray();
        if (StringUtils.isBlank(authorId)) {
            return list;
        }
        final List<JSONObject> rows = longArticleColumns.select(
                "SELECT oId," + LongArticleColumn.COLUMN_TITLE + "," + LongArticleColumn.COLUMN_COVER_URL
                        + "," + LongArticleColumn.COLUMN_ARTICLE_COUNT + "," + LongArticleColumn.COLUMN_ORDER
                        + " FROM `" + longArticleColumns.getName() + "` WHERE " + LongArticleColumn.COLUMN_AUTHOR_ID
                        + "=? AND " + LongArticleColumn.COLUMN_STATUS + "=? ORDER BY "
                        + LongArticleColumn.COLUMN_ORDER + " ASC, oId ASC",
                authorId, LongArticleColumn.COLUMN_STATUS_C_VALID);
        for (final JSONObject row : rows) {
            list.put(new JSONObject()
                    .put("oId", row.optString(Keys.OBJECT_ID))
                    .put("columnTitle", row.optString(LongArticleColumn.COLUMN_TITLE))
                    .put("columnCoverURL", row.optString(LongArticleColumn.COLUMN_COVER_URL))
                    .put("columnArticleCount", row.optInt(LongArticleColumn.COLUMN_ARTICLE_COUNT))
                    .put("columnOrder", row.optInt(LongArticleColumn.COLUMN_ORDER)));
        }
        return list;
    }

    private JSONObject loadUserBrief(final String userId) throws RepositoryException {
        if (StringUtils.isBlank(userId)) {
            return new JSONObject()
                    .put("userName", "未知用户")
                    .put("userNickname", "")
                    .put("userAvatarURL", AvatarQueryService.DEFAULT_AVATAR_URL);
        }
        final JSONObject user = users.get(userId);
        if (user == null) {
            return new JSONObject()
                    .put("userName", "已删除/未知")
                    .put("userNickname", "")
                    .put("userAvatarURL", AvatarQueryService.DEFAULT_AVATAR_URL);
        }
        return new JSONObject()
                .put("userName", user.optString(User.USER_NAME))
                .put("userNickname", user.optString(UserExt.USER_NICKNAME))
                .put("userAvatarURL", resolveUserAvatarURL(user));
    }

    private String resolveUserAvatarURL(final JSONObject user) {
        final JSONObject avatarProbe = new JSONObject()
                .put(Keys.OBJECT_ID, user.optString(Keys.OBJECT_ID))
                .put(UserExt.USER_AVATAR_TYPE, user.optString(UserExt.USER_AVATAR_TYPE))
                .put(UserExt.USER_AVATAR_URL, user.optString(UserExt.USER_AVATAR_URL))
                .put(User.USER_EMAIL, user.optString(User.USER_EMAIL));
        avatarQueryService.fillUserAvatarURL(avatarProbe);
        return avatarProbe.optString(UserExt.USER_AVATAR_URL);
    }

    private JSONArray loadRecentOperationsByDataId(final String dataId) throws RepositoryException {
        final List<JSONObject> opRows = operations.select(
                "SELECT oId," + Operation.OPERATION_USER_ID + "," + Operation.OPERATION_CODE
                        + "," + Operation.OPERATION_DATA_ID + "," + Operation.OPERATION_CREATED
                        + "," + Operation.OPERATION_IP + "," + Operation.OPERATION_UA
                        + " FROM `" + operations.getName() + "` WHERE " + Operation.OPERATION_DATA_ID
                        + "=? ORDER BY " + Operation.OPERATION_CREATED + " DESC, oId DESC LIMIT 15",
                dataId);
        final JSONArray recentOps = new JSONArray();
        for (final JSONObject opRow : opRows) {
            recentOps.put(formatOperationRow(opRow));
        }
        return recentOps;
    }

    private JSONObject normalizeAdminArticleCreateInput(final JSONObject body) {
        final String userName = ApiV3WriteInputs.string(body, User.USER_NAME, 64, true);
        if (userName.isEmpty()) {
            throw new ApiV3Exception(422, "invalid_request", "请填写文章作者用户名");
        }
        final String title = ApiV3WriteInputs.string(body, Article.ARTICLE_TITLE, 255, true);
        if (title.isEmpty()) {
            throw new ApiV3Exception(422, "invalid_request", "文章标题不能为空");
        }
        final String rawContent = ApiV3WriteInputs.string(body, Article.ARTICLE_CONTENT, 102400, false);
        final String cleanContent = Runes.removeControlChars(rawContent);
        if (StringUtils.isBlank(cleanContent)) {
            throw new ApiV3Exception(422, "invalid_request", "文章正文不能为空");
        }
        final String rewardContent = body.has(Article.ARTICLE_REWARD_CONTENT)
                ? Runes.removeControlChars(ApiV3WriteInputs.string(body, Article.ARTICLE_REWARD_CONTENT, 102400, false))
                : "";
        final String tags = body.has(Article.ARTICLE_TAGS)
                ? ApiV3WriteInputs.string(body, Article.ARTICLE_TAGS, 255, true)
                : "";
        final int articleType = body.has(Article.ARTICLE_TYPE)
                ? requireValidArticleType(body, Article.ARTICLE_TYPE)
                : Article.ARTICLE_TYPE_C_NORMAL;
        final int rewardPoint = body.has(Article.ARTICLE_REWARD_POINT)
                ? requireExactInt(body, Article.ARTICLE_REWARD_POINT, 0, 1000000)
                : 0;
        final int qnaOfferPoint = body.has(Article.ARTICLE_QNA_OFFER_POINT)
                ? requireExactInt(body, Article.ARTICLE_QNA_OFFER_POINT, 0, 1000000)
                : 0;
        final int showInList = body.has(Article.ARTICLE_SHOW_IN_LIST)
                ? requireExactInt(body, Article.ARTICLE_SHOW_IN_LIST, 0, 1)
                : Article.ARTICLE_SHOW_IN_LIST_C_YES;
        final int statement = body.has(Article.ARTICLE_STATEMENT)
                ? requireExactInt(body, Article.ARTICLE_STATEMENT, 0, 1)
                : 0;
        final boolean commentable = !body.has(Article.ARTICLE_COMMENTABLE)
                || requireBoolean(body, Article.ARTICLE_COMMENTABLE);
        final long time = body.has(Common.TIME)
                ? requireExactLong(body, Common.TIME, 0L, Long.MAX_VALUE)
                : 0L;

        final JSONObject normalized = new JSONObject()
                .put(User.USER_NAME, userName)
                .put(Article.ARTICLE_TITLE, title)
                .put(Article.ARTICLE_CONTENT, cleanContent)
                .put(Article.ARTICLE_REWARD_CONTENT, rewardContent)
                .put(Article.ARTICLE_TAGS, tags)
                .put(Article.ARTICLE_TYPE, articleType)
                .put(Article.ARTICLE_REWARD_POINT, rewardPoint)
                .put(Article.ARTICLE_QNA_OFFER_POINT, qnaOfferPoint)
                .put(Article.ARTICLE_SHOW_IN_LIST, showInList)
                .put(Article.ARTICLE_STATEMENT, statement)
                .put(Article.ARTICLE_COMMENTABLE, commentable);
        if (time > 0L) {
            normalized.put(Common.TIME, time);
        }
        copyOptionalLongColumnFields(body, normalized);
        return normalized;
    }

    private ArticleUpdateInput normalizeAdminArticleUpdateInput(final JSONObject body) {
        final JSONObject patch = new JSONObject();
        final JSONObject canonical = new JSONObject();

        if (body.has(Article.ARTICLE_TITLE)) {
            final String title = ApiV3WriteInputs.string(body, Article.ARTICLE_TITLE, 255, true);
            if (title.isEmpty()) {
                throw new ApiV3Exception(422, "invalid_request", "文章标题不能为空");
            }
            patch.put(Article.ARTICLE_TITLE, title);
            canonical.put(Article.ARTICLE_TITLE, title);
        }
        if (body.has(Article.ARTICLE_CONTENT)) {
            final String content = Runes.removeControlChars(
                    ApiV3WriteInputs.string(body, Article.ARTICLE_CONTENT, 102400, false));
            if (StringUtils.isBlank(content)) {
                throw new ApiV3Exception(422, "invalid_request", "文章正文不能为空");
            }
            patch.put(Article.ARTICLE_CONTENT, content);
            canonical.put(Article.ARTICLE_CONTENT, content);
        }
        if (body.has(Article.ARTICLE_REWARD_CONTENT)) {
            final String reward = Runes.removeControlChars(
                    ApiV3WriteInputs.string(body, Article.ARTICLE_REWARD_CONTENT, 102400, false));
            patch.put(Article.ARTICLE_REWARD_CONTENT, reward);
            canonical.put(Article.ARTICLE_REWARD_CONTENT, reward);
        }
        if (body.has(Article.ARTICLE_TAGS)) {
            final String tags = ApiV3WriteInputs.string(body, Article.ARTICLE_TAGS, 255, true);
            patch.put(Article.ARTICLE_TAGS, tags);
            canonical.put(Article.ARTICLE_TAGS, tags);
        }
        if (body.has(Article.ARTICLE_REWARD_POINT)) {
            final int val = requireExactInt(body, Article.ARTICLE_REWARD_POINT, 0, 1000000);
            patch.put(Article.ARTICLE_REWARD_POINT, val);
            canonical.put(Article.ARTICLE_REWARD_POINT, val);
        }
        if (body.has(Article.ARTICLE_QNA_OFFER_POINT)) {
            final int val = requireExactInt(body, Article.ARTICLE_QNA_OFFER_POINT, 0, 1000000);
            patch.put(Article.ARTICLE_QNA_OFFER_POINT, val);
            canonical.put(Article.ARTICLE_QNA_OFFER_POINT, val);
        }
        if (body.has(Article.ARTICLE_PUSH_ORDER)) {
            final int val = requireExactInt(body, Article.ARTICLE_PUSH_ORDER, -1000000, 1000000);
            patch.put(Article.ARTICLE_PUSH_ORDER, val);
            canonical.put(Article.ARTICLE_PUSH_ORDER, val);
        }
        if (body.has(Article.ARTICLE_VIEW_CNT)) {
            final int val = requireExactInt(body, Article.ARTICLE_VIEW_CNT, 0, Integer.MAX_VALUE);
            patch.put(Article.ARTICLE_VIEW_CNT, val);
            canonical.put(Article.ARTICLE_VIEW_CNT, val);
        }
        if (body.has(Article.ARTICLE_GOOD_CNT)) {
            final int val = requireExactInt(body, Article.ARTICLE_GOOD_CNT, 0, Integer.MAX_VALUE);
            patch.put(Article.ARTICLE_GOOD_CNT, val);
            canonical.put(Article.ARTICLE_GOOD_CNT, val);
        }
        if (body.has(Article.ARTICLE_BAD_CNT)) {
            final int val = requireExactInt(body, Article.ARTICLE_BAD_CNT, 0, Integer.MAX_VALUE);
            patch.put(Article.ARTICLE_BAD_CNT, val);
            canonical.put(Article.ARTICLE_BAD_CNT, val);
        }
        if (body.has(Article.ARTICLE_COLLECT_CNT)) {
            final int val = requireExactInt(body, Article.ARTICLE_COLLECT_CNT, 0, Integer.MAX_VALUE);
            patch.put(Article.ARTICLE_COLLECT_CNT, val);
            canonical.put(Article.ARTICLE_COLLECT_CNT, val);
        }
        if (body.has(Article.ARTICLE_WATCH_CNT)) {
            final int val = requireExactInt(body, Article.ARTICLE_WATCH_CNT, 0, Integer.MAX_VALUE);
            patch.put(Article.ARTICLE_WATCH_CNT, val);
            canonical.put(Article.ARTICLE_WATCH_CNT, val);
        }
        if (body.has(Article.ARTICLE_COMMENTABLE)) {
            final boolean val = requireBoolean(body, Article.ARTICLE_COMMENTABLE);
            patch.put(Article.ARTICLE_COMMENTABLE, val);
            canonical.put(Article.ARTICLE_COMMENTABLE, val);
        }
        if (body.has(Article.ARTICLE_TYPE)) {
            final int val = requireValidArticleType(body, Article.ARTICLE_TYPE);
            patch.put(Article.ARTICLE_TYPE, val);
            canonical.put(Article.ARTICLE_TYPE, val);
        }
        if (body.has(Article.ARTICLE_STATUS)) {
            final int val = requireExactInt(body, Article.ARTICLE_STATUS, 0, 2);
            patch.put(Article.ARTICLE_STATUS, val);
            canonical.put(Article.ARTICLE_STATUS, val);
        }
        if (body.has(Article.ARTICLE_PERFECT)) {
            final int val = requireExactInt(body, Article.ARTICLE_PERFECT, 0, 1);
            patch.put(Article.ARTICLE_PERFECT, val);
            canonical.put(Article.ARTICLE_PERFECT, val);
        }
        if (body.has(Article.ARTICLE_STATEMENT)) {
            final int val = requireExactInt(body, Article.ARTICLE_STATEMENT, 0, 1);
            patch.put(Article.ARTICLE_STATEMENT, val);
            canonical.put(Article.ARTICLE_STATEMENT, val);
        }
        if (body.has(Article.ARTICLE_SHOW_IN_LIST)) {
            final int val = requireExactInt(body, Article.ARTICLE_SHOW_IN_LIST, 0, 1);
            patch.put(Article.ARTICLE_SHOW_IN_LIST, val);
            canonical.put(Article.ARTICLE_SHOW_IN_LIST, val);
        }

        final JSONObject columnReq = new JSONObject();
        final boolean hasColumnFields = copyOptionalLongColumnFields(body, columnReq);
        if (hasColumnFields) {
            for (final String key : columnReq.keySet()) {
                canonical.put(key, columnReq.opt(key));
            }
        }
        // 当请求携带长文专栏参数或将文章类型变更为非长文时，显式触发章节关联同步或清理
        final boolean switchedAwayFromLong = patch.has(Article.ARTICLE_TYPE)
                && patch.optInt(Article.ARTICLE_TYPE) != Article.ARTICLE_TYPE_C_LONG;
        final JSONObject effectiveColumnReq = (hasColumnFields || switchedAwayFromLong) ? columnReq : null;
        return new ArticleUpdateInput(patch, effectiveColumnReq, canonical);
    }

    private boolean copyOptionalLongColumnFields(final JSONObject body, final JSONObject target) {
        boolean touched = false;
        if (body.has(LongArticleColumn.COLUMN_ID)) {
            target.put(LongArticleColumn.COLUMN_ID,
                    ApiV3WriteInputs.string(body, LongArticleColumn.COLUMN_ID, 64, true));
            touched = true;
        }
        if (body.has(LongArticleColumn.COLUMN_TITLE)) {
            target.put(LongArticleColumn.COLUMN_TITLE,
                    ApiV3WriteInputs.string(body, LongArticleColumn.COLUMN_TITLE, LongArticleColumn.MAX_COLUMN_TITLE_LENGTH, true));
            touched = true;
        }
        if (body.has(LongArticleColumn.COLUMN_COVER_URL)) {
            final String coverUrl = ApiV3WriteInputs.string(
                    body, LongArticleColumn.COLUMN_COVER_URL, LongArticleColumn.MAX_COLUMN_COVER_URL_LENGTH, true);
            if (!coverUrl.isEmpty() && !isValidHttpUrl(coverUrl)) {
                throw new ApiV3Exception(422, "invalid_request", "长文专栏封面链接必须为 http 或 https 协议");
            }
            target.put(LongArticleColumn.COLUMN_COVER_URL, coverUrl);
            touched = true;
        }
        if (body.has(LongArticleColumn.CHAPTER_NO)) {
            final Object rawNo = body.opt(LongArticleColumn.CHAPTER_NO);
            if (rawNo == null || rawNo == JSONObject.NULL || (rawNo instanceof String s && s.trim().isEmpty())) {
                target.put(LongArticleColumn.CHAPTER_NO, "");
            } else if (rawNo instanceof Number num && !(rawNo instanceof Double) && !(rawNo instanceof Float)) {
                final long parsed = num.longValue();
                if (parsed < 1 || parsed > 100000) {
                    throw new ApiV3Exception(422, "invalid_request", "长文章节序号需在 1 至 100000 之间");
                }
                target.put(LongArticleColumn.CHAPTER_NO, String.valueOf(parsed));
            } else if (rawNo instanceof String str) {
                final String trimmed = str.trim();
                try {
                    final int parsed = Integer.parseInt(trimmed);
                    if (parsed < 1 || parsed > 100000) {
                        throw new ApiV3Exception(422, "invalid_request", "长文章节序号需在 1 至 100000 之间");
                    }
                    target.put(LongArticleColumn.CHAPTER_NO, String.valueOf(parsed));
                } catch (final NumberFormatException e) {
                    throw new ApiV3Exception(422, "invalid_request", "长文章节序号必须为正整数");
                }
            } else {
                throw ApiV3WriteInputs.invalid();
            }
            touched = true;
        }
        return touched;
    }

    private JSONObject normalizeAdminCommentUpdateInput(final JSONObject body) {
        final JSONObject normalized = new JSONObject();
        if (body.has(Comment.COMMENT_CONTENT)) {
            final String raw = ApiV3WriteInputs.string(body, Comment.COMMENT_CONTENT, 102400, false);
            final String processed = Runes.removeControlChars(
                    ReservedWords.processReservedWord(Emotions.toAliases(raw)));
            if (StringUtils.isBlank(processed)) {
                throw new ApiV3Exception(422, "invalid_request", "评论内容不能为空");
            }
            normalized.put(Comment.COMMENT_CONTENT, processed);
        }
        if (body.has(Comment.COMMENT_STATUS)) {
            normalized.put(Comment.COMMENT_STATUS, requireExactInt(body, Comment.COMMENT_STATUS, 0, 1));
        }
        if (body.has(Comment.COMMENT_GOOD_CNT)) {
            normalized.put(Comment.COMMENT_GOOD_CNT, requireExactInt(body, Comment.COMMENT_GOOD_CNT, 0, Integer.MAX_VALUE));
        }
        if (body.has(Comment.COMMENT_BAD_CNT)) {
            normalized.put(Comment.COMMENT_BAD_CNT, requireExactInt(body, Comment.COMMENT_BAD_CNT, 0, Integer.MAX_VALUE));
        }
        if (body.has(Comment.COMMENT_VISIBLE)) {
            normalized.put(Comment.COMMENT_VISIBLE, requireExactInt(body, Comment.COMMENT_VISIBLE, 0, 1));
        }
        if (normalized.isEmpty()) {
            throw ApiV3WriteInputs.invalid();
        }
        return normalized;
    }

    private JSONObject normalizeAdminBreezemoonUpdateInput(final JSONObject body) {
        final JSONObject normalized = new JSONObject();
        if (body.has(Breezemoon.BREEZEMOON_CONTENT)) {
            final String raw = ApiV3WriteInputs.string(body, Breezemoon.BREEZEMOON_CONTENT, 102400, false);
            final String processed = Runes.removeControlChars(
                    ReservedWords.processReservedWord(Emotions.toAliases(raw)));
            if (StringUtils.isBlank(processed)) {
                throw new ApiV3Exception(422, "invalid_request", "清风明月内容不能为空");
            }
            normalized.put(Breezemoon.BREEZEMOON_CONTENT, processed);
        }
        if (body.has(Breezemoon.BREEZEMOON_STATUS)) {
            normalized.put(Breezemoon.BREEZEMOON_STATUS, requireExactInt(body, Breezemoon.BREEZEMOON_STATUS, 0, 1));
        }
        if (normalized.isEmpty()) {
            throw ApiV3WriteInputs.invalid();
        }
        return normalized;
    }

    private static int requireValidArticleType(final JSONObject body, final String key) {
        final int value = requireExactInt(body, key, 0, Article.ARTICLE_TYPE_C_LONG);
        if (value == 4) {
            throw ApiV3WriteInputs.invalid();
        }
        return value;
    }

    static long requireExactLong(final JSONObject body, final String key, final long min, final long max) {
        final Object raw = body.opt(key);
        if (!(raw instanceof Number number) || raw instanceof Double || raw instanceof Float) {
            throw ApiV3WriteInputs.invalid();
        }
        final long value = number.longValue();
        if (value < min || value > max) {
            throw ApiV3WriteInputs.invalid();
        }
        return value;
    }

    static boolean requireBoolean(final JSONObject body, final String key) {
        final Object raw = body.opt(key);
        if (!(raw instanceof Boolean flag)) {
            throw ApiV3WriteInputs.invalid();
        }
        return flag;
    }

    static RuntimeException mapAdminServiceException(final ServiceException e,
                                                     final int status, final String code) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof RepositoryException) {
                return new IllegalStateException(e);
            }
        }
        final String msg = StringUtils.isBlank(e.getMessage()) ? "操作条件不满足，请检查输入参数" : e.getMessage();
        return new ApiV3Exception(status, code, msg);
    }

    private static String articleStatusLabel(final int status) {
        return switch (status) {
            case Article.ARTICLE_STATUS_C_VALID -> "正常";
            case Article.ARTICLE_STATUS_C_INVALID -> "已封禁";
            case Article.ARTICLE_STATUS_C_LOCKED -> "已锁定";
            default -> "未知状态";
        };
    }

    private static String articleTypeLabel(final int type) {
        return switch (type) {
            case Article.ARTICLE_TYPE_C_NORMAL -> "普通帖";
            case Article.ARTICLE_TYPE_C_DISCUSSION -> "站内讨论";
            case Article.ARTICLE_TYPE_C_CITY_BROADCAST -> "同城广播";
            case Article.ARTICLE_TYPE_C_THOUGHT -> "思绪";
            case Article.ARTICLE_TYPE_C_QNA -> "问答";
            case Article.ARTICLE_TYPE_C_LONG -> "长文专栏";
            default -> "类型 #" + type;
        };
    }

    private static String commentStatusLabel(final int status) {
        return switch (status) {
            case Comment.COMMENT_STATUS_C_VALID -> "正常";
            case Comment.COMMENT_STATUS_C_INVALID -> "已封禁";
            default -> "未知状态";
        };
    }

    private static String breezemoonStatusLabel(final int status) {
        return switch (status) {
            case Breezemoon.BREEZEMOON_STATUS_C_VALID -> "正常";
            case Breezemoon.BREEZEMOON_STATUS_C_INVALID -> "已封禁";
            default -> "未知状态";
        };
    }

    static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "管理后台服务暂不可用");
    }
}
