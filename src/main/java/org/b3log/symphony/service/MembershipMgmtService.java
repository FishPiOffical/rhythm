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

import java.util.Objects;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Ids;
import org.b3log.latke.util.Times;
import org.b3log.symphony.model.Membership;
import org.b3log.symphony.model.MembershipActivation;
import org.b3log.symphony.model.MembershipLevel;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.Coupon;
import org.b3log.symphony.repository.MembershipActivationRepository;
import org.b3log.symphony.repository.MembershipLevelRepository;
import org.b3log.symphony.repository.MembershipRepository;
import org.b3log.symphony.repository.CouponRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.cache.MembershipCache;
import org.json.JSONObject;
import org.json.JSONArray;

@Singleton
public class MembershipMgmtService {
    private static final Logger LOGGER = LogManager.getLogger(MembershipMgmtService.class);

    /**
     * 非折扣或无效优惠券的惩罚倍率（按原价的倍数）。
     */
    private static final double INVALID_COUPON_PENALTY_RATE = 1.2d;

    /**
     * 优惠券类型：折扣。
     * 当前实现中约定为 0。
     */
    private static final int COUPON_TYPE_DISCOUNT = 0;

    @Inject
    private MembershipLevelRepository levelRepository;

    @Inject
    private MembershipRepository membershipRepository;

    @Inject
    private MembershipActivationRepository activationRepository;

    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    @Inject
    private NotificationMgmtService notificationMgmtService;

    @Inject
    private CouponRepository couponRepository;

    @Inject
    private MembershipCache membershipCache;

    @Inject
    private CloudService cloudService;

    @Inject
    private UserRepository userRepository;

    public String addLevel(final JSONObject level) throws ServiceException {
        final Transaction transaction = levelRepository.beginTransaction();
        try {
            final String lvCode = level.optString(MembershipLevel.LV_CODE);
            final String durationType = level.optString(MembershipLevel.DURATION_TYPE);
            if (StringUtils.isBlank(lvCode) || StringUtils.isBlank(durationType)) {
                throw new ServiceException("lvCode 与 durationType 不能为空");
            }
            if (null != levelRepository.getByCodeAndDurationType(lvCode, durationType)) {
                throw new ServiceException("已存在相同 lvCode + durationType 的等级配置");
            }
            final long now = System.currentTimeMillis();
            level.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
            level.put(MembershipLevel.CREATED_AT, now);
            level.put(MembershipLevel.UPDATED_AT, now);
            levelRepository.add(level);
            transaction.commit();
            return level.optString(Keys.OBJECT_ID);
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Add level failed", e);
            throw new ServiceException(e);
        }
    }

    public void updateLevel(final String oId, final JSONObject levelPatch) throws ServiceException {
        final Transaction transaction = levelRepository.beginTransaction();
        try {
            final JSONObject old = levelRepository.getById(oId);
            if (null == old) {
                throw new ServiceException("等级不存在");
            }
            for (final String key : levelPatch.keySet()) {
                old.put(key, levelPatch.opt(key));
            }
            old.put(MembershipLevel.UPDATED_AT, System.currentTimeMillis());
            levelRepository.update(oId, old);
            transaction.commit();
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Update level failed", e);
            throw new ServiceException(e);
        }
    }

