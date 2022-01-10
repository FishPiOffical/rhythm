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

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Transactional;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Stopwatchs;
import org.b3log.symphony.model.Liveness;
import org.b3log.symphony.processor.UserProcessor;
import org.b3log.symphony.repository.LivenessRepository;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Liveness management service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.1, Jun 12, 2018
 * @since 1.4.0
 */
@Service
public class LivenessMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(LivenessMgmtService.class);

    /**
     * Liveness repository.
     */
    @Inject
    private LivenessRepository livenessRepository;

    /**
     * Activity query service.
     */
    @Inject
    private ActivityQueryService activityQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Liveness query service.
     */
    @Inject
    private LivenessQueryService livenessQueryService;

    /**
     * Increments a field of the specified liveness.
     *
     * @param userId the specified user id
     * @param field  the specified field
     */
    @Transactional
    public void incLiveness(final String userId, final String field) {
        Stopwatchs.start("Inc liveness");
        final String date = DateFormatUtils.format(System.currentTimeMillis(), "yyyyMMdd");

        try {
            JSONObject liveness = livenessRepository.getByUserAndDate(userId, date);
            if (null == liveness) {
                liveness = new JSONObject();

                liveness.put(Liveness.LIVENESS_USER_ID, userId);
                liveness.put(Liveness.LIVENESS_DATE, date);
                liveness.put(Liveness.LIVENESS_POINT, 0);
                liveness.put(Liveness.LIVENESS_ACTIVITY, 0);
                liveness.put(Liveness.LIVENESS_ARTICLE, 0);
                liveness.put(Liveness.LIVENESS_COMMENT, 0);
                liveness.put(Liveness.LIVENESS_PV, 0);
                liveness.put(Liveness.LIVENESS_REWARD, 0);
                liveness.put(Liveness.LIVENESS_THANK, 0);
                liveness.put(Liveness.LIVENESS_VOTE, 0);
                liveness.put(Liveness.LIVENESS_VOTE, 0);
                liveness.put(Liveness.LIVENESS_ACCEPT_ANSWER, 0);

                livenessRepository.add(liveness);
            }

            liveness.put(field, liveness.optInt(field) + 1);

            final int livenessMax = Symphonys.ACTIVITY_YESTERDAY_REWARD_MAX;
            final int currentLiveness = Liveness.calcPoint(liveness);
            float livenessPercent = (float) (Math.round((float) currentLiveness / livenessMax * 100 * 100)) / 100;
            UserProcessor.livenessCache.put(userId, livenessPercent);

            livenessRepository.update(liveness.optString(Keys.OBJECT_ID), liveness);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Updates a liveness [" + date + "] field [" + field + "] failed", e);
        } finally {
            Stopwatchs.end();
        }
    }

    private static Map<String, String> gave2dayCards = new HashMap<>();
    public void checkLiveness() {
        final BeanManager beanManager = BeanManager.getInstance();
        final ActivityMgmtService activityMgmtService = beanManager.getReference(ActivityMgmtService.class);
        final CloudService cloudService = beanManager.getReference(CloudService.class);
        final String date = DateFormatUtils.format(System.currentTimeMillis(), "yyyyMMdd");
        try {
            List<JSONObject> userList = livenessRepository.getByDate(date);
            for (JSONObject i : userList) {
                String userId = i.optString(Liveness.LIVENESS_USER_ID);
                JSONObject user = userQueryService.getUser(userId);
                final int livenessMax = Symphonys.ACTIVITY_YESTERDAY_REWARD_MAX;
                final int currentLiveness = livenessQueryService.getCurrentLivenessPoint(userId);
                float liveness = (float) (Math.round((float) currentLiveness / livenessMax * 100 * 100)) / 100;
                if (!activityQueryService.isCheckedinToday(userId)) {
                    if (liveness >= 10) {
                        activityMgmtService.dailyCheckin(userId);
                        LOGGER.log(Level.INFO, "Checkin for " + user.optString(User.USER_NAME) + " liveness is " + liveness + "%");
                    }
                }
                if (liveness == 100) {
                    if (gave2dayCards.get(userId) == null || !gave2dayCards.get(userId).equals(date)) {
                        if (cloudService.putBag(userId, "checkin2days", 1, Integer.MAX_VALUE) == 0) {
                            LOGGER.log(Level.INFO, "Checkin card 2 days for " + user.optString(User.USER_NAME));
                        }
                        gave2dayCards.put(userId, date);
                    }
                }
            }
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Check liveness [" + date + "] failed", e);
        }
    }

    public void autoCheckin() {
        final String date = DateFormatUtils.format(System.currentTimeMillis(), "HHmm");
        int numDate = Integer.parseInt(date);
        if (numDate >= 0 && numDate <= 5) {
            // 自动签到
            final BeanManager beanManager = BeanManager.getInstance();
            final ActivityMgmtService activityMgmtService = beanManager.getReference(ActivityMgmtService.class);
            final CloudService cloudService = beanManager.getReference(CloudService.class);
            List<JSONObject> bags = cloudService.getBags();
            for (JSONObject i : bags) {
                JSONObject bag = new JSONObject(i.optString("data"));
                if (bag.has("sysCheckinRemain") && bag.optInt("sysCheckinRemain") > 0) {
                    String userId = i.optString("userId");
                    if (!activityQueryService.isCheckedinToday(userId)) {
                        activityMgmtService.dailyCheckin(userId);
                        cloudService.putBag(userId, "sysCheckinRemain", -1, Integer.MAX_VALUE);
                    }
                }
            }
        }
    }
}
