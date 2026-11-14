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
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.processor.WeChatPayProcessor;
import org.b3log.symphony.repository.MembershipLevelRepository;
import org.b3log.symphony.repository.UserMedalRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理后台 v3 会员、优惠券、勋章、积分、工资与背包服务。
 */
@Service
public class ApiV3AdminAssetService {

    private static final Logger LOGGER = LogManager.getLogger(ApiV3AdminAssetService.class);

    @Inject
    private ApiV3AdminService adminService;

    @Inject
    private MembershipMgmtService membershipMgmtService;

    @Inject
    private MembershipLevelRepository levelRepository;

    @Inject
    private CouponMgmtService couponMgmtService;

    @Inject
    private MedalService medalService;

    @Inject
    private MedalBatchService medalBatchService;

    @Inject
    private UserMedalRepository userMedalRepository;

    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    @Inject
    private CloudService cloudService;

    @Inject
    private UserRepository users;

    // ==================== VIP 等级管理 ====================

    /**
     * 查询所有 VIP 等级配置。
     */
    public JSONObject listVipLevels(final JSONObject identity) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(identity, "profile.read", null, true);
            final List<JSONObject> rows = levelRepository.select(
                    "SELECT * FROM `" + levelRepository.getName() + "` ORDER BY sortOrder ASC, oId ASC");
            tx.commit();
            return new JSONObject().put("items", new JSONArray(rows));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询 VIP 等级列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 新增 VIP 等级。
     */
    public JSONObject addVipLevel(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.vip.level.add",
                    idempotencyKey,
                    body,
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final String oId = membershipMgmtService.addLevel(new JSONObject(body.toString()));
                        return new JSONObject().put("oId", oId);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "invalid_request");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("新增 VIP 等级失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 更新 VIP 等级。
     */
    public JSONObject updateVipLevel(final JSONObject identity, final String levelId,
                                     final String idempotencyKey, final JSONObject body) {
        if (StringUtils.isBlank(levelId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.vip.level.update",
                    idempotencyKey,
                    new JSONObject(body.toString()).put("levelId", levelId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        membershipMgmtService.updateLevel(levelId, new JSONObject(body.toString()));
                        return new JSONObject().put("oId", levelId).put("updated", true);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "invalid_request");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新 VIP 等级失败, levelId={}", levelId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 删除 VIP 等级。
     */
    public JSONObject removeVipLevel(final JSONObject identity, final String levelId, final String idempotencyKey) {
        if (StringUtils.isBlank(levelId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.vip.level.remove",
                    idempotencyKey,
                    new JSONObject().put("levelId", levelId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        membershipMgmtService.removeLevel(levelId);
                        return new JSONObject().put("oId", levelId).put("removed", true);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 409, "level_remove_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("删除 VIP 等级失败, levelId={}", levelId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    // ==================== 用户 VIP 实例管理 ====================

    /**
     * 分页查询用户 VIP 会员实例。
     */
    public JSONObject listUserVips(final JSONObject identity, final int page, final int pageSize,
                                   final String userName, final String lvCode, final Integer state) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(identity, "profile.read", null, true);
            final int safePage = Math.max(1, page);
            final int safeSize = Math.min(Math.max(1, pageSize), 100);
            final JSONObject raw = membershipMgmtService.adminListMemberships(
                    safePage, safeSize, StringUtils.trimToNull(userName), StringUtils.trimToNull(lvCode), state);
            tx.commit();
            final int total = raw.optInt("total", 0);
            final JSONArray list = raw.optJSONArray("list");
            return new JSONObject()
                    .put("items", list == null ? new JSONArray() : list)
                    .put("pagination", pagination(safePage, safeSize, total));
        } catch (final ServiceException e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "invalid_request");
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询用户 VIP 列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 管理员免费授予用户 VIP 会员并按原逻辑发放初始礼包。
     */
    public JSONObject addUserVip(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final String target = ApiV3WriteInputs.string(body, "target", 128, false);
        final String levelOId = ApiV3WriteInputs.string(body, "levelOId", 64, false);
        final String configJson = body.has("configJson")
                ? ApiV3WriteInputs.string(body, "configJson", 4096, true)
                : "";
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.vip.user.add",
                    idempotencyKey,
                    new JSONObject().put("target", target).put("levelOId", levelOId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject user = resolveUser(target);
                        final String userId = user.optString(Keys.OBJECT_ID);
                        final JSONObject res = membershipMgmtService.adminAddMembershipNoCost(userId, levelOId, configJson);
                        return new JSONObject()
                                .put("userId", userId)
                                .put("userName", user.optString(User.USER_NAME))
                                .put("membership", res);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "vip_grant_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("赠送用户 VIP 失败, target={}", target, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 修改用户 VIP 会员等级、状态、到期时间与配置。
     */
    public JSONObject updateUserVip(final JSONObject identity, final String userId,
                                    final String idempotencyKey, final JSONObject body) {
        if (StringUtils.isBlank(userId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String lvCode = body.has("lvCode") ? ApiV3WriteInputs.string(body, "lvCode", 64, true) : null;
        final Integer state = body.has("state") ? ApiV3AdminService.requireExactInt(body, "state", 0, 1) : null;
        final Long expiresAt = body.has("expiresAt") ? ApiV3AdminService.requireExactLong(body, "expiresAt", 0L, Long.MAX_VALUE) : null;
        final String configJson = body.has("configJson") ? ApiV3WriteInputs.string(body, "configJson", 4096, true) : null;

        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.vip.user.update",
                    idempotencyKey,
                    new JSONObject(body.toString()).put("userId", userId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject res = membershipMgmtService.adminUpdateMembership(
                                userId, StringUtils.trimToNull(lvCode), state, expiresAt, configJson);
                        return new JSONObject().put("userId", userId).put("membership", res);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "vip_update_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新用户 VIP 失败, userId={}", userId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 按天数延长用户 VIP 会员有效期。
     */
    public JSONObject extendUserVip(final JSONObject identity, final String userId,
                                    final String idempotencyKey, final JSONObject body) {
        if (StringUtils.isBlank(userId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final int days = ApiV3AdminService.requireExactInt(body, "days", 1, 3650);
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.vip.user.extend",
                    idempotencyKey,
                    new JSONObject().put("userId", userId).put("days", days),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject res = membershipMgmtService.adminExtendMembershipDays(userId, days);
                        return new JSONObject().put("userId", userId).put("days", days).put("membership", res);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "vip_extend_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("延长用户 VIP 失败, userId={}, days={}", userId, days, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 按剩余天数折算退还积分并停用用户 VIP。
     */
    public JSONObject refundUserVip(final JSONObject identity, final String userId, final String idempotencyKey) {
        if (StringUtils.isBlank(userId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.vip.user.refund",
                    idempotencyKey,
                    new JSONObject().put("userId", userId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject res = membershipMgmtService.adminRefundMembershipByDays(userId);
                        return new JSONObject().put("userId", userId).put("refund", res);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "vip_refund_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("退订用户 VIP 失败, userId={}", userId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    // ==================== 优惠券管理 ====================

    /**
     * 查询所有优惠券列表。
     */
    public JSONObject listCoupons(final JSONObject identity) {
        final Transaction tx = adminService.begin();
        try {
            adminService.authorizeInCurrentTransaction(identity, "profile.read", null, true);
            final List<JSONObject> list = couponMgmtService.list();
            tx.commit();
            return new JSONObject().put("items", new JSONArray(list));
        } catch (final ServiceException e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "invalid_request");
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询优惠券列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 创建优惠券（times = -1 表示无限次）。
     */
    public JSONObject addCoupon(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final String couponCode = body.has("couponCode")
                ? ApiV3WriteInputs.string(body, "couponCode", 32, true)
                : "";
        if (StringUtils.isNotBlank(couponCode) && (couponCode.length() < 8 || couponCode.length() > 32)) {
            throw new ApiV3Exception(422, "invalid_coupon_code", "自定义优惠券码长度需在 8 到 32 位之间");
        }
        final int couponType = ApiV3AdminService.requireExactInt(body, "couponType", 0, 1);
        final int discount = ApiV3AdminService.requireExactInt(body, "discount", 1, couponType == 0 ? 100 : 1000000);
        final int times = ApiV3AdminService.requireExactInt(body, "times", -1, 1000000);
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.coupon.add",
                    idempotencyKey,
                    new JSONObject().put("couponCode", couponCode).put("couponType", couponType)
                            .put("discount", discount).put("times", times),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONObject createdCoupon = couponMgmtService.add(
                                actorRef.get().userName(),
                                StringUtils.trimToNull(couponCode),
                                couponType,
                                discount,
                                times);
                        return new JSONObject().put("oId", createdCoupon == null ? "" : createdCoupon.optString(Keys.OBJECT_ID));
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "coupon_create_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("创建优惠券失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 更新优惠券可用次数（-1 表示无限次）。
     */
    public JSONObject updateCouponTimes(final JSONObject identity, final String couponId,
                                        final String idempotencyKey, final JSONObject body) {
        if (StringUtils.isBlank(couponId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final int times = ApiV3AdminService.requireExactInt(body, "times", -1, 1000000);
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.coupon.update",
                    idempotencyKey,
                    new JSONObject().put("couponId", couponId).put("times", times),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        couponMgmtService.updateTimes(couponId, times);
                        return new JSONObject().put("oId", couponId).put("times", times);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "coupon_update_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新优惠券次数失败, couponId={}", couponId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 删除优惠券。
     */
    public JSONObject removeCoupon(final JSONObject identity, final String couponId, final String idempotencyKey) {
        if (StringUtils.isBlank(couponId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.coupon.remove",
                    idempotencyKey,
                    new JSONObject().put("couponId", couponId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        couponMgmtService.remove(couponId);
                        return new JSONObject().put("oId", couponId).put("removed", true);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "coupon_remove_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("删除优惠券失败, couponId={}", couponId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    // ==================== 勋章管理 ====================

    /**
     * 分页或按关键词查询勋章定义列表。
     */
    public JSONObject listMedals(final JSONObject identity, final int page, final int pageSize, final String keyword) {
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor = adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", null, false);
            requireMedalViewAccess(actor);

            final int safePage = Math.max(1, page);
            final int safeSize = Math.min(Math.max(1, pageSize), 100);
            final List<JSONObject> list = StringUtils.isNotBlank(keyword)
                    ? medalService.searchMedals(keyword.trim())
                    : medalService.getAllMedalsPaged(safePage, safeSize);
            tx.commit();
            return new JSONObject()
                    .put("items", new JSONArray(list))
                    .put("page", safePage)
                    .put("pageSize", safeSize);
        } catch (final ServiceException e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "invalid_request");
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询勋章列表失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 新增勋章定义（仅管理员）。
     */
    public JSONObject addMedal(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final String name = ApiV3WriteInputs.string(body, "name", 128, false);
        final String type = ApiV3WriteInputs.string(body, "type", 64, false);
        final String description = body.has("description")
                ? ApiV3WriteInputs.string(body, "description", 1024, true)
                : "";
        final String attr = body.has("attr")
                ? ApiV3WriteInputs.string(body, "attr", 4096, true)
                : "{}";
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.medal.add",
                    idempotencyKey,
                    new JSONObject().put("name", name).put("type", type),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final String oId = medalService.addMedal(name, type, description, attr);
                        return new JSONObject().put("oId", oId);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "medal_create_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("新增勋章失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 更新勋章定义（仅管理员）。
     */
    public JSONObject updateMedal(final JSONObject identity, final String medalId,
                                  final String idempotencyKey, final JSONObject body) {
        if (StringUtils.isBlank(medalId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String name = ApiV3WriteInputs.string(body, "name", 128, false);
        final String type = ApiV3WriteInputs.string(body, "type", 64, false);
        final String description = body.has("description")
                ? ApiV3WriteInputs.string(body, "description", 1024, true)
                : "";
        final String attr = body.has("attr")
                ? ApiV3WriteInputs.string(body, "attr", 4096, true)
                : "{}";
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.medal.update",
                    idempotencyKey,
                    new JSONObject().put("medalId", medalId).put("name", name).put("type", type),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        medalService.updateMedal(medalId, name, type, description, attr);
                        return new JSONObject().put("oId", medalId).put("updated", true);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "medal_update_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("更新勋章失败, medalId={}", medalId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 删除勋章定义（仅管理员）。
     */
    public JSONObject deleteMedal(final JSONObject identity, final String medalId, final String idempotencyKey) {
        if (StringUtils.isBlank(medalId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.medal.delete",
                    idempotencyKey,
                    new JSONObject().put("medalId", medalId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        medalService.deleteMedal(medalId);
                        return new JSONObject().put("oId", medalId).put("removed", true);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "medal_delete_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("删除勋章失败, medalId={}", medalId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 重排所有赞助勋章展示顺序（仅管理员）。
     */
    public JSONObject reorderSponsorMedals(final JSONObject identity, final String idempotencyKey) {
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.medal.reorder_sponsor",
                    idempotencyKey,
                    new JSONObject(),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        medalService.reorderAllSponsorMedals();
                        return new JSONObject().put("reordered", true);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "medal_reorder_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("重排赞助勋章失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 查询某枚勋章的持有用户列表。
     */
    public JSONObject listMedalOwners(final JSONObject identity, final String medalId) {
        if (StringUtils.isBlank(medalId)) {
            throw ApiV3WriteInputs.invalid();
        }
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor = adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", null, false);
            requireMedalViewAccess(actor);

            final List<JSONObject> rows = userMedalRepository.select(
                    "SELECT oId,user_id,medal_id,data,expire_time,display,display_order "
                            + "FROM `" + userMedalRepository.getName() + "` WHERE medal_id=? ORDER BY oId DESC LIMIT 200",
                    medalId);
            final JSONArray items = new JSONArray();
            for (final JSONObject row : rows) {
                final String userId = row.optString("user_id");
                final JSONObject user = StringUtils.isBlank(userId) ? null : users.get(userId);
                items.put(new JSONObject()
                        .put("oId", row.optString(Keys.OBJECT_ID))
                        .put("userId", userId)
                        .put("userName", user == null ? "" : user.optString(User.USER_NAME))
                        .put("data", row.optString("data"))
                        .put("expireTime", row.optLong("expire_time", 0L))
                        .put("permanent", row.optLong("expire_time", 0L) == 0L));
            }
            tx.commit();
            return new JSONObject().put("items", items);
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询勋章持有者失败, medalId={}", medalId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 查询指定用户的勋章列表。
     */
    public JSONObject listUserMedals(final JSONObject identity, final String target) {
        if (StringUtils.isBlank(target)) {
            throw ApiV3WriteInputs.invalid();
        }
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor = adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", null, false);
            requireMedalViewAccess(actor);

            final JSONObject user = resolveUser(target);
            final String userId = user.optString(Keys.OBJECT_ID);
            final List<JSONObject> list = medalService.getUserMedals(userId);
            tx.commit();
            return new JSONObject()
                    .put("userId", userId)
                    .put("userName", user.optString(User.USER_NAME))
                    .put("items", new JSONArray(list));
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("查询用户勋章失败, target={}", target, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 批量检测用户是否已持有指定勋章。
     */
    public JSONObject checkMedalHoldStatuses(final JSONObject identity, final JSONObject body) {
        final String medalId = ApiV3WriteInputs.string(body, "medalId", 64, false);
        final JSONArray rawTargets = body.optJSONArray("targets");
        if (rawTargets == null || rawTargets.isEmpty()) {
            throw ApiV3WriteInputs.invalid();
        }
        final Transaction tx = adminService.begin();
        try {
            final ApiV3AdminService.AuthorizedActor actor = adminService.authorizeInCurrentTransaction(
                    identity, "profile.read", null, false);
            requireMedalViewAccess(actor);

            final List<String> userIds = new ArrayList<>();
            final JSONArray resolved = new JSONArray();
            for (int i = 0; i < rawTargets.length(); i++) {
                final String t = StringUtils.trimToEmpty(rawTargets.optString(i));
                if (StringUtils.isBlank(t)) {
                    continue;
                }
                final JSONObject user = tryResolveUser(t);
                if (user != null) {
                    final String uid = user.optString(Keys.OBJECT_ID);
                    userIds.add(uid);
                    resolved.put(new JSONObject()
                            .put("target", t)
                            .put("found", true)
                            .put("userId", uid)
                            .put("userName", user.optString(User.USER_NAME)));
                } else {
                    resolved.put(new JSONObject().put("target", t).put("found", false));
                }
            }
            final Map<String, Boolean> holds = new java.util.HashMap<>();
            if (!userIds.isEmpty()) {
                for (final JSONObject statusRow : medalBatchService.getHoldStatuses(userIds, medalId)) {
                    holds.put(statusRow.optString("userId"), statusRow.optBoolean("held"));
                }
            }
            for (int i = 0; i < resolved.length(); i++) {
                final JSONObject item = resolved.getJSONObject(i);
                if (item.optBoolean("found")) {
                    item.put("holds", holds.getOrDefault(item.optString("userId"), false));
                }
            }
            tx.commit();
            return new JSONObject().put("items", resolved);
        } catch (final ApiV3Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            throw e;
        } catch (final Exception e) {
            ApiV3AdminService.rollbackIfActive(tx);
            LOGGER.error("检测勋章持有状态失败, medalId={}", medalId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 单个授予用户勋章（expireTime = 0 表示永久）。
     */
    public JSONObject grantMedal(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final String target = ApiV3WriteInputs.string(body, "target", 128, false);
        final String medalId = ApiV3WriteInputs.string(body, "medalId", 64, false);
        final long expireTime = body.has("expireTime")
                ? ApiV3AdminService.requireExactLong(body, "expireTime", 0L, Long.MAX_VALUE)
                : 0L;
        final String data = body.has("data") ? ApiV3WriteInputs.string(body, "data", 1024, true) : "";
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, false, actorRef),
                    actorId,
                    "admin.medal.grant",
                    idempotencyKey,
                    new JSONObject().put("target", target).put("medalId", medalId).put("expireTime", expireTime),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.isAdmin() && !actor.permissions().contains(Permission.PERMISSION_ID_C_USER_GIVE_METAL)) {
                            throw ApiV3Requests.forbidden();
                        }
                        final JSONObject user = resolveUser(target);
                        final String userId = user.optString(Keys.OBJECT_ID);
                        medalService.grantMedalToUser(userId, medalId, expireTime, data);
                        return new JSONObject()
                                .put("userId", userId)
                                .put("userName", user.optString(User.USER_NAME))
                                .put("medalId", medalId)
                                .put("expireTime", expireTime);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "medal_grant_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("授予用户勋章失败, target={}, medalId={}", target, medalId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 单个回收用户勋章。
     */
    public JSONObject revokeMedal(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final String target = ApiV3WriteInputs.string(body, "target", 128, false);
        final String medalId = ApiV3WriteInputs.string(body, "medalId", 64, false);
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, false, actorRef),
                    actorId,
                    "admin.medal.revoke",
                    idempotencyKey,
                    new JSONObject().put("target", target).put("medalId", medalId),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.isAdmin() && !actor.permissions().contains(Permission.PERMISSION_ID_C_USER_REMOVE_METAL)) {
                            throw ApiV3Requests.forbidden();
                        }
                        final JSONObject user = resolveUser(target);
                        final String userId = user.optString(Keys.OBJECT_ID);
                        medalService.revokeMedalFromUser(userId, medalId);
                        return new JSONObject()
                                .put("userId", userId)
                                .put("userName", user.optString(User.USER_NAME))
                                .put("medalId", medalId)
                                .put("revoked", true);
                    }));
        } catch (final ServiceException e) {
            throw ApiV3AdminService.mapAdminServiceException(e, 422, "medal_revoke_failed");
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("回收用户勋章失败, target={}, medalId={}", target, medalId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 批量授予勋章，并返回逐条成功/失败明细。
     */
    public JSONObject batchGrantMedals(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final String medalId = ApiV3WriteInputs.string(body, "medalId", 64, false);
        final long expireTime = body.has("expireTime")
                ? ApiV3AdminService.requireExactLong(body, "expireTime", 0L, Long.MAX_VALUE)
                : 0L;
        final String data = body.has("data") ? ApiV3WriteInputs.string(body, "data", 1024, true) : "";
        final JSONArray targets = body.optJSONArray("targets");
        if (targets == null || targets.isEmpty() || targets.length() > 200) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, false, actorRef),
                    actorId,
                    "admin.medal.batch_grant",
                    idempotencyKey,
                    new JSONObject().put("medalId", medalId).put("targets", targets).put("expireTime", expireTime),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.isAdmin() && !actor.permissions().contains(Permission.PERMISSION_ID_C_USER_GIVE_METAL)) {
                            throw ApiV3Requests.forbidden();
                        }
                        final JSONArray results = new JSONArray();
                        int successCount = 0;
                        int failureCount = 0;
                        for (int i = 0; i < targets.length(); i++) {
                            final String target = StringUtils.trimToEmpty(targets.optString(i));
                            if (StringUtils.isBlank(target)) {
                                failureCount++;
                                results.put(new JSONObject().put("target", "").put("ok", false).put("error", "目标为空"));
                                continue;
                            }
                            try {
                                ApiV3TransactionSavepoint.run(() -> {
                                    final JSONObject user = resolveUser(target);
                                    final String userId = user.optString(Keys.OBJECT_ID);
                                    medalService.grantMedalToUser(userId, medalId, expireTime, data);
                                    results.put(new JSONObject()
                                            .put("target", target)
                                            .put("userId", userId)
                                            .put("userName", user.optString(User.USER_NAME))
                                            .put("ok", true));
                                    return null;
                                });
                                successCount++;
                            } catch (final Exception ex) {
                                failureCount++;
                                results.put(new JSONObject()
                                        .put("target", target)
                                        .put("ok", false)
                                        .put("error", StringUtils.isBlank(ex.getMessage()) ? "授予失败" : ex.getMessage()));
                            }
                        }
                        return new JSONObject()
                                .put("medalId", medalId)
                                .put("successCount", successCount)
                                .put("failureCount", failureCount)
                                .put("results", results);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("批量授予勋章失败, medalId={}", medalId, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    // ==================== 积分、工资与背包 ====================

    /**
     * 积分充值（支持 sponsor 金额折算模式或 direct 直接加分模式）。
     */
    public JSONObject chargePoint(final JSONObject identity, final String idempotencyKey,
                                  final Request request, final JSONObject body) {
        final String target = ApiV3WriteInputs.string(body, "target", 128, false);
        final String memo = ApiV3WriteInputs.string(body, "memo", 500, false);
        final String mode = body.has("mode") ? ApiV3WriteInputs.string(body, "mode", 32, false) : "direct";
        final int amount = ApiV3AdminService.requireExactInt(body, "amount", 1, 1000000);
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, false, actorRef),
                    actorId,
                    "admin.point.charge",
                    idempotencyKey,
                    new JSONObject().put("target", target).put("amount", amount).put("mode", mode).put("memo", memo),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.isAdmin() && !actor.permissions().contains(Permission.PERMISSION_ID_C_USER_ADD_POINT)) {
                            throw ApiV3Requests.forbidden();
                        }
                        final JSONObject user = resolveUser(target);
                        final String userId = user.optString(Keys.OBJECT_ID);
                        if ("sponsor".equals(mode)) {
                            WeChatPayProcessor.manual(userId, String.valueOf(amount), memo);
                        } else {
                            pointtransferMgmtService.transferInCurrentTransaction(
                                    Pointtransfer.ID_C_SYS, userId, Pointtransfer.TRANSFER_TYPE_C_CHARGE,
                                    amount, memo, System.currentTimeMillis(), "");
                        }
                        adminService.recordOperation(request, Operation.OPERATION_CODE_C_CHARGE_POINT,
                                userId, actor.user());
                        return new JSONObject()
                                .put("userId", userId)
                                .put("userName", user.optString(User.USER_NAME))
                                .put("amount", amount)
                                .put("mode", mode);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("积分充值失败, target={}", target, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 扣除违规积分。
     */
    public JSONObject deductPoint(final JSONObject identity, final String idempotencyKey,
                                  final Request request, final JSONObject body) {
        return runSinglePointTransfer(
                identity, idempotencyKey, request, body,
                "admin.point.deduct",
                Permission.PERMISSION_ID_C_USER_DEDUCT_POINT,
                Operation.OPERATION_CODE_C_DEDUCT_POINT,
                Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT,
                false);
    }

    /**
     * 初始化用户积分。
     */
    public JSONObject initPoint(final JSONObject identity, final String idempotencyKey,
                                final Request request, final JSONObject body) {
        return runSinglePointTransfer(
                identity, idempotencyKey, request, body,
                "admin.point.init",
                Permission.PERMISSION_ID_C_USER_ADD_POINT,
                Operation.OPERATION_CODE_C_INIT_POINT,
                Pointtransfer.TRANSFER_TYPE_C_INIT,
                true);
    }

    /**
     * 积分兑换扣减。
     */
    public JSONObject exchangePoint(final JSONObject identity, final String idempotencyKey,
                                    final Request request, final JSONObject body) {
        return runSinglePointTransfer(
                identity, idempotencyKey, request, body,
                "admin.point.exchange",
                Permission.PERMISSION_ID_C_USER_EXCHANGE_POINT,
                Operation.OPERATION_CODE_C_EXCHANGE_POINT,
                Pointtransfer.TRANSFER_TYPE_C_EXCHANGE,
                false);
    }

    private JSONObject runSinglePointTransfer(final JSONObject identity, final String idempotencyKey,
                                              final Request request, final JSONObject body,
                                              final String opName, final String requiredPerm,
                                              final int operationCode, final int transferType,
                                              final boolean sysToUser) {
        final String target = ApiV3WriteInputs.string(body, "target", 128, false);
        final int point = ApiV3AdminService.requireExactInt(body, Common.POINT, 1, 10000000);
        final String memo = body.has("memo") ? ApiV3WriteInputs.string(body, "memo", 500, true) : "";
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, false, actorRef),
                    actorId,
                    opName,
                    idempotencyKey,
                    new JSONObject().put("target", target).put(Common.POINT, point).put("memo", memo),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.isAdmin() && !actor.permissions().contains(requiredPerm)) {
                            throw ApiV3Requests.forbidden();
                        }
                        final JSONObject user = resolveUser(target);
                        final String userId = user.optString(Keys.OBJECT_ID);
                        final String fromId = sysToUser ? Pointtransfer.ID_C_SYS : userId;
                        final String toId = sysToUser ? userId : Pointtransfer.ID_C_SYS;
                        final String transferId = pointtransferMgmtService.transferInCurrentTransaction(
                                fromId, toId, transferType, point, memo, System.currentTimeMillis(), "");
                        if (StringUtils.isBlank(transferId)) {
                            throw new ApiV3Exception(422, "point_transfer_failed", "积分划转失败，请确认用户余额与状态");
                        }
                        adminService.recordOperation(request, operationCode, userId, actor.user());
                        return new JSONObject()
                                .put("transferId", transferId)
                                .put("userId", userId)
                                .put("userName", user.optString(User.USER_NAME))
                                .put("point", point);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("积分操作失败, op={}, target={}", opName, target, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 批量发放工资，返回逐条发放明细。
     */
    public JSONObject paySalary(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final JSONArray items = body.optJSONArray("items");
        if (items == null || items.isEmpty() || items.length() > 200) {
            throw ApiV3WriteInputs.invalid();
        }
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, true, actorRef),
                    actorId,
                    "admin.salary.pay",
                    idempotencyKey,
                    new JSONObject().put("items", items),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final JSONArray results = new JSONArray();
                        int successCount = 0;
                        int failureCount = 0;
                        int totalAmount = 0;

                        for (int i = 0; i < items.length(); i++) {
                            final JSONObject row = items.optJSONObject(i);
                            if (row == null) {
                                failureCount++;
                                results.put(new JSONObject().put("index", i).put("ok", false).put("error", "条目格式无效"));
                                continue;
                            }
                            final String target = StringUtils.trimToEmpty(row.optString("target"));
                            final int amount = row.optInt("amount", 0);
                            final String memo = StringUtils.trimToEmpty(row.optString("memo"));
                            if (StringUtils.isBlank(target) || amount <= 0 || amount > 1000000) {
                                failureCount++;
                                results.put(new JSONObject().put("index", i).put("target", target)
                                        .put("ok", false).put("error", "目标或金额无效"));
                                continue;
                            }
                            try {
                                ApiV3TransactionSavepoint.run(() -> {
                                    final JSONObject user = resolveUser(target);
                                    final String userId = user.optString(Keys.OBJECT_ID);
                                    final String transferId = pointtransferMgmtService.transferInCurrentTransaction(
                                            Pointtransfer.ID_C_SYS, userId,
                                            Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT,
                                            amount, userId, System.currentTimeMillis(),
                                            StringUtils.isBlank(memo) ? "后台工资发放" : memo);
                                    if (StringUtils.isBlank(transferId)) {
                                        throw new IllegalStateException("转账未生成流水");
                                    }
                                    results.put(new JSONObject()
                                            .put("index", i)
                                            .put("target", target)
                                            .put("userId", userId)
                                            .put("userName", user.optString(User.USER_NAME))
                                            .put("amount", amount)
                                            .put("transferId", transferId)
                                            .put("ok", true));
                                    return null;
                                });
                                successCount++;
                                totalAmount += amount;
                            } catch (final Exception ex) {
                                failureCount++;
                                results.put(new JSONObject()
                                        .put("index", i)
                                        .put("target", target)
                                        .put("ok", false)
                                        .put("error", StringUtils.isBlank(ex.getMessage()) ? "发放失败" : ex.getMessage()));
                            }
                        }

                        return new JSONObject()
                                .put("successCount", successCount)
                                .put("failureCount", failureCount)
                                .put("totalAmount", totalAmount)
                                .put("results", results);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("批量发放工资失败", e);
            throw ApiV3AdminService.unavailable();
        }
    }

    /**
     * 调整用户背包道具数量。
     */
    public JSONObject adjustBag(final JSONObject identity, final String idempotencyKey, final JSONObject body) {
        final String target = ApiV3WriteInputs.string(body, "target", 128, false);
        final String item = ApiV3WriteInputs.string(body, "item", 128, false);
        final int number = ApiV3AdminService.requireExactInt(body, "number", -100000, 100000);
        final int maxTake = body.has("maxTake")
                ? ApiV3AdminService.requireExactInt(body, "maxTake", 0, 1000000)
                : 999999;
        final String actorId = ApiV3AdminService.actorId(identity);
        final AtomicReference<ApiV3AdminService.AuthorizedActor> actorRef = new AtomicReference<>();

        try {
            return ApiV3IdempotencyMgmtService.runAtomic(
                    adminService.guardedWrite(identity, null, false, actorRef),
                    actorId,
                    "admin.bag.adjust",
                    idempotencyKey,
                    new JSONObject().put("target", target).put("item", item).put("number", number).put("maxTake", maxTake),
                    () -> ApiV3TransactionSavepoint.run(() -> {
                        final ApiV3AdminService.AuthorizedActor actor = actorRef.get();
                        if (!actor.isAdmin() && !actor.permissions().contains(Permission.PERMISSION_ID_C_USER_ADJUST_BAG)) {
                            throw ApiV3Requests.forbidden();
                        }
                        final JSONObject user = resolveUser(target);
                        final String userId = user.optString(Keys.OBJECT_ID);
                        cloudService.putBag(userId, item, number, maxTake);
                        return new JSONObject()
                                .put("userId", userId)
                                .put("userName", user.optString(User.USER_NAME))
                                .put("item", item)
                                .put("number", number);
                    }));
        } catch (final ApiV3Exception e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error("调整用户背包失败, target={}, item={}", target, item, e);
            throw ApiV3AdminService.unavailable();
        }
    }

    private void requireMedalViewAccess(final ApiV3AdminService.AuthorizedActor actor) {
        final Set<String> perms = actor.permissions();
        if (!actor.isAdmin()
                && !perms.contains(Permission.PERMISSION_ID_C_USER_GIVE_METAL)
                && !perms.contains(Permission.PERMISSION_ID_C_USER_REMOVE_METAL)) {
            throw ApiV3Requests.forbidden();
        }
    }

    private JSONObject resolveUser(final String target) throws RepositoryException {
        final JSONObject found = tryResolveUser(target);
        if (found == null) {
            throw new ApiV3Exception(404, "user_not_found", "未找到目标用户：" + target);
        }
        return found;
    }

    private JSONObject tryResolveUser(final String target) throws RepositoryException {
        final String trimmed = StringUtils.trimToEmpty(target);
        if (StringUtils.isBlank(trimmed)) {
            return null;
        }
        if (trimmed.matches("[1-9][0-9]{0,18}")) {
            final JSONObject byId = users.get(trimmed);
            if (byId != null) {
                return byId;
            }
        }
        return users.getByName(trimmed);
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
