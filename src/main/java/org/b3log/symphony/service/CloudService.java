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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.*;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.util.Dates;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    final public static String SYS_MEDAL = "sys-medal";
    final public static String SYS_MUTE = "sys-mute";
    final public static String SYS_RISK = "sys-risk";

    /**
     * 上传存档
     *
     * @param userId
     * @param gameId
     * @param data
     * @return
     */
    synchronized public void sync(final String userId, final String gameId, final JSONObject data) throws ServiceException {
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
            JSONObject stats = data.optJSONObject("stats");
            if (stats == null) {
                return;
            }
            JSONObject top = new JSONObject();
            top.put("achievement", stats.optJSONObject("achieve").toMap().size());
            top.put("know", stats.optLong("know") + stats.optLong("tknow"));
            top.put("days", stats.optLong("days") + stats.optLong("tdays"));
            top.put("reset", stats.optLong("reset"));
            data.put("top", top);
            // 上传新存档
            JSONObject cloudJSON = new JSONObject();
            cloudJSON.put("userId", userId)
                    .put("gameId", gameId)
                    .put("data", data.toString());
            cloudRepository.add(cloudJSON);
            transaction.commit();
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Cannot upload gaming save data to database.", e);

            throw new ServiceException("Failed to upload game save");
        }
    }

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
     * 彻底删除背包中的某个物品
     *
     * @param userId
     * @param item
     * @return
     */
    synchronized public void removeBag(String userId, String item) {
        JSONObject bagJSON = new JSONObject(getBag(userId));
        if (!bagJSON.has(item)) {
            return;
        }
        bagJSON.remove(item);
        saveBag(userId, bagJSON.toString());
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

    synchronized public void saveMetal(String userId, String data) {
        try {
            final Transaction transaction = cloudRepository.beginTransaction();
            Query cloudDeleteQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_MEDAL)
                    ));
            cloudRepository.remove(cloudDeleteQuery);
            JSONObject cloudJSON = new JSONObject();
            cloudJSON.put("userId", userId)
                    .put("gameId", CloudService.SYS_MEDAL)
                    .put("data", data);
            cloudRepository.add(cloudJSON);
            transaction.commit();
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Cannot save metal data to database.", e);
        }
    }

    synchronized public String getMetal(String userId) {
        try {
            Query cloudQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_MEDAL)
                    ));
            JSONObject result = cloudRepository.getFirst(cloudQuery);
            JSONObject object1 = new JSONObject(result.optString("data"));
            JSONArray object2 = object1.optJSONArray("list");
            boolean hasExpired = false;
            for (int i = object2.length() - 1; i >= 0; i--) {
                JSONObject object3 = object2.optJSONObject(i);
                // 如果勋章过期日期在今天之前, 移除
                if (Dates.isExpired(object3.optString("expireDate", "2099-12-31"), Dates.PATTERN_DATE)) {
                    object2.remove(i);
                    hasExpired = true;
                }
            }
            object1.put("list", object2);
            // 如果有过期勋章被移除，更新数据库
            if (hasExpired) {
                saveMetal(userId, object1.toString());
            }
            return object1.toString();
        } catch (Exception e) {
            return new JSONObject().toString();
        }
    }

    synchronized public String getEnabledMetal(String userId) {
        try {
            Query cloudQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_MEDAL)
                    ));
            JSONObject result = cloudRepository.getFirst(cloudQuery);
            JSONObject object1 = new JSONObject(result.optString("data"));
            JSONArray object2 = object1.optJSONArray("list");
            boolean hasExpired = false;
            for (int i = object2.length() - 1; i >= 0; i--) {
                JSONObject object3 = object2.optJSONObject(i);
                if (!object3.optBoolean("enabled")) {
                    object2.remove(i);
                    continue;
                }
                // 如果勋章过期日期在今天之前, 移除
                if (Dates.isExpired(object3.optString("expireDate", "2099-12-31"), Dates.PATTERN_DATE)) {
                    object2.remove(i);
                    hasExpired = true;
                }
            }
            object1.put("list", object2);
            // 如果有过期勋章被移除，更新数据库
            if (hasExpired) {
                saveMetal(userId, object1.toString());
            }
            return object1.toString();
        } catch (Exception e) {
            return new JSONObject().toString();
        }
    }

    synchronized public void giveMetal(String userId, String name, String description, String attr, String data) {
        giveMetal(userId, name, description, attr, data, "2099-12-31");
    }

    synchronized public void giveMetal(String userId, String name, String description, String attr, String data, String expireDate) {
        JSONObject metal = new JSONObject(getMetal(userId));
        if (!metal.has("list")) {
            metal.put("list", new JSONArray());
        }
        JSONArray list = metal.optJSONArray("list");
        for (int i = 0; i < list.length(); i++) {
            JSONObject jsonObject = list.optJSONObject(i);
            if (jsonObject.optString("name").equals(name)) {
                return;
            }
        }
        JSONObject newMetal = new JSONObject()
                .put("name", name)
                .put("description", description)
                .put("attr", attr)
                .put("data", data)
                .put("enabled", true);
        if (StringUtils.isNotBlank(expireDate)) {
            newMetal.put("expireDate", expireDate);
        }
        list.put(newMetal);
        metal.put("list", list);
        saveMetal(userId, metal.toString());
    }

    synchronized public void removeMetal(String userId, String name) {
        try {
            Query cloudQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_MEDAL)
                    ));
            JSONObject result = cloudRepository.getFirst(cloudQuery);
            String dataStr = result.optString("data");
            JSONObject metal = new JSONObject(dataStr);
            if (!metal.has("list")) {
                metal.put("list", new JSONArray());
            }
            JSONArray list = metal.optJSONArray("list");
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonObject = list.optJSONObject(i);
                if (jsonObject.optString("name").equals(name)) {
                    list.remove(i);
                }
            }
            metal.put("list", list);
            saveMetal(userId, metal.toString());
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to remove medal [" + name + "] for user [" + userId + "]", e);
        }
    }

    synchronized public void toggleMetal(String userId, String name, boolean enabled) {
        try {
            Query cloudQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_MEDAL)
                    ));
            JSONObject result = cloudRepository.getFirst(cloudQuery);
            String dataStr = result.optString("data");
            JSONObject metal = new JSONObject(dataStr);
            if (!metal.has("list")) {
                metal.put("list", new JSONArray());
            }
            JSONArray list = metal.optJSONArray("list");
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonObject = list.optJSONObject(i);
                if (jsonObject.optString("name").equals(name)) {
                    list.remove(i);
                    jsonObject.put("enabled", enabled);
                    list.put(jsonObject);
                }
                // 如果勋章过期日期在今天之前, 移除
                if (Dates.isExpired(jsonObject.optString("expireDate", "2099-12-31"), Dates.PATTERN_DATE)) {
                    list.remove(i);
                    // Skip the recursive call to removeMetal, just continue the loop
                }
            }
            metal.put("list", list);
            saveMetal(userId, metal.toString());
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to toggle medal [" + name + "] for user [" + userId + "]", e);
        }
    }
}