    public void removeLevel(final String oId) throws ServiceException {
        final Transaction transaction = levelRepository.beginTransaction();
        try {
            levelRepository.remove(oId);
            transaction.commit();
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Remove level failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * 开通会员：按照等级定义计算过期时间，写入会员记录与开通记录。
     */
    public JSONObject openMembership(final String userId, final String levelOId, final String configJson,
            final String couponCode)
            throws ServiceException {
        final Transaction transaction = membershipRepository.beginTransaction();
        try {
            if (StringUtils.isBlank(userId) || StringUtils.isBlank(levelOId)) {
                throw new ServiceException("参数不完整");
            }
            final JSONObject level = levelRepository.getById(levelOId);
            if (null == level) {
                throw new ServiceException("等级不存在");
            }
            final String lvCode = level.optString(MembershipLevel.LV_CODE);
            final String durationType = level.optString(MembershipLevel.DURATION_TYPE);
            final int durationValue = level.optInt(MembershipLevel.DURATION_VALUE);
            final int price = level.optInt(MembershipLevel.PRICE);
            if (durationValue <= 0) {
                throw new ServiceException("等级周期配置非法");
            }
            final long now = System.currentTimeMillis();
            final long expiresAt = calcExpires(now, durationType, durationValue);

            JSONObject membership = membershipRepository.getByUserId(userId);
            if (Objects.isNull(membership)) {
                membership = new JSONObject();
                membership.put(Membership.USER_ID, userId);
                membership.put(Membership.CREATED_AT, now);
                membership.put(Membership.CONFIG_JSON, configJson);
            } else {
                if (membership.getLong(Membership.EXPIRES_AT) > now) {
                    throw new ServiceException("已经是会员了, 等待会员周期结束");
                }
                membership.put(Membership.CONFIG_JSON, "");
            }
            // 更新会员等级
            membership.put(Membership.LV_CODE, lvCode);
            // 优惠券校验, 如果有优惠券代码, 计算优惠价格.
            // 如果查出来没有结果或 times=0 就说明是假的/无效, 最后按 1.2 倍计算 (略施小惩)
            int finalPrice = price;
            if (StringUtils.isNotBlank(couponCode)) {
                try {
                    final JSONObject coupon = couponRepository.getByCode(couponCode);
                    final int couponType = (null == coupon) ? -1
                            : coupon.optInt(Coupon.COUPON_TYPE, COUPON_TYPE_DISCOUNT);
                    // 检查 coupon 是否存在且类型为折扣；否则惩罚
                    if (null == coupon || couponType != COUPON_TYPE_DISCOUNT) {
                        // 券不存在或不是折扣类型：按惩罚倍率处罚
                        finalPrice = (int) Math.round(price * INVALID_COUPON_PENALTY_RATE);
                    } else {
                        final int times = coupon.optInt(Coupon.TIMES);
                        if (times == 0) {
                            // 券次数为 0（不可用）：不惩罚、按原价结算
                            finalPrice = price;
                        } else {
                            final int discount = coupon.optInt(Coupon.DISCOUNT, 100);
                            finalPrice = (int) Math.round(price * (discount / 100.0d));
                            // 消耗一次（仅当 times > 0），-1 表示不限次不消耗
                            if (times > 0) {
                                coupon.put(Coupon.TIMES, times - 1);
                                coupon.put(Coupon.UPDATED_AT, now);
                                couponRepository.update(coupon.optString(Keys.OBJECT_ID), coupon);
                            }
                        }
                    }
                } catch (final RepositoryException ignore) {
                    // 更新失败? 那就原价吧
                    finalPrice = price;
                }
            }
            final String memo = "开通 " + level.optString(MembershipLevel.LV_NAME) + "("
                    + level.optString(MembershipLevel.DURATION_TYPE) + ") 会员\n" +
                    "原价：" + price + "(" +
                    "优惠价：" + finalPrice + ")";
            // 扣积分（余额不足则失败），参与当前事务
            final String transferId = pointtransferMgmtService.transferInCurrentTransaction(
                    userId,
                    Pointtransfer.ID_C_SYS,
                    Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT,
                    finalPrice,
                    memo,
                    now,
                    "");
            if (null == transferId) {
                throw new ServiceException("当前积分不足, 少年需要继续努力");
            }

            membership.put(Membership.STATE, 1);
            membership.put(Membership.EXPIRES_AT, expiresAt);
            membership.put(Membership.UPDATED_AT, now);

            if (StringUtils.isBlank(membership.optString(Keys.OBJECT_ID))) {
                membership.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
                membershipRepository.add(membership);
            } else {
                membershipRepository.update(membership.optString(Keys.OBJECT_ID), membership);
            }

            final JSONObject activation = new JSONObject();
            activation.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
            activation.put(MembershipActivation.USER_ID, userId);
            activation.put(MembershipActivation.LV_CODE, lvCode);
            activation.put(MembershipActivation.PRICE, finalPrice);
            activation.put(MembershipActivation.DURATION_TYPE, durationType);
            activation.put(MembershipActivation.DURATION_VALUE, durationValue);
            activation.put(MembershipActivation.COUPON_CODE, couponCode);
            activation.put(MembershipActivation.CONFIG_JSON, configJson);
            activation.put(MembershipActivation.CREATED_AT, now);
            activation.put(MembershipActivation.UPDATED_AT, now);
            activationRepository.add(activation);

            transaction.commit();

            // Update cache with the latest active membership
            membershipCache.put(membership);

            grantVipStarterBag(userId, lvCode);

            final JSONObject ret = new JSONObject();
            ret.put("membership", membership);
            ret.put("activation", activation);
            return ret;
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Open membership failed", e);
            throw new ServiceException(e);
        }
    }

    private long calcExpires(final long start, final String durationType, final int durationValue) {
        // 统一从“次日 0 点”起算
        final long nextDayStart = Times.getDayStartTime(start) + 24L * 60L * 60L * 1000L;
        final long days = durationValue;
        return nextDayStart + days * 24L * 60L * 60L * 1000L;
    }

    /**
     * 管理侧：分页查询会员记录（含用户名）。
     */
    public JSONObject adminListMemberships(final int page,
                                           final int pageSize,
                                           final String userName,
                                           final String lvCode,
                                           final Integer state) throws ServiceException {
        try {
            final int safePage = Math.max(page, 1);
            final int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
            final Query query = new Query()
                    .addSort(Membership.UPDATED_AT, SortDirection.DESCENDING)
                    .addSort(Keys.OBJECT_ID, SortDirection.DESCENDING)
                    .setPageCount(1)
                    .setPage(safePage, safePageSize);

            PropertyFilter userFilter = null;
            if (StringUtils.isNotBlank(userName)) {
                final JSONObject user = userRepository.getByName(userName.trim());
                if (null == user) {
                    return new JSONObject()
                            .put("total", 0)
                            .put("page", safePage)
                            .put("pageSize", safePageSize)
                            .put("items", new JSONArray());
                }
                userFilter = new PropertyFilter(Membership.USER_ID, FilterOperator.EQUAL, user.optString(Keys.OBJECT_ID));
            }

            final PropertyFilter lvCodeFilter = StringUtils.isBlank(lvCode)
                    ? null
                    : new PropertyFilter(Membership.LV_CODE, FilterOperator.EQUAL, lvCode.trim());
            final PropertyFilter stateFilter = null == state
                    ? null
                    : new PropertyFilter(Membership.STATE, FilterOperator.EQUAL, state);

            if (null != userFilter && null != lvCodeFilter && null != stateFilter) {
                query.setFilter(CompositeFilterOperator.and(userFilter, lvCodeFilter, stateFilter));
            } else if (null != userFilter && null != lvCodeFilter) {
                query.setFilter(CompositeFilterOperator.and(userFilter, lvCodeFilter));
            } else if (null != userFilter && null != stateFilter) {
                query.setFilter(CompositeFilterOperator.and(userFilter, stateFilter));
            } else if (null != lvCodeFilter && null != stateFilter) {
                query.setFilter(CompositeFilterOperator.and(lvCodeFilter, stateFilter));
            } else if (null != userFilter) {
                query.setFilter(userFilter);
            } else if (null != lvCodeFilter) {
                query.setFilter(lvCodeFilter);
            } else if (null != stateFilter) {
                query.setFilter(stateFilter);
            }

            final long total = membershipRepository.count(query);
            final List<JSONObject> memberships = membershipRepository.getList(query);
            final JSONArray items = new JSONArray();
            final long now = System.currentTimeMillis();
            for (final JSONObject membership : memberships) {
                final JSONObject item = new JSONObject(membership.toString());
                final String uid = item.optString(Membership.USER_ID);
                final JSONObject user = userRepository.get(uid);
                if (null != user) {
                    item.put(User.USER_NAME, user.optString(User.USER_NAME));
                }
                final long expiresAt = item.optLong(Membership.EXPIRES_AT, 0L);
                final int rowState = item.optInt(Membership.STATE, 0);
                if (1 == rowState && expiresAt > 0L && expiresAt <= now) {
                    item.put("runtimeState", "expired");
                } else if (1 == rowState) {
                    item.put("runtimeState", "active");
                } else {
                    item.put("runtimeState", "inactive");
                }
                items.put(item);
            }

            return new JSONObject()
                    .put("total", total)
                    .put("page", safePage)
                    .put("pageSize", safePageSize)
                    .put("items", items);
        } catch (final RepositoryException e) {
            LOGGER.error("Admin list memberships failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * 管理侧：免费新增会员（不扣积分）。
     */
    public JSONObject adminAddMembershipNoCost(final String userId,
                                               final String levelOId,
                                               final String configJson) throws ServiceException {
        final Transaction transaction = membershipRepository.beginTransaction();
        try {
            if (StringUtils.isBlank(userId) || StringUtils.isBlank(levelOId)) {
                throw new ServiceException("参数不完整");
            }

            final JSONObject user = userRepository.get(userId);
            if (null == user) {
                throw new ServiceException("用户不存在");
            }

            final JSONObject level = levelRepository.getById(levelOId);
            if (null == level) {
                throw new ServiceException("等级不存在");
            }

            final long now = System.currentTimeMillis();
            final String lvCode = level.optString(MembershipLevel.LV_CODE);
            final String durationType = level.optString(MembershipLevel.DURATION_TYPE);
            final int durationValue = level.optInt(MembershipLevel.DURATION_VALUE);
            if (durationValue <= 0) {
                throw new ServiceException("等级周期配置非法");
            }

            JSONObject membership = membershipRepository.getByUserId(userId);
            final boolean isNewMembership = Objects.isNull(membership);
            if (isNewMembership) {
                membership = new JSONObject();
                membership.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
                membership.put(Membership.USER_ID, userId);
                membership.put(Membership.CREATED_AT, now);
            } else {
                final int state = membership.optInt(Membership.STATE, 0);
                final long expiresAt = membership.optLong(Membership.EXPIRES_AT, 0L);
                if (state == 1 && (expiresAt == 0L || expiresAt > now)) {
                    throw new ServiceException("该用户已存在有效VIP");
                }
            }

            final long expiresAt = calcExpires(now, durationType, durationValue);
            membership.put(Membership.LV_CODE, lvCode);
            membership.put(Membership.STATE, 1);
            membership.put(Membership.EXPIRES_AT, expiresAt);
            membership.put(Membership.CONFIG_JSON, StringUtils.defaultString(configJson));
            membership.put(Membership.UPDATED_AT, now);

            if (isNewMembership) {
                membershipRepository.add(membership);
            } else {
                membershipRepository.update(membership.optString(Keys.OBJECT_ID), membership);
            }

            final JSONObject activation = new JSONObject();
            activation.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
            activation.put(MembershipActivation.USER_ID, userId);
            activation.put(MembershipActivation.LV_CODE, lvCode);
            activation.put(MembershipActivation.PRICE, 0);
            activation.put(MembershipActivation.DURATION_TYPE, durationType);
            activation.put(MembershipActivation.DURATION_VALUE, durationValue);
            activation.put(MembershipActivation.COUPON_CODE, "ADMIN_FREE");
            activation.put(MembershipActivation.CONFIG_JSON, StringUtils.defaultString(configJson));
            activation.put(MembershipActivation.CREATED_AT, now);
            activation.put(MembershipActivation.UPDATED_AT, now);
            activationRepository.add(activation);

            transaction.commit();
            membershipCache.put(membership);
            grantVipStarterBag(userId, lvCode);

            return new JSONObject()
                    .put("membership", membership)
                    .put("activation", activation);
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Admin add membership without points failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * 管理侧：维护会员信息。
     */
    public JSONObject adminUpdateMembership(final String userId,
                                            final String lvCode,
                                            final Integer state,
                                            final Long expiresAt,
                                            final String configJson) throws ServiceException {
        final Transaction transaction = membershipRepository.beginTransaction();
        try {
            if (StringUtils.isBlank(userId)) {
                throw new ServiceException("参数错误：userId 不能为空");
            }

            final JSONObject membership = membershipRepository.getByUserId(userId);
            if (null == membership) {
                throw new ServiceException("会员记录不存在");
            }

            if (StringUtils.isNotBlank(lvCode)) {
                final Query levelQuery = new Query()
                        .setFilter(new PropertyFilter(MembershipLevel.LV_CODE, FilterOperator.EQUAL, lvCode.trim()))
                        .setPageCount(1)
                        .setPage(1, 1);
                final JSONObject level = levelRepository.getFirst(levelQuery);
                if (null == level) {
                    throw new ServiceException("会员等级代码不存在");
                }
                membership.put(Membership.LV_CODE, lvCode.trim());
            }

            if (null != state) {
                membership.put(Membership.STATE, state);
            }
            if (null != expiresAt) {
                membership.put(Membership.EXPIRES_AT, expiresAt);
            }
            if (null != configJson) {
                membership.put(Membership.CONFIG_JSON, configJson);
            }
            membership.put(Membership.UPDATED_AT, System.currentTimeMillis());

            membershipRepository.update(membership.optString(Keys.OBJECT_ID), membership);
            transaction.commit();

            final int currentState = membership.optInt(Membership.STATE, 0);
            final long currentExpiresAt = membership.optLong(Membership.EXPIRES_AT, 0L);
            final long now = System.currentTimeMillis();
            if (1 == currentState && (0L == currentExpiresAt || currentExpiresAt > now)) {
                membershipCache.put(membership);
            } else {
                membershipCache.remove(userId);
            }

            return membership;
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Admin update membership failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * 管理侧：延长会员到期时间（按天）。
     */
    public JSONObject adminExtendMembershipDays(final String userId, final int days) throws ServiceException {
        final Transaction transaction = membershipRepository.beginTransaction();
        try {
            if (StringUtils.isBlank(userId)) {
                throw new ServiceException("参数错误：userId 不能为空");
            }
            if (days <= 0) {
                throw new ServiceException("参数错误：days 必须大于 0");
            }
            if (days > 36500) {
                throw new ServiceException("参数错误：days 过大");
            }

            final JSONObject membership = membershipRepository.getByUserId(userId);
            if (null == membership) {
                throw new ServiceException("会员记录不存在");
            }

            final long now = System.currentTimeMillis();
            final long oldExpiresAt = membership.optLong(Membership.EXPIRES_AT, 0L);
            if (oldExpiresAt <= 0L) {
                throw new ServiceException("永久会员不支持延长操作");
            }

            final long dayMillis = 24L * 60L * 60L * 1000L;
            final long base = Math.max(oldExpiresAt, now);
            final long newExpiresAt = base + days * dayMillis;

            membership.put(Membership.EXPIRES_AT, newExpiresAt);
            membership.put(Membership.STATE, 1);
            membership.put(Membership.UPDATED_AT, now);
            membershipRepository.update(membership.optString(Keys.OBJECT_ID), membership);

            transaction.commit();
            membershipCache.put(membership);

            return new JSONObject()
                    .put("userId", userId)
                    .put("days", days)
                    .put("oldExpiresAt", oldExpiresAt)
                    .put("newExpiresAt", newExpiresAt)
                    .put("membership", membership);
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Admin extend membership days failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * 管理侧：按天退款并使会员失效。
     */
    public JSONObject adminRefundMembershipByDays(final String userId) throws ServiceException {
        final Transaction transaction = membershipRepository.beginTransaction();
        try {
            if (StringUtils.isBlank(userId)) {
                throw new ServiceException("参数错误：userId 不能为空");
            }

            final JSONObject membership = membershipRepository.getActiveByUserId(userId);
            if (null == membership) {
                throw new ServiceException("当前无有效VIP可退款");
            }

            final long now = System.currentTimeMillis();
            final long expiresAt = membership.optLong(Membership.EXPIRES_AT, 0L);
            if (expiresAt <= 0L) {
                throw new ServiceException("永久会员不支持按天退款");
            }
            if (expiresAt <= now) {
                throw new ServiceException("VIP已过期，无法退款");
            }

            final String lvCode = membership.optString(Membership.LV_CODE);
            final Query activationQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter(MembershipActivation.USER_ID, FilterOperator.EQUAL, userId),
                            new PropertyFilter(MembershipActivation.LV_CODE, FilterOperator.EQUAL, lvCode)
                    ))
                    .addSort(MembershipActivation.CREATED_AT, SortDirection.DESCENDING)
                    .setPageCount(1)
                    .setPage(1, 1);
            final JSONObject activation = activationRepository.getFirst(activationQuery);
            if (null == activation) {
                throw new ServiceException("未找到对应的开通记录，无法按天退款");
            }

            final int paidPoints = activation.optInt(MembershipActivation.PRICE, 0);
            final int totalDays = activation.optInt(MembershipActivation.DURATION_VALUE, 0);
            if (totalDays <= 0) {
                throw new ServiceException("开通记录周期非法，无法退款");
            }

            final long dayMillis = 24L * 60L * 60L * 1000L;
            final int remainingDays = (int) Math.max(0L, (expiresAt - now) / dayMillis);
            final int refundPoints = (int) Math.floor((paidPoints * 1.0d / totalDays) * remainingDays);
            String transferId = "";

            if (refundPoints > 0) {
                final String memo = "VIP按天退款（" + remainingDays + "天）";
                transferId = pointtransferMgmtService.transferInCurrentTransaction(
                        Pointtransfer.ID_C_SYS,
                        userId,
                        Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT,
                        refundPoints,
                        membership.optString(Keys.OBJECT_ID),
                        now,
                        memo
                );
                if (null == transferId) {
                    throw new ServiceException("退款失败，积分转账未完成");
                }
            }

            membership.put(Membership.STATE, 0);
            membership.put(Membership.EXPIRES_AT, now);
            membership.put(Membership.UPDATED_AT, now);
            membershipRepository.update(membership.optString(Keys.OBJECT_ID), membership);

            transaction.commit();
            membershipCache.remove(userId);

            if (StringUtils.isNotBlank(transferId)) {
                try {
                    final JSONObject notification = new JSONObject();
                    notification.put(Notification.NOTIFICATION_USER_ID, userId);
                    notification.put(Notification.NOTIFICATION_DATA_ID, transferId);
                    notificationMgmtService.addPointTransferNotification(notification);
                } catch (final Exception notificationException) {
                    LOGGER.warn("Add refund transfer notification failed [userId=" + userId + ", transferId=" + transferId + "]", notificationException);
                }
            }

            return new JSONObject()
                    .put("userId", userId)
                    .put("refundPoints", refundPoints)
                    .put("remainingDays", remainingDays)
                    .put("totalDays", totalDays)
                    .put("paidPoints", paidPoints)
                    .put("membership", membership);
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Admin refund membership by days failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * 开通会员后的赠送权益。
     */
    private void grantVipStarterBag(final String userId, final String lvCode) {
        switch (lvCode) {
            case "VIP1_YEAR":
                cloudService.putBag(userId, "checkin1day", 20, Integer.MAX_VALUE);
                break;
            case "VIP2_YEAR":
                cloudService.putBag(userId, "checkin1day", 50, Integer.MAX_VALUE);
                break;
            case "VIP3_YEAR":
                cloudService.putBag(userId, "checkin1day", 120, Integer.MAX_VALUE);
                break;
            case "VIP4_YEAR":
                cloudService.putBag(userId, "sysCheckinRemain", 366, Integer.MAX_VALUE);
                break;
            default:
                break;
        }
    }

    /**
     * 更新用户会员配置（仅更新当前激活会员的 configJson）。
     * 需要校验：用户有激活会员、未过期；configJson 与当前等级 benefits 模板匹配（键集合为模板子集且类型一致）。
     */
    public JSONObject updateUserConfig(final String userId, final String configJson) throws ServiceException {
        if (StringUtils.isBlank(userId)) {
            throw new ServiceException("未登录");
        }
        if (StringUtils.isBlank(configJson)) {
            throw new ServiceException("参数错误：configJson 不能为空");
        }
        try {
            final long now = System.currentTimeMillis();
            final JSONObject membership = membershipRepository.getActiveByUserId(userId);
            if (null == membership) {
                throw new ServiceException("未开通会员或未激活");
            }
            final long expiresAt = membership.optLong(Membership.EXPIRES_AT, 0L);
            if (expiresAt != 0L && expiresAt <= now) {
                throw new ServiceException("会员已过期");
            }

            final String lvCode = membership.optString(Membership.LV_CODE);
            // 检查是否为 VIP4 等级
            final boolean isV4 = lvCode.contains("VIP4");
            // 通过 lvCode 获取一个等级定义（不依赖 durationType）
            final Query query = new Query()
                    .setFilter(new PropertyFilter(MembershipLevel.LV_CODE, FilterOperator.EQUAL, lvCode))
                    .setPageCount(1).setPage(1, 1);
            final JSONObject level = levelRepository.getFirst(query);
            if (null == level) {
                throw new ServiceException("会员等级定义不存在");
            }

            final String benefitsTemplateStr = level.optString(MembershipLevel.BENEFITS);
            JSONObject benefitsTemplate;
            try {
                benefitsTemplate = StringUtils.isBlank(benefitsTemplateStr) ? new JSONObject()
                        : new JSONObject(benefitsTemplateStr);
            } catch (final Exception e) {
                throw new ServiceException("等级配置模板非法");
            }

            JSONObject userConfig;
            try {
                userConfig = new JSONObject(configJson);
            } catch (final Exception e) {
                throw new ServiceException("configJson 非法 JSON");
            }

            // 1) 用户配置项必须在模板内（不允许额外键），且类型匹配（与模板）
            for (final String key : userConfig.keySet()) {
                if (!benefitsTemplate.has(key)) {
                    throw new ServiceException("不允许的配置项: " + key);
                }
                final Object tplVal = benefitsTemplate.opt(key);
                final Object usrVal = userConfig.opt(key);
                if (tplVal != null && usrVal != null) {
                    final Class<?> tCls = tplVal.getClass();
                    final Class<?> uCls = usrVal.getClass();
                    final boolean bothNumber = (tplVal instanceof Number) && (usrVal instanceof Number);
                    if (!bothNumber && !tCls.equals(uCls)) {
                        throw new ServiceException("配置项类型不匹配: " + key);
                    }
                }
            }

            // 2) 严格校验：模板中的所有键都必须在用户配置中出现，且值不能为空
            for (final String key : benefitsTemplate.keySet()) {
                if (!userConfig.has(key)) {
                    throw new ServiceException("缺少配置项: " + key);
                }
                final Object usrVal = userConfig.opt(key);
                if (isEmptyValue(usrVal)) {
                    throw new ServiceException("配置项不能为空: " + key);
                }
                final Object tplVal = benefitsTemplate.opt(key);
                if (tplVal != null && usrVal != null) {
                    final Class<?> tCls = tplVal.getClass();
                    final Class<?> uCls = usrVal.getClass();
                    final boolean bothNumber = (tplVal instanceof Number) && (usrVal instanceof Number);
                    if (!bothNumber && !tCls.equals(uCls)) {
                        throw new ServiceException("配置项类型不匹配: " + key);
                    }
                }
                // 不是V4等级, 颜色配置只允许#000000到#ffffff格式
                if (!isV4 && "color".equals(key)) {
                    final String color = userConfig.optString(key);
                    if (!StringUtils.isBlank(color) && !color.matches("^#[0-9a-fA-F]{6}$")) {
                        throw new ServiceException("颜色配置格式错误: " + color);
                    }
                }
            }

            // 更新配置
            membership.put(Membership.CONFIG_JSON, userConfig.toString());
            membership.put(Membership.UPDATED_AT, now);
            membershipRepository.update(membership.optString(Keys.OBJECT_ID), membership);
            // Update cache after config change
            membershipCache.put(membership);
            return membership;
        } catch (final RepositoryException e) {
            LOGGER.error("Update membership config failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * 判断值是否为空：null、空字符串、空对象或空数组视为为空。
     */
    private boolean isEmptyValue(final Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String) {
            return StringUtils.isBlank((String) v);
        }
        if (v instanceof JSONObject) {
            return ((JSONObject) v).length() == 0;
        }
        if (v instanceof JSONArray) {
            return ((JSONArray) v).length() == 0;
        }
        // 数字、布尔类型不作“空”校验
        return false;
    }
}
