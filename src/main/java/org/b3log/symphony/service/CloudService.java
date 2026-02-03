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
import org.b3log.symphony.service.MedalService;
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

    /**
     * Medal service.
     */
    @Inject
    private MedalService medalService;

    final public static String SYS_BAG = "sys-bag";
    final public static String SYS_MEDAL = "sys-medal";
    final public static String SYS_MUTE = "sys-mute";
    final public static String SYS_RISK = "sys-risk";
    /** AI 记忆存储 gameId. */
    public static final String AI_MEMORY = "ai_memory";
    /** 马库斯的全局记忆使用的特殊 userId. */
    public static final String AI_MEMORY_GLOBAL_UID = "13800138000";

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

    /**
     * 获取用户所有勋章（Cloud 风格 JSON 字符串，含未展示和过期）.
     * 返回结构保持不变：{"list":[{name,description,attr,data,enabled,expireDate}]}
     */
    synchronized public String getMedal(String userId) {
        try {
            List<JSONObject> medals = medalService.getUserMedals(userId);
            JSONArray list = new JSONArray();
            for (JSONObject medal : medals) {
                JSONObject item = new JSONObject();
                item.put("name", medal.optString("medal_name"));
                item.put("description", medal.optString("medal_description"));
                item.put("attr", medal.optString("medal_attr"));
                item.put("data", "");
                boolean display = medal.optBoolean("display", true);
                item.put("enabled", display);
                long expireTime = medal.optLong("expire_time", 0L);
                String expireDate = "2099-12-31";
                if (expireTime > 0L) {
                    expireDate = Dates.format(new java.util.Date(expireTime), Dates.PATTERN_DATE);
                }
                item.put("expireDate", expireDate);
                item.put("id", medal.optString("medal_id"));
                item.put("type", medal.optString("medal_type"));
                item.put("order", medal.optInt("display_order"));
                list.put(item);
            }
            JSONObject ret = new JSONObject();
            ret.put("list", list);
            return ret.toString();
        } catch (Exception e) {
            return new JSONObject().put("list", new JSONArray()).toString();
        }
    }

    /**
     * 获取用户所有“已开启展示且未过期”的勋章（Cloud 风格 JSON）.
     * 返回结构保持不变：{"list":[{name,description,attr,data,enabled,expireDate}]}
     */
    synchronized public String getEnabledMedal(String userId) {
        try {
            List<JSONObject> medals = medalService.getUserDisplayedValidMedals(userId);
            JSONArray list = new JSONArray();
            for (JSONObject medal : medals) {
                JSONObject item = new JSONObject();
                item.put("name", medal.optString("medal_name"));
                item.put("description", medal.optString("medal_description"));
                item.put("attr", medal.optString("medal_attr"));
                item.put("data", "");
                boolean display = medal.optBoolean("display", true);
                item.put("enabled", display);
                long expireTime = medal.optLong("expire_time", 0L);
                String expireDate = "2099-12-31";
                if (expireTime > 0L) {
                    expireDate = Dates.format(new java.util.Date(expireTime), Dates.PATTERN_DATE);
                }
                item.put("expireDate", expireDate);
                item.put("id", medal.optString("medal_id"));
                item.put("type", medal.optString("medal_type"));
                item.put("order", medal.optInt("display_order"));
                list.put(item);
            }
            JSONObject ret = new JSONObject();
            ret.put("list", list);
            return ret.toString();
        } catch (Exception e) {
            return new JSONObject().put("list", new JSONArray()).toString();
        }
    }

    synchronized public void giveMedal(String userId, String name, String description, String attr, String data) {
        giveMedal(userId, name, description, attr, data, "2099-12-31");
    }

    /**
     * 给用户发勋章（保持原方法签名，内部代理 MedalService）.
     */
    synchronized public void giveMedal(String userId, String name, String description, String attr, String data, String expireDate) {
        try {
            JSONObject medalDef = medalService.getMedalByExactName(name);
            if (medalDef == null) {
                medalService.addMedal(name, "普通", description, attr);
                medalDef = medalService.getMedalByExactName(name);
                if (medalDef == null) {
                    return;
                }
            }
            String medalId = medalDef.optString("medal_id");
            String date = StringUtils.isNotBlank(expireDate) ? expireDate : "2099-12-31";
            long expireTime = 0L;
            try {
                expireTime = Dates.parseOrNull(date, Dates.PATTERN_DATE).getTime();
            } catch (Exception ignore) {
            }
            medalService.grantMedalToUser(userId, medalId, expireTime, data);
        } catch (ServiceException e) {
            LOGGER.log(Level.ERROR, "Failed to give medal [" + name + "] to user [" + userId + "]", e);
        }
    }

    synchronized public void removeMedal(String userId, String name) {
        try {
            JSONObject medalDef = medalService.getMedalByExactName(name);
            if (medalDef == null) {
                return;
            }
            String medalId = medalDef.optString("medal_id");
            medalService.revokeMedalFromUser(userId, medalId);
        } catch (ServiceException e) {
            LOGGER.log(Level.ERROR, "Failed to remove medal [" + name + "] for user [" + userId + "]", e);
        }
    }

    synchronized public void toggleMedal(String userId, String name, boolean enabled) {
        try {
            JSONObject medalDef = medalService.getMedalByExactName(name);
            if (medalDef == null) {
                return;
            }
            String medalId = medalDef.optString("medal_id");
            medalService.setUserMedalDisplay(userId, medalId, enabled);
        } catch (ServiceException e) {
            LOGGER.log(Level.ERROR, "Failed to toggle medal [" + name + "] for user [" + userId + "]", e);
        }
    }

    /**
     * 获取指定用户的 AI 记忆（JSONArray 字符串存储，返回顺序为时间顺序，可能为空）.
     */
    synchronized public JSONArray getAiMemory(String userId) {
        try {
            Query cloudQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, AI_MEMORY)
                    ));
            JSONObject result = cloudRepository.getFirst(cloudQuery);
            if (result == null) {
                return new JSONArray();
            }
            String data = result.optString("data", "[]");
            return new JSONArray(data);
        } catch (Exception e) {
            LOGGER.log(Level.WARN, "Get AI memory failed for userId {}", userId, e);
            return new JSONArray();
        }
    }

    /**
     * 追加一条 AI 记忆，超出 100 条则删除最旧的，单条截断为 200 字符。
     */
    synchronized public void addAiMemory(String userId, String memory) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(memory)) {
            return;
        }
        memory = memory.trim();
        if (memory.length() > 200) {
            memory = memory.substring(0, 200);
        }
        JSONArray memories = getAiMemory(userId);
        memories.put(memory);
        final List<String> removed = new ArrayList<>();
        if (memories.length() > 100) {
            int overflow = memories.length() - 100;
            for (int i = 0; i < overflow; i++) {
                removed.add(memories.optString(i));
            }
            JSONArray trimmed = new JSONArray();
            for (int i = overflow; i < memories.length(); i++) {
                trimmed.put(memories.get(i));
            }
            memories = trimmed;
        }
        saveAiMemory(userId, memories);
        LOGGER.log(Level.INFO, "AI memory add " + label(userId)
                + ", added=\"" + memory + "\""
                + ", removed=" + removed
                + ", finalCount=" + memories.length()
                + ", finalChars=" + calcChars(memories)
                + ", memories=" + memories.toList());
    }

    /**
     * 按文本删除记忆（按出现顺序删除一次）。
     */
    synchronized public void removeAiMemory(String userId, JSONArray targets) {
        if (Objects.equals(AI_MEMORY_GLOBAL_UID, userId)) {
            LOGGER.log(Level.INFO, "Skip removing global AI memory by request, user=" + label(userId));
            return;
        }
        if (StringUtils.isBlank(userId) || targets == null || targets.length() == 0) {
            return;
        }
        JSONArray memories = getAiMemory(userId);
        List<String> removed = new ArrayList<>();
        for (int t = 0; t < targets.length(); t++) {
            String target = targets.optString(t, "").trim();
            if (target.isEmpty()) {
                continue;
            }
            JSONArray rebuilt = new JSONArray();
            boolean hit = false;
            for (int i = 0; i < memories.length(); i++) {
                String mem = memories.optString(i);
                if (!hit && Objects.equals(mem, target)) {
                    hit = true;
                    removed.add(mem);
                    continue;
                }
                rebuilt.put(mem);
            }
            memories = rebuilt;
        }
        saveAiMemory(userId, memories);
        if (!removed.isEmpty()) {
            LOGGER.log(Level.INFO, "AI memory remove " + label(userId)
                    + ", targets=" + targets.toList()
                    + ", removed=" + removed
                    + ", finalCount=" + memories.length()
                    + ", finalChars=" + calcChars(memories)
                    + ", memories=" + memories.toList());
        }
    }

    /**
     * 清空用户记忆（不允许清空全局记忆）。
     */
    synchronized public void clearAiMemory(String userId) {
        if (StringUtils.isBlank(userId) || Objects.equals(AI_MEMORY_GLOBAL_UID, userId)) {
            LOGGER.log(Level.INFO, "Skip clearing AI memory for user=" + label(userId));
            return;
        }
        saveAiMemory(userId, new JSONArray());
        LOGGER.log(Level.INFO, "AI memory cleared for user=" + label(userId));
    }

    /**
     * 限制单个用户和马库斯全局记忆的总字符数不超过 2000，多余的从最旧开始删除。
     */
    synchronized public void enforceAiMemoryTotalLimit(String userId) {
        JSONArray userMem = getAiMemory(userId);
        JSONArray globalMem = getAiMemory(AI_MEMORY_GLOBAL_UID);

        final List<String> removedUser = new ArrayList<>();
        final List<String> removedGlobal = new ArrayList<>();

        while (calcChars(userMem) + calcChars(globalMem) > 2000) {
            int userChars = calcChars(userMem);
            int globalChars = calcChars(globalMem);
            if ((userChars >= globalChars && userMem.length() > 0) || globalMem.length() == 0) {
                removedUser.add(userMem.optString(0));
                userMem = sliceFrom(userMem, 1);
            } else if (globalMem.length() > 0) {
                removedGlobal.add(globalMem.optString(0));
                globalMem = sliceFrom(globalMem, 1);
            } else {
                break;
            }
        }

        if (!removedUser.isEmpty()) {
            saveAiMemory(userId, userMem);
        }
        if (!removedGlobal.isEmpty()) {
            saveAiMemory(AI_MEMORY_GLOBAL_UID, globalMem);
        }
        if (!removedUser.isEmpty() || !removedGlobal.isEmpty()) {
            LOGGER.log(Level.INFO, "AI memory total trim user=" + label(userId)
                    + ", removedUser=" + removedUser
                    + ", removedGlobal=" + removedGlobal
                    + ", finalChars user=" + calcChars(userMem)
                    + ", global=" + calcChars(globalMem)
                    + ", total=" + (calcChars(userMem) + calcChars(globalMem))
                    + ", userMem=" + userMem.toList()
                    + ", globalMem=" + globalMem.toList());
        }
    }

    private String label(String userId) {
        return Objects.equals(AI_MEMORY_GLOBAL_UID, userId) ? "global(AI_MEMORY_GLOBAL_UID)" : "user(" + userId + ")";
    }

    private int calcChars(JSONArray arr) {
        int len = 0;
        for (int i = 0; i < arr.length(); i++) {
            len += arr.optString(i, "").length();
        }
        return len;
    }

    private JSONArray sliceFrom(JSONArray arr, int start) {
        JSONArray res = new JSONArray();
        for (int i = start; i < arr.length(); i++) {
            res.put(arr.get(i));
        }
        return res;
    }

    /**
     * 覆盖保存 AI 记忆。
     */
    synchronized private void saveAiMemory(String userId, JSONArray memories) {
        try {
            final Transaction transaction = cloudRepository.beginTransaction();
            Query cloudDeleteQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, AI_MEMORY)
                    ));
            cloudRepository.remove(cloudDeleteQuery);

            JSONObject cloudJSON = new JSONObject();
            cloudJSON.put("userId", userId)
                    .put("gameId", AI_MEMORY)
                    .put("data", memories.toString());
            cloudRepository.add(cloudJSON);
            transaction.commit();
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Cannot save AI memory for userId " + userId, e);
        }
    }
}
