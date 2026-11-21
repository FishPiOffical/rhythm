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

import org.apache.commons.lang3.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.Coupon;
import org.b3log.symphony.repository.CouponRepository;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

@Singleton
public class CouponMgmtService {

    @Inject
    private CouponRepository couponRepository;

    public List<JSONObject> list() throws ServiceException {
        try {
            final Query query = new Query().setPage(1, Integer.MAX_VALUE);
            return couponRepository.getList(query);
        } catch (RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    public JSONObject add(final String creater, final String couponCode, final Integer couponType, final Integer discount, final Integer times)
            throws ServiceException {
        final Transaction tx = ApiV3Transactions.strict(couponRepository.beginTransaction());
        try {
            JSONObject result = addInCurrentTransaction(creater, couponCode, couponType, discount, times);
            tx.commit();
            return result;
        } catch (RepositoryException e) {
            throw new ServiceException(e);
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    public JSONObject addInCurrentTransaction(final String creater, final String couponCode, final Integer couponType, final Integer discount, final Integer times)
            throws ServiceException, RepositoryException {
        if (!couponRepository.hasTransactionBegun()) throw new IllegalStateException("需要活动事务");
        final long now = System.currentTimeMillis();
        final JSONObject coupon = new JSONObject();

        // coupon_code: 如果没传则生成 UUID（去掉横线，32位）；如果传了需 8~32 长度
        String code = StringUtils.trimToNull(couponCode);
        if (null == code) {
            code = UUID.randomUUID().toString().replace("-", "");
        } else {
            if (code.length() < 8 || code.length() > 32) {
                throw new ServiceException("coupon_code 长度需在 8~32 之间");
            }
        }
        // 唯一性校验
        final JSONObject existed = couponRepository.getByCode(code);
        if (null != existed) {
            throw new ServiceException("coupon_code 已存在");
        }

        final int type = null == couponType ? 0 : couponType;
        if (null == discount || discount < 0) {
            throw new ServiceException("discount 需为 >= 0 的整数");
        }
        if (null == times) {
            throw new ServiceException("times 不能为空");
        }

        coupon.put(Coupon.COUPON_CODE, code);
        coupon.put(Coupon.COUPON_TYPE, type);
        coupon.put(Coupon.DISCOUNT, discount);
        coupon.put(Coupon.TIMES, times);
        coupon.put(Coupon.CREATER, creater);
        coupon.put(Coupon.CREATED_AT, now);
        coupon.put(Coupon.UPDATED_AT, now);

        final String id = couponRepository.add(coupon);
        coupon.put(Keys.OBJECT_ID, id);
        return coupon;
    }

    public void remove(final String oId) throws ServiceException {
        final Transaction tx = ApiV3Transactions.strict(couponRepository.beginTransaction());
        try {
            removeInCurrentTransaction(oId);
            tx.commit();
        } catch (RepositoryException e) {
            throw new ServiceException(e);
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    public void removeInCurrentTransaction(final String oId) throws ServiceException, RepositoryException {
        if (!couponRepository.hasTransactionBegun()) throw new IllegalStateException("需要活动事务");
        couponRepository.remove(oId);
    }

    public JSONObject updateTimes(final String oId, final Integer times) throws ServiceException {
        final Transaction tx = ApiV3Transactions.strict(couponRepository.beginTransaction());
        try {
            JSONObject result = updateTimesInCurrentTransaction(oId, times);
            tx.commit();
            return result;
        } catch (RepositoryException e) {
            throw new ServiceException(e);
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    public JSONObject updateTimesInCurrentTransaction(final String oId, final Integer times) throws ServiceException, RepositoryException {
        if (!couponRepository.hasTransactionBegun()) throw new IllegalStateException("需要活动事务");
        if (null == times) {
            throw new ServiceException("times 不能为空");
        }
        final JSONObject coupon = couponRepository.get(oId);
        if (null == coupon) {
            throw new ServiceException("优惠券不存在");
        }
        coupon.put(Coupon.TIMES, times);
        coupon.put(Coupon.UPDATED_AT, System.currentTimeMillis());
        couponRepository.update(oId, coupon);
        return coupon;
    }
}