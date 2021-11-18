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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.*;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.CloudRepository;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@Service
public class CloudService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(CloudService.class);

    /**
     * Cloud repository.
     */
    @Inject
    private CloudRepository cloudRepository;

    final public static String SYS_BAG = "sys-bag";

    /**
     * 上传存档
     *
     * @param userId
     * @param gameId
     * @param data
     * @return
     */
    synchronized public void sync(final String userId, final String gameId, final String data) throws ServiceException {
        if (gameId.startsWith("sys-")) {
            return;
        }
        try {
            final Transaction transaction = cloudRepository.beginTransaction();
            // 删除旧存档
            Query cloudDeleteQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, gameId)
                    ));
            cloudRepository.remove(cloudDeleteQuery);
            // 上传新存档
            JSONObject cloudJSON = new JSONObject();
            cloudJSON.put("userId", userId)
                    .put("gameId", gameId)
                    .put("data", data);
            cloudRepository.add(cloudJSON);
            transaction.commit();
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Cannot upload gaming save data to database.", e);

            throw new ServiceException("Failed to upload game save");
        }
    }

    /**
     * 获取存档
     *
     * @param userId
     * @param gameId
     * @return
     */
    public String getFromCloud(final String userId, final String gameId) {
        try {
            Query cloudQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, gameId)
                    ));
            JSONObject result = cloudRepository.getFirst(cloudQuery);
            return result.optString("data");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 保存背包内容
     *
     * @param userId
     * @param data
     */
    synchronized public void saveBag(String userId, String data) {
        try {
            final Transaction transaction = cloudRepository.beginTransaction();
            Query cloudDeleteQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_BAG)
                    ));
            cloudRepository.remove(cloudDeleteQuery);
            JSONObject cloudJSON = new JSONObject();
            cloudJSON.put("userId", userId)
                    .put("gameId", CloudService.SYS_BAG)
                    .put("data", data);
            cloudRepository.add(cloudJSON);
            transaction.commit();
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Cannot save bag data to database.", e);
        }
    }

    /**
     * 读取背包内容
     *
     * @param userId
     * @return
     */
    synchronized public String getBag(String userId) {
        try {
            Query cloudQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_BAG)
                    ));
            JSONObject result = cloudRepository.getFirst(cloudQuery);
            return result.optString("data");
        } catch (Exception e) {
            return new JSONObject().toString();
        }
    }

    /**
     * 获得所有人的背包
     *
     * @return
     */
    synchronized public List<JSONObject> getBags() {
        try {
            Query cloudQuery = new Query()
                    .setFilter(
                            new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_BAG)
                    );
            return cloudRepository.getList(cloudQuery);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 向背包中取放东西
     *
     * @param userId
     * @param item 物品名称
     * @param number 正数为增加，负数为扣除
     * @param maxTake 最多可以拿几件这个物品
     * @return 操作成功返回0，当number传递的是负数且比背包中物品数量多时返回-1
     */
    synchronized public int putBag(String userId, String item, int number, int maxTake) {
        JSONObject bagJSON = new JSONObject(getBag(userId));
        if (!bagJSON.has(item)) {
            bagJSON.put(item, 0);
        }
        int has = bagJSON.getInt(item);
        int sum = has + number;
        if (number > 0) {
            // 增加
            if (sum > maxTake) {
                sum = maxTake;
                bagJSON.put(item, sum);
                saveBag(userId, bagJSON.toString());
                return 1;
            } else {
                bagJSON.put(item, sum);
                saveBag(userId, bagJSON.toString());
                return 0;
            }
        } else if (number < 0) {
            // 扣除
            if (sum >= 0) {
                bagJSON.put(item, sum);
                saveBag(userId, bagJSON.toString());
                return 0;
            } else {
                return -1;
            }
        }

        return -1;
    }
}
