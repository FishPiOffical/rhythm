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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Sponsor;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.SponsorRepository;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author fangcong
 * @version 0.0.1
 * @since Created by work on 2022-02-12 22:58
 **/
@Service
public class SponsorService {

    private static final Logger LOGGER = LogManager.getLogger(SponsorService.class);

    @Inject
    private SponsorRepository repository;

    @Inject
    private UserQueryService userQueryService;

    public void add(final JSONObject record) {
        final Transaction transaction = repository.beginTransaction();
        try {
            recordInCurrentTransaction(record);
            transaction.commit();
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.warn("add sponsor record failed !");
        }
    }

    public void recordInCurrentTransaction(final JSONObject record) throws RepositoryException {
        if (!repository.hasTransactionBegun()) throw new IllegalStateException("赞助记录需要活动事务");
        repository.add(record);
    }

    public static JSONObject paymentRecord(final String userId, final double amount, final String memo, final long time) {
        return new JSONObject().put(UserExt.USER_T_ID, userId).put(Common.TIME, time)
                .put(Sponsor.AMOUNT, amount).put(Sponsor.SPONSOR_MESSAGE, memo);
    }

    public static int pointsForPayment(final double amount) {
        final int points = ((int) amount) * 80;
        return points == 0 ? 1 : points;
    }

    public List<JSONObject> list(int page, int limit) {
        try {
            final List<JSONObject> list = repository.list(page, limit);
            return list.stream().peek(x -> {
                x.put(User.USER_NAME, userQueryService.getUserName(x.optString(UserExt.USER_T_ID)));
                x.put(Common.DATE, new SimpleDateFormat("yyyy-MM-dd").format(x.optLong(Common.TIME)));
                x.put(Common.TIME, new SimpleDateFormat("HH:mm:ss").format(x.optLong(Common.TIME)));
                x.remove(UserExt.USER_T_ID);
            }).collect(Collectors.toList());
        } catch (RepositoryException e) {
            LOGGER.warn("add sponsor record list failed !");
            return Collections.emptyList();
        }
    }

    public double getSum(String userId) {
        try {
            List<JSONObject> data = repository.listByUserId(userId);
            double sum = 0;
            for (JSONObject i : data) {
                sum += i.optDouble("amount");
            }
            return sum;
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Cannot get user sum", e);
            return 0;
        }
    }
}
